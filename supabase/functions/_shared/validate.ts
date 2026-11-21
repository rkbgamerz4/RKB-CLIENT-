// Pure validation helpers shared by the RKB Edge Functions. No Deno or Supabase imports, so they can be unit tested
// with plain Node (see supabase/tests/validate.test.mjs). The Java client implements the same server-key and asset
// rules (ServerKey.java, AssetRules.java); both sides are tested against the same vectors.

export const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export const MC_NAME_RE = /^[A-Za-z0-9_]{1,16}$/;
export const COSMETIC_ID_RE = /^[a-z0-9_]{2,40}$/;
export const SLOTS = ["cape", "badge"] as const;
export const SKIN_MODELS = ["classic", "slim"] as const;
export const SKIN_NAME_RE = /^[\p{L}\p{N} _.-]{1,32}$/u;
export const MAX_SKIN_BYTES = 256 * 1024;

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

export function isSkinName(v: unknown): v is string {
  return typeof v === "string" && SKIN_NAME_RE.test(v.trim()) && v.trim().length > 0;
}

export function isSkinModel(v: unknown): v is (typeof SKIN_MODELS)[number] {
  return typeof v === "string" && (SKIN_MODELS as readonly string[]).includes(v);
}

/** Skin uploads are content-addressed: skins/<sha256>.png in the public-read bucket. */
export function isSkinPath(v: unknown): v is string {
  return isAssetPath(v) && v.startsWith("skins/");
}

const PNG_SIGNATURE = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];

/** Reads an IHDR size without decoding. Returns null for anything that is not a standard Minecraft skin PNG. */
export function skinPngSize(bytes: Uint8Array): { width: number; height: number } | null {
  if (bytes.length < 33 || bytes.length > MAX_SKIN_BYTES) return null;
  for (let i = 0; i < PNG_SIGNATURE.length; i++) {
    if (bytes[i] !== PNG_SIGNATURE[i]) return null;
  }
  if (bytes[12] !== 0x49 || bytes[13] !== 0x48 || bytes[14] !== 0x44 || bytes[15] !== 0x52) return null; // IHDR
  const width = (bytes[16] << 24) | (bytes[17] << 16) | (bytes[18] << 8) | bytes[19];
  const height = (bytes[20] << 24) | (bytes[21] << 16) | (bytes[22] << 8) | bytes[23];
  if (width === 64 && (height === 64 || height === 32)) return { width, height };
  return null;
}

export function isSkinPng(bytes: Uint8Array): boolean {
  return skinPngSize(bytes) !== null;
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
