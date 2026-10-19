package com.rkb.client.module.impl;

import com.rkb.client.hud.TextHudModule;
import com.rkb.client.input.KeyUtil;
import com.rkb.client.module.Category;
import com.rkb.client.module.setting.BoolSetting;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.List;

/** Informational clicks-per-second counter. Only reads mouse state; it does not click for you. */
public class CpsModule extends TextHudModule {
    private final BoolSetting showRight = add(new BoolSetting("show_right", "Show right click", true));
    private final ArrayDeque<Long> left = new ArrayDeque<>();
    private final ArrayDeque<Long> right = new ArrayDeque<>();
    private boolean leftPrev;
    private boolean rightPrev;

    public CpsModule() {
        super("cps", "CPS Counter", "Shows how many times per second you click.",
                Category.COMBAT, "Top Right", 4, 44);
    }

    private static void trim(ArrayDeque<Long> q, long now) {
        while (!q.isEmpty() && now - q.peekFirst() > 1_000_000_000L) {
            q.pollFirst();
        }
    }

    @Override
    protected void prepare(MinecraftClient mc) {
        long now = System.nanoTime();
        boolean l = KeyUtil.isMouseDown(mc, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        boolean r = KeyUtil.isMouseDown(mc, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        if (mc.currentScreen == null) {
            if (l && !leftPrev) {
                left.addLast(now);
            }
            if (r && !rightPrev) {
                right.addLast(now);
            }
        }
        leftPrev = l;
        rightPrev = r;
        trim(left, now);
        trim(right, now);
        super.prepare(mc);
    }

    @Override
    protected List<String> lines(MinecraftClient mc) {
        return List.of(showRight.on() ? "CPS: " + left.size() + " | " + right.size() : "CPS: " + left.size());
    }

    @Override
    protected void onDisable() {
        left.clear();
        right.clear();
    }
}
