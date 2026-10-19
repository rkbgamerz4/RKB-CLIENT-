package com.rkb.client.hud;

import com.rkb.client.gui.Theme;
import com.rkb.client.module.Category;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/** HUD module that shows one or more lines of text. */
public abstract class TextHudModule extends HudModule {
    private static final int PAD = 3;
    private static final int LINE_H = 10;

    private List<String> cached = List.of();

    protected TextHudModule(String id, String name, String description, Category category,
                            String defaultAnchor, int defX, int defY) {
        super(id, name, description, category, defaultAnchor, defX, defY);
    }

    protected abstract List<String> lines(MinecraftClient mc);

    @Override
    protected void prepare(MinecraftClient mc) {
        List<String> l = lines(mc);
        cached = l == null ? List.of() : l;
    }

    @Override
    protected int width(MinecraftClient mc) {
        if (cached.isEmpty()) {
            return 0;
        }
        TextRenderer tr = mc.textRenderer;
        int w = 0;
        for (String s : cached) {
            w = Math.max(w, tr.getWidth(s));
        }
        return w + PAD * 2 + 2;
    }

    @Override
    protected int height(MinecraftClient mc) {
        return cached.isEmpty() ? 0 : cached.size() * LINE_H + PAD * 2 - 1;
    }

    @Override
    protected void draw(DrawContext context, MinecraftClient mc, int x, int y) {
        int w = width(mc);
        int h = height(mc);
        if (Theme.hudBackground()) {
            context.fill(x, y, x + w, y + h, 0x99000000);
        }
        context.fill(x, y, x + 1, y + h, Theme.accent());
        int color = Theme.hudText();
        boolean shadow = Theme.hudShadow();
        int ty = y + PAD;
        for (String s : cached) {
            context.drawText(mc.textRenderer, s, x + PAD + 2, ty, color, shadow);
            ty += LINE_H;
        }
    }
}
