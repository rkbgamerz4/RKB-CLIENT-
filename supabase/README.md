# RKB CLIENT 0.8.0 - Supabase backend

Project: https://gnficbdfqukyubdeqgjt.supabase.co

Nothing here has been applied for you. It was written without access to your project, so run the steps below yourself.

## What it adds
| Item | Purpose |
|---|---|
| `player_badge_settings` | one row per verified Minecraft UUID: badge on/off, style, visibility, timestamps. Rows with `badge_enabled = true` are the registry of RKB users who show a badge. |
| `player_badge_friends` | each player's friends list |
| `rkb_link_*` SQL functions | the only code that touches `minecraft_account_links`, so its column names live in one place |
| `rkb-badges` Edge Function | link, read, write and look up badges |

`player_badges` from the brief is not created: it had the same columns as `player_badge_settings`. Cosmetics tables
(cosmetics, cosmetic_ownerships, equipped_cosmetics, ...) are not touched.

## Steps
1. **Inspect first.** SQL editor, run `supabase/inspect_schema.sql`. Check that `minecraft_account_links` has a user
   column (e.g. `user_id`) and a Minecraft UUID column (e.g. `minecraft_uuid`). The migration detects common names and
   stops with a clear message if it cannot.
2. **Migrate.** `supabase db push`, or paste `supabase/migrations/20261029000000_rkb_badges.sql` into the SQL editor.
   Read the NOTICE line: it shows which columns were detected.
3. **Secret.** `supabase secrets set RKB_LINK_SECRET=$(openssl rand -hex 32)`
4. **Deploy.** `supabase functions deploy rkb-badges --no-verify-jwt`
   `--no-verify-jwt` is needed when you use the new publishable keys (they are not JWTs). The function still verifies
   the user's Auth token itself and rejects anything else.
5. **Auth.** Enable Email auth (Dashboard > Authentication > Providers). Create the player's account on your site or
   dashboard; the game signs in with `/rkb cloud login <email> <password>`.
6. **Client key.** Put the publishable (or anon) key in `config/rkbclient-cloud.json` as `anonKey`. Never put the
   service_role key anywhere near the game.

## Security notes
- Clients can only SELECT their own rows (RLS through `rkb_linked_uuids()`); every write goes through the function.
- A Minecraft UUID is trusted only after Mojang's session server confirms the player joined with a challenge that this
  function signed for that exact Supabase user (HMAC, 5 minute lifetime). One UUID can be linked to one account.
- The Minecraft access token is used on the player's PC to join the challenge; it is never sent to Supabase.
- The in-memory rate limiter is best effort; add real limits in front of the function if you expect abuse.
- `friends` replacement is delete+insert (not atomic). The client retries, which repairs a partial write.

## Conflict strategy
Settings and friends each have an `updated_at` / `friends_updated_at`. On sync the newer timestamp wins and a tie keeps
the server copy. The client never turns the badge ON locally because the cloud says so: a player must switch it on
on that device.
