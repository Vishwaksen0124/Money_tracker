-- 0001_init: extensions, helpers, and the RLS guard.
-- Applied first. Subsequent migrations assume pgcrypto and a working
-- `assert_rls_enabled()` helper.

create extension if not exists pgcrypto;

-- Idiomatic helper: every migration calls this for each table it creates,
-- and rls_lint() walks pg_class to fail if any user table is missing RLS.
create or replace function assert_rls_enabled(p_table regclass)
returns void language plpgsql as $$
begin
    if not (select relrowsecurity from pg_class where oid = p_table) then
        raise exception 'Hardening §21.4: RLS not enabled on %', p_table;
    end if;
    if not (select relforcerowsecurity from pg_class where oid = p_table) then
        raise exception 'Hardening §21.4: RLS not forced on % (table owner bypasses otherwise)', p_table;
    end if;
end $$;

-- Used by CI to assert that no user table escapes RLS coverage.
create or replace function rls_lint()
returns table(missing regclass) language sql as $$
    select c.oid::regclass
    from pg_class c
    join pg_namespace n on n.oid = c.relnamespace
    where c.relkind = 'r'
      and n.nspname = 'public'
      and (not c.relrowsecurity or not c.relforcerowsecurity);
$$;
