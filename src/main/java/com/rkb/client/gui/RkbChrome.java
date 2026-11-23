package com.rkb.client.gui;

import com.rkb.client.cloud.AuthState;
import com.rkb.client.cloud.CloudService;
import com.rkb.client.cosmetics.RkbLogo;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** Draws the shared RKB top bar for screens that are not the module menu. */
public final class RkbChrome {
    @FunctionalInterface
    public interface HitSink {
        void add(RkbUi.Rect rect, Runnable action);
    }

    private RkbChrome() {
    }

    public static void draw(DrawContext ctx, TextRenderer tr, RkbLayout.TopBar chrome, int mx, int my, int active,
                            HitSink sink) {
        RkbLogo.ensure();
        int accent = Theme.accent();
        RkbUi.Rect brand = chrome.brand();
        int logo = 22;
        if (RkbLogo.ready()) {
            RkbLogo.draw(ctx, brand.x(), brand.y() + 1, logo);
        } else {
            RkbUi.roundedFill(ctx, new RkbUi.Rect(brand.x(), brand.y() + 1, logo, logo), accent);
            ctx.drawText(tr, "R", brand.x() + 8, brand.y() + 7, 0xFF02101C, false);
        }
        int bx = brand.x() + logo + 8;
        ctx.drawText(tr, "RKB", bx, brand.y() + 4, RkbUi.TEXT, false);
        ctx.drawText(tr, "CLIENT", bx + tr.getWidth("RKB ") + 3, brand.y() + 4, accent, false);
        ctx.drawText(tr, "PVP UI", bx, brand.y() + 15, RkbUi.MUTED, false);
        sink.add(brand, RkbScreens::home);

        for (int i = 0; i < chrome.navItems().size(); i++) {
            RkbUi.Rect r = chrome.navItems().get(i);
            boolean hover = r.contains(mx, my);
            RkbUi.chip(ctx, r, RkbUi.NAV_LABELS[i], tr, i == active, hover);
            final int nav = i;
            sink.add(r, () -> RkbScreens.openNav(nav));
        }

        RkbUi.Rect account = chrome.account();
        boolean ah = account.contains(mx, my);
        RkbUi.panel(ctx, account, ah ? RkbUi.SURFACE_2 : RkbUi.SURFACE, ah ? accent : RkbUi.LINE);
        AuthState auth = CloudService.authState();
        RkbUi.statusDot(ctx, account.x() + 8, account.y() + 10, auth.tone());
        ctx.drawText(tr, RkbUi.fit(tr, auth.accountLabel(), account.width() - 24),
                account.x() + 18, account.y() + 8, auth.signedIn() ? RkbUi.TEXT : RkbUi.MUTED, false);
        sink.add(account, RkbScreens::account);

        RkbUi.Rect close = chrome.close();
        boolean ch = close.contains(mx, my);
        RkbUi.panel(ctx, close, ch ? RkbUi.SURFACE_2 : RkbUi.SURFACE, ch ? RkbUi.DANGER : RkbUi.LINE);
        ctx.drawText(tr, "X", close.x() + (close.width() - tr.getWidth("X")) / 2, close.y() + 8,
                ch ? RkbUi.DANGER : RkbUi.TEXT, false);
        sink.add(close, RkbScreens::close);
    }
}
