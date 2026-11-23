package com.rkb.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Simple in-game file picker for selecting PNG files (skins). */
public class RkbFilePickerScreen extends Screen {
    private record Hit(RkbUi.Rect rect, Runnable action) {
        boolean contains(int x, int y) { return rect.contains(x, y); }
    }
    private record Entry(Path path, String name, boolean dir) {}

    private final Screen parent;
    private final Consumer<Path> onSelect;
    private final String extension;
    private Path currentDir;
    private final List<Entry> entries = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private float scroll;
    private int contentH;
    private String error = "";

    public RkbFilePickerScreen(Screen parent, String title, String extension, Path startDir, Consumer<Path> onSelect) {
        super(Text.literal(title));
        this.parent = parent;
        this.extension = extension == null ? ".png" : extension.toLowerCase(Locale.ROOT);
        this.onSelect = onSelect;
        Path start = startDir;
        if (start == null || !Files.isDirectory(start)) {
            start = Path.of(System.getProperty("user.home", "."));
        }
        if (!Files.isDirectory(start)) start = Path.of(".");
        this.currentDir = start.toAbsolutePath().normalize();
        refresh();
    }

    @Override public boolean shouldPause() { return false; }

    @Override public void close() { MinecraftClient.getInstance().setScreen(parent); }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        scroll = Math.max(0, Math.min(Math.max(0, contentH - listHeight()), scroll - (float) v * 18));
        return true;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            int mx = (int) click.x();
            int my = (int) click.y();
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit hit = hits.get(i);
                if (hit.contains(mx, my)) { hit.action().run(); return true; }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        return super.keyPressed(input);
    }

    private int listTop() { return 86; }
    private int listHeight() { return Math.max(40, height - listTop() - 34); }

    private void refresh() {
        entries.clear();
        error = "";
        try (Stream<Path> stream = Files.list(currentDir)) {
            stream.forEach(p -> {
                try {
                    String name = p.getFileName() == null ? p.toString() : p.getFileName().toString();
                    if (name.startsWith(".")) return;
                    if (Files.isDirectory(p)) {
                        entries.add(new Entry(p, name, true));
                    } else if (Files.isRegularFile(p) && name.toLowerCase(Locale.ROOT).endsWith(extension)) {
                        entries.add(new Entry(p, name, false));
                    }
                } catch (Exception ignored) { }
            });
        } catch (Exception e) {
            error = "Cannot read folder: " + e.getMessage();
        }
        entries.sort(Comparator.<Entry, Integer>comparing(e -> e.dir ? 0 : 1)
                .thenComparing(e -> e.name.toLowerCase(Locale.ROOT)));
        scroll = 0;
    }

    private void openDir(Path dir) {
        if (dir != null && Files.isDirectory(dir)) {
            currentDir = dir.toAbsolutePath().normalize();
            refresh();
        }
    }

    private void selectFile(Path file) {
        if (file != null && Files.isRegularFile(file)) {
            MinecraftClient.getInstance().setScreen(parent);
            if (onSelect != null) onSelect.accept(file);
        }
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        hits.clear();
        RkbUi.background(ctx, width, height, System.currentTimeMillis());
        TextRenderer tr = textRenderer;
        int pad = 16;
        int panelW = Math.min(720, width - 24);
        int panelH = Math.min(460, height - 24);
        int px = (width - panelW) / 2;
        int py = (height - panelH) / 2;
        RkbUi.Rect panel = new RkbUi.Rect(px, py, panelW, panelH);
        RkbUi.panel(ctx, panel, 0xF5171A20, RkbUi.LINE);

        ctx.drawText(tr, RkbUi.menuText(title.getString()), px + pad, py + 14, RkbUi.TEXT, false);
        ctx.drawText(tr, RkbUi.menuText("Select a " + extension + " file"), px + pad, py + 28, RkbUi.MUTED, false);

        // Close
        RkbUi.Rect close = new RkbUi.Rect(panel.right() - pad - 56, py + 12, 56, 22);
        drawBtn(ctx, tr, close, "Cancel", close.contains(mx, my), false);
        hits.add(new Hit(close, this::close));

        // Quick locations
        int qx = px + pad;
        int qy = py + 44;
        qx = quickBtn(ctx, tr, qx, qy, "Game", Path.of(System.getProperty("user.dir", ".")), mx, my);
        qx = quickBtn(ctx, tr, qx, qy, "Downloads", Path.of(System.getProperty("user.home", ""), "Downloads"), mx, my);
        qx = quickBtn(ctx, tr, qx, qy, "Pictures", Path.of(System.getProperty("user.home", ""), "Pictures"), mx, my);
        qx = quickBtn(ctx, tr, qx, qy, "Storage", Path.of("/storage/emulated/0"), mx, my);
        quickBtn(ctx, tr, qx, qy, "Home", Path.of(System.getProperty("user.home", ".")), mx, my);

        // Path + up
        String pathStr = RkbUi.fit(tr, currentDir.toString(), panelW - pad * 2 - 66);
        ctx.drawText(tr, RkbUi.menuText(pathStr), px + pad, py + 72, RkbUi.DIM, false);
        RkbUi.Rect up = new RkbUi.Rect(panel.right() - pad - 52, py + 66, 52, 20);
        Path parentDir = currentDir.getParent();
        drawBtn(ctx, tr, up, "Up", up.contains(mx, my) && parentDir != null, false);
        if (parentDir != null) hits.add(new Hit(up, () -> openDir(parentDir)));

        RkbUi.Rect list = new RkbUi.Rect(px + pad, py + listTop(), panelW - pad * 2, panelH - listTop() - 18);
        ctx.fill(list.x(), list.y(), list.right(), list.bottom(), RkbUi.SURFACE_2);
        RkbUi.roundedOutline(ctx, list, RkbUi.LINE);
        ctx.enableScissor(list.x() + 1, list.y() + 1, list.right() - 1, list.bottom() - 1);

        int y = list.y() + 4 - (int) scroll;
        int rowH = 24;
        if (!error.isEmpty()) {
            ctx.drawText(tr, RkbUi.menuText(RkbUi.fit(tr, error, list.width() - 16)), list.x() + 8, y + 4, RkbUi.DANGER, false);
            y += rowH;
        }
        if (entries.isEmpty() && error.isEmpty()) {
            ctx.drawText(tr, RkbUi.menuText("No folders or " + extension + " files here."), list.x() + 8, y + 4, RkbUi.MUTED, false);
            y += rowH;
        }
        for (Entry e : entries) {
            RkbUi.Rect row = new RkbUi.Rect(list.x() + 4, y, list.width() - 8, rowH - 2);
            if (row.bottom() >= list.y() && row.y() <= list.bottom()) {
                boolean hover = row.contains(mx, my);
                if (hover) ctx.fill(row.x(), row.y(), row.right(), row.bottom(), e.dir ? RkbUi.SURFACE_3 : 0xFF1F3A2A);
                String icon = e.dir ? "[DIR]" : "[PNG]";
                int iconColor = e.dir ? RkbUi.CHROME_ACCENT : RkbUi.SUCCESS;
                ctx.drawText(tr, RkbUi.menuText(icon), row.x() + 6, row.y() + 7, iconColor, false);
                ctx.drawText(tr, RkbUi.menuText(RkbUi.fit(tr, e.name, row.width() - 56)), row.x() + 44, row.y() + 7,
                        e.dir ? RkbUi.TEXT : RkbUi.TEXT, false);
                final Path p = e.path;
                if (e.dir) hits.add(new Hit(row, () -> openDir(p)));
                else hits.add(new Hit(row, () -> selectFile(p)));
            }
            y += rowH;
        }
        contentH = y + (int) scroll - list.y() + 8;
        ctx.disableScissor();
        super.render(ctx, mx, my, delta);
    }

    private int quickBtn(DrawContext ctx, TextRenderer tr, int x, int y, String label, Path path, int mx, int my) {
        if (path == null || !Files.isDirectory(path)) return x;
        int w = tr.getWidth(label) + 16;
        RkbUi.Rect r = new RkbUi.Rect(x, y, w, 20);
        drawBtn(ctx, tr, r, label, r.contains(mx, my), false);
        hits.add(new Hit(r, () -> openDir(path)));
        return x + w + 6;
    }

    private void drawBtn(DrawContext ctx, TextRenderer tr, RkbUi.Rect r, String label, boolean hover, boolean primary) {
        ctx.fill(r.x(), r.y(), r.right(), r.bottom(), primary ? RkbUi.CHROME_ACCENT : hover ? RkbUi.SURFACE_3 : RkbUi.SURFACE_2);
        RkbUi.roundedOutline(ctx, r, primary ? RkbUi.CHROME_ACCENT : RkbUi.LINE);
        int tw = tr.getWidth(RkbUi.menuText(label));
        ctx.drawText(tr, RkbUi.menuText(label), r.x() + (r.width() - tw) / 2, r.y() + (r.height() - 8) / 2,
                primary ? 0xFFFFFFFF : RkbUi.TEXT, false);
    }
}
