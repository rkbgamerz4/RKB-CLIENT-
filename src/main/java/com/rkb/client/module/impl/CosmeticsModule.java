package com.rkb.client.module.impl;

import com.rkb.client.cosmetics.RemoteCosmetics;
import com.rkb.client.gui.CosmeticsScreen;
import com.rkb.client.module.Category;
import com.rkb.client.module.Module;
import com.rkb.client.module.setting.BoolSetting;
import com.rkb.client.module.setting.Setting;
import net.minecraft.client.MinecraftClient;

/**
 * Switch for the RKB account cosmetics: your equipped cape/badge is drawn on you, and other RKB players' capes and
 * badges are drawn on them. Off by default. While off nothing is drawn and no cosmetics request is sent. Ownership,
 * the shop and the inventory live in the account (see CosmeticsManager); this module only controls display.
 */
public class CosmeticsModule extends Module {
    private static CosmeticsModule instance;

    private final BoolSetting showOthers = add(
            new BoolSetting("show_others", "Show other players' cosmetics", true), () -> isEnabled());
    private final BoolSetting shareMine = add(
            new BoolSetting("share_mine", "Let others see my cosmetics", true), () -> isEnabled());

    public CosmeticsModule() {
        super("rkb_cosmetics", "RKB Cosmetics",
                "Show your RKB account's cape and badge, and other RKB players'. Off by default; needs an RKB account.",
                Category.VISUALS);
        instance = this;
        addAction("open_cosmetics", "Open cosmetics shop", mc -> mc.setScreen(new CosmeticsScreen()));
    }

    public static CosmeticsModule get() {
        return instance;
    }

    public boolean showOthers() {
        return showOthers.on();
    }

    public boolean shareMine() {
        return shareMine.on();
    }

    @Override
    protected void onEnable() {
        RemoteCosmetics.onFeatureChanged();
    }

    @Override
    protected void onDisable() {
        RemoteCosmetics.onFeatureChanged();
    }

    @Override
    protected void onSettingChanged(Setting<?> setting) {
        RemoteCosmetics.onFeatureChanged();
    }

    @Override
    protected void onTick(MinecraftClient mc) {
        RemoteCosmetics.updateSelfUuid(mc);
    }
}
