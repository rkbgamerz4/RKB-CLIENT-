package com.rkb.client.gui;

import com.rkb.client.config.ConfigManager;
import com.rkb.client.input.KeyUtil;
import com.rkb.client.module.Category;
import com.rkb.client.module.Module;
import com.rkb.client.module.ModuleManager;
import com.rkb.client.module.impl.InterfaceModule;
import com.rkb.client.module.setting.BoolSetting;
import com.rkb.client.module.setting.ChoiceSetting;
import com.rkb.client.module.setting.IntSetting;
import com.rkb.client.module.setting.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Dedicated RKB Settings screen. */
public class RkbSettingsScreen extends Screen {
    private record Hit(RkbUi.Rect rect, Runnable action) { boolean contains(int x, int y) { return rect.contains(x, y); } }
    private static final String[] CATS = {"General","Interface","Performance","Controls","HUD","Modules","Other"};

    private final List<Hit> hits = new ArrayList<>();
    private TextFieldWidget search;
    private RkbLayout.SidebarShell shell;
    private int cat = 0;
    private float scroll;
    private int contentH;
    private boolean confirmReset = false;
    private Module listeningModule = null;

    public RkbSettingsScreen() { super(Text.literal("RKB Settings")); }

    @Override protected void init() {
        search = new TextFieldWidget(textRenderer, 0, 0, 180, 20, Text.literal("Search"));
        search.setPlaceholder(Text.literal("Search settings"));
        addDrawableChild(search);
    }
    @Override public boolean shouldPause() { return false; }
    @Override public void close() { ConfigManager.save(); MinecraftClient.getInstance().setScreen(null); }
    @Override public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (shell != null && shell.content().contains((int) mx, (int) my)) {
            int max = Math.max(0, contentH - shell.content().height());
            scroll = Math.max(0, Math.min(max, scroll - (float) v * 18));
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }
    @Override public boolean mouseClicked(Click click, boolean doubled) {
        if (search != null && search.mouseClicked(click, doubled)) { setFocused(search); return true; }
        if (click.button() == 0) {
            for (int i = hits.size() - 1; i >= 0; i--) { Hit h = hits.get(i); if (h.contains((int) click.x(), (int) click.y())) { h.action().run(); return true; } }
        }
        return super.mouseClicked(click, doubled);
    }
    @Override public boolean keyPressed(KeyInput input) {
        if (listeningModule != null) {
            if (input.isEscape()) { listeningModule = null; return true; }
            listeningModule.setKeyCode(input.key());
            listeningModule = null;
            ConfigManager.save();
            return true;
        }
        if (input.isEscape()) { close(); return true; }
        if (search != null && search.keyPressed(input)) return true;
        return super.keyPressed(input);
    }
    @Override public boolean charTyped(CharInput input) { if (search != null && search.charTyped(input)) return true; return super.charTyped(input); }

    @Override public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        hits.clear();
        RkbUi.background(ctx, width, height, System.currentTimeMillis());
        shell = RkbSidebar.draw(ctx, textRenderer, width, height, mouseX, mouseY, RkbSidebar.NavAction.RKB_SETTINGS, (r,a) -> hits.add(new Hit(r,a)));
        TextRenderer tr = textRenderer;
        RkbUi.Rect c = shell.content();
        float titleScale = 1.45f;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(c.x(), c.y());
        ctx.getMatrices().scale(titleScale, titleScale);
        ctx.drawText(tr, RkbUi.menuText("RKB Settings"), 0, 0, RkbUi.TEXT, false);
        ctx.getMatrices().popMatrix();
        ctx.drawText(tr, RkbUi.menuText("Tune the client, not the game."), c.x(), c.y() + 13, RkbUi.MUTED, false);
        int searchW = Math.min(220, Math.max(140, c.width() / 4));
        search.setX(c.right() - searchW);
        search.setY(c.y() + 2);
        search.setWidth(searchW);

        int leftW = Math.min(180, Math.max(120, c.width() * 23 / 100));
        RkbUi.Rect left = new RkbUi.Rect(c.x(), c.y() + 34, leftW, c.bottom() - c.y() - 42);
        RkbUi.Rect right = new RkbUi.Rect(left.right() + 12, c.y() + 34, c.width() - leftW - 12, left.height());
        ctx.fill(left.x(), left.y(), left.right(), left.bottom(), RkbUi.SURFACE);
        drawBorder(ctx, left, RkbUi.LINE);
        for (int i = 0; i < CATS.length; i++) {
            RkbUi.Rect r = new RkbUi.Rect(left.x() + 6, left.y() + 8 + i * 30, left.width() - 12, 26);
            boolean sel = cat == i;
            if (sel) { ctx.fill(r.x(), r.y(), r.right(), r.bottom(), RkbUi.SURFACE_3); ctx.fill(r.x(), r.y()+4, r.x()+2, r.bottom()-4, RkbUi.CHROME_ACCENT); }
            ctx.drawText(tr, CATS[i], r.x() + 10, r.y() + 9, sel ? RkbUi.TEXT : RkbUi.MUTED, false);
            final int idx = i;
            hits.add(new Hit(r, () -> { cat = idx; scroll = 0; confirmReset = false; }));
        }

        ctx.enableScissor(right.x(), right.y(), right.right(), right.bottom());
        int y = right.y() + 8 - (int) scroll;
        y = drawCategory(ctx, tr, right, y, mouseX, mouseY);
        contentH = (y + (int) scroll) - right.y() + 20;
        ctx.disableScissor();
        super.render(ctx, mouseX, mouseY, delta);
    }

    private int drawCategory(DrawContext ctx, TextRenderer tr, RkbUi.Rect area, int y, int mx, int my) {
        String q = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        InterfaceModule ui = ModuleManager.get().ui();
        switch (cat) {
            case 0 -> { // General
                y = section(ctx, tr, area.x()+8, y, "MENU");
                y = settingRow(ctx, tr, area, y, "FPS in menu header", "Show frame rate in the RKB status bar.", ui.menuShowFps(), () -> { toggleSetting(ui, "menu_fps"); }, mx, my, q);
                y = settingRow(ctx, tr, area, y, "Version in menu header", "Show client version next to FPS counter.", ui.menuShowVersion(), () -> { toggleSetting(ui, "menu_version"); }, mx, my, q);
                y = keyRow(ctx, tr, area, y, "Menu key", "Opens the RKB menu from anywhere.", ui.keyCode(), () -> { listeningModule = ui; }, mx, my, q);
                y = section(ctx, tr, area.x()+8, y+8, "HUD");
                y = settingRow(ctx, tr, area, y, "HUD background", "Draw a quiet backing behind HUD text.", ui.hudBackground(), () -> { toggleSetting(ui, "hud_background"); }, mx, my, q);
                y = settingRow(ctx, tr, area, y, "HUD text shadow", "Keep HUD text legible over bright terrain.", ui.hudShadow(), () -> { toggleSetting(ui, "hud_shadow"); }, mx, my, q);
                y = choiceRow(ctx, tr, area, y, ui, "accent", "HUD accent color", "Accent used by HUD elements. UI chrome stays RKB blue.", mx, my, q);
                y = choiceRow(ctx, tr, area, y, ui, "hud_color", "HUD text color", "Primary color for HUD labels.", mx, my, q);
            }
            case 1 -> { // Interface
                y = section(ctx, tr, area.x()+8, y, "INTERFACE");
                for (Setting<?> s : ui.settings()) y = drawAnySetting(ctx, tr, ui, s, area, y, mx, my, q);
            }
            case 2 -> { // Performance
                y = section(ctx, tr, area.x()+8, y, "PERFORMANCE");
                for (Module m : ModuleManager.get().inCategory(Category.PERFORMANCE)) y = moduleRow(ctx, tr, m, area, y, mx, my, q);
            }
            case 3 -> { // Controls
                y = section(ctx, tr, area.x()+8, y, "CONTROLS");
                y = keyRow(ctx, tr, area, y, "Menu key", "Opens the RKB menu from anywhere.", ui.keyCode(), () -> { listeningModule = ui; }, mx, my, q);
                for (Module m : ModuleManager.get().all()) {
                    if (m.keyCode() == -1 && m.category() != Category.CONTROLS) continue;
                    y = moduleKeyRow(ctx, tr, m, area, y, mx, my, q);
                }
            }
            case 4 -> { // HUD
                y = section(ctx, tr, area.x()+8, y, "HUD");
                RkbUi.Rect he = new RkbUi.Rect(area.right() - 128, y - 2, 120, 24);
                drawButton(ctx, tr, he, "Open HUD Editor", he.contains(mx, my), false);
                hits.add(new Hit(he, () -> MinecraftClient.getInstance().setScreen(new HudEditorScreen())));
                y += 30;
                for (Module m : ModuleManager.get().inCategory(Category.HUD)) y = moduleRow(ctx, tr, m, area, y, mx, my, q);
            }
            case 5 -> { // Modules
                y = section(ctx, tr, area.x()+8, y, "MODULES");
                y = actionRow(ctx, tr, area, y, "Open RKB Modules", "Browse, search and configure all modules.", "Open", () -> MinecraftClient.getInstance().setScreen(new RkbModulesScreen()), mx, my, q);
                y = actionRow(ctx, tr, area, y, "Profiles", "Save or load your module setup.", "Open", () -> MinecraftClient.getInstance().setScreen(new RkbMenuScreen(RkbMenuScreen.PAGE_PROFILES)), mx, my, q);
                y = actionRow(ctx, tr, area, y, confirmReset ? "Confirm reset all modules" : "Reset all modules", "Restore all module defaults. This cannot be undone.", confirmReset ? "Confirm" : "Reset", () -> {
                    if (confirmReset) { ConfigManager.resetAll(); confirmReset = false; }
                    else confirmReset = true;
                }, mx, my, q);
            }
            case 6 -> { // Other
                y = section(ctx, tr, area.x()+8, y, "CLIENT TOOLS");
                y = actionRow(ctx, tr, area, y, "Profiles", "Save or load a full setup.", "Open", () -> MinecraftClient.getInstance().setScreen(new RkbMenuScreen(RkbMenuScreen.PAGE_PROFILES)), mx, my, q);
                y = actionRow(ctx, tr, area, y, "HUD Editor", "Move every HUD element.", "Open", () -> MinecraftClient.getInstance().setScreen(new HudEditorScreen()), mx, my, q);
                y = actionRow(ctx, tr, area, y, "Waypoints", "Per-world coordinates.", "Open", () -> MinecraftClient.getInstance().setScreen(new WaypointScreen()), mx, my, q);
                y = actionRow(ctx, tr, area, y, "Friends", "Local friend list.", "Open", () -> MinecraftClient.getInstance().setScreen(new FriendsScreen()), mx, my, q);
                y = actionRow(ctx, tr, area, y, "Account", "Minecraft identity and RKB cloud.", "Open", RkbScreens::account, mx, my, q);
                y += 8;
                y = section(ctx, tr, area.x()+8, y, "LOCAL FILES");
                ctx.drawText(tr, RkbUi.menuText("config/rkbclient.json  ·  config/rkbclient-profiles/"), area.x()+8, y, RkbUi.MUTED, false); y += 14;
                ctx.drawText(tr, RkbUi.menuText("Minecraft access tokens never leave this device."), area.x()+8, y, RkbUi.DIM, false); y += 20;
            }
        }
        return y;
    }

    private int section(DrawContext ctx, TextRenderer tr, int x, int y, String label) {
        ctx.drawText(tr, label, x, y, RkbUi.DIM, false);
        ctx.fill(x, y + 11, x + 220, y + 12, RkbUi.LINE);
        return y + 20;
    }

    private boolean matches(String q, String... vals) {
        if (q.isEmpty()) return true;
        for (String v : vals) if (v != null && v.toLowerCase(Locale.ROOT).contains(q)) return true;
        return false;
    }

    private int settingRow(DrawContext ctx, TextRenderer tr, RkbUi.Rect area, int y, String label, String desc, boolean on, Runnable toggle, int mx, int my, String q) {
        if (!matches(q, label, desc)) return y;
        RkbUi.Rect row = new RkbUi.Rect(area.x()+4, y, area.width()-8, 40);
        ctx.fill(row.x(), row.y(), row.right(), row.bottom(), RkbUi.SURFACE_2); drawBorder(ctx, row, RkbUi.LINE);
        ctx.drawText(tr, label, row.x()+10, row.y()+8, RkbUi.TEXT, false);
        ctx.drawText(tr, RkbUi.fit(tr, desc, row.width()-80), row.x()+10, row.y()+22, RkbUi.MUTED, false);
        RkbUi.Rect tog = new RkbUi.Rect(row.right()-34, row.y()+14, 24, 12);
        drawToggle(ctx, tog, on);
        hits.add(new Hit(row, () -> { toggle.run(); ConfigManager.save(); }));
        return y + 44;
    }

    private int choiceRow(DrawContext ctx, TextRenderer tr, RkbUi.Rect area, int y, Module m, String settingId, String label, String desc, int mx, int my, String q) {
        if (!matches(q, label, desc)) return y;
        for (Setting<?> s : m.settings()) {
            if (s instanceof ChoiceSetting cs && s.id().equals(settingId)) {
                RkbUi.Rect row = new RkbUi.Rect(area.x()+4, y, area.width()-8, 40);
                ctx.fill(row.x(), row.y(), row.right(), row.bottom(), RkbUi.SURFACE_2); drawBorder(ctx, row, RkbUi.LINE);
                ctx.drawText(tr, label, row.x()+10, row.y()+8, RkbUi.TEXT, false);
                ctx.drawText(tr, RkbUi.fit(tr, desc, row.width()-170), row.x()+10, row.y()+22, RkbUi.MUTED, false);
                RkbUi.Rect box = new RkbUi.Rect(row.right()-140, row.y()+10, 130, 20);
                drawButton(ctx, tr, box, cs.get(), box.contains(mx, my), false);
                hits.add(new Hit(box, () -> { cs.cycle(); ConfigManager.save(); }));
                return y + 44;
            }
        }
        return y;
    }

    private int keyRow(DrawContext ctx, TextRenderer tr, RkbUi.Rect area, int y, String label, String desc, int key, Runnable listen, int mx, int my, String q) {
        if (!matches(q, label, desc)) return y;
        RkbUi.Rect row = new RkbUi.Rect(area.x()+4, y, area.width()-8, 40);
        ctx.fill(row.x(), row.y(), row.right(), row.bottom(), RkbUi.SURFACE_2); drawBorder(ctx, row, RkbUi.LINE);
        ctx.drawText(tr, label, row.x()+10, row.y()+8, RkbUi.TEXT, false);
        ctx.drawText(tr, RkbUi.fit(tr, desc, row.width()-170), row.x()+10, row.y()+22, RkbUi.MUTED, false);
        RkbUi.Rect box = new RkbUi.Rect(row.right()-140, row.y()+10, 130, 20);
        String k = listeningModule != null ? "Press key..." : KeyUtil.name(key);
        drawButton(ctx, tr, box, k, box.contains(mx, my), listeningModule != null);
        hits.add(new Hit(box, listen));
        return y + 44;
    }

    private int moduleRow(DrawContext ctx, TextRenderer tr, Module m, RkbUi.Rect area, int y, int mx, int my, String q) {
        if (!matches(q, m.name(), m.description())) return y;
        RkbUi.Rect row = new RkbUi.Rect(area.x()+4, y, area.width()-8, 40);
        ctx.fill(row.x(), row.y(), row.right(), row.bottom(), RkbUi.SURFACE_2); drawBorder(ctx, row, RkbUi.LINE);
        ctx.drawText(tr, m.name(), row.x()+10, row.y()+8, RkbUi.TEXT, false);
        ctx.drawText(tr, RkbUi.fit(tr, m.description(), row.width()-80), row.x()+10, row.y()+22, RkbUi.MUTED, false);
        RkbUi.Rect tog = new RkbUi.Rect(row.right()-34, row.y()+14, 24, 12);
        drawToggle(ctx, tog, m.isEnabled());
        hits.add(new Hit(row, () -> { m.toggle(); ConfigManager.save(); }));
        return y + 44;
    }

    private int moduleKeyRow(DrawContext ctx, TextRenderer tr, Module m, RkbUi.Rect area, int y, int mx, int my, String q) {
        if (!matches(q, m.name(), "keybind")) return y;
        RkbUi.Rect row = new RkbUi.Rect(area.x()+4, y, area.width()-8, 40);
        ctx.fill(row.x(), row.y(), row.right(), row.bottom(), RkbUi.SURFACE_2); drawBorder(ctx, row, RkbUi.LINE);
        ctx.drawText(tr, m.name(), row.x()+10, row.y()+8, RkbUi.TEXT, false);
        ctx.drawText(tr, RkbUi.fit(tr, "Module keybind", row.width()-170), row.x()+10, row.y()+22, RkbUi.MUTED, false);
        RkbUi.Rect box = new RkbUi.Rect(row.right()-140, row.y()+10, 130, 20);
        String k = listeningModule == m ? "Press key..." : KeyUtil.name(m.keyCode());
        drawButton(ctx, tr, box, k, box.contains(mx, my), listeningModule == m);
        hits.add(new Hit(box, () -> listeningModule = m));
        return y + 44;
    }

    private int actionRow(DrawContext ctx, TextRenderer tr, RkbUi.Rect area, int y, String label, String desc, String btn, Runnable action, int mx, int my, String q) {
        if (!matches(q, label, desc)) return y;
        RkbUi.Rect row = new RkbUi.Rect(area.x()+4, y, area.width()-8, 40);
        ctx.fill(row.x(), row.y(), row.right(), row.bottom(), RkbUi.SURFACE_2); drawBorder(ctx, row, RkbUi.LINE);
        ctx.drawText(tr, label, row.x()+10, row.y()+8, RkbUi.TEXT, false);
        ctx.drawText(tr, RkbUi.fit(tr, desc, row.width()-150), row.x()+10, row.y()+22, RkbUi.MUTED, false);
        RkbUi.Rect b = new RkbUi.Rect(row.right()-112, row.y()+10, 102, 20);
        drawButton(ctx, tr, b, btn, b.contains(mx, my), false);
        hits.add(new Hit(b, action));
        return y + 44;
    }

    private int drawAnySetting(DrawContext ctx, TextRenderer tr, Module m, Setting<?> s, RkbUi.Rect area, int y, int mx, int my, String q) {
        if (!matches(q, s.name(), "")) return y;
        RkbUi.Rect row = new RkbUi.Rect(area.x()+4, y, area.width()-8, 40);
        ctx.fill(row.x(), row.y(), row.right(), row.bottom(), RkbUi.SURFACE_2); drawBorder(ctx, row, RkbUi.LINE);
        ctx.drawText(tr, s.name(), row.x()+10, row.y()+8, RkbUi.TEXT, false);
        ctx.drawText(tr, RkbUi.fit(tr, "", row.width()-170), row.x()+10, row.y()+22, RkbUi.MUTED, false);
        if (s instanceof BoolSetting b) {
            RkbUi.Rect tog = new RkbUi.Rect(row.right()-34, row.y()+14, 24, 12); drawToggle(ctx, tog, b.on());
            hits.add(new Hit(row, () -> { b.toggle(); ConfigManager.save(); }));
        } else if (s instanceof ChoiceSetting cs) {
            RkbUi.Rect box = new RkbUi.Rect(row.right()-140, row.y()+10, 130, 20); drawButton(ctx, tr, box, cs.get(), box.contains(mx, my), false);
            hits.add(new Hit(box, () -> { cs.cycle(); ConfigManager.save(); }));
        } else if (s instanceof IntSetting is) {
            RkbUi.Rect minus = new RkbUi.Rect(row.right()-96, row.y()+10, 20, 20); RkbUi.Rect plus = new RkbUi.Rect(row.right()-30, row.y()+10, 20, 20);
            drawButton(ctx, tr, minus, "-", minus.contains(mx, my), false); drawButton(ctx, tr, plus, "+", plus.contains(mx, my), false);
            String val = String.valueOf(is.get()); ctx.drawText(tr, val, row.right()-58-tr.getWidth(val)/2, row.y()+16, RkbUi.TEXT, false);
            hits.add(new Hit(minus, () -> { is.step(-1); ConfigManager.save(); }));
            hits.add(new Hit(plus, () -> { is.step(1); ConfigManager.save(); }));
        }
        return y + 44;
    }

    private void toggleSetting(InterfaceModule ui, String id) {
        for (Setting<?> s : ui.settings()) if (s.id().equals(id) && s instanceof BoolSetting b) b.toggle();
    }

    private void drawButton(DrawContext ctx, TextRenderer tr, RkbUi.Rect r, String label, boolean hover, boolean primary) {
        ctx.fill(r.x(), r.y(), r.right(), r.bottom(), primary ? RkbUi.CHROME_ACCENT : hover ? RkbUi.SURFACE_3 : RkbUi.SURFACE_2);
        drawBorder(ctx, r, primary ? RkbUi.CHROME_ACCENT : RkbUi.LINE);
        int tw = tr.getWidth(label);
        ctx.drawText(tr, RkbUi.fit(tr, label, r.width()-8), r.x() + Math.max(4, (r.width()-Math.min(tw, r.width()-8))/2), r.y() + (r.height()-8)/2, primary ? 0xFFFFFFFF : RkbUi.TEXT, false);
    }
    private void drawToggle(DrawContext ctx, RkbUi.Rect r, boolean on) {
        ctx.fill(r.x(), r.y(), r.right(), r.bottom(), on ? RkbUi.CHROME_ACCENT : RkbUi.SURFACE_3);
        drawBorder(ctx, r, on ? RkbUi.CHROME_ACCENT : RkbUi.LINE);
        int k = on ? r.right() - r.height() + 2 : r.x() + 2;
        ctx.fill(k, r.y() + 2, k + r.height() - 4, r.bottom() - 2, 0xFFFFFFFF);
    }
    private void drawBorder(DrawContext ctx, RkbUi.Rect r, int color) {
        ctx.fill(r.x(), r.y(), r.right(), r.y() + 1, color);
        ctx.fill(r.x(), r.bottom() - 1, r.right(), r.bottom(), color);
        ctx.fill(r.x(), r.y(), r.x() + 1, r.bottom(), color);
        ctx.fill(r.right() - 1, r.y(), r.right(), r.bottom(), color);
    }
}
