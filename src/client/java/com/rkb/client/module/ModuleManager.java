package com.rkb.client.module;

import com.rkb.client.modules.render.ZoomModule;

import java.util.ArrayList;
import java.util.List;

public class ModuleManager {

    private static final List<Module> MODULES = new ArrayList<>();

    static {
        MODULES.add(new ZoomModule());

        MODULES.add(new Module("FPS Display", ModuleCategory.HUD));
        MODULES.add(new Module("Armor Status", ModuleCategory.HUD));
        MODULES.add(new Module("Motion Blur", ModuleCategory.VISUAL));
    }

    public static List<Module> getModules() {
        return MODULES;
    }

    public static List<Module> getModules(ModuleCategory category) {
        return MODULES.stream()
                .filter(module -> module.getCategory() == category)
                .toList();
    }
}
