create table if not exists public.memories (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  title text not null default '',
  content text not null default '',
  type text not null default 'Moment',
  tags text[] not null default '{}',
  mood text not null default '',
  scene text not null default '',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  constraint memories_type_check check (type in ('Moment', 'Idea', 'Preference', 'Person'))
);

alter table public.memories enable row level security;

create index if not exists memories_user_updated_idx
  on public.memories (user_id, updated_at desc);

create index if not exists memories_user_active_updated_idx
  on public.memories (user_id, updated_at desc)
  where deleted_at is null;

drop policy if exists "memories_select_own" on public.memories;
create policy "memories_select_own"
  on public.memories
  for select
  to authenticated
  using ((select auth.uid()) = user_id);

drop policy if exists "memories_insert_own" on public.memories;
create policy "memories_insert_own"
  on public.memories
  for insert
  to authenticated
  with check ((select auth.uid()) = user_id);

drop policy if exists "memories_update_own" on public.memories;
create policy "memories_update_own"
  on public.memories
  for update
  to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);

grant select, insert, update on table public.memories to authenticated;
