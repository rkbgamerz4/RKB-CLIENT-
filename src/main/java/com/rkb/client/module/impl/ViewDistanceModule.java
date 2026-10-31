package com.rkb.client.module.impl;

import com.rkb.client.module.Category;
import com.rkb.client.module.setting.IntSetting;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;

public class ViewDistanceModule extends OptionOverrideModule<Integer> {
    private final IntSetting distance = add(new IntSetting("distance", "Chunks", 2, 32, 1, 8));

    public ViewDistanceModule() {
        super("view_distance", "View Distance", "Caps your render distance. Lower is faster on weak devices.",
                Category.PERFORMANCE);
    }

    @Override
    public boolean persistEnabled() {
        return false;
    }

    @Override
    protected SimpleOption<Integer> option(GameOptions o) {
        return o.getViewDistance();
    }

    @Override
    protected Integer target() {
        return distance.value();
    }
}
