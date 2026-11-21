# RKB CLIENT 0.9.0

**Minecraft:** 1.21.11  |  **Loader:** Fabric 0.18.2+  |  **Requires:** Fabric API  |  **Java:** 21

## Account-based cosmetics
- Your **RKB account** (shown as RKB_1234) owns your cosmetics. Minecraft account, UUID and launcher never decide ownership.
- COSMETICS window: Cape Collection (exactly ten official capes with honest Owned / Not Owned / Equipped state), Skin,
  Skin Sets, Equipped, RKB Collection, Upload Skin, Preview and Save & Apply.
- Shop, inventory, equip/unequip and purchase history also remain available through `/rkb cosmetics ...`.
- Free items can be claimed; codes can be redeemed; paid items start a pending purchase that you complete outside the game
  and the server fulfils. The server checks everything; the client only sends actions.

## Skins and identity
- Save up to 20 named skin sets from 64x64 or 64x32 PNGs (classic/slim detected), rename/delete them, apply instantly
  without restarting, and preview them with a cape. Files stay local in `config/rkbclient-skins/`.
- The current Minecraft username/UUID is used automatically; there is no separate RKB login screen in cosmetics.
  Skin sets sync only after `/rkb cloud link` proves that exact UUID. Verified RKB CLIENT players see each other's
  skins and capes through a cached, hash-checked lookup (no network on the render thread).
- Known RKB name tags show the RKB logo or the equipped badge before the real username (toggle in module settings).

## Backend (`supabase/`)
- New tables: accounts, cosmetics, inventory, equipped, purchase history, redeem codes, presence, skin sets, equipped skin.
  RLS on all of them; clients can only read their own rows; every write goes through service-role functions.
- New Edge Function `rkb-cosmetics` with catalog/inventory/claim/redeem/purchase/equip/presence/lookup and skin actions.
  Apply `20261121000000_rkb_skins_and_cape_collection.sql` as well; it updates lookups and seeds the ten cape catalog rows.
- Existing tables and `cosmetics-catalog` are untouched.

## Verification and known limits
- Gradle build and unit tests pass locally (see docs/TESTING_0.9.0.md); the Supabase migration/function were not applied to
  the live project and the game was not run here.
- Offline Minecraft identities cannot be proven; they stay local-only for skins (docs/COSMETICS_0.9.0.md).
- No payment provider is integrated.
