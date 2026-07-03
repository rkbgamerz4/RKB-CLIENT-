package com.rkb.client.module;

public class Module {

    private final String name;
    private final Category category;

    private boolean enabled;
    private float animation;

    public Module(String name, Category category) {
        this.name = name;
        this.category = category;
        this.enabled = false;
        this.animation = 0.0f;
    }

    public String getName() {
        return name;
    }

    public Category getCategory() {
        return category;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public float getAnimation() {
        return animation;
    }

    public void setAnimation(float animation) {
        this.animation = animation;
    }

    public void onEnable() {
    }

    public void onDisable() {
    }

    public void toggle() {
        enabled = !enabled;

        if (enabled) {
            onEnable();
        } else {
            onDisable();
        }
    }
}
