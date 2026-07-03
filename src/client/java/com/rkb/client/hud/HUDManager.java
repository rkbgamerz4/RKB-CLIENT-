package com.rkb.client.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;

public class HUDManager {

    private static final HUDManager INSTANCE = new HUDManager();

    public static HUDManager getInstance() {
        return INSTANCE;
    }

    public void render(GuiGraphics guiGraphics) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null) return;

        guiGraphics.drawString(
                mc.font,
                "RKB Client",
                5,
                5,
                0x00A8FF,
                true
        );
    }
}
