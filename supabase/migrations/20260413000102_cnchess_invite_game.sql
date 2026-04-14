-- CNChess presence + invites + games

create table if not exists public.cnchess_presence (
    user_id uuid primary key references auth.users(id) on delete cascade,
    device_id text not null default 'android',
    is_foreground boolean not null default true,
    last_seen_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.chess_games (
    id uuid primary key default gen_random_uuid(),
    red_user_id uuid not null references auth.users(id) on delete cascade,
    black_user_id uuid not null references auth.users(id) on delete cascade,
    status text not null default 'active'
        check (status in ('active', 'finished', 'aborted')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.chess_invites (
    id uuid primary key default gen_random_uuid(),
    from_user_id uuid not null references auth.users(id) on delete cascade,
    to_user_id uuid not null references auth.users(id) on delete cascade,
    status text not null default 'pending'
        check (status in ('pending', 'accepted', 'rejected', 'expired', 'canceled')),
    expires_at timestamptz not null default (now() + interval '60 seconds'),
    game_id uuid references public.chess_games(id) on delete set null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    check (from_user_id <> to_user_id)
);

create index if not exists idx_cnchess_presence_last_seen on public.cnchess_presence(last_seen_at desc);
create index if not exists idx_chess_invites_to_status on public.chess_invites(to_user_id, status, created_at desc);
create index if not exists idx_chess_invites_from_status on public.chess_invites(from_user_id, status, created_at desc);

alter table public.cnchess_presence enable row level security;
alter table public.chess_games enable row level security;
alter table public.chess_invites enable row level security;

drop policy if exists cnchess_presence_select on public.cnchess_presence;
do $$
begin
    if to_regclass('public.friendships') is not null then
        execute $sql$
            create policy cnchess_presence_select on public.cnchess_presence
            for select to authenticated
            using (
                user_id = auth.uid()
                or exists (
                    select 1
                    from public.friendships f
                    where f.status = 'accepted'
                      and (
                        (f.requester_id = auth.uid() and f.addressee_id = cnchess_presence.user_id)
                        or
                        (f.addressee_id = auth.uid() and f.requester_id = cnchess_presence.user_id)
                      )
                )
            )
        $sql$;
    else
        execute $sql$
            create policy cnchess_presence_select on public.cnchess_presence
            for select to authenticated
            using (user_id = auth.uid())
        $sql$;
    end if;
end $$;

drop policy if exists cnchess_presence_insert on public.cnchess_presence;
create policy cnchess_presence_insert on public.cnchess_presence
for insert to authenticated
with check (user_id = auth.uid());

drop policy if exists cnchess_presence_update on public.cnchess_presence;
create policy cnchess_presence_update on public.cnchess_presence
for update to authenticated
using (user_id = auth.uid())
with check (user_id = auth.uid());

drop policy if exists chess_invites_select on public.chess_invites;
create policy chess_invites_select on public.chess_invites
for select to authenticated
using (from_user_id = auth.uid() or to_user_id = auth.uid());

drop policy if exists chess_invites_insert on public.chess_invites;
create policy chess_invites_insert on public.chess_invites
for insert to authenticated
with check (
    from_user_id = auth.uid()
    and status = 'pending'
);

drop policy if exists chess_invites_update on public.chess_invites;
create policy chess_invites_update on public.chess_invites
for update to authenticated
using (from_user_id = auth.uid() or to_user_id = auth.uid())
with check (from_user_id = auth.uid() or to_user_id = auth.uid());

drop policy if exists chess_games_select on public.chess_games;
create policy chess_games_select on public.chess_games
for select to authenticated
using (red_user_id = auth.uid() or black_user_id = auth.uid());

drop policy if exists chess_games_insert on public.chess_games;
create policy chess_games_insert on public.chess_games
for insert to authenticated
with check (red_user_id = auth.uid() or black_user_id = auth.uid());

drop policy if exists chess_games_update on public.chess_games;
create policy chess_games_update on public.chess_games
for update to authenticated
using (red_user_id = auth.uid() or black_user_id = auth.uid())
with check (red_user_id = auth.uid() or black_user_id = auth.uid());

alter publication supabase_realtime add table public.cnchess_presence;
alter publication supabase_realtime add table public.chess_invites;
alter publication supabase_realtime add table public.chess_games;
