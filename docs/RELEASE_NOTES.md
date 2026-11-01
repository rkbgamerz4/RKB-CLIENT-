# RKB CLIENT 0.9.0

**Minecraft:** 1.21.11  |  **Loader:** Fabric 0.18.2+  |  **Requires:** Fabric API  |  **Java:** 21

## Account-based cosmetics
- Your **RKB account** (shown as RKB_1234) owns your cosmetics. Minecraft account, UUID and launcher never decide ownership.
- Shop, inventory, equip/unequip and purchase history: COSMETICS tab or `/rkb cosmetics ...`.
- Free items can be claimed; codes can be redeemed; paid items start a pending purchase that you complete outside the game
  and the server fulfils. The server checks everything; the client only sends actions.
- **RKB Cosmetics** module (Visuals, off by default): your cape and badge, and other RKB players' capes and badges.
- `/rkb cloud register` creates an RKB account.

## Backend (`supabase/`)
- New tables: accounts, cosmetics, inventory, equipped, purchase history, redeem codes, presence. RLS on all of them; clients can only read their own rows; every write goes through service-role functions.
- New Edge Function `rkb-cosmetics`. Existing tables and `cosmetics-catalog` are untouched.

## Known limits
- Not built or run in game by the author (docs/TESTING_0.9.0.md).
- Offline Minecraft identities cannot be proven; first-come on a server (docs/COSMETICS_0.9.0.md).
- No payment provider is integrated.
