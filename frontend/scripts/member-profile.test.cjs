const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../../src/main/resources/static/public/js/user.js'), 'utf8');

class Element {
    textContent = ''; hidden = true; disabled = false; attrs = {}; handlers = {}; focused = false;
    classList = {toggle: (name, value) => { this.attrs[name] = value; }};
    addEventListener(name, callback) { this.handlers[name] = callback; }
    setAttribute(name, value) { this.attrs[name] = value; }
    removeAttribute(name) { delete this.attrs[name]; }
    focus() { this.focused = true; }
    fire(name) { return this.handlers[name]?.({preventDefault() {}}); }
}

function setupProfile(fetch) {
    class Form extends Element { valid = true; reportValidity() { return this.valid; } }
    class Button extends Element { innerHTML = '<img alt="">Save'; }
    const form = new Form(), button = new Button(), status = new Element();
    form.action = '/api/public/members/profile';
    form.querySelector = selector => selector === 'button[type="submit"]' ? button : status;
    const redirects = [];
    const start = source.indexOf('    const memberProfileForm =');
    const end = source.indexOf('    // 로그인 처리', start);
    vm.runInNewContext(source.slice(start, end), {
        document: {querySelector: () => form}, HTMLFormElement: Form, HTMLButtonElement: Button,
        FormData: class { constructor(value) { this.form = value; } }, fetch, Error,
        window: {location: {assign: url => redirects.push(url)}}
    });
    return {form, button, status, redirects};
}

test('profile saves multipart data once, displays completion and restores the original button', async () => {
    let done, requests = 0, options;
    const ui = setupProfile((url, request) => {
        assert.equal(url, '/api/public/members/profile');
        requests++; options = request;
        return new Promise(resolve => { done = resolve; });
    });
    const pending = ui.form.fire('submit');
    await ui.form.fire('submit');
    assert.equal(requests, 1);
    assert.equal(ui.button.disabled, true);
    assert.equal(options.body.form, ui.form);
    assert.equal(options.credentials, 'same-origin');
    done({ok: true, status: 200}); await pending;
    assert.equal(ui.status.textContent, 'Profile saved.');
    assert.equal(ui.status.hidden, false);
    assert.equal(ui.status.attrs['is-error'], false);
    assert.equal(ui.button.innerHTML, '<img alt="">Save');
    assert.equal(ui.button.disabled, false);
});

test('profile does not submit invalid forms and handles server errors and expired sessions', async () => {
    const invalid = setupProfile(() => assert.fail('Unexpected request'));
    invalid.form.valid = false;
    await invalid.form.fire('submit');
    const failure = setupProfile(async () => ({ok: false, status: 400, text: async () => 'Institution is required'}));
    await failure.form.fire('submit');
    assert.equal(failure.status.textContent, 'Institution is required');
    assert.equal(failure.status.attrs['is-error'], true);
    const expired = setupProfile(async () => ({ok: false, status: 401}));
    await expired.form.fire('submit');
    assert.deepEqual(expired.redirects, ['/login']);
    const csrf = setupProfile(async () => ({ok: false, status: 403, headers: {get: () => 'true'}}));
    await csrf.form.fire('submit');
    assert.match(csrf.status.textContent, /reload this page/);
});

async function setupCountry(savedCountry) {
    class Select extends Element {
        value = ''; options = [{text: 'Select a country.'}];
        append(option) { this.options.push(option); if (option.selected) this.value = option.value; }
    }
    class Input extends Element { value = '+81'; }
    const select = new Select(), code = new Input(), display = new Element();
    select.dataset = {selectedCountry: savedCountry, countryCodeTarget: '#code', countryCodeDisplay: '#display'};
    select.form = {querySelector: selector => selector === '#code' ? code : display};
    const start = source.indexOf('    const setupCountrySelect =');
    const end = source.indexOf("    document.querySelectorAll('[data-country-select]')", start);
    vm.runInNewContext(source.slice(start, end) + '\nsetupCountrySelect(select);', {
        select, HTMLSelectElement: Select, HTMLInputElement: Input,
        Option: class { constructor(text, value, defaultSelected = false, selected = false) { Object.assign(this, {text, value, defaultSelected, selected}); } },
        fetch: async () => ({ok: true, json: async () => [{countryName: 'Japan', dialCode: '+81'}, {countryName: 'South Korea', dialCode: '+82'}]}),
        window: {alert: message => assert.fail(message)}
    });
    await new Promise(resolve => setImmediate(resolve));
    return {select, code, display};
}

test('country choice updates the visible calling code and submitted hidden value together', async () => {
    const ui = await setupCountry('Japan');
    assert.equal(ui.select.value, 'Japan');
    assert.equal(ui.display.textContent, '+81');
    ui.select.value = 'South Korea'; ui.select.fire('change');
    assert.equal(ui.code.value, '+82');
    assert.equal(ui.display.textContent, '+82');
});

test('an existing country missing from the active list stays selected', async () => {
    const ui = await setupCountry('Saved country');
    assert.equal(ui.select.value, 'Saved country');
    assert.equal(ui.code.value, '+81');
});
