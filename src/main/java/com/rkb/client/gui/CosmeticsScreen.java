package com.rkb.client.gui;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.rkb.client.cloud.CloudService;
import com.rkb.client.cloud.CloudStore;
import com.rkb.client.cosmetics.CapeCollection;
import com.rkb.client.cosmetics.CapeTextures;
import com.rkb.client.cosmetics.CosmeticsManager;
import com.rkb.client.cosmetics.PlayerIdentity;
import com.rkb.client.cosmetics.PlayerTextures;
import com.rkb.client.cosmetics.RkbLogo;
import com.rkb.client.cosmetics.SkinRules;
import com.rkb.client.cosmetics.SkinSelection;
import com.rkb.client.cosmetics.SkinSetStore;
import com.rkb.client.cosmetics.SkinTexturesCache;
import com.rkb.client.input.KeyUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.Text;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The single RKB cosmetics screen.
 *
 * One navigation strip (Skin / Cape / Redeem) replaces the old fourteen-page list. Skin and cape previews are rendered
 * with Minecraft's own player entity renderer, so the default Steve/Alex texture, applied local skins and equipped capes
 * all use the real in-game assets. All remote work remains in CosmeticsManager/CloudService and never runs on the
 * render thread.
 */
public class CosmeticsScreen extends Screen {
    public static final int TAB_SKIN = 0;
    public static final int TAB_CAPE = 1;
    public static final int TAB_REDEEM = 2;

    private enum Input { NONE, PATH, URL }

    private record Hit(int x1, int y1, int x2, int y2, boolean inList, Runnable action) {
        boolean contains(int x, int y) {
            return x >= x1 && x < x2 && y >= y1 && y < y2;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private CosmeticsLayout.Layout layout;
    private int tab = TAB_SKIN;
    private Input input = Input.NONE;
    private float listScroll;
    private int listTotal;

    private TextFieldWidget pathField;
    private TextFieldWidget urlField;
    private TextFieldWidget renameField;
    private TextFieldWidget redeemField;
    private int pathFieldY;
    private int urlFieldY;
    private int renameFieldY;
    private int redeemFieldY;

    private String selectedSkinId = "";
    private String selectedCapeId = "";
    private String renamingId = "";
    private String toast = "";
    private long toastUntil;

    private boolean mouseWasDown;
    private boolean dragging;
    private boolean pressedInList;
    private int pressY;
    private int lastY;

    private PreviewPlayer previewPlayer;
    private ClientWorld previewWorld;

    public CosmeticsScreen() {
        super(Text.literal("RKB Cosmetics"));
    }

    // ------------------------------------------------------------------ lifecycle / input

    @Override
    protected void init() {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        layout = CosmeticsLayout.of(width, height);

        String oldPath = pathField == null ? "" : pathField.getText();
        String oldUrl = urlField == null ? "" : urlField.getText();
        String oldRename = renameField == null ? "" : renameField.getText();
        String oldCode = redeemField == null ? "" : redeemField.getText();
        int fieldW = Math.max(80, layout.list().width() - 20);

        pathField = new TextFieldWidget(tr, layout.list().x() + 8, layout.list().y(), fieldW, 18,
                Text.literal("Skin PNG path"));
        pathField.setMaxLength(220);
        pathField.setPlaceholder(Text.literal("Paste a PNG path, then press Import"));
        pathField.setText(oldPath);
        addSelectableChild(pathField);

        urlField = new TextFieldWidget(tr, layout.list().x() + 8, layout.list().y(), fieldW, 18,
                Text.literal("Skin image URL"));
        urlField.setMaxLength(400);
        urlField.setPlaceholder(Text.literal("https://example.com/skin.png"));
        urlField.setText(oldUrl);
        addSelectableChild(urlField);

        renameField = new TextFieldWidget(tr, layout.list().x() + 8, layout.list().y(), fieldW - 96, 18,
                Text.literal("Skin name"));
        renameField.setMaxLength(SkinRules.NAME_MAX);
        renameField.setPlaceholder(Text.literal("Skin set name"));
        renameField.setText(oldRename);
        addSelectableChild(renameField);

        redeemField = new TextFieldWidget(tr, layout.list().x() + 8, layout.list().y(), fieldW - 86, 20,
                Text.literal("Redeem code"));
        redeemField.setMaxLength(40);
        redeemField.setPlaceholder(Text.literal("Enter code"));
        redeemField.setText(oldCode);
        addSelectableChild(redeemField);

        SkinSetStore.load();
        CosmeticsManager.refresh();
        for (CapeCollection.Design d : CapeCollection.all()) {
            CapeTextures.ensureBuiltin(d.id());
        }
        for (CosmeticsManager.Item item : CosmeticsManager.byType("cape")) {
            if (item.active() && item.assetReady()) {
                CapeTextures.ensure(item.id(), item.assetSha(), item.assetPath());
            }
        }
        SkinSetStore.SkinSet active = SkinSetStore.activeSet();
        if (active != null) {
            SkinTexturesCache.ensureLocal(active.sha256(), SkinSetStore.fileOf(active));
        }
        RkbLogo.ensure();

        if (selectedSkinId.isEmpty() || SkinSetStore.byId(selectedSkinId) == null) {
            selectedSkinId = active == null ? "" : active.id();
        }
        CosmeticsManager.Item equipped = CosmeticsManager.equippedItem("cape");
        if (selectedCapeId.isEmpty() || CosmeticsManager.item(selectedCapeId) == null) {
            selectedCapeId = equipped == null ? "" : equipped.id();
        }
        mouseWasDown = KeyUtil.isMouseDown(mc, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (layout != null && layout.list().contains((int) mouseX, (int) mouseY)) {
            listScroll -= (float) (verticalAmount * 24.0);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        TextFieldWidget field = activeField();
        if (field != null && field.isFocused()) {
            if (field.keyPressed(input)) {
                return true;
            }
            if (input.isEscape()) {
                field.setFocused(false);
                return true;
            }
        }
        if (input.isEscape()) {
            MinecraftClient.getInstance().setScreen(new RkbMenuScreen());
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
        setTab(TAB_SKIN);
        input = Input.PATH;
        SkinSetStore.importFiles(pngs);
        showToast("Importing " + pngs.size() + " skin" + (pngs.size() == 1 ? "" : "s") + "...");
    }

    private TextFieldWidget activeField() {
        if (tab == TAB_SKIN && input == Input.PATH && pathField != null && pathField.isFocused()) {
            return pathField;
        }
        if (tab == TAB_SKIN && input == Input.URL && urlField != null && urlField.isFocused()) {
            return urlField;
        }
        if (tab == TAB_SKIN && !renamingId.isEmpty() && renameField != null && renameField.isFocused()) {
            return renameField;
        }
        if (tab == TAB_REDEEM && redeemField != null && redeemField.isFocused()) {
            return redeemField;
        }
        return null;
    }

    private void blurFields() {
        if (pathField != null) {
            pathField.setFocused(false);
        }
        if (urlField != null) {
            urlField.setFocused(false);
        }
        if (renameField != null) {
            renameField.setFocused(false);
        }
        if (redeemField != null) {
            redeemField.setFocused(false);
        }
    }

    private void setTab(int next) {
        tab = next;
        input = Input.NONE;
        renamingId = "";
        listScroll = 0;
        blurFields();
    }

    private void showToast(String text) {
        toast = text == null ? "" : text;
        toastUntil = System.currentTimeMillis() + 2800;
    }

    private void handleMouse(MinecraftClient mc, int mx, int my) {
        boolean down = KeyUtil.isMouseDown(mc, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        if (down && !mouseWasDown) {
            pressY = my;
            lastY = my;
            dragging = false;
            pressedInList = layout != null && layout.list().contains(mx, my) && fieldAt(mx, my) == null;
        } else if (down) {
            if (!dragging && pressedInList && Math.abs(my - pressY) > 4) {
                dragging = true;
            }
            if (dragging) {
                listScroll -= (my - lastY);
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
            if (h.contains(mx, my)) {
                h.action().run();
                return;
            }
        }
    }

    private TextFieldWidget fieldAt(int mx, int my) {
        if (tab == TAB_SKIN && input == Input.PATH && pathField != null && pathField.visible
                && pathField.isMouseOver(mx, my)) {
            return pathField;
        }
        if (tab == TAB_SKIN && input == Input.URL && urlField != null && urlField.visible
                && urlField.isMouseOver(mx, my)) {
            return urlField;
        }
        if (tab == TAB_SKIN && !renamingId.isEmpty() && renameField != null && renameField.visible
                && renameField.isMouseOver(mx, my)) {
            return renameField;
        }
        if (tab == TAB_REDEEM && redeemField != null && redeemField.visible && redeemField.isMouseOver(mx, my)) {
            return redeemField;
        }
        return null;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        MinecraftClient mc = MinecraftClient.getInstance();
        handleMouse(mc, mx, my);
        hits.clear();
        TextRenderer tr = mc.textRenderer;
        int accent = Theme.accent();

        ctx.fill(0, 0, width, height, 0xD0050814);
        drawPanel(ctx, layout.panel(), Theme.PANEL, Theme.BORDER, accent);
        drawHeader(ctx, tr, accent, mx, my);
        drawTabs(ctx, tr, accent, mx, my);

        ctx.enableScissor(layout.preview().x(), layout.preview().y(), layout.preview().right(),
                layout.preview().bottom());
        drawPreview(ctx, tr, accent, mx, my, delta);
        ctx.disableScissor();

        ctx.enableScissor(layout.list().x(), layout.list().y(), layout.list().right(), layout.list().bottom());
        listTotal = layout.list().height();
        switch (tab) {
            case TAB_CAPE -> drawCapeList(ctx, tr, accent);
            case TAB_REDEEM -> drawRedeemList(ctx, tr, accent);
            default -> drawSkinList(ctx, tr, accent);
        }
        ctx.disableScissor();

        listScroll = Math.max(0, Math.min(Math.max(0, listTotal - layout.list().height()), listScroll));
        positionFields();
        drawScrollbar(ctx, accent);
        ctx.enableScissor(layout.list().x(), layout.list().y(), layout.list().right(), layout.list().bottom());
        drawFields(ctx, mx, my, delta);
        ctx.disableScissor();
        if (System.currentTimeMillis() < toastUntil) {
            drawToast(ctx, tr, accent);
        }
    }

    private void drawHeader(DrawContext ctx, TextRenderer tr, int accent, int mx, int my) {
        CosmeticsLayout.Rect h = layout.header();
        int logo = Math.min(30, Math.max(22, h.height() - 14));
        int lx = h.x() + 2;
        int ly = h.y() + (h.height() - logo) / 2;
        if (RkbLogo.ready()) {
            RkbLogo.draw(ctx, lx, ly, logo);
        } else {
            ctx.fill(lx + 2, ly + 2, lx + logo - 2, ly + logo - 2, accent);
            ctx.drawText(tr, "R", lx + logo / 3, ly + logo / 4, Theme.BLACK, true);
        }

        int textX = lx + logo + 10;
        ctx.drawText(tr, Text.literal("RKB CLIENT").styled(s -> s.withBold(true)), textX, h.y() + 5, Theme.TEXT,
                false);
        String player = PlayerIdentity.username();
        if (player.isEmpty()) {
            player = "Player";
        }
        ctx.drawText(tr, "COSMETICS  ·  " + fit(tr, player, Math.max(60, h.width() / 3)), textX, h.y() + 19,
                accent, false);

        int closeW = 26;
        int closeX = h.right() - closeW;
        boolean closeHover = mx >= closeX && mx < h.right() && my >= h.y() + 2 && my < h.y() + 26;
        drawPanel(ctx, new CosmeticsLayout.Rect(closeX, h.y() + 2, closeW, 24),
                closeHover ? Theme.CARD_HOVER : Theme.CARD, Theme.BORDER, accent);
        ctx.drawText(tr, "X", closeX + 9, h.y() + 9, Theme.TEXT, false);
        hits.add(new Hit(closeX, h.y() + 2, h.right(), h.y() + 26, false,
                () -> MinecraftClient.getInstance().setScreen(new RkbMenuScreen())));

        String status = CloudService.statusText();
        int statusW = Math.min(220, Math.max(110, tr.getWidth(status) + 30));
        int statusX = closeX - statusW - 8;
        if (statusX > textX + 120) {
            int sy = h.y() + 6;
            drawPanel(ctx, new CosmeticsLayout.Rect(statusX, sy, statusW, 22), Theme.CARD, Theme.BORDER, accent);
            int dot = CloudService.status() == CloudService.Status.CONNECTED ? 0xFF39D98A
                    : CloudService.status() == CloudService.Status.ERROR ? 0xFFFF6B6B
                    : PlayerIdentity.signedIn() ? 0xFFFFC857 : Theme.MUTED;
            ctx.fill(statusX + 8, sy + 9, statusX + 12, sy + 13, dot);
            ctx.drawText(tr, fit(tr, status, statusW - 22), statusX + 17, sy + 7,
                    dot == 0xFF39D98A ? Theme.TEXT : Theme.MUTED, false);
        }
    }

    private void drawTabs(DrawContext ctx, TextRenderer tr, int accent, int mx, int my) {
        for (int i = 0; i < layout.tabRects().size(); i++) {
            CosmeticsLayout.Rect r = layout.tabRects().get(i);
            boolean active = i == tab;
            boolean hover = r.contains(mx, my);
            int bg = active ? accent : hover ? Theme.CARD_HOVER : Theme.CARD;
            drawPanel(ctx, r, bg, active ? accent : Theme.BORDER, accent);
            int color = active ? Theme.BLACK : hover ? Theme.TEXT : Theme.MUTED;
            String label = CosmeticsLayout.TABS[i].toUpperCase(Locale.ROOT);
            ctx.drawText(tr, label, r.x() + (r.width() - tr.getWidth(label)) / 2,
                    r.y() + (r.height() - 9) / 2, color, false);
            final int picked = i;
            hits.add(new Hit(r.x(), r.y(), r.right(), r.bottom(), false, () -> setTab(picked)));
        }
    }

    private void drawPreview(DrawContext ctx, TextRenderer tr, int accent, int mx, int my, float delta) {
        CosmeticsLayout.Rect box = layout.preview();
        ctx.fillGradient(box.x(), box.y(), box.right(), box.bottom(), 0xFF111B30, 0xFF0A0F1D);
        softOutline(ctx, box, Theme.BORDER);
        ctx.fill(box.x() + 10, box.y() + 10, box.x() + 46, box.y() + 12, accent);

        SkinSelection selection = selectedSkin();
        SkinSetStore.SkinSet set = SkinSetStore.byId(selectedSkinId);
        CosmeticsManager.Item cape = tab == TAB_CAPE ? selectedCapeItem() : CosmeticsManager.equippedItem("cape");

        int modelBottom = box.bottom() - 54;
        int drawBottom = Math.max(box.y() + 120, modelBottom - 4);
        drawPlayerModel(ctx, new CosmeticsLayout.Rect(box.x() + 6, box.y() + 18, box.width() - 12,
                drawBottom - box.y() - 18), selection, cape, tab == TAB_CAPE ? 0f : 180f, mx, my, delta);

        String skinLabel = selection.custom() ? selection.name() : "Minecraft default skin";
        String modelLabel = selection.custom() ? skinModelLabel(selection.model()) : "Vanilla Steve/Alex texture";
        String capeLabel = cape == null ? "No cape" : cape.name() + (CosmeticsManager.isEquipped(cape.id()) ? "  ·  equipped" : "");
        ctx.drawText(tr, fit(tr, skinLabel, box.width() - 20), box.x() + 10, modelBottom - 22, Theme.TEXT, false);
        ctx.drawText(tr, fit(tr, modelLabel, box.width() - 20), box.x() + 10, modelBottom - 10, Theme.MUTED, false);
        ctx.drawText(tr, fit(tr, capeLabel, box.width() - 20), box.x() + 10, modelBottom + 2,
                cape != null && CosmeticsManager.isEquipped(cape.id()) ? accent : Theme.MUTED, false);

        int buttonY = box.bottom() - 32;
        if (tab == TAB_SKIN) {
            if (selection.custom()) {
                int toggleW = Math.min(70, box.width() / 3);
                String model = skinModelLabel(selection.model()).startsWith("Slim") ? "Classic" : "Slim";
                actionButton(ctx, tr, model, box.x() + toggleW + 6, buttonY, true, false, () -> {
                    String next = skinModelLabel(selection.model()).startsWith("Slim")
                            ? SkinRules.MODEL_CLASSIC : SkinRules.MODEL_SLIM;
                    SkinSetStore.setModel(selectedSkinId, next);
                    showToast("Skin model: " + SkinRules.label(next));
                });
                actionButton(ctx, tr, "Apply Skin", box.right() - 8, buttonY, true, true, () -> {
                    SkinSetStore.activate(selectedSkinId);
                    SkinSetStore.saveAndApply();
                    showToast("Skin applied locally.");
                });
            } else {
                actionButton(ctx, tr, "Apply Skin", box.right() - 8, buttonY, false, true, null);
            }
            actionButton(ctx, tr, "Reset to Default", box.right() - 8 - actionButtonWidth(tr, "Apply Skin") - 22,
                    buttonY, SkinSetStore.activeSet() != null, false, () -> {
                        SkinSetStore.deactivate();
                        selectedSkinId = "";
                        showToast("Minecraft default skin restored.");
                    });
        } else if (tab == TAB_CAPE) {
            boolean equipped = cape != null && CosmeticsManager.isEquipped(cape.id());
            actionButton(ctx, tr, "Remove Cape", box.right() - 8, buttonY,
                    CosmeticsManager.equippedItem("cape") != null, false,
                    () -> CosmeticsManager.unequip("cape"));
            actionButton(ctx, tr, equipped ? "Equipped" : "Apply Cape",
                    box.right() - 8 - actionButtonWidth(tr, "Remove Cape") - 22, buttonY,
                    cape != null && cape.usable() && !equipped, true,
                    () -> {
                        CosmeticsManager.equip(cape.id());
                        showToast("Applying " + cape.name() + "...");
                    });
        } else {
            ctx.drawText(tr, fit(tr, "Live V2 inventory · " + CloudService.statusText(), box.width() - 20),
                    box.x() + 10, buttonY + 5, Theme.MUTED, false);
        }
    }

    private void drawSkinList(DrawContext ctx, TextRenderer tr, int accent) {
        CosmeticsLayout.Rect r = layout.list();
        int y = r.y() + 6 - Math.round(listScroll);
        ctx.drawText(tr, "SKIN COLLECTION", r.x() + 4, y, accent, false);
        y += 14;
        y = wrapped(ctx, tr, "Local skins only. The default row uses the real Steve/Alex texture for this UUID.",
                r.x() + 4, y, r.width() - 8, Theme.MUTED);
        y += 7;

        boolean defaultSelected = selectedSkinId.isEmpty() || SkinSetStore.byId(selectedSkinId) == null;
        y = drawSkinRow(ctx, tr, accent, null, defaultSelected, r, y);

        List<SkinSetStore.SkinSet> sets = SkinSetStore.state().sets();
        if (sets.isEmpty()) {
            ctx.drawText(tr, "No saved skins yet. Upload a PNG or import an image URL below.", r.x() + 8, y + 8,
                    Theme.MUTED, false);
            y += 30;
        } else {
            for (SkinSetStore.SkinSet set : sets) {
                y = drawSkinRow(ctx, tr, accent, set, set.id().equals(selectedSkinId), r, y);
            }
        }
        y += 6;

        y = drawAddSkinCard(ctx, tr, accent, r, y);
        listTotal = y - r.y() + 10;
    }

    private int drawSkinRow(DrawContext ctx, TextRenderer tr, int accent, SkinSetStore.SkinSet set, boolean selected,
                            CosmeticsLayout.Rect r, int y) {
        int h = set == null ? 38 : 42;
        CosmeticsLayout.Rect row = new CosmeticsLayout.Rect(r.x() + 2, y, r.width() - 6, h);
        drawPanel(ctx, row, selected ? Theme.CARD_HOVER : Theme.CARD, selected ? accent : Theme.BORDER, accent);
        if (selected) {
            ctx.fill(row.x(), row.y() + 4, row.x() + 3, row.bottom() - 4, accent);
        }
        String name = set == null ? "Minecraft default" : set.name();
        String sub = set == null ? "Real vanilla texture · " + PlayerIdentity.shortUuid() : SkinRules.label(set.model()) + "  ·  local PNG";
        ctx.drawText(tr, fit(tr, name, row.width() - 110), row.x() + 10, row.y() + 6, Theme.TEXT, false);
        ctx.drawText(tr, fit(tr, sub, row.width() - 110), row.x() + 10, row.y() + 19, Theme.MUTED, false);

        int right = row.right() - 7;
        if (set == null) {
            ctx.drawText(tr, selected ? "SELECTED" : "Default", right - tr.getWidth(selected ? "SELECTED" : "Default"),
                    row.y() + 14, selected ? accent : Theme.MUTED, false);
            hits.add(new Hit(row.x(), row.y(), row.right(), row.bottom(), true, () -> {
                selectedSkinId = "";
                listScroll = 0;
            }));
            return y + h + 4;
        }

        if (renamingId.equals(set.id())) {
            renameFieldY = row.y() + h + 2;
            actionButton(ctx, tr, "Save", right, row.y() + 12, true, true, () -> {
                SkinSetStore.rename(set.id(), renameField.getText());
                renamingId = "";
                renameField.setFocused(false);
            });
            actionButton(ctx, tr, "Cancel", right - actionButtonWidth(tr, "Save") - 5, row.y() + 12, true, false, () -> {
                renamingId = "";
                renameField.setFocused(false);
            });
        } else if (selected) {
            actionButton(ctx, tr, "Delete", right, row.y() + 12, true, false, () -> SkinSetStore.delete(set.id()));
            actionButton(ctx, tr, "Rename", right - actionButtonWidth(tr, "Delete") - 5, row.y() + 12, true, false, () -> {
                renamingId = set.id();
                renameField.setText(set.name());
                renameField.setFocused(true);
            });
        } else {
            ctx.drawText(tr, set.id().equals(SkinSetStore.state().activeId()) ? "APPLIED" : "SELECT",
                    right - tr.getWidth(set.id().equals(SkinSetStore.state().activeId()) ? "APPLIED" : "SELECT"),
                    row.y() + 14, set.id().equals(SkinSetStore.state().activeId()) ? accent : Theme.MUTED, false);
        }
        hits.add(new Hit(row.x(), row.y(), row.right(), row.bottom(), true, () -> {
            selectedSkinId = set.id();
            renamingId = "";
            listScroll = 0;
        }));
        return y + h + 4;
    }

    private int drawAddSkinCard(DrawContext ctx, TextRenderer tr, int accent, CosmeticsLayout.Rect r, int y) {
        CosmeticsLayout.Rect card = new CosmeticsLayout.Rect(r.x() + 2, y, r.width() - 6, 76);
        drawPanel(ctx, card, Theme.CARD_INNER, Theme.BORDER, accent);
        ctx.drawText(tr, "ADD A SKIN", card.x() + 10, card.y() + 8, accent, false);
        ctx.drawText(tr, "PNG, 64x64 or 64x32 · at most 256 KB · stored locally", card.x() + 10, card.y() + 21,
                Theme.MUTED, false);

        int by = card.y() + 38;
        int right = card.right() - 9;
        actionButton(ctx, tr, "Import URL", right, by, true, false, () -> {
            input = input == Input.URL ? Input.NONE : Input.URL;
            listScroll = 0;
        });
        actionButton(ctx, tr, "Upload PNG", right - actionButtonWidth(tr, "Import URL") - 5, by, true, false, () -> {
            input = input == Input.PATH ? Input.NONE : Input.PATH;
            listScroll = 0;
        });
        actionButton(ctx, tr, "Scan inbox", right - actionButtonWidth(tr, "Upload PNG") - actionButtonWidth(tr, "Import URL") - 10,
                by, true, false, () -> {
                    SkinSetStore.scanInbox();
                    showToast("Checking the local inbox folder...");
                });

        int fieldY = card.y() + 58;
        if (input == Input.PATH) {
            ctx.drawText(tr, "PNG path", card.x() + 10, fieldY + 4, accent, false);
            pathFieldY = fieldY - 4;
            actionButton(ctx, tr, "Import", card.right() - 9, fieldY - 2, true, true, () -> {
                String value = pathField.getText();
                pathField.setText("");
                SkinSetStore.importPath(value);
                showToast("Importing local skin...");
            });
        } else if (input == Input.URL) {
            ctx.drawText(tr, "PNG URL", card.x() + 10, fieldY + 4, accent, false);
            urlFieldY = fieldY - 4;
            actionButton(ctx, tr, "Import", card.right() - 9, fieldY - 2, true, true, () -> {
                String value = urlField.getText();
                urlField.setText("");
                SkinSetStore.importUrl(value);
                showToast("Fetching skin over HTTPS...");
            });
        }
        if (input != Input.NONE) {
            ctx.drawText(tr, "Local only · nothing is uploaded to Supabase", card.x() + 10, card.bottom() - 12,
                    Theme.MUTED, false);
        }
        return card.bottom() + 8;
    }

    private void drawCapeList(DrawContext ctx, TextRenderer tr, int accent) {
        CosmeticsLayout.Rect r = layout.list();
        int y = r.y() + 6 - Math.round(listScroll);
        ctx.drawText(tr, "CAPE COLLECTION", r.x() + 4, y, accent, false);
        y += 14;
        y = wrapped(ctx, tr, "Ownership and assets come from V2. Missing or inactive capes stay unavailable.",
                r.x() + 4, y, r.width() - 8, Theme.MUTED);
        y += 8;

        List<CosmeticsManager.Item> all = CosmeticsManager.byType("cape");
        y = drawCapeGroup(ctx, tr, accent, r, y, "RKB ORIGINAL", all.stream()
                .filter(i -> CapeCollection.byId(i.id()) == null || "RKB".equals(CapeCollection.byId(i.id()).country()))
                .toList());
        y = drawCapeGroup(ctx, tr, accent, r, y, "COUNTRY CAPES", all.stream()
                .filter(i -> CapeCollection.byId(i.id()) != null && !"RKB".equals(CapeCollection.byId(i.id()).country()))
                .toList());

        if (all.isEmpty()) {
            ctx.drawText(tr, "The catalog has not loaded yet. Local bundled designs remain unavailable until V2 "
                    + "confirms ownership.", r.x() + 8, y + 8, Theme.MUTED, false);
            y += 34;
        }
        listTotal = y - r.y() + 10;
    }

    private int drawCapeGroup(DrawContext ctx, TextRenderer tr, int accent, CosmeticsLayout.Rect r, int y,
                              String title, List<CosmeticsManager.Item> capes) {
        if (capes.isEmpty()) {
            return y;
        }
        ctx.drawText(tr, title, r.x() + 4, y, accent, false);
        y += 13;
        for (CosmeticsManager.Item item : capes) {
            int h = 52;
            CosmeticsLayout.Rect row = new CosmeticsLayout.Rect(r.x() + 2, y, r.width() - 6, h);
            boolean equipped = CosmeticsManager.isEquipped(item.id());
            boolean selected = item.id().equals(selectedCapeId);
            drawPanel(ctx, row, selected ? Theme.CARD_HOVER : Theme.CARD, equipped ? accent : selected ? Theme.BORDER : Theme.BORDER,
                    accent);
            if (equipped) {
                ctx.fill(row.x(), row.y() + 5, row.x() + 3, row.bottom() - 5, accent);
            }

            Identifier tex = CapeTextures.texture(item.id(), item.assetSha());
            int tx = row.x() + 8;
            int ty = row.y() + 10;
            if (tex != null) {
                drawScaledTexture(ctx, tex, tx, ty, 1f, 1f, 6, 4, 64, 32, 1.5f);
            } else {
                ctx.fill(tx, ty, tx + 28, ty + 18, Theme.CARD_INNER);
            }
            int textX = tx + 36;
            String name = item.name().isEmpty() ? item.id() : item.name();
            ctx.drawText(tr, fit(tr, name, row.width() - 150), textX, row.y() + 8, Theme.TEXT, false);
            String status = equipped ? "EQUIPPED" : item.usable() ? "OWNED · READY"
                    : !item.active() ? "NOT IN CATALOG" : !item.assetReady() ? "ASSET UNAVAILABLE" : "NOT OWNED";
            int statusColor = equipped ? accent : item.usable() ? 0xFF39D98A : Theme.MUTED;
            ctx.drawText(tr, status, textX, row.y() + 21, statusColor, false);
            String detail = item.rarity().isEmpty() ? item.type() : item.rarity() + "  ·  " + item.type();
            ctx.drawText(tr, fit(tr, detail, row.width() - 150), textX, row.y() + 34, Theme.MUTED, false);

            int right = row.right() - 7;
            if (item.usable()) {
                actionButton(ctx, tr, equipped ? "Unequip" : "Equip", right, row.y() + 16, true, equipped,
                        () -> {
                            if (equipped) {
                                CosmeticsManager.unequip("cape");
                            } else {
                                CosmeticsManager.equip(item.id());
                                showToast("Applying " + name + "...");
                            }
                        });
            }
            hits.add(new Hit(row.x(), row.y(), row.right(), row.bottom(), true, () -> {
                if (item.usable()) {
                    selectedCapeId = item.id();
                    listScroll = 0;
                }
            }));
            y += h + 5;
        }
        return y + 8;
    }

    private void drawRedeemList(DrawContext ctx, TextRenderer tr, int accent) {
        CosmeticsLayout.Rect r = layout.list();
        int y = r.y() + 6 - Math.round(listScroll);
        ctx.drawText(tr, "REDEEM", r.x() + 4, y, accent, false);
        y += 14;

        CosmeticsLayout.Rect card = new CosmeticsLayout.Rect(r.x() + 2, y, r.width() - 6, 104);
        drawPanel(ctx, card, Theme.CARD, Theme.BORDER, accent);
        ctx.drawText(tr, "CODE", card.x() + 10, card.y() + 9, accent, false);
        redeemFieldY = card.y() + 23;
        boolean busy = CosmeticsManager.busy();
        actionButton(ctx, tr, busy ? "Working..." : "Redeem", card.right() - 10, card.y() + 24,
                !busy && redeemField.getText() != null && !redeemField.getText().isBlank(), true, () -> {
                    String code = redeemField.getText();
                    redeemField.setText("");
                    CosmeticsManager.redeem(code);
                });
        ctx.drawText(tr, busy ? "Contacting the authenticated V2 function..." : "Codes are accepted only while signed in and linked.",
                card.x() + 10, card.y() + 52, busy ? accent : Theme.MUTED, false);
        String message = CosmeticsManager.message();
        int resultColor = message.isEmpty() ? Theme.MUTED : CosmeticsManager.lastResultOk() ? 0xFF39D98A : 0xFFFF8B8B;
        ctx.drawText(tr, fit(tr, message.isEmpty() ? "No code submitted in this session." : message, card.width() - 20),
                card.x() + 10, card.y() + 70, resultColor, false);
        ctx.drawText(tr, "V2 has no redemption-history action; no history is shown.", card.x() + 10, card.y() + 86,
                Theme.MUTED, false);
        y = card.bottom() + 10;

        CosmeticsLayout.Rect cloud = new CosmeticsLayout.Rect(r.x() + 2, y, r.width() - 6, 104);
        drawPanel(ctx, cloud, Theme.CARD_INNER, Theme.BORDER, accent);
        ctx.drawText(tr, "CLOUD INVENTORY", cloud.x() + 10, cloud.y() + 8, accent, false);
        ctx.drawText(tr, fit(tr, CloudService.statusText(), cloud.width() - 20), cloud.x() + 10, cloud.y() + 22,
                Theme.TEXT, false);
        String account = CloudStore.signedIn()
                ? (CloudStore.email().isEmpty() ? "signed in" : CloudStore.email()) : "not signed in";
        String linked = CloudService.isLinked() ? CloudStore.linkedName() + " · " + PlayerIdentity.shortUuidFor(CloudStore.linkedUuid())
                : "not linked";
        ctx.drawText(tr, fit(tr, account, cloud.width() / 2 - 16), cloud.x() + 10, cloud.y() + 38, Theme.MUTED, false);
        ctx.drawText(tr, fit(tr, linked, cloud.width() / 2 - 16), cloud.x() + cloud.width() / 2 + 6, cloud.y() + 38,
                Theme.MUTED, false);
        ctx.drawText(tr, "Last sync: " + (CloudStore.lastSyncMs() <= 0 ? "never" : ago(CloudStore.lastSyncMs())),
                cloud.x() + 10, cloud.y() + 52, Theme.MUTED, false);
        int by = cloud.y() + 72;
        actionButton(ctx, tr, "Sync now", cloud.right() - 10, by, CosmeticsManager.ready(), true,
                CloudService::manualSync);
        actionButton(ctx, tr, "Link Minecraft", cloud.right() - 10 - actionButtonWidth(tr, "Sync now") - 5, by,
                CosmeticsManager.ready(), false, CloudService::link);
        actionButton(ctx, tr, "Refresh", cloud.right() - 10 - actionButtonWidth(tr, "Sync now")
                - actionButtonWidth(tr, "Link Minecraft") - 10, by, CosmeticsManager.ready(), false,
                CosmeticsManager::refresh);
        y = cloud.bottom() + 10;

        List<CosmeticsManager.Item> owned = new ArrayList<>();
        for (CosmeticsManager.Item item : CosmeticsManager.state().items()) {
            if (item.owned()) {
                owned.add(item);
            }
        }
        ctx.drawText(tr, "OWNED ITEMS  ·  " + owned.size(), r.x() + 4, y, accent, false);
        y += 14;
        if (owned.isEmpty()) {
            ctx.drawText(tr, "No V2 inventory is loaded. Local skins and bundled cape textures still work.",
                    r.x() + 8, y + 4, Theme.MUTED, false);
            y += 24;
        } else {
            for (CosmeticsManager.Item item : owned) {
                CosmeticsLayout.Rect row = new CosmeticsLayout.Rect(r.x() + 2, y, r.width() - 6, 30);
                drawPanel(ctx, row, Theme.CARD, Theme.BORDER, accent);
                String status = CosmeticsManager.isEquipped(item.id()) ? "equipped" : item.usable() ? "ready" : "unavailable";
                ctx.drawText(tr, fit(tr, item.name(), row.width() - 100), row.x() + 8, row.y() + 4, Theme.TEXT, false);
                ctx.drawText(tr, fit(tr, item.type() + "  ·  " + status, row.width() - 100), row.x() + 8, row.y() + 16,
                        Theme.MUTED, false);
                if (!"cape".equals(item.type()) && item.usable() && !CosmeticsManager.isEquipped(item.id())) {
                    actionButton(ctx, tr, "Equip", row.right() - 7, row.y() + 7, true, true,
                            () -> CosmeticsManager.equip(item.id()));
                }
                y += 34;
            }
        }
        listTotal = y - r.y() + 10;
    }

    // ------------------------------------------------------------------ 3D preview

    private void drawPlayerModel(DrawContext ctx, CosmeticsLayout.Rect box, SkinSelection selection,
                                 CosmeticsManager.Item cape, float baseYaw, int mx, int my, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) {
            ctx.drawText(mc.textRenderer, "3D preview is available from the in-game menu.", box.x() + 10,
                    box.y() + 10, Theme.MUTED, false);
            return;
        }

        Identifier capeTex = cape == null || !cape.usable() ? null : CapeTextures.texture(cape.id(), cape.assetSha());
        SkinTextures textures = previewTextures(selection, capeTex);
        LivingEntity entity = previewEntity(mc, textures);
        EntityRenderState state = mc.getEntityRenderDispatcher().getAndUpdateRenderState(entity, delta);
        state.light = 0xF000F0;
        state.shadowPieces.clear();
        state.outlineColor = 0;

        float pitch = 0f;
        if (state instanceof LivingEntityRenderState living) {
            float lookYaw = (float) Math.atan((box.x() + box.width() / 2f - mx) / 40f) * 20f;
            float lookPitch = (float) Math.atan((box.y() + box.height() / 2f - my) / 40f) * 20f;
            living.bodyYaw = baseYaw + lookYaw;
            living.relativeHeadYaw = lookYaw;
            living.pitch = -lookPitch;
            pitch = living.pitch;
            float scale = Math.max(0.0001f, living.baseScale);
            living.width /= scale;
            living.height /= scale;
            living.baseScale = 1f;
        }

        Quaternionf body = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf rotation = new Quaternionf().rotateX(pitch * 0.017453292f);
        body.mul(rotation);
        Vector3f center = new Vector3f(0f, state.height / 2f, 0f);
        int size = Math.max(24, Math.min(box.width() * 3 / 10, box.height() / 3));
        ctx.addEntity(state, size, center, body, rotation, box.x() + 4, box.y() + 4, box.right() - 4, box.bottom());
    }

    private SkinTextures previewTextures(SkinSelection selection, Identifier capeTex) {
        UUID id = PlayerIdentity.uuid();
        SkinTextures base = DefaultSkinHelper.getSkinTextures(id == null ? UUID.nameUUIDFromBytes(new byte[]{1}) : id);
        Identifier body = base.body().texturePath();
        PlayerSkinType model = base.model();
        if (selection.custom()) {
            Identifier custom = SkinTexturesCache.texture(selection.sha());
            if (custom != null) {
                body = custom;
                model = PlayerTextures.toType(selection.model());
            } else {
                SkinSetStore.SkinSet set = SkinSetStore.byId(selectedSkinId);
                if (set != null) {
                    SkinTexturesCache.ensureLocal(set.sha256(), SkinSetStore.fileOf(set));
                }
            }
        }
        AssetInfo.TextureAsset cape = base.cape();
        if (capeTex != null) {
            cape = new AssetInfo.TextureAssetInfo(capeTex, capeTex);
        }
        return new SkinTextures(new AssetInfo.TextureAssetInfo(body, body), cape, base.elytra(), model, base.secure());
    }

    private LivingEntity previewEntity(MinecraftClient mc, SkinTextures textures) {
        UUID id = PlayerIdentity.uuid();
        if (id == null) {
            id = UUID.nameUUIDFromBytes(new byte[]{1});
        }
        String name = PlayerIdentity.username();
        if (name == null || name.isEmpty()) {
            name = "RKB";
        }
        if (previewPlayer == null || previewWorld != mc.world || !id.equals(previewPlayer.getUuid())) {
            previewWorld = mc.world;
            previewPlayer = new PreviewPlayer(mc.world, id, name, textures);
        } else {
            previewPlayer.textures(textures);
        }
        return previewPlayer;
    }

    private static final class PreviewPlayer extends AbstractClientPlayerEntity {
        private SkinTextures textures;

        private PreviewPlayer(ClientWorld world, UUID id, String name, SkinTextures textures) {
            super(world, new GameProfile(id, name));
            this.textures = textures;
            setBodyYaw(180f);
            setHeadYaw(180f);
        }

        @Override
        public SkinTextures getSkin() {
            return textures;
        }

        private void textures(SkinTextures textures) {
            this.textures = textures;
        }
    }

    // ------------------------------------------------------------------ shared drawing helpers

    private void drawFields(DrawContext ctx, int mx, int my, float delta) {
        if (pathField != null && pathField.visible) {
            pathField.render(ctx, mx, my, delta);
        }
        if (urlField != null && urlField.visible) {
            urlField.render(ctx, mx, my, delta);
        }
        if (renameField != null && renameField.visible) {
            renameField.render(ctx, mx, my, delta);
        }
        if (redeemField != null && redeemField.visible) {
            redeemField.render(ctx, mx, my, delta);
        }
    }

    private void positionFields() {
        CosmeticsLayout.Rect r = layout.list();
        int fieldW = Math.max(80, r.width() - 20);
        positionField(pathField, tab == TAB_SKIN && input == Input.PATH, r.x() + 72, pathFieldY,
                Math.max(60, fieldW - 168), r);
        positionField(urlField, tab == TAB_SKIN && input == Input.URL, r.x() + 66, urlFieldY,
                Math.max(60, fieldW - 162), r);
        positionField(renameField, tab == TAB_SKIN && !renamingId.isEmpty(), r.x() + 10, renameFieldY,
                Math.max(50, fieldW - 96), r);
        positionField(redeemField, tab == TAB_REDEEM, r.x() + 10, redeemFieldY,
                Math.max(60, fieldW - 96), r);
    }

    private static void positionField(TextFieldWidget field, boolean show, int x, int y, int w, CosmeticsLayout.Rect list) {
        if (field == null) {
            return;
        }
        boolean inView = y >= list.y() - 20 && y <= list.bottom();
        field.setVisible(show && inView);
        field.setX(x);
        field.setY(y);
        field.setWidth(w);
    }

    private void drawScrollbar(DrawContext ctx, int accent) {
        CosmeticsLayout.Rect r = layout.list();
        int max = listTotal - r.height();
        if (max <= 4) {
            return;
        }
        int barH = Math.max(20, r.height() * r.height() / Math.max(1, listTotal));
        int barY = r.y() + (int) ((r.height() - barH) * (listScroll / max));
        ctx.fill(r.right() - 3, barY, r.right() - 1, barY + barH, accent);
    }

    private void drawPanel(DrawContext ctx, CosmeticsLayout.Rect r, int bg, int border, int accent) {
        if (r.width() <= 0 || r.height() <= 0) {
            return;
        }
        ctx.fill(r.x() + 2, r.y(), r.right() - 2, r.bottom(), bg);
        ctx.fill(r.x(), r.y() + 2, r.right(), r.bottom() - 2, bg);
        ctx.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.bottom() - 1, bg);
        ctx.fill(r.x() + 4, r.y(), r.right() - 4, r.y() + 1, border);
        ctx.fill(r.x() + 4, r.bottom() - 1, r.right() - 4, r.bottom(), border);
        ctx.fill(r.x(), r.y() + 4, r.x() + 1, r.bottom() - 4, border);
        ctx.fill(r.right() - 1, r.y() + 4, r.right(), r.bottom() - 4, border);
    }

    private static void softOutline(DrawContext ctx, CosmeticsLayout.Rect r, int color) {
        ctx.fill(r.x(), r.y() + 3, r.x() + 1, r.bottom() - 3, color);
        ctx.fill(r.right() - 1, r.y() + 3, r.right(), r.bottom() - 3, color);
        ctx.fill(r.x() + 3, r.y(), r.right() - 3, r.y() + 1, color);
        ctx.fill(r.x() + 3, r.bottom() - 1, r.right() - 3, r.bottom(), color);
    }

    private int actionButton(DrawContext ctx, TextRenderer tr, String label, int right, int y, boolean enabled,
                             boolean primary, Runnable action) {
        int w = actionButtonWidth(tr, label);
        int x = right - w;
        int h = 20;
        int bg = !enabled ? Theme.CARD_INNER : primary ? Theme.accent() : Theme.BORDER;
        drawPanel(ctx, new CosmeticsLayout.Rect(x, y, w, h), bg, enabled ? Theme.BORDER : Theme.CARD_INNER,
                Theme.accent());
        ctx.drawText(tr, label, x + (w - tr.getWidth(label)) / 2, y + 6,
                !enabled ? Theme.OFF : primary ? Theme.BLACK : Theme.TEXT, false);
        if (enabled && action != null) {
            hits.add(new Hit(x, y, x + w, y + h, false, action));
        }
        return x;
    }

    private static int actionButtonWidth(TextRenderer tr, String label) {
        return Math.max(52, tr.getWidth(label) + 16);
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

    private static String skinModelLabel(String model) {
        return SkinRules.MODEL_SLIM.equals(model) ? "Slim (Alex)" : "Classic (Steve)";
    }

    private SkinSelection selectedSkin() {
        SkinSetStore.SkinSet set = SkinSetStore.byId(selectedSkinId);
        return set == null ? SkinSelection.defaultSkin() : SkinSelection.ofCustom(set.name(), set.sha256(), set.model());
    }

    private CosmeticsManager.Item selectedCapeItem() {
        CosmeticsManager.Item selected = CosmeticsManager.item(selectedCapeId);
        return selected == null ? CosmeticsManager.equippedItem("cape") : selected;
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
        int w = Math.min(layout.list().width() - 12, tr.getWidth(toast) + 18);
        if (w <= 20) {
            return;
        }
        int x = layout.list().x() + (layout.list().width() - w) / 2;
        int y = layout.list().bottom() - 24;
        drawPanel(ctx, new CosmeticsLayout.Rect(x, y, w, 18), Theme.BLACK, accent, accent);
        ctx.drawText(tr, fit(tr, toast, w - 10), x + 5, y + 5, Theme.TEXT, false);
    }
}
