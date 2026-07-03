package com.rkb.client.gui.render;

import net.minecraft.client.gui.GuiGraphics;

public class PanelRenderer {

    public static void drawPanel(
            GuiGraphics guiGraphics,
            int x,
            int y,
            int width,
            int height,
            int color
    ) {

        // Border
        guiGraphics.fill(
                x - 1,
                y - 1,
                x + width + 1,
                y + height + 1,
                UIColors.BORDER
        );

        // Main Panel
        guiGraphics.fill(
                x,
                y,
                x + width,
                y + height,
                UIColors.PANEL
        );
    }
}
