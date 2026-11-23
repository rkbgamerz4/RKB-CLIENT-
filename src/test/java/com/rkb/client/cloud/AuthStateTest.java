package com.rkb.client.cloud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AuthStateTest {
    @Test
    void signedOutNeverClaimsAnAccount() {
        AuthState state = AuthState.signedOut(true);
        assertFalse(state.signedIn());
        assertFalse(state.linked());
        assertFalse(state.canUseCloud());
        assertEquals("Signed out", state.accountLabel());
        assertEquals("Not linked", state.linkLabel());
        assertEquals("Local mode", state.statusLine());
    }

    @Test
    void signedInWithoutLinkAsksToLink() {
        AuthState state = new AuthState(true, true, false, false, true, "pilot@rkb.gg", "");
        assertTrue(state.canUseCloud());
        assertEquals("pilot@rkb.gg", state.accountLabel());
        assertEquals("Link Minecraft", state.linkLabel());
        assertEquals("Link required", state.statusLine());
        assertEquals(0xFFFFC857, state.tone());
    }

    @Test
    void linkedAccountIsCloudReady() {
        AuthState state = new AuthState(true, true, true, false, true, "pilot@rkb.gg", "");
        assertEquals("Minecraft linked", state.linkLabel());
        assertEquals("Cloud ready", state.statusLine());
        assertEquals(0xFF43D99A, state.tone());
    }

    @Test
    void missingConfigurationIsReportedHonestly() {
        AuthState state = AuthState.signedOut(false);
        assertEquals("Cloud key missing", state.statusLine());
        assertFalse(state.canUseCloud());
    }

    @Test
    void blankEmailNeverRendersAsSignedInIdentity() {
        AuthState state = new AuthState(true, true, false, false, true, "  ", "");
        assertEquals("Signed in", state.accountLabel());
    }
}
