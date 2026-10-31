package com.rkb.client.cloud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rkb.client.RkbClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only copy of your existing cosmetics-catalog Edge Function, cached in config/rkbclient-cosmetics-cache.json.
 * Metadata only: nothing here equips, renders or activates a cosmetic, and cape ownership is a separate system.
 * The response format of cosmetics-catalog was not available when this was written, so parsing is lenient (a JSON
 * array, or an object holding an array under cosmetics/items/data) and entries marked inactive are dropped.
 * Fetched only when you run /rkb cloud cosmetics.
 */
public final class CosmeticsCatalog {
    public record Entry(String id, String name, String type) {
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

    /** Background fetch; reports through CloudService.notifyPlayer. */
    public static void refresh() {
        if (CloudStore.anonKey().isEmpty()) {
            CloudService.notifyPlayer("Add your Supabase publishable key to config/rkbclient-cloud.json first.");
            return;
        }
        CloudService.background(() -> {
            try {
                String bearer = CloudStore.accessToken().isEmpty() ? CloudStore.anonKey() : CloudStore.accessToken();
                JsonObject res = CloudService.http("GET", CloudStore.url() + "/functions/v1/cosmetics-catalog", null, bearer);
                List<Entry> parsed = parse(res);
                Cache c = new Cache();
                c.fetchedMs = System.currentTimeMillis();
                c.entries = parsed;
                Path f = file();
                Files.createDirectories(f.getParent());
                Files.writeString(f, GSON.toJson(c), StandardCharsets.UTF_8);
                entries = List.copyOf(parsed);
                CloudService.notifyPlayer("Cosmetics catalog: " + parsed.size() + " active entries cached (metadata only, nothing is equipped).");
            } catch (CloudException e) {
                CloudService.notifyPlayer("Cosmetics catalog request failed: " + e.getMessage());
            } catch (IOException e) {
                CloudService.notifyPlayer("Could not reach the cosmetics catalog. Showing cached data if any.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    static List<Entry> parse(JsonObject root) {
        JsonArray arr = null;
        for (String k : new String[]{"_array", "cosmetics", "items", "data"}) {
            if (root.has(k) && root.get(k).isJsonArray()) {
                arr = root.getAsJsonArray(k);
                break;
            }
        }
        List<Entry> out = new ArrayList<>();
        if (arr == null) {
            return out;
        }
        for (JsonElement e : arr) {
            if (!e.isJsonObject()) {
                continue;
            }
            JsonObject o = e.getAsJsonObject();
            if (isFalse(o, "active") || isFalse(o, "is_active") || isFalse(o, "enabled")) {
                continue;
            }
            String id = text(o, "id", "slug", "key");
            if (id.isEmpty()) {
                continue;
            }
            out.add(new Entry(id, text(o, "name", "display_name", "title"), text(o, "type", "category", "kind")));
        }
        return out;
    }

    private static boolean isFalse(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsJsonPrimitive().isBoolean() && !o.get(key).getAsBoolean();
    }

    private static String text(JsonObject o, String... keys) {
        for (String k : keys) {
            if (o.has(k) && o.get(k).isJsonPrimitive()) {
                return o.get(k).getAsString();
            }
        }
        return "";
    }
}
