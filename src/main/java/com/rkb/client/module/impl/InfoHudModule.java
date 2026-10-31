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

    private long lastKey = Long.MIN_VALUE;
    private List<String> cached = List.of();

    public InfoHudModule() {
        super("info_hud", "Info HUD", "Small panel with version and movement speed.",
                Category.HUD, "Top Right", 4, 4);
    }

    @Override
    protected List<String> lines(MinecraftClient mc) {
        boolean sp = speed.on() && mc.player != null;
        long tenths = sp ? Math.round(mc.player.getVelocity().horizontalLength() * 200.0) : 0;
        long key = tenths * 8 + (mcVersion.on() ? 1 : 0) + (clientVersion.on() ? 2 : 0) + (sp ? 4 : 0);
        if (key != lastKey) {
            lastKey = key;
            List<String> out = new ArrayList<>(3);
            if (mcVersion.on()) {
                out.add("Minecraft " + VersionInfo.minecraft());
            }
            if (clientVersion.on()) {
                out.add("RKB CLIENT " + VersionInfo.mod());
            }
            if (sp) {
                out.add(String.format(Locale.ROOT, "Speed: %.1f b/s", tenths / 10.0));
            }
            cached = out;
        }
        return cached;
    }
}
