insert into storage.buckets (
    id,
    name,
    public,
    file_size_limit,
    allowed_mime_types
)
values (
    'app-releases',
    'app-releases',
    true,
    209715200,
    array['application/vnd.android.package-archive']
)
on conflict (id) do update
set
    public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

update storage.buckets
set
    file_size_limit = 209715200,
    allowed_mime_types = array['application/vnd.android.package-archive'],
    public = true
where id = 'app-releases' or name = 'app-releases';
