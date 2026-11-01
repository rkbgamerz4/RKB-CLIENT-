# RKB account cosmetics (0.9.0)

## The ownership rule
Cosmetics belong to the **RKB account** (a Supabase Auth user, shown as `RKB_1234`). Minecraft username, Minecraft UUID,
launcher and game profile are never an ownership key. Same RKB account = same inventory on every PC and launcher, with an
offline or an official Minecraft account. Another RKB account has its own inventory; nothing is shared.

## Using it
1. Create or sign in to an RKB account: `/rkb cloud register <email> <password>` or `/rkb cloud login <email> <password>`
   (needs your publishable key in `config/rkbclient-cloud.json`).
2. Open the **COSMETICS** tab (or `/rkb cosmetics open`): Shop (claim free items, start a purchase), Inventory (equip / unequip), History.
   Commands: `/rkb cosmetics list|inventory|history|refresh|claim <id>|buy <id>|redeem <code>|equip <id>|unequip <slot>`.
3. Turn on the **RKB Cosmetics** module (Visuals; off by default) to see your cape/badge and other RKB players'. In its settings:
   "Show other players' cosmetics" and "Let others see my cosmetics".

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

## Limitations
- No payment provider is integrated; paid items are fulfilled by you from the service role (see supabase/README.md).
- Existing `cosmetics`/`cosmetic_*` data is not imported (schema was unavailable).
- Capes only render in first-person-hidden/third-person like any cape, and only for players whose client has loaded the image.
- Everything client-side is unverified until built and run in game (see TESTING_0.9.0.md).
