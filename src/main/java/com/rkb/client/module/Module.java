package com.rkb.client.module;

import com.rkb.client.RkbClient;
import com.rkb.client.input.KeyUtil;
import com.rkb.client.module.setting.Setting;
import net.minecraft.client.MinecraftClient;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Base class for every RKB CLIENT module. */
public abstract class Module {
    private static final int MAX_FAILURES = 3;

    private final String id;
    private final String name;
    private final String description;
    private final Category category;
    private final List<Setting<?>> settings = new ArrayList<>();

    private boolean enabled;
    /** True once onEnable() has run for the current enabled state. */
    private boolean active;
    /** Default key until a binding is attached; afterwards the last key set through setKeyCode(). */
    private int keyCode = KeyUtil.UNBOUND;
    private KeyBinding binding;
    private boolean keyPinned;
    private int failures;

    protected Module(String id, String name, String description, Category category) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
    }

    protected <S extends Setting<?>> S add(S setting) {
        setting.setListener(() -> {
            try {
                onSettingChanged(setting);
            } catch (Throwable t) {
                RkbClient.LOGGER.error("Module '{}' failed in onSettingChanged", id, t);
            }
        });
        settings.add(setting);
        return setting;
    }

    public final String id() {
        return id;
    }

    public final String name() {
        return name;
    }

    public final String description() {
        return description;
    }

    public final Category category() {
        return category;
    }

    public final List<Setting<?>> settings() {
        return Collections.unmodifiableList(settings);
    }

    public final boolean isEnabled() {
        return enabled || !isToggleable();
    }

    /** Declares the default key; call from the subclass constructor only. */
    protected final void defaultKey(int key) {
        this.keyCode = key;
    }

    /** Default key, valid only before the vanilla key binding is attached. */
    public final int defaultKeyCode() {
        return keyCode;
    }

    public final void attachBinding(KeyBinding binding) {
        this.binding = binding;
    }

    /** Current key. Reads the vanilla key binding so rebinding in Options > Controls is respected. */
    public final int keyCode() {
        if (binding != null) {
            return binding.isUnbound() ? KeyUtil.UNBOUND : KeyBindingHelper.getBoundKeyOf(binding).getCode();
        }
        return keyCode;
    }

    public void setKeyCode(int key) {
        int k = KeyUtil.isValid(key) ? key : KeyUtil.UNBOUND;
        this.keyCode = k;
        this.keyPinned = true;
        applyKey(k);
    }

    /** Re-applies a key from the config file (options.txt may be read after mod init). */
    public final void reapplyPinnedKey() {
        if (keyPinned) {
            applyKey(keyCode);
        }
    }

    private void applyKey(int k) {
        if (binding == null) {
            return;
        }
        binding.setBoundKey(InputUtil.Type.KEYSYM.createFromCode(k));
        KeyBinding.updateKeysByCode();
    }

    /** Modules that return false are always on and cannot be switched off. */
    public boolean isToggleable() {
        return true;
    }

    /** Whether the enabled flag is written to the config file. */
    public boolean persistEnabled() {
        return true;
    }

    /** Applies a loaded enabled flag without running hooks; see activateLoaded(). */
    public final void restoreEnabled(boolean value) {
        if (isToggleable()) {
            this.enabled = value;
            this.active = false;
        }
    }

    /** Runs onEnable() for modules restored as enabled. Called once the client is ready. */
    public final void activateIfNeeded(MinecraftClient mc) {
        if (enabled && !active) {
            runEnableHook();
        }
    }

    public final void setEnabled(boolean value) {
        if (!isToggleable() || value == enabled) {
            return;
        }
        enabled = value;
        if (value) {
            failures = 0;
            runEnableHook();
        } else {
            runDisableHook();
        }
    }

    public final void toggle() {
        setEnabled(!enabled);
    }

    private void runEnableHook() {
        active = true;
        try {
            onEnable();
        } catch (Throwable t) {
            RkbClient.LOGGER.error("Module '{}' failed to enable; disabling it.", id, t);
            enabled = false;
            active = false;
        }
    }

    private void runDisableHook() {
        active = false;
        try {
            onDisable();
        } catch (Throwable t) {
            RkbClient.LOGGER.error("Module '{}' failed to disable cleanly.", id, t);
        }
    }

    /** Called on shutdown: runs onDisable() so modules can restore game state. */
    public final void shutdown() {
        if (enabled && active) {
            runDisableHook();
        }
    }

    public final void tickSafe(MinecraftClient mc) {
        if (!enabled || !active) {
            return;
        }
        try {
            onTick(mc);
        } catch (Throwable t) {
            handleFailure("tick", t);
        }
    }

    public final void renderSafe(DrawContext context, MinecraftClient mc) {
        if (!enabled || !active) {
            return;
        }
        try {
            renderHud(context, mc);
        } catch (Throwable t) {
            handleFailure("render", t);
        }
    }

    private void handleFailure(String where, Throwable t) {
        failures++;
        RkbClient.LOGGER.error("Module '{}' failed during {} ({}/{}).", id, where, failures, MAX_FAILURES, t);
        if (failures >= MAX_FAILURES) {
            RkbClient.LOGGER.error("Disabling module '{}' after repeated failures.", id);
            setEnabled(false);
        }
    }

    /** Called when the user presses this module's keybind (in-world, no screen open). */
    public void onKeyPressed(MinecraftClient mc) {
        toggle();
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    protected void onTick(MinecraftClient mc) {
    }

    protected void onSettingChanged(Setting<?> setting) {
    }

    public void renderHud(DrawContext context, MinecraftClient mc) {
    }
}
