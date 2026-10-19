package com.rkb.client.input;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/** GLFW key helpers. Keybinds are stored as raw GLFW key codes (-1 = unbound). */
public final class KeyUtil {
    public static final int UNBOUND = -1;
    public static final int FIRST_KEY = GLFW.GLFW_KEY_SPACE;
    public static final int LAST_KEY = GLFW.GLFW_KEY_LAST;

    private static final Map<Integer, String> NAMES = new HashMap<>();

    static {
        NAMES.put(GLFW.GLFW_KEY_SPACE, "Space");
        NAMES.put(GLFW.GLFW_KEY_ESCAPE, "Esc");
        NAMES.put(GLFW.GLFW_KEY_ENTER, "Enter");
        NAMES.put(GLFW.GLFW_KEY_TAB, "Tab");
        NAMES.put(GLFW.GLFW_KEY_BACKSPACE, "Backspace");
        NAMES.put(GLFW.GLFW_KEY_INSERT, "Insert");
        NAMES.put(GLFW.GLFW_KEY_DELETE, "Delete");
        NAMES.put(GLFW.GLFW_KEY_RIGHT, "Right");
        NAMES.put(GLFW.GLFW_KEY_LEFT, "Left");
        NAMES.put(GLFW.GLFW_KEY_DOWN, "Down");
        NAMES.put(GLFW.GLFW_KEY_UP, "Up");
        NAMES.put(GLFW.GLFW_KEY_PAGE_UP, "Page Up");
        NAMES.put(GLFW.GLFW_KEY_PAGE_DOWN, "Page Down");
        NAMES.put(GLFW.GLFW_KEY_HOME, "Home");
        NAMES.put(GLFW.GLFW_KEY_END, "End");
        NAMES.put(GLFW.GLFW_KEY_CAPS_LOCK, "Caps Lock");
        NAMES.put(GLFW.GLFW_KEY_LEFT_SHIFT, "Left Shift");
        NAMES.put(GLFW.GLFW_KEY_RIGHT_SHIFT, "Right Shift");
        NAMES.put(GLFW.GLFW_KEY_LEFT_CONTROL, "Left Ctrl");
        NAMES.put(GLFW.GLFW_KEY_RIGHT_CONTROL, "Right Ctrl");
        NAMES.put(GLFW.GLFW_KEY_LEFT_ALT, "Left Alt");
        NAMES.put(GLFW.GLFW_KEY_RIGHT_ALT, "Right Alt");
        NAMES.put(GLFW.GLFW_KEY_GRAVE_ACCENT, "`");
        NAMES.put(GLFW.GLFW_KEY_MINUS, "-");
        NAMES.put(GLFW.GLFW_KEY_EQUAL, "=");
        NAMES.put(GLFW.GLFW_KEY_COMMA, ",");
        NAMES.put(GLFW.GLFW_KEY_PERIOD, ".");
        NAMES.put(GLFW.GLFW_KEY_SLASH, "/");
        NAMES.put(GLFW.GLFW_KEY_SEMICOLON, ";");
        NAMES.put(GLFW.GLFW_KEY_APOSTROPHE, "'");
        NAMES.put(GLFW.GLFW_KEY_LEFT_BRACKET, "[");
        NAMES.put(GLFW.GLFW_KEY_RIGHT_BRACKET, "]");
        NAMES.put(GLFW.GLFW_KEY_BACKSLASH, "\\");
        for (int i = 0; i < 12; i++) {
            NAMES.put(GLFW.GLFW_KEY_F1 + i, "F" + (i + 1));
        }
        for (int i = 0; i <= 9; i++) {
            NAMES.put(GLFW.GLFW_KEY_0 + i, String.valueOf(i));
            NAMES.put(GLFW.GLFW_KEY_KP_0 + i, "Num " + i);
        }
        for (int i = 0; i < 26; i++) {
            NAMES.put(GLFW.GLFW_KEY_A + i, String.valueOf((char) ('A' + i)));
        }
    }

    private KeyUtil() {
    }

    public static boolean isValid(int key) {
        return key >= FIRST_KEY && key <= LAST_KEY;
    }

    public static boolean isDown(MinecraftClient mc, int key) {
        if (!isValid(key) || mc == null || mc.getWindow() == null) {
            return false;
        }
        return GLFW.glfwGetKey(mc.getWindow().getHandle(), key) == GLFW.GLFW_PRESS;
    }

    public static boolean isMouseDown(MinecraftClient mc, int button) {
        if (mc == null || mc.getWindow() == null) {
            return false;
        }
        return GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), button) == GLFW.GLFW_PRESS;
    }

    public static String name(int key) {
        if (!isValid(key)) {
            return "None";
        }
        return NAMES.getOrDefault(key, "Key " + key);
    }
}
