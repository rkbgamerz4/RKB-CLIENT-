package com.rkb.client.mixin;

import com.rkb.client.cosmetics.CosmeticsRender;
import com.rkb.client.module.impl.NameTagBadgeModule;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * Adds RKB badges to a player's display name (used by name tags): the badge the player equipped in their RKB account,
 * and the friends/everyone name tag badge. require = 0: if the method is renamed in a future version the game still
 * starts and badges are simply not shown.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true, require = 0)
    private void rkbclient$badge(CallbackInfoReturnable<Text> cir) {
        Text original = cir.getReturnValue();
        UUID id = ((Entity) (Object) this).getUuid();
        Text out = CosmeticsRender.withBadge(id, original);
        // A known RKB player already has the logo/equipped badge from the cosmetics lookup. Only use the older
        // friends/everyone badge for players the cosmetics backend does not know, so nothing is drawn twice.
        if (!CosmeticsRender.hasRkbIdentity(id) && NameTagBadgeModule.applies(this)) {
            out = NameTagBadgeModule.decorate(out);
        }
        if (out != original) {
            cir.setReturnValue(out);
        }
    }
}
