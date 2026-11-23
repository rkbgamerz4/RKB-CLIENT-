package com.rkb.client.gui;

import com.rkb.client.cloud.AuthState;
import com.rkb.client.cloud.CloudService;
import com.rkb.client.cloud.CloudStore;
import com.rkb.client.cosmetics.CosmeticsManager;
import com.rkb.client.cosmetics.PlayerIdentity;
import com.rkb.client.cosmetics.RkbLogo;
import com.rkb.client.input.KeyUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Real Supabase account flow: sign in, create account, sign out, link Minecraft and sync V2 inventory. */
public class RkbAccountScreen extends Screen {
    private record Hit(RkbUi.Rect rect, Runnable action) {
    }

    private final List<Hit> hits = new ArrayList<>();
    private RkbLayout.TopBar chrome;
    private TextFieldWidget emailField;
    private TextFieldWidget passwordField;
    private boolean passwordMasked;
    private boolean mouseWasDown;
    private String localNote = "";

    private RkbUi.Rect panelBounds;
    private RkbUi.Rect innerBounds;
    private float scroll;
    private boolean scrollable;
    private boolean dragging;
    private boolean pressedInPanel;
    private int pressY;
    private int lastY;

    public RkbAccountScreen() {
        super(Text.literal("RKB Account"));
    }

    @Override
    protected void init() {
        chrome = RkbLayout.topBar(width, height);
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
        passwordField.setEditableColor(0x00000000);
        passwordField.setUneditableColor(0x00000000);
        addSelectableChild(passwordField);

        RkbLogo.ensure();
        mouseWasDown = KeyUtil.isMouseDown(mc, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        TextFieldWidget focused = emailField != null && emailField.isFocused() ? emailField
                : passwordField != null && passwordField.isFocused() ? passwordField : null;
        if (focused != null) {
            if (focused.keyPressed(input)) {
                return true;
            }
            if (input.isEscape()) {
                focused.setFocused(false);
                return true;
            }
        }
        if (input.isEscape()) {
            RkbScreens.close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        int accent = Theme.accent();
        hits.clear();
        RkbUi.background(ctx, width, height, System.currentTimeMillis());
        drawChrome(ctx, tr, accent, mx, my);

        AuthState auth = CloudService.authState();
        RkbUi.Rect panel = new RkbUi.Rect(12, chrome.contentTop(), width - 24,
                Math.max(120, height - chrome.contentTop() - 16));
        panelBounds = panel;
        RkbUi.panel(ctx, panel, 0xDD0E1422, RkbUi.LINE);
        RkbUi.Rect inner = panel.inset(14);
        innerBounds = inner;

        boolean stacked = width < 720;
        RkbUi.Rect summary;
        RkbUi.Rect form;
        int maxScroll;
        if (stacked) {
            int summaryH = Math.min(104, Math.max(70, inner.height() / 3));
            int formH = auth.signedIn() ? 190 : 230;
            int contentH = summaryH + 10 + formH;
            maxScroll = Math.max(0, contentH - inner.height());
            scroll = Math.max(0, Math.min(maxScroll, scroll));
            int y = inner.y() - Math.round(scroll);
            summary = new RkbUi.Rect(inner.x(), y, inner.width(), summaryH);
            form = new RkbUi.Rect(inner.x(), summary.bottom() + 10, inner.width(), formH);
        } else {
            scroll = 0;
            maxScroll = 0;
            int summaryW = Math.max(230, inner.width() * 38 / 100);
            summary = new RkbUi.Rect(inner.x(), inner.y(), summaryW, inner.height());
            form = new RkbUi.Rect(summary.right() + 14, inner.y(), inner.width() - summaryW - 14,
                    inner.height());
        }
        scrollable = maxScroll > 0;

        ctx.enableScissor(inner.x(), inner.y(), inner.right(), inner.bottom());
        drawSummary(ctx, tr, accent, summary, auth);
        if (auth.signedIn()) {
            drawSignedIn(ctx, tr, accent, form, auth, mx, my);
        } else {
            drawSignIn(ctx, tr, accent, form, auth, mx, my, delta);
        }
        ctx.disableScissor();
        if (maxScroll > 0) {
            int barH = Math.max(18, inner.height() * inner.height() / (inner.height() + maxScroll));
            int barY = inner.y() + (int) ((inner.height() - barH) * (scroll / maxScroll));
            RkbUi.roundedFill(ctx, new RkbUi.Rect(inner.right() - 3, barY, 3, barH), accent);
        }
        handleMouse(mc, mx, my);
    }

    private void drawChrome(DrawContext ctx, TextRenderer tr, int accent, int mx, int my) {
        RkbUi.Rect brand = chrome.brand();
        if (RkbLogo.ready()) {
            RkbLogo.draw(ctx, brand.x(), brand.y() - 1, 24);
        }
        ctx.drawText(tr, Text.literal("RKB").styled(s -> s.withBold(true)), brand.x() + 28, brand.y() + 3,
                RkbUi.TEXT, false);
        ctx.drawText(tr, "ACCOUNT", brand.x() + 28 + tr.getWidth("RKB "), brand.y() + 3, accent, false);

        for (int i = 0; i < chrome.navItems().size(); i++) {
            RkbUi.Rect r = chrome.navItems().get(i);
            boolean hover = r.contains(mx, my);
            RkbUi.chip(ctx, r, RkbUi.NAV_LABELS[i], tr, false, hover);
            final int nav = i;
            hits.add(new Hit(r, () -> RkbScreens.openNav(nav)));
        }

        RkbUi.Rect account = chrome.account();
        RkbUi.panel(ctx, account, RkbUi.SURFACE, RkbUi.LINE);
        AuthState auth = CloudService.authState();
        ctx.drawText(tr, RkbUi.fit(tr, auth.accountLabel(), account.width() - 16), account.x() + 8,
                account.y() + 8, auth.signedIn() ? accent : RkbUi.MUTED, false);

        RkbUi.Rect close = chrome.close();
        boolean ch = close.contains(mx, my);
        RkbUi.panel(ctx, close, ch ? RkbUi.SURFACE_2 : RkbUi.SURFACE, ch ? accent : RkbUi.LINE);
        ctx.drawText(tr, "X", close.x() + 10, close.y() + 8, RkbUi.TEXT, false);
        hits.add(new Hit(close, RkbScreens::close));
        hits.add(new Hit(brand, RkbScreens::home));
    }

    private void drawSummary(DrawContext ctx, TextRenderer tr, int accent, RkbUi.Rect r, AuthState auth) {
        RkbUi.panel(ctx, r, RkbUi.SURFACE_3, RkbUi.LINE);
        ctx.drawText(tr, "RKB ACCOUNT", r.x() + 12, r.y() + 12, accent, false);
        ctx.drawText(tr, Text.literal(auth.accountLabel()).styled(s -> s.withBold(true)), r.x() + 12,
                r.y() + 30, RkbUi.TEXT, false);
        RkbUi.statusDot(ctx, r.x() + 12, r.y() + 48, auth.tone());
        if (r.height() > 58) {
            ctx.drawText(tr, auth.statusLine(), r.x() + 22, r.y() + 44, auth.tone(), false);
        }

        int y = r.y() + 64;
        if (r.height() > 84 && y + 20 < r.bottom()) {
            ctx.drawText(tr, "LINKED MINECRAFT", r.x() + 12, y, RkbUi.MUTED, false);
            String linked = CloudService.isLinked()
                    ? CloudStore.linkedName() + "  ·  " + PlayerIdentity.shortUuidFor(CloudStore.linkedUuid())
                    : "Not linked";
            ctx.drawText(tr, RkbUi.fit(tr, linked, r.width() - 24), r.x() + 12, y + 12,
                    CloudService.isLinked() ? RkbUi.TEXT : RkbUi.MUTED, false);
            y += 30;
        }
        if (y + 20 < r.bottom()) {
            ctx.drawText(tr, "CLOUD INVENTORY", r.x() + 12, y, RkbUi.MUTED, false);
            int owned = 0;
            for (CosmeticsManager.Item item : CosmeticsManager.state().items()) {
                if (item.owned()) {
                    owned++;
                }
            }
            String inv = owned == 0 ? "No V2 items loaded" : owned + " owned item" + (owned == 1 ? "" : "s");
            ctx.drawText(tr, inv, r.x() + 12, y + 12, RkbUi.TEXT, false);
            y += 30;
        }
        if (y + 20 < r.bottom()) {
            String sync = CloudStore.lastSyncMs() <= 0 ? "never" : ago(CloudStore.lastSyncMs());
            ctx.drawText(tr, "LAST SYNC  " + sync, r.x() + 12, y, RkbUi.MUTED, false);
        }
        if (!auth.message().isBlank() && r.height() > 150) {
            String msg = RkbUi.fit(tr, auth.message(), r.width() - 24);
            ctx.drawText(tr, msg, r.x() + 12, r.bottom() - 20,
                    auth.lastOk() ? RkbUi.SUCCESS : auth.busy() ? RkbUi.WARNING : RkbUi.DANGER, false);
        }
    }

    private void drawSignIn(DrawContext ctx, TextRenderer tr, int accent, RkbUi.Rect r, AuthState auth,
                            int mx, int my, float delta) {
        ctx.drawText(tr, "SIGN IN", r.x(), r.y() + 2, accent, false);
        ctx.drawText(tr, RkbUi.fit(tr, "Supabase Auth over HTTPS. Minecraft tokens never leave this device.",
                r.width()), r.x(), r.y() + 16, RkbUi.MUTED, false);

        int fieldW = Math.max(120, r.width() - 8);
        int y = r.y() + 38;
        drawField(ctx, tr, emailField, new RkbUi.Rect(r.x(), y, fieldW, 24), "E-MAIL", "you@example.com",
                mx, my, delta);
        y += 34;
        drawField(ctx, tr, passwordField, new RkbUi.Rect(r.x(), y, fieldW, 24), "PASSWORD", "Password",
                mx, my, delta);
        passwordMasked = true;
        y += 36;

        boolean busy = auth.busy();
        RkbUi.Rect signIn = new RkbUi.Rect(r.x(), y, Math.min(170, r.width()), 26);
        RkbUi.button(ctx, tr, signIn, busy ? "Signing in..." : "Sign in", signIn.contains(mx, my), true, !busy);
        if (!busy) {
            hits.add(new Hit(signIn, () -> {
                localNote = "";
                CloudService.login(emailField.getText(), passwordField.getText());
                passwordField.setText("");
            }));
        }
        RkbUi.Rect create = new RkbUi.Rect(signIn.right() + 8, y, Math.min(170, r.width() - signIn.width() - 8), 26);
        if (create.width() > 80) {
            RkbUi.button(ctx, tr, create, "Create account", create.contains(mx, my), false, !busy);
            if (!busy) {
                hits.add(new Hit(create, () -> {
                    localNote = "";
                    CloudService.register(emailField.getText(), passwordField.getText());
                    passwordField.setText("");
                }));
            }
        }

        y += 38;
        String message = !localNote.isBlank() ? localNote : auth.message().isBlank()
                ? "Sign in to sync capes, badges and redeem codes." : auth.message();
        ctx.drawText(tr, RkbUi.fit(tr, message, r.width()), r.x(), y,
                auth.busy() ? RkbUi.WARNING : auth.lastOk() ? RkbUi.SUCCESS : RkbUi.MUTED, false);
        y += 18;
        ctx.drawText(tr, RkbUi.fit(tr, "Local skins, HUD settings and bundled capes stay available offline.",
                r.width()), r.x(), y, RkbUi.MUTED, false);
        if (CloudStore.anonKey().isEmpty()) {
            ctx.drawText(tr, "Cloud key missing from config/rkbclient-cloud.json.", r.x(), y + 14,
                    RkbUi.DANGER, false);
        }
    }

    private void drawSignedIn(DrawContext ctx, TextRenderer tr, int accent, RkbUi.Rect r, AuthState auth,
                              int mx, int my) {
        ctx.drawText(tr, "CLOUD ACTIONS", r.x(), r.y() + 2, accent, false);
        ctx.drawText(tr, "Signed in only after Supabase returned a valid session.", r.x(), r.y() + 16,
                RkbUi.MUTED, false);
        int y = r.y() + 40;
        int half = Math.max(90, (r.width() - 8) / 2);
        RkbUi.Rect link = new RkbUi.Rect(r.x(), y, half, 26);
        RkbUi.button(ctx, tr, link, CloudService.isLinked() ? "Relink Minecraft" : "Link Minecraft",
                link.contains(mx, my), !CloudService.isLinked(), !auth.busy());
        if (!auth.busy()) {
            hits.add(new Hit(link, CloudService::link));
        }
        RkbUi.Rect sync = new RkbUi.Rect(link.right() + 8, y, half, 26);
        RkbUi.button(ctx, tr, sync, "Sync now", sync.contains(mx, my), false, !auth.busy());
        if (!auth.busy()) {
            hits.add(new Hit(sync, CloudService::manualSync));
        }
        y += 34;

        RkbUi.Rect refresh = new RkbUi.Rect(r.x(), y, half, 26);
        RkbUi.button(ctx, tr, refresh, "Refresh status", refresh.contains(mx, my), false, !auth.busy());
        hits.add(new Hit(refresh, () -> {
            CosmeticsManager.refresh();
            localNote = "Refreshing catalog...";
        }));
        RkbUi.Rect signOut = new RkbUi.Rect(refresh.right() + 8, y, half, 26);
        RkbUi.button(ctx, tr, signOut, "Sign out", signOut.contains(mx, my), false, !auth.busy());
        hits.add(new Hit(signOut, () -> {
            CloudService.logout();
            localNote = "";
        }));
        y += 38;

        if (auth.message().isBlank()) {
            ctx.drawText(tr, RkbUi.fit(tr, CloudService.isLinked()
                    ? "Minecraft ownership is verified. V2 inventory is available."
                    : "Sign in is complete. Link Minecraft to use V2 inventory and redeem.", r.width()),
                    r.x(), y, CloudService.isLinked() ? RkbUi.SUCCESS : RkbUi.WARNING, false);
        } else {
            ctx.drawText(tr, RkbUi.fit(tr, auth.message(), r.width()), r.x(), y,
                    auth.lastOk() ? RkbUi.SUCCESS : RkbUi.DANGER, false);
        }
        y += 20;
        ctx.drawText(tr, "Cloud status: " + CloudService.statusText(), r.x(), y, RkbUi.MUTED, false);
    }

    private void drawField(DrawContext ctx, TextRenderer tr, TextFieldWidget field, RkbUi.Rect r, String label,
                           String placeholder, int mx, int my, float delta) {
        ctx.drawText(tr, label, r.x() + 2, r.y() - 11, field.isFocused() ? Theme.accent() : RkbUi.MUTED, false);
        RkbUi.panel(ctx, r, field.isFocused() ? 0xFF101A2C : RkbUi.SURFACE_3,
                field.isFocused() ? Theme.accent() : RkbUi.LINE);
        field.setX(r.x() + 7);
        field.setY(r.y() + 7);
        field.setWidth(r.width() - 14);
        field.setVisible(true);
        field.render(ctx, mx, my, delta);
        if (field == passwordField && passwordMasked) {
            String text = field.getText();
            if (!text.isEmpty()) {
                String mask = "*".repeat(Math.min(text.length(), 40));
                ctx.drawText(tr, mask, r.x() + 8, r.y() + 8, RkbUi.TEXT, false);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (scrollable) {
            scroll -= (float) (verticalAmount * 24.0);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void handleMouse(MinecraftClient mc, int mx, int my) {
        boolean down = KeyUtil.isMouseDown(mc, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        if (down && !mouseWasDown) {
            pressY = my;
            lastY = my;
            dragging = false;
            pressedInPanel = panelBounds != null && panelBounds.contains(mx, my);
        } else if (down) {
            if (!dragging && scrollable && pressedInPanel && Math.abs(my - pressY) > 4) {
                dragging = true;
            }
            if (dragging) {
                scroll -= (my - lastY);
                lastY = my;
            }
        } else if (mouseWasDown) {
            if (!dragging) {
                click(mx, my);
            }
            dragging = false;
        }
        mouseWasDown = down;
    }

    private void click(int mx, int my) {
        TextFieldWidget field = fieldAt(mx, my);
        if (field != null) {
            clearFocus();
            field.setFocused(true);
            field.mouseClicked(new Click(mx, my, new MouseInput(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0)), false);
            return;
        }
        clearFocus();
        for (int i = hits.size() - 1; i >= 0; i--) {
            RkbUi.Rect hit = hits.get(i).rect();
            if (!hit.contains(mx, my)) {
                continue;
            }
            if (panelBounds != null && hit.y() >= panelBounds.y()
                    && (innerBounds == null || !innerBounds.contains(mx, my))) {
                continue;
            }
            hits.get(i).action().run();
            break;
        }
    }

    private TextFieldWidget fieldAt(int mx, int my) {
        if (CloudService.authState().signedIn()) {
            return null;
        }
        if (innerBounds == null || !innerBounds.contains(mx, my)) {
            return null;
        }
        if (emailField != null && emailField.visible && emailField.isMouseOver(mx, my)) {
            return emailField;
        }
        if (passwordField != null && passwordField.visible && passwordField.isMouseOver(mx, my)) {
            return passwordField;
        }
        return null;
    }

    private void clearFocus() {
        if (emailField != null) {
            emailField.setFocused(false);
        }
        if (passwordField != null) {
            passwordField.setFocused(false);
        }
    }

    private static String ago(long ms) {
        long s = Math.max(0, (System.currentTimeMillis() - ms) / 1000);
        if (s < 60) {
            return s + "s ago";
        }
        if (s < 3600) {
            return (s / 60) + "m ago";
        }
        return (s / 3600) + "h ago";
    }
}
