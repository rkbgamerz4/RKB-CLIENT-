package com.rkb.client.cosmetics;

/**
 * Holds data for exactly ONE RKB account. Asking for a different account id returns nothing, and storing data for a
 * different account replaces the old data, so one account's inventory can never be shown for another account
 * (account switching, shared PCs, a second login). Pure Java.
 */
public final class AccountCache<T> {
    private String accountId = "";
    private T data;

    public synchronized void put(String accountId, T data) {
        this.accountId = accountId == null ? "" : accountId;
        this.data = data;
    }

    /** The cached data, or null when none exists for exactly this account. */
    public synchronized T get(String accountId) {
        if (accountId == null || accountId.isEmpty() || !accountId.equals(this.accountId)) {
            return null;
        }
        return data;
    }

    public synchronized String accountId() {
        return accountId;
    }

    public synchronized void clear() {
        accountId = "";
        data = null;
    }
}
