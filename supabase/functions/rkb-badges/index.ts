// RKB CLIENT 0.8.0 - rkb-badges Edge Function (Deno).
//
// One endpoint, POST JSON { action: ..., ... }, always with "Authorization: Bearer <Supabase Auth access token>".
// Actions: link_challenge, link_verify, get, put_settings, put_friends, lookup.
//
// Security model
//  - The caller is identified ONLY by the verified Supabase Auth JWT (auth.getUser). A UUID sent by the client is
//    never trusted: the Minecraft UUID used for data access is the one stored in minecraft_account_links, and a link
//    is only created after Mojang's session server confirms that the player really joined with that account
//    (the client proves ownership with the standard "join server" handshake; its Minecraft token never leaves the PC).
//  - The service role key stays here (environment variable). Clients only hold the publishable/anon key.
//  - All input is validated; error responses never include internals.
//
// Deploy: see supabase/README.md. Needs the secret RKB_LINK_SECRET (random, 32+ bytes).

import { createClient } from "npm:@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const LINK_SECRET = Deno.env.get("RKB_LINK_SECRET") ?? "";

const admin = createClient(SUPABASE_URL, SERVICE_KEY, { auth: { persistSession: false } });

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const NAME_RE = /^[A-Za-z0-9_]{3,16}$/;
const STYLES = new Set(["logo", "letter"]);
const MODES = new Set(["friends", "everyone"]);
const MAX_FRIENDS = 500;
const MAX_LOOKUP = 200;
const CHALLENGE_TTL_MS = 5 * 60_000;

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { ...CORS, "Content-Type": "application/json" } });
}
function fail(status: number, code: string): Response {
  return json(status, { error: code });
}

// ---- tiny per-instance rate limiter (best effort; real limits belong in your gateway) ----------------------
const hits = new Map<string, number[]>();
function limited(userId: string, perMinute = 90): boolean {
  const now = Date.now();
  const list = (hits.get(userId) ?? []).filter((t) => now - t < 60_000);
  list.push(now);
  hits.set(userId, list);
  return list.length > perMinute;
}

// ---- helpers ----------------------------------------------------------------------------------------------
function randomHex(bytes: number): string {
  const a = new Uint8Array(bytes);
  crypto.getRandomValues(a);
  return Array.from(a, (b) => b.toString(16).padStart(2, "0")).join("");
}

async function hmacHex(data: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw", new TextEncoder().encode(LINK_SECRET), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const sig = new Uint8Array(await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(data)));
  return Array.from(sig, (b) => b.toString(16).padStart(2, "0")).join("");
}

function safeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

function dashed(id32: string): string {
  const h = id32.toLowerCase();
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}

function parseTime(v: unknown): number {
  const t = typeof v === "string" ? Date.parse(v) : NaN;
  const now = Date.now();
  if (Number.isNaN(t)) return now;
  return Math.min(t, now + 60_000); // never accept timestamps from the future
}

async function linkedUuid(userId: string): Promise<string | null> {
  const { data, error } = await admin.rpc("rkb_link_uuid", { p_user: userId });
  if (error || !data) return null;
  return String(data).toLowerCase();
}

// ---- main -------------------------------------------------------------------------------------------------
Deno.serve(async (req: Request): Promise<Response> => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
  if (req.method !== "POST") return fail(405, "method_not_allowed");
  if (!SUPABASE_URL || !SERVICE_KEY || !ANON_KEY || LINK_SECRET.length < 32) return fail(500, "server_not_configured");

  const authHeader = req.headers.get("Authorization") ?? "";
  if (!authHeader.startsWith("Bearer ")) return fail(401, "unauthorized");
  const userClient = createClient(SUPABASE_URL, ANON_KEY, {
    global: { headers: { Authorization: authHeader } },
    auth: { persistSession: false },
  });
  const { data: userData, error: userErr } = await userClient.auth.getUser();
  if (userErr || !userData?.user) return fail(401, "unauthorized");
  const userId = userData.user.id;
  if (limited(userId)) return fail(429, "rate_limited");

  let body: Record<string, unknown>;
  try {
    body = await req.json();
  } catch {
    return fail(400, "invalid_json");
  }

  try {
    switch (body.action) {
      // ---- account linking ---------------------------------------------------------------------------
      case "link_challenge": {
        const nonce = randomHex(8);
        const exp = Date.now() + CHALLENGE_TTL_MS;
        const sig = (await hmacHex(`${userId}.${nonce}.${exp}`)).slice(0, 24);
        return json(200, { server_id: `${nonce}-${exp.toString(36)}-${sig}`, expires_in: CHALLENGE_TTL_MS / 1000 });
      }

      case "link_verify": {
        const username = String(body.username ?? "");
        const serverId = String(body.server_id ?? "");
        if (!NAME_RE.test(username)) return fail(400, "invalid_username");
        const parts = serverId.split("-");
        if (parts.length !== 3) return fail(400, "invalid_challenge");
        const [nonce, expB36, sig] = parts;
        const exp = parseInt(expB36, 36);
        if (!/^[0-9a-f]{16}$/.test(nonce) || !Number.isFinite(exp) || exp < Date.now()) return fail(400, "challenge_expired");
        const expected = (await hmacHex(`${userId}.${nonce}.${exp}`)).slice(0, 24);
        if (!safeEqual(sig, expected)) return fail(400, "invalid_challenge");

        // Mojang confirms the player "joined" with this serverId using the real account.
        const url = `https://sessionserver.mojang.com/session/minecraft/hasJoined?username=${encodeURIComponent(username)}&serverId=${encodeURIComponent(serverId)}`;
        const res = await fetch(url);
        if (res.status !== 200) return fail(403, "ownership_not_verified");
        const profile = await res.json();
        if (!profile?.id || !/^[0-9a-f]{32}$/i.test(profile.id)) return fail(502, "mojang_error");
        const uuid = dashed(profile.id);

        const { data: owner } = await admin.rpc("rkb_link_owner", { p_uuid: uuid });
        if (owner && String(owner) !== userId) return fail(409, "uuid_linked_to_other_account");
        const { error } = await admin.rpc("rkb_link_set", { p_user: userId, p_uuid: uuid, p_name: String(profile.name ?? username) });
        if (error) return fail(500, "link_failed");
        return json(200, { linked_uuid: uuid, name: profile.name ?? username });
      }

      // ---- badge data (need a verified link) ---------------------------------------------------------
      case "get": {
        const uuid = await linkedUuid(userId);
        if (!uuid) return fail(403, "not_linked");
        const { data: settings } = await admin.from("player_badge_settings").select("badge_enabled,badge_style,visibility_mode,friends_updated_at,updated_at").eq("player_uuid", uuid).maybeSingle();
        const { data: friends } = await admin.from("player_badge_friends").select("friend_uuid").eq("owner_uuid", uuid).limit(MAX_FRIENDS);
        return json(200, { player_uuid: uuid, settings: settings ?? null, friends: (friends ?? []).map((f) => f.friend_uuid) });
      }

      case "put_settings": {
        const uuid = await linkedUuid(userId);
        if (!uuid) return fail(403, "not_linked");
        const style = String(body.badge_style ?? "");
        const mode = String(body.visibility_mode ?? "");
        if (typeof body.badge_enabled !== "boolean" || !STYLES.has(style) || !MODES.has(mode)) return fail(400, "invalid_settings");
        const clientTime = parseTime(body.client_updated_at);

        // Conflict rule: the newer updated_at wins; ties keep the server copy.
        const { data: existing } = await admin.from("player_badge_settings").select("*").eq("player_uuid", uuid).maybeSingle();
        if (existing && Date.parse(existing.updated_at) >= clientTime) {
          return json(200, { applied: false, settings: existing });
        }
        const row = { player_uuid: uuid, badge_enabled: body.badge_enabled, badge_style: style, visibility_mode: mode, updated_at: new Date(clientTime).toISOString() };
        const { data, error } = await admin.from("player_badge_settings").upsert(row, { onConflict: "player_uuid" }).select().single();
        if (error) return fail(500, "write_failed");
        return json(200, { applied: true, settings: data });
      }

      case "put_friends": {
        const uuid = await linkedUuid(userId);
        if (!uuid) return fail(403, "not_linked");
        if (!Array.isArray(body.friends) || body.friends.length > MAX_FRIENDS) return fail(400, "invalid_friends");
        const list = [...new Set(body.friends.map((x) => String(x).toLowerCase()))];
        if (list.some((f) => !UUID_RE.test(f) || f === uuid)) return fail(400, "invalid_friends");
        const clientTime = parseTime(body.client_updated_at);

        const { data: existing } = await admin.from("player_badge_settings").select("friends_updated_at").eq("player_uuid", uuid).maybeSingle();
        if (existing && Date.parse(existing.friends_updated_at) >= clientTime) return json(200, { applied: false });
        if (!existing) {
          const { error } = await admin.from("player_badge_settings").insert({ player_uuid: uuid });
          if (error) return fail(500, "write_failed");
        }
        // replace the whole list (last writer wins). Not atomic; a retry from the client repairs a partial write.
        const del = await admin.from("player_badge_friends").delete().eq("owner_uuid", uuid);
        if (del.error) return fail(500, "write_failed");
        if (list.length > 0) {
          const ins = await admin.from("player_badge_friends").insert(list.map((f) => ({ owner_uuid: uuid, friend_uuid: f })));
          if (ins.error) return fail(500, "write_failed");
        }
        const upd = await admin.from("player_badge_settings").update({ friends_updated_at: new Date(clientTime).toISOString() }).eq("player_uuid", uuid);
        if (upd.error) return fail(500, "write_failed");
        return json(200, { applied: true, count: list.length });
      }

      // ---- who shows a badge (any signed-in user; returns only opt-in data) --------------------------
      case "lookup": {
        if (!Array.isArray(body.uuids) || body.uuids.length > MAX_LOOKUP) return fail(400, "invalid_uuids");
        const ids = [...new Set(body.uuids.map((x) => String(x).toLowerCase()))].filter((x) => UUID_RE.test(x));
        if (ids.length === 0) return json(200, { badges: [] });
        const { data, error } = await admin.from("player_badge_settings").select("player_uuid,badge_style").in("player_uuid", ids).eq("badge_enabled", true);
        if (error) return fail(500, "read_failed");
        return json(200, { badges: (data ?? []).map((r) => ({ uuid: r.player_uuid, style: r.badge_style })) });
      }

      default:
        return fail(400, "unknown_action");
    }
  } catch (_e) {
    return fail(500, "internal_error");
  }
});
