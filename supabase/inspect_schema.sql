-- RKB CLIENT 0.8.0: run this FIRST in the Supabase SQL editor and read the result.
-- It changes nothing. The migration adapts to minecraft_account_links, so check that the columns it needs exist.

select table_name
from information_schema.tables
where table_schema = 'public'
order by table_name;

select column_name, data_type, is_nullable
from information_schema.columns
where table_schema = 'public' and table_name = 'minecraft_account_links'
order by ordinal_position;

-- Do the new tables already exist?
select to_regclass('public.player_badge_settings') as player_badge_settings,
       to_regclass('public.player_badge_friends')  as player_badge_friends,
       to_regclass('public.player_badges')         as player_badges;

-- Existing policies on the link table (for your information)
select policyname, cmd, roles, qual
from pg_policies
where schemaname = 'public' and tablename = 'minecraft_account_links';
