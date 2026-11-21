package com.rkb.client.cosmetics;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

/** Checks applied to every downloaded cosmetic image before it is decoded. Pure Java. */
public final class AssetRules {
    public static final int MAX_BYTES = 256 * 1024;
    private static final Pattern PATH = Pattern.compile("^[a-z0-9_/-]{1,100}\\.png$");
    private static final Pattern SHA = Pattern.compile("^[0-9a-f]{64}$");

    private AssetRules() {
    }

    public static boolean validPath(String path) {
        return path != null && PATH.matcher(path).matches() && !path.contains("..") && !path.startsWith("/");
    }

    public static boolean validSha(String sha) {
        return sha != null && SHA.matcher(sha).matches();
    }

    /** Standard capes are 64x32; HD capes are exact multiples of that (128x64, 256x128, 512x256). */
    public static boolean validCapeSize(int width, int height) {
        return width >= 64 && width <= 512 && width == height * 2 && (width & (width - 1)) == 0;
    }

    public static String sha256Hex(byte[] data) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder(64);
            for (byte b : d) {
                sb.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Public URL of an asset in the rkb-cosmetics bucket. Returns null for an unsafe path. */
    public static String storageUrl(String baseUrl, String path) {
        if (!validPath(path) || baseUrl == null || !baseUrl.startsWith("https://")) {
            return null;
        }
        return baseUrl + "/storage/v1/object/public/rkb-cosmetics/" + path;
    }

    /** UTF-8 bytes used for local server hashing. */
    public static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
