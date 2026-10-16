-- Run once in Supabase Dashboard → SQL Editor. Safe to re-run.

-- 1. notes table (Database + Realtime demo) ---------------------------------
create table if not exists public.notes (
  id         bigint generated always as identity primary key,
  user_id    uuid not null default auth.uid() references auth.users(id) on delete cascade,
  title      text not null,
  content    text not null default '',
  is_done    boolean not null default false,
  created_at timestamptz not null default now()
);

alter table public.notes enable row level security;

drop policy if exists "notes_select_own" on public.notes;
drop policy if exists "notes_insert_own" on public.notes;
drop policy if exists "notes_update_own" on public.notes;
drop policy if exists "notes_delete_own" on public.notes;
create policy "notes_select_own" on public.notes for select to authenticated using (user_id = auth.uid());
create policy "notes_insert_own" on public.notes for insert to authenticated with check (user_id = auth.uid());
create policy "notes_update_own" on public.notes for update to authenticated using (user_id = auth.uid());
create policy "notes_delete_own" on public.notes for delete to authenticated using (user_id = auth.uid());

-- Realtime: publish row changes (full old row on update/delete)
alter table public.notes replica identity full;
do $$ begin
  alter publication supabase_realtime add table public.notes;
exception when duplicate_object then null; end $$;

-- 2. RPC functions ----------------------------------------------------------
create or replace function public.add_numbers(a int, b int) returns int
language sql immutable as $$ select a + b $$;

create or replace function public.hello_world() returns text
language sql immutable as $$ select 'Hello from Postgres'::text $$;

grant execute on function public.add_numbers(int, int), public.hello_world() to anon, authenticated;

-- 3. Storage bucket + policies ---------------------------------------------
insert into storage.buckets (id, name, public)
values ('test-bucket', 'test-bucket', true)
on conflict (id) do update set public = true;

drop policy if exists "test_bucket_read"   on storage.objects;
drop policy if exists "test_bucket_insert" on storage.objects;
drop policy if exists "test_bucket_update" on storage.objects;
drop policy if exists "test_bucket_delete" on storage.objects;
create policy "test_bucket_read"   on storage.objects for select to anon, authenticated using (bucket_id = 'test-bucket');
create policy "test_bucket_insert" on storage.objects for insert to authenticated with check (bucket_id = 'test-bucket');
create policy "test_bucket_update" on storage.objects for update to authenticated using (bucket_id = 'test-bucket');
create policy "test_bucket_delete" on storage.objects for delete to authenticated using (bucket_id = 'test-bucket');
