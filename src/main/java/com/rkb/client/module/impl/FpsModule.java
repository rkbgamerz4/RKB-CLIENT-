package com.rkb.client.module.impl;

import com.rkb.client.hud.TextHudModule;
import com.rkb.client.module.Category;
import com.rkb.client.module.setting.BoolSetting;
import net.minecraft.client.MinecraftClient;

import java.util.List;

public class FpsModule extends TextHudModule {
    private final BoolSetting label = add(new BoolSetting("show_label", "Show label", true));

    public FpsModule() {
        super("fps", "FPS Display", "Shows your current frames per second.",
                Category.HUD, "Top Left", 4, 4);
        restoreEnabled(true);
    }

    @Override
    protected List<String> lines(MinecraftClient mc) {
        int fps = mc.getCurrentFps();
        return List.of(label.on() ? "FPS: " + fps : String.valueOf(fps));
    }
}
