-- RKB CLIENT 0.9.0: run in the Supabase SQL editor BEFORE the migration and keep the output.
-- Changes nothing. The new rkb_* tables do not touch your existing cosmetics tables.

select table_name
from information_schema.tables
where table_schema = 'public'
  and (table_name like 'cosmetic%' or table_name like 'rkb_%' or table_name like 'player_badge%' or table_name = 'minecraft_account_links')
order by table_name;

-- columns of the existing cosmetics tables (to plan a data import)
select table_name, column_name, data_type, is_nullable
from information_schema.columns
where table_schema = 'public'
  and table_name in ('cosmetics', 'cosmetic_ownerships', 'equipped_cosmetics', 'cosmetic_purchases', 'cosmetic_admins', 'cosmetic_audit_log')
order by table_name, ordinal_position;

-- row counts, so you know what an import would move
select 'cosmetics' as t, count(*) from public.cosmetics
union all select 'cosmetic_ownerships', count(*) from public.cosmetic_ownerships
union all select 'equipped_cosmetics', count(*) from public.equipped_cosmetics
union all select 'cosmetic_purchases', count(*) from public.cosmetic_purchases;
