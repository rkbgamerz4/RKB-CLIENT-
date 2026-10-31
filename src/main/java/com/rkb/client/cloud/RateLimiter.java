package com.rkb.client.cloud;

/** Allows an action at most once per interval. Not thread-safe on purpose; use from one thread. */
public final class RateLimiter {
    private final long intervalMs;
    private long last = Long.MIN_VALUE / 2;

    public RateLimiter(long intervalMs) {
        this.intervalMs = intervalMs;
    }

    public boolean tryAcquire(long nowMs) {
        if (nowMs - last >= intervalMs) {
            last = nowMs;
            return true;
        }
        return false;
    }
}
