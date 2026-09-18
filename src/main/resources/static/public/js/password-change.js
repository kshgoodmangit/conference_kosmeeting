(() => {
    const form = document.querySelector('#member-password-change-form');
    if (!(form instanceof HTMLFormElement)) return;

    const names = ['currentPassword', 'newPassword', 'newPasswordConfirm'];
    const fields = Object.fromEntries(names.map(name => [name, form.elements.namedItem(name)]));
    const submit = form.querySelector('button[type="submit"]');
    const status = form.querySelector('#password-change-status');
    let busy = false;
    let locked = false;
    const clearErrors = () => {
        status.hidden = true;
        names.forEach(name => {
            fields[name].removeAttribute('aria-invalid');
            form.querySelector(`#${name}-error`).hidden = true;
        });
    };
    const showError = (message, field) => {
        if (names.includes(field)) {
            const error = form.querySelector(`#${field}-error`);
            error.textContent = message;
            error.hidden = false;
            fields[field].setAttribute('aria-invalid', 'true');
            fields[field].focus();
        } else {
            status.textContent = message;
            status.hidden = false;
            status.focus();
        }
    };
    names.forEach(name => fields[name].addEventListener('input', clearErrors));
    form.querySelectorAll('[data-password-toggle]').forEach(button => {
        button.addEventListener('click', () => {
            const input = form.querySelector(`#${button.dataset.passwordToggle}`);
            const visible = input.type === 'password';
            input.type = visible ? 'text' : 'password';
            button.setAttribute('aria-pressed', String(visible));
            button.setAttribute('aria-label', window.PublicSite.t(visible ? window.PublicSite.t('Hide password') : window.PublicSite.t('Show password')));
        });
    });

    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (busy || locked) return;
        clearErrors();
        const data = Object.fromEntries(names.map(name => [name, fields[name].value]));
        if (!data.currentPassword.trim()) return showError(window.PublicSite.t('Enter your current password.'), 'currentPassword');
        if (data.newPassword.length < 8 || data.newPassword.length > 16 || data.newPassword !== data.newPassword.trim()) {
            return showError(window.PublicSite.t('Use 8-16 characters without leading or trailing spaces.'), 'newPassword');
        }
        if (data.newPassword === data.currentPassword.trim()) {
            return showError(window.PublicSite.t('Choose a password different from your current password.'), 'newPassword');
        }
        if (data.newPassword !== data.newPasswordConfirm) return showError(window.PublicSite.t('Passwords do not match.'), 'newPasswordConfirm');

        busy = true;
        submit.disabled = true;
        submit.textContent = window.PublicSite.t('Changing...');
        form.setAttribute('aria-busy', 'true');
        names.forEach(name => { fields[name].readOnly = true; });
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 20000);
        try {
            const csrf = form.querySelector('[data-change-csrf]');
            const response = await fetch(form.action, {
                method: 'POST', credentials: 'same-origin', signal: controller.signal,
                headers: {'Content-Type': 'application/json', 'Accept': 'application/json',
                    ...(csrf ? {'X-CSRF-TOKEN': csrf.value} : {})},
                body: JSON.stringify(data)
            });
            if (response.ok) {
                form.reset();
                locked = true;
                window.location.replace(window.PublicSite.pageUrl('/login?password=changed'));
                return;
            }
            if (response.status === 401) {
                form.reset();
                locked = true;
                window.location.replace(window.PublicSite.pageUrl('/login?password=session-expired'));
                return;
            }
            if (response.status === 403 && response.headers.get('X-CSRF-ERROR') === 'true') {
                showError(window.PublicSite.t('Your session has expired. Please reload this page and try again.'));
                return;
            }
            const error = await response.json();
            showError(error.message || window.PublicSite.t('Password change failed. Please try again.'), error.field);
            if (response.status === 429) {
                locked = true;
                const seconds = Number(response.headers.get('Retry-After')) || 3600;
                setTimeout(() => { locked = false; submit.disabled = false; }, Math.min(seconds, 3600) * 1000);
            }
        } catch (error) {
            showError(error.name === 'AbortError'
                ? window.PublicSite.t('The request timed out. Please try signing in with your new password before retrying.')
                : window.PublicSite.t('Unable to complete the request. Please check your connection and try again.'));
        } finally {
            clearTimeout(timeout);
            busy = false;
            form.removeAttribute('aria-busy');
            names.forEach(name => { fields[name].readOnly = false; });
            submit.disabled = locked;
            submit.textContent = window.PublicSite.t('Change Password');
        }
    });
    submit.disabled = false;
})();
