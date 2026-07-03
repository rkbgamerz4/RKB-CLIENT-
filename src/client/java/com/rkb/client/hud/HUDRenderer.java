package com.rkb.client.hud;

public class HUDRenderer {

    public void render() {
        renderWatermark();
        renderFPS();
        renderPing();
        renderCoordinates();
    }

    private void renderWatermark() {
        System.out.println("RKB CLIENT");
    }

    private void renderFPS() {
        System.out.println("FPS: 0");
    }

    private void renderPing() {
        System.out.println("Ping: 0ms");
    }

    private void renderCoordinates() {
        System.out.println("XYZ: 0 0 0");
    }
}
