package com.rkb.client.module.impl;

import com.rkb.client.hud.TextHudModule;
import com.rkb.client.module.Category;
import com.rkb.client.module.setting.BoolSetting;
import net.minecraft.client.MinecraftClient;

import java.util.List;
import java.util.Locale;

public class SpeedometerModule extends TextHudModule {
    private final BoolSetting vertical = add(new BoolSetting("vertical", "Include vertical speed", false));
    private long lastKey = -1;
    private List<String> cached = List.of();

    public SpeedometerModule() {
        super("speedometer", "Speedometer", "Shows your movement speed in blocks per second.",
                Category.HUD, "Top Left", 120, 62);
    }

    @Override
    protected List<String> lines(MinecraftClient mc) {
        if (mc.player == null) {
            return List.of();
        }
        var v = mc.player.getVelocity();
        double speed = (vertical.on() ? v.length() : v.horizontalLength()) * 20.0;
        long key = Math.round(speed * 10);
        if (key != lastKey) {
            lastKey = key;
            cached = List.of(String.format(Locale.ROOT, "Speed: %.1f b/s", key / 10.0));
        }
        return cached;
    }
}
