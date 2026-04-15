-- 正式棋钟：用时由服务器根据 last_move_at / created_at 与 now() 的差值决定（不信任客户端 p_spent_ms）
-- 行棋方剩余时间 <= 0 且未在时限内完成走子 → timeout，对手获胜

-- 被动超时：当前行棋方思考时间已耗尽时，将棋局标为 timeout（在拉取对局等时机调用）
create or replace function public.cnchess_sync_timeout_if_due(p_game_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    g public.chess_games%rowtype;
    t_start timestamptz;
    elapsed_ms bigint;
    rem bigint;
begin
    select * into g from public.chess_games where id = p_game_id for update;
    if not found then
        return;
    end if;
    if g.status <> 'active' then
        return;
    end if;

    t_start := coalesce(g.last_move_at, g.created_at);
    elapsed_ms := floor(extract(epoch from (now() - t_start)) * 1000)::bigint;
    if elapsed_ms < 0 then
        elapsed_ms := 0;
    end if;
    if elapsed_ms > 86400000 then
        elapsed_ms := 86400000;
    end if;

    if g.turn_side = 'red' then
        rem := g.red_time_ms - elapsed_ms;
        if rem > 0 then
            return;
        end if;
        update public.chess_games
        set
            status = 'timeout',
            winner_user_id = g.black_user_id,
            red_time_ms = 0,
            last_move_at = now(),
            updated_at = now(),
            draw_offer_by_user_id = null,
            draw_offer_at = null
        where id = p_game_id;
    elsif g.turn_side = 'black' then
        rem := g.black_time_ms - elapsed_ms;
        if rem > 0 then
            return;
        end if;
        update public.chess_games
        set
            status = 'timeout',
            winner_user_id = g.red_user_id,
            black_time_ms = 0,
            last_move_at = now(),
            updated_at = now(),
            draw_offer_by_user_id = null,
            draw_offer_at = null
        where id = p_game_id;
    end if;
end;
$$;

grant execute on function public.cnchess_sync_timeout_if_due(uuid) to authenticated;

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
set search_path = public
as $$
declare
    g public.chess_games%rowtype;
    next_turn text;
    t_start timestamptz;
    spent_ms bigint;
    mover_rem bigint;
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

    t_start := coalesce(g.last_move_at, g.created_at);
    spent_ms := floor(extract(epoch from (now() - t_start)) * 1000)::bigint;
    if spent_ms < 0 then
        spent_ms := 0;
    end if;
    if spent_ms > 86400000 then
        spent_ms := 86400000;
    end if;

    if p_side = 'red' then
        mover_rem := g.red_time_ms;
    else
        mover_rem := g.black_time_ms;
    end if;

    -- 本步思考时间已超过剩余时间：判负，不记录走子
    if mover_rem < spent_ms then
        if p_side = 'red' then
            update public.chess_games
            set
                status = 'timeout',
                winner_user_id = g.black_user_id,
                red_time_ms = 0,
                last_move_at = now(),
                updated_at = now(),
                draw_offer_by_user_id = null,
                draw_offer_at = null
            where id = p_game_id;
        else
            update public.chess_games
            set
                status = 'timeout',
                winner_user_id = g.red_user_id,
                black_time_ms = 0,
                last_move_at = now(),
                updated_at = now(),
                draw_offer_by_user_id = null,
                draw_offer_at = null
            where id = p_game_id;
        end if;
        return query select false, g.move_no, 'timeout_loss'::text;
        return;
    end if;

    next_turn := case when p_side = 'red' then 'black' else 'red' end;
    next_red_ms := g.red_time_ms - case when p_side = 'red' then spent_ms else 0 end;
    next_black_ms := g.black_time_ms - case when p_side = 'black' then spent_ms else 0 end;
    next_status := case when p_draw_reason is null then 'active' else 'draw' end;
    next_winner := null;

    insert into public.chess_moves(
        game_id, move_no, side, from_row, from_col, to_row, to_col,
        fen_before, fen_after, spent_ms, position_hash, is_check, is_chase, judge_tag
    ) values (
        p_game_id, p_move_no + 1, p_side, p_from_row, p_from_col, p_to_row, p_to_col,
        p_fen_before, p_fen_after, spent_ms, p_position_hash, p_is_check, p_is_chase, p_judge_tag
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
