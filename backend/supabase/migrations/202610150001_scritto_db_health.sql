-- Minimal database connectivity probe.
-- This function returns no application data and is safe to execute with the
-- publishable key. Keep future data access protected by RLS and explicit grants.
create or replace function public.scritto_db_health()
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$
  select jsonb_build_object('status', 'ok');
$$;

revoke all on function public.scritto_db_health() from public;
grant execute on function public.scritto_db_health() to anon, authenticated;
