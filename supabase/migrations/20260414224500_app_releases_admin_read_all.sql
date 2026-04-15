drop policy if exists "app_releases_admin_read_all" on public.app_releases;
create policy "app_releases_admin_read_all"
on public.app_releases
for select
to authenticated
using (
    exists (
        select 1
        from public.app_release_admins admins
        where admins.user_id = auth.uid()
    )
);
