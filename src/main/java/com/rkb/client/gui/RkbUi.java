package com.rkb.client.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Shared visual language for every RKB screen.
 *
 * Rounded panels are approximated with a few small fills so the client needs no extra texture or dependency. All
 * helpers are cheap immediate-mode draws and must be called from the Minecraft render thread only.
 */
public final class RkbUi {
    public static final int BG = 0xFF070A12;
    public static final int SURFACE = 0xFF0E1422;
    public static final int SURFACE_2 = 0xFF141D30;
    public static final int SURFACE_3 = 0xFF0A101C;
    public static final int ACCENT = 0xFF00A8FF;
    public static final int ACCENT_2 = 0xFF246BFF;
    public static final int TEXT = 0xFFF3F6FF;
    public static final int MUTED = 0xFF96A8BE;
    public static final int LINE = 0xFF1B263D;
    public static final int SUCCESS = 0xFF43D99A;
    public static final int WARNING = 0xFFFFC857;
    public static final int DANGER = 0xFFFF7B7B;

    public static final int TOP_H = 42;
    public static final int NAV_H = 26;
    public static final String[] NAV_LABELS = {"MAIN MENU", "SETTINGS", "PROFILES", "HUD EDITOR", "WAYPOINTS",
            "COSMETICS", "ABOUT"};
    /** Index in NAV_LABELS, or -1 for a screen that is not one of the seven main pages. */
    public static final int NAV_NONE = -1;

    public record Rect(int x, int y, int width, int height) {
        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public boolean contains(int px, int py) {
            return width > 0 && height > 0 && px >= x && px < right() && py >= y && py < bottom();
        }

        public Rect inset(int n) {
            return new Rect(x + n, y + n, Math.max(0, width - n * 2), Math.max(0, height - n * 2));
        }
    }

    private RkbUi() {
    }

    public static void background(DrawContext ctx, int width, int height, long millis) {
        ctx.fillGradient(0, 0, width, height, 0xFF070A12, 0xFF0B1220);
        float phase = (millis % 12000L) / 12000f;
        int glowX = (int) (width * (0.18f + 0.08f * Math.sin(phase * Math.PI * 2)));
        int glowY = (int) (height * 0.08f);
        int glowW = Math.max(120, width / 3);
        int glowH = Math.max(80, height / 2);
        ctx.fillGradient(glowX, glowY, glowX + glowW, glowY + glowH, 0x2800A8FF, 0x0000A8FF);
        ctx.fillGradient(width - glowW, height - glowH, width, height, 0x18246BFF, 0x00246BFF);

        // Subtle diagonal pinstripes: texture without a panorama dependency.
        for (int x = -height; x < width; x += 52) {
            ctx.fill(x + 18, 0, x + 19, height, 0x0800A8FF);
        }
        ctx.fill(0, height - 2, width, height, 0xAA00A8FF);
    }

    public static void panel(DrawContext ctx, Rect r, int fill, int border) {
        if (r == null || r.width() <= 0 || r.height() <= 0) {
            return;
        }
        roundedFill(ctx, r, fill);
        if (border != 0) {
            roundedOutline(ctx, r, border);
        }
    }

    public static void roundedFill(DrawContext ctx, Rect r, int color) {
        int x = r.x();
        int y = r.y();
        int x2 = r.right();
        int y2 = r.bottom();
        ctx.fill(x + 3, y, x2 - 3, y2, color);
        ctx.fill(x + 1, y + 1, x2 - 1, y2 - 1, color);
        ctx.fill(x, y + 3, x2, y2 - 3, color);
    }

    public static void roundedOutline(DrawContext ctx, Rect r, int color) {
        int x = r.x();
        int y = r.y();
        int x2 = r.right();
        int y2 = r.bottom();
        ctx.fill(x + 4, y, x2 - 4, y + 1, color);
        ctx.fill(x + 4, y2 - 1, x2 - 4, y2, color);
        ctx.fill(x, y + 4, x + 1, y2 - 4, color);
        ctx.fill(x2 - 1, y + 4, x2, y2 - 4, color);
        ctx.fill(x + 2, y + 1, x + 4, y + 2, color);
        ctx.fill(x2 - 4, y + 1, x2 - 2, y + 2, color);
        ctx.fill(x + 2, y2 - 2, x + 4, y2 - 1, color);
        ctx.fill(x2 - 4, y2 - 2, x2 - 2, y2 - 1, color);
    }

    public static void button(DrawContext ctx, TextRenderer tr, Rect r, String label, boolean hover,
                              boolean primary, boolean enabled) {
        if (r == null || r.width() <= 0) {
            return;
        }
        int fill;
        int border;
        int text;
        if (!enabled) {
            fill = SURFACE_3;
            border = LINE;
            text = 0xFF5A6880;
        } else if (primary) {
            fill = hover ? 0xFF16B5FF : ACCENT;
            border = hover ? 0xFF8AD9FF : ACCENT_2;
            text = 0xFF02101C;
        } else {
            fill = hover ? SURFACE_2 : SURFACE;
            border = hover ? ACCENT : LINE;
            text = hover ? TEXT : 0xFFDCE7F8;
        }
        panel(ctx, r, fill, border);
        if (label != null && !label.isEmpty()) {
            String shown = fit(tr, label, r.width() - 16);
            ctx.drawText(tr, shown, r.x() + (r.width() - tr.getWidth(shown)) / 2,
                    r.y() + (r.height() - 9) / 2, text, false);
        }
    }

    public static int buttonWidth(TextRenderer tr, String label) {
        return Math.max(64, tr.getWidth(label) + 24);
    }

    public static void toggle(DrawContext ctx, int x, int y, int w, int h, float on, int accent) {
        int track = blend(0xFF26324A, accent, on);
        roundedFill(ctx, new Rect(x, y, w, h), track);
        int knob = h - 4;
        int kx = x + 2 + Math.round((w - knob - 4) * clamp01(on));
        roundedFill(ctx, new Rect(kx, y + 2, knob, knob), on > 0.5f ? 0xFF04101B : 0xFFD7E2F2);
    }

    public static void slider(DrawContext ctx, int x, int y, int w, float value, int accent) {
        int cy = y + 4;
        ctx.fill(x, cy, x + w, cy + 3, 0xFF26324A);
        int filled = Math.round(w * clamp01(value));
        if (filled > 0) {
            ctx.fill(x, cy, x + filled, cy + 3, accent);
        }
        int kx = x + Math.max(0, Math.min(w - 2, filled));
        ctx.fill(kx - 3, cy - 3, kx + 3, cy + 6, 0xFFF3F6FF);
        ctx.fill(kx - 2, cy - 2, kx + 2, cy + 5, accent);
    }

    public static void chip(DrawContext ctx, Rect r, String label, TextRenderer tr, boolean active, boolean hover) {
        panel(ctx, r, active ? ACCENT : hover ? SURFACE_2 : SURFACE, active ? ACCENT : LINE);
        String shown = fit(tr, label, r.width() - 12);
        ctx.drawText(tr, shown, r.x() + (r.width() - tr.getWidth(shown)) / 2,
                r.y() + (r.height() - 9) / 2, active ? 0xFF02101C : hover ? TEXT : MUTED, false);
    }

    public static void statusDot(DrawContext ctx, int x, int y, int color) {
        ctx.fill(x, y + 1, x + 1, y + 4, color);
        ctx.fill(x + 1, y, x + 4, y + 5, color);
        ctx.fill(x + 4, y + 1, x + 5, y + 4, color);
    }

    public static String fit(TextRenderer tr, String s, int maxW) {
        if (s == null) {
            return "";
        }
        if (maxW <= 0 || tr.getWidth(s) <= maxW) {
            return s;
        }
        int ew = tr.getWidth("..");
        int n = s.length();
        while (n > 0 && tr.getWidth(s.substring(0, n)) + ew > maxW) {
            n--;
        }
        return s.substring(0, n) + "..";
    }

    public static int blend(int a, int b, float t) {
        float f = clamp01(t);
        int aa = (int) (((a >>> 24) & 255) + (((b >>> 24) & 255) - ((a >>> 24) & 255)) * f);
        int ar = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * f);
        int ag = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * f);
        int ab = (int) ((a & 255) + ((b & 255) - (a & 255)) * f);
        return (aa << 24) | (ar << 16) | (ag << 8) | ab;
    }

    public static float clamp01(float f) {
        return Math.max(0f, Math.min(1f, f));
    }
}
