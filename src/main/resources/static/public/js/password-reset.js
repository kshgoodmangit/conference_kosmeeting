(() => {
    const requestForm = document.querySelector('#member-recovery-form');
    const resetForm = document.querySelector('#member-reset-form');
    if (!(requestForm instanceof HTMLFormElement) && !(resetForm instanceof HTMLFormElement)) return;

    const showStatus = (element, message, error = false) => {
        element.textContent = message;
        element.hidden = false;
        element.classList.toggle('is-error', error);
        element.focus();
    };
    const post = async (form, url, data) => {
        const csrf = form.querySelector('[data-reset-csrf]');
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 20000);
        try {
            const response = await fetch(url, {
                method: 'POST', credentials: 'same-origin', signal: controller.signal,
                headers: {'Content-Type': 'application/json', 'Accept': 'text/plain',
                    ...(csrf ? {'X-CSRF-TOKEN': csrf.value} : {})},
                body: JSON.stringify(data)
            });
            if (response.status === 403 && response.headers.get('X-CSRF-ERROR') === 'true') {
                throw new Error('Your session has expired. Please reload this page and try again.');
            }
            const text = await response.text();
            if (!response.ok) {
                const error = new Error(text || 'The request could not be completed. Please try again.');
                error.status = response.status;
                throw error;
            }
            return text;
        } finally {
            clearTimeout(timeout);
        }
    };
    const errorMessage = error => error.name === 'AbortError'
        ? 'The request timed out. Please check your email or try again later.'
        : error instanceof TypeError ? 'Unable to connect. Please check your connection and try again.' : error.message;

    if (requestForm instanceof HTMLFormElement) {
        const email = requestForm.querySelector('#recovery-email');
        const status = requestForm.querySelector('#recovery-status');
        const submit = requestForm.querySelector('button[type="submit"]');
        let busy = false;
        let nextRequestAt = 0;
        requestForm.addEventListener('submit', async event => {
            event.preventDefault();
            email.value = email.value.trim();
            if (busy || Date.now() < nextRequestAt || !requestForm.reportValidity()) return;
            busy = true;
            submit.disabled = true;
            submit.textContent = 'Sending...';
            requestForm.setAttribute('aria-busy', 'true');
            try {
                const message = await post(requestForm, requestForm.action, {email: email.value});
                showStatus(status, message);
                nextRequestAt = Date.now() + 60000;
            } catch (error) {
                showStatus(status, errorMessage(error), true);
            } finally {
                busy = false;
                requestForm.removeAttribute('aria-busy');
                submit.textContent = 'Send Reset Link';
                submit.disabled = Date.now() < nextRequestAt;
                if (submit.disabled) setTimeout(() => { submit.disabled = false; }, nextRequestAt - Date.now());
            }
        });
        submit.disabled = false;
    }

    if (resetForm instanceof HTMLFormElement) {
        let token = new URLSearchParams(window.location.hash.slice(1)).get('token') || '';
        // Retain the fragment until a successful reset so refreshing can validate the same link again.
        const status = resetForm.querySelector('#reset-status');
        const fields = resetForm.querySelector('#reset-fields');
        const password = resetForm.querySelector('#reset-password');
        const confirmation = resetForm.querySelector('#reset-password-confirm');
        const submit = resetForm.querySelector('button[type="submit"]');
        const requestAgain = resetForm.querySelector('#reset-request-again');
        let ready = false;
        let busy = false;
        const invalidLink = message => {
            ready = false;
            fields.disabled = true;
            fields.hidden = true;
            requestAgain.hidden = false;
            showStatus(status, message, true);
        };
        if (!/^[A-Za-z0-9_-]{43}$/.test(token)) {
            invalidLink('This reset link is invalid or has expired. Please request a new link.');
        } else {
            post(resetForm, '/api/public/members/password-reset/validate', {token})
                .then(() => {
                    ready = true;
                    fields.disabled = false;
                    fields.hidden = false;
                    status.hidden = true;
                    password.focus();
                })
                .catch(error => invalidLink(errorMessage(error)));
        }
        [password, confirmation].forEach(input => input.addEventListener('input', () => {
            password.setCustomValidity('');
            confirmation.setCustomValidity('');
        }));
        resetForm.addEventListener('submit', async event => {
            event.preventDefault();
            if (!ready || busy) return;
            password.setCustomValidity(password.value !== password.value.trim()
                ? 'Do not use spaces at the beginning or end.'
                : password.value.length < 8 || password.value.length > 16 ? 'Use 8-16 characters.' : '');
            confirmation.setCustomValidity(password.value !== confirmation.value ? 'Passwords do not match.' : '');
            if (!resetForm.reportValidity()) return;
            busy = true;
            submit.disabled = true;
            submit.textContent = 'Saving...';
            resetForm.setAttribute('aria-busy', 'true');
            try {
                await post(resetForm, resetForm.action, {token, password: password.value, passwordConfirm: confirmation.value});
                ready = false;
                token = '';
                window.history.replaceState(null, '', window.location.pathname);
                password.value = '';
                confirmation.value = '';
                fields.disabled = true;
                fields.hidden = true;
                showStatus(status, 'Your password has been reset. Please use Back to Login to sign in with your new password.');
            } catch (error) {
                if (error.status === 400) invalidLink(errorMessage(error));
                else showStatus(status, errorMessage(error), true);
            } finally {
                busy = false;
                submit.disabled = !ready;
                submit.textContent = 'Save New Password';
                resetForm.removeAttribute('aria-busy');
            }
        });
    }
})();
