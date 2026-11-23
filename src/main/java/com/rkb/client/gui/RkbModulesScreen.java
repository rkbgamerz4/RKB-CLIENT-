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

        // Reference-style mod menu panel inside the RKB content shell.
        int pad = 12;
        RkbUi.Rect panel = new RkbUi.Rect(c.x(), c.y(), c.width(), c.height() - 2);
        ctx.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), 0xF5080B12);
        drawBorder(ctx, panel, 0x22FFFFFF);

        // Header: red logo badge + spacer + close
        int hx = panel.x() + pad;
        int hy = panel.y() + pad;
        RkbUi.Rect logo = new RkbUi.Rect(hx, hy, 142, 34);
        ctx.fill(logo.x(), logo.y(), logo.right(), logo.bottom(), 0xFFE33342);
        drawBorder(ctx, logo, 0xFFB32632);
        ctx.drawText(tr, RkbUi.menuText("\u25A6 RKB MOD MENU"), logo.x() + 10, logo.y() + 13, 0xFFFFFFFF, false);
        hits.add(new Hit(logo, () -> { detail = null; scroll = 0; filter = null; favoritesOnly = false; }));

        RkbUi.Rect close = new RkbUi.Rect(panel.right() - pad - 32, hy + 2, 32, 30);
        boolean closeHover = close.contains(mx, my);
        ctx.fill(close.x(), close.y(), close.right(), close.bottom(), closeHover ? 0xFF3A1820 : 0xFF24131A);
        drawBorder(ctx, close, 0xFF6C2734);
        ctx.drawText(tr, "x", close.x() + 12, close.y() + 10, 0xFFE15A67, false);
        hits.add(new Hit(close, this::close));

        // Filters row
        int fy = hy + 44;
        int chipH = 28;
        int chipGap = 6;
        int cxChip = hx;
        cxChip = drawFilterChip(ctx, tr, "All", cxChip, fy, chipH, filter == null && !favoritesOnly,
                () -> { filter = null; favoritesOnly = false; scroll = 0; }, mx, my) + chipGap;
        for (Category cat : Category.values()) {
            if (cat.isPage()) continue;
            String label = cat.label();
            int cw = tr.getWidth(label) + 28;
            if (cxChip + cw > panel.right() - pad - 220) break; // keep row compact; rest accessible via All/search
            cxChip = drawFilterChip(ctx, tr, label, cxChip, fy, chipH, filter == cat && !favoritesOnly,
                    () -> { filter = cat; favoritesOnly = false; scroll = 0; }, mx, my) + chipGap;
        }

        // Right controls: search, favorites heart, view indicator
        int rightCtl = panel.right() - pad;
        RkbUi.Rect viewBtn = new RkbUi.Rect(rightCtl - 28, fy, 28, 28);
        drawIconButton(ctx, tr, viewBtn, "\u25A6", false, mx, my);
        rightCtl = viewBtn.x() - 6;
        RkbUi.Rect favBtn = new RkbUi.Rect(rightCtl - 28, fy, 28, 28);
        drawIconButton(ctx, tr, favBtn, "\u2665", favoritesOnly, mx, my);
        hits.add(new Hit(favBtn, () -> { favoritesOnly = !favoritesOnly; filter = null; scroll = 0; }));
        rightCtl = favBtn.x() - 8;
        int searchW = Math.max(120, Math.min(230, rightCtl - cxChip - 8));
        search.setX(rightCtl - searchW);
        search.setY(fy + 4);
        search.setWidth(searchW);
        search.setHeight(20);
        search.visible = true;

        // Grid/list
        int listY = fy + 38;
        RkbUi.Rect list = new RkbUi.Rect(panel.x() + pad, listY, panel.width() - pad * 2,
                Math.max(60, panel.bottom() - pad - listY));
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
        int cols = list.width() >= 900 ? 4 : list.width() >= 650 ? 3 : list.width() >= 430 ? 2 : 1;
        int gap = 8;
        int cardW = (list.width() - gap * (cols - 1)) / cols;
        int cardH = 112;
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
                ctx.fill(card.x(), card.y(), card.right(), card.bottom(), hover ? 0xFF151E2C : 0xFF111722);
                drawBorder(ctx, card, hover ? 0xFF52647D : 0xFF242D3B);

                // body click opens detail; specific controls added after to win hit-testing
                hits.add(new Hit(card, () -> { detail = m; scroll = 0; }));

                // top row: name + favorite heart
                String name = RkbUi.fit(tr, m.name(), cardW - 34);
                ctx.drawText(tr, RkbUi.menuText(name), cx + 10, cy + 10, 0xFFE0E5EE, false);
                boolean fav = Favorites.contains(m.id());
                RkbUi.Rect heart = new RkbUi.Rect(cx + cardW - 22, cy + 8, 14, 14);
                ctx.drawText(tr, fav ? "\u2665" : "\u2661", heart.x(), heart.y() + 2, fav ? 0xFFDF3544 : 0xFF647187, false);
                hits.add(new Hit(new RkbUi.Rect(heart.x() - 4, heart.y() - 4, 22, 22), () -> { Favorites.toggle(m.id()); ConfigManager.save(); }));

                // center symbol
                String sym = moduleSymbol(m);
                float ss = 2.0f;
                int symW = Math.round(tr.getWidth(sym) * ss);
                ctx.getMatrices().pushMatrix();
                ctx.getMatrices().translate(cx + (cardW - symW) / 2f, cy + 34);
                ctx.getMatrices().scale(ss, ss);
                ctx.drawText(tr, sym, 0, 0, 0xFFD6DEEB, false);
                ctx.getMatrices().popMatrix();

                // bottom row: gear + enabled/disabled
                RkbUi.Rect cfg = new RkbUi.Rect(cx + 10, cy + cardH - 30, 24, 22);
                boolean cfgHover = cfg.contains(mx, my);
                ctx.fill(cfg.x(), cfg.y(), cfg.right(), cfg.bottom(), cfgHover ? 0xFF2B374A : 0xFF202A39);
                drawBorder(ctx, cfg, cfgHover ? 0xFF52647D : 0xFF202A39);
                ctx.drawText(tr, "\u2699", cfg.x() + 8, cfg.y() + 7, 0xFFC4CFDF, false);
                hits.add(new Hit(cfg, () -> { detail = m; scroll = 0; }));

                RkbUi.Rect tog = new RkbUi.Rect(cx + 40, cy + cardH - 30, cardW - 50, 22);
                drawStateToggle(ctx, tr, tog, m.isEnabled());
                hits.add(new Hit(tog, () -> { m.toggle(); ConfigManager.save(); }));
            }
            i++;
        }
        int rows = (shown.size() + cols - 1) / cols;
        contentH = rows * (cardH + gap) + 24;
        ctx.fill(list.x(), list.bottom() - 16, list.right(), list.bottom(), 0xF5080B12);
        ctx.drawText(tr, shown.size() + " modules \u00B7 " + enabledCount + " enabled", list.x(), list.bottom() - 14, 0xFF9CA8BA, false);
    }

    private String moduleSymbol(Module m) {
        String n = m.name();
        if (n == null || n.isEmpty()) return "?";
        // short, stable symbol; avoids emoji/font dependency issues in MC
        return n.length() <= 3 ? n.toUpperCase(Locale.ROOT) : n.substring(0, 1).toUpperCase(Locale.ROOT);
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

    private int drawFilterChip(DrawContext ctx, TextRenderer tr, String label, int x, int y, int h, boolean active, Runnable action, int mx, int my) {
        int w = tr.getWidth(label) + 28;
        RkbUi.Rect r = new RkbUi.Rect(x, y, w, h);
        boolean hover = r.contains(mx, my);
        int fill = active ? 0xFFDF3544 : (hover ? 0xFF263245 : 0xFF171D28);
        ctx.fill(r.x(), r.y(), r.right(), r.bottom(), fill);
        drawBorder(ctx, r, active ? 0xFFDF3544 : 0x14FFFFFF);
        ctx.drawText(tr, RkbUi.menuText(label), r.x() + 14, r.y() + (h - 8) / 2, active ? 0xFFFFFFFF : 0xFF9BA6B8, false);
        hits.add(new Hit(r, action));
        return x + w;
    }

    private void drawIconButton(DrawContext ctx, TextRenderer tr, RkbUi.Rect r, String icon, boolean active, int mx, int my) {
        boolean hover = r.contains(mx, my);
        ctx.fill(r.x(), r.y(), r.right(), r.bottom(), active ? 0xFFDF3544 : (hover ? 0xFF263245 : 0xFF171D28));
        drawBorder(ctx, r, active ? 0xFFDF3544 : 0x14FFFFFF);
        int tw = tr.getWidth(icon);
        ctx.drawText(tr, icon, r.x() + (r.width() - tw) / 2, r.y() + (r.height() - 8) / 2, active ? 0xFFFFFFFF : 0xFFD8DFEB, false);
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
        drawBorder(ctx, r, on ? 0xFF1F8A4C : 0xFF343D4D);
        String label = on ? "Enabled" : "Disabled";
        int w = tr.getWidth(RkbUi.menuText(label));
        ctx.drawText(tr, RkbUi.menuText(label), r.x() + (r.width() - w) / 2, r.y() + (r.height() - 8) / 2,
                on ? 0xFFE0FFE9 : 0xFFAEB7C5, false);
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
