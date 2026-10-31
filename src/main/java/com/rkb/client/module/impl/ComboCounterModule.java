package com.rkb.client.module.impl;

import com.rkb.client.hud.TextHudModule;
import com.rkb.client.module.Category;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.ActionResult;

import java.util.List;

/** Counts your attacks on living entities in a row; resets when you get hurt or stop for 3 seconds. */
public class ComboCounterModule extends TextHudModule {
    private int combo;
    private long lastHit;
    private int shown = -1;
    private List<String> cached = List.of();

    public ComboCounterModule() {
        super("combo_counter", "Combo Counter", "Counts your attacks in a row. Resets when you are hurt.",
                Category.HUD, "Top Left", 120, 48);
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            onAttack(world.isClient(), entity);
            return ActionResult.PASS;
        });
    }

    private void onAttack(boolean clientSide, Entity entity) {
        if (clientSide && isEnabled() && entity instanceof LivingEntity) {
            combo++;
            lastHit = System.currentTimeMillis();
        }
    }

    @Override
    protected void onTick(MinecraftClient mc) {
        if (combo > 0 && (System.currentTimeMillis() - lastHit > 3000 || (mc.player != null && mc.player.hurtTime > 0))) {
            combo = 0;
        }
    }

    @Override
    protected List<String> lines(MinecraftClient mc) {
        if (combo != shown) {
            shown = combo;
            cached = List.of("Combo: " + combo);
        }
        return cached;
    }
}
