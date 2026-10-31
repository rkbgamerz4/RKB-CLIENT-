package com.rkb.client.cloud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.rkb.client.RkbClient;
import com.rkb.client.badge.BadgeStore;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The player's saved Friends list for the name tag badge, stored by UUID in config/rkbclient-friends.json and
 * mirrored to the cloud when Cloud Sync is on. Methods that change the list must run on the game thread; the
 * render thread only calls isFriend(), which reads an immutable snapshot.
 */
public final class FriendsManager {
    public record Friend(UUID uuid, String name) {
    }

    public enum AddResult { ADDED, DUPLICATE, SELF }

    static class Entry {
        String uuid;
        String name;
    }

    static class Data {
        long updatedMs;
        boolean legacyMigrated;
        List<Entry> friends = new ArrayList<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, String> FRIENDS = new LinkedHashMap<>();
    private static volatile Set<UUID> snapshot = Set.of();
    private static long updatedMs;
    private static boolean legacyMigrated;
    private static boolean legacyRunning;

    private FriendsManager() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("rkbclient-friends.json");
    }

    // ------------------------------------------------------------------ queries

    public static boolean isFriend(UUID id) {
        return snapshot.contains(id);
    }

    public static synchronized List<Friend> list() {
        List<Friend> out = new ArrayList<>(FRIENDS.size());
        for (Map.Entry<UUID, String> e : FRIENDS.entrySet()) {
            out.add(new Friend(e.getKey(), e.getValue()));
        }
        return out;
    }

    public static synchronized int size() {
        return FRIENDS.size();
    }

    public static synchronized long updatedMs() {
        return updatedMs;
    }

    public static String displayName(Friend f) {
        return f.name() == null || f.name().isEmpty() ? UuidUtil.shortId(f.uuid()) : f.name();
    }

    // ------------------------------------------------------------------ changes (game thread)

    private static synchronized void publish() {
        snapshot = Set.copyOf(FRIENDS.keySet());
    }

    public static synchronized AddResult add(UUID id, String name) {
        UUID self = selfUuid();
        if (self != null && self.equals(id)) {
            return AddResult.SELF;
        }
        if (FRIENDS.containsKey(id)) {
            return AddResult.DUPLICATE;
        }
        FRIENDS.put(id, name == null ? "" : name);
        updatedMs = System.currentTimeMillis();
        publish();
        save();
        CloudService.onFriendsChanged();
        return AddResult.ADDED;
    }

    public static synchronized boolean remove(UUID id) {
        if (FRIENDS.remove(id) == null) {
            return false;
        }
        updatedMs = System.currentTimeMillis();
        publish();
        save();
        CloudService.onFriendsChanged();
        return true;
    }

    /** Replaces the list with the server's copy (cloud wins). Names are kept for UUIDs we already know. */
    public static synchronized void replaceFromServer(List<UUID> uuids, long serverMs) {
        Map<UUID, String> old = new LinkedHashMap<>(FRIENDS);
        FRIENDS.clear();
        for (UUID id : uuids) {
            FRIENDS.put(id, old.getOrDefault(id, ""));
        }
        updatedMs = serverMs;
        publish();
        save();
    }

    private static UUID selfUuid() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc == null || mc.getSession() == null ? null : mc.getSession().getUuidOrNull();
    }

    // ------------------------------------------------------------------ add / remove by user input

    /** feedback is always called on the game thread. */
    public static void addByInput(String input, Consumer<String> feedback) {
        MinecraftClient mc = MinecraftClient.getInstance();
        String text = input == null ? "" : input.trim();
        UUID id = UuidUtil.parse(text);
        if (id != null) {
            String name = onlineName(mc, id);
            finishAdd(id, name == null ? "" : name, feedback);
            return;
        }
        if (!UuidUtil.isValidUsername(text)) {
            feedback.accept("That is not a valid Minecraft username or UUID.");
            return;
        }
        PlayerEntity online = onlinePlayer(mc, text);
        if (online != null) {
            finishAdd(online.getUuid(), online.getName().getString(), feedback);
            return;
        }
        feedback.accept("Looking up " + text + "...");
        final String name = text;
        CloudService.background(() -> {
            try {
                MojangLookup.Result r = MojangLookup.byName(name);
                if (r == null) {
                    mc.execute(() -> feedback.accept("No Minecraft player is named " + name + "."));
                } else {
                    mc.execute(() -> finishAdd(r.uuid(), r.name(), feedback));
                }
            } catch (IOException e) {
                mc.execute(() -> feedback.accept("Could not reach Mojang. Check your connection and try again."));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public static void removeByInput(String input, Consumer<String> feedback) {
        String text = input == null ? "" : input.trim();
        UUID id = UuidUtil.parse(text);
        if (id == null) {
            for (Friend f : list()) {
                if (f.name() != null && f.name().equalsIgnoreCase(text)) {
                    id = f.uuid();
                    break;
                }
            }
        }
        if (id == null) {
            feedback.accept("No friend matches \"" + text + "\".");
            return;
        }
        feedback.accept(remove(id) ? "Removed " + text + " from your friends." : text + " is not in your friends list.");
    }

    private static void finishAdd(UUID id, String name, Consumer<String> feedback) {
        String label = name == null || name.isEmpty() ? UuidUtil.shortId(id) : name;
        switch (add(id, name)) {
            case ADDED -> feedback.accept("Added " + label + " to your friends.");
            case DUPLICATE -> feedback.accept(label + " is already in your friends list.");
            case SELF -> feedback.accept("You cannot add yourself.");
        }
    }

    private static PlayerEntity onlinePlayer(MinecraftClient mc, String name) {
        if (mc.world == null) {
            return null;
        }
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p.getName().getString().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }

    private static String onlineName(MinecraftClient mc, UUID id) {
        if (mc.world == null) {
            return null;
        }
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p.getUuid().equals(id)) {
                return p.getName().getString();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ persistence

    public static synchronized void load() {
        Path f = file();
        if (Files.isRegularFile(f)) {
            try {
                Data data = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Data.class);
                if (data != null) {
                    updatedMs = data.updatedMs;
                    legacyMigrated = data.legacyMigrated;
                    FRIENDS.clear();
                    if (data.friends != null) {
                        for (Entry e : data.friends) {
                            UUID id = e == null ? null : UuidUtil.parse(e.uuid);
                            if (id != null) {
                                FRIENDS.put(id, e.name == null ? "" : e.name);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                RkbClient.LOGGER.warn("Friends file is unreadable ({}); starting empty.", e.toString());
            }
        }
        publish();
    }

    public static synchronized void save() {
        try {
            Data data = new Data();
            data.updatedMs = updatedMs;
            data.legacyMigrated = legacyMigrated;
            for (Map.Entry<UUID, String> e : FRIENDS.entrySet()) {
                Entry en = new Entry();
                en.uuid = UuidUtil.canonical(e.getKey());
                en.name = e.getValue();
                data.friends.add(en);
            }
            Path f = file();
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling("rkbclient-friends.json.tmp");
            Files.writeString(tmp, GSON.toJson(data), StandardCharsets.UTF_8);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            RkbClient.LOGGER.error("Could not save friends: {}", e.toString());
        }
    }

    /**
     * Version 0.7 stored friends as names. When the badge is switched on, resolve those names to UUIDs once
     * (Mojang allows this; it is paced) and move them into the new list.
     */
    public static void resolveLegacyIfNeeded() {
        synchronized (FriendsManager.class) {
            if (legacyMigrated || legacyRunning) {
                return;
            }
            if (BadgeStore.list().isEmpty()) {
                legacyMigrated = true;
                save();
                return;
            }
            legacyRunning = true;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        List<String> names = BadgeStore.list();
        CloudService.background(() -> {
            boolean allDone = true;
            for (String n : names) {
                if (!UuidUtil.isValidUsername(n)) {
                    continue;
                }
                try {
                    MojangLookup.Result r = MojangLookup.byName(n);
                    if (r != null) {
                        mc.execute(() -> add(r.uuid(), r.name()));
                    }
                    Thread.sleep(400);
                } catch (IOException e) {
                    allDone = false;
                    break;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    allDone = false;
                    break;
                }
            }
            final boolean done = allDone;
            mc.execute(() -> {
                synchronized (FriendsManager.class) {
                    legacyRunning = false;
                    if (done) {
                        legacyMigrated = true;
                        save();
                    }
                }
            });
        });
    }

    static String lowerName(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
