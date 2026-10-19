package com.rkb.client.module.impl;

import com.rkb.client.hud.TextHudModule;
import com.rkb.client.module.Category;
import com.rkb.client.module.setting.BoolSetting;
import com.rkb.client.module.setting.IntSetting;
import net.minecraft.client.MinecraftClient;

import java.util.List;
import java.util.Locale;

public class CoordinatesModule extends TextHudModule {
    private final IntSetting decimals = add(new IntSetting("decimals", "Decimals", 0, 2, 1, 0));
    private final BoolSetting facing = add(new BoolSetting("show_facing", "Show facing", true));

    public CoordinatesModule() {
        super("coordinates", "Coordinates", "Shows your XYZ position and facing direction.",
                Category.HUD, "Top Left", 4, 18);
        restoreEnabled(true);
    }

    static String direction(float yaw) {
        String[] names = {"South", "West", "North", "East"};
        int idx = Math.floorMod((int) Math.floor((yaw + 45.0) / 90.0), 4);
        return names[idx];
    }

    private String fmt(double v) {
        if (decimals.value() == 0) {
            return String.valueOf((int) Math.floor(v));
        }
        return String.format(Locale.ROOT, "%." + decimals.value() + "f", v);
    }

    @Override
    protected List<String> lines(MinecraftClient mc) {
        if (mc.player == null) {
            return List.of();
        }
        String xyz = "XYZ: " + fmt(mc.player.getX()) + " " + fmt(mc.player.getY()) + " " + fmt(mc.player.getZ());
        if (facing.on()) {
            return List.of(xyz, "Facing: " + direction(mc.player.getYaw()));
        }
        return List.of(xyz);
    }
}
