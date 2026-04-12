-- ============================================================================
-- qr_login_sessions 表：Talk 扫码登录 + Cloud 桌面端 QR 授权共用会话表
-- ============================================================================
-- 必须在 Supabase 控制台 SQL Editor 执行此文件，或通过 supabase db push 应用。
-- ============================================================================

create table if not exists public.qr_login_sessions (
    session_id  uuid        primary key,
    status      text        not null default 'pending'
                            check (status in ('pending', 'approved', 'expired')),
    email       text,
    login_token text,
    expires_at  timestamptz,
    created_at  timestamptz not null default now()
);

-- ── RLS ──────────────────────────────────────────────────────────────────────
alter table public.qr_login_sessions enable row level security;

-- 已认证用户可以创建自己的会话（Talk 创建时已登录）
drop policy if exists qr_sessions_insert on public.qr_login_sessions;
create policy qr_sessions_insert on public.qr_login_sessions
    for insert to authenticated
    with check (true);

-- 已认证用户可以读取所有 pending 会话（Talk 轮询时需要读自己的会话）
drop policy if exists qr_sessions_select on public.qr_login_sessions
;
create policy qr_sessions_select on public.qr_login_sessions
    for select to authenticated
    using (true);

-- UPDATE 仅允许 service role（approve-login Edge Function 用 SUPABASE_SERVICE_ROLE_KEY 调用，绕过 RLS）
-- 普通用户无 update 权限，安全。

-- ── Realtime ──────────────────────────────────────────────────────────────────
-- 将表加入 supabase_realtime 发布，Talk 才能通过 Realtime 订阅到会话状态变更。
-- 如果已经加入过，此语句不会报错（ALTER PUBLICATION ... ADD TABLE 是幂等的）。
alter publication supabase_realtime add table public.qr_login_sessions;
