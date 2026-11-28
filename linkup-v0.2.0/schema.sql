-- Run once in Supabase SQL Editor. Never put the service_role key in the client.
create table if not exists public.linkup_items (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  kind text not null check (kind in ('text','file')),
  text_content text check (octet_length(text_content) <= 1048576),
  file_name text,
  mime_type text,
  byte_size bigint not null default 0 check (byte_size >= 0),
  drive_id text,
  device_name text not null check (char_length(device_name) <= 60),
  device_id uuid not null,
  created_at timestamptz not null default now(),
  deleted_at timestamptz,
  storage_deleted_at timestamptz,
  check (deleted_at is not null or (kind='text' and text_content is not null and drive_id is null) or (kind='file' and drive_id is not null and file_name is not null))
);
create index if not exists linkup_items_owner_id on public.linkup_items(user_id,id);
alter table public.linkup_items enable row level security;
revoke all on public.linkup_items from anon, authenticated;
grant select, insert on public.linkup_items to authenticated;
grant update (deleted_at,text_content,storage_deleted_at) on public.linkup_items to authenticated;
drop policy if exists linkup_select on public.linkup_items;
create policy linkup_select on public.linkup_items for select to authenticated using (auth.uid()=user_id);
drop policy if exists linkup_insert on public.linkup_items;
create policy linkup_insert on public.linkup_items for insert to authenticated with check (auth.uid()=user_id);
drop policy if exists linkup_update on public.linkup_items;
create policy linkup_update on public.linkup_items for update to authenticated using (auth.uid()=user_id) with check (auth.uid()=user_id);
-- Prevent reactivating a deleted object even from a stale client.
create or replace function public.linkup_guard_tombstone() returns trigger language plpgsql set search_path='' as $$
begin
  if old.deleted_at is not null then new.deleted_at := old.deleted_at; end if;
  if new.deleted_at is not null then new.text_content := null; end if;
  if old.storage_deleted_at is not null then new.storage_deleted_at := old.storage_deleted_at; end if;
  return new;
end; $$;
drop trigger if exists linkup_guard_tombstone on public.linkup_items;
create trigger linkup_guard_tombstone before update on public.linkup_items for each row execute function public.linkup_guard_tombstone();
do $$ begin
  if not exists(select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename='linkup_items') then
    alter publication supabase_realtime add table public.linkup_items;
  end if;
end $$;
create table if not exists public.linkup_drive_accounts (user_id uuid primary key references auth.users(id) on delete cascade, drive_identity text not null);
alter table public.linkup_drive_accounts enable row level security;
revoke all on public.linkup_drive_accounts from anon, authenticated;
grant select,insert on public.linkup_drive_accounts to authenticated;
drop policy if exists linkup_drive_read on public.linkup_drive_accounts;
create policy linkup_drive_read on public.linkup_drive_accounts for select to authenticated using(auth.uid()=user_id);
drop policy if exists linkup_drive_insert on public.linkup_drive_accounts;
create policy linkup_drive_insert on public.linkup_drive_accounts for insert to authenticated with check(auth.uid()=user_id);
-- Preserve existing Activity rows and prevent new plaintext uploads until E2EE.
create table if not exists public.linkup_activity (
 id uuid primary key, user_id uuid not null references auth.users(id) on delete cascade,
 device_id uuid not null, device_name text not null, platform text not null,
 kind text not null check(kind in ('app','unlock','location','screen','idle')),
 app text, label text, start_ms bigint not null, end_ms bigint not null,
 lat double precision check(lat between -90 and 90), lon double precision check(lon between -180 and 180), accuracy double precision,
 check(end_ms>=start_ms), check(kind<>'location' or (lat is not null and lon is not null))
);
create index if not exists linkup_activity_owner_time on public.linkup_activity(user_id,start_ms,end_ms);
alter table public.linkup_activity enable row level security;
revoke all on public.linkup_activity from public,anon,authenticated;
grant select,delete on public.linkup_activity to authenticated;
drop policy if exists activity_select on public.linkup_activity;
create policy activity_select on public.linkup_activity for select to authenticated using(auth.uid()=user_id);
drop policy if exists activity_insert on public.linkup_activity;
drop policy if exists activity_delete on public.linkup_activity;
create policy activity_delete on public.linkup_activity for delete to authenticated using(auth.uid()=user_id);
create table if not exists public.linkup_disk_accounts(user_id uuid not null references auth.users(id) on delete cascade,provider text not null check(provider in ('google','yandex')),drive_identity text not null,primary key(user_id,provider));
alter table public.linkup_disk_accounts enable row level security;
revoke all on public.linkup_disk_accounts from anon,authenticated;
grant select,insert on public.linkup_disk_accounts to authenticated;
drop policy if exists disk_select on public.linkup_disk_accounts;
create policy disk_select on public.linkup_disk_accounts for select to authenticated using(auth.uid()=user_id);
drop policy if exists disk_insert on public.linkup_disk_accounts;
create policy disk_insert on public.linkup_disk_accounts for insert to authenticated with check(auth.uid()=user_id);
insert into public.linkup_disk_accounts(user_id,provider,drive_identity) select user_id,'google',drive_identity from public.linkup_drive_accounts on conflict do nothing;
