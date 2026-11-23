package com.rkb.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Small centered notice/modal used for unavailable actions. */
public class RkbNoticeScreen extends Screen {
    private record Hit(RkbUi.Rect rect, Runnable action) {
        boolean contains(int x, int y) { return rect.contains(x, y); }
    }

    private final Screen parent;
    private final String title;
    private final String message;
    private final String detail;
    private final List<Hit> hits = new ArrayList<>();

    public RkbNoticeScreen(Screen parent, String title, String message, String detail) {
        super(Text.literal(title));
        this.parent = parent;
        this.title = title;
        this.message = message;
        this.detail = detail;
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() { MinecraftClient.getInstance().setScreen(parent); }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.isEscape() || input.key() == GLFW.GLFW_KEY_ENTER) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        int mx = (int) click.x();
        int my = (int) click.y();
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.contains(mx, my)) { h.action().run(); return true; }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        hits.clear();
        RkbUi.background(ctx, width, height, System.currentTimeMillis());
        TextRenderer tr = this.textRenderer;
        int pw = Math.min(460, width - 40);
        int ph = 160;
        int px = (width - pw) / 2;
        int py = (height - ph) / 2;
        RkbUi.panel(ctx, new RkbUi.Rect(px, py, pw, ph), RkbUi.SURFACE_2, RkbUi.LINE);
        ctx.drawText(tr, title, px + 20, py + 20, RkbUi.TEXT, false);
        ctx.drawText(tr, RkbUi.fit(tr, message, pw - 40), px + 20, py + 46, RkbUi.MUTED, false);
        ctx.drawText(tr, RkbUi.fit(tr, detail, pw - 40), px + 20, py + 64, RkbUi.DIM, false);
        RkbUi.Rect ok = new RkbUi.Rect(px + pw - 116, py + ph - 44, 96, 28);
        boolean hover = ok.contains(mouseX, mouseY);
        RkbUi.button(ctx, tr, ok, "OK", hover, true, true);
        hits.add(new Hit(ok, this::close));
    }
}
