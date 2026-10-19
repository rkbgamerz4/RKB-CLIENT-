package com.rkb.client.module.impl;

import com.rkb.client.VersionInfo;
import com.rkb.client.hud.TextHudModule;
import com.rkb.client.module.Category;
import com.rkb.client.module.setting.BoolSetting;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class InfoHudModule extends TextHudModule {
    private final BoolSetting mcVersion = add(new BoolSetting("show_mc_version", "Minecraft version", true));
    private final BoolSetting clientVersion = add(new BoolSetting("show_client_version", "RKB version", false));
    private final BoolSetting speed = add(new BoolSetting("show_speed", "Speed (blocks/s)", true));

    public InfoHudModule() {
        super("info_hud", "Info HUD", "Small panel with version and movement speed.",
                Category.HUD, "Top Right", 4, 4);
    }

    @Override
    protected List<String> lines(MinecraftClient mc) {
        List<String> out = new ArrayList<>();
        if (mcVersion.on()) {
            out.add("Minecraft " + VersionInfo.minecraft());
        }
        if (clientVersion.on()) {
            out.add("RKB CLIENT " + VersionInfo.mod());
        }
        if (speed.on() && mc.player != null) {
            double bps = mc.player.getVelocity().horizontalLength() * 20.0;
            out.add(String.format(Locale.ROOT, "Speed: %.1f b/s", bps));
        }
        return out;
    }
}
