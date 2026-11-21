# Testing report, RKB CLIENT 0.9.0

Honest status: **Gradle build and unit tests pass locally. Minecraft was not run and the live Supabase project was not
accessible**, so in-game rendering and applied database changes are still unverified.

## Executed here
| Check | How | Result |
|---|---|---|
| `gradle compileJava`, `gradle test`, `gradle build` | Gradle 9.2.0, Java 21, Fabric Loom 1.14.10, Minecraft 1.21.11 | passed |
| JUnit: UUID parsing, sync rule, rate limiter, catalog parsing | `src/test/java/com/rkb/client/cloud/CloudLogicTest.java` | passed |
| JUnit: server key/asset rules, account isolation, badge keys | `src/test/java/com/rkb/client/cosmetics/CosmeticsLogicTest.java` | passed |
| JUnit: skin name/model/uuid rules, PNG IHDR size validation, ten bundled cape files and hashes | `src/test/java/com/rkb/client/cosmetics/SkinRulesTest.java` | passed |
| Node: server-key shared vectors, asset paths, hashes, redeem codes, windows, skin names/models/paths, PNG IHDR sizes | `node --experimental-strip-types supabase/tests/validate.test.mjs` | 34 checks passed |
| Java compile of new GUI, skin-set store/cloud sync, texture caches, mixins and backend hooks | `gradle compileJava` | passed |
| Gradle packaging (jar, sourcesJar, remapped jars) | `gradle build` | passed |

Baseline fixes that were required before the new code would compile:
`ParticlesMode` moved to `net.minecraft.particle.ParticlesMode`; `MinecraftClient.getSessionService()` is now
`getApiServices().sessionService()`; the remote cosmetics scheduled task wraps its tick body in a try/catch so a
transient failure cannot cancel all future refreshes.

## NOT executed (you must run these)
- The two new SQL migrations, including `rkb_skin_*` RPCs, RLS and the updated `rkb_lookup_looks`. They were read
  carefully, not run against Postgres or Supabase.
- The `rkb-cosmetics` Edge Function skin actions (`skin_list`, `skin_upload`, `skin_rename`, `skin_delete`,
  `skin_equip`, `skin_unequip`). The pure validation layer is unit tested with Node; the Deno function itself was not run.
- In game: the Cosmetics window layout and drag/scroll behaviour, skin/cape rendering, name-tag logo, presence/lookup,
  skin upload by path and drag-and-drop, join/leave and account switching.

## Test plan with expected results
**Account switching / inventory persistence (needs backend)**
1. Account A: `/rkb cosmetics claim badge_rkb_logo`, equip it. Account B (second email): inventory empty, nothing equipped. *Expected: B never sees A's item.*
2. Sign out and in as A on another PC/launcher, with an offline Minecraft name. *Expected: same inventory and equipment.*
3. Switch to B in the same game session. *Expected: the screen shows B's data only; `config/rkbclient-cosmetics-account.json` holds B's id.*
4. As B, send `claim`/`equip` with A's item id via a modified client. *Expected: `already_owned`/`not_owned`/`not_claimable`; paid items are never granted by `claim`.*
5. Redeem the same code twice, from A and B. *Expected: second attempt "not valid, already used".*
6. `select * from rkb_inventory` as B's token through PostgREST. *Expected: only B's rows (RLS).*

**Cape collection (needs backend)**
1. Fresh account, open Cape Collection. *Expected: exactly ten cards; all show NOT OWNED; no Equip button works.*
2. Grant `cape_bangladesh` from the service role, reload. *Expected: OWNED, then EQUIPPED after Equip; other cards unchanged.*
3. Remove the catalog row for one cape. *Expected: that card still appears, NOT OWNED, with no claim/equip action.*

**Skin sets (local and verified cloud)**
1. Upload a valid 64x64 PNG by path; drop a second one on the window; try a 128x128 PNG and a text file.
   *Expected: two named sets saved, bad files rejected with a clear message, original files untouched.*
2. Rename, delete with confirmation, apply another set, restart Minecraft. *Expected: names, order and active set persist; the active skin is present immediately after applying, without a restart.*
3. Offline/unlinked account: open Save & Apply. *Expected: local-only message; no skin actions accepted by the backend.*
4. Link with `/rkb cloud link`, refresh. *Expected: eligible sets uploaded once to `rkb_skin_sets`, equipped id reported; switching a set updates it without a restart.*
5. Two RKB accounts claim the same Minecraft UUID: only the Mojang-verified link may touch cloud skins; the other gets `identity_not_verified`.

**Multiplayer visibility (needs two game clients)**
1. Player 1 official (linked), Player 2 offline name; both module on, both signed in, each with a cape and skin set equipped, same server.
2. *Expected within ~40 s: each sees the other's skin/cape; presence rows exist (`verified` true for the linked one only), and the offline one's skin is not applied to other players.*
3. Player 3 (module off) *Expected: sees nothing and sends no requests.*
4. Two accounts claim the same offline name on one server. *Expected: second gets "identity_taken".*
