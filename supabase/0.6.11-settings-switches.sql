-- 0.6.11: the switch that needs no update.
--
-- Everything here is meant to be changed from Admin panel -> Access and take effect on
-- every phone at once. A phone reads these values on launch (and when the Account page
-- opens); it never ships them inside an APK, so flipping one costs no install.

-- 1. Where the switches live -------------------------------------------------------
create table if not exists public.app_settings (
  id             int primary key default 1,
  rename_allowed boolean not null default true,
  updated_at     timestamptz not null default now(),
  constraint app_settings_single_row check (id = 1)
);
insert into public.app_settings (id) values (1) on conflict (id) do nothing;
alter table public.app_settings enable row level security;
-- No policies: read through pre_re_public_settings, written through the admin function.

-- 2. What every phone may read ------------------------------------------------------
-- Only non-secret switches belong here. Nothing that identifies a person, and nothing
-- that would let a caller learn the owner list or a granted level.
create or replace function public.pre_re_public_settings()
returns table(rename_allowed boolean)
language sql stable security definer set search_path = public as $f$
  select s.rename_allowed from public.app_settings s where s.id = 1;
$f$;

-- 3. The owner flips it -------------------------------------------------------------
-- NOTE: `pre_re_is_admin` is not defined here. On the live project it compares against the
-- single row in public.admin_keys (see supabase/0.6.21-owner-key.sql), so the passphrase is
-- changed by updating that row, not by rewriting the function.
create or replace function public.pre_re_admin_set_rename(p_key text, p_allowed boolean)
returns boolean language plpgsql security definer set search_path = public as $f$
begin
  if not public.pre_re_is_admin(p_key) then return false; end if;
  insert into public.app_settings (id, rename_allowed, updated_at)
  values (1, coalesce(p_allowed, true), now())
  on conflict (id) do update
    set rename_allowed = excluded.rename_allowed, updated_at = now();
  return true;
end $f$;

-- 4. Renaming yourself obeys the switch ----------------------------------------------
-- Same function as before, with one guard at the top: with the switch off, a phone cannot
-- change its own name even if it never received an update.
create or replace function public.pre_re_rename_self(p_device text, p_username text)
returns jsonb language plpgsql security definer set search_path = public as $f$
declare v_new text := public.pre_re_normalise(p_username); holder text; reason text; allowed boolean;
begin
  select s.rename_allowed into allowed from public.app_settings s where s.id = 1;
  if coalesce(allowed, true) = false then
    return jsonb_build_object('status','rename_off');
  end if;
  if p_device is null or length(btrim(p_device)) < 8 then
    return jsonb_build_object('status','bad_device');
  end if;
  reason := public.pre_re_reason(p_username);
  if reason <> 'ok' then return jsonb_build_object('status', reason); end if;
  perform pg_advisory_xact_lock(hashtextextended('pre_re_names', 0));
  select username into holder from public.usernames where device_id = p_device;
  if not found then return jsonb_build_object('status','no_name'); end if;
  if holder = v_new then return jsonb_build_object('status','already_yours','name',v_new); end if;
  if exists (select 1 from public.reserved_names r where r.name = v_new) then
    return jsonb_build_object('status','reserved');
  end if;
  if exists (select 1 from public.usernames u where u.username = v_new) then
    return jsonb_build_object('status','taken');
  end if;
  begin
    update public.usernames set username = v_new where device_id = p_device;
    update public.username_grants set username = v_new where username = holder;
  exception when unique_violation then return jsonb_build_object('status','taken');
  end;
  return jsonb_build_object('status','ok','name',v_new);
end $f$;

grant execute on function public.pre_re_public_settings() to anon, authenticated;
grant execute on function public.pre_re_admin_set_rename(text, boolean) to anon, authenticated;
grant execute on function public.pre_re_rename_self(text, text) to anon, authenticated;

-- 5. The name belongs to the email ---------------------------------------------------
-- Until now a name belonged to the install. The email is the account key: the name,
-- the access level and both ticks follow it, so the same person gets the same name on a
-- new phone, and the owner's list can be read by address rather than guessed at.
alter table public.usernames
  add column if not exists email text;

create unique index if not exists usernames_email_unique
  on public.usernames (lower(email))
  where email is not null;

-- Only the device that holds the name may attach an address to it, so one phone cannot
-- re-point someone else's name, and one address cannot hold two names.
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

-- The name an address holds. A display name only; nothing else about the person.
create or replace function public.pre_re_name_for_email(p_email text)
returns text language sql stable security definer set search_path = public as $f$
  select u.username from public.usernames u
   where lower(u.email) = lower(btrim(coalesce(p_email, '')))
   limit 1;
$f$;

grant execute on function public.pre_re_link_email(text, text, text) to anon, authenticated;
grant execute on function public.pre_re_name_for_email(text) to anon, authenticated;
