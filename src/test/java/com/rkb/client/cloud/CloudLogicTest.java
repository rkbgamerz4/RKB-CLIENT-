package com.rkb.client.cloud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

class CloudLogicTest {
    @Test
    void parsesDashedAndCompactUuids() {
        UUID expected = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
        assertEquals(expected, UuidUtil.parse("069a79f4-44e9-4726-a5be-fca90e38aaf5"));
        assertEquals(expected, UuidUtil.parse("069a79f444e94726a5befca90e38aaf5"));
        assertEquals(expected, UuidUtil.parse("  069A79F4-44E9-4726-A5BE-FCA90E38AAF5  "));
    }

    @Test
    void rejectsInvalidUuids() {
        assertNull(UuidUtil.parse(null));
        assertNull(UuidUtil.parse(""));
        assertNull(UuidUtil.parse("not-a-uuid"));
        assertNull(UuidUtil.parse("069a79f4-44e9-4726-a5be-fca90e38aafZ"));
    }

    @Test
    void validatesUsernames() {
        assertTrue(UuidUtil.isValidUsername("Notch"));
        assertTrue(UuidUtil.isValidUsername("a_b"));
        assertFalse(UuidUtil.isValidUsername("ab"));
        assertFalse(UuidUtil.isValidUsername("this_name_is_way_too_long"));
        assertFalse(UuidUtil.isValidUsername("bad name"));
        assertFalse(UuidUtil.isValidUsername(null));
    }

    @Test
    void newerTimestampWins() {
        assertEquals(SyncMerge.Decision.PULL_FROM_SERVER, SyncMerge.decide(100, 200));
        assertEquals(SyncMerge.Decision.PUSH_TO_SERVER, SyncMerge.decide(300, 200));
        assertEquals(SyncMerge.Decision.KEEP, SyncMerge.decide(200, 200));
        assertEquals(SyncMerge.Decision.KEEP, SyncMerge.decide(0, 0));
        assertEquals(SyncMerge.Decision.PULL_FROM_SERVER, SyncMerge.decide(0, 5));
    }

    @Test
    void rateLimiterSpacesCalls() {
        RateLimiter r = new RateLimiter(1000);
        assertTrue(r.tryAcquire(0));
        assertFalse(r.tryAcquire(500));
        assertTrue(r.tryAcquire(1000));
    }

    @Test
    void cosmeticsParsingIsLenientAndSkipsInactive() {
        JsonObject root = JsonParser.parseString(
                "{\"cosmetics\":[{\"id\":\"cape_1\",\"name\":\"Cape\",\"type\":\"cape\",\"is_active\":true},"
                        + "{\"id\":\"old\",\"name\":\"Old\",\"type\":\"cape\",\"is_active\":false},{\"name\":\"no id\"}]}")
                .getAsJsonObject();
        List<CosmeticsCatalog.Entry> out = CosmeticsCatalog.parse(root);
        assertEquals(1, out.size());
        assertNotNull(out.get(0));
        assertEquals("cape_1", out.get(0).id());
    }
}
