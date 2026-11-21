package com.rkb.client.cloud;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Players known (from the cloud) to be RKB users with their badge switched on. The render thread only does a
 * hash lookup on an immutable snapshot; all updates happen on the cloud thread and swap the snapshot.
 */
public final class BadgeRegistry {
    private static final long RECHECK_MS = 5 * 60_000L;

    private static volatile Set<UUID> eligible = Set.of();
    private static final Map<UUID, Long> CHECKED = new HashMap<>();

    private BadgeRegistry() {
    }

    public static boolean isEligible(UUID id) {
        return eligible.contains(id);
    }

    public static synchronized void clear() {
        eligible = Set.of();
        CHECKED.clear();
    }

    /** Replaces the verified RKB identity set supplied by the V2 lookup. Never called from the render thread. */
    public static synchronized void replaceEligible(Set<UUID> verified) {
        eligible = verified == null ? Set.of() : Set.copyOf(verified);
    }

    /** UUIDs from 'visible' that were not checked in the last five minutes. */
    static synchronized List<UUID> needCheck(Collection<UUID> visible, long nowMs) {
        List<UUID> out = new ArrayList<>();
        for (UUID id : visible) {
            Long t = CHECKED.get(id);
            if (t == null || nowMs - t > RECHECK_MS) {
                out.add(id);
            }
        }
        return out;
    }

    static synchronized void apply(List<UUID> checked, Set<UUID> positives, long nowMs) {
        Set<UUID> next = new HashSet<>(eligible);
        for (UUID id : checked) {
            CHECKED.put(id, nowMs);
            if (positives.contains(id)) {
                next.add(id);
            } else {
                next.remove(id);
            }
        }
        if (CHECKED.size() > 1024) {
            CHECKED.clear();
        }
        eligible = Set.copyOf(next);
    }
}
