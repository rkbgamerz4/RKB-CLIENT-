package com.rkb.client.cosmetics;

import com.rkb.client.RkbClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;

/** The supplied RKB "R" logo, registered once from the mod resources. */
public final class RkbLogo {
    public static final Identifier ID = Identifier.of(RkbClient.MOD_ID, "textures/gui/rkb_logo.png");
    private static boolean ready;
    private static boolean failed;

    private RkbLogo() {
    }

    public static void ensure() {
        if (ready || failed) {
            return;
        }
        try (InputStream in = RkbLogo.class.getResourceAsStream("/assets/rkbclient/textures/gui/rkb_logo.png")) {
            if (in == null) {
                failed = true;
                return;
            }
            NativeImage image = NativeImage.read(in);
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "RKB logo", image);
            texture.upload();
            MinecraftClient.getInstance().getTextureManager().registerTexture(ID, texture);
            ready = true;
        } catch (Throwable t) {
            failed = true;
            RkbClient.LOGGER.warn("RKB logo could not be registered: {}", t.toString());
        }
    }

    public static boolean ready() {
        ensure();
        return ready;
    }

    public static void draw(DrawContext ctx, int x, int y, int size) {
        ensure();
        if (ready) {
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, ID, x, y, 0f, 0f, size, size, 128, 128);
        }
    }
}
