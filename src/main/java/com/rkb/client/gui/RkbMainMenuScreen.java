package com.rkb.client.gui;

import com.rkb.client.VersionInfo;
import com.rkb.client.cosmetics.PlayerIdentity;
import com.rkb.client.cosmetics.RkbLogo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * RKB main menu, matched to the supplied landscape reference: panorama background, top-right account/action bar,
 * centered RKB logo and brand, one vertical menu column and a version/FPS footer.
 */
public class RkbMainMenuScreen extends Screen {
    private static final int ACCENT = 0xFF2585FF;
    private static final int ICON_BLUE = 0xFF91C3FF;
    private static final int HOVER_BLUE = 0xFF1F68CF;
    private static final int HOVER_BORDER = 0xFF62A7FF;
    private static final int QUIT_BG = 0xFF5B141F;
    private static final int QUIT_BORDER = 0xFFB42B3D;
    private static final int QUIT_HOVER = 0xFFA51E36;

    private record Hit(RkbUi.Rect rect, Runnable action) {
        boolean contains(int x, int y) { return rect.contains(x, y); }
    }

    private record MenuEntry(String label, String icon, Runnable action, boolean quit) {}

    private final List<Hit> hits = new ArrayList<>();

    public RkbMainMenuScreen() { super(Text.literal("RKB CLIENT")); }

    @Override public boolean shouldPause() { return false; }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (MinecraftClient.getInstance().world != null) close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            int mx = (int) click.x();
            int my = (int) click.y();
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (h.contains(mx, my)) { h.action().run(); return true; }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        hits.clear();
        drawBackdrop(ctx, delta);
        TextRenderer tr = textRenderer;
        float s = scale();

        drawTopBar(ctx, tr, mouseX, mouseY, s);
        drawCenter(ctx, tr, mouseX, mouseY, s);
        drawFooter(ctx, tr, s);
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawBackdrop(DrawContext ctx, float delta) {
        boolean drew = false;
        if (MinecraftClient.getInstance().world == null) {
            try {
                renderPanoramaBackground(ctx, delta);
                drew = true;
            } catch (Throwable ignored) {
                drew = false;
            }
        }
        if (!drew) {
            ctx.fill(0, 0, width, height, RkbUi.BG);
        }
        // Reference-style vignette: a quiet darkening toward the edges without glow or gradients.
        ctx.fill(0, 0, width, height, 0x3002050C);
        ctx.fill(0, 0, width, Math.max(1, height / 10), 0x2202050C);
        ctx.fill(0, height - Math.max(1, height / 8), width, height, 0x3302050C);
    }

    private float scale() {
        return Math.max(0.35f, Math.min(1.15f, height / 432f));
    }

    private static int px(float value, float s) {
        return Math.max(1, Math.round(value * s));
    }

    private void drawTopBar(DrawContext ctx, TextRenderer tr, int mx, int my, float s) {
        MinecraftClient mc = MinecraftClient.getInstance();
        String name = PlayerIdentity.username();
        if (name == null || name.isEmpty()) name = "Player";

        int h = px(28, s);
        int pad = px(6, s);
        int avatar = px(18, s);
        int gap = px(5, s);
        int chevronW = px(7, s);
        int nameW = tr.getWidth(RkbUi.menuText(name));
        int profileW = pad + avatar + gap + nameW + gap + chevronW + pad;
        int top = px(11, s);
        int right = width - px(13, s);
        int x = right - profileW;

        RkbUi.Rect profile = new RkbUi.Rect(x, top, profileW, h);
        boolean profileHover = profile.contains(mx, my);
        RkbUi.roundedFill(ctx, profile, profileHover ? 0xCC111A28 : 0xAA080B13);
        RkbUi.roundedOutline(ctx, profile, 0x22FFFFFF);
        drawAvatar(ctx, profile.x() + pad, profile.y() + (h - avatar) / 2, avatar);
        int tx = profile.x() + pad + avatar + gap;
        ctx.drawText(tr, RkbUi.menuText(name), tx, profile.y() + (h - 8) / 2, RkbUi.TEXT, false);
        drawChevron(ctx, tx + nameW + gap, profile.y() + h / 2, s, RkbUi.MUTED);
        hits.add(new Hit(profile, RkbScreens::account));

        int gapX = px(8, s);
        int sepW = Math.max(1, px(1, s));
        int sepH = px(18, s);
        int iconW = px(15, s);
        int iconH = px(21, s);
        int cursor = profile.x() - gapX;

        cursor = drawTopIcon(ctx, mx, my, cursor, top + (h - iconH) / 2, iconW, iconH, "logout", RkbUi.MUTED,
                RkbScreens::account);
        cursor -= gapX;
        ctx.fill(cursor, top + (h - sepH) / 2, cursor + sepW, top + (h + sepH) / 2, 0x55FFFFFF);
        cursor -= gapX;
        cursor = drawTopIcon(ctx, mx, my, cursor, top + (h - iconH) / 2, iconW, iconH, "sliders", ICON_BLUE,
                () -> mc.setScreen(new RkbSettingsScreen()));
        cursor -= gapX;
        cursor = drawTopIcon(ctx, mx, my, cursor, top + (h - iconH) / 2, iconW, iconH, "rkblogo", ICON_BLUE,
                () -> mc.setScreen(new RkbModulesScreen()));
        cursor -= gapX;
        drawTopIcon(ctx, mx, my, cursor, top + (h - iconH) / 2, iconW, iconH, "shirt", ICON_BLUE,
                () -> mc.setScreen(new CosmeticsScreen()));
        cursor -= gapX;
        ctx.fill(cursor, top + (h - sepH) / 2, cursor + sepW, top + (h + sepH) / 2, 0x55FFFFFF);
    }

    private int drawTopIcon(DrawContext ctx, int mx, int my, int right, int y, int w, int h, String icon, int color,
                            Runnable action) {
        RkbUi.Rect r = new RkbUi.Rect(right - w, y, w, h);
        boolean hover = r.contains(mx, my);
        drawIcon(ctx, icon, r.x() + w / 2, r.y() + h / 2, Math.min(w, h), hover ? 0xFFFFFFFF : color);
        hits.add(new Hit(r, action));
        return r.x();
    }

    private void drawCenter(DrawContext ctx, TextRenderer tr, int mx, int my, float s) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int menuW = Math.min(px(185, s), width - px(40, s));
        if (menuW < px(120, s)) menuW = Math.max(px(100, s), width - px(16, s));

        // Matches the reference composition: five centered entries. Cosmetics, RKB Modules, RKB Settings and
        // Account stay reachable from the top-right action bar, so no destination is lost.
        MenuEntry[] entries = {
                new MenuEntry("Singleplayer", "play", () -> mc.setScreen(new SelectWorldScreen(this)), false),
                new MenuEntry("Multiplayer", "users", () -> mc.setScreen(new MultiplayerScreen(this)), false),
                new MenuEntry("Mods", "mods", () -> ModMenuBridge.openOrWarn(this), false),
                new MenuEntry("Settings", "sliders", () -> mc.setScreen(new OptionsScreen(this, mc.options)), false),
                new MenuEntry("Quit Game", "power", mc::scheduleStop, true)
        };

        int logoH = px(56, s);
        float brandScale = 2.5f * s;
        int brandH = Math.round(9f * brandScale);
        int brandGap = px(18, s);
        int gap = Math.max(2, px(3, s));
        int footerReserve = px(22, s);
        int topLimit = px(8, s);

        int maxBtn = px(29, s);
        int minBtn = Math.max(10, px(15, s));
        int avail = height - footerReserve - topLimit - logoH - brandH - brandGap - gap * (entries.length - 1);
        int btnH = Math.max(minBtn, Math.min(maxBtn, avail / entries.length));
        int menuH = entries.length * btnH + gap * (entries.length - 1);
        int total = logoH + brandH + brandGap + menuH;
        int blockTop = Math.max(topLimit, (int) (height * 0.515f - total / 2f));

        int cx = width / 2;
        int logoX = cx - logoH / 2;
        RkbLogo.ensure();
        if (RkbLogo.ready()) {
            RkbLogo.draw(ctx, logoX, blockTop, logoH);
        } else {
            drawIcon(ctx, "rkblogo", cx, blockTop + logoH / 2, logoH, ICON_BLUE);
        }

        int brandY = blockTop + logoH + px(2, s);
        MutableText rkb = RkbUi.menuText("RKB").setStyle(RkbUi.menuText("RKB").getStyle()
                .withBold(true).withItalic(true));
        MutableText client = RkbUi.menuText(" CLIENT").setStyle(RkbUi.menuText(" CLIENT").getStyle()
                .withBold(true).withItalic(true));
        int rkbW = Math.round(tr.getWidth(rkb) * brandScale);
        int clientW = Math.round(tr.getWidth(client) * brandScale);
        int brandX = cx - (rkbW + clientW) / 2;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(brandX, brandY);
        ctx.getMatrices().scale(brandScale, brandScale);
        ctx.drawText(tr, rkb, 0, 0, ACCENT, true);
        ctx.drawText(tr, client, tr.getWidth(rkb), 0, RkbUi.TEXT, true);
        ctx.getMatrices().popMatrix();

        int menuTop = brandY + brandH + brandGap;
        int x = cx - menuW / 2;
        for (int i = 0; i < entries.length; i++) {
            MenuEntry e = entries[i];
            RkbUi.Rect r = new RkbUi.Rect(x, menuTop + i * (btnH + gap), menuW, btnH);
            drawMenuButton(ctx, tr, r, e, mx, my, s);
        }
    }

    private void drawMenuButton(DrawContext ctx, TextRenderer tr, RkbUi.Rect r, MenuEntry e, int mx, int my, float s) {
        boolean hover = r.contains(mx, my);
        int y = hover ? r.y() - 1 : r.y();
        RkbUi.Rect rr = new RkbUi.Rect(r.x(), y, r.width(), r.height());
        int fill;
        int border;
        if (e.quit()) {
            fill = hover ? QUIT_HOVER : QUIT_BG;
            border = QUIT_BORDER;
        } else {
            fill = hover ? HOVER_BLUE : 0xEB070A12;
            border = hover ? HOVER_BORDER : 0x0CFFFFFF;
        }
        RkbUi.roundedFill(ctx, rr, fill);
        RkbUi.roundedOutline(ctx, rr, border);
        int colW = Math.max(px(18, s), Math.round(r.width() * 0.145f));
        int iconSize = Math.max(10, Math.min(px(15, s), rr.height() - 4));
        drawIcon(ctx, e.icon(), rr.x() + colW / 2, rr.y() + rr.height() / 2, iconSize, ICON_BLUE);
        MutableText label = RkbUi.menuText(e.label());
        int lw = tr.getWidth(label);
        int lx = rr.x() + colW + (rr.width() - colW * 2 - lw) / 2;
        ctx.drawText(tr, label, lx, rr.y() + (rr.height() - 8) / 2, e.quit() ? 0xFFFFD7DC : RkbUi.TEXT, false);
        hits.add(new Hit(rr, e.action()));
    }

    private void drawFooter(DrawContext ctx, TextRenderer tr, float s) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int y = height - px(12, s) - 9;
        String left = "RKB Client v" + VersionInfo.mod() + "   |   Minecraft " + VersionInfo.minecraft();
        int ping = 0;
        if (mc.getNetworkHandler() != null && mc.player != null) {
            var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (entry != null) ping = entry.getLatency();
        }
        String right = "FPS: " + mc.getCurrentFps() + "   |   Ping: " + ping + "ms";
        ctx.drawText(tr, RkbUi.menuText(left), px(13, s), y, 0xFFE3E6ED, true);
        int rw = tr.getWidth(RkbUi.menuText(right));
        ctx.drawText(tr, RkbUi.menuText(right), width - px(13, s) - rw, y, 0xFFE3E6ED, true);
    }

    // ------------------------------------------------------------------ small vector icons

    private void drawAvatar(DrawContext ctx, int x, int y, int size) {
        RkbUi.roundedFill(ctx, new RkbUi.Rect(x, y, size, size), 0xFF263247);
        int u = Math.max(1, size / 8);
        ctx.fill(x + 2 * u, y + 2 * u, x + 6 * u, y + 4 * u, 0xFF3B2A22); // hair
        ctx.fill(x + 2 * u, y + 4 * u, x + 6 * u, y + 7 * u, 0xFFC38D70); // face
        ctx.fill(x + 3 * u, y + 5 * u, x + 4 * u, y + 6 * u, 0xFF1B2230); // eyes
        ctx.fill(x + 5 * u, y + 5 * u, x + 6 * u, y + 6 * u, 0xFF1B2230);
        ctx.fill(x + 3 * u, y + 7 * u, x + 5 * u, y + 8 * u, 0xFF8F6251);
    }

    private void drawChevron(DrawContext ctx, int x, int cy, float s, int color) {
        int u = Math.max(1, px(2, s));
        ctx.fill(x, cy - u, x + u, cy, color);
        ctx.fill(x + u, cy, x + 2 * u, cy + u, color);
        ctx.fill(x + 2 * u, cy - u, x + 3 * u, cy, color);
    }

    private void drawIcon(DrawContext ctx, String icon, int cx, int cy, int size, int color) {
        int u = Math.max(1, size / 8);
        int x = cx - 4 * u;
        int y = cy - 4 * u;
        switch (icon) {
            case "play" -> {
                for (int i = 0; i < 4; i++) {
                    ctx.fill(x + 2 * u + i * u, y + u + i * u, x + 2 * u + (i + 1) * u, y + 8 * u - i * u, color);
                }
            }
            case "users" -> {
                ctx.fill(x + u, y + 2 * u, x + 4 * u, y + 5 * u, color);
                ctx.fill(x + 4 * u, y + 3 * u, x + 7 * u, y + 6 * u, color);
                ctx.fill(x, y + 5 * u, x + 5 * u, y + 8 * u, color);
                ctx.fill(x + 4 * u, y + 6 * u, x + 8 * u, y + 8 * u, color);
            }
            case "mods", "grid" -> {
                ctx.fill(x + u, y + u, x + 4 * u, y + 4 * u, color);
                ctx.fill(x + 5 * u, y + u, x + 8 * u, y + 4 * u, color);
                ctx.fill(x + u, y + 5 * u, x + 4 * u, y + 8 * u, color);
                ctx.fill(x + 5 * u, y + 5 * u, x + 8 * u, y + 8 * u, color);
            }
            case "shirt" -> {
                ctx.fill(x + 2 * u, y + u, x + 6 * u, y + 2 * u, color);
                ctx.fill(x + u, y + 2 * u, x + 7 * u, y + 8 * u, color);
                ctx.fill(x + 3 * u, y + 2 * u, x + 5 * u, y + 8 * u, 0xFF18243A);
            }
            case "sliders" -> {
                ctx.fill(x + u, y + 2 * u, x + 8 * u, y + 3 * u, color);
                ctx.fill(x + u, y + 5 * u, x + 8 * u, y + 6 * u, color);
                ctx.fill(x + u, y + 8 * u - u, x + 8 * u, y + 8 * u, color);
                ctx.fill(x + 3 * u, y + u, x + 5 * u, y + 4 * u, color);
                ctx.fill(x + 5 * u, y + 4 * u, x + 7 * u, y + 7 * u, color);
            }
            case "power" -> {
                ctx.fill(x + 3 * u, y + u, x + 5 * u, y + 5 * u, color);
                ctx.fill(x + u, y + 3 * u, x + 2 * u, y + 6 * u, color);
                ctx.fill(x + 6 * u, y + 3 * u, x + 7 * u, y + 6 * u, color);
                ctx.fill(x + 2 * u, y + 6 * u, x + 6 * u, y + 7 * u, color);
            }
            case "logout" -> {
                ctx.fill(x + u, y + u, x + 2 * u, y + 8 * u, color);
                ctx.fill(x + 2 * u, y + u, x + 6 * u, y + 2 * u, color);
                ctx.fill(x + 2 * u, y + 7 * u, x + 6 * u, y + 8 * u, color);
                ctx.fill(x + 4 * u, y + 4 * u, x + 8 * u, y + 5 * u, color);
                ctx.fill(x + 6 * u, y + 3 * u, x + 7 * u, y + 4 * u, color);
                ctx.fill(x + 6 * u, y + 5 * u, x + 7 * u, y + 6 * u, color);
            }
            case "rkblogo" -> {
                ctx.fill(x + 2 * u, y + u, x + 5 * u, y + 7 * u, color);
                ctx.fill(x + 5 * u, y + u, x + 8 * u, y + 3 * u, color);
                ctx.fill(x + 4 * u, y + 4 * u, x + 8 * u, y + 8 * u, 0xFF176DFF);
            }
            default -> ctx.fill(x + 2 * u, y + 2 * u, x + 6 * u, y + 6 * u, color);
        }
    }
}
