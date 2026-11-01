package com.rkb.client.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CosmeticsLogicTest {
    // The same vectors are checked on the server side in supabase/tests/validate.test.mjs
    @Test
    void serverKeyMatchesServerRule() {
        assertEquals("play.example.com", ServerKey.normalize("Play.Example.com:25565"));
        assertEquals("play.example.com", ServerKey.normalize("play.example.com."));
        assertEquals("play.example.com:25566", ServerKey.normalize("play.example.com:25566"));
        assertEquals("mc.test", ServerKey.normalize("  mc.test  "));
        assertNull(ServerKey.normalize("bad host"));
        assertNull(ServerKey.normalize(""));
        assertNull(ServerKey.normalize(null));
        assertNull(ServerKey.normalize("a".repeat(101)));
    }

    @Test
    void assetRulesRejectUnsafeInput() {
        assertTrue(AssetRules.validPath("capes/blue_01.png"));
        assertFalse(AssetRules.validPath("../secret.png"));
        assertFalse(AssetRules.validPath("/abs.png"));
        assertFalse(AssetRules.validPath("a/b.jpg"));
        assertFalse(AssetRules.validPath(null));
        assertTrue(AssetRules.validCapeSize(64, 32));
        assertTrue(AssetRules.validCapeSize(512, 256));
        assertFalse(AssetRules.validCapeSize(64, 64));
        assertFalse(AssetRules.validCapeSize(1024, 512));
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", AssetRules.sha256Hex(new byte[0]));
        assertNull(AssetRules.storageUrl("http://x", "capes/a.png"));
    }

    @Test
    void oneAccountsCacheNeverLeaksToAnother() {
        AccountCache<String> cache = new AccountCache<>();
        cache.put("acct-1234", "inventory of 1234");
        assertEquals("inventory of 1234", cache.get("acct-1234"));
        assertNull(cache.get("acct-5678"));
        cache.put("acct-5678", "inventory of 5678");
        assertNull(cache.get("acct-1234"));
        assertEquals("inventory of 5678", cache.get("acct-5678"));
        cache.clear();
        assertNull(cache.get("acct-5678"));
    }

    @Test
    void unknownBadgeKeysAreIgnored() {
        assertTrue(BadgeGlyphs.supported("rkb_logo"));
        assertFalse(BadgeGlyphs.supported("made_up"));
        assertFalse(BadgeGlyphs.supported(null));
    }
}
