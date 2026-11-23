package com.rkb.client.gui;

import com.rkb.client.input.KeyUtil;
import com.rkb.client.waypoint.Waypoint;
import com.rkb.client.waypoint.WaypointStore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Waypoint list for the current server/world: add by name and coordinates, track, hide, delete. */
public class WaypointScreen extends Screen {
    private static final int ROW_H = 34;

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

    private RkbLayout.TopBar chrome;
    private int contentX, contentY, contentW, contentHBox, innerX, innerW;
    private int formY, listX, listY, listW, listH;
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

        chrome = RkbLayout.topBar(width, height);
        contentX = 8;
        contentW = width - 16;
        contentY = chrome.contentTop();
        contentHBox = height - contentY - 14;
        innerX = contentX + 12;
        innerW = contentW - 24;
        formY = contentY + 48;
        listX = innerX;
        listW = innerW;
        listY = formY + 36;
        listH = Math.max(20, contentY + contentHBox - 12 - listY);

        String name = nameField == null ? "" : nameField.getText();
        String xs = xField == null || xField.getText().isEmpty()
                ? (mc.player == null ? "0" : String.valueOf(mc.player.getBlockX())) : xField.getText();
        String ys = yField == null || yField.getText().isEmpty()
                ? (mc.player == null ? "64" : String.valueOf(mc.player.getBlockY())) : yField.getText();
        String zs = zField == null || zField.getText().isEmpty()
                ? (mc.player == null ? "0" : String.valueOf(mc.player.getBlockZ())) : zField.getText();

        int addW = 48;
        int gap = 6;
        int numW = Math.max(34, (innerW - addW - gap * 4) / 4);
        int nameW = innerW - addW - numW * 3 - gap * 4;
        int fx = innerX;
        nameField = field(mc, fx + 7, formY + 7, nameW - 14, "Name", name, false);
        fx += nameW + gap;
        xField = field(mc, fx + 7, formY + 7, numW - 14, "X", xs, true);
        fx += numW + gap;
        yField = field(mc, fx + 7, formY + 7, numW - 14, "Y", ys, true);
        fx += numW + gap;
        zField = field(mc, fx + 7, formY + 7, numW - 14, "Z", zs, true);

        mouseWasDown = KeyUtil.isMouseDown(mc, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private TextFieldWidget field(MinecraftClient mc, int x, int y, int w, String label, String value, boolean numeric) {
        TextFieldWidget f = new TextFieldWidget(mc.textRenderer, x, y, w, 12, Text.literal(label));
        f.setMaxLength(numeric ? 9 : 24);
        if (numeric) {
            f.setTextPredicate(s -> s.matches("-?\\d*"));
        }
        f.setPlaceholder(Text.literal(label));
        f.setText(value);
        f.setDrawsBackground(false);
        addSelectableChild(f);
        return f;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        WaypointStore.save();
    }

    @Override
    public void close() {
        WaypointStore.save();
        RkbScreens.close();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll -= (float) (verticalAmount * 20.0);
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
        TextFieldWidget field = fieldAt(mx, my);
        if (field != null) {
            clearFocus();
            field.setFocused(true);
            field.mouseClicked(new Click(mx, my, new MouseInput(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0)), false);
            return;
        }
        clearFocus();
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.contains(mx, my) && (!h.inList() || inList(mx, my))) {
                h.action().run();
                return;
            }
        }
    }

    private TextFieldWidget fieldAt(int mx, int my) {
        for (TextFieldWidget f : new TextFieldWidget[]{nameField, xField, yField, zField}) {
            if (f != null && f.isMouseOver(mx, my)) {
                return f;
            }
        }
        return null;
    }

    private void clearFocus() {
        for (TextFieldWidget f : new TextFieldWidget[]{nameField, xField, yField, zField}) {
            if (f != null) {
                f.setFocused(false);
            }
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        for (TextFieldWidget f : new TextFieldWidget[]{nameField, xField, yField, zField}) {
            if (f != null && f.isFocused() && f.keyPressed(input)) {
                return true;
            }
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        for (TextFieldWidget f : new TextFieldWidget[]{nameField, xField, yField, zField}) {
            if (f != null && f.isFocused() && f.charTyped(input)) {
                return true;
            }
        }
        return super.charTyped(input);
    }

    private int button(DrawContext c, TextRenderer tr, String label, int right, int y, boolean inList, Runnable action) {
        int w = Math.max(36, tr.getWidth(label) + 14);
        int x = right - w;
        RkbUi.button(c, tr, new RkbUi.Rect(x, y, w, 18), label, false, false, true);
        hits.add(new Hit(x, y, right, y + 18, inList, action));
        return x;
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        MinecraftClient mc = MinecraftClient.getInstance();
        handleMouse(mc, mx, my);
        hits.clear();
        TextRenderer tr = mc.textRenderer;
        int accent = Theme.chromeAccent();

        RkbUi.background(ctx, width, height, System.currentTimeMillis());
        RkbUi.panel(ctx, new RkbUi.Rect(contentX, contentY, contentW, contentHBox), 0xE60E1422, RkbUi.LINE);
        ctx.drawText(tr, "Waypoints", innerX, contentY + 12, RkbUi.TEXT, false);
        String where = "Current world: " + dimKey.replace("minecraft:", "");
        ctx.drawText(tr, RkbUi.fit(tr, where, innerW - 120), innerX, contentY + 26, RkbUi.MUTED, false);

        int addW = 48;
        int gap = 6;
        int numW = Math.max(34, (innerW - addW - gap * 4) / 4);
        int nameW = innerW - addW - numW * 3 - gap * 4;
        int fx = innerX;
        drawFieldBox(ctx, nameField, fx, formY, nameW, "Name", accent);
        fx += nameW + gap;
        drawFieldBox(ctx, xField, fx, formY, numW, "X", accent);
        fx += numW + gap;
        drawFieldBox(ctx, yField, fx, formY, numW, "Y", accent);
        fx += numW + gap;
        drawFieldBox(ctx, zField, fx, formY, numW, "Z", accent);

        RkbUi.Rect add = new RkbUi.Rect(fx + numW + gap, formY, addW, 20);
        RkbUi.button(ctx, tr, add, "Add", add.contains(mx, my), true, true);
        hits.add(new Hit(add.x(), add.y(), add.right(), add.bottom(), false, this::addFromForm));

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
                RkbUi.roundedFill(ctx, new RkbUi.Rect(listX, y, listW - 6, ROW_H - 4),
                        isTracked ? 0xFF152438 : RkbUi.SURFACE);
                int color = WaypointStore.COLORS[w.color % WaypointStore.COLORS.length];
                ctx.fill(listX + 2, y + 4, listX + 4, y + ROW_H - 8, w.visible ? color : 0xFF26324A);

                String title = w.name;
                if (here && mc.player != null) {
                    int d = (int) Math.round(Math.hypot(w.x + 0.5 - mc.player.getX(), w.z + 0.5 - mc.player.getZ()));
                    title += "  ·  " + d + "m";
                }
                int bx = right;
                bx = button(ctx, tr, "Del", bx - 4, y + 8, true, () -> {
                    WaypointStore.remove(worldKey, w);
                    toast("Deleted " + w.name);
                });
                bx = button(ctx, tr, w.visible ? "Hide" : "Show", bx - 4, y + 8, true, () -> {
                    w.visible = !w.visible;
                    WaypointStore.save();
                });
                bx = button(ctx, tr, isTracked ? "Untrack" : "Track", bx - 4, y + 8, true,
                        () -> WaypointStore.setTracked(worldKey, isTracked ? "" : w.name));

                int textMax = bx - listX - 16;
                ctx.drawText(tr, RkbUi.fit(tr, title, Math.max(20, textMax)), listX + 10, y + 7,
                        isTracked ? accent : RkbUi.TEXT, false);
                String sub = w.x + " " + w.y + " " + w.z
                        + (here ? "" : "  (" + w.dim.replace("minecraft:", "") + ")");
                ctx.drawText(tr, RkbUi.fit(tr, sub, Math.max(20, textMax)), listX + 10, y + 19,
                        RkbUi.MUTED, false);
            }
            y += ROW_H;
        }
        ctx.disableScissor();
        contentH = list.size() * ROW_H;
        if (list.isEmpty()) {
            ctx.drawText(tr, "No waypoints yet. Fill the form and press Add.", listX + 4, listY + 8, RkbUi.MUTED, false);
        }
        if (contentH > listH) {
            int barH = Math.max(16, listH * listH / contentH);
            int barPos = listY + (int) ((listH - barH) * (scroll / Math.max(1f, contentH - listH)));
            RkbUi.roundedFill(ctx, new RkbUi.Rect(listX + listW - 3, barPos, 3, barH), accent);
        }
        if (System.currentTimeMillis() < toastUntil) {
            int tw = tr.getWidth(toast) + 22;
            int tx = contentX + (contentW - tw) / 2;
            int ty = contentY + contentHBox - 26;
            RkbUi.panel(ctx, new RkbUi.Rect(tx, ty, tw, 20), 0xF00E1422, accent);
            ctx.drawText(tr, toast, tx + 11, ty + 6, RkbUi.TEXT, false);
        }

        RkbChrome.draw(ctx, tr, chrome, mx, my, 4, (r, action) -> hits.add(new Hit(r.x(), r.y(), r.right(),
                r.bottom(), false, action)));
    }

    private void drawFieldBox(DrawContext ctx, TextFieldWidget field, int x, int y, int w, String label, int accent) {
        RkbUi.Rect box = new RkbUi.Rect(x, y, w, 20);
        RkbUi.panel(ctx, box, field.isFocused() ? 0xFF101A2C : RkbUi.SURFACE_3,
                field.isFocused() ? accent : RkbUi.LINE);
        field.setX(x + 7);
        field.setY(y + 7);
        field.setWidth(Math.max(10, w - 14));
        field.render(ctx, 0, 0, 0);
        if (field.getText().isEmpty()) {
            ctx.drawText(textRenderer, label, x + 7, y + 6, RkbUi.MUTED, false);
        }
    }
}
