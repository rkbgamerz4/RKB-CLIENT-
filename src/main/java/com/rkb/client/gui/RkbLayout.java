package com.rkb.client.gui;

import java.util.ArrayList;
import java.util.List;

/** Pure responsive geometry shared by the RKB title screen and the in-game chrome. */
public final class RkbLayout {
    public record MainMenu(RkbUi.Rect brand, RkbUi.Rect hero, RkbUi.Rect account, RkbUi.Rect status,
                           List<RkbUi.Rect> buttons, boolean compact) {
    }

    public record TopBar(RkbUi.Rect brand, RkbUi.Rect account, RkbUi.Rect close, RkbUi.Rect nav,
                         List<RkbUi.Rect> navItems, int contentTop, boolean stacked) {
    }

    private RkbLayout() {
    }

    public static MainMenu mainMenu(int screenWidth, int screenHeight) {
        int w = Math.max(320, screenWidth);
        int h = Math.max(240, screenHeight);
        int margin = w < 560 ? 12 : 18;
        boolean compact = w < 620 || h < 340;
        int contentW = w - margin * 2;
        int contentH = h - margin * 2;

        List<RkbUi.Rect> buttons = new ArrayList<>(6);
        RkbUi.Rect brand;
        RkbUi.Rect hero;
        RkbUi.Rect account;
        RkbUi.Rect status;

        if (compact) {
            int accountH = 34;
            int gap = 6;
            brand = new RkbUi.Rect(margin, margin, contentW, 30);
            status = new RkbUi.Rect(Math.max(margin, w - margin - 96), margin + 8, 96, 14);
            hero = new RkbUi.Rect(margin, brand.bottom() + 6, contentW, 0);
            int accountY = h - margin - accountH;
            int areaTop = hero.y();
            int areaH = Math.max(72, accountY - 8 - areaTop);
            int rows = 3;
            int buttonH = Math.max(22, Math.min(34, (areaH - (rows - 1) * gap) / rows));
            int totalH = rows * buttonH + (rows - 1) * gap;
            int startY = areaTop + Math.max(0, (areaH - totalH) / 2);
            int buttonW = (contentW - gap) / 2;
            for (int i = 0; i < 6; i++) {
                int col = i % 2;
                int row = i / 2;
                buttons.add(new RkbUi.Rect(margin + col * (buttonW + gap), startY + row * (buttonH + gap),
                        buttonW, buttonH));
            }
            account = new RkbUi.Rect(margin, accountY, contentW, accountH);
        } else {
            int menuW = Math.max(250, Math.min(320, contentW * 34 / 100));
            int menuX = w - margin - menuW;
            int heroW = menuX - margin - Math.max(18, contentW / 30);
            int buttonH = Math.max(30, Math.min(38, (contentH - 96) / 6 - 6));
            int totalH = 6 * buttonH + 5 * 7;
            int startY = Math.max(margin + 38, (h - totalH) / 2 - 10);
            for (int i = 0; i < 6; i++) {
                buttons.add(new RkbUi.Rect(menuX, startY + i * (buttonH + 7), menuW, buttonH));
            }
            hero = new RkbUi.Rect(margin + 6, margin + 34, Math.max(180, heroW - 6), h - margin * 2 - 40);
            brand = new RkbUi.Rect(margin, margin, heroW, 30);
            account = new RkbUi.Rect(hero.x(), hero.bottom() - 74, hero.width(), 66);
            status = new RkbUi.Rect(w - margin - 150, margin + 4, 150, 20);
        }
        return new MainMenu(brand, hero, account, status, List.copyOf(buttons), compact);
    }

    public static TopBar topBar(int screenWidth, int screenHeight) {
        int w = Math.max(320, screenWidth);
        boolean stacked = w < 900;
        int margin = 8;
        int closeW = 26;
        int accountW = w < 560 ? 112 : 168;
        RkbUi.Rect close = new RkbUi.Rect(w - margin - closeW, margin, closeW, 26);
        RkbUi.Rect account = new RkbUi.Rect(close.x() - 6 - accountW, margin, accountW, 26);
        RkbUi.Rect brand = new RkbUi.Rect(margin, margin, Math.min(190, Math.max(116, w / 5)), 26);
        RkbUi.Rect nav;
        List<RkbUi.Rect> items = new ArrayList<>(RkbUi.NAV_LABELS.length);
        if (stacked) {
            int rows = w < 440 ? 2 : 1;
            int navH = rows * RkbUi.NAV_H + (rows - 1) * 4;
            nav = new RkbUi.Rect(margin, margin + 32, w - margin * 2, navH);
        } else {
            nav = new RkbUi.Rect(brand.right() + 10, margin + 1,
                    Math.max(120, account.x() - brand.right() - 18), RkbUi.NAV_H);
        }
        int gap = stacked ? 3 : 4;
        if (nav.height() > RkbUi.NAV_H) {
            for (int i = 0; i < RkbUi.NAV_LABELS.length; i++) {
                int row = i < 4 ? 0 : 1;
                int col = i < 4 ? i : i - 4;
                int count = row == 0 ? 4 : 3;
                int itemW = (nav.width() - gap * (count - 1)) / count;
                items.add(new RkbUi.Rect(nav.x() + col * (itemW + gap), nav.y() + row * (RkbUi.NAV_H + 4),
                        itemW, RkbUi.NAV_H));
            }
        } else {
            int itemW = Math.max(24, (nav.width() - gap * (RkbUi.NAV_LABELS.length - 1))
                    / RkbUi.NAV_LABELS.length);
            for (int i = 0; i < RkbUi.NAV_LABELS.length; i++) {
                items.add(new RkbUi.Rect(nav.x() + i * (itemW + gap), nav.y(), itemW, nav.height()));
            }
        }
        int contentTop = nav.bottom() + 8;
        if (!stacked) {
            contentTop = Math.max(nav.bottom(), account.bottom()) + 10;
        }
        return new TopBar(brand, account, close, nav, List.copyOf(items), contentTop, stacked);
    }

    public static int safeBottom(int screenHeight) {
        return screenHeight - 18;
    }

    public record SidebarShell(RkbUi.Rect sidebar, RkbUi.Rect brand, RkbUi.Rect nav, List<RkbUi.Rect> navItems,
                               RkbUi.Rect account, RkbUi.Rect content, RkbUi.Rect footer, int sidebarWidth,
                               boolean compact) {
    }

    /** Responsive left-sidebar shell for interior RKB screens. */
    public static SidebarShell sidebarShell(int screenWidth, int screenHeight, int navCount) {
        int w = Math.max(320, screenWidth);
        int h = Math.max(240, screenHeight);
        boolean compact = w < 960 || h < 420;
        int sidebarW = compact ? Math.max(54, Math.min(170, w * 22 / 100)) : 216;
        if (w < 720) {
            sidebarW = Math.max(48, Math.min(150, w * 28 / 100));
        }
        RkbUi.Rect sidebar = new RkbUi.Rect(0, 0, sidebarW, h);
        RkbUi.Rect brand = new RkbUi.Rect(14, 14, sidebarW - 28, 34);
        int navY = 72;
        int itemH = 28;
        int gap = 4;
        List<RkbUi.Rect> items = new ArrayList<>(navCount);
        for (int i = 0; i < navCount; i++) {
            items.add(new RkbUi.Rect(10, navY + i * (itemH + gap), sidebarW - 20, itemH));
        }
        int navBottom = navCount == 0 ? navY : items.get(items.size() - 1).bottom();
        RkbUi.Rect nav = new RkbUi.Rect(10, navY, sidebarW - 20, Math.max(0, navBottom - navY));
        int footerH = 28;
        RkbUi.Rect footer = new RkbUi.Rect(sidebarW, h - footerH, w - sidebarW, footerH);
        int accountH = 58;
        RkbUi.Rect account = new RkbUi.Rect(12, h - footerH - accountH - 12, sidebarW - 24, accountH);
        int contentX = sidebarW + 16;
        int contentW = Math.max(120, w - contentX - 16);
        RkbUi.Rect content = new RkbUi.Rect(contentX, 16, contentW, Math.max(80, h - 16 - footerH));
        return new SidebarShell(sidebar, brand, nav, List.copyOf(items), account, content, footer, sidebarW, compact);
    }
}
