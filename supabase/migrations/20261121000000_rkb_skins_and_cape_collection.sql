-- RKB CLIENT 0.9.0 - skin sets, equipped skin and the ten official capes
--
-- Prerequisites: 20261029000000_rkb_badges.sql (rkb_link_uuid) and 20261031000000_rkb_cosmetics.sql.
-- The 0.8.0 Mojang-verified link is the ONLY way a Minecraft UUID may read or write cloud skins. Nothing here
-- trusts a Minecraft name, and no offline/unlinked identity can claim another player's sets.
--
-- New tables: rkb_skin_sets, rkb_equipped_skin
-- Updated:    rkb_lookup_looks (remote skins are only returned when presence.verified = true)
-- Seeded:     the ten bundled capes in rkb_cosmetics (catalog rows only; NO inventory grants)

begin;

-- 1. Skin sets -------------------------------------------------------------------------------------------
create table if not exists public.rkb_skin_sets (
  id           uuid primary key default gen_random_uuid(),
  account_id   uuid not null references public.rkb_accounts (id) on delete cascade,
  mc_uuid      uuid not null,
  name         text not null check (char_length(name) between 1 and 32 and name ~ '^[[:alnum:] _.-]+$'),
  model        text not null check (model in ('classic', 'slim')),
  asset_path   text not null check (asset_path ~ '^[a-z0-9_/-]{1,100}\.png$' and asset_path not like '%..%'),
  asset_sha256 text not null check (asset_sha256 ~ '^[0-9a-f]{64}$'),
  size_bytes   integer not null check (size_bytes > 0 and size_bytes <= 262144),
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now(),
  constraint rkb_skin_sets_name_unique unique (account_id, name)
);
create index if not exists rkb_skin_sets_account_idx on public.rkb_skin_sets (account_id, updated_at desc);
create unique index if not exists rkb_skin_sets_name_ci_idx on public.rkb_skin_sets (account_id, lower(name));

create table if not exists public.rkb_equipped_skin (
  account_id  uuid primary key references public.rkb_accounts (id) on delete cascade,
  skin_set_id uuid not null references public.rkb_skin_sets (id) on delete cascade,
  equipped_at timestamptz not null default now()
);

-- The link helper is created by the 0.8.0 migration. Fail loudly if it is missing rather than treating everyone
-- as verified.
do $$
begin
  if public.rkb_link_uuid('00000000-0000-0000-0000-000000000000'::uuid) is null then
    raise notice 'rkb_link_uuid exists; no linked identity for the probe UUID (expected)';
  end if;
exception when undefined_function then
  raise exception 'public.rkb_link_uuid(uuid) is missing: apply the 0.8.0 badges migration first';
end $$;

create or replace function public.rkb_skin_verify(p_user uuid, p_mc_uuid uuid) returns void
language plpgsql stable security definer set search_path = public as $$
begin
  if p_user is null or p_mc_uuid is null
     or public.rkb_link_uuid(p_user) is distinct from p_mc_uuid then
    raise exception 'identity_not_verified';
  end if;
end $$;

-- 2. Writes (service role only) --------------------------------------------------------------------------
create or replace function public.rkb_skin_upload(
  p_user uuid, p_mc_uuid uuid, p_name text, p_model text,
  p_path text, p_sha text, p_size integer
) returns jsonb
language plpgsql security definer set search_path = public as $$
declare v_id uuid; v_count int;
begin
  perform public.rkb_skin_verify(p_user, p_mc_uuid);
  perform public.rkb_ensure_account(p_user);
  if not exists (select 1 from public.rkb_skin_sets where account_id = p_user and asset_sha256 = p_sha) then
    select count(*) into v_count from public.rkb_skin_sets where account_id = p_user;
    if v_count >= 20 then return jsonb_build_object('ok', false, 'error', 'set_limit'); end if;
  end if;
  if exists (select 1 from public.rkb_skin_sets where account_id = p_user and lower(name) = lower(p_name)) then
    return jsonb_build_object('ok', false, 'error', 'name_taken');
  end if;
  if p_model not in ('classic', 'slim') or p_size <= 0 or p_size > 262144
     or p_sha !~ '^[0-9a-f]{64}$' or p_path !~ '^[a-z0-9_/-]{1,100}\.png$' or p_path like '%..%' then
    return jsonb_build_object('ok', false, 'error', 'invalid_skin');
  end if;
  insert into public.rkb_skin_sets (account_id, mc_uuid, name, model, asset_path, asset_sha256, size_bytes)
  values (p_user, p_mc_uuid, p_name, p_model, p_path, p_sha, p_size) returning id into v_id;
  return jsonb_build_object('ok', true, 'id', v_id);
exception when unique_violation then
  return jsonb_build_object('ok', false, 'error', 'name_taken');
end $$;

create or replace function public.rkb_skin_rename(p_user uuid, p_id uuid, p_name text) returns jsonb
language plpgsql security definer set search_path = public as $$
begin
  perform public.rkb_ensure_account(p_user);
  update public.rkb_skin_sets set name = p_name, updated_at = now()
    where id = p_id and account_id = p_user;
  if not found then return jsonb_build_object('ok', false, 'error', 'not_found'); end if;
  return jsonb_build_object('ok', true);
exception when unique_violation then
  return jsonb_build_object('ok', false, 'error', 'name_taken');
end $$;

create or replace function public.rkb_skin_delete(p_user uuid, p_id uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
begin
  delete from public.rkb_skin_sets where id = p_id and account_id = p_user;
  return jsonb_build_object('ok', found);
end $$;

create or replace function public.rkb_skin_equip(p_user uuid, p_id uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
declare v_mc uuid;
begin
  perform public.rkb_ensure_account(p_user);
  select mc_uuid into v_mc from public.rkb_skin_sets where id = p_id and account_id = p_user;
  if not found then return jsonb_build_object('ok', false, 'error', 'not_found'); end if;
  perform public.rkb_skin_verify(p_user, v_mc);
  insert into public.rkb_equipped_skin (account_id, skin_set_id) values (p_user, p_id)
    on conflict (account_id) do update set skin_set_id = excluded.skin_set_id, equipped_at = now();
  return jsonb_build_object('ok', true);
end $$;

create or replace function public.rkb_skin_unequip(p_user uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
begin
  delete from public.rkb_equipped_skin where account_id = p_user;
  return jsonb_build_object('ok', true);
end $$;

create or replace function public.rkb_skin_list(p_user uuid, p_mc_uuid uuid) returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare v_sets jsonb; v_equipped jsonb;
begin
  perform public.rkb_skin_verify(p_user, p_mc_uuid);
  select coalesce(jsonb_agg(jsonb_build_object(
      'id', s.id, 'name', s.name, 'model', s.model,
      'asset_path', s.asset_path, 'asset_sha256', s.asset_sha256, 'size_bytes', s.size_bytes
    ) order by s.updated_at desc), '[]'::jsonb)
    into v_sets from public.rkb_skin_sets s where s.account_id = p_user;
  select jsonb_build_object('skin_set_id', e.skin_set_id) into v_equipped
    from public.rkb_equipped_skin e where e.account_id = p_user;
  return jsonb_build_object('ok', true, 'sets', v_sets, 'equipped', coalesce(v_equipped, '{}'::jsonb));
end $$;

-- 3. Remote lookups: a skin is only exposed when the link was verified by Mojang --------------------------
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
                where e.account_id = p.account_id and e.slot = 'badge' and c.active),
      'skin', case when p.verified then
               (select jsonb_build_object('id', s.id, 'path', s.asset_path, 'sha256', s.asset_sha256, 'model', s.model)
                from public.rkb_equipped_skin e join public.rkb_skin_sets s on s.id = e.skin_set_id
                where e.account_id = p.account_id) end
  )), '[]'::jsonb)
  from public.rkb_presence p
  where p.server_key = p_server_key and p.mc_uuid = any (p_uuids) and p.last_seen > now() - interval '30 days'
$$;

do $$
declare f text;
begin
  foreach f in array array[
    'rkb_skin_verify(uuid,uuid)', 'rkb_skin_upload(uuid,uuid,text,text,text,text,integer)',
    'rkb_skin_rename(uuid,uuid,text)', 'rkb_skin_delete(uuid,uuid)', 'rkb_skin_equip(uuid,uuid)',
    'rkb_skin_unequip(uuid)', 'rkb_skin_list(uuid,uuid)', 'rkb_lookup_looks(text,uuid[])']
  loop
    execute format('revoke all on function public.%s from public, anon, authenticated', f);
    execute format('grant execute on function public.%s to service_role', f);
  end loop;
end $$;

alter table public.rkb_skin_sets    enable row level security;
alter table public.rkb_equipped_skin enable row level security;
revoke all on public.rkb_skin_sets, public.rkb_equipped_skin from anon, authenticated;
grant select on public.rkb_skin_sets, public.rkb_equipped_skin to authenticated;
drop policy if exists "rkb skin sets: read own" on public.rkb_skin_sets;
create policy "rkb skin sets: read own" on public.rkb_skin_sets for select to authenticated using (account_id = auth.uid());
drop policy if exists "rkb equipped skin: read own" on public.rkb_equipped_skin;
create policy "rkb equipped skin: read own" on public.rkb_equipped_skin for select to authenticated using (account_id = auth.uid());

-- 4. The ten official capes. These rows are catalog metadata only: no inventory rows are granted, so every player
--    starts with all ten shown as NOT OWNED until you grant or sell them from the service role.
insert into public.rkb_cosmetics (id, name, description, type, rarity, price_cents, currency, claimable, purchase_info, asset_path, asset_sha256, active)
values
  ('cape_bangladesh', 'Bangladesh Cape', 'Deep green with the red sun of Bangladesh.', 'cape', 'common', 0, 'USD', false, '', 'capes/cape_bangladesh.png', 'a0a968022cd244761a003d6e76a58f8738d87a0513b18bff2528cc7856665af3', true),
  ('cape_india', 'India Cape', 'Saffron, white and green with the Ashoka Chakra.', 'cape', 'common', 0, 'USD', false, '', 'capes/cape_india.png', '0fa68e721f325e605aa62ddbe10111b42845b5cae799e3eeaeea60551a589989', true),
  ('cape_usa', 'USA Cape', 'The Stars and Stripes in a clean cape cut.', 'cape', 'common', 0, 'USD', false, '', 'capes/cape_usa.png', 'fbec079eca741235d694a32c3958fad6b5eea6a1366bc7582947260d89ff89c0', true),
  ('cape_pakistan', 'Pakistan Cape', 'Pakistan green, white band, crescent and star.', 'cape', 'common', 0, 'USD', false, '', 'capes/cape_pakistan.png', 'c490b80d68006465e2c80477d77214d830cb1a2277d3a3b4562d7badef0d892c', true),
  ('cape_brazil', 'Brazil Cape', 'Green field, yellow diamond and blue globe.', 'cape', 'common', 0, 'USD', false, '', 'capes/cape_brazil.png', 'b1b7fe48b99a0e8e0813429a368e7f0a3d3f321e8b480c07ad50f6e967361e90', true),
  ('cape_japan', 'Japan Cape', 'White cloth with the red sun.', 'cape', 'common', 0, 'USD', false, '', 'capes/cape_japan.png', 'a2db3cad885323316429da9ebc24c0ae7749b3709121c35b153c23b9f5f23282', true),
  ('cape_uk', 'UK Cape', 'The Union Jack in navy, white and red.', 'cape', 'common', 0, 'USD', false, '', 'capes/cape_uk.png', '0179e34b3f646a2b161632462cd5e426a1131293b8d97f52a3f2051ee3fbc634', true),
  ('cape_nepal', 'Nepal Cape', 'The crimson and blue double-pennant of Nepal.', 'cape', 'common', 0, 'USD', false, '', 'capes/cape_nepal.png', 'ac8557db2082003014259678596fc3a27c9a0159b63d8f1b57fcf2b327103738', true),
  ('cape_rkb_original', 'RKB Original Cape', 'The original RKB navy cape with a cyan R.', 'cape', 'rare', 0, 'USD', false, '', 'capes/cape_rkb_original.png', '7ef892d65ad5451af591730fa43ef5cacaee60b4eef8b27d31102263b7037264', true),
  ('cape_rkb_shadow', 'RKB Shadow Cape', 'A black RKB cape with a restrained blue edge.', 'cape', 'epic', 0, 'USD', false, '', 'capes/cape_rkb_shadow.png', '44480cc0352bdec064474af6544f704d9d1c705ba6cb5931f8ca9c066624e39c', true)
on conflict (id) do update set
  name = excluded.name, description = excluded.description, type = 'cape', rarity = excluded.rarity,
  asset_path = excluded.asset_path, asset_sha256 = excluded.asset_sha256, active = true;

commit;
