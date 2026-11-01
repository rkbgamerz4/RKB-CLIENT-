package com.rkb.client.mixin;

import com.rkb.client.cosmetics.CapeTextures;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * Swaps in an RKB account's equipped cape. Only reads in-memory maps (no network, no disk). require = 0: if the
 * method is renamed in a future version the game still starts and capes are simply not shown.
 */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true, require = 0)
    private void rkbclient$cape(CallbackInfoReturnable<SkinTextures> cir) {
        UUID id = ((Entity) (Object) this).getUuid();
        SkinTextures original = cir.getReturnValue();
        SkinTextures replaced = CapeTextures.apply(id, original);
        if (replaced != original) {
            cir.setReturnValue(replaced);
        }
    }
}
