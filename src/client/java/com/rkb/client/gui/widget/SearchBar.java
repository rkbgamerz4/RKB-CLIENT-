package com.rkb.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class SearchBar {

    public static void draw(
            GuiGraphics g,
            int x,
            int y,
            int width,
            String text
    ) {

        // Background
        g.fill(x, y, x + width, y + 24, 0xFF111827);

        // Border
        g.fill(x, y, x + width, y + 1, 0xFF00A8FF);
        g.fill(x, y + 23, x + width, y + 24, 0xFF00A8FF);
        g.fill(x, y, x + 1, y + 24, 0xFF00A8FF);
        g.fill(x + width - 1, y, x + width, y + 24, 0xFF00A8FF);

        String display = text.isEmpty() ? "Search modules..." : text;

        g.drawString(
                Minecraft.getInstance().font,
                display,
                x + 8,
                y + 8,
                text.isEmpty() ? 0xFF777777 : 0xFFFFFFFF,
                false
        );
    }
}
