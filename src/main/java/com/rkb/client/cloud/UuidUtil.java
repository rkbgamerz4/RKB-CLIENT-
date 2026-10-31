package com.rkb.client.cloud;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** UUID and username validation. Pure Java, no Minecraft classes, so it can be unit tested. */
public final class UuidUtil {
    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final Pattern HEX32 = Pattern.compile("^[0-9a-fA-F]{32}$");
    private static final Pattern DASHED = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private UuidUtil() {
    }

    /** Accepts dashed or 32-digit hex UUIDs. Returns null for anything else (never throws). */
    public static UUID parse(String input) {
        if (input == null) {
            return null;
        }
        String s = input.trim();
        if (HEX32.matcher(s).matches()) {
            s = s.substring(0, 8) + "-" + s.substring(8, 12) + "-" + s.substring(12, 16) + "-"
                    + s.substring(16, 20) + "-" + s.substring(20);
        }
        if (!DASHED.matcher(s).matches()) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static boolean isValidUsername(String name) {
        return name != null && NAME.matcher(name.trim()).matches();
    }

    /** First 8 characters, for showing a UUID when the name is unknown. */
    public static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    public static String canonical(UUID id) {
        return id.toString().toLowerCase(Locale.ROOT);
    }
}
