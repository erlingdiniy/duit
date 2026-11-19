/**
 * duit. — User Feedback endpoint (Google Apps Script, bound to the "duit. — User Feedback" sheet).
 *
 * Write-only: it appends one validated row and never returns sheet data. The spreadsheet stays
 * private; the web app runs as the owner. Nothing here is a secret, because the URL ships inside
 * a public APK. Anti-spam measures below raise the cost of abuse; they cannot stop a determined
 * attacker (see feedback-backend/README.md).
 */

var SHEET_NAME = 'Feedback';
var HEADERS = ['Timestamp', 'Category', 'Message', 'Email', 'App Version', 'Status'];
var CATEGORIES = ['Bug Report', 'Feature Request', 'General Feedback'];
var STATUSES = ['New', 'Reviewed', 'Planned', 'Done', "Won't Fix"];

var LIMITS = {
  maxBodyBytes: 8 * 1024,
  messageMin: 10,
  messageMax: 2000,
  emailMax: 254,
  versionMax: 32,
  perInstallPerHour: 5,     // per random install id the app sends (spoofable, but stops accidental floods)
  globalPerMinute: 20,      // whole endpoint
  globalPerDay: 300,        // whole endpoint; protects the sheet from bulk spam
  duplicateWindowSec: 600   // same message text within 10 minutes is rejected
};

// ------------------------------------------------------------------------------------------------
// HTTP handlers
// ------------------------------------------------------------------------------------------------

function doPost(e) {
  try {
    var raw = (e && e.postData && e.postData.contents) || '';
    if (raw.length > LIMITS.maxBodyBytes) return reply_(false, 'PAYLOAD_TOO_LARGE');

    var input;
    try {
      input = JSON.parse(raw);
    } catch (parseError) {
      return reply_(false, 'INVALID_JSON');
    }

    var checked = validateFeedback_(input);
    if (!checked.ok) return reply_(false, checked.error);
    if (checked.spam) return reply_(true, null); // honeypot: pretend success, store nothing

    var lock = LockService.getScriptLock();
    if (!lock.tryLock(5000)) return reply_(false, 'BUSY_TRY_LATER');
    try {
      var limited = checkRateLimits_(checked.value);
      if (limited) return reply_(false, limited);
      appendRow_(checked.value);
    } finally {
      lock.releaseLock();
    }
    return reply_(true, null);
  } catch (unexpected) {
    // Deliberately no logging of the request or message contents.
    return reply_(false, 'SERVER_ERROR');
  }
}

/** Health check only; never exposes data. */
function doGet() {
  return reply_(true, null, { service: 'duit-feedback', version: 1 });
}

// ------------------------------------------------------------------------------------------------
// Validation (pure functions, also exercised by feedback-backend/test-validation.js)
// ------------------------------------------------------------------------------------------------

function validateFeedback_(input) {
  if (!input || typeof input !== 'object' || Array.isArray(input)) return fail_('INVALID_BODY');

  // Honeypot: a hidden field real users never fill. Bots that fill every field are silently dropped.
  if (input.website !== undefined && String(input.website).trim() !== '') return { ok: true, spam: true };

  var category = typeof input.category === 'string' ? input.category.trim() : '';
  if (CATEGORIES.indexOf(category) < 0) return fail_('INVALID_CATEGORY');

  var message = typeof input.message === 'string' ? cleanText_(input.message) : '';
  if (message.length < LIMITS.messageMin) return fail_('MESSAGE_TOO_SHORT');
  if (message.length > LIMITS.messageMax) return fail_('MESSAGE_TOO_LONG');

  var email = '';
  if (input.email !== undefined && input.email !== null && String(input.email).trim() !== '') {
    email = String(input.email).trim();
    if (email.length > LIMITS.emailMax || !/^[^\s@<>"']+@[^\s@<>"']+\.[A-Za-z]{2,}$/.test(email)) {
      return fail_('INVALID_EMAIL');
    }
  }

  var appVersion = typeof input.appVersion === 'string' ? input.appVersion.trim() : '';
  if (appVersion.length === 0 || appVersion.length > LIMITS.versionMax ||
      !/^\d{1,3}\.\d{1,3}\.\d{1,3}(-[0-9A-Za-z.]{1,20})?$/.test(appVersion)) {
    return fail_('INVALID_APP_VERSION');
  }

  var installId = typeof input.installId === 'string' ? input.installId.trim() : '';
  if (installId !== '' && !/^[0-9a-f-]{16,64}$/i.test(installId)) return fail_('INVALID_INSTALL_ID');

  return {
    ok: true,
    spam: false,
    value: { category: category, message: message, email: email, appVersion: appVersion, installId: installId }
  };
}

/** Removes control characters (keeps line breaks and tabs) and trims. */
function cleanText_(text) {
  return String(text)
    .replace(/\r\n?/g, '\n')
    .replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F​-‏‪-‮⁦-⁩]/g, '')
    .trim();
}

/**
 * Stops spreadsheet formula injection: a cell starting with = + - @ (or a tab / carriage return)
 * could be evaluated by Sheets. Prefixing an apostrophe stores it as plain text.
 */
function safeCell_(value) {
  var text = String(value == null ? '' : value);
  return /^[=+\-@\t\r]/.test(text) ? "'" + text : text;
}

function fail_(code) {
  return { ok: false, error: code };
}

// ------------------------------------------------------------------------------------------------
// Rate limits and storage
// ------------------------------------------------------------------------------------------------

function checkRateLimits_(value) {
  var cache = CacheService.getScriptCache();
  var now = new Date();
  var minuteKey = 'g_min_' + Utilities.formatDate(now, 'UTC', 'yyyyMMddHHmm');
  var dayKey = 'g_day_' + Utilities.formatDate(now, 'UTC', 'yyyyMMdd');
  var messageKey = 'dup_' + hash_(value.category + '|' + value.message.toLowerCase());
  var installKey = value.installId ? 'inst_' + hash_(value.installId) + '_' + Utilities.formatDate(now, 'UTC', 'yyyyMMddHH') : null;

  if (cache.get(messageKey)) return 'DUPLICATE';
  if (count_(cache, minuteKey) >= LIMITS.globalPerMinute) return 'RATE_LIMITED';
  if (countDaily_(dayKey) >= LIMITS.globalPerDay) return 'DAILY_LIMIT_REACHED';
  if (installKey && count_(cache, installKey) >= LIMITS.perInstallPerHour) return 'RATE_LIMITED';

  cache.put(messageKey, '1', LIMITS.duplicateWindowSec);
  cache.put(minuteKey, String(count_(cache, minuteKey) + 1), 120);
  if (installKey) cache.put(installKey, String(count_(cache, installKey) + 1), 3600);
  PropertiesService.getScriptProperties().setProperty(dayKey, String(countDaily_(dayKey) + 1));
  return null;
}

function count_(cache, key) {
  return Number(cache.get(key) || 0);
}

/** Daily counter lives in Script Properties so it survives cache eviction; old days are pruned. */
function countDaily_(dayKey) {
  var props = PropertiesService.getScriptProperties();
  var all = props.getProperties();
  Object.keys(all).forEach(function (key) {
    if (key.indexOf('g_day_') === 0 && key !== dayKey) props.deleteProperty(key);
  });
  return Number(all[dayKey] || 0);
}

function hash_(text) {
  var bytes = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, text, Utilities.Charset.UTF_8);
  return bytes.map(function (b) { return ('0' + (b & 0xff).toString(16)).slice(-2); }).join('').slice(0, 32);
}

function appendRow_(value) {
  var sheet = getSheet_();
  // The timestamp is always generated here, never taken from the client. The install id is used
  // only for rate limiting and is not stored.
  sheet.appendRow([
    new Date(),
    safeCell_(value.category),
    safeCell_(value.message),
    safeCell_(value.email),
    safeCell_(value.appVersion),
    'New'
  ]);
}

function getSheet_() {
  var spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = spreadsheet.getSheetByName(SHEET_NAME);
  if (!sheet) throw new Error('Feedback sheet missing; run setup() once from the editor.');
  return sheet;
}

function reply_(ok, error, extra) {
  var body = { ok: ok };
  if (error) body.error = error;
  if (extra) Object.keys(extra).forEach(function (k) { body[k] = extra[k]; });
  return ContentService.createTextOutput(JSON.stringify(body)).setMimeType(ContentService.MimeType.JSON);
}

// ------------------------------------------------------------------------------------------------
// One-time setup and editor-only test (run from the Apps Script editor)
// ------------------------------------------------------------------------------------------------

/** Creates the Feedback sheet with headers, text formats and a Status dropdown. Safe to re-run. */
function setup() {
  var spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = spreadsheet.getSheetByName(SHEET_NAME) || spreadsheet.insertSheet(SHEET_NAME);
  sheet.getRange(1, 1, 1, HEADERS.length).setValues([HEADERS]).setFontWeight('bold');
  sheet.setFrozenRows(1);
  sheet.getRange('A2:A').setNumberFormat('yyyy-mm-dd hh:mm:ss');
  sheet.getRange('B2:E').setNumberFormat('@'); // plain text: no formulas, no auto-formatting
  sheet.getRange('F2:F').setDataValidation(
    SpreadsheetApp.newDataValidation().requireValueInList(STATUSES, true).setAllowInvalid(false).build()
  );
  sheet.setColumnWidth(3, 420);
}

/** Editor-only smoke test: validates sample inputs without writing anything. */
function testValidation() {
  var samples = [
    { category: 'Bug Report', message: 'The widget does not refresh.', appVersion: '1.0.0' },
    { category: 'Bug Report', message: '=HYPERLINK("http://x")', appVersion: '1.0.0' },
    { category: 'Spam', message: 'hello there friend', appVersion: '1.0.0' },
    { category: 'General Feedback', message: 'short', appVersion: '1.0.0' },
    { category: 'Feature Request', message: 'Please add dark widgets.', appVersion: '1.0.0', website: 'bot' }
  ];
  samples.forEach(function (sample) {
    var result = validateFeedback_(sample);
    Logger.log(JSON.stringify({ category: sample.category, ok: result.ok, spam: !!result.spam, error: result.error || null }));
  });
}
