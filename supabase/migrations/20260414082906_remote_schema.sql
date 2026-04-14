drop extension if exists "pg_net";

drop policy "qr_sessions_insert" on "public"."qr_login_sessions";

drop policy "qr_sessions_select" on "public"."qr_login_sessions";

drop policy "cnchess_presence_select" on "public"."cnchess_presence";

alter table "public"."qr_login_sessions" drop constraint "qr_login_sessions_status_check";


  create table "public"."battery_levels" (
    "user_id" text not null,
    "battery_percent" integer not null,
    "updated_at" bigint not null,
    "device_model" text not null default ''::text,
    "device_friendly_name" text not null default ''::text
      );


alter table "public"."battery_levels" enable row level security;


  create table "public"."friendships" (
    "id" uuid not null default gen_random_uuid(),
    "requester_id" uuid not null,
    "addressee_id" uuid not null,
    "status" text not null default 'pending'::text,
    "created_at" timestamp with time zone default now(),
    "updated_at" timestamp with time zone default now(),
    "room_id" uuid
      );


alter table "public"."friendships" enable row level security;


  create table "public"."message_reads" (
    "message_id" uuid not null,
    "user_id" uuid not null,
    "read_at" timestamp with time zone default now()
      );


alter table "public"."message_reads" enable row level security;


  create table "public"."messages" (
    "id" uuid not null default gen_random_uuid(),
    "room_id" uuid not null,
    "sender_id" uuid not null,
    "content" text not null,
    "type" text not null default 'text'::text,
    "created_at" timestamp with time zone default now(),
    "deleted_at" timestamp with time zone
      );


alter table "public"."messages" enable row level security;


  create table "public"."profiles" (
    "id" uuid not null,
    "display_name" text not null default 'User'::text,
    "avatar_url" text,
    "email" text,
    "online_at" timestamp with time zone,
    "created_at" timestamp with time zone default now()
      );


alter table "public"."profiles" enable row level security;


  create table "public"."room_members" (
    "room_id" uuid not null,
    "user_id" uuid not null
      );


alter table "public"."room_members" enable row level security;


  create table "public"."rooms" (
    "id" uuid not null default gen_random_uuid(),
    "created_at" timestamp with time zone default now()
      );


alter table "public"."rooms" enable row level security;


  create table "public"."screen_time_reports" (
    "user_id" text not null,
    "period" text not null,
    "total_foreground_ms" bigint not null,
    "top_apps" jsonb not null default '[]'::jsonb,
    "updated_at" bigint not null,
    "device_friendly_name" text not null default ''::text,
    "device_model" text not null default ''::text
      );


alter table "public"."screen_time_reports" enable row level security;


  create table "public"."temperature_levels" (
    "user_id" uuid not null,
    "temperature" integer not null,
    "updated_at" bigint not null,
    "device_model" text not null default ''::text,
    "device_friendly_name" text not null default ''::text
      );


alter table "public"."temperature_levels" enable row level security;


  create table "public"."weather_snapshots" (
    "user_id" text not null,
    "latitude" double precision not null,
    "longitude" double precision not null,
    "city_label" text not null,
    "temp" text not null,
    "feels_like" text not null default ''::text,
    "text_desc" text not null,
    "icon" text not null,
    "obs_time" text not null default ''::text,
    "api_update_time" text not null default ''::text,
    "forecast_json" text not null default ''::text,
    "device_friendly_name" text not null default ''::text,
    "updated_at" bigint not null
      );


alter table "public"."weather_snapshots" enable row level security;

alter table "public"."qr_login_sessions" alter column "created_at" drop not null;

alter table "public"."qr_login_sessions" alter column "expires_at" set default (now() + '00:05:00'::interval);

alter table "public"."qr_login_sessions" alter column "session_id" set default gen_random_uuid();

CREATE UNIQUE INDEX battery_levels_pkey ON public.battery_levels USING btree (user_id);

CREATE UNIQUE INDEX friendships_pkey ON public.friendships USING btree (id);

CREATE UNIQUE INDEX friendships_requester_id_addressee_id_key ON public.friendships USING btree (requester_id, addressee_id);

CREATE UNIQUE INDEX message_reads_pkey ON public.message_reads USING btree (message_id, user_id);

CREATE UNIQUE INDEX messages_pkey ON public.messages USING btree (id);

CREATE UNIQUE INDEX profiles_pkey ON public.profiles USING btree (id);

CREATE UNIQUE INDEX room_members_pkey ON public.room_members USING btree (room_id, user_id);

CREATE UNIQUE INDEX rooms_pkey ON public.rooms USING btree (id);

CREATE UNIQUE INDEX screen_time_reports_pkey ON public.screen_time_reports USING btree (user_id, period);

CREATE UNIQUE INDEX temperature_levels_pkey ON public.temperature_levels USING btree (user_id);

CREATE UNIQUE INDEX weather_snapshots_pkey ON public.weather_snapshots USING btree (user_id);

alter table "public"."battery_levels" add constraint "battery_levels_pkey" PRIMARY KEY using index "battery_levels_pkey";

alter table "public"."friendships" add constraint "friendships_pkey" PRIMARY KEY using index "friendships_pkey";

alter table "public"."message_reads" add constraint "message_reads_pkey" PRIMARY KEY using index "message_reads_pkey";

alter table "public"."messages" add constraint "messages_pkey" PRIMARY KEY using index "messages_pkey";

alter table "public"."profiles" add constraint "profiles_pkey" PRIMARY KEY using index "profiles_pkey";

alter table "public"."room_members" add constraint "room_members_pkey" PRIMARY KEY using index "room_members_pkey";

alter table "public"."rooms" add constraint "rooms_pkey" PRIMARY KEY using index "rooms_pkey";

alter table "public"."screen_time_reports" add constraint "screen_time_reports_pkey" PRIMARY KEY using index "screen_time_reports_pkey";

alter table "public"."temperature_levels" add constraint "temperature_levels_pkey" PRIMARY KEY using index "temperature_levels_pkey";

alter table "public"."weather_snapshots" add constraint "weather_snapshots_pkey" PRIMARY KEY using index "weather_snapshots_pkey";

alter table "public"."battery_levels" add constraint "battery_levels_battery_percent_check" CHECK (((battery_percent >= 0) AND (battery_percent <= 100))) not valid;

alter table "public"."battery_levels" validate constraint "battery_levels_battery_percent_check";

alter table "public"."friendships" add constraint "friendships_addressee_id_fkey" FOREIGN KEY (addressee_id) REFERENCES auth.users(id) ON DELETE CASCADE not valid;

alter table "public"."friendships" validate constraint "friendships_addressee_id_fkey";

alter table "public"."friendships" add constraint "friendships_requester_id_addressee_id_key" UNIQUE using index "friendships_requester_id_addressee_id_key";

alter table "public"."friendships" add constraint "friendships_requester_id_fkey" FOREIGN KEY (requester_id) REFERENCES auth.users(id) ON DELETE CASCADE not valid;

alter table "public"."friendships" validate constraint "friendships_requester_id_fkey";

alter table "public"."friendships" add constraint "friendships_room_id_fkey" FOREIGN KEY (room_id) REFERENCES public.rooms(id) ON DELETE SET NULL not valid;

alter table "public"."friendships" validate constraint "friendships_room_id_fkey";

alter table "public"."message_reads" add constraint "message_reads_message_id_fkey" FOREIGN KEY (message_id) REFERENCES public.messages(id) ON DELETE CASCADE not valid;

alter table "public"."message_reads" validate constraint "message_reads_message_id_fkey";

alter table "public"."message_reads" add constraint "message_reads_user_id_fkey" FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE not valid;

alter table "public"."message_reads" validate constraint "message_reads_user_id_fkey";

alter table "public"."messages" add constraint "messages_room_id_fkey" FOREIGN KEY (room_id) REFERENCES public.rooms(id) ON DELETE CASCADE not valid;

alter table "public"."messages" validate constraint "messages_room_id_fkey";

alter table "public"."messages" add constraint "messages_sender_id_fkey" FOREIGN KEY (sender_id) REFERENCES auth.users(id) not valid;

alter table "public"."messages" validate constraint "messages_sender_id_fkey";

alter table "public"."profiles" add constraint "profiles_id_fkey" FOREIGN KEY (id) REFERENCES auth.users(id) ON DELETE CASCADE not valid;

alter table "public"."profiles" validate constraint "profiles_id_fkey";

alter table "public"."room_members" add constraint "room_members_room_id_fkey" FOREIGN KEY (room_id) REFERENCES public.rooms(id) ON DELETE CASCADE not valid;

alter table "public"."room_members" validate constraint "room_members_room_id_fkey";

alter table "public"."room_members" add constraint "room_members_user_id_fkey" FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE not valid;

alter table "public"."room_members" validate constraint "room_members_user_id_fkey";

alter table "public"."screen_time_reports" add constraint "screen_time_reports_period_check" CHECK ((period = ANY (ARRAY['today'::text, 'last_7_days'::text]))) not valid;

alter table "public"."screen_time_reports" validate constraint "screen_time_reports_period_check";

alter table "public"."temperature_levels" add constraint "temperature_levels_user_id_fkey" FOREIGN KEY (user_id) REFERENCES auth.users(id) not valid;

alter table "public"."temperature_levels" validate constraint "temperature_levels_user_id_fkey";

set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.get_my_room_ids()
 RETURNS SETOF uuid
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
  SELECT room_id FROM public.room_members WHERE user_id = auth.uid();
$function$
;

CREATE OR REPLACE FUNCTION public.handle_friendship_accepted()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
DECLARE v_room_id uuid;
BEGIN
  IF NEW.status = 'accepted' AND OLD.status = 'pending' THEN
    INSERT INTO public.rooms DEFAULT VALUES RETURNING id INTO v_room_id;
    INSERT INTO public.room_members(room_id, user_id) VALUES
      (v_room_id, NEW.requester_id),
      (v_room_id, NEW.addressee_id);
    NEW.room_id = v_room_id;
    -- 同时清除反向 pending 请求
    DELETE FROM public.friendships
    WHERE requester_id = NEW.addressee_id
      AND addressee_id = NEW.requester_id
      AND status = 'pending';
  END IF;
  RETURN NEW;
END;
$function$
;

CREATE OR REPLACE FUNCTION public.handle_new_user()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
begin
  insert into public.profiles(id, display_name, avatar_url, email)
  values (
    new.id,
    coalesce(
      nullif(trim(new.raw_user_meta_data->>'full_name'), ''),
      nullif(trim(new.raw_user_meta_data->>'user_name'), ''),
      nullif(split_part(new.email, '@', 1), ''),
      'User'
    ),
    new.raw_user_meta_data->>'avatar_url',
    new.email
  )
  on conflict (id) do nothing;
  return new;
end;
$function$
;

CREATE OR REPLACE FUNCTION public.prevent_duplicate_friend_request()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
begin
  if exists (
    select 1 from public.friendships
    where requester_id = new.addressee_id
      and addressee_id = new.requester_id
  ) then
    raise exception 'reverse_request_exists';
  end if;
  return new;
end;
$function$
;

CREATE OR REPLACE FUNCTION public.rls_auto_enable()
 RETURNS event_trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'pg_catalog'
AS $function$
DECLARE
  cmd record;
BEGIN
  FOR cmd IN
    SELECT *
    FROM pg_event_trigger_ddl_commands()
    WHERE command_tag IN ('CREATE TABLE', 'CREATE TABLE AS', 'SELECT INTO')
      AND object_type IN ('table','partitioned table')
  LOOP
     IF cmd.schema_name IS NOT NULL AND cmd.schema_name IN ('public') AND cmd.schema_name NOT IN ('pg_catalog','information_schema') AND cmd.schema_name NOT LIKE 'pg_toast%' AND cmd.schema_name NOT LIKE 'pg_temp%' THEN
      BEGIN
        EXECUTE format('alter table if exists %s enable row level security', cmd.object_identity);
        RAISE LOG 'rls_auto_enable: enabled RLS on %', cmd.object_identity;
      EXCEPTION
        WHEN OTHERS THEN
          RAISE LOG 'rls_auto_enable: failed to enable RLS on %', cmd.object_identity;
      END;
     ELSE
        RAISE LOG 'rls_auto_enable: skip % (either system schema or not in enforced list: %.)', cmd.object_identity, cmd.schema_name;
     END IF;
  END LOOP;
END;
$function$
;

CREATE OR REPLACE FUNCTION public.make_move_v2(p_game_id uuid, p_move_no bigint, p_side text, p_from_row integer, p_from_col integer, p_to_row integer, p_to_col integer, p_fen_before text, p_fen_after text, p_spent_ms bigint DEFAULT 0, p_position_hash text DEFAULT NULL::text, p_is_check boolean DEFAULT false, p_is_chase boolean DEFAULT false, p_judge_tag text DEFAULT NULL::text, p_draw_reason text DEFAULT NULL::text)
 RETURNS TABLE(ok boolean, new_move_no bigint, error text)
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
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
$function$
;

grant delete on table "public"."battery_levels" to "anon";

grant insert on table "public"."battery_levels" to "anon";

grant references on table "public"."battery_levels" to "anon";

grant select on table "public"."battery_levels" to "anon";

grant trigger on table "public"."battery_levels" to "anon";

grant truncate on table "public"."battery_levels" to "anon";

grant update on table "public"."battery_levels" to "anon";

grant delete on table "public"."battery_levels" to "authenticated";

grant insert on table "public"."battery_levels" to "authenticated";

grant references on table "public"."battery_levels" to "authenticated";

grant select on table "public"."battery_levels" to "authenticated";

grant trigger on table "public"."battery_levels" to "authenticated";

grant truncate on table "public"."battery_levels" to "authenticated";

grant update on table "public"."battery_levels" to "authenticated";

grant delete on table "public"."battery_levels" to "service_role";

grant insert on table "public"."battery_levels" to "service_role";

grant references on table "public"."battery_levels" to "service_role";

grant select on table "public"."battery_levels" to "service_role";

grant trigger on table "public"."battery_levels" to "service_role";

grant truncate on table "public"."battery_levels" to "service_role";

grant update on table "public"."battery_levels" to "service_role";

grant delete on table "public"."friendships" to "anon";

grant insert on table "public"."friendships" to "anon";

grant references on table "public"."friendships" to "anon";

grant select on table "public"."friendships" to "anon";

grant trigger on table "public"."friendships" to "anon";

grant truncate on table "public"."friendships" to "anon";

grant update on table "public"."friendships" to "anon";

grant delete on table "public"."friendships" to "authenticated";

grant insert on table "public"."friendships" to "authenticated";

grant references on table "public"."friendships" to "authenticated";

grant select on table "public"."friendships" to "authenticated";

grant trigger on table "public"."friendships" to "authenticated";

grant truncate on table "public"."friendships" to "authenticated";

grant update on table "public"."friendships" to "authenticated";

grant delete on table "public"."friendships" to "service_role";

grant insert on table "public"."friendships" to "service_role";

grant references on table "public"."friendships" to "service_role";

grant select on table "public"."friendships" to "service_role";

grant trigger on table "public"."friendships" to "service_role";

grant truncate on table "public"."friendships" to "service_role";

grant update on table "public"."friendships" to "service_role";

grant delete on table "public"."message_reads" to "anon";

grant insert on table "public"."message_reads" to "anon";

grant references on table "public"."message_reads" to "anon";

grant select on table "public"."message_reads" to "anon";

grant trigger on table "public"."message_reads" to "anon";

grant truncate on table "public"."message_reads" to "anon";

grant update on table "public"."message_reads" to "anon";

grant delete on table "public"."message_reads" to "authenticated";

grant insert on table "public"."message_reads" to "authenticated";

grant references on table "public"."message_reads" to "authenticated";

grant select on table "public"."message_reads" to "authenticated";

grant trigger on table "public"."message_reads" to "authenticated";

grant truncate on table "public"."message_reads" to "authenticated";

grant update on table "public"."message_reads" to "authenticated";

grant delete on table "public"."message_reads" to "service_role";

grant insert on table "public"."message_reads" to "service_role";

grant references on table "public"."message_reads" to "service_role";

grant select on table "public"."message_reads" to "service_role";

grant trigger on table "public"."message_reads" to "service_role";

grant truncate on table "public"."message_reads" to "service_role";

grant update on table "public"."message_reads" to "service_role";

grant delete on table "public"."messages" to "anon";

grant insert on table "public"."messages" to "anon";

grant references on table "public"."messages" to "anon";

grant select on table "public"."messages" to "anon";

grant trigger on table "public"."messages" to "anon";

grant truncate on table "public"."messages" to "anon";

grant update on table "public"."messages" to "anon";

grant delete on table "public"."messages" to "authenticated";

grant insert on table "public"."messages" to "authenticated";

grant references on table "public"."messages" to "authenticated";

grant select on table "public"."messages" to "authenticated";

grant trigger on table "public"."messages" to "authenticated";

grant truncate on table "public"."messages" to "authenticated";

grant update on table "public"."messages" to "authenticated";

grant delete on table "public"."messages" to "service_role";

grant insert on table "public"."messages" to "service_role";

grant references on table "public"."messages" to "service_role";

grant select on table "public"."messages" to "service_role";

grant trigger on table "public"."messages" to "service_role";

grant truncate on table "public"."messages" to "service_role";

grant update on table "public"."messages" to "service_role";

grant delete on table "public"."profiles" to "anon";

grant insert on table "public"."profiles" to "anon";

grant references on table "public"."profiles" to "anon";

grant select on table "public"."profiles" to "anon";

grant trigger on table "public"."profiles" to "anon";

grant truncate on table "public"."profiles" to "anon";

grant update on table "public"."profiles" to "anon";

grant delete on table "public"."profiles" to "authenticated";

grant insert on table "public"."profiles" to "authenticated";

grant references on table "public"."profiles" to "authenticated";

grant select on table "public"."profiles" to "authenticated";

grant trigger on table "public"."profiles" to "authenticated";

grant truncate on table "public"."profiles" to "authenticated";

grant update on table "public"."profiles" to "authenticated";

grant delete on table "public"."profiles" to "service_role";

grant insert on table "public"."profiles" to "service_role";

grant references on table "public"."profiles" to "service_role";

grant select on table "public"."profiles" to "service_role";

grant trigger on table "public"."profiles" to "service_role";

grant truncate on table "public"."profiles" to "service_role";

grant update on table "public"."profiles" to "service_role";

grant delete on table "public"."room_members" to "anon";

grant insert on table "public"."room_members" to "anon";

grant references on table "public"."room_members" to "anon";

grant select on table "public"."room_members" to "anon";

grant trigger on table "public"."room_members" to "anon";

grant truncate on table "public"."room_members" to "anon";

grant update on table "public"."room_members" to "anon";

grant delete on table "public"."room_members" to "authenticated";

grant insert on table "public"."room_members" to "authenticated";

grant references on table "public"."room_members" to "authenticated";

grant select on table "public"."room_members" to "authenticated";

grant trigger on table "public"."room_members" to "authenticated";

grant truncate on table "public"."room_members" to "authenticated";

grant update on table "public"."room_members" to "authenticated";

grant delete on table "public"."room_members" to "service_role";

grant insert on table "public"."room_members" to "service_role";

grant references on table "public"."room_members" to "service_role";

grant select on table "public"."room_members" to "service_role";

grant trigger on table "public"."room_members" to "service_role";

grant truncate on table "public"."room_members" to "service_role";

grant update on table "public"."room_members" to "service_role";

grant delete on table "public"."rooms" to "anon";

grant insert on table "public"."rooms" to "anon";

grant references on table "public"."rooms" to "anon";

grant select on table "public"."rooms" to "anon";

grant trigger on table "public"."rooms" to "anon";

grant truncate on table "public"."rooms" to "anon";

grant update on table "public"."rooms" to "anon";

grant delete on table "public"."rooms" to "authenticated";

grant insert on table "public"."rooms" to "authenticated";

grant references on table "public"."rooms" to "authenticated";

grant select on table "public"."rooms" to "authenticated";

grant trigger on table "public"."rooms" to "authenticated";

grant truncate on table "public"."rooms" to "authenticated";

grant update on table "public"."rooms" to "authenticated";

grant delete on table "public"."rooms" to "service_role";

grant insert on table "public"."rooms" to "service_role";

grant references on table "public"."rooms" to "service_role";

grant select on table "public"."rooms" to "service_role";

grant trigger on table "public"."rooms" to "service_role";

grant truncate on table "public"."rooms" to "service_role";

grant update on table "public"."rooms" to "service_role";

grant delete on table "public"."screen_time_reports" to "anon";

grant insert on table "public"."screen_time_reports" to "anon";

grant references on table "public"."screen_time_reports" to "anon";

grant select on table "public"."screen_time_reports" to "anon";

grant trigger on table "public"."screen_time_reports" to "anon";

grant truncate on table "public"."screen_time_reports" to "anon";

grant update on table "public"."screen_time_reports" to "anon";

grant delete on table "public"."screen_time_reports" to "authenticated";

grant insert on table "public"."screen_time_reports" to "authenticated";

grant references on table "public"."screen_time_reports" to "authenticated";

grant select on table "public"."screen_time_reports" to "authenticated";

grant trigger on table "public"."screen_time_reports" to "authenticated";

grant truncate on table "public"."screen_time_reports" to "authenticated";

grant update on table "public"."screen_time_reports" to "authenticated";

grant delete on table "public"."screen_time_reports" to "service_role";

grant insert on table "public"."screen_time_reports" to "service_role";

grant references on table "public"."screen_time_reports" to "service_role";

grant select on table "public"."screen_time_reports" to "service_role";

grant trigger on table "public"."screen_time_reports" to "service_role";

grant truncate on table "public"."screen_time_reports" to "service_role";

grant update on table "public"."screen_time_reports" to "service_role";

grant delete on table "public"."temperature_levels" to "anon";

grant insert on table "public"."temperature_levels" to "anon";

grant references on table "public"."temperature_levels" to "anon";

grant select on table "public"."temperature_levels" to "anon";

grant trigger on table "public"."temperature_levels" to "anon";

grant truncate on table "public"."temperature_levels" to "anon";

grant update on table "public"."temperature_levels" to "anon";

grant delete on table "public"."temperature_levels" to "authenticated";

grant insert on table "public"."temperature_levels" to "authenticated";

grant references on table "public"."temperature_levels" to "authenticated";

grant select on table "public"."temperature_levels" to "authenticated";

grant trigger on table "public"."temperature_levels" to "authenticated";

grant truncate on table "public"."temperature_levels" to "authenticated";

grant update on table "public"."temperature_levels" to "authenticated";

grant delete on table "public"."temperature_levels" to "service_role";

grant insert on table "public"."temperature_levels" to "service_role";

grant references on table "public"."temperature_levels" to "service_role";

grant select on table "public"."temperature_levels" to "service_role";

grant trigger on table "public"."temperature_levels" to "service_role";

grant truncate on table "public"."temperature_levels" to "service_role";

grant update on table "public"."temperature_levels" to "service_role";

grant delete on table "public"."weather_snapshots" to "anon";

grant insert on table "public"."weather_snapshots" to "anon";

grant references on table "public"."weather_snapshots" to "anon";

grant select on table "public"."weather_snapshots" to "anon";

grant trigger on table "public"."weather_snapshots" to "anon";

grant truncate on table "public"."weather_snapshots" to "anon";

grant update on table "public"."weather_snapshots" to "anon";

grant delete on table "public"."weather_snapshots" to "authenticated";

grant insert on table "public"."weather_snapshots" to "authenticated";

grant references on table "public"."weather_snapshots" to "authenticated";

grant select on table "public"."weather_snapshots" to "authenticated";

grant trigger on table "public"."weather_snapshots" to "authenticated";

grant truncate on table "public"."weather_snapshots" to "authenticated";

grant update on table "public"."weather_snapshots" to "authenticated";

grant delete on table "public"."weather_snapshots" to "service_role";

grant insert on table "public"."weather_snapshots" to "service_role";

grant references on table "public"."weather_snapshots" to "service_role";

grant select on table "public"."weather_snapshots" to "service_role";

grant trigger on table "public"."weather_snapshots" to "service_role";

grant truncate on table "public"."weather_snapshots" to "service_role";

grant update on table "public"."weather_snapshots" to "service_role";


  create policy "battery_levels_insert_all"
  on "public"."battery_levels"
  as permissive
  for insert
  to authenticated
with check (true);



  create policy "battery_levels_read_all"
  on "public"."battery_levels"
  as permissive
  for select
  to authenticated
using (true);



  create policy "battery_levels_update_all"
  on "public"."battery_levels"
  as permissive
  for update
  to authenticated
using (true)
with check (true);



  create policy "accept/reject request"
  on "public"."friendships"
  as permissive
  for update
  to public
using ((auth.uid() = addressee_id));



  create policy "delete friendship"
  on "public"."friendships"
  as permissive
  for delete
  to public
using (((auth.uid() = requester_id) OR (auth.uid() = addressee_id)));



  create policy "see own friendships"
  on "public"."friendships"
  as permissive
  for select
  to public
using (((auth.uid() = requester_id) OR (auth.uid() = addressee_id)));



  create policy "send friend request"
  on "public"."friendships"
  as permissive
  for insert
  to public
with check ((auth.uid() = requester_id));



  create policy "mark read upsert"
  on "public"."message_reads"
  as permissive
  for update
  to public
using ((auth.uid() = user_id));



  create policy "mark read"
  on "public"."message_reads"
  as permissive
  for insert
  to public
with check ((auth.uid() = user_id));



  create policy "see reads"
  on "public"."message_reads"
  as permissive
  for select
  to public
using ((EXISTS ( SELECT 1
   FROM (public.messages m
     JOIN public.room_members rm ON ((rm.room_id = m.room_id)))
  WHERE ((m.id = message_reads.message_id) AND (rm.user_id = auth.uid())))));



  create policy "messages_insert"
  on "public"."messages"
  as permissive
  for insert
  to public
with check (((sender_id = auth.uid()) AND (room_id IN ( SELECT friendships.room_id
   FROM public.friendships
  WHERE ((friendships.status = 'accepted'::text) AND ((friendships.requester_id = auth.uid()) OR (friendships.addressee_id = auth.uid())))))));



  create policy "messages_select"
  on "public"."messages"
  as permissive
  for select
  to public
using ((room_id IN ( SELECT friendships.room_id
   FROM public.friendships
  WHERE ((friendships.status = 'accepted'::text) AND ((friendships.requester_id = auth.uid()) OR (friendships.addressee_id = auth.uid()))))));



  create policy "messages_update"
  on "public"."messages"
  as permissive
  for update
  to public
using (((sender_id = auth.uid()) AND (room_id IN ( SELECT friendships.room_id
   FROM public.friendships
  WHERE ((friendships.status = 'accepted'::text) AND ((friendships.requester_id = auth.uid()) OR (friendships.addressee_id = auth.uid())))))));



  create policy "insert own profile"
  on "public"."profiles"
  as permissive
  for insert
  to public
with check ((auth.uid() = id));



  create policy "read profiles"
  on "public"."profiles"
  as permissive
  for select
  to public
using (true);



  create policy "update own profile"
  on "public"."profiles"
  as permissive
  for update
  to public
using ((auth.uid() = id));



  create policy "允许任何人创建登录会话"
  on "public"."qr_login_sessions"
  as permissive
  for insert
  to public
with check (true);



  create policy "允许任何人根据 ID 查询会话状态"
  on "public"."qr_login_sessions"
  as permissive
  for select
  to public
using (true);



  create policy "own_rows_only"
  on "public"."room_members"
  as permissive
  for select
  to public
using ((user_id = auth.uid()));



  create policy "see own rooms"
  on "public"."rooms"
  as permissive
  for select
  to public
using ((EXISTS ( SELECT 1
   FROM public.room_members rm
  WHERE ((rm.room_id = rooms.id) AND (rm.user_id = auth.uid())))));



  create policy "screen_time_reports_insert_all"
  on "public"."screen_time_reports"
  as permissive
  for insert
  to authenticated
with check (true);



  create policy "screen_time_reports_read_all"
  on "public"."screen_time_reports"
  as permissive
  for select
  to authenticated
using (true);



  create policy "screen_time_reports_update_all"
  on "public"."screen_time_reports"
  as permissive
  for update
  to authenticated
using (true)
with check (true);



  create policy "authenticated_select"
  on "public"."temperature_levels"
  as permissive
  for select
  to authenticated
using (true);



  create policy "authenticated_update"
  on "public"."temperature_levels"
  as permissive
  for update
  to authenticated
using ((auth.uid() = user_id));



  create policy "authenticated_upsert"
  on "public"."temperature_levels"
  as permissive
  for insert
  to authenticated
with check ((auth.uid() = user_id));



  create policy "weather read all"
  on "public"."weather_snapshots"
  as permissive
  for select
  to authenticated
using (true);



  create policy "weather update own"
  on "public"."weather_snapshots"
  as permissive
  for update
  to authenticated
using (((auth.uid())::text = user_id));



  create policy "weather upsert own"
  on "public"."weather_snapshots"
  as permissive
  for insert
  to authenticated
with check (((auth.uid())::text = user_id));



  create policy "cnchess_presence_select"
  on "public"."cnchess_presence"
  as permissive
  for select
  to authenticated
using (((user_id = auth.uid()) OR (EXISTS ( SELECT 1
   FROM public.friendships f
  WHERE ((f.status = 'accepted'::text) AND (((f.requester_id = auth.uid()) AND (f.addressee_id = cnchess_presence.user_id)) OR ((f.addressee_id = auth.uid()) AND (f.requester_id = cnchess_presence.user_id))))))));


CREATE TRIGGER on_friendship_accepted BEFORE UPDATE ON public.friendships FOR EACH ROW EXECUTE FUNCTION public.handle_friendship_accepted();

CREATE TRIGGER prevent_duplicate_request BEFORE INSERT ON public.friendships FOR EACH ROW EXECUTE FUNCTION public.prevent_duplicate_friend_request();

CREATE TRIGGER on_auth_user_created AFTER INSERT ON auth.users FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();


