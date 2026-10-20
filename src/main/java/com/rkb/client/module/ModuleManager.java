package com.rkb.client.module;

import com.rkb.client.module.impl.ArmorHudModule;
import com.rkb.client.module.impl.PerfStatsModule;
import com.rkb.client.module.impl.PingModule;
import com.rkb.client.module.impl.PotionHudModule;
import com.rkb.client.module.impl.SessionTimerModule;
import com.rkb.client.module.impl.ToggleSneakModule;
import com.rkb.client.module.impl.WorldInfoModule;
import com.rkb.client.module.impl.ClockModule;
import com.rkb.client.module.impl.CoordinatesModule;
import com.rkb.client.module.impl.CpsModule;
import com.rkb.client.module.impl.CrosshairModule;
import com.rkb.client.module.impl.FpsModule;
import com.rkb.client.module.impl.InfoHudModule;
import com.rkb.client.module.impl.InterfaceModule;
import com.rkb.client.module.impl.KeystrokesModule;
import com.rkb.client.module.impl.MemoryModule;
import com.rkb.client.module.impl.MinimapModule;
import com.rkb.client.module.impl.ToggleSprintModule;
import com.rkb.client.module.impl.ZoomModule;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Registry of all modules plus keybind polling. */
public final class ModuleManager {
    private static ModuleManager instance;

    private final Map<String, Module> modules = new LinkedHashMap<>();
    private List<Module> allCache = List.of();
    private final Map<Category, List<Module>> byCategory = new EnumMap<>(Category.class);
    private InterfaceModule ui;

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
        instance.register(new ToggleSneakModule());
        instance.register(new ArmorHudModule());
        instance.register(new PotionHudModule());
        instance.register(new PingModule());
        instance.register(new SessionTimerModule());
        instance.register(new PerfStatsModule());
        instance.register(new WorldInfoModule());
        instance.register(new MinimapModule());
        instance.freeze();
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

    /** Builds immutable lookup caches once, so per-frame callers never allocate. */
    private void freeze() {
        allCache = List.copyOf(modules.values());
        for (Module m : allCache) {
            m.captureDefaults();
        }
        for (Category c : Category.values()) {
            List<Module> l = new ArrayList<>();
            for (Module m : allCache) {
                if (m.category() == c) {
                    l.add(m);
                }
            }
            byCategory.put(c, List.copyOf(l));
        }
        ui = byType(InterfaceModule.class);
    }

    public List<Module> all() {
        return allCache;
    }

    public List<Module> inCategory(Category category) {
        return byCategory.getOrDefault(category, List.of());
    }

    /** Cached interface settings module (never null after init). */
    public InterfaceModule ui() {
        return ui;
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
        for (Module m : allCache) {
            m.activateIfNeeded(mc);
        }
    }

    public void tick(MinecraftClient mc) {
        for (Module m : allCache) {
            m.tickSafe(mc);
        }
    }

    public void shutdown(MinecraftClient mc) {
        for (Module m : allCache) {
            m.shutdown();
        }
    }
}
