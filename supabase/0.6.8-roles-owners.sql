-- 0.6.8: roles, ticks, and admin owners.
--
-- Safe to run on the live project more than once. It only adds things; no existing
-- function signature is changed, so an older phone keeps working exactly as it did.
--
-- What it adds:
--   1. username_grants.partner - the black tick. The blue tick is the existing `verified`.
--   2. admin_owners            - the Google addresses that get the admin panel.
--   3. pre_re_my_grant         - one call at startup: name, level, features, both ticks.
--   4. pre_re_admin_owners / pre_re_admin_owner_add / pre_re_admin_owner_remove
--   5. pre_re_admin_set_tick   - blue / black / none, in one call.

-- 1. The black tick ---------------------------------------------------------------
alter table public.username_grants
  add column if not exists partner boolean not null default false;

-- 2. Who may open the admin panel --------------------------------------------------
create table if not exists public.admin_owners (
  email      text primary key,
  note       text,
  created_at timestamptz not null default now()
);
alter table public.admin_owners enable row level security;
-- No policies: the table is only reachable through the functions below.

-- 3. This phone's whole grant, in one call -----------------------------------------
-- Returns one row per install. `granted` is false for a name this device does not hold,
-- which is what stops a phone inheriting someone else's level by typing their name.
create or replace function public.pre_re_my_grant(p_device text)
returns jsonb language plpgsql stable security definer set search_path = public as $f$
declare row_grant record;
begin
  if p_device is null or length(btrim(p_device)) < 8 then
    return jsonb_build_object('granted', false);
  end if;
  select u.username, g.level, g.features, g.verified, g.partner
    into row_grant
    from public.usernames u
    left join public.username_grants g on g.username = u.username
   where u.device_id = p_device;
  if not found then
    return jsonb_build_object('granted', false);
  end if;
  return jsonb_build_object(
    'granted', true,
    'username', row_grant.username,
    'level', coalesce(row_grant.level, 'NONE'),
    'features', coalesce(row_grant.features, '{}'::text[]),
    'verified', coalesce(row_grant.verified, false),
    'partner', coalesce(row_grant.partner, false)
  );
end $f$;

-- 4. The owner list ----------------------------------------------------------------
create or replace function public.pre_re_admin_owners(p_key text)
returns jsonb language plpgsql stable security definer set search_path = public as $f$
begin
  if not public.pre_re_is_admin(p_key) then return jsonb_build_object('status','bad_key'); end if;
  return jsonb_build_object(
    'status', 'ok',
    'owners', coalesce((
      select jsonb_agg(jsonb_build_object('email', o.email, 'note', o.note) order by o.email)
        from public.admin_owners o
    ), '[]'::jsonb)
  );
end $f$;

create or replace function public.pre_re_admin_owner_add(p_key text, p_email text)
returns jsonb language plpgsql security definer set search_path = public as $f$
declare v_email text := lower(btrim(coalesce(p_email,'')));
begin
  if not public.pre_re_is_admin(p_key) then return jsonb_build_object('status','bad_key'); end if;
  if v_email !~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$' then
    return jsonb_build_object('status','bad_email');
  end if;
  insert into public.admin_owners(email) values (v_email) on conflict (email) do nothing;
  return jsonb_build_object('status','ok','email',v_email);
end $f$;

create or replace function public.pre_re_admin_owner_remove(p_key text, p_email text)
returns jsonb language plpgsql security definer set search_path = public as $f$
begin
  if not public.pre_re_is_admin(p_key) then return jsonb_build_object('status','bad_key'); end if;
  delete from public.admin_owners where email = lower(btrim(coalesce(p_email,'')));
  return jsonb_build_object('status','ok');
end $f$;

-- 5. Ticks -------------------------------------------------------------------------
-- p_tick: 'blue' (admin role), 'black' (partner), 'none'. Blue wins if both are asked
-- for, so a person never shows two ticks at once.
create or replace function public.pre_re_admin_set_tick(p_key text, p_username text, p_tick text)
returns jsonb language plpgsql security definer set search_path = public as $f$
declare v_name text := public.pre_re_normalise(p_username); v_tick text := lower(btrim(coalesce(p_tick,'none')));
begin
  if not public.pre_re_is_admin(p_key) then return jsonb_build_object('status','bad_key'); end if;
  if v_tick not in ('blue','black','none') then return jsonb_build_object('status','bad_tick'); end if;
  if not exists (select 1 from public.usernames where username = v_name) then
    return jsonb_build_object('status','no_such_name');
  end if;
  insert into public.username_grants(username, level, features, verified, partner)
  values (v_name, 'NONE', '{}'::text[], v_tick = 'blue', v_tick = 'black')
  on conflict (username) do update
    set verified = (v_tick = 'blue'),
        partner  = (v_tick = 'black');
  return jsonb_build_object('status','ok','tick',v_tick);
end $f$;

-- The addresses below are examples: the app adds real ones from Admin panel -> Access,
-- once the passphrase is in. Nothing here grants anything on its own.

-- 6. "Is this address an owner?" — asked by the app on every sign-in ---------------
-- Deliberately a yes/no only: it never lists the owners, so a stolen publishable key
-- cannot enumerate them, and the answer is only useful if you already know the address.
create or replace function public.pre_re_is_owner(p_email text)
returns boolean language sql stable security definer set search_path = public as $f$
  select exists (
    select 1 from public.admin_owners o
     where o.email = lower(btrim(coalesce(p_email, '')))
  );
$f$;

grant execute on function public.pre_re_is_owner(text) to anon, authenticated;
grant execute on function public.pre_re_my_grant(text) to anon, authenticated;
grant execute on function public.pre_re_admin_owners(text) to anon, authenticated;
grant execute on function public.pre_re_admin_owner_add(text, text) to anon, authenticated;
grant execute on function public.pre_re_admin_owner_remove(text, text) to anon, authenticated;
grant execute on function public.pre_re_admin_set_tick(text, text, text) to anon, authenticated;
