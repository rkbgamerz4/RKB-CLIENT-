package com.rkb.client.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Module ids the user marked with the heart. Saved inside rkbclient.json. */
public final class Favorites {
    private static final Set<String> IDS = new LinkedHashSet<>();

    private Favorites() {
    }

    public static boolean contains(String id) {
        return IDS.contains(id);
    }

    public static void toggle(String id) {
        if (!IDS.remove(id)) {
            IDS.add(id);
        }
    }

    public static void clear() {
        IDS.clear();
    }

    public static void addRaw(String id) {
        if (id != null && !id.isBlank()) {
            IDS.add(id);
        }
    }

    public static boolean isEmpty() {
        return IDS.isEmpty();
    }

    public static List<String> list() {
        return new ArrayList<>(IDS);
    }
}
