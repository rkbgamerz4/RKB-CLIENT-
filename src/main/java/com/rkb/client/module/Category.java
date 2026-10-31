package com.rkb.client.module;

public enum Category {
    HUD("HUD", false),
    PERFORMANCE("Performance", false),
    VISUALS("Visuals", false),
    CONTROLS("Controls", false),
    MOVEMENT("Movement", false),
    UTILITY("Utility", false),
    WORLD("World", false),
    CONFIGURATION("Configuration", true),
    ABOUT("About RKB", true);

    private final String label;
    private final boolean page;

    Category(String label, boolean page) {
        this.label = label;
        this.page = page;
    }

    public String label() {
        return label;
    }

    /** Pages (Configuration, About) have no modules; the menu draws them itself. */
    public boolean isPage() {
        return page;
    }
}
