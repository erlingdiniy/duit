// Local test of the pure validation and sanitising functions in Code.gs (no Google services needed).
// Run: node feedback-backend/test-validation.js
const fs = require('fs');
const path = require('path');
const assert = require('assert');
const vm = require('vm');

const context = {};
vm.createContext(context);
vm.runInContext(fs.readFileSync(path.join(__dirname, 'Code.gs'), 'utf8'), context);
const { validateFeedback_: validate, safeCell_: safe, cleanText_: clean } = context;

const ok = (input) => validate(input);
const base = { category: 'Bug Report', message: 'The widget does not refresh.', appVersion: '1.0.0' };

// Valid inputs
assert.strictEqual(ok(base).ok, true);
assert.strictEqual(ok({ ...base, email: 'me@example.com', installId: '0f8e2a6c-4b1d-4e7a-9c3b-1a2b3c4d5e6f' }).ok, true);
assert.strictEqual(ok({ ...base, appVersion: '0.1.0-beta.5' }).ok, true);
assert.strictEqual(ok({ ...base, email: '' }).value.email, '');

// Categories
for (const c of ['Bug Report', 'Feature Request', 'General Feedback']) assert.strictEqual(ok({ ...base, category: c }).ok, true);
assert.strictEqual(ok({ ...base, category: 'bug report' }).error, 'INVALID_CATEGORY');
assert.strictEqual(ok({ ...base, category: undefined }).error, 'INVALID_CATEGORY');

// Message length after cleaning
assert.strictEqual(ok({ ...base, message: 'too short' }).error, 'MESSAGE_TOO_SHORT');
assert.strictEqual(ok({ ...base, message: '   \u0000\u0001 tiny   ' }).error, 'MESSAGE_TOO_SHORT');
assert.strictEqual(ok({ ...base, message: 'x'.repeat(2001) }).error, 'MESSAGE_TOO_LONG');
assert.strictEqual(ok({ ...base, message: 'x'.repeat(2000) }).ok, true);

// Email and version
assert.strictEqual(ok({ ...base, email: 'not-an-email' }).error, 'INVALID_EMAIL');
assert.strictEqual(ok({ ...base, email: 'a@b.c' }).error, 'INVALID_EMAIL');
assert.strictEqual(ok({ ...base, email: '<script>@x.com' }).error, 'INVALID_EMAIL');
assert.strictEqual(ok({ ...base, appVersion: '' }).error, 'INVALID_APP_VERSION');
assert.strictEqual(ok({ ...base, appVersion: '1.0' }).error, 'INVALID_APP_VERSION');
assert.strictEqual(ok({ ...base, appVersion: '=1+1' }).error, 'INVALID_APP_VERSION');
assert.strictEqual(ok({ ...base, installId: 'not valid!' }).error, 'INVALID_INSTALL_ID');

// Body shape and honeypot
assert.strictEqual(ok(null).error, 'INVALID_BODY');
assert.strictEqual(ok([base]).error, 'INVALID_BODY');
assert.strictEqual(ok({ ...base, website: 'http://spam' }).spam, true);
assert.strictEqual(ok({ ...base, website: '' }).spam, false);

// Formula injection and control characters
for (const s of ['=SUM(A1)', '+1', '-1', '@x', '\tx', '\rx']) assert.ok(safe(s).startsWith("'"), s);
assert.strictEqual(safe('Normal text'), 'Normal text');
assert.strictEqual(safe('a=b'), 'a=b');
assert.strictEqual(clean('Hi‮evil\u0007 there\r\nok'), 'Hievil there\nok');

console.log('feedback validation: all checks passed');
