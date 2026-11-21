package com.rkb.client.cloud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rkb.client.cosmetics.AssetRules;
import com.rkb.client.cosmetics.BadgeGlyphs;
import com.rkb.client.cosmetics.CapeCollection;
import com.rkb.client.cosmetics.SkinRules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Shared request/response handling for the deployed {@code rkb-cosmetics-v2} Edge Function.
 *
 * The function is JWT protected and the authenticated response shape cannot be inspected from this client repository,
 * so parsing deliberately accepts the common field names used by the deployed RKB backend. It never invents an id,
 * asset path, hash, ownership flag or verification flag: if a field is missing, the item is treated as unavailable.
 */
public final class CosmeticsV2Api {
    public static final String FUNCTION = "rkb-cosmetics-v2";

    public static final String LINK_MINECRAFT = "link_minecraft";
    public static final String CATALOG = "catalog";
    public static final String INVENTORY = "inventory";
    public static final String EQUIP = "equip";
    public static final String UNEQUIP = "unequip";
    public static final String REDEEM = "redeem";
    public static final String PRESENCE = "presence";
    public static final String LOOKUP = "lookup";

    /** Short-lived caches: account data is re-fetched every few minutes, looks expire within a minute. */
    public static final long ACCOUNT_TTL_MS = 15 * 60_000L;
    public static final long LOOK_TTL_MS = 60_000L;

    public record CatalogItem(String id, String name, String description, String type, String rarity,
                              int priceCents, String currency, boolean claimable, String assetPath, String assetSha,
                              String badgeKey, boolean active, boolean assetReady) {
    }

    public record Inventory(Set<String> owned, Map<String, String> equipped) {
    }

    public record Asset(String id, String path, String sha) {
    }

    /** A remote player identity. A null or unverified identity is never returned by {@link #parseIdentities}. */
    public record Identity(UUID uuid, boolean verified, Asset cape, String badgeKey, Asset skin, String skinModel) {
        public boolean rkbClient() {
            return verified;
        }
    }

    private CosmeticsV2Api() {
    }

    public static JsonObject request(String action) {
        JsonObject body = new JsonObject();
        body.addProperty("action", action);
        return body;
    }

    public static JsonObject request(String action, String key, String value) {
        JsonObject body = request(action);
        if (key != null && !key.isEmpty()) {
            body.addProperty(key, value == null ? "" : value);
        }
        return body;
    }

    /**
     * Link body. Only the session username and the locally generated server id are sent. The Minecraft access token is
     * used for the official join on this device and is never included here.
     */
    public static JsonObject linkRequest(String minecraftName, String serverId) {
        JsonObject body = request(LINK_MINECRAFT);
        body.addProperty("minecraft_name", minecraftName == null ? "" : minecraftName);
        body.addProperty("server_id", serverId == null ? "" : serverId);
        return body;
    }

    /** Presence body. The raw server address and the Minecraft access token are never included. */
    public static JsonObject presenceRequest(String serverHash, UUID minecraftUuid, String clientVersion) {
        JsonObject body = request(PRESENCE);
        body.addProperty("server_hash", serverHash == null ? "" : serverHash);
        body.addProperty("mc_uuid", UuidUtil.canonical(minecraftUuid));
        body.addProperty("client_version", clientVersion == null ? "" : clientVersion);
        return body;
    }

    /** Lookup body. It contains hashed server context and UUIDs only, never names or raw server addresses. */
    public static JsonObject lookupRequest(String serverHash, Collection<UUID> uuids) {
        JsonObject body = request(LOOKUP);
        body.addProperty("server_hash", serverHash == null ? "" : serverHash);
        JsonArray ids = new JsonArray();
        if (uuids != null) {
            for (UUID id : uuids) {
                if (id != null) {
                    ids.add(UuidUtil.canonical(id));
                }
            }
        }
        body.add("uuids", ids);
        return body;
    }

    /** Offline guard shared with CloudService: no V2 call is possible without a signed-in session and a key. */
    public static boolean canCall(boolean signedIn, boolean keyPresent, boolean stopping) {
        return signedIn && keyPresent && !stopping;
    }

    /** True when cached data is missing or older than its short TTL. */
    public static boolean expired(long fetchedMs, long now, long ttlMs) {
        return fetchedMs <= 0 || now < fetchedMs || now - fetchedMs > ttlMs;
    }

    /** True when the response does not report an error. Missing {@code ok} is accepted. */
    public static boolean ok(JsonObject root) {
        if (root == null) {
            return false;
        }
        if (root.has("ok") && root.get("ok").isJsonPrimitive() && root.get("ok").getAsJsonPrimitive().isBoolean()) {
            return root.get("ok").getAsBoolean();
        }
        if (!error(root).isEmpty()) {
            return false;
        }
        if (root.has("success") && root.get("success").isJsonPrimitive()
                && root.get("success").getAsJsonPrimitive().isBoolean()) {
            return root.get("success").getAsBoolean();
        }
        return true;
    }

    public static String error(JsonObject root) {
        if (root == null) {
            return "empty_response";
        }
        String code = text(root, "error", "error_code", "code", "reason");
        if (!code.isEmpty()) {
            return code;
        }
        JsonObject nested = object(root, "error", "result", "response");
        if (nested != null) {
            code = text(nested, "error", "error_code", "code", "reason");
            if (!code.isEmpty()) {
                return code;
            }
        }
        // A message is only an error when the response explicitly says it failed.
        boolean failed = isFalseFlag(root, "ok") || isFalseFlag(root, "success");
        if (failed) {
            code = text(root, "message", "detail");
            if (!code.isEmpty()) {
                return code;
            }
        }
        return "";
    }

    /** UUID returned by {@code link_minecraft}; never trusts a name-only response. */
    public static String linkedUuid(JsonObject root) {
        if (root == null) {
            return "";
        }
        for (String key : new String[]{"minecraft_uuid", "mc_uuid", "linked_uuid", "player_uuid", "uuid",
                "minecraft_id", "verified_uuid"}) {
            String value = text(root, key);
            if (UuidUtil.parse(value) != null) {
                return UuidUtil.parse(value).toString();
            }
        }
        for (String key : new String[]{"minecraft", "identity", "player", "account", "link", "result", "data"}) {
            JsonObject nested = object(root, key);
            if (nested != null) {
                String value = linkedUuid(nested);
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        return "";
    }

    // ------------------------------------------------------------------ catalog

    public static List<CatalogItem> parseCatalog(JsonObject root) {
        List<CatalogItem> out = new ArrayList<>();
        for (JsonObject o : entries(root, "items", "catalog", "cosmetics", "data", "result", "response", "entries",
                "results", "list")) {
            String id = text(o, "id", "cosmetic_id", "slug", "key");
            if (id.isEmpty() || inactive(o)) {
                continue;
            }
            String type = normalizeType(text(o, "type", "category", "kind"));
            String path = text(o, "asset_path", "path", "file", "image_path", "asset");
            String sha = text(o, "asset_sha256", "sha256", "sha", "hash", "file_sha256");
            String badge = text(o, "badge_key", "badge", "glyph");
            boolean assetReady = switch (type) {
                case "badge" -> BadgeGlyphs.supported(badge);
                case "cape", "skin" -> AssetRules.validSha(sha) && (AssetRules.validPath(path)
                        || ("cape".equals(type) && CapeCollection.matchesBundled(id, sha)));
                default -> true;
            };
            out.add(new CatalogItem(id, text(o, "name", "display_name", "title"),
                    text(o, "description", "desc", "details"), type, text(o, "rarity", "tier"),
                    number(o, "price_cents", "price", "cost_cents"), text(o, "currency"),
                    bool(o, "claimable", "free"), path, sha, badge, true, assetReady));
        }
        return List.copyOf(out);
    }

    // ------------------------------------------------------------------ inventory

    public static Inventory parseInventory(JsonObject root) {
        Set<String> owned = new LinkedHashSet<>();
        Map<String, String> equipped = new LinkedHashMap<>();
        String[] entryKeys = {"owned", "inventory", "items", "cosmetics", "entries", "list", "data", "result", "response"};

        for (JsonObject o : entries(root, entryKeys)) {
            String id = text(o, "cosmetic_id", "id", "item_id", "slug");
            if (id.isEmpty()) {
                continue;
            }
            if (bool(o, "owned", "unlocked", "has") || o.has("cosmetic_id") || o.has("item_id")) {
                owned.add(id);
            }
        }
        addStringIds(root, owned, "owned", "inventory_ids", "cosmetic_ids");
        for (String parent : new String[]{"data", "result", "response"}) {
            addStringIds(object(root, parent), owned, "owned", "inventory_ids", "cosmetic_ids");
        }

        JsonObject eq = objectAny(root, "equipped", "equipped_cosmetics", "loadout");
        if (eq != null) {
            for (Map.Entry<String, JsonElement> e : eq.entrySet()) {
                String slot = normalizeSlot(e.getKey());
                String id = primitiveText(e.getValue());
                if (e.getValue().isJsonObject()) {
                    id = text(e.getValue().getAsJsonObject(), "cosmetic_id", "id", "item_id");
                }
                if (!slot.isEmpty() && !id.isEmpty()) {
                    equipped.put(slot, id);
                }
            }
        }
        for (JsonObject o : entries(root, "equipped", "equipped_cosmetics", "loadout", "data", "result", "response")) {
            String slot = normalizeSlot(text(o, "slot", "type", "category", "kind"));
            String id = text(o, "cosmetic_id", "id", "item_id");
            if (!slot.isEmpty() && !id.isEmpty()) {
                equipped.put(slot, id);
            }
        }
        for (String parent : new String[]{"", "data", "result", "response"}) {
            JsonObject source = parent.isEmpty() ? root : object(root, parent);
            for (String slot : new String[]{"cape", "badge", "skin"}) {
                String id = text(source, "equipped_" + slot, slot + "_id", slot);
                if (!id.isEmpty()) {
                    equipped.put(slot, id);
                }
            }
        }
        return new Inventory(Set.copyOf(owned), Map.copyOf(equipped));
    }

    // ------------------------------------------------------------------ multiplayer lookup

    public static List<Identity> parseIdentities(JsonObject root) {
        List<Identity> out = new ArrayList<>();
        for (JsonObject o : entries(root, "players", "identities", "looks", "results", "data", "result", "response",
                "list")) {
            UUID id = uuid(o, "uuid", "mc_uuid", "minecraft_uuid", "player_uuid", "id");
            if (id == null) {
                continue;
            }
            if (!verified(o)) {
                continue;   // never render a cape, badge, skin or prefix for an unverified identity
            }
            Asset cape = asset(o, "cape");
            Asset skin = asset(o, "skin");
            String skinModel = "";
            JsonObject skinObject = equippedObject(o, "skin");
            if (skinObject != null) {
                skinModel = text(skinObject, "model", "skin_model");
            }
            if (skin != null && !SkinRules.validModel(skinModel)) {
                skin = null;    // no valid model means no remote skin
            }
            out.add(new Identity(id, true, cape, badge(o), skin, skinModel));
        }
        return List.copyOf(out);
    }

    private static boolean verified(JsonObject o) {
        if (bool(o, "verified", "is_verified", "rkb_verified", "identity_verified")) {
            return true;
        }
        for (String key : new String[]{"identity", "player", "minecraft", "account", "result"}) {
            JsonObject nested = object(o, key);
            if (nested != null && bool(nested, "verified", "is_verified", "rkb_verified", "identity_verified")) {
                return true;
            }
        }
        return false;
    }

    private static Asset asset(JsonObject o, String kind) {
        JsonObject a = equippedObject(o, kind);
        if (a == null || inactive(a)) {
            return null;
        }
        String id = text(a, "id", "cosmetic_id", "asset_id", "slug");
        String path = text(a, "asset_path", "path", "file", "image_path");
        String sha = text(a, "asset_sha256", "sha256", "sha", "hash", "file_sha256");
        boolean bundledCape = "cape".equals(kind) && CapeCollection.matchesBundled(id, sha);
        if (id.isEmpty() || !AssetRules.validSha(sha) || (!AssetRules.validPath(path) && !bundledCape)) {
            return null;
        }
        return new Asset(id, path, sha);
    }

    private static String badge(JsonObject o) {
        String key = text(o, "badge_key", "badge", "glyph");
        JsonObject b = equippedObject(o, "badge");
        if (b != null && !inactive(b)) {
            String nested = text(b, "key", "badge_key", "id", "cosmetic_id");
            if (!nested.isEmpty()) {
                key = nested;
            }
        }
        return BadgeGlyphs.supported(key) ? key : null;
    }

    /** Finds cape/badge/skin beneath the player or in an equipped/loadout object. */
    private static JsonObject equippedObject(JsonObject o, String kind) {
        JsonObject direct = object(o, kind);
        if (direct != null) {
            return direct;
        }
        for (String parent : new String[]{"equipped", "equipped_cosmetics", "cosmetics", "loadout", "look"}) {
            JsonObject p = object(o, parent);
            if (p != null) {
                JsonObject nested = object(p, kind, kind + "_id");
                if (nested != null) {
                    return nested;
                }
            }
        }
        for (String parent : new String[]{"identity", "player", "minecraft", "result"}) {
            JsonObject p = object(o, parent);
            if (p != null) {
                JsonObject nested = equippedObject(p, kind);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ tolerant JSON helpers

    /** Direct or one-level nested object lookup. */
    public static JsonObject object(JsonObject o, String... keys) {
        if (o == null) {
            return null;
        }
        for (String key : keys) {
            if (o.has(key) && o.get(key).isJsonObject()) {
                return o.get(key).getAsJsonObject();
            }
        }
        return null;
    }

    /** Looks for an object at the root or beneath data/result/response envelopes. */
    public static JsonObject objectAny(JsonObject o, String... keys) {
        JsonObject direct = object(o, keys);
        if (direct != null) {
            return direct;
        }
        if (o == null) {
            return null;
        }
        for (String parent : new String[]{"data", "result", "response"}) {
            JsonObject nested = object(o, parent);
            if (nested != null) {
                JsonObject found = objectAny(nested, keys);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    public static String text(JsonObject o, String... keys) {
        if (o == null) {
            return "";
        }
        for (String key : keys) {
            if (!o.has(key) || o.get(key).isJsonNull()) {
                continue;
            }
            JsonElement e = o.get(key);
            if (e.isJsonPrimitive()) {
                String s = e.getAsString().trim();
                if (!s.isEmpty()) {
                    return s;
                }
            }
        }
        for (String key : keys) {
            JsonObject nested = object(o, key);
            if (nested != null) {
                for (String inner : new String[]{"id", "uuid", "key", "name", "value"}) {
                    if (nested.has(inner) && nested.get(inner).isJsonPrimitive()) {
                        String s = nested.get(inner).getAsString().trim();
                        if (!s.isEmpty()) {
                            return s;
                        }
                    }
                }
            }
        }
        return "";
    }

    public static String primitiveText(JsonElement e) {
        if (e != null && e.isJsonPrimitive() && !e.getAsJsonPrimitive().isBoolean()) {
            return e.getAsString().trim();
        }
        return "";
    }

    private static boolean isFalseFlag(JsonObject o, String key) {
        return o != null && o.has(key) && o.get(key).isJsonPrimitive()
                && o.get(key).getAsJsonPrimitive().isBoolean() && !o.get(key).getAsBoolean();
    }

    public static boolean bool(JsonObject o, String... keys) {
        if (o == null) {
            return false;
        }
        for (String key : keys) {
            if (o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsJsonPrimitive().isBoolean()
                    && o.get(key).getAsBoolean()) {
                return true;
            }
            if (o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsJsonPrimitive().isNumber()) {
                try {
                    if (o.get(key).getAsInt() == 1) {
                        return true;
                    }
                } catch (RuntimeException ignored) {
                    // not a numeric boolean
                }
            }
        }
        return false;
    }

    public static int number(JsonObject o, String... keys) {
        if (o == null) {
            return 0;
        }
        for (String key : keys) {
            if (o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsJsonPrimitive().isNumber()) {
                try {
                    return o.get(key).getAsInt();
                } catch (RuntimeException ignored) {
                    // keep looking
                }
            }
        }
        return 0;
    }

    private static UUID uuid(JsonObject o, String... keys) {
        return UuidUtil.parse(text(o, keys));
    }

    private static boolean inactive(JsonObject o) {
        if (bool(o, "active", "is_active", "enabled")) {
            return false;
        }
        for (String key : new String[]{"active", "is_active", "enabled"}) {
            if (o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsJsonPrimitive().isBoolean()) {
                return true;    // an explicit false wins even if a different flag says true
            }
        }
        String status = text(o, "status", "state").toLowerCase(Locale.ROOT);
        return status.equals("inactive") || status.equals("disabled") || status.equals("missing")
                || status.equals("placeholder");
    }

    private static String normalizeType(String type) {
        String t = type.trim().toLowerCase(Locale.ROOT);
        return switch (t) {
            case "capes", "couture" -> "cape";
            case "badges" -> "badge";
            case "skins" -> "skin";
            default -> t.isEmpty() ? "cosmetic" : t;
        };
    }

    private static String normalizeSlot(String slot) {
        String s = slot.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "cosmetic_id", "item_id", "id", "type", "category", "kind" -> "";
            case "hat", "body", "feet" -> "cape";
            default -> s;
        };
    }

    private static void addStringIds(JsonObject root, Set<String> out, String... keys) {
        if (root == null) {
            return;
        }
        for (String key : keys) {
            JsonElement e = root.get(key);
            if (e == null || !e.isJsonArray()) {
                continue;
            }
            for (JsonElement item : e.getAsJsonArray()) {
                String id = primitiveText(item);
                if (!id.isEmpty()) {
                    out.add(id);
                } else if (item.isJsonObject()) {
                    id = text(item.getAsJsonObject(), "cosmetic_id", "id", "item_id");
                    if (!id.isEmpty()) {
                        out.add(id);
                    }
                }
            }
        }
    }

    /** Returns object entries from the first matching array (or a nested data/result object). */
    private static List<JsonObject> entries(JsonObject root, String... keys) {
        List<JsonObject> out = new ArrayList<>();
        if (root == null) {
            return out;
        }
        for (String key : keys) {
            JsonElement e = root.get(key);
            if (e == null) {
                continue;
            }
            if (e.isJsonArray()) {
                for (JsonElement item : e.getAsJsonArray()) {
                    if (item.isJsonObject()) {
                        out.add(item.getAsJsonObject());
                    }
                }
                if (!out.isEmpty()) {
                    return out;
                }
            } else if (e.isJsonObject()) {
                JsonObject o = e.getAsJsonObject();
                if (looksLikeEntry(o)) {
                    out.add(o);
                    return out;
                }
                out.addAll(entries(o, keys));
                if (!out.isEmpty()) {
                    return out;
                }
            }
        }
        return out;
    }

    private static boolean looksLikeEntry(JsonObject o) {
        return o.has("id") || o.has("cosmetic_id") || o.has("uuid") || o.has("mc_uuid") || o.has("item_id");
    }

    /** Used by tests to keep the set of mutable remote identities small and predictable. */
    public static Set<UUID> uuids(List<Identity> identities) {
        Set<UUID> out = new HashSet<>();
        for (Identity i : identities) {
            out.add(i.uuid());
        }
        return out;
    }
}
