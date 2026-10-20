package com.rkb.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rkb.client.RkbClient;
import com.rkb.client.module.Module;
import com.rkb.client.module.ModuleManager;
import com.rkb.client.module.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Reads and writes config/rkbclient.json and three profile slots in config/rkbclient-profiles/.
 * Never throws: a missing or malformed file falls back to defaults (a broken main file is renamed
 * to rkbclient.json.broken so nothing is lost). Disk access happens only on load, on user changes
 * and on shutdown, never per frame.
 */
public final class ConfigManager {
    public static final int PROFILE_SLOTS = 3;

    private static final int CONFIG_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConfigManager() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("rkbclient.json");
    }

    private static Path profileFile(int slot) {
        return FabricLoader.getInstance().getConfigDir().resolve("rkbclient-profiles")
                .resolve("profile" + slot + ".json");
    }

    // ------------------------------------------------------------------ main config

    public static void load() {
        Path file = file();
        if (!Files.isRegularFile(file)) {
            RkbClient.LOGGER.info("No config found; writing defaults to {}", file);
            save();
            return;
        }
        try {
            apply(parse(file));
            RkbClient.LOGGER.info("Config loaded from {}", file);
        } catch (Exception e) {
            RkbClient.LOGGER.error("Config is unreadable ({}). Using defaults.", e.toString());
            backupBroken(file);
            save();
        }
    }

    public static synchronized void save() {
        write(file());
    }

    // ------------------------------------------------------------------ profiles

    public static boolean profileExists(int slot) {
        return Files.isRegularFile(profileFile(slot));
    }

    public static boolean saveProfile(int slot) {
        return write(profileFile(slot));
    }

    /** Loads a profile live: modules are disabled, the profile applied, then lifecycle hooks re-run. */
    public static boolean loadProfile(int slot) {
        JsonObject root;
        try {
            root = parse(profileFile(slot));
        } catch (Exception e) {
            RkbClient.LOGGER.warn("Profile {} could not be loaded: {}", slot, e.toString());
            return false;
        }
        for (Module m : ModuleManager.get().all()) {
            m.setEnabled(false);
        }
        apply(root);
        for (Module m : ModuleManager.get().all()) {
            m.syncLifecycle();
        }
        save();
        return true;
    }

    public static void resetAll() {
        for (Module m : ModuleManager.get().all()) {
            m.resetToDefaults();
        }
        save();
    }

    // ------------------------------------------------------------------ internals

    private static JsonObject parse(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        JsonElement root = JsonParser.parseString(text);
        if (!root.isJsonObject()) {
            throw new IllegalStateException("root is not a JSON object");
        }
        return root.getAsJsonObject();
    }

    private static void apply(JsonObject root) {
        JsonElement modsEl = root.get("modules");
        if (modsEl == null || !modsEl.isJsonObject()) {
            return;
        }
        JsonObject mods = modsEl.getAsJsonObject();
        for (Module module : ModuleManager.get().all()) {
            JsonElement el = mods.get(module.id());
            if (el == null || !el.isJsonObject()) {
                continue;
            }
            JsonObject obj = el.getAsJsonObject();
            try {
                if (obj.has("enabled") && obj.get("enabled").isJsonPrimitive()
                        && obj.get("enabled").getAsJsonPrimitive().isBoolean()) {
                    module.restoreEnabled(obj.get("enabled").getAsBoolean());
                }
                if (obj.has("key") && obj.get("key").isJsonPrimitive()
                        && obj.get("key").getAsJsonPrimitive().isNumber()) {
                    module.setKeyCode(obj.get("key").getAsInt());
                }
                JsonElement sEl = obj.get("settings");
                if (sEl != null && sEl.isJsonObject()) {
                    JsonObject sObj = sEl.getAsJsonObject();
                    for (Setting<?> s : module.settings()) {
                        try {
                            if (sObj.has(s.id())) {
                                s.fromJson(sObj.get(s.id()));
                            }
                        } catch (Exception e) {
                            RkbClient.LOGGER.warn("Ignoring invalid setting {}.{}: {}",
                                    module.id(), s.id(), e.toString());
                        }
                    }
                }
            } catch (Exception e) {
                RkbClient.LOGGER.warn("Ignoring invalid config for module '{}': {}", module.id(), e.toString());
            }
        }
    }

    private static boolean write(Path file) {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("configVersion", CONFIG_VERSION);
            JsonObject mods = new JsonObject();
            for (Module m : ModuleManager.get().all()) {
                JsonObject o = new JsonObject();
                if (m.isToggleable() && m.persistEnabled()) {
                    o.addProperty("enabled", m.isEnabled());
                }
                o.addProperty("key", m.keyCode());
                JsonObject s = new JsonObject();
                for (Setting<?> setting : m.settings()) {
                    s.add(setting.id(), setting.toJson());
                }
                o.add("settings", s);
                mods.add(m.id(), o);
            }
            root.add("modules", mods);

            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            RkbClient.LOGGER.error("Could not save {}: {}", file, e.toString());
            return false;
        }
    }

    private static void backupBroken(Path file) {
        try {
            Files.move(file, file.resolveSibling("rkbclient.json.broken"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            RkbClient.LOGGER.warn("Could not back up broken config: {}", e.toString());
        }
    }
}
