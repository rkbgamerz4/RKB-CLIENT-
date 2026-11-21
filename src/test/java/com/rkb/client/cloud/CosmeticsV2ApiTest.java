package com.rkb.client.cloud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rkb.client.cosmetics.CapeCollection;
import com.rkb.client.cosmetics.ServerKey;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

class CosmeticsV2ApiTest {
    private static final UUID PLAYER = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String SHA = "a".repeat(64);

    @Test
    void serverAddressIsHashedLocally() {
        assertEquals("64ec67cac6003fc199c0203a22e0e3f80159fe12506407f8d322cbfc88c0f6ad",
                ServerKey.sha256Hex("Play.Example.com:25565"));
        assertNull(ServerKey.sha256Hex("not a valid host"));
    }

    @Test
    void requestBodiesContainOnlyAllowedV2Fields() {
        JsonObject link = CosmeticsV2Api.linkRequest("Notch", "server-id");
        assertEquals(Set.of("action", "minecraft_name", "server_id"), link.keySet());
        assertEquals("link_minecraft", link.get("action").getAsString());
        assertFalse(link.has("access_token"));
        assertFalse(link.has("token"));

        JsonObject presence = CosmeticsV2Api.presenceRequest("hash", PLAYER, "0.9.0");
        assertEquals(Set.of("action", "server_hash", "mc_uuid", "client_version"), presence.keySet());
        assertEquals("069a79f4-44e9-4726-a5be-fca90e38aaf5", presence.get("mc_uuid").getAsString());
        assertFalse(presence.has("server"));
        assertFalse(presence.has("address"));
        assertFalse(presence.has("minecraft_name"));
        assertFalse(presence.has("access_token"));

        JsonObject lookup = CosmeticsV2Api.lookupRequest("hash", Arrays.asList(PLAYER, null));
        assertEquals(Set.of("action", "server_hash", "uuids"), lookup.keySet());
        assertEquals(1, lookup.getAsJsonArray("uuids").size());
        assertEquals("069a79f4-44e9-4726-a5be-fca90e38aaf5",
                lookup.getAsJsonArray("uuids").get(0).getAsString());
        assertFalse(lookup.toString().contains("address"));
        assertFalse(lookup.toString().contains("access_token"));
    }

    @Test
    void offlineAndExpiredDataAreRejected() {
        assertTrue(CosmeticsV2Api.canCall(true, true, false));
        assertFalse(CosmeticsV2Api.canCall(false, true, false));
        assertFalse(CosmeticsV2Api.canCall(true, false, false));
        assertFalse(CosmeticsV2Api.canCall(true, true, true));

        assertFalse(CosmeticsV2Api.expired(1_000, 1_500, 1_000));
        assertTrue(CosmeticsV2Api.expired(1_000, 2_001, 1_000));
        assertTrue(CosmeticsV2Api.expired(0, 2_000, 1_000));
        assertTrue(CosmeticsV2Api.expired(1_000, 900, 1_000));
    }

    @Test
    void tolerantResponseHandlingDoesNotTrustSuccessMessages() {
        JsonObject success = json("{\"ok\":true,\"message\":\"linked\"}");
        assertTrue(CosmeticsV2Api.ok(success));
        assertEquals("", CosmeticsV2Api.error(success));

        JsonObject failed = json("{\"ok\":false,\"message\":\"bad_code\"}");
        assertFalse(CosmeticsV2Api.ok(failed));
        assertEquals("bad_code", CosmeticsV2Api.error(failed));

        JsonObject errorOnly = json("{\"error\":\"rate_limited\"}");
        assertFalse(CosmeticsV2Api.ok(errorOnly));
        assertEquals("rate_limited", CosmeticsV2Api.error(errorOnly));
    }

    @Test
    void linkingRequiresAnActualUuid() {
        assertEquals("", CosmeticsV2Api.linkedUuid(json("{\"minecraft_name\":\"Notch\"}")));
        assertEquals(PLAYER.toString(), CosmeticsV2Api.linkedUuid(
                json("{\"data\":{\"minecraft_uuid\":\"" + PLAYER + "\"}}")));
    }

    @Test
    void catalogSkipsInactivePlaceholderAndMissingAssets() {
        String bundled = CapeCollection.bundledSha("cape_bangladesh");
        assertNotNull(bundled);
        JsonObject root = json("{\"items\":["
                + "{\"id\":\"cape_ok\",\"type\":\"cape\",\"asset_path\":\"capes/a.png\",\"asset_sha256\":\"" + SHA + "\"},"
                + "{\"id\":\"cape_no_asset\",\"type\":\"cape\"},"
                + "{\"id\":\"cape_placeholder\",\"type\":\"cape\",\"status\":\"placeholder\"},"
                + "{\"id\":\"cape_bangladesh\",\"type\":\"cape\",\"asset_sha256\":\"" + bundled + "\"},"
                + "{\"id\":\"skin_fake_bundle\",\"type\":\"skin\",\"asset_sha256\":\"" + bundled + "\"},"
                + "{\"id\":\"badge_unknown\",\"type\":\"badge\",\"badge_key\":\"made_up\"},"
                + "{\"name\":\"no id\"}]}");

        var items = CosmeticsV2Api.parseCatalog(root);
        assertEquals(5, items.size());
        Set<String> ids = items.stream().map(CosmeticsV2Api.CatalogItem::id)
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(ids.contains("cape_ok"));
        assertTrue(ids.contains("cape_bangladesh"));
        assertFalse(ids.contains("cape_placeholder"));
        assertFalse(ids.contains(""));
        for (var item : items) {
            boolean ready = switch (item.id()) {
                case "cape_ok", "cape_bangladesh" -> true;
                default -> false;
            };
            assertEquals(ready, item.assetReady(), item.id());
            assertTrue(item.active(), item.id());
        }
    }

    @Test
    void inventoryParsesNestedBackendEnvelopes() {
        JsonObject root = json("{\"ok\":true,\"data\":{\"owned\":[{\"cosmetic_id\":\"cape_ok\"},\"badge_ok\"],"
                + "\"equipped\":{\"cape\":\"cape_ok\",\"badge\":{\"cosmetic_id\":\"rkb_logo\"}}}}");
        var inventory = CosmeticsV2Api.parseInventory(root);
        assertEquals(Set.of("cape_ok", "badge_ok"), inventory.owned());
        assertEquals("cape_ok", inventory.equipped().get("cape"));
        assertEquals("rkb_logo", inventory.equipped().get("badge"));
    }

    @Test
    void lookupReturnsOnlyVerifiedIdentitiesWithSafeAssets() {
        String bundled = CapeCollection.bundledSha("cape_bangladesh");
        assertNotNull(bundled);
        JsonObject root = json("{\"players\":["
                + "{\"uuid\":\"" + PLAYER + "\",\"verified\":true,\"badge_key\":\"rkb_logo\","
                + "\"cape\":{\"id\":\"cape_bangladesh\",\"asset_sha256\":\"" + bundled + "\"},"
                + "\"skin\":{\"id\":\"skin_x\",\"asset_path\":\"skins/x.png\",\"asset_sha256\":\"" + SHA + "\","
                + "\"model\":\"classic\"}},"
                + "{\"uuid\":\"" + OTHER + "\",\"verified\":false,\"badge_key\":\"rkb_logo\"},"
                + "{\"uuid\":\"00000000-0000-0000-0000-000000000002\",\"verified\":true,"
                + "\"badge_key\":\"made_up\",\"skin\":{\"id\":\"cape_bangladesh\",\"asset_sha256\":\"" + bundled
                + "\",\"model\":\"classic\"}}]}");

        var identities = CosmeticsV2Api.parseIdentities(root);
        Set<UUID> verified = CosmeticsV2Api.uuids(identities);
        assertEquals(2, verified.size());
        assertTrue(verified.contains(PLAYER));
        assertFalse(verified.contains(OTHER));   // explicitly unverified

        var identity = identities.stream().filter(i -> i.uuid().equals(PLAYER)).findFirst().orElseThrow();
        assertTrue(identity.verified());
        assertNotNull(identity.cape());
        assertNotNull(identity.skin());
        assertEquals("rkb_logo", identity.badgeKey());

        var assetless = identities.stream().filter(i -> !i.uuid().equals(PLAYER)).findFirst().orElseThrow();
        assertTrue(assetless.verified());
        assertNull(assetless.cape());
        assertNull(assetless.skin());
        assertNull(assetless.badgeKey());
    }

    private static JsonObject json(String value) {
        return JsonParser.parseString(value).getAsJsonObject();
    }
}
