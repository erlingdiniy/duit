# Private Developer-Enabled Build (`betaDev`) & Maintenance Guide

This document lives only on the private `personal-edition` branch and is blocked from public pushes by `.git/hooks/pre-push`.

## 1. Edition Matrix

| Build Type | Package ID | Signing Key | Public v1.2.0 Features | FCM Broadcasts | Public In-App Updater | Developer Center |
|---|---|---|---|---|---|---|
| Public Stable / Beta (`beta`) | `com.diniy.duit.beta` | `duit-beta.p12` (`CN=duit. Beta`) | Yes | Yes (`duit_announcements` / `duit_beta_broadcasts`) | Enabled (`BetaUpdates`) | **Excluded** (`noDevCenter`) |
| Private Developer (`betaDev`) | `com.diniy.duit.beta` | `duit-beta.p12` (`CN=duit. Beta`) | Yes | Yes (`duit_announcements`) | **Compile-time disabled** (`EditionUpdatePrompt` no-op) | **Included** (`src/personal`) |
| Offline Personal (`fluid` / `release`) | `com.diniy.duit` | Debug key | Yes | No (`process*GoogleServices` disabled) | Disabled (`noUpdater`) | Included (`src/personal`) |

## 2. Shared Codebase Maintenance Workflow

All financial features, Room migrations, UI screens, themes, and bug fixes live in `app/src/main`.

1. Make shared fixes on public `main` first (never include `app/src/personal/`, `app/src/betaDev/`, `app/src/testBetaDev/`, or `docs/PRIVATE_DEVELOPER_BUILD.md` on `main`).
2. Bring shared fixes into private `personal-edition` via `git checkout personal-edition && git merge main`.
3. Never push `personal-edition` to `erlingdiniy/duit` or `erlingdiniy/duit-beta` (enforced by `.git/hooks/pre-push`).

## 3. Versioning Strategy & Anti-Overwrite Protection

- Public Stable `X.Y.Z` uses `versionCode = X*1_000_000 + Y*1_000 + Z` (e.g. `1.2.0` -> `1002000`).
- Public Beta `X.Y.0-beta.N` uses `X*1_000_000 + Y*1_000 - 100 + N` (e.g. `1.2.0-beta.2` -> `1001902`).
- Private Developer `betaDev` defaults to `${betaVersionName}-dev` and the same numeric base `versionCode` (e.g. `1.2.0-dev` -> `1002000`), matching `com.diniy.duit.beta` v1.2.0 for an in-place `adb install -r` without wiping data.
- Because `1.2.0-dev` does not contain `-beta.`, `DeveloperBroadcastSpec.defaultTopicForVersion("1.2.0-dev")` keeps the phone subscribed to Stable's `duit_announcements` FCM topic.
- For private developer-only hotfixes between public releases, bump monotonically above the installed `versionCode` using `-PduitBetaDevVersionName=1.2.0-dev.1 -PduitBetaDevVersionCode=1002001`.
- Compile-time updater lock: `betaDev` excludes `com/diniy/duit/update/**` from `src/beta/java`, omits `REQUEST_INSTALL_PACKAGES` and `BetaInstallStatusReceiver` from `AndroidManifest.xml`, sets `BETA_REPOSITORY = ""`, and compiles `src/betaDev/java/com/diniy/duit/update/EditionUpdatePrompt.kt` (`EditionUpdatePrompt() = Unit`), preventing public GitHub updates from overwriting Developer Center.

## 4. Build & Sign Commands (Never Store Passwords or Keys in Repo)

### A. Build Public Stable APK (`beta`, isolated from Developer Center)
```powershell
.\gradlew.bat testBetaUnitTest assembleBeta verifyBetaReleaseIsolation `
  -PduitBetaRepository=erlingdiniy/duit-beta `
  -PduitBetaVersionCode=1002000 `
  -PduitBetaVersionName=1.2.0 `
  -PduitBetaAppName=duit. `
  --no-daemon
```
Sign interactively using `%LOCALAPPDATA%\duit-beta-signing\duit-beta.p12` (alias `duit-beta`).

### B. Build Public Beta APK (`beta`, isolated from Developer Center)
```powershell
.\gradlew.bat testBetaUnitTest assembleBeta verifyBetaReleaseIsolation `
  -PduitBetaRepository=erlingdiniy/duit-beta `
  -PduitBetaVersionCode=1001903 `
  -PduitBetaVersionName=1.2.0-beta.3 `
  "-PduitBetaAppName=duit. Beta" `
  --no-daemon
```

### C. Build & Sign Private Developer-Enabled APK (`betaDev` for your phone)
```powershell
.\gradlew.bat testBetaDevUnitTest assembleBetaDev `
  -PduitBetaVersionCode=1002000 `
  -PduitBetaVersionName=1.2.0 `
  -PduitBetaAppName=duit. `
  -PduitBetaDevVersionCode=1002000 `
  -PduitBetaDevVersionName=1.2.0-dev `
  --no-daemon
```
Stage `app\build\outputs\apk\betaDev\app-betaDev-unsigned.apk` and run `C:\Users\SV\Documents\duit-release-staging\private-dev\v1.2.0-dev\Sign-duit-BetaDev.cmd` to sign with `%LOCALAPPDATA%\duit-beta-signing\duit-beta.p12`, verify certificate SHA-256 `e26f499ec2524650f5fbb7cf139e62f3de7dd001ad1afbbe1718034a2b785d14`, and upgrade in-place via `adb install -r duit-1.2.0-dev.apk`.
