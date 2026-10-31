package com.rkb.client.module.impl;

import com.rkb.client.gui.MapScreen;
import com.rkb.client.gui.RectMesh;
import com.rkb.client.gui.Theme;
import com.rkb.client.gui.WaypointScreen;
import com.rkb.client.hud.HudModule;
import com.rkb.client.map.MapSampler;
import com.rkb.client.module.Category;
import com.rkb.client.module.setting.BoolSetting;
import com.rkb.client.module.setting.ChoiceSetting;
import com.rkb.client.module.setting.IntSetting;
import com.rkb.client.module.setting.Setting;
import com.rkb.client.waypoint.Waypoint;
import com.rkb.client.waypoint.WaypointStore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Terrain minimap with waypoints. Terrain only: no player, mob or item radar.
 * Cost control: the map is sampled a few rows per tick; the visible picture is merged into a small list of
 * rectangles that is rebuilt only when you move a block, turn (with rotation on) or change a setting; waypoints
 * and the tracked line are the only things drawn fresh each frame. Buffers are freed when the module is turned off.
 */
public class MinimapModule extends HudModule {
    private static final int[] RANGES = {32, 48, 64};
    private static final int[] QUANT = {4, 12, 24};

    private final IntSetting size = add(new IntSetting("size", "Size (px)", 48, 160, 16, 96));
    private final ChoiceSetting range = add(new ChoiceSetting("range", "Range (blocks)", List.of("32", "48", "64"), "32"));
    private final ChoiceSetting quality = add(new ChoiceSetting("quality", "Quality (Low = fastest)", List.of("High", "Medium", "Low"), "Medium"));
    private final ChoiceSetting shape = add(new ChoiceSetting("shape", "Shape", List.of("Square", "Circle"), "Square"));
    private final BoolSetting rotate = add(new BoolSetting("rotate", "Rotate with player", false));
    private final IntSetting opacity = add(new IntSetting("opacity", "Opacity %", 30, 100, 10, 100));
    private final BoolSetting caveAuto = add(new BoolSetting("cave_auto", "Cave view underground", true));
    private final BoolSetting coords = add(new BoolSetting("show_coords", "Show coordinates", true));
    private final BoolSetting north = add(new BoolSetting("show_north", "Show N marker", true));
    private final BoolSetting showWaypoints = add(new BoolSetting("show_waypoints", "Show waypoints", true));
    private final BoolSetting names = add(new BoolSetting("show_names", "Show waypoint names", false));
    private final BoolSetting trackLine = add(new BoolSetting("track_line", "Line to tracked waypoint", true));
    private final BoolSetting deathWp = add(new BoolSetting("death_waypoint", "Save a waypoint when you die", true));

    private final MapSampler sampler = new MapSampler();
    private final RectMesh mesh = new RectMesh();

    private int n = 32;
    private int[] colors = new int[0];
    private int[] next = new int[0];
    private int[] heights = new int[0];
    private int[] nextHeights = new int[0];
    private boolean[] water = new boolean[0];
    private boolean[] nextWater = new boolean[0];
    private int[] view = new int[0];
    private int originX;
    private int originZ;
    private int passX;
    private int passZ;
    private int row;
    private boolean ready;
    private boolean caveMode;

    private boolean viewDirty = true;
    private int lastPbx = Integer.MIN_VALUE;
    private int lastPbz;
    private int lastYawBucket;

    private Object lastWorld;
    private int keyTick;
    private String worldKey = "unknown";
    private String dimKey = "minecraft:overworld";
    private boolean wasDead;

    private Waypoint tracked;
    private String coordsText = "";
    private String trackText = "";
    private int lastBx = Integer.MIN_VALUE;
    private int lastBy;
    private int lastBz;
    private int lastTrackDist = -1;
    private String lastTrackName = "";

    private double projDx;
    private double projDy;
    private boolean projClamped;

    public MinimapModule() {
        super("minimap", "Minimap", "Terrain map with waypoints, a full-screen map (key M) and a line to the one you track.",
                Category.WORLD, "Top Right", 6, 120);
        addAction("open_map", "Open full map", GLFW.GLFW_KEY_M, mc -> mc.setScreen(new MapScreen()));
        addAction("add_waypoint", "Add waypoint here", this::addHere);
        addAction("open_waypoints", "Open waypoint list", mc -> mc.setScreen(new WaypointScreen()));
        addAction("next_tracked", "Track next waypoint", this::trackNext);
    }

    private int rangeBlocks() {
        return RANGES[Math.max(0, Math.min(RANGES.length - 1, range.index()))];
    }

    // ------------------------------------------------------------------ actions

    private void addHere(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) {
            return;
        }
        Waypoint w = WaypointStore.add(mc, null, mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
        mc.player.sendMessage(Text.literal("Waypoint saved: " + w.name), true);
    }

    private void trackNext(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) {
            return;
        }
        String key = WaypointStore.worldKey(mc);
        String dim = WaypointStore.dimKey(mc);
        List<Waypoint> usable = new ArrayList<>();
        for (Waypoint w : WaypointStore.all(key)) {
            if (w.visible && w.dim.equals(dim)) {
                usable.add(w);
            }
        }
        if (usable.isEmpty()) {
            mc.player.sendMessage(Text.literal("No waypoints here"), true);
            return;
        }
        String current = WaypointStore.tracked(key);
        int idx = -1;
        for (int i = 0; i < usable.size(); i++) {
            if (usable.get(i).name.equals(current)) {
                idx = i;
            }
        }
        Waypoint pick = usable.get((idx + 1) % usable.size());
        WaypointStore.setTracked(key, pick.name);
        mc.player.sendMessage(Text.literal("Tracking: " + pick.name), true);
    }

    // ------------------------------------------------------------------ lifecycle / sampling

    @Override
    protected void onSettingChanged(Setting<?> setting) {
        viewDirty = true;
    }

    @Override
    protected void onDisable() {
        ready = false;
        colors = new int[0];
        next = new int[0];
        heights = new int[0];
        nextHeights = new int[0];
        water = new boolean[0];
        nextWater = new boolean[0];
        view = new int[0];
        mesh.clear();
        row = 0;
    }

    @Override
    protected void onTick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) {
            ready = false;
            lastWorld = null;
            return;
        }
        if (mc.world != lastWorld || ++keyTick >= 40) {
            lastWorld = mc.world;
            keyTick = 0;
            worldKey = WaypointStore.worldKey(mc);
            dimKey = WaypointStore.dimKey(mc);
        }
        boolean dead = mc.player.getHealth() <= 0f;
        if (dead && !wasDead && deathWp.on()) {
            WaypointStore.setDeath(mc, mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
        }
        wasDead = dead;

        int want = rangeBlocks();
        if (want != n || next.length != want * want) {
            n = want;
            int total = n * n;
            colors = new int[total];
            next = new int[total];
            heights = new int[total];
            nextHeights = new int[total];
            water = new boolean[total];
            nextWater = new boolean[total];
            view = new int[total];
            row = 0;
            ready = false;
            viewDirty = true;
        }
        ClientWorld w = mc.world;
        if (row == 0) {
            passX = mc.player.getBlockX() - n / 2;
            passZ = mc.player.getBlockZ() - n / 2;
            caveMode = w.getDimension().hasCeiling()
                    || (caveAuto.on() && !w.isSkyVisible(mc.player.getBlockPos()));
        }
        int py = mc.player.getBlockY();
        int rows = Math.max(2, n / 8);
        for (int r = 0; r < rows && row < n; r++, row++) {
            int z = passZ + row;
            for (int i = 0; i < n; i++) {
                int idx = row * n + i;
                sampler.sample(w, passX + i, z, caveMode, py);
                nextHeights[idx] = sampler.height;
                nextWater[idx] = sampler.water;
                if (!sampler.known) {
                    next[idx] = MapSampler.UNKNOWN_COLOR;
                } else {
                    int northY = row > 0 ? nextHeights[idx - n] : sampler.height;
                    int westY = i > 0 ? nextHeights[idx - 1] : sampler.height;
                    next[idx] = MapSampler.shade(sampler.rgb, sampler.water, sampler.height, northY, westY);
                }
            }
        }
        if (row >= n) {
            int[] t = colors;
            colors = next;
            next = t;
            int[] th = heights;
            heights = nextHeights;
            nextHeights = th;
            boolean[] tw = water;
            water = nextWater;
            nextWater = tw;
            originX = passX;
            originZ = passZ;
            row = 0;
            ready = true;
            viewDirty = true;
        }
    }

    // ------------------------------------------------------------------ per-frame info

    @Override
    protected void prepare(MinecraftClient mc) {
        tracked = null;
        if (mc.player == null) {
            return;
        }
        String t = WaypointStore.tracked(worldKey);
        if (!t.isEmpty()) {
            List<Waypoint> list = WaypointStore.all(worldKey);
            for (int i = 0; i < list.size(); i++) {
                Waypoint w = list.get(i);
                if (w.visible && w.name.equals(t) && w.dim.equals(dimKey)) {
                    tracked = w;
                    break;
                }
            }
        }
        int bx = mc.player.getBlockX();
        int by = mc.player.getBlockY();
        int bz = mc.player.getBlockZ();
        if (bx != lastBx || by != lastBy || bz != lastBz) {
            lastBx = bx;
            lastBy = by;
            lastBz = bz;
            coordsText = bx + " " + by + " " + bz;
        }
        if (tracked != null) {
            int dist = (int) Math.round(Math.hypot(tracked.x + 0.5 - mc.player.getX(), tracked.z + 0.5 - mc.player.getZ()));
            if (dist != lastTrackDist || !tracked.name.equals(lastTrackName)) {
                lastTrackDist = dist;
                lastTrackName = tracked.name;
                trackText = tracked.name + "  " + dist + "m";
            }
        }
    }

    private int cell() {
        return Math.max(1, size.value() / rangeBlocks());
    }

    @Override
    protected int width(MinecraftClient mc) {
        return cell() * rangeBlocks() + 2;
    }

    @Override
    protected int height(MinecraftClient mc) {
        return width(mc) + (coords.on() ? 11 : 0) + (tracked != null ? 11 : 0);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void draw(DrawContext ctx, MinecraftClient mc, int x, int y) {
        int cell = cell();
        int s = cell * n;
        int ox = x + 1;
        int oy = y + 1;
        boolean circle = shape.index() == 1;
        int alpha = (opacity.value() * 255 / 100) << 24;

        double fx = 0;
        double fz = -1;
        if (rotate.on() && mc.player != null) {
            double th = Math.toRadians(mc.player.getYaw());
            fx = -Math.sin(th);
            fz = Math.cos(th);
        }

        if (!circle) {
            ctx.fill(x, y, x + s + 2, y + s + 2, 0xFF1A2440);
            ctx.fill(ox, oy, ox + s, oy + s, alpha | 0x05080F);
        }
        if (ready && mc.player != null && colors.length == n * n && view.length == n * n) {
            int pbx = (int) Math.floor(mc.player.getX());
            int pbz = (int) Math.floor(mc.player.getZ());
            int yawBucket = rotate.on() ? Math.round(mc.player.getYaw() / 2f) : 0;
            if (viewDirty || pbx != lastPbx || pbz != lastPbz || yawBucket != lastYawBucket) {
                lastPbx = pbx;
                lastPbz = pbz;
                lastYawBucket = yawBucket;
                viewDirty = false;
                buildView(mc, fx, fz, circle, alpha);
                mesh.build(view, n, n);
            }
            mesh.draw(ctx, ox, oy, cell);
            drawOverlay(ctx, mc, ox, oy, s, cell, fx, fz, circle);
        }

        int ty = y + s + 4;
        if (coords.on()) {
            ctx.drawText(mc.textRenderer, coordsText, x + 1, ty, Theme.hudText(), Theme.hudShadow());
            ty += 11;
        }
        if (tracked != null) {
            ctx.drawText(mc.textRenderer, trackText, x + 1, ty, WaypointStore.COLORS[tracked.color % WaypointStore.COLORS.length], true);
        }
    }

    private void buildView(MinecraftClient mc, double fx, double fz, boolean circle, int alpha) {
        double pcx = mc.player.getX() - originX;
        double pcz = mc.player.getZ() - originZ;
        double half = n / 2.0;
        double outer = half * half;
        double inner = (half - 1.3) * (half - 1.3);
        int q = QUANT[Math.max(0, Math.min(QUANT.length - 1, quality.index()))];
        for (int j = 0; j < n; j++) {
            double dy = j + 0.5 - half;
            for (int i = 0; i < n; i++) {
                double dx = i + 0.5 - half;
                if (circle) {
                    double d2 = dx * dx + dy * dy;
                    if (d2 > outer) {
                        view[j * n + i] = 0;
                        continue;
                    }
                    if (d2 > inner) {
                        view[j * n + i] = 0xFF000000 | 0x2A3A66;
                        continue;
                    }
                }
                double wx = -dx * fz - dy * fx;
                double wz = dx * fx - dy * fz;
                int sx = (int) Math.floor(pcx + wx);
                int sz = (int) Math.floor(pcz + wz);
                int c = (sx >= 0 && sx < n && sz >= 0 && sz < n) ? colors[sz * n + sx] : MapSampler.UNKNOWN_COLOR;
                int r = ((c >> 16) & 255) / q * q;
                int g = ((c >> 8) & 255) / q * q;
                int b = (c & 255) / q * q;
                view[j * n + i] = alpha | (r << 16) | (g << 8) | b;
            }
        }
    }

    private void drawOverlay(DrawContext ctx, MinecraftClient mc, int ox, int oy, int s, int cell,
                             double fx, double fz, boolean circle) {
        int cx = ox + s / 2;
        int cy = oy + s / 2;
        double half = n / 2.0 - 2;

        if (showWaypoints.on()) {
            List<Waypoint> list = WaypointStore.all(worldKey);
            if (trackLine.on() && tracked != null) {
                project(mc, tracked, fx, fz, half, circle);
                line(ctx, cx, cy, cx + (int) Math.round(projDx * cell), cy + (int) Math.round(projDy * cell),
                        WaypointStore.COLORS[tracked.color % WaypointStore.COLORS.length]);
            }
            for (int i = 0; i < list.size(); i++) {
                Waypoint w = list.get(i);
                if (!w.visible || !w.dim.equals(dimKey)) {
                    continue;
                }
                project(mc, w, fx, fz, half, circle);
                int px = cx + (int) Math.round(projDx * cell);
                int py = cy + (int) Math.round(projDy * cell);
                int color = WaypointStore.COLORS[w.color % WaypointStore.COLORS.length];
                int r = w == tracked ? 3 : 2;
                ctx.fill(px - r, py - r, px + r, py + r, 0xFF000000);
                ctx.fill(px - r + 1, py - r + 1, px + r - 1, py + r - 1, color);
                if (names.on() && !projClamped) {
                    ctx.drawText(mc.textRenderer, w.name, px + 4, py - 4, color, true);
                }
            }
        }

        ctx.fill(cx - 2, cy - 2, cx + 2, cy + 2, 0xFF000000);
        ctx.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFFFFFFF);
        double yaw = Math.toRadians(mc.player.getYaw());
        double ex = -Math.sin(yaw);
        double ez = Math.cos(yaw);
        double ddx = -ex * fz + ez * fx;
        double ddy = -ex * fx - ez * fz;
        int tx = cx + (int) Math.round(ddx * 5);
        int ty = cy + (int) Math.round(ddy * 5);
        ctx.fill(tx - 1, ty - 1, tx + 1, ty + 1, Theme.accent());

        if (north.on()) {
            double r = s / 2.0 - 6;
            int nx = cx + (int) Math.round(-fx * r);
            int ny = cy + (int) Math.round(fz * r);
            ctx.drawText(mc.textRenderer, "N", nx - 2, ny - 4, Theme.TEXT, true);
        }
    }

    /** Sets projDx/projDy (cells from the map centre, clamped to the edge) and projClamped. No allocation. */
    private void project(MinecraftClient mc, Waypoint w, double fx, double fz, double half, boolean circle) {
        double wx = w.x + 0.5 - mc.player.getX();
        double wz = w.z + 0.5 - mc.player.getZ();
        double dx = -wx * fz + wz * fx;
        double dy = -wx * fx - wz * fz;
        projClamped = false;
        double limit = circle ? Math.hypot(dx, dy) : Math.max(Math.abs(dx), Math.abs(dy));
        if (limit > half && limit > 0) {
            double k = half / limit;
            dx *= k;
            dy *= k;
            projClamped = true;
        }
        projDx = dx;
        projDy = dy;
    }

    /** Dotted line between two pixels. */
    private static void line(DrawContext ctx, int x0, int y0, int x1, int y1, int color) {
        int dx = x1 - x0;
        int dy = y1 - y0;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) {
            return;
        }
        for (int i = 0; i <= steps; i++) {
            if (i % 3 == 2) {
                continue;
            }
            int px = x0 + dx * i / steps;
            int py = y0 + dy * i / steps;
            ctx.fill(px, py, px + 1, py + 1, color);
        }
    }
}
