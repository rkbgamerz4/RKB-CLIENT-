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
import java.util.UUID;

/**
 * Downloads, verifies and registers cape images. Official RKB capes ship inside the mod; for those the bundled file is
 * used only when its hash matches the hash in the server catalog. Any other cape is downloaded, size-limited,
 * SHA-256 checked and cached. All I/O is off the render thread; the render path only reads a map.
 */
public final class CapeTextures {
    private static final int MAX_TEXTURES = 40;

    private static volatile Map<String, Identifier> bySha = Map.of();
    private static volatile Map<String, Identifier> byId = Map.of();
    private static final Set<String> IN_FLIGHT = new HashSet<>();
    private static final Set<String> FAILED = new HashSet<>();

    private CapeTextures() {
    }

    private static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("rkbclient-cosmetics");
    }

    public static boolean ready(String sha) {
        return sha != null && bySha.containsKey(sha);
    }

    public static Identifier texture(String sha) {
        return sha == null ? null : bySha.get(sha);
    }

    public static Identifier texture(String id, String sha) {
        Identifier t = id == null ? null : byId.get(id);
        return t != null ? t : texture(sha);
    }

    /** The cape the player has equipped, or null when it is not loaded yet / there is none. */
    public static Identifier textureOf(UUID player) {
        RemoteCosmetics.Look look = RemoteCosmetics.lookOf(player);
        return look == null || look.capeSha() == null ? null : bySha.get(look.capeSha());
    }

    /** Starts a download if needed. Safe to call repeatedly. */
    public static void ensure(String id, String sha, String path) {
        if (!AssetRules.validSha(sha) || !AssetRules.validPath(path)) {
            return;
        }
        if (CapeCollection.matchesBundled(id, sha)) {
            ensureBuiltin(id);
            return;
        }
        start(sha, id, false, path);
    }

    /** Registers a bundled cape for the collection thumbnails and for the official cape ids. */
    public static void ensureBuiltin(String id) {
        if (!CapeCollection.isOfficial(id) || byId.containsKey(id)) {
            return;
        }
        String sha = CapeCollection.bundledSha(id);
        byte[] data = CapeCollection.bundledBytes(id);
        if (sha == null || data == null) {
            return;
        }
        start(sha, id, true, null);
    }

    private static void start(String sha, String id, boolean builtin, String path) {
        synchronized (IN_FLIGHT) {
            if ((bySha.containsKey(sha) && (id == null || id.isEmpty() || byId.containsKey(id)))
                    || FAILED.contains(sha) || !IN_FLIGHT.add(sha) || bySha.size() >= MAX_TEXTURES) {
                return;
            }
        }
        CloudService.background(() -> {
            try {
                byte[] data = builtin ? CapeCollection.bundledBytes(id) : fetch(sha, path);
                NativeImage image = NativeImage.read(new ByteArrayInputStream(data));
                if (!AssetRules.validCapeSize(image.getWidth(), image.getHeight())) {
                    image.close();
                    throw new IOException("unsupported cape size");
                }
                MinecraftClient mc = MinecraftClient.getInstance();
                mc.execute(() -> register(mc, sha, id, image));
            } catch (Exception e) {
                RkbClient.LOGGER.warn("Cape {} could not be loaded: {}", sha.substring(0, 8), e.toString());
                synchronized (IN_FLIGHT) {
                    FAILED.add(sha);
                    IN_FLIGHT.remove(sha);
                }
            }
        });
    }

    private static void register(MinecraftClient mc, String sha, String id, NativeImage image) {
        try {
            Identifier tex = Identifier.of(RkbClient.MOD_ID, "cosmetics/cape_" + sha.substring(0, 16));
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "RKB cape " + sha.substring(0, 8), image);
            texture.upload();
            mc.getTextureManager().registerTexture(tex, texture);
            Map<String, Identifier> nextSha = new HashMap<>(bySha);
            nextSha.put(sha, tex);
            bySha = Map.copyOf(nextSha);
            if (id != null && !id.isEmpty()) {
                Map<String, Identifier> nextId = new HashMap<>(byId);
                nextId.put(id, tex);
                byId = Map.copyOf(nextId);
            }
            PlayerTextures.clearResults();
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

    public static void clearResults() {
        PlayerTextures.clearResults();
    }
}
