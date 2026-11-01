package com.rkb.client.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
 *  1. presence: tells the backend "this Minecraft player on this server is my RKB account" (display only, never ownership)
 *  2. lookup: asks which players around you have an RKB account with an equipped cape/badge
 * The render thread only calls lookOf(), a hash lookup on an immutable snapshot.
 */
public final class RemoteCosmetics {
    /** capeSha/capePath are null when no cape is equipped; badgeKey is null when no supported badge is equipped. */
    public record Look(String capeId, String capePath, String capeSha, String badgeKey) {
    }

    private record Snapshot(String serverKey, UUID selfUuid, String selfName, List<UUID> visible) {
    }

    private static final long RECHECK_MS = 120_000L;
    private static final long PRESENCE_EVERY_MS = 10 * 60_000L;

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
        if (id.equals(selfUuid)) {
            return self;
        }
        return others.get(id);
    }

    public static boolean isSelf(UUID id) {
        return id.equals(selfUuid);
    }

    public static void updateSelfUuid(MinecraftClient mc) {
        selfUuid = mc.player == null ? null : mc.player.getUuid();
    }

    /** Called by CosmeticsManager when your own inventory/equipment changed. */
    public static void setSelf(Look look) {
        CosmeticsModule m = CosmeticsModule.get();
        self = m != null && m.isEnabled() ? look : null;
        CapeTextures.clearResults();
        if (self != null && self.capeSha() != null) {
            CapeTextures.ensure(self.capeSha(), self.capePath());
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
            CapeTextures.clearResults();
            return;
        }
        CosmeticsManager.refresh();
        if (CloudStore.signedIn() && !CloudStore.anonKey().isEmpty()) {
            task = CloudService.scheduler().scheduleWithFixedDelay(CloudService.guarded(RemoteCosmetics::tick), 3, 20, TimeUnit.SECONDS);
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
            return;   // singleplayer or no world: nothing to register or look up
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
        lastPresenceKey = s.serverKey();     // do not retry every 20 seconds
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
                if (capeSha != null || badge != null) {
                    found.put(id, new Look(capeId, capePath, capeSha, badge));
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
                CapeTextures.ensure(l.capeSha(), l.capePath());
            }
        }
    }

    private static String text(JsonObject o, String key) {
        return o != null && o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }
}
