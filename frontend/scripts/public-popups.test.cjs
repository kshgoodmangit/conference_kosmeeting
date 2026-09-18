const {test} = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const source = fs.readFileSync(path.resolve(__dirname, '../../src/main/resources/static/public/js/popups.js'), 'utf8');
const flush = () => new Promise(resolve => setImmediate(resolve));

function harness({layout = 1, count = 4, mobile = false, reduced = false, bannerVisible = false, fail = false, csrfRetry = false, suppressed = false, empty = false, loadFail = false} = {}) {
    const eventTarget = () => {
        const events = new Map();
        return {
            addEventListener(type, fn) { if (!events.has(type)) events.set(type, new Set()); events.get(type).add(fn); },
            removeEventListener(type, fn) { events.get(type)?.delete(fn); },
            fire(type, event = {}) { [...(events.get(type) || [])].forEach(fn => fn(event)); }
        };
    };
    let document;
    const element = (extra = {}) => ({...eventTarget(), hidden: false, dataset: {}, attributes: {}, isConnected: true,
        querySelectorAll: () => [], querySelector: () => null,
        setAttribute(name, value) { this.attributes[name] = value; },
        focus() { document.activeElement = this; }, contains(el) { return this === el; },
        ...extra});
    const cards = Array.from({length: count}, () => element());
    const close = element(), backdrop = element(), hide = element(), next = element(), prev = element(), pause = element();
    const counter = element(), error = element({hidden: true}), banner = element({hidden: !bannerVisible});
    const cookieDialog = element({open: false});
    const buttons = [close, hide, ...(count > 1 ? [next, prev, ...(layout === 4 ? [] : [pause])] : [])];
    const panel = element({contains: el => el === panel || buttons.includes(el), querySelectorAll: () => buttons});
    const selectors = {'.conference-popups-panel': panel, '[data-popup-current]': counter, '[data-popup-error]': error,
        '[data-popup-hide]': hide, '[data-popup-next]': count > 1 ? next : null, '[data-popup-prev]': count > 1 ? prev : null,
        '[data-popup-pause]': layout !== 4 && count > 1 ? pause : null};
    const root = element({hidden: true, dataset: {layout}, contains: el => panel.contains(el),
        querySelector: selector => selectors[selector],
        querySelectorAll: selector => ({'[data-popup-index]': cards, '[data-popup-close]': [backdrop, close], 'button[data-popup-close]': [close], button: buttons, img: []})[selector] || []});
    const body = element({style: {overflow: 'auto'}, append() {}}), homeLink = element();
    const trigger = element(), status = element({hidden: true});
    document = {...eventTarget(), activeElement: body, body, documentElement: {style: {overflow: 'visible'}}, hidden: false,
        getElementById: id => ({'conference-popups': suppressed ? null : root, 'cookie-banner': banner, 'cookie-preferences': cookieDialog})[id],
        createElement: () => ({content: {querySelector: () => root}}),
        querySelector: selector => ({'[data-popup-open]': trigger, '[data-popup-status]': status})[selector] || homeLink};
    const mobileMedia = {...eventTarget(), matches: mobile}, reducedMedia = {...eventTarget(), matches: reduced};
    const timers = new Map(), timeouts = new Map(), requests = [];
    let id = 0, posts = 0, reloaded = false;
    const window = {...eventTarget(), document,
        matchMedia: query => query.includes('max-width') ? mobileMedia : reducedMedia,
        setInterval: fn => { timers.set(++id, fn); return id; }, clearInterval: id => timers.delete(id),
        setTimeout: fn => { timeouts.set(++id, fn); return id; }, clearTimeout: id => timeouts.delete(id),
        location: {reload() { reloaded = true; }}};
    const fetch = async (url, options) => {
        requests.push({url, options});
        if (url === '/popups/display') {
            if (loadFail) throw new Error('offline');
            return {ok: true, status: empty ? 204 : 200, text: async () => '<section></section>'};
        }
        if (url === '/api/security/csrf-token') return {ok: true, json: async () => ({headerName: 'X-CSRF-TOKEN', token: 'fixture-token'})};
        assert.equal(url, '/api/popups/dismiss-today');
        assert.equal(options.headers['X-CSRF-TOKEN'], 'fixture-token');
        posts++;
        if (fail) throw new Error('offline');
        return csrfRetry && posts === 1 ? {ok: false, status: 403, headers: {get: () => 'true'}} : {ok: true};
    };
    vm.runInNewContext(source, {window, document, fetch, AbortController});
    return {root, cards, panel, close, backdrop, hide, next, prev, pause, counter, error, banner, document, window, trigger, status,
        timers, requests, mobileMedia, timeouts, isReloaded: () => reloaded,
        tick: () => [...timers.values()].forEach(fn => fn())};
}

test('all eight layouts support 0, 1, 2, 3 and 4 items without cloning cards', () => {
    for (let layout = 1; layout <= 8; layout++) for (let count = 0; count <= 4; count++) {
        const h = harness({layout, count});
        assert.equal(h.cards.length, count);
        assert.equal(h.root.hidden, count === 0);
        if (!count) continue;
        assert.equal(h.cards.filter(c => !c.hidden).length, layout >= 4 && layout <= 7 ? Math.min(3, count) : 1);
        if (count > 1) {
            h.next.fire('click'); assert.equal(h.counter.textContent, '2');
            h.prev.fire('click'); assert.equal(h.counter.textContent, '1');
            h.prev.fire('click'); assert.equal(h.counter.textContent, String(count));
        } else assert.equal(h.timers.size, 0);
    }
});
test('mobile presents one interactive card, and resizing restores the desktop row', () => {
    const h = harness({layout: 5, mobile: true});
    assert.equal(h.cards.filter(c => !c.hidden).length, 1);
    h.mobileMedia.matches = false; h.mobileMedia.fire('change');
    assert.equal(h.cards.filter(c => !c.hidden && !c.inert).length, 3);
});
test('stacked cards are inert, and manual deck and reduced-motion never auto-advance', () => {
    for (const layout of [4, 6, 7]) {
        const h = harness({layout});
        assert.equal(h.cards.filter(c => !c.inert).length, 1);
    }
    assert.equal(harness({layout: 4}).timers.size, 0);
    assert.equal(harness({reduced: true}).timers.size, 0);
});
test('rotation pauses on hover, keyboard interaction, explicit pause and hidden tabs', () => {
    const h = harness();
    h.tick(); assert.equal(h.counter.textContent, '2');
    h.panel.fire('pointerenter'); assert.equal(h.timers.size, 0);
    h.panel.fire('pointerleave'); assert.equal(h.timers.size, 1);
    h.pause.fire('click'); assert.equal(h.timers.size, 0);
    h.pause.fire('click'); assert.equal(h.timers.size, 1);
    h.document.hidden = true; h.document.fire('visibilitychange'); assert.equal(h.timers.size, 0);
    h.document.hidden = false; h.document.activeElement = h.next; h.panel.fire('focusin'); assert.equal(h.timers.size, 0);
});
test('backdrop clicks keep overlay layouts open; X and Escape close without persisting', () => {
    for (const layout of [4, 5, 6, 7, 8]) for (const action of ['close', 'escape']) {
        const h = harness({layout});
        h.backdrop.fire('click');
        assert.equal(h.root.hidden, false);
        assert.equal(h.document.body.style.overflow, 'hidden');
        if (action === 'close') h.close.fire('click');
        else h.document.fire('keydown', {key: 'Escape', preventDefault() {}});
        assert.equal(h.root.hidden, true);
        assert.equal(h.document.body.style.overflow, 'auto');
        assert.equal(h.requests.length, 0);
    }
});

test('ordinary close does not persist and restores the previous scroll state', () => {
    const h = harness({layout: 8});
    assert.equal(h.document.body.style.overflow, 'hidden');
    assert.equal(h.document.documentElement.style.overflow, 'hidden');
    h.close.fire('click');
    assert.equal(h.root.hidden, true); assert.equal(h.document.body.style.overflow, 'auto');
    assert.equal(h.document.documentElement.style.overflow, 'visible');
    assert.equal(h.timers.size, 0); assert.equal(h.requests.length, 0);
});
test('hiding today waits for the server and prevents duplicate submissions', async () => {
    const h = harness({layout: 8});
    h.hide.fire('click'); h.hide.fire('click');
    assert.equal(h.root.hidden, false);
    h.close.fire('click'); assert.equal(h.root.hidden, false);
    await flush();
    assert.equal(h.requests.filter(r => r.options.method === 'POST').length, 1);
    assert.equal(h.root.hidden, true); assert.equal(h.document.body.style.overflow, 'auto');
});
test('failed persistence keeps the dialog available and allows ordinary close', async () => {
    const h = harness({fail: true}); h.hide.fire('click'); await flush();
    assert.equal(h.root.hidden, false); assert.equal(h.error.hidden, false); assert.equal(h.hide.disabled, false);
    h.close.fire('click'); assert.equal(h.root.hidden, true);
});
test('expired CSRF retries once with a fresh token', async () => {
    const h = harness({csrfRetry: true}); h.hide.fire('click'); await flush();
    assert.equal(h.requests.length, 4); assert.equal(h.root.hidden, true);
});
test('cookie choice appears first and history-cache restore revalidates with server', () => {
    const h = harness({bannerVisible: true}); assert.equal(h.root.hidden, true);
    h.banner.hidden = true; h.window.fire('congress:cookie-consent-change'); assert.equal(h.root.hidden, false);
    h.window.fire('pageshow', {persisted: true}); assert.equal(h.isReloaded(), true);
});

test('quick menu reopens after daily dismissal without changing the saved preference', async () => {
    const h = harness({layout: 8});
    h.hide.fire('click'); await flush();
    const requests = h.requests.length;
    h.trigger.focus(); h.trigger.fire('click');
    assert.equal(h.root.hidden, false);
    assert.equal(h.requests.length, requests);
    assert.equal(h.document.body.style.overflow, 'hidden');
    h.document.fire('keydown', {key: 'Escape', preventDefault() {}});
    assert.equal(h.root.hidden, true);
    assert.equal(h.document.activeElement, h.trigger);
    h.window.fire('congress:cookie-consent-change');
    assert.equal(h.root.hidden, true);
    h.trigger.fire('click');
    assert.equal(h.root.hidden, false);
});

test('suppressed home loads popups only on demand and prevents duplicate requests', async () => {
    const h = harness({suppressed: true});
    assert.equal(h.requests.length, 0);
    h.trigger.focus(); h.trigger.fire('click'); h.trigger.fire('click'); await flush();
    assert.equal(h.requests.length, 1);
    assert.equal(h.requests[0].url, '/popups/display');
    assert.equal(h.requests[0].options.cache, 'no-store');
    assert.equal(h.root.hidden, false);
    assert.equal(h.trigger.disabled, false);
    h.close.fire('click'); h.trigger.fire('click');
    assert.equal(h.root.hidden, false);
    assert.equal(h.requests.length, 1);
});

test('empty and failed manual loads offer feedback and allow retry', async () => {
    for (const options of [{empty: true}, {loadFail: true}]) {
        const h = harness({suppressed: true, ...options});
        h.trigger.fire('click'); await flush();
        assert.equal(h.root.hidden, true);
        assert.equal(h.status.hidden, false);
        assert.equal(h.trigger.disabled, false);
        h.trigger.fire('click'); await flush();
        assert.equal(h.requests.length, 2);
    }
});
