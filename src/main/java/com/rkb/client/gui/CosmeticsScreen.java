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
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
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
    private RkbLayout.SidebarShell sidebarShell;
    private PlayerEntityModel classicModel;
    private PlayerEntityModel slimModel;

    public CosmeticsScreen() {
        super(Text.literal("RKB Cosmetics"));
    }

    // ------------------------------------------------------------------ lifecycle / input

    @Override
    protected void init() {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;

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
            RkbScreens.home();
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
        int accent = Theme.chromeAccent();

        RkbUi.background(ctx, width, height, System.currentTimeMillis());
        sidebarShell = RkbSidebar.draw(ctx, tr, width, height, mx, my, RkbSidebar.NavAction.COSMETICS,
                (r, action) -> hits.add(new Hit(r.x(), r.y(), r.right(), r.bottom(), false, action)));
        layout = CosmeticsLayout.of(sidebarShell.content());
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
        float titleScale = 1.55f;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(h.x(), h.y() + 1);
        ctx.getMatrices().scale(titleScale, titleScale);
        ctx.drawText(tr, RkbUi.menuText("Cosmetics"), 0, 0, RkbUi.TEXT, false);
        ctx.getMatrices().popMatrix();
        String player = PlayerIdentity.username();
        if (player.isEmpty()) {
            player = "Player";
        }
        ctx.drawText(tr, RkbUi.menuText("Customize your look!  ·  " + fit(tr, player, Math.max(60, h.width() / 3))),
                h.x(), h.y() + 20, RkbUi.MUTED, false);

        String status = CloudService.statusText();
        int statusW = Math.min(220, Math.max(110, tr.getWidth(status) + 30));
        int statusX = h.right() - statusW;
        if (statusX > h.x() + 180) {
            int sy = h.y() + 4;
            RkbUi.panel(ctx, new RkbUi.Rect(statusX, sy, statusW, 22), RkbUi.SURFACE, RkbUi.LINE);
            int dot = CloudService.status() == CloudService.Status.CONNECTED ? RkbUi.SUCCESS
                    : CloudService.status() == CloudService.Status.ERROR ? RkbUi.DANGER
                    : PlayerIdentity.signedIn() ? RkbUi.WARNING : RkbUi.MUTED;
            RkbUi.statusDot(ctx, statusX + 8, sy + 9, dot);
            ctx.drawText(tr, fit(tr, status, statusW - 24), statusX + 18, sy + 7,
                    dot == RkbUi.SUCCESS ? RkbUi.TEXT : RkbUi.MUTED, false);
        }
    }

    private void drawTabs(DrawContext ctx, TextRenderer tr, int accent, int mx, int my) {
        for (int i = 0; i < layout.tabRects().size(); i++) {
            CosmeticsLayout.Rect r = layout.tabRects().get(i);
            boolean active = i == tab;
            boolean hover = r.contains(mx, my);
            RkbUi.chip(ctx, new RkbUi.Rect(r.x(), r.y(), r.width(), r.height()),
                    CosmeticsLayout.TABS[i], tr, active, hover);
            final int picked = i;
            hits.add(new Hit(r.x(), r.y(), r.right(), r.bottom(), false, () -> setTab(picked)));
        }
    }

    private void drawPreview(DrawContext ctx, TextRenderer tr, int accent, int mx, int my, float delta) {
        CosmeticsLayout.Rect box = layout.preview();
        if (box.width() <= 0 || box.height() <= 0) {
            return;
        }
        ctx.fillGradient(box.x(), box.y(), box.right(), box.bottom(), 0xFF111B30, 0xFF0A0F1D);
        softOutline(ctx, box, Theme.BORDER);
        boolean tiny = box.height() < 150;
        ctx.fill(box.x() + 10, box.y() + (tiny ? 2 : 10), box.x() + 46, box.y() + (tiny ? 3 : 12), accent);

        SkinSelection selection = selectedSkin();
        CosmeticsManager.Item cape = tab == TAB_CAPE ? selectedCapeItem() : CosmeticsManager.equippedItem("cape");
        boolean customSkin = tab == TAB_SKIN && selection.custom();
        String modelToggle = customSkin
                ? (skinModelLabel(selection.model()).startsWith("Slim") ? "Classic" : "Slim")
                : "";
        int buttonY = box.bottom() - (tiny ? 24 : 30);
        boolean wrapModel = false;
        if (customSkin && !tiny) {
            int applyW = actionButtonWidth(tr, "Apply Skin");
            int resetW = actionButtonWidth(tr, "Reset to Default");
            int modelW = actionButtonWidth(tr, modelToggle);
            wrapModel = box.right() - 8 - applyW - 6 - resetW - 6 - modelW < box.x() + 8;
        }
        int topPad = tiny ? 6 : 18;
        int infoBottom = tiny ? buttonY - 4 : box.bottom() - (wrapModel ? 78 : customSkin ? 56 : 52);
        int drawBottom = tiny ? Math.max(box.y() + topPad + 12, infoBottom)
                : Math.max(box.y() + 72, infoBottom - 36);
        int modelH = Math.max(8, drawBottom - box.y() - topPad);
        drawPlayerModel(ctx, new CosmeticsLayout.Rect(box.x() + 6, box.y() + topPad, box.width() - 12,
                modelH), selection, cape, tab == TAB_CAPE ? 0f : 180f, mx, my, delta);

        if (!tiny) {
            String skinLabel = selection.custom() ? selection.name() : "Minecraft default skin";
            String modelLabel = selection.custom() ? skinModelLabel(selection.model())
                    : "Vanilla Steve/Alex texture";
            String capeLabel = cape == null ? "No cape"
                    : cape.name() + (CosmeticsManager.isEquipped(cape.id()) ? "  ·  equipped" : "");
            int labelY = Math.max(drawBottom + 2, infoBottom - 28);
            ctx.drawText(tr, fit(tr, skinLabel, box.width() - 20), box.x() + 10, labelY, Theme.TEXT, false);
            ctx.drawText(tr, fit(tr, modelLabel, box.width() - 20), box.x() + 10, labelY + 12, Theme.MUTED,
                    false);
            ctx.drawText(tr, fit(tr, capeLabel, box.width() - 20), box.x() + 10, labelY + 24,
                    cape != null && CosmeticsManager.isEquipped(cape.id()) ? accent : Theme.MUTED, false);
        } else if (customSkin) {
            if (box.height() >= 90) {
                ctx.drawText(tr, modelToggle + " model", box.x() + 6, box.y() + 4, RkbUi.MUTED, false);
            }
            // On very short screens the model itself becomes the Classic/Slim switch.
            hits.add(new Hit(box.x() + 4, box.y() + 4, box.right() - 4, buttonY - 2, false, () -> {
                String next = skinModelLabel(selection.model()).startsWith("Slim")
                        ? SkinRules.MODEL_CLASSIC : SkinRules.MODEL_SLIM;
                SkinSetStore.setModel(selectedSkinId, next);
                showToast("Skin model: " + SkinRules.label(next));
            }));
        }

        if (tab == TAB_SKIN) {
            String applyLabel = tiny ? "Apply" : "Apply Skin";
            String resetLabel = tiny ? "Reset" : "Reset to Default";
            int right = box.right() - 8;
            int applyX = actionButton(ctx, tr, applyLabel, right, buttonY, selection.custom(), true,
                    selection.custom() ? () -> {
                        SkinSetStore.activate(selectedSkinId);
                        SkinSetStore.saveAndApply();
                        showToast("Skin applied locally.");
                    } : null);
            int resetX = actionButton(ctx, tr, resetLabel, applyX - 6, buttonY,
                    SkinSetStore.activeSet() != null, false, () -> {
                        SkinSetStore.deactivate();
                        selectedSkinId = "";
                        showToast("Minecraft default skin restored.");
                    });
            if (customSkin && !tiny) {
                int modelY = wrapModel ? buttonY - 24 : buttonY;
                int modelRight = wrapModel ? box.right() - 8 : resetX - 6;
                actionButton(ctx, tr, modelToggle, modelRight, modelY, true, false, () -> {
                    String next = skinModelLabel(selection.model()).startsWith("Slim")
                            ? SkinRules.MODEL_CLASSIC : SkinRules.MODEL_SLIM;
                    SkinSetStore.setModel(selectedSkinId, next);
                    showToast("Skin model: " + SkinRules.label(next));
                });
            }
        } else if (tab == TAB_CAPE) {
            boolean equipped = cape != null && CosmeticsManager.isEquipped(cape.id());
            String removeLabel = tiny ? "Unequip" : "Remove Cape";
            actionButton(ctx, tr, removeLabel, box.right() - 8, buttonY,
                    CosmeticsManager.equippedItem("cape") != null, false,
                    () -> CosmeticsManager.unequip("cape"));
            actionButton(ctx, tr, equipped ? "Equipped" : tiny ? "Equip" : "Apply Cape",
                    box.right() - 8 - actionButtonWidth(tr, removeLabel) - (tiny ? 6 : 22), buttonY,
                    cape != null && cape.usable() && !equipped, true,
                    () -> {
                        CosmeticsManager.equip(cape.id());
                        showToast("Applying " + cape.name() + "...");
                    });
        } else {
            ctx.drawText(tr, fit(tr, "Live V2 inventory · " + CloudService.statusText(), box.width() - 20),
                    box.x() + 10, tiny ? box.y() + 4 : buttonY + 5, Theme.MUTED, false);
        }
    }

    private void drawSkinList(DrawContext ctx, TextRenderer tr, int accent) {
        CosmeticsLayout.Rect r = layout.list();
        int y = r.y() + 6 - Math.round(listScroll);
        ctx.drawText(tr, RkbUi.menuText("SKIN COLLECTION"), r.x() + 4, y, accent, false);
        y += 14;
        y = wrapped(ctx, tr, "Local skins only. The default tile uses the real Steve/Alex texture for this UUID.",
                r.x() + 4, y, r.width() - 8, Theme.MUTED);
        y += 8;

        List<SkinSetStore.SkinSet> sets = SkinSetStore.state().sets();
        int total = 1 + sets.size();
        int cols = r.width() >= 520 ? 5 : r.width() >= 400 ? 4 : r.width() >= 300 ? 3 : 2;
        int gap = 6;
        int tileW = Math.max(48, (r.width() - 8 - gap * (cols - 1)) / cols);
        int tileH = 66;
        int rows = (total + cols - 1) / cols;
        for (int i = 0; i < total; i++) {
            int col = i % cols;
            int row = i / cols;
            CosmeticsLayout.Rect tile = new CosmeticsLayout.Rect(r.x() + 2 + col * (tileW + gap),
                    y + row * (tileH + gap), tileW, tileH);
            if (i == 0) {
                drawSkinTile(ctx, tr, accent, tile, null);
            } else {
                SkinSetStore.SkinSet set = sets.get(i - 1);
                drawSkinTile(ctx, tr, accent, tile, set);
            }
        }
        y += rows * tileH + Math.max(0, rows - 1) * gap + 4;

        if (sets.isEmpty()) {
            ctx.drawText(tr, fit(tr, "No saved skins yet. Upload a PNG or import an image URL below.",
                    r.width() - 16), r.x() + 4, y, Theme.MUTED, false);
            y += 16;
        }

        SkinSetStore.SkinSet selectedSet = SkinSetStore.byId(selectedSkinId);
        if (selectedSet != null) {
            int right = r.right() - 6;
            if (renamingId.equals(selectedSet.id())) {
                renameFieldY = y + 4;
                actionButton(ctx, tr, "Save", right, y, true, true, () -> {
                    SkinSetStore.rename(selectedSet.id(), renameField.getText());
                    renamingId = "";
                    renameField.setFocused(false);
                });
                actionButton(ctx, tr, "Cancel", right - actionButtonWidth(tr, "Save") - 5, y, true, false, () -> {
                    renamingId = "";
                    renameField.setFocused(false);
                });
                y += 28;
            } else {
                actionButton(ctx, tr, "Delete", right, y, true, false, () -> SkinSetStore.delete(selectedSet.id()));
                actionButton(ctx, tr, "Rename", right - actionButtonWidth(tr, "Delete") - 5, y, true, false, () -> {
                    renamingId = selectedSet.id();
                    renameField.setText(selectedSet.name());
                    renameField.setFocused(true);
                });
                y += 26;
            }
        }
        y += 4;
        y = drawAddSkinCard(ctx, tr, accent, r, y);
        listTotal = y - r.y() + 10;
    }

    private void drawSkinTile(DrawContext ctx, TextRenderer tr, int accent, CosmeticsLayout.Rect tile,
                              SkinSetStore.SkinSet set) {
        boolean selected = set == null
                ? (selectedSkinId.isEmpty() || SkinSetStore.byId(selectedSkinId) == null)
                : set.id().equals(selectedSkinId);
        boolean applied = set == null
                ? SkinSetStore.activeSet() == null
                : set.id().equals(SkinSetStore.state().activeId());
        int fill = selected ? Theme.CARD_HOVER : Theme.CARD;
        drawPanel(ctx, tile, fill, selected ? accent : Theme.BORDER, accent);
        if (selected) {
            ctx.fill(tile.x(), tile.y() + 4, tile.x() + 2, tile.bottom() - 4, accent);
        }
        // simple model silhouette so the grid reads like the reference tiles
        int u = Math.max(1, tile.height() / 40);
        int mx = tile.x() + 10;
        int my = tile.y() + 8;
        int bodyW = 10 * u;
        int bodyH = 13 * u;
        ctx.fill(mx + 2 * u, my, mx + 8 * u, my + 5 * u, 0xFFC38D70);
        ctx.fill(mx + 2 * u, my + 5 * u, mx + 8 * u, my + 6 * u, 0xFF171A22);
        ctx.fill(mx + u, my + 6 * u, mx + 9 * u, my + 6 * u + bodyH, selected ? 0xFF2A3140 : 0xFF20242F);
        ctx.fill(mx + 2 * u, my + 6 * u, mx + 8 * u, my + 9 * u, set == null ? 0xFF2F7AD8 : 0xFF8A2638);
        ctx.fill(mx + 2 * u, my + 6 * u + bodyH, mx + 4 * u, my + 6 * u + bodyH + 7 * u, 0xFF171A22);
        ctx.fill(mx + 6 * u, my + 6 * u + bodyH, mx + 8 * u, my + 6 * u + bodyH + 7 * u, 0xFF171A22);

        String name = set == null ? "Minecraft default" : set.name();
        String sub = set == null ? "Vanilla Steve/Alex" : SkinRules.label(set.model()) + " · local PNG";
        int textX = mx + 12 * u;
        int textW = Math.max(20, tile.right() - textX - 6);
        ctx.drawText(tr, fit(tr, name, textW), textX, tile.y() + 12, Theme.TEXT, false);
        ctx.drawText(tr, fit(tr, sub, textW), textX, tile.y() + 24, Theme.MUTED, false);
        if (applied) {
            ctx.drawText(tr, "APPLIED", textX, tile.y() + 40, accent, false);
        } else {
            ctx.drawText(tr, selected ? "SELECTED" : "SELECT", textX, tile.y() + 40, selected ? accent : Theme.DIM, false);
        }
        hits.add(new Hit(tile.x(), tile.y(), tile.right(), tile.bottom(), true, () -> {
            selectedSkinId = set == null ? "" : set.id();
            renamingId = "";
            listScroll = 0;
        }));
    }

    private int drawAddSkinCard(DrawContext ctx, TextRenderer tr, int accent, CosmeticsLayout.Rect r, int y) {
        ctx.drawText(tr, RkbUi.menuText("ADD A SKIN"), r.x() + 4, y, accent, false);
        String scan = "Scan inbox";
        actionButton(ctx, tr, scan, r.right() - 6, y - 4, true, false, () -> {
            SkinSetStore.scanInbox();
            showToast("Checking the local inbox folder...");
        });
        y += 14;
        ctx.drawText(tr, fit(tr, "PNG, 64x64 or 64x32 · at most 256 KB · stored locally", r.width() - 8),
                r.x() + 4, y, Theme.MUTED, false);
        y += 14;

        int gap = 8;
        int half = Math.max(70, (r.width() - 8 - gap) / 2);
        int cardH = 72;
        CosmeticsLayout.Rect upload = new CosmeticsLayout.Rect(r.x() + 2, y, half, cardH);
        CosmeticsLayout.Rect url = new CosmeticsLayout.Rect(upload.right() + gap, y, half, cardH);
        drawSkinSourceCard(ctx, tr, accent, upload, "Upload PNG", "Select a skin file",
                input == Input.PATH, () -> {
                    input = input == Input.PATH ? Input.NONE : Input.PATH;
                    listScroll = 0;
                }, false);
        drawSkinSourceCard(ctx, tr, accent, url, "Import URL", "Paste a skin URL",
                input == Input.URL, () -> {
                    input = input == Input.URL ? Input.NONE : Input.URL;
                    listScroll = 0;
                }, true);
        y += cardH + 6;

        if (input == Input.PATH) {
            ctx.drawText(tr, RkbUi.menuText("PNG path"), r.x() + 4, y + 6, accent, false);
            pathFieldY = y;
            actionButton(ctx, tr, "Import", r.right() - 6, y + 2, true, true, () -> {
                String value = pathField.getText();
                pathField.setText("");
                SkinSetStore.importPath(value);
                showToast("Importing local skin...");
            });
            y += 24;
        } else if (input == Input.URL) {
            ctx.drawText(tr, RkbUi.menuText("PNG URL"), r.x() + 4, y + 6, accent, false);
            urlFieldY = y;
            actionButton(ctx, tr, "Import", r.right() - 6, y + 2, true, true, () -> {
                String value = urlField.getText();
                urlField.setText("");
                SkinSetStore.importUrl(value);
                showToast("Fetching skin over HTTPS...");
            });
            y += 24;
        }
        if (input != Input.NONE) {
            ctx.drawText(tr, fit(tr, "Local only · nothing is uploaded to Supabase", r.width() - 8),
                    r.x() + 4, y + 2, Theme.MUTED, false);
            y += 14;
        }
        return y + 6;
    }

    private void drawSkinSourceCard(DrawContext ctx, TextRenderer tr, int accent, CosmeticsLayout.Rect card,
                                    String title, String subtitle, boolean active, Runnable action, boolean link) {
        drawPanel(ctx, card, active ? Theme.CARD_HOVER : Theme.CARD_INNER,
                active ? accent : Theme.BORDER, accent);
        int cx = card.x() + card.width() / 2;
        int iconY = card.y() + 18;
        int c = active ? accent : Theme.MUTED;
        if (link) {
            int u = 2;
            ctx.fill(cx - 6 * u, iconY + 2 * u, cx + 2 * u, iconY + 3 * u, c);
            ctx.fill(cx + 2 * u, iconY, cx + 3 * u, iconY + 8 * u, c);
            ctx.fill(cx - 3 * u, iconY + 5 * u, cx + 3 * u, iconY + 6 * u, c);
        } else {
            int u = 2;
            ctx.fill(cx - 2 * u, iconY, cx + 2 * u, iconY + 5 * u, c);
            ctx.fill(cx - 1 * u, iconY + 5 * u, cx + 1 * u, iconY + 8 * u, c);
            ctx.fill(cx - 4 * u, iconY + 3 * u, cx - 2 * u, iconY + 4 * u, c);
            ctx.fill(cx + 2 * u, iconY + 3 * u, cx + 4 * u, iconY + 4 * u, c);
        }
        String titleFit = RkbUi.fit(tr, title, card.width() - 12);
        String subFit = RkbUi.fit(tr, subtitle, card.width() - 12);
        ctx.drawText(tr, RkbUi.menuText(titleFit), card.x() + (card.width() - tr.getWidth(RkbUi.menuText(titleFit))) / 2, card.y() + 34, active ? accent : Theme.TEXT, false);
        ctx.drawText(tr, RkbUi.menuText(subFit), card.x() + (card.width() - tr.getWidth(RkbUi.menuText(subFit))) / 2, card.y() + 48, Theme.MUTED, false);
        hits.add(new Hit(card.x(), card.y(), card.right(), card.bottom(), true, action));
    }

    private void drawCapeList(DrawContext ctx, TextRenderer tr, int accent) {
        CosmeticsLayout.Rect r = layout.list();
        int y = r.y() + 6 - Math.round(listScroll);
        ctx.drawText(tr, RkbUi.menuText("CAPE COLLECTION"), r.x() + 4, y, accent, false);
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
            ctx.drawText(tr, fit(tr, "The catalog has not loaded yet. Local bundled designs remain unavailable "
                    + "until V2 confirms ownership.", r.width() - 16), r.x() + 8, y + 8, Theme.MUTED, false);
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
            boolean narrow = row.width() < 300;
            int tx = row.x() + 8;
            int ty = row.y() + 10;
            if (!narrow) {
                if (tex != null) {
                    drawScaledTexture(ctx, tex, tx, ty, 1f, 1f, 6, 4, 64, 32, 1.5f);
                } else {
                    ctx.fill(tx, ty, tx + 28, ty + 18, Theme.CARD_INNER);
                }
            }
            int textX = narrow ? row.x() + 8 : tx + 36;
            int textW = narrow ? Math.max(40, row.width() - 16 - (item.usable() ? 70 : 0))
                    : row.width() - 150;
            String name = item.name().isEmpty() ? item.id() : item.name();
            ctx.drawText(tr, fit(tr, name, textW), textX, row.y() + 8, Theme.TEXT, false);
            String status = equipped ? "EQUIPPED" : item.usable() ? "OWNED · READY"
                    : !item.active() ? "NOT IN CATALOG" : !item.assetReady() ? "ASSET UNAVAILABLE" : "NOT OWNED";
            int statusColor = equipped ? accent : item.usable() ? 0xFF39D98A : Theme.MUTED;
            ctx.drawText(tr, fit(tr, status, textW), textX, row.y() + 21, statusColor, false);
            String detail = item.rarity().isEmpty() ? item.type() : item.rarity() + "  ·  " + item.type();
            ctx.drawText(tr, fit(tr, detail, textW), textX, row.y() + 34, Theme.MUTED, false);

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
        ctx.drawText(tr, RkbUi.menuText("REDEEM"), r.x() + 4, y, accent, false);
        y += 14;

        boolean narrow = r.width() < 320;
        CosmeticsLayout.Rect card = new CosmeticsLayout.Rect(r.x() + 2, y, r.width() - 6, 104);
        drawPanel(ctx, card, Theme.CARD, Theme.BORDER, accent);
        ctx.drawText(tr, RkbUi.menuText("CODE"), card.x() + 10, card.y() + 9, accent, false);
        redeemFieldY = card.y() + 23;
        boolean busy = CosmeticsManager.busy();
        actionButton(ctx, tr, busy ? "Working..." : "Redeem", card.right() - 10, card.y() + 24,
                !busy && redeemField.getText() != null && !redeemField.getText().isBlank(), true, () -> {
                    String code = redeemField.getText();
                    redeemField.setText("");
                    CosmeticsManager.redeem(code);
                });
        ctx.drawText(tr, fit(tr, busy ? "Contacting the authenticated V2 function..."
                        : "Codes are accepted only while signed in and linked.", card.width() - 20),
                card.x() + 10, card.y() + 52, busy ? accent : Theme.MUTED, false);
        String message = CosmeticsManager.message();
        int resultColor = message.isEmpty() ? Theme.MUTED : CosmeticsManager.lastResultOk() ? 0xFF39D98A : 0xFFFF8B8B;
        ctx.drawText(tr, fit(tr, message.isEmpty() ? "No code submitted in this session." : message, card.width() - 20),
                card.x() + 10, card.y() + 70, resultColor, false);
        ctx.drawText(tr, fit(tr, "V2 has no redemption-history action; no history is shown.", card.width() - 20),
                card.x() + 10, card.y() + 86, Theme.MUTED, false);
        y = card.bottom() + 10;

        CosmeticsLayout.Rect cloud = new CosmeticsLayout.Rect(r.x() + 2, y, r.width() - 6, 104);
        drawPanel(ctx, cloud, Theme.CARD_INNER, Theme.BORDER, accent);
        ctx.drawText(tr, RkbUi.menuText("CLOUD INVENTORY"), cloud.x() + 10, cloud.y() + 8, accent, false);
        ctx.drawText(tr, fit(tr, CloudService.statusText(), cloud.width() - 20), cloud.x() + 10, cloud.y() + 22,
                Theme.TEXT, false);
        String account = CloudStore.signedIn()
                ? (CloudStore.email().isEmpty() ? "signed in" : CloudStore.email()) : "not signed in";
        String linked = CloudService.isLinked() ? CloudStore.linkedName() + " · " + PlayerIdentity.shortUuidFor(CloudStore.linkedUuid())
                : "not linked";
        ctx.drawText(tr, fit(tr, account, cloud.width() / 2 - 16), cloud.x() + 10, cloud.y() + 38, Theme.MUTED, false);
        ctx.drawText(tr, fit(tr, linked, cloud.width() / 2 - 16), cloud.x() + cloud.width() / 2 + 6, cloud.y() + 38,
                Theme.MUTED, false);
        ctx.drawText(tr, fit(tr, "Last sync: " + (CloudStore.lastSyncMs() <= 0 ? "never" : ago(CloudStore.lastSyncMs())),
                cloud.width() - 20), cloud.x() + 10, cloud.y() + 52, Theme.MUTED, false);
        String syncLabel = narrow ? "Sync" : "Sync now";
        String linkLabel = narrow ? "Link" : "Link Minecraft";
        int by = cloud.y() + 72;
        actionButton(ctx, tr, syncLabel, cloud.right() - 10, by, CosmeticsManager.ready(), true,
                CloudService::manualSync);
        actionButton(ctx, tr, linkLabel, cloud.right() - 10 - actionButtonWidth(tr, syncLabel) - 5, by,
                CosmeticsManager.ready(), false, CloudService::link);
        actionButton(ctx, tr, "Refresh", cloud.right() - 10 - actionButtonWidth(tr, syncLabel)
                - actionButtonWidth(tr, linkLabel) - 10, by, CosmeticsManager.ready(), false,
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
            ctx.drawText(tr, fit(tr, "No V2 inventory is loaded. Local skins and bundled cape textures still work.",
                    r.width() - 16), r.x() + 8, y + 4, Theme.MUTED, false);
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
        Identifier capeTex = cape == null || !cape.usable() ? null : CapeTextures.texture(cape.id(), cape.assetSha());
        SkinTextures textures = previewTextures(selection, capeTex);
        if (mc.world == null) {
            // The world-free title screen cannot build a ClientWorld, so use Minecraft's own GUI player-skin
            // renderer. It renders the same real model and texture, just without world lighting.
            drawGuiPlayerSkin(ctx, box, textures);
            return;
        }

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

    private void drawGuiPlayerSkin(DrawContext ctx, CosmeticsLayout.Rect box, SkinTextures textures) {
        if (box.width() <= 12 || box.height() <= 8) {
            return;
        }
        boolean slim = textures.model() == PlayerSkinType.SLIM;
        PlayerEntityModel model = model(slim);
        PlayerEntityRenderState state = new PlayerEntityRenderState();
        state.bodyYaw = 180f;
        state.relativeHeadYaw = 0f;
        state.pitch = 0f;
        state.hatVisible = true;
        state.jacketVisible = true;
        state.leftSleeveVisible = true;
        state.rightSleeveVisible = true;
        state.leftPantsLegVisible = true;
        state.rightPantsLegVisible = true;
        model.setVisible(true);
        model.setAngles(state);
        int pad = Math.max(2, Math.min(10, box.height() / 6));
        int bottomPad = Math.max(2, Math.min(34, box.height() / 3));
        ctx.addPlayerSkin(model, textures.body().texturePath(), 1f, 0f, 180f, 0f,
                box.x() + 6, box.y() + pad, box.right() - 6, box.bottom() - bottomPad);
    }

    private PlayerEntityModel model(boolean slim) {
        if (slim) {
            if (slimModel == null) {
                slimModel = buildModel(true);
            }
            return slimModel;
        }
        if (classicModel == null) {
            classicModel = buildModel(false);
        }
        return classicModel;
    }

    private static PlayerEntityModel buildModel(boolean slim) {
        ModelData data = PlayerEntityModel.getTexturedModelData(Dilation.NONE, slim);
        return new PlayerEntityModel(TexturedModelData.of(data, 64, 64).createModel(), slim);
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
        boolean narrow = r.width() < 240;
        positionField(pathField, tab == TAB_SKIN && input == Input.PATH, narrow ? r.x() + 10 : r.x() + 72,
                pathFieldY, narrow ? Math.max(50, r.width() - 70) : Math.max(60, fieldW - 168), r);
        positionField(urlField, tab == TAB_SKIN && input == Input.URL, narrow ? r.x() + 10 : r.x() + 66,
                urlFieldY, narrow ? Math.max(50, r.width() - 70) : Math.max(60, fieldW - 162), r);
        positionField(renameField, tab == TAB_SKIN && !renamingId.isEmpty(), r.x() + 10, renameFieldY,
                narrow ? Math.max(50, r.width() - 20) : Math.max(50, fieldW - 96), r);
        positionField(redeemField, tab == TAB_REDEEM, r.x() + 10, redeemFieldY,
                Math.max(50, r.width() - 70), r);
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
        RkbUi.panel(ctx, new RkbUi.Rect(r.x(), r.y(), r.width(), r.height()), bg, border);
    }

    private static void softOutline(DrawContext ctx, CosmeticsLayout.Rect r, int color) {
        RkbUi.roundedOutline(ctx, new RkbUi.Rect(r.x(), r.y(), r.width(), r.height()), color);
    }

    private int actionButton(DrawContext ctx, TextRenderer tr, String label, int right, int y, boolean enabled,
                             boolean primary, Runnable action) {
        int w = actionButtonWidth(tr, label);
        int x = right - w;
        RkbUi.Rect r = new RkbUi.Rect(x, y, w, 20);
        RkbUi.button(ctx, tr, r, label, false, primary, enabled);
        if (enabled && action != null) {
            hits.add(new Hit(r.x(), r.y(), r.right(), r.bottom(), false, action));
        }
        return x;
    }

    private static int actionButtonWidth(TextRenderer tr, String label) {
        return Math.max(46, tr.getWidth(label) + 16);
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
