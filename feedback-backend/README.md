# duit. — Feedback Backend

Write-only Google Sheets + Google Apps Script endpoint used by **Settings -> Help & Feedback** in the public duit. release (`com.diniy.duit.beta`).

Files:
- `Code.gs` — Google Apps Script Web App (`doPost`, `doGet`, `setup`, `testValidation`).
- `test-validation.js` — Local Node.js unit checks for input validation and formula-injection escaping:
  ```bash
  node feedback-backend/test-validation.js
  ```

---

## 1. Google Sheets + Apps Script Setup (~5 minutes)

1. Open [Google Sheets](https://sheets.new) and create a spreadsheet (for example, `duit. — User Feedback`). Keep access **Restricted**—never make the sheet public.
2. Open **Extensions -> Apps Script**, replace the template code with the full contents of [`Code.gs`](Code.gs), and save.
3. Run **`setup`** once from the editor and grant permissions (scoped to the spreadsheet and script cache/properties).
   - Creates the **`Feedback`** sheet with headers: `Timestamp | Category | Message | Email | App Version | Status` (`New`, `Reviewed`, `Planned`, `Done`, `Won't Fix`), formatted as plain text in columns B–E.
4. *(Optional)* Run **`testValidation`** from the editor and verify the execution log. No rows are written.
5. Choose **Deploy -> New deployment -> Web app**:
   - *Execute as:* **Me**
   - *Who has access:* **Anyone**
   - Copy the deployed Web app URL (`https://script.google.com/macros/s/.../exec`).
6. When updating `Code.gs` later, use **Deploy -> Manage deployments -> Edit -> Version: New version** so the URL stays the same.

> The spreadsheet stays private. Allowing "Anyone" on the Web App only permits `POST`/`GET` requests to the write-only script, which never returns spreadsheet rows.

---

## 2. Safeguards Implemented in `Code.gs`

| Risk | Mitigation in `Code.gs` |
| --- | --- |
| Spreadsheet formula injection (`=`, `+`, `-`, `@`, tab, CR) | `safeCell_` prefixes `'`, and columns B–E are formatted as plain text (`@`) |
| Oversized or malformed payloads | 8 KB body cap, strict JSON validation, fixed categories, 10–2,000 char message, valid email <= 254 chars, strict semver/beta `appVersion` regex |
| Hidden control / bidi characters | Control characters and Unicode bidi/zero-width overrides stripped before storing |
| Automated bot spam | Hidden honeypot field (`website`) silently drops filled bot submissions |
| Flooding & duplicates | `LockService`, 20 req/min global cap, 300 req/day global cap, 5 req/hour per random `installId`, 10-minute duplicate hash rejection |
| Spoofed timestamps | Server always sets `new Date()` |
| Request log leaks | Errors return short codes without logging user message contents |
| Financial privacy | Android app sends zero balances, transactions, receipts, PINs, or backups |

---

## 3. Honest Threat Model & Limitations

- **The `/exec` endpoint URL is public by nature:** It ships in `BuildConfig.FEEDBACK_URL` inside the public APK. No secret token is embedded in the Android client because any client-side secret can be extracted.
- **No per-IP rate limit in Apps Script:** Google Apps Script does not expose client IP addresses to `doPost`. `installId` is a random per-install UUID that blocks accidental floods, not determined spoofing.
- **Global quota exhaustion:** A determined flood can consume the 300/day cap (`DAILY_LIMIT_REACHED`) temporarily, though no private spreadsheet data or user records are exposed.
- **Free-text user warnings:** The Android UI warns contributors never to type passwords, PINs, or financial details into feedback messages.

If spam ever becomes significant, a reverse proxy with CAPTCHA/per-IP throttling (such as a Cloudflare Worker + Turnstile) can sit in front of the same sheet without changing the Android privacy guarantees.

---

## 4. Smoke Test (After Deployment)

Pass `-L` because Google Apps Script returns a `302` redirect to `script.googleusercontent.com`:

```bash
URL='https://script.google.com/macros/s/XXXX/exec'

# 1. Health check
curl -sL "$URL"
# -> {"ok":true,"service":"duit-feedback","version":1}

# 2. Valid submission (appends one row with Status "New")
curl -sL -H 'Content-Type: text/plain' -d '{"category":"Bug Report","message":"Test from curl: widget refresh.","appVersion":"1.0.0"}' "$URL"
# -> {"ok":true}

# 3. Rejected category
curl -sL -H 'Content-Type: text/plain' -d '{"category":"Spam","message":"hello there friend","appVersion":"1.0.0"}' "$URL"
# -> {"ok":false,"error":"INVALID_CATEGORY"}

# 4. Formula escaping check (stored with leading ')
curl -sL -H 'Content-Type: text/plain' -d '{"category":"General Feedback","message":"=HYPERLINK(\"http://x\",\"y\")","appVersion":"1.0.0"}' "$URL"

# 5. Duplicate check within 10 minutes
# Repeating request #2 returns {"ok":false,"error":"DUPLICATE"}
```

Delete test rows from the sheet after verifying.

---

## 5. Android Client Contract

- **Endpoint:** `POST` to `BuildConfig.FEEDBACK_URL` (`Content-Type: text/plain; charset=utf-8` with JSON body), following redirects (`script.google.com` -> `script.googleusercontent.com`) with 10s connect / 15s read timeouts.
- **Payload:**
  ```json
  {
    "category": "Bug Report | Feature Request | General Feedback",
    "message": "10-2000 characters",
    "email": "optional, empty if omitted",
    "appVersion": "1.2.0-beta.2",
    "installId": "random per-install UUID (optional)",
    "website": ""
  }
  ```
- **Response (`HTTP 200` JSON):** `{"ok":true}` or `{"ok":false,"error":"<CODE>"}` (`INVALID_JSON`, `INVALID_BODY`, `PAYLOAD_TOO_LARGE`, `INVALID_CATEGORY`, `MESSAGE_TOO_SHORT`, `MESSAGE_TOO_LONG`, `INVALID_EMAIL`, `INVALID_APP_VERSION`, `INVALID_INSTALL_ID`, `DUPLICATE`, `RATE_LIMITED`, `DAILY_LIMIT_REACHED`, `BUSY_TRY_LATER`, `SERVER_ERROR`).
- Personal/offline source builds (`com.diniy.duit`) omit `INTERNET` permission and hide feedback/network features cleanly.
