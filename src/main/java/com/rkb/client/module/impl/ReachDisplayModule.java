package com.rkb.client.module.impl;

import com.rkb.client.hud.TextHudModule;
import com.rkb.client.module.Category;
import net.minecraft.client.MinecraftClient;

import java.util.List;
import java.util.Locale;

/** Distance to the entity under your crosshair. Information only. */
public class ReachDisplayModule extends TextHudModule {
    private long lastKey = -1;
    private List<String> cached = List.of();

    public ReachDisplayModule() {
        super("target_distance", "Target Distance", "Shows how far away the entity under your crosshair is.",
                Category.HUD, "Top Left", 120, 34);
    }

    @Override
    protected List<String> lines(MinecraftClient mc) {
        if (mc.targetedEntity == null || mc.player == null) {
            return List.of();
        }
        long key = Math.round(mc.player.distanceTo(mc.targetedEntity) * 10);
        if (key != lastKey) {
            lastKey = key;
            cached = List.of("Distance: " + String.format(Locale.ROOT, "%.1f", key / 10.0));
        }
        return cached;
    }
}
