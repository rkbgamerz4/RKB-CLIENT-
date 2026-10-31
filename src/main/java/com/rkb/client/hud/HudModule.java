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

    private int lastX;
    private int lastY;
    private int lastW;
    private int lastH;

    protected HudModule(String id, String name, String description, Category category,
                        String defaultAnchor, int defX, int defY) {
        super(id, name, description, category);
        this.anchor = add(new ChoiceSetting("anchor", "Anchor", ANCHORS, defaultAnchor));
        this.offsetX = add(new IntSetting("offset_x", "Offset X", 0, 1000, 2, defX));
        this.offsetY = add(new IntSetting("offset_y", "Offset Y", 0, 1000, 2, defY));
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
        lastX = x;
        lastY = y;
        lastW = w;
        lastH = h;
        draw(context, mc, x, y);
    }

    /** Bounds from the last frame this element was drawn (used by the HUD editor). */
    public final int boundsX() {
        return lastX;
    }

    public final int boundsY() {
        return lastY;
    }

    public final int boundsW() {
        return lastW;
    }

    public final int boundsH() {
        return lastH;
    }

    /** Moves the element to a screen position, snapping the anchor to the nearest corner. */
    public final void moveTo(int x, int y, int sw, int sh) {
        boolean right = x + lastW / 2 > sw / 2;
        boolean bottom = y + lastH / 2 > sh / 2;
        anchor.select((bottom ? "Bottom " : "Top ") + (right ? "Right" : "Left"));
        offsetX.set(right ? sw - lastW - x : x);
        offsetY.set(bottom ? sh - lastH - y : y);
    }

    public final void resetPosition() {
        anchor.reset();
        offsetX.reset();
        offsetY.reset();
    }
}
