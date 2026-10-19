package com.rkb.client.module;

public enum Category {
    HUD("HUD"),
    VISUAL("Visual"),
    PERFORMANCE("Performance"),
    UTILITY("Utility"),
    COMBAT("Combat"),
    MOVEMENT("Movement");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
