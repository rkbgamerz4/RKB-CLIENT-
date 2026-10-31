package com.rkb.client.module.impl;

import com.rkb.client.hud.TextHudModule;
import com.rkb.client.module.Category;
import com.rkb.client.module.setting.BoolSetting;
import net.minecraft.client.MinecraftClient;

import java.util.List;

public class MemoryModule extends TextHudModule {
    private final BoolSetting showMax = add(new BoolSetting("show_max", "Show maximum", true));
    private long lastRefresh;
    private boolean lastShowMax;
    private List<String> cached = List.of();

    public MemoryModule() {
        super("memory", "Memory Usage", "Shows how much Java heap memory the game is using.",
                Category.PERFORMANCE, "Top Left", 4, 46);
    }

    @Override
    protected List<String> lines(MinecraftClient mc) {
        long now = System.nanoTime();
        boolean sm = showMax.on();
        if (now - lastRefresh > 500_000_000L || sm != lastShowMax) {
            lastRefresh = now;
            lastShowMax = sm;
            Runtime rt = Runtime.getRuntime();
            long usedMb = (rt.totalMemory() - rt.freeMemory()) / 1048576L;
            long maxMb = rt.maxMemory() / 1048576L;
            cached = List.of(sm ? "RAM: " + usedMb + " / " + maxMb + " MB" : "RAM: " + usedMb + " MB");
        }
        return cached;
    }
}
