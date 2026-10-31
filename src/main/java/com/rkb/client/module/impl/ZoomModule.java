package com.rkb.client.module.impl;

import com.rkb.client.module.Category;
import com.rkb.client.module.Module;
import com.rkb.client.module.setting.IntSetting;
import com.rkb.client.module.setting.Setting;
import net.minecraft.client.MinecraftClient;

/** Lowers the FOV option while enabled and restores it afterwards. Bind a key to toggle it. */
public class ZoomModule extends Module {
    private final IntSetting zoomFov = add(new IntSetting("zoom_fov", "Zoom FOV", 30, 70, 5, 30));
    private int original = -1;

    public ZoomModule() {
        super("zoom", "Zoom", "Narrows your field of view while on. Bind a key to toggle it.", Category.VISUALS);
    }

    /** Never stored as enabled, so a crash can't leave the game zoomed at next launch. */
    @Override
    public boolean persistEnabled() {
        return false;
    }

    @Override
    protected void onEnable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options == null) {
            return;
        }
        original = mc.options.getFov().getValue();
        mc.options.getFov().setValue(zoomFov.value());
    }

    @Override
    protected void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null && original > 0) {
            mc.options.getFov().setValue(original);
        }
        original = -1;
    }

    @Override
    protected void onSettingChanged(Setting<?> setting) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (isEnabled() && mc.options != null && original > 0) {
            mc.options.getFov().setValue(zoomFov.value());
        }
    }
}
