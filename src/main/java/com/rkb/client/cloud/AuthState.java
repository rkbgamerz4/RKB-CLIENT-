package com.rkb.client.cloud;

/**
 * Pure, display-safe snapshot of the RKB account state. It never contains a token or password and can be unit tested
 * without Minecraft.
 */
public record AuthState(boolean configured, boolean signedIn, boolean linked, boolean busy, boolean lastOk,
                        String email, String message) {
    public static AuthState signedOut(boolean configured) {
        return new AuthState(configured, false, false, false, false, "", "");
    }

    public boolean canUseCloud() {
        return configured && signedIn;
    }

    public String accountLabel() {
        if (!signedIn) {
            return "Signed out";
        }
        return email == null || email.isBlank() ? "Signed in" : email;
    }

    public String linkLabel() {
        if (!signedIn) {
            return "Not linked";
        }
        return linked ? "Minecraft linked" : "Link Minecraft";
    }

    public String statusLine() {
        if (!configured) {
            return "Cloud key missing";
        }
        if (!signedIn) {
            return "Local mode";
        }
        if (linked) {
            return "Cloud ready";
        }
        return "Link required";
    }

    public int tone() {
        if (!signedIn) {
            return 0xFF96A8BE;
        }
        if (linked) {
            return 0xFF43D99A;
        }
        return 0xFFFFC857;
    }
}
