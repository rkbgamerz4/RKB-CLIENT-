package com.rkb.client.module.impl;

import com.rkb.client.module.Category;
import com.rkb.client.module.setting.IntSetting;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;

public class EntityDistanceModule extends OptionOverrideModule<Double> {
    private final IntSetting percent = add(new IntSetting("percent", "Entity distance %", 50, 300, 25, 75));

    public EntityDistanceModule() {
        super("entity_distance", "Entity Distance", "Lowers how far away mobs and entities are drawn.",
                Category.PERFORMANCE);
    }

    @Override
    public boolean persistEnabled() {
        return false;
    }

    @Override
    protected SimpleOption<Double> option(GameOptions o) {
        return o.getEntityDistanceScaling();
    }

    @Override
    protected Double target() {
        return percent.value() / 100.0;
    }
}
