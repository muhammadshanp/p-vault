-- 0.6.21: the owner passphrase, and the owner address, as the live project actually has them.
--
-- Written after reading the live database, because the earlier notes in this repository
-- described the wrong mechanism. On the live project `pre_re_is_admin` does NOT hash a
-- phrase and compare it to a constant in the function body. It reads one row:
--
--     select exists (
--       select 1 from public.admin_keys where id = 1
--        and key_hash = lower(trim(coalesce(p_key, '')))
--     );
--
-- so the app sends the SHA-256 of what was typed, and the table holds that hash. Two
-- consequences worth knowing:
--   * rewriting `pre_re_is_admin` is not how the passphrase is changed — the row is;
--   * if the row and the phrase in the app disagree, every owner function refuses with
--     'bad_key', the "Make this account an owner" step fails, and the admin panel never
--     appears. That is exactly what happened: the row held a different phrase.
--
-- Run this once per project. Safe to run more than once. Change the two values below first
-- if you want a different phrase or a different owner address.
-- Expected answer: Success. No rows returned.

-- 1. The passphrase ---------------------------------------------------------------
-- The phrase is stored as its SHA-256, never in plain text. pgcrypto lives in the
-- `extensions` schema on Supabase, so it is called by its full name.
create extension if not exists pgcrypto with schema extensions;

insert into public.admin_keys (id, key_hash, updated_at)
values (
  1,
  encode(extensions.digest('prere-owner-willow-3db4', 'sha256'), 'hex'),
  now()
)
on conflict (id) do update
  set key_hash = excluded.key_hash,
      updated_at = now();

-- 2. The owner address ------------------------------------------------------------
-- The panel is offered to an address on this list, and to nothing else. Without at least
-- one row here, no address is an owner, whatever the passphrase says.
insert into public.admin_owners (email, note)
values ('muhammadshanpofficial@gmail.com', 'owner')
on conflict (email) do update set note = 'owner';

-- 3. Proof ------------------------------------------------------------------------
-- Run this on its own afterwards; it should answer true for both.
--
--   select public.pre_re_is_admin(
--            encode(extensions.digest('prere-owner-willow-3db4', 'sha256'), 'hex')
--          ) as passphrase_works,
--          public.pre_re_is_owner('muhammadshanpofficial@gmail.com') as address_is_owner,
--          public.pre_re_account('muhammadshanpofficial@gmail.com') as account;
