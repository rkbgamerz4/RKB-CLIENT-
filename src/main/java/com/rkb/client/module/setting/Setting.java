package com.rkb.client.module.setting;

import com.google.gson.JsonElement;

/** Base class for a persisted, user-editable module option. */
public abstract class Setting<T> {
    private final String id;
    private final String name;
    private Runnable listener = () -> { };
    private java.util.function.BooleanSupplier visible = () -> true;

    protected Setting(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public abstract T get();

    public abstract JsonElement toJson();

    /** Must tolerate wrong types; callers also catch exceptions. */
    public abstract void fromJson(JsonElement element);

    public abstract void reset();

    /** The menu hides the setting while this returns false (e.g. options of a feature that is switched off). */
    public void setVisibleWhen(java.util.function.BooleanSupplier supplier) {
        this.visible = supplier == null ? () -> true : supplier;
    }

    public boolean visible() {
        return visible.getAsBoolean();
    }

    public void setListener(Runnable listener) {
        this.listener = listener == null ? () -> { } : listener;
    }

    protected void changed() {
        listener.run();
    }
}
