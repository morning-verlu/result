-- CNChess gameplay schema extension + make_move RPC

alter table public.chess_games
    add column if not exists fen text not null default 'rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR',
    add column if not exists turn_side text not null default 'red' check (turn_side in ('red', 'black')),
    add column if not exists red_time_ms bigint not null default 600000,
    add column if not exists black_time_ms bigint not null default 600000,
    add column if not exists winner_user_id uuid references auth.users(id) on delete set null,
    add column if not exists move_no bigint not null default 0,
    add column if not exists last_move_at timestamptz;

alter table public.chess_games
    drop constraint if exists chess_games_status_check;
alter table public.chess_games
    add constraint chess_games_status_check check (status in ('active', 'finished', 'aborted', 'timeout', 'resigned', 'draw'));

create table if not exists public.chess_moves (
    id uuid primary key default gen_random_uuid(),
    game_id uuid not null references public.chess_games(id) on delete cascade,
    move_no bigint not null,
    side text not null check (side in ('red', 'black')),
    from_row int not null check (from_row between 0 and 9),
    from_col int not null check (from_col between 0 and 8),
    to_row int not null check (to_row between 0 and 9),
    to_col int not null check (to_col between 0 and 8),
    fen_before text,
    fen_after text,
    spent_ms bigint not null default 0,
    created_at timestamptz not null default now(),
    unique (game_id, move_no)
);

create index if not exists idx_chess_moves_game_move_no on public.chess_moves(game_id, move_no desc);

alter table public.chess_moves enable row level security;

drop policy if exists chess_moves_select on public.chess_moves;
create policy chess_moves_select on public.chess_moves
for select to authenticated
using (
    exists (
        select 1 from public.chess_games g
        where g.id = chess_moves.game_id
          and (g.red_user_id = auth.uid() or g.black_user_id = auth.uid())
    )
);

drop policy if exists chess_moves_insert on public.chess_moves;
create policy chess_moves_insert on public.chess_moves
for insert to authenticated
with check (
    exists (
        select 1 from public.chess_games g
        where g.id = chess_moves.game_id
          and g.status = 'active'
          and (
            (chess_moves.side = 'red' and g.red_user_id = auth.uid())
            or
            (chess_moves.side = 'black' and g.black_user_id = auth.uid())
          )
    )
);

drop function if exists public.make_move(uuid, bigint, text, int, int, int, int, text, text, bigint);
create function public.make_move(
    p_game_id uuid,
    p_move_no bigint,
    p_side text,
    p_from_row int,
    p_from_col int,
    p_to_row int,
    p_to_col int,
    p_fen_before text,
    p_fen_after text,
    p_spent_ms bigint default 0
) returns table(ok boolean, new_move_no bigint, error text)
language plpgsql
security definer
as $$
declare
    g public.chess_games%rowtype;
    next_turn text;
    next_red_ms bigint;
    next_black_ms bigint;
    next_status text;
    next_winner uuid;
begin
    select * into g from public.chess_games where id = p_game_id for update;
    if not found then
        return query select false, 0::bigint, 'game_not_found'::text;
        return;
    end if;
    if g.status <> 'active' then
        return query select false, g.move_no, 'game_not_active'::text;
        return;
    end if;
    if g.move_no <> p_move_no then
        return query select false, g.move_no, 'move_no_conflict'::text;
        return;
    end if;
    if g.turn_side <> p_side then
        return query select false, g.move_no, 'turn_conflict'::text;
        return;
    end if;
    if p_fen_before is not null and g.fen <> p_fen_before then
        return query select false, g.move_no, 'fen_conflict'::text;
        return;
    end if;

    next_turn := case when p_side = 'red' then 'black' else 'red' end;
    next_red_ms := g.red_time_ms - case when p_side = 'red' then p_spent_ms else 0 end;
    next_black_ms := g.black_time_ms - case when p_side = 'black' then p_spent_ms else 0 end;
    next_status := 'active';
    next_winner := null;

    if next_red_ms <= 0 then
        next_red_ms := 0;
        next_status := 'timeout';
        next_winner := g.black_user_id;
    elsif next_black_ms <= 0 then
        next_black_ms := 0;
        next_status := 'timeout';
        next_winner := g.red_user_id;
    end if;

    insert into public.chess_moves(
        game_id, move_no, side, from_row, from_col, to_row, to_col, fen_before, fen_after, spent_ms
    ) values (
        p_game_id, p_move_no + 1, p_side, p_from_row, p_from_col, p_to_row, p_to_col, p_fen_before, p_fen_after, p_spent_ms
    );

    update public.chess_games
    set
        fen = coalesce(p_fen_after, g.fen),
        turn_side = next_turn,
        move_no = p_move_no + 1,
        red_time_ms = next_red_ms,
        black_time_ms = next_black_ms,
        status = next_status,
        winner_user_id = next_winner,
        last_move_at = now(),
        updated_at = now()
    where id = p_game_id;

    return query select true, p_move_no + 1, null::text;
end;
$$;

grant execute on function public.make_move(uuid, bigint, text, int, int, int, int, text, text, bigint) to authenticated;

alter publication supabase_realtime add table public.chess_moves;
