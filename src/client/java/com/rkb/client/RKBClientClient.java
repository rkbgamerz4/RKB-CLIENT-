package com.rkb.client;

import com.rkb.client.hud.HUDManager;
import com.rkb.client.keybind.RKBKeybinds;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public class RKBClientClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        System.out.println("RKB Client Loaded!");
        RKBKeybinds.register();

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            HUDManager.getInstance().render(drawContext);
        });
    }
}
