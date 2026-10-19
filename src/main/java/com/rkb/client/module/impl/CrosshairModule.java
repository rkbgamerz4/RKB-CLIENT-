package com.rkb.client.module.impl;

import com.rkb.client.module.Category;
import com.rkb.client.module.Module;
import com.rkb.client.module.setting.BoolSetting;
import com.rkb.client.module.setting.ChoiceSetting;
import com.rkb.client.module.setting.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

public class CrosshairModule extends Module {
    private static final List<String> COLORS = List.of("White", "Cyan", "Blue", "Green", "Red", "Yellow");
    private static final int[] COLOR_VALUES = {0xFFFFFFFF, 0xFF00A8FF, 0xFF1B60FF, 0xFF55FF7F, 0xFFFF4D4D, 0xFFFFE04D};

    private final ChoiceSetting style = add(new ChoiceSetting("style", "Style",
            List.of("Cross", "Dot", "Cross + Dot"), "Cross"));
    private final IntSetting size = add(new IntSetting("size", "Arm length", 1, 20, 1, 5));
    private final IntSetting gap = add(new IntSetting("gap", "Gap", 0, 10, 1, 3));
    private final IntSetting thickness = add(new IntSetting("thickness", "Thickness", 1, 4, 1, 1));
    private final ChoiceSetting color = add(new ChoiceSetting("color", "Color", COLORS, "Cyan"));
    private final BoolSetting outline = add(new BoolSetting("outline", "Outline", true));
    private final BoolSetting hideVanilla = add(new BoolSetting("hide_vanilla", "Hide vanilla crosshair", true));

    private static CrosshairModule instance;

    public CrosshairModule() {
        super("crosshair", "Custom Crosshair", "Replaces the vanilla crosshair with your own style.", Category.VISUAL);
        instance = this;
    }

    /** Used by the InGameHud mixin. */
    public static boolean shouldHideVanilla() {
        CrosshairModule m = instance;
        return m != null && m.isEnabled() && m.hideVanilla.on();
    }

    private static void rect(DrawContext c, int x1, int y1, int x2, int y2, int col, boolean outl) {
        if (outl) {
            c.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0xFF000000);
        }
        c.fill(x1, y1, x2, y2, col);
    }

    @Override
    public void renderHud(DrawContext c, MinecraftClient mc) {
        if (mc.currentScreen != null || !mc.options.getPerspective().isFirstPerson()) {
            return;
        }
        int cx = mc.getWindow().getScaledWidth() / 2;
        int cy = mc.getWindow().getScaledHeight() / 2;
        int col = COLOR_VALUES[color.index()];
        boolean outl = outline.on();
        int t = thickness.value();
        int lo = t / 2;
        int hi = t - lo;
        String s = style.get();

        if (!s.equals("Dot")) {
            int g = gap.value();
            int len = size.value();
            rect(c, cx - lo - g - len, cy - lo, cx - lo - g, cy + hi, col, outl); // left
            rect(c, cx + hi + g, cy - lo, cx + hi + g + len, cy + hi, col, outl); // right
            rect(c, cx - lo, cy - lo - g - len, cx + hi, cy - lo - g, col, outl); // top
            rect(c, cx - lo, cy + hi + g, cx + hi, cy + hi + g + len, col, outl); // bottom
        }
        if (!s.equals("Cross")) {
            int d = Math.max(2, t);
            int dlo = d / 2;
            rect(c, cx - dlo, cy - dlo, cx - dlo + d, cy - dlo + d, col, outl);
        }
    }
}
