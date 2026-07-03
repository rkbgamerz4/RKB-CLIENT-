package com.rkb.client.gui;

import com.rkb.client.gui.render.PanelRenderer;
import com.rkb.client.gui.render.SidebarRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class RKBClickGuiScreen extends Screen {

    public RKBClickGuiScreen() {
        super(Component.literal("RKB Client"));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        PanelRenderer.drawPanel(guiGraphics, 60, 40, 900, 500, 0xFF0B1220);
        SidebarRenderer.drawSidebar(guiGraphics);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
