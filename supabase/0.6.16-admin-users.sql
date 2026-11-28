-- 0.6.16: the owner's user list.
--
-- The admin panel could only show usernames, and only through the Firebase names page that
-- was never wired into the panel. This file gives the panel one server call that answers
-- "who is registered, and what do they have?" — including the addresses now attached to
-- names, which is what makes it a user list rather than a name list.
--
-- Still behind the owner passphrase: the SHA-256 of the phrase is compared in the database,
-- and the function refuses everything else. The passphrase is never stored on a phone.
--
-- Safe to run more than once. Expected answer: Success. No rows returned.

-- 0. When the name was claimed ---------------------------------------------------------
-- Older projects have no such column; the list still works without it, but "joined" is the
-- first thing anyone looks for on a user list, so it is added when it is missing.
alter table public.usernames
  add column if not exists created_at timestamptz not null default now();

-- 1. Every user, newest first ----------------------------------------------------------
create or replace function public.pre_re_admin_users(p_key text)
returns jsonb language plpgsql stable security definer set search_path = public as $f$
declare v_rows jsonb;
begin
  if not public.pre_re_is_admin(p_key) then
    return jsonb_build_object('status', 'bad_key');
  end if;

  select coalesce(jsonb_agg(row order by row->>'created_at' desc nulls last), '[]'::jsonb)
    into v_rows
    from (
      select jsonb_build_object(
        'username',   u.username,
        'email',      u.email,
        'created_at', u.created_at,
        'level',      coalesce(g.level, 'NONE'),
        'features',   coalesce(to_jsonb(g.features), '[]'::jsonb),
        'verified',   coalesce(g.verified, false),
        'partner',    coalesce(g.partner, false)
      ) as row
        from public.usernames u
        left join public.username_grants g on g.username = u.username
    ) rows;

  return jsonb_build_object('status', 'ok', 'users', v_rows);
end $f$;

grant execute on function public.pre_re_admin_users(text) to anon, authenticated;

-- 2. One user, in full ---------------------------------------------------------------
-- What the panel opens when a row is tapped: the same facts, plus whether the address is an
-- owner, so the list can say "admin" without a second call per person.
create or replace function public.pre_re_admin_user(p_key text, p_username text)
returns jsonb language plpgsql stable security definer set search_path = public as $f$
declare v_name text := public.pre_re_normalise(p_username); found_user record;
begin
  if not public.pre_re_is_admin(p_key) then
    return jsonb_build_object('status', 'bad_key');
  end if;

  select u.username, u.email, u.created_at,
         coalesce(g.level, 'NONE') as level,
         coalesce(to_jsonb(g.features), '[]'::jsonb) as features,
         coalesce(g.verified, false) as verified,
         coalesce(g.partner, false) as partner
    into found_user
    from public.usernames u
    left join public.username_grants g on g.username = u.username
   where u.username = v_name;

  if not found then
    return jsonb_build_object('status', 'not_found');
  end if;

  return jsonb_build_object(
    'status', 'ok',
    'username', found_user.username,
    'email', found_user.email,
    'created_at', found_user.created_at,
    'level', found_user.level,
    'features', found_user.features,
    'verified', found_user.verified,
    'partner', found_user.partner,
    'admin', exists (
      select 1 from public.admin_owners o
       where lower(o.email) = lower(coalesce(found_user.email, ''))
    )
  );
end $f$;

grant execute on function public.pre_re_admin_user(text, text) to anon, authenticated;

-- 3. A count, for the panel's heading ------------------------------------------------
create or replace function public.pre_re_public_count()
returns jsonb language sql stable security definer set search_path = public as $f$
  select jsonb_build_object(
    'users', (select count(*) from public.usernames),
    'with_email', (select count(*) from public.usernames where email is not null)
  );
$f$;

grant execute on function public.pre_re_public_count() to anon, authenticated;
