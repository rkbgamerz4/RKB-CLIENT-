package com.rkb.client.gui;

import com.rkb.client.VersionInfo;
import com.rkb.client.config.ConfigManager;
import com.rkb.client.input.KeyUtil;
import com.rkb.client.input.KeybindRegistry;
import com.rkb.client.module.Category;
import com.rkb.client.module.KeyAction;
import com.rkb.client.module.Module;
import com.rkb.client.module.ModuleManager;
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
 * RKB menu: category sidebar, searchable module cards with animated toggles, a settings page per
 * module (keybind, options, reset), Configuration (profiles, tools) and About pages.
 * Mouse clicks are read with one guarded GLFW call per frame so the screen works with touch-to-mouse
 * emulation (drag the list to scroll); keyboard input is event-driven.
 */
public class RkbMenuScreen extends Screen {
    private static final int HEADER_H = 26;
    private static final int CARD_H = 44;
    private static final int GAP = 6;
    private static final int ROW_H = 20;
    private static final int SIDE_BG = 0xFF03050E;

    private record Hit(int x1, int y1, int x2, int y2, boolean inList, Runnable action) {
        boolean contains(int x, int y) {
            return x >= x1 && x < x2 && y >= y1 && y < y2;
        }
    }

    private static Category lastCategory = Category.HUD;

    private final List<Hit> hits = new ArrayList<>();
    private final Map<Module, float[]> anims = new HashMap<>();
    private final float[] sideAnim = new float[Category.values().length];
    private final List<Category> sideCats = new ArrayList<>();

    private TextFieldWidget search;
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

    private int px, py, pw, ph, sideW, barY, listX, listY, listW, listH;

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
        String previous = search == null ? "" : search.getText();
        pw = Math.min(width - 12, 470);
        ph = Math.min(height - 12, 300);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        sideW = pw < 340 ? 74 : 100;
        listX = px + sideW + 6;
        listW = pw - sideW - 12;
        barY = py + HEADER_H + 5;
        listY = barY + 21;
        listH = py + ph - 6 - listY;

        sideCats.clear();
        for (Category c : Category.values()) {
            if (c.isPage() || !ModuleManager.get().inCategory(c).isEmpty()) {
                sideCats.add(c);
            }
        }
        if (!sideCats.contains(lastCategory)) {
            lastCategory = sideCats.get(0);
        }

        search = new TextFieldWidget(mc.textRenderer, listX, barY, listW, 16, Text.literal("Search"));
        search.setMaxLength(32);
        search.setPlaceholder(Text.literal("Search modules..."));
        search.setChangedListener(s -> scroll = 0);
        search.setText(previous);
        addSelectableChild(search);

        mouseWasDown = KeyUtil.isMouseDown(mc, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    public boolean shouldCloseOnEsc() {
        return listening == null && detail == null;
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

    private void selectCategory(Category c) {
        lastCategory = c;
        detail = null;
        listening = null;
        scroll = 0;
        pageAnim = 0f;
        confirmReset = false;
        search.setText("");
    }

    private void openDetail(Module m) {
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

    // ------------------------------------------------------------------ input

    /** Event-driven keys: keybind capture, menu key to close, Esc to go back, arrow scrolling. */
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
            if (input.isEscape() && detail != null) {
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

    /** Small button, right edge at 'right'. Returns its left x. */
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

        ctx.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, Theme.BORDER);
        ctx.fill(px, py, px + pw, py + ph, Theme.PANEL);
        drawHeader(ctx, tr, mc, accent);
        drawSidebar(ctx, tr, mx, my, accent);

        boolean page = lastCategory.isPage();
        boolean showSearch = detail == null && !page;
        search.visible = showSearch;
        if (!showSearch && search.isFocused()) {
            search.setFocused(false);
        }
        if (showSearch) {
            search.render(ctx, mx, my, delta);
        } else if (detail != null) {
            drawBackBar(ctx, tr, accent);
        } else {
            text(ctx, tr, lastCategory.label(), listX, barY + 4, accent);
        }

        int maxScroll = Math.max(0, contentH - listH);
        scroll = Math.max(0, Math.min(maxScroll, scroll));

        ctx.enableScissor(listX, listY, listX + listW, listY + listH);
        if (detail != null) {
            drawDetail(ctx, tr, accent);
        } else if (lastCategory == Category.CONFIGURATION) {
            drawConfig(ctx, tr, accent);
        } else if (lastCategory == Category.ABOUT) {
            drawAbout(ctx, tr);
        } else {
            drawGrid(ctx, tr, mx, my, accent);
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
        if (System.currentTimeMillis() < toastUntil) {
            int tw = tr.getWidth(toast) + 16;
            int tx = listX + (listW - tw) / 2;
            int ty = listY + listH - 20;
            ctx.fill(tx, ty, tx + tw, ty + 16, Theme.BLACK);
            outline(ctx, tx, ty, tw, 16, accent);
            text(ctx, tr, toast, tx + 8, ty + 4, Theme.TEXT);
        }
    }

    private void drawHeader(DrawContext ctx, TextRenderer tr, MinecraftClient mc, int accent) {
        ctx.fill(px, py, px + pw, py + HEADER_H, Theme.BLACK);
        ctx.fill(px, py + HEADER_H, px + pw, py + HEADER_H + 1, Theme.BORDER);
        ctx.fill(px + 6, py + 5, px + 22, py + 21, accent);
        text(ctx, tr, "R", px + 12, py + 9, Theme.BLACK);
        text(ctx, tr, "RKB", px + 28, py + 9, accent);
        text(ctx, tr, "CLIENT", px + 28 + tr.getWidth("RKB "), py + 9, Theme.TEXT);

        int right = px + pw - 8;
        if (Theme.menuShowVersion()) {
            String v = "MC " + VersionInfo.minecraft();
            right -= tr.getWidth(v);
            text(ctx, tr, v, right, py + 9, Theme.MUTED);
            right -= 12;
        }
        if (Theme.menuShowFps()) {
            String f = "FPS " + mc.getCurrentFps();
            right -= tr.getWidth(f);
            text(ctx, tr, f, right, py + 9, accent);
        }
    }

    private void drawSidebar(DrawContext ctx, TextRenderer tr, int mx, int my, int accent) {
        ctx.fill(px, py + HEADER_H + 1, px + sideW, py + ph, SIDE_BG);
        int count = Math.max(1, sideCats.size());
        int step = Math.max(14, Math.min(22, (ph - HEADER_H - 8 - 40) / count));
        for (int i = 0; i < sideCats.size(); i++) {
            Category cat = sideCats.get(i);
            int bx = px + 4;
            int by = py + HEADER_H + 6 + i * step;
            int bw = sideW - 8;
            int bh = step - 2;
            boolean selected = cat == lastCategory;
            boolean hover = mx >= bx && mx < bx + bw && my >= by && my < by + bh;
            int idx = cat.ordinal();
            sideAnim[idx] += ((selected ? 1f : hover ? 0.5f : 0f) - sideAnim[idx]) * k;
            float a = sideAnim[idx];
            ctx.fill(bx, by, bx + bw, by + bh, lerp(SIDE_BG, Theme.CARD, a));
            ctx.fill(bx, by, bx + 2, by + bh, lerp(SIDE_BG, accent, a));
            text(ctx, tr, fit(tr, cat.label(), bw - 10), bx + 8, by + (bh - 8) / 2, lerp(Theme.MUTED, Theme.TEXT, a));
            hits.add(new Hit(bx, by, bx + bw, by + bh, false, () -> selectCategory(cat)));
        }
        int fy = py + ph - 32;
        int fx = px + 4;
        int fw = sideW - 8;
        boolean fh = mx >= fx && mx < fx + fw && my >= fy && my < fy + 14;
        ctx.fill(fx, fy, fx + fw, fy + 14, fh ? Theme.CARD_HOVER : Theme.CARD);
        outline(ctx, fx, fy, fw, 14, accent);
        String label = "HUD Editor";
        text(ctx, tr, fit(tr, label, fw - 6), fx + (fw - Math.min(fw - 6, tr.getWidth(label))) / 2, fy + 3, Theme.TEXT);
        hits.add(new Hit(fx, fy, fx + fw, fy + 14, false, () -> {
            ConfigManager.save();
            MinecraftClient.getInstance().setScreen(new HudEditorScreen());
        }));
        text(ctx, tr, fit(tr, "RKB " + VersionInfo.mod(), sideW - 10), px + 7, py + ph - 13, Theme.MUTED);
    }

    private void drawBackBar(DrawContext ctx, TextRenderer tr, int accent) {
        String back = "< Back";
        int bw = tr.getWidth(back) + 12;
        ctx.fill(listX, barY, listX + bw, barY + 16, Theme.CARD);
        outline(ctx, listX, barY, bw, 16, accent);
        text(ctx, tr, back, listX + 6, barY + 4, Theme.TEXT);
        hits.add(new Hit(listX, barY, listX + bw, barY + 16, false, this::closeDetail));
        text(ctx, tr, fit(tr, detail.name(), listW - bw - 12), listX + bw + 8, barY + 4, Theme.TEXT);
    }

    private List<Module> visibleModules() {
        String q = search.getText().trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return ModuleManager.get().inCategory(lastCategory);
        }
        List<Module> out = new ArrayList<>();
        for (Module m : ModuleManager.get().all()) {
            if (m.name().toLowerCase(Locale.ROOT).contains(q)
                    || m.id().toLowerCase(Locale.ROOT).contains(q)
                    || m.description().toLowerCase(Locale.ROOT).contains(q)) {
                out.add(m);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ module grid

    private void drawGrid(DrawContext ctx, TextRenderer tr, int mx, int my, int accent) {
        List<Module> mods = visibleModules();
        int cols = listW >= 250 ? 2 : 1;
        int cardW = (listW - 6 - (cols - 1) * GAP) / cols;
        int originY = listY - Math.round(scroll);

        for (int i = 0; i < mods.size(); i++) {
            Module m = mods.get(i);
            int x = listX + (i % cols) * (cardW + GAP);
            int y = originY + (i / cols) * (CARD_H + GAP);
            if (y + CARD_H < listY || y > listY + listH) {
                continue;
            }
            float[] a = anim(m);
            boolean hover = inList(mx, my) && mx >= x && mx < x + cardW && my >= y && my < y + CARD_H;
            a[0] += ((m.isEnabled() ? 1f : 0f) - a[0]) * k;
            a[1] += ((hover ? 1f : 0f) - a[1]) * k;

            ctx.fill(x, y, x + cardW, y + CARD_H, lerp(Theme.CARD, Theme.CARD_HOVER, a[1]));
            ctx.fill(x, y, x + 2, y + CARD_H, lerp(Theme.OFF, accent, a[0]));
            text(ctx, tr, fit(tr, m.name(), cardW - 16), x + 8, y + 5, Theme.TEXT);
            text(ctx, tr, fit(tr, m.description(), cardW - 16), x + 8, y + 16, Theme.MUTED);

            int cy = y + CARD_H - 16;
            String gear = "Settings";
            int gw = tr.getWidth(gear) + 10;
            ctx.fill(x + 8, cy, x + 8 + gw, cy + 12, Theme.CARD_INNER);
            text(ctx, tr, gear, x + 13, cy + 2, Theme.MUTED);

            if (m.isToggleable()) {
                pill(ctx, x + cardW - 36, cy, 28, 12, a[0], accent);
                hits.add(new Hit(x, y, x + cardW, y + CARD_H, true, () -> {
                    m.toggle();
                    ConfigManager.save();
                }));
            } else {
                String s = "Always on";
                text(ctx, tr, s, x + cardW - 8 - tr.getWidth(s), cy + 2, Theme.MUTED);
                hits.add(new Hit(x, y, x + cardW, y + CARD_H, true, () -> openDetail(m)));
            }
            hits.add(new Hit(x + 8, cy, x + 8 + gw, cy + 12, true, () -> openDetail(m)));
        }
        int rows = (mods.size() + cols - 1) / cols;
        contentH = rows * (CARD_H + GAP);
        if (mods.isEmpty()) {
            text(ctx, tr, "No modules found.", listX + 2, listY + 4, Theme.MUTED);
        }
    }

    // ------------------------------------------------------------------ module settings page

    private void drawDetail(DrawContext ctx, TextRenderer tr, int accent) {
        Module m = detail;
        int cw = listW - 6;
        int right = listX + cw - 8;
        int startY = listY - Math.round(scroll);
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

        for (Setting<?> s : m.settings()) {
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
            y += ROW_H;
        }

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
        y += 22;
        contentH = y - startY;
    }

    // ------------------------------------------------------------------ configuration page

    private void drawConfig(DrawContext ctx, TextRenderer tr, int accent) {
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
            int saveX = button(ctx, tr, "Save", right, y + 2, true, () -> {
                showToast(ConfigManager.saveProfile(s) ? "Saved Profile " + s : "Could not save Profile " + s);
            });
            button(ctx, tr, "Load", saveX - 6, y + 2, exists, () -> {
                showToast(ConfigManager.loadProfile(s) ? "Loaded Profile " + s : "Could not load Profile " + s);
            });
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
        button(ctx, tr, "Open", right, y + 2, true, () -> openDetail(ModuleManager.get().ui()));
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
