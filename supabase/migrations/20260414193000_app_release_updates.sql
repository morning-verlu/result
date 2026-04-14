create table if not exists public.app_release_admins (
    user_id uuid primary key references auth.users(id) on delete cascade,
    created_at timestamptz not null default now()
);

create table if not exists public.app_releases (
    id uuid primary key default gen_random_uuid(),
    package_name text not null,
    version_code integer not null check (version_code > 0),
    version_name text not null,
    title text not null default '版本更新',
    changelog text not null default '',
    download_url text not null,
    file_size_bytes bigint,
    force_update boolean not null default false,
    min_supported_version_code integer not null default 0,
    rollout_percent integer not null default 100 check (rollout_percent between 1 and 100),
    enabled boolean not null default true,
    created_by uuid references auth.users(id) on delete set null,
    created_at timestamptz not null default now(),
    published_at timestamptz not null default now()
);

create unique index if not exists app_releases_package_version_uniq
    on public.app_releases(package_name, version_code);

create index if not exists app_releases_package_enabled_idx
    on public.app_releases(package_name, enabled, version_code desc, published_at desc);

alter table public.app_release_admins enable row level security;
alter table public.app_releases enable row level security;

drop policy if exists "app_release_admins_select_self" on public.app_release_admins;
create policy "app_release_admins_select_self"
on public.app_release_admins
for select
to authenticated
using (auth.uid() = user_id);

drop policy if exists "app_releases_public_read" on public.app_releases;
create policy "app_releases_public_read"
on public.app_releases
for select
to anon, authenticated
using (enabled = true);

drop policy if exists "app_releases_admin_insert" on public.app_releases;
create policy "app_releases_admin_insert"
on public.app_releases
for insert
to authenticated
with check (
    exists (
        select 1
        from public.app_release_admins admins
        where admins.user_id = auth.uid()
    )
);

drop policy if exists "app_releases_admin_update" on public.app_releases;
create policy "app_releases_admin_update"
on public.app_releases
for update
to authenticated
using (
    exists (
        select 1
        from public.app_release_admins admins
        where admins.user_id = auth.uid()
    )
)
with check (
    exists (
        select 1
        from public.app_release_admins admins
        where admins.user_id = auth.uid()
    )
);

drop policy if exists "app_releases_admin_delete" on public.app_releases;
create policy "app_releases_admin_delete"
on public.app_releases
for delete
to authenticated
using (
    exists (
        select 1
        from public.app_release_admins admins
        where admins.user_id = auth.uid()
    )
);

insert into storage.buckets (id, name, public)
values ('app-releases', 'app-releases', true)
on conflict (id) do update
set public = excluded.public;

drop policy if exists "app_release_files_public_read" on storage.objects;
create policy "app_release_files_public_read"
on storage.objects
for select
to anon, authenticated
using (bucket_id = 'app-releases');

drop policy if exists "app_release_files_admin_insert" on storage.objects;
create policy "app_release_files_admin_insert"
on storage.objects
for insert
to authenticated
with check (
    bucket_id = 'app-releases'
    and exists (
        select 1
        from public.app_release_admins admins
        where admins.user_id = auth.uid()
    )
);

drop policy if exists "app_release_files_admin_update" on storage.objects;
create policy "app_release_files_admin_update"
on storage.objects
for update
to authenticated
using (
    bucket_id = 'app-releases'
    and exists (
        select 1
        from public.app_release_admins admins
        where admins.user_id = auth.uid()
    )
)
with check (
    bucket_id = 'app-releases'
    and exists (
        select 1
        from public.app_release_admins admins
        where admins.user_id = auth.uid()
    )
);

drop policy if exists "app_release_files_admin_delete" on storage.objects;
create policy "app_release_files_admin_delete"
on storage.objects
for delete
to authenticated
using (
    bucket_id = 'app-releases'
    and exists (
        select 1
        from public.app_release_admins admins
        where admins.user_id = auth.uid()
    )
);
