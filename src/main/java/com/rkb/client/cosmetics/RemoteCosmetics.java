package com.rkb.client.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rkb.client.RkbClient;
import com.rkb.client.cloud.CloudException;
import com.rkb.client.cloud.CloudService;
import com.rkb.client.cloud.CloudStore;
import com.rkb.client.cloud.UuidUtil;
import com.rkb.client.module.impl.CosmeticsModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.entity.player.PlayerEntity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * What other players (and you) wear. Two background jobs, both on the cloud thread and both off the render path:
 *  1. presence: tells the backend "this Minecraft player on this server is my RKB account" (display only)
 *  2. lookup: asks which players around you are RKB CLIENT users and which cape/badge/skin they have equipped
 *
 * The server decides {@code verified}; the client only applies remote skins when that flag is true. A local-only
 * (offline or unlinked) player never uploads skin sets, so an offline username cannot claim another inventory.
 */
public final class RemoteCosmetics {
    /**
     * Any of the cosmetic fields may be null. {@code rkbClient} means the player is known to run RKB CLIENT.
     * {@code skinVerified} is required before a remote skin is drawn.
     */
    public record Look(String capeId, String capePath, String capeSha, String badgeKey,
                       String skinId, String skinPath, String skinSha, String skinModel,
                       boolean rkbClient, boolean skinVerified) {
    }

    private record Snapshot(String serverKey, UUID selfUuid, String selfName, List<UUID> visible) {
    }

    private static final long RECHECK_MS = 30_000L;
    private static final long PRESENCE_EVERY_MS = 5 * 60_000L;

    private static volatile Map<UUID, Look> others = Map.of();
    private static volatile Look self;
    private static volatile UUID selfUuid;
    private static final Map<UUID, Long> CHECKED = new HashMap<>();   // cloud thread
    private static ScheduledFuture<?> task;
    private static String lastPresenceKey = "";
    private static long lastPresenceMs;
    private static final Set<String> WARNED = new HashSet<>();

    private RemoteCosmetics() {
    }

    // ------------------------------------------------------------------ render-thread reads

    public static Look lookOf(UUID id) {
        if (id == null) {
            return null;
        }
        if (id.equals(selfUuid)) {
            return self;
        }
        return others.get(id);
    }

    public static boolean isSelf(UUID id) {
        return id != null && id.equals(selfUuid);
    }

    public static boolean isRkbClient(UUID id) {
        Look l = lookOf(id);
        return l != null && l.rkbClient();
    }

    public static void updateSelfUuid(MinecraftClient mc) {
        UUID now = mc.player == null ? null : mc.player.getUuid();
        if (now == null ? selfUuid != null : !now.equals(selfUuid)) {
            selfUuid = now;
            PlayerTextures.clearResults();
            if (now == null) {
                others = Map.of();
                self = null;
            }
        }
    }

    /** Called by CosmeticsManager when your own inventory/equipment changed. */
    public static void setSelf(Look look) {
        CosmeticsModule m = CosmeticsModule.get();
        self = m != null && m.isEnabled() ? look : null;
        PlayerTextures.clearResults();
        if (self != null && self.capeSha() != null) {
            CapeTextures.ensure(self.capeId(), self.capeSha(), self.capePath());
        }
    }

    // ------------------------------------------------------------------ switching the feature on and off (game thread)

    public static synchronized void onFeatureChanged() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
        CosmeticsModule m = CosmeticsModule.get();
        boolean on = m != null && m.isEnabled() && !CloudService.isStopping();
        if (!on) {
            others = Map.of();
            self = null;
            synchronized (CHECKED) {
                CHECKED.clear();
            }
            PlayerTextures.clearResults();
            return;
        }
        CosmeticsManager.refresh();
        CapeCollection.all().forEach(c -> CapeTextures.ensureBuiltin(c.id()));
        if (CloudStore.signedIn() && !CloudStore.anonKey().isEmpty()) {
            task = CloudService.scheduler().scheduleWithFixedDelay(CloudService.guarded(() -> {
                try {
                    tick();
                } catch (Exception e) {
                    RkbClient.LOGGER.warn("RKB cosmetics refresh failed: {}", e.toString());
                }
            }), 3, 10, TimeUnit.SECONDS);
        }
    }

    // ------------------------------------------------------------------ cloud thread

    private static Snapshot snapshot() {
        MinecraftClient mc = MinecraftClient.getInstance();
        CompletableFuture<Snapshot> f = new CompletableFuture<>();
        mc.execute(() -> {
            if (mc.world == null || mc.player == null) {
                f.complete(null);
                return;
            }
            ServerInfo info = mc.getCurrentServerEntry();
            String key = info == null ? null : ServerKey.normalize(info.address);
            List<UUID> visible = new ArrayList<>();
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p != mc.player) {
                    visible.add(p.getUuid());
                }
            }
            f.complete(new Snapshot(key, mc.player.getUuid(), mc.player.getName().getString(), visible));
        });
        try {
            return f.get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            return null;
        }
    }

    private static void tick() throws Exception {
        CosmeticsModule m = CosmeticsModule.get();
        if (m == null || !m.isEnabled() || !CloudStore.signedIn() || CloudStore.anonKey().isEmpty()) {
            return;
        }
        Snapshot s = snapshot();
        if (s == null || s.serverKey() == null) {
            // Singleplayer, title screen or leaving a server: drop stale remote cosmetics.
            if (!others.isEmpty()) {
                others = Map.of();
            }
            return;
        }
        long now = System.currentTimeMillis();
        if (m.shareMine() && (!s.serverKey().equals(lastPresenceKey) || now - lastPresenceMs > PRESENCE_EVERY_MS)) {
            registerPresence(s, now);
        }
        if (m.showOthers()) {
            lookup(s, now);
        }
    }

    private static void registerPresence(Snapshot s, long now) throws CloudException, IOException, InterruptedException {
        JsonObject b = new JsonObject();
        b.addProperty("action", "presence");
        b.addProperty("mc_uuid", UuidUtil.canonical(s.selfUuid()));
        b.addProperty("mc_name", s.selfName());
        b.addProperty("server_key", s.serverKey());
        JsonObject res = CloudService.callFunction("rkb-cosmetics", b);
        if (res.has("ok") && res.get("ok").getAsBoolean()) {
            lastPresenceKey = s.serverKey();
            lastPresenceMs = now;
            return;
        }
        String error = res.has("error") ? res.get("error").getAsString() : "presence_failed";
        lastPresenceKey = s.serverKey();     // do not retry every 10 seconds
        lastPresenceMs = now;
        if (WARNED.add(error)) {
            CloudService.notifyPlayer(switch (error) {
                case "identity_taken", "identity_verified_elsewhere" ->
                        "Another RKB account already uses this Minecraft name on this server, so your cosmetics will not show here.";
                case "too_many_identities" -> "This RKB account is already registered with too many Minecraft identities.";
                default -> "Could not share your cosmetics here (" + error + ").";
            });
        }
    }

    private static void lookup(Snapshot s, long now) throws CloudException, IOException, InterruptedException {
        List<UUID> toCheck = new ArrayList<>();
        synchronized (CHECKED) {
            for (UUID id : s.visible()) {
                Long t = CHECKED.get(id);
                if ((t == null || now - t > RECHECK_MS) && toCheck.size() < 100) {
                    toCheck.add(id);
                }
            }
        }
        if (toCheck.isEmpty()) {
            return;
        }
        JsonArray arr = new JsonArray();
        for (UUID id : toCheck) {
            arr.add(UuidUtil.canonical(id));
        }
        JsonObject b = new JsonObject();
        b.addProperty("action", "lookup");
        b.addProperty("server_key", s.serverKey());
        b.add("uuids", arr);
        JsonObject res = CloudService.callFunction("rkb-cosmetics", b);

        Map<UUID, Look> found = new HashMap<>();
        if (res.has("looks") && res.get("looks").isJsonArray()) {
            for (JsonElement e : res.getAsJsonArray("looks")) {
                if (!e.isJsonObject()) {
                    continue;
                }
                JsonObject o = e.getAsJsonObject();
                UUID id = UuidUtil.parse(text(o, "uuid"));
                if (id == null) {
                    continue;
                }
                boolean verified = bool(o, "verified");
                String capeId = null;
                String capePath = null;
                String capeSha = null;
                if (o.has("cape") && o.get("cape").isJsonObject()) {
                    JsonObject c = o.getAsJsonObject("cape");
                    if (AssetRules.validSha(text(c, "sha256")) && AssetRules.validPath(text(c, "path"))) {
                        capeId = text(c, "id");
                        capePath = text(c, "path");
                        capeSha = text(c, "sha256");
                    }
                }
                String badge = null;
                if (o.has("badge") && o.get("badge").isJsonObject() && BadgeGlyphs.supported(text(o.getAsJsonObject("badge"), "key"))) {
                    badge = text(o.getAsJsonObject("badge"), "key");
                }
                String skinId = null;
                String skinPath = null;
                String skinSha = null;
                String skinModel = null;
                if (verified && o.has("skin") && o.get("skin").isJsonObject()) {
                    JsonObject sk = o.getAsJsonObject("skin");
                    String path = text(sk, "path");
                    String sha = text(sk, "sha256");
                    String model = text(sk, "model");
                    if (AssetRules.validSha(sha) && AssetRules.validPath(path) && SkinRules.validModel(model)) {
                        skinId = text(sk, "id");
                        skinPath = path;
                        skinSha = sha;
                        skinModel = model;
                    }
                }
                boolean rkb = !o.has("rkb") || bool(o, "rkb");
                if (rkb || capeSha != null || badge != null || skinSha != null) {
                    found.put(id, new Look(capeId, capePath, capeSha, badge, skinId, skinPath, skinSha, skinModel,
                            rkb, verified && skinSha != null));
                }
            }
        }
        Set<UUID> stillVisible = new HashSet<>(s.visible());
        Map<UUID, Look> next = new HashMap<>();
        for (Map.Entry<UUID, Look> e : others.entrySet()) {
            if (stillVisible.contains(e.getKey()) && !toCheck.contains(e.getKey())) {
                next.put(e.getKey(), e.getValue());
            }
        }
        next.putAll(found);
        others = Map.copyOf(next);
        synchronized (CHECKED) {
            for (UUID id : toCheck) {
                CHECKED.put(id, now);
            }
            if (CHECKED.size() > 1024) {
                CHECKED.clear();
            }
        }
        for (Look l : found.values()) {
            if (l.capeSha() != null) {
                CapeTextures.ensure(l.capeId(), l.capeSha(), l.capePath());
            }
            if (l.skinSha() != null && l.skinVerified()) {
                SkinTexturesCache.ensureRemote(l.skinSha(), l.skinPath());
            }
        }
    }

    private static String text(JsonObject o, String key) {
        return o != null && o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }

    private static boolean bool(JsonObject o, String key) {
        return o != null && o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsBoolean();
    }
}
