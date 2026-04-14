-- CNChess draw-offer flow: offer/accept/reject

alter table public.chess_games
    add column if not exists draw_offer_by_user_id uuid references auth.users(id) on delete set null,
    add column if not exists draw_offer_at timestamptz;

create index if not exists idx_chess_games_draw_offer_by on public.chess_games(draw_offer_by_user_id);

drop function if exists public.make_move_v2(
    uuid, bigint, text, int, int, int, int, text, text, bigint, text, boolean, boolean, text, text
);
create function public.make_move_v2(
    p_game_id uuid,
    p_move_no bigint,
    p_side text,
    p_from_row int,
    p_from_col int,
    p_to_row int,
    p_to_col int,
    p_fen_before text,
    p_fen_after text,
    p_spent_ms bigint default 0,
    p_position_hash text default null,
    p_is_check boolean default false,
    p_is_chase boolean default false,
    p_judge_tag text default null,
    p_draw_reason text default null
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
    if p_judge_tag = 'illegal_long_check' then
        return query select false, g.move_no, 'illegal_long_check'::text;
        return;
    end if;
    if p_judge_tag = 'illegal_long_chase' then
        return query select false, g.move_no, 'illegal_long_chase'::text;
        return;
    end if;

    next_turn := case when p_side = 'red' then 'black' else 'red' end;
    next_red_ms := g.red_time_ms - case when p_side = 'red' then p_spent_ms else 0 end;
    next_black_ms := g.black_time_ms - case when p_side = 'black' then p_spent_ms else 0 end;
    next_status := case when p_draw_reason is null then 'active' else 'draw' end;
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
        game_id, move_no, side, from_row, from_col, to_row, to_col,
        fen_before, fen_after, spent_ms, position_hash, is_check, is_chase, judge_tag
    ) values (
        p_game_id, p_move_no + 1, p_side, p_from_row, p_from_col, p_to_row, p_to_col,
        p_fen_before, p_fen_after, p_spent_ms, p_position_hash, p_is_check, p_is_chase, p_judge_tag
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
        draw_reason = p_draw_reason,
        draw_offer_by_user_id = null,
        draw_offer_at = null,
        last_move_at = now(),
        updated_at = now()
    where id = p_game_id;

    return query select true, p_move_no + 1, null::text;
end;
$$;

grant execute on function public.make_move_v2(
    uuid, bigint, text, int, int, int, int, text, text, bigint, text, boolean, boolean, text, text
) to authenticated;
