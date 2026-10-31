package com.rkb.client.module.impl;

import com.rkb.client.hud.TextHudModule;
import com.rkb.client.module.Category;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;

import java.util.List;

public class HeldItemModule extends TextHudModule {
    private long lastKey = -1;
    private List<String> cached = List.of();

    public HeldItemModule() {
        super("held_item", "Held Item Info", "Shows the name, count or durability of the item in your hand.",
                Category.HUD, "Bottom Right", 6, 100);
    }

    @Override
    protected List<String> lines(MinecraftClient mc) {
        if (mc.player == null) {
            return List.of();
        }
        ItemStack st = mc.player.getMainHandStack();
        if (st.isEmpty()) {
            return List.of();
        }
        long key = System.identityHashCode(st.getItem()) * 1_000_000L + st.getCount() * 10_000L + st.getDamage();
        if (key != lastKey) {
            lastKey = key;
            String name = st.getName().getString();
            String extra = "";
            if (st.isDamageable()) {
                extra = "Durability " + (st.getMaxDamage() - st.getDamage()) + " / " + st.getMaxDamage();
            } else if (st.getCount() > 1) {
                extra = "x" + st.getCount();
            }
            cached = extra.isEmpty() ? List.of(name) : List.of(name, extra);
        }
        return cached;
    }
}
