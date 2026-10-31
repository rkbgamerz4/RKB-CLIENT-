package com.rkb.client.gui;

import com.rkb.client.input.KeyUtil;
import com.rkb.client.input.KeybindRegistry;
import com.rkb.client.map.MapSampler;
import com.rkb.client.waypoint.Waypoint;
import com.rkb.client.waypoint.WaypointStore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen map of the loaded chunks. Drag to pan, scroll or +/- to zoom, tap to pick a spot, then add a
 * waypoint there. Sampling is spread over several frames and the picture is merged into rectangles once per
 * refresh, so the screen stays smooth.
 */
public class MapScreen extends Screen {
    private static final int[] BPC = {1, 2, 4};
    private static final int CELL = 4;
    private static final int MAX_COLS = 140;
    private static final int MAX_ROWS = 80;
    private static final int HEADER = 20;
    private static final int FOOTER = 26;

    private record Hit(int x1, int y1, int x2, int y2, Runnable action) {
        boolean contains(int x, int y) {
            return x >= x1 && x < x2 && y >= y1 && y < y2;
        }
    }

    private static int zoomIdx = 0;

    private final List<Hit> hits = new ArrayList<>();
    private final MapSampler sampler = new MapSampler();
    private final RectMesh mesh = new RectMesh();

    private int cols, rows;
    private int[] grid = new int[0];
    private int[] next = new int[0];
    private int[] heights = new int[0];
    private int[] nextHeights = new int[0];
    private int sampleRow;
    private boolean sampling;
    private boolean cave;
    private boolean haveData;

    private int centerX, centerZ;
    private int originX, originZ, passOriginX, passOriginZ, passBpc = 1, bpc = 1;
    private int panX, panY;
    private boolean hasSel;
    private int selX, selZ;

    private int mapX, mapY, mapW, mapH, gx, gy;
    private String worldKey = "unknown";
    private String dimKey = "minecraft:overworld";
    private String toast = "";
    private long toastUntil;

    private boolean mouseWasDown;
    private boolean dragging;
    private boolean pressedInMap;
    private int pressX, pressY, lastX, lastY;

    public MapScreen() {
        super(Text.literal("RKB Map"));
    }

    @Override
    protected void init() {
        MinecraftClient mc = MinecraftClient.getInstance();
        worldKey = WaypointStore.worldKey(mc);
        dimKey = WaypointStore.dimKey(mc);
        mapX = 6;
        mapY = HEADER + 4;
        mapW = width - 12;
        mapH = height - mapY - FOOTER - 2;
        cols = Math.max(8, Math.min(MAX_COLS, mapW / CELL));
        rows = Math.max(8, Math.min(MAX_ROWS, mapH / CELL));
        grid = new int[cols * rows];
        next = new int[cols * rows];
        heights = new int[cols * rows];
        nextHeights = new int[cols * rows];
        gx = mapX + (mapW - cols * CELL) / 2;
        gy = mapY + (mapH - rows * CELL) / 2;
        if (mc.player != null) {
            centerX = mc.player.getBlockX();
            centerZ = mc.player.getBlockZ();
        }
        haveData = false;
        startSampling(mc);
        mouseWasDown = KeyUtil.isMouseDown(mc, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    public boolean shouldPause() {
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        changeZoom(verticalAmount > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        KeyBinding mapKey = KeybindRegistry.actionBinding("minimap", "open_map");
        if (mapKey != null && mapKey.matchesKey(input)) {
            MinecraftClient.getInstance().setScreen(null);
            return true;
        }
        return super.keyPressed(input);
    }

    // ------------------------------------------------------------------ sampling

    private void changeZoom(int delta) {
        int z = Math.max(0, Math.min(BPC.length - 1, zoomIdx + delta));
        if (z != zoomIdx) {
            zoomIdx = z;
            startSampling(MinecraftClient.getInstance());
        }
    }

    private void startSampling(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) {
            return;
        }
        passBpc = BPC[zoomIdx];
        passOriginX = centerX - cols * passBpc / 2;
        passOriginZ = centerZ - rows * passBpc / 2;
        sampleRow = 0;
        sampling = true;
        cave = ((ClientWorld) mc.world).getDimension().hasCeiling()
                || !mc.world.isSkyVisible(mc.player.getBlockPos());
    }

    private void sampleSlice(MinecraftClient mc) {
        if (!sampling || mc.world == null || mc.player == null) {
            return;
        }
        ClientWorld w = mc.world;
        int py = mc.player.getBlockY();
        int budget = 6;
        while (budget-- > 0 && sampleRow < rows) {
            int z = passOriginZ + sampleRow * passBpc;
            for (int i = 0; i < cols; i++) {
                int idx = sampleRow * cols + i;
                sampler.sample(w, passOriginX + i * passBpc, z, cave, py);
                nextHeights[idx] = sampler.height;
                if (!sampler.known) {
                    next[idx] = MapSampler.UNKNOWN_COLOR;
                } else {
                    int northY = sampleRow > 0 ? nextHeights[idx - cols] : sampler.height;
                    int westY = i > 0 ? nextHeights[idx - 1] : sampler.height;
                    next[idx] = MapSampler.shade(sampler.rgb, sampler.water, sampler.height, northY, westY);
                }
            }
            sampleRow++;
        }
        if (sampleRow >= rows) {
            int[] t = grid;
            grid = next;
            next = t;
            int[] th = heights;
            heights = nextHeights;
            nextHeights = th;
            originX = passOriginX;
            originZ = passOriginZ;
            bpc = passBpc;
            mesh.build(grid, cols, rows);
            haveData = true;
            sampling = false;
        }
    }

    // ------------------------------------------------------------------ input

    private boolean inMap(int mx, int my) {
        return mx >= mapX && mx < mapX + mapW && my >= mapY && my < mapY + mapH;
    }

    private void handleMouse(MinecraftClient mc, int mx, int my) {
        boolean down = KeyUtil.isMouseDown(mc, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        if (down && !mouseWasDown) {
            pressX = mx;
            pressY = my;
            lastX = mx;
            lastY = my;
            dragging = false;
            pressedInMap = inMap(mx, my);
        } else if (down) {
            if (!dragging && pressedInMap && (Math.abs(mx - pressX) > 4 || Math.abs(my - pressY) > 4)) {
                dragging = true;
            }
            if (dragging) {
                panX += mx - lastX;
                panY += my - lastY;
                lastX = mx;
                lastY = my;
            }
        } else if (mouseWasDown) {
            if (dragging) {
                centerX -= Math.round(panX / (float) CELL * bpc);
                centerZ -= Math.round(panY / (float) CELL * bpc);
                panX = 0;
                panY = 0;
                startSampling(mc);
            } else {
                click(mx, my);
            }
            dragging = false;
        }
        mouseWasDown = down;
    }

    private void click(int mx, int my) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.contains(mx, my)) {
                h.action().run();
                return;
            }
        }
        if (inMap(mx, my) && haveData) {
            selX = originX + Math.round((mx - gx - panX) / (float) CELL * bpc - 0.5f);
            selZ = originZ + Math.round((my - gy - panY) / (float) CELL * bpc - 0.5f);
            hasSel = true;
        }
    }

    private void addWaypointAtSelection() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) {
            return;
        }
        int x = hasSel ? selX : mc.player.getBlockX();
        int z = hasSel ? selZ : mc.player.getBlockZ();
        sampler.sample(mc.world, x, z, false, mc.player.getBlockY());
        int y = hasSel && sampler.known ? sampler.height + 1 : mc.player.getBlockY();
        Waypoint w = WaypointStore.add(mc, null, x, y, z);
        toast("Added " + w.name + " at " + x + " " + z);
    }

    private void toast(String s) {
        toast = s;
        toastUntil = System.currentTimeMillis() + 2500;
    }

    // ------------------------------------------------------------------ drawing

    private int toScreenX(double worldX) {
        return gx + panX + (int) Math.round((worldX - originX) / bpc * CELL);
    }

    private int toScreenY(double worldZ) {
        return gy + panY + (int) Math.round((worldZ - originZ) / bpc * CELL);
    }

    private int button(DrawContext c, TextRenderer tr, String label, int x, int y, Runnable action) {
        int w = tr.getWidth(label) + 12;
        c.fill(x, y, x + w, y + 14, Theme.BORDER);
        c.drawText(tr, label, x + 6, y + 3, Theme.TEXT, false);
        hits.add(new Hit(x, y, x + w, y + 14, action));
        return x + w + 4;
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        MinecraftClient mc = MinecraftClient.getInstance();
        handleMouse(mc, mx, my);
        sampleSlice(mc);
        hits.clear();
        TextRenderer tr = mc.textRenderer;
        int accent = Theme.accent();

        ctx.fill(0, 0, width, HEADER, Theme.BLACK);
        ctx.drawText(tr, "Map", 8, 6, accent, false);
        ctx.drawText(tr, dimKey.replace("minecraft:", "") + "   1 cell = " + BPC[zoomIdx] + " block"
                + (BPC[zoomIdx] > 1 ? "s" : ""), 8 + tr.getWidth("Map") + 10, 6, Theme.MUTED, false);
        if (inMap(mx, my) && haveData) {
            int wx = originX + Math.round((mx - gx - panX) / (float) CELL * bpc - 0.5f);
            int wz = originZ + Math.round((my - gy - panY) / (float) CELL * bpc - 0.5f);
            String c = wx + ", " + wz;
            ctx.drawText(tr, c, width - 8 - tr.getWidth(c), 6, Theme.TEXT, false);
        }

        ctx.fill(mapX - 1, mapY - 1, mapX + mapW + 1, mapY + mapH + 1, Theme.BORDER);
        ctx.fill(mapX, mapY, mapX + mapW, mapY + mapH, 0xFF05080F);
        ctx.enableScissor(mapX, mapY, mapX + mapW, mapY + mapH);
        if (haveData) {
            mesh.draw(ctx, gx + panX, gy + panY, CELL);
            drawMarkers(ctx, mc, tr, accent);
        } else {
            ctx.drawText(tr, "Loading map...", mapX + 8, mapY + 8, Theme.MUTED, false);
        }
        ctx.disableScissor();

        int fy = height - FOOTER + 4;
        int bx = 8;
        bx = button(ctx, tr, "+ Waypoint", bx, fy, this::addWaypointAtSelection);
        bx = button(ctx, tr, "Zoom -", bx, fy, () -> changeZoom(-1));
        bx = button(ctx, tr, "Zoom +", bx, fy, () -> changeZoom(1));
        bx = button(ctx, tr, "Center", bx, fy, () -> {
            MinecraftClient game = MinecraftClient.getInstance();
            if (game.player != null) {
                centerX = game.player.getBlockX();
                centerZ = game.player.getBlockZ();
                panX = 0;
                panY = 0;
                startSampling(game);
            }
        });
        String close = "Close";
        int cw = tr.getWidth(close) + 12;
        int closeX = width - 8 - cw;
        ctx.fill(closeX, fy, closeX + cw, fy + 14, accent);
        ctx.drawText(tr, close, closeX + 6, fy + 3, Theme.BLACK, false);
        hits.add(new Hit(closeX, fy, closeX + cw, fy + 14, () -> MinecraftClient.getInstance().setScreen(null)));
        if (hasSel) {
            String sel = "Selected " + selX + ", " + selZ;
            ctx.drawText(tr, sel, Math.max(bx + 4, closeX - 8 - tr.getWidth(sel)), fy + 3, Theme.TEXT, false);
        }
        if (System.currentTimeMillis() < toastUntil) {
            int tw = tr.getWidth(toast) + 16;
            int tx = (width - tw) / 2;
            ctx.fill(tx, mapY + 6, tx + tw, mapY + 22, Theme.BLACK);
            ctx.drawText(tr, toast, tx + 8, mapY + 10, Theme.TEXT, false);
        }
    }

    private void drawMarkers(DrawContext ctx, MinecraftClient mc, TextRenderer tr, int accent) {
        List<Waypoint> list = WaypointStore.all(worldKey);
        for (int i = 0; i < list.size(); i++) {
            Waypoint w = list.get(i);
            if (!w.visible || !w.dim.equals(dimKey)) {
                continue;
            }
            int px = toScreenX(w.x + 0.5);
            int py = toScreenY(w.z + 0.5);
            if (px < mapX || px > mapX + mapW || py < mapY || py > mapY + mapH) {
                continue;
            }
            int color = WaypointStore.COLORS[w.color % WaypointStore.COLORS.length];
            ctx.fill(px - 3, py - 3, px + 3, py + 3, 0xFF000000);
            ctx.fill(px - 2, py - 2, px + 2, py + 2, color);
            ctx.drawText(tr, w.name, px + 5, py - 4, color, true);
        }
        if (hasSel) {
            int sx = toScreenX(selX + 0.5);
            int sy = toScreenY(selZ + 0.5);
            ctx.fill(sx - 5, sy, sx + 6, sy + 1, accent);
            ctx.fill(sx, sy - 5, sx + 1, sy + 6, accent);
        }
        if (mc.player != null) {
            int px = toScreenX(mc.player.getX());
            int py = toScreenY(mc.player.getZ());
            ctx.fill(px - 3, py - 3, px + 3, py + 3, 0xFF000000);
            ctx.fill(px - 2, py - 2, px + 2, py + 2, 0xFFFFFFFF);
            double yaw = Math.toRadians(mc.player.getYaw());
            int tx = px + (int) Math.round(-Math.sin(yaw) * 7);
            int ty = py + (int) Math.round(Math.cos(yaw) * 7);
            ctx.fill(tx - 1, ty - 1, tx + 1, ty + 1, accent);
        }
    }
}
