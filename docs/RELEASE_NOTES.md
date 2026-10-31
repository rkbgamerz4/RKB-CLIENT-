# RKB CLIENT 0.8.0

**Minecraft:** 1.21.11  |  **Loader:** Fabric 0.18.2+  |  **Requires:** Fabric API  |  **Java:** 21

## Name Tag Badge
- OFF by default. While it is off nothing is drawn and nothing is sent to the cloud.
- Master toggle plus a **NAME TAG BADGE** section in the Settings tab: Badge style (Logo / Letter R), Visibility
  (Friends / Everyone), Cloud sync, Sync status. Options that do not apply are hidden while it is off.
- **Friends** shows the badge only for players in your saved friends list (stored by UUID). **Everyone** shows it for
  your friends plus players the cloud lists as RKB users with their badge on (needs Cloud Sync).
- Your own name tag never carries the badge.
- Friends: add by username or UUID (`/rkb friend add <player>` or the Manage screen), remove, list, duplicate and
  invalid-input handling. Usernames are resolved to UUIDs (online players first, then Mojang).
- Old 0.7 friend names are converted to UUIDs the first time the badge is on.

## Cloud (Supabase)
- Sign in with `/rkb cloud login <email> <password>`; `/rkb cloud link` proves you own the Minecraft account
  (Mojang join handshake checked by the server). One Minecraft account can belong to one RKB account.
- Settings and friends sync in the background (never per frame, rate limited). Newer timestamp wins; ties keep the
  server copy; the cloud never switches the badge on for you.
- Works offline: local settings and friends keep working and sync later.
- `/rkb cloud cosmetics` caches the metadata of your existing cosmetics-catalog function. Nothing is equipped.
- Database migration, RLS policies and the `rkb-badges` Edge Function are in `supabase/` (not applied for you).

## Other
- New `visibleWhen` rule for settings, so menus can hide controls that do not apply.
- JUnit tests for the pure logic (UUID parsing, sync rule, rate limiter, catalog parsing).

## Known limits
- Not compiled or run in-game by the author; see docs/TESTING_0.8.0.md.
- Needs your Supabase publishable key in `config/rkbclient-cloud.json` and the backend deployed (supabase/README.md).
- Offline / guest Minecraft accounts cannot be linked.
- If you already had the badge switched on in 0.7 it stays on (you enabled it); new installs start with it off.
