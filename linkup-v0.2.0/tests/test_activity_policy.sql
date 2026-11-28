\set ON_ERROR_STOP on
begin;
create role anon;
create role authenticated;
create schema auth;
create table auth.users (id uuid primary key);
create function auth.uid() returns uuid language sql stable as
$$ select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
grant usage on schema auth to anon, authenticated;
create publication supabase_realtime;
\ir ../schema.sql

insert into auth.users values ('00000000-0000-0000-0000-000000000001'),
                             ('00000000-0000-0000-0000-000000000002');
-- Recreate the old upload grant and policy before testing the migration.
grant insert on public.linkup_activity to authenticated;
create policy activity_insert on public.linkup_activity for insert to authenticated
    with check (auth.uid()=user_id);
set local role authenticated;
set local request.jwt.claim.sub='00000000-0000-0000-0000-000000000001';
insert into public.linkup_activity (id, user_id, device_id, device_name, platform, kind, start_ms, end_ms)
    values ('10000000-0000-0000-0000-000000000001', auth.uid(),
            '20000000-0000-0000-0000-000000000001', 'fixture', 'android', 'app', 1, 2);
reset role;
\ir ../schema.sql

set local role authenticated;
do $$
begin
    if (select count(*) from public.linkup_activity) <> 1 then
        raise exception 'Owner must retain access to previously stored Activity';
    end if;
    begin
        insert into public.linkup_activity (id, user_id, device_id, device_name, platform, kind, start_ms, end_ms)
            values ('10000000-0000-0000-0000-000000000002', auth.uid(),
                    '20000000-0000-0000-0000-000000000001', 'fixture', 'android', 'app', 3, 4);
        raise exception 'Authenticated Activity uploads are still allowed';
    exception when insufficient_privilege then
        null;
    end;
end $$;

set local request.jwt.claim.sub='00000000-0000-0000-0000-000000000002';
do $$
begin
    if exists (select 1 from public.linkup_activity) then
        raise exception 'Another account can read the Activity history';
    end if;
end $$;
set local request.jwt.claim.sub='00000000-0000-0000-0000-000000000001';
delete from public.linkup_activity;
reset role;
do $$
begin
    if exists (select 1 from public.linkup_activity) then
        raise exception 'Owner could not remove their Activity history';
    end if;
end $$;
rollback;
