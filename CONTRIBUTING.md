# Contributing to duit.

Thank you for helping test and improve **duit.**! Please read the guidelines below before opening an issue or submitting a pull request.

## Privacy & Security Rules (Read First)

- **Never share private financial data:** Do not attach real `.duitbackup` files, local Room databases (`*.db`), unredacted screenshots showing personal balances/account details, PINs, or passwords to issues or pull requests.
- **Never commit secrets or signing assets:** Keystores (`.p12`, `.jks`, `.keystore`), passwords, `.env` files, `local.properties`, and `google-services.json` must stay strictly out of Git.
- **Report security vulnerabilities responsibly:** Do not disclose exploitable security issues publicly in issue threads until a fix is coordinated with the maintainer (use **Settings -> Help & Feedback** in the app if needed).

---

## Development Setup

1. Install **JDK 17** and **Android SDK 35** (`compileSdk = 35`, `minSdk = 29`).
2. Clone the repository and open it in Android Studio (or build from the command line using `gradlew.bat` / `./gradlew`).
3. Create a local `local.properties` file pointing to your Android SDK (`sdk.dir=...`)—this file is ignored by Git.

### Useful Commands

```powershell
# Run Debug and Beta unit test suites
.\gradlew.bat :app:testDebugUnitTest :app:testBetaUnitTest

# Build offline Debug APK (com.diniy.duit)
.\gradlew.bat :app:assembleDebug

# Build unsigned Public Beta APK (com.diniy.duit.beta)
.\gradlew.bat :app:assembleBeta
```

*(Note: Official `google-services.json` and beta signing keystores are not part of the repository; `:app:assembleBeta` compiles cleanly without `google-services.json`.)*

---

## Engineering Invariants

Any pull request modifying core logic or UI must preserve these project guarantees:

1. **Local-First by Default:** Never upload financial records, wallets, transactions, receipts, or logs off-device. Keep `android:allowBackup="false"` and keep personal builds (`debug`, `release`, `fluid`, `benchmark`) free of `INTERNET` permissions and Firebase dependencies.
2. **Exact Integer Monetary Math:** Money must always be stored and calculated as `Long` in sen (`amountSen`, `balanceSen`). Never use `Float` or `Double` for currency math.
3. **Room Schema Compatibility:** Never silently edit an existing database table. Any schema change requires incrementing the Room DB version, writing and unit-testing a migration, and committing the exported JSON schema under `app/schemas/`.
4. **Manual Dependency Injection:** Dependencies are wired explicitly in `DuitContainer`; avoid introducing Hilt/Koin without prior architectural discussion.
5. **Preserve Existing Visual Systems & Accessibility:** Respect both **Candy Bubble 2.0** (Light/Dark) and **Pure Liquid Glass** (Light/Dark, LOW/MID/HIGH glass intensity), maintain minimum `48dp` interactive touch targets, and ensure existing Privacy Mode amount masking continues to apply across screens and Glance widgets.

---

## Pull Request Checklist

- [ ] Focused, minimal diff matching existing code style (`kotlin.code.style=official`).
- [ ] All relevant tests pass: `.\gradlew.bat :app:testDebugUnitTest :app:testBetaUnitTest`.
- [ ] New behavior or bug fixes include unit tests (especially for calculations, migrations, security, or backup/restore).
- [ ] If any Room schema changed, a tested migration and exported schema JSON are included.
- [ ] Screenshots attached to UI PRs use only sample/synthetic data.
