package com.rkb.client.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class IntSetting extends Setting<Integer> {
    private final int min;
    private final int max;
    private final int step;
    private final int def;
    private int value;

    public IntSetting(String id, String name, int min, int max, int step, int def) {
        super(id, name);
        this.min = min;
        this.max = max;
        this.step = Math.max(1, step);
        this.def = clamp(def);
        this.value = this.def;
    }

    private int clamp(int v) {
        return Math.max(min, Math.min(max, v));
    }

    @Override
    public Integer get() {
        return value;
    }

    public int value() {
        return value;
    }

    public void set(int v) {
        int c = clamp(v);
        if (c != value) {
            value = c;
            changed();
        }
    }

    /** dir = +1 or -1. */
    public void step(int dir) {
        set(value + dir * step);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement e) {
        if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
            value = clamp(e.getAsInt());
        }
    }

    @Override
    public void reset() {
        set(def);
    }
}
