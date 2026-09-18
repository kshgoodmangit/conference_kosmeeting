const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

const source = fs.readFileSync(path.join(__dirname, '../../src/main/resources/static/public/js/user.js'), 'utf8');
const start = source.indexOf("    document.querySelectorAll('[data-email-verification]')");
const end = source.indexOf('    // 관리자에서 사용하는 국가 목록', start);
assert.ok(start >= 0 && end > start);

function setup(fetch) {
    class Element {
        value = ''; textContent = ''; disabled = false; hidden = true;
        handlers = {};
        addEventListener(name, callback) { this.handlers[name] = callback; }
        fire(name) { return this.handlers[name]?.({preventDefault() {}}); }
        focus() {}
    }
    class Input extends Element { reportValidity() { return this.value.includes('@'); } }
    class Button extends Element { click() { return this.fire('click'); } }
    const email = new Input(), code = new Input(), send = new Button(), verify = new Button(), status = new Element(), csrf = new Input();
    const form = new Element();
    csrf.value = 'csrf-test-token';
    const signup = new Button(), memberLinks = new Element();
    form.querySelector = selector => selector === '[name="_csrf"]' ? csrf : signup;
    email.form = form;
    const controls = {'[data-verification-email]':email, '[data-email-code]':code, '[data-send-email-code]':send,
        '[data-verify-email-code]':verify, '[data-email-verification-status]':status,
        '[data-email-existing-member-links]':memberLinks};
    const section = {dataset:{}, querySelector: selector => controls[selector]};
    vm.runInNewContext(source.slice(start,end), {
        document: {querySelectorAll: () => [section]},
        HTMLInputElement: Input, HTMLButtonElement: Button, HTMLElement: Element,
        fetch, Date, setInterval: () => 1, clearInterval() {}
    });
    return {email,code,send,verify,status,signup,memberLinks,section};
}
const success = {ok:true, json:async()=>({message:'Code sent', expiresInSeconds:600, resendAfterSeconds:60})};

test('code remains disabled until the send succeeds; request includes CSRF and email only', async()=>{
    let resolve, request;
    const ui = setup((url, options)=>{request={url,options};return new Promise(done=>{resolve=done;});});
    assert.equal(ui.code.disabled,true);
    ui.email.value='member@example.com';
    const pending=ui.send.fire('click');
    assert.equal(ui.send.disabled,true);
    assert.equal(ui.code.disabled,true);
    assert.equal(request.url,'/api/public/members/email-verification/send');
    assert.deepEqual(JSON.parse(request.options.body),{email:'member@example.com'});
    assert.equal(request.options.headers['X-CSRF-TOKEN'],'csrf-test-token');
    resolve(success); await pending;
    assert.equal(ui.code.disabled,false);
    assert.equal(ui.verify.disabled,true);
    assert.match(ui.send.textContent,/Resend in/);
    ui.code.value='123456'; ui.code.fire('input');
    assert.equal(ui.verify.disabled,false);
    ui.email.value='other@example.com'; ui.email.fire('input');
    assert.equal(ui.code.disabled,true);
    assert.equal(ui.code.value,'');
    assert.equal(ui.verify.disabled,true);
});

test('failed send cannot enable code entry', async()=>{
    const ui=setup(async()=>({ok:false,status:503,text:async()=>'Could not send'}));
    ui.email.value='member@example.com'; await ui.send.fire('click');
    assert.equal(ui.code.disabled,true);
    assert.equal(ui.send.disabled,false);
    assert.equal(ui.status.textContent,'Could not send');
});

test('stale successful response cannot activate an edited email', async()=>{
    let resolve;
    const ui=setup(()=>new Promise(done=>{resolve=done;}));
    ui.email.value='member@example.com'; const pending=ui.send.fire('click');
    ui.email.value='other@example.com'; ui.email.fire('input');
    resolve(success); await pending;
    assert.equal(ui.code.disabled,true);
    assert.equal(ui.status.textContent,'');
});

test('rate-limited send preserves disabled input and starts retry cooldown', async()=>{
    const ui=setup(async()=>({ok:false,status:429,text:async()=>'Please wait'}));
    ui.email.value='member@example.com'; await ui.send.fire('click');
    assert.equal(ui.code.disabled,true);
    assert.equal(ui.send.disabled,true);
    assert.match(ui.send.textContent,/Resend in/);
});

async function sent(fetchVerify) {
    const ui = setup((url, options) => url.endsWith('/send') ? Promise.resolve(success) : fetchVerify(url, options));
    ui.email.value = 'member@example.com';
    await ui.send.fire('click');
    ui.code.value = '123456'; ui.code.fire('input');
    return ui;
}

test('successful verification enables signup and changing email revokes it', async()=>{
    let request;
    const ui = await sent(async(url,options)=>{
        request={url,options};
        return {ok:true,json:async()=>({existingMember:false,expiresInSeconds:1800,message:'Email verified'})};
    });
    assert.equal(ui.signup.disabled,true);
    await ui.verify.fire('click');
    assert.equal(request.url,'/api/public/members/email-verification/verify');
    assert.deepEqual(JSON.parse(request.options.body),{email:'member@example.com',code:'123456'});
    assert.equal(request.options.headers['X-CSRF-TOKEN'],'csrf-test-token');
    assert.equal(ui.signup.disabled,false);
    assert.equal(ui.code.disabled,true);
    assert.equal(ui.verify.textContent,'Verified');
    ui.email.value='other@example.com'; ui.email.fire('input');
    assert.equal(ui.signup.disabled,true);
    assert.equal(ui.section.dataset.verifiedEmail,'');
});

test('existing member gets recovery links but cannot sign up', async()=>{
    const ui = await sent(async()=>({ok:true,json:async()=>({existingMember:true,expiresInSeconds:0,message:'Already registered'})}));
    await ui.verify.fire('click');
    assert.equal(ui.memberLinks.hidden,false);
    assert.equal(ui.signup.disabled,true);
    assert.equal(ui.status.textContent,'Already registered');
    ui.email.value='other@example.com'; ui.email.fire('input');
    assert.equal(ui.memberLinks.hidden,true);
});

test('incorrect code can be retried but exhausted challenge requires a new send', async()=>{
    let status=400;
    const ui = await sent(async()=>({ok:false,status,text:async()=>'Code rejected'}));
    await ui.verify.fire('click');
    assert.equal(ui.code.disabled,false);
    assert.equal(ui.verify.disabled,false);
    assert.equal(ui.signup.disabled,true);
    status=410;
    await ui.verify.fire('click');
    assert.equal(ui.code.disabled,true);
    assert.equal(ui.verify.disabled,true);
    assert.equal(ui.status.textContent,'Code rejected');
});

test('stale verification success does not authorize changed email', async()=>{
    let resolve;
    const ui = await sent(()=>new Promise(done=>{resolve=done;}));
    const pending=ui.verify.fire('click');
    ui.email.value='other@example.com'; ui.email.fire('input');
    resolve({ok:true,json:async()=>({existingMember:false,expiresInSeconds:1800,message:'Verified'})});
    await pending;
    assert.equal(ui.signup.disabled,true);
    assert.equal(ui.section.dataset.verifiedEmail,'');
});
