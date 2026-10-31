package com.rkb.client.badge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.rkb.client.RkbClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Player names that get the RKB "R" badge on their name tag (config/rkbclient-badges.json). */
public final class BadgeStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> NAMES = Collections.synchronizedSet(new TreeSet<>());
    private static volatile int version;

    private BadgeStore() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("rkbclient-badges.json");
    }

    /** Bumped on every change so callers can drop cached lookups. */
    public static int version() {
        return version;
    }

    public static boolean contains(String name) {
        return name != null && NAMES.contains(name.toLowerCase(Locale.ROOT));
    }

    public static boolean add(String name) {
        boolean added = NAMES.add(name.toLowerCase(Locale.ROOT));
        if (added) {
            version++;
            save();
        }
        return added;
    }

    public static boolean remove(String name) {
        boolean removed = NAMES.remove(name.toLowerCase(Locale.ROOT));
        if (removed) {
            version++;
            save();
        }
        return removed;
    }

    public static List<String> list() {
        synchronized (NAMES) {
            return new ArrayList<>(NAMES);
        }
    }

    public static void load() {
        Path file = file();
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            List<String> loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
                    new TypeToken<List<String>>() { }.getType());
            if (loaded != null) {
                for (String s : loaded) {
                    if (s != null && !s.isBlank()) {
                        NAMES.add(s.toLowerCase(Locale.ROOT));
                    }
                }
            }
        } catch (Exception e) {
            RkbClient.LOGGER.warn("Badge list is unreadable ({}); starting empty.", e.toString());
        }
    }

    private static synchronized void save() {
        try {
            Path file = file();
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("rkbclient-badges.json.tmp");
            Files.writeString(tmp, GSON.toJson(list()), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            RkbClient.LOGGER.error("Could not save badge list: {}", e.toString());
        }
    }
}
