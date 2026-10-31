package com.rkb.client.module.impl;

import com.rkb.client.hud.TextHudModule;
import com.rkb.client.module.Category;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;

import java.util.List;

/** Block and sky light at your feet. Useful for judging where mobs can spawn. */
public class LightLevelModule extends TextHudModule {
    private final BlockPos.Mutable pos = new BlockPos.Mutable();
    private int tick;
    private List<String> cached = List.of();

    public LightLevelModule() {
        super("light_level", "Light Level", "Shows block and sky light where you stand.",
                Category.WORLD, "Top Left", 120, 90);
    }

    @Override
    protected void onTick(MinecraftClient mc) {
        if (++tick < 5) {
            return;
        }
        tick = 0;
        if (mc.world == null || mc.player == null) {
            cached = List.of();
            return;
        }
        pos.set(mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
        int block = mc.world.getLightLevel(LightType.BLOCK, pos);
        int sky = mc.world.getLightLevel(LightType.SKY, pos);
        cached = List.of("Light: block " + block + "  sky " + sky);
    }

    @Override
    protected List<String> lines(MinecraftClient mc) {
        return cached;
    }
}
