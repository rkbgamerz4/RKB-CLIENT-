package com.rkb.client.cloud;

/**
 * Conflict rule for cloud sync, used for both settings and the friends list:
 * the side with the newer timestamp wins; equal timestamps change nothing.
 */
public final class SyncMerge {
    public enum Decision { KEEP, PULL_FROM_SERVER, PUSH_TO_SERVER }

    private SyncMerge() {
    }

    /**
     * @param localMs  local last-change time (0 = never changed locally)
     * @param serverMs server last-change time (0 = nothing on the server)
     */
    public static Decision decide(long localMs, long serverMs) {
        if (serverMs > localMs) {
            return Decision.PULL_FROM_SERVER;
        }
        if (localMs > serverMs) {
            return Decision.PUSH_TO_SERVER;
        }
        return Decision.KEEP;
    }
}
