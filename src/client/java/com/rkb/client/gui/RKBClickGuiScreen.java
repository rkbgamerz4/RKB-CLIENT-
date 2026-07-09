package com.rkb.client.gui;

import com.rkb.client.gui.render.PanelRenderer;
import com.rkb.client.gui.render.SidebarRenderer;
import com.rkb.client.gui.widget.ModuleButton;
import com.rkb.client.gui.widget.SearchBar;
import com.rkb.client.module.Module;
import com.rkb.client.module.ModuleManager;
import com.rkb.client.module.ModuleCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class RKBClickGuiScreen extends Screen {
    private ModuleCategory selectedCategory = ModuleCategory.HOME;

    public RKBClickGuiScreen() {
        super(Component.literal("RKB Client"));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        int panelX = (this.width - 900) / 2;

        int panelY = (this.height - 500) / 2;

        PanelRenderer.drawPanel(guiGraphics, 180, 40, 900, 500, 0xFF0B1220);
        SidebarRenderer.drawSidebar(guiGraphics, panelX, panelY, selectedCategory.name().substring(0,1) + selectedCategory.name().substring(1).toLowerCase());

        SearchBar.draw(guiGraphics, panelX + 190, panelY + 15, 250, "");

        int moduleY = panelY + 50;

        for (Module module : ModuleManager.getModules(selectedCategory)) {
            ModuleButton.draw(guiGraphics, panelX + 190, moduleY, 220, 36, module.getName(), module.isEnabled());
            moduleY += 45;
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int panelY = (this.height - 500) / 2;
        int panelX = (this.width - 900) / 2;

        if (mouseX >= panelX && mouseX <= panelX + 170) {
            int index = (int)((mouseY - (panelY + 65)) / 24);
            if (index >= 0 && index < ModuleCategory.values().length) {
                selectedCategory = ModuleCategory.values()[index];
                return true;
            }
        }

        int moduleY = panelY + 50;

        for (Module module : ModuleManager.getModules(selectedCategory)) {
            if (mouseX >= 190 && mouseX <= 410 && mouseY >= moduleY && mouseY <= moduleY + 36) {
                module.toggle();
                return true;
            }
            moduleY += 45;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

}
