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

    public ClockModule() {
        super("clock", "Clock", "Shows your real-world time.", Category.UTILITY, "Top Right", 4, 30);
    }

    @Override
    protected List<String> lines(MinecraftClient mc) {
        String pattern = (hour24.on() ? "HH:mm" : "hh:mm") + (seconds.on() ? ":ss" : "") + (hour24.on() ? "" : " a");
        return List.of(LocalTime.now().format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)));
    }
}
