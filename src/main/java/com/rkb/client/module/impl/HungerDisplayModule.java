package com.rkb.client.module.impl;

import com.rkb.client.hud.TextHudModule;
import com.rkb.client.module.Category;
import com.rkb.client.module.setting.BoolSetting;
import net.minecraft.client.MinecraftClient;

import java.util.List;
import java.util.Locale;

public class HungerDisplayModule extends TextHudModule {
    private final BoolSetting saturation = add(new BoolSetting("show_saturation", "Show saturation", true));
    private long lastKey = -1;
    private List<String> cached = List.of();

    public HungerDisplayModule() {
        super("hunger_display", "Hunger Display", "Shows your food level and saturation.",
                Category.HUD, "Bottom Left", 6, 74);
    }

    @Override
    protected List<String> lines(MinecraftClient mc) {
        if (mc.player == null) {
            return List.of();
        }
        int food = mc.player.getHungerManager().getFoodLevel();
        float sat = mc.player.getHungerManager().getSaturationLevel();
        long key = food * 100000L + Math.round(sat * 10) + (saturation.on() ? 1L << 40 : 0);
        if (key != lastKey) {
            lastKey = key;
            cached = saturation.on()
                    ? List.of("Hunger: " + food, "Saturation: " + String.format(Locale.ROOT, "%.1f", sat))
                    : List.of("Hunger: " + food);
        }
        return cached;
    }
}
