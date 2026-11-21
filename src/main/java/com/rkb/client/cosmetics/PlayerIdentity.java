package com.rkb.client.cosmetics;

import com.rkb.client.cloud.CloudStore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;

import java.util.Locale;
import java.util.UUID;

/**
 * The Minecraft identity the game is already running as.
 *
 * There is deliberately no RKB username/password screen. The current Minecraft username and UUID are read from the
 * running game session. Cloud offers (claiming a cape, syncing skin sets, showing cosmetics to other players) are only
 * enabled when the RKB account has proven this exact UUID through the existing Mojang link flow
 * ({@code /rkb cloud link}). An offline or unlinked name is local-only and can never claim somebody else's inventory.
 */
public final class PlayerIdentity {
    private PlayerIdentity() {
    }

    public static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }

    public static String username() {
        Session s = mc().getSession();
        if (s != null && s.getUsername() != null && !s.getUsername().isBlank()) {
            return s.getUsername();
        }
        return mc().player == null ? "" : mc().player.getName().getString();
    }

    /** Prefers the game session UUID; falls back to the player entity while the session has none. */
    public static UUID uuid() {
        Session s = mc().getSession();
        if (s != null && s.getUuidOrNull() != null) {
            return s.getUuidOrNull();
        }
        return mc().player == null ? null : mc().player.getUuid();
    }

    public static String uuidString() {
        UUID id = uuid();
        return id == null ? "" : id.toString().toLowerCase(Locale.ROOT);
    }

    /** True only when the signed-in RKB account proved this exact Minecraft UUID with Mojang. */
    public static boolean verified() {
        String linked = CloudStore.linkedUuid();
        String current = uuidString();
        return !linked.isEmpty() && !current.isEmpty() && linked.equalsIgnoreCase(current);
    }

    public static boolean signedIn() {
        return CloudStore.signedIn() && !CloudStore.anonKey().isEmpty();
    }

    /** Verified Minecraft UUID + signed-in RKB account: the only state in which cloud inventory is read or written. */
    public static boolean verifiedOnline() {
        return signedIn() && verified();
    }

    /** Display-only. */
    public static String statusText() {
        if (!signedIn()) {
            return "Local only";
        }
        if (!verified()) {
            return "Link required";
        }
        return "Online · verified";
    }

    /** Display-only. */
    public static String statusDetail() {
        if (!signedIn()) {
            return "Local skins, bundled capes and cached textures work offline. Sign in to use V2 account features.";
        }
        if (!verified()) {
            return "Run /rkb cloud link once to prove this Minecraft account. Until then only local cosmetics are used.";
        }
        return "V2 inventory and redeem are enabled for this linked UUID. Skin uploads are not part of V2.";
    }

    public static String shortUuid() {
        String u = uuidString();
        return u.length() > 8 ? u.substring(0, 8) : (u.isEmpty() ? "offline" : u);
    }
}
