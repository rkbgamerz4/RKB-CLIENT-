package com.rkb.client.gui;

import com.rkb.client.module.ModuleManager;
import com.rkb.client.module.impl.InterfaceModule;

/** RKB palette. All colours are ARGB. */
public final class Theme {
    public static final int BLACK = 0xFF101216;
    public static final int PANEL = 0xFF101216;
    public static final int CARD = 0xFF1C2028;
    public static final int CARD_HOVER = 0xFF222733;
    public static final int CARD_INNER = 0xFF171A20;
    public static final int BORDER = 0xFF292E38;
    public static final int CYAN = 0xFF3988D8;
    public static final int BLUE = 0xFF3988D8;
    public static final int TEXT = 0xFFF0F1F3;
    public static final int MUTED = 0xFFA0A6B0;
    public static final int OFF = 0xFF232833;
    public static final int DIM = 0xCC101216;
    /** Fixed menu chrome accent. HUD uses {@link #hudAccent()}. */
    public static final int CHROME_ACCENT = 0xFF3988D8;
    public static final int CHROME_ACCENT_HOVER = 0xFF4B98E6;

    private Theme() {
    }

    private static InterfaceModule ui() {
        return ModuleManager.get().ui();
    }

    /** Fixed accent for menu chrome and non-HUD UI. */
    public static int chromeAccent() {
        return CHROME_ACCENT;
    }

    /** User-configured HUD accent. Do not use for menu chrome. */
    public static int hudAccent() {
        InterfaceModule m = ui();
        return m == null ? 0xFF00A8FF : m.accent();
    }

    /** @deprecated use {@link #hudAccent()} for HUD or {@link #chromeAccent()} for menu chrome. */
    @Deprecated
    public static int accent() {
        return hudAccent();
    }

    public static int hudText() {
        InterfaceModule m = ui();
        return m == null ? TEXT : m.hudTextColor();
    }

    public static boolean hudBackground() {
        InterfaceModule m = ui();
        return m == null || m.hudBackground();
    }

    public static boolean hudShadow() {
        InterfaceModule m = ui();
        return m == null || m.hudShadow();
    }

    public static boolean menuShowFps() {
        InterfaceModule m = ui();
        return m == null || m.menuShowFps();
    }

    public static boolean menuShowVersion() {
        InterfaceModule m = ui();
        return m == null || m.menuShowVersion();
    }
}
