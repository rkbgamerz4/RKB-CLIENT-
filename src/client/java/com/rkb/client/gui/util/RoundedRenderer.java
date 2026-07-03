package com.rkb.client.gui.util;

import net.minecraft.client.gui.GuiGraphics;

public class RoundedRenderer {

    public static void drawRoundedRect(
            GuiGraphics g,
            int x,
            int y,
            int w,
            int h,
            int color
    ) {
        g.fill(x + 3, y, x + w - 3, y + h, color);
        g.fill(x, y + 3, x + w, y + h - 3, color);

        g.fill(x + 1, y + 1, x + 3, y + 3, color);
        g.fill(x + w - 3, y + 1, x + w - 1, y + 3, color);

        g.fill(x + 1, y + h - 3, x + 3, y + h - 1, color);
        g.fill(x + w - 3, y + h - 3, x + w - 1, y + h - 1, color);
    }
}
