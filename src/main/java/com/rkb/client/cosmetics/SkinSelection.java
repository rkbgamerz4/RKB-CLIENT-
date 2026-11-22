package com.rkb.client.cosmetics;

/**
 * Pure description of what the skin preview should show.
 *
 * A custom selection points at a local skin-set PNG; otherwise the preview must use vanilla's real default skin for
 * the current UUID (Steve/Alex as determined by Minecraft). No synthetic player texture is ever invented here.
 */
public record SkinSelection(boolean custom, String name, String sha, String model) {
    private static final SkinSelection DEFAULT = new SkinSelection(false, "Minecraft default", "", "");

    public static SkinSelection defaultSkin() {
        return DEFAULT;
    }

    public static SkinSelection ofCustom(String name, String sha, String model) {
        if (sha == null || sha.isBlank()) {
            return DEFAULT;
        }
        return new SkinSelection(true, name == null || name.isBlank() ? "Skin" : name, sha, model == null ? "" : model);
    }

    /** The reset action is simply a return to the default selection; no file is deleted or replaced. */
    public static SkinSelection afterReset() {
        return DEFAULT;
    }

    public boolean usesMinecraftDefault() {
        return !custom;
    }

    public String modelOrDefault(String fallback) {
        return custom && SkinRules.validModel(model) ? model : (fallback == null ? "" : fallback);
    }
}
