**duit. v1.3.0** is the latest **Stable release** of the `1.3` series (`com.diniy.duit.beta`, launcher name `duit.`, `versionCode 1003000`) distributed via `erlingdiniy/duit-beta`. It preserves the existing public package ID (`com.diniy.duit.beta`), original signing certificate, and Room v14 financial database so `1.1.1`, `1.2.0-beta.1`, `1.2.0-beta.2`, and `1.2.0` upgrade seamlessly in-place with all wallets, transactions, receipts, and preferences intact.

## What's New
- **Opt-In Crash Reporting (Firebase Crashlytics):** Optional crash and ANR diagnostics (strictly **Off by default**) under **Settings -> Privacy & Your Data -> Crash reporting**. Never includes wallet balances, transactions, salaries, commissions, debts, savings, receipts, names, emails, or PINs; turning it off immediately deletes unsent cached reports.
- **Opt-In Usage Analytics (Firebase Analytics):** Optional privacy-first feature counts (strictly **Off by default**, separate from crash reporting) under **Settings -> Security & Privacy** or **Settings -> Privacy & Your Data -> Usage Analytics**. Only generic allowlisted events (`feature_used`, `onboarding_completed`, `app_updated`, `update_prompt_action`) are recorded, with Android Advertising ID (`AD_ID`), AdServices permissions, ad personalization, and user IDs permanently disabled. Toggling off immediately clears local queued analytics data (`resetAnalyticsData()`).

## Improvements
- **Privacy & Open-Source License Transparency:** Updated `Privacy & Your Data`, `About -> Privacy & Security`, `About -> Open-Source Licenses` (`Firebase Analytics SDK`, `Firebase Cloud Messaging SDK`, `Firebase Crashlytics SDK`), and [PRIVACY.md](docs/PRIVACY.md).
- **Backup Isolation:** Crash reporting and analytics consent preferences remain local to the device and are never included inside exported `.duitbackup` archives.
- **Zero Ad Tracking:** Explicitly strips `com.google.android.gms.permission.AD_ID`, AdServices permissions, and Install Referrer bindings from the public APK manifest.

## Before You Install
- Recommended: create an encrypted backup first (**Settings -> Backup & Restore**).
- Upgrades seamlessly over `1.1.1` (`1001001`), `1.2.0-beta.1` (`1001901`), `1.2.0-beta.2` (`1001902`), and `1.2.0` (`1002000`). Android blocks downgrades back to earlier versions without uninstalling.

## Verification Metadata
- Package: `com.diniy.duit.beta` (`versionCode 1003000`)
- Target Repository: `erlingdiniy/duit-beta` (Stable release channel)
- Expected Signing Certificate SHA-256: `e26f499ec2524650f5fbb7cf139e62f3de7dd001ad1afbbe1718034a2b785d14`
- Signed APK SHA-256: populate after interactive release signing with `duit-beta.p12`.
