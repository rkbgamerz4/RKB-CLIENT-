package com.rkb.client.cosmetics;

import java.util.Map;

/**
 * Badges are glyphs shipped inside the mod (assets/minecraft/font/default.json), so the server can only choose among
 * these keys. A badge cosmetic with an unknown key is ignored.
 */
public final class BadgeGlyphs {
    private static final Map<String, String> GLYPHS = Map.of(
            "rkb_logo", "\uE7A1",
            "rkb_gold", "\uE7A2",
            "rkb_violet", "\uE7A3",
            "rkb_red", "\uE7A4");

    private BadgeGlyphs() {
    }

    /** The glyph for a badge key, or null if this client does not support it. */
    public static String glyph(String key) {
        return key == null ? null : GLYPHS.get(key);
    }

    public static boolean supported(String key) {
        return glyph(key) != null;
    }
}
