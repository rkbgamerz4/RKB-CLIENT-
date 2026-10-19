package com.rkb.client.hud;

import com.rkb.client.module.Category;
import com.rkb.client.module.Module;
import com.rkb.client.module.setting.ChoiceSetting;
import com.rkb.client.module.setting.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/** A module that draws a box on the HUD, positioned by anchor + offset settings. */
public abstract class HudModule extends Module {
    public static final List<String> ANCHORS = List.of("Top Left", "Top Right", "Bottom Left", "Bottom Right");

    protected final ChoiceSetting anchor;
    protected final IntSetting offsetX;
    protected final IntSetting offsetY;

    protected HudModule(String id, String name, String description, Category category,
                        String defaultAnchor, int defX, int defY) {
        super(id, name, description, category);
        this.anchor = add(new ChoiceSetting("anchor", "Anchor", ANCHORS, defaultAnchor));
        this.offsetX = add(new IntSetting("offset_x", "Offset X", 0, 400, 2, defX));
        this.offsetY = add(new IntSetting("offset_y", "Offset Y", 0, 400, 2, defY));
    }

    protected void prepare(MinecraftClient mc) {
    }

    protected abstract int width(MinecraftClient mc);

    protected abstract int height(MinecraftClient mc);

    protected abstract void draw(DrawContext context, MinecraftClient mc, int x, int y);

    @Override
    public void renderHud(DrawContext context, MinecraftClient mc) {
        prepare(mc);
        int w = width(mc);
        int h = height(mc);
        if (w <= 0 || h <= 0) {
            return;
        }
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        String a = anchor.get();
        boolean right = a.contains("Right");
        boolean bottom = a.contains("Bottom");
        int x = right ? sw - w - offsetX.value() : offsetX.value();
        int y = bottom ? sh - h - offsetY.value() : offsetY.value();
        draw(context, mc, x, y);
    }
}
