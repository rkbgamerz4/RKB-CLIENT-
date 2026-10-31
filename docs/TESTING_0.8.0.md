# Testing report, RKB CLIENT 0.8.0

Honest status: **Minecraft was not run, Gradle was not run, Supabase was not touched.** The sandbox had no network,
no Minecraft, no Gradle and no access to your Supabase project.

## What was actually executed
| Check | How | Result |
|---|---|---|
| UUID parsing (dashed, 32 hex, uppercase, whitespace), invalid UUIDs, usernames | compiled `UuidUtil` with the JDK compiler and ran a small harness | 9 checks passed |
| Sync conflict rule (newer wins, tie keeps, fresh install pulls) | same harness, `SyncMerge` | 5 checks passed |
| Rate limiter spacing | same harness | passed |
| Imports / class references inside the project, braces, JSON files (fabric.mod.json, mixins, font provider, lang) | static script | no problems |
| javac over all sources without libraries | filtered for errors that are not "missing Minecraft/Fabric class" | none found; this is a weak check because missing libraries hide other errors |
| Edge Function braces/parentheses | static script | balanced; **not run, not type-checked** |
| Migration SQL | read twice for quoting; **not executed** | unverified |

The JUnit file `src/test/java/com/rkb/client/cloud/CloudLogicTest.java` mirrors the harness plus a catalog-parsing test.
It has not been run (needs Gradle and Gson). Run `./gradlew test`. If the test setup causes trouble, delete the `test`
block and the three `test*` lines in `build.gradle`; nothing else depends on them.

## Not tested (needs you)
Default OFF on a fresh config, enable/disable toggle, Logo and Letter R, Friends vs Everyone, offline behaviour,
login/logout, link, cloud sync both directions, duplicate friend, invalid UUID in game, client start-up, existing HUD
and the existing badge behaviour, shutdown with a sync pending. Suggested order:
1. Delete `config/rkbclient.json`, start the game, confirm Settings > NAME TAG BADGE shows "Enable badge" off and no
   other controls.
2. Switch it on, add a friend with `/rkb friend add <name>`, check the logo appears on that player.
3. Add `anonKey`, deploy the backend (supabase/README.md), run `/rkb cloud login`, `/rkb cloud link`, `/rkb cloud sync`.
4. Turn the network off and confirm local settings still work and the status says Offline.

## Assumptions to check on first build
- `MinecraftClient.getSession()`, `getSessionService()`, `Session.getUuidOrNull()/getUsername()/getAccessToken()` exist as
  used (checked against the 1.21.11 Yarn javadoc for Session; `joinServer(UUID, String, String)` was not checked).
- `ClientWorld.getPlayers()`, `PlayerEntity.getUuid()`, `Text.empty()`, `Style.withColor(int)`.
- `search.visible` field and `TextFieldWidget.setPlaceholder` (from earlier versions).
