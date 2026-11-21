package com.rkb.client.cloud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.rkb.client.RkbClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * Local cloud state in config/rkbclient-cloud.json: Supabase URL, the publishable key you paste in, the login session
 * (tokens are stored in plain text, like most launchers; keep the file private) and sync bookkeeping.
 * Never put the service_role key here.
 */
public final class CloudStore {
    public static final String DEFAULT_URL = "https://uoghboucoajkpbgyfxsf.supabase.co";
    public static final String DEFAULT_PUBLISHABLE_KEY = "sb_publishable_sH2LXAAtH1r8ciqiPTfaSQ_ATYLpxkB";
    private static final String LEGACY_URL = "https://gnficbdfqukyubdeqgjt.supabase.co";

    static class Data {
        String supabaseUrl = DEFAULT_URL;
        String anonKey = DEFAULT_PUBLISHABLE_KEY;
        String accessToken = "";
        String refreshToken = "";
        long expiresAtMs;
        String userId = "";
        String email = "";
        String linkedUuid = "";
        String linkedName = "";
        long settingsUpdatedMs;
        boolean lastEnabled;
        long lastSyncMs;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data d = new Data();

    private CloudStore() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("rkbclient-cloud.json");
    }

    public static synchronized void load() {
        Path f = file();
        if (!Files.isRegularFile(f)) {
            save();
            return;
        }
        try {
            Data loaded = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Data.class);
            if (loaded != null) {
                d = loaded;
                boolean legacy = d.supabaseUrl != null && d.supabaseUrl.equals(LEGACY_URL);
                if (d.supabaseUrl == null || !d.supabaseUrl.startsWith("https://") || legacy) {
                    d.supabaseUrl = DEFAULT_URL;
                }
                d.anonKey = nz(d.anonKey);
                if (legacy || d.anonKey.isEmpty()) {
                    d.anonKey = DEFAULT_PUBLISHABLE_KEY;
                }
                d.accessToken = nz(d.accessToken);
                d.refreshToken = nz(d.refreshToken);
                d.userId = nz(d.userId);
                d.email = nz(d.email);
                d.linkedUuid = nz(d.linkedUuid);
                d.linkedName = nz(d.linkedName);
                save();
            }
        } catch (Exception e) {
            RkbClient.LOGGER.warn("Cloud config is unreadable ({}); using defaults.", e.toString());
            d = new Data();
        }
    }

    public static synchronized void save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling("rkbclient-cloud.json.tmp");
            Files.writeString(tmp, GSON.toJson(d), StandardCharsets.UTF_8);
            try {
                Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException | IOException ignored) {
                // not a POSIX file system (Windows); nothing to do
            }
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            RkbClient.LOGGER.error("Could not save cloud config: {}", e.toString());
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    public static synchronized String url() {
        return d.supabaseUrl.endsWith("/") ? d.supabaseUrl.substring(0, d.supabaseUrl.length() - 1) : d.supabaseUrl;
    }

    public static synchronized String anonKey() {
        return d.anonKey;
    }

    public static synchronized String accessToken() {
        return d.accessToken;
    }

    public static synchronized String refreshToken() {
        return d.refreshToken;
    }

    public static synchronized long expiresAtMs() {
        return d.expiresAtMs;
    }

    public static synchronized String userId() {
        return d.userId;
    }

    public static synchronized String email() {
        return d.email;
    }

    public static synchronized String linkedUuid() {
        return d.linkedUuid;
    }

    public static synchronized String linkedName() {
        return d.linkedName;
    }

    public static synchronized long settingsUpdatedMs() {
        return d.settingsUpdatedMs;
    }

    public static synchronized boolean lastEnabled() {
        return d.lastEnabled;
    }

    public static synchronized long lastSyncMs() {
        return d.lastSyncMs;
    }

    public static synchronized boolean signedIn() {
        return !d.accessToken.isEmpty() || !d.refreshToken.isEmpty();
    }

    public static synchronized void setSession(String access, String refresh, long expiresAtMs, String userId, String email) {
        d.accessToken = nz(access);
        d.refreshToken = nz(refresh);
        d.expiresAtMs = expiresAtMs;
        if (userId != null && !userId.isEmpty()) {
            d.userId = userId;
        }
        if (email != null && !email.isEmpty()) {
            d.email = email;
        }
        save();
    }

    public static synchronized void clearSession() {
        d.accessToken = "";
        d.refreshToken = "";
        d.expiresAtMs = 0;
        d.userId = "";
        d.email = "";
        d.linkedUuid = "";
        d.linkedName = "";
        save();
    }

    public static synchronized void setLinked(String uuid, String name) {
        d.linkedUuid = nz(uuid);
        if (name != null && !name.isEmpty()) {
            d.linkedName = name;
        }
        save();
    }

    public static synchronized void setSettingsUpdated(long ms) {
        d.settingsUpdatedMs = ms;
        save();
    }

    public static synchronized void setLastEnabled(boolean v) {
        d.lastEnabled = v;
        save();
    }

    public static synchronized void setLastSync(long ms) {
        d.lastSyncMs = ms;
        save();
    }
}
