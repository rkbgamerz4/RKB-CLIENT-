package com.rkb.client.gui.widget;

import com.rkb.client.gui.render.ToggleRenderer;
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

        int bg = enabled ? 0xFF101A2A : 0xFF151C28;

        RoundedRenderer.drawRoundedRect(g, x, y, w, h, bg);

        g.drawString(
                Minecraft.getInstance().font,
                name,
                x + 12,
                y + 11,
                0xFFFFFFFF,
                false
        );

        ToggleRenderer.draw(
                g,
                x + w - 46,
                y + 10,
                enabled
        );
    }
}
