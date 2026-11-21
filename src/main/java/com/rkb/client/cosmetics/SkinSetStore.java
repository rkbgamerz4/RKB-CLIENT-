package com.rkb.client.cosmetics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rkb.client.RkbClient;
import com.rkb.client.cloud.CloudService;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.texture.NativeImage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Local skin-set library. Files live in config/rkbclient-skins/files and the index in
 * config/rkbclient-skins.json. Applying a set only changes this client's view of the player (and, when the identity is
 * verified, what other RKB CLIENT users see); no server restart is needed.
 */
public final class SkinSetStore {
    public record SkinSet(String id, String name, String model, String file, String sha256, long size,
                          String remoteId, long updatedMs) {
        public boolean remote() {
            return remoteId != null && !remoteId.isEmpty();
        }
    }

    public record State(List<SkinSet> sets, String activeId, String message, int version) {
        public SkinSet active() {
            for (SkinSet s : sets) {
                if (s.id().equals(activeId)) {
                    return s;
                }
            }
            return null;
        }
    }

    private static final Object LOCK = new Object();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<SkinSet> SETS = new ArrayList<>();
    private static String activeId = "";
    private static volatile String message = "";
    private static volatile int version;
    private static volatile boolean busy;
    private static volatile State snapshot = new State(List.of(), "", "", 0);
    private static boolean loaded;

    private SkinSetStore() {
    }

    public static Path root() {
        return FabricLoader.getInstance().getConfigDir().resolve("rkbclient-skins");
    }

    public static Path filesDir() {
        return root().resolve("files");
    }

    public static Path indexFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("rkbclient-skins.json");
    }

    public static Path fileOf(SkinSet set) {
        return set == null ? null : filesDir().resolve(set.file());
    }

    public static State state() {
        if (!loaded) {
            load();
        }
        return snapshot;
    }

    public static boolean busy() {
        return busy;
    }

    public static String message() {
        return message;
    }

    public static SkinSet activeSet() {
        return state().active();
    }

    public static boolean isActive(String id) {
        return id != null && id.equals(state().activeId());
    }

    public static SkinSet byId(String id) {
        if (id == null) {
            return null;
        }
        for (SkinSet s : state().sets()) {
            if (s.id().equals(id)) {
                return s;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ load / save

    public static void load() {
        synchronized (LOCK) {
            if (loaded) {
                return;
            }
            loaded = true;
            SETS.clear();
            activeId = "";
            Path f = indexFile();
            if (Files.isRegularFile(f)) {
                try {
                    JsonObject root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                    activeId = str(root, "activeId");
                    if (root.has("sets") && root.get("sets").isJsonArray()) {
                        for (JsonElement e : root.getAsJsonArray("sets")) {
                            if (!e.isJsonObject()) {
                                continue;
                            }
                            SkinSet s = parse(e.getAsJsonObject());
                            if (s != null && Files.isRegularFile(filesDir().resolve(s.file()))) {
                                SETS.add(s);
                            }
                        }
                    }
                } catch (Exception e) {
                    RkbClient.LOGGER.warn("Skin-set index is unreadable ({}). Local sets were not changed.", e.toString());
                }
            }
            sort();
            if (!activeId.isEmpty() && byIdLocked(activeId) == null) {
                activeId = "";
            }
            publish();
        }
        SkinSet active = activeSet();
        if (active != null) {
            SkinTexturesCache.ensureLocal(active.sha256(), fileOf(active));
        }
    }

    private static void save() {
        try {
            Files.createDirectories(root());
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            root.addProperty("activeId", activeId);
            JsonArray arr = new JsonArray();
            for (SkinSet s : SETS) {
                JsonObject o = new JsonObject();
                o.addProperty("id", s.id());
                o.addProperty("name", s.name());
                o.addProperty("model", s.model());
                o.addProperty("file", s.file());
                o.addProperty("sha256", s.sha256());
                o.addProperty("size", s.size());
                o.addProperty("remoteId", s.remoteId());
                o.addProperty("updatedMs", s.updatedMs());
                arr.add(o);
            }
            root.add("sets", arr);
            Path tmp = indexFile().resolveSibling("rkbclient-skins.json.tmp");
            Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
            Files.move(tmp, indexFile(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            RkbClient.LOGGER.warn("Could not save the skin-set index: {}", e.toString());
        }
    }

    private static SkinSet parse(JsonObject o) {
        String id = str(o, "id");
        String name = str(o, "name");
        String model = str(o, "model");
        String file = str(o, "file");
        String sha = str(o, "sha256");
        if (!SkinRules.validSetId(id) || !SkinRules.validName(name) || !SkinRules.validModel(model)
                || file.isEmpty() || file.contains("/") || file.contains("\\") || !AssetRules.validSha(sha)) {
            return null;
        }
        return new SkinSet(id, SkinRules.cleanName(name), model, file, sha, num(o, "size"),
                str(o, "remoteId"), num(o, "updatedMs"));
    }

    private static void publish() {
        List<SkinSet> copy = List.copyOf(SETS);
        snapshot = new State(copy, activeId, message, version);
    }

    private static void bump() {
        version++;
        publish();
    }

    private static void sort() {
        SETS.sort(Comparator.comparingLong(SkinSet::updatedMs).reversed());
    }

    private static SkinSet byIdLocked(String id) {
        for (SkinSet s : SETS) {
            if (s.id().equals(id)) {
                return s;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ user actions (safe to call from the UI)

    public static void importFiles(List<Path> paths) {
        if (paths == null || paths.isEmpty()) {
            return;
        }
        if (busy) {
            say("One moment, still saving the previous skin...");
            return;
        }
        busy = true;
        bump();
        CloudService.background(() -> {
            List<String> done = new ArrayList<>();
            for (Path p : paths) {
                String result = importOne(p, false);
                if (!result.isEmpty()) {
                    done.add(result);
                }
            }
            busy = false;
            if (!done.isEmpty()) {
                say(String.join(" ", done));
            }
            bump();
        });
    }

    public static void importPath(String input) {
        String s = input == null ? "" : input.trim();
        if (s.isEmpty()) {
            say("Paste the path to a 64x64 or 64x32 PNG first.");
            return;
        }
        if (s.startsWith("~")) {
            s = System.getProperty("user.home", "") + s.substring(1);
        }
        Path p = Path.of(s);
        if (!p.isAbsolute()) {
            Path inGame = FabricLoader.getInstance().getGameDir().resolve(s);
            Path inConfig = FabricLoader.getInstance().getConfigDir().resolve(s);
            if (Files.isRegularFile(inGame)) {
                p = inGame;
            } else if (Files.isRegularFile(inConfig)) {
                p = inConfig;
            }
        }
        if (!Files.isRegularFile(p)) {
            say("No PNG found at that path. You can also drop a PNG onto the game window.");
            return;
        }
        importFiles(List.of(p));
    }

    /** Import from a background task; returns a short status line or "". */
    private static String importOne(Path source, boolean cloudCopy) {
        try {
            long size = Files.size(source);
            if (size > SkinRules.MAX_BYTES) {
                return "That PNG is larger than 256 KB.";
            }
            return importBytes(Files.readAllBytes(source), source.getFileName() == null ? "skin.png"
                    : source.getFileName().toString());
        } catch (Exception e) {
            RkbClient.LOGGER.warn("Skin import failed: {}", e.toString());
            return "Could not read that skin: " + e.getMessage();
        }
    }

    /** Validates and stores skin bytes. Shared by local files and direct HTTPS image imports. */
    private static String importBytes(byte[] data, String fileName) {
        String problem = SkinRules.validatePng(data);
        if (!problem.isEmpty()) {
            return problem;
        }
        String sha = AssetRules.sha256Hex(data);
        synchronized (LOCK) {
            for (SkinSet s : SETS) {
                if (s.sha256().equals(sha)) {
                    return "That skin is already saved as " + s.name() + ".";
                }
            }
            if (SETS.size() >= SkinRules.MAX_SETS) {
                return "You can save at most " + SkinRules.MAX_SETS + " skin sets.";
            }
        }
        String model = detectModel(data);
        String id = UUID.randomUUID().toString();
        String name = uniqueName(nameFrom(fileName));
        String file = id + ".png";
        try {
            Files.createDirectories(filesDir());
            Files.write(filesDir().resolve(file), data);
        } catch (IOException e) {
            return "Could not save that skin: " + e.getMessage();
        }
        SkinSet set = new SkinSet(id, name, model, file, sha, data.length, "", System.currentTimeMillis());
        synchronized (LOCK) {
            SETS.add(0, set);
            sort();
            if (activeId.isEmpty()) {
                activeId = id;
            }
            save();
            bump();
        }
        SkinTexturesCache.ensureBytes(sha, data);
        PlayerTextures.clearResults();
        return "Saved " + name + " (" + SkinRules.label(model) + ").";
    }

    /**
     * Imports a direct HTTPS PNG URL into the local library. HTTPS only, no redirects, 256 KB limit and the same PNG
     * validation as a local file. Nothing is sent to Supabase or stored outside the local skin library.
     */
    public static void importUrl(String input) {
        String s = input == null ? "" : input.trim();
        URI uri;
        try {
            uri = URI.create(s);
        } catch (RuntimeException e) {
            say("That is not a valid image URL.");
            return;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isEmpty()
                || uri.getUserInfo() != null) {
            say("Use a direct https:// PNG URL.");
            return;
        }
        if (busy) {
            say("One moment, still saving the previous skin...");
            return;
        }
        busy = true;
        bump();
        CloudService.background(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(12)).GET().build();
                HttpResponse<InputStream> res = CloudService.HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
                if (res.statusCode() != 200) {
                    say("The image server returned HTTP " + res.statusCode() + ".");
                    return;
                }
                byte[] data;
                try (InputStream in = res.body()) {
                    data = in.readNBytes(SkinRules.MAX_BYTES + 1);
                }
                if (data.length > SkinRules.MAX_BYTES) {
                    say("That image is larger than 256 KB.");
                    return;
                }
                String path = uri.getPath() == null ? "skin.png" : uri.getPath();
                String fileName = path.substring(path.lastIndexOf('/') + 1);
                say(importBytes(data, fileName.isEmpty() ? "skin.png" : fileName));
            } catch (Exception e) {
                RkbClient.LOGGER.warn("Skin URL import failed: {}", e.toString());
                say("Could not import that image URL.");
            } finally {
                busy = false;
                bump();
            }
        });
    }

    /** Used by SkinCloudSync after a verified remote download. */
    static SkinSet addDownloaded(String remoteId, String name, String model, String sha, long size, byte[] data) {
        if (data == null || data.length > SkinRules.MAX_BYTES || !AssetRules.validSha(sha)
                || !AssetRules.sha256Hex(data).equals(sha)) {
            return null;
        }
        String problem = SkinRules.validatePng(data);
        if (!problem.isEmpty()) {
            return null;
        }
        synchronized (LOCK) {
            for (SkinSet s : SETS) {
                if (s.sha256().equals(sha)) {
                    SkinSet linked = new SkinSet(s.id(), s.name(), s.model(), s.file(), s.sha256(), s.size(),
                            remoteId == null ? "" : remoteId, s.updatedMs());
                    replaceLocked(linked);
                    save();
                    bump();
                    SkinTexturesCache.ensureLocal(linked.sha256(), fileOf(linked));
                    return linked;
                }
            }
            if (SETS.size() >= SkinRules.MAX_SETS) {
                return null;
            }
            String id = UUID.randomUUID().toString();
            String file = id + ".png";
            try {
                Files.createDirectories(filesDir());
                Files.write(filesDir().resolve(file), data);
            } catch (IOException e) {
                return null;
            }
            SkinSet set = new SkinSet(id, uniqueName(SkinRules.cleanName(name)), model, file, sha, size,
                    remoteId == null ? "" : remoteId, System.currentTimeMillis());
            SETS.add(0, set);
            sort();
            save();
            bump();
            SkinTexturesCache.ensureBytes(sha, data);
            return set;
        }
    }

    private static void replaceLocked(SkinSet set) {
        for (int i = 0; i < SETS.size(); i++) {
            if (SETS.get(i).id().equals(set.id())) {
                SETS.set(i, set);
                return;
            }
        }
    }

    public static void rename(String id, String newName) {
        String name = SkinRules.cleanName(newName);
        if (!SkinRules.validName(name)) {
            say("Use 1 to 32 letters, numbers, spaces, dots, dashes or underscores.");
            return;
        }
        SkinSet old = byId(id);
        if (old == null) {
            return;
        }
        SkinSet updated;
        synchronized (LOCK) {
            for (SkinSet s : SETS) {
                if (!s.id().equals(id) && s.name().equalsIgnoreCase(name)) {
                    say("You already have a skin set named " + name + ".");
                    return;
                }
            }
            updated = new SkinSet(old.id(), name, old.model(), old.file(), old.sha256(), old.size(),
                    old.remoteId(), System.currentTimeMillis());
            replaceLocked(updated);
            save();
            bump();
        }
        SkinCloudSync.rename(updated);
        say("Renamed to " + name + ".");
    }

    public static void delete(String id) {
        SkinSet old = byId(id);
        if (old == null) {
            return;
        }
        synchronized (LOCK) {
            SETS.removeIf(s -> s.id().equals(id));
            if (activeId.equals(id)) {
                activeId = SETS.isEmpty() ? "" : SETS.get(0).id();
            }
            save();
            bump();
        }
        try {
            Files.deleteIfExists(fileOf(old));
        } catch (IOException ignored) {
            // the index is already correct
        }
        SkinCloudSync.delete(old);
        PlayerTextures.clearResults();
        say("Deleted " + old.name() + ".");
    }

    public static void activate(String id) {
        SkinSet set = byId(id);
        if (set == null) {
            say("That skin set no longer exists.");
            return;
        }
        synchronized (LOCK) {
            activeId = id;
            save();
            bump();
        }
        SkinTexturesCache.retry(set.sha256());
        SkinTexturesCache.ensureLocal(set.sha256(), fileOf(set));
        PlayerTextures.clearResults();
        SkinCloudSync.equip(set);
        say("Applied " + set.name() + ". Switch to another set at any time.");
    }

    public static void deactivate() {
        synchronized (LOCK) {
            activeId = "";
            save();
            bump();
        }
        PlayerTextures.clearResults();
        SkinCloudSync.unequip();
        say("Removed your RKB skin. Your normal Minecraft skin is shown again.");
    }

    /** The Save and Apply button applies the selected set on this client; V2 has no skin upload action. */
    public static void saveAndApply() {
        SkinSet active = activeSet();
        if (active == null) {
            say("Save or select a skin set first.");
            return;
        }
        SkinTexturesCache.retry(active.sha256());
        SkinTexturesCache.ensureLocal(active.sha256(), fileOf(active));
        PlayerTextures.clearResults();
        say("Saved and applied " + active.name() + " locally. Skin cloud upload is not available in V2.");
    }

    /** New RKB account or Minecraft UUID: keep the files, but forget remote ids from the previous identity. */
    public static void onAccountChanged() {
        synchronized (LOCK) {
            for (int i = 0; i < SETS.size(); i++) {
                SkinSet s = SETS.get(i);
                if (s.remote()) {
                    SETS.set(i, new SkinSet(s.id(), s.name(), s.model(), s.file(), s.sha256(), s.size(), "", s.updatedMs()));
                }
            }
            save();
            bump();
        }
        SkinCloudSync.refresh();
    }

    public static void onIdentityLinked() {
        SkinCloudSync.refresh();
    }

    // ------------------------------------------------------------------ helpers for SkinCloudSync

    static void markRemote(String localId, String remoteId, long updatedMs) {
        synchronized (LOCK) {
            SkinSet s = byIdLocked(localId);
            if (s == null) {
                return;
            }
            replaceLocked(new SkinSet(s.id(), s.name(), s.model(), s.file(), s.sha256(), s.size(),
                    remoteId == null ? "" : remoteId, updatedMs <= 0 ? s.updatedMs() : updatedMs));
            save();
            bump();
        }
    }

    static void updateFromRemote(String localId, String name, String model, String sha, long size, long updatedMs) {
        synchronized (LOCK) {
            SkinSet s = byIdLocked(localId);
            if (s == null) {
                return;
            }
            replaceLocked(new SkinSet(s.id(), SkinRules.validName(name) ? name : s.name(), model, s.file(), sha,
                    size, s.remoteId(), updatedMs <= 0 ? s.updatedMs() : updatedMs));
            save();
            bump();
        }
    }

    /** Replaces a local set with a changed cloud version, writing the new PNG before the index points at it. */
    static void replaceFromRemote(String localId, String name, String model, String sha, byte[] data, long updatedMs) {
        if (data == null || !AssetRules.validSha(sha) || !AssetRules.sha256Hex(data).equals(sha)) {
            return;
        }
        String problem = SkinRules.validatePng(data);
        if (!problem.isEmpty()) {
            return;
        }
        SkinSet written;
        synchronized (LOCK) {
            SkinSet s = byIdLocked(localId);
            if (s == null) {
                return;
            }
            try {
                Files.createDirectories(filesDir());
                Files.write(fileOf(s), data);
            } catch (IOException e) {
                RkbClient.LOGGER.warn("Could not store cloud skin {}: {}", sha.substring(0, 8), e.toString());
                return;
            }
            written = new SkinSet(s.id(), SkinRules.validName(name) ? name : s.name(), model, s.file(), sha,
                    data.length, s.remoteId(), updatedMs <= 0 ? s.updatedMs() : updatedMs);
            replaceLocked(written);
            save();
            bump();
        }
        SkinTexturesCache.retry(sha);
        SkinTexturesCache.ensureBytes(sha, data);
        if (written.id().equals(activeId)) {
            PlayerTextures.clearResults();
        }
    }

    static void setActiveLocally(String localId) {
        synchronized (LOCK) {
            if (byIdLocked(localId) == null) {
                return;
            }
            activeId = localId;
            save();
            bump();
        }
        SkinSet set = byId(localId);
        if (set != null) {
            SkinTexturesCache.ensureLocal(set.sha256(), fileOf(set));
        }
        PlayerTextures.clearResults();
    }

    static String uniqueName(String base) {
        String name = SkinRules.cleanName(base);
        if (!SkinRules.validName(name)) {
            name = "My Skin";
        }
        if (name.length() > SkinRules.NAME_MAX) {
            name = name.substring(0, SkinRules.NAME_MAX).trim();
        }
        synchronized (LOCK) {
            String candidate = name;
            int n = 2;
            while (hasNameLocked(candidate)) {
                String suffix = " " + n++;
                int room = SkinRules.NAME_MAX - suffix.length();
                candidate = (name.length() > room ? name.substring(0, room) : name) + suffix;
            }
            return candidate;
        }
    }

    private static boolean hasNameLocked(String name) {
        for (SkinSet s : SETS) {
            if (s.name().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private static String nameFrom(String fileName) {
        String n = fileName == null ? "Skin" : fileName;
        int dot = n.toLowerCase(Locale.ROOT).lastIndexOf(".png");
        if (dot > 0) {
            n = n.substring(0, dot);
        }
        n = n.replace('_', ' ').trim();
        return n.isEmpty() ? "Skin" : n;
    }

    private static String detectModel(byte[] data) {
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(data));
            try {
                return SkinPng.detectModel(image);
            } finally {
                image.close();
            }
        } catch (IOException e) {
            return SkinRules.MODEL_CLASSIC;
        }
    }

    private static void say(String text) {
        message = text;
        version++;
        publish();
    }

    static void setMessage(String text) {
        say(text);
    }

    private static String str(JsonObject o, String key) {
        return o != null && o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }

    private static long num(JsonObject o, String key) {
        try {
            return o != null && o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsLong() : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** Called from the client tick to refresh a cached state snapshot cheaply. */
    public static void tick() {
        if (!loaded) {
            load();
        }
    }

    public static void scanInbox() {
        Path inbox = root().resolve("inbox");
        try {
            Files.createDirectories(inbox);
            try (var stream = Files.list(inbox)) {
                List<Path> pngs = stream
                        .filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                        .toList();
                if (pngs.isEmpty()) {
                    say("No PNG files in " + inbox.toAbsolutePath());
                    return;
                }
                importFiles(pngs);
            }
        } catch (IOException e) {
            say("Could not read the inbox: " + e.getMessage());
        }
    }

    public static void openFolderHint() {
        say("Skin folder: " + filesDir().toAbsolutePath());
    }

    /** Kept for the UI so it can show a count without Minecraft classes in tests. */
    public static int count() {
        return state().sets().size();
    }

    public static String activeLabel() {
        SkinSet s = activeSet();
        return s == null ? "None" : s.name();
    }

    /** The version field lets the screen repaint only when something changed. */
    public static int version() {
        return version;
    }
}
