package com.rkb.client.cloud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/** Resolves a Minecraft username to a verified UUID through Mojang's public API. Call from a background thread. */
public final class MojangLookup {
    public record Result(UUID uuid, String name) {
    }

    private MojangLookup() {
    }

    /** Returns null when no such player exists. Throws IOException when Mojang cannot be reached. */
    public static Result byName(String name) throws IOException, InterruptedException {
        if (!UuidUtil.isValidUsername(name)) {
            return null;
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.mojang.com/users/profiles/minecraft/" + name.trim()))
                .timeout(Duration.ofSeconds(8)).GET().build();
        HttpResponse<String> res = CloudService.HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 404 || res.statusCode() == 204) {
            return null;
        }
        if (res.statusCode() != 200) {
            throw new IOException("Mojang answered " + res.statusCode());
        }
        JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
        UUID id = UuidUtil.parse(o.get("id").getAsString());
        if (id == null) {
            throw new IOException("Mojang returned an invalid id");
        }
        return new Result(id, o.has("name") ? o.get("name").getAsString() : name.trim());
    }
}
