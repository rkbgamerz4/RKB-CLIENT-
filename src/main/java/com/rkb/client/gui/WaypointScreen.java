package com.rkb.client.gui;

import com.rkb.client.input.KeyUtil;
import com.rkb.client.waypoint.Waypoint;
import com.rkb.client.waypoint.WaypointStore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Waypoint list for the current server/world: add by name and coordinates, track, hide, delete. */
public class WaypointScreen extends Screen {
    private static final int ROW_H = 28;

    private record Hit(int x1, int y1, int x2, int y2, boolean inList, Runnable action) {
        boolean contains(int x, int y) {
            return x >= x1 && x < x2 && y >= y1 && y < y2;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private TextFieldWidget nameField;
    private TextFieldWidget xField;
    private TextFieldWidget yField;
    private TextFieldWidget zField;

    private String worldKey = "unknown";
    private String dimKey = "minecraft:overworld";
    private float scroll;
    private int contentH;
    private String toast = "";
    private long toastUntil;

    private int px, py, pw, ph, listX, listY, listW, listH;
    private boolean mouseWasDown;
    private boolean dragging;
    private boolean pressedInList;
    private int pressY;
    private int lastY;

    public WaypointScreen() {
        super(Text.literal("RKB Waypoints"));
    }

    @Override
    protected void init() {
        MinecraftClient mc = MinecraftClient.getInstance();
        worldKey = WaypointStore.worldKey(mc);
        dimKey = WaypointStore.dimKey(mc);
        pw = Math.min(width - 12, 440);
        ph = Math.min(height - 12, 280);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        listX = px + 8;
        listW = pw - 16;
        int formY = py + 28;
        listY = formY + 22;
        listH = py + ph - 8 - listY;

        String name = nameField == null ? "" : nameField.getText();
        String xs = xField == null || xField.getText().isEmpty()
                ? (mc.player == null ? "0" : String.valueOf(mc.player.getBlockX())) : xField.getText();
        String ys = yField == null || yField.getText().isEmpty()
                ? (mc.player == null ? "64" : String.valueOf(mc.player.getBlockY())) : yField.getText();
        String zs = zField == null || zField.getText().isEmpty()
                ? (mc.player == null ? "0" : String.valueOf(mc.player.getBlockZ())) : zField.getText();

        int addW = 36;
        int gap = 4;
        int numW = Math.max(30, (listW - addW - gap * 4) / 4);
        int nameW = listW - addW - numW * 3 - gap * 4;
        int fx = listX;
        nameField = field(mc, fx, formY, nameW, "Name", name, false);
        fx += nameW + gap;
        xField = field(mc, fx, formY, numW, "X", xs, true);
        fx += numW + gap;
        yField = field(mc, fx, formY, numW, "Y", ys, true);
        fx += numW + gap;
        zField = field(mc, fx, formY, numW, "Z", zs, true);

        mouseWasDown = KeyUtil.isMouseDown(mc, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private TextFieldWidget field(MinecraftClient mc, int x, int y, int w, String label, String value, boolean numeric) {
        TextFieldWidget f = new TextFieldWidget(mc.textRenderer, x, y, w, 16, Text.literal(label));
        f.setMaxLength(numeric ? 9 : 24);
        if (numeric) {
            f.setTextPredicate(s -> s.matches("-?\\d*"));
        }
        f.setPlaceholder(Text.literal(label));
        f.setText(value);
        addSelectableChild(f);
        return f;
    }

    public boolean shouldPause() {
        return false;
    }

    public void removed() {
        WaypointStore.save();
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll -= (float) (verticalAmount * 18.0);
        return true;
    }

    private void toast(String s) {
        toast = s;
        toastUntil = System.currentTimeMillis() + 2500;
    }

    private static int parse(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void addFromForm() {
        MinecraftClient mc = MinecraftClient.getInstance();
        Waypoint w = WaypointStore.add(mc, nameField.getText(), parse(xField.getText(), 0),
                parse(yField.getText(), 64), parse(zField.getText(), 0));
        nameField.setText("");
        toast("Added " + w.name);
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
        if (nameField.isMouseOver(mx, my) || xField.isMouseOver(mx, my)
                || yField.isMouseOver(mx, my) || zField.isMouseOver(mx, my)) {
            return;
        }
        nameField.setFocused(false);
        xField.setFocused(false);
        yField.setFocused(false);
        zField.setFocused(false);
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.contains(mx, my) && (!h.inList() || inList(mx, my))) {
                h.action().run();
                return;
            }
        }
    }

    private int button(DrawContext c, TextRenderer tr, String label, int right, int y, boolean inList, Runnable action) {
        int w = tr.getWidth(label) + 10;
        int x = right - w;
        c.fill(x, y, right, y + 13, Theme.BORDER);
        c.drawText(tr, label, x + 5, y + 3, Theme.TEXT, false);
        hits.add(new Hit(x, y, right, y + 13, inList, action));
        return x;
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        MinecraftClient mc = MinecraftClient.getInstance();
        handleMouse(mc, mx, my);
        hits.clear();
        TextRenderer tr = mc.textRenderer;
        int accent = Theme.accent();

        ctx.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, Theme.BORDER);
        ctx.fill(px, py, px + pw, py + ph, Theme.PANEL);
        ctx.fill(px, py, px + pw, py + 22, Theme.BLACK);
        ctx.drawText(tr, "Waypoints", px + 8, py + 7, accent, false);
        String where = dimKey.replace("minecraft:", "");
        ctx.drawText(tr, where, px + 8 + tr.getWidth("Waypoints") + 10, py + 7, Theme.MUTED, false);
        int backW = tr.getWidth("Back") + 12;
        int backX = px + pw - 8 - backW;
        ctx.fill(backX, py + 4, backX + backW, py + 18, Theme.CARD);
        ctx.drawText(tr, "Back", backX + 6, py + 8, Theme.TEXT, false);
        hits.add(new Hit(backX, py + 4, backX + backW, py + 18, false, () -> {
            WaypointStore.save();
            MinecraftClient.getInstance().setScreen(new RkbMenuScreen());
        }));

        nameField.render(ctx, mx, my, delta);
        xField.render(ctx, mx, my, delta);
        yField.render(ctx, mx, my, delta);
        zField.render(ctx, mx, my, delta);
        int addW = 36;
        int addX = px + pw - 8 - addW;
        int formY = py + 28;
        ctx.fill(addX, formY, addX + addW, formY + 16, accent);
        ctx.drawText(tr, "Add", addX + (addW - tr.getWidth("Add")) / 2, formY + 4, Theme.BLACK, false);
        hits.add(new Hit(addX, formY, addX + addW, formY + 16, false, this::addFromForm));

        List<Waypoint> list = WaypointStore.all(worldKey);
        String tracked = WaypointStore.tracked(worldKey);
        int maxScroll = Math.max(0, contentH - listH);
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        int y = listY - Math.round(scroll);
        int right = listX + listW - 6;

        ctx.enableScissor(listX, listY, listX + listW, listY + listH);
        for (int i = 0; i < list.size(); i++) {
            Waypoint w = list.get(i);
            if (y + ROW_H >= listY && y <= listY + listH) {
                boolean here = w.dim.equals(dimKey);
                boolean isTracked = w.name.equals(tracked);
                ctx.fill(listX, y, listX + listW - 6, y + ROW_H - 2, Theme.CARD);
                int color = WaypointStore.COLORS[w.color % WaypointStore.COLORS.length];
                ctx.fill(listX, y, listX + 3, y + ROW_H - 2, w.visible ? color : Theme.OFF);

                String title = w.name;
                if (here && mc.player != null) {
                    int d = (int) Math.round(Math.hypot(w.x + 0.5 - mc.player.getX(), w.z + 0.5 - mc.player.getZ()));
                    title += "  " + d + "m";
                }
                int bx = right;
                bx = button(ctx, tr, "Del", bx - 4, y + 3, true, () -> {
                    WaypointStore.remove(worldKey, w);
                    toast("Deleted " + w.name);
                });
                bx = button(ctx, tr, w.visible ? "Hide" : "Show", bx - 4, y + 3, true, () -> {
                    w.visible = !w.visible;
                    WaypointStore.save();
                });
                bx = button(ctx, tr, isTracked ? "Untrack" : "Track", bx - 4, y + 3, true,
                        () -> WaypointStore.setTracked(worldKey, isTracked ? "" : w.name));

                int textMax = bx - listX - 14;
                ctx.drawText(tr, fit(tr, title, textMax), listX + 8, y + 5, isTracked ? accent : Theme.TEXT, false);
                String sub = w.x + " " + w.y + " " + w.z + (here ? "" : "  (" + w.dim.replace("minecraft:", "") + ")");
                ctx.drawText(tr, fit(tr, sub, textMax), listX + 8, y + 16, Theme.MUTED, false);
            }
            y += ROW_H;
        }
        ctx.disableScissor();
        contentH = list.size() * ROW_H;
        if (list.isEmpty()) {
            ctx.drawText(tr, "No waypoints yet. Fill the form and press Add.", listX + 2, listY + 4, Theme.MUTED, false);
        }
        if (contentH > listH) {
            int barH = Math.max(16, listH * listH / contentH);
            int barPos = listY + (int) ((listH - barH) * (scroll / Math.max(1f, contentH - listH)));
            ctx.fill(listX + listW - 3, barPos, listX + listW - 1, barPos + barH, accent);
        }
        if (System.currentTimeMillis() < toastUntil) {
            int tw = tr.getWidth(toast) + 16;
            int tx = px + (pw - tw) / 2;
            int ty = py + ph - 24;
            ctx.fill(tx, ty, tx + tw, ty + 16, Theme.BLACK);
            ctx.drawText(tr, toast, tx + 8, ty + 4, Theme.TEXT, false);
        }
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
}
