**duit. v1.2.0-beta.2** is a **public beta release** (`com.diniy.duit.beta`, `versionCode 1001902`) distributed via `erlingdiniy/duit-beta`. It upgrades seamlessly over both `1.1.1` (`1001001`) and `1.2.0-beta.1` (`1001901`)—including via the in-app updater—while preserving all local data.

## What's New
- Added the **Pure Liquid Glass** visual theme (Light & Dark, selectable LOW / MID / HIGH glass intensity, AGSL optics on Android 13+ with frosted fallback on Android 12 and translucent fallback on Android 10–11) alongside **Candy Bubble 2.0**.
- Added **Receipt Vault** for private local PDF, JPG, and PNG attachments linked to transactions and preserved inside encrypted `.duitbackup` archives.
- Added **Smart Money Calendar**, **My Tax Pocket**, **Little Wins Club**, **Savings Challenges**, and a shareable **Money Personality** card.
- Added **Developer Broadcasts** announcements (no analytics or financial data collected) alongside **Closed/Public Beta** in-app updates.

## Improvements
- Moved non-UI startup tasks (notification channels, invalidation observer, reminder rescheduling, and broadcast subscription) off the main thread for faster cold launch.
- Cached AGSL Liquid Glass render effects and shifted Glance widget updates onto `Dispatchers.IO` to reduce scroll frame jank.
- Expanded the `CalendarChip` touch target to at least `48dp` for accessibility without changing its visual layout.
- Hardened backup/restore size caps (`10 MB` per receipt, `12 MB` raw receipts / `32 MB` backup file), double-tap guards, and atomic staged receipt restore recovery.
- Removed the personal payment QR code from **Settings -> Support duit.** (donations are temporarily unavailable; no personal payment details are bundled).

## Bug Fixes
- Refined **Pure Quick Action** layout and gesture behavior.
- Prevented synchronous recycling of hardware-drawn Receipt Vault bitmaps during compose rendering.
- Fixed interrupted post-staging receipt restore recovery so committed Room restores complete forward cleanly.

## Beta Notice & Known Limitations
- **Beta release:** This is a public pre-release build, not a final stable release.
- **Untested on physical hardware — Receipt Vault live preview:** Private storage, Room migration, backup/restore recovery, and memory limits are covered by automated unit tests and code inspection, **but live on-device rendering of attached PDF/JPG receipts in Receipt Vault could not be tested on hardware** (the QA device had 0 attached receipts).
- **Skipped opt-in Beta tests:** Four opt-in environment-gated unit tests (`SavingsGoalFormRenderTest`, `LiveFeedbackSmokeTest`, `CandySwitchRenderTest`, and `CandyButtonRenderTest`) are skipped during standard local test runs.
- **Local database encryption scope:** Your on-device Room database relies on Android's private app sandbox rather than separate database-level encryption. Exported `.duitbackup` archives are encrypted with AES-256-GCM using your password.

## Before You Install
- Create a backup first (**Settings -> Backup & Restore**).
- Installs cleanly over **duit. 1.1.1** or **1.2.0-beta.1** (`com.diniy.duit.beta`) while preserving existing data. Android does not allow downgrading from `1.2.0-beta.2` back to older builds.

## Asset Verification
- File: `duit-1.2.0-beta.2.apk` (`8,450,511` bytes)
- Package: `com.diniy.duit.beta` (`versionCode 1001902`)
- Signing Certificate SHA-256: `e26f499ec2524650f5fbb7cf139e62f3de7dd001ad1afbbe1718034a2b785d14`

SHA-256 APK: `1d314949a9473475a115fb8d2c686b5ff2fc92ec647e87f88f8739ba50ae4f7d`
