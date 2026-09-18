(() => {
    'use strict';
    if (window.congressCookieConsent || location.pathname.startsWith('/admin')) return;
    const KEY = 'conference.cookieConsent';
    const VERSION = 1;
    const banner = document.getElementById('cookie-banner');
    const dialog = document.getElementById('cookie-preferences');
    if (!banner || !dialog) return;
    const analytics = document.getElementById('cookie-analytics');
    const settings = document.querySelectorAll('[data-cookie-settings]');
    const status = document.getElementById('cookie-status');
    let returnFocus;
    let expiryTimer;
    const browserOptOut = () => navigator.doNotTrack === '1' || navigator.globalPrivacyControl === true;
    const valid = value => value && value.version === VERSION && value.essential === true
        && typeof value.analytics === 'boolean' && Number.isFinite(value.expiresAt)
        && Number.isFinite(value.savedAt) && value.savedAt <= Date.now()
        && value.expiresAt > Date.now() && value.expiresAt > value.savedAt;
    const read = () => {
        try { const value = JSON.parse(localStorage.getItem(KEY)); return valid(value) ? value : null; }
        catch { return null; }
    };
    // Move old keys once, preserving the original expiry and any newer choice.
    const migrate = (storageName, oldKey, newKey, validate = () => true) => {
        try {
            const store = window[storageName];
            const oldValue = store.getItem(oldKey);
            if (oldValue === null) return;
            if (store.getItem(newKey) === null) {
                let parsed;
                try { parsed = JSON.parse(oldValue); } catch { parsed = null; }
                if (validate(parsed)) store.setItem(newKey, oldValue);
            }
            store.removeItem(oldKey);
        } catch { /* Do not enable analytics if migration cannot persist consent. */ }
    };
    migrate('localStorage', 'congress.cookieConsent', KEY, valid);
    let choice = read();
    if (choice?.analytics && !browserOptOut()) {
        migrate('localStorage', 'congress.analytics.visitor', 'conference.analytics.visitor');
        migrate('sessionStorage', 'congress.analytics.session', 'conference.analytics.session');
    }
    const clearAnalytics = () => {
        try { localStorage.removeItem('conference.analytics.visitor'); } catch { /* Storage unavailable. */ }
        try { sessionStorage.removeItem('conference.analytics.session'); } catch { /* Storage unavailable. */ }
        try { localStorage.removeItem('congress.analytics.visitor'); } catch { /* Legacy storage unavailable. */ }
        try { sessionStorage.removeItem('congress.analytics.session'); } catch { /* Legacy storage unavailable. */ }
    };
    const isAnalyticsAllowed = () => Boolean(valid(choice) && choice.analytics && !browserOptOut());
    const resize = () => {
        const height = banner.hidden ? 0 : banner.getBoundingClientRect().height;
        document.getElementById('cookie-banner-spacer').style.height = `${height}px`;
    };
    const update = () => {
        if (!valid(choice)) choice = null;
        if (!isAnalyticsAllowed()) clearAnalytics();
        banner.hidden = Boolean(choice);
        analytics.checked = isAnalyticsAllowed();
        analytics.disabled = browserOptOut();
        document.getElementById('cookie-browser-preference').hidden = !browserOptOut();
        resize();
        clearTimeout(expiryTimer);
        if (choice) expiryTimer = setTimeout(update, Math.min(choice.expiresAt - Date.now(), 2147483647));
        window.dispatchEvent(new CustomEvent('congress:cookie-consent-change'));
    };
    const close = () => {
        if (dialog.open) dialog.close();
    };
    const save = allowed => {
        const now = new Date();
        const expires = new Date(now);
        // Clamp month-end dates (for example August 31 -> February 28).
        expires.setUTCDate(1);
        expires.setUTCMonth(expires.getUTCMonth() + 6);
        const lastDay = new Date(Date.UTC(expires.getUTCFullYear(), expires.getUTCMonth() + 1, 0)).getUTCDate();
        expires.setUTCDate(Math.min(now.getUTCDate(), lastDay));
        choice = { version: VERSION, essential: true, analytics: allowed && !browserOptOut(), savedAt: now.getTime(), expiresAt: expires.getTime() };
        let persisted = false;
        try { localStorage.setItem(KEY, JSON.stringify(choice)); persisted = true; } catch { /* Honor the choice in memory for this page. */ }
        close();
        update();
        // Preserve the viewport when moving focus to the footer settings button.
        if (banner.contains(document.activeElement)) settings[0]?.focus({ preventScroll: true });
        status.textContent = persisted ? 'Cookie preferences saved.' : 'Cookie preferences applied for this page. Your browser could not save them for future visits.';
    };
    settings.forEach(button => {
        button.hidden = false;
        button.addEventListener('click', () => {
            returnFocus = button;
            analytics.checked = isAnalyticsAllowed();
            dialog.showModal();
        });
    });
    document.querySelectorAll('[data-cookie-choice]').forEach(button => button.addEventListener('click', () => save(button.dataset.cookieChoice === 'all')));
    document.getElementById('cookie-preferences-save').addEventListener('click', () => save(analytics.checked));
    document.getElementById('cookie-preferences-close').addEventListener('click', close);
    dialog.addEventListener('close', () => {
        const target = returnFocus && !returnFocus.closest('[hidden]') ? returnFocus : settings[0];
        target?.focus({ preventScroll: true });
    });
    window.congressCookieConsent = Object.freeze({ isAnalyticsAllowed });
    addEventListener('storage', event => {
        if (event.key === KEY || event.key === null) { choice = read(); update(); }
    });
    // Refresh after a back/forward cache restore or a long-inactive tab.
    addEventListener('pageshow', event => { if (event.persisted) { choice = read(); update(); } });
    addEventListener('focus', () => { if (choice && !valid(choice)) update(); });
    addEventListener('resize', resize);
    if (typeof ResizeObserver !== 'undefined') new ResizeObserver(resize).observe(banner);
    update();
})();
