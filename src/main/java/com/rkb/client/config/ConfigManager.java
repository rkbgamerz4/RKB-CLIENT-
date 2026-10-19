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
 * Reads and writes config/rkbclient.json. Never throws: a missing or malformed file falls back
 * to defaults (a broken file is renamed to rkbclient.json.broken so nothing is lost).
 */
public final class ConfigManager {
    private static final int CONFIG_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConfigManager() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("rkbclient.json");
    }

    public static void load() {
        Path file = file();
        if (!Files.isRegularFile(file)) {
            RkbClient.LOGGER.info("No config found; writing defaults to {}", file);
            save();
            return;
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            JsonElement root = JsonParser.parseString(text);
            if (!root.isJsonObject()) {
                throw new IllegalStateException("root is not a JSON object");
            }
            apply(root.getAsJsonObject());
            RkbClient.LOGGER.info("Config loaded from {}", file);
        } catch (Exception e) {
            RkbClient.LOGGER.error("Config is unreadable ({}). Using defaults.", e.toString());
            backupBroken(file);
            save();
        }
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

    public static synchronized void save() {
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

            Path file = file();
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("rkbclient.json.tmp");
            Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            RkbClient.LOGGER.error("Could not save config: {}", e.toString());
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
