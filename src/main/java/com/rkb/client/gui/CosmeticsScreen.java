package com.rkb.client.gui;

import com.rkb.client.cloud.CloudStore;
import com.rkb.client.cosmetics.CosmeticsManager;
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

/**
 * RKB account cosmetics: Shop (claim / how to buy), Inventory (equip / unequip) and History.
 * Every button only sends an action to the server through CosmeticsManager; what you own is whatever the server says.
 */
public class CosmeticsScreen extends Screen {
    private static final int ROW_H = 30;
    private static final int TAB_SHOP = 0;
    private static final int TAB_INV = 1;
    private static final int TAB_HIS = 2;
    private static final String[] TABS = {"Shop", "Inventory", "History"};

    private record Hit(int x1, int y1, int x2, int y2, Runnable action) {
        boolean contains(int x, int y) {
            return x >= x1 && x < x2 && y >= y1 && y < y2;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private TextFieldWidget codeField;
    private int tab = TAB_SHOP;
    private float scroll;
    private int contentH;
    private int px, py, pw, ph, listY, listH;
    private boolean mouseWasDown;
    private boolean dragging;
    private boolean pressedInList;
    private int pressY;
    private int lastY;

    public CosmeticsScreen() {
        super(Text.literal("RKB Cosmetics"));
    }

    @Override
    protected void init() {
        MinecraftClient mc = MinecraftClient.getInstance();
        String previous = codeField == null ? "" : codeField.getText();
        pw = Math.min(width - 12, 460);
        ph = Math.min(height - 12, 290);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        listY = py + 62;
        listH = py + ph - 34 - listY;

        codeField = new TextFieldWidget(mc.textRenderer, px + 8, py + ph - 26, pw - 16 - 58, 16, Text.literal("Code"));
        codeField.setMaxLength(32);
        codeField.setPlaceholder(Text.literal("Redeem code (XXXX-XXXX-XXXX-XXXX)"));
        codeField.setText(previous);
        addSelectableChild(codeField);
        mouseWasDown = KeyUtil.isMouseDown(mc, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        CosmeticsManager.refresh();
    }

    public boolean shouldPause() {
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll -= (float) (verticalAmount * 18.0);
        return true;
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
        if (codeField.isMouseOver(mx, my)) {
            return;
        }
        codeField.setFocused(false);
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.contains(mx, my)) {
                h.action().run();
                return;
            }
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

    private static String price(CosmeticsManager.Item i) {
        if (i.priceCents() == 0) {
            return "Free";
        }
        return String.format(java.util.Locale.ROOT, "%d.%02d %s", i.priceCents() / 100, i.priceCents() % 100, i.currency());
    }

    private int button(DrawContext c, TextRenderer tr, String label, int right, int y, boolean enabled, Runnable action) {
        int w = tr.getWidth(label) + 12;
        int x = right - w;
        c.fill(x, y, right, y + 14, enabled ? Theme.BORDER : Theme.CARD_INNER);
        c.drawText(tr, label, x + 6, y + 3, enabled ? Theme.TEXT : Theme.OFF, false);
        if (enabled && action != null) {
            hits.add(new Hit(x, y, right, y + 14, action));
        }
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
        CosmeticsManager.State st = CosmeticsManager.state();
        boolean signedIn = CosmeticsManager.ready();

        ctx.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, Theme.BORDER);
        ctx.fill(px, py, px + pw, py + ph, Theme.PANEL);
        ctx.fill(px, py, px + pw, py + 22, Theme.BLACK);
        ctx.drawText(tr, "RKB cosmetics", px + 8, py + 7, accent, false);
        String who = !signedIn ? "Signed out" : st.rkbId().isEmpty() ? CloudStore.email() : st.rkbId();
        int backW = tr.getWidth("Back") + 12;
        int backX = px + pw - 8 - backW;
        ctx.fill(backX, py + 4, backX + backW, py + 18, Theme.CARD);
        ctx.drawText(tr, "Back", backX + 6, py + 8, Theme.TEXT, false);
        hits.add(new Hit(backX, py + 4, backX + backW, py + 18, () -> MinecraftClient.getInstance().setScreen(new RkbMenuScreen())));
        ctx.drawText(tr, who, backX - 10 - tr.getWidth(who), py + 8, signedIn ? Theme.TEXT : Theme.MUTED, false);

        // tabs + refresh
        int tx = px + 8;
        for (int i = 0; i < TABS.length; i++) {
            int w = tr.getWidth(TABS[i]) + 16;
            boolean active = i == tab;
            ctx.fill(tx, py + 26, tx + w, py + 42, active ? accent : Theme.CARD);
            ctx.drawText(tr, TABS[i], tx + 8, py + 30, active ? Theme.BLACK : Theme.MUTED, false);
            final int idx = i;
            hits.add(new Hit(tx, py + 26, tx + w, py + 42, () -> {
                tab = idx;
                scroll = 0;
            }));
            tx += w + 4;
        }
        button(ctx, tr, CosmeticsManager.busy() ? "Working..." : "Refresh", px + pw - 8, py + 28, signedIn && !CosmeticsManager.busy(), CosmeticsManager::refresh);

        String msg = CosmeticsManager.message();
        if (!signedIn) {
            msg = "Sign in with your RKB account: /rkb cloud login <email> <password>  (new: /rkb cloud register <email> <password>)";
        } else if (msg.isEmpty()) {
            msg = tab == TAB_SHOP ? "Owned items stay in your account on every launcher and Minecraft account."
                    : tab == TAB_INV ? "Equip one cape and one badge." : "Your claims, codes and purchases.";
        }
        ctx.drawText(tr, fit(tr, msg, pw - 16), px + 8, py + 48, Theme.MUTED, false);

        // list
        List<CosmeticsManager.Item> shop = new ArrayList<>();
        List<CosmeticsManager.Item> owned = new ArrayList<>();
        for (CosmeticsManager.Item i : st.items()) {
            shop.add(i);
            if (i.owned()) {
                owned.add(i);
            }
        }
        int count = tab == TAB_SHOP ? shop.size() : tab == TAB_INV ? owned.size() : st.history().size();
        contentH = count * ROW_H;
        scroll = Math.max(0, Math.min(Math.max(0, contentH - listH), scroll));
        int y = listY - Math.round(scroll);
        int right = px + pw - 14;

        ctx.enableScissor(px + 8, listY, px + pw - 8, listY + listH);
        for (int n = 0; n < count; n++) {
            if (y + ROW_H >= listY && y <= listY + listH) {
                ctx.fill(px + 8, y, px + pw - 14, y + ROW_H - 2, Theme.CARD);
                if (tab == TAB_HIS) {
                    CosmeticsManager.Entry e = st.history().get(n);
                    ctx.drawText(tr, fit(tr, e.cosmeticId() + "  (" + e.kind() + ")", pw - 140), px + 16, y + 4, Theme.TEXT, false);
                    String amount = e.amountCents() == 0 ? "free" : String.format(java.util.Locale.ROOT, "%d.%02d %s", e.amountCents() / 100, e.amountCents() % 100, e.currency());
                    String when = e.createdAt().length() >= 10 ? e.createdAt().substring(0, 10) : e.createdAt();
                    ctx.drawText(tr, fit(tr, when + "   " + amount + (e.reference().isEmpty() ? "" : "   " + e.reference()), pw - 140), px + 16, y + 16, Theme.MUTED, false);
                    String status = e.status();
                    ctx.drawText(tr, status, right - 4 - tr.getWidth(status), y + 10, "completed".equals(status) ? accent : Theme.MUTED, false);
                } else {
                    CosmeticsManager.Item it = tab == TAB_SHOP ? shop.get(n) : owned.get(n);
                    boolean equipped = it.id().equals(st.equipped().get(it.type()));
                    ctx.fill(px + 8, y, px + 11, y + ROW_H - 2, equipped ? accent : Theme.OFF);
                    ctx.drawText(tr, fit(tr, it.name(), pw - 150), px + 16, y + 4, Theme.TEXT, false);
                    ctx.drawText(tr, fit(tr, it.type() + "  " + it.rarity() + "  " + price(it), pw - 150), px + 16, y + 16, Theme.MUTED, false);
                    int bx = right - 4;
                    if (tab == TAB_SHOP) {
                        if (it.owned()) {
                            ctx.drawText(tr, "Owned", bx - tr.getWidth("Owned"), y + 10, accent, false);
                        } else if (it.claimable() && it.priceCents() == 0) {
                            button(ctx, tr, "Claim", bx, y + 8, !CosmeticsManager.busy(), () -> CosmeticsManager.claim(it.id()));
                        } else {
                            button(ctx, tr, "Buy", bx, y + 8, !CosmeticsManager.busy() && it.priceCents() > 0, () -> CosmeticsManager.purchaseStart(it.id()));
                        }
                    } else {
                        if (equipped) {
                            button(ctx, tr, "Unequip", bx, y + 8, !CosmeticsManager.busy(), () -> CosmeticsManager.unequip(it.type()));
                        } else {
                            button(ctx, tr, "Equip", bx, y + 8, !CosmeticsManager.busy(), () -> CosmeticsManager.equip(it.id()));
                        }
                    }
                }
            }
            y += ROW_H;
        }
        ctx.disableScissor();
        if (count == 0) {
            ctx.drawText(tr, signedIn ? (tab == TAB_SHOP ? "The shop is empty." : tab == TAB_INV ? "You do not own anything yet." : "No history yet.") : "",
                    px + 10, listY + 6, Theme.MUTED, false);
        }

        // redeem
        codeField.render(ctx, mx, my, delta);
        int rx = px + pw - 8 - 52;
        ctx.fill(rx, py + ph - 26, rx + 52, py + ph - 10, signedIn ? accent : Theme.OFF);
        ctx.drawText(tr, "Redeem", rx + (52 - tr.getWidth("Redeem")) / 2, py + ph - 22, Theme.BLACK, false);
        if (signedIn) {
            hits.add(new Hit(rx, py + ph - 26, rx + 52, py + ph - 10, () -> {
                String code = codeField.getText();
                codeField.setText("");
                CosmeticsManager.redeem(code);
            }));
        }
    }
}
