package com.rkb.client.gui;

import java.util.List;

/**
 * Pure, testable geometry for the single-screen cosmetics UI.
 *
 * The panel keeps a safe margin from the screen edges (important on touch devices with launcher overlays), uses one
 * three-way tab strip and splits the body into a fixed preview plus a scrollable list. Nothing here touches Minecraft.
 */
public final class CosmeticsLayout {
    public static final String[] TABS = {"Skin", "Cape", "Redeem"};

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

        public boolean overlaps(Rect other) {
            return other != null && x < other.right() && other.x < right() && y < other.bottom() && other.y < bottom();
        }
    }

    public record Layout(Rect panel, Rect header, Rect tabs, List<Rect> tabRects, Rect content,
                         Rect preview, Rect list, boolean compact) {
    }

    private CosmeticsLayout() {
    }

    public static Layout of(int screenWidth, int screenHeight) {
        int w = Math.max(160, screenWidth);
        int h = Math.max(160, screenHeight);
        int margin = clamp(w / 90, 6, 12);
        int panelW = Math.min(w - margin * 2, 1040);
        int panelH = Math.min(h - margin * 2, 680);
        int px = (w - panelW) / 2;
        int py = (h - panelH) / 2;
        int pad = clamp(panelW / 70, 8, 14);

        Rect panel = new Rect(px, py, panelW, panelH);
        int headerH = clamp(panelH / 8, 44, 58);
        Rect header = new Rect(px + pad, py + 3, panelW - pad * 2, headerH - 6);

        int tabH = clamp(panelH / 11, 28, 36);
        int tabY = py + headerH + 2;
        int tabGap = 5;
        int tabW = (panelW - pad * 2 - tabGap * (TABS.length - 1)) / TABS.length;
        Rect tabs = new Rect(px + pad, tabY, panelW - pad * 2, tabH);
        List<Rect> tabRects = new java.util.ArrayList<>(TABS.length);
        for (int i = 0; i < TABS.length; i++) {
            tabRects.add(new Rect(tabs.x() + i * (tabW + tabGap), tabY, tabW, tabH));
        }

        int contentY = tabs.bottom() + 8;
        Rect content = new Rect(px + pad, contentY, panelW - pad * 2,
                Math.max(40, panel.bottom() - pad - contentY));
        boolean compact = content.width() < 520;
        Rect preview;
        Rect list;
        if (compact) {
            int previewH = clamp(content.height() * 2 / 5, 132, 190);
            preview = new Rect(content.x(), content.y(), content.width(), previewH);
            list = new Rect(content.x(), preview.bottom() + 8, content.width(),
                    Math.max(30, content.bottom() - preview.bottom() - 8));
        } else {
            int previewW = clamp(content.width() * 36 / 100, 190, 270);
            preview = new Rect(content.x(), content.y(), previewW, content.height());
            list = new Rect(preview.right() + 10, content.y(),
                    Math.max(60, content.right() - preview.right() - 10), content.height());
        }
        return new Layout(panel, header, tabs, List.copyOf(tabRects), content, preview, list, compact);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
