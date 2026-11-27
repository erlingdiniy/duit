# Private Developer-Enabled Build (`betaDev`) & Maintenance Guide

This document lives only on the private `personal-edition` branch and is blocked from public pushes by `.git/hooks/pre-push`.

## 1. Edition Matrix

| Build Type | Package ID | Signing Key | Public v1.2.0 Features | FCM Broadcasts | Updater Source | Developer Center |
|---|---|---|---|---|---|---|
| Public Stable / Beta (`beta`) | `com.diniy.duit.beta` | `duit-beta.p12` (`CN=duit. Beta`) | Yes | Yes (`duit_announcements` / `duit_beta_broadcasts`) | Public `erlingdiniy/duit-beta` (`BetaUpdates`) | **Excluded** (`noDevCenter`) |
| Private Developer (`betaDev`) | `com.diniy.duit.beta` | `duit-beta.p12` (`CN=duit. Beta`) | Yes | Yes (`duit_announcements`) | Private `erlingdiniy/duit-developer-console` (`PrivateDevUpdates`, PAT auth, tag `v*-dev*` only) | **Included** (`src/personal`) |
| Offline Personal (`fluid` / `release`) | `com.diniy.duit` | Debug key | Yes | No | Disabled (`noUpdater`) | Included (`src/personal`) |

## 2. Shared Codebase Maintenance Workflow

All financial features, Room migrations, UI screens, themes, and bug fixes live in `app/src/main`.

1. Implement shared financial/UI fixes on public `main` first (never include `app/src/personal/`, `app/src/betaDev/`, `app/src/testBetaDev/`, `scripts/release-pipeline.ps1`, or `docs/PRIVATE_DEVELOPER_BUILD.md` on `main`).
2. Merge shared fixes into private `personal-edition`: `git checkout personal-edition && git merge main`.
3. Never push `personal-edition` to `erlingdiniy/duit` or `erlingdiniy/duit-beta` (enforced by `.git/hooks/pre-push`).

## 3. Phase 20B–20E Synchronized Versioning Strategy & Overwrite Protection

- **Public Stable `X.Y.Z`**: `X*1_000_000 + Y*1_000 + Z` (e.g. `1.2.0` -> `1002000`, `1.3.0` -> `1003000`).
- **Public Beta `X.Y.0-beta.N`**: `X*1_000_000 + Y*1_000 - 100 + N` (e.g. `1.2.0-beta.2` -> `1001902`, `1.3.0-beta.1` -> `1002901`).
- **Private Developer `X.Y.Z-dev[.N]` (`betaDev`)**:
  `major*10_000_000 + minor*10_000 + patch*100 + devPatch`
  - `1.2.0-dev` -> `10020000`
  - `1.2.0-dev.2` -> `10020002`
  - `1.3.0-dev.1` -> `10030001`
  - `2.0.0-dev` -> `20000000`
- Ordering invariant: `1002000` (`1.2.0`) < `1002901` (`1.3.0-beta.1`) < `1003000` (`1.3.0`) << `10020002` (`1.2.0-dev.2`) < `10030001` (`1.3.0-dev.1`).
- Every `-dev` build is guaranteed monotonically higher than any installed `1.x..9.x` public APK (`>= 10_020_000`) so public releases cannot accidentally downgrade or overwrite Developer Center.
- In-app GA4 OAuth/Data API reporting remains gated behind `EXPERIMENTAL_GA4_API_ENABLED = false` in `DevCenterModels.kt` until live verification is resumed; official Firebase Analytics Console shortcuts remain active.
- `PrivateDevUpdates` additionally enforces:
  1. Target repository must not be `erlingdiniy/duit-beta` or `erlingdiniy/duit`, and GitHub API must confirm `"private": true`.
  2. GitHub PAT (`Authorization: Bearer ...`) is sent **only** to `api.github.com` and stripped before following the `302` redirect to `objects.githubusercontent.com` / `release-assets.githubusercontent.com`.
  3. Asset must match `duit-<version>-dev[.N].apk` with a valid `sha256:` digest, matching SHA-256 hash, matching package (`com.diniy.duit.beta`), matching `CN=duit. Beta` signer, newer `versionCode`, and manifest metadata `com.diniy.duit.PRIVATE_DEV_EDITION=true`.
  4. Installations always hand off through Android's standard `PackageInstaller` system confirmation dialog (`USER_ACTION_REQUIRED`).

## 4. Pipeline Commands (Never Store Passwords or Keys in Repo)

### Synchronized & Individual Builds via Unified Script (`scripts/release-pipeline.ps1`)
```powershell
# Synchronized dry-run for Public Stable v1.3.0 + Private Developer v1.3.0-dev.1
.\scripts\release-pipeline.ps1 -Target Synchronized -VersionName 1.3.0 -DevPatch 1 -DryRun

# Interactive sign both v1.3.0 and v1.3.0-dev.1 using %LOCALAPPDATA%\duit-beta-signing\duit-beta.p12
.\scripts\release-pipeline.ps1 -Target Synchronized -VersionName 1.3.0 -DevPatch 1

# Individual targets
.\scripts\release-pipeline.ps1 -Target Stable -VersionName 1.3.0 -DryRun
.\scripts\release-pipeline.ps1 -Target PrivateDev -VersionName 1.3.0-dev.1 -DryRun
.\scripts\release-pipeline.ps1 -Target Beta -VersionName 1.3.0-beta.1 -DryRun
```

### Publishing a Future Private Developer Update (`erlingdiniy/duit-developer-console`)
1. Confirm `%LOCALAPPDATA%\duit-beta-signing\duit-beta.p12` signed APK certificate SHA-256 matches `e26f499ec2524650f5fbb7cf139e62f3de7dd001ad1afbbe1718034a2b785d14`.
2. Ensure GitHub repository `erlingdiniy/duit-developer-console` is **private** (`isPrivate: true`), and that your Developer Center GitHub PAT includes **Contents (Read)** on `erlingdiniy/duit-developer-console`.
3. Create a non-draft GitHub Release in `erlingdiniy/duit-developer-console` (example tag `v1.2.0-dev.2`, asset `duit-1.2.0-dev.2.apk`, changelog notes):
   ```powershell
   gh release create v1.2.0-dev.2 `
     "C:\Users\SV\Documents\duit-release-staging\private-dev\v1.2.0-dev.2\duit-1.2.0-dev.2.apk" `
     -R erlingdiniy/duit-developer-console `
     --title "duit. v1.2.0-dev.2" `
     --notes "## Improvements`n- Describe update here"
   ```
4. Never upload `-dev` APKs to `erlingdiniy/duit-beta` or `erlingdiniy/duit`.
5. In the installed app on your phone: automatic update checks run on resume, or tap **Settings -> About duit. -> Check Now**. Tap **Update Now**, review Android's system installer prompt, and install in place preserving all data and Developer Center state.
