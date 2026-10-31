package com.rkb.client.module.impl;

import com.rkb.client.hud.TextHudModule;
import com.rkb.client.module.Category;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Locale;

public class BiomeModule extends TextHudModule {
    private final BlockPos.Mutable pos = new BlockPos.Mutable();
    private int tick;
    private List<String> cached = List.of();

    public BiomeModule() {
        super("biome", "Biome Display", "Shows the biome you are standing in.", Category.WORLD, "Top Left", 120, 76);
    }

    @Override
    protected void onTick(MinecraftClient mc) {
        if (++tick < 10) {
            return;
        }
        tick = 0;
        if (mc.world == null || mc.player == null) {
            cached = List.of();
            return;
        }
        pos.set(mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
        String path = mc.world.getBiome(pos).getKey().map(k -> k.getValue().getPath()).orElse("unknown");
        StringBuilder sb = new StringBuilder("Biome: ");
        boolean up = true;
        for (char c : path.toLowerCase(Locale.ROOT).toCharArray()) {
            if (c == '_') {
                sb.append(' ');
                up = true;
            } else {
                sb.append(up ? Character.toUpperCase(c) : c);
                up = false;
            }
        }
        cached = List.of(sb.toString());
    }

    @Override
    protected List<String> lines(MinecraftClient mc) {
        return cached;
    }
}
