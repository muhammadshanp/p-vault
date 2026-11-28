# Supabase username and access service

The Android app uses the Supabase project `pre-re` for claimed usernames and owner-managed access. It does **not** upload vault files, notes, photos, reels or local chat messages to Supabase. An optional Google Drive backup or user-initiated export is separate from this service.

## Data and access

- `usernames` stores the normalised claimed name, an install identifier and claim time.
- `username_grants` stores per-name access level, enabled features and the blue verified flag.
- `reserved_names` stores names set aside by the owner and optional notes.
- `admin_keys` stores the owner passphrase hash, not the passphrase. The app must never embed the passphrase or a service-role key.

Direct table access is restricted by row-level security; the app uses server functions. The owner can list names, set access, reserve names, rename a user and set the blue tick in **Admin panel → Access**. Name changes preserve the device binding and grants. A phone fetches its current name at startup when online.

The username service is not a general-purpose authentication system. The install ID is not a substitute for a verified account. Do not build private online messaging on it without stronger server-verified authentication and appropriate authorization.

The owner passphrase is hashed in the app before it is sent. That hash is effectively a bearer credential: anyone who obtains it can use it as the key, and a weak passphrase is vulnerable to guessing. Do not log or publish either value; use a long, unique passphrase. Owner credentials should be rotated if exposed.

## Server functions

`pre_re_check`, `pre_re_claim`, `pre_re_my_name`, and `pre_re_my_access` provide the app's name and access operations. Owner functions include `pre_re_is_admin`, `pre_re_admin_names`, `pre_re_admin_set_access`, `pre_re_admin_reserved`, `pre_re_admin_reserve`, `pre_re_admin_unreserve`, `pre_re_admin_rename` and `pre_re_admin_verify`. The server also retains `pre_re_release`; the app does not expose name release to users. The repository contains a follow-up safety migration in `supabase/0.6.5-safety.sql`; it is not a complete record of the existing live database setup.

## Messaging

The green tick and online messages to the owner are **not implemented**. Existing Chats are local. A separate online messaging feature would require users' informed consent, server-verified identities and access controls; only messages sent using that feature should be uploaded. Supabase Google sign-in was not configured when last checked.

## 0.6.8: roles and admin owners

`supabase/0.6.8-roles-owners.sql` is the migration for this release. It adds:

- `username_grants.partner` — the black tick. `verified` stays the blue tick (admin role).
- `admin_owners` — the Google addresses that may open the admin panel. Filled from
  **Admin panel → Access → Admin accounts**.
- `pre_re_is_owner(email)` — a yes/no the app asks on sign-in, so an owner never needs the
  passphrase and the list is never handed out.
- `pre_re_my_grant(device)` — name, level, features and both ticks in one call at startup.
- `pre_re_admin_set_tick(key, name, tick)` — `blue`, `black` or `none`.
- `pre_re_admin_owners`, `pre_re_admin_owner_add`, `pre_re_admin_owner_remove`.

Run it in the Supabase SQL editor before installing 0.6.8-pro. It is safe to run twice and
changes no existing signature, so an older phone keeps working.

## The owner passphrase

`pre_re_is_admin(p_key)` takes the **SHA-256** of what the owner typed and compares it with
`public.admin_keys.key_hash` (row `id = 1`). The phrase itself is not stored anywhere in the
app or in this repository; the row is the only copy, and it is a hash.

To change the phrase, update that row — never the function:

```sql
insert into public.admin_keys (id, key_hash, updated_at)
values (1, encode(extensions.digest('your-new-phrase', 'sha256'), 'hex'), now())
on conflict (id) do update set key_hash = excluded.key_hash, updated_at = now();
```

`public.admin_owners` is who may open the admin panel. It starts empty, and an empty list
means nobody is an owner — the panel is then unreachable, because the panel is the only
screen that can add to the list. Seed it with the owner's Google address:

```sql
insert into public.admin_owners (email, note) values ('owner@example.com', 'owner')
on conflict (email) do update set note = 'owner';
```

`supabase/0.6.21-owner-key.sql` does both in one file. Once the app knows an address is an
owner, the Account page's **Make this account an owner** adds the signed-in address with the
passphrase alone.

