package com.rkb.client.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class BoolSetting extends Setting<Boolean> {
    private final boolean def;
    private boolean value;

    public BoolSetting(String id, String name, boolean def) {
        super(id, name);
        this.def = def;
        this.value = def;
    }

    @Override
    public Boolean get() {
        return value;
    }

    public boolean on() {
        return value;
    }

    public void set(boolean v) {
        if (v != value) {
            value = v;
            changed();
        }
    }

    public void toggle() {
        set(!value);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement e) {
        if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) {
            value = e.getAsBoolean();
        }
    }

    @Override
    public void reset() {
        set(def);
    }
}
