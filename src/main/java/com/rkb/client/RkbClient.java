package com.rkb.client;

import com.rkb.client.config.ConfigManager;
import com.rkb.client.gui.RkbMenuScreen;
import com.rkb.client.hud.HudRenderer;
import com.rkb.client.input.KeybindRegistry;
import com.rkb.client.module.ModuleManager;
import com.rkb.client.waypoint.WaypointStore;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
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
        HudRenderer.init();

        ClientTickEvents.END_CLIENT_TICK.register(RkbClient::onTick);

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            ConfigManager.save();
            WaypointStore.save();
            ModuleManager.get().shutdown(client);
        });

        // "/rkb" opens the menu; useful on Android where Right Shift may be unavailable.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("rkb").executes(context -> {
                    requestMenu();
                    return 1;
                })));

        LOGGER.info("{} initialised ({} modules). Open the menu with Right Shift or /rkb.",
                NAME, ModuleManager.get().all().size());
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
