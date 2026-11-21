// Run: node --experimental-strip-types supabase/tests/validate.test.mjs
import {
  inWindow, isAssetPath, isCosmeticId, isMcName, isSha256, isSkinModel, isSkinName, isSkinPath, isSkinPng,
  isSlot, isUuid, MAX_SKIN_BYTES, normalizeCode, normalizeServerKey, skinPngSize,
} from "../functions/_shared/validate.ts";

let failed = 0;
function check(ok, what) { console.log((ok ? "PASS " : "FAIL ") + what); if (!ok) failed++; }
const eq = (a, b, what) => check(a === b, `${what} (got ${JSON.stringify(a)})`);

// server key: these vectors are shared with the Java test (ServerKey)
eq(normalizeServerKey("Play.Example.com:25565"), "play.example.com", "drops default port and lower-cases");
eq(normalizeServerKey("play.example.com."), "play.example.com", "drops trailing dot");
eq(normalizeServerKey("play.example.com:25566"), "play.example.com:25566", "keeps custom port");
eq(normalizeServerKey("  mc.test  "), "mc.test", "trims");
eq(normalizeServerKey("bad host"), null, "rejects spaces");
eq(normalizeServerKey(""), null, "rejects empty");
eq(normalizeServerKey("a".repeat(101)), null, "rejects overlong");
eq(normalizeServerKey(42), null, "rejects non-string");

// ids and ownership inputs
check(isUuid("069a79f4-44e9-4726-a5be-fca90e38aaf5"), "uuid accepted");
check(!isUuid("069a79f444e94726a5befca90e38aaf5"), "compact uuid rejected here (client sends dashed)");
check(!isUuid("'; drop table rkb_inventory;--"), "sql-ish uuid rejected");
check(isCosmeticId("cape_blue_1") && !isCosmeticId("Cape Blue") && !isCosmeticId("a") && !isCosmeticId("x".repeat(41)), "cosmetic id rules");
check(isSlot("cape") && isSlot("badge") && !isSlot("hat") && !isSlot(null), "slot rules");
check(isMcName("Steve_01") && !isMcName("bad name") && !isMcName("this_name_is_too_long"), "minecraft name rules");

// assets
check(isAssetPath("capes/blue_01.png"), "asset path accepted");
check(!isAssetPath("../secret.png") && !isAssetPath("/abs.png") && !isAssetPath("a/b.jpg") && !isAssetPath("UPPER.png") && !isAssetPath("a..b.png"), "bad asset paths rejected");
check(isSha256("a".repeat(64)) && !isSha256("A".repeat(64)) && !isSha256("abc"), "sha256 format");

// redeem codes
eq(normalizeCode("abcd-1234-efgh-5678"), "ABCD1234EFGH5678", "code normalised");
eq(normalizeCode("short"), null, "short code rejected");
eq(normalizeCode("x".repeat(33)), null, "long code rejected");
eq(normalizeCode(null), null, "null code rejected");

// availability windows
const now = Date.parse("2026-11-01T00:00:00Z");
check(inWindow(null, null, now), "no window = available");
check(!inWindow("2026-12-01T00:00:00Z", null, now), "not yet available");
check(!inWindow(null, "2026-10-01T00:00:00Z", now), "expired");
check(inWindow("2026-10-01T00:00:00Z", "2026-12-01T00:00:00Z", now), "inside window");

// skin sets (the same vectors are checked by the Java SkinRulesTest)
check(isSkinName("My Skin") && isSkinName("Дарк Скин") && isSkinName("A.B-1_2"), "skin names accepted");
check(!isSkinName("") && !isSkinName("a".repeat(33)) && !isSkinName("bad/name") && !isSkinName(null), "bad skin names rejected");
check(isSkinModel("classic") && isSkinModel("slim") && !isSkinModel("alex") && !isSkinModel(null), "skin models");
check(isSkinPath("skins/" + "a".repeat(64) + ".png"), "skin storage path accepted");
check(!isSkinPath("capes/a.png") && !isSkinPath("../skins/a.png") && !isSkinPath("skins/a.JPG"), "bad skin paths rejected");

function png(width, height) {
  const b = new Uint8Array(33);
  b.set([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a], 0);
  b[11] = 13;
  b.set([0x49, 0x48, 0x44, 0x52], 12);
  b[19] = width;
  b[23] = height;
  return b;
}
eq(JSON.stringify(skinPngSize(png(64, 32))), '{"width":64,"height":32}', "PNG IHDR parsed");
check(isSkinPng(png(64, 64)) && isSkinPng(png(64, 32)), "standard skin dimensions accepted");
check(!isSkinPng(png(128, 128)) && !isSkinPng(new Uint8Array(3)), "HD and malformed skins rejected");
check(!isSkinPng(new Uint8Array(MAX_SKIN_BYTES + 1)), "oversized upload rejected");

console.log(failed === 0 ? "ALL PASSED" : failed + " FAILED");
process.exit(failed === 0 ? 0 : 1);
