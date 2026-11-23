package com.rkb.client.gui;

import com.rkb.client.cloud.CloudService;
import com.rkb.client.cloud.FriendsManager;
import com.rkb.client.input.KeyUtil;
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

/** Name tag badge friends: add by Minecraft username or UUID, remove, and see the list and cloud status. */
public class FriendsScreen extends Screen {
    private static final int ROW_H = 30;

    private record Hit(RkbUi.Rect rect, Runnable action) {
    }

    private final List<Hit> hits = new ArrayList<>();
    private TextFieldWidget input;
    private float scroll;
    private int contentH;
    private String message = "Type a username or UUID, then press Add.";
    private RkbLayout.TopBar chrome;
    private int contentX, contentY, contentW, contentHBox, innerX, innerW;
    private int listX, listY, listW, listH;
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
        chrome = RkbLayout.topBar(width, height);
        contentX = 8;
        contentW = width - 16;
        contentY = chrome.contentTop();
        contentHBox = height - contentY - 14;
        innerX = contentX + 12;
        innerW = contentW - 24;
        listX = innerX;
        listW = innerW;
        listY = contentY + 96;
        listH = Math.max(20, contentY + contentHBox - 12 - listY);

        input = new TextFieldWidget(mc.textRenderer, innerX + 8, contentY + 63, innerW - 86, 12,
                Text.literal("Player"));
        input.setMaxLength(40);
        input.setPlaceholder(Text.literal("Username or UUID"));
        input.setText(previous);
        input.setDrawsBackground(false);
        addSelectableChild(input);
        mouseWasDown = KeyUtil.isMouseDown(mc, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        RkbScreens.back();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll -= (float) (verticalAmount * 20.0);
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
        if (input.isMouseOver(mx, my)) {
            input.setFocused(true);
            input.mouseClicked(new Click(mx, my, new MouseInput(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0)), false);
            return;
        }
        input.setFocused(false);
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.rect().contains(mx, my)) {
                h.action().run();
                return;
            }
        }
    }

    @Override
    public boolean keyPressed(KeyInput key) {
        if (input.isFocused() && input.keyPressed(key)) {
            return true;
        }
        return super.keyPressed(key);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (this.input.isFocused() && this.input.charTyped(input)) {
            return true;
        }
        return super.charTyped(input);
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
        ctx.drawText(tr, "Badge friends", innerX, contentY + 12, RkbUi.TEXT, false);
        String cloud = "Cloud: " + CloudService.statusText() + "  ·  " + FriendsManager.size()
                + (FriendsManager.size() == 1 ? " friend" : " friends");
        ctx.drawText(tr, RkbUi.fit(tr, cloud, innerW - 90), innerX, contentY + 27, RkbUi.MUTED, false);

        String back = "<  Settings";
        int bw = tr.getWidth(back) + 18;
        RkbUi.Rect backRect = new RkbUi.Rect(contentX + contentW - bw - 12, contentY + 10, bw, 20);
        boolean hover = backRect.contains(mx, my);
        RkbUi.panel(ctx, backRect, hover ? RkbUi.SURFACE_2 : RkbUi.SURFACE, hover ? accent : RkbUi.LINE);
        ctx.drawText(tr, back, backRect.x() + 9, backRect.y() + 6, hover ? RkbUi.TEXT : RkbUi.MUTED, false);
        hits.add(new Hit(backRect, RkbScreens::back));

        RkbUi.Rect fieldBox = new RkbUi.Rect(innerX, contentY + 54, innerW, 24);
        RkbUi.panel(ctx, fieldBox, input.isFocused() ? 0xFF101A2C : RkbUi.SURFACE_3,
                input.isFocused() ? accent : RkbUi.LINE);
        input.setX(innerX + 8);
        input.setY(contentY + 61);
        input.setWidth(Math.max(40, innerW - 90));
        input.render(ctx, mx, my, delta);
        if (input.getText().isEmpty() && !input.isFocused()) {
            ctx.drawText(tr, "Username or UUID", innerX + 8, contentY + 61, RkbUi.MUTED, false);
        }
        RkbUi.Rect add = new RkbUi.Rect(contentX + contentW - 12 - 52, contentY + 54, 52, 24);
        RkbUi.button(ctx, tr, add, "Add", add.contains(mx, my), true, true);
        hits.add(new Hit(add, this::addFromInput));
        ctx.drawText(tr, RkbUi.fit(tr, message, innerW), innerX, contentY + 84, RkbUi.MUTED, false);

        List<FriendsManager.Friend> list = FriendsManager.list();
        contentH = list.size() * ROW_H;
        scroll = Math.max(0, Math.min(Math.max(0, contentH - listH), scroll));
        int y = listY - Math.round(scroll);
        ctx.enableScissor(listX, listY, listX + listW, listY + listH);
        for (int i = 0; i < list.size(); i++) {
            FriendsManager.Friend f = list.get(i);
            if (y + ROW_H >= listY && y <= listY + listH) {
                RkbUi.roundedFill(ctx, new RkbUi.Rect(listX, y, listW - 6, ROW_H - 4), RkbUi.SURFACE);
                ctx.fill(listX + 2, y + 4, listX + 4, y + ROW_H - 8, accent);
                ctx.drawText(tr, RkbUi.fit(tr, FriendsManager.displayName(f), listW - 120),
                        listX + 10, y + 5, RkbUi.TEXT, false);
                ctx.drawText(tr, f.uuid().toString().substring(0, 13) + "...", listX + 10, y + 16,
                        RkbUi.MUTED, false);
                int rw = Math.max(52, tr.getWidth("Remove") + 16);
                RkbUi.Rect remove = new RkbUi.Rect(listX + listW - 10 - rw, y + 5, rw, 18);
                RkbUi.button(ctx, tr, remove, "Remove", remove.contains(mx, my), false, true);
                final FriendsManager.Friend target = f;
                hits.add(new Hit(remove, () -> {
                    FriendsManager.remove(target.uuid());
                    say("Removed " + FriendsManager.displayName(target) + ".");
                }));
            }
            y += ROW_H;
        }
        ctx.disableScissor();
        if (list.isEmpty()) {
            ctx.drawText(tr, "No friends yet.", listX + 4, listY + 8, RkbUi.MUTED, false);
        }

        RkbChrome.draw(ctx, tr, chrome, mx, my, RkbUi.NAV_NONE,
                (r, action) -> hits.add(new Hit(r, action)));
    }
}
