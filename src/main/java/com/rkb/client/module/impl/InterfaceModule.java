package com.rkb.client.module.impl;

import com.rkb.client.gui.RkbMenuScreen;
import com.rkb.client.input.KeyUtil;
import com.rkb.client.module.Category;
import com.rkb.client.module.Module;
import com.rkb.client.module.setting.BoolSetting;
import com.rkb.client.module.setting.ChoiceSetting;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Global interface settings. Its keybind is the key that opens the menu (default Right Shift). */
public class InterfaceModule extends Module {
    private static final List<String> HUD_COLORS = List.of("White", "Cyan", "Blue", "Gray", "Green");
    private static final int[] HUD_COLOR_VALUES = {0xFFFFFFFF, 0xFF00A8FF, 0xFF246BFF, 0xFF96A8BE, 0xFF55FF7F};

    private final ChoiceSetting accent = add(new ChoiceSetting("accent", "Accent color", List.of("Cyan", "Blue"), "Cyan"));
    private final ChoiceSetting hudColor = add(new ChoiceSetting("hud_color", "HUD text color", HUD_COLORS, "White"));
    private final BoolSetting hudBackground = add(new BoolSetting("hud_background", "HUD background", true));
    private final BoolSetting hudShadow = add(new BoolSetting("hud_shadow", "HUD text shadow", true));
    private final BoolSetting menuFps = add(new BoolSetting("menu_fps", "FPS in menu header", true));
    private final BoolSetting menuVersion = add(new BoolSetting("menu_version", "Version in menu header", true));

    public InterfaceModule() {
        super("interface", "Interface",
                "Menu look, HUD style, and the menu key (click the key button to rebind).", Category.UTILITY);
        defaultKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    @Override
    public boolean isToggleable() {
        return false;
    }

    /** The menu key can never be cleared, otherwise the menu would become unreachable. */
    @Override
    public void setKeyCode(int key) {
        super.setKeyCode(KeyUtil.isValid(key) ? key : GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    @Override
    public void onKeyPressed(MinecraftClient mc) {
        mc.setScreen(new RkbMenuScreen());
    }

    public int accent() {
        return accent.index() == 1 ? 0xFF246BFF : 0xFF00A8FF;
    }

    public int hudTextColor() {
        return HUD_COLOR_VALUES[hudColor.index()];
    }

    public boolean hudBackground() {
        return hudBackground.on();
    }

    public boolean hudShadow() {
        return hudShadow.on();
    }

    public boolean menuShowFps() {
        return menuFps.on();
    }

    public boolean menuShowVersion() {
        return menuVersion.on();
    }
}
