package com.rkb.client.gui;

import com.rkb.client.VersionInfo;
import com.rkb.client.config.ConfigManager;
import com.rkb.client.input.KeyUtil;
import com.rkb.client.input.KeybindRegistry;
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
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The RKB menu: category sidebar, module search, scrollable module list with inline settings and
 * keybind capture. Clicks and keys are read by polling GLFW state each frame, which keeps this
 * screen independent of Minecraft's input-event signatures and works with touch-to-mouse emulation
 * (drag the list to scroll).
 */
public class RkbMenuScreen extends Screen {
    private static final int HEADER_H = 26;
    private static final int ROW_H = 28;
    private static final int SUB_H = 16;

    private record Hit(int x1, int y1, int x2, int y2, boolean inList, Runnable action) {
        boolean contains(int x, int y) {
            return x >= x1 && x < x2 && y >= y1 && y < y2;
        }
    }

    private static Category lastCategory = Category.HUD;

    private final List<Hit> hits = new ArrayList<>();
    private final Set<String> expanded = new HashSet<>();

    private TextFieldWidget search;
    private Module listening;
    private float scroll;
    private int contentH;
    private boolean closing;

    private int px, py, pw, ph, sideW, listX, listY, listW, listH;

    private boolean mouseWasDown;
    private boolean dragging;
    private boolean pressedInList;
    private int pressY;
    private int lastY;

    public RkbMenuScreen() {
        super(Text.literal("RKB CLIENT"));
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        MinecraftClient mc = MinecraftClient.getInstance();
        String previous = search == null ? "" : search.getText();
        pw = Math.min(width - 12, 460);
        ph = Math.min(height - 12, 290);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        sideW = pw < 330 ? 66 : 94;
        listX = px + sideW + 6;
        listW = pw - sideW - 12;
        int top = py + HEADER_H + 5;
        listY = top + 21;
        listH = py + ph - 6 - listY;

        search = new TextFieldWidget(mc.textRenderer, listX, top, listW, 16, Text.literal("Search"));
        search.setMaxLength(32);
        search.setPlaceholder(Text.literal("Search modules..."));
        search.setChangedListener(s -> scroll = 0);
        search.setText(previous);
        addSelectableChild(search);

        mouseWasDown = KeyUtil.isMouseDown(mc, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    public boolean shouldCloseOnEsc() {
        return listening == null;
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

    // ------------------------------------------------------------------ input

    /** Event-driven keys: keybind capture, menu key to close, arrow scrolling. No GLFW polling. */
    @Override
    public boolean keyPressed(KeyInput input) {
        if (listening != null) {
            int k = input.key();
            if (k == GLFW.GLFW_KEY_BACKSPACE || k == GLFW.GLFW_KEY_DELETE) {
                listening.setKeyCode(KeyUtil.UNBOUND);
            } else if (!input.isEscape()) {
                listening.setKeyCode(k);
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

    private void handleInput(MinecraftClient mc, int mx, int my) {
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
        if (search.isMouseOver(mx, my)) {
            return;
        }
        search.setFocused(false);
        Module before = listening;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.contains(mx, my) && (!h.inList() || inList(mx, my))) {
                h.action().run();
                break;
            }
        }
        if (listening == before) {
            listening = null;
        }
    }

    // ------------------------------------------------------------------ drawing

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

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        MinecraftClient mc = MinecraftClient.getInstance();
        handleInput(mc, mx, my);
        if (closing) {
            return;
        }
        hits.clear();
        TextRenderer tr = mc.textRenderer;

        ctx.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, Theme.BORDER);
        ctx.fill(px, py, px + pw, py + ph, Theme.PANEL);

        drawHeader(ctx, tr, mc);
        drawSidebar(ctx, tr, mx, my);
        search.render(ctx, mx, my, delta);
        drawList(ctx, tr, mx, my);
    }

    private void drawHeader(DrawContext ctx, TextRenderer tr, MinecraftClient mc) {
        int accent = Theme.accent();
        ctx.fill(px, py, px + pw, py + HEADER_H, Theme.BLACK);
        ctx.fill(px, py + HEADER_H, px + pw, py + HEADER_H + 1, Theme.BORDER);
        // logo placeholder
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

    private void drawSidebar(DrawContext ctx, TextRenderer tr, int mx, int my) {
        int accent = Theme.accent();
        boolean searching = !search.getText().trim().isEmpty();
        ctx.fill(px, py + HEADER_H + 1, px + sideW, py + ph, 0xFF03050E);
        Category[] cats = Category.values();
        for (int i = 0; i < cats.length; i++) {
            Category cat = cats[i];
            int bx = px + 4;
            int by = py + HEADER_H + 8 + i * 22;
            int bw = sideW - 8;
            boolean selected = !searching && cat == lastCategory;
            boolean hover = mx >= bx && mx < bx + bw && my >= by && my < by + 18;
            if (selected) {
                ctx.fill(bx, by, bx + bw, by + 18, Theme.CARD);
                ctx.fill(bx, by, bx + 2, by + 18, accent);
            } else if (hover) {
                ctx.fill(bx, by, bx + bw, by + 18, Theme.CARD_HOVER);
            }
            text(ctx, tr, fit(tr, cat.label(), bw - 10), bx + 8, by + 5, selected ? Theme.TEXT : Theme.MUTED);
            hits.add(new Hit(bx, by, bx + bw, by + 18, false, () -> {
                lastCategory = cat;
                scroll = 0;
                search.setText("");
            }));
        }
        text(ctx, tr, fit(tr, "RKB " + VersionInfo.mod(), sideW - 10), px + 7, py + ph - 13, Theme.MUTED);
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

    private void drawList(DrawContext ctx, TextRenderer tr, int mx, int my) {
        int accent = Theme.accent();
        List<Module> mods = visibleModules();
        int cw = listW - 6;
        float maxScroll = Math.max(0, contentH - listH);
        scroll = Math.max(0, Math.min(maxScroll, scroll));

        ctx.enableScissor(listX, listY, listX + listW, listY + listH);
        int y = listY - Math.round(scroll);
        for (Module m : mods) {
            boolean open = expanded.contains(m.id());
            boolean on = m.isEnabled();
            boolean hover = inList(mx, my) && mx < listX + cw && my >= y && my < y + ROW_H;
            ctx.fill(listX, y, listX + cw, y + ROW_H, hover ? Theme.CARD_HOVER : Theme.CARD);
            ctx.fill(listX, y, listX + 2, y + ROW_H, on ? accent : Theme.OFF);

            int rightEdge = listX + cw - 8;
            if (m.isToggleable()) {
                int tw = 28;
                int th = 12;
                int tx = rightEdge - tw;
                int ty = y + (ROW_H - th) / 2;
                ctx.fill(tx, ty, tx + tw, ty + th, on ? accent : Theme.OFF);
                int knobX = on ? tx + tw - th + 1 : tx + 1;
                ctx.fill(knobX, ty + 1, knobX + th - 2, ty + th - 1, Theme.TEXT);
                rightEdge = tx - 6;
                hits.add(new Hit(listX, y, listX + cw, y + ROW_H, true, () -> {
                    m.toggle();
                    ConfigManager.save();
                }));
            } else {
                String label = "Always on";
                int lw = tr.getWidth(label);
                text(ctx, tr, label, rightEdge - lw, y + 10, Theme.MUTED);
                rightEdge -= lw + 6;
            }

            String editLabel = open ? "Hide" : "Edit";
            int ew = tr.getWidth(editLabel) + 10;
            int ex = rightEdge - ew;
            int ey = y + (ROW_H - 14) / 2;
            ctx.fill(ex, ey, ex + ew, ey + 14, open ? Theme.BORDER : Theme.CARD_INNER);
            text(ctx, tr, editLabel, ex + 5, ey + 3, open ? Theme.TEXT : Theme.MUTED);
            hits.add(new Hit(ex, ey, ex + ew, ey + 14, true, () -> {
                if (!expanded.remove(m.id())) {
                    expanded.add(m.id());
                }
            }));

            text(ctx, tr, fit(tr, m.name(), ex - listX - 14), listX + 8, y + 5, Theme.TEXT);
            text(ctx, tr, fit(tr, m.description(), ex - listX - 14), listX + 8, y + 16, Theme.MUTED);

            y += ROW_H;
            if (open) {
                y = drawSettings(ctx, tr, m, y, cw);
            }
            y += 4;
        }
        contentH = y + Math.round(scroll) - listY;
        ctx.disableScissor();

        if (mods.isEmpty()) {
            text(ctx, tr, "No modules found.", listX + 8, listY + 8, Theme.MUTED);
        }
        if (contentH > listH) {
            int barH = Math.max(16, listH * listH / contentH);
            int barY = listY + (int) ((listH - barH) * (scroll / Math.max(1f, contentH - listH)));
            ctx.fill(listX + listW - 3, barY, listX + listW - 1, barY + barH, accent);
        }
    }

    private int drawSettings(DrawContext ctx, TextRenderer tr, Module m, int y, int cw) {
        int accent = Theme.accent();
        int rows = 1 + m.settings().size();
        int h = rows * SUB_H + 6;
        ctx.fill(listX, y, listX + cw, y + h, Theme.CARD_INNER);
        int right = listX + cw - 8;
        int yy = y + 3;

        // keybind row
        boolean capturing = listening == m;
        text(ctx, tr, "Keybind", listX + 8, yy + 4, Theme.MUTED);
        String keyLabel = capturing ? "Press a key..." : KeyUtil.name(m.keyCode());
        int kw = Math.max(46, tr.getWidth(keyLabel) + 10);
        int kx = right - kw;
        ctx.fill(kx, yy + 1, kx + kw, yy + SUB_H - 1, capturing ? accent : Theme.CARD);
        text(ctx, tr, keyLabel, kx + (kw - tr.getWidth(keyLabel)) / 2, yy + 4, capturing ? Theme.BLACK : Theme.TEXT);
        hits.add(new Hit(kx, yy + 1, kx + kw, yy + SUB_H - 1, true, () -> {
            if (listening == m) {
                listening = null;
            } else {
                listening = m;
            }
        }));
        yy += SUB_H;

        for (Setting<?> s : m.settings()) {
            text(ctx, tr, fit(tr, s.name(), right - listX - 110), listX + 8, yy + 4, Theme.TEXT);
            if (s instanceof BoolSetting b) {
                int tw = 24;
                int tx = right - tw;
                boolean on = b.on();
                ctx.fill(tx, yy + 2, tx + tw, yy + SUB_H - 2, on ? accent : Theme.OFF);
                int knobX = on ? tx + tw - 10 : tx + 1;
                ctx.fill(knobX, yy + 3, knobX + 9, yy + SUB_H - 3, Theme.TEXT);
                hits.add(new Hit(tx, yy, tx + tw, yy + SUB_H, true, () -> {
                    b.toggle();
                    ConfigManager.save();
                }));
            } else if (s instanceof IntSetting i) {
                int plusX = right - 14;
                int valX = plusX - 34;
                int minusX = valX - 14;
                ctx.fill(minusX, yy + 2, minusX + 14, yy + SUB_H - 2, Theme.CARD);
                text(ctx, tr, "-", minusX + 5, yy + 4, Theme.TEXT);
                ctx.fill(plusX, yy + 2, plusX + 14, yy + SUB_H - 2, Theme.CARD);
                text(ctx, tr, "+", plusX + 4, yy + 4, Theme.TEXT);
                String v = String.valueOf(i.value());
                text(ctx, tr, v, valX + (34 - tr.getWidth(v)) / 2, yy + 4, accent);
                hits.add(new Hit(minusX, yy, minusX + 14, yy + SUB_H, true, () -> {
                    i.step(-1);
                    ConfigManager.save();
                }));
                hits.add(new Hit(plusX, yy, plusX + 14, yy + SUB_H, true, () -> {
                    i.step(1);
                    ConfigManager.save();
                }));
            } else if (s instanceof ChoiceSetting c) {
                String v = c.get();
                int bw = Math.max(50, tr.getWidth(v) + 10);
                int bx = right - bw;
                ctx.fill(bx, yy + 1, bx + bw, yy + SUB_H - 1, Theme.CARD);
                text(ctx, tr, v, bx + (bw - tr.getWidth(v)) / 2, yy + 4, accent);
                hits.add(new Hit(bx, yy + 1, bx + bw, yy + SUB_H - 1, true, () -> {
                    c.cycle();
                    ConfigManager.save();
                }));
            }
            yy += SUB_H;
        }
        return y + h;
    }
}
