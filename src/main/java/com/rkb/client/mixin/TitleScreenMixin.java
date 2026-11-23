package com.rkb.client.mixin;

import com.rkb.client.gui.RkbMainMenuScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the vanilla title screen with RKB's real main menu.
 *
 * The replacement is deferred with {@code mc.execute} so it runs after vanilla has finished constructing/initialising
 * TitleScreen. Vanilla screens are still used for Singleplayer, Multiplayer and Options, so their functionality and
 * world/server list code are untouched.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {
    @Inject(method = "init", at = @At("TAIL"), require = 1)
    private void rkbclient$replaceTitle(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> {
            if (mc.currentScreen instanceof TitleScreen) {
                mc.setScreen(new RkbMainMenuScreen());
            }
        });
    }
}
