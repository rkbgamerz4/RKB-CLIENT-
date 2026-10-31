package com.rkb.client.module.impl;

import com.rkb.client.module.Category;
import com.rkb.client.module.setting.ChoiceSetting;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.option.SimpleOption;

import java.util.List;

public class ReduceParticlesModule extends OptionOverrideModule<ParticlesMode> {
    private final ChoiceSetting mode = add(new ChoiceSetting("mode", "Particles", List.of("Decreased", "Minimal"), "Minimal"));

    public ReduceParticlesModule() {
        super("reduce_particles", "Reduce Particles", "Lowers the particle setting to save frame rate.",
                Category.PERFORMANCE);
    }

    @Override
    protected SimpleOption<ParticlesMode> option(GameOptions o) {
        return o.getParticles();
    }

    @Override
    protected ParticlesMode target() {
        return mode.index() == 0 ? ParticlesMode.DECREASED : ParticlesMode.MINIMAL;
    }
}
