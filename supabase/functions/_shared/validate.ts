// Pure validation helpers shared by the RKB Edge Functions. No Deno or Supabase imports, so they can be unit tested
// with plain Node (see supabase/tests/validate.test.mjs). The Java client implements the same server-key and asset
// rules (ServerKey.java, AssetRules.java); both sides are tested against the same vectors.

export const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export const MC_NAME_RE = /^[A-Za-z0-9_]{1,16}$/;
export const COSMETIC_ID_RE = /^[a-z0-9_]{2,40}$/;
export const SLOTS = ["cape", "badge"] as const;

export function isUuid(v: unknown): v is string {
  return typeof v === "string" && UUID_RE.test(v);
}

export function isCosmeticId(v: unknown): v is string {
  return typeof v === "string" && COSMETIC_ID_RE.test(v);
}

export function isSlot(v: unknown): v is (typeof SLOTS)[number] {
  return typeof v === "string" && (SLOTS as readonly string[]).includes(v);
}

export function isMcName(v: unknown): v is string {
  return typeof v === "string" && MC_NAME_RE.test(v);
}

/** Same rule as the Java client: lower case, drop the default port, drop trailing dots, restrict characters. */
export function normalizeServerKey(address: unknown): string | null {
  if (typeof address !== "string") return null;
  let s = address.trim().toLowerCase();
  if (s.endsWith(":25565")) s = s.slice(0, -6);
  while (s.endsWith(".")) s = s.slice(0, -1);
  return /^[a-z0-9._:\[\]-]{1,100}$/.test(s) ? s : null;
}

/** Storage path of a cape image: lower case letters, digits, underscore, dash, slash, ending in .png, no "..". */
export function isAssetPath(v: unknown): v is string {
  return typeof v === "string" && /^[a-z0-9_/-]{1,100}\.png$/.test(v) && !v.includes("..") && !v.startsWith("/");
}

export function isSha256(v: unknown): v is string {
  return typeof v === "string" && /^[0-9a-f]{64}$/.test(v);
}

/** Redeem codes: letters and digits only, 8 to 32 of them. Returns the canonical upper-case form or null. */
export function normalizeCode(v: unknown): string | null {
  if (typeof v !== "string") return null;
  const s = v.toUpperCase().replace(/[^A-Z0-9]/g, "");
  return s.length >= 8 && s.length <= 32 ? s : null;
}

/** Is "now" inside the optional availability window? */
export function inWindow(from: string | null, until: string | null, now = Date.now()): boolean {
  if (from && Date.parse(from) > now) return false;
  if (until && Date.parse(until) < now) return false;
  return true;
}
