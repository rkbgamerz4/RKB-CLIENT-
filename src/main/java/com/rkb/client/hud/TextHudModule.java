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
    private List<String> measured;
    private int cachedW;
    private int cachedH;

    protected TextHudModule(String id, String name, String description, Category category,
                            String defaultAnchor, int defX, int defY) {
        super(id, name, description, category, defaultAnchor, defX, defY);
    }

    protected abstract List<String> lines(MinecraftClient mc);

    @Override
    protected void prepare(MinecraftClient mc) {
        List<String> l = lines(mc);
        if (l == null) {
            l = List.of();
        }
        cached = l;
        if (l != measured) {
            measured = l;
            if (l.isEmpty()) {
                cachedW = 0;
                cachedH = 0;
            } else {
                TextRenderer tr = mc.textRenderer;
                int w = 0;
                for (int i = 0; i < l.size(); i++) {
                    w = Math.max(w, tr.getWidth(l.get(i)));
                }
                cachedW = w + PAD * 2 + 2;
                cachedH = l.size() * LINE_H + PAD * 2 - 1;
            }
        }
    }

    @Override
    protected int width(MinecraftClient mc) {
        return cachedW;
    }

    @Override
    protected int height(MinecraftClient mc) {
        return cachedH;
    }

    @Override
    protected void draw(DrawContext context, MinecraftClient mc, int x, int y) {
        int w = cachedW;
        int h = cachedH;
        if (Theme.hudBackground()) {
            context.fill(x, y, x + w, y + h, 0x99000000);
        }
        context.fill(x, y, x + 1, y + h, Theme.accent());
        int color = Theme.hudText();
        boolean shadow = Theme.hudShadow();
        int ty = y + PAD;
        for (int i = 0; i < cached.size(); i++) {
            context.drawText(mc.textRenderer, cached.get(i), x + PAD + 2, ty, color, shadow);
            ty += LINE_H;
        }
    }
}
