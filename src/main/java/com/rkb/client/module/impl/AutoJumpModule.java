package com.rkb.client.module.impl;

import com.rkb.client.module.Category;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;

public class AutoJumpModule extends OptionOverrideModule<Boolean> {
    public AutoJumpModule() {
        super("auto_jump", "Auto Jump", "Jumps up one-block steps for you (vanilla Auto-Jump option).",
                Category.MOVEMENT);
    }

    @Override
    protected SimpleOption<Boolean> option(GameOptions o) {
        return o.getAutoJump();
    }

    @Override
    protected Boolean target() {
        return Boolean.TRUE;
    }
}
