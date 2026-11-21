-- Follow-up to reserved_names_rename_and_verified on the live project.
-- Serialize mutations that can claim, reserve or rename the same name.
create or replace function public.pre_re_claim(p_username text, p_device text)
returns jsonb language plpgsql security definer set search_path = public as $f$
declare wanted text := public.pre_re_normalise(p_username); holder text;
begin
  if p_device is null or length(btrim(p_device)) < 8 then return jsonb_build_object('status','bad_device'); end if;
  if public.pre_re_reason(p_username) <> 'ok' then return jsonb_build_object('status',public.pre_re_reason(p_username)); end if;
  perform pg_advisory_xact_lock(hashtextextended('pre_re_names', 0));
  if exists (select 1 from public.reserved_names r where r.name = wanted) then return jsonb_build_object('status','reserved'); end if;
  select device_id into holder from public.usernames where username = wanted for update;
  if found then
    if holder = p_device then return jsonb_build_object('status','already_yours'); end if;
    return jsonb_build_object('status','taken');
  end if;
  begin
    insert into public.usernames (username,device_id) values (wanted,p_device);
  exception when unique_violation then return jsonb_build_object('status','taken'); end;
  return jsonb_build_object('status','ok');
end $f$;

create or replace function public.pre_re_admin_reserve(p_key text, p_name text, p_note text default null)
returns jsonb language plpgsql security definer set search_path = public as $f$
declare v_name text := public.pre_re_normalise(p_name); reason text := public.pre_re_reason(p_name);
begin
  if not public.pre_re_is_admin(p_key) then return jsonb_build_object('status','bad_key'); end if;
  if reason <> 'ok' then return jsonb_build_object('status',reason); end if;
  perform pg_advisory_xact_lock(hashtextextended('pre_re_names', 0));
  if exists (select 1 from public.usernames where username = v_name) then return jsonb_build_object('status','taken'); end if;
  insert into public.reserved_names(name,note) values(v_name,p_note)
    on conflict(name) do update set note=excluded.note;
  return jsonb_build_object('status','ok','name',v_name);
end $f$;

create or replace function public.pre_re_admin_rename(p_key text, p_old text, p_new text)
returns jsonb language plpgsql security definer set search_path = public as $f$
declare v_old text := public.pre_re_normalise(p_old); v_new text := public.pre_re_normalise(p_new);
begin
  if not public.pre_re_is_admin(p_key) then return jsonb_build_object('status','bad_key'); end if;
  perform pg_advisory_xact_lock(hashtextextended('pre_re_names', 0));
  if not exists (select 1 from public.usernames where username=v_old) then return jsonb_build_object('status','no_such_name'); end if;
  if v_new=v_old then return jsonb_build_object('status','same'); end if;
  if public.pre_re_reason(p_new) <> 'ok' then return jsonb_build_object('status',public.pre_re_reason(p_new)); end if;
  if exists (select 1 from public.reserved_names where name=v_new) then return jsonb_build_object('status','reserved'); end if;
  if exists (select 1 from public.usernames where username=v_new) then return jsonb_build_object('status','taken'); end if;
  update public.usernames set username=v_new where username=v_old;
  return jsonb_build_object('status','ok','name',v_new);
end $f$;

create or replace function public.pre_re_my_access(p_username text, p_device text)
returns table(level text, features text[], granted boolean, verified boolean)
language sql stable security definer set search_path = public as $f$
  select coalesce(g.level,'NONE'), coalesce(g.features,'{}'::text[]), true, coalesce(g.verified,false)
    from public.usernames u left join public.username_grants g on g.username=u.username
   where u.username=public.pre_re_normalise(coalesce(p_username,'')) and u.device_id=coalesce(p_device,'')
  union all
  select 'NONE', '{}'::text[], false, false
   where not exists (select 1 from public.usernames u where u.username=public.pre_re_normalise(coalesce(p_username,'')) and u.device_id=coalesce(p_device,''));
$f$;
