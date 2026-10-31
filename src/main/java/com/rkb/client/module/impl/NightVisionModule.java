package com.rkb.client.module.impl;

import com.rkb.client.module.Category;
import com.rkb.client.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

/**
 * Gives your own client a night-vision effect. The server is never told, so it is purely visual.
 * Some servers do not allow this; check the rules. It is removed again when you turn the module off.
 */
public class NightVisionModule extends Module {
    private boolean added;

    public NightVisionModule() {
        super("night_vision", "Night Vision", "See clearly in the dark (client-side only; some servers forbid it).",
                Category.VISUALS);
    }

    @Override
    protected void onTick(MinecraftClient mc) {
        if (mc.player == null) {
            added = false;
            return;
        }
        if (!mc.player.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
            mc.player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.NIGHT_VISION, StatusEffectInstance.INFINITE, 0, false, false, false));
            added = true;
        }
    }

    @Override
    protected void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (added && mc.player != null) {
            mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
        }
        added = false;
    }
}
