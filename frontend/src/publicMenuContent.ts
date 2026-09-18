// Keep the order in sync with templates/public/fragments.html. Reuse the actual
// public assets instead of copying CSS into the administrator bundle.
export const PUBLIC_MENU_CONTENT_CSS = [
    '/public/css/user.css',
    '/public/css/bootstrap.css',
    'https://cdn.jsdelivr.net/gh/orioncactus/pretendard/dist/web/variable/pretendardvariable.css'
];

const escapeAttribute = (value: string) => value.replace(/[&<>"']/g, (character) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
})[character]!);

export const publicMenuPreviewDocument = (html: string, language = 'en') => {
    const origin = window.location.origin;
    const stylesheets = PUBLIC_MENU_CONTENT_CSS.map((path) =>
        `<link rel="stylesheet" href="${escapeAttribute(new URL(path, origin).href)}">`
    ).join('');
    // The preview stays sandboxed. Only the public site's styles/fonts and
    // same-origin images are loaded; submitted scripts/forms cannot run.
    const policy = `default-src 'none'; style-src ${origin} https://cdn.jsdelivr.net 'unsafe-inline'; font-src https://cdn.jsdelivr.net; img-src ${origin} data:; form-action 'none'; base-uri ${origin}`;
    return `<!doctype html><html lang="${escapeAttribute(language)}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><meta http-equiv="Content-Security-Policy" content="${escapeAttribute(policy)}"><base href="${escapeAttribute(origin)}/">${stylesheets}</head><body><main><section id="content"><div class="cms-content">${html}</div></section></main></body></html>`;
};
