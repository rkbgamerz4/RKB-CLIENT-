// RKB CLIENT 0.9.0 - rkb-cosmetics Edge Function (Deno).
//
// POST JSON { action, ... } with "Authorization: Bearer <Supabase Auth access token>".
// The caller IS the RKB account: the account id is the verified JWT subject and nothing else. Item ids, prices,
// ownership and slots are never taken from the request as facts: the database decides (see the SQL functions).
//
// Actions: account, catalog, inventory, history, claim, redeem, purchase_start, equip, unequip, presence, lookup.
// Reads go through the caller's own JWT, so Row Level Security applies. Writes call SECURITY DEFINER SQL functions
// with the service role. No Minecraft session token is ever sent here.

import { createClient } from "npm:@supabase/supabase-js@2";
import {
  inWindow, isCosmeticId, isMcName, isSlot, isUuid, normalizeCode, normalizeServerKey,
} from "../_shared/validate.ts";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

const admin = createClient(SUPABASE_URL, SERVICE_KEY, { auth: { persistSession: false } });

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const MAX_LOOKUP = 100;

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { ...CORS, "Content-Type": "application/json" } });
}
const fail = (status: number, error: string) => json(status, { ok: false, error });

// best-effort per-instance limiter; add a real one in front of the function if you expect abuse
const hits = new Map<string, number[]>();
function limited(key: string, perMinute: number): boolean {
  const now = Date.now();
  const list = (hits.get(key) ?? []).filter((t) => now - t < 60_000);
  list.push(now);
  hits.set(key, list);
  return list.length > perMinute;
}

Deno.serve(async (req: Request): Promise<Response> => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
  if (req.method !== "POST") return fail(405, "method_not_allowed");
  if (!SUPABASE_URL || !SERVICE_KEY || !ANON_KEY) return fail(500, "server_not_configured");

  const authHeader = req.headers.get("Authorization") ?? "";
  if (!authHeader.startsWith("Bearer ")) return fail(401, "unauthorized");
  const userClient = createClient(SUPABASE_URL, ANON_KEY, {
    global: { headers: { Authorization: authHeader } },
    auth: { persistSession: false },
  });
  const { data: userData, error: userErr } = await userClient.auth.getUser();
  if (userErr || !userData?.user) return fail(401, "unauthorized");
  const accountId = userData.user.id;
  if (limited(accountId, 120)) return fail(429, "rate_limited");

  let body: Record<string, unknown>;
  try {
    body = await req.json();
  } catch {
    return fail(400, "invalid_json");
  }

  try {
    switch (body.action) {
      case "account": {
        const { data, error } = await admin.rpc("rkb_ensure_account", { p_user: accountId });
        if (error) return fail(500, "account_failed");
        return json(200, { ok: true, rkb_id: data });
      }

      case "catalog": {
        const { data: items, error } = await userClient.from("rkb_cosmetics")
          .select("id,name,description,type,rarity,price_cents,currency,claimable,purchase_info,asset_path,asset_sha256,badge_key,available_from,available_until")
          .order("created_at", { ascending: true }).limit(500);
        if (error) return fail(500, "read_failed");
        const { data: owned } = await userClient.from("rkb_inventory").select("cosmetic_id").limit(1000);
        const ownedSet = new Set((owned ?? []).map((r) => r.cosmetic_id));
        const list = (items ?? [])
          .filter((c) => inWindow(c.available_from, c.available_until))
          .map(({ available_from: _f, available_until: _u, ...c }) => ({ ...c, owned: ownedSet.has(c.id) }));
        return json(200, { ok: true, items: list });
      }

      case "inventory": {
        const { data: inv, error } = await userClient.from("rkb_inventory")
          .select("cosmetic_id,source,acquired_at").order("acquired_at", { ascending: false }).limit(1000);
        if (error) return fail(500, "read_failed");
        const { data: eq } = await userClient.from("rkb_equipped_cosmetics").select("slot,cosmetic_id");
        return json(200, { ok: true, items: inv ?? [], equipped: eq ?? [] });
      }

      case "history": {
        const { data, error } = await userClient.from("rkb_purchase_history")
          .select("cosmetic_id,kind,status,amount_cents,currency,reference,created_at,completed_at")
          .order("created_at", { ascending: false }).limit(100);
        if (error) return fail(500, "read_failed");
        return json(200, { ok: true, entries: data ?? [] });
      }

      case "claim": {
        if (!isCosmeticId(body.cosmetic_id)) return fail(400, "invalid_cosmetic_id");
        const { data, error } = await admin.rpc("rkb_claim", { p_account: accountId, p_cosmetic: body.cosmetic_id });
        if (error) return fail(500, "claim_failed");
        return json(200, data);
      }

      case "redeem": {
        if (limited(`redeem:${accountId}`, 10)) return fail(429, "rate_limited");
        const code = normalizeCode(body.code);
        if (!code) return json(200, { ok: false, error: "invalid_or_used_code" });
        const { data, error } = await admin.rpc("rkb_redeem", { p_account: accountId, p_code: code });
        if (error) return fail(500, "redeem_failed");
        return json(200, data);
      }

      case "purchase_start": {
        if (!isCosmeticId(body.cosmetic_id)) return fail(400, "invalid_cosmetic_id");
        const { data, error } = await admin.rpc("rkb_purchase_start", { p_account: accountId, p_cosmetic: body.cosmetic_id });
        if (error) return fail(500, "purchase_failed");
        return json(200, data);
      }

      case "equip": {
        if (!isCosmeticId(body.cosmetic_id)) return fail(400, "invalid_cosmetic_id");
        const { data, error } = await admin.rpc("rkb_equip", { p_account: accountId, p_cosmetic: body.cosmetic_id });
        if (error) return fail(500, "equip_failed");
        return json(200, data);
      }

      case "unequip": {
        if (!isSlot(body.slot)) return fail(400, "invalid_slot");
        const { data, error } = await admin.rpc("rkb_unequip", { p_account: accountId, p_slot: body.slot });
        if (error) return fail(500, "unequip_failed");
        return json(200, data);
      }

      // The client says "this Minecraft player on this server is me". Ownership is never involved.
      case "presence": {
        const serverKey = normalizeServerKey(body.server_key);
        if (!serverKey || !isUuid(body.mc_uuid) || !isMcName(body.mc_name)) return fail(400, "invalid_presence");
        const mcUuid = body.mc_uuid.toLowerCase();
        // "verified" is decided HERE from the 0.8.0 Mojang-verified link, never from the request.
        let verified = false;
        const linked = await admin.rpc("rkb_link_uuid", { p_user: accountId });
        if (!linked.error && linked.data) verified = String(linked.data).toLowerCase() === mcUuid;
        const { data, error } = await admin.rpc("rkb_presence_register", {
          p_account: accountId, p_mc_uuid: mcUuid, p_mc_name: body.mc_name, p_server_key: serverKey, p_verified: verified,
        });
        if (error) return fail(500, "presence_failed");
        return json(200, data);
      }

      case "lookup": {
        const serverKey = normalizeServerKey(body.server_key);
        if (!serverKey || !Array.isArray(body.uuids) || body.uuids.length > MAX_LOOKUP) return fail(400, "invalid_lookup");
        const ids = [...new Set(body.uuids.map((x) => String(x).toLowerCase()))].filter(isUuid);
        if (ids.length === 0) return json(200, { ok: true, looks: [] });
        const { data, error } = await admin.rpc("rkb_lookup_looks", { p_server_key: serverKey, p_uuids: ids });
        if (error) return fail(500, "lookup_failed");
        return json(200, { ok: true, looks: data ?? [] });
      }

      default:
        return fail(400, "unknown_action");
    }
  } catch (_e) {
    return fail(500, "internal_error");
  }
});
