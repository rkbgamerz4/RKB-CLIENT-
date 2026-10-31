package com.rkb.client.module.impl;

import com.rkb.client.module.Category;
import com.rkb.client.module.setting.IntSetting;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;

public class SensitivityModule extends OptionOverrideModule<Double> {
    private final IntSetting percent = add(new IntSetting("percent", "Sensitivity %", 10, 200, 10, 100));

    public SensitivityModule() {
        super("sensitivity", "Mouse Sensitivity", "Sets your look sensitivity from here (vanilla option).",
                Category.CONTROLS);
    }

    @Override
    public boolean persistEnabled() {
        return false;
    }

    @Override
    protected SimpleOption<Double> option(GameOptions o) {
        return o.getMouseSensitivity();
    }

    @Override
    protected Double target() {
        return percent.value() / 200.0;
    }
}
