package com.rkb.client.gui;

import com.rkb.client.config.ConfigManager;
import com.rkb.client.hud.HudModule;
import com.rkb.client.input.KeyUtil;
import com.rkb.client.module.Module;
import com.rkb.client.module.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Drag HUD elements to a new position. The nearest screen corner becomes their anchor. */
public class HudEditorScreen extends Screen {
    private static final int BTN_Y = 6;
    private static final int BTN_H = 16;

    private final List<HudModule> hud = new ArrayList<>();
    private HudModule dragging;
    private int grabX;
    private int grabY;
    private boolean mouseWasDown;

    public HudEditorScreen() {
        super(Text.literal("RKB HUD Editor"));
    }

    @Override
    protected void init() {
        hud.clear();
        for (Module m : ModuleManager.get().all()) {
            if (m instanceof HudModule h) {
                hud.add(h);
            }
        }
        mouseWasDown = KeyUtil.isMouseDown(MinecraftClient.getInstance(), GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    public boolean shouldPause() {
        return false;
    }

    public void removed() {
        ConfigManager.save();
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static void box(DrawContext c, int x, int y, int w, int h, int color) {
        c.fill(x, y, x + w, y + 1, color);
        c.fill(x, y + h - 1, x + w, y + h, color);
        c.fill(x, y, x + 1, y + h, color);
        c.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static void button(DrawContext c, TextRenderer tr, String label, int x, int w, boolean hover) {
        c.fill(x, BTN_Y, x + w, BTN_Y + BTN_H, hover ? Theme.CARD_HOVER : Theme.CARD);
        box(c, x, BTN_Y, w, BTN_H, Theme.accent());
        c.drawText(tr, label, x + (w - tr.getWidth(label)) / 2, BTN_Y + 4, Theme.TEXT, false);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        int accent = Theme.accent();

        boolean any = false;
        for (HudModule h : hud) {
            h.renderSafe(ctx, mc);
        }
        for (HudModule h : hud) {
            if (h.isEnabled() && h.boundsW() > 0) {
                any = true;
                int color = h == dragging ? Theme.TEXT : accent;
                box(ctx, h.boundsX() - 1, h.boundsY() - 1, h.boundsW() + 2, h.boundsH() + 2, color);
                ctx.drawText(tr, h.name(), h.boundsX(), Math.max(0, h.boundsY() - 11), color, true);
            }
        }

        int doneW = tr.getWidth("Done") + 16;
        int resetW = tr.getWidth("Reset positions") + 16;
        int startX = (width - (doneW + resetW + 6)) / 2;
        int resetX = startX + doneW + 6;
        button(ctx, tr, "Done", startX, doneW, inside(mx, my, startX, BTN_Y, doneW, BTN_H));
        button(ctx, tr, "Reset positions", resetX, resetW, inside(mx, my, resetX, BTN_Y, resetW, BTN_H));
        String hint = any ? "Drag an element to move it. Esc to close."
                : "No HUD element is on. Enable some in the menu first.";
        ctx.drawText(tr, hint, (width - tr.getWidth(hint)) / 2, BTN_Y + BTN_H + 6, Theme.MUTED, false);

        boolean down = KeyUtil.isMouseDown(mc, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        if (down && !mouseWasDown) {
            if (inside(mx, my, startX, BTN_Y, doneW, BTN_H)) {
                mouseWasDown = true;
                ConfigManager.save();
                mc.setScreen(new RkbMenuScreen());
                return;
            } else if (inside(mx, my, resetX, BTN_Y, resetW, BTN_H)) {
                for (HudModule h : hud) {
                    h.resetPosition();
                }
                ConfigManager.save();
            } else {
                for (int i = hud.size() - 1; i >= 0; i--) {
                    HudModule h = hud.get(i);
                    if (h.isEnabled() && h.boundsW() > 0
                            && inside(mx, my, h.boundsX(), h.boundsY(), h.boundsW(), h.boundsH())) {
                        dragging = h;
                        grabX = mx - h.boundsX();
                        grabY = my - h.boundsY();
                        break;
                    }
                }
            }
        } else if (down && dragging != null) {
            dragging.moveTo(mx - grabX, my - grabY, width, height);
        } else if (!down && mouseWasDown && dragging != null) {
            dragging = null;
            ConfigManager.save();
        }
        mouseWasDown = down;
    }
}
