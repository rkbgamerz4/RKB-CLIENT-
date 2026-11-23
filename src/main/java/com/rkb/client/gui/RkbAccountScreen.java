package com.rkb.client.gui;

import com.rkb.client.cloud.AuthState;
import com.rkb.client.cloud.CloudService;
import com.rkb.client.cloud.CloudStore;
import com.rkb.client.cosmetics.CosmeticsManager;
import com.rkb.client.cosmetics.PlayerIdentity;
import com.rkb.client.input.KeyUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** RKB Account screen: Minecraft identity, RKB cloud sign-in/link/sync. */
public class RkbAccountScreen extends Screen {
    private record Hit(RkbUi.Rect rect, Runnable action) {
        boolean contains(int x, int y) { return rect.contains(x, y); }
    }

    private final List<Hit> hits = new ArrayList<>();
    private RkbLayout.SidebarShell shell;
    private TextFieldWidget emailField;
    private TextFieldWidget passwordField;
    private boolean mouseWasDown;
    private boolean dragging;
    private boolean pressedInContent;
    private int pressY;
    private int lastY;
    private float scroll;
    private int contentH;
    private String localNote = "";

    public RkbAccountScreen() {
        super(Text.literal("RKB Account"));
    }

    @Override
    protected void init() {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        String oldEmail = emailField == null ? "" : emailField.getText();
        String oldPass = passwordField == null ? "" : passwordField.getText();

        emailField = new TextFieldWidget(tr, 0, 0, 100, 20, Text.literal("E-mail"));
        emailField.setMaxLength(160);
        emailField.setPlaceholder(Text.literal("you@example.com"));
        emailField.setText(oldEmail);
        emailField.setDrawsBackground(false);
        addSelectableChild(emailField);

        passwordField = new TextFieldWidget(tr, 0, 0, 100, 20, Text.literal("Password"));
        passwordField.setMaxLength(160);
        passwordField.setPlaceholder(Text.literal("Password"));
        passwordField.setText(oldPass);
        passwordField.setDrawsBackground(false);
        addSelectableChild(passwordField);

        mouseWasDown = KeyUtil.isMouseDown(mc, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() { RkbScreens.close(); }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (shell != null && shell.content().contains((int) mx, (int) my)) {
            int max = Math.max(0, contentH - shell.content().height());
            scroll = Math.max(0, Math.min(max, scroll - (float) v * 18));
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (emailField != null && emailField.mouseClicked(click, doubled)) { setFocused(emailField); return true; }
        if (passwordField != null && passwordField.mouseClicked(click, doubled)) { setFocused(passwordField); return true; }
        if (emailField != null) emailField.setFocused(false);
        if (passwordField != null) passwordField.setFocused(false);
        if (click.button() == 0) {
            int mx = (int) click.x(), my = (int) click.y();
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (h.contains(mx, my)) { h.action().run(); return true; }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (emailField != null && emailField.isFocused()) {
            if (emailField.keyPressed(input)) return true;
            if (input.isEscape()) { emailField.setFocused(false); return true; }
        }
        if (passwordField != null && passwordField.isFocused()) {
            if (passwordField.keyPressed(input)) return true;
            if (input.isEscape()) { passwordField.setFocused(false); return true; }
        }
        if (input.isEscape()) { close(); return true; }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (emailField != null && emailField.isFocused() && emailField.charTyped(input)) return true;
        if (passwordField != null && passwordField.isFocused() && passwordField.charTyped(input)) return true;
        return super.charTyped(input);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        handleMouse(MinecraftClient.getInstance(), mx, my);
        hits.clear();
        TextRenderer tr = textRenderer;
        RkbUi.background(ctx, width, height, System.currentTimeMillis());
        shell = RkbSidebar.draw(ctx, tr, width, height, mx, my, null, (r, a) -> hits.add(new Hit(r, a)));

        RkbUi.Rect c = shell.content();
        ctx.drawText(tr, RkbUi.menuText("RKB Account"), c.x(), c.y(), RkbUi.TEXT, false);
        ctx.drawText(tr, RkbUi.menuText("Minecraft identity, RKB cloud and local cosmetics."), c.x(), c.y() + 12, RkbUi.MUTED, false);

        AuthState auth = CloudService.authState();
        RkbUi.Rect body = new RkbUi.Rect(c.x(), c.y() + 30, c.width(), Math.max(60, c.height() - 30));
        ctx.enableScissor(body.x(), body.y(), body.right(), body.bottom());
        int y = body.y() - (int) scroll;

        boolean stacked = body.width() < 560;
        int gap = 12;
        int cardW = stacked ? body.width() : Math.max(180, (body.width() - gap) / 2);
        int leftX = body.x();
        int rightX = stacked ? body.x() : leftX + cardW + gap;

        int yL = drawMinecraftCard(ctx, tr, leftX, y, cardW, auth, mx, my);
        int yR = drawRkbCard(ctx, tr, rightX, stacked ? yL + gap : y, cardW, auth, mx, my, delta);
        int bottom = Math.max(yL, yR) + 12;
        int yP = drawPrivacyCard(ctx, tr, leftX, bottom, body.width(), mx, my);
        contentH = (yP + (int) scroll) - body.y();
        ctx.disableScissor();
    }

    private int drawMinecraftCard(DrawContext ctx, TextRenderer tr, int x, int y, int w, AuthState auth, int mx, int my) {
        int h = 126;
        RkbUi.panel(ctx, new RkbUi.Rect(x, y, w, h), RkbUi.SURFACE_2, RkbUi.LINE);
        ctx.drawText(tr, RkbUi.menuText("MINECRAFT ACCOUNT"), x + 12, y + 12, RkbUi.DIM, false);
        String name = PlayerIdentity.username();
        boolean offline = !PlayerIdentity.signedIn();
        ctx.drawText(tr, RkbUi.menuText(name.isEmpty() ? "Unknown player" : name), x + 12, y + 30, RkbUi.TEXT, false);
        int dot = offline ? RkbUi.WARNING : RkbUi.SUCCESS;
        ctx.fill(x + 12, y + 48, x + 18, y + 54, dot);
        ctx.drawText(tr, RkbUi.menuText(offline ? "Offline mode / local session" : "Minecraft online session"), x + 24, y + 47, RkbUi.MUTED, false);
        String uuid = PlayerIdentity.shortUuid();
        ctx.drawText(tr, RkbUi.menuText("UUID: " + (uuid == null || uuid.isEmpty() ? "unknown" : uuid)), x + 12, y + 64, RkbUi.MUTED, false);
        ctx.drawText(tr, RkbUi.menuText("Offline UUIDs are local identities and are never claimed as Mojang-verified."), x + 12, y + 80, RkbUi.DIM, false);
        String link = CloudService.isLinked() ? "Linked to RKB account" : "Not linked to RKB account";
        ctx.drawText(tr, RkbUi.menuText(link), x + 12, y + 100, CloudService.isLinked() ? RkbUi.SUCCESS : RkbUi.MUTED, false);
        return y + h;
    }

    private int drawRkbCard(DrawContext ctx, TextRenderer tr, int x, int y, int w, AuthState auth, int mx, int my, float delta) {
        boolean signedIn = auth.signedIn();
        int h = signedIn ? 170 : 210;
        RkbUi.panel(ctx, new RkbUi.Rect(x, y, w, h), RkbUi.SURFACE_2, RkbUi.LINE);
        ctx.drawText(tr, RkbUi.menuText("RKB CLOUD"), x + 12, y + 12, RkbUi.DIM, false);
        ctx.fill(x + 12, y + 31, x + 18, y + 37, signedIn ? RkbUi.SUCCESS : RkbUi.DIM);
        ctx.drawText(tr, RkbUi.menuText(signedIn ? "Signed in" : "Signed out"), x + 24, y + 30, RkbUi.TEXT, false);
        ctx.drawText(tr, RkbUi.menuText(signedIn ? RkbUi.fit(tr, auth.accountLabel(), w - 24) : "Local mode - no RKB registration required"), x + 12, y + 46, RkbUi.MUTED, false);

        if (signedIn) {
            String linked = CloudService.isLinked() ? "Minecraft linked: " + CloudStore.linkedName() : "Minecraft not linked";
            ctx.drawText(tr, RkbUi.menuText(RkbUi.fit(tr, linked, w - 24)), x + 12, y + 62, CloudService.isLinked() ? RkbUi.SUCCESS : RkbUi.WARNING, false);
            int btnY = y + 82;
            int bw = Math.max(80, (w - 36) / 2);
            drawAction(ctx, tr, new RkbUi.Rect(x + 12, btnY, bw, 24), CloudService.isLinked() ? "Relink" : "Link Minecraft", !auth.busy(), () -> CloudService.link(), mx, my);
            drawAction(ctx, tr, new RkbUi.Rect(x + 18 + bw, btnY, bw, 24), "Sync", !auth.busy(), () -> CloudService.manualSync(), mx, my);
            drawAction(ctx, tr, new RkbUi.Rect(x + 12, btnY + 30, bw, 24), "Refresh", true, () -> { CosmeticsManager.refresh(); localNote = "Refreshing..."; }, mx, my);
            drawAction(ctx, tr, new RkbUi.Rect(x + 18 + bw, btnY + 30, bw, 24), "Sign out", !auth.busy(), () -> { CloudService.logout(); localNote = ""; }, mx, my);
            String sync = CloudStore.lastSyncMs() <= 0 ? "never" : ago(CloudStore.lastSyncMs());
            ctx.drawText(tr, RkbUi.menuText("Last sync: " + sync), x + 12, y + 146, RkbUi.DIM, false);
        } else {
            int fy = y + 68;
            drawField(ctx, tr, emailField, new RkbUi.Rect(x + 12, fy, w - 24, 22), mx, my, delta, false);
            drawField(ctx, tr, passwordField, new RkbUi.Rect(x + 12, fy + 30, w - 24, 22), mx, my, delta, true);
            int bw = Math.max(80, (w - 36) / 2);
            int by = fy + 62;
            drawAction(ctx, tr, new RkbUi.Rect(x + 12, by, bw, 24), auth.busy() ? "..." : "Sign in", !auth.busy(), () -> {
                localNote = ""; CloudService.login(emailField.getText(), passwordField.getText()); passwordField.setText("");
            }, mx, my);
            drawAction(ctx, tr, new RkbUi.Rect(x + 18 + bw, by, bw, 24), "Create account", !auth.busy(), () -> {
                localNote = ""; CloudService.register(emailField.getText(), passwordField.getText()); passwordField.setText("");
            }, mx, my);
            if (CloudStore.anonKey().isEmpty()) {
                ctx.drawText(tr, RkbUi.menuText("Cloud key missing from config/rkbclient-cloud.json."), x + 12, by + 30, RkbUi.WARNING, false);
            }
        }
        String msg = !localNote.isBlank() ? localNote : auth.message();
        if (msg != null && !msg.isBlank()) {
            int col = auth.busy() ? RkbUi.WARNING : auth.lastOk() ? RkbUi.SUCCESS : RkbUi.MUTED;
            ctx.drawText(tr, RkbUi.menuText(RkbUi.fit(tr, msg, w - 24)), x + 12, y + h - 16, col, false);
        }
        return y + h;
    }

    private int drawPrivacyCard(DrawContext ctx, TextRenderer tr, int x, int y, int w, int mx, int my) {
        int h = 74;
        RkbUi.panel(ctx, new RkbUi.Rect(x, y, w, h), RkbUi.SURFACE, RkbUi.LINE);
        ctx.drawText(tr, RkbUi.menuText("PRIVACY & LOCAL MODE"), x + 12, y + 10, RkbUi.DIM, false);
        ctx.drawText(tr, RkbUi.menuText("Minecraft access tokens never leave this device."), x + 12, y + 26, RkbUi.MUTED, false);
        ctx.drawText(tr, RkbUi.menuText("Local skins, capes, HUD, modules and settings work without RKB registration."), x + 12, y + 40, RkbUi.MUTED, false);
        ctx.drawText(tr, RkbUi.menuText("Cloud sync is optional and only uses your RKB account session."), x + 12, y + 54, RkbUi.MUTED, false);
        return y + h;
    }

    private void drawAction(DrawContext ctx, TextRenderer tr, RkbUi.Rect r, String label, boolean enabled, Runnable action, int mx, int my) {
        boolean hover = enabled && r.contains(mx, my);
        int fill = !enabled ? RkbUi.SURFACE : hover ? RkbUi.SURFACE_3 : RkbUi.SURFACE_2;
        ctx.fill(r.x(), r.y(), r.right(), r.bottom(), fill);
        int border = hover ? RkbUi.CHROME_ACCENT : RkbUi.LINE;
        ctx.fill(r.x(), r.y(), r.right(), r.y() + 1, border);
        ctx.fill(r.x(), r.bottom() - 1, r.right(), r.bottom(), border);
        ctx.fill(r.x(), r.y(), r.x() + 1, r.bottom(), border);
        ctx.fill(r.right() - 1, r.y(), r.right(), r.bottom(), border);
        ctx.drawText(tr, RkbUi.menuText(RkbUi.fit(tr, label, r.width() - 8)), r.x() + 6, r.y() + 8, enabled ? RkbUi.TEXT : RkbUi.DIM, false);
        if (enabled) hits.add(new Hit(r, action));
    }

    private void drawField(DrawContext ctx, TextRenderer tr, TextFieldWidget field, RkbUi.Rect r, int mx, int my, float delta, boolean password) {
        boolean focus = field.isFocused();
        ctx.fill(r.x(), r.y(), r.right(), r.bottom(), focus ? RkbUi.SURFACE_3 : RkbUi.SURFACE);
        int border = focus ? RkbUi.CHROME_ACCENT : RkbUi.LINE;
        ctx.fill(r.x(), r.y(), r.right(), r.y() + 1, border);
        ctx.fill(r.x(), r.bottom() - 1, r.right(), r.bottom(), border);
        ctx.fill(r.x(), r.y(), r.x() + 1, r.bottom(), border);
        ctx.fill(r.right() - 1, r.y(), r.right(), r.bottom(), border);
        field.setX(r.x() + 6);
        field.setY(r.y() + 7);
        field.setWidth(r.width() - 12);
        field.setVisible(true);
        // For password, render masked manually
        if (password && !field.getText().isEmpty()) {
            String real = field.getText();
            field.setEditableColor(0x00000000);
            field.render(ctx, mx, my, delta);
            field.setEditableColor(0xE0E0E0);
            String mask = "*".repeat(Math.min(real.length(), 40));
            ctx.drawText(tr, RkbUi.menuText(mask), r.x() + 6, r.y() + 7, RkbUi.TEXT, false);
        } else {
            field.setEditableColor(0xE0E0E0);
            field.render(ctx, mx, my, delta);
        }
    }

    private void handleMouse(MinecraftClient mc, int mx, int my) {
        boolean down = KeyUtil.isMouseDown(mc, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        if (down && !mouseWasDown) {
            pressY = my; lastY = my; dragging = false;
            pressedInContent = shell != null && shell.content().contains(mx, my);
        } else if (down) {
            if (!dragging && pressedInContent && Math.abs(my - pressY) > 4) dragging = true;
            if (dragging) { scroll -= (my - lastY); lastY = my; }
        } else if (mouseWasDown) {
            dragging = false;
        }
        mouseWasDown = down;
        int max = shell == null ? 0 : Math.max(0, contentH - shell.content().height());
        scroll = Math.max(0, Math.min(max, scroll));
    }

    private static String ago(long ms) {
        long s = Math.max(0, (System.currentTimeMillis() - ms) / 1000);
        if (s < 60) return s + "s ago";
        if (s < 3600) return (s / 60) + "m ago";
        return (s / 3600) + "h ago";
    }
}
