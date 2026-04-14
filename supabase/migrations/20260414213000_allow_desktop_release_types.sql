update storage.buckets
set
    file_size_limit = 209715200,
    allowed_mime_types = null,
    public = true
where id = 'app-releases' or name = 'app-releases';
