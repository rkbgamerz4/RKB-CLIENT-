package com.rkb.client.module.impl;

import com.rkb.client.hud.TextHudModule;
import com.rkb.client.module.Category;
import com.rkb.client.module.setting.BoolSetting;
import net.minecraft.client.MinecraftClient;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class ClockModule extends TextHudModule {
    private final BoolSetting hour24 = add(new BoolSetting("hour_24", "24-hour clock", true));
    private final BoolSetting seconds = add(new BoolSetting("show_seconds", "Show seconds", false));

    private long lastKey = Long.MIN_VALUE;
    private List<String> cached = List.of();

    public ClockModule() {
        super("clock", "Clock", "Shows your real-world time.", Category.UTILITY, "Top Right", 4, 30);
    }

    @Override
    protected List<String> lines(MinecraftClient mc) {
        boolean h24 = hour24.on();
        boolean sec = seconds.on();
        long unit = System.currentTimeMillis() / (sec ? 1000L : 60000L);
        long key = unit * 4 + (h24 ? 1 : 0) + (sec ? 2 : 0);
        if (key != lastKey) {
            lastKey = key;
            String pattern = (h24 ? "HH:mm" : "hh:mm") + (sec ? ":ss" : "") + (h24 ? "" : " a");
            cached = List.of(LocalTime.now().format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)));
        }
        return cached;
    }
}
