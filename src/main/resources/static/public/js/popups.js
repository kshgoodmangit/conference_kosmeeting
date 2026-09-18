(() => {
    'use strict';
    const setup = root => {
        if (!root) return;
        const panel = root.querySelector('.conference-popups-panel');
        const cards = [...root.querySelectorAll('[data-popup-index]')];
        if (!cards.length) return;
        const layout = Number(root.dataset.layout);
        const modal = layout >= 4;
        const mobile = window.matchMedia('(max-width: 700px)');
        const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
        const counter = root.querySelector('[data-popup-current]');
        const pauseButton = root.querySelector('[data-popup-pause]');
        const hideButton = root.querySelector('[data-popup-hide]');
        const error = root.querySelector('[data-popup-error]');
        const banner = document.getElementById('cookie-banner');
        const cookieDialog = document.getElementById('cookie-preferences');
        const intervals = { 1: 4500, 2: 4500, 3: 4500, 5: 4400, 6: 4000, 7: 4200, 8: 3900 };
        let active = 0, timer, opened = false, closed = false, saving = false, hovered = false, paused = false;
        let returnFocus, previousOverflow, previousRootOverflow;
        const frames = [...root.querySelectorAll('iframe')].map(frame => ({ frame, src: frame.getAttribute('src') }));
        const focusables = () => [...panel.querySelectorAll('a[href], button:not(:disabled), iframe, [tabindex="0"]')]
            .filter(element => !element.closest('[hidden], [inert]'));

        const render = () => {
            cards.forEach((card, index) => {
                const relative = (index - active + cards.length) % cards.length;
                const position = relative === 0 ? 'center' : relative === 1 ? 'right'
                    : relative === cards.length - 1 ? 'left' : 'hidden';
                const multi = layout >= 4 && layout <= 7 && !mobile.matches;
                const visible = position === 'center' || (multi && position !== 'hidden');
                card.dataset.position = position;
                card.hidden = !visible;
                // Only the row layout exposes side-card links; stacked cards are decorative.
                const interactive = visible && (position === 'center' || layout === 5);
                card.inert = !interactive;
                card.setAttribute('aria-hidden', String(!interactive));
            });
            if (counter) counter.textContent = String(active + 1);
        };
        const stopTimer = () => { window.clearInterval(timer); timer = undefined; };
        const schedule = () => {
            stopTimer();
            const focused = document.activeElement !== panel && panel.contains(document.activeElement);
            if (!opened || closed || saving || paused || hovered || focused || document.hidden
                || reducedMotion.matches || !intervals[layout] || cards.length < 2 || cookieDialog?.open) return;
            timer = window.setInterval(() => { active = (active + 1) % cards.length; render(); }, intervals[layout]);
        };
        const move = delta => {
            active = (active + delta + cards.length) % cards.length;
            render();
            schedule();
        };
        const close = () => {
            if (saving || closed) return;
            closed = true;
            stopTimer();
            const hadFocus = root.contains(document.activeElement);
            root.hidden = true;
            // Stop embedded media as well as animations.
            frames.forEach(({ frame }) => frame.removeAttribute('src'));
            if (modal && opened) {
                document.body.style.overflow = previousOverflow;
                document.documentElement.style.overflow = previousRootOverflow;
            }
            if (hadFocus) {
                const target = returnFocus?.isConnected && returnFocus !== document.body
                    ? returnFocus : document.querySelector('header a[href]');
                target?.focus();
            }
            document.removeEventListener('keydown', onKeydown);
        };
        const onKeydown = event => {
            if (!opened || closed || cookieDialog?.open) return;
            if (event.key === 'Escape') { event.preventDefault(); close(); return; }
            if (event.key !== 'Tab' || !modal) return;
            const targets = focusables();
            const first = targets[0], last = targets[targets.length - 1];
            if (!first) { event.preventDefault(); panel.focus(); return; }
            if (event.shiftKey && (document.activeElement === first || document.activeElement === panel || !panel.contains(document.activeElement))) {
                event.preventDefault(); last.focus();
            } else if (!event.shiftKey && (document.activeElement === last || !panel.contains(document.activeElement))) {
                event.preventDefault(); first.focus();
            }
        };
        const open = (manual = false) => {
            // Finish the site's existing cookie choice before presenting a second dialog.
            if (saving || (opened && !closed)) return;
            if (manual) { opened = false; closed = false; }
            if (closed || (banner && !banner.hidden) || cookieDialog?.open) return;
            opened = true;
            hovered = false;
            error.hidden = true;
            frames.forEach(({ frame, src }) => { if (src) frame.setAttribute('src', src); });
            returnFocus = manual ? document.querySelector('[data-popup-open]') : document.activeElement;
            previousOverflow = document.body.style.overflow;
            previousRootOverflow = document.documentElement.style.overflow;
            render();
            root.hidden = false;
            if (modal) {
                panel.setAttribute('aria-modal', 'true');
                document.body.style.overflow = 'hidden';
                document.documentElement.style.overflow = 'hidden';
            }
            panel.focus({ preventScroll: true });
            document.addEventListener('keydown', onKeydown);
            schedule();
        };
        const dismissToday = async () => {
            if (saving) return;
            saving = true;
            error.hidden = true;
            hideButton.textContent = 'Saving…';
            root.querySelectorAll('button').forEach(button => { button.disabled = true; });
            schedule();
            const abort = new AbortController();
            const timeout = window.setTimeout(() => abort.abort(), 15000);
            try {
                // Re-fetch once on CSRF expiry, keeping the public session protection intact.
                for (let attempt = 0; attempt < 2; attempt++) {
                    const tokenResponse = await fetch('/api/security/csrf-token', { credentials: 'same-origin', cache: 'no-store', signal: abort.signal });
                    if (!tokenResponse.ok) throw new Error('token');
                    const token = await tokenResponse.json();
                    const response = await fetch(window.PublicSite.apiUrl('/api/popups/dismiss-today'), {
                        method: 'POST', credentials: 'same-origin', cache: 'no-store',
                        headers: { [token.headerName]: token.token }, signal: abort.signal
                    });
                    if (response.ok) { saving = false; close(); return; }
                    if (attempt === 0 && response.status === 403 && response.headers.get('X-CSRF-ERROR') === 'true') continue;
                    throw new Error('save');
                }
            } catch {
                error.textContent = 'Your preference could not be saved. Please try again, or use the close icon for this visit.';
                error.hidden = false;
            } finally {
                window.clearTimeout(timeout);
                saving = false;
                root.querySelectorAll('button').forEach(button => { button.disabled = false; });
                hideButton.textContent = 'Do not show again today';
                schedule();
            }
        };

        root.querySelectorAll('button[data-popup-close]').forEach(button => button.addEventListener('click', close));
        root.querySelector('[data-popup-prev]')?.addEventListener('click', () => move(-1));
        root.querySelector('[data-popup-next]')?.addEventListener('click', () => move(1));
        hideButton.addEventListener('click', dismissToday);
        pauseButton?.addEventListener('click', () => {
            paused = !paused;
            pauseButton.setAttribute('aria-pressed', String(paused));
            pauseButton.textContent = paused ? 'Play' : 'Pause';
            schedule();
        });
        panel.addEventListener('pointerenter', () => { hovered = true; schedule(); });
        panel.addEventListener('pointerleave', () => { hovered = false; schedule(); });
        panel.addEventListener('focusin', schedule);
        panel.addEventListener('focusout', () => window.setTimeout(schedule, 0));
        mobile.addEventListener('change', () => { render(); schedule(); });
        reducedMotion.addEventListener('change', schedule);
        document.addEventListener('visibilitychange', schedule);
        cookieDialog?.addEventListener('close', () => { open(); schedule(); });
        window.addEventListener('congress:cookie-consent-change', () => { open(); schedule(); });
        root.querySelectorAll('img').forEach(img => {
            const imageError = () => {
                if (img.hidden) return;
                img.hidden = true;
                const message = document.createElement('p');
                message.className = 'conference-popup-image-error';
                message.textContent = 'This image is temporarily unavailable.';
                img.after(message);
            };
            img.addEventListener('error', imageError);
            if (img.complete && img.naturalWidth === 0) imageError();
        });
        return open;
    };

    let open = setup(document.getElementById('conference-popups'));
    open?.();
    const trigger = document.querySelector('[data-popup-open]');
    const status = document.querySelector('[data-popup-status]');
    const notify = message => {
        if (!status) return;
        status.textContent = message;
        status.hidden = !message;
    };
    let loading = false;
    trigger?.addEventListener('click', async () => {
        if (loading) return;
        notify('');
        if (open) { open(true); return; }
        loading = true;
        trigger.disabled = true;
        const abort = new AbortController();
        const timeout = window.setTimeout(() => abort.abort(), 15000);
        try {
            const response = await fetch(window.PublicSite.apiUrl('/popups/display'), {
                credentials: 'same-origin', cache: 'no-store', signal: abort.signal
            });
            if (response.status === 204) { notify('No popups available.'); return; }
            if (!response.ok) throw new Error('load');
            const template = document.createElement('template');
            template.innerHTML = await window.PublicSite.responseMessage(response);
            const root = template.content.querySelector('#conference-popups');
            if (!root) throw new Error('content');
            document.body.append(root);
            open = setup(root);
            if (!open) { root.remove(); throw new Error('empty'); }
            open(true);
        } catch {
            notify('Unable to load popups. Please try again.');
        } finally {
            window.clearTimeout(timeout);
            loading = false;
            trigger.disabled = false;
        }
    });
    // Revalidate the HttpOnly preference even when the initial page has no popup markup.
    window.addEventListener('pageshow', event => { if (event.persisted) window.location.reload(); });
})();
