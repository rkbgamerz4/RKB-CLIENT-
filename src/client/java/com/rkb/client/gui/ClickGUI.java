package com.rkb.client.gui;

public class ClickGUI {

    private static final ClickGUI INSTANCE = new ClickGUI();

    private boolean open = false;

    public static ClickGUI getInstance() {
        return INSTANCE;
    }

    public boolean isOpen() {
        return open;
    }

    public void toggle() {
        open = !open;
        System.out.println("RKB ClickGUI: " + (open ? "Opened" : "Closed"));
    }
}
