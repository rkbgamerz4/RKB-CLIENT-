package com.rkb.client.hud;

import com.rkb.client.RkbClient;
import com.rkb.client.module.Module;
import com.rkb.client.module.ModuleManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Hooks Fabric's HUD callback and lets each enabled module draw itself. */
public final class HudRenderer {
    private HudRenderer() {
    }

    public static void init() {
        HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));
    }

    private static void render(DrawContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null || mc.getWindow() == null) {
            return;
        }
        try {
            ModuleManager.get().pollKeys(mc);
        } catch (Throwable t) {
            RkbClient.LOGGER.error("Key polling failed", t);
        }
        if (mc.options.hudHidden) {
            return;
        }
        for (Module m : ModuleManager.get().all()) {
            m.renderSafe(context, mc);
        }
    }
}
