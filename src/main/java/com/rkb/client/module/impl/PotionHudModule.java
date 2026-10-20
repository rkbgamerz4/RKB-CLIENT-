package com.rkb.client.module.impl;

import com.rkb.client.hud.TextHudModule;
import com.rkb.client.module.Category;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class PotionHudModule extends TextHudModule {
    public PotionHudModule() {
        super("potion_hud", "Potion Effects", "Lists your active status effects with their time left.",
                Category.HUD, "Top Right", 4, 76);
    }

    @Override
    protected List<String> lines(MinecraftClient mc) {
        if (mc.player == null) {
            return List.of();
        }
        Collection<StatusEffectInstance> effects = mc.player.getStatusEffects();
        if (effects.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        int n = 0;
        for (StatusEffectInstance e : effects) {
            if (n++ >= 8) {
                break;
            }
            String name = e.getEffectType().value().getName().getString();
            int amp = e.getAmplifier() + 1;
            String time;
            if (e.isInfinite()) {
                time = "inf";
            } else {
                int secs = e.getDuration() / 20;
                time = (secs / 60) + ":" + (secs % 60 < 10 ? "0" : "") + (secs % 60);
            }
            out.add(name + (amp > 1 ? " " + amp : "") + "  " + time);
        }
        return out;
    }
}
