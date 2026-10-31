package com.rkb.client.module.impl;

import com.rkb.client.module.Category;
import com.rkb.client.module.Module;
import net.minecraft.client.MinecraftClient;

/** Turns on Minecraft's own "Toggle Sneak" accessibility option while enabled. */
public class ToggleSneakModule extends Module {
    private boolean original;

    public ToggleSneakModule() {
        super("toggle_sneak", "Toggle Sneak", "Press the sneak key once to sneak, again to stop (vanilla option).",
                Category.CONTROLS);
    }

    @Override
    protected void onEnable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options == null) {
            return;
        }
        original = mc.options.getSneakToggled().getValue();
        mc.options.getSneakToggled().setValue(true);
    }

    @Override
    protected void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null) {
            mc.options.getSneakToggled().setValue(original);
        }
    }
}
