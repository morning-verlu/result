-- Cloud module business schema (Hybrid mode)
-- Reuse existing auth.users + profiles, create cloud-specific tables only.

create extension if not exists pgcrypto;

create table if not exists public.cloud_nodes (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references auth.users(id) on delete cascade,
    parent_id uuid references public.cloud_nodes(id) on delete set null,
    path text not null,
    file_name text not null,
    mime_type text,
    size_bytes bigint not null default 0,
    is_dir boolean not null default false,
    storage_bucket text not null default 'cloud-files',
    storage_object_path text,
    is_trashed boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.cloud_transfers (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references auth.users(id) on delete cascade,
    node_id uuid references public.cloud_nodes(id) on delete set null,
    direction text not null check (direction in ('upload','download')),
    status text not null check (status in ('queued','running','paused','failed','done')),
    transferred_bytes bigint not null default 0,
    total_bytes bigint not null default 0,
    resume_token text,
    client_id text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.cloud_shares (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references auth.users(id) on delete cascade,
    node_id uuid not null references public.cloud_nodes(id) on delete cascade,
    share_token text not null unique,
    expires_at timestamptz,
    max_downloads int,
    download_count int not null default 0,
    created_at timestamptz not null default now()
);

create table if not exists public.cloud_share_codes (
    share_id uuid primary key references public.cloud_shares(id) on delete cascade,
    code_hash text not null,
    created_at timestamptz not null default now()
);

create table if not exists public.cloud_recent (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references auth.users(id) on delete cascade,
    node_id uuid not null references public.cloud_nodes(id) on delete cascade,
    accessed_at timestamptz not null default now()
);

alter table public.cloud_nodes enable row level security;
alter table public.cloud_transfers enable row level security;
alter table public.cloud_shares enable row level security;
alter table public.cloud_share_codes enable row level security;
alter table public.cloud_recent enable row level security;

drop policy if exists cloud_nodes_owner_rw on public.cloud_nodes;
create policy cloud_nodes_owner_rw on public.cloud_nodes
for all using (owner_id = auth.uid()) with check (owner_id = auth.uid());

drop policy if exists cloud_transfers_owner_rw on public.cloud_transfers;
create policy cloud_transfers_owner_rw on public.cloud_transfers
for all using (owner_id = auth.uid()) with check (owner_id = auth.uid());

drop policy if exists cloud_shares_owner_rw on public.cloud_shares;
create policy cloud_shares_owner_rw on public.cloud_shares
for all using (owner_id = auth.uid()) with check (owner_id = auth.uid());

drop policy if exists cloud_share_codes_owner_rw on public.cloud_share_codes;
create policy cloud_share_codes_owner_rw on public.cloud_share_codes
for all using (
  exists (
    select 1 from public.cloud_shares s
    where s.id = share_id and s.owner_id = auth.uid()
  )
)
with check (
  exists (
    select 1 from public.cloud_shares s
    where s.id = share_id and s.owner_id = auth.uid()
  )
);

drop policy if exists cloud_recent_owner_rw on public.cloud_recent;
create policy cloud_recent_owner_rw on public.cloud_recent
for all using (owner_id = auth.uid()) with check (owner_id = auth.uid());

-- Public read is done via edge-function / rpc verification:
-- verify share_token + optional code, then return a signed URL from storage.

-- ─── 分享功能说明 ─────────────────────────────────────────────────────────────
-- "分享给 Talk 好友" 功能通过以下方式实现（无需额外表）：
--   1. 客户端调用 cloud-files Edge Function 获取带签名的 S3 下载链接
--   2. 在 cloud_shares 记录该分享（owner_id, node_id, share_token, expires_at）
--   3. 向 messages 表插入一条消息（room_id = 好友聊天室，type = 'text'，内容含链接）
--      Talk 好友可在聊天界面直接看到并点击下载
--
-- 若需对外公开访问（无需登录），可创建 cloud-share-download Edge Function：
--   - 接收 token 查询 cloud_shares → 验证未过期 → 用 service role 获取 S3 签名 URL → 302 跳转
--   - 同时维护 cloud_shares.download_count（并发控制 max_downloads）
--
-- RLS 补充建议（允许被分享方通过 token 读取 cloud_shares 元数据）：
-- create policy cloud_shares_token_read on public.cloud_shares
--   for select using (true);   -- 通过 Edge Function service role 绕过，客户端不需要此 policy
