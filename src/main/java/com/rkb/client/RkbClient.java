package com.rkb.client;

import com.rkb.client.badge.BadgeStore;
import com.rkb.client.cloud.CloudService;
import com.rkb.client.cloud.CloudStore;
import com.rkb.client.cloud.FriendsManager;
import com.rkb.client.config.ConfigManager;
import com.rkb.client.gui.RkbMenuScreen;
import com.rkb.client.hud.HudRenderer;
import com.rkb.client.input.KeybindRegistry;
import com.rkb.client.module.ModuleManager;
import com.rkb.client.waypoint.WaypointStore;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client entrypoint for RKB CLIENT. */
public class RkbClient implements ClientModInitializer {
    public static final String MOD_ID = "rkbclient";
    public static final String NAME = "RKB CLIENT";
    public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

    private static boolean started = false;
    private static boolean menuRequested = false;

    @Override
    public void onInitializeClient() {
        ModuleManager.init();
        KeybindRegistry.registerAll(ModuleManager.get());
        ConfigManager.load();
        WaypointStore.load();
        BadgeStore.load();
        CloudStore.load();
        FriendsManager.load();
        HudRenderer.init();

        ClientTickEvents.END_CLIENT_TICK.register(RkbClient::onTick);

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            // stop every background cloud task first, so switching modules off at shutdown never triggers a sync
            CloudService.shutdown();
            ConfigManager.save();
            WaypointStore.save();
            FriendsManager.save();
            ModuleManager.get().shutdown(client);
        });

        RkbCommands.register();

        LOGGER.info("{} initialised ({} modules). Open the menu with Right Shift or /rkb.",
                NAME, ModuleManager.get().all().size());
    }

    private static java.util.function.Supplier<net.minecraft.client.gui.screen.Screen> pendingScreen;

    /** Opens a screen on the next tick (a screen opened straight from a chat command would be closed with the chat). */
    public static void requestScreen(java.util.function.Supplier<net.minecraft.client.gui.screen.Screen> screen) {
        pendingScreen = screen;
    }

    public static void requestMenu() {
        menuRequested = true;
    }

    private static int tickErrors = 0;

    private static void onTick(MinecraftClient mc) {
        if (mc.options == null) {
            return;
        }
        try {
            ModuleManager manager = ModuleManager.get();
            if (!started) {
                started = true;
                KeybindRegistry.reapplyPinnedKeys();
                manager.activateLoaded(mc);
            }
            manager.tick(mc);
            KeybindRegistry.tick(mc);
            if (pendingScreen != null && mc.currentScreen == null) {
                var supplier = pendingScreen;
                pendingScreen = null;
                mc.setScreen(supplier.get());
            }
            if (menuRequested && mc.currentScreen == null) {
                menuRequested = false;
                mc.setScreen(new RkbMenuScreen());
            }
        } catch (Throwable t) {
            menuRequested = false;
            if (tickErrors++ < 5) {
                LOGGER.error("RKB CLIENT tick failed (contained, game continues)", t);
            }
        }
    }
}
