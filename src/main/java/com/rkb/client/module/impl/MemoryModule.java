package com.rkb.client.module.impl;

import com.rkb.client.hud.TextHudModule;
import com.rkb.client.module.Category;
import com.rkb.client.module.setting.BoolSetting;
import net.minecraft.client.MinecraftClient;

import java.util.List;

public class MemoryModule extends TextHudModule {
    private final BoolSetting showMax = add(new BoolSetting("show_max", "Show maximum", true));

    public MemoryModule() {
        super("memory", "Memory Usage", "Shows how much Java heap memory the game is using.",
                Category.PERFORMANCE, "Top Left", 4, 46);
    }

    @Override
    protected List<String> lines(MinecraftClient mc) {
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / 1048576L;
        long maxMb = rt.maxMemory() / 1048576L;
        return List.of(showMax.on() ? "RAM: " + usedMb + " / " + maxMb + " MB" : "RAM: " + usedMb + " MB");
    }
}
