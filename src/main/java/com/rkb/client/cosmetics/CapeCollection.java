package com.rkb.client.cosmetics;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The ten RKB cape designs that ship inside the mod.
 *
 * The files are only a safety net for the official collection: ownership is always read from the signed-in RKB
 * account. A missing catalog row never makes a cape "owned"; it is shown as not owned and cannot be equipped.
 */
public final class CapeCollection {
    public record Design(String id, String name, String country, String description, String rarity) {
        public String resource() {
            return "/assets/rkbclient/textures/cosmetics/" + id + ".png";
        }
    }

    private static final List<Design> ALL = List.of(
            new Design("cape_bangladesh", "Bangladesh Cape", "Bangladesh",
                    "Deep green with the red sun of Bangladesh.", "common"),
            new Design("cape_india", "India Cape", "India",
                    "Saffron, white and green with the Ashoka Chakra.", "common"),
            new Design("cape_usa", "USA Cape", "United States",
                    "The Stars and Stripes in a clean cape cut.", "common"),
            new Design("cape_pakistan", "Pakistan Cape", "Pakistan",
                    "Pakistan green, white band, crescent and star.", "common"),
            new Design("cape_brazil", "Brazil Cape", "Brazil",
                    "Green field, yellow diamond and blue globe.", "common"),
            new Design("cape_japan", "Japan Cape", "Japan",
                    "White cloth with the red sun.", "common"),
            new Design("cape_uk", "UK Cape", "United Kingdom",
                    "The Union Jack in navy, white and red.", "common"),
            new Design("cape_nepal", "Nepal Cape", "Nepal",
                    "The crimson and blue double-pennant of Nepal.", "common"),
            new Design("cape_rkb_original", "RKB Original Cape", "RKB",
                    "The original RKB navy cape with a cyan R.", "rare"),
            new Design("cape_rkb_shadow", "RKB Shadow Cape", "RKB",
                    "A black RKB cape with a restrained blue edge.", "epic"));

    private static final Map<String, byte[]> BUNDLED = new ConcurrentHashMap<>();
    private static final Map<String, String> BUNDLED_SHA = new ConcurrentHashMap<>();

    private CapeCollection() {
    }

    public static List<Design> all() {
        return ALL;
    }

    public static Design byId(String id) {
        if (id == null) {
            return null;
        }
        String key = id.toLowerCase(Locale.ROOT);
        for (Design d : ALL) {
            if (d.id().equals(key)) {
                return d;
            }
        }
        return null;
    }

    public static boolean isOfficial(String id) {
        return byId(id) != null;
    }

    /** Reads the PNG shipped in the mod. Returns null when the resource is missing. The bytes are cached. */
    public static byte[] bundledBytes(String id) {
        Design d = byId(id);
        if (d == null) {
            return null;
        }
        byte[] cached = BUNDLED.computeIfAbsent(d.id(), key -> {
            byte[] read = readBundled(key);
            return read == null ? new byte[0] : read;
        });
        return cached.length == 0 ? null : cached;
    }

    private static byte[] readBundled(String id) {
        Design d = byId(id);
        if (d == null) {
            return null;
        }
        try (InputStream in = CapeCollection.class.getResourceAsStream(d.resource())) {
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
            byte[] buf = new byte[1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    /** SHA-256 of the bundled PNG, or null if the resource is missing. */
    public static String bundledSha(String id) {
        Design d = byId(id);
        if (d == null) {
            return null;
        }
        String sha = BUNDLED_SHA.computeIfAbsent(d.id(), key -> {
            byte[] data = bundledBytes(key);
            return data == null ? "" : AssetRules.sha256Hex(data);
        });
        return sha.isEmpty() ? null : sha;
    }

    /** True when the server catalog points at exactly the image we ship. */
    public static boolean matchesBundled(String id, String sha) {
        if (!isOfficial(id) || !AssetRules.validSha(sha)) {
            return false;
        }
        String local = bundledSha(id);
        return local != null && local.equals(sha);
    }
}
