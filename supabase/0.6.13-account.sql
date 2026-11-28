-- 0.6.13: the account, not the phone.
--
-- Three faults, one cause: the name belonged to the install, so the email could not
-- recognise it. The email is the account key; this file makes the database agree.
--
--   1. A secured address (owner/admin) is recognised by its email every time, so the
--      panel opens from the login page onwards and not only after a fresh sign-in.
--   2. A name held by an email can be changed by whoever is signed in with that email,
--      from any phone — not only by the one that originally claimed it.
--   3. The email truly holds the name: it is attached to the address, and every phone
--      reads it back by address instead of trusting local storage.
--
-- Safe to run more than once. Expected answer: Success. No rows returned.

-- 1. An admin is an admin by address ---------------------------------------------------
-- The owner list already answers per address; this is the same question under the name
-- the app calls, and it also accepts addresses marked as admins.
create or replace function public.pre_re_is_owner(p_email text)
returns boolean language sql stable security definer set search_path = public as $f$
  select coalesce((
    select true from public.admins a
     where lower(a.email) = lower(btrim(coalesce(p_email, '')))
     limit 1
  ), false);
$f$;

grant execute on function public.pre_re_is_owner(text) to anon, authenticated;

-- 2. Changing your own name -----------------------------------------------------------
-- Rules, in order:
--   * the owner switch still wins: with it off, nobody renames themselves;
--   * a name attached to an email may be renamed by a device presenting that email;
--   * a name attached to an email and claimed on another phone is NOT touched by a
--     device that cannot present the email (that would be stealing a name);
--   * a name with no email yet behaves as before: the claiming device may change it.
create or replace function public.pre_re_rename_self_email(
  p_device text,
  p_username text,
  p_email text default null
)
returns jsonb language plpgsql security definer set search_path = public as $f$
declare
  v_new text := public.pre_re_normalise(p_username);
  v_email text := lower(btrim(coalesce(p_email, '')));
  holder text; holder_email text; reason text; allowed boolean;
begin
  select s.rename_allowed into allowed from public.app_settings s where s.id = 1;
  if coalesce(allowed, true) = false then
    return jsonb_build_object('status', 'rename_off');
  end if;
  if p_device is null or length(btrim(p_device)) < 8 then
    return jsonb_build_object('status', 'bad_device');
  end if;
  reason := public.pre_re_reason(p_username);
  if reason <> 'ok' then return jsonb_build_object('status', reason); end if;

  perform pg_advisory_xact_lock(hashtextextended('pre_re_names', 0));

  -- The name presented by an email: that is the account, wherever it is signed in.
  if v_email <> '' then
    select u.username, lower(u.email) into holder, holder_email
      from public.usernames u
     where lower(u.email) = v_email
     limit 1;
  end if;

  -- No name under that email: fall back to the name this install holds.
  if holder is null then
    select u.username, lower(u.email) into holder, holder_email
      from public.usernames u
     where u.device_id = p_device
     limit 1;
    if holder is null then return jsonb_build_object('status', 'no_name'); end if;
    -- The install's name already belongs to a different email: do not take it.
    if holder_email is not null and v_email <> '' and holder_email <> v_email then
      return jsonb_build_object('status', 'not_yours');
    end if;
    -- An email is signed in and the install holds a name nobody owns: adopt the address.
    if holder_email is null and v_email <> '' then
      begin
        update public.usernames set email = v_email where username = holder;
      exception when unique_violation then
        return jsonb_build_object('status', 'email_taken');
      end;
    end if;
  end if;

  if holder = v_new then
    return jsonb_build_object('status', 'already_yours', 'name', v_new);
  end if;
  if exists (select 1 from public.reserved_names r where r.name = v_new) then
    return jsonb_build_object('status', 'reserved');
  end if;
  if exists (select 1 from public.usernames u where u.username = v_new) then
    return jsonb_build_object('status', 'taken');
  end if;

  begin
    update public.usernames set username = v_new where username = holder;
    update public.username_grants set username = v_new where username = holder;
    -- The names held for other devices point at the same person: keep them together.
    update public.pre_re_devices set username = v_new where username = holder;
  exception when undefined_table then
    -- Older installs have no device table; the two updates above are the whole job.
    null;
  when unique_violation then
    return jsonb_build_object('status', 'taken');
  end;

  return jsonb_build_object('status', 'ok', 'name', v_new);
end $f$;

grant execute on function public.pre_re_rename_self_email(text, text, text) to anon, authenticated;

-- 3. The email holds the name ---------------------------------------------------------
-- Every phone, on launch, asks this one question and gets its name, level and ticks back.
-- A display name and two badges: nothing about the contents of anyone's vault.
create or replace function public.pre_re_account(p_email text)
returns jsonb language sql stable security definer set search_path = public as $f$
  with who as (
    select u.username, lower(u.email) as email
      from public.usernames u
     where lower(u.email) = lower(btrim(coalesce(p_email, '')))
     limit 1
  )
  select jsonb_build_object(
    'name',   (select w.username from who w),
    'email',  (select w.email from who w),
    'level',  (select coalesce(g.level, 'none')
                 from public.username_grants g
                where g.username = (select w.username from who w)
                limit 1),
    'verified', exists (
      select 1 from public.username_grants g
       where g.username = (select w.username from who w) and coalesce(g.verified, false)
    ),
    'partner', exists (
      select 1 from public.username_grants g
       where g.username = (select w.username from who w) and coalesce(g.partner, false)
    ),
    'admin', exists (
      select 1 from public.admins a
       where lower(a.email) = lower(btrim(coalesce(p_email, '')))
    )
  );
$f$;

grant execute on function public.pre_re_account(text) to anon, authenticated;

-- 4. An address change is a link, not a re-point --------------------------------------
-- Kept from 0.6.11: only the device holding the name may attach its address. Nothing to
-- change here; the block is repeated so this file can be run on its own.
create or replace function public.pre_re_link_email(p_device text, p_username text, p_email text)
returns jsonb language plpgsql security definer set search_path = public as $f$
declare v_name text := public.pre_re_normalise(p_username); v_email text := lower(btrim(coalesce(p_email,'')));
begin
  if p_device is null or length(btrim(p_device)) < 8 then
    return jsonb_build_object('status','bad_device');
  end if;
  if v_email !~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$' then
    return jsonb_build_object('status','bad_email');
  end if;
  if not exists (select 1 from public.usernames u where u.username = v_name and u.device_id = p_device) then
    -- A phone signed in with the address that already holds this name may re-link it:
    -- that is the same person on a new phone, not a stranger claiming a name.
    if exists (select 1 from public.usernames u where u.username = v_name and lower(u.email) = v_email) then
      return jsonb_build_object('status','ok');
    end if;
    return jsonb_build_object('status','not_yours');
  end if;
  if exists (
    select 1 from public.usernames u
     where lower(u.email) = v_email and u.username <> v_name
  ) then
    return jsonb_build_object('status','email_taken');
  end if;
  update public.usernames set email = v_email where username = v_name and device_id = p_device;
  return jsonb_build_object('status','ok');
end $f$;

grant execute on function public.pre_re_link_email(text, text, text) to anon, authenticated;
