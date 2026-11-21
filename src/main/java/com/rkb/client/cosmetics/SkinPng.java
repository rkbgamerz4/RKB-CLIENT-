package com.rkb.client.cosmetics;

import net.minecraft.client.texture.NativeImage;

/**
 * Skin image inspection that needs a decoded image. Model detection is the same rule used by popular skin tools:
 * a slim (Alex) arm leaves the fourth arm column transparent in the 64x64 layout.
 */
public final class SkinPng {
    private SkinPng() {
    }

    public static String detectModel(NativeImage image) {
        if (image == null || image.getHeight() < 64) {
            return SkinRules.MODEL_CLASSIC;   // legacy 64x32 has no slim layout
        }
        try {
            // Right arm back column and left arm front column. Classic skins keep both opaque.
            if (alpha(image, 54, 20) == 0 || alpha(image, 46, 52) == 0) {
                return SkinRules.MODEL_SLIM;
            }
        } catch (RuntimeException ignored) {
            // fall through to classic
        }
        return SkinRules.MODEL_CLASSIC;
    }

    private static int alpha(NativeImage image, int x, int y) {
        return (image.getColorArgb(x, y) >>> 24) & 0xff;
    }
}
