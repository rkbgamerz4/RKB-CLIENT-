package com.rkb.client.cosmetics;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Render-side helpers. Everything here only reads caches: no network, no disk. */
public final class CosmeticsRender {
    private static final Map<String, Text> PREFIXES = new HashMap<>();

    private CosmeticsRender() {
    }

    /** Puts an equipped RKB badge glyph in front of a player's name, if that player has one. */
    public static Text withBadge(UUID player, Text original) {
        if (RemoteCosmetics.isSelf(player)) {
            return original;   // your own name tag is never drawn
        }
        RemoteCosmetics.Look look = RemoteCosmetics.lookOf(player);
        if (look == null || look.badgeKey() == null) {
            return original;
        }
        String glyph = BadgeGlyphs.glyph(look.badgeKey());
        if (glyph == null) {
            return original;
        }
        Text prefix = PREFIXES.get(look.badgeKey());
        if (prefix == null) {
            prefix = Text.literal(glyph + " ").styled(s -> s.withColor(0xFFFFFF));
            PREFIXES.put(look.badgeKey(), prefix);
        }
        MutableText out = Text.empty();
        out.append(prefix);
        out.append(original);
        return out;
    }
}
