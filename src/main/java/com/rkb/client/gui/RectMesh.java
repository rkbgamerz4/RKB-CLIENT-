package com.rkb.client.gui;

import net.minecraft.client.gui.DrawContext;

/**
 * Merges a grid of coloured cells into as few rectangles as possible (greedy meshing). The result is rebuilt only
 * when the grid changes, so drawing a frame costs one fill per rectangle instead of one per cell. This is what keeps
 * the minimap cheap on phones. A cell value of 0 means "draw nothing".
 */
public final class RectMesh {
    private int[] rx = new int[0];
    private int[] ry = new int[0];
    private int[] rw = new int[0];
    private int[] rh = new int[0];
    private int[] rc = new int[0];
    private boolean[] used = new boolean[0];
    private int count;

    public int count() {
        return count;
    }

    public void clear() {
        count = 0;
    }

    public void build(int[] cells, int w, int h) {
        int total = w * h;
        if (rx.length < total) {
            rx = new int[total];
            ry = new int[total];
            rw = new int[total];
            rh = new int[total];
            rc = new int[total];
        }
        if (used.length < total) {
            used = new boolean[total];
        } else {
            java.util.Arrays.fill(used, 0, total, false);
        }
        count = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                int c = cells[idx];
                if (c == 0 || used[idx]) {
                    continue;
                }
                int ww = 1;
                while (x + ww < w && !used[idx + ww] && cells[idx + ww] == c) {
                    ww++;
                }
                int hh = 1;
                boolean grow = true;
                while (grow && y + hh < h) {
                    int base = (y + hh) * w + x;
                    for (int k = 0; k < ww; k++) {
                        if (used[base + k] || cells[base + k] != c) {
                            grow = false;
                            break;
                        }
                    }
                    if (grow) {
                        hh++;
                    }
                }
                for (int yy = 0; yy < hh; yy++) {
                    int base = (y + yy) * w + x;
                    for (int k = 0; k < ww; k++) {
                        used[base + k] = true;
                    }
                }
                rx[count] = x;
                ry[count] = y;
                rw[count] = ww;
                rh[count] = hh;
                rc[count] = c;
                count++;
            }
        }
    }

    public void draw(DrawContext ctx, int ox, int oy, int cell) {
        for (int i = 0; i < count; i++) {
            ctx.fill(ox + rx[i] * cell, oy + ry[i] * cell, ox + (rx[i] + rw[i]) * cell, oy + (ry[i] + rh[i]) * cell, rc[i]);
        }
    }
}
