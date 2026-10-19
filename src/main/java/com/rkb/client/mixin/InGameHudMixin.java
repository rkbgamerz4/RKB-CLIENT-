package com.rkb.client.mixin;

import com.rkb.client.module.impl.CrosshairModule;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the vanilla crosshair while the RKB crosshair module is active.
 * require = 0: if the target method is renamed in a future version the game still starts,
 * and the module simply draws next to the vanilla crosshair.
 */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true, require = 0)
    private void rkbclient$hideVanillaCrosshair(CallbackInfo ci) {
        if (CrosshairModule.shouldHideVanilla()) {
            ci.cancel();
        }
    }
}
