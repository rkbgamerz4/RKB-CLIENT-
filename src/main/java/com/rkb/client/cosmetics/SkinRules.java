package com.rkb.client.cosmetics;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Validation shared by the local skin-set library and the cloud uploader. Pure Java (no Minecraft classes), so the
 * exact same rules can be unit tested and mirrored in supabase/functions/_shared/validate.ts.
 */
public final class SkinRules {
    public static final int MAX_BYTES = 256 * 1024;
    public static final int MAX_SETS = 20;
    public static final int NAME_MAX = 32;

    public static final String MODEL_CLASSIC = "classic";
    public static final String MODEL_SLIM = "slim";

    private static final Pattern NAME = Pattern.compile("^[\\p{L}\\p{N} _.-]{1," + NAME_MAX + "}$");
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

    private SkinRules() {
    }

    public static boolean validName(String name) {
        return name != null && NAME.matcher(name.trim()).matches();
    }

    public static String cleanName(String name) {
        return name == null ? "" : name.trim();
    }

    public static boolean validModel(String model) {
        return MODEL_CLASSIC.equals(model) || MODEL_SLIM.equals(model);
    }

    public static String modelFromLabel(String label) {
        return "slim".equalsIgnoreCase(label) ? MODEL_SLIM : MODEL_CLASSIC;
    }

    public static String label(String model) {
        return MODEL_SLIM.equals(model) ? "Slim (Alex)" : "Classic (Steve)";
    }

    public static boolean validUuid(String id) {
        return id != null && UUID_PATTERN.matcher(id.toLowerCase(Locale.ROOT)).matches();
    }

    public static boolean validSetId(String id) {
        try {
            return id != null && UUID.fromString(id) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Standard skins are 64x64 (modern) or 64x32 (legacy). HD skins are not accepted for account sync. */
    public static boolean validDimensions(int width, int height) {
        return width == 64 && (height == 64 || height == 32);
    }

    /** Reads width and height from a PNG IHDR without decoding the image. Returns null for non-PNG data. */
    public static int[] pngSize(byte[] data) {
        if (data == null || data.length < 33) {
            return null;
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (data[i] != PNG_SIGNATURE[i]) {
                return null;
            }
        }
        // bytes 8..11 = length, 12..15 = "IHDR"
        if (data[12] != 'I' || data[13] != 'H' || data[14] != 'D' || data[15] != 'R') {
            return null;
        }
        int width = readInt(data, 16);
        int height = readInt(data, 20);
        return width > 0 && height > 0 ? new int[]{width, height} : null;
    }

    private static int readInt(byte[] d, int at) {
        return ((d[at] & 0xff) << 24) | ((d[at + 1] & 0xff) << 16) | ((d[at + 2] & 0xff) << 8) | (d[at + 3] & 0xff);
    }

    /** Quick validation of an uploaded file before it is decoded. */
    public static String validatePng(byte[] data) {
        if (data == null || data.length == 0) {
            return "The file is empty.";
        }
        if (data.length > MAX_BYTES) {
            return "That PNG is larger than 256 KB.";
        }
        int[] size = pngSize(data);
        if (size == null) {
            return "That is not a PNG file.";
        }
        if (!validDimensions(size[0], size[1])) {
            return "Minecraft skins must be 64x64 or 64x32 pixels (this file is " + size[0] + "x" + size[1] + ").";
        }
        return "";
    }

    public static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
