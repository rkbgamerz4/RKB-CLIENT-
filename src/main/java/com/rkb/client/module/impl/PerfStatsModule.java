package com.rkb.client.module.impl;

import com.rkb.client.hud.TextHudModule;
import com.rkb.client.module.Category;
import net.minecraft.client.MinecraftClient;

import java.util.List;
import java.util.Locale;

/** Average and lowest FPS over the last 120 frames, plus average frame time. */
public class PerfStatsModule extends TextHudModule {
    private static final int N = 120;

    private final long[] frames = new long[N];
    private int index;
    private int filled;
    private int counter;
    private long last;
    private List<String> cached = List.of();

    public PerfStatsModule() {
        super("perf_stats", "Performance Stats", "Average and lowest FPS over the last 120 frames, plus frame time.",
                Category.PERFORMANCE, "Top Left", 4, 74);
    }

    @Override
    protected void onEnable() {
        last = 0;
        filled = 0;
        index = 0;
        cached = List.of();
    }

    @Override
    protected void prepare(MinecraftClient mc) {
        long now = System.nanoTime();
        if (last != 0) {
            frames[index] = now - last;
            index = (index + 1) % N;
            if (filled < N) {
                filled++;
            }
        }
        last = now;
        if (++counter >= 20 && filled > 5) {
            counter = 0;
            long sum = 0;
            long max = 0;
            for (int i = 0; i < filled; i++) {
                sum += frames[i];
                max = Math.max(max, frames[i]);
            }
            double avgFps = 1_000_000_000.0 * filled / sum;
            double minFps = 1_000_000_000.0 / max;
            cached = List.of(
                    String.format(Locale.ROOT, "FPS avg %d  low %d", (int) avgFps, (int) minFps),
                    String.format(Locale.ROOT, "Frame %.1f ms", sum / (double) filled / 1_000_000.0));
        }
        super.prepare(mc);
    }

    @Override
    protected List<String> lines(MinecraftClient mc) {
        return cached;
    }
}
