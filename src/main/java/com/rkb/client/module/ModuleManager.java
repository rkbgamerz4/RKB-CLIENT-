package com.rkb.client.module;

import com.rkb.client.input.KeyUtil;
import com.rkb.client.module.impl.ClockModule;
import com.rkb.client.module.impl.CoordinatesModule;
import com.rkb.client.module.impl.CpsModule;
import com.rkb.client.module.impl.CrosshairModule;
import com.rkb.client.module.impl.FpsModule;
import com.rkb.client.module.impl.InfoHudModule;
import com.rkb.client.module.impl.InterfaceModule;
import com.rkb.client.module.impl.KeystrokesModule;
import com.rkb.client.module.impl.MemoryModule;
import com.rkb.client.module.impl.ToggleSprintModule;
import com.rkb.client.module.impl.ZoomModule;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Registry of all modules plus keybind polling. */
public final class ModuleManager {
    private static ModuleManager instance;

    private final Map<String, Module> modules = new LinkedHashMap<>();
    private final boolean[] previousDown = new boolean[KeyUtil.LAST_KEY + 1];

    private ModuleManager() {
    }

    public static void init() {
        if (instance != null) {
            return;
        }
        instance = new ModuleManager();
        instance.register(new FpsModule());
        instance.register(new CoordinatesModule());
        instance.register(new InfoHudModule());
        instance.register(new KeystrokesModule());
        instance.register(new CrosshairModule());
        instance.register(new ZoomModule());
        instance.register(new MemoryModule());
        instance.register(new ClockModule());
        instance.register(new InterfaceModule());
        instance.register(new CpsModule());
        instance.register(new ToggleSprintModule());
    }

    public static ModuleManager get() {
        if (instance == null) {
            init();
        }
        return instance;
    }

    private void register(Module module) {
        if (modules.putIfAbsent(module.id(), module) != null) {
            throw new IllegalStateException("Duplicate module id: " + module.id());
        }
    }

    public List<Module> all() {
        return Collections.unmodifiableList(new ArrayList<>(modules.values()));
    }

    public List<Module> inCategory(Category category) {
        List<Module> out = new ArrayList<>();
        for (Module m : modules.values()) {
            if (m.category() == category) {
                out.add(m);
            }
        }
        return out;
    }

    public Module byId(String id) {
        return modules.get(id);
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T byType(Class<T> type) {
        for (Module m : modules.values()) {
            if (type.isInstance(m)) {
                return (T) m;
            }
        }
        return null;
    }

    public void activateLoaded(MinecraftClient mc) {
        for (Module m : modules.values()) {
            m.activateIfNeeded(mc);
        }
    }

    public void tick(MinecraftClient mc) {
        for (Module m : modules.values()) {
            m.tickSafe(mc);
        }
    }

    public void shutdown(MinecraftClient mc) {
        for (Module m : modules.values()) {
            m.shutdown();
        }
    }

    /**
     * Edge-detects bound keys. Safe to call several times per frame; keys only fire when
     * the player is in-world with no screen open.
     */
    public void pollKeys(MinecraftClient mc) {
        if (mc == null || mc.getWindow() == null) {
            return;
        }
        boolean free = mc.currentScreen == null;
        List<Module> fired = new ArrayList<>();
        for (Module m : modules.values()) {
            int key = m.effectiveKey();
            if (!KeyUtil.isValid(key)) {
                continue;
            }
            boolean down = KeyUtil.isDown(mc, key);
            if (down && !previousDown[key] && free) {
                fired.add(m);
            }
        }
        for (Module m : modules.values()) {
            int key = m.effectiveKey();
            if (KeyUtil.isValid(key)) {
                previousDown[key] = KeyUtil.isDown(mc, key);
            }
        }
        for (Module m : fired) {
            try {
                m.onKeyPressed(mc);
            } catch (Throwable t) {
                com.rkb.client.RkbClient.LOGGER.error("Keybind action for '{}' failed", m.id(), t);
            }
        }
    }
}
