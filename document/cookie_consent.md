# Public cookie preferences

The shared Thymeleaf public layout includes an English bottom banner and a native
Cookie Settings dialog. It covers the home page, content and member pages, and
public 404 pages. Administrator pages are excluded.

## Choices and storage

- **Accept All**: essential functionality and optional first-party analytics.
- **Essential Only**: keeps essential functionality; disables analytics.
- **Settings**: essential storage is always active; analytics is optional and off
  until explicitly allowed. Closing the dialog does not save a choice.
- The footer's **Cookie Settings** button reopens the dialog to change or withdraw
  the choice.
- `localStorage['conference.cookieConsent']` stores version, essential/analytics
  choices, save time and expiry. Choices expire after six calendar months, with
  month-end dates clamped to the last day of the target month. The banner returns
  after expiry or invalid/unsupported data. Increment the version when the scope
  changes and a new choice is needed.
- Storage failure defaults to analytics off. An explicit choice can still apply
  in memory on that page; an accessible status explains that it was not saved.
- Existing `congress.*` keys are migrated to `conference.*` on page load.
  Valid consent retains its original expiry; an existing new key takes priority.
  Analytics identifiers are migrated only when analytics is allowed. Old keys
  are removed after migration, and both old/new identifiers are cleared when
  analytics is disabled. Expired or malformed legacy consent never enables tracking.

## Analytics integration

`cookie-consent.js` loads before `analytics.js`. Analytics configuration requests,
identifier creation, page views and engagement tracking start only after optional
analytics is allowed. No external consent provider or analytics SDK is added.

Withdrawal, expiry and changes in another tab stop collection, abort pending
requests, clear retries/listeners, and remove `conference.analytics.visitor` and
`conference.analytics.session` from the current browser context. Already delivered
events remain in the existing analytics database. A suspended tab checks the
current choice again when restored from the back/forward cache.

Do Not Track and Global Privacy Control continue to disable analytics, including
when the visitor selects Accept All. The dialog explains this browser preference.
Existing sign-in, security and registration behavior is independent of analytics
consent. The existing Privacy Policy page is linked; its published content is not
automatically changed. Statistics now represent visitors who allow analytics.

## Verification

```powershell
node --test frontend/scripts/analytics-tracker.test.cjs
.\gradlew17.bat test --tests '*PublicTemplateRenderTest' -x npmBuildTask
```

Browser checks: first visit, Essential Only, reopen settings, enable/disable
Analytics, close without saving, reload, keyboard focus/Escape, narrow viewport.
Backend schemas and database settings do not change.
