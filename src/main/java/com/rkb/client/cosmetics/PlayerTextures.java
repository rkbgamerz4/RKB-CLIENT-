package com.rkb.client.cosmetics;

import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Composes the final SkinTextures for a player from (1) a local/remote RKB skin, (2) an equipped RKB cape and
 * (3) the vanilla skin. Only in-memory lookups: no network, no disk, no allocation when the inputs did not change.
 */
public final class PlayerTextures {
    private record Cached(SkinTextures original, Identifier skin, Identifier cape, PlayerSkinType model,
                          SkinTextures result) {
    }

    private static final Map<UUID, Cached> RESULTS = new ConcurrentHashMap<>();

    private PlayerTextures() {
    }

    public static SkinTextures apply(UUID player, SkinTextures original) {
        if (player == null || original == null) {
            return original;
        }
        Identifier skin = null;
        PlayerSkinType model = null;
        if (RemoteCosmetics.isSelf(player)) {
            SkinSetStore.SkinSet active = SkinSetStore.activeSet();
            if (active != null) {
                skin = SkinTexturesCache.texture(active.sha256());
                model = toType(active.model());
            }
        } else {
            RemoteCosmetics.Look look = RemoteCosmetics.lookOf(player);
            if (look != null && look.skinSha() != null && look.skinVerified()) {
                skin = SkinTexturesCache.texture(look.skinSha());
                model = toType(look.skinModel());
            }
        }
        Identifier cape = CapeTextures.textureOf(player);
        if (skin == null && cape == null) {
            return original;
        }
        Cached c = RESULTS.get(player);
        if (c != null && c.original() == original && same(c.skin(), skin) && same(c.cape(), cape)
                && c.model() == model) {
            return c.result();
        }
        SkinTextures result = original;
        if (skin != null) {
            result = new SkinTextures(new AssetInfo.TextureAssetInfo(skin, skin), result.cape(), result.elytra(),
                    model == null ? original.model() : model, original.secure());
        }
        if (cape != null) {
            result = new SkinTextures(result.body(), new AssetInfo.TextureAssetInfo(cape, cape), result.elytra(),
                    result.model(), result.secure());
        }
        RESULTS.put(player, new Cached(original, skin, cape, model, result));
        if (RESULTS.size() > 512) {
            RESULTS.clear();
        }
        return result;
    }

    private static boolean same(Identifier a, Identifier b) {
        return a == null ? b == null : a.equals(b);
    }

    public static PlayerSkinType toType(String model) {
        return SkinRules.MODEL_SLIM.equals(model) ? PlayerSkinType.SLIM : PlayerSkinType.WIDE;
    }

    public static void clearResults() {
        RESULTS.clear();
    }

    public static void clear(UUID player) {
        if (player != null) {
            RESULTS.remove(player);
        }
    }
}
