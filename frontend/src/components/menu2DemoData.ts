import { publicMenuPreviewDocument } from '../publicMenuContent.ts';

export interface DemoLanguage {
    code: string;
    name: string;
    nativeName: string;
    enabled: boolean;
}

export type TranslationStatus = 'draft' | 'review' | 'published';

export interface DemoTranslation {
    name: string;
    html: string;
    status: TranslationStatus;
    visible: boolean;
}

export interface DemoMenu {
    key: string;
    parent: string | null;
    type: 'folder' | 'page';
    enabled: boolean;
    translations: Record<string, DemoTranslation | undefined>;
}

export interface Menu2DemoState {
    version: 1;
    languages: DemoLanguage[];
    menus: DemoMenu[];
}

export const LANGUAGE_OPTIONS: DemoLanguage[] = [
    { code: 'ko', name: '한국어', nativeName: '한국어', enabled: true },
    { code: 'en', name: '영어', nativeName: 'English', enabled: true },
    { code: 'ja', name: '일본어', nativeName: '日本語', enabled: false },
    {
        code: 'zh-Hans',
        name: '중국어 간체',
        nativeName: '简体中文',
        enabled: false,
    },
    { code: 'fr', name: '프랑스어', nativeName: 'Français', enabled: false },
    { code: 'de', name: '독일어', nativeName: 'Deutsch', enabled: false },
    { code: 'es', name: '스페인어', nativeName: 'Español', enabled: false },
];

export const blankTranslation = (): DemoTranslation => ({
    name: '',
    html: '',
    status: 'draft',
    visible: true,
});

const welcomeKo =
    '<h2>새로운 연결, 함께 만드는 내일</h2><p>ICMS 2026에 여러분을 초대합니다.</p><p>세계 각국의 연구자와 임상 전문가가 한자리에 모여 최신 연구 성과를 나누고, 학문 간 경계를 넘어 새로운 협력의 가능성을 모색합니다.</p><h3>지식과 경험이 만나는 자리</h3><p>기조 강연, 심포지엄, 구두 및 포스터 발표를 통해 폭넓은 학술 교류의 기회를 제공합니다. 젊은 연구자를 위한 네트워킹 프로그램도 준비되어 있습니다.</p><p>여러분의 소중한 연구와 이야기를 기다리겠습니다.</p><p><strong>ICMS 2026 조직위원회</strong></p>';
const welcomeEn =
    '<h2>New connections. Shared discoveries.</h2><p>Welcome to ICMS 2026.</p><p>Join researchers and clinical experts from around the world to exchange new findings and explore opportunities for collaboration across disciplines.</p><h3>Where knowledge meets experience</h3><p>Discover keynote lectures, symposia, and oral and poster sessions, alongside networking opportunities for early-career researchers.</p><p>We look forward to welcoming you and hearing your ideas.</p><p><strong>ICMS 2026 Organizing Committee</strong></p>';

const definitions: Array<
    [string, string | null, 'folder' | 'page', string, string, string]
> = [
    ['about', null, 'folder', '학회 안내', 'About ICMS', '学会案内'],
    ['welcome', 'about', 'page', '인사말', 'Welcome Message', 'ご挨拶'],
    [
        'committee',
        'about',
        'page',
        '조직위원회',
        'Organizing Committee',
        '組織委員会',
    ],
    [
        'program',
        null,
        'folder',
        '학술 프로그램',
        'Scientific Program',
        '学術プログラム',
    ],
    [
        'program-overview',
        'program',
        'page',
        '프로그램 개요',
        'Program at a Glance',
        'プログラム概要',
    ],
    [
        'speakers',
        'program',
        'page',
        '초청 연자',
        'Invited Speakers',
        '招待講演者',
    ],
    [
        'abstract',
        null,
        'folder',
        '초록 접수',
        'Abstract Submission',
        '抄録投稿',
    ],
    [
        'guidelines',
        'abstract',
        'page',
        '초록 제출 안내',
        'Submission Guidelines',
        '投稿案内',
    ],
    [
        'submission',
        'abstract',
        'page',
        '온라인 초록 접수',
        'Submit an Abstract',
        'オンライン投稿',
    ],
    ['registration', null, 'folder', '참가 등록', 'Registration', '参加登録'],
    [
        'fees',
        'registration',
        'page',
        '등록비 안내',
        'Registration Fees',
        '参加費',
    ],
    [
        'venue',
        'registration',
        'page',
        '오시는 길',
        'Venue & Directions',
        '会場アクセス',
    ],
];

export const createMenu2Demo = (): Menu2DemoState => ({
    version: 1,
    languages: LANGUAGE_OPTIONS.slice(0, 3).map((language) => ({
        ...language,
    })),
    menus: definitions.map(([key, parent, type, ko, en, ja], index) => {
        const koHtml =
            type === 'folder'
                ? ''
                : `<h2>${ko}</h2><p>ICMS 2026 ${ko} 페이지입니다.</p><p>참가자 여러분을 위한 상세 정보를 확인해 주세요.</p><h3>주요 안내</h3><ul><li>행사 일정과 안내사항을 확인해 주세요.</li><li>추가 문의는 학회 사무국으로 연락해 주세요.</li></ul>`;
        const enHtml =
            type === 'folder'
                ? ''
                : `<h2>${en}</h2><p>Explore ${en.toLowerCase()} for ICMS 2026.</p><h3>Important information</h3><ul><li>Please check the conference schedule and instructions.</li><li>Contact the conference secretariat for further assistance.</li></ul>`;
        const translations: DemoMenu['translations'] = {
            ko: {
                name: ko,
                html: key === 'welcome' ? welcomeKo : koHtml,
                status: 'published',
                visible: true,
            },
        };
        if (key !== 'submission' && key !== 'venue') {
            translations.en = {
                name: en,
                html: key === 'welcome' ? welcomeEn : enHtml,
                status:
                    key === 'committee'
                        ? 'draft'
                        : key === 'guidelines'
                          ? 'review'
                          : 'published',
                visible: true,
            };
        }
        if (type === 'folder' || index < 3) {
            translations.ja = {
                name: ja,
                html:
                    type === 'folder'
                        ? ''
                        : `<h2>${ja}</h2><p>ICMS 2026へようこそ。</p><p>世界各国の研究者とともに、新たな研究成果と知識を共有しましょう。</p>`,
                status: type === 'folder' ? 'published' : 'draft',
                visible: true,
            };
        }
        return { key, parent, type, enabled: true, translations };
    }),
});

export const hasContent = (html: string) =>
    Boolean(
        html
            .replace(/<[^>]*>/g, '')
            .replace(/&nbsp;/g, ' ')
            .trim() || /<(img|iframe|hr)\b/i.test(html),
    );
export const isComplete = (menu: DemoMenu, translation?: DemoTranslation) =>
    Boolean(
        translation?.name.trim() &&
        (menu.type === 'folder' || hasContent(translation.html)),
    );
export const translationState = (menu: DemoMenu, code: string) => {
    const value = menu.translations[code];
    return !value || (!value.name.trim() && !hasContent(value.html))
        ? 'missing'
        : value.status;
};
export const menuLabel = (menu: DemoMenu) =>
    menu.translations.ko?.name || menu.key;

export const DEMO_STORAGE_KEY = 'icms-menu2-prototype-v1';

export const loadMenu2Demo = (): Menu2DemoState => {
    try {
        const stored: unknown = JSON.parse(
            localStorage.getItem(DEMO_STORAGE_KEY) ?? 'null',
        );
        if (typeof stored !== 'object' || !stored) return createMenu2Demo();
        const data = stored as Menu2DemoState;
        if (
            data.version !== 1 ||
            !Array.isArray(data.languages) ||
            !Array.isArray(data.menus) ||
            !data.menus.length
        )
            return createMenu2Demo();
        if (
            !data.languages.some((language) => language.code === 'ko') ||
            new Set(data.menus.map((menu) => menu.key)).size !==
                data.menus.length
        )
            return createMenu2Demo();
        for (const language of data.languages) {
            if (
                !LANGUAGE_OPTIONS.some(
                    (option) => option.code === language.code,
                ) ||
                typeof language.enabled !== 'boolean'
            )
                return createMenu2Demo();
        }
        for (const menu of data.menus) {
            if (
                typeof menu.key !== 'string' ||
                !menu.translations ||
                !['page', 'folder'].includes(menu.type)
            )
                return createMenu2Demo();
            if (
                menu.parent &&
                !data.menus.some(
                    (parent) =>
                        parent.key === menu.parent &&
                        parent.parent === null &&
                        parent.type === 'folder',
                )
            )
                return createMenu2Demo();
            for (const translation of Object.values(menu.translations)) {
                if (
                    translation &&
                    (typeof translation.name !== 'string' ||
                        typeof translation.html !== 'string' ||
                        !['draft', 'review', 'published'].includes(
                            translation.status,
                        ))
                )
                    return createMenu2Demo();
            }
        }
        return data;
    } catch {
        return createMenu2Demo();
    }
};

export const escapeHtml = (value: string) =>
    value.replace(
        /[&<>"']/g,
        (character) =>
            ({
                '&': '&amp;',
                '<': '&lt;',
                '>': '&gt;',
                '"': '&quot;',
                "'": '&#39;',
            })[character]!,
    );

export const previewDocument = publicMenuPreviewDocument;
