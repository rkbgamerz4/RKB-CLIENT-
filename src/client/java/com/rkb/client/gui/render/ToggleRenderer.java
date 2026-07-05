package com.rkb.client.gui.render;

import net.minecraft.client.gui.GuiGraphics;

public class ToggleRenderer {

    public static void draw(
            GuiGraphics g,
            int x,
            int y,
            boolean enabled
    ) {

        int bg = enabled ? 0xFF00A8FF : 0xFF3A3A3A;

        g.fill(x, y, x + 32, y + 16, bg);

        if (enabled) {
            g.fill(x + 18, y + 2, x + 30, y + 14, 0xFFFFFFFF);
        } else {
            g.fill(x + 2, y + 2, x + 14, y + 14, 0xFFFFFFFF);
        }
    }
}
