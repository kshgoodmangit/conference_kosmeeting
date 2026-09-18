import { getApiCsrfRequestHeaders } from './adminSession.ts';

const TEXT_STYLES = [
    'text-align',
    'color',
    'background-color',
    'font-family',
    'font-size'
].join(',');

const ALLOWED_CONTENT = [
    '*(*)',
    'p h1 h2 h3 h4 h5 h6 blockquote pre code div span article section figure figcaption{' + TEXT_STYLES + '}',
    'br strong em b i u s strike',
    'ul ol li',
    'hr',
    'table[border,cellpadding,cellspacing,width,height,align]',
    'thead tbody tfoot tr colgroup col{width}',
    'th td[colspan,rowspan,width,height,align,valign]{' + TEXT_STYLES + '}',
    'a[!href,target,title]',
    'img[!src,alt,title,loading,width,height,align]{float,width,height,margin,margin-left,margin-right}'
].join(';');

const YOUTUBE_ALLOWED_CONTENT = 'iframe[!src,!width,!height,title,frameborder,loading,referrerpolicy,allow,allowfullscreen]';

const DISALLOWED_CONTENT = [
    '*[on*]',
    '*[srcdoc]',
    '*[id,lang,data-*]',
    'script',
    'style',
    'link',
    'meta',
    'object',
    'embed',
    'form',
    'input',
    'button',
    'textarea',
    'select',
    'option',
    'font'
].join(';');

export const createCkEditorSecurityConfig = async (allowYoutube = false, enableUploads = true): Promise<Record<string, unknown>> => ({
    allowedContent: allowYoutube ? `${ALLOWED_CONTENT};${YOUTUBE_ALLOWED_CONTENT}` : ALLOWED_CONTENT,
    disallowedContent: allowYoutube ? DISALLOWED_CONTENT : `${DISALLOWED_CONTENT};iframe`,
    fileTools_requestHeaders: enableUploads ? await getApiCsrfRequestHeaders() : {}
});
