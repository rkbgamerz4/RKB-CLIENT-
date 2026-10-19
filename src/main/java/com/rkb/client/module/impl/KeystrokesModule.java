package com.rkb.client.module.impl;

import com.rkb.client.gui.Theme;
import com.rkb.client.hud.HudModule;
import com.rkb.client.module.Category;
import com.rkb.client.module.setting.BoolSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class KeystrokesModule extends HudModule {
    private static final int BOX = 18;
    private static final int GAP = 2;

    private final BoolSetting space = add(new BoolSetting("show_space", "Show jump bar", true));

    public KeystrokesModule() {
        super("keystrokes", "Keystrokes", "Shows which movement keys you are holding.",
                Category.HUD, "Bottom Left", 6, 6);
    }

    @Override
    protected int width(MinecraftClient mc) {
        return BOX * 3 + GAP * 2;
    }

    @Override
    protected int height(MinecraftClient mc) {
        return BOX * 2 + GAP + (space.on() ? BOX / 2 + GAP : 0);
    }

    private void key(DrawContext c, MinecraftClient mc, int x, int y, int w, int h, String label, boolean down) {
        c.fill(x, y, x + w, y + h, down ? Theme.accent() : 0x99000000);
        int tw = mc.textRenderer.getWidth(label);
        c.drawText(mc.textRenderer, label, x + (w - tw) / 2, y + (h - 8) / 2, down ? 0xFF000000 : Theme.hudText(), false);
    }

    @Override
    protected void draw(DrawContext c, MinecraftClient mc, int x, int y) {
        var o = mc.options;
        key(c, mc, x + BOX + GAP, y, BOX, BOX, "W", o.forwardKey.isPressed());
        key(c, mc, x, y + BOX + GAP, BOX, BOX, "A", o.leftKey.isPressed());
        key(c, mc, x + BOX + GAP, y + BOX + GAP, BOX, BOX, "S", o.backKey.isPressed());
        key(c, mc, x + (BOX + GAP) * 2, y + BOX + GAP, BOX, BOX, "D", o.rightKey.isPressed());
        if (space.on()) {
            key(c, mc, x, y + (BOX + GAP) * 2, BOX * 3 + GAP * 2, BOX / 2, "", o.jumpKey.isPressed());
        }
    }
}
