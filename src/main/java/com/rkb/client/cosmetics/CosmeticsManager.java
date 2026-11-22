package com.rkb.client.cosmetics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rkb.client.RkbClient;
import com.rkb.client.cloud.CloudException;
import com.rkb.client.cloud.CloudService;
import com.rkb.client.cloud.CloudStore;
import com.rkb.client.cloud.CosmeticsV2Api;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The signed-in RKB account's V2 catalog, inventory and equipment.
 *
 * Only the deployed {@code rkb-cosmetics-v2} function can grant, own or equip anything. This class keeps a short-lived
 * display cache, expires it when it is stale, and never invents an id, asset path, hash or ownership flag.
 */
public final class CosmeticsManager {
    public record Item(String id, String name, String description, String type, String rarity, int priceCents,
                       String currency, boolean claimable, String purchaseInfo, String assetPath, String assetSha,
                       String badgeKey, boolean owned, boolean active, boolean assetReady) {
        public boolean usable() {
            return owned && active && assetReady;
        }
    }

    /** V2 has no history action; kept so existing callers can display an empty list. */
    public record Entry(String cosmeticId, String kind, String status, int amountCents, String currency,
                        String reference, String createdAt) {
    }

    public record State(String rkbId, List<Item> items, Map<String, String> equipped, List<Entry> history,
                        long fetchedMs) {
    }

    private static final State EMPTY = new State("", List.of(), Map.of(), List.of(), 0);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AccountCache<State> CACHE = new AccountCache<>();

    private static volatile boolean busy;
    private static volatile int version;
    private static volatile String message = "";
    private static volatile boolean lastResultOk;
    private static String diskTriedFor = "";

    private CosmeticsManager() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("rkbclient-cosmetics-account.json");
    }

    // ------------------------------------------------------------------ reads (UI)

    /** Never returns expired remote data. Local skins and bundled cape textures are independent of this cache. */
    public static State state() {
        String user = CloudStore.userId();
        State s = CACHE.get(user);
        if (s == null && !user.isEmpty() && !user.equals(diskTriedFor)) {
            diskTriedFor = user;
            s = loadDisk(user);
        }
        return s == null || stale(s) ? EMPTY : s;
    }

    public static boolean stale() {
        State s = CACHE.get(CloudStore.userId());
        return s != null && stale(s);
    }

    private static boolean stale(State s) {
        return CosmeticsV2Api.expired(s.fetchedMs(), System.currentTimeMillis(), CosmeticsV2Api.ACCOUNT_TTL_MS);
    }

    public static boolean busy() {
        return busy;
    }

    public static int version() {
        return version;
    }

    public static String message() {
        return message;
    }

    /** True only for the most recent V2 action that the backend actually accepted. */
    public static boolean lastResultOk() {
        return lastResultOk;
    }

    public static boolean ready() {
        return CloudStore.signedIn() && !CloudStore.anonKey().isEmpty();
    }

    // ------------------------------------------------------------------ account changes (game thread)

    public static void onAccountChanged() {
        CACHE.clear();
        diskTriedFor = "";
        RemoteCosmetics.setSelf(null);
        version++;
        refresh();
        RemoteCosmetics.onFeatureChanged();
        SkinSetStore.onAccountChanged();
    }

    public static void onSignedOut() {
        CACHE.clear();
        diskTriedFor = "";
        message = "";
        RemoteCosmetics.setSelf(null);
        try {
            Files.deleteIfExists(file());
        } catch (IOException e) {
            RkbClient.LOGGER.warn("Could not delete the cosmetics cache: {}", e.toString());
        }
        SkinSetStore.onAccountChanged();
        version++;
    }

    // ------------------------------------------------------------------ V2 data and actions

    /** Called by {@link CloudService} after a successful catalog + inventory fetch. */
    public static void applyV2(String account, List<CosmeticsV2Api.CatalogItem> catalog,
                        CosmeticsV2Api.Inventory inventory, String linkedUuid) {
        if (account.isEmpty() || !account.equals(CloudStore.userId())) {
            return;
        }
        List<Item> items = new ArrayList<>();
        Map<String, Item> byId = new HashMap<>();
        for (CosmeticsV2Api.CatalogItem c : catalog) {
            Item item = new Item(c.id(), emptyTo(c.name(), c.id()), c.description(), c.type(), c.rarity(),
                    c.priceCents(), emptyTo(c.currency(), "USD"), c.claimable(), "", c.assetPath(), c.assetSha(),
                    c.badgeKey(), inventory.owned().contains(c.id()), c.active(), c.assetReady());
            items.add(item);
            byId.put(item.id(), item);
        }
        // Bundled designs stay visible for reference, but a missing catalog row never grants or activates them.
        for (CapeCollection.Design d : CapeCollection.all()) {
            if (!byId.containsKey(d.id())) {
                Item fallback = new Item(d.id(), d.name(), d.description(), "cape", d.rarity(), 0, "USD", false,
                        "", "", CapeCollection.bundledSha(d.id()), "", false, false,
                        CapeCollection.bundledSha(d.id()) != null);
                items.add(fallback);
                byId.put(fallback.id(), fallback);
            }
        }

        Map<String, String> equipped = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : inventory.equipped().entrySet()) {
            String slot = normalizeSlot(e.getKey());
            Item item = byId.get(e.getValue());
            if (item != null && item.usable() && compatible(slot, item.type())) {
                equipped.put(slot, item.id());
            }
        }

        State st = new State(emptyTo(linkedUuid, ""), List.copyOf(items), Map.copyOf(equipped), List.of(),
                System.currentTimeMillis());
        CACHE.put(account, st);
        saveDisk(account, st);
        MinecraftClient.getInstance().execute(() -> RemoteCosmetics.setSelf(ownLook(st)));
        message = "";
        version++;
    }

    public static void refresh() {
        if (!ready() || busy) {
            return;
        }
        busy = true;
        version++;
        CloudService.background(() -> {
            try {
                CloudService.syncNow(false);
                if (stale()) {
                    say("Remote data is still unavailable. Local skins remain ready.", false);
                }
            } catch (Throwable t) {
                say("Could not refresh cosmetics: " + t.getMessage(), false);
            } finally {
                busy = false;
                version++;
            }
        });
    }

    public static void redeem(String code) {
        if (code == null || code.trim().isEmpty()) {
            say("Enter a redeem code first.", true);
            return;
        }
        act(CosmeticsV2Api.request(CosmeticsV2Api.REDEEM, "code", code.trim()),
                r -> "Code accepted. Refreshing your inventory.");
    }

    public static void equip(String cosmeticId) {
        act(CosmeticsV2Api.request(CosmeticsV2Api.EQUIP, "cosmetic_id", cosmeticId),
                r -> "Equipped " + cosmeticId + ".");
    }

    public static void unequip(String slot) {
        act(CosmeticsV2Api.request(CosmeticsV2Api.UNEQUIP, "slot", normalizeSlot(slot)),
                r -> "Unequipped your " + normalizeSlot(slot) + ".");
    }

    /** V2 does not expose claim/purchase_start. Kept only so old callers get a truthful answer. */
    public static void claim(String cosmeticId) {
        say("RKB Cosmetics V2 has no claim action. Use Redeem instead.", true);
    }

    public static void purchaseStart(String cosmeticId) {
        say("RKB Cosmetics V2 has no purchase action in this client.", true);
    }

    private static void act(JsonObject body, Function<JsonObject, String> okMessage) {
        if (!ready()) {
            say("Sign in first: /rkb cloud login <email> <password>", true);
            return;
        }
        if (busy) {
            say("One moment, still working...", false);
            return;
        }
        busy = true;
        version++;
        CloudService.background(() -> {
            try {
                JsonObject res = CloudService.callV2(body);
                if (!CosmeticsV2Api.ok(res)) {
                    say(errorText(CosmeticsV2Api.error(res)), true, false);
                } else {
                    say(okMessage.apply(res), true, true);
                    CloudService.syncNow(false);
                }
            } catch (CloudException e) {
                say(e.status() == 429 ? errorText("rate_limited") : e.status() == 401
                        ? "Your RKB session ended. Sign in again." : errorText(e.getMessage()), true);
            } catch (IOException e) {
                say("You are offline. No cloud change was made.", true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                busy = false;
                version++;
            }
        });
    }

    static String errorText(String code) {
        return switch (code == null ? "" : code) {
            case "not_found", "unknown_cosmetic" -> "That cosmetic does not exist or is not available.";
            case "not_available", "inactive" -> "That cosmetic is not available right now.";
            case "not_owned" -> "You do not own that cosmetic.";
            case "asset_unavailable" -> "The backend asset for that cosmetic is not ready.";
            case "invalid_or_used_code" -> "That code is not valid, was already used or has expired.";
            case "rate_limited", "too_many_requests" -> "Slow down a little and try again.";
            case "identity_not_verified", "not_linked", "link_required" ->
                    "Link this Minecraft account with /rkb cloud link first.";
            case "signed_out" -> "Your RKB session ended. Sign in again.";
            default -> "Something went wrong (" + (code == null || code.isEmpty() ? "request_failed" : code) + ").";
        };
    }

    private static void say(String text, boolean chat) {
        say(text, chat, false);
    }

    private static void say(String text, boolean chat, boolean ok) {
        message = text;
        lastResultOk = ok;
        version++;
        if (chat) {
            CloudService.notifyPlayer(text);
        }
    }

    static RemoteCosmetics.Look ownLook(State st) {
        Item cape = find(st, st.equipped().get("cape"));
        Item badge = find(st, st.equipped().get("badge"));
        if ((cape == null || !cape.usable()) && (badge == null || !badge.usable())) {
            return null;
        }
        String capeId = cape == null ? null : cape.id();
        String path = cape == null ? null : cape.assetPath();
        String sha = cape != null && cape.usable() && AssetRules.validSha(cape.assetSha()) ? cape.assetSha() : null;
        String key = badge != null && badge.usable() && BadgeGlyphs.supported(badge.badgeKey())
                ? badge.badgeKey() : null;
        if (sha == null && key == null) {
            return null;
        }
        // The local player's skin comes from SkinSetStore; remote skin fields stay empty for the account look.
        return new RemoteCosmetics.Look(capeId, path, sha, key, null, null, null, null, true, false);
    }

    // ------------------------------------------------------------------ collection helpers

    public static List<Item> collection() {
        return state().items();
    }

    public static Item item(String id) {
        if (id == null) {
            return null;
        }
        for (Item i : collection()) {
            if (i.id().equals(id)) {
                return i;
            }
        }
        return null;
    }

    public static Item equippedItem(String slot) {
        State st = state();
        String id = st.equipped().get(normalizeSlot(slot));
        return id == null ? null : find(st, id);
    }

    public static boolean isEquipped(String id) {
        if (id == null) {
            return false;
        }
        return state().equipped().containsValue(id);
    }

    public static List<Item> byType(String type) {
        List<Item> out = new ArrayList<>();
        for (Item i : collection()) {
            if (type.equals(i.type())) {
                out.add(i);
            }
        }
        return List.copyOf(out);
    }

    public static List<Item> countryCapes() {
        List<Item> out = new ArrayList<>();
        for (Item i : byType("cape")) {
            CapeCollection.Design d = CapeCollection.byId(i.id());
            if (d != null && !"RKB".equals(d.country())) {
                out.add(i);
            }
        }
        return List.copyOf(out);
    }

    public static List<Item> originalCapes() {
        List<Item> out = new ArrayList<>();
        for (Item i : byType("cape")) {
            CapeCollection.Design d = CapeCollection.byId(i.id());
            if (d == null || "RKB".equals(d.country())) {
                out.add(i);
            }
        }
        return List.copyOf(out);
    }

    private static Item find(State st, String id) {
        if (id == null) {
            return null;
        }
        for (Item i : st.items()) {
            if (i.id().equals(id)) {
                return i;
            }
        }
        return null;
    }

    private static String normalizeSlot(String slot) {
        String s = slot == null ? "" : slot.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (s) {
            case "capes", "couture" -> "cape";
            case "badges" -> "badge";
            case "skins" -> "skin";
            default -> s;
        };
    }

    private static boolean compatible(String slot, String type) {
        return slot.isEmpty() || slot.equals(type);
    }

    private static String emptyTo(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    // ------------------------------------------------------------------ display cache (expires quickly)

    private static synchronized void saveDisk(String accountId, State st) {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("accountId", accountId);
            root.addProperty("rkbId", st.rkbId());
            root.addProperty("fetchedMs", st.fetchedMs());
            JsonArray items = new JsonArray();
            for (Item i : st.items()) {
                JsonObject o = new JsonObject();
                o.addProperty("id", i.id());
                o.addProperty("name", i.name());
                o.addProperty("description", i.description());
                o.addProperty("type", i.type());
                o.addProperty("rarity", i.rarity());
                o.addProperty("price_cents", i.priceCents());
                o.addProperty("currency", i.currency());
                o.addProperty("asset_path", i.assetPath());
                o.addProperty("asset_sha256", i.assetSha());
                o.addProperty("badge_key", i.badgeKey());
                o.addProperty("owned", i.owned());
                o.addProperty("active", i.active());
                o.addProperty("asset_ready", i.assetReady());
                items.add(o);
            }
            root.add("items", items);
            JsonObject eq = new JsonObject();
            for (Map.Entry<String, String> e : st.equipped().entrySet()) {
                eq.addProperty(e.getKey(), e.getValue());
            }
            root.add("equipped", eq);
            Path f = file();
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            RkbClient.LOGGER.warn("Could not save the cosmetics cache: {}", e.toString());
        }
    }

    private static State loadDisk(String accountId) {
        Path f = file();
        if (!Files.isRegularFile(f)) {
            return null;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!accountId.equals(str(root, "accountId"))) {
                return null;   // the cache belongs to another account: ignore it
            }
            Set<String> owned = new LinkedHashSet<>();
            List<Item> items = new ArrayList<>();
            if (root.has("items") && root.get("items").isJsonArray()) {
                for (JsonElement e : root.getAsJsonArray("items")) {
                    JsonObject o = e.getAsJsonObject();
                    String id = str(o, "id");
                    if (id.isEmpty()) {
                        continue;
                    }
                    boolean isOwned = bool(o, "owned");
                    if (isOwned) {
                        owned.add(id);
                    }
                    items.add(new Item(id, str(o, "name"), str(o, "description"), str(o, "type"), str(o, "rarity"),
                            num(o, "price_cents"), str(o, "currency"), false, "", str(o, "asset_path"),
                            str(o, "asset_sha256"), str(o, "badge_key"), isOwned,
                            !o.has("active") || bool(o, "active"),
                            !o.has("asset_ready") || bool(o, "asset_ready")));
                }
            }
            Map<String, String> equipped = new LinkedHashMap<>();
            if (root.has("equipped") && root.get("equipped").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("equipped").entrySet()) {
                    JsonElement value = e.getValue();
                    String id = value.isJsonObject() ? str(value.getAsJsonObject(), "cosmetic_id")
                            : value.isJsonPrimitive() ? value.getAsString() : "";
                    if (!id.isEmpty()) {
                        equipped.put(normalizeSlot(e.getKey()), id);
                    }
                }
            } else if (root.has("equipped") && root.get("equipped").isJsonArray()) {
                for (JsonElement e : root.getAsJsonArray("equipped")) {
                    JsonObject o = e.getAsJsonObject();
                    equipped.put(str(o, "slot"), str(o, "cosmetic_id"));
                }
            }
            State st = new State(str(root, "rkbId"), List.copyOf(items), Map.copyOf(equipped), List.of(),
                    num(root, "fetchedMs"));
            CACHE.put(accountId, st);
            return st;
        } catch (Exception e) {
            RkbClient.LOGGER.warn("Cosmetics cache is unreadable ({}).", e.toString());
            return null;
        }
    }

    private static String str(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : "";
    }

    private static int num(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() && o.get(k).getAsJsonPrimitive().isNumber()
                ? o.get(k).getAsInt() : 0;
    }

    private static boolean bool(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() && o.get(k).getAsJsonPrimitive().isBoolean()
                && o.get(k).getAsBoolean();
    }
}
