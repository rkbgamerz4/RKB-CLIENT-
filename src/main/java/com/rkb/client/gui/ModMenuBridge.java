package com.rkb.client.gui;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** Optional ModMenu integration. Never routes to RKB Modules. */
public final class ModMenuBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("RKB CLIENT/ModMenu");
    private static final String MOD_ID = "modmenu";

    private ModMenuBridge() {}

    public static boolean isAvailable() {
        return FabricLoader.getInstance().isModLoaded(MOD_ID);
    }

    public static void openOrWarn(Screen parent) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!isAvailable()) {
            mc.setScreen(new RkbNoticeScreen(parent, "Mods", "Fabric Mod Menu is not installed.",
                    "Install Mod Menu to browse Fabric mods. This button never opens RKB Modules."));
            return;
        }
        try {
            Screen screen = createModsScreen(parent);
            if (screen != null) {
                mc.setScreen(screen);
                return;
            }
        } catch (Throwable t) {
            LOGGER.warn("Failed to open ModMenu screen", t);
        }
        mc.setScreen(new RkbNoticeScreen(parent, "Mods", "Fabric Mod Menu could not be opened.",
                "ModMenu is installed, but its screen could not be opened safely."));
    }

    private static Screen createModsScreen(Screen parent) throws Exception {
        // ModMenu >= 6/7/8+ commonly exposes ModMenuApi.createModsScreen(Screen)
        try {
            Class<?> api = Class.forName("com.terraformersmc.modmenu.api.ModMenuApi");
            Method m = api.getMethod("createModsScreen", Screen.class);
            Object out = m.invoke(null, parent);
            if (out instanceof Screen s) return s;
        } catch (ReflectiveOperationException ignored) {
        }
        // Fallback: direct ModsScreen constructor
        try {
            Class<?> cls = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");
            Constructor<?> c = cls.getConstructor(Screen.class);
            Object out = c.newInstance(parent);
            if (out instanceof Screen s) return s;
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }
}
