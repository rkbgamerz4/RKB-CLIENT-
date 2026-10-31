package com.rkb.client.module.impl;

import com.rkb.client.hud.TextHudModule;
import com.rkb.client.module.Category;
import com.rkb.client.module.setting.BoolSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Counts a few useful items in your inventory (refreshed twice a second). */
public class ItemCounterModule extends TextHudModule {
    private final BoolSetting pearls = add(new BoolSetting("pearls", "Ender pearls", true));
    private final BoolSetting gapples = add(new BoolSetting("gapples", "Golden apples", true));
    private final BoolSetting totems = add(new BoolSetting("totems", "Totems of undying", true));
    private final BoolSetting arrows = add(new BoolSetting("arrows", "Arrows", false));
    private final BoolSetting xp = add(new BoolSetting("xp", "Experience bottles", false));

    private int tick;
    private List<String> cached = List.of();

    public ItemCounterModule() {
        super("item_counter", "Item Counter", "Counts pearls, golden apples, totems and more in your inventory.",
                Category.HUD, "Bottom Right", 6, 120);
    }

    @Override
    protected void onTick(MinecraftClient mc) {
        if (++tick < 10) {
            return;
        }
        tick = 0;
        if (mc.player == null) {
            cached = List.of();
            return;
        }
        PlayerInventory inv = mc.player.getInventory();
        List<String> out = new ArrayList<>(5);
        if (pearls.on()) {
            add(out, "Pearls", inv.count(Items.ENDER_PEARL));
        }
        if (gapples.on()) {
            add(out, "Gapples", inv.count(Items.GOLDEN_APPLE));
        }
        if (totems.on()) {
            add(out, "Totems", inv.count(Items.TOTEM_OF_UNDYING));
        }
        if (arrows.on()) {
            add(out, "Arrows", inv.count(Items.ARROW));
        }
        if (xp.on()) {
            add(out, "XP bottles", inv.count(Items.EXPERIENCE_BOTTLE));
        }
        cached = out;
    }

    private static void add(List<String> out, String label, int count) {
        if (count > 0) {
            out.add(label + ": " + count);
        }
    }

    @Override
    protected List<String> lines(MinecraftClient mc) {
        return cached;
    }
}
