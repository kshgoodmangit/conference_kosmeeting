const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../../src/main/resources/static/public/js/password-change.js'), 'utf8');

function setup(fetch) {
    class Element {
        value = ''; textContent = ''; hidden = true; disabled = false; readOnly = false;
        attrs = {}; handlers = {}; focused = false;
        addEventListener(name, callback) { this.handlers[name] = callback; }
        fire(name) { return this.handlers[name]?.({preventDefault() {}}); }
        setAttribute(name, value) { this.attrs[name] = value; }
        getAttribute(name) { return this.attrs[name]; }
        removeAttribute(name) { delete this.attrs[name]; }
        focus() { this.focused = true; }
    }
    class Form extends Element {}
    const form = new Form(), submit = new Element(), status = new Element(), csrf = new Element();
    csrf.value = 'csrf-test';
    const names = ['currentPassword', 'newPassword', 'newPasswordConfirm'];
    const fields = Object.fromEntries(names.map(name => [name, new Element()]));
    const errors = Object.fromEntries(names.map(name => [`#${name}-error`, new Element()]));
    fields.currentPassword.value = 'OldPassword123!';
    fields.newPassword.value = fields.newPasswordConfirm.value = 'NewPassword123!';
    const toggle = new Element();
    toggle.dataset = {passwordToggle: 'current'};
    toggle.attrs['aria-label'] = 'Show current password';
    fields.currentPassword.type = 'password';
    form.action = '/api/public/members/password/change';
    form.elements = {namedItem: name => fields[name]};
    form.querySelector = selector => ({'button[type="submit"]': submit, '#password-change-status': status,
        '[data-change-csrf]': csrf, '#current': fields.currentPassword, ...errors})[selector];
    form.querySelectorAll = () => [toggle];
    form.reset = () => names.forEach(name => { fields[name].value = ''; });
    const redirects = [], timers = [];
    vm.runInNewContext(source, {
        document: {querySelector: () => form}, HTMLFormElement: Form,
        window: {location: {replace: url => redirects.push(url)}},
        fetch, AbortController, setTimeout: (callback, ms) => { timers.push({callback, ms}); return timers.length; }, clearTimeout() {}
    });
    return {form, submit, status, fields, errors, toggle, redirects, timers};
}
const response = (status, body = {}, retry = null) => ({ok: status === 204, status,
    headers: {get: key => key === 'Retry-After' ? retry : null}, json: async () => body});

test('submits only passwords with CSRF, prevents duplicate requests and clears fields before login', async () => {
    let request, resolve;
    const ui = setup((url, options) => { request = {url, options}; return new Promise(done => { resolve = done; }); });
    const pending = ui.form.fire('submit');
    assert.equal(ui.submit.disabled, true);
    assert.equal(ui.fields.newPassword.readOnly, true);
    assert.equal(ui.submit.textContent, 'Changing...');
    assert.equal(request.options.headers['X-CSRF-TOKEN'], 'csrf-test');
    assert.equal(request.options.credentials, 'same-origin');
    assert.equal(request.url, '/api/public/members/password/change');
    assert.deepEqual(JSON.parse(request.options.body), {
        currentPassword: 'OldPassword123!', newPassword: 'NewPassword123!', newPasswordConfirm: 'NewPassword123!'
    });
    await ui.form.fire('submit');
    resolve(response(204));
    await pending;
    assert.deepEqual(ui.redirects, ['/login?password=changed']);
    assert.equal(ui.fields.currentPassword.value, '');
    assert.equal(ui.submit.disabled, true);
});

test('invalid lengths, whitespace, same password and mismatches never reach the server', async () => {
    const ui = setup(() => { throw new Error('Unexpected request'); });
    for (const value of ['short', '12345678901234567', ' Password123', 'OldPassword123!']) {
        ui.fields.newPassword.value = value;
        await ui.form.fire('submit');
        assert.equal(ui.errors['#newPassword-error'].hidden, false);
        assert.equal(ui.fields.newPassword.attrs['aria-invalid'], 'true');
    }
    ui.fields.newPassword.value = 'ValidPassword';
    await ui.form.fire('submit');
    assert.equal(ui.errors['#newPasswordConfirm-error'].hidden, false);
});

test('wrong current password focuses field and allows retry', async () => {
    const ui = setup(async () => response(400, {field: 'currentPassword', message: 'Your current password is incorrect.'}));
    await ui.form.fire('submit');
    assert.equal(ui.errors['#currentPassword-error'].textContent, 'Your current password is incorrect.');
    assert.equal(ui.fields.currentPassword.focused, true);
    assert.equal(ui.submit.disabled, false);
    ui.fields.currentPassword.fire('input');
    assert.equal(ui.errors['#currentPassword-error'].hidden, true);
});

test('expired login redirects and rate limit disables retries', async () => {
    const expired = setup(async () => response(401));
    await expired.form.fire('submit');
    assert.deepEqual(expired.redirects, ['/login?password=session-expired']);
    const limited = setup(async () => response(429, {message: 'Too many attempts.'}, '3600'));
    await limited.form.fire('submit');
    assert.equal(limited.submit.disabled, true);
    assert.equal(limited.status.textContent, 'Too many attempts.');
    limited.timers.find(timer => timer.ms === 3600000).callback();
    assert.equal(limited.submit.disabled, false);
});

test('CSRF expiry and connection failures display actionable errors', async () => {
    const csrf = setup(async () => ({...response(403), headers: {get: () => 'true'}}));
    await csrf.form.fire('submit');
    assert.match(csrf.status.textContent, /reload this page/);
    const network = setup(async () => { throw new TypeError('Failed to fetch'); });
    await network.form.fire('submit');
    assert.match(network.status.textContent, /connection/);
    assert.equal(network.submit.disabled, false);
});

test('show and hide preserves password and updates accessibility state', () => {
    const ui = setup(() => {});
    ui.toggle.fire('click');
    assert.equal(ui.fields.currentPassword.type, 'text');
    assert.equal(ui.toggle.attrs['aria-pressed'], 'true');
    ui.toggle.fire('click');
    assert.equal(ui.fields.currentPassword.type, 'password');
    assert.equal(ui.fields.currentPassword.value, 'OldPassword123!');
    assert.equal(ui.toggle.attrs['aria-label'], 'Show current password');
});
