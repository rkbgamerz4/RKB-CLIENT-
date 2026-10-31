package com.rkb.client.module.impl;

import com.rkb.client.hud.TextHudModule;
import com.rkb.client.module.Category;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.util.List;

public class ServerAddressModule extends TextHudModule {
    private String lastAddress = "";
    private List<String> cached = List.of();

    public ServerAddressModule() {
        super("server_address", "Server Address", "Shows the address of the server you are on.",
                Category.HUD, "Top Right", 4, 150);
    }

    @Override
    protected List<String> lines(MinecraftClient mc) {
        ServerInfo info = mc.getCurrentServerEntry();
        if (info == null || info.address == null) {
            return List.of();
        }
        if (!info.address.equals(lastAddress)) {
            lastAddress = info.address;
            cached = List.of("Server: " + info.address);
        }
        return cached;
    }
}
