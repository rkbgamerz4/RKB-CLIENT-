package com.rkb.client.gui;

import com.rkb.client.gui.render.PanelRenderer;
import com.rkb.client.gui.render.SidebarRenderer;
import com.rkb.client.gui.widget.ModuleButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class RKBClickGuiScreen extends Screen {
    private boolean zoomEnabled = true;
    private boolean fpsEnabled = true;

    public RKBClickGuiScreen() {
        super(Component.literal("RKB Client"));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        PanelRenderer.drawPanel(guiGraphics, 180, 40, 900, 500, 0xFF0B1220);
        SidebarRenderer.drawSidebar(guiGraphics, (this.width - 900) / 2, (this.height - 500) / 2);
        ModuleButton.draw(guiGraphics, 370, 90, 220, 36, "Zoom", zoomEnabled);
        ModuleButton.draw(guiGraphics, 370, 135, 220, 36, "FPS Display", fpsEnabled);
        ModuleButton.draw(guiGraphics, 370, 180, 220, 36, "Armor Status", false);
        ModuleButton.draw(guiGraphics, 370, 225, 220, 36, "Motion Blur", true);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= 250 && mouseX <= 470 && mouseY >= 90 && mouseY <= 126) {
            zoomEnabled = !zoomEnabled;
            return true;
        }
        if (mouseX >= 250 && mouseX <= 470 && mouseY >= 135 && mouseY <= 171) {
            fpsEnabled = !fpsEnabled;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

}
