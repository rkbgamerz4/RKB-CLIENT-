package com.rkb.client.cloud;

/** A non-2xx answer from Supabase or the Edge Function. The message is a short code, never a secret. */
public final class CloudException extends Exception {
    private final int status;

    public CloudException(int status, String code) {
        super(code);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
