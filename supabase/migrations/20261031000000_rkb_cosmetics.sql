-- RKB CLIENT 0.9.0 - account-based cosmetics and inventory
--
-- OWNERSHIP RULE: every cosmetic belongs to an RKB account (auth.users.id, shown to players as RKB_1234).
-- Nothing in this file uses a Minecraft username, Minecraft UUID or launcher profile as an ownership key.
--
-- New tables:  rkb_accounts, rkb_cosmetics, rkb_inventory, rkb_equipped_cosmetics, rkb_purchase_history
-- Also needed: rkb_redeem_codes (single-use codes, stored only as hashes) and rkb_presence
--              (display-only link "this Minecraft player on this server is shown with this RKB account's
--              equipped cosmetics"; it is NOT ownership and is documented in docs/COSMETICS_0.9.0.md).
-- Existing cosmetics/cosmetic_* tables, minecraft_account_links and the 0.8.0 badge tables are NOT modified.
--
-- All writes happen through the SECURITY DEFINER functions below, which only the service role (the Edge Function)
-- can execute. Signed-in clients can SELECT only their own rows.

begin;

-- 1. Accounts ------------------------------------------------------------------------------------------
create table if not exists public.rkb_accounts (
  id         uuid primary key references auth.users (id) on delete cascade,
  rkb_id     text not null unique check (rkb_id ~ '^RKB_[0-9]{4,12}$'),
  created_at timestamptz not null default now()
);

create or replace function public.rkb_make_id() returns text
language plpgsql volatile security definer set search_path = public as $$
declare
  digits int := 4;
  attempt int := 0;
  candidate text;
begin
  loop
    candidate := 'RKB_' || lpad((floor(random() * power(10, digits)))::bigint::text, digits, '0');
    exit when not exists (select 1 from public.rkb_accounts where rkb_id = candidate);
    attempt := attempt + 1;
    if attempt % 5 = 0 then
      digits := least(digits + 1, 12);   -- the space is filling up: use longer ids
    end if;
  end loop;
  return candidate;
end $$;

create or replace function public.rkb_ensure_account(p_user uuid) returns text
language plpgsql security definer set search_path = public as $$
declare
  v_id text;
begin
  select rkb_id into v_id from public.rkb_accounts where id = p_user;
  if v_id is null then
    insert into public.rkb_accounts (id, rkb_id) values (p_user, public.rkb_make_id())
    on conflict (id) do nothing;
    select rkb_id into v_id from public.rkb_accounts where id = p_user;
  end if;
  return v_id;
end $$;

-- every new Auth user gets an RKB account automatically
create or replace function public.rkb_on_new_user() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  perform public.rkb_ensure_account(new.id);
  return new;
end $$;

drop trigger if exists rkb_on_new_user on auth.users;
create trigger rkb_on_new_user after insert on auth.users
  for each row execute function public.rkb_on_new_user();

-- accounts that already exist (kept; their ids are generated now)
do $$
declare r record;
begin
  for r in select id from auth.users u where not exists (select 1 from public.rkb_accounts a where a.id = u.id) loop
    perform public.rkb_ensure_account(r.id);
  end loop;
end $$;

-- 2. Catalog -------------------------------------------------------------------------------------------
create table if not exists public.rkb_cosmetics (
  id              text primary key check (id ~ '^[a-z0-9_]{2,40}$'),
  name            text not null,
  description     text not null default '',
  type            text not null check (type in ('cape', 'badge')),   -- also the equip slot; add types here later
  rarity          text not null default 'common' check (rarity in ('common', 'rare', 'epic', 'legendary')),
  price_cents     integer not null default 0 check (price_cents >= 0),
  currency        text not null default 'USD',
  claimable       boolean not null default false,                    -- free items players may claim themselves
  purchase_info   text not null default '',                          -- how to pay for a paid item (shown to the player)
  asset_path      text check (asset_path is null or asset_path ~ '^[a-z0-9_/-]{1,100}\.png$'),
  asset_sha256    text check (asset_sha256 is null or asset_sha256 ~ '^[0-9a-f]{64}$'),
  badge_key       text check (badge_key is null or badge_key ~ '^[a-z0-9_]{2,30}$'),
  active          boolean not null default true,
  available_from  timestamptz,
  available_until timestamptz,
  created_at      timestamptz not null default now(),
  constraint rkb_cape_has_asset check (type <> 'cape' or (asset_path is not null and asset_sha256 is not null)),
  constraint rkb_badge_has_key  check (type <> 'badge' or badge_key is not null)
);

-- 3. Inventory (what an account owns) -------------------------------------------------------------------
create table if not exists public.rkb_inventory (
  id          uuid primary key default gen_random_uuid(),
  account_id  uuid not null references public.rkb_accounts (id) on delete cascade,
  cosmetic_id text not null references public.rkb_cosmetics (id),
  source      text not null check (source in ('claim', 'redeem', 'purchase', 'admin')),
  acquired_at timestamptz not null default now(),
  constraint rkb_inventory_unique unique (account_id, cosmetic_id)
);
create index if not exists rkb_inventory_account_idx on public.rkb_inventory (account_id);

-- 4. Equipped (one item per slot). The composite foreign key means an account can only equip what it owns,
--    enforced by the database itself, not just by application code.
create table if not exists public.rkb_equipped_cosmetics (
  account_id  uuid not null,
  slot        text not null check (slot in ('cape', 'badge')),
  cosmetic_id text not null,
  equipped_at timestamptz not null default now(),
  primary key (account_id, slot),
  constraint rkb_equipped_owned foreign key (account_id, cosmetic_id)
    references public.rkb_inventory (account_id, cosmetic_id) on delete cascade
);

-- 5. Purchase history ------------------------------------------------------------------------------------
create table if not exists public.rkb_purchase_history (
  id           uuid primary key default gen_random_uuid(),
  account_id   uuid not null references public.rkb_accounts (id) on delete cascade,
  cosmetic_id  text not null references public.rkb_cosmetics (id),
  kind         text not null check (kind in ('claim', 'redeem', 'purchase', 'admin')),
  status       text not null check (status in ('pending', 'completed', 'cancelled', 'refunded')),
  amount_cents integer not null default 0 check (amount_cents >= 0),
  currency     text not null default 'USD',
  reference    text unique,                    -- RKB-XXXXXXXX shown to the player for a pending purchase
  provider     text,
  provider_ref text,
  note         text,
  created_at   timestamptz not null default now(),
  completed_at timestamptz
);
create unique index if not exists rkb_history_provider_ref_idx
  on public.rkb_purchase_history (provider, provider_ref) where provider_ref is not null;
create index if not exists rkb_history_account_idx on public.rkb_purchase_history (account_id, created_at desc);

-- 6. Redeem codes (stored hashed; the plaintext exists only when you create it) --------------------------
create table if not exists public.rkb_redeem_codes (
  code_hash   text primary key check (code_hash ~ '^[0-9a-f]{64}$'),
  cosmetic_id text not null references public.rkb_cosmetics (id),
  created_at  timestamptz not null default now(),
  expires_at  timestamptz,
  redeemed_by uuid references public.rkb_accounts (id),
  redeemed_at timestamptz
);

-- 7. Presence: which Minecraft player (on which server) shows which RKB account's equipped cosmetics ------
-- Display only. Ownership never reads this table. 'verified' means the Minecraft UUID was proven through the
-- Mojang join handshake of the 0.8.0 account link; offline-mode identities cannot be proven and are first-come.
create table if not exists public.rkb_presence (
  id         uuid primary key default gen_random_uuid(),
  account_id uuid not null references public.rkb_accounts (id) on delete cascade,
  mc_uuid    uuid not null,
  mc_name    text not null check (mc_name ~ '^[A-Za-z0-9_]{1,16}$'),
  server_key text not null check (server_key ~ '^[a-z0-9._:\[\]-]{1,100}$'),
  verified   boolean not null default false,
  first_seen timestamptz not null default now(),
  last_seen  timestamptz not null default now(),
  constraint rkb_presence_identity unique (server_key, mc_uuid)
);
create index if not exists rkb_presence_account_idx on public.rkb_presence (account_id);

-- 8. Server-side logic (service role only) ---------------------------------------------------------------

-- free claim: only items flagged claimable with price 0, inside their availability window
create or replace function public.rkb_claim(p_account uuid, p_cosmetic text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare c public.rkb_cosmetics%rowtype; v_inv uuid;
begin
  perform public.rkb_ensure_account(p_account);
  select * into c from public.rkb_cosmetics where id = p_cosmetic and active;
  if not found then return jsonb_build_object('ok', false, 'error', 'not_found'); end if;
  if (c.available_from is not null and c.available_from > now()) or (c.available_until is not null and c.available_until < now()) then
    return jsonb_build_object('ok', false, 'error', 'not_available');
  end if;
  if not c.claimable or c.price_cents > 0 then
    return jsonb_build_object('ok', false, 'error', 'not_claimable');
  end if;
  insert into public.rkb_inventory (account_id, cosmetic_id, source) values (p_account, c.id, 'claim')
    on conflict (account_id, cosmetic_id) do nothing returning id into v_inv;
  if v_inv is null then return jsonb_build_object('ok', false, 'error', 'already_owned'); end if;
  insert into public.rkb_purchase_history (account_id, cosmetic_id, kind, status, amount_cents, currency, completed_at)
    values (p_account, c.id, 'claim', 'completed', 0, c.currency, now());
  return jsonb_build_object('ok', true, 'cosmetic_id', c.id);
end $$;

-- single-use code: validated and consumed atomically; unknown, used and expired codes look identical
create or replace function public.rkb_redeem(p_account uuid, p_code text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_hash text := encode(sha256(convert_to(upper(regexp_replace(coalesce(p_code, ''), '[^A-Za-z0-9]', '', 'g')), 'UTF8')), 'hex');
  v_cos text; c public.rkb_cosmetics%rowtype; v_used text;
begin
  perform public.rkb_ensure_account(p_account);
  select cosmetic_id into v_cos from public.rkb_redeem_codes
    where code_hash = v_hash and redeemed_by is null and (expires_at is null or expires_at > now());
  if v_cos is null then return jsonb_build_object('ok', false, 'error', 'invalid_or_used_code'); end if;
  select * into c from public.rkb_cosmetics where id = v_cos and active;
  if not found then return jsonb_build_object('ok', false, 'error', 'invalid_or_used_code'); end if;
  if exists (select 1 from public.rkb_inventory where account_id = p_account and cosmetic_id = v_cos) then
    return jsonb_build_object('ok', false, 'error', 'already_owned');      -- the code is NOT consumed
  end if;
  update public.rkb_redeem_codes set redeemed_by = p_account, redeemed_at = now()
    where code_hash = v_hash and redeemed_by is null and (expires_at is null or expires_at > now())
    returning cosmetic_id into v_used;
  if v_used is null then return jsonb_build_object('ok', false, 'error', 'invalid_or_used_code'); end if;   -- lost a race
  insert into public.rkb_inventory (account_id, cosmetic_id, source) values (p_account, v_cos, 'redeem')
    on conflict (account_id, cosmetic_id) do nothing;
  insert into public.rkb_purchase_history (account_id, cosmetic_id, kind, status, amount_cents, currency, completed_at)
    values (p_account, v_cos, 'redeem', 'completed', 0, c.currency, now());
  return jsonb_build_object('ok', true, 'cosmetic_id', v_cos);
end $$;

-- paid items: only creates a PENDING record with a reference. Nothing is granted here.
create or replace function public.rkb_purchase_start(p_account uuid, p_cosmetic text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare c public.rkb_cosmetics%rowtype; h public.rkb_purchase_history%rowtype; v_ref text;
begin
  perform public.rkb_ensure_account(p_account);
  select * into c from public.rkb_cosmetics where id = p_cosmetic and active;
  if not found then return jsonb_build_object('ok', false, 'error', 'not_found'); end if;
  if (c.available_from is not null and c.available_from > now()) or (c.available_until is not null and c.available_until < now()) then
    return jsonb_build_object('ok', false, 'error', 'not_available');
  end if;
  if c.price_cents = 0 then return jsonb_build_object('ok', false, 'error', 'use_claim'); end if;
  if exists (select 1 from public.rkb_inventory where account_id = p_account and cosmetic_id = c.id) then
    return jsonb_build_object('ok', false, 'error', 'already_owned');
  end if;
  select * into h from public.rkb_purchase_history
    where account_id = p_account and cosmetic_id = c.id and kind = 'purchase' and status = 'pending' limit 1;
  if not found then
    v_ref := 'RKB-' || upper(substr(md5(random()::text || clock_timestamp()::text || p_account::text), 1, 8));
    insert into public.rkb_purchase_history (account_id, cosmetic_id, kind, status, amount_cents, currency, reference)
      values (p_account, c.id, 'purchase', 'pending', c.price_cents, c.currency, v_ref) returning * into h;
  end if;
  return jsonb_build_object('ok', true, 'reference', h.reference, 'price_cents', c.price_cents,
                            'currency', c.currency, 'purchase_info', c.purchase_info);
end $$;

-- called ONLY by the service role: an admin from the SQL editor, or a payment webhook after the provider confirmed
-- the payment. Idempotent per (provider, provider_ref). The price is checked here, never taken from the client.
create or replace function public.rkb_fulfil_by_reference(p_reference text, p_provider text, p_provider_ref text, p_amount_cents integer)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare h public.rkb_purchase_history%rowtype; c public.rkb_cosmetics%rowtype;
begin
  if p_provider_ref is not null and exists (select 1 from public.rkb_purchase_history where provider = p_provider and provider_ref = p_provider_ref) then
    return jsonb_build_object('ok', true, 'duplicate', true);
  end if;
  select * into h from public.rkb_purchase_history where reference = p_reference and kind = 'purchase' and status = 'pending' for update;
  if not found then return jsonb_build_object('ok', false, 'error', 'unknown_or_completed_reference'); end if;
  select * into c from public.rkb_cosmetics where id = h.cosmetic_id;
  if p_amount_cents < c.price_cents then return jsonb_build_object('ok', false, 'error', 'underpaid'); end if;
  insert into public.rkb_inventory (account_id, cosmetic_id, source) values (h.account_id, h.cosmetic_id, 'purchase')
    on conflict (account_id, cosmetic_id) do nothing;
  update public.rkb_purchase_history set status = 'completed', completed_at = now(), provider = p_provider,
    provider_ref = p_provider_ref, amount_cents = p_amount_cents where id = h.id;
  return jsonb_build_object('ok', true, 'account_id', h.account_id, 'cosmetic_id', h.cosmetic_id);
end $$;

-- admin grant (SQL editor / service role only), e.g. giveaways or support fixes
create or replace function public.rkb_admin_grant(p_account uuid, p_cosmetic text, p_note text default null) returns jsonb
language plpgsql security definer set search_path = public as $$
declare c public.rkb_cosmetics%rowtype; v_inv uuid;
begin
  perform public.rkb_ensure_account(p_account);
  select * into c from public.rkb_cosmetics where id = p_cosmetic;
  if not found then return jsonb_build_object('ok', false, 'error', 'not_found'); end if;
  insert into public.rkb_inventory (account_id, cosmetic_id, source) values (p_account, c.id, 'admin')
    on conflict (account_id, cosmetic_id) do nothing returning id into v_inv;
  if v_inv is null then return jsonb_build_object('ok', false, 'error', 'already_owned'); end if;
  insert into public.rkb_purchase_history (account_id, cosmetic_id, kind, status, amount_cents, currency, note, completed_at)
    values (p_account, c.id, 'admin', 'completed', 0, c.currency, p_note, now());
  return jsonb_build_object('ok', true);
end $$;

-- generate redeem codes (SQL editor only). Returns the plaintext codes once; only hashes are stored.
create or replace function public.rkb_admin_create_codes(p_cosmetic text, p_count integer, p_expires timestamptz default null)
returns setof text
language plpgsql security definer set search_path = public as $$
declare i int; v_code text;
begin
  if not exists (select 1 from public.rkb_cosmetics where id = p_cosmetic) then raise exception 'unknown cosmetic %', p_cosmetic; end if;
  if p_count < 1 or p_count > 500 then raise exception 'count must be 1..500'; end if;
  for i in 1..p_count loop
    v_code := upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 16));      -- 64 bits
    insert into public.rkb_redeem_codes (code_hash, cosmetic_id, expires_at)
      values (encode(sha256(convert_to(v_code, 'UTF8')), 'hex'), p_cosmetic, p_expires);
    return next substr(v_code, 1, 4) || '-' || substr(v_code, 5, 4) || '-' || substr(v_code, 9, 4) || '-' || substr(v_code, 13, 4);
  end loop;
end $$;

-- equip: ownership is checked here AND by the composite foreign key
create or replace function public.rkb_equip(p_account uuid, p_cosmetic text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare c public.rkb_cosmetics%rowtype;
begin
  select * into c from public.rkb_cosmetics where id = p_cosmetic and active;
  if not found then return jsonb_build_object('ok', false, 'error', 'not_found'); end if;
  if not exists (select 1 from public.rkb_inventory where account_id = p_account and cosmetic_id = c.id) then
    return jsonb_build_object('ok', false, 'error', 'not_owned');
  end if;
  insert into public.rkb_equipped_cosmetics (account_id, slot, cosmetic_id) values (p_account, c.type, c.id)
    on conflict (account_id, slot) do update set cosmetic_id = excluded.cosmetic_id, equipped_at = now();
  return jsonb_build_object('ok', true, 'slot', c.type);
end $$;

create or replace function public.rkb_unequip(p_account uuid, p_slot text) returns jsonb
language plpgsql security definer set search_path = public as $$
begin
  delete from public.rkb_equipped_cosmetics where account_id = p_account and slot = p_slot;
  return jsonb_build_object('ok', true);
end $$;

-- presence: see the table comment above for the trust model
create or replace function public.rkb_presence_register(p_account uuid, p_mc_uuid uuid, p_mc_name text, p_server_key text, p_verified boolean)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare e public.rkb_presence%rowtype; v_count int;
begin
  perform public.rkb_ensure_account(p_account);
  select * into e from public.rkb_presence where server_key = p_server_key and mc_uuid = p_mc_uuid for update;
  if found and e.account_id <> p_account then
    if p_verified then
      delete from public.rkb_presence where id = e.id;                       -- a proven identity beats an unproven one
    elsif e.verified then
      return jsonb_build_object('ok', false, 'error', 'identity_verified_elsewhere');
    elsif e.last_seen > now() - interval '30 days' then
      return jsonb_build_object('ok', false, 'error', 'identity_taken');
    else
      delete from public.rkb_presence where id = e.id;                       -- stale claim: free to take over
    end if;
    e := null;
  end if;
  select count(*) into v_count from public.rkb_presence
    where account_id = p_account and last_seen > now() - interval '30 days' and not (server_key = p_server_key and mc_uuid = p_mc_uuid);
  if v_count >= 10 then return jsonb_build_object('ok', false, 'error', 'too_many_identities'); end if;
  insert into public.rkb_presence (account_id, mc_uuid, mc_name, server_key, verified)
    values (p_account, p_mc_uuid, p_mc_name, p_server_key, p_verified)
    on conflict (server_key, mc_uuid) do update set mc_name = excluded.mc_name, verified = excluded.verified, last_seen = now();
  return jsonb_build_object('ok', true, 'verified', p_verified);
end $$;

-- what viewers see: equipped cape/badge of the accounts behind these players on this server
create or replace function public.rkb_lookup_looks(p_server_key text, p_uuids uuid[]) returns jsonb
language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(jsonb_build_object(
      'uuid', p.mc_uuid,
      'verified', p.verified,
      'cape', (select jsonb_build_object('id', c.id, 'path', c.asset_path, 'sha256', c.asset_sha256)
               from public.rkb_equipped_cosmetics e join public.rkb_cosmetics c on c.id = e.cosmetic_id
               where e.account_id = p.account_id and e.slot = 'cape' and c.active),
      'badge', (select jsonb_build_object('id', c.id, 'key', c.badge_key)
                from public.rkb_equipped_cosmetics e join public.rkb_cosmetics c on c.id = e.cosmetic_id
                where e.account_id = p.account_id and e.slot = 'badge' and c.active)
  )), '[]'::jsonb)
  from public.rkb_presence p
  where p.server_key = p_server_key and p.mc_uuid = any (p_uuids) and p.last_seen > now() - interval '30 days'
$$;

-- only the service role may run any of these
do $$
declare f text;
begin
  foreach f in array array[
    'rkb_make_id()', 'rkb_ensure_account(uuid)', 'rkb_on_new_user()', 'rkb_claim(uuid,text)', 'rkb_redeem(uuid,text)',
    'rkb_purchase_start(uuid,text)', 'rkb_fulfil_by_reference(text,text,text,integer)', 'rkb_admin_grant(uuid,text,text)',
    'rkb_admin_create_codes(text,integer,timestamptz)', 'rkb_equip(uuid,text)', 'rkb_unequip(uuid,text)',
    'rkb_presence_register(uuid,text,text,text,boolean)', 'rkb_lookup_looks(text,uuid[])']
  loop
    execute format('revoke all on function public.%s from public, anon, authenticated', f);
    execute format('grant execute on function public.%s to service_role', f);
  end loop;
end $$;

-- 9. Row level security ------------------------------------------------------------------------------------
alter table public.rkb_accounts           enable row level security;
alter table public.rkb_cosmetics          enable row level security;
alter table public.rkb_inventory          enable row level security;
alter table public.rkb_equipped_cosmetics enable row level security;
alter table public.rkb_purchase_history   enable row level security;
alter table public.rkb_redeem_codes       enable row level security;   -- no policy: unreachable from clients
alter table public.rkb_presence           enable row level security;   -- no policy: unreachable from clients

revoke all on public.rkb_accounts, public.rkb_cosmetics, public.rkb_inventory, public.rkb_equipped_cosmetics,
  public.rkb_purchase_history, public.rkb_redeem_codes, public.rkb_presence from anon, authenticated;
grant select on public.rkb_accounts, public.rkb_cosmetics, public.rkb_inventory, public.rkb_equipped_cosmetics,
  public.rkb_purchase_history to authenticated;

drop policy if exists "rkb accounts: read own" on public.rkb_accounts;
create policy "rkb accounts: read own" on public.rkb_accounts for select to authenticated using (id = auth.uid());

drop policy if exists "rkb cosmetics: read active catalog" on public.rkb_cosmetics;
create policy "rkb cosmetics: read active catalog" on public.rkb_cosmetics for select to authenticated using (active);

drop policy if exists "rkb inventory: read own" on public.rkb_inventory;
create policy "rkb inventory: read own" on public.rkb_inventory for select to authenticated using (account_id = auth.uid());

drop policy if exists "rkb equipped: read own" on public.rkb_equipped_cosmetics;
create policy "rkb equipped: read own" on public.rkb_equipped_cosmetics for select to authenticated using (account_id = auth.uid());

drop policy if exists "rkb history: read own" on public.rkb_purchase_history;
create policy "rkb history: read own" on public.rkb_purchase_history for select to authenticated using (account_id = auth.uid());

-- 10. Cape images: public READ bucket, no write policies (upload with the dashboard or the service role) ---------
insert into storage.buckets (id, name, public) values ('rkb-cosmetics', 'rkb-cosmetics', true)
  on conflict (id) do nothing;

commit;
