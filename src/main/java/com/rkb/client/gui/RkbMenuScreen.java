package com.rkb.client.gui;

import com.rkb.client.VersionInfo;
import com.rkb.client.config.ConfigManager;
import com.rkb.client.cloud.CloudService;
import com.rkb.client.cloud.CloudStore;
import com.rkb.client.cloud.FriendsManager;
import com.rkb.client.config.Favorites;
import com.rkb.client.input.KeyUtil;
import com.rkb.client.input.KeybindRegistry;
import com.rkb.client.module.Category;
import com.rkb.client.module.KeyAction;
import com.rkb.client.module.Module;
import com.rkb.client.module.ModuleManager;
import com.rkb.client.module.impl.NameTagBadgeModule;
import com.rkb.client.module.setting.BoolSetting;
import com.rkb.client.module.setting.ChoiceSetting;
import com.rkb.client.module.setting.IntSetting;
import com.rkb.client.module.setting.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * RKB mod menu, laid out like a modern client menu: tab bar on top, filter chips, search, favourites and a grid of
 * module cards (icon, heart, gear, Enabled/Disabled button). Mouse clicks are read with one guarded GLFW call per
 * frame so the screen works with touch-to-mouse emulation (drag the list to scroll); keyboard input is event-driven.
 */
public class RkbMenuScreen extends Screen {
    private static final int TAB_H = 20;
    private static final int GAP = 6;
    private static final int ROW_H = 20;
    private static final int FOOT_H = 14;
    private static final int[] DUMMY = new int[0];

    private static final String[] TAB_LABELS = {"MOD MENU", "SETTINGS", "PROFILES", "HUD EDITOR", "WAYPOINTS", "COSMETICS", "ABOUT"};
    private static final int PAGE_MODS = 0;
    private static final int PAGE_SETTINGS = 1;
    private static final int PAGE_PROFILES = 2;
    private static final int PAGE_ABOUT = 3;

    private record Hit(int x1, int y1, int x2, int y2, boolean inList, Runnable action) {
        boolean contains(int x, int y) {
            return x >= x1 && x < x2 && y >= y1 && y < y2;
        }
    }

    /** kind: 0 = All, 1 = New, 2 = one category. */
    private record Filter(String label, Category cat, int kind) {
    }

    private static int filterIdx = 0;
    private static boolean compactMode = false;
    private static boolean favOnly = false;

    private final List<Hit> hits = new ArrayList<>();
    private final Map<Module, float[]> anims = new HashMap<>();
    private final List<Filter> filters = new ArrayList<>();
    private final List<Module> shown = new ArrayList<>();
    private boolean listDirty = true;

    private int[] chipX = DUMMY;
    private int[] chipY = DUMMY;
    private int[] chipW = DUMMY;
    private int chipRows = 1;

    private TextFieldWidget search;
    private int tab = PAGE_MODS;
    private Module detail;
    private Module listening;
    private float scroll;
    private int contentH;
    private float pageAnim = 1f;
    private float k = 0.2f;
    private long lastNanos = System.nanoTime();
    private boolean closing;
    private boolean confirmReset;
    private String toast = "";
    private long toastUntil;

    private int px, py, pw, ph, tabY, innerX, innerW, searchY, chipsY, gridY, bottom;
    private int listX, listY, listW, listH;

    private boolean mouseWasDown;
    private boolean dragging;
    private boolean pressedInList;
    private int pressY;
    private int lastY;

    public RkbMenuScreen() {
        super(Text.literal("RKB CLIENT"));
    }

    // ------------------------------------------------------------------ setup

    @Override
    protected void init() {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        String previous = search == null ? "" : search.getText();

        pw = Math.min(width - 12, 520);
        ph = Math.min(height - 12 - TAB_H - 4, 300);
        px = (width - pw) / 2;
        tabY = (height - (TAB_H + 4 + ph)) / 2;
        py = tabY + TAB_H + 4;
        innerX = px + 8;
        innerW = pw - 16;
        bottom = py + ph - FOOT_H - 4;
        listX = innerX;
        listW = innerW;
        searchY = py + 8;

        filters.clear();
        filters.add(new Filter("All", null, 0));
        filters.add(new Filter("New", null, 1));
        for (Category c : Category.values()) {
            if (!c.isPage() && !ModuleManager.get().inCategory(c).isEmpty()) {
                filters.add(new Filter(c.label(), c, 2));
            }
        }
        if (filterIdx >= filters.size()) {
            filterIdx = 0;
        }

        // chip flow layout
        int n = filters.size();
        chipX = new int[n];
        chipY = new int[n];
        chipW = new int[n];
        int x = innerX;
        int y = searchY + 22;
        chipRows = 1;
        for (int i = 0; i < n; i++) {
            int w = tr.getWidth(filters.get(i).label()) + 14;
            if (x + w > innerX + innerW && x > innerX) {
                x = innerX;
                y += 19;
                chipRows++;
            }
            chipX[i] = x;
            chipY[i] = y;
            chipW[i] = w;
            x += w + 4;
        }
        chipsY = searchY + 22;
        gridY = chipsY + chipRows * 19 + 2;

        int sw = innerW - 2 * 20;
        search = new TextFieldWidget(tr, innerX, searchY, sw, 16, Text.literal("Search"));
        search.setMaxLength(32);
        search.setPlaceholder(Text.literal("Search"));
        search.setChangedListener(s -> {
            scroll = 0;
            listDirty = true;
        });
        search.setText(previous);
        addSelectableChild(search);
        listDirty = true;

        mouseWasDown = KeyUtil.isMouseDown(mc, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    public boolean shouldCloseOnEsc() {
        return listening == null && !(tab == PAGE_MODS && detail != null);
    }

    public boolean shouldPause() {
        return false;
    }

    public void removed() {
        ConfigManager.save();
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll -= (float) (verticalAmount * 18.0);
        return true;
    }

    // ------------------------------------------------------------------ navigation

    private void openTab(int t) {
        tab = t;
        detail = null;
        listening = null;
        scroll = 0;
        pageAnim = 0f;
        confirmReset = false;
    }

    private void openDetail(Module m) {
        tab = PAGE_MODS;
        detail = m;
        listening = null;
        scroll = 0;
        pageAnim = 0f;
    }

    private void closeDetail() {
        detail = null;
        listening = null;
        scroll = 0;
        pageAnim = 0f;
    }

    private void showToast(String text) {
        toast = text;
        toastUntil = System.currentTimeMillis() + 2500;
    }

    private Module pageModule() {
        return tab == PAGE_SETTINGS ? ModuleManager.get().ui() : detail;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean keyPressed(KeyInput input) {
        if (listening != null) {
            int key = input.key();
            if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
                listening.setKeyCode(KeyUtil.UNBOUND);
            } else if (!input.isEscape()) {
                listening.setKeyCode(key);
            }
            listening = null;
            ConfigManager.save();
            return true;
        }
        if (!search.isFocused()) {
            KeyBinding menuKey = KeybindRegistry.bindingOf(ModuleManager.get().ui());
            if (menuKey != null && menuKey.matchesKey(input)) {
                closing = true;
                MinecraftClient.getInstance().setScreen(null);
                return true;
            }
            if (input.isEscape() && tab == PAGE_MODS && detail != null) {
                closeDetail();
                return true;
            }
            if (input.isUp()) {
                scroll -= 18;
                return true;
            }
            if (input.isDown()) {
                scroll += 18;
                return true;
            }
        }
        return super.keyPressed(input);
    }

    private boolean inList(int mx, int my) {
        return mx >= listX && mx < listX + listW && my >= listY && my < listY + listH;
    }

    private void handleMouse(MinecraftClient mc, int mx, int my) {
        boolean down = KeyUtil.isMouseDown(mc, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        if (down && !mouseWasDown) {
            pressY = my;
            lastY = my;
            dragging = false;
            pressedInList = inList(mx, my);
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
        if (search.visible && search.isMouseOver(mx, my)) {
            return;
        }
        search.setFocused(false);
        Module listeningBefore = listening;
        boolean confirmBefore = confirmReset;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.contains(mx, my) && (!h.inList() || inList(mx, my))) {
                h.action().run();
                break;
            }
        }
        if (listening == listeningBefore) {
            listening = null;
        }
        if (confirmBefore && confirmReset) {
            confirmReset = false;
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void text(DrawContext c, TextRenderer tr, String s, int x, int y, int color) {
        c.drawText(tr, s, x, y, color, false);
    }

    private static String fit(TextRenderer tr, String s, int maxW) {
        if (maxW <= 0) {
            return "";
        }
        if (tr.getWidth(s) <= maxW) {
            return s;
        }
        int ew = tr.getWidth("..");
        int n = s.length();
        while (n > 0 && tr.getWidth(s.substring(0, n)) + ew > maxW) {
            n--;
        }
        return s.substring(0, n) + "..";
    }

    private static List<String> wrap(TextRenderer tr, String s, int maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String test = line.isEmpty() ? word : line + " " + word;
            if (tr.getWidth(test) > maxW && !line.isEmpty()) {
                out.add(line.toString());
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(test);
            }
        }
        if (!line.isEmpty()) {
            out.add(line.toString());
        }
        return out;
    }

    private static int lerp(int a, int b, float t) {
        float f = Math.max(0f, Math.min(1f, t));
        int aa = (int) (((a >>> 24) & 255) + (((b >>> 24) & 255) - ((a >>> 24) & 255)) * f);
        int ar = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * f);
        int ag = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * f);
        int ab = (int) ((a & 255) + ((b & 255) - (a & 255)) * f);
        return (aa << 24) | (ar << 16) | (ag << 8) | ab;
    }

    private float[] anim(Module m) {
        float[] a = anims.get(m);
        if (a == null) {
            a = new float[]{m.isEnabled() ? 1f : 0f, 0f};
            anims.put(m, a);
        }
        return a;
    }

    private static void outline(DrawContext c, int x, int y, int w, int h, int color) {
        c.fill(x, y, x + w, y + 1, color);
        c.fill(x, y + h - 1, x + w, y + h, color);
        c.fill(x, y, x + 1, y + h, color);
        c.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static void pill(DrawContext c, int x, int y, int w, int h, float t, int accent) {
        c.fill(x, y, x + w, y + h, lerp(Theme.OFF, accent, t));
        int knob = x + 1 + Math.round((w - h) * Math.max(0f, Math.min(1f, t)));
        c.fill(knob, y + 1, knob + h - 2, y + h - 1, Theme.TEXT);
    }

    private static void heart(DrawContext c, int x, int y, int color) {
        c.fill(x + 1, y, x + 3, y + 1, color);
        c.fill(x + 4, y, x + 6, y + 1, color);
        c.fill(x, y + 1, x + 7, y + 3, color);
        c.fill(x + 1, y + 3, x + 6, y + 4, color);
        c.fill(x + 2, y + 4, x + 5, y + 5, color);
        c.fill(x + 3, y + 5, x + 4, y + 6, color);
    }

    private static void gear(DrawContext c, int cx, int cy, int color, int hole) {
        c.fill(cx - 3, cy - 1, cx + 4, cy + 2, color);
        c.fill(cx - 1, cy - 3, cx + 2, cy + 4, color);
        c.fill(cx - 2, cy - 2, cx + 3, cy + 3, color);
        c.fill(cx - 1, cy - 1, cx + 2, cy + 2, hole);
    }

    private int button(DrawContext c, TextRenderer tr, String label, int right, int y, boolean enabled, Runnable action) {
        int w = tr.getWidth(label) + 12;
        int x = right - w;
        c.fill(x, y, right, y + 14, enabled ? Theme.BORDER : Theme.CARD_INNER);
        text(c, tr, label, x + 6, y + 3, enabled ? Theme.TEXT : Theme.OFF);
        if (enabled && action != null) {
            hits.add(new Hit(x, y, right, y + 14, true, action));
        }
        return x;
    }

    private void rowBg(DrawContext c, int y, int cw) {
        c.fill(listX, y, listX + cw, y + ROW_H - 2, Theme.CARD);
    }

    private void refreshList() {
        listDirty = false;
        shown.clear();
        String q = search.getText().trim().toLowerCase(Locale.ROOT);
        Filter f = filters.get(Math.min(filterIdx, filters.size() - 1));
        for (Module m : ModuleManager.get().all()) {
            if (!q.isEmpty()) {
                if (!m.name().toLowerCase(Locale.ROOT).contains(q)
                        && !m.id().toLowerCase(Locale.ROOT).contains(q)
                        && !m.description().toLowerCase(Locale.ROOT).contains(q)) {
                    continue;
                }
            } else if (f.kind() == 1 && !ModuleMeta.isNew(m)) {
                continue;
            } else if (f.kind() == 2 && m.category() != f.cat()) {
                continue;
            }
            if (favOnly && !Favorites.contains(m.id())) {
                continue;
            }
            shown.add(m);
        }
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        MinecraftClient mc = MinecraftClient.getInstance();
        handleMouse(mc, mx, my);
        if (closing) {
            return;
        }
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
        lastNanos = now;
        k = Math.min(1f, dt * 14f);
        pageAnim = Math.min(1f, pageAnim + dt * 6f);

        hits.clear();
        TextRenderer tr = mc.textRenderer;
        int accent = Theme.accent();
        boolean grid = tab == PAGE_MODS && detail == null;
        if (grid) {
            listY = gridY;
        } else {
            listY = py + 32;
        }
        listH = Math.max(10, bottom - listY);

        outline(ctx, px - 1, py - 1, pw + 2, ph + 2, Theme.BORDER);
        ctx.fill(px, py, px + pw, py + ph, Theme.PANEL);
        drawTabs(ctx, tr, mx, my, accent);

        search.visible = grid;
        if (!grid && search.isFocused()) {
            search.setFocused(false);
        }
        if (grid) {
            drawFilterRow(ctx, tr, mx, my, delta, accent);
        } else {
            drawPageHeader(ctx, tr, accent);
        }

        int maxScroll = Math.max(0, contentH - listH);
        scroll = Math.max(0, Math.min(maxScroll, scroll));

        ctx.enableScissor(listX, listY, listX + listW, listY + listH);
        if (grid) {
            drawGrid(ctx, tr, mx, my, accent);
        } else if (tab == PAGE_PROFILES) {
            drawProfiles(ctx, tr, accent);
        } else if (tab == PAGE_ABOUT) {
            drawAbout(ctx, tr);
        } else if (tab == PAGE_SETTINGS) {
            drawSettingsPage(ctx, tr, accent);
        } else {
            drawDetail(ctx, tr, accent, pageModule());
        }
        ctx.disableScissor();

        if (pageAnim < 1f) {
            int a = (int) ((1f - pageAnim) * 200f);
            ctx.fill(listX, listY, listX + listW, listY + listH, (a << 24) | 0x050814);
        }
        if (contentH > listH) {
            int barH = Math.max(16, listH * listH / contentH);
            int barPos = listY + (int) ((listH - barH) * (scroll / Math.max(1f, contentH - listH)));
            ctx.fill(listX + listW - 3, barPos, listX + listW - 1, barPos + barH, accent);
        }
        drawFooter(ctx, tr, mc);
        if (System.currentTimeMillis() < toastUntil) {
            int tw = tr.getWidth(toast) + 16;
            int tx = listX + (listW - tw) / 2;
            int ty = bottom - 20;
            ctx.fill(tx, ty, tx + tw, ty + 16, Theme.BLACK);
            outline(ctx, tx, ty, tw, 16, accent);
            text(ctx, tr, toast, tx + 8, ty + 4, Theme.TEXT);
        }
    }

    private void drawTabs(DrawContext ctx, TextRenderer tr, int mx, int my, int accent) {
        int closeW = TAB_H;
        int avail = pw - closeW - 6;
        int gap = 3;
        int count = TAB_LABELS.length;
        int tw = (avail - gap * (count - 1)) / count;
        int activeTab = tab == PAGE_MODS ? 0 : tab == PAGE_SETTINGS ? 1 : tab == PAGE_PROFILES ? 2 : 6;
        for (int i = 0; i < count; i++) {
            int x = px + i * (tw + gap);
            boolean active = i == activeTab;
            boolean hover = mx >= x && mx < x + tw && my >= tabY && my < tabY + TAB_H;
            ctx.fill(x, tabY, x + tw, tabY + TAB_H, active ? accent : hover ? Theme.CARD_HOVER : Theme.CARD);
            String label = fit(tr, TAB_LABELS[i], tw - 8);
            text(ctx, tr, label, x + (tw - tr.getWidth(label)) / 2, tabY + 6, active ? Theme.BLACK : hover ? Theme.TEXT : Theme.MUTED);
            final int idx = i;
            hits.add(new Hit(x, tabY, x + tw, tabY + TAB_H, false, () -> {
                switch (idx) {
                    case 0 -> openTab(PAGE_MODS);
                    case 1 -> openTab(PAGE_SETTINGS);
                    case 2 -> openTab(PAGE_PROFILES);
                    case 3 -> {
                        ConfigManager.save();
                        MinecraftClient.getInstance().setScreen(new HudEditorScreen());
                    }
                    case 4 -> {
                        ConfigManager.save();
                        MinecraftClient.getInstance().setScreen(new WaypointScreen());
                    }
                    case 5 -> {
                        ConfigManager.save();
                        MinecraftClient.getInstance().setScreen(new CosmeticsScreen());
                    }
                    default -> openTab(PAGE_ABOUT);
                }
            }));
        }
        int cx = px + pw - closeW;
        boolean ch = mx >= cx && mx < cx + closeW && my >= tabY && my < tabY + TAB_H;
        ctx.fill(cx, tabY, cx + closeW, tabY + TAB_H, ch ? Theme.CARD_HOVER : Theme.BLACK);
        outline(ctx, cx, tabY, closeW, TAB_H, accent);
        text(ctx, tr, "X", cx + (closeW - tr.getWidth("X")) / 2, tabY + 6, Theme.TEXT);
        hits.add(new Hit(cx, tabY, cx + closeW, tabY + TAB_H, false, () -> {
            closing = true;
            MinecraftClient.getInstance().setScreen(null);
        }));
    }

    private void drawFilterRow(DrawContext ctx, TextRenderer tr, int mx, int my, float delta, int accent) {
        search.render(ctx, mx, my, delta);

        int bx = innerX + innerW - 18;
        // grid / compact toggle
        ctx.fill(bx, searchY, bx + 16, searchY + 16, compactMode ? accent : Theme.CARD);
        int ic = compactMode ? Theme.BLACK : Theme.MUTED;
        ctx.fill(bx + 3, searchY + 3, bx + 7, searchY + 7, ic);
        ctx.fill(bx + 9, searchY + 3, bx + 13, searchY + 7, ic);
        ctx.fill(bx + 3, searchY + 9, bx + 7, searchY + 13, ic);
        ctx.fill(bx + 9, searchY + 9, bx + 13, searchY + 13, ic);
        hits.add(new Hit(bx, searchY, bx + 16, searchY + 16, false, () -> {
            compactMode = !compactMode;
            scroll = 0;
        }));
        // favourites filter
        int hx = bx - 20;
        ctx.fill(hx, searchY, hx + 16, searchY + 16, favOnly ? Theme.BORDER : Theme.CARD);
        heart(ctx, hx + 4, searchY + 5, favOnly ? accent : 0xFF59627A);
        hits.add(new Hit(hx, searchY, hx + 16, searchY + 16, false, () -> {
            favOnly = !favOnly;
            scroll = 0;
            listDirty = true;
        }));

        for (int i = 0; i < filters.size(); i++) {
            boolean active = i == filterIdx;
            int x = chipX[i];
            int y = chipY[i];
            int w = chipW[i];
            boolean hover = mx >= x && mx < x + w && my >= y && my < y + 16;
            ctx.fill(x, y, x + w, y + 16, active ? accent : hover ? Theme.CARD_HOVER : Theme.CARD);
            String label = filters.get(i).label();
            text(ctx, tr, label, x + 7, y + 4, active ? Theme.BLACK : hover ? Theme.TEXT : Theme.MUTED);
            final int idx = i;
            hits.add(new Hit(x, y, x + w, y + 16, false, () -> {
                filterIdx = idx;
                scroll = 0;
                listDirty = true;
                search.setText("");
                pageAnim = 0.4f;
            }));
        }
    }

    private void drawPageHeader(DrawContext ctx, TextRenderer tr, int accent) {
        int y = py + 8;
        if (tab == PAGE_MODS && detail != null) {
            String back = "< Back";
            int bw = tr.getWidth(back) + 12;
            ctx.fill(innerX, y, innerX + bw, y + 16, Theme.CARD);
            outline(ctx, innerX, y, bw, 16, accent);
            text(ctx, tr, back, innerX + 6, y + 4, Theme.TEXT);
            hits.add(new Hit(innerX, y, innerX + bw, y + 16, false, this::closeDetail));
            text(ctx, tr, fit(tr, detail.name(), innerW - bw - 12), innerX + bw + 8, y + 4, Theme.TEXT);
        } else {
            String title = tab == PAGE_SETTINGS ? "Interface settings"
                    : tab == PAGE_PROFILES ? "Profiles and tools" : "About RKB";
            text(ctx, tr, title, innerX, y + 4, accent);
        }
        ctx.fill(innerX, y + 20, innerX + innerW, y + 21, Theme.BORDER);
    }

    private void drawFooter(DrawContext ctx, TextRenderer tr, MinecraftClient mc) {
        StringBuilder sb = new StringBuilder();
        if (Theme.menuShowVersion()) {
            sb.append("MC ").append(VersionInfo.minecraft()).append("   ");
        }
        if (Theme.menuShowFps()) {
            sb.append("FPS ").append(mc.getCurrentFps()).append("   ");
        }
        sb.append(com.rkb.client.module.impl.NameTagBadgeModule.ICON).append(" RKB CLIENT ").append(VersionInfo.mod());
        text(ctx, tr, sb.toString(), innerX, py + ph - FOOT_H + 1, Theme.MUTED);
    }

    // ------------------------------------------------------------------ module grid

    private void drawGrid(DrawContext ctx, TextRenderer tr, int mx, int my, int accent) {
        if (listDirty) {
            refreshList();
        }
        boolean compact = compactMode;
        int cardH = compact ? 46 : 80;
        int minW = compact ? 96 : 116;
        int cols = Math.max(1, (listW - 6 + GAP) / (minW + GAP));
        int cardW = (listW - 6 - (cols - 1) * GAP) / cols;
        int originY = listY + 6 - Math.round(scroll);
        int enabledBg = lerp(accent, 0xFF05080F, 0.62f);

        for (int i = 0; i < shown.size(); i++) {
            Module m = shown.get(i);
            int x = listX + (i % cols) * (cardW + GAP);
            int y = originY + (i / cols) * (cardH + GAP);
            if (y + cardH < listY - 8 || y > listY + listH) {
                continue;
            }
            float[] a = anim(m);
            boolean hover = inList(mx, my) && mx >= x && mx < x + cardW && my >= y && my < y + cardH;
            a[0] += ((m.isEnabled() ? 1f : 0f) - a[0]) * k;
            a[1] += ((hover ? 1f : 0f) - a[1]) * k;

            ctx.fill(x, y, x + cardW, y + cardH, lerp(Theme.CARD, Theme.CARD_HOVER, a[1]));
            outline(ctx, x, y, cardW, cardH, lerp(Theme.BORDER, accent, a[0] * 0.8f));
            text(ctx, tr, fit(tr, m.name(), cardW - 26), x + 6, y + 6, Theme.TEXT);
            boolean fav = Favorites.contains(m.id());
            heart(ctx, x + cardW - 13, y + 6, fav ? accent : 0xFF59627A);

            if (!compact) {
                ctx.drawItem(ModuleMeta.icon(m), x + (cardW - 16) / 2, y + 26);
            }

            int by = y + cardH - 21;
            ctx.fill(x + 5, by, x + 21, by + 16, Theme.CARD_INNER);
            gear(ctx, x + 13, by + 8, lerp(Theme.MUTED, Theme.TEXT, a[1]), Theme.CARD_INNER);
            int tx = x + 25;
            int tw = cardW - 30;
            String label;
            int textColor;
            if (m.isToggleable()) {
                ctx.fill(tx, by, tx + tw, by + 16, lerp(0xFF141B2D, enabledBg, a[0]));
                label = m.isEnabled() ? "Enabled" : "Disabled";
                textColor = lerp(Theme.MUTED, accent, a[0]);
            } else {
                ctx.fill(tx, by, tx + tw, by + 16, 0xFF141B2D);
                label = "Open";
                textColor = Theme.MUTED;
            }
            text(ctx, tr, label, tx + (tw - tr.getWidth(label)) / 2, by + 4, textColor);

            if (ModuleMeta.isNew(m)) {
                int nw = tr.getWidth("NEW!") + 6;
                ctx.fill(x + 4, y - 5, x + 4 + nw, y + 5, accent);
                text(ctx, tr, "NEW!", x + 7, y - 3, Theme.BLACK);
            }

            hits.add(new Hit(x, y, x + cardW, y + cardH, true, () -> {
                if (m.isToggleable()) {
                    m.toggle();
                    ConfigManager.save();
                } else {
                    openDetail(m);
                }
            }));
            hits.add(new Hit(x + cardW - 15, y + 3, x + cardW - 2, y + 15, true, () -> {
                Favorites.toggle(m.id());
                listDirty = true;
                ConfigManager.save();
            }));
            hits.add(new Hit(x + 5, by, x + 21, by + 16, true, () -> openDetail(m)));
            if (m.isToggleable()) {
                hits.add(new Hit(tx, by, tx + tw, by + 16, true, () -> {
                    m.toggle();
                    ConfigManager.save();
                }));
            } else {
                hits.add(new Hit(tx, by, tx + tw, by + 16, true, () -> openDetail(m)));
            }
        }
        int rows = (shown.size() + cols - 1) / cols;
        contentH = 6 + rows * (cardH + GAP);
        if (shown.isEmpty()) {
            text(ctx, tr, favOnly ? "No favourites here yet. Tap the heart on a module." : "No modules found.",
                    listX + 4, listY + 10, Theme.MUTED);
        }
    }

    // ------------------------------------------------------------------ module settings page

    private void drawDetail(DrawContext ctx, TextRenderer tr, int accent, Module m) {
        int startY = listY - Math.round(scroll);
        int y = drawDetailBody(ctx, tr, accent, m, startY);
        y = drawResetButton(ctx, tr, accent, m, y);
        contentH = y - startY;
    }

    private int drawDetailBody(DrawContext ctx, TextRenderer tr, int accent, Module m, int startY) {
        int cw = listW - 6;
        int right = listX + cw - 8;
        int y = startY;

        for (String line : wrap(tr, m.description(), cw - 4)) {
            text(ctx, tr, line, listX + 2, y, Theme.MUTED);
            y += 11;
        }
        y += 4;

        if (m.isToggleable()) {
            float[] a = anim(m);
            a[0] += ((m.isEnabled() ? 1f : 0f) - a[0]) * k;
            rowBg(ctx, y, cw);
            text(ctx, tr, "Enabled", listX + 8, y + 5, Theme.TEXT);
            pill(ctx, right - 28, y + 3, 28, 12, a[0], accent);
            hits.add(new Hit(listX, y, listX + cw, y + ROW_H - 2, true, () -> {
                m.toggle();
                ConfigManager.save();
            }));
            y += ROW_H;
        }

        rowBg(ctx, y, cw);
        boolean capturing = listening == m;
        text(ctx, tr, "Keybind", listX + 8, y + 5, Theme.TEXT);
        String keyLabel = capturing ? "Press a key..." : KeyUtil.name(m.keyCode());
        int kw = Math.max(46, tr.getWidth(keyLabel) + 12);
        int kx = right - kw;
        ctx.fill(kx, y + 2, kx + kw, y + ROW_H - 4, capturing ? accent : Theme.BORDER);
        text(ctx, tr, keyLabel, kx + (kw - tr.getWidth(keyLabel)) / 2, y + 5, capturing ? Theme.BLACK : Theme.TEXT);
        hits.add(new Hit(kx, y + 2, kx + kw, y + ROW_H - 4, true, () -> listening = listening == m ? null : m));
        y += ROW_H;

        if (m.isEnabled() || m.actions().isEmpty() || !m.isToggleable()) {
            for (KeyAction act : m.actions()) {
                rowBg(ctx, y, cw);
                text(ctx, tr, fit(tr, act.name(), right - listX - 70), listX + 8, y + 5, Theme.TEXT);
                button(ctx, tr, "Run", right, y + 2, true, () -> {
                    MinecraftClient game = MinecraftClient.getInstance();
                    if (!m.isEnabled()) {
                        showToast("Turn on " + m.name() + " first");
                        return;
                    }
                    if (game.player == null) {
                        showToast("Join a world first");
                        return;
                    }
                    act.run().accept(game);
                    showToast(act.name());
                });
                y += ROW_H;
            }
        }

        for (Setting<?> s : m.settings()) {
            if (s.visible()) {
                y = drawSettingRow(ctx, tr, accent, s, y, cw, right);
            }
        }
        return y;
    }

    private int drawSettingRow(DrawContext ctx, TextRenderer tr, int accent, Setting<?> s, int y, int cw, int right) {
        rowBg(ctx, y, cw);
        text(ctx, tr, fit(tr, s.name(), right - listX - 120), listX + 8, y + 5, Theme.TEXT);
        if (s instanceof BoolSetting b) {
            pill(ctx, right - 28, y + 3, 28, 12, b.on() ? 1f : 0f, accent);
            hits.add(new Hit(right - 28, y, right, y + ROW_H - 2, true, () -> {
                b.toggle();
                ConfigManager.save();
            }));
        } else if (s instanceof IntSetting n) {
            int plusX = right - 14;
            int valX = plusX - 34;
            int minusX = valX - 14;
            ctx.fill(minusX, y + 3, minusX + 14, y + ROW_H - 5, Theme.BORDER);
            text(ctx, tr, "-", minusX + 5, y + 5, Theme.TEXT);
            ctx.fill(plusX, y + 3, plusX + 14, y + ROW_H - 5, Theme.BORDER);
            text(ctx, tr, "+", plusX + 4, y + 5, Theme.TEXT);
            String v = String.valueOf(n.value());
            text(ctx, tr, v, valX + (34 - tr.getWidth(v)) / 2, y + 5, accent);
            hits.add(new Hit(minusX, y, minusX + 14, y + ROW_H - 2, true, () -> {
                n.step(-1);
                ConfigManager.save();
            }));
            hits.add(new Hit(plusX, y, plusX + 14, y + ROW_H - 2, true, () -> {
                n.step(1);
                ConfigManager.save();
            }));
        } else if (s instanceof ChoiceSetting c) {
            String v = c.get();
            int bw = Math.max(50, tr.getWidth(v) + 12);
            int bx = right - bw;
            ctx.fill(bx, y + 2, bx + bw, y + ROW_H - 4, Theme.BORDER);
            text(ctx, tr, v, bx + (bw - tr.getWidth(v)) / 2, y + 5, accent);
            hits.add(new Hit(bx, y + 2, bx + bw, y + ROW_H - 4, true, () -> {
                c.cycle();
                ConfigManager.save();
            }));
        }
        return y + ROW_H;
    }

    private int drawResetButton(DrawContext ctx, TextRenderer tr, int accent, Module m, int y) {
        int cw = listW - 6;
        y += 4;
        ctx.fill(listX, y, listX + cw, y + 18, Theme.CARD);
        outline(ctx, listX, y, cw, 18, accent);
        String reset = "Reset to defaults";
        text(ctx, tr, reset, listX + (cw - tr.getWidth(reset)) / 2, y + 5, Theme.TEXT);
        hits.add(new Hit(listX, y, listX + cw, y + 18, true, () -> {
            m.resetToDefaults();
            ConfigManager.save();
            showToast(m.name() + " reset");
        }));
        return y + 22;
    }

    // ------------------------------------------------------------------ settings tab: interface + name tag badge

    private void drawSettingsPage(DrawContext ctx, TextRenderer tr, int accent) {
        int cw = listW - 6;
        int right = listX + cw - 8;
        int startY = listY - Math.round(scroll);
        int y = drawDetailBody(ctx, tr, accent, ModuleManager.get().ui(), startY);
        y = drawResetButton(ctx, tr, accent, ModuleManager.get().ui(), y);

        NameTagBadgeModule b = NameTagBadgeModule.get();
        if (b != null) {
            y += 4;
            text(ctx, tr, "NAME TAG BADGE", listX + 2, y + 1, accent);
            y += 14;

            float[] a = anim(b);
            a[0] += ((b.isEnabled() ? 1f : 0f) - a[0]) * k;
            rowBg(ctx, y, cw);
            text(ctx, tr, "Enable badge", listX + 8, y + 5, Theme.TEXT);
            pill(ctx, right - 28, y + 3, 28, 12, a[0], accent);
            hits.add(new Hit(listX, y, listX + cw, y + ROW_H - 2, true, () -> {
                b.toggle();
                ConfigManager.save();
            }));
            y += ROW_H;

            if (!b.isEnabled()) {
                for (String line : wrap(tr, "Off by default. While it is off no badge is drawn and nothing is sent to the cloud.", cw - 4)) {
                    text(ctx, tr, line, listX + 2, y + 2, Theme.MUTED);
                    y += 11;
                }
                y += 6;
            } else {
                for (Setting<?> s : b.settings()) {
                    if (s.visible()) {
                        y = drawSettingRow(ctx, tr, accent, s, y, cw, right);
                    }
                }
                rowBg(ctx, y, cw);
                text(ctx, tr, "Sync status", listX + 8, y + 5, Theme.TEXT);
                String st = CloudService.statusText();
                text(ctx, tr, fit(tr, st, cw / 2), right - Math.min(cw / 2, tr.getWidth(st)), y + 5, accent);
                y += ROW_H;

                if (b.cloudSyncOn()) {
                    rowBg(ctx, y, cw);
                    String who = CloudStore.signedIn() ? CloudStore.email() : "Not signed in";
                    text(ctx, tr, fit(tr, "Account: " + who, cw - 90), listX + 8, y + 5, Theme.TEXT);
                    if (CloudStore.signedIn()) {
                        button(ctx, tr, "Sync now", right, y + 2, true, CloudService::manualSync);
                    }
                    y += ROW_H;
                    if (!CloudStore.signedIn() || !CloudService.isLinked()) {
                        String hint = CloudStore.anonKey().isEmpty()
                                ? "Paste your Supabase publishable key into config/rkbclient-cloud.json as anonKey."
                                : !CloudStore.signedIn()
                                ? "Sign in with /rkb cloud login <email> <password>, then /rkb cloud link."
                                : "Run /rkb cloud link to prove you own this Minecraft account.";
                        for (String line : wrap(tr, hint, cw - 4)) {
                            text(ctx, tr, line, listX + 2, y + 2, Theme.MUTED);
                            y += 11;
                        }
                        y += 4;
                    }
                }
                rowBg(ctx, y, cw);
                text(ctx, tr, "Friends (" + FriendsManager.size() + ")", listX + 8, y + 5, Theme.TEXT);
                button(ctx, tr, "Manage", right, y + 2, true, () -> {
                    ConfigManager.save();
                    MinecraftClient.getInstance().setScreen(new FriendsScreen());
                });
                y += ROW_H;
            }
        }
        contentH = y + 6 - startY;
    }

    // ------------------------------------------------------------------ profiles page

    private void drawProfiles(DrawContext ctx, TextRenderer tr, int accent) {
        int cw = listW - 6;
        int right = listX + cw - 8;
        int startY = listY - Math.round(scroll);
        int y = startY;

        text(ctx, tr, "Profiles", listX + 2, y + 1, accent);
        y += 12;
        for (int slot = 1; slot <= ConfigManager.PROFILE_SLOTS; slot++) {
            final int s = slot;
            boolean exists = ConfigManager.profileExists(slot);
            rowBg(ctx, y, cw);
            text(ctx, tr, "Profile " + slot + (exists ? "" : " (empty)"), listX + 8, y + 5,
                    exists ? Theme.TEXT : Theme.MUTED);
            int saveX = button(ctx, tr, "Save", right, y + 2, true, () ->
                    showToast(ConfigManager.saveProfile(s) ? "Saved Profile " + s : "Could not save Profile " + s));
            button(ctx, tr, "Load", saveX - 6, y + 2, exists, () ->
                    showToast(ConfigManager.loadProfile(s) ? "Loaded Profile " + s : "Could not load Profile " + s));
            y += ROW_H;
        }
        y += 6;

        text(ctx, tr, "Tools", listX + 2, y + 1, accent);
        y += 12;
        rowBg(ctx, y, cw);
        text(ctx, tr, "HUD Editor", listX + 8, y + 5, Theme.TEXT);
        button(ctx, tr, "Open", right, y + 2, true, () -> {
            ConfigManager.save();
            MinecraftClient.getInstance().setScreen(new HudEditorScreen());
        });
        y += ROW_H;

        rowBg(ctx, y, cw);
        text(ctx, tr, "Interface and menu key", listX + 8, y + 5, Theme.TEXT);
        button(ctx, tr, "Open", right, y + 2, true, () -> openTab(PAGE_SETTINGS));
        y += ROW_H;

        rowBg(ctx, y, cw);
        text(ctx, tr, "Reset all modules", listX + 8, y + 5, Theme.TEXT);
        button(ctx, tr, confirmReset ? "Click again to confirm" : "Reset", right, y + 2, true, () -> {
            if (confirmReset) {
                ConfigManager.resetAll();
                confirmReset = false;
                showToast("All modules reset");
            } else {
                confirmReset = true;
            }
        });
        y += ROW_H + 6;

        for (String line : wrap(tr, "A profile stores every module state, setting and keybind. Loading one replaces "
                + "your current setup.", cw - 4)) {
            text(ctx, tr, line, listX + 2, y, Theme.MUTED);
            y += 11;
        }
        contentH = y + 4 - startY;
    }

    // ------------------------------------------------------------------ about page

    private void drawAbout(DrawContext ctx, TextRenderer tr) {
        int cw = listW - 6;
        int startY = listY - Math.round(scroll);
        int y = startY;
        String[] lines = {
                "RKB CLIENT " + VersionInfo.mod(),
                "Minecraft " + VersionInfo.minecraft() + "  |  Fabric Loader " + VersionInfo.loader(),
                "",
                "Menu key: " + KeyUtil.name(ModuleManager.get().ui().keyCode()) + " or the /rkb command.",
                "Every key is also listed in Options > Controls > RKB CLIENT.",
                "Settings: config/rkbclient.json. Profiles: config/rkbclient-profiles/.",
                "",
                "Original RKB design with a layout inspired by popular clients. Not affiliated with Feather Client, "
                        + "Mojang or Microsoft.",
                "",
                "Licence: MIT."
        };
        for (String l : lines) {
            if (l.isEmpty()) {
                y += 6;
                continue;
            }
            for (String w : wrap(tr, l, cw - 4)) {
                text(ctx, tr, w, listX + 2, y, w.startsWith("RKB CLIENT") ? Theme.TEXT : Theme.MUTED);
                y += 11;
            }
        }
        contentH = y + 4 - startY;
    }
}
