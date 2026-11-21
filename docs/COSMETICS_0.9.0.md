# RKB account cosmetics (0.9.0)

## The ownership rule
Cosmetics belong to the **RKB account** (a Supabase Auth user, shown as `RKB_1234`). Minecraft username, Minecraft UUID,
launcher and game profile are never an ownership key. Same RKB account = same inventory on every PC and launcher, with an
offline or an official Minecraft account. Another RKB account has its own inventory; nothing is shared.

## Using it
1. Create or sign in to an RKB account: `/rkb cloud register <email> <password>` or `/rkb cloud login <email> <password>`
   (needs your publishable key in `config/rkbclient-cloud.json`).
2. Open the **COSMETICS** tab (or `/rkb cosmetics open`). The full window has eight pages: **Cape Collection**, **Skin**,
   **Skin Sets**, **Equipped**, **RKB Collection**, **Upload Skin**, **Preview** and **Save & Apply**.
   Commands: `/rkb cosmetics list|inventory|history|refresh|claim <id>|buy <id>|redeem <code>|equip <id>|unequip <slot>`.
3. Turn on the **RKB Cosmetics** module (Visuals; off by default) to see your cape/badge and other RKB players'. In its settings:
   "Show other players' cosmetics", "Let others see my cosmetics" and "RKB logo on name tags".

## Cape Collection
The collection always shows exactly ten designs: **Bangladesh, India, USA, Pakistan, Brazil, Japan, UK, Nepal,
RKB Original, RKB Shadow**. A card is labelled **EQUIPPED**, **OWNED** or **NOT OWNED** from the server's catalog and
inventory only. The PNGs are bundled with the mod as a display safety net, but a bundled image is used only when its
SHA-256 matches the catalog row; a missing catalog row or a missing ownership row never makes a cape claimable or owned.
You cannot equip a cape you do not own.

## Skin sets (local library + verified cloud sync)
- **Upload Skin**: paste a PNG path, use `Scan inbox` on `config/rkbclient-skins/inbox/`, or drop one or more PNGs on the
  cosmetics window. Files are copied to `config/rkbclient-skins/files/`; the original is never changed.
- Only 64x64 (modern) and 64x32 (legacy) PNGs up to 256 KB are accepted. Classic (Steve) and slim (Alex) models are
  detected from the skin texture. Legacy images are expanded to the modern 64x64 layout on load so the preview and the
  in-game model agree. HD skins are rejected rather than silently distorted.
- **Skin Sets**: up to 20 named sets; rename, delete with confirmation, and Apply instantly. No Minecraft restart or
  server rejoin is needed. The active set and the index are persisted locally.
- **Preview** shows your skin with a selected cape. **Save & Apply** persists the library, applies the active set and
  pushes eligible changes to your verified RKB account.
- Cloud sync requires the signed-in RKB account to have proven the *current* Minecraft UUID with `/rkb cloud link`.
  Sets then sync through `rkb_skin_sets` / `rkb_equipped_skin` and the `rkb-cosmetics` Edge Function (new skin actions).
  Offline or unlinked accounts keep using the local library only and never read or write another player's cloud skins.
- On a new account or UUID, local files stay on disk but old remote links are dropped; verified accounts can upload them
  again. A cloud version with a different image replaces the local PNG only after its SHA-256 and PNG dimensions pass.

## Identity and multiplayer
The menu reads your current Minecraft username/UUID from the running session; there is no second RKB username/password
login screen. Cloud inventory and skin sync are enabled only when `linkedUuid == session UUID` (the Mojang-verified link).
For other players, the backend lookup returns a `skin` object only for presence rows marked `verified`; unverified/offline
identities keep their cape/badge behaviour but cannot make others load an arbitrary skin. Textures are fetched off the
render thread, SHA-256 checked, size-limited (256 KB) and cached in `config/rkbclient-cosmetics/` and
`config/rkbclient-skins/cache/`. Join/leave and cosmetic changes refresh the lookup; stale entries are dropped. Known RKB
players get the RKB logo or their equipped badge before their real username in name tags (toggle in module settings).

## Flows (all decided on the server)
| Action | What the server checks |
|---|---|
| claim | item exists, active, in its availability window, `claimable` and price 0, not already owned |
| redeem | hashed code unused and unexpired, item active, not already owned (then the code is consumed atomically) |
| buy | creates a pending history row only. Nothing is granted until you fulfil it (price checked against the catalog) |
| equip | the account owns the item (function check + composite foreign key); one item per slot |
| history | the account's own rows (RLS) |

## Seeing other players
Viewers need the module on and an RKB login. Every ~20 seconds (never during a frame) the client asks which players around it
have an RKB account with equipped items, downloads each cape image once (hash-checked, 256 KB limit, cached in
`config/rkbclient-cosmetics/`), and draws from memory. Equipped badges are glyphs shipped in the mod (keys `rkb_logo`,
`rkb_gold`, `rkb_violet`, `rkb_red`); other badge keys are ignored.

### Trust model of "who is this player" (read this)
To show your cape on *your Minecraft player*, other clients must map that Minecraft player to your RKB account. That map
(`rkb_presence`) is **display only**; ownership never reads it. Its strength depends on the Minecraft account:
- **Official account linked with `/rkb cloud link`** (Mojang join handshake): *verified*. A verified identity replaces an unverified claim.
- **Offline account** (or an official one that is not linked): *cannot be proven*. First account to claim that Minecraft name on that server keeps it for 30 days after its last activity. Someone could register a name before its real owner does and then their cape shows on that name. The damage is cosmetic, but it is a real limit of offline mode.
- One account can register at most 10 identities. Presence is per server (the same offline name on two servers is two entries).
Privacy: enabling "Let others see my cosmetics" sends your Minecraft name, UUID and the server address to the backend. Nothing is sent while the module is off.

## Compatibility
| | |
|---|---|
| Minecraft | 1.21.11 |
| Fabric Loader | 0.18.2 or newer (built against 0.18.4) |
| Fabric API | 0.141.1+1.21.11 or a compatible 1.21.11 build |
| Java | 21 |
| Needs | the same RKB CLIENT jar on every player who should see the cosmetics |
Any launcher that can run a Fabric 1.21.11 profile and load a mod from `mods/` works in principle (RKB LAUNCHER, Zalith, Prism, ...).
This is **not assumed**: a launcher or client with its own mod loader (for example Feather's) may not load Fabric mods at all.
Other mods that replace capes or skins can conflict, because RKB swaps the cape in the player's skin textures.

## Backend changes needed
Apply `supabase/migrations/20261121000000_rkb_skins_and_cape_collection.sql` after the two earlier 0.9.0 migrations,
then redeploy `rkb-cosmetics`. It creates `rkb_skin_sets` and `rkb_equipped_skin` with RLS and service-role RPCs, updates
`rkb_lookup_looks` so remote skins require `verified`, and seeds the ten cape catalog rows (no inventory grants).
Cap PNGs in `src/main/resources/assets/rkbclient/textures/cosmetics/` must also be uploaded to the `rkb-cosmetics`
bucket at the seeded `asset_path` values if you want non-bundled clients to download them.

## Limitations
- No payment provider is integrated; paid items are fulfilled by you from the service role (see supabase/README.md).
- Existing `cosmetics`/`cosmetic_*` data is not imported (schema was unavailable).
- Capes only render in first-person-hidden/third-person like any cape, and only for players whose client has loaded the image.
- The new migration and Edge Function are supplied, **not applied to the live project** (no Supabase credentials were
  available). Until you apply them, skin-set cloud actions return an error and local sets still work offline.
- Gradle build and unit tests pass, but the window, rendering and multiplayer flows have not been exercised in game
  (see TESTING_0.9.0.md).
