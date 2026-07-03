package com.rkb.client.input;

import java.util.HashMap;
import java.util.Map;

public class KeybindManager {

    private final Map<String, Integer> keybinds = new HashMap<>();

    public void register(String module, int key) {
        keybinds.put(module, key);
    }

    public int getKey(String module) {
        return keybinds.getOrDefault(module, -1);
    }

    public void setKey(String module, int key) {
        keybinds.put(module, key);
    }
}
