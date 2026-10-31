package com.rkb.client.module.impl;

import com.rkb.client.hud.TextHudModule;
import com.rkb.client.module.Category;
import com.rkb.client.module.setting.BoolSetting;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

public class WorldInfoModule extends TextHudModule {
    private final BoolSetting weather = add(new BoolSetting("show_weather", "Show weather", true));

    public WorldInfoModule() {
        super("world_info", "World Info", "Shows the in-game day, time of day and weather.",
                Category.WORLD, "Top Right", 4, 90);
    }

    @Override
    protected List<String> lines(MinecraftClient mc) {
        if (mc.world == null) {
            return List.of();
        }
        long t = mc.world.getTimeOfDay();
        long day = t / 24000L + 1;
        int tod = (int) (t % 24000L);
        int hour = (tod / 1000 + 6) % 24;
        int minute = (tod % 1000) * 60 / 1000;
        List<String> out = new ArrayList<>(2);
        out.add("Day " + day + "  " + (hour < 10 ? "0" : "") + hour + ":" + (minute < 10 ? "0" : "") + minute);
        if (weather.on()) {
            out.add("Weather: " + (mc.world.isThundering() ? "Thunder" : mc.world.isRaining() ? "Rain" : "Clear"));
        }
        return out;
    }
}
