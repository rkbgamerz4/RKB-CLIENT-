package com.rkb.client.module.impl;

import com.rkb.client.hud.TextHudModule;
import com.rkb.client.module.Category;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;

import java.util.List;

public class PingModule extends TextHudModule {
    public PingModule() {
        super("ping", "Ping Display", "Shows your latency to the server.", Category.HUD, "Top Left", 4, 60);
    }

    @Override
    protected List<String> lines(MinecraftClient mc) {
        ClientPlayNetworkHandler handler = mc.getNetworkHandler();
        if (handler == null || mc.player == null) {
            return List.of();
        }
        PlayerListEntry entry = handler.getPlayerListEntry(mc.player.getUuid());
        return List.of(entry == null ? "Ping: -" : "Ping: " + entry.getLatency() + " ms");
    }
}
