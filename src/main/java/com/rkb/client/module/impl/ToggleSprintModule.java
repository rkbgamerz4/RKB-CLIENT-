package com.rkb.client.module.impl;

import com.rkb.client.module.Category;
import com.rkb.client.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Keeps the sprint key held while you walk forward. Uses the normal sprint key, so the server
 * sees exactly what it would see if you held sprint yourself.
 */
public class ToggleSprintModule extends Module {
    public ToggleSprintModule() {
        super("toggle_sprint", "Toggle Sprint", "Hold forward once and keep sprinting, no need to hold the sprint key.",
                Category.MOVEMENT);
    }

    @Override
    protected void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.currentScreen != null) {
            return;
        }
        if (mc.options.forwardKey.isPressed()) {
            mc.options.sprintKey.setPressed(true);
        }
    }

    @Override
    protected void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null) {
            mc.options.sprintKey.setPressed(false);
        }
    }
}
