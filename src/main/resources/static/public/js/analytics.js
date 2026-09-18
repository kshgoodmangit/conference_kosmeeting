(() => {
    'use strict';
    if (window.__congressAnalytics || location.pathname.startsWith('/admin')) return;
    window.__congressAnalytics = true;
    const allowed = () => navigator.doNotTrack !== '1' && navigator.globalPrivacyControl !== true
        && window.congressCookieConsent?.isAnalyticsAllowed() === true;
    let stopCollection;
    const syncConsent = () => {
        if (!allowed()) {
            stopCollection?.();
            stopCollection = undefined;
        } else if (!stopCollection) {
            stopCollection = startCollection();
        }
    };
    addEventListener('congress:cookie-consent-change', syncConsent);
    syncConsent();

    function startCollection() {
        const controller = new AbortController();
        let disposed = false;
        let stopped = false;
        const canSend = () => !disposed && allowed();
        const uuid = () => crypto.randomUUID();
        const storage = name => { try { return window[name]; } catch { return null; } };
        const visitorStore = storage('localStorage');
        const sessionStore = storage('sessionStorage');
        const safeRead = (store, key) => { try { return JSON.parse(store.getItem(key) || 'null'); } catch { return null; } };
        const safeWrite = (store, key, value) => { try { store.setItem(key, JSON.stringify(value)); } catch { /* In-memory fallback. */ } };
        let visitor = safeRead(visitorStore, 'conference.analytics.visitor');
        if (!visitor || typeof visitor.id !== 'string' || !Number.isFinite(visitor.expires) || visitor.expires <= Date.now())
            visitor = { id: uuid(), expires: Date.now() + 365 * 86400000 };
        safeWrite(visitorStore, 'conference.analytics.visitor', visitor);
        const incoming = new URLSearchParams(location.search);
        let referrer = '';
        try { const url = new URL(document.referrer); if (url.origin !== location.origin) referrer = url.hostname; } catch { /* Direct navigation. */ }
        const newSession = () => ({
            id: uuid(), lastActivity: Date.now(), referrerHost: referrer,
            utmSource: (incoming.get('utm_source') || '').slice(0, 100),
            utmMedium: (incoming.get('utm_medium') || '').slice(0, 100),
            utmCampaign: (incoming.get('utm_campaign') || '').slice(0, 200)
        });
        let session = safeRead(sessionStore, 'conference.analytics.session');
        if (!session || typeof session.id !== 'string' || !Number.isFinite(session.lastActivity) || Date.now() - session.lastActivity > 1800000) session = newSession();
        safeWrite(sessionStore, 'conference.analytics.session', session);
        let csrf;
        let started = false;
        let lastTick = performance.now();
        let activeAt = Date.now();
        let accumulated = 0;
        let wasVisible = document.visibilityState === 'visible';
        const retries = new Set();
        const listeners = [];
        const listen = (target, name, handler) => {
            target.addEventListener(name, handler, { passive: true });
            listeners.push(() => target.removeEventListener(name, handler));
        };
        const countTime = () => {
            const now = performance.now();
            if (wasVisible && Date.now() - activeAt <= 60000)
                accumulated += Math.max(0, Math.min(15, (now - lastTick) / 1000));
            lastTick = now;
        };
        const getConfig = async () => {
            if (!canSend()) return null;
            const response = await fetch('/api/analytics/config', { credentials: 'same-origin', cache: 'no-store', signal: controller.signal });
            if (!response.ok || !canSend()) return null;
            const config = await response.json();
            return canSend() ? config : null;
        };
        const send = type => {
            if (!canSend() || !csrf || !started || stopped) return;
            const seconds = type === 'PAGE_VIEW' ? 0 : Math.min(60, Math.floor(accumulated));
            if (type === 'HEARTBEAT' && seconds === 0) return;
            accumulated -= seconds;
            const payload = {
                eventId: uuid(), visitorId: visitor.id, sessionId: session.id,
                eventType: type, pagePath: location.pathname.slice(0, 500),
                pageTitle: document.title.slice(0, 255), referrerHost: session.referrerHost || null,
                utmSource: session.utmSource || null, utmMedium: session.utmMedium || null,
                utmCampaign: session.utmCampaign || null, durationSeconds: seconds,
                occurredAt: new Date().toISOString()
            };
            const post = async () => {
                if (!canSend() || stopped) return null;
                return fetch('/api/analytics/events', {
                    method: 'POST', credentials: 'same-origin', keepalive: true, signal: controller.signal,
                    headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
                    body: JSON.stringify(payload)
                });
            };
            // Retries retain the event ID and recheck consent before every request.
            post().then(async response => {
                if (canSend() && !stopped && response?.status === 403 && response.headers.get('X-CSRF-ERROR') === 'true') {
                    const config = await getConfig();
                    if (config && canSend()) { csrf = config; await post(); }
                }
            }).catch(() => {
                if (!canSend() || stopped) return;
                const retry = setTimeout(() => { retries.delete(retry); post().catch(() => {}); }, 2000);
                retries.add(retry);
            });
        };
        const activity = () => {
            if (!canSend() || stopped) return false;
            let rotated = false;
            if (Date.now() - session.lastActivity > 1800000 && started) {
                accumulated = 0; session = newSession(); send('PAGE_VIEW'); rotated = true;
            }
            activeAt = Date.now(); session.lastActivity = activeAt;
            safeWrite(sessionStore, 'conference.analytics.session', session);
            return rotated;
        };
        ['pointerdown', 'keydown', 'scroll'].forEach(event => listen(window, event, activity));
        getConfig().then(config => {
            if (!config || !canSend()) return;
            csrf = config; started = true; lastTick = performance.now(); send('PAGE_VIEW');
        }).catch(() => {});
        const interval = setInterval(() => {
            if (!allowed()) { syncConsent(); return; }
            if (stopped || disposed) return;
            countTime(); send('HEARTBEAT');
        }, 15000);
        listen(document, 'visibilitychange', () => {
            if (!canSend() || stopped) return;
            countTime(); wasVisible = document.visibilityState === 'visible';
            if (!wasVisible) send('HEARTBEAT');
            else { lastTick = performance.now(); activity(); }
        });
        listen(window, 'pagehide', () => { countTime(); send('PAGE_EXIT'); stopped = true; });
        listen(window, 'pageshow', event => {
            if (event.persisted && canSend()) {
                stopped = false; wasVisible = document.visibilityState === 'visible';
                const rotated = activity(); lastTick = performance.now();
                if (!rotated) send('PAGE_VIEW');
            }
        });
        return () => {
            disposed = true; stopped = true; accumulated = 0;
            controller.abort();
            clearInterval(interval);
            retries.forEach(clearTimeout);
            listeners.forEach(remove => remove());
        };
    }
})();
