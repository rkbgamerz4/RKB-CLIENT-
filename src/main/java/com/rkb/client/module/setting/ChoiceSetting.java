package com.rkb.client.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;

public class ChoiceSetting extends Setting<String> {
    private final List<String> options;
    private final int defIndex;
    private int index;

    public ChoiceSetting(String id, String name, List<String> options, String def) {
        super(id, name);
        this.options = List.copyOf(options);
        int i = this.options.indexOf(def);
        this.defIndex = i < 0 ? 0 : i;
        this.index = this.defIndex;
    }

    @Override
    public String get() {
        return options.get(index);
    }

    public int index() {
        return index;
    }

    public List<String> options() {
        return options;
    }

    public void select(String value) {
        int i = options.indexOf(value);
        if (i >= 0 && i != index) {
            index = i;
            changed();
        }
    }

    public void cycle() {
        index = (index + 1) % options.size();
        changed();
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(get());
    }

    @Override
    public void fromJson(JsonElement e) {
        if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
            int i = options.indexOf(e.getAsString());
            if (i >= 0) {
                index = i;
            }
        }
    }

    @Override
    public void reset() {
        if (index != defIndex) {
            index = defIndex;
            changed();
        }
    }
}
