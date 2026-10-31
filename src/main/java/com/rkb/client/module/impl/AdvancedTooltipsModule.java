package com.rkb.client.module.impl;

import com.rkb.client.module.Category;
import com.rkb.client.module.Module;
import net.minecraft.client.MinecraftClient;

/** Same as pressing F3+H: item tooltips show durability and item IDs. */
public class AdvancedTooltipsModule extends Module {
    private boolean original;
    private boolean saved;

    public AdvancedTooltipsModule() {
        super("advanced_tooltips", "Advanced Tooltips", "Shows durability and item IDs in tooltips (like F3+H).",
                Category.UTILITY);
    }

    @Override
    protected void onEnable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null) {
            original = mc.options.advancedItemTooltips;
            saved = true;
            mc.options.advancedItemTooltips = true;
        }
    }

    @Override
    protected void onTick(MinecraftClient mc) {
        if (mc.options != null) {
            mc.options.advancedItemTooltips = true;
        }
    }

    @Override
    protected void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (saved && mc.options != null) {
            mc.options.advancedItemTooltips = original;
        }
        saved = false;
    }
}
