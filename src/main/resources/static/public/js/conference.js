(() => {
    const root = document.documentElement;
    const savedTheme = localStorage.getItem('conference-theme');
    const preferredTheme = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    root.dataset.theme = savedTheme === 'dark' || savedTheme === 'light' ? savedTheme : preferredTheme;

    document.querySelector('[data-theme-toggle]')?.addEventListener('click', () => {
        const nextTheme = root.dataset.theme === 'dark' ? 'light' : 'dark';
        root.dataset.theme = nextTheme;
        localStorage.setItem('conference-theme', nextTheme);
    });

    const menuButton = document.querySelector('[data-menu-toggle]');
    const mobileNavigation = document.querySelector('[data-mobile-navigation]');

    const closeMenu = () => {
        if (!(menuButton instanceof HTMLButtonElement) || !(mobileNavigation instanceof HTMLElement)) {
            return;
        }
        mobileNavigation.hidden = true;
        menuButton.setAttribute('aria-expanded', 'false');
        menuButton.setAttribute('aria-label', 'Open menu');
    };

    menuButton?.addEventListener('click', () => {
        if (!(menuButton instanceof HTMLButtonElement) || !(mobileNavigation instanceof HTMLElement)) {
            return;
        }
        const willOpen = mobileNavigation.hidden;
        mobileNavigation.hidden = !willOpen;
        menuButton.setAttribute('aria-expanded', String(willOpen));
        menuButton.setAttribute('aria-label', willOpen ? 'Close menu' : 'Open menu');
    });

    window.matchMedia('(min-width: 1121px)').addEventListener('change', (event) => {
        if (event.matches) {
            closeMenu();
        }
    });

    document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape') {
            closeMenu();
        }
    });
})();
