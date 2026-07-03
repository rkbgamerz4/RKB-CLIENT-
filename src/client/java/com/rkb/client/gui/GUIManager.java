package com.rkb.client.gui;

public class GUIManager {

    private boolean opened = false;

    public void open() {
        opened = true;
        System.out.println("RKB Client GUI Opened");
    }

    public void close() {
        opened = false;
        System.out.println("RKB Client GUI Closed");
    }

    public boolean isOpened() {
        return opened;
    }
}
