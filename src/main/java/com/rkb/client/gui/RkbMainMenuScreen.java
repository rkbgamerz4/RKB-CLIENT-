package com.rkb.client.gui;

import com.rkb.client.VersionInfo;
import com.rkb.client.cloud.AuthState;
import com.rkb.client.cloud.CloudService;
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
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Redesigned RKB main menu. */
public class RkbMainMenuScreen extends Screen {
    private record Hit(RkbUi.Rect rect, Runnable action) { boolean contains(int x, int y) { return rect.contains(x, y); } }
    private record MenuButton(String label, String icon, boolean primary, Runnable action) {}

    private final List<Hit> hits = new ArrayList<>();

    public RkbMainMenuScreen() { super(Text.literal("RKB CLIENT")); }

    @Override public boolean shouldPause() { return false; }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) return true;
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            int mx = (int) click.x(); int my = (int) click.y();
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
        RkbUi.background(ctx, width, height, System.currentTimeMillis());
        TextRenderer tr = textRenderer;
        AuthState auth = CloudService.authState();

        drawAccountChip(ctx, tr, mouseX, mouseY, auth);
        drawBrand(ctx, tr);
        drawButtons(ctx, tr, mouseX, mouseY);
        drawFooter(ctx, tr, auth);
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawAccountChip(DrawContext ctx, TextRenderer tr, int mx, int my, AuthState auth) {
        int w = Math.min(240, Math.max(170, width / 5));
        int h = 34;
        RkbUi.Rect r = new RkbUi.Rect(width - w - 18, 18, w, h);
        boolean hover = r.contains(mx, my);
        ctx.fill(r.x(), r.y(), r.right(), r.bottom(), hover ? RkbUi.SURFACE_3 : RkbUi.SURFACE_2);
        drawBorder(ctx, r, RkbUi.LINE);
        ctx.fill(r.x() + 8, r.y() + 8, r.x() + 26, r.y() + 26, RkbUi.SURFACE_3);
        drawBorder(ctx, new RkbUi.Rect(r.x()+8, r.y()+8, 18, 18), RkbUi.LINE);
        String name = RkbUi.fit(tr, auth.signedIn() ? auth.accountLabel() : "Local mode", w - 44);
        ctx.drawText(tr, name, r.x() + 32, r.y() + 8, RkbUi.TEXT, false);
        ctx.drawText(tr, auth.signedIn() ? "RKB Cloud" : "Signed out", r.x() + 32, r.y() + 19, RkbUi.MUTED, false);
        hits.add(new Hit(r, RkbScreens::account));
    }

    private void drawBrand(DrawContext ctx, TextRenderer tr) {
        int cx = width / 2;
        int top = Math.max(36, height / 8);
        RkbLogo.ensure();
        int logoSize = Math.min(72, Math.max(48, height / 10));
        if (RkbLogo.ready()) RkbLogo.draw(ctx, cx - logoSize / 2, top, logoSize);
        int titleY = top + logoSize + 24;
        String rkb = "RKB ";
        String client = "CLIENT";
        int tw = tr.getWidth(rkb) + tr.getWidth(client);
        ctx.drawText(tr, rkb, cx - tw / 2, titleY, RkbUi.TEXT, false);
        ctx.drawText(tr, client, cx - tw / 2 + tr.getWidth(rkb), titleY, RkbUi.CHROME_ACCENT, false);
        String sub = "MINECRAFT PVP CLIENT  ·  " + VersionInfo.minecraft();
        int sw = tr.getWidth(sub);
        ctx.drawText(tr, sub, cx - sw / 2, titleY + 16, RkbUi.MUTED, false);
    }

    private void drawButtons(DrawContext ctx, TextRenderer tr, int mx, int my) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int cx = width / 2;
        int colW = Math.min(238, Math.max(170, (width - 60) / 2));
        int gap = 14;
        int btnH = 34;
        int rowH = 42;
        int startY = Math.max(height / 2 - 40, 220);
        if (height < 360) startY = height / 2 - 20;
        int x1 = cx - colW - gap / 2;
        int x2 = cx + gap / 2;

        ctx.drawText(tr, RkbUi.menuText("MINECRAFT"), x1, startY - 14, RkbUi.DIM, false);
        ctx.drawText(tr, RkbUi.menuText("RKB"), x2, startY - 14, RkbUi.DIM, false);

        MenuButton[] left = {
                new MenuButton("Singleplayer", "play", true, () -> mc.setScreen(new SelectWorldScreen(this))),
                new MenuButton("Multiplayer", "users", false, () -> mc.setScreen(new MultiplayerScreen(this))),
                new MenuButton("Mods", "cube", false, () -> ModMenuBridge.openOrWarn(this)),
                new MenuButton("Settings", "globe", false, () -> mc.setScreen(new OptionsScreen(this, mc.options))),
        };
        MenuButton[] right = {
                new MenuButton("Cosmetics", "shirt", false, () -> mc.setScreen(new CosmeticsScreen())),
                new MenuButton("RKB Modules", "grid", false, () -> mc.setScreen(new RkbModulesScreen())),
                new MenuButton("RKB Settings", "sliders", false, () -> mc.setScreen(new RkbSettingsScreen())),
        };
        for (int i = 0; i < left.length; i++) drawButton(ctx, tr, new RkbUi.Rect(x1, startY + i * rowH, colW, btnH), left[i], mx, my);
        for (int i = 0; i < right.length; i++) drawButton(ctx, tr, new RkbUi.Rect(x2, startY + i * rowH, colW, btnH), right[i], mx, my);

        int quitY = startY + 4 * rowH + 14;
        RkbUi.Rect quit = new RkbUi.Rect(x1, Math.min(quitY, height - 56), colW * 2 + gap, 32);
        drawDangerButton(ctx, tr, quit, "Quit Game", quit.contains(mx, my));
        hits.add(new Hit(quit, mc::scheduleStop));
    }

    private void drawButton(DrawContext ctx, TextRenderer tr, RkbUi.Rect r, MenuButton b, int mx, int my) {
        boolean hover = r.contains(mx, my);
        int fill = b.primary ? (hover ? RkbUi.CHROME_ACCENT_HOVER : RkbUi.CHROME_ACCENT) : (hover ? RkbUi.SURFACE_3 : RkbUi.SURFACE_2);
        ctx.fill(r.x(), r.y(), r.right(), r.bottom(), fill);
        drawBorder(ctx, r, b.primary ? (hover ? RkbUi.CHROME_ACCENT_HOVER : RkbUi.CHROME_ACCENT) : RkbUi.LINE);
        int tx = r.x() + 14;
        int color = b.primary ? 0xFFFFFFFF : RkbUi.TEXT;
        ctx.drawText(tr, b.label(), tx, r.y() + (r.height() - 8) / 2, color, false);
        hits.add(new Hit(r, b.action()));
    }

    private void drawDangerButton(DrawContext ctx, TextRenderer tr, RkbUi.Rect r, String label, boolean hover) {
        ctx.fill(r.x(), r.y(), r.right(), r.bottom(), hover ? 0xFF331A1D : RkbUi.DANGER_BG);
        drawBorder(ctx, r, 0xFF3A2428);
        int tw = tr.getWidth(label);
        ctx.drawText(tr, label, r.x() + (r.width() - tw) / 2, r.y() + (r.height() - 8) / 2, RkbUi.DANGER, false);
    }

    private void drawFooter(DrawContext ctx, TextRenderer tr, AuthState auth) {
        int y = height - 18;
        ctx.fill(0, y - 8, width, y - 7, RkbUi.LINE);
        String left = "RKB CLIENT " + VersionInfo.mod() + "  ·  Minecraft " + VersionInfo.minecraft();
        String right = "FPS " + MinecraftClient.getInstance().getCurrentFps() + "  ·  " + (auth.signedIn() ? "RKB Cloud" : "Local mode");
        ctx.drawText(tr, left, 16, y, RkbUi.DIM, false);
        int rw = tr.getWidth(right);
        ctx.drawText(tr, right, width - 16 - rw, y, RkbUi.DIM, false);
    }

    private void drawBorder(DrawContext ctx, RkbUi.Rect r, int color) {
        ctx.fill(r.x(), r.y(), r.right(), r.y() + 1, color);
        ctx.fill(r.x(), r.bottom() - 1, r.right(), r.bottom(), color);
        ctx.fill(r.x(), r.y(), r.x() + 1, r.bottom(), color);
        ctx.fill(r.right() - 1, r.y(), r.right(), r.bottom(), color);
    }
}
