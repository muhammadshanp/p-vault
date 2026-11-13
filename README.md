# P Vault

Website: **https://muhammadshanp.github.io/p-vault-website/** (source: [p-vault-website](https://github.com/muhammadshanp/p-vault-website))

A private vault for photos, PDFs, text notes and Instagram Reels. Android only. Local by default, with optional Google Drive backup.

## Download

Latest APK: **https://github.com/muhammadshanp/p-vault/releases/latest/download/P-Vault.apk** (Android 8.0 or newer). All versions are on the [Releases page](https://github.com/muhammadshanp/p-vault/releases). Once installed, P Vault offers later updates by itself.

## What's new

- **0.5.8** — The navigation pill slides smoothly between tabs and follows your finger while swiping.
- **0.5.7** — New floating navigation capsule with a glowing pill for the current tab; lists scroll behind it and the + button sits above it.
- **0.5.6** — Floating bottom navigation bar with rounded corners. New Settings layout. Push notifications from the P Vault team through Firebase, with an admin panel for the owner to send them. The start-up logo fades out smoothly. A bell icon at the top lists received notifications. A weekly suggestion to connect Google Drive when no backup folder is set.
- **0.5.5** — Restoring a backup adds it to the vault and keeps the files already on the phone. Reels show the Instagram logo.
- **0.5.4** — Reliable Google Drive backups: every backup has its own name, clean-up never removes the newest one, and the write is verified.
- **0.5.3** — Slide-in menu from the three-line button; profile picture at the top right.
- **0.5.2** — Updates download and install from inside the app; smoother swiping between tabs.
- **0.5.1** — Rebuilt bottom navigation and an open/close animation for Settings.
- **0.5.0** — Redesigned interface.

## What it does

- Your profile: name and photo, stored on the device and included in Drive backups when you use them.
- A clean white theme by default, with an optional flat Dark theme.
- Import images (system photo picker) and PDFs (system file picker) — no storage permission needed.
- Write, view and edit plain text notes.
- A WhatsApp-style Chats view inside Notes. Create your own chats and add text, photo and voice messages (voice notes need the microphone permission). Give each chat its own picture from the photo icon in its header or the chat manager, and change or remove it from the same menu. Rename or delete chats from the edit button beside the Chats title. Every message has a saved tick; tap a message to select it, then edit or delete it. Messages stay on the device.
- Save Instagram Reels as links, organised into categories (Pending, Tech, AI, Anime, Others, plus your own). Links are validated before saving.
- Share a reel from Instagram straight into P Vault; it opens the save dialog with Pending selected.
- Browse in five tabs: Files, Reels, Images, PDFs, Notes. Search by title, filter reels by category.
- Swipe left or right on the content to move between tabs.
- The Notes tab opens Chats by default; switch the Chats/Notes chips to see plain notes.
- A floating + button on every tab, including Home: add a file, reel, photo, PDF, note or chat.
- Rename and delete items. Reels can be moved between categories; categories can be renamed (Pending is reserved).
- Tap the pencil on any section to select multiple files, reels, notes or chats and delete them together.
- Search hints and item counts follow the current section.
- Optionally locked behind a PIN (salted SHA-256 hash) and optional fingerprint. On first launch, P Vault asks whether you want a PIN; choose Skip to use it without a lock. The fingerprint prompt opens automatically when you return to the app; PIN is the fallback. Five wrong PIN attempts lock the app for 30 seconds. Auto-lock can run immediately, after 30 seconds, after 1 minute, or after 5 minutes. The launch PIN can be turned off in Settings.
- A full Settings screen: theme, app lock on/off, change PIN, fingerprint unlock, auto-lock delay, auto-backup on/off, backup reminder interval, backups kept, export/import, clear vault data, and About/version.
- Screenshots, screen recording and the recent-apps preview are allowed.
- Optional Google Drive backup through Android's system folder picker. Install the free Google Drive app, sign in there, and pick a Drive folder once. No Google Cloud project or paid plan is needed. Local storage stays primary. A dedicated Google account just for backups is a good idea. Backups are not encrypted.
- On launch, checks the public GitHub Releases page for a newer version and shows an **Update available** dialog. Tap **Update now** and the release APK downloads inside P Vault, is checked (it must be P Vault, not older, and signed with the same key), and is handed to Android's installer. Android still asks you to confirm, and the first time it asks you to allow P Vault to install apps. If the in-app download fails, **Use browser** opens the download instead.
- Everything lives in the app's private storage. The network is used for the GitHub version check, the APK download from the GitHub release when you choose to update, and Firebase Cloud Messaging, which registers the device with Google so push notifications can be delivered; Drive uploads are handled by the Google Drive app. There is no backend and no other server.

## Stack

- Kotlin 2.1 / Jetpack Compose (Material 3)
- Coil for images, built-in `PdfRenderer` for PDFs
- `androidx.biometric` for fingerprint
- Android Storage Access Framework (`DocumentsContract`) for the Drive folder
- No database, no backend of its own, no blur effects. P Vault writes a normal zip; the Google Drive app uploads it. One HTTPS request checks the latest GitHub release on launch. Firebase Cloud Messaging delivers push notifications.

Metadata is the filename: `<timestamp>_<title>.<ext>`. Reels are small `.reel` files containing the URL, grouped in `reels/NN_<Category>` folders. `reels/00_Pending` is created automatically for shared reels. Chats are folders in `chats/NN_<Name>`, one timestamped `.txt` file per message; editing rewrites that file, deleting removes it. A chat picture is stored as `avatar_<timestamp>` in the same folder.

## Build

Requirements: JDK 17+ and the Android SDK (platform 35, build-tools 35.0.0).

```powershell
.\gradlew assembleRelease
```

The APK is written to `app\build\outputs\apk\release\app-release.apk`. It is an optimised release build signed with the debug key, because every earlier P Vault was signed with that key and updates must match it. `build.ps1` does the same and copies the result to `P-Vault.apk`.

## Updates

P Vault reads the latest release from `https://api.github.com/repos/muhammadshanp/p-vault/releases/latest` when it opens. If the release tag is newer than the installed version, it shows **Update available**; **Update now** downloads the release APK inside the app and opens Android's installer.

To publish an update:

1. Raise `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Merge to `main`.

The **Release** workflow then builds `P-Vault.apk`, checks it is signed with the original key, and publishes a release tagged `v<versionName>`. If a draft release with that tag exists, its notes are kept and the APK is attached to it. A version that is already published is left alone. The workflow needs the `DEBUG_KEYSTORE_BASE64` repository secret (the base64 text of the `debug.keystore` that signed earlier releases).

To release by hand instead, build `P-Vault.apk` with `build.ps1` on the original PC, create a GitHub release with a tag such as `v0.5.5`, attach the APK, and publish it.

The tag must contain the version number (`v0.5.5` or `0.5.5` both work). No update file needs to be uploaded separately. Offline launches skip the check silently.

## Notifications

P Vault receives push notifications through Firebase Cloud Messaging (free plan), so a message arrives even when the app is closed. Android 13 and newer ask once for permission to show notifications; Settings → Notifications opens Android's switch for them.

### Admin panel

The owner can send a notification to all users from inside the app:

1. Open the menu, then press and hold **Version …** at the bottom. The admin panel opens.
2. Tap **Sign in with Google** and choose the Google account that owns the Firebase project.
3. Write a title and message and tap **Send to all users**.

Notifications sent from the admin panel are also saved on each phone and listed behind the **bell icon** at the top of the app, with a dot when there is something new. After the first sign-in, **Admin panel** stays in the menu on that phone. Google decides who may send: only accounts with access to the Firebase project are accepted, and no key or password is stored in the app. One-time setup in the Firebase console: add the SHA-1 of the signing key under Project settings → Your apps, and enable Google under Authentication → Sign-in method.

### Firebase console

Messages sent this way appear in the status bar, but are only added to the bell list if P Vault is open when they arrive. To send a message to everyone without the app, open the [Firebase console](https://console.firebase.google.com/project/p-vault-app/messaging), choose **Messaging** → **New campaign** → **Notifications**, write the title and text, target the app `com.dudeapp.vault` (or the topic `all`), and publish. Only people with access to the Firebase project can send.

`app/google-services.json` identifies the Firebase project. It contains identifiers, not passwords. Firebase Analytics is not included.

## Data and reinstall

- **Updating the app keeps everything.** Installing a new APK over the old one does not touch the vault, settings, profile or Drive connection.
- **Uninstalling deletes the local vault.** Android removes app-private files. Your Google Drive backups are not deleted.
- **Android Auto Backup is enabled.** When Google backup is on and the vault fits Android's backup quota (about 25 MB), Android can restore the app-private files after a reinstall. This is not guaranteed and depends on the phone and backup timing.
- **If Auto Backup does not restore**, the first launch with no local data shows **Restore your vault?**. Choose **Restore from Drive**, pick the latest `P_Vault_*.zip`, and the app restores it.
- **Reconnect Drive once after a reinstall.** Android revokes the folder permission even when Auto Backup restores the saved folder name. Tap **Connect** and pick the same folder again; the existing backups stay there.
- **An empty vault is not auto-backed up.** A fresh install cannot add an empty zip and push out a good backup. Normal **Backups kept** pruning still applies when a real backup is made.

Auto Backup stores app data in the user's Google account, including vault files and settings. The Drive folder backup remains the reliable full copy.

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
- **Restore merges.** Files already on the phone stay. Items from the backup are added; ones the phone already has are skipped, and a vault item or reel with the same name but different content is kept as a second copy. Reel categories and chats with the same name are combined.

Backups are ordinary zip files, not encrypted. Anyone with access to the Google account can read them. Keep P Vault open while a backup or restore is running; Android can stop a backgrounded app on a large vault.

Drive backup is one-way, not live sync. Auto-backup runs only while P Vault is open; if you close the app before the 20-second wait, the pending change is retried on the next launch when Drive is connected. The backup reminder appears only when you open the app; it is not a background notification.

P Vault writes to the folder through Android's storage provider, and the Google Drive app does the network upload. The app's only other network use is the GitHub update check and update download. If the Drive app reports that storage is full, free space and back up again.


## Settings

Tap the three-line menu button at the top left to slide the menu in: Profile, Settings, Back up now, Check for updates and About. Your profile picture at the top right opens your profile. Open Settings from the menu. It includes:

- Theme: Light or Dark.
- App lock on/off, change PIN, fingerprint unlock, and auto-lock delay.
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

- PIN + fingerprint is an app lock, not encryption. The app lock is optional, and screenshots/recents are allowed, so anything visible on screen can be captured. A rooted/forensic device can still read app-private files. Drive backups are plain zips.
- Chats are local notes in a WhatsApp-style view. There is no messaging or sync. Drive backup is optional and one-way.
- Uninstalling the app deletes the local vault. Drive backups remain in Google Drive if you made them.
- Deleting a chat deletes its messages. Deleting all chats does not recreate the default chats.
- Forgetting the PIN while app lock is on means no way in.
- Drive backup is one-way and auto-backup runs only while P Vault is open. A backup can be interrupted if Android kills the app. Restore adds the backup to the local vault and never removes what is already on the phone. The connected folder keeps the number of P_Vault zips you choose in Settings; use a dedicated folder per phone.
- Drive backup requires the Google Drive app and a folder you grant access to. If the app is uninstalled or the folder is removed, connect again.

## Package

`com.dudeapp.vault` — version 0.5.8 (versionCode 38).
