package com.rkb.client.cosmetics;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Turns a server address into the key used to scope RKB presence ("this Minecraft identity on this server").
 * Same rule as supabase/functions/_shared/validate.ts (normalizeServerKey); both are tested with the same vectors.
 * Pure Java, no Minecraft classes.
 */
public final class ServerKey {
    private static final Pattern ALLOWED = Pattern.compile("^[a-z0-9._:\\[\\]-]{1,100}$");

    private ServerKey() {
    }

    /** Returns null when the address is unusable. */
    public static String normalize(String address) {
        if (address == null) {
            return null;
        }
        String s = address.trim().toLowerCase(Locale.ROOT);
        if (s.endsWith(":25565")) {
            s = s.substring(0, s.length() - 6);
        }
        while (s.endsWith(".")) {
            s = s.substring(0, s.length() - 1);
        }
        return ALLOWED.matcher(s).matches() ? s : null;
    }
}
