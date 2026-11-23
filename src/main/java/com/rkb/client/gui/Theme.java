package com.rkb.client.gui;

import com.rkb.client.module.ModuleManager;
import com.rkb.client.module.impl.InterfaceModule;

/** RKB palette. All colours are ARGB. */
public final class Theme {
    public static final int BLACK = 0xFF070A12;
    public static final int PANEL = 0xFF070A12;
    public static final int CARD = 0xFF0E1422;
    public static final int CARD_HOVER = 0xFF141D30;
    public static final int CARD_INNER = 0xFF0A101C;
    public static final int BORDER = 0xFF1B263D;
    public static final int CYAN = 0xFF00A8FF;
    public static final int BLUE = 0xFF246BFF;
    public static final int TEXT = 0xFFF3F6FF;
    public static final int MUTED = 0xFF96A8BE;
    public static final int OFF = 0xFF26324A;
    public static final int DIM = 0xC0070A12;

    private Theme() {
    }

    private static InterfaceModule ui() {
        return ModuleManager.get().ui();
    }

    public static int accent() {
        InterfaceModule m = ui();
        return m == null ? CYAN : m.accent();
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
