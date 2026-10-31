package com.rkb.client.module.impl;

import com.rkb.client.module.Category;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;

public class NoViewBobbingModule extends OptionOverrideModule<Boolean> {
    public NoViewBobbingModule() {
        super("no_view_bobbing", "No View Bobbing", "Turns off camera bobbing while you walk (vanilla option).",
                Category.VISUALS);
    }

    @Override
    protected SimpleOption<Boolean> option(GameOptions o) {
        return o.getBobView();
    }

    @Override
    protected Boolean target() {
        return Boolean.FALSE;
    }
}
