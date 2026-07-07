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

        guiGraphics.fill(
                panelX,
                panelY,
                panelX + 170,
                panelY + 500,
                UIColors.SIDEBAR
        );

        guiGraphics.drawString(
                Minecraft.getInstance().font,
                "RKB CLIENT",
                panelX + 20,
                panelY + 20,
                UIColors.PRIMARY,
                true
        );

        int y = panelY + 65;        for (String category : CATEGORIES) {

            boolean selected = category.equals("Home");

            if (selected) {
                guiGraphics.fill(
                        panelX + 8,
                        y - 4,
                        panelX + 160,
                        y + 14,
                        0x3300A8FF
                );
            }

            guiGraphics.drawString(
                    Minecraft.getInstance().font,
                    category,
                    panelX + 20,
                    y,
                    selected ? UIColors.PRIMARY : UIColors.TEXT,
                    false
            );

            y += 24;
        }
    }
}
