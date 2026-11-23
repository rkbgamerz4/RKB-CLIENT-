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
    private record Hit(RkbUi.Rect rect, Runnable action) {
    }

    private final List<HudModule> hud = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private HudModule dragging;
    private int grabX;
    private int grabY;
    private boolean mouseWasDown;

    private RkbLayout.TopBar chrome;
    private int contentY;
    private int headerH = 40;

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
        chrome = RkbLayout.topBar(width, height);
        contentY = chrome.contentTop();
        mouseWasDown = KeyUtil.isMouseDown(MinecraftClient.getInstance(), GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        ConfigManager.save();
    }

    private void click(int mx, int my) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            if (hits.get(i).rect().contains(mx, my)) {
                hits.get(i).action().run();
                return;
            }
        }
        for (int i = hud.size() - 1; i >= 0; i--) {
            HudModule h = hud.get(i);
            if (h.isEnabled() && h.boundsW() > 0
                    && mx >= h.boundsX() && mx < h.boundsX() + h.boundsW()
                    && my >= h.boundsY() && my < h.boundsY() + h.boundsH()) {
                dragging = h;
                grabX = mx - h.boundsX();
                grabY = my - h.boundsY();
                break;
            }
        }
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        int accent = Theme.chromeAccent();
        hits.clear();

        RkbUi.background(ctx, width, height, System.currentTimeMillis());
        boolean any = false;
        for (HudModule h : hud) {
            h.renderSafe(ctx, mc);
        }
        for (HudModule h : hud) {
            if (h.isEnabled() && h.boundsW() > 0) {
                any = true;
                int color = h == dragging ? RkbUi.TEXT : accent;
                RkbUi.roundedOutline(ctx, new RkbUi.Rect(h.boundsX() - 1, h.boundsY() - 1,
                        h.boundsW() + 2, h.boundsH() + 2), color);
                ctx.drawText(tr, h.name(), h.boundsX(), Math.max(0, h.boundsY() - 11), color, false);
            }
        }

        RkbUi.Rect header = new RkbUi.Rect(8, contentY, width - 16, headerH);
        RkbUi.panel(ctx, header, 0xE60E1422, RkbUi.LINE);
        ctx.drawText(tr, "HUD Editor", header.x() + 12, header.y() + 9, RkbUi.TEXT, false);

        int doneW = Math.max(58, tr.getWidth("Done") + 24);
        int resetW = Math.max(96, tr.getWidth("Reset positions") + 24);
        RkbUi.Rect done = new RkbUi.Rect(header.right() - 12 - doneW, header.y() + 10, doneW, 20);
        RkbUi.Rect reset = new RkbUi.Rect(done.x() - 8 - resetW, header.y() + 10, resetW, 20);
        String hint = any ? "Drag any element to move it. Corners are the anchors."
                : "No HUD element is on. Enable some in the module menu first.";
        ctx.drawText(tr, RkbUi.fit(tr, hint, reset.x() - header.x() - 24), header.x() + 12,
                header.y() + 23, RkbUi.MUTED, false);
        RkbUi.button(ctx, tr, reset, "Reset positions", reset.contains(mx, my), false, true);
        RkbUi.button(ctx, tr, done, "Done", done.contains(mx, my), true, true);
        hits.add(new Hit(reset, () -> {
            for (HudModule h : hud) {
                h.resetPosition();
            }
            ConfigManager.save();
        }));
        hits.add(new Hit(done, () -> {
            ConfigManager.save();
            RkbScreens.home();
        }));

        RkbChrome.draw(ctx, tr, chrome, mx, my, 3, (r, action) -> hits.add(new Hit(r, action)));

        boolean down = KeyUtil.isMouseDown(mc, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        if (down && !mouseWasDown) {
            click(mx, my);
        } else if (down && dragging != null) {
            dragging.moveTo(mx - grabX, my - grabY, width, height);
        } else if (!down && mouseWasDown && dragging != null) {
            dragging = null;
            ConfigManager.save();
        }
        mouseWasDown = down;
    }
}
