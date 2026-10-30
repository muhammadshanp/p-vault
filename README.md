# P Vault

A private vault for photos, PDFs, text notes and Instagram Reels. Android only. Local by default, with optional Google Drive backup.

## What it does

- Your profile: name and photo, stored on the device and included in Drive backups when you use them.
- A clean white theme by default, with an optional flat Dark theme.
- Import images (system photo picker) and PDFs (system file picker) — no storage permission needed.
- Write, view and edit plain text notes.
- A WhatsApp-style Chats view inside Notes, with Anime, Tech, AI, Movies and College chats, plus your own. Give each chat its own picture from the photo icon in its header or the chat manager, and change or remove it from the same menu. Rename or delete chats from the edit button beside the Chats title. Every message has a saved tick; tap a message to select it, then edit or delete it. Messages stay on the device.
- Save Instagram Reels as links, organised into categories (Pending, Tech, AI, Anime, Others, plus your own). Links are validated before saving.
- Share a reel from Instagram straight into P Vault; it opens the save dialog with Pending selected.
- Browse in five tabs: Files, Reels, Images, PDFs, Notes. Search by title, filter reels by category.
- The Notes tab opens Chats by default; switch the Chats/Notes chips to see plain notes.
- A contextual + in every section: add a file, reel, photo, PDF or note.
- Rename and delete items. Reels can be moved between categories; categories can be renamed (Pending is reserved).
- Search hints and item counts follow the current section.
- Locked behind a PIN (salted SHA-256 hash) and optional fingerprint. The fingerprint prompt opens automatically when you return to the app; PIN is the fallback. Five wrong PIN attempts lock the app for 30 seconds. Auto-lock can run immediately, after 30 seconds, after 1 minute, or after 5 minutes.
- A full Settings screen: theme, change PIN, fingerprint unlock, auto-lock delay, auto-backup on/off, backup reminder interval, backups kept, export/import, clear vault data, and About/version.
- Screenshots, screen recording and the recent-apps preview are blocked.
- Optional Google Drive backup through Android's system folder picker. Install the free Google Drive app, sign in there, and pick a Drive folder once. No Google Cloud project or paid plan is needed. Local storage stays primary. A dedicated Google account just for backups is a good idea. Backups are not encrypted.
- On launch, checks the public GitHub Releases page for a newer version and shows an **Update available** dialog. The Update button opens the release page; it never installs anything by itself.
- Everything lives in the app's private storage. The only network request is the GitHub version check; Drive uploads are handled by the Google Drive app. There is no backend and no other server.

## Stack

- Kotlin 2.1 / Jetpack Compose (Material 3)
- Coil for images, built-in `PdfRenderer` for PDFs
- `androidx.biometric` for fingerprint
- Android Storage Access Framework (`DocumentsContract`) for the Drive folder
- No database, no backend, no blur effects. P Vault writes a normal zip; the Google Drive app uploads it. One HTTPS request checks the latest GitHub release on launch.

Metadata is the filename: `<timestamp>_<title>.<ext>`. Reels are small `.reel` files containing the URL, grouped in `reels/NN_<Category>` folders. `reels/00_Pending` is created automatically for shared reels. Chats are folders in `chats/NN_<Name>`, one timestamped `.txt` file per message; editing rewrites that file, deleting removes it. A chat picture is stored as `avatar_<timestamp>` in the same folder.

## Build

Requirements: JDK 17+ and the Android SDK (platform 35, build-tools 35.0.0).

```powershell
.\gradlew assembleDebug
```

The debug APK is written to `app\build\outputs\apk\debug\app-debug.apk`.

## Updates

P Vault reads the latest release from `https://api.github.com/repos/muhammadshanp/p-vault/releases/latest` when it opens. If the release tag is newer than the installed version, it shows **Update available** with a button that opens the release page.

To publish an update:

1. Raise `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Build `P-Vault.apk`.
3. Create a GitHub release with a tag such as `v1.10`, attach `P-Vault.apk`, and publish it.

The tag must contain the version number (`v1.10` or `1.10` both work). No update file needs to be uploaded separately. Offline launches skip the check silently.

## Sharing reels

In Instagram, open a reel, tap Share, and choose P Vault. The app opens on the Reels tab with the link filled in and Pending selected. Save it there, then move it to Tech, AI, Anime, Others or your own category from its ⋮ menu whenever you are ready. You can also paste a link manually with +. Only valid `http`/`https` links can be saved.

## Google Drive backup

This uses the free Google Drive app and Android's own folder picker. There is no Google Cloud project to create and P Vault never asks for your Google password.

1. Install the Google Drive app from Play Store and add your Google account there.
2. Open your profile in P Vault and find **Google Drive backup**.
3. Tap **Connect**. Android's file picker opens. Choose **Drive**, then pick or create a folder such as `P Vault Backups`, and allow access.
4. Tap **Back up now** to write `P_Vault_<timestamp>.zip` into that folder.

The folder permission is remembered, so you connect once. If you uninstall the Drive app or delete the folder, connect again.

- **Auto-backup:** about 20 seconds after you stop changing the vault, P Vault writes a fresh full zip while the app is open. It only runs when a Drive folder is connected. Turn it off in Settings if you prefer manual backups only. If you close the app before the wait finishes, the pending change is retried when you next open P Vault with Drive connected. This is one-way; it never downloads changes from Drive.
- **Backup reminder:** when you open P Vault with Drive connected, it offers a backup when the last successful one is older than the interval you chose in Settings (daily, weekly, every 2 weeks or monthly). **Later** snoozes it for the same interval. This is only an in-app reminder; it does not upload by itself.
- **Backups kept:** the connected folder keeps the newest 1, 2, 3, 5 or 10 `P_Vault_*.zip` files as set in Settings. Older P_Vault backups in that folder are deleted. Old `Dude_*.zip` files are left alone.
- **Restore from a zip file (local or Drive)** opens the same Android picker, so you can pick a backup from the Drive folder or from the phone.

Backups are ordinary zip files, not encrypted. Anyone with access to the Google account can read them. Keep P Vault open while a backup or restore is running; Android can stop a backgrounded app on a large vault.

Drive backup is one-way, not live sync. Auto-backup runs only while P Vault is open; if you close the app before the 20-second wait, the pending change is retried on the next launch when Drive is connected. The backup reminder appears only when you open the app; it is not a background notification.

P Vault writes to the folder through Android's storage provider, and the Google Drive app does the network upload. The app's only other network use is the GitHub update check. If the Drive app reports that storage is full, free space and back up again.


## Settings

Open Settings from the gear beside your name. It includes:

- Theme: White or Dark.
- Change PIN, fingerprint unlock, and auto-lock delay.
- Auto-backup on/off and the backup reminder interval.
- How many backups to keep in the Google Drive folder.
- Export the vault to any zip file, import a P Vault backup, or clear all local vault data.
- About: the installed version and the local-first behaviour.

Clearing local data removes photos, PDFs, notes, reels, chats and your profile. It does not delete Google Drive backups or the PIN, and it does not trigger an automatic backup of the empty vault. You can import a backup afterwards.

## Install

Enable USB debugging on the phone, then:

```powershell
adb install -r P-Vault.apk
```

Or copy `P-Vault.apk` to the phone and open it (allow "install unknown apps" for your file manager).

## Limits

- PIN + fingerprint is an app lock, not encryption. Screenshots and recents are blocked, but a rooted/forensic device can still read app-private files. Drive backups are plain zips.
- Chats are local notes in a WhatsApp-style view. There is no messaging or sync. Drive backup is optional and one-way.
- Uninstalling the app deletes the local vault. Drive backups remain in Google Drive if you made them.
- Deleting a chat deletes its messages. Deleting all chats does not recreate the default chats.
- Forgetting the PIN means no way in.
- Drive backup is one-way and auto-backup runs only while P Vault is open. A backup can be interrupted if Android kills the app. Restore replaces the local vault. The connected folder keeps the number of P_Vault zips you choose in Settings; use a dedicated folder per phone.
- Drive backup requires the Google Drive app and a folder you grant access to. If the app is uninstalled or the folder is removed, connect again.

## Package

`com.dudeapp.vault` — version 1.9 (versionCode 10).
