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
