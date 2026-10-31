package com.rkb.client.module.impl;

import com.rkb.client.gui.Theme;
import com.rkb.client.hud.HudModule;
import com.rkb.client.module.Category;
import com.rkb.client.module.setting.BoolSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

public class ArmorHudModule extends HudModule {
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final int ROW = 18;

    private final BoolSetting percent = add(new BoolSetting("show_percent", "Show durability %", true));
    private final ItemStack[] stacks = new ItemStack[SLOTS.length];
    private int count;

    public ArmorHudModule() {
        super("armor_hud", "Armor Status", "Shows the armor you are wearing and its durability.",
                Category.HUD, "Bottom Right", 6, 40);
    }

    @Override
    protected void prepare(MinecraftClient mc) {
        count = 0;
        if (mc.player == null) {
            return;
        }
        for (int i = 0; i < SLOTS.length; i++) {
            ItemStack st = mc.player.getEquippedStack(SLOTS[i]);
            if (!st.isEmpty()) {
                stacks[count++] = st;
            }
        }
    }

    @Override
    protected int width(MinecraftClient mc) {
        if (count == 0) {
            return 0;
        }
        return 20 + (percent.on() ? mc.textRenderer.getWidth("100%") + 4 : 0);
    }

    @Override
    protected int height(MinecraftClient mc) {
        return count * ROW;
    }

    @Override
    protected void draw(DrawContext ctx, MinecraftClient mc, int x, int y) {
        if (Theme.hudBackground()) {
            ctx.fill(x, y, x + width(mc), y + height(mc), 0x99000000);
        }
        for (int i = 0; i < count; i++) {
            ItemStack st = stacks[i];
            int ry = y + i * ROW;
            ctx.drawItem(st, x + 1, ry + 1);
            if (percent.on() && st.isDamageable() && st.getMaxDamage() > 0) {
                int pct = 100 - st.getDamage() * 100 / st.getMaxDamage();
                int color = pct < 25 ? 0xFFFF4D4D : Theme.hudText();
                ctx.drawText(mc.textRenderer, pct + "%", x + 20, ry + 5, color, Theme.hudShadow());
            }
        }
    }
}
