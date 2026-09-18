const {test} = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const {randomUUID} = require('node:crypto');
const source = name => fs.readFileSync(path.resolve(__dirname, '../../src/main/resources/static/public/js', name), 'utf8');
const flush = () => new Promise(resolve => setImmediate(resolve));
const KEY = 'conference.cookieConsent';
function harness({pathname = '/', tracking = false, gpc = false, blockedStorage = false, consent = true, configDelay = false, failPost = false, csrfRetry = false, savedChoice, legacyChoice, legacyAnalytics = false} = {}) {
    let now = Date.parse('2026-08-31T12:00:00Z');
    const requests = [], sent = [], timeouts = new Map(), intervals = new Map();
    let serial = 0, resolveConfig;
    const eventTarget = () => {
        const listeners = new Map();
        return {
            addEventListener(name, fn) { if (!listeners.has(name)) listeners.set(name, new Set()); listeners.get(name).add(fn); },
            removeEventListener(name, fn) { listeners.get(name)?.delete(fn); },
            dispatchEvent(event) { [...(listeners.get(event.type) || [])].forEach(fn => { if (listeners.get(event.type).has(fn)) fn(event); }); }
        };
    };
    const store = () => { const entries = new Map(); return {getItem: key => entries.get(key) ?? null, setItem: (key, value) => entries.set(key, value), removeItem: key => entries.delete(key), clear: () => entries.clear()}; };
    const local = store(), session = store();
    if (savedChoice !== undefined) local.setItem(KEY, savedChoice);
    else if (consent !== null) local.setItem(KEY, JSON.stringify({version: 1, essential: true, analytics: consent, savedAt: now, expiresAt: now + 180 * 86400000}));
    local.setItem('conference.analytics.visitor', JSON.stringify({id: 'previous-visitor', expires: now + 365 * 86400000}));
    if (legacyChoice !== undefined) local.setItem('congress.cookieConsent', legacyChoice);
    if (legacyAnalytics) {
        local.removeItem('conference.analytics.visitor');
        local.setItem('congress.analytics.visitor', JSON.stringify({id: 'legacy-visitor', expires: now + 365 * 86400000}));
        session.setItem('congress.analytics.session', JSON.stringify({id: 'legacy-session', lastActivity: now}));
    }
    const nodes = {};
    const node = id => nodes[id] = {
        ...eventTarget(), hidden: true, style: {}, checked: false, disabled: false, dataset: {}, open: false,
        getBoundingClientRect: () => ({height: 140}), contains: () => false,
        closest: () => null, focus() { document.activeElement = this; },
        showModal() { this.open = true; }, close() { this.open = false; this.dispatchEvent({type: 'close'}); }
    };
    ['cookie-banner', 'cookie-preferences', 'cookie-analytics', 'cookie-status', 'cookie-banner-spacer', 'cookie-browser-preference', 'cookie-preferences-save', 'cookie-preferences-close'].forEach(node);
    const settings = [node('settings')];
    const all = node('all'), essential = node('essential');
    all.dataset.cookieChoice = 'all'; essential.dataset.cookieChoice = 'essential';
    const document = {...eventTarget(), title: 'Test', referrer: 'https://www.google.com/?q=private', visibilityState: 'visible',
        getElementById: id => nodes[id], querySelectorAll: selector => selector === '[data-cookie-settings]' ? settings : [all, essential]};
    const config = {ok: true, status: 200, json: async () => ({headerName: 'X-CSRF-TOKEN', token: 'test-only'})};
    const context = {...eventTarget(), document, location: {pathname, search: '?email=private&utm_source=example', origin: 'https://example.test'},
        navigator: {doNotTrack: tracking ? '1' : '0', globalPrivacyControl: gpc}, crypto: {randomUUID}, localStorage: local, sessionStorage: session,
        URL, URLSearchParams, AbortController, CustomEvent: class {constructor(type) {this.type = type;}},
        performance: {now: () => now}, Date: class extends Date {constructor(...args) {super(...(args.length ? args : [now]));} static now() {return now;}},
        setInterval: fn => {intervals.set(++serial, fn); return serial;}, clearInterval: id => intervals.delete(id),
        setTimeout: (fn, ms) => {timeouts.set(++serial, {fn, at: now + ms}); return serial;}, clearTimeout: id => timeouts.delete(id),
        fetch: async (url, options) => {
            requests.push({url, options});
            if (options?.body) {
                sent.push(JSON.parse(options.body));
                if (failPost) throw new Error('offline');
                if (csrfRetry) return {ok: false, status: 403, headers: {get: () => 'true'}};
            } else if (configDelay) return new Promise(resolve => {resolveConfig = () => resolve(config);});
            return config;
        }
    };
    context.window = context;
    if (blockedStorage) ['localStorage', 'sessionStorage'].forEach(name => Object.defineProperty(context, name, {get() {throw new Error('denied');}}));
    vm.createContext(context);
    vm.runInContext(source('cookie-consent.js'), context);
    vm.runInContext(source('analytics.js'), context);
    return {
        sent, requests, document, local, session, nodes, context,
        advance: seconds => { now += seconds * 1000; },
        timers: () => [...timeouts].forEach(([id, timer]) => { if (timer.at <= now && timeouts.delete(id)) timer.fn(); }),
        tick: () => [...intervals.values()].forEach(fn => fn()),
        event: (name, event = {}) => (name === 'visibilitychange' ? document : context).dispatchEvent({type: name, ...event}),
        click: id => nodes[id].dispatchEvent({type: 'click'}), resolveConfig: () => resolveConfig?.()
    };
}
test('consented view, incremental visible engagement and no query PII', async () => {
    const h = harness(); await flush();
    assert.equal(h.sent.length, 1); assert.equal(h.sent[0].pagePath, '/'); assert.equal(h.sent[0].referrerHost, 'www.google.com');
    h.advance(15); h.tick(); h.advance(7); h.document.visibilityState = 'hidden'; h.event('visibilitychange'); h.event('pagehide');
    await flush(); assert.equal(h.sent.filter(e => e.eventType === 'PAGE_VIEW').length, 1);
    assert.equal(h.sent.reduce((sum, e) => sum + e.durationSeconds, 0), 22);
    assert.equal(new Set(h.sent.map(e => e.eventId)).size, h.sent.length);
});
test('no choice or essential-only choice makes no analytics request and removes old identifiers', async () => {
    for (const consent of [null, false]) {
        const h = harness({consent}); h.advance(20); h.tick(); h.event('pointerdown'); await flush();
        assert.equal(h.requests.length, 0); assert.equal(h.local.getItem('conference.analytics.visitor'), null);
        assert.equal(h.session.getItem('conference.analytics.session'), null);
        assert.equal(h.nodes['cookie-banner'].hidden, consent !== null);
    }
});
test('accepting starts immediately, saves six calendar months and preserves consent on navigation', async () => {
    const h = harness({consent: null}); h.click('all'); await flush();
    assert.equal(h.sent.length, 1); assert.equal(h.nodes['cookie-banner'].hidden, true);
    const choice = JSON.parse(h.local.getItem(KEY));
    assert.equal(choice.analytics, true); assert.equal(new Date(choice.expiresAt).toISOString(), '2027-02-28T12:00:00.000Z');
    const next = harness({savedChoice: JSON.stringify(choice)}); await flush();
    assert.equal(next.nodes['cookie-banner'].hidden, true); assert.equal(next.sent.length, 1);
});
test('settings preserve choice, allow withdrawal and can enable analytics again without duplicate views', async () => {
    const h = harness(); await flush(); h.click('settings');
    assert.equal(h.nodes['cookie-preferences'].open, true); assert.equal(h.nodes['cookie-analytics'].checked, true);
    h.nodes['cookie-analytics'].checked = false; h.click('cookie-preferences-save');
    assert.equal(h.nodes['cookie-preferences'].open, false);
    assert.equal(JSON.parse(h.local.getItem(KEY)).analytics, false);
    const count = h.sent.length; h.advance(30); h.tick(); h.event('pointerdown'); h.event('pagehide'); await flush();
    assert.equal(h.sent.length, count); assert.equal(h.local.getItem('conference.analytics.visitor'), null);
    assert.equal(h.session.getItem('conference.analytics.session'), null);
    assert.ok(h.requests.every(r => r.options.signal.aborted));
    h.click('all'); await flush();
    assert.equal(h.sent.filter(e => e.eventType === 'PAGE_VIEW').length, 2);
});
test('closing settings without saving leaves initial consent unset', () => {
    const h = harness({consent: null}); h.click('settings'); h.nodes['cookie-analytics'].checked = true; h.click('cookie-preferences-close');
    assert.equal(h.local.getItem(KEY), null); assert.equal(h.nodes['cookie-banner'].hidden, false); assert.equal(h.requests.length, 0);
});
test('respects DNT and GPC even after Accept All; excludes administrator pages', async () => {
    for (const options of [{tracking: true}, {gpc: true}, {pathname: '/admin/dashboard'}]) {
        const h = harness(options); h.click('all'); await flush(); assert.equal(h.requests.length, 0);
    }
});
test('blocked storage defaults off but explicit consent enables in-memory collection', async () => {
    const h = harness({blockedStorage: true}); await flush(); assert.equal(h.requests.length, 0);
    h.click('all'); await flush(); assert.equal(h.sent[0].eventType, 'PAGE_VIEW');
    assert.match(h.nodes['cookie-status'].textContent, /could not save/);
    h.click('essential'); h.advance(15); h.tick(); await flush(); assert.equal(h.sent.length, 1);
});
test('expired, malformed and unsupported choices fail closed', async () => {
    const bad = ['{', '{}', JSON.stringify({version: 99, essential: true, analytics: true, savedAt: 1, expiresAt: 9999999999999}),
        JSON.stringify({version: 1, essential: true, analytics: true, savedAt: 1, expiresAt: 2})];
    for (const savedChoice of bad) {
        const h = harness({savedChoice}); await flush(); assert.equal(h.requests.length, 0); assert.equal(h.nodes['cookie-banner'].hidden, false);
    }
});
test('expiry in an open tab stops collection and shows the banner again', async () => {
    const h = harness(); await flush(); const count = h.sent.length;
    h.advance(181 * 86400); h.timers(); h.tick(); h.event('pointerdown'); await flush();
    assert.equal(h.sent.length, count); assert.equal(h.nodes['cookie-banner'].hidden, false); assert.equal(h.local.getItem('conference.analytics.visitor'), null);
});
test('cross-tab withdrawal and clearing storage stop collection', async () => {
    for (const key of [KEY, null]) {
        const h = harness(); await flush(); h.local.removeItem(KEY); h.event('storage', {key});
        const count = h.sent.length; h.advance(30); h.tick(); await flush();
        assert.equal(h.sent.length, count); assert.equal(h.nodes['cookie-banner'].hidden, false);
        assert.equal(h.session.getItem('conference.analytics.session'), null);
    }
});
test('withdrawal while configuration is pending never sends a view', async () => {
    const h = harness({configDelay: true}); h.click('essential'); h.resolveConfig(); await flush(); assert.equal(h.sent.length, 0);
});
test('withdrawal cancels network retries and stale configuration retries', async () => {
    const h = harness({failPost: true}); await flush(); h.click('essential'); h.advance(3); h.timers(); await flush(); assert.equal(h.sent.length, 1);
    const retry = harness({csrfRetry: true}); await flush(); const count = retry.requests.length;
    retry.click('essential'); retry.advance(3); retry.timers(); await flush(); assert.equal(retry.requests.length, count);
});
test('bfcache restore after idle starts exactly one new session view', async () => {
    const h = harness(); await flush(); const first = h.sent[0].sessionId;
    h.event('pagehide'); h.advance(1900); h.event('pageshow', {persisted: true}); await flush();
    const views = h.sent.filter(e => e.eventType === 'PAGE_VIEW'); assert.equal(views.length, 2); assert.notEqual(views[1].sessionId, first);
});
test('bfcache restore after another tab withdraws never resumes analytics', async () => {
    const h = harness(); await flush(); h.event('pagehide'); const count = h.sent.length;
    h.local.removeItem(KEY); h.event('pageshow', {persisted: true}); await flush(); assert.equal(h.sent.length, count);
});

test('legacy consent and identifiers migrate without changing expiry or counting a new visitor', async () => {
    const legacyChoice = JSON.stringify({version: 1, essential: true, analytics: true, savedAt: Date.parse('2026-08-01T00:00:00Z'), expiresAt: Date.parse('2027-02-01T00:00:00Z')});
    const h = harness({consent: null, legacyChoice, legacyAnalytics: true}); await flush();
    assert.equal(h.local.getItem(KEY), legacyChoice);
    assert.equal(h.local.getItem('congress.cookieConsent'), null);
    assert.equal(h.local.getItem('congress.analytics.visitor'), null);
    assert.equal(h.session.getItem('congress.analytics.session'), null);
    assert.equal(h.sent[0].visitorId, 'legacy-visitor');
    assert.equal(h.sent[0].sessionId, 'legacy-session');
    assert.equal(h.nodes['cookie-banner'].hidden, true);
});
test('new rejection takes priority over legacy approval and deletes all analytics identifiers', async () => {
    const legacyChoice = JSON.stringify({version: 1, essential: true, analytics: true, savedAt: 1, expiresAt: Date.parse('2027-02-01T00:00:00Z')});
    const h = harness({consent: false, legacyChoice, legacyAnalytics: true}); await flush();
    assert.equal(JSON.parse(h.local.getItem(KEY)).analytics, false);
    assert.equal(h.requests.length, 0);
    for (const key of ['congress.cookieConsent', 'congress.analytics.visitor', 'conference.analytics.visitor']) assert.equal(h.local.getItem(key), null);
    for (const key of ['congress.analytics.session', 'conference.analytics.session']) assert.equal(h.session.getItem(key), null);
});
test('legacy rejection is preserved; expired or malformed legacy consent cannot enable analytics', async () => {
    for (const legacyChoice of ['{', JSON.stringify({version: 1, essential: true, analytics: true, savedAt: 1, expiresAt: 2}),
        JSON.stringify({version: 1, essential: true, analytics: false, savedAt: 1, expiresAt: Date.parse('2027-02-01T00:00:00Z')})]) {
        const h = harness({consent: null, legacyChoice, legacyAnalytics: true}); await flush();
        assert.equal(h.requests.length, 0);
        assert.equal(h.local.getItem('congress.cookieConsent'), null);
        assert.equal(h.local.getItem('congress.analytics.visitor'), null);
        assert.equal(h.session.getItem('congress.analytics.session'), null);
        assert.equal(h.nodes['cookie-banner'].hidden, Boolean(h.local.getItem(KEY)));
    }
});
