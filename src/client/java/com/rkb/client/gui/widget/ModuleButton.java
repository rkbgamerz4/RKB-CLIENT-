package com.rkb.client.gui.widget;

import com.rkb.client.gui.render.ToggleRenderer;
import com.rkb.client.gui.render.UIColors;
import com.rkb.client.gui.util.RoundedRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class ModuleButton {

    public static void draw(
            GuiGraphics g,
            int x,
            int y,
            int w,
            int h,
            String name,
            boolean enabled
    ) {

        int bg = enabled ? UIColors.PANEL : 0xFF101722;

        RoundedRenderer.drawRoundedRect(g, x, y, w, h, bg);

        if (enabled) {
            g.fill(x, y, x + 3, y + h, UIColors.PRIMARY);
        }

        g.drawString(
                Minecraft.getInstance().font,
                name,
                x + 14,
                y + 12,
                UIColors.TEXT,
                false
        );

        ToggleRenderer.draw(
                g,
                x + w - 48,
                y + 10,
                enabled
        );
    }
}
