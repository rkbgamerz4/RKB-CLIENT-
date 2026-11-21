package com.rkb.client.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rkb.client.RkbClient;
import com.rkb.client.cloud.CloudException;
import com.rkb.client.cloud.CloudService;
import com.rkb.client.cloud.CloudStore;
import com.rkb.client.cloud.UuidUtil;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cloud bridge for the local skin-set library.
 *
 * It is deliberately inert unless the signed-in RKB account has proven the current Minecraft UUID. An offline name can
 * therefore never list, upload, rename or equip another player's cloud skins. Every write is decided by the
 * rkb-cosmetics Edge Function and the SQL functions behind it, never by this class.
 */
public final class SkinCloudSync {
    private static final AtomicBoolean SYNCING = new AtomicBoolean();
    private static volatile long lastSyncMs;

    private SkinCloudSync() {
    }

    public static boolean eligible() {
        return PlayerIdentity.verifiedOnline() && !CloudService.isStopping();
    }

    public static long lastSyncMs() {
        return lastSyncMs;
    }

    /** Fire-and-forget refresh. One sync runs at a time. */
    public static void refresh() {
        if (!eligible() || !SYNCING.compareAndSet(false, true)) {
            return;
        }
        CloudService.background(() -> {
            try {
                syncNow();
                lastSyncMs = System.currentTimeMillis();
            } catch (CloudException e) {
                if (e.status() == 401) {
                    SkinSetStore.setMessage("Your RKB session ended. Sign in again with /rkb cloud login.");
                } else if (e.status() == 403) {
                    SkinSetStore.setMessage("Link this Minecraft account with /rkb cloud link to sync skin sets.");
                } else if (e.status() != 429) {
                    SkinSetStore.setMessage("Could not sync skin sets: " + e.getMessage());
                }
            } catch (IOException e) {
                SkinSetStore.setMessage("You are offline. Skin sets stay saved on this device.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                RkbClient.LOGGER.warn("Skin sync failed: {}", e.toString());
                SkinSetStore.setMessage("Skin sync failed. Local sets are still available.");
            } finally {
                SYNCING.set(false);
            }
        });
    }

    private record Remote(String id, String name, String model, String path, String sha, long size) {
    }

    private static void syncNow() throws CloudException, IOException, InterruptedException {
        if (!eligible()) {
            return;
        }
        String mc = PlayerIdentity.uuidString();
        JsonObject body = new JsonObject();
        body.addProperty("action", "skin_list");
        body.addProperty("mc_uuid", mc);
        JsonObject res = CloudService.callFunction("rkb-cosmetics", body);
        if (res.has("ok") && !res.get("ok").getAsBoolean()) {
            return;
        }

        List<Remote> remotes = new ArrayList<>();
        if (res.has("sets") && res.get("sets").isJsonArray()) {
            for (JsonElement e : res.getAsJsonArray("sets")) {
                if (!e.isJsonObject()) {
                    continue;
                }
                JsonObject o = e.getAsJsonObject();
                String id = str(o, "id");
                String name = str(o, "name");
                String model = str(o, "model");
                String path = str(o, "asset_path");
                String sha = str(o, "asset_sha256");
                long size = o.has("size_bytes") ? o.get("size_bytes").getAsLong() : 0;
                if (SkinRules.validSetId(id) && SkinRules.validName(name) && SkinRules.validModel(model)
                        && AssetRules.validPath(path) && AssetRules.validSha(sha)) {
                    remotes.add(new Remote(id, name, model, path, sha, size));
                }
            }
        }
        String equipped = res.has("equipped") && res.get("equipped").isJsonObject()
                ? str(res.getAsJsonObject("equipped"), "skin_set_id") : "";

        Set<String> seenRemote = new HashSet<>();
        for (Remote r : remotes) {
            seenRemote.add(r.id());
            SkinSetStore.SkinSet local = localByRemote(r.id());
            if (local != null) {
                if (!local.sha256().equals(r.sha())) {
                    byte[] data = download(r.sha(), r.path());
                    if (data != null) {
                        SkinSetStore.replaceFromRemote(local.id(), r.name(), r.model(), r.sha(), data, 0);
                    }
                } else {
                    SkinSetStore.updateFromRemote(local.id(), r.name(), r.model(), r.sha(), r.size(), 0);
                }
                continue;
            }
            SkinSetStore.SkinSet same = localBySha(r.sha());
            if (same != null) {
                SkinSetStore.markRemote(same.id(), r.id(), 0);
                continue;
            }
            byte[] data = download(r.sha(), r.path());
            if (data != null) {
                SkinSetStore.addDownloaded(r.id(), r.name(), r.model(), r.sha(), data.length, data);
            }
        }

        // Local-only sets are uploaded once. Sets whose cloud row disappeared are left on disk and become local again.
        for (SkinSetStore.SkinSet local : SkinSetStore.state().sets()) {
            if (local.remote() && !seenRemote.contains(local.remoteId())) {
                SkinSetStore.markRemote(local.id(), "", 0);
            }
        }
        for (SkinSetStore.SkinSet local : SkinSetStore.state().sets()) {
            if (!local.remote()) {
                upload(local);
            }
        }

        SkinSetStore.SkinSet active = SkinSetStore.activeSet();
        if (active == null && !equipped.isEmpty()) {
            for (SkinSetStore.SkinSet local : SkinSetStore.state().sets()) {
                if (equipped.equals(local.remoteId())) {
                    SkinSetStore.setActiveLocally(local.id());
                    SkinSetStore.setMessage("Applied " + local.name() + " from your verified RKB account.");
                    break;
                }
            }
        }
    }

    private static SkinSetStore.SkinSet localByRemote(String remoteId) {
        for (SkinSetStore.SkinSet s : SkinSetStore.state().sets()) {
            if (remoteId.equals(s.remoteId())) {
                return s;
            }
        }
        return null;
    }

    private static SkinSetStore.SkinSet localBySha(String sha) {
        for (SkinSetStore.SkinSet s : SkinSetStore.state().sets()) {
            if (sha.equals(s.sha256())) {
                return s;
            }
        }
        return null;
    }

    private static void upload(SkinSetStore.SkinSet set) throws CloudException, IOException, InterruptedException {
        Path file = SkinSetStore.fileOf(set);
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        byte[] data = Files.readAllBytes(file);
        if (data.length > SkinRules.MAX_BYTES || !AssetRules.sha256Hex(data).equals(set.sha256())) {
            return;
        }
        JsonObject b = new JsonObject();
        b.addProperty("action", "skin_upload");
        b.addProperty("mc_uuid", PlayerIdentity.uuidString());
        b.addProperty("mc_name", PlayerIdentity.username());
        b.addProperty("name", set.name());
        b.addProperty("model", set.model());
        b.addProperty("png_base64", Base64.getEncoder().encodeToString(data));
        JsonObject res = CloudService.callFunction("rkb-cosmetics", b);
        if (res.has("ok") && res.get("ok").getAsBoolean() && res.has("id")) {
            SkinSetStore.markRemote(set.id(), res.get("id").getAsString(), 0);
        } else if (res.has("error")) {
            SkinSetStore.setMessage(errorText(res.get("error").getAsString()));
        }
    }

    static void rename(SkinSetStore.SkinSet set) {
        if (!eligible() || set == null || !set.remote()) {
            return;
        }
        CloudService.background(() -> {
            try {
                JsonObject b = new JsonObject();
                b.addProperty("action", "skin_rename");
                b.addProperty("mc_uuid", PlayerIdentity.uuidString());
                b.addProperty("id", set.remoteId());
                b.addProperty("name", set.name());
                CloudService.callFunction("rkb-cosmetics", b);
            } catch (Exception e) {
                RkbClient.LOGGER.warn("Skin rename sync failed: {}", e.toString());
            }
        });
    }

    static void delete(SkinSetStore.SkinSet set) {
        if (!eligible() || set == null || !set.remote()) {
            return;
        }
        CloudService.background(() -> {
            try {
                JsonObject b = new JsonObject();
                b.addProperty("action", "skin_delete");
                b.addProperty("mc_uuid", PlayerIdentity.uuidString());
                b.addProperty("id", set.remoteId());
                CloudService.callFunction("rkb-cosmetics", b);
            } catch (Exception e) {
                RkbClient.LOGGER.warn("Skin delete sync failed: {}", e.toString());
            }
        });
    }

    static void equip(SkinSetStore.SkinSet set) {
        if (!eligible() || set == null) {
            return;
        }
        CloudService.background(() -> {
            try {
                String remote = set.remoteId();
                if (remote == null || remote.isEmpty()) {
                    upload(set);
                    remote = SkinSetStore.byId(set.id()) == null ? "" : SkinSetStore.byId(set.id()).remoteId();
                }
                if (remote == null || remote.isEmpty()) {
                    return;
                }
                JsonObject b = new JsonObject();
                b.addProperty("action", "skin_equip");
                b.addProperty("mc_uuid", PlayerIdentity.uuidString());
                b.addProperty("id", remote);
                JsonObject res = CloudService.callFunction("rkb-cosmetics", b);
                if (res.has("ok") && !res.get("ok").getAsBoolean()) {
                    SkinSetStore.setMessage(errorText(str(res, "error")));
                }
            } catch (Exception e) {
                RkbClient.LOGGER.warn("Skin equip sync failed: {}", e.toString());
            }
        });
    }

    static void unequip() {
        if (!eligible()) {
            return;
        }
        CloudService.background(() -> {
            try {
                JsonObject b = new JsonObject();
                b.addProperty("action", "skin_unequip");
                b.addProperty("mc_uuid", PlayerIdentity.uuidString());
                CloudService.callFunction("rkb-cosmetics", b);
            } catch (Exception e) {
                RkbClient.LOGGER.warn("Skin unequip sync failed: {}", e.toString());
            }
        });
    }

    private static byte[] download(String sha, String path) throws IOException, InterruptedException {
        if (!AssetRules.validSha(sha) || !AssetRules.validPath(path)) {
            return null;
        }
        Path cache = SkinTexturesCache.cacheDir().resolve(sha + ".png");
        if (Files.isRegularFile(cache) && Files.size(cache) <= SkinRules.MAX_BYTES) {
            byte[] data = Files.readAllBytes(cache);
            if (AssetRules.sha256Hex(data).equals(sha)) {
                return data;
            }
        }
        String url = AssetRules.storageUrl(CloudStore.url(), path);
        if (url == null) {
            return null;
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12)).GET().build();
        HttpResponse<InputStream> res = CloudService.HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() != 200) {
            res.body().close();
            return null;
        }
        byte[] data;
        try (InputStream in = res.body()) {
            data = in.readNBytes(SkinRules.MAX_BYTES + 1);
        }
        if (data.length > SkinRules.MAX_BYTES || !AssetRules.sha256Hex(data).equals(sha)) {
            return null;
        }
        Files.createDirectories(SkinTexturesCache.cacheDir());
        Files.write(cache, data);
        return data;
    }

    private static String errorText(String code) {
        return switch (code) {
            case "identity_not_verified" -> "Link this Minecraft account with /rkb cloud link first.";
            case "name_taken" -> "A cloud skin set with that name already exists.";
            case "set_limit" -> "You reached the 20 skin-set limit for this Minecraft account.";
            case "not_found" -> "That cloud skin set no longer exists.";
            case "invalid_skin" -> "The server rejected that skin image.";
            default -> "Skin cloud action failed (" + code + ").";
        };
    }

    private static String str(JsonObject o, String key) {
        return o != null && o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }
}
