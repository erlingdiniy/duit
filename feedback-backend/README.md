# duit. — Feedback backend (Phase 1)

Google Sheets + Google Apps Script, tulis-sahaja (write-only). App Android **belum** diubah.

Fail:
- `Code.gs` — endpoint Apps Script (`doPost`, `setup`, `testValidation`).
- `test-validation.js` — ujian tempatan untuk logik pengesahan: `node feedback-backend/test-validation.js`.

---

## 1. Persediaan Google Sheets + Apps Script (kira-kira 5 minit)

1. Buka https://sheets.new dengan akaun Google kau. Namakan fail: **`duit. — User Feedback`**. Jangan kongsi (kekal *Restricted*).
2. **Extensions → Apps Script**. Padam kod contoh, tampal seluruh `Code.gs`, tekan **Save**.
3. Pilih fungsi **`setup`** → **Run**. Google akan minta kebenaran (akses spreadsheet ini sahaja + simpanan skrip). Benarkan.
   - Tab **`Feedback`** dicipta: `Timestamp | Category | Message | Email | App Version | Status`, lajur teks biasa, dropdown Status (`New`, `Reviewed`, `Planned`, `Done`, `Won't Fix`).
4. (Pilihan) Pilih **`testValidation`** → **Run** → **Execution log**: semak keputusan. Tiada baris ditulis.
5. **Deploy → New deployment → Web app**:
   - *Execute as*: **Me**
   - *Who has access*: **Anyone**
   - **Deploy**, salin **Web app URL** (`https://script.google.com/macros/s/…/exec`).
6. Untuk kemas kini kod kemudian: **Deploy → Manage deployments → Edit → Version: New version** (URL kekal sama).

> Spreadsheet kekal peribadi. "Anyone" hanya membenarkan orang **menghantar** maklum balas ke URL, bukan membaca sheet. Skrip tak pernah memulangkan data sheet.

---

## 2. Perlindungan yang dilaksanakan

| Ancaman | Perlindungan dalam `Code.gs` |
|---|---|
| Suntikan formula (`=`, `+`, `-`, `@`, tab, CR) | `safeCell_` tambah `'` di depan; lajur B–E diformat teks biasa |
| Input rosak/besar | Badan ≤ 8 KB, JSON sahaja, kategori senarai tetap, mesej 10–2000 aksara, e-mel ≤ 254 + format, versi `X.Y.Z(-label)` |
| Aksara tersembunyi | Aksara kawalan & Unicode bidi/zero-width dibuang |
| Bot asas | Medan honeypot `website` — jika diisi, balas "berjaya" tetapi tiada disimpan |
| Banjir | Had global 20/minit & 300/hari; had 5/jam bagi setiap `installId`; mesej sama ditolak dalam 10 minit; `LockService` elak perlumbaan |
| Timestamp palsu | Masa sentiasa dijana pelayan (`new Date()`) |
| Kebocoran log | Tiada `Logger`/`console` untuk kandungan permintaan; ralat dipulangkan sebagai kod sahaja |
| Data kewangan | App **tidak** hantar baki, transaksi, PIN, kata laluan atau rekod. Hanya 4 medan di atas |

---

## 3. Had keselamatan (jujur)

- **URL itu awam.** Ia berada dalam APK awam; sesiapa boleh mengekstrak dan menghantar POST terus. Tiada "rahsia" dalam app yang boleh menghalangnya — sebarang token dalam APK boleh dibaca, jadi **tidak digunakan**.
- **Tiada had mengikut IP.** Apps Script tidak mendedahkan IP pengirim. `installId` dijana oleh app dan **boleh dipalsukan**; ia hanya menghalang banjir tidak sengaja.
- **Had global boleh disalahgunakan:** penyerang boleh menghabiskan kuota 300/hari supaya maklum balas sebenar ditolak (`DAILY_LIMIT_REACHED`) sehingga hari berikutnya. Data sedia ada tetap selamat.
- **Spam yang lepas** akan masuk ke sheet sebagai baris teks biasa (tidak boleh dilaksanakan); perlu dipadam secara manual.
- **Kuota Google** (masa larian, pelaksanaan serentak, akaun percuma) terpakai; Apps Script sentiasa pulangkan HTTP 200, jadi status sebenar ada dalam JSON.
- **Teks bebas:** pengguna mungkin menaip maklumat sensitif sendiri dalam mesej. UI Phase 2 perlu memberi amaran jelas.

**Penilaian:** untuk beta/stabil berskala kecil, risiko terburuk ialah spam dan gangguan kuota — **tiada data kewangan atau sheet terdedah** — jadi Apps Script boleh diterima. **Jika spam menjadi masalah**, naik taraf ke Cloudflare Worker (percuma) dengan Cloudflare Turnstile (CAPTCHA tanpa geseran) dan had mengikut IP di depan sheet yang sama. Firebase App Check dengan Play Integrity **tidak sesuai** kerana duit. diedarkan melalui GitHub, bukan Play Store.

---

## 4. Ujian minimum (selepas deploy)

Gantikan `URL` dengan Web app URL kau. `-L` perlu kerana Apps Script membalas melalui redirect.

```bash
URL='https://script.google.com/macros/s/XXXX/exec'

# 1. Kesihatan
curl -sL "$URL"
# -> {"ok":true,"service":"duit-feedback","version":1}

# 2. Berjaya (satu baris "New" muncul dalam sheet)
curl -sL -H 'Content-Type: text/plain' -d '{"category":"Bug Report","message":"Test from curl: widget refresh.","appVersion":"1.0.0"}' "$URL"
# -> {"ok":true}

# 3. Ditolak
curl -sL -H 'Content-Type: text/plain' -d '{"category":"Spam","message":"hello there friend","appVersion":"1.0.0"}' "$URL"
# -> {"ok":false,"error":"INVALID_CATEGORY"}

# 4. Formula disimpan sebagai teks (sel bermula dengan ')
curl -sL -H 'Content-Type: text/plain' -d '{"category":"General Feedback","message":"=HYPERLINK(\"http://x\",\"y\")","appVersion":"1.0.0"}' "$URL"

# 5. Pendua dalam 10 minit
#    Ulang arahan 2 -> {"ok":false,"error":"DUPLICATE"}
```

Padam baris ujian dari sheet selepas selesai.

---

## 5. Kontrak integrasi Android (Phase 2)

**Permintaan**
- `POST {FEEDBACK_URL}` — URL diletak dalam `BuildConfig` melalui Gradle property (bukan rahsia).
- Header: `Content-Type: text/plain; charset=utf-8` (badan ialah JSON).
- Ikut redirect (302 ke `script.googleusercontent.com`); timeout sambung 10 s, baca 15 s.
- Badan:
```json
{
  "category": "Bug Report | Feature Request | General Feedback",
  "message": "10–2000 characters",
  "email": "optional, may be omitted or empty",
  "appVersion": "1.0.0",
  "installId": "random UUID generated once per install (optional)",
  "website": ""
}
```
- `website` sentiasa rentetan kosong (honeypot). `installId` ialah UUID rawak, **bukan** pengecam peranti/akaun.

**Jawapan** (sentiasa HTTP 200, JSON):
- `{"ok":true}` — berjaya.
- `{"ok":false,"error":"<CODE>"}` dengan kod:
  `INVALID_JSON`, `INVALID_BODY`, `PAYLOAD_TOO_LARGE`, `INVALID_CATEGORY`, `MESSAGE_TOO_SHORT`, `MESSAGE_TOO_LONG`, `INVALID_EMAIL`, `INVALID_APP_VERSION`, `INVALID_INSTALL_ID`, `DUPLICATE`, `RATE_LIMITED`, `DAILY_LIMIT_REACHED`, `BUSY_TRY_LATER`, `SERVER_ERROR`.
- Ralat rangkaian / bukan JSON → anggap "tidak dihantar", benarkan cuba semula; jangan hantar semula automatik secara berulang.

**Peraturan app**
- Sahkan input yang sama di app sebelum hantar (elak permintaan sia-sia).
- Jangan sertakan data kewangan, nama wallet, baki, PIN, log, atau maklumat peranti.
- Tunjuk amaran: "Don't include passwords, PINs or financial details."
- Hanya dalam build yang ada `INTERNET` (beta/stable). Edisi peribadi (`com.diniy.duit`) tiada `INTERNET`; borang tidak dipaparkan di situ melainkan diluluskan.
