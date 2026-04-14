# Unified App Update System

This repository now includes a unified version check + release backend for:

- `cn.verlu.sync`
- `cn.verlu.talk`
- `cn.verlu.music`
- `cn.verlu.doctor`
- `cn.verlu.cnchess`

## 1) Supabase migration

Apply migration:

- `supabase db push`

New objects:

- `public.app_releases`: release metadata and update strategy
- `public.app_release_admins`: users allowed to publish/manage releases

## 2) Configure release storage bucket

Create a public bucket named:

- `app-releases`

The admin web uploads APK files to this bucket and stores `download_url` in `app_releases`.

## 3) Grant admin permission

Insert one or more rows into `public.app_release_admins`:

```sql
insert into public.app_release_admins (user_id)
values ('<your-auth-user-uuid>');
```

## 4) Run admin backend UI

Directory:

- `admin-web`

Setup:

1. Copy `.env.example` to `.env`
2. Fill `VITE_SUPABASE_URL` and `VITE_SUPABASE_ANON_KEY`
3. `npm install`
4. `npm run dev`

Features:

- admin login with Supabase auth
- upload APK + publish release strategy
- optional/force update switch
- minimum supported version (hard cutoff)
- rollout percentage (gray release)
- enable/disable history entries

## 5) Client behavior in all Android apps

Every app now checks `app_releases` on startup and applies:

- `force_update = true` OR `currentVersionCode < min_supported_version_code` => mandatory update dialog
- otherwise rollout by deterministic bucket (`rollout_percent`)
- `download_url` downloaded through Android `DownloadManager`

All apps request `REQUEST_INSTALL_PACKAGES` and prompt users to install from the downloaded APK.
