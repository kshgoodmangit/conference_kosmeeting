(() => {
    'use strict';
    const data = document.body?.dataset || {};
    const language = data.language || document.documentElement.lang || 'en';
    const siteBase = (data.siteBase || '').replace(/\/$/, '');
    const apiBase = (data.apiBase || '').replace(/\/$/, '');
    const localUrl = value => new URL(value, window.location.origin);
    const pageUrl = value => {
        if (!value || !value.trim()) return siteBase + '/';
        if (value.startsWith('#') || /^(?:[a-z][a-z\d+.-]*:|\/\/)/i.test(value)) return value;
        if (value.startsWith('/api/') || value.startsWith('/public/speakers/')) return apiUrl(value);
        if (/^\/(?:public|assets|vendor)\//.test(value)) return value;
        const url = localUrl(value);
        if (siteBase && (url.pathname === siteBase || url.pathname.startsWith(siteBase + '/'))) {
            return url.pathname + url.search + url.hash;
        }
        return siteBase + url.pathname + url.search + url.hash;
    };
    const apiUrl = value => {
        if (!apiBase) throw new Error('The conference API context is missing.');
        const url = localUrl(value);
        if (url.origin !== window.location.origin) throw new Error('A public API request must use this site.');
        let pathname = url.pathname;
        if (/^\/api\/(?:(?:boards|popups|mail)\/images|countries|security)\//.test(pathname)) {
            return pathname + url.search + url.hash;
        }
        if (pathname.startsWith('/public/speakers/')) pathname = '/speakers/' + pathname.slice('/public/speakers/'.length);
        if (!pathname.startsWith(apiBase + '/') && pathname !== apiBase) {
            if (/^\/api\/public\/\d+(?:\/|$)/.test(pathname)) throw new Error('The API belongs to another conference.');
            pathname = pathname.replace(/^\/api\/public(?=\/)/, '').replace(/^\/api(?=\/)/, '');
            pathname = apiBase + (pathname.startsWith('/') ? pathname : '/' + pathname);
        }
        url.searchParams.set('lang', language);
        return pathname + url.search + url.hash;
    };
    const t = (message, parameters = {}) => {
        let translated = language.toLowerCase().startsWith('ko')
            ? window.publicMessagesKo?.[message] || message : message;
        for (const [key, value] of Object.entries(parameters)) translated = translated.replaceAll('{' + key + '}', String(value));
        return translated;
    };
    const responseMessage = async response => {
        const body = await response.text();
        if (!response.ok) {
            try { const error = JSON.parse(body); return typeof error.message === 'string' ? error.message : ''; }
            catch { return body; }
        }
        return body;
    };
    const storageKey = name => `conference.${data.conferenceSeq || 'public'}.${name}`;
    window.PublicSite = Object.freeze({ language, pageUrl, apiUrl, t, responseMessage, storageKey });
})();
