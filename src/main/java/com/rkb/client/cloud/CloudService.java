package com.rkb.client.cloud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rkb.client.RkbClient;
import com.rkb.client.cosmetics.CosmeticsManager;
import com.rkb.client.cosmetics.RemoteCosmetics;
import com.rkb.client.module.impl.NameTagBadgeModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Background cloud sync for the name tag badge (Supabase Auth + the rkb-badges Edge Function).
 *
 * Rules this class keeps:
 *  - It never runs on the render thread and nothing here is called per frame.
 *  - With the badge off, or Cloud Sync off, or signed out, it schedules nothing and sends nothing
 *    (one final settings push is allowed right after you switch the badge off, so the cloud stops listing you).
 *  - All work runs on one daemon thread that is shut down when the client closes.
 *  - Tokens, passwords and the Minecraft access token are never logged.
 */
public final class CloudService {
    public enum Status { IDLE, SYNCING, CONNECTED, OFFLINE, ERROR }

    public static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private static ScheduledExecutorService exec;
    private static ScheduledFuture<?> syncTask;
    private static ScheduledFuture<?> registryTask;
    private static final AtomicBoolean SETTINGS_QUEUED = new AtomicBoolean();
    private static final AtomicBoolean FRIENDS_QUEUED = new AtomicBoolean();
    private static volatile Status status = Status.IDLE;
    private static volatile String lastError = "";
    private static volatile boolean stopping;
    private static volatile boolean applyingRemote;
    private static final RateLimiter MANUAL_SYNC = new RateLimiter(5_000);

    private CloudService() {
    }

    // ------------------------------------------------------------------ threading

    private static synchronized ScheduledExecutorService exec() {
        if (exec == null || exec.isShutdown()) {
            exec = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "rkb-cloud");
                t.setDaemon(true);
                return t;
            });
        }
        return exec;
    }

    private static Runnable guard(Runnable r) {
        return () -> {
            try {
                r.run();
            } catch (Throwable t) {
                RkbClient.LOGGER.warn("Cloud task failed: {}", t.toString());
            }
        };
    }

    /** Shared scheduler for background tasks of other RKB features; stopped by shutdown(). */
    public static ScheduledExecutorService scheduler() {
        return exec();
    }

    public static Runnable guarded(Runnable r) {
        return guard(r);
    }

    public static boolean isStopping() {
        return stopping;
    }

    public static void background(Runnable r) {
        if (!stopping) {
            exec().execute(guard(r));
        }
    }

    /** Called when the client closes: cancels every task and stops the thread. */
    public static synchronized void shutdown() {
        stopping = true;
        cancelTasks();
        if (exec != null) {
            exec.shutdownNow();
            exec = null;
        }
        BadgeRegistry.clear();
    }

    private static synchronized void cancelTasks() {
        if (syncTask != null) {
            syncTask.cancel(false);
            syncTask = null;
        }
        if (registryTask != null) {
            registryTask.cancel(false);
            registryTask = null;
        }
    }

    // ------------------------------------------------------------------ state helpers

    public static boolean applyingRemote() {
        return applyingRemote;
    }

    private static boolean badgeOn() {
        NameTagBadgeModule m = NameTagBadgeModule.get();
        return m != null && m.isEnabled();
    }

    /** Cloud Sync switched on, key present and signed in. (The badge itself may be on or off.) */
    public static boolean cloudAllowed() {
        NameTagBadgeModule m = NameTagBadgeModule.get();
        return m != null && m.cloudSyncOn() && !CloudStore.anonKey().isEmpty() && CloudStore.signedIn() && !stopping;
    }

    private static boolean featureActive() {
        return badgeOn() && cloudAllowed();
    }

    public static boolean isLinked() {
        return !CloudStore.linkedUuid().isEmpty();
    }

    public static String statusText() {
        NameTagBadgeModule m = NameTagBadgeModule.get();
        if (m == null || !m.isEnabled()) {
            return "Off";
        }
        if (!m.cloudSyncOn()) {
            return "Local only";
        }
        if (CloudStore.anonKey().isEmpty()) {
            return "Missing API key";
        }
        if (!CloudStore.signedIn()) {
            return "Signed out";
        }
        if (!isLinked()) {
            return "Account not linked";
        }
        return switch (status) {
            case SYNCING -> "Syncing";
            case CONNECTED -> "Connected";
            case OFFLINE -> "Offline (using local data)";
            case ERROR -> "Error: " + lastError;
            default -> "Ready";
        };
    }

    public static void notifyPlayer(String message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.sendMessage(Text.literal("[RKB] " + message), false);
            } else {
                RkbClient.LOGGER.info("[RKB] {}", message);
            }
        });
    }

    // ------------------------------------------------------------------ feature switches (game thread)

    /** Call whenever the badge is switched on/off or its cloud options change. */
    public static synchronized void onBadgeFeatureChanged() {
        if (stopping) {
            return;
        }
        NameTagBadgeModule m = NameTagBadgeModule.get();
        boolean enabled = m != null && m.isEnabled();
        if (m != null && enabled != CloudStore.lastEnabled()) {
            CloudStore.setLastEnabled(enabled);
            CloudStore.setSettingsUpdated(System.currentTimeMillis());
            queueSettingsPush();
        }
        cancelTasks();
        if (!enabled) {
            BadgeRegistry.clear();
            status = Status.IDLE;
            return;
        }
        FriendsManager.resolveLegacyIfNeeded();
        if (!cloudAllowed()) {
            BadgeRegistry.clear();
            return;
        }
        syncTask = exec().scheduleWithFixedDelay(guard(() -> syncNow(false)), 2, 600, TimeUnit.SECONDS);
        if (m.everyoneMode()) {
            registryTask = exec().scheduleWithFixedDelay(guard(CloudService::registryTick), 5, 45, TimeUnit.SECONDS);
        } else {
            BadgeRegistry.clear();
        }
    }

    public static void onSettingsEdited() {
        if (applyingRemote || stopping) {
            return;
        }
        CloudStore.setSettingsUpdated(System.currentTimeMillis());
        queueSettingsPush();
        onBadgeFeatureChanged();
    }

    public static void onFriendsChanged() {
        if (!stopping && cloudAllowed()) {
            queueFriendsPush();
        }
    }

    private static void queueSettingsPush() {
        if (cloudAllowed() && SETTINGS_QUEUED.compareAndSet(false, true)) {
            exec().schedule(guard(() -> {
                SETTINGS_QUEUED.set(false);
                runSync(CloudService::pushSettings);
            }), 3, TimeUnit.SECONDS);
        }
    }

    private static void queueFriendsPush() {
        if (cloudAllowed() && FRIENDS_QUEUED.compareAndSet(false, true)) {
            exec().schedule(guard(() -> {
                FRIENDS_QUEUED.set(false);
                runSync(CloudService::pushFriends);
            }), 3, TimeUnit.SECONDS);
        }
    }

    // ------------------------------------------------------------------ commands

    public static void login(String email, String password) {
        if (CloudStore.anonKey().isEmpty()) {
            notifyPlayer("Add your Supabase publishable key as \"anonKey\" in config/rkbclient-cloud.json first.");
            return;
        }
        notifyPlayer("Signing in...");
        background(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("email", email);
                body.addProperty("password", password);
                storeSession(http("POST", CloudStore.url() + "/auth/v1/token?grant_type=password", body, CloudStore.anonKey()));
                notifyPlayer("Signed in as " + CloudStore.email() + ".");
                syncNow(true);
                MinecraftClient.getInstance().execute(() -> {
                    onBadgeFeatureChanged();
                    RemoteCosmetics.onFeatureChanged();
                });
            } catch (CloudException e) {
                notifyPlayer(e.status() == 400 || e.status() == 401 ? "Sign in failed: wrong email or password." : "Sign in failed: " + e.getMessage());
            } catch (IOException e) {
                notifyPlayer("Could not reach the server. You are offline; local settings still work.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /** Creates a new RKB account (Supabase Auth sign-up). Whether e-mail confirmation is needed depends on your project. */
    public static void register(String email, String password) {
        if (CloudStore.anonKey().isEmpty()) {
            notifyPlayer("Add your Supabase publishable key as \"anonKey\" in config/rkbclient-cloud.json first.");
            return;
        }
        notifyPlayer("Creating your RKB account...");
        background(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("email", email);
                body.addProperty("password", password);
                JsonObject res = http("POST", CloudStore.url() + "/auth/v1/signup", body, CloudStore.anonKey());
                if (res.has("access_token")) {
                    storeSession(res);
                    notifyPlayer("Account created and signed in as " + CloudStore.email() + ".");
                    MinecraftClient.getInstance().execute(() -> {
                        onBadgeFeatureChanged();
                        RemoteCosmetics.onFeatureChanged();
                    });
                } else {
                    notifyPlayer("Account created. Check your e-mail to confirm it, then run /rkb cloud login.");
                }
            } catch (CloudException e) {
                notifyPlayer("Sign up failed: " + e.getMessage());
            } catch (IOException e) {
                notifyPlayer("Could not reach the server. Try again when you are online.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public static void logout() {
        cancelTasks();
        CloudStore.clearSession();
        BadgeRegistry.clear();
        CosmeticsManager.onSignedOut();
        RemoteCosmetics.onFeatureChanged();
        status = Status.IDLE;
        notifyPlayer("Signed out. Local settings and friends are kept.");
    }

    /** Proves you own this Minecraft account (Mojang join handshake) and links it to your RKB account. */
    public static void link() {
        if (!CloudStore.signedIn()) {
            notifyPlayer("Sign in first: /rkb cloud login <email> <password>");
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        Session session = mc.getSession();
        UUID uuid = session.getUuidOrNull();
        String name = session.getUsername();
        String token = session.getAccessToken();
        var sessionService = mc.getSessionService();
        if (uuid == null || name == null) {
            notifyPlayer("This game session has no Minecraft account (offline mode), so it cannot be linked.");
            return;
        }
        notifyPlayer("Verifying your Minecraft account...");
        background(() -> {
            try {
                JsonObject c = new JsonObject();
                c.addProperty("action", "link_challenge");
                String serverId = authedCall(c).get("server_id").getAsString();
                try {
                    sessionService.joinServer(uuid, token, serverId);
                } catch (Exception e) {
                    notifyPlayer("Minecraft could not verify this account. Offline or guest accounts cannot be linked.");
                    return;
                }
                JsonObject v = new JsonObject();
                v.addProperty("action", "link_verify");
                v.addProperty("username", name);
                v.addProperty("server_id", serverId);
                JsonObject res = authedCall(v);
                CloudStore.setLinked(res.get("linked_uuid").getAsString(), name);
                notifyPlayer("Linked " + name + " to " + CloudStore.email() + ".");
                syncNow(true);
            } catch (CloudException e) {
                notifyPlayer(switch (e.getMessage()) {
                    case "ownership_not_verified" -> "Mojang did not confirm this account. Try again.";
                    case "uuid_linked_to_other_account" -> "This Minecraft account is already linked to another RKB account.";
                    default -> "Linking failed: " + e.getMessage();
                });
            } catch (IOException e) {
                notifyPlayer("Could not reach the server. Try again when you are online.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public static void manualSync() {
        if (!cloudAllowed()) {
            notifyPlayer("Turn on Cloud Sync, add your key and sign in first.");
            return;
        }
        if (!MANUAL_SYNC.tryAcquire(System.currentTimeMillis())) {
            notifyPlayer("Please wait a few seconds between syncs.");
            return;
        }
        background(() -> {
            syncNow(true);
            notifyPlayer("Sync: " + statusText());
        });
    }

    // ------------------------------------------------------------------ sync work (cloud thread)

    private interface SyncAction {
        void run() throws CloudException, IOException, InterruptedException;
    }

    private static void runSync(SyncAction action) {
        if (!cloudAllowed()) {
            return;
        }
        status = Status.SYNCING;
        try {
            action.run();
            status = Status.CONNECTED;
            lastError = "";
        } catch (HttpTimeoutException e) {
            status = Status.OFFLINE;
        } catch (IOException e) {
            status = Status.OFFLINE;
        } catch (CloudException e) {
            status = Status.ERROR;
            lastError = e.getMessage();
            if (e.status() == 401) {
                CloudStore.clearSession();
                lastError = "signed_out";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static void syncNow(boolean manual) {
        runSync(() -> {
            JsonObject res;
            try {
                JsonObject g = new JsonObject();
                g.addProperty("action", "get");
                res = authedCall(g);
            } catch (CloudException e) {
                if (e.status() == 403 && "not_linked".equals(e.getMessage())) {
                    CloudStore.setLinked("", null);
                    if (manual) {
                        notifyPlayer("Your RKB account is not linked to a Minecraft account yet. Run /rkb cloud link.");
                    }
                    status = Status.IDLE;
                    return;
                }
                throw e;
            }
            CloudStore.setLinked(str(res, "player_uuid"), null);
            JsonObject s = res.has("settings") && res.get("settings").isJsonObject() ? res.getAsJsonObject("settings") : null;

            long serverSettingsMs = s == null ? 0 : parseIso(str(s, "updated_at"));
            switch (SyncMerge.decide(CloudStore.settingsUpdatedMs(), serverSettingsMs)) {
                case PULL_FROM_SERVER -> applyServerSettings(s, serverSettingsMs);
                case PUSH_TO_SERVER -> pushSettings();
                default -> { }
            }

            long serverFriendsMs = s == null ? 0 : parseIso(str(s, "friends_updated_at"));
            switch (SyncMerge.decide(FriendsManager.updatedMs(), serverFriendsMs)) {
                case PULL_FROM_SERVER -> {
                    List<UUID> ids = new ArrayList<>();
                    if (res.has("friends") && res.get("friends").isJsonArray()) {
                        for (JsonElement e : res.getAsJsonArray("friends")) {
                            UUID id = UuidUtil.parse(e.getAsString());
                            if (id != null) {
                                ids.add(id);
                            }
                        }
                    }
                    MinecraftClient.getInstance().execute(() -> FriendsManager.replaceFromServer(ids, serverFriendsMs));
                }
                case PUSH_TO_SERVER -> pushFriends();
                default -> { }
            }
            CloudStore.setLastSync(System.currentTimeMillis());
        });
    }

    private static void applyServerSettings(JsonObject s, long serverMs) {
        NameTagBadgeModule m = NameTagBadgeModule.get();
        if (m == null || s == null) {
            return;
        }
        String style = str(s, "badge_style");
        String mode = str(s, "visibility_mode");
        MinecraftClient.getInstance().execute(() -> {
            applyingRemote = true;
            try {
                m.applyFromCloud(style, mode);
            } finally {
                applyingRemote = false;
            }
            onBadgeFeatureChanged();
        });
        CloudStore.setSettingsUpdated(serverMs);
    }

    private static void pushSettings() throws CloudException, IOException, InterruptedException {
        NameTagBadgeModule m = NameTagBadgeModule.get();
        if (m == null || !cloudAllowed() || !isLinked()) {
            return;
        }
        JsonObject b = new JsonObject();
        b.addProperty("action", "put_settings");
        b.addProperty("badge_enabled", m.isEnabled());
        b.addProperty("badge_style", m.styleKey());
        b.addProperty("visibility_mode", m.visibilityKey());
        b.addProperty("client_updated_at", Instant.ofEpochMilli(Math.max(1, CloudStore.settingsUpdatedMs())).toString());
        JsonObject res = authedCall(b);
        if (res.has("applied") && !res.get("applied").getAsBoolean() && res.has("settings") && res.get("settings").isJsonObject()) {
            JsonObject s = res.getAsJsonObject("settings");
            applyServerSettings(s, parseIso(str(s, "updated_at")));
        }
    }

    private static void pushFriends() throws CloudException, IOException, InterruptedException {
        if (!cloudAllowed() || !isLinked() || FriendsManager.updatedMs() == 0) {
            return;
        }
        JsonArray arr = new JsonArray();
        for (FriendsManager.Friend f : FriendsManager.list()) {
            arr.add(UuidUtil.canonical(f.uuid()));
        }
        JsonObject b = new JsonObject();
        b.addProperty("action", "put_friends");
        b.add("friends", arr);
        b.addProperty("client_updated_at", Instant.ofEpochMilli(FriendsManager.updatedMs()).toString());
        authedCall(b);
    }

    // ------------------------------------------------------------------ registry (who shows a badge)

    private static void registryTick() {
        NameTagBadgeModule m = NameTagBadgeModule.get();
        if (!featureActive() || m == null || !m.everyoneMode() || !isLinked()) {
            BadgeRegistry.clear();
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        CompletableFuture<List<UUID>> visible = new CompletableFuture<>();
        mc.execute(() -> {
            List<UUID> ids = new ArrayList<>();
            if (mc.world != null && mc.player != null) {
                for (PlayerEntity p : mc.world.getPlayers()) {
                    if (p != mc.player) {
                        ids.add(p.getUuid());
                    }
                }
            }
            visible.complete(ids);
        });
        List<UUID> ids;
        try {
            ids = visible.get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            return;
        }
        long now = System.currentTimeMillis();
        List<UUID> toCheck = BadgeRegistry.needCheck(ids, now);
        if (toCheck.isEmpty()) {
            return;
        }
        if (toCheck.size() > 200) {
            toCheck = toCheck.subList(0, 200);
        }
        final List<UUID> batch = toCheck;
        runSync(() -> {
            JsonArray arr = new JsonArray();
            for (UUID id : batch) {
                arr.add(UuidUtil.canonical(id));
            }
            JsonObject b = new JsonObject();
            b.addProperty("action", "lookup");
            b.add("uuids", arr);
            JsonObject res = authedCall(b);
            Set<UUID> positives = new HashSet<>();
            if (res.has("badges") && res.get("badges").isJsonArray()) {
                for (JsonElement e : res.getAsJsonArray("badges")) {
                    UUID id = UuidUtil.parse(str(e.getAsJsonObject(), "uuid"));
                    if (id != null) {
                        positives.add(id);
                    }
                }
            }
            BadgeRegistry.apply(batch, positives, System.currentTimeMillis());
        });
    }

    // ------------------------------------------------------------------ HTTP

    private static void storeSession(JsonObject auth) {
        String previousUser = CloudStore.userId();
        String access = str(auth, "access_token");
        String refresh = str(auth, "refresh_token");
        long expiresIn = auth.has("expires_in") ? auth.get("expires_in").getAsLong() : 3600;
        String userId = auth.has("user") && auth.get("user").isJsonObject() ? str(auth.getAsJsonObject("user"), "id") : "";
        String email = auth.has("user") && auth.get("user").isJsonObject() ? str(auth.getAsJsonObject("user"), "email") : "";
        CloudStore.setSession(access, refresh, System.currentTimeMillis() + expiresIn * 1000L, userId, email);
        if (!userId.isEmpty() && !userId.equals(previousUser)) {
            // a different RKB account is now signed in: drop everything cached for the old one
            MinecraftClient.getInstance().execute(CosmeticsManager::onAccountChanged);
        }
    }

    private static void refreshSession() throws CloudException, IOException, InterruptedException {
        String refresh = CloudStore.refreshToken();
        if (refresh.isEmpty()) {
            throw new CloudException(401, "signed_out");
        }
        JsonObject body = new JsonObject();
        body.addProperty("refresh_token", refresh);
        storeSession(http("POST", CloudStore.url() + "/auth/v1/token?grant_type=refresh_token", body, CloudStore.anonKey()));
    }

    /** Calls a Supabase Edge Function as the signed-in RKB account (Auth token, refreshed once if needed). */
    public static JsonObject callFunction(String function, JsonObject body) throws CloudException, IOException, InterruptedException {
        if (CloudStore.anonKey().isEmpty() || !CloudStore.signedIn()) {
            throw new CloudException(401, "signed_out");
        }
        return authedCall(function, body);
    }

    private static JsonObject authedCall(JsonObject body) throws CloudException, IOException, InterruptedException {
        return authedCall("rkb-badges", body);
    }

    private static JsonObject authedCall(String function, JsonObject body) throws CloudException, IOException, InterruptedException {
        if (CloudStore.accessToken().isEmpty() || CloudStore.expiresAtMs() - 60_000 < System.currentTimeMillis()) {
            refreshSession();
        }
        String url = CloudStore.url() + "/functions/v1/" + function;
        try {
            return http("POST", url, body, CloudStore.accessToken());
        } catch (CloudException e) {
            if (e.status() != 401) {
                throw e;
            }
            refreshSession();
            return http("POST", url, body, CloudStore.accessToken());
        }
    }

    /** Used by the cosmetics catalog too. */
    static JsonObject http(String method, String url, JsonObject body, String bearer)
            throws CloudException, IOException, InterruptedException {
        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("apikey", CloudStore.anonKey())
                .header("Authorization", "Bearer " + bearer)
                .header("Content-Type", "application/json");
        if (body != null) {
            rb.method(method, HttpRequest.BodyPublishers.ofString(body.toString()));
        } else {
            rb.method(method, HttpRequest.BodyPublishers.noBody());
        }
        HttpResponse<String> res = HTTP.send(rb.build(), HttpResponse.BodyHandlers.ofString());
        JsonObject out = parseObject(res.body());
        if (res.statusCode() / 100 != 2) {
            throw new CloudException(res.statusCode(), errorCode(out));
        }
        return out;
    }

    private static JsonObject parseObject(String text) {
        if (text == null || text.isBlank()) {
            return new JsonObject();
        }
        try {
            JsonElement e = JsonParser.parseString(text);
            if (e.isJsonObject()) {
                return e.getAsJsonObject();
            }
            JsonObject wrap = new JsonObject();
            wrap.add("_array", e);
            return wrap;
        } catch (RuntimeException ex) {
            return new JsonObject();
        }
    }

    private static String errorCode(JsonObject o) {
        for (String k : new String[]{"error", "error_description", "msg", "message"}) {
            if (o.has(k) && o.get(k).isJsonPrimitive()) {
                String s = o.get(k).getAsString();
                return s.length() > 80 ? s.substring(0, 80) : s;
            }
        }
        return "request_failed";
    }

    private static String str(JsonObject o, String key) {
        return o != null && o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }

    private static long parseIso(String iso) {
        if (iso == null || iso.isEmpty()) {
            return 0;
        }
        try {
            return Instant.parse(iso).toEpochMilli();
        } catch (RuntimeException e) {
            try {
                return java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli();
            } catch (RuntimeException e2) {
                return 0;
            }
        }
    }
}
