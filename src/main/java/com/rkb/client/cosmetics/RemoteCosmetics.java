package com.rkb.client.cosmetics;

import com.google.gson.JsonObject;
import com.rkb.client.RkbClient;
import com.rkb.client.cloud.BadgeRegistry;
import com.rkb.client.cloud.CloudException;
import com.rkb.client.cloud.CloudService;
import com.rkb.client.cloud.CloudStore;
import com.rkb.client.cloud.CosmeticsV2Api;
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
 * Presence and verified multiplayer looks for the deployed V2 function.
 *
 * Only a locally computed SHA-256 of the normalized server address is sent. The raw server address and the Minecraft
 * access token never leave this device. Every remote identity must be explicitly verified by the backend before a
 * cape, badge, skin or name prefix is drawn.
 */
public final class RemoteCosmetics {
    /**
     * Any cosmetic field may be null. {@code rkbClient} means the backend explicitly verified this Minecraft UUID.
     * {@code skinVerified} is required before a remote skin is drawn.
     */
    public record Look(String capeId, String capePath, String capeSha, String badgeKey,
                       String skinId, String skinPath, String skinSha, String skinModel,
                       boolean rkbClient, boolean skinVerified) {
    }

    private record Cached(Look look, long fetchedMs) {
        boolean expired(long now) {
            return CosmeticsV2Api.expired(fetchedMs, now, CosmeticsV2Api.LOOK_TTL_MS);
        }
    }

    private record Snapshot(String serverHash, UUID selfUuid, List<UUID> visible) {
    }

    private static final long PRESENCE_EVERY_MS = 5 * 60_000L;

    private static volatile Map<UUID, Cached> others = Map.of();
    private static volatile Look self;
    private static volatile UUID selfUuid;
    private static final Map<UUID, Long> CHECKED = new HashMap<>();   // cloud thread
    private static ScheduledFuture<?> task;
    private static String lastPresenceHash = "";
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
        Cached c = others.get(id);
        if (c == null || c.expired(System.currentTimeMillis())) {
            return null;
        }
        return c.look();
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
                clearRemote();
            } else {
                CosmeticsManager.ensureOfflineLook();
            }
        }
    }

    /** Called by CosmeticsManager when your own verified inventory/equipment changed. */
    public static void setSelf(Look look) {
        CosmeticsModule m = CosmeticsModule.get();
        self = m != null && m.isEnabled() ? look : null;
        PlayerTextures.clearResults();
        if (self != null && self.capeSha() != null) {
            CapeTextures.ensure(self.capeId(), self.capeSha(), self.capePath());
        }
    }

    /** Drops all remote metadata. Used on disconnect, sign-out, feature-off and client shutdown. */
    public static synchronized void clearRemote() {
        if (!others.isEmpty() || self != null || !CHECKED.isEmpty()) {
            others = Map.of();
            self = null;
            synchronized (CHECKED) {
                CHECKED.clear();
            }
            BadgeRegistry.clear();
            PlayerTextures.clearResults();
        }
        lastPresenceHash = "";
        lastPresenceMs = 0;
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
            clearRemote();
            return;
        }
        CapeCollection.all().forEach(c -> CapeTextures.ensureBuiltin(c.id()));
        if (!CloudService.cloudAllowed()) {
            clearRemote();
            return;
        }
        task = CloudService.scheduler().scheduleWithFixedDelay(CloudService.guarded(() -> {
            try {
                tick();
            } catch (Exception e) {
                RkbClient.LOGGER.warn("RKB cosmetics refresh failed: {}", e.toString());
            }
        }), 3, 10, TimeUnit.SECONDS);
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
            String hash = info == null ? null : ServerKey.sha256Hex(info.address);
            List<UUID> visible = new ArrayList<>();
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p != mc.player) {
                    visible.add(p.getUuid());
                }
            }
            f.complete(new Snapshot(hash, mc.player.getUuid(), visible));
        });
        try {
            return f.get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            return null;
        }
    }

    private static void tick() throws Exception {
        CosmeticsModule m = CosmeticsModule.get();
        if (m == null || !m.isEnabled() || !CloudService.cloudAllowed()) {
            clearRemote();
            return;
        }
        Snapshot s = snapshot();
        if (s == null || s.serverHash() == null) {
            // Singleplayer, title screen or leaving a server: drop stale remote cosmetics.
            clearRemote();
            return;
        }
        long now = System.currentTimeMillis();
        if (m.shareMine() && CloudService.isLinked()
                && (!s.serverHash().equals(lastPresenceHash) || now - lastPresenceMs > PRESENCE_EVERY_MS)) {
            registerPresence(s, now);
        }
        if (m.showOthers()) {
            lookup(s, now);
        }
    }

    private static void registerPresence(Snapshot s, long now) throws CloudException, IOException, InterruptedException {
        JsonObject res = CloudService.callV2(CosmeticsV2Api.presenceRequest(
                s.serverHash(), s.selfUuid(), RkbClient.VERSION));
        String error = CosmeticsV2Api.ok(res) ? "" : CosmeticsV2Api.error(res);
        // Do not retry every ten seconds, even when the backend refuses this identity.
        lastPresenceHash = s.serverHash();
        lastPresenceMs = now;
        if (!error.isEmpty() && WARNED.add(error)) {
            CloudService.notifyPlayer(switch (error) {
                case "identity_taken", "identity_verified_elsewhere" ->
                        "Another RKB account already uses this Minecraft identity on this server.";
                case "identity_not_verified", "not_linked", "link_required" ->
                        "Link this Minecraft account with /rkb cloud link to share cosmetics.";
                case "rate_limited", "too_many_requests" -> "Too many presence updates. Try again later.";
                default -> "Could not share your cosmetics here (" + error + ").";
            });
        }
    }

    private static void lookup(Snapshot s, long now) throws CloudException, IOException, InterruptedException {
        List<UUID> toCheck = new ArrayList<>();
        synchronized (CHECKED) {
            for (UUID id : s.visible()) {
                Long t = CHECKED.get(id);
                if (t == null || CosmeticsV2Api.expired(t, now, CosmeticsV2Api.LOOK_TTL_MS)) {
                    toCheck.add(id);
                }
            }
        }
        if (toCheck.isEmpty()) {
            expireVisible(s.visible(), now);
            return;
        }
        if (toCheck.size() > 100) {
            toCheck = new ArrayList<>(toCheck.subList(0, 100));
        }
        JsonObject res = CloudService.callV2(CosmeticsV2Api.lookupRequest(s.serverHash(), toCheck));
        if (!CosmeticsV2Api.ok(res)) {
            return;   // keep the short-lived cache; never invent a verified look
        }

        List<CosmeticsV2Api.Identity> identities = CosmeticsV2Api.parseIdentities(res);
        Map<UUID, Cached> found = new HashMap<>();
        Set<UUID> eligible = new HashSet<>();
        for (CosmeticsV2Api.Identity identity : identities) {
            if (!s.visible().contains(identity.uuid())) {
                continue;   // never keep a look for a player who left
            }
            CosmeticsV2Api.Asset cape = identity.cape();
            CosmeticsV2Api.Asset skin = identity.skin();
            Cached cached = new Cached(new Look(
                    cape == null ? null : cape.id(),
                    cape == null ? null : cape.path(),
                    cape == null ? null : cape.sha(),
                    identity.badgeKey(),
                    skin == null ? null : skin.id(),
                    skin == null ? null : skin.path(),
                    skin == null ? null : skin.sha(),
                    skin == null ? null : identity.skinModel(),
                    true,
                    skin != null), now);
            found.put(identity.uuid(), cached);
            if (identity.badgeKey() != null) {
                eligible.add(identity.uuid());
            }
            if (cape != null) {
                CapeTextures.ensure(cape.id(), cape.sha(), cape.path());
            }
            if (skin != null) {
                SkinTexturesCache.ensureRemote(skin.sha(), skin.path());
            }
        }

        Set<UUID> stillVisible = new HashSet<>(s.visible());
        Map<UUID, Cached> next = new HashMap<>();
        for (Map.Entry<UUID, Cached> e : others.entrySet()) {
            if (stillVisible.contains(e.getKey()) && !toCheck.contains(e.getKey()) && !e.getValue().expired(now)) {
                next.put(e.getKey(), e.getValue());
                if (e.getValue().look().badgeKey() != null) {
                    eligible.add(e.getKey());
                }
            }
        }
        next.putAll(found);
        others = Map.copyOf(next);
        BadgeRegistry.replaceEligible(eligible);

        synchronized (CHECKED) {
            for (UUID id : toCheck) {
                CHECKED.put(id, now);
            }
            CHECKED.keySet().removeIf(id -> !stillVisible.contains(id));
        }
    }

    private static void expireVisible(List<UUID> visible, long now) {
        Map<UUID, Cached> next = new HashMap<>();
        Set<UUID> eligible = new HashSet<>();
        for (Map.Entry<UUID, Cached> e : others.entrySet()) {
            if (visible.contains(e.getKey()) && !e.getValue().expired(now)) {
                next.put(e.getKey(), e.getValue());
                if (e.getValue().look().badgeKey() != null) {
                    eligible.add(e.getKey());
                }
            }
        }
        if (next.size() != others.size()) {
            others = Map.copyOf(next);
            BadgeRegistry.replaceEligible(eligible);
        }
    }
}
