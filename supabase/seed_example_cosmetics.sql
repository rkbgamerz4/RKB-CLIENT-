-- EXAMPLE catalog rows for RKB CLIENT 0.9.0. Edit before running; nothing here runs automatically.

-- A free badge players can claim themselves (uses a glyph shipped in the mod: rkb_logo, rkb_gold, rkb_violet, rkb_red)
insert into public.rkb_cosmetics (id, name, description, type, rarity, price_cents, currency, claimable, badge_key)
values ('badge_rkb_logo', 'RKB Logo Badge', 'The RKB logo before your name.', 'badge', 'common', 0, 'USD', true, 'rkb_logo')
on conflict (id) do nothing;

-- A paid badge. It cannot be claimed; "Buy" only creates a pending reference. You grant it after you got the money.
insert into public.rkb_cosmetics (id, name, description, type, rarity, price_cents, currency, claimable, badge_key, purchase_info)
values ('badge_rkb_gold', 'Gold RKB Badge', 'A gold RKB logo before your name.', 'badge', 'rare', 299, 'USD', false, 'rkb_gold',
        'Pay in the RKB shop and quote the reference below.')
on conflict (id) do nothing;

-- A cape. 1) upload capes/blue_01.png (64x32 or 128x64 ...) to the "rkb-cosmetics" Storage bucket
--         2) put its SHA-256 below:  sha256sum blue_01.png   (the game refuses an image whose hash does not match)
-- insert into public.rkb_cosmetics (id, name, description, type, rarity, price_cents, currency, claimable, asset_path, asset_sha256)
-- values ('cape_blue_01', 'Blue Cape', 'A blue cape.', 'cape', 'common', 0, 'USD', true,
--         'capes/blue_01.png', '<64 hex characters from sha256sum>');

-- ---- operating the shop (SQL editor / service role only) -------------------------------------------------
-- Find an account:           select a.rkb_id, u.email from public.rkb_accounts a join auth.users u on u.id = a.id;
-- Give an item (giveaway):   select public.rkb_admin_grant('<account uuid>', 'badge_rkb_gold', 'giveaway');
-- 10 redeem codes:           select * from public.rkb_admin_create_codes('badge_rkb_gold', 10, now() + interval '90 days');
-- Complete a paid purchase after the money arrived (price is checked against the catalog):
--                            select public.rkb_fulfil_by_reference('RKB-AB12CD34', 'manual', 'receipt-0001', 299);
-- Pending purchases:         select * from public.rkb_purchase_history where status = 'pending';
