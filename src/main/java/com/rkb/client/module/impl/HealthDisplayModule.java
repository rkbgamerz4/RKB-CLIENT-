package com.rkb.client.module.impl;

import com.rkb.client.hud.TextHudModule;
import com.rkb.client.module.Category;
import com.rkb.client.module.setting.BoolSetting;
import net.minecraft.client.MinecraftClient;

import java.util.List;
import java.util.Locale;

public class HealthDisplayModule extends TextHudModule {
    private final BoolSetting absorption = add(new BoolSetting("show_absorption", "Show absorption", true));
    private long lastKey = -1;
    private List<String> cached = List.of();

    public HealthDisplayModule() {
        super("health_display", "Health Display", "Shows your health as a number.", Category.HUD, "Bottom Left", 6, 60);
    }

    @Override
    protected List<String> lines(MinecraftClient mc) {
        if (mc.player == null) {
            return List.of();
        }
        float h = mc.player.getHealth();
        float a = absorption.on() ? mc.player.getAbsorptionAmount() : 0f;
        long key = Math.round(h * 10) * 10000L + Math.round(a * 10);
        if (key != lastKey) {
            lastKey = key;
            String s = "Health: " + String.format(Locale.ROOT, "%.1f", h)
                    + (a > 0 ? " +" + String.format(Locale.ROOT, "%.1f", a) : "");
            cached = List.of(s);
        }
        return cached;
    }
}
