package com.rkb.client.cosmetics;

import com.rkb.client.module.impl.CosmeticsModule;
import com.rkb.client.module.impl.NameTagBadgeModule;
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

    public static boolean hasRkbIdentity(UUID player) {
        return !RemoteCosmetics.isSelf(player) && RemoteCosmetics.isRkbClient(player);
    }

    /**
     * Puts the equipped RKB badge, or the plain RKB logo for an RKB CLIENT user without a badge, in front of a
     * player's name. The real Minecraft username is always kept after the prefix.
     */
    public static Text withBadge(UUID player, Text original) {
        if (RemoteCosmetics.isSelf(player)) {
            return original;   // your own name tag is never drawn
        }
        RemoteCosmetics.Look look = RemoteCosmetics.lookOf(player);
        if (look == null) {
            return original;
        }
        String key = look.badgeKey();
        String glyph = key == null ? null : BadgeGlyphs.glyph(key);
        if (glyph == null) {
            CosmeticsModule module = CosmeticsModule.get();
            if (!look.rkbClient() || module == null || !module.isEnabled() || !module.nameTagLogo()) {
                return original;
            }
            key = "brand";
            glyph = NameTagBadgeModule.ICON;
        }
        Text prefix = PREFIXES.get(key);
        if (prefix == null) {
            prefix = Text.literal(glyph + " ").styled(s -> s.withColor(0xFFFFFF));
            PREFIXES.put(key, prefix);
        }
        MutableText out = Text.empty();
        out.append(prefix);
        out.append(original);
        return out;
    }
}
