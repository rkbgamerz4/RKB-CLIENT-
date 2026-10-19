package com.rkb.client.hud;

import com.rkb.client.module.Module;
import com.rkb.client.module.ModuleManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

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
        if (mc.options.hudHidden) {
            return;
        }
        List<Module> mods = ModuleManager.get().all();
        for (int i = 0, n = mods.size(); i < n; i++) {
            mods.get(i).renderSafe(context, mc);
        }
    }
}
