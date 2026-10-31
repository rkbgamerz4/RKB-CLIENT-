package com.rkb.client.module.impl;

import com.rkb.client.hud.TextHudModule;
import com.rkb.client.module.Category;
import com.rkb.client.module.setting.BoolSetting;
import com.rkb.client.module.setting.IntSetting;
import net.minecraft.client.MinecraftClient;

import java.util.List;
import java.util.Locale;

public class CoordinatesModule extends TextHudModule {
    private static final double[] SCALE = {1.0, 10.0, 100.0};

    private final IntSetting decimals = add(new IntSetting("decimals", "Decimals", 0, 2, 1, 0));
    private final BoolSetting facing = add(new BoolSetting("show_facing", "Show facing", true));

    private long lastX = Long.MIN_VALUE;
    private long lastY;
    private long lastZ;
    private int lastDir = -1;
    private int lastDecimals = -1;
    private boolean lastFacing;
    private List<String> cached = List.of();

    public CoordinatesModule() {
        super("coordinates", "Coordinates", "Shows your XYZ position and facing direction.",
                Category.HUD, "Top Left", 4, 18);
        restoreEnabled(true);
    }

    private static final String[] DIRS = {"South", "West", "North", "East"};

    static String direction(float yaw) {
        return DIRS[dirIndex(yaw)];
    }

    private static int dirIndex(float yaw) {
        return Math.floorMod((int) Math.floor((yaw + 45.0) / 90.0), 4);
    }

    private String fmt(double v, int d) {
        if (d == 0) {
            return String.valueOf((int) Math.floor(v));
        }
        return String.format(Locale.ROOT, "%." + d + "f", v);
    }

    @Override
    protected List<String> lines(MinecraftClient mc) {
        if (mc.player == null) {
            return List.of();
        }
        int d = decimals.value();
        double sc = SCALE[d];
        long x = (long) Math.floor(mc.player.getX() * sc);
        long y = (long) Math.floor(mc.player.getY() * sc);
        long z = (long) Math.floor(mc.player.getZ() * sc);
        int dir = dirIndex(mc.player.getYaw());
        boolean showFacing = facing.on();
        if (x != lastX || y != lastY || z != lastZ || dir != lastDir || d != lastDecimals || showFacing != lastFacing) {
            lastX = x;
            lastY = y;
            lastZ = z;
            lastDir = dir;
            lastDecimals = d;
            lastFacing = showFacing;
            String xyz = "XYZ: " + fmt(mc.player.getX(), d) + " " + fmt(mc.player.getY(), d) + " " + fmt(mc.player.getZ(), d);
            cached = showFacing ? List.of(xyz, "Facing: " + DIRS[dir]) : List.of(xyz);
        }
        return cached;
    }
}
