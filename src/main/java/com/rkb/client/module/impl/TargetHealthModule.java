package com.rkb.client.module.impl;

import com.rkb.client.hud.TextHudModule;
import com.rkb.client.module.Category;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

import java.util.List;
import java.util.Locale;

/** Health of the mob or player under your crosshair (only what you are looking at, no radar). */
public class TargetHealthModule extends TextHudModule {
    private long lastKey = -1;
    private List<String> cached = List.of();

    public TargetHealthModule() {
        super("target_health", "Target Health", "Shows the health of the entity you are looking at.",
                Category.HUD, "Top Left", 120, 4);
    }

    @Override
    protected List<String> lines(MinecraftClient mc) {
        Entity e = mc.targetedEntity;
        if (!(e instanceof LivingEntity target) || !target.isAlive()) {
            return List.of();
        }
        float hp = target.getHealth();
        long key = System.identityHashCode(target) * 100000L + Math.round(hp * 10);
        if (key != lastKey) {
            lastKey = key;
            cached = List.of(target.getName().getString(),
                    "HP " + String.format(Locale.ROOT, "%.1f", hp) + " / "
                            + String.format(Locale.ROOT, "%.1f", target.getMaxHealth()));
        }
        return cached;
    }
}
