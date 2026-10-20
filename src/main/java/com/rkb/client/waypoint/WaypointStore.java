package com.rkb.client.waypoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.rkb.client.RkbClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Waypoints saved per server (or per singleplayer world) in config/rkbclient-waypoints.json.
 * Disk is touched only on load, on a user change and on shutdown, never per frame.
 */
public final class WaypointStore {
    public static final int[] COLORS = {
            0xFFFF4D4D, 0xFFFF9F43, 0xFFFFE04D, 0xFF55FF7F, 0xFF00A8FF, 0xFF1B60FF, 0xFFB06BFF, 0xFFFFFFFF};
    public static final String[] COLOR_NAMES = {"Red", "Orange", "Yellow", "Green", "Cyan", "Blue", "Purple", "White"};
    public static final String DEATH_NAME = "Last death";

    static class WorldData {
        String tracked = "";
        List<Waypoint> list = new ArrayList<>();
    }

    static class Data {
        Map<String, WorldData> worlds = new HashMap<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data = new Data();

    private WaypointStore() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("rkbclient-waypoints.json");
    }

    // ------------------------------------------------------------------ keys

    public static String worldKey(MinecraftClient mc) {
        try {
            ServerInfo info = mc.getCurrentServerEntry();
            if (info != null && info.address != null) {
                return "mp:" + info.address.toLowerCase(Locale.ROOT);
            }
            if (mc.getServer() != null) {
                return "sp:" + mc.getServer().getSaveProperties().getLevelName();
            }
        } catch (Throwable t) {
            RkbClient.LOGGER.warn("Could not read world name for waypoints: {}", t.toString());
        }
        return "unknown";
    }

    public static String dimKey(MinecraftClient mc) {
        return mc.world == null ? "minecraft:overworld" : mc.world.getRegistryKey().getValue().toString();
    }

    // ------------------------------------------------------------------ access

    private static WorldData world(String worldKey) {
        return data.worlds.computeIfAbsent(worldKey, k -> new WorldData());
    }

    /** Live list for one world; callers must call save() after changing it. */
    public static List<Waypoint> all(String worldKey) {
        return world(worldKey).list;
    }

    public static String tracked(String worldKey) {
        String t = world(worldKey).tracked;
        return t == null ? "" : t;
    }

    public static void setTracked(String worldKey, String name) {
        world(worldKey).tracked = name == null ? "" : name;
        save();
    }

    private static String uniqueName(List<Waypoint> list, String base) {
        String name = base;
        int i = 2;
        while (exists(list, name)) {
            name = base + " " + i++;
        }
        return name;
    }

    private static boolean exists(List<Waypoint> list, String name) {
        for (Waypoint w : list) {
            if (w.name.equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** Adds a waypoint; blank names become "Waypoint N". Returns the stored waypoint. */
    public static Waypoint add(MinecraftClient mc, String name, int x, int y, int z) {
        String key = worldKey(mc);
        List<Waypoint> list = all(key);
        String base = name == null || name.isBlank() ? "Waypoint " + (list.size() + 1) : name.trim();
        Waypoint w = new Waypoint(uniqueName(list, base), x, y, z, dimKey(mc), list.size() % COLORS.length);
        list.add(w);
        save();
        return w;
    }

    /** Replaces the single "Last death" waypoint of the current world and dimension. */
    public static void setDeath(MinecraftClient mc, int x, int y, int z) {
        String key = worldKey(mc);
        List<Waypoint> list = all(key);
        list.removeIf(w -> w.name.equals(DEATH_NAME));
        list.add(new Waypoint(DEATH_NAME, x, y, z, dimKey(mc), 0));
        save();
    }

    public static void remove(String worldKey, Waypoint w) {
        WorldData d = world(worldKey);
        d.list.remove(w);
        if (w.name.equals(d.tracked)) {
            d.tracked = "";
        }
        save();
    }

    // ------------------------------------------------------------------ disk

    public static void load() {
        Path file = file();
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            Data loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Data.class);
            if (loaded != null && loaded.worlds != null) {
                data = loaded;
            }
        } catch (Exception e) {
            RkbClient.LOGGER.error("Waypoint file is unreadable ({}); starting empty.", e.toString());
            try {
                Files.move(file, file.resolveSibling("rkbclient-waypoints.json.broken"),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // nothing more to do
            }
        }
    }

    public static synchronized void save() {
        try {
            Path file = file();
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("rkbclient-waypoints.json.tmp");
            Files.writeString(tmp, GSON.toJson(data), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            RkbClient.LOGGER.error("Could not save waypoints: {}", e.toString());
        }
    }
}
