package com.rkb.client.gui;

import com.rkb.client.config.ConfigManager;
import com.rkb.client.config.Favorites;
import com.rkb.client.input.KeyUtil;
import com.rkb.client.module.Category;
import com.rkb.client.module.Module;
import com.rkb.client.module.ModuleManager;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Dedicated RKB Modules browser. */
public class RkbModulesScreen extends Screen {
    private record Hit(RkbUi.Rect rect, Runnable action) {
        boolean contains(int x, int y) { return rect.contains(x, y); }
    }

    private final List<Hit> hits = new ArrayList<>();
    private final List<Module> shown = new ArrayList<>();
    private TextFieldWidget search;
    private RkbLayout.SidebarShell shell;
    private Category filter = null;
    private boolean favoritesOnly = false;
    private Module detail;
    private float scroll;
    private int contentH;
    private String listening = null; // moduleId:settingId or moduleId:key

    public RkbModulesScreen() { super(Text.literal("RKB Modules")); }

    @Override
    protected void init() {
        int w = Math.min(320, Math.max(160, width - 280));
        search = new TextFieldWidget(textRenderer, 0, 0, w, 20, Text.literal("Search"));
        search.setMaxLength(64);
        search.setPlaceholder(Text.literal("Search modules..."));
        addDrawableChild(search);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() {
        ConfigManager.save();
        MinecraftClient.getInstance().setScreen(null);
    }

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
        if (search != null && search.mouseClicked(click, doubled)) {
            setFocused(search);
            return true;
        }
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
    public boolean keyPressed(KeyInput input) {
        if (listening != null) {
            if (input.isEscape()) { listening = null; return true; }
            // keybind capture handled in detail action; simple escape
            listening = null;
            return true;
        }
        if (input.isEscape()) {
            if (detail != null) { detail = null; return true; }
            close();
            return true;
        }
        if (search != null && search.keyPressed(input)) return true;
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (search != null && search.charTyped(input)) return true;
        return super.charTyped(input);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        hits.clear();
        RkbUi.background(ctx, width, height, System.currentTimeMillis());
        shell = RkbSidebar.draw(ctx, textRenderer, width, height, mouseX, mouseY, RkbSidebar.NavAction.MODULES, (r,a) -> hits.add(new Hit(r,a)));
        drawContent(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawContent(DrawContext ctx, int mx, int my, float delta) {
        TextRenderer tr = textRenderer;
        RkbUi.Rect c = shell.content();
        float titleScale = 1.45f;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(c.x(), c.y());
        ctx.getMatrices().scale(titleScale, titleScale);
        ctx.drawText(tr, RkbUi.menuText("RKB Modules"), 0, 0, RkbUi.TEXT, false);
        ctx.getMatrices().popMatrix();
        ctx.drawText(tr, RkbUi.menuText("Enable, search, and configure every registered module."), c.x(), c.y() + 13, RkbUi.MUTED, false);

        int searchW = Math.min(240, Math.max(140, c.width() / 3));
        search.setX(c.x());
        search.setY(c.y() + 28);
        search.setWidth(searchW);
        search.visible = true;

        // chips
        int chipX = c.x() + searchW + 10;
        int chipY = c.y() + 28;
        chipX = drawChip(ctx, tr, "All", chipX, chipY, filter == null && !favoritesOnly, () -> { filter = null; favoritesOnly = false; scroll = 0; }, mx, my);
        for (Category cat : Category.values()) {
            if (cat.isPage()) continue;
            chipX = drawChip(ctx, tr, cat.label(), chipX, chipY, filter == cat && !favoritesOnly, () -> { filter = cat; favoritesOnly = false; scroll = 0; }, mx, my);
            if (chipX > c.right() - 70) { chipX = c.x() + searchW + 10; chipY += 26; }
        }
        drawChip(ctx, tr, "Favorites", chipX, chipY, favoritesOnly, () -> { favoritesOnly = !favoritesOnly; filter = null; scroll = 0; }, mx, my);

        int listY = chipY + 32;
        RkbUi.Rect list = new RkbUi.Rect(c.x(), listY, c.width(), Math.max(60, c.bottom() - listY - 8));
        ctx.fill(list.x(), list.y(), list.right(), list.bottom(), RkbUi.BG);
        ctx.enableScissor(list.x(), list.y(), list.right(), list.bottom());

        rebuildShown();
        if (detail != null) {
            drawDetail(ctx, tr, list, mx, my);
        } else {
            drawGrid(ctx, tr, list, mx, my);
        }
        ctx.disableScissor();
    }

    private void rebuildShown() {
        shown.clear();
        String q = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        for (Module m : ModuleManager.get().all()) {
            if (filter != null && m.category() != filter) continue;
            if (favoritesOnly && !Favorites.contains(m.id())) continue;
            if (!q.isEmpty()) {
                String hay = (m.name() + " " + m.description() + " " + m.category().label()).toLowerCase(Locale.ROOT);
                if (!hay.contains(q)) continue;
            }
            shown.add(m);
        }
        shown.sort(Comparator.comparing((Module m) -> m.category().ordinal()).thenComparing(Module::name, String.CASE_INSENSITIVE_ORDER));
    }

    private void drawGrid(DrawContext ctx, TextRenderer tr, RkbUi.Rect list, int mx, int my) {
        int cols = list.width() >= 980 ? 4 : list.width() >= 720 ? 3 : list.width() >= 470 ? 2 : 1;
        int gap = 8;
        int cardW = (list.width() - gap * (cols - 1)) / cols;
        int cardH = 86;
        int y = list.y() - (int) scroll;
        int x0 = list.x();
        int i = 0;
        int enabledCount = 0;
        for (Module m : shown) if (m.isEnabled()) enabledCount++;
        for (Module m : shown) {
            int col = i % cols;
            int row = i / cols;
            int cx = x0 + col * (cardW + gap);
            int cy = y + row * (cardH + gap);
            RkbUi.Rect card = new RkbUi.Rect(cx, cy, cardW, cardH);
            if (card.bottom() >= list.y() && card.y() <= list.bottom()) {
                boolean hover = card.contains(mx, my);
                ctx.fill(card.x(), card.y(), card.right(), card.bottom(), hover ? RkbUi.SURFACE_3 : RkbUi.SURFACE_2);
                drawBorder(ctx, card, hover ? 0xFF3A4352 : RkbUi.LINE);
                if (m.isEnabled()) ctx.fill(card.x(), card.y() + 8, card.x() + 2, card.bottom() - 8, RkbUi.SUCCESS);
                ctx.drawText(tr, RkbUi.fit(tr, m.name(), cardW - 20), cx + 12, cy + 10, RkbUi.TEXT, false);
                ctx.drawText(tr, RkbUi.fit(tr, m.category().label().toUpperCase(Locale.ROOT), cardW - 20), cx + 12, cy + 24, RkbUi.DIM, false);
                ctx.drawText(tr, RkbUi.fit(tr, m.description(), cardW - 20), cx + 12, cy + 38, RkbUi.MUTED, false);
                // settings button
                RkbUi.Rect cfg = new RkbUi.Rect(cx + 10, cy + cardH - 26, 20, 18);
                drawBorder(ctx, cfg, cfg.contains(mx, my) ? RkbUi.CHROME_ACCENT : RkbUi.LINE);
                ctx.drawText(tr, RkbUi.menuText("..."), cfg.x() + 5, cfg.y() + 5, RkbUi.MUTED, false);
                hits.add(new Hit(cfg, () -> { detail = m; scroll = 0; }));
                // reference-style state bar
                RkbUi.Rect tog = new RkbUi.Rect(cx + cardW - 70, cy + cardH - 26, 60, 18);
                drawStateToggle(ctx, tr, tog, m.isEnabled());
                hits.add(new Hit(tog, () -> { m.toggle(); ConfigManager.save(); }));
                RkbUi.Rect body = new RkbUi.Rect(cx, cy, cardW, cardH - 30);
                hits.add(new Hit(body, () -> { detail = m; scroll = 0; }));
            }
            i++;
        }
        int rows = (shown.size() + cols - 1) / cols;
        contentH = rows * (cardH + gap) + 24;
        ctx.fill(list.x(), list.bottom() - 16, list.right(), list.bottom(), RkbUi.BG);
        ctx.drawText(tr, shown.size() + " modules · " + enabledCount + " enabled", list.x(), list.bottom() - 14, RkbUi.DIM, false);
    }

    private void drawDetail(DrawContext ctx, TextRenderer tr, RkbUi.Rect list, int mx, int my) {
        Module m = detail;
        int y = list.y() - (int) scroll;
        RkbUi.Rect back = new RkbUi.Rect(list.x(), y, 70, 24);
        drawButton(ctx, tr, back, "Back", back.contains(mx, my), false);
        hits.add(new Hit(back, () -> { detail = null; scroll = 0; }));
        ctx.drawText(tr, m.name(), list.x() + 80, y + 7, RkbUi.TEXT, false);
        ctx.drawText(tr, m.category().label(), list.x() + 80, y + 20, RkbUi.DIM, false);
        y += 36;
        ctx.drawText(tr, RkbUi.fit(tr, m.description(), list.width() - 16), list.x(), y, RkbUi.MUTED, false);
        y += 24;
        // enabled
        RkbUi.Rect tog = new RkbUi.Rect(list.right() - 30, y - 2, 24, 12);
        ctx.drawText(tr, RkbUi.menuText("Enabled"), list.x(), y, RkbUi.TEXT, false);
        drawToggle(ctx, tog, m.isEnabled());
        hits.add(new Hit(new RkbUi.Rect(list.x(), y - 4, list.width(), 20), () -> { m.toggle(); ConfigManager.save(); }));
        y += 28;
        for (Setting<?> s : m.settings()) {
            y = drawSetting(ctx, tr, m, s, list.x(), y, list.width(), mx, my);
        }
        contentH = (y + (int) scroll) - list.y() + 20;
    }

    private int drawSetting(DrawContext ctx, TextRenderer tr, Module m, Setting<?> s, int x, int y, int w, int mx, int my) {
        ctx.fill(x, y, x + w, y + 40, RkbUi.SURFACE_2);
        drawBorder(ctx, new RkbUi.Rect(x, y, w, 40), RkbUi.LINE);
        ctx.drawText(tr, s.name(), x + 10, y + 8, RkbUi.TEXT, false);
        ctx.drawText(tr, RkbUi.fit(tr, "", w - 160), x + 10, y + 22, RkbUi.MUTED, false);
        if (s instanceof BoolSetting b) {
            RkbUi.Rect tog = new RkbUi.Rect(x + w - 34, y + 14, 24, 12);
            drawToggle(ctx, tog, b.on());
            hits.add(new Hit(new RkbUi.Rect(x, y, w, 40), () -> { b.toggle(); ConfigManager.save(); }));
        } else if (s instanceof IntSetting iSet) {
            RkbUi.Rect minus = new RkbUi.Rect(x + w - 96, y + 10, 20, 20);
            RkbUi.Rect plus = new RkbUi.Rect(x + w - 30, y + 10, 20, 20);
            drawButton(ctx, tr, minus, "-", minus.contains(mx, my), false);
            drawButton(ctx, tr, plus, "+", plus.contains(mx, my), false);
            String val = String.valueOf(iSet.get());
            ctx.drawText(tr, val, x + w - 58 - tr.getWidth(val) / 2, y + 16, RkbUi.TEXT, false);
            hits.add(new Hit(minus, () -> { iSet.step(-1); ConfigManager.save(); }));
            hits.add(new Hit(plus, () -> { iSet.step(1); ConfigManager.save(); }));
        } else if (s instanceof ChoiceSetting cSet) {
            RkbUi.Rect box = new RkbUi.Rect(x + w - 140, y + 10, 130, 20);
            drawButton(ctx, tr, box, cSet.get(), box.contains(mx, my), false);
            hits.add(new Hit(box, () -> { cSet.cycle(); ConfigManager.save(); }));
        } else {
            ctx.drawText(tr, RkbUi.fit(tr, String.valueOf(s.get()), 120), x + w - 130, y + 16, RkbUi.MUTED, false);
        }
        return y + 44;
    }

    private int drawChip(DrawContext ctx, TextRenderer tr, String label, int x, int y, boolean active, Runnable action, int mx, int my) {
        int w = tr.getWidth(label) + 18;
        RkbUi.Rect r = new RkbUi.Rect(x, y, w, 22);
        boolean hover = r.contains(mx, my);
        int fill = active ? RkbUi.CHROME_ACCENT : (hover ? RkbUi.SURFACE_3 : 0);
        if (fill != 0) ctx.fill(r.x(), r.y(), r.right(), r.bottom(), fill);
        drawBorder(ctx, r, active ? RkbUi.CHROME_ACCENT : RkbUi.LINE);
        ctx.drawText(tr, label, r.x() + 9, r.y() + 7, active ? 0xFFFFFFFF : RkbUi.MUTED, false);
        hits.add(new Hit(r, action));
        return x + w + 6;
    }

    private void drawButton(DrawContext ctx, TextRenderer tr, RkbUi.Rect r, String label, boolean hover, boolean primary) {
        int fill = primary ? RkbUi.CHROME_ACCENT : (hover ? RkbUi.SURFACE_3 : RkbUi.SURFACE_2);
        ctx.fill(r.x(), r.y(), r.right(), r.bottom(), fill);
        drawBorder(ctx, r, primary ? RkbUi.CHROME_ACCENT : RkbUi.LINE);
        int tw = tr.getWidth(label);
        ctx.drawText(tr, label, r.x() + (r.width() - tw) / 2, r.y() + (r.height() - 8) / 2, primary ? 0xFFFFFFFF : RkbUi.TEXT, false);
    }

    private void drawStateToggle(DrawContext ctx, TextRenderer tr, RkbUi.Rect r, boolean on) {
        ctx.fill(r.x(), r.y(), r.right(), r.bottom(), on ? 0xFF176D3C : 0xFF272E3A);
        drawBorder(ctx, r, on ? 0xFF1F8A4C : RkbUi.LINE);
        String label = on ? "Enabled" : "Disabled";
        int w = tr.getWidth(RkbUi.menuText(label));
        ctx.drawText(tr, RkbUi.menuText(label), r.x() + (r.width() - w) / 2, r.y() + (r.height() - 8) / 2,
                on ? 0xFFE0FFE9 : RkbUi.MUTED, false);
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
