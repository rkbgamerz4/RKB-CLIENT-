package com.rkb.client.module.impl;

import com.rkb.client.hud.TextHudModule;
import com.rkb.client.module.Category;
import net.minecraft.client.MinecraftClient;

import java.util.List;

public class SessionTimerModule extends TextHudModule {
    private static final long START = System.nanoTime();

    private long lastSecond = -1;
    private List<String> cached = List.of();

    public SessionTimerModule() {
        super("session_timer", "Session Timer", "Shows how long the game has been running.",
                Category.UTILITY, "Top Right", 4, 62);
    }

    @Override
    protected List<String> lines(MinecraftClient mc) {
        long s = (System.nanoTime() - START) / 1_000_000_000L;
        if (s != lastSecond) {
            lastSecond = s;
            cached = List.of(String.format("Session: %02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60));
        }
        return cached;
    }
}
