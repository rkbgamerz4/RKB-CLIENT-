package com.rkb.client.cloud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rkb.client.RkbClient;
import com.rkb.client.cosmetics.CosmeticsManager;
import com.rkb.client.cosmetics.RemoteCosmetics;
import com.rkb.client.cosmetics.SkinSetStore;
import com.rkb.client.module.impl.NameTagBadgeModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import net.minecraft.text.Text;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Supabase Auth plus the deployed {@code rkb-cosmetics-v2} Edge Function.
 *
 * Rules this class keeps:
 *  - It never runs on the render thread and nothing here is called per frame.
 *  - No request is scheduled while signed out, without the publishable key, or after client shutdown.
 *  - The Minecraft access token is used only for the official session join on this device; it is never sent to
 *    Supabase and is never logged.
 *  - All work runs on one daemon thread that is shut down when the client closes.
 */
public final class CloudService {
    public enum Status { IDLE, SYNCING, CONNECTED, OFFLINE, ERROR }

    public static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private static ScheduledExecutorService exec;
    private static ScheduledFuture<?> syncTask;
    private static volatile Status status = Status.IDLE;
    private static volatile String lastError = "";
    private static volatile boolean authBusy;
    private static volatile boolean authOk;
    private static volatile String authMessage = "";
    private static volatile boolean stopping;
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
        RemoteCosmetics.clearRemote();
    }

    private static synchronized void cancelTasks() {
        if (syncTask != null) {
            syncTask.cancel(false);
            syncTask = null;
        }
    }

    // ------------------------------------------------------------------ state helpers

    /** Signed in with a publishable key: the only state in which V2 account requests are allowed. */
    public static boolean cloudAllowed() {
        return CosmeticsV2Api.canCall(CloudStore.signedIn(), !CloudStore.anonKey().isEmpty(), stopping);
    }

    /** The old badge module keeps its own opt-in for cloud-based name tags. */
    private static boolean nameTagCloudAllowed() {
        NameTagBadgeModule m = NameTagBadgeModule.get();
        return m != null && m.isEnabled() && m.cloudSyncOn();
    }

    public static boolean isLinked() {
        return !CloudStore.linkedUuid().isEmpty();
    }

    public static Status status() {
        return status;
    }

    /** Display-only account snapshot. Tokens never leave CloudStore. */
    public static AuthState authState() {
        return new AuthState(!CloudStore.anonKey().isEmpty(), CloudStore.signedIn(), isLinked(),
                authBusy, authOk, CloudStore.email(), authMessage);
    }

    public static boolean authBusy() {
        return authBusy;
    }

    public static String authMessage() {
        return authMessage;
    }

    private static void setAuth(boolean busy, boolean ok, String message) {
        authBusy = busy;
        authOk = ok;
        authMessage = message == null ? "" : message;
    }

    public static String statusText() {
        if (!cloudAllowed()) {
            return CloudStore.anonKey().isEmpty() ? "Missing publishable key" : "Signed out";
        }
        if (!isLinked()) {
            return "Connected · link Minecraft to verify";
        }
        return switch (status) {
            case SYNCING -> "Syncing";
            case CONNECTED -> "Connected · verified";
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

    /** Account sync is independent of the name-tag module; the module only controls cloud-based name tags. */
    public static void onBadgeFeatureChanged() {
        if (stopping) {
            return;
        }
        NameTagBadgeModule m = NameTagBadgeModule.get();
        boolean enabled = m != null && m.isEnabled();
        if (enabled != CloudStore.lastEnabled()) {
            CloudStore.setLastEnabled(enabled);
            CloudStore.setSettingsUpdated(System.currentTimeMillis());
        }
        if (!nameTagCloudAllowed()) {
            BadgeRegistry.clear();
        }
        startCloudTasks();
        RemoteCosmetics.onFeatureChanged();
    }

    /** Local-only settings: V2 has no settings action, so nothing is sent. */
    public static void onSettingsEdited() {
        if (stopping) {
            return;
        }
        CloudStore.setSettingsUpdated(System.currentTimeMillis());
        onBadgeFeatureChanged();
    }

    /** Friends stay on this device; V2 has no friends action. */
    public static void onFriendsChanged() {
        // Intentionally local: never send a friends list to the V2 cosmetics function.
    }

    /** Starts the account catalog/inventory sync when an authenticated session exists. */
    public static synchronized void startCloudTasks() {
        cancelTasks();
        if (!cloudAllowed()) {
            return;
        }
        syncTask = exec().scheduleWithFixedDelay(guard(() -> syncNow(false)), 2, 300, TimeUnit.SECONDS);
    }

    // ------------------------------------------------------------------ commands

    public static void login(String email, String password) {
        if (CloudStore.anonKey().isEmpty()) {
            setAuth(false, false, "Cloud is not configured: publishable key missing.");
            notifyPlayer("Add your Supabase publishable key as \"anonKey\" in config/rkbclient-cloud.json first.");
            return;
        }
        if (email == null || email.isBlank() || password == null || password.isEmpty()) {
            setAuth(false, false, "Enter your e-mail and password.");
            return;
        }
        if (authBusy) {
            return;
        }
        setAuth(true, false, "Signing in...");
        notifyPlayer("Signing in...");
        background(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("email", email.trim());
                body.addProperty("password", password);
                storeSession(http("POST", CloudStore.url() + "/auth/v1/token?grant_type=password", body,
                        CloudStore.anonKey()));
                String who = CloudStore.email().isBlank() ? "your RKB account" : CloudStore.email();
                setAuth(false, true, "Signed in as " + who + ".");
                notifyPlayer("Signed in as " + who + ".");
                startCloudTasks();
                syncNow(true);
                MinecraftClient.getInstance().execute(CloudService::onBadgeFeatureChanged);
            } catch (CloudException e) {
                String msg = e.status() == 400 || e.status() == 401
                        ? "Wrong e-mail or password." : "Sign in failed: " + e.getMessage();
                setAuth(false, false, msg);
                notifyPlayer("Sign in failed: " + msg);
            } catch (IOException e) {
                setAuth(false, false, "Could not reach the server. Local mode is still available.");
                notifyPlayer("Could not reach the server. You are offline; local settings still work.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                setAuth(false, false, "Sign in was interrupted.");
            }
        });
    }

    /** Creates a new RKB account (Supabase Auth sign-up). */
    public static void register(String email, String password) {
        if (CloudStore.anonKey().isEmpty()) {
            setAuth(false, false, "Cloud is not configured: publishable key missing.");
            notifyPlayer("Add your Supabase publishable key as \"anonKey\" in config/rkbclient-cloud.json first.");
            return;
        }
        if (email == null || email.isBlank() || password == null || password.length() < 6) {
            setAuth(false, false, "Use a valid e-mail and a password of at least 6 characters.");
            return;
        }
        if (authBusy) {
            return;
        }
        setAuth(true, false, "Creating your RKB account...");
        notifyPlayer("Creating your RKB account...");
        background(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("email", email.trim());
                body.addProperty("password", password);
                JsonObject res = http("POST", CloudStore.url() + "/auth/v1/signup", body, CloudStore.anonKey());
                if (res.has("access_token")) {
                    storeSession(res);
                    String who = CloudStore.email().isBlank() ? "your RKB account" : CloudStore.email();
                    setAuth(false, true, "Account created and signed in as " + who + ".");
                    notifyPlayer("Account created and signed in as " + who + ".");
                    startCloudTasks();
                    syncNow(true);
                    MinecraftClient.getInstance().execute(CloudService::onBadgeFeatureChanged);
                } else {
                    setAuth(false, true, "Account created. Check your e-mail, then sign in.");
                    notifyPlayer("Account created. Check your e-mail to confirm it, then sign in.");
                }
            } catch (CloudException e) {
                setAuth(false, false, "Sign up failed: " + e.getMessage());
                notifyPlayer("Sign up failed: " + e.getMessage());
            } catch (IOException e) {
                setAuth(false, false, "Could not reach the server. Try again when you are online.");
                notifyPlayer("Could not reach the server. Try again when you are online.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                setAuth(false, false, "Sign up was interrupted.");
            }
        });
    }

    public static void logout() {
        cancelTasks();
        CloudStore.clearSession();
        BadgeRegistry.clear();
        CosmeticsManager.onSignedOut();
        RemoteCosmetics.clearRemote();
        status = Status.IDLE;
        lastError = "";
        setAuth(false, false, "Signed out. Local skins and friends are kept.");
        notifyPlayer("Signed out. Local skins and friends are kept.");
    }

    /** Proves ownership with Mojang locally, then links only the name and resulting server id to the V2 backend. */
    public static void link() {
        if (!cloudAllowed()) {
            notifyPlayer("Sign in first: /rkb cloud login <email> <password>");
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        Session session = mc.getSession();
        UUID uuid = session == null ? null : session.getUuidOrNull();
        String name = session == null ? null : session.getUsername();
        String token = session == null ? "" : session.getAccessToken();
        if (uuid == null || name == null || !UuidUtil.isValidUsername(name)) {
            notifyPlayer("This game session has no verified Minecraft account (offline mode), so it cannot be linked.");
            return;
        }
        // Random server id for this one join. The access token never leaves this device.
        String serverId = UUID.randomUUID().toString().replace("-", "");
        notifyPlayer("Verifying your Minecraft account...");
        background(() -> {
            try {
                try {
                    mc.getApiServices().sessionService().joinServer(uuid, token, serverId);
                } catch (Exception e) {
                    notifyPlayer("Minecraft could not verify this account. Offline or guest accounts cannot be linked.");
                    return;
                }
                JsonObject res = callV2(CosmeticsV2Api.linkRequest(name, serverId));
                requireOk(res);
                String linked = CosmeticsV2Api.linkedUuid(res);
                if (linked.isEmpty()) {
                    throw new CloudException(502, "invalid_link_response");
                }
                CloudStore.setLinked(linked, name);
                notifyPlayer("Linked " + name + " as " + UuidUtil.shortId(UuidUtil.parse(linked)) + ".");
                startCloudTasks();
                syncNow(true);
                SkinSetStore.onIdentityLinked();
                MinecraftClient.getInstance().execute(RemoteCosmetics::onFeatureChanged);
            } catch (CloudException e) {
                notifyPlayer(switch (e.getMessage()) {
                    case "ownership_not_verified", "session_not_verified" ->
                            "Mojang did not confirm this account. Try again.";
                    case "uuid_linked_to_other_account", "identity_taken" ->
                            "This Minecraft account is already linked to another RKB account.";
                    case "invalid_link_response" -> "The backend did not return a verified Minecraft UUID.";
                    case "rate_limited" -> "Too many link attempts. Wait a minute and try again.";
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
            notifyPlayer("Sign in and add your publishable key first.");
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

    // ------------------------------------------------------------------ V2 sync work (cloud thread)

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
                setAuth(false, false, "Your RKB session ended. Sign in again.");
            } else if (e.status() == 403 && (lastError.equals("not_linked")
                    || lastError.equals("identity_not_verified") || lastError.equals("link_required"))) {
                CloudStore.setLinked("", null);
                BadgeRegistry.clear();
                status = Status.IDLE;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Fetches catalog + inventory through the V2 function. No other V2 actions are called here. */
    public static void syncNow(boolean manual) {
        runSync(() -> {
            String account = CloudStore.userId();
            if (account.isEmpty()) {
                throw new CloudException(401, "signed_out");
            }
            JsonObject catalog = callV2(CosmeticsV2Api.request(CosmeticsV2Api.CATALOG));
            requireOk(catalog);
            JsonObject inventory = callV2(CosmeticsV2Api.request(CosmeticsV2Api.INVENTORY));
            requireOk(inventory);
            if (!account.equals(CloudStore.userId())) {
                return;   // another account signed in while the requests were in flight
            }
            CosmeticsManager.applyV2(account, CosmeticsV2Api.parseCatalog(catalog),
                    CosmeticsV2Api.parseInventory(inventory), CloudStore.linkedUuid());
            CloudStore.setLastSync(System.currentTimeMillis());
        });
    }

    /** Throws for an explicit backend error. A missing {@code ok} field is accepted. */
    private static void requireOk(JsonObject res) throws CloudException {
        if (CosmeticsV2Api.ok(res)) {
            return;
        }
        String error = CosmeticsV2Api.error(res);
        int status = error.equals("rate_limited") || error.equals("too_many_requests") ? 429 : 403;
        throw new CloudException(status, error.isEmpty() ? "request_failed" : error);
    }

    // ------------------------------------------------------------------ HTTP

    private static void storeSession(JsonObject auth) {
        String previousUser = CloudStore.userId();
        String access = text(auth, "access_token");
        String refresh = text(auth, "refresh_token");
        long expiresIn = auth.has("expires_in") ? auth.get("expires_in").getAsLong() : 3600;
        String userId = object(auth, "user") == null ? "" : text(auth.getAsJsonObject("user"), "id");
        String email = object(auth, "user") == null ? "" : text(auth.getAsJsonObject("user"), "email");
        CloudStore.setSession(access, refresh, System.currentTimeMillis() + expiresIn * 1000L, userId, email);
        if (!userId.isEmpty() && !userId.equals(previousUser)) {
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
        storeSession(http("POST", CloudStore.url() + "/auth/v1/token?grant_type=refresh_token", body,
                CloudStore.anonKey()));
    }

    /** Sends a fully formed V2 action body (action already set by the caller). */
    public static JsonObject callV2(JsonObject body) throws CloudException, IOException, InterruptedException {
        if (!cloudAllowed()) {
            throw new CloudException(401, "signed_out");
        }
        if (body == null || text(body, "action").isEmpty()) {
            throw new CloudException(400, "missing_action");
        }
        return authedCall(CosmeticsV2Api.FUNCTION, body);
    }

    private static JsonObject authedCall(String function, JsonObject body)
            throws CloudException, IOException, InterruptedException {
        if (CloudStore.accessToken().isEmpty()
                || CloudStore.expiresAtMs() - 60_000 < System.currentTimeMillis()) {
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
            String error = CosmeticsV2Api.error(out);
            throw new CloudException(res.statusCode(), error.isEmpty() ? "request_failed" : error);
        }
        return out;
    }

    private static JsonObject parseObject(String text) {
        if (text == null || text.isBlank()) {
            return new JsonObject();
        }
        try {
            return JsonParser.parseString(text).getAsJsonObject();
        } catch (RuntimeException e) {
            return new JsonObject();
        }
    }

    private static JsonObject object(JsonObject o, String key) {
        if (o != null && o.has(key) && o.get(key).isJsonObject()) {
            return o.getAsJsonObject(key);
        }
        return null;
    }

    private static String text(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull() || !o.get(key).isJsonPrimitive()) {
            return "";
        }
        return o.get(key).getAsString().trim();
    }
}
