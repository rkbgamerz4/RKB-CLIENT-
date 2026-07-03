package com.rkb.client.gui.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class SidebarRenderer {

    private static final String[] CATEGORIES = {
            "Combat",
            "Movement",
            "Render",
            "Player",
            "HUD",
            "World",
            "Misc",
            "Settings"
    };

    public static void drawSidebar(GuiGraphics guiGraphics) {

        guiGraphics.fill(60, 40, 220, 540, 0xFF08111E);

        guiGraphics.drawString(
                Minecraft.getInstance().font,
                "RKB CLIENT",
                80,
                60,
                0xFF00A8FF,
                true
        );

        int y = 105;

        for (String category : CATEGORIES) {
            guiGraphics.drawString(
                    Minecraft.getInstance().font,
                    category,
                    80,
                    y,
                    0xFFFFFFFF,
                    false
            );

            y += 22;
        }
    }
}
