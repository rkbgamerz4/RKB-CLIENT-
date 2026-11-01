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
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The signed-in RKB account's catalog, inventory, equipment and purchase history.
 *
 * The server is the only authority. This class never grants, prices or "unlocks" anything: it sends an action
 * (claim, redeem, equip ...) and shows what the server answered. The data it keeps is a cache that belongs to exactly
 * one account (AccountCache), is replaced on every account change and is deleted on sign-out.
 */
public final class CosmeticsManager {
    public record Item(String id, String name, String description, String type, String rarity, int priceCents,
                       String currency, boolean claimable, String purchaseInfo, String assetPath, String assetSha,
                       String badgeKey, boolean owned) {
    }

    public record Entry(String cosmeticId, String kind, String status, int amountCents, String currency,
                        String reference, String createdAt) {
    }

    public record State(String rkbId, List<Item> items, Map<String, String> equipped, List<Entry> history, long fetchedMs) {
    }

    private static final State EMPTY = new State("", List.of(), Map.of(), List.of(), 0);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AccountCache<State> CACHE = new AccountCache<>();

    private static volatile boolean busy;
    private static volatile int version;
    private static volatile String message = "";
    private static String diskTriedFor = "";

    private CosmeticsManager() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("rkbclient-cosmetics-account.json");
    }

    // ------------------------------------------------------------------ reads (UI)

    public static State state() {
        String user = CloudStore.userId();
        State s = CACHE.get(user);
        if (s == null && !user.isEmpty() && !user.equals(diskTriedFor)) {
            diskTriedFor = user;
            s = loadDisk(user);
        }
        return s == null ? EMPTY : s;
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
    }

    public static void onSignedOut() {
        CACHE.clear();
        diskTriedFor = "";
        RemoteCosmetics.setSelf(null);
        try {
            Files.deleteIfExists(file());
        } catch (IOException e) {
            RkbClient.LOGGER.warn("Could not delete the cosmetics cache: {}", e.toString());
        }
        version++;
    }

    // ------------------------------------------------------------------ actions (all go to the server)

    public static void refresh() {
        if (!ready() || busy) {
            return;
        }
        busy = true;
        version++;
        CloudService.background(() -> {
            try {
                fetchAll();
            } catch (CloudException e) {
                say(e.status() == 401 ? "Your RKB session ended. Sign in again: /rkb cloud login <email> <password>"
                        : "Could not load cosmetics: " + e.getMessage(), false);
            } catch (IOException e) {
                say("You are offline. Showing your last saved inventory.", false);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                busy = false;
                version++;
            }
        });
    }

    public static void claim(String cosmeticId) {
        act(body("claim", "cosmetic_id", cosmeticId), r -> "Claimed " + cosmeticId + ". It is now in your inventory.");
    }

    public static void redeem(String code) {
        act(body("redeem", "code", code), r -> "Code accepted. The cosmetic is now in your inventory.");
    }

    public static void purchaseStart(String cosmeticId) {
        act(body("purchase_start", "cosmetic_id", cosmeticId), r -> {
            String ref = r.has("reference") ? r.get("reference").getAsString() : "?";
            String info = r.has("purchase_info") ? r.get("purchase_info").getAsString() : "";
            return "Purchase started. Reference " + ref + ". " + (info.isEmpty() ? "Follow the shop's payment instructions and quote this reference." : info)
                    + " It is added to your inventory only after the payment is confirmed.";
        });
    }

    public static void equip(String cosmeticId) {
        act(body("equip", "cosmetic_id", cosmeticId), r -> "Equipped " + cosmeticId + ".");
    }

    public static void unequip(String slot) {
        act(body("unequip", "slot", slot), r -> "Unequipped your " + slot + ".");
    }

    private static JsonObject body(String action, String key, String value) {
        JsonObject b = new JsonObject();
        b.addProperty("action", action);
        b.addProperty(key, value);
        return b;
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
                JsonObject res = CloudService.callFunction("rkb-cosmetics", body);
                if (res.has("ok") && !res.get("ok").getAsBoolean()) {
                    say(errorText(res.has("error") ? res.get("error").getAsString() : "failed"), true);
                } else {
                    say(okMessage.apply(res), true);
                    fetchAll();
                }
            } catch (CloudException e) {
                say(e.status() == 429 ? errorText("rate_limited") : e.status() == 401
                        ? "Sign in first: /rkb cloud login <email> <password>" : "That did not work: " + e.getMessage(), true);
            } catch (IOException e) {
                say("You are offline. Try again when you are connected.", true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                busy = false;
                version++;
            }
        });
    }

    static String errorText(String code) {
        return switch (code) {
            case "not_found" -> "That cosmetic does not exist or is not available.";
            case "not_available" -> "That cosmetic is not available right now.";
            case "not_claimable" -> "That cosmetic is not free. Use Buy to see how to get it.";
            case "use_claim" -> "That cosmetic is free: use Claim.";
            case "already_owned" -> "You already own that cosmetic.";
            case "not_owned" -> "You do not own that cosmetic.";
            case "invalid_or_used_code" -> "That code is not valid, was already used or has expired.";
            case "rate_limited" -> "Slow down a little and try again.";
            default -> "Something went wrong (" + code + ").";
        };
    }

    private static void say(String text, boolean chat) {
        message = text;
        version++;
        if (chat) {
            CloudService.notifyPlayer(text);
        }
    }

    // ------------------------------------------------------------------ fetching (cloud thread)

    private static JsonObject call(String action) throws CloudException, IOException, InterruptedException {
        JsonObject b = new JsonObject();
        b.addProperty("action", action);
        return CloudService.callFunction("rkb-cosmetics", b);
    }

    private static void fetchAll() throws CloudException, IOException, InterruptedException {
        String user = CloudStore.userId();
        String rkbId = str(call("account"), "rkb_id");
        JsonObject cat = call("catalog");
        JsonObject inv = call("inventory");
        JsonObject his = call("history");
        if (!user.equals(CloudStore.userId())) {
            return;   // another account signed in meanwhile: never show these results for it
        }
        List<Item> items = new ArrayList<>();
        if (cat.has("items") && cat.get("items").isJsonArray()) {
            for (JsonElement e : cat.getAsJsonArray("items")) {
                if (e.isJsonObject()) {
                    items.add(parseItem(e.getAsJsonObject()));
                }
            }
        }
        Map<String, String> equipped = new HashMap<>();
        if (inv.has("equipped") && inv.get("equipped").isJsonArray()) {
            for (JsonElement e : inv.getAsJsonArray("equipped")) {
                if (e.isJsonObject()) {
                    equipped.put(str(e.getAsJsonObject(), "slot"), str(e.getAsJsonObject(), "cosmetic_id"));
                }
            }
        }
        List<Entry> history = new ArrayList<>();
        if (his.has("entries") && his.get("entries").isJsonArray()) {
            for (JsonElement e : his.getAsJsonArray("entries")) {
                if (e.isJsonObject()) {
                    JsonObject o = e.getAsJsonObject();
                    history.add(new Entry(str(o, "cosmetic_id"), str(o, "kind"), str(o, "status"), num(o, "amount_cents"),
                            str(o, "currency"), str(o, "reference"), str(o, "created_at")));
                }
            }
        }
        State st = new State(rkbId, List.copyOf(items), Map.copyOf(equipped), List.copyOf(history), System.currentTimeMillis());
        CACHE.put(user, st);
        saveDisk(user, st);
        RemoteCosmetics.Look own = ownLook(st);
        MinecraftClient.getInstance().execute(() -> RemoteCosmetics.setSelf(own));
        message = "";
    }

    static RemoteCosmetics.Look ownLook(State st) {
        Item cape = find(st, st.equipped().get("cape"));
        Item badge = find(st, st.equipped().get("badge"));
        String sha = cape != null && AssetRules.validSha(cape.assetSha()) && AssetRules.validPath(cape.assetPath()) ? cape.assetSha() : null;
        String key = badge != null && BadgeGlyphs.supported(badge.badgeKey()) ? badge.badgeKey() : null;
        if (sha == null && key == null) {
            return null;
        }
        return new RemoteCosmetics.Look(cape == null ? null : cape.id(), sha == null ? null : cape.assetPath(), sha, key);
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

    private static Item parseItem(JsonObject o) {
        return new Item(str(o, "id"), str(o, "name"), str(o, "description"), str(o, "type"), str(o, "rarity"),
                num(o, "price_cents"), str(o, "currency"), bool(o, "claimable"), str(o, "purchase_info"),
                str(o, "asset_path"), str(o, "asset_sha256"), str(o, "badge_key"), bool(o, "owned"));
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : "";
    }

    private static int num(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() && o.get(k).getAsJsonPrimitive().isNumber() ? o.get(k).getAsInt() : 0;
    }

    private static boolean bool(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() && o.get(k).getAsBoolean();
    }

    // ------------------------------------------------------------------ disk cache (display only, per account)

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
                o.addProperty("claimable", i.claimable());
                o.addProperty("purchase_info", i.purchaseInfo());
                o.addProperty("asset_path", i.assetPath());
                o.addProperty("asset_sha256", i.assetSha());
                o.addProperty("badge_key", i.badgeKey());
                o.addProperty("owned", i.owned());
                items.add(o);
            }
            root.add("items", items);
            JsonArray eq = new JsonArray();
            for (Map.Entry<String, String> e : st.equipped().entrySet()) {
                JsonObject o = new JsonObject();
                o.addProperty("slot", e.getKey());
                o.addProperty("cosmetic_id", e.getValue());
                eq.add(o);
            }
            root.add("equipped", eq);
            JsonArray his = new JsonArray();
            for (Entry h : st.history()) {
                JsonObject o = new JsonObject();
                o.addProperty("cosmetic_id", h.cosmeticId());
                o.addProperty("kind", h.kind());
                o.addProperty("status", h.status());
                o.addProperty("amount_cents", h.amountCents());
                o.addProperty("currency", h.currency());
                o.addProperty("reference", h.reference());
                o.addProperty("created_at", h.createdAt());
                his.add(o);
            }
            root.add("entries", his);
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
            List<Item> items = new ArrayList<>();
            for (JsonElement e : root.getAsJsonArray("items")) {
                items.add(parseItem(e.getAsJsonObject()));
            }
            Map<String, String> equipped = new HashMap<>();
            for (JsonElement e : root.getAsJsonArray("equipped")) {
                equipped.put(str(e.getAsJsonObject(), "slot"), str(e.getAsJsonObject(), "cosmetic_id"));
            }
            List<Entry> history = new ArrayList<>();
            for (JsonElement e : root.getAsJsonArray("entries")) {
                JsonObject o = e.getAsJsonObject();
                history.add(new Entry(str(o, "cosmetic_id"), str(o, "kind"), str(o, "status"), num(o, "amount_cents"),
                        str(o, "currency"), str(o, "reference"), str(o, "created_at")));
            }
            State st = new State(str(root, "rkbId"), List.copyOf(items), Map.copyOf(equipped), List.copyOf(history),
                    root.has("fetchedMs") ? root.get("fetchedMs").getAsLong() : 0);
            CACHE.put(accountId, st);
            return st;
        } catch (Exception e) {
            RkbClient.LOGGER.warn("Cosmetics cache is unreadable ({}).", e.toString());
            return null;
        }
    }
}
