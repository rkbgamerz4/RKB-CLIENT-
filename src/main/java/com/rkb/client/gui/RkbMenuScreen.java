package com.rkb.client.gui;

import com.rkb.client.VersionInfo;
import com.rkb.client.cloud.AuthState;
import com.rkb.client.cloud.CloudService;
import com.rkb.client.cloud.CloudStore;
import com.rkb.client.cloud.FriendsManager;
import com.rkb.client.config.ConfigManager;
import com.rkb.client.config.Favorites;
import com.rkb.client.cosmetics.RkbLogo;
import com.rkb.client.input.KeyUtil;
import com.rkb.client.input.KeybindRegistry;
import com.rkb.client.module.Category;
import com.rkb.client.module.KeyAction;
import com.rkb.client.module.Module;
import com.rkb.client.module.ModuleManager;
import com.rkb.client.module.impl.InterfaceModule;
import com.rkb.client.module.impl.NameTagBadgeModule;
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
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The in-game RKB surface. One shared top bar drives every section; the page body hosts the module browser, grouped
 * settings, profiles and about information. Module settings and keybinds stay exactly where they were, so no saved
 * value or feature is lost in the redesign.
 */
public class RkbMenuScreen extends Screen {
    public static final int PAGE_MODS = 0;
    public static final int PAGE_SETTINGS = 1;
    public static final int PAGE_PROFILES = 2;
    public static final int PAGE_ABOUT = 6;

    private static final int GAP = 6;
    private static final int ROW_H = 22;
    private static final int[] DUMMY = new int[0];
    private static final String[] SETTING_GROUPS = {
            "Interface", "HUD", "Performance", "Controls", "Modules", "Other"
    };

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

    private RkbLayout.TopBar chrome;
    private TextFieldWidget search;
    private int tab;
    private int settingsGroup;
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

    private int contentX, contentY, contentW, contentHBox, innerX, innerW, bodyTop, bodyBottom;
    private int searchY, chipsY, gridY;
    private int listX, listY, listW, listH;

    private boolean mouseWasDown;
    private boolean dragging;
    private boolean pressedInList;
    private int pressY;
    private int lastY;

    public RkbMenuScreen() {
        this(PAGE_MODS);
    }

    public RkbMenuScreen(int initialPage) {
        super(Text.literal("RKB CLIENT"));
        this.tab = normalizePage(initialPage);
    }

    private static int normalizePage(int page) {
        return page == PAGE_SETTINGS || page == PAGE_PROFILES || page == PAGE_ABOUT ? page : PAGE_MODS;
    }

    // ------------------------------------------------------------------ setup

    @Override
    protected void init() {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        String previous = search == null ? "" : search.getText();

        chrome = RkbLayout.topBar(width, height);
        contentX = 8;
        contentW = Math.max(160, width - 16);
        contentY = chrome.contentTop();
        contentHBox = Math.max(120, height - contentY - 14);
        innerX = contentX + 12;
        innerW = contentW - 24;
        bodyTop = contentY + 38;
        bodyBottom = contentY + contentHBox - 14;
        listX = innerX;
        listW = innerW;

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

        int n = filters.size();
        chipX = new int[n];
        chipY = new int[n];
        chipW = new int[n];
        searchY = bodyTop;
        int x = innerX;
        int y = searchY + 26;
        chipRows = 1;
        for (int i = 0; i < n; i++) {
            int w = tr.getWidth(filters.get(i).label()) + 16;
            if (x + w > innerX + innerW && x > innerX) {
                x = innerX;
                y += 21;
                chipRows++;
            }
            chipX[i] = x;
            chipY[i] = y;
            chipW[i] = w;
            x += w + 4;
        }
        chipsY = searchY + 26;
        gridY = chipsY + chipRows * 21 + 4;

        search = new TextFieldWidget(tr, innerX + 9, searchY + 7, Math.max(60, innerW - 96), 12,
                Text.literal("Search"));
        search.setMaxLength(32);
        search.setPlaceholder(Text.literal("Search modules"));
        search.setDrawsBackground(false);
        search.setChangedListener(s -> {
            scroll = 0;
            listDirty = true;
        });
        search.setText(previous);
        addSelectableChild(search);
        listDirty = true;

        RkbLogo.ensure();
        mouseWasDown = KeyUtil.isMouseDown(mc, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return listening == null && !(tab == PAGE_MODS && detail != null);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        ConfigManager.save();
    }

    @Override
    public void close() {
        closing = true;
        ConfigManager.save();
        RkbScreens.close();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll -= (float) (verticalAmount * 20.0);
        return true;
    }

    // ------------------------------------------------------------------ navigation

    private void openTab(int t) {
        tab = normalizePage(t);
        detail = null;
        listening = null;
        scroll = 0;
        pageAnim = 0f;
        confirmReset = false;
        if (search != null) {
            search.setFocused(false);
        }
    }

    private void openDetail(Module m) {
        tab = PAGE_MODS;
        detail = m;
        listening = null;
        scroll = 0;
        pageAnim = 0f;
        if (search != null) {
            search.setFocused(false);
        }
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

    private int activeNav() {
        return switch (tab) {
            case PAGE_SETTINGS -> 1;
            case PAGE_PROFILES -> 2;
            case PAGE_ABOUT -> 6;
            default -> RkbUi.NAV_NONE;
        };
    }

    private void navTo(int index) {
        switch (index) {
            case 0 -> RkbScreens.home();
            case 1 -> openTab(PAGE_SETTINGS);
            case 2 -> openTab(PAGE_PROFILES);
            case 3, 4, 5 -> RkbScreens.openNav(index);
            case 6 -> openTab(PAGE_ABOUT);
            default -> RkbScreens.home();
        }
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
        if (search != null && search.visible && search.isFocused()) {
            if (search.keyPressed(input)) {
                return true;
            }
            if (input.isEscape()) {
                search.setFocused(false);
                return true;
            }
        } else {
            KeyBinding menuKey = KeybindRegistry.bindingOf(ModuleManager.get().ui());
            if (menuKey != null && menuKey.matchesKey(input)) {
                close();
                return true;
            }
            if (input.isEscape() && tab == PAGE_MODS && detail != null) {
                closeDetail();
                return true;
            }
            if (input.isUp()) {
                scroll -= 20;
                return true;
            }
            if (input.isDown()) {
                scroll += 20;
                return true;
            }
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (search != null && search.visible && search.isFocused() && search.charTyped(input)) {
            return true;
        }
        return super.charTyped(input);
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
        if (search != null && search.visible && search.isMouseOver(mx, my)) {
            search.setFocused(true);
            search.mouseClicked(new Click(mx, my, new MouseInput(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0)), false);
            return;
        }
        if (search != null) {
            search.setFocused(false);
        }
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
        return RkbUi.fit(tr, s, maxW);
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
        int w = Math.max(34, tr.getWidth(label) + 16);
        int x = right - w;
        RkbUi.button(c, tr, new RkbUi.Rect(x, y, w, 18), label, false, false, enabled);
        if (enabled && action != null) {
            hits.add(new Hit(x, y, right, y + 18, true, action));
        }
        return x;
    }

    private void rowBg(DrawContext c, int y, int cw) {
        RkbUi.roundedFill(c, new RkbUi.Rect(listX, y, cw, ROW_H - 3), RkbUi.SURFACE);
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
        int accent = Theme.chromeAccent();
        RkbUi.background(ctx, width, height, System.currentTimeMillis());
        drawChrome(ctx, tr, accent, mx, my);

        RkbUi.panel(ctx, new RkbUi.Rect(contentX, contentY, contentW, contentHBox), 0xE60E1422, RkbUi.LINE);
        drawPageHeader(ctx, tr, accent);

        boolean grid = tab == PAGE_MODS && detail == null;
        if (grid) {
            listY = gridY;
        } else if (tab == PAGE_SETTINGS) {
            listY = bodyTop + 28;
        } else {
            listY = bodyTop + 8;
        }
        listH = Math.max(10, bodyBottom - listY);

        search.visible = grid;
        if (!grid && search.isFocused()) {
            search.setFocused(false);
        }
        if (grid) {
            drawFilterRow(ctx, tr, mx, my, delta, accent);
        } else if (tab == PAGE_SETTINGS) {
            drawSettingsGroups(ctx, tr, mx, my, accent);
        }

        int maxScroll = Math.max(0, contentH - listH);
        scroll = Math.max(0, Math.min(maxScroll, scroll));

        ctx.enableScissor(listX, listY, listX + listW, listY + listH);
        if (grid) {
            drawGrid(ctx, tr, mx, my, accent);
        } else if (tab == PAGE_PROFILES) {
            drawProfiles(ctx, tr, accent);
        } else if (tab == PAGE_ABOUT) {
            drawAbout(ctx, tr, accent);
        } else if (tab == PAGE_SETTINGS) {
            drawSettingsPage(ctx, tr, accent);
        } else {
            drawDetail(ctx, tr, accent, pageModule());
        }
        ctx.disableScissor();

        if (tab == PAGE_MODS && detail != null) {
            drawBackChip(ctx, tr, accent, mx, my);
        }

        if (pageAnim < 1f) {
            int a = (int) ((1f - pageAnim) * 170f);
            ctx.fill(listX, listY, listX + listW, listY + listH, (a << 24) | 0x070A12);
        }
        if (contentH > listH) {
            int barH = Math.max(16, listH * listH / contentH);
            int barPos = listY + (int) ((listH - barH) * (scroll / Math.max(1f, contentH - listH)));
            RkbUi.roundedFill(ctx, new RkbUi.Rect(listX + listW - 4, barPos, 3, barH), accent);
        }
        drawFooter(ctx, tr, mc);
        if (System.currentTimeMillis() < toastUntil) {
            int tw = tr.getWidth(toast) + 22;
            int tx = listX + (listW - tw) / 2;
            int ty = bodyBottom - 24;
            RkbUi.panel(ctx, new RkbUi.Rect(tx, ty, tw, 20), 0xF00E1422, accent);
            text(ctx, tr, toast, tx + 11, ty + 6, RkbUi.TEXT);
        }
    }

    private void drawChrome(DrawContext ctx, TextRenderer tr, int accent, int mx, int my) {
        RkbUi.Rect brand = chrome.brand();
        int logo = 22;
        if (RkbLogo.ready()) {
            RkbLogo.draw(ctx, brand.x(), brand.y() + 1, logo);
        } else {
            RkbUi.roundedFill(ctx, new RkbUi.Rect(brand.x(), brand.y() + 1, logo, logo), accent);
            text(ctx, tr, "R", brand.x() + 8, brand.y() + 7, 0xFF02101C);
        }
        int bx = brand.x() + logo + 8;
        ctx.drawText(tr, Text.literal("RKB").styled(s -> s.withBold(true)), bx, brand.y() + 4, RkbUi.TEXT, false);
        ctx.drawText(tr, "CLIENT", bx + tr.getWidth("RKB ") + 3, brand.y() + 4, accent, false);
        ctx.drawText(tr, "PVP UI", bx, brand.y() + 15, RkbUi.MUTED, false);
        hits.add(new Hit(brand.x(), brand.y(), brand.right(), brand.bottom(), false, RkbScreens::home));

        int active = activeNav();
        for (int i = 0; i < chrome.navItems().size(); i++) {
            RkbUi.Rect r = chrome.navItems().get(i);
            boolean hover = r.contains(mx, my);
            RkbUi.chip(ctx, r, RkbUi.NAV_LABELS[i], tr, i == active, hover);
            final int nav = i;
            hits.add(new Hit(r.x(), r.y(), r.right(), r.bottom(), false, () -> navTo(nav)));
        }

        RkbUi.Rect account = chrome.account();
        boolean ah = account.contains(mx, my);
        RkbUi.panel(ctx, account, ah ? RkbUi.SURFACE_2 : RkbUi.SURFACE, ah ? accent : RkbUi.LINE);
        AuthState auth = CloudService.authState();
        RkbUi.statusDot(ctx, account.x() + 8, account.y() + 10, auth.tone());
        text(ctx, tr, fit(tr, auth.accountLabel(), account.width() - 24), account.x() + 18, account.y() + 8,
                auth.signedIn() ? RkbUi.TEXT : RkbUi.MUTED);
        hits.add(new Hit(account.x(), account.y(), account.right(), account.bottom(), false, RkbScreens::account));

        RkbUi.Rect close = chrome.close();
        boolean ch = close.contains(mx, my);
        RkbUi.panel(ctx, close, ch ? RkbUi.SURFACE_2 : RkbUi.SURFACE, ch ? RkbUi.DANGER : RkbUi.LINE);
        text(ctx, tr, "X", close.x() + (close.width() - tr.getWidth("X")) / 2, close.y() + 8,
                ch ? RkbUi.DANGER : RkbUi.TEXT);
        hits.add(new Hit(close.x(), close.y(), close.right(), close.bottom(), false, this::close));
    }

    private void drawPageHeader(DrawContext ctx, TextRenderer tr, int accent) {
        String title;
        String sub;
        switch (tab) {
            case PAGE_SETTINGS -> {
                title = "Settings";
                sub = "Menu, HUD, performance and controls. Every saved value is preserved.";
            }
            case PAGE_PROFILES -> {
                title = "Profiles";
                sub = "Save or load complete module setups.";
            }
            case PAGE_ABOUT -> {
                title = "About RKB";
                sub = "Client information, keys and local file locations.";
            }
            default -> {
                title = detail == null ? "Modules" : detail.name();
                sub = detail == null ? "Enable modules, favourite them and open their settings."
                        : fit(tr, detail.description(), innerW - 180);
            }
        }
        ctx.drawText(tr, Text.literal(title).styled(s -> s.withBold(true)), innerX, contentY + 12, RkbUi.TEXT,
                false);
        text(ctx, tr, fit(tr, sub, innerW - 150), innerX, contentY + 25, RkbUi.MUTED);

        String status = "";
        if (Theme.menuShowFps()) {
            status = "FPS " + MinecraftClient.getInstance().getCurrentFps();
        }
        if (Theme.menuShowVersion()) {
            status = status.isEmpty() ? "MC " + VersionInfo.minecraft() : status + "  ·  MC " + VersionInfo.minecraft();
        }
        if (!status.isEmpty()) {
            int sw = tr.getWidth(status) + 16;
            RkbUi.Rect chip = new RkbUi.Rect(contentX + contentW - sw - 12, contentY + 12, sw, 18);
            RkbUi.panel(ctx, chip, 0x80141D30, RkbUi.LINE);
            text(ctx, tr, status, chip.x() + 8, chip.y() + 5, RkbUi.MUTED);
        }
    }

    private void drawBackChip(DrawContext ctx, TextRenderer tr, int accent, int mx, int my) {
        String back = "<  Back";
        int bw = tr.getWidth(back) + 18;
        RkbUi.Rect r = new RkbUi.Rect(contentX + contentW - bw - 12, contentY + 10, bw, 20);
        boolean hover = r.contains(mx, my);
        RkbUi.panel(ctx, r, hover ? RkbUi.SURFACE_2 : RkbUi.SURFACE, hover ? accent : RkbUi.LINE);
        text(ctx, tr, back, r.x() + 9, r.y() + 6, hover ? RkbUi.TEXT : RkbUi.MUTED);
        hits.add(new Hit(r.x(), r.y(), r.right(), r.bottom(), false, this::closeDetail));
    }

    private void drawFilterRow(DrawContext ctx, TextRenderer tr, int mx, int my, float delta, int accent) {
        RkbUi.Rect field = new RkbUi.Rect(innerX, searchY, innerW, 24);
        RkbUi.panel(ctx, field, search.isFocused() ? 0xFF101A2C : RkbUi.SURFACE_3,
                search.isFocused() ? accent : RkbUi.LINE);
        search.setX(innerX + 9);
        search.setY(searchY + 7);
        search.setWidth(Math.max(60, innerW - 96));
        search.render(ctx, mx, my, delta);
        if (search.getText().isEmpty() && !search.isFocused()) {
            text(ctx, tr, "Search modules", innerX + 9, searchY + 8, RkbUi.MUTED);
        }

        int bx = innerX + innerW - 20;
        RkbUi.panel(ctx, new RkbUi.Rect(bx, searchY + 4, 18, 16), compactMode ? accent : RkbUi.SURFACE,
                compactMode ? accent : RkbUi.LINE);
        int ic = compactMode ? 0xFF02101C : RkbUi.MUTED;
        ctx.fill(bx + 4, searchY + 8, bx + 8, searchY + 11, ic);
        ctx.fill(bx + 10, searchY + 8, bx + 14, searchY + 11, ic);
        ctx.fill(bx + 4, searchY + 13, bx + 8, searchY + 16, ic);
        ctx.fill(bx + 10, searchY + 13, bx + 14, searchY + 16, ic);
        hits.add(new Hit(bx, searchY + 4, bx + 18, searchY + 20, false, () -> {
            compactMode = !compactMode;
            scroll = 0;
        }));

        int hx = bx - 24;
        RkbUi.panel(ctx, new RkbUi.Rect(hx, searchY + 4, 18, 16), favOnly ? 0xFF2A1A24 : RkbUi.SURFACE,
                favOnly ? accent : RkbUi.LINE);
        heart(ctx, hx + 5, searchY + 9, favOnly ? accent : 0xFF59627A);
        hits.add(new Hit(hx, searchY + 4, hx + 18, searchY + 20, false, () -> {
            favOnly = !favOnly;
            scroll = 0;
            listDirty = true;
        }));

        for (int i = 0; i < filters.size(); i++) {
            boolean active = i == filterIdx;
            int x = chipX[i];
            int y = chipY[i];
            int w = chipW[i];
            boolean hover = mx >= x && mx < x + w && my >= y && my < y + 18;
            RkbUi.chip(ctx, new RkbUi.Rect(x, y, w, 18), filters.get(i).label(), tr, active, hover);
            final int idx = i;
            hits.add(new Hit(x, y, x + w, y + 18, false, () -> {
                filterIdx = idx;
                scroll = 0;
                listDirty = true;
                pageAnim = 0.4f;
            }));
        }
    }

    private void drawSettingsGroups(DrawContext ctx, TextRenderer tr, int mx, int my, int accent) {
        int gap = 4;
        int w = (innerW - gap * (SETTING_GROUPS.length - 1)) / SETTING_GROUPS.length;
        int x = innerX;
        for (int i = 0; i < SETTING_GROUPS.length; i++) {
            RkbUi.Rect r = new RkbUi.Rect(x, bodyTop, w, 22);
            boolean hover = r.contains(mx, my);
            RkbUi.chip(ctx, r, SETTING_GROUPS[i], tr, i == settingsGroup, hover);
            final int group = i;
            hits.add(new Hit(r.x(), r.y(), r.right(), r.bottom(), false, () -> {
                settingsGroup = group;
                scroll = 0;
            }));
            x += w + gap;
        }
    }

    private void drawFooter(DrawContext ctx, TextRenderer tr, MinecraftClient mc) {
        String left = "RKB CLIENT " + VersionInfo.mod();
        String right = "Right Shift opens this menu  ·  /rkb";
        text(ctx, tr, left, contentX + 12, contentY + contentHBox - 10, RkbUi.MUTED);
        text(ctx, tr, right, contentX + contentW - 12 - tr.getWidth(right), contentY + contentHBox - 10,
                RkbUi.MUTED);
    }

    // ------------------------------------------------------------------ module grid

    private void drawGrid(DrawContext ctx, TextRenderer tr, int mx, int my, int accent) {
        if (listDirty) {
            refreshList();
        }
        boolean compact = compactMode || listH < 110;
        int cardH = compact ? 50 : 84;
        int minW = compact ? 100 : 122;
        int cols = Math.max(1, (listW - 6 + GAP) / (minW + GAP));
        int cardW = (listW - 6 - (cols - 1) * GAP) / cols;
        int originY = listY + 4 - Math.round(scroll);
        int enabledBg = RkbUi.blend(accent, 0xFF070A12, 0.62f);

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

            int fill = RkbUi.blend(RkbUi.SURFACE, RkbUi.SURFACE_2, a[1]);
            RkbUi.panel(ctx, new RkbUi.Rect(x, y, cardW, cardH), fill,
                    RkbUi.blend(RkbUi.LINE, accent, a[0] * 0.8f));
            ctx.fill(x + 3, y + 3, x + 4, y + cardH - 3, RkbUi.blend(RkbUi.LINE, accent, a[0]));

            text(ctx, tr, fit(tr, m.name(), cardW - 30), x + 8, y + 7, RkbUi.TEXT);
            boolean fav = Favorites.contains(m.id());
            heart(ctx, x + cardW - 15, y + 7, fav ? accent : 0xFF59627A);

            if (!compact) {
                ctx.drawItem(ModuleMeta.icon(m), x + 10, y + 28);
                String cat = m.category().label();
                text(ctx, tr, fit(tr, cat, cardW - 38), x + 31, y + 33, RkbUi.MUTED);
            }

            int by = y + cardH - 22;
            RkbUi.panel(ctx, new RkbUi.Rect(x + 6, by, 18, 16), RkbUi.SURFACE_3, RkbUi.LINE);
            gear(ctx, x + 15, by + 8, RkbUi.blend(RkbUi.MUTED, RkbUi.TEXT, a[1]), RkbUi.SURFACE_3);
            int tx = x + 28;
            int tw = cardW - 34;
            RkbUi.Rect state = new RkbUi.Rect(tx, by, tw, 16);
            RkbUi.panel(ctx, state, m.isToggleable() ? RkbUi.blend(RkbUi.SURFACE_3, enabledBg, a[0]) : RkbUi.SURFACE_3,
                    RkbUi.blend(RkbUi.LINE, accent, a[0] * 0.7f));
            String label;
            int textColor;
            if (m.isToggleable()) {
                label = m.isEnabled() ? "Enabled" : "Disabled";
                textColor = RkbUi.blend(RkbUi.MUTED, accent, a[0]);
            } else {
                label = "Open";
                textColor = RkbUi.MUTED;
            }
            text(ctx, tr, label, tx + (tw - tr.getWidth(label)) / 2, by + 4, textColor);

            if (ModuleMeta.isNew(m)) {
                int nw = tr.getWidth("NEW") + 8;
                RkbUi.panel(ctx, new RkbUi.Rect(x + 4, y - 4, nw, 12), accent, accent);
                text(ctx, tr, "NEW", x + 8, y - 1, 0xFF02101C);
            }

            hits.add(new Hit(x, y, x + cardW, y + cardH, true, () -> {
                if (m.isToggleable()) {
                    m.toggle();
                    ConfigManager.save();
                } else {
                    openDetail(m);
                }
            }));
            hits.add(new Hit(x + cardW - 17, y + 4, x + cardW - 3, y + 17, true, () -> {
                Favorites.toggle(m.id());
                listDirty = true;
                ConfigManager.save();
            }));
            hits.add(new Hit(x + 6, by, x + 24, by + 16, true, () -> openDetail(m)));
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
            text(ctx, tr, favOnly ? "No favourites yet. Tap the heart on a module." : "No modules found.",
                    listX + 4, listY + 12, RkbUi.MUTED);
        }
    }

    // ------------------------------------------------------------------ module detail

    private void drawDetail(DrawContext ctx, TextRenderer tr, int accent, Module m) {
        if (m == null) {
            return;
        }
        int startY = listY - Math.round(scroll);
        int y = drawDetailBody(ctx, tr, accent, m, startY);
        y = drawResetButton(ctx, tr, accent, m, y);
        contentH = y - startY;
    }

    private int drawDetailBody(DrawContext ctx, TextRenderer tr, int accent, Module m, int startY) {
        if (m == null) {
            return startY;
        }
        int cw = listW - 6;
        int right = listX + cw - 8;
        int y = startY;

        for (String line : wrap(tr, m.description(), cw - 8)) {
            text(ctx, tr, line, listX + 4, y, RkbUi.MUTED);
            y += 12;
        }
        y += 6;

        if (m.isToggleable()) {
            float[] a = anim(m);
            a[0] += ((m.isEnabled() ? 1f : 0f) - a[0]) * k;
            rowBg(ctx, y, cw);
            text(ctx, tr, "Enabled", listX + 10, y + 6, RkbUi.TEXT);
            RkbUi.toggle(ctx, right - 30, y + 4, 30, 15, a[0], accent);
            hits.add(new Hit(listX, y, listX + cw, y + ROW_H - 3, true, () -> {
                m.toggle();
                ConfigManager.save();
            }));
            y += ROW_H;
        }

        rowBg(ctx, y, cw);
        boolean capturing = listening == m;
        text(ctx, tr, "Keybind", listX + 10, y + 6, RkbUi.TEXT);
        String keyLabel = capturing ? "Press a key..." : KeyUtil.name(m.keyCode());
        int kw = Math.max(56, tr.getWidth(keyLabel) + 18);
        int kx = right - kw;
        RkbUi.panel(ctx, new RkbUi.Rect(kx, y + 3, kw, 17), capturing ? accent : RkbUi.SURFACE_3,
                capturing ? accent : RkbUi.LINE);
        text(ctx, tr, keyLabel, kx + (kw - tr.getWidth(keyLabel)) / 2, y + 7,
                capturing ? 0xFF02101C : RkbUi.TEXT);
        hits.add(new Hit(kx, y + 3, kx + kw, y + 20, true, () -> listening = listening == m ? null : m));
        y += ROW_H;

        if (m.isEnabled() || m.actions().isEmpty() || !m.isToggleable()) {
            for (KeyAction act : m.actions()) {
                rowBg(ctx, y, cw);
                text(ctx, tr, fit(tr, act.name(), right - listX - 80), listX + 10, y + 6, RkbUi.TEXT);
                button(ctx, tr, "Run", right, y + 3, true, () -> {
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
        text(ctx, tr, fit(tr, s.name(), right - listX - 130), listX + 10, y + 6, RkbUi.TEXT);
        if (s instanceof BoolSetting b) {
            float on = b.on() ? 1f : 0f;
            RkbUi.toggle(ctx, right - 30, y + 4, 30, 15, on, accent);
            hits.add(new Hit(right - 32, y, right, y + ROW_H - 3, true, () -> {
                b.toggle();
                ConfigManager.save();
            }));
        } else if (s instanceof IntSetting n) {
            int plusX = right - 18;
            int minusX = plusX - 42;
            int valX = minusX + 16;
            RkbUi.panel(ctx, new RkbUi.Rect(minusX, y + 3, 16, 17), RkbUi.SURFACE_3, RkbUi.LINE);
            text(ctx, tr, "-", minusX + 6, y + 7, RkbUi.TEXT);
            String v = String.valueOf(n.value());
            text(ctx, tr, v, valX + (24 - tr.getWidth(v)) / 2, y + 7, accent);
            RkbUi.panel(ctx, new RkbUi.Rect(plusX, y + 3, 16, 17), RkbUi.SURFACE_3, RkbUi.LINE);
            text(ctx, tr, "+", plusX + 5, y + 7, RkbUi.TEXT);
            hits.add(new Hit(minusX, y, minusX + 16, y + ROW_H - 3, true, () -> {
                n.step(-1);
                ConfigManager.save();
            }));
            hits.add(new Hit(plusX, y, plusX + 16, y + ROW_H - 3, true, () -> {
                n.step(1);
                ConfigManager.save();
            }));
        } else if (s instanceof ChoiceSetting c) {
            String v = c.get();
            int bw = Math.max(58, tr.getWidth(v) + 20);
            int bx = right - bw;
            RkbUi.panel(ctx, new RkbUi.Rect(bx, y + 3, bw, 17), RkbUi.SURFACE_3, RkbUi.LINE);
            text(ctx, tr, v, bx + (bw - tr.getWidth(v)) / 2, y + 7, accent);
            hits.add(new Hit(bx, y + 3, bx + bw, y + 20, true, () -> {
                c.cycle();
                ConfigManager.save();
            }));
        }
        return y + ROW_H;
    }

    private int drawResetButton(DrawContext ctx, TextRenderer tr, int accent, Module m, int y) {
        int cw = listW - 6;
        y += 8;
        RkbUi.Rect r = new RkbUi.Rect(listX, y, cw, 20);
        boolean hover = false;
        RkbUi.button(ctx, tr, r, "Reset to defaults", hover, false, true);
        hits.add(new Hit(r.x(), r.y(), r.right(), r.bottom(), true, () -> {
            m.resetToDefaults();
            ConfigManager.save();
            showToast(m.name() + " reset");
        }));
        return y + 26;
    }

    // ------------------------------------------------------------------ settings page

    private void drawSettingsPage(DrawContext ctx, TextRenderer tr, int accent) {
        int cw = listW - 6;
        int right = listX + cw - 8;
        int startY = listY - Math.round(scroll);
        int y = startY;

        switch (settingsGroup) {
            case 1 -> y = drawQuickModuleGroup(ctx, tr, accent, y, cw, right, Category.HUD,
                    "HUD elements. Open a module to change its detailed settings.");
            case 2 -> y = drawQuickModuleGroup(ctx, tr, accent, y, cw, right, Category.PERFORMANCE,
                    "Performance modules. Toggle one here or open it for details.");
            case 3 -> y = drawControlsGroup(ctx, tr, accent, y, cw, right);
            case 4 -> y = drawModulesGroup(ctx, tr, accent, y, cw, right);
            case 5 -> y = drawOtherGroup(ctx, tr, accent, y, cw, right);
            default -> {
                text(ctx, tr, "Menu and HUD appearance. These values are saved in config/rkbclient.json.",
                        listX + 4, y, RkbUi.MUTED);
                y += 16;
                InterfaceModule ui = ModuleManager.get().ui();
                y = drawDetailBody(ctx, tr, accent, ui, y);
                y = drawResetButton(ctx, tr, accent, ui, y);
            }
        }
        contentH = y + 6 - startY;
    }

    private int drawQuickModuleGroup(DrawContext ctx, TextRenderer tr, int accent, int y, int cw, int right,
                                     Category category, String hint) {
        for (String line : wrap(tr, hint, cw - 8)) {
            text(ctx, tr, line, listX + 4, y, RkbUi.MUTED);
            y += 12;
        }
        y += 6;
        List<Module> modules = ModuleManager.get().inCategory(category);
        if (modules.isEmpty()) {
            text(ctx, tr, "No modules in this group.", listX + 4, y + 4, RkbUi.MUTED);
            return y + 20;
        }
        for (Module m : modules) {
            y = drawQuickModuleRow(ctx, tr, accent, m, y, cw, right);
        }
        return y;
    }

    private int drawQuickModuleRow(DrawContext ctx, TextRenderer tr, int accent, Module m, int y, int cw, int right) {
        float[] a = anim(m);
        a[0] += ((m.isEnabled() ? 1f : 0f) - a[0]) * k;
        rowBg(ctx, y, cw);
        int catX = right - 112;
        text(ctx, tr, fit(tr, m.name(), Math.max(40, catX - listX - 22)), listX + 10, y + 6, RkbUi.TEXT);
        text(ctx, tr, fit(tr, m.category().label(), 54), catX, y + 6, RkbUi.MUTED);
        RkbUi.toggle(ctx, right - 32, y + 4, 30, 15, a[0], accent);
        hits.add(new Hit(right - 34, y, right, y + ROW_H - 3, true, () -> {
            if (m.isToggleable()) {
                m.toggle();
                ConfigManager.save();
            } else {
                openDetail(m);
            }
        }));
        int gx = right - 52;
        RkbUi.panel(ctx, new RkbUi.Rect(gx, y + 4, 16, 15), RkbUi.SURFACE_3, RkbUi.LINE);
        gear(ctx, gx + 8, y + 11, RkbUi.MUTED, RkbUi.SURFACE_3);
        hits.add(new Hit(gx, y + 4, gx + 16, y + 19, true, () -> openDetail(m)));
        return y + ROW_H;
    }

    private int drawControlsGroup(DrawContext ctx, TextRenderer tr, int accent, int y, int cw, int right) {
        InterfaceModule ui = ModuleManager.get().ui();
        rowBg(ctx, y, cw);
        text(ctx, tr, "Menu key", listX + 10, y + 6, RkbUi.TEXT);
        boolean capturing = listening == ui;
        String keyLabel = capturing ? "Press a key..." : KeyUtil.name(ui.keyCode());
        int kw = Math.max(56, tr.getWidth(keyLabel) + 18);
        int kx = right - kw;
        RkbUi.panel(ctx, new RkbUi.Rect(kx, y + 3, kw, 17), capturing ? accent : RkbUi.SURFACE_3,
                capturing ? accent : RkbUi.LINE);
        text(ctx, tr, keyLabel, kx + (kw - tr.getWidth(keyLabel)) / 2, y + 7,
                capturing ? 0xFF02101C : RkbUi.TEXT);
        hits.add(new Hit(kx, y + 3, kx + kw, y + 20, true, () -> listening = capturing ? null : ui));
        y += ROW_H + 4;

        text(ctx, tr, "Control and movement modules", listX + 4, y, accent);
        y += 14;
        for (Category category : new Category[]{Category.CONTROLS, Category.MOVEMENT}) {
            for (Module m : ModuleManager.get().inCategory(category)) {
                y = drawQuickModuleRow(ctx, tr, accent, m, y, cw, right);
            }
        }
        return y;
    }

    private int drawModulesGroup(DrawContext ctx, TextRenderer tr, int accent, int y, int cw, int right) {
        rowBg(ctx, y, cw);
        text(ctx, tr, "Module browser", listX + 10, y + 6, RkbUi.TEXT);
        button(ctx, tr, "Open", right, y + 3, true, () -> openTab(PAGE_MODS));
        y += ROW_H + 4;

        NameTagBadgeModule badge = NameTagBadgeModule.get();
        if (badge == null) {
            return y;
        }
        text(ctx, tr, "NAME TAG BADGE", listX + 4, y, accent);
        y += 14;
        y = drawQuickModuleRow(ctx, tr, accent, badge, y, cw, right);
        if (badge.isEnabled()) {
            for (Setting<?> s : badge.settings()) {
                if (s.visible()) {
                    y = drawSettingRow(ctx, tr, accent, s, y, cw, right);
                }
            }
        } else {
            for (String line : wrap(tr, "Off by default. While it is off no badge is drawn and nothing is sent to the cloud.",
                    cw - 8)) {
                text(ctx, tr, line, listX + 4, y, RkbUi.MUTED);
                y += 12;
            }
            y += 4;
        }
        return y;
    }

    private int drawOtherGroup(DrawContext ctx, TextRenderer tr, int accent, int y, int cw, int right) {
        AuthState auth = CloudService.authState();
        rowBg(ctx, y, cw);
        text(ctx, tr, "RKB account", listX + 10, y + 6, RkbUi.TEXT);
        int accountX = right - 150;
        text(ctx, tr, fit(tr, auth.accountLabel(), Math.max(40, right - 44 - accountX)), accountX, y + 6,
                auth.signedIn() ? accent : RkbUi.MUTED);
        button(ctx, tr, "Account", right, y + 3, true, RkbScreens::account);
        y += ROW_H;

        rowBg(ctx, y, cw);
        text(ctx, tr, "Cloud status", listX + 10, y + 6, RkbUi.TEXT);
        int cloudX = right - 130;
        text(ctx, tr, fit(tr, CloudService.statusText(), Math.max(40, right - 44 - cloudX)), cloudX, y + 6,
                RkbUi.MUTED);
        if (CloudStore.signedIn()) {
            button(ctx, tr, "Sync", right, y + 3, true, CloudService::manualSync);
        }
        y += ROW_H;

        rowBg(ctx, y, cw);
        text(ctx, tr, "Friends (" + FriendsManager.size() + ")", listX + 10, y + 6, RkbUi.TEXT);
        button(ctx, tr, "Manage", right, y + 3, true, () -> {
            ConfigManager.save();
            MinecraftClient.getInstance().setScreen(new FriendsScreen());
        });
        y += ROW_H;

        rowBg(ctx, y, cw);
        text(ctx, tr, "Reset all modules", listX + 10, y + 6, RkbUi.TEXT);
        button(ctx, tr, confirmReset ? "Confirm reset" : "Reset", right, y + 3, true, () -> {
            if (confirmReset) {
                ConfigManager.resetAll();
                confirmReset = false;
                showToast("All modules reset");
            } else {
                confirmReset = true;
            }
        });
        y += ROW_H + 8;

        String[] lines = {
                "Local files: config/rkbclient.json and config/rkbclient-profiles/.",
                "Cloud: Supabase Auth + the RKB Cosmetics V2 function.",
                "Minecraft access tokens never leave this device."
        };
        for (String line : lines) {
            for (String w : wrap(tr, line, cw - 8)) {
                text(ctx, tr, w, listX + 4, y, RkbUi.MUTED);
                y += 12;
            }
            y += 2;
        }
        return y;
    }

    // ------------------------------------------------------------------ profiles page

    private void drawProfiles(DrawContext ctx, TextRenderer tr, int accent) {
        int cw = listW - 6;
        int right = listX + cw - 8;
        int startY = listY - Math.round(scroll);
        int y = startY;

        for (int slot = 1; slot <= ConfigManager.PROFILE_SLOTS; slot++) {
            final int s = slot;
            boolean exists = ConfigManager.profileExists(slot);
            RkbUi.panel(ctx, new RkbUi.Rect(listX, y, cw, 44), RkbUi.SURFACE, RkbUi.LINE);
            text(ctx, tr, "Profile " + slot, listX + 10, y + 8, RkbUi.TEXT);
            text(ctx, tr, exists ? "Saved setup" : "Empty slot", listX + 10, y + 22,
                    exists ? RkbUi.SUCCESS : RkbUi.MUTED);
            button(ctx, tr, "Save", right, y + 13, true, () ->
                    showToast(ConfigManager.saveProfile(s) ? "Saved Profile " + s : "Could not save Profile " + s));
            int loadRight = right - Math.max(34, tr.getWidth("Save") + 16) - 6;
            int lx = button(ctx, tr, "Load", loadRight, y + 13, exists, () ->
                    showToast(ConfigManager.loadProfile(s) ? "Loaded Profile " + s : "Could not load Profile " + s));
            if (!exists) {
                text(ctx, tr, "Load", lx + 10, y + 8, RkbUi.MUTED);
            }
            y += 50;
        }

        y += 4;
        text(ctx, tr, "TOOLS", listX + 4, y, accent);
        y += 14;
        RkbUi.panel(ctx, new RkbUi.Rect(listX, y, cw, 26), RkbUi.SURFACE, RkbUi.LINE);
        text(ctx, tr, "HUD Editor", listX + 10, y + 9, RkbUi.TEXT);
        button(ctx, tr, "Open", right, y + 4, true, () -> {
            ConfigManager.save();
            MinecraftClient.getInstance().setScreen(new HudEditorScreen());
        });
        y += 32;

        RkbUi.panel(ctx, new RkbUi.Rect(listX, y, cw, 26), RkbUi.SURFACE, RkbUi.LINE);
        text(ctx, tr, "Settings", listX + 10, y + 9, RkbUi.TEXT);
        button(ctx, tr, "Open", right, y + 4, true, () -> openTab(PAGE_SETTINGS));
        y += 36;

        for (String line : wrap(tr, "A profile stores every module state, setting and keybind. Loading one replaces "
                + "your current setup.", cw - 8)) {
            text(ctx, tr, line, listX + 4, y, RkbUi.MUTED);
            y += 12;
        }
        contentH = y + 4 - startY;
    }

    // ------------------------------------------------------------------ about page

    private void drawAbout(DrawContext ctx, TextRenderer tr, int accent) {
        int cw = listW - 6;
        int startY = listY - Math.round(scroll);
        int y = startY;

        RkbUi.panel(ctx, new RkbUi.Rect(listX, y, cw, 52), RkbUi.SURFACE_3, RkbUi.LINE);
        int logo = 30;
        if (RkbLogo.ready()) {
            RkbLogo.draw(ctx, listX + 12, y + 11, logo);
        }
        text(ctx, tr, "RKB CLIENT " + VersionInfo.mod(), listX + 52, y + 12, RkbUi.TEXT);
        text(ctx, tr, "Minecraft " + VersionInfo.minecraft() + "  ·  Fabric " + VersionInfo.loader(),
                listX + 52, y + 26, RkbUi.MUTED);
        text(ctx, tr, "RKB UI refresh for 0.9.0", listX + 52, y + 38, accent);
        y += 62;

        String[] lines = {
                "Menu key: " + KeyUtil.name(ModuleManager.get().ui().keyCode()) + " or the /rkb command.",
                "Every key is also listed in Options > Controls > RKB CLIENT.",
                "Settings: config/rkbclient.json. Profiles: config/rkbclient-profiles/.",
                "Cloud: config/rkbclient-cloud.json. Tokens stay on this device.",
                "Original RKB design, inspired by modern PvP clients. Not affiliated with Feather Client,",
                "Mojang or Microsoft.",
                "Licence: MIT."
        };
        for (String l : lines) {
            for (String w : wrap(tr, l, cw - 8)) {
                text(ctx, tr, w, listX + 4, y, RkbUi.MUTED);
                y += 12;
            }
        }
        y += 6;
        AuthState auth = CloudService.authState();
        RkbUi.panel(ctx, new RkbUi.Rect(listX, y, cw, 26), RkbUi.SURFACE, RkbUi.LINE);
        RkbUi.statusDot(ctx, listX + 10, y + 10, auth.tone());
        text(ctx, tr, fit(tr, auth.accountLabel() + "  ·  " + auth.linkLabel(), cw - 120),
                listX + 20, y + 9, RkbUi.TEXT);
        button(ctx, tr, "Account", listX + cw - 8, y + 4, true, RkbScreens::account);
        contentH = y + 34 - startY;
    }
}
