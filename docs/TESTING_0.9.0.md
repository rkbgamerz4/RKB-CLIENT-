# Testing report, RKB CLIENT 0.9.0

Honest status: **Minecraft, Gradle and Supabase were not run.** The sandbox had no network, no game, no Gradle and no
Postgres, and no access to your Supabase project.

## Executed here
| Check | How | Result |
|---|---|---|
| Server key normalisation (shared vectors), asset path/hash/size rules, redeem-code and id validation, availability windows | Node (`node --experimental-strip-types supabase/tests/validate.test.mjs`) against `_shared/validate.ts` | 25 checks passed |
| The same server-key and asset rules in Java | compiled with the JDK compiler and a harness | passed |
| **Account switching, client side:** RKB_1234's cached inventory is never returned for RKB_5678; switching back or signing out empties it | `AccountCache` harness | 7 checks passed |
| Badge keys: shipped glyphs supported, unknown ignored | harness | passed |
| Project consistency: imports, braces, JSON files, mixin list, every badge glyph has a font provider and a texture file | static script | no problems |
| javac over all sources without libraries, filtered for unresolved symbols that belong to our own classes | script | none (weak check: missing libraries hide other errors) |

## NOT executed (you must run these)
- `./gradlew build` and `./gradlew test` (JUnit mirrors of the checks above).
- The SQL migration and every SQL function (claim, redeem, purchase_start, fulfil, equip, presence, lookup). Read twice for quoting, never run.
- The Edge Function (type-checked by nothing, run by nothing).
- In game: cape rendering, name tag badges, presence/lookup, the Cosmetics screen, commands.

## Test plan with expected results
**Account switching / inventory persistence (needs backend)**
1. Account A: `/rkb cosmetics claim badge_rkb_logo`, equip it. Account B (second email): inventory empty, nothing equipped. *Expected: B never sees A's item.*
2. Sign out and in as A on another PC/launcher, with an offline Minecraft name. *Expected: same inventory and equipment.*
3. Switch to B in the same game session. *Expected: the screen shows B's data only; `config/rkbclient-cosmetics-account.json` holds B's id.*
4. As B, send `claim`/`equip` with A's item id via a modified client. *Expected: `already_owned`/`not_owned`/`not_claimable`; paid items are never granted by `claim`.*
5. Redeem the same code twice, from A and B. *Expected: second attempt "not valid, already used".*
6. `select * from rkb_inventory` as B's token through PostgREST. *Expected: only B's rows (RLS).*

**Multiplayer visibility (needs two game clients)**
1. Player 1 official (linked), Player 2 offline name; both module on, both signed in, each with a badge and a cape equipped, same server.
2. *Expected within ~40 s: each sees the other's badge on the name tag and cape on the model; presence rows exist (`verified` true for the linked one only).*
3. Player 3 (module off) *Expected: sees nothing and sends no requests.*
4. Two accounts claim the same offline name on one server. *Expected: second gets "identity_taken".*

## Assumptions to check on first build
`AbstractClientPlayerEntity.getSkin()` (mixin target; the 1.21.11 Yarn javadoc for `ClientPlayerLikeEntity` lists `getSkin`),
`SkinTextures` constructor and `AssetInfo.TextureAssetInfo(Identifier, Identifier)` (checked in the javadoc),
`NativeImageBackedTexture(Supplier, NativeImage)` + `upload()` (checked), `TextureManager.registerTexture(Identifier, AbstractTexture)` (not checked),
`NativeImage.read(InputStream)`, `ClientWorld.getPlayers()`, `MinecraftClient.getSessionService().joinServer(UUID, String, String)` (from 0.8.0, not checked).
