package com.rkb.client.gui;

import com.rkb.client.VersionInfo;
import com.rkb.client.cloud.AuthState;
import com.rkb.client.cloud.CloudService;
import com.rkb.client.cosmetics.PlayerIdentity;
import com.rkb.client.cosmetics.RkbLogo;
import com.rkb.client.input.KeyUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * RKB's real title screen, installed by {@code TitleScreenMixin}. It keeps the vanilla destinations for Singleplayer,
 * Multiplayer and Options, while presenting one RKB navigation and account surface.
 */
public class RkbMainMenuScreen extends Screen {
    private static final int SINGLEPLAYER = 0;
    private static final int MULTIPLAYER = 1;
    private static final int MODS = 2;
    private static final int SETTINGS = 3;
    private static final int COSMETICS = 4;
    private static final int QUIT = 5;

    private record Hit(RkbUi.Rect rect, Runnable action) {
    }

    private final List<Hit> hits = new ArrayList<>();
    private RkbLayout.MainMenu layout;
    private boolean mouseWasDown;
    private long openedAt = System.currentTimeMillis();

    public RkbMainMenuScreen() {
        super(Text.literal("RKB CLIENT"));
    }

    @Override
    protected void init() {
        layout = RkbLayout.mainMenu(width, height);
        RkbLogo.ensure();
        mouseWasDown = KeyUtil.isMouseDown(MinecraftClient.getInstance(), GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        int accent = Theme.accent();
        hits.clear();

        RkbUi.background(ctx, width, height, System.currentTimeMillis());
        drawBrand(ctx, tr, accent);
        drawHero(ctx, tr, accent);
        drawAccount(ctx, tr, accent, mx, my);
        drawButtons(ctx, tr, accent, mx, my);
        drawStatus(ctx, tr, accent);
        drawFooter(ctx, tr);

        handleMouse(mc, mx, my);
    }

    private void drawBrand(DrawContext ctx, TextRenderer tr, int accent) {
        RkbUi.Rect b = layout.brand();
        int logo = layout.compact() ? Math.min(30, b.height()) : Math.min(42, b.height() + 6);
        if (RkbLogo.ready()) {
            RkbLogo.draw(ctx, b.x(), b.y() - 2, logo);
        } else {
            RkbUi.panel(ctx, new RkbUi.Rect(b.x(), b.y(), logo, logo), accent, accent);
            ctx.drawText(tr, "R", b.x() + 9, b.y() + 8, 0xFF02101C, true);
        }
        int x = b.x() + logo + 12;
        int ty = layout.compact() ? b.y() + 7 : b.y() + 3;
        ctx.drawText(tr, Text.literal("RKB").styled(s -> s.withBold(true)), x, ty, RkbUi.TEXT, false);
        ctx.drawText(tr, "CLIENT", x + tr.getWidth("RKB ") + 4, ty, accent, false);
        if (!layout.compact()) {
            ctx.drawText(tr, "MINECRAFT PVP CLIENT  ·  " + VersionInfo.minecraft(), x, b.y() + 16,
                    RkbUi.MUTED, false);
        }
    }

    private void drawHero(DrawContext ctx, TextRenderer tr, int accent) {
        RkbUi.Rect h = layout.hero();
        if (layout.compact()) {
            return;
        }
        int x = h.x() + 6;
        int y = h.y() + Math.max(34, h.height() / 4);
        ctx.drawText(tr, Text.literal("PLAY CLEANER.").styled(s -> s.withBold(true)), x, y,
                RkbUi.TEXT, false);
        ctx.drawText(tr, Text.literal("WIN FASTER.").styled(s -> s.withBold(true)), x, y + 18,
                accent, false);
        ctx.drawText(tr, "One RKB surface for modules, HUD, waypoints, skins and capes.", x, y + 38,
                RkbUi.MUTED, false);

        int chipY = y + 58;
        String[] chips = {"PVP HUD", "3D COSMETICS", "LOCAL SKINS", "CLOUD V2"};
        int cx = x;
        for (String chip : chips) {
            int cw = tr.getWidth(chip) + 16;
            RkbUi.Rect r = new RkbUi.Rect(cx, chipY, cw, 18);
            RkbUi.panel(ctx, r, 0x66141D30, RkbUi.LINE);
            ctx.drawText(tr, chip, cx + 8, chipY + 5, RkbUi.MUTED, false);
            cx += cw + 6;
        }
    }

    private void drawAccount(DrawContext ctx, TextRenderer tr, int accent, int mx, int my) {
        RkbUi.Rect a = layout.account();
        boolean hover = a.contains(mx, my);
        RkbUi.panel(ctx, a, hover ? RkbUi.SURFACE_2 : 0xCC0E1422, hover ? accent : RkbUi.LINE);
        MinecraftClient mc = MinecraftClient.getInstance();
        UUID uuid = PlayerIdentity.uuid();
        if (uuid == null) {
            uuid = UUID.nameUUIDFromBytes(new byte[]{7, 10, 18});
        }
        SkinTextures skin = DefaultSkinHelper.getSkinTextures(uuid);
        int head = Math.min(44, a.height() - 12);
        PlayerSkinDrawer.draw(ctx, skin, a.x() + 10, a.y() + (a.height() - head) / 2, head);

        AuthState auth = CloudService.authState();
        int x = a.x() + head + 20;
        String name = PlayerIdentity.username();
        if (name == null || name.isBlank()) {
            name = "Minecraft player";
        }
        int textW = a.width() - head - 74;
        boolean roomy = a.height() >= 50;
        ctx.drawText(tr, RkbUi.fit(tr, name, textW), x, a.y() + (roomy ? 10 : 7), RkbUi.TEXT, false);
        ctx.drawText(tr, RkbUi.fit(tr, auth.accountLabel(), textW), x, a.y() + (roomy ? 23 : 19),
                auth.signedIn() ? accent : RkbUi.MUTED, false);
        if (roomy) {
            ctx.drawText(tr, RkbUi.fit(tr, auth.linkLabel(), textW), x, a.y() + 36, RkbUi.MUTED, false);
        }
        ctx.drawText(tr, ">", a.right() - 16, a.y() + (a.height() - 9) / 2,
                hover ? accent : RkbUi.MUTED, false);
        hits.add(new Hit(a, RkbScreens::account));
    }

    private void drawButtons(DrawContext ctx, TextRenderer tr, int accent, int mx, int my) {
        String[] labels = {"SINGLEPLAYER", "MULTIPLAYER", "MODS", "SETTINGS", "COSMETICS", "QUIT GAME"};
        for (int i = 0; i < labels.length; i++) {
            RkbUi.Rect r = layout.buttons().get(i);
            boolean hover = r.contains(mx, my);
            boolean primary = i == SINGLEPLAYER;
            boolean danger = i == QUIT;
            int fill = danger ? (hover ? 0xFF35151C : 0xCC1A1016) : primary ? (hover ? 0xFF16B5FF : accent)
                    : hover ? RkbUi.SURFACE_2 : 0xCC0E1422;
            int border = danger ? (hover ? RkbUi.DANGER : 0xFF4A2630)
                    : primary ? accent : hover ? accent : RkbUi.LINE;
            RkbUi.panel(ctx, r, fill, border);
            int iconX = r.x() + 12;
            drawIcon(ctx, i, iconX, r.y() + (r.height() - 12) / 2,
                    primary ? 0xFF02101C : danger ? RkbUi.DANGER : hover ? accent : RkbUi.MUTED);
            String label = RkbUi.fit(tr, labels[i], r.width() - 54);
            ctx.drawText(tr, label, r.x() + 32, r.y() + (r.height() - 9) / 2,
                    primary ? 0xFF02101C : danger ? RkbUi.DANGER : hover ? RkbUi.TEXT : 0xFFDCE7F8, false);
            if (i != QUIT) {
                ctx.drawText(tr, ">", r.right() - 14, r.y() + (r.height() - 9) / 2,
                        primary ? 0xFF02101C : RkbUi.MUTED, false);
            }
            final int button = i;
            hits.add(new Hit(r, () -> activate(button)));
        }
    }

    private void activate(int button) {
        MinecraftClient mc = MinecraftClient.getInstance();
        switch (button) {
            case SINGLEPLAYER -> mc.setScreen(new SelectWorldScreen(this));
            case MULTIPLAYER -> mc.setScreen(new MultiplayerScreen(this));
            case MODS -> mc.setScreen(new RkbMenuScreen(RkbMenuScreen.PAGE_MODS));
            case SETTINGS -> mc.setScreen(new RkbMenuScreen(RkbMenuScreen.PAGE_SETTINGS));
            case COSMETICS -> mc.setScreen(new CosmeticsScreen());
            case QUIT -> mc.scheduleStop();
            default -> {
            }
        }
    }

    private void drawStatus(DrawContext ctx, TextRenderer tr, int accent) {
        RkbUi.Rect s = layout.status();
        if (layout.compact()) {
            String text = Theme.menuShowFps() ? "FPS " + MinecraftClient.getInstance().getCurrentFps() : "RKB "
                    + VersionInfo.mod();
            ctx.drawText(tr, RkbUi.fit(tr, text, s.width()), s.x(), s.y(), RkbUi.MUTED, false);
            return;
        }
        RkbUi.panel(ctx, s, 0xAA0E1422, RkbUi.LINE);
        RkbUi.statusDot(ctx, s.x() + 8, s.y() + 6, accent);
        ctx.drawText(tr, "FPS " + MinecraftClient.getInstance().getCurrentFps(), s.x() + 18, s.y() + 6,
                RkbUi.MUTED, false);
    }

    private void drawFooter(DrawContext ctx, TextRenderer tr) {
        if (layout.compact()) {
            return;
        }
        String left = "RKB CLIENT " + VersionInfo.mod();
        String right = "Right Shift  ·  /rkb  ·  " + PlayerIdentity.shortUuid();
        ctx.drawText(tr, left, 12, height - 14, RkbUi.MUTED, false);
        ctx.drawText(tr, right, width - 12 - tr.getWidth(right), height - 14, RkbUi.MUTED, false);
    }

    private void drawIcon(DrawContext ctx, int index, int x, int y, int color) {
        switch (index) {
            case SINGLEPLAYER -> {
                ctx.fill(x + 1, y, x + 3, y + 12, color);
                ctx.fill(x + 3, y + 2, x + 5, y + 10, color);
                ctx.fill(x + 5, y + 4, x + 7, y + 8, color);
                ctx.fill(x + 7, y + 5, x + 9, y + 7, color);
            }
            case MULTIPLAYER -> {
                ctx.fill(x, y + 6, x + 12, y + 8, color);
                ctx.fill(x + 2, y + 3, x + 10, y + 5, color);
                ctx.fill(x + 2, y + 8, x + 10, y + 10, color);
            }
            case MODS -> {
                for (int i = 0; i < 3; i++) {
                    ctx.fill(x + i * 4, y, x + i * 4 + 3, y + 3, color);
                    ctx.fill(x + i * 4, y + 4, x + i * 4 + 3, y + 7, color);
                    ctx.fill(x + i * 4, y + 8, x + i * 4 + 3, y + 11, color);
                }
            }
            case SETTINGS -> {
                ctx.fill(x + 1, y + 1, x + 11, y + 11, color);
                ctx.fill(x + 4, y + 4, x + 8, y + 8, 0xFF0E1422);
                ctx.fill(x + 5, y - 1, x + 7, y + 2, color);
                ctx.fill(x + 5, y + 10, x + 7, y + 13, color);
            }
            case COSMETICS -> {
                ctx.fill(x + 4, y, x + 6, y + 12, color);
                ctx.fill(x, y + 4, x + 12, y + 6, color);
                ctx.fill(x + 2, y + 2, x + 4, y + 4, color);
                ctx.fill(x + 8, y + 8, x + 10, y + 10, color);
            }
            case QUIT -> {
                ctx.fill(x, y + 2, x + 2, y + 10, color);
                ctx.fill(x + 2, y + 1, x + 4, y + 3, color);
                ctx.fill(x + 2, y + 9, x + 4, y + 11, color);
                ctx.fill(x + 4, y + 4, x + 11, y + 6, color);
                ctx.fill(x + 8, y + 6, x + 10, y + 8, color);
                ctx.fill(x + 6, y + 8, x + 8, y + 10, color);
            }
            default -> {
            }
        }
    }

    private void handleMouse(MinecraftClient mc, int mx, int my) {
        boolean down = KeyUtil.isMouseDown(mc, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        if (down && !mouseWasDown) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (h.rect().contains(mx, my)) {
                    h.action().run();
                    break;
                }
            }
        }
        mouseWasDown = down;
    }
}
