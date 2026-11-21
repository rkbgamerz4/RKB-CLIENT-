package com.rkb.client.gui;

import com.rkb.client.cloud.CloudStore;
import com.rkb.client.cosmetics.CapeCollection;
import com.rkb.client.cosmetics.CapeTextures;
import com.rkb.client.cosmetics.CosmeticsManager;
import com.rkb.client.cosmetics.PlayerIdentity;
import com.rkb.client.cosmetics.RkbLogo;
import com.rkb.client.cosmetics.SkinCloudSync;
import com.rkb.client.cosmetics.SkinSetStore;
import com.rkb.client.cosmetics.SkinTexturesCache;
import com.rkb.client.input.KeyUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * RKB CLIENT cosmetics. A Feather-inspired dark panel with the ten official capes, the player's current Minecraft
 * identity, a real local skin-set library, upload, preview and a single Save and Apply action.
 *
 * Nothing here performs I/O or networking on the render thread. Buttons call CosmeticsManager/SkinSetStore, which run
 * their work on the cloud thread and repaint from immutable snapshots.
 */
public class CosmeticsScreen extends Screen {
    private static final int PAGE_CAPES = 0;
    private static final int PAGE_SKIN = 1;
    private static final int PAGE_SETS = 2;
    private static final int PAGE_EQUIPPED = 3;
    private static final int PAGE_COLLECTION = 4;
    private static final int PAGE_UPLOAD = 5;
    private static final int PAGE_PREVIEW = 6;
    private static final int PAGE_SAVE = 7;

    private static final String[] NAV = {
            "Cape Collection", "Skin", "Skin Sets", "Equipped", "RKB Collection", "Upload Skin", "Preview", "Save & Apply"
    };

    private record Hit(int x1, int y1, int x2, int y2, boolean inContent, Runnable action) {
        boolean contains(int x, int y) {
            return x >= x1 && x < x2 && y >= y1 && y < y2;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private int page = PAGE_CAPES;
    private float scroll;
    private int contentTotal;
    private int px, py, pw, ph, navW, contentX, contentY, contentW, contentH;

    private TextFieldWidget pathField;
    private TextFieldWidget renameField;
    private TextFieldWidget redeemField;
    private String renamingId = "";
    private String confirmDelete = "";
    private String previewCapeId = "";
    private String toast = "";
    private long toastUntil;

    private boolean mouseWasDown;
    private boolean dragging;
    private boolean pressedInList;
    private int pressY;
    private int lastY;

    public CosmeticsScreen() {
        super(Text.literal("RKB Cosmetics"));
    }

    @Override
    protected void init() {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        pw = Math.min(width - 10, 760);
        ph = Math.min(height - 10, 430);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        navW = pw < 520 ? 94 : 126;
        contentX = px + navW + 8;
        contentY = py + Math.min(52, Math.max(42, ph / 7));
        contentW = Math.max(80, pw - navW - 18);
        contentH = Math.max(30, ph - (contentY - py) - 16);

        String oldPath = pathField == null ? "" : pathField.getText();
        String oldRename = renameField == null ? "" : renameField.getText();
        String oldCode = redeemField == null ? "" : redeemField.getText();

        pathField = new TextFieldWidget(tr, contentX + 8, contentY + 40, contentW - 16, 18, Text.literal("Skin PNG path"));
        pathField.setMaxLength(220);
        pathField.setPlaceholder(Text.literal("C:\\path\\skin.png  or  config/rkbclient-skins/inbox/skin.png"));
        pathField.setText(oldPath);
        addSelectableChild(pathField);

        renameField = new TextFieldWidget(tr, contentX + 8, contentY + contentH - 28, contentW - 112, 18, Text.literal("Name"));
        renameField.setMaxLength(32);
        renameField.setPlaceholder(Text.literal("Skin set name"));
        renameField.setText(oldRename);
        addSelectableChild(renameField);

        redeemField = new TextFieldWidget(tr, contentX + 8, contentY + contentH - 28, contentW - 92, 18, Text.literal("Code"));
        redeemField.setMaxLength(40);
        redeemField.setPlaceholder(Text.literal("Redeem code"));
        redeemField.setText(oldCode);
        addSelectableChild(redeemField);

        SkinSetStore.load();
        SkinCloudSync.refresh();
        CosmeticsManager.refresh();
        CapeCollection.all().forEach(c -> CapeTextures.ensureBuiltin(c.id()));
        RkbLogo.ensure();
        if (previewCapeId.isEmpty()) {
            CosmeticsManager.Item cape = CosmeticsManager.equippedItem("cape");
            previewCapeId = cape == null ? CapeCollection.all().get(0).id() : cape.id();
        }
        mouseWasDown = KeyUtil.isMouseDown(mc, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseY >= contentY && mouseY < contentY + contentH) {
            scroll -= (float) (verticalAmount * 22.0);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (activeField() != null && activeField().isFocused()) {
            if (activeField().keyPressed(input)) {
                return true;
            }
            if (input.isEscape()) {
                activeField().setFocused(false);
                return true;
            }
        }
        if (input.isEscape() && page != PAGE_CAPES) {
            setPage(PAGE_CAPES);
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void onFilesDropped(List<Path> paths) {
        List<Path> pngs = new ArrayList<>();
        for (Path p : paths) {
            if (p != null && p.getFileName() != null
                    && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")) {
                pngs.add(p);
            }
        }
        if (pngs.isEmpty()) {
            showToast("Drop a Minecraft skin PNG (64x64 or 64x32).");
            return;
        }
        setPage(PAGE_UPLOAD);
        SkinSetStore.importFiles(pngs);
        showToast("Importing " + pngs.size() + " skin" + (pngs.size() == 1 ? "" : "s") + "...");
    }

    private TextFieldWidget activeField() {
        if (page == PAGE_UPLOAD && pathField != null && pathField.isFocused()) {
            return pathField;
        }
        if (page == PAGE_SETS && !renamingId.isEmpty() && renameField != null && renameField.isFocused()) {
            return renameField;
        }
        if (page == PAGE_COLLECTION && redeemField != null && redeemField.isFocused()) {
            return redeemField;
        }
        return null;
    }

    private void blurFields() {
        if (pathField != null) {
            pathField.setFocused(false);
        }
        if (renameField != null) {
            renameField.setFocused(false);
        }
        if (redeemField != null) {
            redeemField.setFocused(false);
        }
    }

    private void setPage(int p) {
        page = p;
        scroll = 0;
        confirmDelete = "";
        if (p != PAGE_SETS) {
            renamingId = "";
            if (renameField != null) {
                renameField.setFocused(false);
            }
        }
        blurFields();
    }

    private void showToast(String s) {
        toast = s == null ? "" : s;
        toastUntil = System.currentTimeMillis() + 3200;
    }

    private boolean inContent(int x, int y) {
        return x >= contentX && x < contentX + contentW && y >= contentY && y < contentY + contentH;
    }

    private void handleMouse(MinecraftClient mc, int mx, int my) {
        boolean down = KeyUtil.isMouseDown(mc, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        if (down && !mouseWasDown) {
            pressY = my;
            lastY = my;
            dragging = false;
            pressedInList = inContent(mx, my);
        } else if (down) {
            if (!dragging && pressedInList && Math.abs(my - pressY) > 4) {
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
            blurFields();
            field.setFocused(true);
            field.mouseClicked(new Click(mx, my, new MouseInput(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0)), false);
            return;
        }
        blurFields();
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.contains(mx, my) && (!h.inContent() || inContent(mx, my))) {
                h.action().run();
                return;
            }
        }
    }

    private TextFieldWidget fieldAt(int mx, int my) {
        if (page == PAGE_UPLOAD && pathField != null && pathField.isMouseOver(mx, my)) {
            return pathField;
        }
        if (page == PAGE_SETS && !renamingId.isEmpty() && renameField != null && renameField.isMouseOver(mx, my)) {
            return renameField;
        }
        if (page == PAGE_COLLECTION && redeemField != null && redeemField.isMouseOver(mx, my)) {
            return redeemField;
        }
        return null;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        MinecraftClient mc = MinecraftClient.getInstance();
        handleMouse(mc, mx, my);
        hits.clear();
        TextRenderer tr = mc.textRenderer;
        int accent = Theme.accent();

        // Keep the world visible but make text readable, like the rest of the RKB menu.
        ctx.fill(0, 0, width, height, 0x66020812);
        ctx.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, Theme.BORDER);
        ctx.fill(px, py, px + pw, py + ph, Theme.PANEL);
        ctx.fill(px, py, px + pw, py + 1, accent);
        drawHeader(ctx, tr, accent, mx, my);

        drawNav(ctx, tr, accent, mx, my);
        ctx.fill(px + navW, contentY - 6, px + navW + 1, py + ph - 8, Theme.BORDER);
        drawTagline(ctx, tr);

        // fields are positioned per page
        positionFields();

        ctx.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);
        contentTotal = contentH;
        switch (page) {
            case PAGE_CAPES -> drawCapes(ctx, tr, accent, mx, my);
            case PAGE_SKIN -> drawSkinPage(ctx, tr, accent, mx, my);
            case PAGE_SETS -> drawSkinSets(ctx, tr, accent, mx, my);
            case PAGE_EQUIPPED -> drawEquipped(ctx, tr, accent, mx, my);
            case PAGE_COLLECTION -> drawCollection(ctx, tr, accent, mx, my);
            case PAGE_UPLOAD -> drawUpload(ctx, tr, accent, mx, my);
            case PAGE_PREVIEW -> drawPreviewPage(ctx, tr, accent, mx, my);
            default -> drawSavePage(ctx, tr, accent, mx, my);
        }
        ctx.disableScissor();

        if (contentTotal > 0) {
            scroll = Math.max(0, Math.min(Math.max(0, contentTotal - contentH), scroll));
        }
        drawScrollbar(ctx, accent);
        drawFields(ctx, mx, my, delta);
        if (System.currentTimeMillis() < toastUntil || !toast.isEmpty()) {
            if (System.currentTimeMillis() < toastUntil) {
                drawToast(ctx, tr, accent);
            }
        }
        String msg = SkinSetStore.message();
        if (!msg.isEmpty()) {
            ctx.drawText(tr, fit(tr, msg, contentW - 8), contentX + 4, py + ph - 12, Theme.MUTED, false);
        }
    }

    private void drawHeader(DrawContext ctx, TextRenderer tr, int accent, int mx, int my) {
        int logo = Math.min(28, Math.max(20, ph / 15));
        int lx = px + 12;
        int ly = py + (Math.min(52, Math.max(42, ph / 7)) - logo) / 2;
        if (RkbLogo.ready()) {
            RkbLogo.draw(ctx, lx, ly, logo);
        } else {
            ctx.drawText(tr, "R", lx + 4, ly + 5, accent, false);
        }
        Text title = Text.literal("RKB CLIENT").styled(s -> s.withBold(true));
        ctx.drawText(tr, title, lx + logo + 8, py + 12, Theme.TEXT, false);
        ctx.drawText(tr, "COSMETICS", lx + logo + 8, py + 24, accent, false);

        String name = PlayerIdentity.username();
        if (name.isEmpty()) {
            name = "Player";
        }
        int chipW = Math.min(180, Math.max(104, tr.getWidth(name) + 48));
        int chipX = px + pw - chipW - 34;
        int chipY = py + 9;
        int chipH = 26;
        ctx.fill(chipX, chipY, chipX + chipW, chipY + chipH, Theme.CARD);
        ctx.fill(chipX, chipY, chipX + 2, chipY + chipH, PlayerIdentity.verifiedOnline() ? accent : Theme.OFF);
        int dot = PlayerIdentity.verifiedOnline() ? 0xFF39D98A : PlayerIdentity.signedIn() ? 0xFFFFC857 : Theme.MUTED;
        ctx.fill(chipX + 10, chipY + 8, chipX + 14, chipY + 12, dot);
        ctx.drawText(tr, fit(tr, name, chipW - 28), chipX + 18, chipY + 4, Theme.TEXT, false);
        ctx.drawText(tr, PlayerIdentity.shortUuid() + "  " + PlayerIdentity.statusText(), chipX + 18, chipY + 14,
                PlayerIdentity.verifiedOnline() ? accent : Theme.MUTED, false);

        int cx = px + pw - 26;
        boolean hover = mx >= cx && mx < cx + 20 && my >= py + 8 && my < py + 30;
        ctx.fill(cx, py + 8, cx + 20, py + 30, hover ? Theme.CARD_HOVER : Theme.CARD);
        ctx.drawText(tr, "X", cx + 8, py + 13, Theme.TEXT, false);
        hits.add(new Hit(cx, py + 8, cx + 20, py + 30, false, () -> MinecraftClient.getInstance().setScreen(new RkbMenuScreen())));
    }

    private void drawNav(DrawContext ctx, TextRenderer tr, int accent, int mx, int my) {
        int x = px + 8;
        int usable = navW - 12;
        int itemH = Math.min(27, Math.max(20, (contentH - 8) / NAV.length));
        int y = contentY;
        for (int i = 0; i < NAV.length; i++) {
            boolean active = i == page;
            boolean hover = mx >= x && mx < x + usable && my >= y && my < y + itemH - 2;
            int bg = active ? accent : hover ? Theme.CARD_HOVER : Theme.CARD;
            ctx.fill(x, y, x + usable, y + itemH - 2, bg);
            if (active) {
                ctx.fill(x, y, x + 2, y + itemH - 2, Theme.TEXT);
            }
            String label = NAV[i];
            int color = active ? Theme.BLACK : hover ? Theme.TEXT : Theme.MUTED;
            if (i == PAGE_SAVE && !active) {
                color = accent;
            }
            ctx.drawText(tr, fit(tr, label, usable - 12), x + 7, y + (itemH - 10) / 2, color, false);
            final int p = i;
            hits.add(new Hit(x, y, x + usable, y + itemH - 2, false, () -> setPage(p)));
            y += itemH;
        }
    }

    private void drawTagline(DrawContext ctx, TextRenderer tr) {
        String t = "PLAY  •  CUSTOMIZE  •  DOMINATE";
        int color = 0x667F93B0;
        ctx.drawText(tr, t, px + (pw - tr.getWidth(t)) / 2, py + ph - 12, color, false);
    }

    private void positionFields() {
        if (pathField != null) {
            pathField.setVisible(page == PAGE_UPLOAD);
            pathField.setX(contentX + 8);
            pathField.setY(contentY + 40);
            pathField.setWidth(Math.max(60, contentW - 16));
        }
        if (renameField != null) {
            renameField.setVisible(page == PAGE_SETS && !renamingId.isEmpty());
            renameField.setX(contentX + 8);
            renameField.setY(contentY + contentH - 30);
            renameField.setWidth(Math.max(50, contentW - 116));
        }
        if (redeemField != null) {
            redeemField.setVisible(page == PAGE_COLLECTION);
            redeemField.setX(contentX + 8);
            redeemField.setY(contentY + contentH - 30);
            redeemField.setWidth(Math.max(40, contentW - 92));
        }
    }

    private void drawFields(DrawContext ctx, int mx, int my, float delta) {
        if (pathField != null && pathField.visible) {
            pathField.render(ctx, mx, my, delta);
        }
        if (renameField != null && renameField.visible) {
            renameField.render(ctx, mx, my, delta);
        }
        if (redeemField != null && redeemField.visible) {
            redeemField.render(ctx, mx, my, delta);
        }
    }

    private void drawScrollbar(DrawContext ctx, int accent) {
        int max = contentTotal - contentH;
        if (max <= 0) {
            return;
        }
        int barH = Math.max(16, contentH * contentH / Math.max(1, contentTotal));
        int barPos = contentY + (int) ((contentH - barH) * (scroll / max));
        ctx.fill(contentX + contentW - 3, barPos, contentX + contentW - 1, barPos + barH, accent);
    }

    private void drawCapes(DrawContext ctx, TextRenderer tr, int accent, int mx, int my) {
        int start = contentY + 4 - Math.round(scroll);
        int y = start;
        ctx.drawText(tr, "Cape Collection", contentX, y, Theme.TEXT, false);
        y += 13;
        ctx.drawText(tr, "Owned capes can be previewed and equipped. Ownership is checked by the RKB backend.", contentX, y,
                Theme.MUTED, false);
        y += 18;

        List<CosmeticsManager.Item> capes = CosmeticsManager.byType("cape");
        int cols = contentW >= 430 ? 2 : 1;
        int gap = 7;
        int cardW = Math.max(120, (contentW - gap * (cols - 1)) / cols);
        int cardH = 84;
        for (int i = 0; i < capes.size(); i++) {
            CosmeticsManager.Item item = capes.get(i);
            int x = contentX + (i % cols) * (cardW + gap);
            int cardY = y + (i / cols) * (cardH + gap);
            if (cardY + cardH < contentY - 12 || cardY > contentY + contentH + 12) {
                continue;
            }
            boolean equipped = CosmeticsManager.isEquipped(item.id());
            ctx.fill(x, cardY, x + cardW, cardY + cardH, Theme.CARD);
            outline(ctx, x, cardY, cardW, cardH, equipped ? accent : Theme.BORDER);
            if (equipped) {
                ctx.fill(x, cardY, x + 2, cardY + cardH, accent);
            }
            int texX = x + 8;
            int texY = cardY + 22;
            Identifier tex = CapeTextures.texture(item.id(), item.assetSha());
            if (tex == null) {
                CapeTextures.ensureBuiltin(item.id());
                ctx.fill(texX, texY, texX + 18, texY + 29, Theme.CARD_INNER);
            } else {
                drawScaledTexture(ctx, tex, texX, texY, 1f, 1f, 10, 16, 64, 32, 1.8f);
            }
            String status = equipped ? "EQUIPPED" : item.owned() ? "OWNED" : "NOT OWNED";
            int statusColor = equipped ? accent : item.owned() ? 0xFF39D98A : Theme.MUTED;
            ctx.drawText(tr, fit(tr, item.name(), cardW - 52), x + 32, cardY + 8, Theme.TEXT, false);
            ctx.drawText(tr, fit(tr, description(item), cardW - 52), x + 32, cardY + 18, Theme.MUTED, false);
            ctx.drawText(tr, status, x + 32, cardY + 32, statusColor, false);

            int by = cardY + cardH - 20;
            int right = x + cardW - 6;
            if (item.owned()) {
                if (equipped) {
                    actionButton(ctx, tr, "Unequip", right, by, true, false,
                            () -> CosmeticsManager.unequip("cape"));
                } else {
                    actionButton(ctx, tr, "Equip", right, by, true, true,
                            () -> CosmeticsManager.equip(item.id()));
                }
            } else if (item.claimable() && item.priceCents() == 0 && CosmeticsManager.ready()) {
                actionButton(ctx, tr, "Claim", right, by, true, false, () -> CosmeticsManager.claim(item.id()));
            }
            int previous = actionButtonWidth(tr, "Preview");
            actionButton(ctx, tr, "Preview", x + 6 + previous + 4, by, true, false, () -> {
                previewCapeId = item.id();
                setPage(PAGE_PREVIEW);
            });
        }
        contentTotal = y + ((capes.size() + cols - 1) / cols) * (cardH + gap) + 4 - start;
    }

    private void drawSkinPage(DrawContext ctx, TextRenderer tr, int accent, int mx, int my) {
        int start = contentY + 4 - Math.round(scroll);
        int y = start;
        ctx.drawText(tr, "Skin", contentX, y, Theme.TEXT, false);
        y += 14;
        y = wrapped(ctx, tr, "Your RKB skin is applied immediately, without restarting Minecraft. Verified RKB CLIENT "
                + "players see it in multiplayer; other launchers do not.", contentX, y, contentW, Theme.MUTED);
        y += 8;

        SkinSetStore.SkinSet active = SkinSetStore.activeSet();
        ctx.fill(contentX, y, contentX + contentW, y + 78, Theme.CARD);
        outline(ctx, contentX, y, contentX + contentW, y + 78, Theme.BORDER);
        drawPreview(ctx, contentX + 10, y + 8, 3, active, CosmeticsManager.equippedItem("cape"));
        ctx.drawText(tr, active == null ? "Using your normal Minecraft skin" : active.name(), contentX + 82, y + 10,
                Theme.TEXT, false);
        ctx.drawText(tr, active == null ? "No RKB skin is applied." : SkinRulesLabel(active),
                contentX + 82, y + 24, Theme.MUTED, false);
        ctx.drawText(tr, active == null ? "Choose a saved set or upload a PNG." : (active.remote() ? "Cloud synced" : "Local only"),
                contentX + 82, y + 38, active != null && active.remote() ? accent : Theme.MUTED, false);
        int by = y + 52;
        int right = contentX + contentW - 8;
        if (active != null) {
            actionButton(ctx, tr, "Remove", right, by, true, false, SkinSetStore::deactivate);
            right -= actionButtonWidth(tr, "Remove") + 5;
        }
        actionButton(ctx, tr, "Preview", right, by, true, false, () -> setPage(PAGE_PREVIEW));
        right -= actionButtonWidth(tr, "Preview") + 5;
        actionButton(ctx, tr, "Skin sets", right, by, true, true, () -> setPage(PAGE_SETS));
        y += 88;

        ctx.drawText(tr, "IDENTITY", contentX, y, accent, false);
        y += 14;
        y = infoRow(ctx, tr, "Minecraft username", PlayerIdentity.username(), y);
        y = infoRow(ctx, tr, "UUID", PlayerIdentity.shortUuid(), y);
        y = infoRow(ctx, tr, "Cloud status", PlayerIdentity.statusText(), y);
        y += 4;
        y = wrapped(ctx, tr, PlayerIdentity.statusDetail(), contentX, y, contentW, Theme.MUTED);
        contentTotal = y + 6 - start;
    }

    private void drawSkinSets(DrawContext ctx, TextRenderer tr, int accent, int mx, int my) {
        int start = contentY + 4 - Math.round(scroll);
        int y = start;
        List<SkinSetStore.SkinSet> sets = SkinSetStore.state().sets();
        ctx.drawText(tr, "Skin Sets", contentX, y, Theme.TEXT, false);
        ctx.drawText(tr, sets.size() + " / " + 20 + " saved", contentX + contentW - tr.getWidth(sets.size() + " / 20 saved"),
                y, Theme.MUTED, false);
        y += 15;
        y = wrapped(ctx, tr, "Save multiple named skins, rename or delete them, and switch without restarting. "
                + "Verified accounts sync eligible sets to the RKB backend.", contentX, y, contentW, Theme.MUTED);
        y += 8;

        if (sets.isEmpty()) {
            ctx.fill(contentX, y, contentX + contentW, y + 62, Theme.CARD);
            outline(ctx, contentX, y, contentX + contentW, y + 62, Theme.BORDER);
            ctx.drawText(tr, "No skin sets yet.", contentX + 10, y + 10, Theme.TEXT, false);
            ctx.drawText(tr, "Upload a 64x64 or 64x32 PNG, or drop one on the window.", contentX + 10, y + 24,
                    Theme.MUTED, false);
            actionButton(ctx, tr, "Upload skin", contentX + contentW - 8, y + 34, true, true, () -> setPage(PAGE_UPLOAD));
            y += 72;
        } else {
            for (SkinSetStore.SkinSet s : sets) {
                if (y + 38 < contentY - 12 || y > contentY + contentH + 12) {
                    y += 42;
                    continue;
                }
                boolean active = s.id().equals(SkinSetStore.state().activeId());
                ctx.fill(contentX, y, contentX + contentW, y + 38, Theme.CARD);
                outline(ctx, contentX, y, contentX + contentW, y + 38, active ? accent : Theme.BORDER);
                if (active) {
                    ctx.fill(contentX, y, contentX + 2, y + 38, accent);
                }
                ctx.drawText(tr, fit(tr, s.name(), contentW - 190), contentX + 10, y + 5, Theme.TEXT, false);
                ctx.drawText(tr, SkinRulesLabel(s) + "  " + (s.remote() ? "cloud" : "local"),
                        contentX + 10, y + 18, s.remote() ? accent : Theme.MUTED, false);
                int right = contentX + contentW - 8;
                if (confirmDelete.equals(s.id())) {
                    actionButton(ctx, tr, "Confirm", right, y + 11, true, false, () -> {
                        SkinSetStore.delete(s.id());
                        confirmDelete = "";
                    });
                    right -= actionButtonWidth(tr, "Confirm") + 4;
                } else {
                    actionButton(ctx, tr, "Delete", right, y + 11, true, false, () -> confirmDelete = s.id());
                    right -= actionButtonWidth(tr, "Delete") + 4;
                }
                actionButton(ctx, tr, "Rename", right, y + 11, true, false, () -> {
                    renamingId = s.id();
                    renameField.setText(s.name());
                    renameField.setFocused(true);
                });
                right -= actionButtonWidth(tr, "Rename") + 4;
                if (active) {
                    ctx.drawText(tr, "APPLIED", right - tr.getWidth("APPLIED"), y + 13, accent, false);
                } else {
                    actionButton(ctx, tr, "Apply", right, y + 11, true, true, () -> SkinSetStore.activate(s.id()));
                }
                y += 42;
            }
        }
        y += 4;
        ctx.drawText(tr, "Tip: drop a PNG anywhere on this screen to import it.", contentX, y, Theme.MUTED, false);
        contentTotal = y + 36 - start;
        if (!renamingId.isEmpty()) {
            actionButton(ctx, tr, "Save name", contentX + contentW - 8, contentY + contentH - 28, true, true, () -> {
                SkinSetStore.rename(renamingId, renameField.getText());
                renamingId = "";
                renameField.setFocused(false);
            });
            actionButton(ctx, tr, "Cancel", contentX + contentW - 8 - actionButtonWidth(tr, "Save name") - 5,
                    contentY + contentH - 28, true, false, () -> {
                        renamingId = "";
                        renameField.setFocused(false);
                    });
        }
    }

    private void drawEquipped(DrawContext ctx, TextRenderer tr, int accent, int mx, int my) {
        int start = contentY + 4 - Math.round(scroll);
        int y = start;
        ctx.drawText(tr, "Equipped Cosmetics", contentX, y, Theme.TEXT, false);
        y += 14;
        y = wrapped(ctx, tr, "What other RKB CLIENT players see on you. Your Minecraft username is never replaced.", contentX, y,
                contentW, Theme.MUTED);
        y += 8;
        CosmeticsManager.Item cape = CosmeticsManager.equippedItem("cape");
        CosmeticsManager.Item badge = CosmeticsManager.equippedItem("badge");
        y = equippedRow(ctx, tr, accent, "Cape", cape == null ? "None" : cape.name(), y,
                cape == null ? null : "Unequip", cape == null ? null : () -> CosmeticsManager.unequip("cape"),
                "Change", () -> setPage(PAGE_CAPES));
        SkinSetStore.SkinSet skin = SkinSetStore.activeSet();
        y = equippedRow(ctx, tr, accent, "Skin", skin == null ? "Normal Minecraft skin" : skin.name(), y,
                skin == null ? null : "Remove", skin == null ? null : SkinSetStore::deactivate,
                "Change", () -> setPage(PAGE_SETS));
        y = equippedRow(ctx, tr, accent, "Badge", badge == null ? "None" : badge.name(), y,
                badge == null ? null : "Unequip", badge == null ? null : () -> CosmeticsManager.unequip("badge"),
                "Collection", () -> setPage(PAGE_COLLECTION));
        y += 10;
        ctx.drawText(tr, "IDENTITY", contentX, y, accent, false);
        y += 14;
        y = infoRow(ctx, tr, "Username", PlayerIdentity.username(), y);
        y = infoRow(ctx, tr, "Minecraft UUID", PlayerIdentity.shortUuid(), y);
        y = infoRow(ctx, tr, "Visibility", PlayerIdentity.verifiedOnline() ? "Synced with verified UUID" : "Local only", y);
        y = wrapped(ctx, tr, PlayerIdentity.statusDetail(), contentX, y + 2, contentW, Theme.MUTED);
        contentTotal = y + 6 - start;
    }

    private void drawCollection(DrawContext ctx, TextRenderer tr, int accent, int mx, int my) {
        int start = contentY + 4 - Math.round(scroll);
        int y = start;
        ctx.drawText(tr, "RKB Collection", contentX, y, Theme.TEXT, false);
        y += 14;
        y = wrapped(ctx, tr, "Capes and badges from the RKB catalog. Ownership, prices and claimability are decided by "
                + "the backend; unowned items cannot be equipped.", contentX, y, contentW, Theme.MUTED);
        y += 8;

        List<CosmeticsManager.Item> items = CosmeticsManager.collection();
        String lastType = "";
        for (CosmeticsManager.Item item : items) {
            if (!item.type().equals(lastType)) {
                lastType = item.type();
                ctx.drawText(tr, lastType.toUpperCase(Locale.ROOT) + "S", contentX, y, accent, false);
                y += 13;
            }
            if (y + 30 >= contentY - 12 && y <= contentY + contentH + 12) {
                boolean equipped = CosmeticsManager.isEquipped(item.id());
                ctx.fill(contentX, y, contentX + contentW, y + 28, Theme.CARD);
                outline(ctx, contentX, y, contentX + contentW, y + 28, equipped ? accent : Theme.BORDER);
                ctx.drawText(tr, fit(tr, item.name(), contentW - 170), contentX + 8, y + 4, Theme.TEXT, false);
                String status = equipped ? "Equipped" : item.owned() ? "Owned" : "Not owned";
                if (!item.owned() && item.claimable() && item.priceCents() == 0) {
                    status = "Not owned · claimable";
                }
                ctx.drawText(tr, fit(tr, item.type() + "  " + item.rarity() + "  " + status + "  " + price(item),
                        contentW - 170), contentX + 8, y + 15, equipped ? accent : Theme.MUTED, false);
                int right = contentX + contentW - 7;
                if (item.owned()) {
                    if (equipped) {
                        actionButton(ctx, tr, "Unequip", right, y + 6, true, false,
                                () -> CosmeticsManager.unequip(item.type()));
                    } else {
                        actionButton(ctx, tr, "Equip", right, y + 6, true, true,
                                () -> CosmeticsManager.equip(item.id()));
                    }
                } else if (item.claimable() && item.priceCents() == 0 && CosmeticsManager.ready()) {
                    actionButton(ctx, tr, "Claim", right, y + 6, true, false, () -> CosmeticsManager.claim(item.id()));
                } else if (item.priceCents() > 0 && CosmeticsManager.ready()) {
                    actionButton(ctx, tr, "Buy", right, y + 6, true, false, () -> CosmeticsManager.purchaseStart(item.id()));
                }
            }
            y += 32;
        }
        // Reserve the redeem row at the bottom.
        y = Math.max(y, contentY + contentH - 38);
        ctx.drawText(tr, "Redeem a code", contentX, y, Theme.MUTED, false);
        int rx = contentX + contentW - 78;
        ctx.fill(rx, contentY + contentH - 30, rx + 78, contentY + contentH - 10, accent);
        ctx.drawText(tr, "Redeem", rx + (78 - tr.getWidth("Redeem")) / 2, contentY + contentH - 25, Theme.BLACK, false);
        hits.add(new Hit(rx, contentY + contentH - 30, rx + 78, contentY + contentH - 10, false, () -> {
            String code = redeemField.getText();
            redeemField.setText("");
            CosmeticsManager.redeem(code);
        }));
        contentTotal = Math.max(y + 42 - start, contentH);
    }

    private void drawUpload(DrawContext ctx, TextRenderer tr, int accent, int mx, int my) {
        int start = contentY + 4 - Math.round(scroll);
        int y = start;
        ctx.drawText(tr, "Upload Skin", contentX, y, Theme.TEXT, false);
        y += 14;
        y = wrapped(ctx, tr, "Use a Minecraft skin PNG: 64x64 (modern) or 64x32 (legacy), at most 256 KB. "
                + "Slim and classic models are detected automatically. The file is copied into the RKB library; "
                + "your original is not changed.", contentX, y, contentW, Theme.MUTED);
        y += 18;

        ctx.drawText(tr, "PNG path", contentX, y, accent, false);
        y += 24;
        ctx.drawText(tr, "Drop a PNG anywhere on this screen, or paste its path above and press Import.", contentX,
                y, Theme.MUTED, false);
        y += 20;
        int right = contentX + contentW - 8;
        actionButton(ctx, tr, "Import", right, contentY + 40, true, true, () -> {
            String p = pathField.getText();
            pathField.setText("");
            SkinSetStore.importPath(p);
        });
        right -= actionButtonWidth(tr, "Import") + 5;
        actionButton(ctx, tr, "Scan inbox", right, contentY + 40, true, false, SkinSetStore::scanInbox);
        y += 10;

        ctx.drawText(tr, "INBOX FOLDER", contentX, y, accent, false);
        y += 13;
        y = wrapped(ctx, tr, SkinSetStore.root().resolve("inbox").toAbsolutePath().toString(), contentX, y, contentW, Theme.MUTED);
        y += 8;
        ctx.fill(contentX, y, contentX + contentW, y + 54, Theme.CARD);
        outline(ctx, contentX, y, contentX + contentW, y + 54, Theme.BORDER);
        ctx.drawText(tr, "Model rules", contentX + 10, y + 7, Theme.TEXT, false);
        ctx.drawText(tr, "Classic (Steve) · Slim (Alex) · 64x64 or 64x32 · PNG only", contentX + 10, y + 20, Theme.MUTED, false);
        ctx.drawText(tr, "No executable files, no network fetch, no silent skin change.", contentX + 10, y + 33, Theme.MUTED, false);
        y += 64;

        List<SkinSetStore.SkinSet> sets = SkinSetStore.state().sets();
        if (!sets.isEmpty()) {
            ctx.drawText(tr, "RECENT", contentX, y, accent, false);
            y += 13;
            int shown = 0;
            for (SkinSetStore.SkinSet s : sets) {
                if (shown++ >= 4) {
                    break;
                }
                ctx.drawText(tr, fit(tr, s.name() + "   " + SkinRulesLabel(s), contentW - 80), contentX, y, Theme.TEXT, false);
                actionButton(ctx, tr, s.id().equals(SkinSetStore.state().activeId()) ? "Applied" : "Apply",
                        contentX + contentW - 8, y - 3, !s.id().equals(SkinSetStore.state().activeId()), false,
                        () -> SkinSetStore.activate(s.id()));
                y += 18;
            }
        }
        contentTotal = Math.max(y + 8 - start, contentH);
    }

    private void drawPreviewPage(DrawContext ctx, TextRenderer tr, int accent, int mx, int my) {
        int start = contentY + 4 - Math.round(scroll);
        int y = start;
        ctx.drawText(tr, "Preview", contentX, y, Theme.TEXT, false);
        y += 14;
        SkinSetStore.SkinSet skin = SkinSetStore.activeSet();
        if (skin == null) {
            for (SkinSetStore.SkinSet s : SkinSetStore.state().sets()) {
                skin = s;
                break;
            }
        }
        CosmeticsManager.Item cape = previewItem();
        int boxW = Math.min(180, contentW);
        int boxH = Math.min(190, contentH - 42);
        ctx.fill(contentX, y, contentX + boxW, y + boxH, Theme.CARD);
        outline(ctx, contentX, y, contentX + boxW, y + boxH, Theme.BORDER);
        drawPreview(ctx, contentX + 12, y + 10, 4, skin, cape);
        String label = skin == null ? "Normal Minecraft skin" : skin.name();
        ctx.drawText(tr, fit(tr, label, boxW - 20), contentX + 10, y + boxH - 16, Theme.TEXT, false);

        int sx = contentX + boxW + 12;
        int sy = y + 4;
        if (sx + 90 > contentX + contentW) {
            sx = contentX;
            sy = y + boxH + 8;
        }
        ctx.drawText(tr, "CURRENT", sx, sy, accent, false);
        sy += 14;
        sy = infoRowAt(ctx, tr, "Skin", skin == null ? "Normal" : skin.name(), sx, sy);
        sy = infoRowAt(ctx, tr, "Model", skin == null ? "Minecraft default" : SkinRulesLabel(skin), sx, sy);
        sy = infoRowAt(ctx, tr, "Cape", cape == null ? "None" : cape.name(), sx, sy);
        sy = infoRowAt(ctx, tr, "Player", PlayerIdentity.username(), sx, sy);
        sy += 6;
        SkinSetStore.SkinSet applySkin = skin;
        actionButton(ctx, tr, "Apply skin", sx + 100, sy, skin != null, true,
                () -> {
                    if (applySkin != null) {
                        SkinSetStore.activate(applySkin.id());
                    }
                });
        if (cape != null) {
            actionButton(ctx, tr, "Equip cape", sx + 100, sy + 20, true, false,
                    () -> CosmeticsManager.equip(cape.id()));
        }
        sy += 44;
        ctx.drawText(tr, "Capes", sx, sy, accent, false);
        sy += 13;
        int cw = 74;
        int n = 0;
        for (CapeCollection.Design d : CapeCollection.all()) {
            int cx = sx + (n % 4) * (cw + 4);
            int cy = sy + (n / 4) * 36;
            boolean selected = d.id().equals(previewCapeId);
            ctx.fill(cx, cy, cx + cw, cy + 32, selected ? Theme.BORDER : Theme.CARD);
            Identifier tex = CapeTextures.texture(d.id(), CapeTexturesSha(d.id()));
            if (tex != null) {
                drawScaledTexture(ctx, tex, cx + 4, cy + 2, 1f, 1f, 10, 16, 64, 32, 1.7f);
            }
            ctx.drawText(tr, fit(tr, shortName(d.name()), cw - 28), cx + 24, cy + 12, Theme.TEXT, false);
            hits.add(new Hit(cx, cy, cx + cw, cy + 32, true, () -> previewCapeId = d.id()));
            n++;
        }
        contentTotal = Math.max(y + boxH + 8 + 8 - start, contentH);
    }

    private void drawSavePage(DrawContext ctx, TextRenderer tr, int accent, int mx, int my) {
        int start = contentY + 4 - Math.round(scroll);
        int y = start;
        ctx.drawText(tr, "Save and Apply", contentX, y, Theme.TEXT, false);
        y += 14;
        y = wrapped(ctx, tr, "One action applies your selected skin and equipped cosmetics, saves the library, and pushes "
                + "eligible changes to your verified RKB account.", contentX, y, contentW, Theme.MUTED);
        y += 10;

        SkinSetStore.SkinSet skin = SkinSetStore.activeSet();
        CosmeticsManager.Item cape = CosmeticsManager.equippedItem("cape");
        CosmeticsManager.Item badge = CosmeticsManager.equippedItem("badge");
        y = infoRow(ctx, tr, "Skin set", skin == null ? "None" : skin.name(), y);
        y = infoRow(ctx, tr, "Skin model", skin == null ? "—" : SkinRulesLabel(skin), y);
        y = infoRow(ctx, tr, "Cape", cape == null ? "None" : cape.name(), y);
        y = infoRow(ctx, tr, "Badge", badge == null ? "None" : badge.name(), y);
        y = infoRow(ctx, tr, "Identity", PlayerIdentity.username() + "  (" + PlayerIdentity.shortUuid() + ")", y);
        y = infoRow(ctx, tr, "Cloud", PlayerIdentity.statusText(), y);
        y += 10;

        int bw = Math.min(240, contentW - 20);
        int bx = contentX + (contentW - bw) / 2;
        ctx.fill(bx, y, bx + bw, y + 34, accent);
        String label = "Save and Apply";
        ctx.drawText(tr, label, bx + (bw - tr.getWidth(label)) / 2, y + 11, Theme.BLACK, false);
        hits.add(new Hit(bx, y, bx + bw, y + 34, true, SkinSetStore::saveAndApply));
        y += 46;
        String hint = SkinCloudSync.eligible()
                ? "Verified UUID: changes are eligible for cloud sync."
                : "Local-only mode: sign in and run /rkb cloud link to sync. Redemption requires internet.";
        y = wrapped(ctx, tr, hint, contentX, y, contentW, SkinCloudSync.eligible() ? accent : Theme.MUTED);
        y += 8;
        if (SkinCloudSync.lastSyncMs() > 0) {
            ctx.drawText(tr, "Last cloud sync: " + ago(SkinCloudSync.lastSyncMs()), contentX, y, Theme.MUTED, false);
            y += 12;
        }
        actionButton(ctx, tr, "Refresh cloud", contentX + contentW - 8, y, true, false, SkinCloudSync::refresh);
        contentTotal = y + 28 - start;
    }

    // ------------------------------------------------------------------ pieces

    private CosmeticsManager.Item previewItem() {
        CosmeticsManager.Item selected = CosmeticsManager.item(previewCapeId);
        if (selected == null) {
            selected = CosmeticsManager.equippedItem("cape");
        }
        if (selected == null) {
            for (CosmeticsManager.Item i : CosmeticsManager.byType("cape")) {
                if (i.owned()) {
                    return i;
                }
            }
        }
        return selected;
    }

    private void drawPreview(DrawContext ctx, int x, int y, int scale, SkinSetStore.SkinSet set,
                             CosmeticsManager.Item cape) {
        // Shadow.
        ctx.fill(x + 3 * scale, y + 31 * scale, x + 13 * scale, y + 32 * scale, 0x66000000);
        Identifier capeTex = cape == null ? null : CapeTextures.texture(cape.id(), cape.assetSha());
        if (capeTex != null) {
            // The cape is drawn behind the body, like the in-game layer.
            drawScaledTexture(ctx, capeTex, x + 5 * scale, y + 8 * scale, 1f, 1f, 10, 16, 64, 32, scale);
        }
        Identifier skin = set == null ? null : SkinTexturesCache.texture(set.sha256());
        if (skin == null && set != null) {
            SkinTexturesCache.ensureLocal(set.sha256(), SkinSetStore.fileOf(set));
        }
        String model = set == null ? "classic" : set.model();
        int armW = "slim".equals(model) ? 3 : 4;
        if (skin == null) {
            int col = 0xFF1A2440;
            int hi = 0xFF263A63;
            ctx.fill(x + 5 * scale, y + 1 * scale, x + 11 * scale, y + 8 * scale, hi);
            ctx.fill(x + 6 * scale, y + 8 * scale, x + 10 * scale, y + 20 * scale, col);
            ctx.fill(x + 2 * scale, y + 9 * scale, x + 5 * scale, y + 20 * scale, col);
            ctx.fill(x + 11 * scale, y + 9 * scale, x + 14 * scale, y + 20 * scale, col);
            ctx.fill(x + 6 * scale, y + 20 * scale, x + 8 * scale, y + 30 * scale, col);
            ctx.fill(x + 9 * scale, y + 20 * scale, x + 11 * scale, y + 30 * scale, col);
            return;
        }
        // head, body, arms, legs; the texture is 64x64 or 64x32.
        int th = 64;
        drawScaledTexture(ctx, skin, x + 4 * scale, y, 8f, 8f, 8, 8, 64, th, scale);
        drawScaledTexture(ctx, skin, x + 4 * scale, y + 8 * scale, 20f, 20f, 8, 12, 64, th, scale);
        drawScaledTexture(ctx, skin, x, y + 8 * scale, 44f, 20f, armW, 12, 64, th, scale);
        int leftU = th == 64 ? 36 : 44;
        drawScaledTexture(ctx, skin, x + (4 + armW) * scale, y + 8 * scale, leftU, 20f, armW, 12, 64, th, scale);
        drawScaledTexture(ctx, skin, x + 4 * scale, y + 20 * scale, 4f, 20f, 4, 12, 64, th, scale);
        int leftLegU = th == 64 ? 20 : 4;
        drawScaledTexture(ctx, skin, x + 8 * scale, y + 20 * scale, leftLegU, 20f, 4, 12, 64, th, scale);
        // hat layer, kept subtle.
        drawScaledTexture(ctx, skin, x + 4 * scale, y, 40f, 8f, 8, 8, 64, th, scale);
    }

    private void drawScaledTexture(DrawContext ctx, Identifier id, int x, int y, float u, float v, int w, int h,
                                   int tw, int th, float scale) {
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(scale, scale);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, id, 0, 0, u, v, w, h, tw, th);
        m.popMatrix();
    }

    private int infoRow(DrawContext ctx, TextRenderer tr, String label, String value, int y) {
        return infoRowAt(ctx, tr, label, value, contentX, y);
    }

    private int infoRowAt(DrawContext ctx, TextRenderer tr, String label, String value, int x, int y) {
        ctx.fill(x, y, x + contentW, y + 18, Theme.CARD);
        ctx.drawText(tr, label, x + 7, y + 5, Theme.MUTED, false);
        ctx.drawText(tr, fit(tr, value, contentW - 120), x + Math.min(104, contentW / 3), y + 5, Theme.TEXT, false);
        return y + 20;
    }

    private int equippedRow(DrawContext ctx, TextRenderer tr, int accent, String label, String value, int y,
                            String removeLabel, Runnable remove, String changeLabel, Runnable change) {
        ctx.fill(contentX, y, contentX + contentW, y + 34, Theme.CARD);
        outline(ctx, contentX, y, contentX + contentW, y + 34, Theme.BORDER);
        ctx.drawText(tr, label, contentX + 8, y + 6, accent, false);
        ctx.drawText(tr, fit(tr, value, contentW - 150), contentX + 8, y + 18, Theme.TEXT, false);
        int right = contentX + contentW - 7;
        if (removeLabel != null && remove != null) {
            actionButton(ctx, tr, removeLabel, right, y + 9, true, false, remove);
            right -= actionButtonWidth(tr, removeLabel) + 4;
        }
        actionButton(ctx, tr, changeLabel, right, y + 9, true, true, change);
        return y + 38;
    }

    private int wrapped(DrawContext ctx, TextRenderer tr, String text, int x, int y, int width, int color) {
        int max = Math.max(20, width);
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String test = line.isEmpty() ? word : line + " " + word;
            if (tr.getWidth(test) > max && !line.isEmpty()) {
                ctx.drawText(tr, line.toString(), x, y, color, false);
                y += 11;
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(test);
            }
        }
        if (!line.isEmpty()) {
            ctx.drawText(tr, line.toString(), x, y, color, false);
            y += 11;
        }
        return y;
    }

    private int actionButton(DrawContext ctx, TextRenderer tr, String label, int right, int y, boolean enabled,
                             boolean primary, Runnable action) {
        int w = actionButtonWidth(tr, label);
        int x = right - w;
        int bg = !enabled ? Theme.CARD_INNER : primary ? Theme.accent() : Theme.BORDER;
        ctx.fill(x, y, x + w, y + 16, bg);
        ctx.drawText(tr, label, x + (w - tr.getWidth(label)) / 2, y + 4,
                !enabled ? Theme.OFF : primary ? Theme.BLACK : Theme.TEXT, false);
        if (enabled && action != null) {
            hits.add(new Hit(x, y, x + w, y + 16, true, action));
        }
        return x;
    }

    private int actionButtonWidth(TextRenderer tr, String label) {
        return Math.max(44, tr.getWidth(label) + 14);
    }

    private static void outline(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static String fit(TextRenderer tr, String s, int maxW) {
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

    private static String description(CosmeticsManager.Item item) {
        CapeCollection.Design d = CapeCollection.byId(item.id());
        if (d != null) {
            return d.description();
        }
        return item.description();
    }

    private static String price(CosmeticsManager.Item item) {
        if (item.priceCents() == 0) {
            return "Free";
        }
        return String.format(Locale.ROOT, "%d.%02d %s", item.priceCents() / 100, item.priceCents() % 100, item.currency());
    }

    private static String SkinRulesLabel(SkinSetStore.SkinSet set) {
        if (set == null) {
            return "";
        }
        return "slim".equals(set.model()) ? "Slim (Alex)" : "Classic (Steve)";
    }

    private static String CapeTexturesSha(String id) {
        String sha = CapeCollection.bundledSha(id);
        return sha == null ? "" : sha;
    }

    private static String shortName(String name) {
        return name.replace(" Cape", "");
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

    private void drawToast(DrawContext ctx, TextRenderer tr, int accent) {
        int w = Math.min(contentW - 8, tr.getWidth(toast) + 16);
        int x = contentX + (contentW - w) / 2;
        int y = contentY + contentH - 20;
        ctx.fill(x, y, x + w, y + 16, Theme.BLACK);
        outline(ctx, x, y, w, 16, accent);
        ctx.drawText(tr, fit(tr, toast, w - 10), x + 5, y + 4, Theme.TEXT, false);
    }
}
