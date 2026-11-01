package com.rkb.client.cosmetics;

import com.rkb.client.RkbClient;
import com.rkb.client.cloud.CloudService;
import com.rkb.client.cloud.CloudStore;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Downloads, verifies and registers cape images, and swaps them into a player's skin textures.
 * Every image is: path-checked, size-limited (256 KB), SHA-256 checked against the catalog, decoded and size-checked
 * before it becomes a texture. Downloads and decoding run on the cloud thread; textures are registered on the game
 * thread; the render path (apply) only reads maps.
 */
public final class CapeTextures {
    private static final int MAX_TEXTURES = 32;

    private record Cached(SkinTextures original, Identifier texture, SkinTextures result) {
    }

    private static volatile Map<String, Identifier> loaded = Map.of();
    private static final Set<String> IN_FLIGHT = new HashSet<>();
    private static final Set<String> FAILED = new HashSet<>();
    private static final Map<UUID, Cached> RESULTS = new ConcurrentHashMap<>();

    private CapeTextures() {
    }

    private static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("rkbclient-cosmetics");
    }

    /** True once the texture for this image hash is ready to draw. */
    public static boolean ready(String sha) {
        return sha != null && loaded.containsKey(sha);
    }

    /** Starts the download if needed. Safe to call repeatedly. */
    public static void ensure(String sha, String path) {
        if (!AssetRules.validSha(sha) || !AssetRules.validPath(path)) {
            return;
        }
        synchronized (IN_FLIGHT) {
            if (loaded.containsKey(sha) || FAILED.contains(sha) || !IN_FLIGHT.add(sha) || loaded.size() >= MAX_TEXTURES) {
                return;
            }
        }
        CloudService.background(() -> {
            try {
                byte[] data = fetch(sha, path);
                NativeImage image = NativeImage.read(new ByteArrayInputStream(data));
                if (!AssetRules.validCapeSize(image.getWidth(), image.getHeight())) {
                    image.close();
                    throw new IOException("unsupported cape size");
                }
                MinecraftClient mc = MinecraftClient.getInstance();
                mc.execute(() -> register(mc, sha, image));
            } catch (Exception e) {
                RkbClient.LOGGER.warn("Cape {} could not be loaded: {}", sha.substring(0, 8), e.toString());
                synchronized (IN_FLIGHT) {
                    FAILED.add(sha);
                    IN_FLIGHT.remove(sha);
                }
            }
        });
    }

    private static void register(MinecraftClient mc, String sha, NativeImage image) {
        try {
            Identifier id = Identifier.of(RkbClient.MOD_ID, "cosmetics/cape_" + sha.substring(0, 16));
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "RKB cape " + sha.substring(0, 8), image);
            texture.upload();
            mc.getTextureManager().registerTexture(id, texture);
            Map<String, Identifier> next = new HashMap<>(loaded);
            next.put(sha, id);
            loaded = Map.copyOf(next);
        } catch (Throwable t) {
            RkbClient.LOGGER.warn("Cape texture registration failed: {}", t.toString());
            synchronized (IN_FLIGHT) {
                FAILED.add(sha);
            }
        } finally {
            synchronized (IN_FLIGHT) {
                IN_FLIGHT.remove(sha);
            }
        }
    }

    // ------------------------------------------------------------------ download (cloud thread)

    private static byte[] fetch(String sha, String path) throws IOException, InterruptedException {
        Path cache = dir().resolve(sha + ".png");
        if (Files.isRegularFile(cache) && Files.size(cache) <= AssetRules.MAX_BYTES) {
            byte[] cached = Files.readAllBytes(cache);
            if (AssetRules.sha256Hex(cached).equals(sha)) {
                return cached;
            }
        }
        String url = AssetRules.storageUrl(CloudStore.url(), path);
        if (url == null) {
            throw new IOException("unsafe asset path");
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12)).GET().build();
        HttpResponse<InputStream> res = CloudService.HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() != 200) {
            res.body().close();
            throw new IOException("asset status " + res.statusCode());
        }
        byte[] data;
        try (InputStream in = res.body()) {
            data = in.readNBytes(AssetRules.MAX_BYTES + 1);
        }
        if (data.length > AssetRules.MAX_BYTES) {
            throw new IOException("asset too large");
        }
        if (!AssetRules.sha256Hex(data).equals(sha)) {
            throw new IOException("asset hash mismatch");
        }
        Files.createDirectories(dir());
        Files.write(cache, data);
        return data;
    }

    // ------------------------------------------------------------------ render path (no I/O, no allocation when cached)

    /** Returns the skin with this player's equipped cape, or the original when there is none or it is not loaded yet. */
    public static SkinTextures apply(UUID player, SkinTextures original) {
        RemoteCosmetics.Look look = RemoteCosmetics.lookOf(player);
        if (look == null || look.capeSha() == null || original == null) {
            return original;
        }
        Identifier tex = loaded.get(look.capeSha());
        if (tex == null) {
            return original;
        }
        Cached c = RESULTS.get(player);
        if (c != null && c.original() == original && c.texture().equals(tex)) {
            return c.result();
        }
        SkinTextures result = new SkinTextures(original.body(), new AssetInfo.TextureAssetInfo(tex, tex),
                original.elytra(), original.model(), original.secure());
        RESULTS.put(player, new Cached(original, tex, result));
        if (RESULTS.size() > 256) {
            RESULTS.clear();
        }
        return result;
    }

    public static void clearResults() {
        RESULTS.clear();
    }
}
