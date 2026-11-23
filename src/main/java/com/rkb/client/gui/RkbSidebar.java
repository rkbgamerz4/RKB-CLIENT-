package com.rkb.client.gui;

import com.rkb.client.VersionInfo;
import com.rkb.client.cloud.AuthState;
import com.rkb.client.cloud.CloudService;
import com.rkb.client.cosmetics.PlayerIdentity;
import com.rkb.client.cosmetics.RkbLogo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;

/** Shared left sidebar shell for redesigned RKB interior screens. */
public final class RkbSidebar {
    public static final NavItem[] ITEMS = {
            new NavItem("Singleplayer", "play", NavAction.SINGLEPLAYER),
            new NavItem("Multiplayer", "users", NavAction.MULTIPLAYER),
            new NavItem("Mods", "cube", NavAction.MODS),
            new NavItem("Settings", "globe", NavAction.MINECRAFT_SETTINGS),
            new NavItem("Cosmetics", "shirt", NavAction.COSMETICS),
            new NavItem("RKB Modules", "grid", NavAction.MODULES),
            new NavItem("RKB Settings", "sliders", NavAction.RKB_SETTINGS),
            new NavItem("Quit Game", "power", NavAction.QUIT)
    };

    public enum NavAction {
        SINGLEPLAYER, MULTIPLAYER, MODS, MINECRAFT_SETTINGS, COSMETICS, MODULES, RKB_SETTINGS, QUIT
    }

    public record NavItem(String label, String icon, NavAction action) {}

    @FunctionalInterface
    public interface HitSink {
        void add(RkbUi.Rect rect, Runnable action);
    }

    private RkbSidebar() {}

    public static RkbLayout.SidebarShell draw(DrawContext ctx, TextRenderer tr, int width, int height, int mouseX, int mouseY,
                                              NavAction active, HitSink hits) {
        RkbLayout.SidebarShell shell = RkbLayout.sidebarShell(width, height, ITEMS.length);
        int sw = shell.sidebarWidth();
        ctx.fill(0, 0, sw, height, RkbUi.SURFACE);
        ctx.fill(sw - 1, 0, sw, height, RkbUi.LINE);

        RkbLogo.ensure();
        int logo = 24;
        if (RkbLogo.ready()) {
            RkbLogo.draw(ctx, 16, 16, logo);
        }
        ctx.drawText(tr, RkbUi.menuText("RKB"), 46, 20, RkbUi.TEXT, false);
        ctx.drawText(tr, RkbUi.menuText("CLIENT"), 46 + tr.getWidth("RKB "), 20, RkbUi.CHROME_ACCENT, false);
        ctx.drawText(tr, RkbUi.menuText("MINECRAFT PVP"), 46, 31, RkbUi.DIM, false);

        String group = null;
        for (int i = 0; i < ITEMS.length; i++) {
            NavItem item = ITEMS[i];
            String g = groupFor(item.action());
            RkbUi.Rect r = shell.navItems().get(i);
            boolean selected = item.action() == active;
            boolean hover = r.contains(mouseX, mouseY);
            if (selected) {
                ctx.fill(r.x(), r.y(), r.right(), r.bottom(), RkbUi.SURFACE_3);
                ctx.fill(r.x(), r.y() + 3, r.x() + 2, r.bottom() - 3, RkbUi.CHROME_ACCENT);
            } else if (hover) {
                ctx.fill(r.x(), r.y(), r.right(), r.bottom(), 0xFF1F2530);
            }
            int color = selected ? RkbUi.TEXT : RkbUi.MUTED;
            drawNavIcon(ctx, item.icon(), r.x() + 10, r.y() + 6, selected ? RkbUi.CHROME_ACCENT : RkbUi.MUTED);
            ctx.drawText(tr, RkbUi.fit(tr, item.label(), r.width() - 36), r.x() + 32, r.y() + 10, color, false);
            final NavAction action = item.action();
            hits.add(r, () -> activate(action));
        }

        // account card
        RkbUi.Rect ac = shell.account();
        RkbUi.panel(ctx, ac, RkbUi.SURFACE_2, RkbUi.LINE);
        AuthState auth = CloudService.authState();
        int dotColor = auth.signedIn() ? RkbUi.SUCCESS : RkbUi.DIM;
        ctx.fill(ac.x() + 10, ac.y() + 12, ac.x() + 16, ac.y() + 18, dotColor);
        ctx.drawText(tr, RkbUi.menuText("RKB CLOUD"), ac.x() + 22, ac.y() + 10, RkbUi.DIM, false);
        ctx.drawText(tr, RkbUi.fit(tr, auth.signedIn() ? "Signed in" : "Signed out", ac.width() - 20), ac.x() + 10, ac.y() + 24, RkbUi.TEXT, false);
        String who = auth.signedIn() ? auth.accountLabel() : "Local mode";
        ctx.drawText(tr, RkbUi.fit(tr, who, ac.width() - 20), ac.x() + 10, ac.y() + 35, RkbUi.MUTED, false);
        ctx.drawText(tr, RkbUi.menuText("Manage account"), ac.x() + 10, ac.y() + 46, RkbUi.CHROME_ACCENT, false);
        hits.add(ac, RkbScreens::account);

        // footer
        RkbUi.Rect ft = shell.footer();
        ctx.fill(ft.x(), ft.y(), ft.right(), ft.bottom(), RkbUi.BG);
        ctx.fill(ft.x(), ft.y(), ft.right(), ft.y() + 1, RkbUi.LINE);
        String left = "RKB CLIENT " + VersionInfo.mod() + "  ·  Minecraft " + VersionInfo.minecraft();
        String right = "FPS " + MinecraftClient.getInstance().getCurrentFps() + "  ·  " + (auth.signedIn() ? "RKB Cloud" : "Local mode");
        ctx.drawText(tr, RkbUi.fit(tr, left, ft.width() / 2 - 12), ft.x() + 12, ft.y() + 10, RkbUi.DIM, false);
        int rw = tr.getWidth(right);
        ctx.drawText(tr, RkbUi.fit(tr, right, ft.width() / 2 - 12), ft.right() - 12 - Math.min(rw, ft.width() / 2 - 12), ft.y() + 10, RkbUi.DIM, false);
        return shell;
    }

    private static String groupFor(NavAction action) {
        return switch (action) {
            case SINGLEPLAYER, MULTIPLAYER, MODS, MINECRAFT_SETTINGS -> "MINECRAFT";
            case COSMETICS, MODULES, RKB_SETTINGS -> "RKB";
            case QUIT -> "SYSTEM";
        };
    }

    private static void activate(NavAction action) {
        MinecraftClient mc = MinecraftClient.getInstance();
        switch (action) {
            case SINGLEPLAYER -> mc.setScreen(new SelectWorldScreen(mc.currentScreen));
            case MULTIPLAYER -> mc.setScreen(new MultiplayerScreen(mc.currentScreen));
            case MODS -> ModMenuBridge.openOrWarn(mc.currentScreen);
            case MINECRAFT_SETTINGS -> mc.setScreen(new OptionsScreen(mc.currentScreen, mc.options));
            case COSMETICS -> mc.setScreen(new CosmeticsScreen());
            case MODULES -> mc.setScreen(new RkbModulesScreen());
            case RKB_SETTINGS -> mc.setScreen(new RkbSettingsScreen());
            case QUIT -> mc.scheduleStop();
        }
    }

    private static void drawNavIcon(DrawContext ctx, String icon, int x, int y, int color) {
        // simple 16x16 line icons
        switch (icon) {
            case "play" -> {
                ctx.fill(x + 4, y + 2, x + 6, y + 14, color);
                ctx.fill(x + 6, y + 4, x + 8, y + 12, color);
                ctx.fill(x + 8, y + 6, x + 10, y + 10, color);
                ctx.fill(x + 10, y + 7, x + 12, y + 9, color);
            }
            case "users" -> {
                drawRectOutline(ctx, x + 5, y + 2, 6, 6, color);
                ctx.fill(x + 3, y + 10, x + 13, y + 11, color);
                ctx.fill(x + 2, y + 11, x + 14, y + 14, color);
            }
            case "cube" -> drawRectOutline(ctx, x + 2, y + 2, 12, 12, color);
            case "globe" -> drawRectOutline(ctx, x + 2, y + 2, 12, 12, color);
            case "shirt" -> {
                ctx.fill(x + 4, y + 2, x + 12, y + 4, color);
                ctx.fill(x + 3, y + 4, x + 13, y + 14, color);
                ctx.fill(x + 6, y + 4, x + 10, y + 14, RkbUi.SURFACE);
            }
            case "grid" -> {
                for (int r = 0; r < 2; r++) for (int c = 0; c < 2; c++)
                    drawRectOutline(ctx, x + 2 + c * 7, y + 2 + r * 7, 5, 5, color);
            }
            case "sliders" -> {
                ctx.fill(x + 2, y + 4, x + 14, y + 5, color);
                ctx.fill(x + 2, y + 8, x + 14, y + 9, color);
                ctx.fill(x + 2, y + 12, x + 14, y + 13, color);
                ctx.fill(x + 5, y + 3, x + 7, y + 6, color);
                ctx.fill(x + 9, y + 7, x + 11, y + 10, color);
                ctx.fill(x + 6, y + 11, x + 8, y + 14, color);
            }
            case "power" -> {
                drawRectOutline(ctx, x + 3, y + 4, 10, 10, color);
                ctx.fill(x + 7, y + 1, x + 9, y + 8, color);
            }
            default -> drawRectOutline(ctx, x + 2, y + 2, 12, 12, color);
        }
    }

    private static void drawRectOutline(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }
}
