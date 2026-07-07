package com.rkb.client.gui.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class SidebarRenderer {

    private static final String[] CATEGORIES = {
            "Home",
            "Gameplay",
            "Visual",
            "HUD",
            "Performance",
            "Replay",
            "Online",
            "Settings"
    };

    public static void drawSidebar(GuiGraphics guiGraphics, int panelX, int panelY) {

        guiGraphics.fill(panelX, panelY, panelX + 170, panelY + 500, 0xFF08111E);

        guiGraphics.drawString(
                Minecraft.getInstance().font,
                "RKB CLIENT",
                panelX + 20,
                panelY + 20,
                0xFF00A8FF,
                true
        );

        int y = panelY + 65;

        for (String category : CATEGORIES) {
            if (category.equals("Home")) {
                guiGraphics.fill(panelX + 8, y - 4, panelX + 155, y + 14, 0x3300A8FF);
            }
            guiGraphics.drawString(
                    Minecraft.getInstance().font,
                    category,
                    panelX + 20,
                    y,
                    category.equals("Home") ? 0xFF00A8FF : 0xFFFFFFFF,
                    false
            );
            y += 24;
        }
    }
}
