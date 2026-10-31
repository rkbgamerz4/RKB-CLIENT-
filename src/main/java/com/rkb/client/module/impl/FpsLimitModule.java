package com.rkb.client.module.impl;

import com.rkb.client.module.Category;
import com.rkb.client.module.setting.IntSetting;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;

public class FpsLimitModule extends OptionOverrideModule<Integer> {
    private final IntSetting limit = add(new IntSetting("max_fps", "Max FPS (260 = unlimited)", 20, 260, 10, 60));

    public FpsLimitModule() {
        super("fps_limit", "FPS Limit", "Caps the frame rate to save battery and heat.", Category.PERFORMANCE);
    }

    @Override
    public boolean persistEnabled() {
        return false;
    }

    @Override
    protected SimpleOption<Integer> option(GameOptions o) {
        return o.getMaxFps();
    }

    @Override
    protected Integer target() {
        return limit.value();
    }
}
