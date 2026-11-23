package com.rkb.client.gui;

import com.rkb.client.config.ConfigManager;
import net.minecraft.client.MinecraftClient;

/** Central navigation so every RKB screen opens the same destinations. */
public final class RkbScreens {
    private RkbScreens() {
    }

    public static void home() {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.setScreen(mc.world == null ? new RkbMainMenuScreen() : new RkbMenuScreen());
    }

    /** Returns to the RKB menu from a sub-screen such as Friends or the map. */
    public static void back() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ConfigManager.save();
        mc.setScreen(mc.world == null ? new RkbMainMenuScreen() : new RkbMenuScreen());
    }

    public static void close() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ConfigManager.save();
        mc.setScreen(mc.world == null ? new RkbMainMenuScreen() : null);
    }

    public static void openNav(int index) {
        ConfigManager.save();
        MinecraftClient mc = MinecraftClient.getInstance();
        switch (index) {
            case 0 -> home();
            case 1 -> mc.setScreen(new RkbMenuScreen(RkbMenuScreen.PAGE_SETTINGS));
            case 2 -> mc.setScreen(new RkbMenuScreen(RkbMenuScreen.PAGE_PROFILES));
            case 3 -> mc.setScreen(new HudEditorScreen());
            case 4 -> mc.setScreen(new WaypointScreen());
            case 5 -> mc.setScreen(new CosmeticsScreen());
            case 6 -> mc.setScreen(new RkbMenuScreen(RkbMenuScreen.PAGE_ABOUT));
            default -> home();
        }
    }

    public static void account() {
        MinecraftClient.getInstance().setScreen(new RkbAccountScreen());
    }
}
