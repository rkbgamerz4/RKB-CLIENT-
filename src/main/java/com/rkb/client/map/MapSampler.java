package com.rkb.client.map;

import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

/**
 * Reads one map column from the loaded world. Shared by the minimap and the full map screen. Not thread-safe;
 * give each consumer its own instance. Results are left in the public fields to avoid allocating per cell.
 */
public final class MapSampler {
    public static final int UNKNOWN_HEIGHT = Integer.MIN_VALUE;
    public static final int UNKNOWN_COLOR = 0xFF0B0F1A;

    public int height = UNKNOWN_HEIGHT;
    public int rgb;
    public boolean known;
    public boolean water;

    private final BlockPos.Mutable pos = new BlockPos.Mutable();

    public void sample(ClientWorld w, int x, int z, boolean cave, int py) {
        water = false;
        if (!w.isChunkLoaded(x >> 4, z >> 4)) {
            known = false;
            height = UNKNOWN_HEIGHT;
            return;
        }
        int y;
        if (cave) {
            y = py;
            int bottom = w.getBottomY();
            for (int steps = 0; y > bottom && steps < 64; steps++, y--) {
                pos.set(x, y, z);
                if (!w.getBlockState(pos).isAir()) {
                    break;
                }
            }
        } else {
            y = w.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
        }
        height = y;
        pos.set(x, y, z);
        BlockState state = w.getBlockState(pos);
        MapColor mapColor = state.getMapColor(w, pos);
        if (mapColor == MapColor.CLEAR) {
            known = false;
            return;
        }
        known = true;
        rgb = mapColor.color;
        if (mapColor == MapColor.WATER_BLUE) {
            water = true;
            int depth = 1;
            while (depth < 12) {
                pos.set(x, y - depth, z);
                if (w.getBlockState(pos).getMapColor(w, pos) != MapColor.WATER_BLUE) {
                    break;
                }
                depth++;
            }
            rgb = lerp(0x4F86C6, 0x12305C, Math.min(1f, (depth - 1) / 10f));
        }
    }

    private static int lerp(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /**
     * Soft hill shading from the north and west neighbours plus a slight desaturation, so the map reads like
     * terrain instead of flat paint. Unknown neighbours (UNKNOWN_HEIGHT) are ignored.
     */
    public static int shade(int rgb, boolean water, int y, int northY, int westY) {
        float f = 1f;
        if (!water) {
            int n = northY == UNKNOWN_HEIGHT ? y : northY;
            int wv = westY == UNKNOWN_HEIGHT ? y : westY;
            int g = Math.max(-6, Math.min(6, (y - n) + (y - wv)));
            f = 1f + g * 0.04f;
        }
        int r = (rgb >> 16) & 255;
        int g2 = (rgb >> 8) & 255;
        int b = rgb & 255;
        float lum = 0.3f * r + 0.59f * g2 + 0.11f * b;
        int rr = Math.min(255, Math.max(0, (int) ((r * 0.85f + lum * 0.15f) * f * 0.95f)));
        int gg = Math.min(255, Math.max(0, (int) ((g2 * 0.85f + lum * 0.15f) * f * 0.95f)));
        int bb = Math.min(255, Math.max(0, (int) ((b * 0.85f + lum * 0.15f) * f * 0.95f)));
        return 0xFF000000 | (rr << 16) | (gg << 8) | bb;
    }
}
