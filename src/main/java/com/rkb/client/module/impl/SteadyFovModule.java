package com.rkb.client.module.impl;

import com.rkb.client.module.Category;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;

public class SteadyFovModule extends OptionOverrideModule<Double> {
    public SteadyFovModule() {
        super("steady_fov", "Steady FOV", "Stops the field of view from changing when you sprint or have speed.",
                Category.VISUALS);
    }

    @Override
    protected SimpleOption<Double> option(GameOptions o) {
        return o.getFovEffectScale();
    }

    @Override
    protected Double target() {
        return 0.0;
    }
}
