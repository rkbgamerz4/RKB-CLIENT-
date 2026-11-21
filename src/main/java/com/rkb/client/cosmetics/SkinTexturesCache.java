package com.rkb.client.cosmetics;

import com.rkb.client.RkbClient;
import com.rkb.client.cloud.CloudService;
import com.rkb.client.cloud.CloudStore;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Skin images, downloaded/decoded off the render thread and registered on the game thread.
 * The render path only looks up an immutable hash -> Identifier map.
 */
public final class SkinTexturesCache {
    private static final int MAX_TEXTURES = 40;

    private static volatile Map<String, Identifier> loaded = Map.of();
    private static final Set<String> IN_FLIGHT = new HashSet<>();
    private static final Set<String> FAILED = new HashSet<>();

    private SkinTexturesCache() {
    }

    public static Path cacheDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("rkbclient-skins").resolve("cache");
    }

    public static boolean ready(String sha) {
        return sha != null && loaded.containsKey(sha);
    }

    public static Identifier texture(String sha) {
        return sha == null ? null : loaded.get(sha);
    }

    /** Local file saved by SkinSetStore. */
    public static void ensureLocal(String sha, Path file) {
        if (!AssetRules.validSha(sha) || file == null) {
            return;
        }
        start(sha, () -> {
            byte[] data = Files.readAllBytes(file);
            return checked(sha, data);
        });
    }

    /** Remote skin from the public rkb-cosmetics bucket, cached on disk after a hash check. */
    public static void ensureRemote(String sha, String path) {
        if (!AssetRules.validSha(sha) || !AssetRules.validPath(path)) {
            return;
        }
        start(sha, () -> {
            Path cache = cacheDir().resolve(sha + ".png");
            if (Files.isRegularFile(cache) && Files.size(cache) <= SkinRules.MAX_BYTES) {
                byte[] data = Files.readAllBytes(cache);
                if (AssetRules.sha256Hex(data).equals(sha)) {
                    return checked(sha, data);
                }
            }
            String url = AssetRules.storageUrl(CloudStore.url(), path);
            if (url == null) {
                throw new IOException("unsafe skin path");
            }
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12)).GET().build();
            HttpResponse<InputStream> res = CloudService.HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (res.statusCode() != 200) {
                res.body().close();
                throw new IOException("skin status " + res.statusCode());
            }
            byte[] data;
            try (InputStream in = res.body()) {
                data = in.readNBytes(SkinRules.MAX_BYTES + 1);
            }
            if (data.length > SkinRules.MAX_BYTES) {
                throw new IOException("skin too large");
            }
            byte[] valid = checked(sha, data);
            Files.createDirectories(cacheDir());
            Files.write(cache, valid);
            return valid;
        });
    }

    /** Bytes that were just imported; reuse them instead of reading the file again. */
    public static void ensureBytes(String sha, byte[] data) {
        if (!AssetRules.validSha(sha) || data == null) {
            return;
        }
        start(sha, () -> checked(sha, data.clone()));
    }

    private static byte[] checked(String sha, byte[] data) throws IOException {
        if (data.length > SkinRules.MAX_BYTES) {
            throw new IOException("skin too large");
        }
        if (!AssetRules.sha256Hex(data).equals(sha)) {
            throw new IOException("skin hash mismatch");
        }
        String problem = SkinRules.validatePng(data);
        if (!problem.isEmpty()) {
            throw new IOException(problem);
        }
        return data;
    }

    private interface Loader {
        byte[] load() throws Exception;
    }

    private static void start(String sha, Loader loader) {
        synchronized (IN_FLIGHT) {
            if (loaded.containsKey(sha) || FAILED.contains(sha) || !IN_FLIGHT.add(sha) || loaded.size() >= MAX_TEXTURES) {
                return;
            }
        }
        CloudService.background(() -> {
            try {
                byte[] data = loader.load();
                NativeImage image = NativeImage.read(new ByteArrayInputStream(data));
                if (!SkinRules.validDimensions(image.getWidth(), image.getHeight())) {
                    image.close();
                    throw new IOException("unsupported skin size");
                }
                NativeImage normalized = normalizeLegacy(image);
                MinecraftClient mc = MinecraftClient.getInstance();
                mc.execute(() -> register(mc, sha, normalized));
            } catch (Exception e) {
                RkbClient.LOGGER.warn("Skin {} could not be loaded: {}", sha.substring(0, 8), e.toString());
                synchronized (IN_FLIGHT) {
                    FAILED.add(sha);
                    IN_FLIGHT.remove(sha);
                }
            }
        });
    }

    /**
     * Vanilla skins may still be the legacy 64x32 layout. Expand them to the modern 64x64 layout using the same
     * remapping Minecraft uses when it loads a skin, so the in-game renderer and the menu preview agree.
     */
    private static NativeImage normalizeLegacy(NativeImage image) {
        if (image.getHeight() != 32) {
            return image;
        }
        NativeImage out = new NativeImage(64, 64, true);
        out.copyFrom(image);
        image.close();
        out.fillRect(0, 32, 64, 32, 0);
        out.copyRect(4, 16, 16, 32, 4, 4, true, false);
        out.copyRect(8, 16, 16, 32, 4, 4, true, false);
        out.copyRect(0, 20, 24, 32, 4, 12, true, false);
        out.copyRect(4, 20, 16, 32, 4, 12, true, false);
        out.copyRect(8, 20, 8, 32, 4, 12, true, false);
        out.copyRect(12, 20, 16, 32, 4, 12, true, false);
        out.copyRect(44, 16, -8, 32, 4, 4, true, false);
        out.copyRect(48, 16, -8, 32, 4, 4, true, false);
        out.copyRect(40, 20, 0, 32, 4, 12, true, false);
        out.copyRect(44, 20, -8, 32, 4, 12, true, false);
        out.copyRect(48, 20, -16, 32, 4, 12, true, false);
        out.copyRect(52, 20, -8, 32, 4, 12, true, false);
        stripAlpha(out, 0, 0, 32, 16);
        stripColor(out, 32, 0, 64, 32);
        stripAlpha(out, 0, 16, 64, 32);
        stripAlpha(out, 16, 48, 48, 64);
        return out;
    }

    private static void stripAlpha(NativeImage image, int x1, int y1, int x2, int y2) {
        for (int x = x1; x < x2; x++) {
            for (int y = y1; y < y2; y++) {
                image.setColorArgb(x, y, image.getColorArgb(x, y) | 0xFF000000);
            }
        }
    }

    private static void stripColor(NativeImage image, int x1, int y1, int x2, int y2) {
        for (int x = x1; x < x2; x++) {
            for (int y = y1; y < y2; y++) {
                int argb = image.getColorArgb(x, y);
                if ((argb >>> 24) < 128) {
                    return;
                }
            }
        }
        for (int x = x1; x < x2; x++) {
            for (int y = y1; y < y2; y++) {
                image.setColorArgb(x, y, image.getColorArgb(x, y) & 0x00FFFFFF);
            }
        }
    }

    private static void register(MinecraftClient mc, String sha, NativeImage image) {
        try {
            Identifier id = Identifier.of(RkbClient.MOD_ID, "cosmetics/skin_" + sha.substring(0, 16));
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "RKB skin " + sha.substring(0, 8), image);
            texture.upload();
            mc.getTextureManager().registerTexture(id, texture);
            Map<String, Identifier> next = new HashMap<>(loaded);
            next.put(sha, id);
            loaded = Map.copyOf(next);
            PlayerTextures.clearResults();
        } catch (Throwable t) {
            RkbClient.LOGGER.warn("Skin texture registration failed: {}", t.toString());
            synchronized (IN_FLIGHT) {
                FAILED.add(sha);
            }
        } finally {
            synchronized (IN_FLIGHT) {
                IN_FLIGHT.remove(sha);
            }
        }
    }

    /** Retry a previously failed image (for example after the player replaces a file). */
    public static void retry(String sha) {
        if (sha == null) {
            return;
        }
        synchronized (IN_FLIGHT) {
            FAILED.remove(sha);
        }
    }
}
