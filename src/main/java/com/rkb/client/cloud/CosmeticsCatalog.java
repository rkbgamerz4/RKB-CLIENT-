package com.rkb.client.cloud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.rkb.client.RkbClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Metadata-only local cache of the V2 {@code catalog} action. Never equips or grants anything. */
public final class CosmeticsCatalog {
    public record Entry(String id, String name, String type, boolean active, boolean assetReady) {
    }

    static class Cache {
        long fetchedMs;
        List<Entry> entries = new ArrayList<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile List<Entry> entries = List.of();

    private CosmeticsCatalog() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("rkbclient-cosmetics-cache.json");
    }

    /** Parses a V2 catalog response into metadata-only entries used by tests and the cloud command. */
    public static List<Entry> parse(JsonObject root) {
        List<Entry> out = new ArrayList<>();
        for (CosmeticsV2Api.CatalogItem item : CosmeticsV2Api.parseCatalog(root)) {
            out.add(new Entry(item.id(), item.name(), item.type(), item.active(), item.assetReady()));
        }
        return List.copyOf(out);
    }

    public static List<Entry> cached() {
        if (entries.isEmpty() && Files.isRegularFile(file())) {
            try {
                Cache c = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), Cache.class);
                if (c != null && c.entries != null) {
                    entries = List.copyOf(c.entries);
                }
            } catch (Exception e) {
                RkbClient.LOGGER.warn("Cosmetics cache is unreadable ({}).", e.toString());
            }
        }
        return entries;
    }

    /** Background V2 catalog fetch; reports through CloudService.notifyPlayer. */
    public static void refresh() {
        if (!CloudService.cloudAllowed()) {
            CloudService.notifyPlayer("Sign in with a publishable key first.");
            return;
        }
        CloudService.background(() -> {
            try {
                JsonObject res = CloudService.callV2(CosmeticsV2Api.request(CosmeticsV2Api.CATALOG));
                if (!CosmeticsV2Api.ok(res)) {
                    CloudService.notifyPlayer("Catalog request failed: " + CosmeticsV2Api.error(res));
                    return;
                }
                List<Entry> parsed = new ArrayList<>();
                for (CosmeticsV2Api.CatalogItem i : CosmeticsV2Api.parseCatalog(res)) {
                    parsed.add(new Entry(i.id(), i.name(), i.type(), i.active(), i.assetReady()));
                }
                Cache c = new Cache();
                c.fetchedMs = System.currentTimeMillis();
                c.entries = parsed;
                Path f = file();
                Files.createDirectories(f.getParent());
                Files.writeString(f, GSON.toJson(c), StandardCharsets.UTF_8);
                entries = List.copyOf(parsed);
                CloudService.notifyPlayer("V2 catalog: " + parsed.size() + " active entries cached (metadata only).");
            } catch (CloudException e) {
                CloudService.notifyPlayer("Catalog request failed: " + e.getMessage());
            } catch (IOException e) {
                CloudService.notifyPlayer("Could not reach the catalog. Showing cached data if any.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
}
