> **Historical note:** This document describes the earlier pre re rebrand. Version 0.6.6 restored the P Vault name, original icon, and P Vault update source; see [README.md](README.md) for the current state.

# pre re — rebrand notes

This folder is the Android app (Kotlin + Jetpack Compose) rebranded from **P Vault**
to **pre re**, with the supplied logo. Source imported from the `p-vault-main` upload.

## What changed

| Area | Before | After |
| --- | --- | --- |
| Launcher label | `P Vault` / `P Vault Pro` | `pre re` (`versionCode 79`, `versionName 0.5.49`) |
| Launcher icon | generic foreground on `#16181D` | your ring mark on `#111827` |
| Icon densities | `xxxhdpi` only | `mdpi 108` → `xxxhdpi 432` (`drawable-*/ic_launcher_foreground.png`) |
| Adaptive-icon background | `#16181D` | `#111827` (`values/colors.xml`) |
| Gradle project name | `PVault` | `pre-re` (`settings.gradle.kts`) |
| User-visible text | `P Vault` | `pre re` (screens, notifications, dialogs, README) |
| Update source | `muhammadshanp/p-vault` + `P-Vault.apk` | `muhammadshanp/Pre-release-` + `pre-re.apk` |
| "What's new" link | old website `update.html` | release notes: `…/releases/tag/v<version>` |
| Theme catalogue | `p-vault-website/themes.json` | `raw.githubusercontent.com/…/android/theme-catalog/themes.json` |
| About → website | old website | `https://muhammadshanp.github.io/Pre-release-/` (Pages workflow) |
| CI location | `android/.github/workflows` (ignored by GitHub) | root `.github/workflows`, `working-directory: android` |

The icon foreground was generated at the correct adaptive-icon proportions — a 108dp
canvas with the mark inside the 72dp safe zone — and verified against the analytic
area of the ring (0.2727 coverage, exact to 4 decimal places at every density).

## What was deliberately left alone — and why

These look like the brand but are **compatibility identifiers**. Renaming them would
break existing installs, backups, or server config:

| Kept | Where | Why |
| --- | --- | --- |
| `com.dudeapp.vault` | `applicationId`, `namespace`, package dirs | Changes app identity: nothing installs over an existing build, and Firebase/Google config no longer matches. |
| `p-vault-app` | Firebase project id (`AdminPanel.kt`, `Access.kt`, `FirebaseSession.kt`) | Server-side project id. Must match `google-services.json`. |
| `pvault` | `BackupCrypto.EXTENSION` | Backup file extension. Renaming makes old backups unreadable. |
| `PVAULT-ENC-1` | `BackupCrypto.MAGIC` | Header inside encrypted backups, checked on restore. |
| `P_Vault_*` | `DriveBackup.kt` | Backup discovery prefix; old backups would stop being found. |
| `PVaultTheme` | Compose theme function | Internal symbol, never shown to users. |
| `pvault` | `Usernames.kt` reserved list | Reserved-name list; harmless. |

## How to get an APK

### CI — nothing to install

`.github/workflows/build.yml` runs on every push that touches `android/`:

1. **Actions** tab → the latest **Build check** run → **Artifacts** → `test-apk-not-for-release`.
2. That APK is release-optimised (fast Compose) but signed with a throwaway debug key,
   so it installs on a phone but **cannot** replace an installed P Vault.

Publishing a real release: bump `versionCode`/`versionName` in `app/build.gradle.kts`,
merge to `main`. `.github/workflows/release.yml` builds `pre-re.apk`, signs it with
`DEBUG_KEYSTORE_BASE64`, checks the fingerprint, and publishes the GitHub release.
It refuses to publish unless the key matches the one that signed earlier installs.

### Local

```bash
cd android
export ANDROID_HOME=$HOME/Android/Sdk     # JDK 17+, SDK platform 35
./gradlew :app:assembleDebug              # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease            # app/build/outputs/apk/release/app-release.apk
./gradlew :app:testReleaseUnitTest        # unit tests
```

On Windows, `build.ps1` does the release build (edit its two hardcoded JDK/SDK paths).
Or open the `android/` folder in Android Studio and use
**Build → Build Bundle(s) / APK(s) → Build APK(s)**.

## Signing — the one thing that decides whether an update installs

| Goal | Key |
| --- | --- |
| Replace/update existing P Vault installs | the exact keystore that signed 0.5.x, via `PV_KEYSTORE` or `~/.android/debug.keystore` (alias `androiddebugkey`, password `android`) |
| Genuinely new app | a fresh keystore, kept safe forever |
| Just test on your phone | the CI artifact is fine |

Same `applicationId` + same key = Android installs it as an upgrade. Different key =
"Android cannot install it over this app" (the app's own updater checks this too).

## Optional: a clean break

Changing `applicationId` (e.g. `dev.prere.vault`) makes this a separate app from
P Vault: fresh Firebase project, new `google-services.json`, no update path, no shared
backups. Everything in the compatibility table above would then need renaming too.

## Verified here / not verified

Verified in this workspace:

- all `app/src/main/**/*.xml` parse; `google-services.json` and `themes.json` parse
- all four workflow YAML files parse; `working-directory` and artifact paths checked
- launcher icon PNGs: RGBA, correct alpha, ring coverage 0.2727 = analytic area at every density
- no user-visible `P Vault` strings or old-brand URLs remain in `app/src`
- the URLs the updater and theme catalogue use now match this repo

Not verified here — **no Android SDK and Gradle cannot reach `google()`/`mavenCentral()`
in this sandbox**:

- Kotlin compilation, Gradle build, unit tests, Compose UI

The CI **Build check** run on this branch is the first real compile. Check the Actions
tab before trusting the APK.
