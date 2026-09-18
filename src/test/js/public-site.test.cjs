const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const root = 'src/main/resources/static/public/js/';

function site(base, language = 'ko', conferenceSeq = '8') {
    const window = {location: {origin: 'https://conference.example'}};
    const sandbox = {window, URL, document: {body: {dataset: {siteBase: base, apiBase: '/api/public/' + conferenceSeq, language, conferenceSeq}}, documentElement: {lang: language}}};
    vm.runInNewContext(fs.readFileSync(root + 'public-messages.js', 'utf8'), sandbox);
    vm.runInNewContext(fs.readFileSync(root + 'public-site.js', 'utf8'), sandbox);
    return window.PublicSite;
}

test('public navigation preserves conference and language across all four URL modes', () => {
    for (const base of ['', '/ko', '/apdrc8', '/apdrc8/ko']) {
        const urls = site(base);
        for (const path of ['/join-domestic', '/join-international', '/mypage-abstract', '/mypage-registration',
                '/mypage-profile', '/mypage-password', '/mypage-certificate', '/program-at-a-glance',
                '/abstract-write?seq=123', '/abstract-review?seq=123', '/notice-detail?seq=12&page=2#attachments']) {
            assert.equal(urls.pageUrl(path), base + path);
        }
        assert.equal(urls.pageUrl('/login?password=changed'), base + '/login?password=changed');
        assert.equal(urls.pageUrl('/'), base + '/');
        assert.equal(urls.pageUrl(base + '/welcome-message'), base + '/welcome-message');
        assert.equal(urls.pageUrl('https://external.example/path'), 'https://external.example/path');
        assert.equal(urls.pageUrl('/unknown/nested/page?x=1'), base + '/unknown/nested/page?x=1');
        assert.equal(urls.pageUrl(''), base + '/');
        assert.equal(urls.pageUrl('#details'), '#details');
        assert.equal(urls.pageUrl(base + '/notice-detail?seq=12#attachments'), base + '/notice-detail?seq=12#attachments');
    }
});

test('page URLs never reinterpret former folders or notice identifiers', () => {
    for (const base of ['', '/ko', '/apdrc8', '/apdrc8/ko']) {
        const urls = site(base);
        for (const path of ['/mypage/abstract/write?seq=123', '/join/domestic',
                '/program/program-at-a-glance/', '/information/noticedetail/12?page=2#attachments']) {
            assert.equal(urls.pageUrl(path), base + path);
        }
    }
});

test('public URL helpers preserve shared assets and scope only conference resources', () => {
    const urls = site('/apdrc8/ko');
    for (const path of ['/public/img/logo.svg', '/assets/application.js?v=1', '/vendor/editor/editor.css']) {
        assert.equal(urls.pageUrl(path), path);
    }
    for (const path of ['/api/boards/images/202609/image.png', '/api/popups/images/202609/image.jpg',
            '/api/mail/images/202609/image.png', '/api/countries/used', '/api/security/csrf-token']) {
        assert.equal(urls.pageUrl(path), path);
        assert.equal(urls.apiUrl(path), path);
    }
    assert.equal(urls.pageUrl('/public/speakers/12/image'), '/api/public/8/speakers/12/image?lang=ko');
    assert.equal(urls.pageUrl('/api/sponsors/12/logo'), '/api/public/8/sponsors/12/logo?lang=ko');
});

test('API URLs scope legacy routes, retain data parameters, and never append IDs after lang', () => {
    const urls = site('/apdrc8/ko');
    assert.equal(urls.apiUrl('/api/public/abstracts/123'), '/api/public/8/abstracts/123?lang=ko');
    assert.equal(urls.apiUrl('/abstracts/123/attachments?page=2'), '/api/public/8/abstracts/123/attachments?page=2&lang=ko');
    assert.equal(urls.apiUrl('/api/public/8/abstracts?lang=en'), '/api/public/8/abstracts?lang=ko');
    assert.equal(urls.apiUrl('/api/popups/dismiss-today'), '/api/public/8/popups/dismiss-today?lang=ko');
    assert.equal(urls.apiUrl('/popups/display'), '/api/public/8/popups/display?lang=ko');
    assert.equal(urls.apiUrl('/api/analytics/events'), '/api/public/8/analytics/events?lang=ko');
    assert.throws(() => urls.apiUrl('/api/public/9/abstracts'), /another conference/);
    assert.throws(() => urls.apiUrl('https://external.example/abstracts'), /this site/);
});

test('structured errors display translated messages without destroying successful JSON DTOs', async () => {
    const urls = site('/apdrc8/ko');
    assert.equal(await urls.responseMessage({ok:false, text:async()=>'{"code":"CLOSED","message":"접수 기간이 아닙니다."}'}), '접수 기간이 아닙니다.');
    assert.equal(await urls.responseMessage({ok:false, text:async()=>'Legacy error'}), 'Legacy error');
    const dto = '{"message":"안내","registration":{"seq":4}}';
    assert.equal(await urls.responseMessage({ok:true, text:async()=>dto}), dto);
    assert.equal(urls.t('Resend in {seconds}s', {seconds:30}), '30초 후 재발송');
    assert.equal(site('', 'en').t('Login failed.'), 'Login failed.');
    assert.notEqual(urls.storageKey('analytics.session'), site('/apdrc9/ko','ko','9').storageKey('analytics.session'));
});
