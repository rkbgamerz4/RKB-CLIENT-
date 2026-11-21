# RKB CLIENT - Supabase backend (0.8.0 badges, 0.9.0 account cosmetics)

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


---

# 0.9.0 - account-based cosmetics

Nothing here has been applied for you either.

| Item | Purpose |
|---|---|
| `rkb_accounts` | one row per Supabase Auth user; `rkb_id` such as `RKB_1234` is the player-facing account id |
| `rkb_cosmetics` | the catalog (cape / badge, price, claimable, asset path + SHA-256, badge key) |
| `rkb_inventory` | what an **account** owns (`unique(account_id, cosmetic_id)`) |
| `rkb_equipped_cosmetics` | one item per slot; a composite foreign key makes "equip something you do not own" impossible in the database |
| `rkb_purchase_history` | claims, redeems, pending/completed purchases, with a unique provider reference for idempotency |
| `rkb_redeem_codes` | single-use codes, stored only as SHA-256 hashes |
| `rkb_presence` | display only: which Minecraft identity on which server shows which account's equipped items. Never used for ownership |
| `rkb-cosmetics` function | thin authenticated front for the SQL functions |

The existing tables (`cosmetics`, `cosmetic_ownerships`, `equipped_cosmetics`, `cosmetic_purchases`, ...) and
`cosmetics-catalog` are **not touched and no data is imported**: their columns were not available. Run
`supabase/inspect_cosmetics_schema.sql` and send the output if you want an import written.

## Steps
1. Run `supabase/inspect_cosmetics_schema.sql` and keep the output.
2. Apply the 0.8.0 migration first if you have not (the presence check reuses `rkb_link_uuid`; without it every identity is simply "unverified").
3. Apply `supabase/migrations/20261031000000_rkb_cosmetics.sql`. It also creates the public-read Storage bucket `rkb-cosmetics`.
4. `supabase functions deploy rkb-cosmetics --no-verify-jwt` (same reason as `rkb-badges`: the function verifies the user token itself).
5. Insert catalog rows: see `supabase/seed_example_cosmetics.sql` (also shows how to grant items, create codes and fulfil purchases).
6. Upload cape PNGs to the bucket and store each file's SHA-256 in `asset_sha256`.

## How paid items work
There is no payment provider wired in (none was specified). "Buy" in the game only creates a **pending** history row with a
reference. Nothing is granted until you complete it from the service role: `rkb_fulfil_by_reference(reference, provider,
provider_ref, amount_cents)` checks the amount against the catalog price and is idempotent per provider reference. A
payment webhook function can call the same SQL function after the provider confirms the payment.

## Security notes
- The account is the verified JWT subject. The request never carries an account id, price, owner or slot as a fact.
- Clients can only SELECT their own inventory, equipment and history (RLS). All writes go through functions that only the service role can execute.
- Redeem: unknown, used and expired codes return the same answer; a code is not consumed if you already own the item; 10 attempts per minute per account (best effort, per function instance).
- Minecraft session tokens never reach Supabase. Presence stores a Minecraft UUID, name and server address you chose to share.

---

## Skin sets and the ten official capes (same 0.9.0 release)

| Item | Purpose |
|---|---|
| `rkb_skin_sets` | named 64x64/64x32 PNG sets (classic/slim), one row per verified Minecraft UUID's RKB account |
| `rkb_equipped_skin` | the one skin set other verified RKB CLIENT players see |
| `rkb_skin_*` RPCs | list/upload/rename/delete/equip/unequip; every write rechecks the 0.8.0 Mojang link |
| updated `rkb_lookup_looks` | adds a `skin` object only when the presence row is `verified` |
| cape catalog seed | the exact ten bundled capes; **no inventory rows are granted** |

Steps: apply `20261029000000_rkb_badges.sql` and `20261031000000_rkb_cosmetics.sql` first, then
`20261121000000_rkb_skins_and_cape_collection.sql`. Redeploy `rkb-cosmetics` afterwards. Upload the ten PNGs from
`src/main/resources/assets/rkbclient/textures/cosmetics/` to the `rkb-cosmetics` bucket at the `asset_path` values in
the migration (the mod also ships them, so it can show them before the bucket is populated). The bucket stays public-read
with no client write policy: the Edge Function validates the PNG signature, IHDR dimensions and 256 KB limit, computes
the SHA-256 and uploads to `skins/<sha>.png` with the service role.

Security: the Edge Function derives the account from the Auth JWT and requires `rkb_link_uuid(jwt_subject) == mc_uuid`
for every skin action; the SQL functions check the same link and ownership. An offline or unlinked Minecraft name is
local-only and cannot read or write another account's skins. The new tables have RLS: clients can SELECT only their own
rows; all writes are service-role functions. Nothing above has been applied to the live project from this repository.
