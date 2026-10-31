package com.rkb.client.mixin;

import com.rkb.client.module.impl.NameTagBadgeModule;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds the RKB "R" badge to a player's display name (used by name tags). require = 0: if the method is
 * renamed in a future version the game still starts and the badge is simply not shown.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true, require = 0)
    private void rkbclient$badge(CallbackInfoReturnable<Text> cir) {
        if (NameTagBadgeModule.applies(this)) {
            cir.setReturnValue(NameTagBadgeModule.decorate(cir.getReturnValue()));
        }
    }
}
