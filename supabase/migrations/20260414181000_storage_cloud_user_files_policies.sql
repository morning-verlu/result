-- Ensure talk/cloud shared bucket exists and allow users to manage only their own files:
-- owners/<auth.uid()>/...

do $$
begin
    if to_regclass('storage.buckets') is not null then
        insert into storage.buckets (id, name, public)
        values ('cloud-user-files', 'cloud-user-files', false)
        on conflict (id) do update set public = excluded.public;
    end if;
end $$;

do $$
begin
    if to_regclass('storage.objects') is not null
       and not exists (
           select 1
           from pg_policies
           where schemaname = 'storage'
             and tablename = 'objects'
             and policyname = 'cloud_user_files_select_own'
       ) then
        execute $policy$
            create policy cloud_user_files_select_own
            on storage.objects
            for select
            to authenticated
            using (
                bucket_id = 'cloud-user-files'
                and split_part(name, '/', 1) = 'owners'
                and split_part(name, '/', 2) = auth.uid()::text
            )
        $policy$;
    end if;
end $$;

do $$
begin
    if to_regclass('storage.objects') is not null
       and not exists (
           select 1
           from pg_policies
           where schemaname = 'storage'
             and tablename = 'objects'
             and policyname = 'cloud_user_files_insert_own'
       ) then
        execute $policy$
            create policy cloud_user_files_insert_own
            on storage.objects
            for insert
            to authenticated
            with check (
                bucket_id = 'cloud-user-files'
                and split_part(name, '/', 1) = 'owners'
                and split_part(name, '/', 2) = auth.uid()::text
            )
        $policy$;
    end if;
end $$;

do $$
begin
    if to_regclass('storage.objects') is not null
       and not exists (
           select 1
           from pg_policies
           where schemaname = 'storage'
             and tablename = 'objects'
             and policyname = 'cloud_user_files_update_own'
       ) then
        execute $policy$
            create policy cloud_user_files_update_own
            on storage.objects
            for update
            to authenticated
            using (
                bucket_id = 'cloud-user-files'
                and split_part(name, '/', 1) = 'owners'
                and split_part(name, '/', 2) = auth.uid()::text
            )
            with check (
                bucket_id = 'cloud-user-files'
                and split_part(name, '/', 1) = 'owners'
                and split_part(name, '/', 2) = auth.uid()::text
            )
        $policy$;
    end if;
end $$;

do $$
begin
    if to_regclass('storage.objects') is not null
       and not exists (
           select 1
           from pg_policies
           where schemaname = 'storage'
             and tablename = 'objects'
             and policyname = 'cloud_user_files_delete_own'
       ) then
        execute $policy$
            create policy cloud_user_files_delete_own
            on storage.objects
            for delete
            to authenticated
            using (
                bucket_id = 'cloud-user-files'
                and split_part(name, '/', 1) = 'owners'
                and split_part(name, '/', 2) = auth.uid()::text
            )
        $policy$;
    end if;
end $$;
