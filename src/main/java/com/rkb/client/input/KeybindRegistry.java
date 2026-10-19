package com.rkb.client.input;

import com.rkb.client.RkbClient;
import com.rkb.client.module.Module;
import com.rkb.client.module.ModuleManager;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registers one vanilla key binding per module (visible in Options > Controls) and consumes key
 * presses from Minecraft's own input events. No GLFW polling, so it behaves the same on desktop
 * and on Android launchers whose GLFW layer is emulated.
 */
public final class KeybindRegistry {
    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of(RkbClient.MOD_ID, "main"));

    private static final Map<Module, KeyBinding> BY_MODULE = new HashMap<>();
    private static Module[] modules = new Module[0];
    private static KeyBinding[] bindings = new KeyBinding[0];

    private KeybindRegistry() {
    }

    public static void registerAll(ModuleManager manager) {
        List<Module> all = manager.all();
        modules = new Module[all.size()];
        bindings = new KeyBinding[all.size()];
        for (int i = 0; i < all.size(); i++) {
            Module m = all.get(i);
            KeyBinding kb = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                    "key." + RkbClient.MOD_ID + "." + m.id(), InputUtil.Type.KEYSYM, m.defaultKeyCode(), CATEGORY));
            m.attachBinding(kb);
            modules[i] = m;
            bindings[i] = kb;
            BY_MODULE.put(m, kb);
        }
    }

    public static KeyBinding bindingOf(Module m) {
        return m == null ? null : BY_MODULE.get(m);
    }

    /** Called once on the first tick, after Minecraft has loaded options.txt. */
    public static void reapplyPinnedKeys() {
        for (Module m : modules) {
            m.reapplyPinnedKey();
        }
    }

    /** Runs each client tick: consumes queued presses and fires the module actions. */
    public static void tick(MinecraftClient mc) {
        for (int i = 0; i < bindings.length; i++) {
            KeyBinding kb = bindings[i];
            while (kb.wasPressed()) {
                if (mc.currentScreen == null) {
                    try {
                        modules[i].onKeyPressed(mc);
                    } catch (Throwable t) {
                        RkbClient.LOGGER.error("Keybind action for '{}' failed", modules[i].id(), t);
                    }
                }
            }
        }
    }
}
