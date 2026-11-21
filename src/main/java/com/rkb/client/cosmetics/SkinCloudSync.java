package com.rkb.client.cosmetics;

/**
 * Local-only placeholder for skin cloud sync.
 *
 * RKB Cosmetics V2 exposes catalog/inventory/equip/unequip/redeem/presence/lookup only. It does not expose a private
 * skin-upload endpoint, so this class intentionally performs no network request and claims no successful sync. Local
 * skin sets continue to work through {@link SkinSetStore}; remote skins, when present, come from the V2 lookup and are
 * rendered by {@link SkinTexturesCache}.
 */
public final class SkinCloudSync {
    public static final String STATUS = "Local only · V2 has no skin upload action";

    private SkinCloudSync() {
    }

    /** Kept for old callers; always false because no private cloud skin endpoint is assumed. */
    public static boolean eligible() {
        return false;
    }

    public static long lastSyncMs() {
        return 0;
    }

    /** No-op: there is no supported V2 skin sync action. */
    public static void refresh() {
    }

    static void rename(SkinSetStore.SkinSet set) {
    }

    static void delete(SkinSetStore.SkinSet set) {
    }

    static void equip(SkinSetStore.SkinSet set) {
    }

    static void unequip() {
    }
}
