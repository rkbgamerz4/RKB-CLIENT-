package com.rkb.client.module.impl;

import com.rkb.client.module.Category;
import com.rkb.client.module.Module;
import com.rkb.client.module.setting.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;

import java.util.Objects;

/**
 * Sets one vanilla video/control option while enabled and restores the player's own value when disabled.
 * If the game crashes while such a module is on, the option keeps the changed value until you reset it
 * in Options.
 */
public abstract class OptionOverrideModule<T> extends Module {
    private T original;
    private boolean saved;

    protected OptionOverrideModule(String id, String name, String description, Category category) {
        super(id, name, description, category);
    }

    protected abstract SimpleOption<T> option(GameOptions options);

    protected abstract T target();

    private void apply(MinecraftClient mc) {
        if (mc.options == null) {
            return;
        }
        SimpleOption<T> o = option(mc.options);
        if (!saved) {
            original = o.getValue();
            saved = true;
        }
        T t = target();
        if (!Objects.equals(o.getValue(), t)) {
            o.setValue(t);
        }
    }

    @Override
    protected void onEnable() {
        apply(MinecraftClient.getInstance());
    }

    @Override
    protected void onTick(MinecraftClient mc) {
        apply(mc);
    }

    @Override
    protected void onSettingChanged(Setting<?> setting) {
        if (isEnabled()) {
            apply(MinecraftClient.getInstance());
        }
    }

    @Override
    protected void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (saved && mc.options != null) {
            option(mc.options).setValue(original);
        }
        saved = false;
        original = null;
    }
}
