-- RKB CLIENT 0.8.0 - name tag badge cloud sync
--
-- Creates (only if missing):
--   public.player_badge_settings   one row per verified Minecraft UUID: badge on/off, style, visibility, timestamps
--   public.player_badge_friends    the friends list each player chose
-- Why not three tables: the brief listed player_badges and player_badge_settings with identical columns.
-- One table is enough: player_badge_settings. Rows with badge_enabled = true are the "RKB users who show a badge"
-- registry, readable only through the rkb-badges Edge Function (lookup action).
--
-- It also reads public.minecraft_account_links WITHOUT changing it. Its column names are detected below, and
-- all access goes through the small functions this migration creates (rkb_link_*). If detection fails the
-- migration stops with a clear message; run supabase/inspect_schema.sql and adapt the candidate lists.

begin;

-- 1. Link-table adapter -----------------------------------------------------------------------------------
do $$
declare
  ucol text;
  mcol text;
  ncol text;
begin
  if to_regclass('public.minecraft_account_links') is null then
    raise exception 'public.minecraft_account_links not found. Run supabase/inspect_schema.sql and adapt this migration.';
  end if;

  select column_name into ucol
  from information_schema.columns
  where table_schema = 'public' and table_name = 'minecraft_account_links'
    and column_name in ('user_id', 'auth_user_id', 'account_id', 'owner_id')
  order by array_position(array['user_id', 'auth_user_id', 'account_id', 'owner_id'], column_name::text)
  limit 1;

  select column_name into mcol
  from information_schema.columns
  where table_schema = 'public' and table_name = 'minecraft_account_links'
    and column_name in ('minecraft_uuid', 'mc_uuid', 'player_uuid', 'minecraft_id', 'uuid')
  order by array_position(array['minecraft_uuid', 'mc_uuid', 'player_uuid', 'minecraft_id', 'uuid'], column_name::text)
  limit 1;

  select column_name into ncol
  from information_schema.columns
  where table_schema = 'public' and table_name = 'minecraft_account_links'
    and column_name in ('minecraft_name', 'mc_name', 'minecraft_username', 'player_name', 'username')
  order by array_position(array['minecraft_name', 'mc_name', 'minecraft_username', 'player_name', 'username'], column_name::text)
  limit 1;

  if ucol is null or mcol is null then
    raise exception 'Could not find the user and Minecraft UUID columns in minecraft_account_links (user=%, uuid=%). Adapt the candidate lists.', ucol, mcol;
  end if;
  raise notice 'minecraft_account_links adapter: user column=%, uuid column=%, name column=%', ucol, mcol, coalesce(ncol, '(none)');

  -- UUID owned by an auth user (service role / Edge Function only)
  execute format($f$
    create or replace function public.rkb_link_uuid(p_user uuid) returns uuid
    language sql stable security definer set search_path = public as $b$
      select (%2$I)::text::uuid from public.minecraft_account_links where %1$I = p_user limit 1
    $b$ $f$, ucol, mcol);

  -- auth user that owns a UUID (to refuse linking one UUID to two accounts)
  execute format($f$
    create or replace function public.rkb_link_owner(p_uuid uuid) returns uuid
    language sql stable security definer set search_path = public as $b$
      select (%1$I)::text::uuid from public.minecraft_account_links where (%2$I)::text = p_uuid::text limit 1
    $b$ $f$, ucol, mcol);

  -- create or update the link after Mojang verified ownership
  if ncol is not null then
    execute format($f$
      create or replace function public.rkb_link_set(p_user uuid, p_uuid uuid, p_name text) returns void
      language plpgsql security definer set search_path = public as $b$
      begin
        update public.minecraft_account_links set %2$I = p_uuid, %3$I = p_name where %1$I = p_user;
        if not found then
          insert into public.minecraft_account_links (%1$I, %2$I, %3$I) values (p_user, p_uuid, p_name);
        end if;
      end $b$ $f$, ucol, mcol, ncol);
  else
    execute format($f$
      create or replace function public.rkb_link_set(p_user uuid, p_uuid uuid, p_name text) returns void
      language plpgsql security definer set search_path = public as $b$
      begin
        update public.minecraft_account_links set %2$I = p_uuid where %1$I = p_user;
        if not found then
          insert into public.minecraft_account_links (%1$I, %2$I) values (p_user, p_uuid);
        end if;
      end $b$ $f$, ucol, mcol);
  end if;

  -- UUIDs linked to the signed-in user (used by the RLS policies below)
  execute format($f$
    create or replace function public.rkb_linked_uuids() returns setof uuid
    language sql stable security definer set search_path = public as $b$
      select (%2$I)::text::uuid from public.minecraft_account_links where %1$I = auth.uid()
    $b$ $f$, ucol, mcol);
end $$;

revoke all on function public.rkb_link_uuid(uuid) from public, anon, authenticated;
revoke all on function public.rkb_link_owner(uuid) from public, anon, authenticated;
revoke all on function public.rkb_link_set(uuid, uuid, text) from public, anon, authenticated;
revoke all on function public.rkb_linked_uuids() from public, anon;
grant execute on function public.rkb_linked_uuids() to authenticated;

-- 2. Tables ---------------------------------------------------------------------------------------------
create table if not exists public.player_badge_settings (
  id                 uuid primary key default gen_random_uuid(),
  player_uuid        uuid not null unique,
  badge_enabled      boolean not null default false,
  badge_style        text not null default 'logo' check (badge_style in ('logo', 'letter')),
  visibility_mode    text not null default 'friends' check (visibility_mode in ('friends', 'everyone')),
  friends_updated_at timestamptz not null default to_timestamp(0),
  updated_at         timestamptz not null default now()
);

create table if not exists public.player_badge_friends (
  id          uuid primary key default gen_random_uuid(),
  owner_uuid  uuid not null,
  friend_uuid uuid not null,
  created_at  timestamptz not null default now(),
  constraint player_badge_friends_unique unique (owner_uuid, friend_uuid),
  constraint player_badge_friends_not_self check (owner_uuid <> friend_uuid)
);
create index if not exists player_badge_friends_owner_idx on public.player_badge_friends (owner_uuid);
create index if not exists player_badge_settings_enabled_idx on public.player_badge_settings (player_uuid) where badge_enabled;

-- 3. Row level security ----------------------------------------------------------------------------------
-- Clients may only READ their own rows. All writes go through the rkb-badges Edge Function, which verifies the
-- JWT and the linked Minecraft UUID, then writes with the service role (which bypasses RLS).
alter table public.player_badge_settings enable row level security;
alter table public.player_badge_friends  enable row level security;

revoke all on public.player_badge_settings from anon, authenticated;
revoke all on public.player_badge_friends  from anon, authenticated;
grant select on public.player_badge_settings to authenticated;
grant select on public.player_badge_friends  to authenticated;

drop policy if exists "badge settings: read own" on public.player_badge_settings;
create policy "badge settings: read own" on public.player_badge_settings
  for select to authenticated
  using (player_uuid in (select public.rkb_linked_uuids()));

drop policy if exists "badge friends: read own" on public.player_badge_friends;
create policy "badge friends: read own" on public.player_badge_friends
  for select to authenticated
  using (owner_uuid in (select public.rkb_linked_uuids()));

commit;
