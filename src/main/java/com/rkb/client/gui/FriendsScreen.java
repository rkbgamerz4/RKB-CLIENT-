package com.rkb.client.gui;

import com.rkb.client.cloud.CloudService;
import com.rkb.client.cloud.FriendsManager;
import com.rkb.client.input.KeyUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Name tag badge friends: add by Minecraft username or UUID, remove, and see the list and cloud status. */
public class FriendsScreen extends Screen {
    private static final int ROW_H = 22;

    private record Hit(int x1, int y1, int x2, int y2, Runnable action) {
        boolean contains(int x, int y) {
            return x >= x1 && x < x2 && y >= y1 && y < y2;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private TextFieldWidget input;
    private float scroll;
    private int contentH;
    private String message = "Type a username or UUID, then press Add.";
    private int px, py, pw, ph, listY, listH;
    private boolean mouseWasDown;
    private boolean dragging;
    private boolean pressedInList;
    private int pressY;
    private int lastY;

    public FriendsScreen() {
        super(Text.literal("RKB Friends"));
    }

    @Override
    protected void init() {
        MinecraftClient mc = MinecraftClient.getInstance();
        String previous = input == null ? "" : input.getText();
        pw = Math.min(width - 12, 440);
        ph = Math.min(height - 12, 280);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        listY = py + 76;
        listH = py + ph - 8 - listY;

        input = new TextFieldWidget(mc.textRenderer, px + 8, py + 46, pw - 16 - 46, 16, Text.literal("Player"));
        input.setMaxLength(40);
        input.setPlaceholder(Text.literal("Username or UUID"));
        input.setText(previous);
        addSelectableChild(input);
        mouseWasDown = KeyUtil.isMouseDown(mc, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    public boolean shouldPause() {
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll -= (float) (verticalAmount * 18.0);
        return true;
    }

    private void say(String text) {
        message = text;
    }

    private void addFromInput() {
        String text = input.getText();
        input.setText("");
        FriendsManager.addByInput(text, this::say);
    }

    private boolean inList(int mx, int my) {
        return mx >= px + 8 && mx < px + pw - 8 && my >= listY && my < listY + listH;
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
        if (input.isMouseOver(mx, my)) {
            return;
        }
        input.setFocused(false);
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.contains(mx, my)) {
                h.action().run();
                return;
            }
        }
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
        ctx.drawText(tr, "Badge friends", px + 8, py + 7, accent, false);
        int backW = tr.getWidth("Back") + 12;
        int backX = px + pw - 8 - backW;
        ctx.fill(backX, py + 4, backX + backW, py + 18, Theme.CARD);
        ctx.drawText(tr, "Back", backX + 6, py + 8, Theme.TEXT, false);
        hits.add(new Hit(backX, py + 4, backX + backW, py + 18, () -> MinecraftClient.getInstance().setScreen(new RkbMenuScreen())));

        ctx.drawText(tr, "Cloud: " + CloudService.statusText(), px + 8, py + 28, Theme.MUTED, false);
        input.render(ctx, mx, my, delta);
        int addX = px + pw - 8 - 42;
        ctx.fill(addX, py + 46, addX + 42, py + 62, accent);
        ctx.drawText(tr, "Add", addX + (42 - tr.getWidth("Add")) / 2, py + 50, Theme.BLACK, false);
        hits.add(new Hit(addX, py + 46, addX + 42, py + 62, this::addFromInput));
        ctx.drawText(tr, fit(tr, message, pw - 16), px + 8, py + 64, Theme.TEXT, false);

        List<FriendsManager.Friend> list = FriendsManager.list();
        contentH = list.size() * ROW_H;
        scroll = Math.max(0, Math.min(Math.max(0, contentH - listH), scroll));
        int y = listY - Math.round(scroll);
        ctx.enableScissor(px + 8, listY, px + pw - 8, listY + listH);
        for (int i = 0; i < list.size(); i++) {
            FriendsManager.Friend f = list.get(i);
            if (y + ROW_H >= listY && y <= listY + listH) {
                ctx.fill(px + 8, y, px + pw - 14, y + ROW_H - 2, Theme.CARD);
                ctx.fill(px + 8, y, px + 11, y + ROW_H - 2, accent);
                ctx.drawText(tr, fit(tr, FriendsManager.displayName(f), pw - 130), px + 16, y + 3, Theme.TEXT, false);
                ctx.drawText(tr, f.uuid().toString().substring(0, 13) + "...", px + 16, y + 12, Theme.MUTED, false);
                int rw = tr.getWidth("Remove") + 10;
                int rx = px + pw - 14 - 4 - rw;
                ctx.fill(rx, y + 3, rx + rw, y + 17, Theme.BORDER);
                ctx.drawText(tr, "Remove", rx + 5, y + 6, Theme.TEXT, false);
                final FriendsManager.Friend target = f;
                hits.add(new Hit(rx, y + 3, rx + rw, y + 17, () -> {
                    FriendsManager.remove(target.uuid());
                    say("Removed " + FriendsManager.displayName(target) + ".");
                }));
            }
            y += ROW_H;
        }
        ctx.disableScissor();
        if (list.isEmpty()) {
            ctx.drawText(tr, "No friends yet.", px + 10, listY + 4, Theme.MUTED, false);
        }
    }

    private static String fit(TextRenderer tr, String s, int maxW) {
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
