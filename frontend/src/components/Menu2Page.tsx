import {
    useEffect,
    useRef,
    useState,
    type FormEvent,
    type ReactNode,
} from 'react';
import {
    ArrowDown,
    ArrowLeft,
    ArrowRight,
    ArrowUp,
    Check,
    CheckCircle2,
    ChevronDown,
    ChevronRight,
    Columns2,
    Copy,
    Eye,
    FileText,
    Folder,
    Globe2,
    Languages,
    LayoutGrid,
    ListTree,
    Monitor,
    Moon,
    Plus,
    RotateCcw,
    Save,
    Search,
    Settings2,
    Smartphone,
    Sun,
    X,
} from 'lucide-react';
import {
    CkEditorRichTextEditor,
    type CkEditorRichTextEditorHandle,
} from './CkEditorRichTextEditor';
import {
    NotificationToast,
    type NotificationItem,
    type NotificationType,
} from './NotificationToast';
import { useConfirm } from './confirmDialogContext';
import {
    blankTranslation,
    createMenu2Demo,
    DEMO_STORAGE_KEY,
    escapeHtml,
    hasContent,
    isComplete,
    LANGUAGE_OPTIONS,
    loadMenu2Demo,
    menuLabel,
    previewDocument,
    translationState,
    type DemoMenu,
    type DemoTranslation,
    type Menu2DemoState,
} from './menu2DemoData';

type View = 'editor' | 'coverage' | 'preview';
type Status = ReturnType<typeof translationState>;
const inputStyle =
    'w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm font-normal text-slate-900 placeholder:text-slate-400 focus:border-blue-500 focus:outline-none dark:border-slate-700 dark:bg-slate-900 dark:text-slate-50';
const secondary =
    'inline-flex items-center justify-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-40 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-300 dark:hover:bg-slate-800';
const primary =
    'inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-40 dark:bg-blue-600 dark:text-white dark:hover:bg-blue-700';
const statusLabels: Record<Status, string> = {
    missing: '미번역',
    draft: '작성 중',
    review: '검토 요청',
    published: '게시 완료',
};
const statusStyles: Record<Status, string> = {
    missing:
        'bg-amber-50 text-amber-700 dark:bg-amber-950/40 dark:text-amber-300',
    draft: 'bg-slate-100 text-slate-500 dark:bg-slate-800 dark:text-slate-400',
    review: 'bg-blue-50 text-blue-700 dark:bg-blue-950/40 dark:text-blue-300',
    published:
        'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300',
};

const Badge = ({ state }: { state: Status }) => (
    <span
        className={`inline-flex shrink-0 items-center gap-1 rounded-full px-2 py-1 text-[10px] font-semibold ${statusStyles[state]}`}
    >
        {state === 'published' && <Check className="h-3 w-3" />}
        {statusLabels[state]}
    </span>
);
const Field = ({ label, children }: { label: string; children: ReactNode }) => (
    <label className="block space-y-1.5 text-sm font-semibold text-slate-700 dark:text-slate-200">
        <span>{label}</span>
        {children}
    </label>
);

export const Menu2Page = () => {
    const confirm = useConfirm();
    const [data, setData] = useState<Menu2DemoState>(loadMenu2Demo);
    const [savedJson, setSavedJson] = useState(() => JSON.stringify(data));
    const [selectedKey, setSelectedKey] = useState(
        () =>
            data.menus.find((menu) => menu.key === 'welcome')?.key ??
            data.menus[0].key,
    );
    const [languageCode, setLanguageCode] = useState(() =>
        data.languages.some((language) => language.code === 'en') ? 'en' : 'ko',
    );
    const [view, setView] = useState<View>('editor');
    const [query, setQuery] = useState('');
    const [onlyPending, setOnlyPending] = useState(false);
    const [collapsed, setCollapsed] = useState<string[]>([]);
    const [showReference, setShowReference] = useState(true);
    const [referenceCode, setReferenceCode] = useState('ko');
    const [showLanguages, setShowLanguages] = useState(false);
    const [newLanguage, setNewLanguage] = useState('');
    const [showAddMenu, setShowAddMenu] = useState(false);
    const [showMobileTree, setShowMobileTree] = useState(false);
    const [newMenu, setNewMenu] = useState({
        name: '',
        key: '',
        parent: '',
        type: 'page' as DemoMenu['type'],
    });
    const [mobilePreview, setMobilePreview] = useState(false);
    const [editorVersion, setEditorVersion] = useState(0);
    const [savedAt, setSavedAt] = useState('');
    const [dark, setDark] = useState(false);
    const [notifications, setNotifications] = useState<NotificationItem[]>([]);
    const editorRef = useRef<CkEditorRichTextEditorHandle>(null);
    const notificationId = useRef(0);
    const timers = useRef<ReturnType<typeof setTimeout>[]>([]);
    const selectedMenu = data.menus.find((menu) => menu.key === selectedKey)!;
    const current =
        selectedMenu.translations[languageCode] ?? blankTranslation();
    const language = data.languages.find((item) => item.code === languageCode)!;
    const reference = selectedMenu.translations[referenceCode];
    const dirty = savedJson !== JSON.stringify(data);
    const counts = {
        published: data.menus.filter(
            (menu) => translationState(menu, languageCode) === 'published',
        ).length,
        missing: data.menus.filter(
            (menu) => translationState(menu, languageCode) === 'missing',
        ).length,
        review: data.menus.filter(
            (menu) => translationState(menu, languageCode) === 'review',
        ).length,
    };

    useEffect(() => () => timers.current.forEach(clearTimeout), []);
    useEffect(() => {
        const warn = (event: BeforeUnloadEvent) => {
            if (
                dirty ||
                (editorRef.current?.hasChanges() &&
                    editorRef.current.getData() !== current.html)
            ) {
                event.preventDefault();
                event.returnValue = '';
            }
        };
        window.addEventListener('beforeunload', warn);
        return () => window.removeEventListener('beforeunload', warn);
    }, [dirty, current.html]);

    const notify = (type: NotificationType, message: string) => {
        const id = ++notificationId.current;
        setNotifications((items) => [...items, { id, type, message }]);
        timers.current.push(
            setTimeout(
                () =>
                    setNotifications((items) =>
                        items.filter((item) => item.id !== id),
                    ),
                4000,
            ),
        );
    };

    const updateTranslation = (patch: Partial<DemoTranslation>) => {
        setData((previous) => ({
            ...previous,
            menus: previous.menus.map((menu) => {
                if (menu.key !== selectedKey) return menu;
                const translation =
                    menu.translations[languageCode] ?? blankTranslation();
                // Editing a published translation puts it back into draft for explicit review.
                const status =
                    patch.status ??
                    ((patch.name !== undefined || patch.html !== undefined) &&
                    translation.status === 'published'
                        ? 'draft'
                        : translation.status);
                return {
                    ...menu,
                    translations: {
                        ...menu.translations,
                        [languageCode]: { ...translation, ...patch, status },
                    },
                };
            }),
        }));
    };

    const withEditorContent = () => {
        if (!editorRef.current?.hasChanges()) return data;
        const html = editorRef.current?.getData();
        if (html === undefined || html === current.html) return data;
        return {
            ...data,
            menus: data.menus.map((menu) =>
                menu.key !== selectedKey
                    ? menu
                    : {
                          ...menu,
                          translations: {
                              ...menu.translations,
                              [languageCode]: {
                                  ...current,
                                  html,
                                  status:
                                      current.status === 'published'
                                          ? ('draft' as const)
                                          : current.status,
                              },
                          },
                      },
            ),
        };
    };

    const navigate = (
        key: string,
        code = languageCode,
        nextView: View = view,
    ) => {
        setData(withEditorContent());
        setSelectedKey(key);
        setLanguageCode(code);
        setView(nextView);
        if (referenceCode === code)
            setReferenceCode(
                code === 'ko'
                    ? (data.languages.find((item) => item.code !== 'ko')
                          ?.code ?? 'ko')
                    : 'ko',
            );
        setEditorVersion((version) => version + 1);
    };

    const save = (publish = false) => {
        let next = withEditorContent();
        if (publish) {
            const menu = next.menus.find((item) => item.key === selectedKey)!;
            const translation = menu.translations[languageCode];
            if (!isComplete(menu, translation)) {
                notify(
                    'error',
                    menu.type === 'page'
                        ? '게시하려면 메뉴명과 본문을 모두 작성해 주세요.'
                        : '게시하려면 메뉴명을 작성해 주세요.',
                );
                return;
            }
            next = {
                ...next,
                menus: next.menus.map((item) =>
                    item.key !== selectedKey
                        ? item
                        : {
                              ...item,
                              translations: {
                                  ...item.translations,
                                  [languageCode]: {
                                      ...translation!,
                                      status: 'published',
                                  },
                              },
                          },
                ),
            };
        }
        try {
            const json = JSON.stringify(next);
            localStorage.setItem(DEMO_STORAGE_KEY, json);
            setData(next);
            setSavedJson(json);
            setSavedAt(
                new Date().toLocaleTimeString('ko-KR', {
                    hour: '2-digit',
                    minute: '2-digit',
                }),
            );
            notify(
                'success',
                publish
                    ? `${language.name} 게시 상태와 변경사항을 이 브라우저에 저장했습니다.`
                    : '모든 언어의 변경사항을 이 브라우저에 저장했습니다.',
            );
        } catch {
            notify(
                'error',
                '브라우저에 저장하지 못했습니다. 저장 공간 또는 브라우저 설정을 확인해 주세요.',
            );
        }
    };

    const copyReference = async () => {
        if (!reference || (!reference.name && !reference.html)) return;
        if (
            (current.name || hasContent(current.html)) &&
            !(await confirm({
                title: '원문 내용 복사',
                message: `${language.name} 메뉴명과 본문을 참고 언어의 내용으로 바꿉니다. 계속하시겠습니까?`,
                confirmText: '복사',
            }))
        )
            return;
        updateTranslation({
            name: reference.name,
            html: reference.html,
            status: 'draft',
        });
        setEditorVersion((version) => version + 1);
        notify('info', '원문을 복사했습니다. 내용을 번역한 뒤 검토해 주세요.');
    };

    const resetDemo = async () => {
        if (
            !(await confirm({
                title: '체험 내용 초기화',
                message:
                    '이 브라우저에 저장한 메뉴와 번역을 지우고 처음의 예시 데이터로 돌아갑니다.',
                confirmText: '초기화',
                tone: 'danger',
            }))
        )
            return;
        try {
            localStorage.removeItem(DEMO_STORAGE_KEY);
        } catch {
            notify('error', '브라우저 저장 내용을 초기화하지 못했습니다.');
            return;
        }
        const next = createMenu2Demo();
        setData(next);
        setSavedJson(JSON.stringify(next));
        setSelectedKey('welcome');
        setLanguageCode('en');
        setReferenceCode('ko');
        setQuery('');
        setOnlyPending(false);
        setCollapsed([]);
        setView('editor');
        setSavedAt('');
        setEditorVersion((version) => version + 1);
        notify('success', '처음의 예시 데이터로 돌아왔습니다.');
    };

    const addLanguage = () => {
        const addition = LANGUAGE_OPTIONS.find(
            (item) => item.code === newLanguage,
        );
        if (
            !addition ||
            data.languages.some((item) => item.code === newLanguage)
        )
            return;
        const next = withEditorContent();
        setData({
            ...next,
            languages: [...next.languages, { ...addition, enabled: false }],
        });
        setLanguageCode(addition.code);
        setReferenceCode('ko');
        setNewLanguage('');
        setView('editor');
        setEditorVersion((version) => version + 1);
        notify(
            'info',
            `${addition.name} 작업 공간을 추가했습니다. 번역을 준비한 뒤 언어를 공개하세요.`,
        );
    };

    const addMenu = (event: FormEvent) => {
        event.preventDefault();
        const key = newMenu.key.trim();
        if (!newMenu.name.trim() || !/^[a-z][a-z0-9-]*$/.test(key)) {
            notify(
                'error',
                '메뉴명과 영문 소문자·숫자·하이픈으로 된 메뉴 키를 입력해 주세요.',
            );
            return;
        }
        if (data.menus.some((menu) => menu.key === key)) {
            notify('error', '이미 사용 중인 메뉴 키입니다.');
            return;
        }
        const next = withEditorContent();
        const added: DemoMenu = {
            key,
            parent: newMenu.parent || null,
            type: newMenu.type,
            enabled: true,
            translations: {
                ko: { ...blankTranslation(), name: newMenu.name.trim() },
            },
        };
        setData({ ...next, menus: [...next.menus, added] });
        setSelectedKey(key);
        setLanguageCode('ko');
        setView('editor');
        setQuery('');
        setOnlyPending(false);
        setCollapsed([]);
        setShowAddMenu(false);
        setNewMenu({ name: '', key: '', parent: '', type: 'page' });
        setEditorVersion((version) => version + 1);
        notify(
            'success',
            '공통 메뉴를 추가했습니다. 각 언어 탭에서 번역을 작성하세요.',
        );
    };

    const moveMenu = (direction: number) => {
        const next = withEditorContent();
        const siblings = next.menus.filter(
            (menu) => menu.parent === selectedMenu.parent,
        );
        const sibling =
            siblings[
                siblings.findIndex((menu) => menu.key === selectedKey) +
                    direction
            ];
        if (!sibling) return;
        const list = [...next.menus];
        const index = list.findIndex((menu) => menu.key === selectedKey);
        const target = list.findIndex((menu) => menu.key === sibling.key);
        [list[index], list[target]] = [list[target], list[index]];
        setData({ ...next, menus: list });
    };

    const nextMissing = () => {
        const next = withEditorContent();
        const candidates = next.menus.filter(
            (menu) => translationState(menu, languageCode) === 'missing',
        );
        const index = candidates.findIndex((menu) => menu.key === selectedKey);
        const target = candidates[(index + 1) % candidates.length];
        if (!target) {
            notify(
                'info',
                '이 언어의 미번역 메뉴가 없습니다. 번역 현황에서 검토 상태를 확인해 주세요.',
            );
            return;
        }
        navigate(target.key, languageCode, 'editor');
    };

    const matches = (menu: DemoMenu) =>
        (!query.trim() ||
            `${menuLabel(menu)} ${menu.key} ${menu.translations[languageCode]?.name ?? ''}`
                .toLowerCase()
                .includes(query.trim().toLowerCase())) &&
        (!onlyPending || translationState(menu, languageCode) !== 'published');
    const filteredMenus = data.menus.filter(matches);
    const renderTree = (parent: string | null, depth = 0): ReactNode =>
        data.menus
            .filter((menu) => menu.parent === parent)
            .map((menu) => {
                const children = data.menus.filter(
                    (child) => child.parent === menu.key,
                );
                const childMatches = children.some(matches);
                if (!matches(menu) && !childMatches) return null;
                const expanded =
                    !collapsed.includes(menu.key) ||
                    Boolean(query) ||
                    onlyPending;
                const active = menu.key === selectedKey;
                return (
                    <li
                        key={menu.key}
                        className={
                            depth
                                ? 'ml-4 border-l border-slate-200 pl-2 dark:border-slate-800'
                                : 'mt-1'
                        }
                    >
                        <div
                            className={`flex items-center rounded-lg ${active ? 'bg-blue-50 ring-1 ring-inset ring-blue-200 dark:bg-blue-950/40 dark:ring-blue-900' : 'hover:bg-slate-50 dark:hover:bg-slate-900'}`}
                        >
                            {children.length > 0 && (
                                <button
                                    type="button"
                                    aria-label={`${menuLabel(menu)} ${expanded ? '접기' : '펼치기'}`}
                                    aria-expanded={expanded}
                                    className="py-3 pl-2 text-slate-400 dark:text-slate-500"
                                    onClick={() =>
                                        setCollapsed((previous) =>
                                            expanded
                                                ? [...previous, menu.key]
                                                : previous.filter(
                                                      (key) => key !== menu.key,
                                                  ),
                                        )
                                    }
                                >
                                    {expanded ? (
                                        <ChevronDown className="h-3.5 w-3.5" />
                                    ) : (
                                        <ChevronRight className="h-3.5 w-3.5" />
                                    )}
                                </button>
                            )}
                            <button
                                type="button"
                                aria-current={active ? 'page' : undefined}
                                onClick={() => navigate(menu.key)}
                                className={`flex min-w-0 flex-1 items-center gap-2 px-2 py-3 text-left ${active ? 'text-blue-700 dark:text-blue-300' : 'text-slate-700 dark:text-slate-300'}`}
                            >
                                {menu.type === 'folder' ? (
                                    <Folder className="h-4 w-4 shrink-0 text-slate-400 dark:text-slate-500" />
                                ) : (
                                    <FileText className="h-3.5 w-3.5 shrink-0 text-slate-400 dark:text-slate-500" />
                                )}
                                <span className="min-w-0 flex-1">
                                    <span className="block truncate text-xs font-semibold">
                                        {menuLabel(menu)}
                                    </span>
                                    {!menu.enabled && (
                                        <span className="text-[10px] text-slate-400 dark:text-slate-500">
                                            전체 비공개
                                        </span>
                                    )}
                                </span>
                                <Badge
                                    state={translationState(menu, languageCode)}
                                />
                            </button>
                        </div>
                        {expanded && children.length > 0 && (
                            <ul>{renderTree(menu.key, depth + 1)}</ul>
                        )}
                    </li>
                );
            });

    const previewVisible = (menu: DemoMenu): boolean => {
        const translation = menu.translations[languageCode];
        const parent = menu.parent
            ? data.menus.find((item) => item.key === menu.parent)
            : undefined;
        return (
            language.enabled &&
            menu.enabled &&
            Boolean(translation?.visible) &&
            translation?.status === 'published' &&
            (!parent || previewVisible(parent))
        );
    };
    const siblings = data.menus.filter(
        (menu) => menu.parent === selectedMenu.parent,
    );
    const siblingIndex = siblings.findIndex((menu) => menu.key === selectedKey);
    const routePath = `/${languageCode}/${selectedMenu.parent ? selectedMenu.parent + '/' : ''}${selectedMenu.key}`;

    return (
        <div className={dark ? 'dark' : ''}>
            <div className="min-h-screen bg-slate-100 text-slate-900 dark:bg-slate-950 dark:text-slate-50">
                <header className="border-b border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-950">
                    <div className="mx-auto flex max-w-[1600px] items-center justify-between gap-3 px-4 py-3 md:px-8">
                        <div className="flex items-center gap-3">
                            <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-blue-600 text-white dark:bg-blue-600 dark:text-white">
                                <Globe2 className="h-5 w-5" />
                            </span>
                            <div>
                                <p className="text-sm font-bold tracking-wide">
                                    ICMS{' '}
                                    <span className="font-normal text-slate-400 dark:text-slate-500">
                                        2026
                                    </span>
                                </p>
                                <p className="text-[10px] text-slate-500 dark:text-slate-400">
                                    CONFERENCE MANAGEMENT
                                </p>
                            </div>
                            <span className="ml-2 hidden border-l border-slate-200 pl-4 text-xs text-slate-500 dark:border-slate-800 dark:text-slate-400 sm:block">
                                사이트 운영 / 다국어 메뉴
                            </span>
                        </div>
                        <div className="flex items-center gap-2">
                            <a href="/admin/menu" className={secondary}>
                                <ArrowLeft className="h-3.5 w-3.5" />
                                <span className="hidden sm:inline">
                                    기존 메뉴관리
                                </span>
                            </a>
                            <button
                                type="button"
                                onClick={() => setDark((value) => !value)}
                                className={secondary}
                                aria-label={dark ? '라이트 모드' : '다크 모드'}
                            >
                                {dark ? (
                                    <Sun className="h-4 w-4" />
                                ) : (
                                    <Moon className="h-4 w-4" />
                                )}
                            </button>
                        </div>
                    </div>
                </header>

                <main className="mx-auto max-w-[1600px] space-y-4 p-4 pb-40 sm:pb-28 md:p-8 md:pb-28">
                    <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-blue-100 bg-blue-50/70 px-3 py-2 text-xs text-blue-700 dark:border-blue-900 dark:bg-blue-950/30 dark:text-blue-300">
                        <p>
                            <span className="mr-2 font-bold">체험 화면</span>
                            예시 메뉴로 자유롭게 편집해 보세요. 변경사항은 이
                            브라우저에만 저장됩니다.
                        </p>
                        <button
                            type="button"
                            onClick={() => void resetDemo()}
                            className="inline-flex items-center gap-1.5 font-semibold hover:underline"
                        >
                            <RotateCcw className="h-3 w-3" />
                            예시 초기화
                        </button>
                    </div>
                    <section className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-950">
                        <div className="flex flex-col justify-between gap-4 p-4 sm:flex-row sm:items-center md:p-5">
                            <div>
                                <div className="flex items-center gap-2">
                                    <Languages className="shrink-0 h-4 w-4 text-blue-600 dark:text-blue-400" />
                                    <h1 className="text-sm font-semibold md:text-base">
                                        다국어 메뉴 관리
                                    </h1>
                                </div>
                                <p className="mt-1 text-xs text-slate-400 dark:text-slate-400">
                                    메뉴 구조는 함께, 이름과 콘텐츠는 언어별로
                                    관리하세요.
                                </p>
                            </div>
                            <div className="flex flex-wrap items-center gap-2">
                                <button
                                    type="button"
                                    onClick={() =>
                                        setShowLanguages((value) => !value)
                                    }
                                    aria-expanded={showLanguages}
                                    className={secondary}
                                >
                                    <Settings2 className="h-4 w-4" />
                                    지원 언어 설정
                                </button>
                                <button
                                    type="button"
                                    onClick={() => save()}
                                    className={primary}
                                >
                                    <Save className="h-4 w-4" />
                                    변경사항 저장
                                </button>
                            </div>
                        </div>
                        {showLanguages && (
                            <div className="space-y-4 border-t border-slate-200 bg-slate-50/70 p-4 dark:border-slate-800 dark:bg-slate-900/40 md:p-5">
                                <div className="flex justify-between">
                                    <div>
                                        <h2 className="text-sm font-semibold">
                                            지원 언어와 공개 설정
                                        </h2>
                                        <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                                            새 언어는 준비 중으로 추가됩니다.
                                            번역을 마친 뒤 공개하세요.
                                        </p>
                                    </div>
                                    <button
                                        type="button"
                                        className={secondary}
                                        aria-label="지원 언어 설정 닫기"
                                        onClick={() => setShowLanguages(false)}
                                    >
                                        <X className="h-4 w-4" />
                                    </button>
                                </div>
                                <div className="flex flex-wrap gap-3">
                                    {data.languages.map((item) => (
                                        <label
                                            key={item.code}
                                            className="flex items-center gap-3 rounded-lg border border-slate-200 bg-white px-4 py-3 text-sm dark:border-slate-700 dark:bg-slate-950"
                                        >
                                            <input
                                                type="checkbox"
                                                checked={item.enabled}
                                                disabled={item.code === 'ko'}
                                                onChange={(event) => {
                                                    const next =
                                                        withEditorContent();
                                                    setData({
                                                        ...next,
                                                        languages:
                                                            next.languages.map(
                                                                (value) =>
                                                                    value.code ===
                                                                    item.code
                                                                        ? {
                                                                              ...value,
                                                                              enabled:
                                                                                  event
                                                                                      .target
                                                                                      .checked,
                                                                          }
                                                                        : value,
                                                            ),
                                                    });
                                                }}
                                                className="h-4 w-4 accent-blue-600"
                                            />
                                            <span>
                                                {item.name}{' '}
                                                <span className="text-xs text-slate-400 dark:text-slate-500">
                                                    {item.code === 'ko'
                                                        ? '기본 언어'
                                                        : item.enabled
                                                          ? '공개'
                                                          : '준비 중'}
                                                </span>
                                            </span>
                                        </label>
                                    ))}
                                </div>
                                <div className="flex max-w-lg gap-2">
                                    <select
                                        aria-label="추가할 언어"
                                        className={inputStyle}
                                        value={newLanguage}
                                        onChange={(event) =>
                                            setNewLanguage(event.target.value)
                                        }
                                    >
                                        <option value="">
                                            추가할 언어 선택
                                        </option>
                                        {LANGUAGE_OPTIONS.filter(
                                            (item) =>
                                                !data.languages.some(
                                                    (active) =>
                                                        active.code ===
                                                        item.code,
                                                ),
                                        ).map((item) => (
                                            <option
                                                key={item.code}
                                                value={item.code}
                                            >
                                                {item.name} · {item.nativeName}
                                            </option>
                                        ))}
                                    </select>
                                    <button
                                        type="button"
                                        onClick={addLanguage}
                                        disabled={!newLanguage}
                                        className={`${secondary} shrink-0`}
                                    >
                                        <Plus className="h-4 w-4" />
                                        언어 추가
                                    </button>
                                </div>
                            </div>
                        )}
                        <div
                            className="flex overflow-x-auto border-y border-slate-200 px-4 dark:border-slate-800 md:px-5"
                            aria-label="작업 언어"
                        >
                            {data.languages.map((item) => {
                                const completed = data.menus.filter(
                                    (menu) =>
                                        translationState(menu, item.code) ===
                                        'published',
                                ).length;
                                return (
                                    <button
                                        key={item.code}
                                        type="button"
                                        aria-pressed={
                                            languageCode === item.code
                                        }
                                        onClick={() =>
                                            navigate(selectedKey, item.code)
                                        }
                                        className={`relative flex min-w-[165px] items-center gap-3 border-b-2 px-4 py-4 text-left ${languageCode === item.code ? 'border-blue-600 bg-blue-50/40 text-blue-700 dark:border-blue-400 dark:bg-blue-950/20 dark:text-blue-300' : 'border-transparent text-slate-600 hover:bg-slate-50 dark:text-slate-400 dark:hover:bg-slate-900'}`}
                                    >
                                        <span
                                            className={`rounded-md px-2 py-1 text-xs font-bold uppercase ${languageCode === item.code ? 'bg-blue-100 dark:bg-blue-900' : 'bg-slate-100 dark:bg-slate-800'}`}
                                        >
                                            {item.code === 'zh-Hans'
                                                ? 'ZH'
                                                : item.code}
                                        </span>
                                        <span>
                                            <span className="block text-sm font-semibold">
                                                {item.name}
                                                {!item.enabled && (
                                                    <span className="ml-2 text-[10px] font-normal text-slate-400 dark:text-slate-500">
                                                        준비 중
                                                    </span>
                                                )}
                                            </span>
                                            <span className="mt-0.5 block text-[10px] opacity-70">
                                                {completed}/{data.menus.length}{' '}
                                                게시 완료
                                            </span>
                                        </span>
                                    </button>
                                );
                            })}
                        </div>
                        <div className="grid grid-cols-3 divide-x divide-slate-200 dark:divide-slate-800">
                            <Stat
                                label="공통 메뉴"
                                value={data.menus.length}
                                detail="언어별로 같은 구조를 사용합니다"
                            />
                            <Stat
                                label={`${language.name} 게시 완료`}
                                value={counts.published}
                                detail={`전체 메뉴의 ${Math.round((counts.published / data.menus.length) * 100)}%`}
                                green
                            />
                            <div className="flex flex-wrap items-center justify-between gap-2 p-3 md:p-5">
                                <div>
                                    <p className="text-[11px] font-medium sm:text-xs text-slate-500 dark:text-slate-400">
                                        {language.name} 작업 필요
                                    </p>
                                    <p className="mt-1 text-xl font-bold text-amber-600 dark:text-amber-400">
                                        {data.menus.length - counts.published}
                                        <span className="ml-3 hidden text-xs font-normal text-slate-400 dark:text-slate-500 sm:inline">
                                            미번역 {counts.missing} · 검토 요청{' '}
                                            {counts.review}
                                        </span>
                                    </p>
                                </div>
                                <button
                                    type="button"
                                    onClick={() => {
                                        setOnlyPending(true);
                                        setQuery('');
                                        navigate(
                                            selectedKey,
                                            languageCode,
                                            'coverage',
                                        );
                                    }}
                                    className={secondary}
                                >
                                    확인
                                    <ArrowRight className="h-3 w-3" />
                                </button>
                            </div>
                        </div>
                    </section>

                    <div className="flex flex-wrap items-center justify-between gap-3">
                        <div className="flex max-w-full overflow-x-auto rounded-lg border border-slate-200 bg-white p-1 dark:border-slate-800 dark:bg-slate-950">
                            {(
                                [
                                    {
                                        id: 'editor',
                                        label: '콘텐츠 편집',
                                        icon: FileText,
                                    },
                                    {
                                        id: 'coverage',
                                        label: '번역 현황',
                                        icon: LayoutGrid,
                                    },
                                    {
                                        id: 'preview',
                                        label: '사이트 미리보기',
                                        icon: Eye,
                                    },
                                ] as const
                            ).map((item) => (
                                <button
                                    type="button"
                                    key={item.id}
                                    onClick={() =>
                                        navigate(
                                            selectedKey,
                                            languageCode,
                                            item.id,
                                        )
                                    }
                                    aria-pressed={view === item.id}
                                    className={`inline-flex shrink-0 items-center gap-1.5 whitespace-nowrap rounded-md px-2 py-2 text-xs font-semibold sm:gap-2 sm:px-3 ${view === item.id ? 'bg-slate-900 text-white dark:bg-slate-100 dark:text-slate-900' : 'text-slate-500 hover:bg-slate-50 dark:text-slate-400 dark:hover:bg-slate-900'}`}
                                >
                                    <item.icon className="h-3.5 w-3.5" />
                                    {item.label}
                                </button>
                            ))}
                        </div>
                        <span className="inline-flex items-center gap-1.5 text-xs text-slate-500 dark:text-slate-400">
                            {dirty ? (
                                <>
                                    <span className="h-1.5 w-1.5 rounded-full bg-amber-500 dark:bg-amber-400" />
                                    저장하지 않은 변경사항
                                </>
                            ) : (
                                <>
                                    <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600 dark:text-emerald-400" />
                                    {savedAt
                                        ? `${savedAt} 브라우저에 저장됨`
                                        : '예시 데이터를 불러왔습니다'}
                                </>
                            )}
                        </span>
                    </div>

                    {view !== 'coverage' && (
                        <div className="flex items-center gap-2 rounded-lg border border-slate-200 bg-white p-3 dark:border-slate-800 dark:bg-slate-950 lg:hidden">
                            <label className="min-w-0 flex-1">
                                <span className="mb-1 block text-[11px] font-semibold text-slate-500 dark:text-slate-400">
                                    편집할 메뉴
                                </span>
                                <select
                                    aria-label="편집할 메뉴 빠른 선택"
                                    className={inputStyle}
                                    value={selectedKey}
                                    onChange={(event) => {
                                        navigate(event.target.value);
                                        setShowMobileTree(false);
                                    }}
                                >
                                    {data.menus.map((menu) => (
                                        <option key={menu.key} value={menu.key}>
                                            {menu.parent ? '　└ ' : ''}
                                            {menuLabel(menu)} ·{' '}
                                            {
                                                statusLabels[
                                                    translationState(
                                                        menu,
                                                        languageCode,
                                                    )
                                                ]
                                            }
                                        </option>
                                    ))}
                                </select>
                            </label>
                            <button
                                type="button"
                                aria-expanded={showMobileTree}
                                onClick={() =>
                                    setShowMobileTree((value) => !value)
                                }
                                className={secondary}
                            >
                                <ListTree className="h-4 w-4" />
                                {showMobileTree ? '닫기' : '트리'}
                            </button>
                        </div>
                    )}
                    {view === 'coverage' ? (
                        <section className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-950">
                            <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 p-4 dark:border-slate-800 md:p-5">
                                <div>
                                    <h2 className="text-sm font-semibold">
                                        한눈에 보는 번역 현황
                                    </h2>
                                    <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                                        상태를 클릭하면 해당 메뉴의 언어
                                        편집기로 이동합니다.
                                    </p>
                                </div>
                                <label className="flex items-center gap-2 text-xs">
                                    <input
                                        type="checkbox"
                                        checked={onlyPending}
                                        onChange={(event) =>
                                            setOnlyPending(event.target.checked)
                                        }
                                        className="h-4 w-4 accent-blue-600"
                                    />
                                    {language.name} 작업 필요만 보기
                                </label>
                            </div>
                            <div className="overflow-x-auto">
                                <table className="w-full min-w-[600px] text-left text-sm">
                                    <thead className="bg-slate-50 dark:bg-slate-900">
                                        <tr>
                                            <th className="p-4">공통 메뉴</th>
                                            {data.languages.map((item) => (
                                                <th
                                                    key={item.code}
                                                    className="p-4"
                                                >
                                                    {item.name}
                                                    <span className="ml-2 text-xs font-normal text-slate-400 dark:text-slate-500">
                                                        {item.nativeName}
                                                    </span>
                                                </th>
                                            ))}
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {data.menus
                                            .filter(
                                                (menu) =>
                                                    !onlyPending ||
                                                    translationState(
                                                        menu,
                                                        languageCode,
                                                    ) !== 'published',
                                            )
                                            .map((menu) => (
                                                <tr
                                                    key={menu.key}
                                                    className="border-t border-slate-100 hover:bg-slate-50/50 dark:border-slate-800 dark:hover:bg-slate-900/50"
                                                >
                                                    <td className="p-4">
                                                        <span
                                                            className={`flex items-center gap-2 ${menu.parent ? 'pl-4' : 'font-semibold'}`}
                                                        >
                                                            {menu.type ===
                                                            'folder' ? (
                                                                <Folder className="h-4 w-4 text-slate-400" />
                                                            ) : (
                                                                <FileText className="h-3.5 w-3.5 text-slate-400" />
                                                            )}
                                                            {menuLabel(menu)}
                                                        </span>
                                                    </td>
                                                    {data.languages.map(
                                                        (item) => (
                                                            <td
                                                                key={item.code}
                                                                className="p-3"
                                                            >
                                                                <button
                                                                    type="button"
                                                                    aria-label={`${menuLabel(menu)} ${item.name} 편집`}
                                                                    onClick={() => {
                                                                        setOnlyPending(
                                                                            false,
                                                                        );
                                                                        setQuery(
                                                                            '',
                                                                        );
                                                                        navigate(
                                                                            menu.key,
                                                                            item.code,
                                                                            'editor',
                                                                        );
                                                                    }}
                                                                    className="flex w-full flex-col items-start gap-1.5 rounded-lg p-2 hover:bg-blue-50 focus-visible:outline-blue-500 dark:hover:bg-blue-950/40"
                                                                >
                                                                    <Badge
                                                                        state={translationState(
                                                                            menu,
                                                                            item.code,
                                                                        )}
                                                                    />
                                                                    <span className="max-w-52 truncate text-xs text-slate-500 dark:text-slate-400">
                                                                        {menu
                                                                            .translations[
                                                                            item
                                                                                .code
                                                                        ]
                                                                            ?.name ||
                                                                            '번역을 시작하세요'}
                                                                    </span>
                                                                </button>
                                                            </td>
                                                        ),
                                                    )}
                                                </tr>
                                            ))}
                                    </tbody>
                                </table>
                                {onlyPending &&
                                    counts.published === data.menus.length && (
                                        <p className="p-10 text-center text-sm text-slate-500 dark:text-slate-400">
                                            모든 메뉴가 게시 완료 상태입니다.
                                        </p>
                                    )}
                            </div>
                        </section>
                    ) : (
                        <div className="grid items-start gap-4 lg:grid-cols-[270px_minmax(0,1fr)]">
                            <aside
                                className={`${showMobileTree ? 'block' : 'hidden'} overflow-hidden rounded-xl lg:block border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-950`}
                            >
                                <div className="flex items-center justify-between border-b border-slate-200 p-4 dark:border-slate-800">
                                    <h2 className="flex items-center gap-2 text-sm font-semibold">
                                        <ListTree className="h-4 w-4 text-slate-400 dark:text-slate-500" />
                                        공통 메뉴 트리
                                    </h2>
                                    <span className="text-[10px] text-slate-400 dark:text-slate-500">
                                        {data.menus.length}개
                                    </span>
                                </div>
                                <div className="space-y-3 p-3">
                                    <div className="relative">
                                        <Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-400 dark:text-slate-500" />
                                        <input
                                            aria-label="메뉴 검색"
                                            placeholder="메뉴명 또는 키 검색"
                                            className={`${inputStyle} !py-2 pl-9`}
                                            value={query}
                                            onChange={(event) =>
                                                setQuery(event.target.value)
                                            }
                                        />
                                    </div>
                                    <label className="flex items-center gap-2 text-xs text-slate-500 dark:text-slate-400">
                                        <input
                                            type="checkbox"
                                            checked={onlyPending}
                                            onChange={(event) =>
                                                setOnlyPending(
                                                    event.target.checked,
                                                )
                                            }
                                            className="h-3.5 w-3.5 accent-blue-600"
                                        />
                                        {language.name} 작업 필요만 보기
                                    </label>
                                </div>
                                <nav
                                    aria-label="편집할 공통 메뉴"
                                    className="max-h-[580px] overflow-y-auto px-2 pb-3"
                                >
                                    <ul>{renderTree(null)}</ul>
                                    {filteredMenus.length === 0 && (
                                        <div className="space-y-3 px-3 py-8 text-center">
                                            <p className="text-xs text-slate-500 dark:text-slate-400">
                                                조건에 맞는 메뉴가 없습니다.
                                            </p>
                                            <button
                                                type="button"
                                                className={secondary}
                                                onClick={() => {
                                                    setQuery('');
                                                    setOnlyPending(false);
                                                }}
                                            >
                                                조건 초기화
                                            </button>
                                        </div>
                                    )}
                                </nav>
                                <div className="border-t border-slate-200 p-3 dark:border-slate-800">
                                    <button
                                        type="button"
                                        onClick={() =>
                                            setShowAddMenu((value) => !value)
                                        }
                                        className="inline-flex w-full items-center justify-center gap-2 rounded-lg bg-emerald-600 px-3 py-2 text-xs font-semibold text-white hover:bg-emerald-700 dark:bg-emerald-600 dark:text-white dark:hover:bg-emerald-700"
                                    >
                                        <Plus className="h-4 w-4" />
                                        공통 메뉴 추가
                                    </button>
                                    {showAddMenu && (
                                        <form
                                            onSubmit={addMenu}
                                            className="mt-4 space-y-3"
                                        >
                                            <Field label="국문 메뉴명">
                                                <input
                                                    autoFocus
                                                    value={newMenu.name}
                                                    onChange={(event) =>
                                                        setNewMenu((value) => ({
                                                            ...value,
                                                            name: event.target
                                                                .value,
                                                        }))
                                                    }
                                                    className={inputStyle}
                                                    maxLength={100}
                                                />
                                            </Field>
                                            <Field label="메뉴 키">
                                                <input
                                                    value={newMenu.key}
                                                    placeholder="e.g. accommodation"
                                                    onChange={(event) =>
                                                        setNewMenu((value) => ({
                                                            ...value,
                                                            key: event.target
                                                                .value,
                                                        }))
                                                    }
                                                    className={inputStyle}
                                                    maxLength={80}
                                                />
                                            </Field>
                                            <Field label="메뉴 유형">
                                                <select
                                                    value={newMenu.type}
                                                    onChange={(event) =>
                                                        setNewMenu((value) => ({
                                                            ...value,
                                                            type: event.target
                                                                .value as DemoMenu['type'],
                                                            parent: '',
                                                        }))
                                                    }
                                                    className={inputStyle}
                                                >
                                                    <option value="page">
                                                        일반 페이지
                                                    </option>
                                                    <option value="folder">
                                                        폴더
                                                    </option>
                                                </select>
                                            </Field>
                                            <Field label="상위 메뉴">
                                                <select
                                                    value={newMenu.parent}
                                                    disabled={
                                                        newMenu.type ===
                                                        'folder'
                                                    }
                                                    onChange={(event) =>
                                                        setNewMenu((value) => ({
                                                            ...value,
                                                            parent: event.target
                                                                .value,
                                                        }))
                                                    }
                                                    className={inputStyle}
                                                >
                                                    <option value="">
                                                        최상위
                                                    </option>
                                                    {data.menus
                                                        .filter(
                                                            (menu) =>
                                                                menu.type ===
                                                                    'folder' &&
                                                                !menu.parent,
                                                        )
                                                        .map((menu) => (
                                                            <option
                                                                key={menu.key}
                                                                value={menu.key}
                                                            >
                                                                {menuLabel(
                                                                    menu,
                                                                )}
                                                            </option>
                                                        ))}
                                                </select>
                                            </Field>
                                            <div className="flex justify-end gap-2">
                                                <button
                                                    type="button"
                                                    className={secondary}
                                                    onClick={() =>
                                                        setShowAddMenu(false)
                                                    }
                                                >
                                                    취소
                                                </button>
                                                <button
                                                    type="submit"
                                                    className={primary}
                                                >
                                                    추가
                                                </button>
                                            </div>
                                        </form>
                                    )}
                                </div>
                            </aside>

                            <section className="min-w-0 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-950">
                                <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 p-4 dark:border-slate-800 md:p-5">
                                    <div>
                                        <p className="mb-1 text-[11px] text-slate-400 dark:text-slate-500">
                                            {selectedMenu.parent
                                                ? menuLabel(
                                                      data.menus.find(
                                                          (menu) =>
                                                              menu.key ===
                                                              selectedMenu.parent,
                                                      )!,
                                                  )
                                                : '공통 메뉴'}{' '}
                                            /{' '}
                                            {selectedMenu.type === 'folder'
                                                ? '폴더'
                                                : '일반 페이지'}
                                        </p>
                                        <h2 className="flex items-center gap-2 text-base font-semibold">
                                            {menuLabel(selectedMenu)}
                                            <span className="font-normal text-slate-300 dark:text-slate-600">
                                                /
                                            </span>
                                            <span className="text-blue-600 dark:text-blue-400">
                                                {language.name}
                                            </span>
                                            <Badge
                                                state={translationState(
                                                    selectedMenu,
                                                    languageCode,
                                                )}
                                            />
                                        </h2>
                                    </div>
                                    {view === 'editor' ? (
                                        <button
                                            type="button"
                                            aria-pressed={showReference}
                                            onClick={() =>
                                                setShowReference(
                                                    (value) => !value,
                                                )
                                            }
                                            className={secondary}
                                        >
                                            <Columns2 className="h-4 w-4" />
                                            {showReference
                                                ? '원문 비교 닫기'
                                                : '원문 함께 보기'}
                                        </button>
                                    ) : (
                                        <div className="flex gap-1">
                                            <button
                                                type="button"
                                                aria-label="데스크톱 미리보기"
                                                aria-pressed={!mobilePreview}
                                                className={secondary}
                                                onClick={() =>
                                                    setMobilePreview(false)
                                                }
                                            >
                                                <Monitor className="h-4 w-4" />
                                            </button>
                                            <button
                                                type="button"
                                                aria-label="모바일 미리보기"
                                                aria-pressed={mobilePreview}
                                                className={secondary}
                                                onClick={() =>
                                                    setMobilePreview(true)
                                                }
                                            >
                                                <Smartphone className="h-4 w-4" />
                                            </button>
                                        </div>
                                    )}
                                </div>

                                {view === 'editor' ? (
                                    <>
                                        <details className="border-b border-slate-200 bg-slate-50/50 dark:border-slate-800 dark:bg-slate-900/30">
                                            <summary className="cursor-pointer px-5 py-3 text-xs font-semibold text-slate-500 dark:text-slate-400">
                                                공통 설정 · 메뉴 키, 노출 및
                                                순서
                                            </summary>
                                            <div className="flex flex-wrap items-center justify-between gap-3 px-5 pb-4">
                                                <p className="text-xs text-slate-500 dark:text-slate-400">
                                                    메뉴 키{' '}
                                                    <code className="ml-1 rounded bg-slate-100 px-2 py-1 dark:bg-slate-800">
                                                        {selectedMenu.key}
                                                    </code>
                                                </p>
                                                <label className="flex items-center gap-2 text-xs">
                                                    <input
                                                        type="checkbox"
                                                        checked={
                                                            selectedMenu.enabled
                                                        }
                                                        onChange={(event) => {
                                                            const next =
                                                                withEditorContent();
                                                            setData({
                                                                ...next,
                                                                menus: next.menus.map(
                                                                    (menu) =>
                                                                        menu.key ===
                                                                        selectedKey
                                                                            ? {
                                                                                  ...menu,
                                                                                  enabled:
                                                                                      event
                                                                                          .target
                                                                                          .checked,
                                                                              }
                                                                            : menu,
                                                                ),
                                                            });
                                                        }}
                                                        className="h-4 w-4 accent-blue-600"
                                                    />
                                                    전체 언어에서 사용
                                                </label>
                                                <div className="flex gap-1">
                                                    <button
                                                        type="button"
                                                        aria-label="메뉴 위로 이동"
                                                        disabled={
                                                            siblingIndex === 0
                                                        }
                                                        onClick={() =>
                                                            moveMenu(-1)
                                                        }
                                                        className={secondary}
                                                    >
                                                        <ArrowUp className="h-3.5 w-3.5" />
                                                        위로
                                                    </button>
                                                    <button
                                                        type="button"
                                                        aria-label="메뉴 아래로 이동"
                                                        disabled={
                                                            siblingIndex ===
                                                            siblings.length - 1
                                                        }
                                                        onClick={() =>
                                                            moveMenu(1)
                                                        }
                                                        className={secondary}
                                                    >
                                                        <ArrowDown className="h-3.5 w-3.5" />
                                                        아래로
                                                    </button>
                                                </div>
                                            </div>
                                        </details>
                                        <div
                                            className={`grid ${showReference && data.languages.length > 1 ? 'xl:grid-cols-[minmax(240px,0.75fr)_minmax(0,1.25fr)]' : 'grid-cols-1'}`}
                                        >
                                            {showReference &&
                                                data.languages.length > 1 && (
                                                    <div className="order-2 min-w-0 space-y-4 border-b xl:order-1 border-slate-200 bg-slate-50/60 p-4 dark:border-slate-800 dark:bg-slate-900/30 xl:border-b-0 xl:border-r md:p-5">
                                                        <div className="flex flex-wrap items-center justify-between gap-2">
                                                            <label className="flex items-center gap-2 text-xs font-semibold text-slate-500 dark:text-slate-400">
                                                                참고 원문
                                                                <select
                                                                    aria-label="참고 언어"
                                                                    className="rounded-md border border-slate-200 bg-white px-2 py-1.5 text-xs text-slate-700 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
                                                                    value={
                                                                        referenceCode
                                                                    }
                                                                    onChange={(
                                                                        event,
                                                                    ) =>
                                                                        setReferenceCode(
                                                                            event
                                                                                .target
                                                                                .value,
                                                                        )
                                                                    }
                                                                >
                                                                    {data.languages
                                                                        .filter(
                                                                            (
                                                                                item,
                                                                            ) =>
                                                                                item.code !==
                                                                                languageCode,
                                                                        )
                                                                        .map(
                                                                            (
                                                                                item,
                                                                            ) => (
                                                                                <option
                                                                                    key={
                                                                                        item.code
                                                                                    }
                                                                                    value={
                                                                                        item.code
                                                                                    }
                                                                                >
                                                                                    {
                                                                                        item.name
                                                                                    }
                                                                                </option>
                                                                            ),
                                                                        )}
                                                                </select>
                                                            </label>
                                                            <span className="text-[10px] text-slate-400 dark:text-slate-500">
                                                                읽기 전용
                                                            </span>
                                                        </div>
                                                        <div>
                                                            <p className="mb-1.5 text-xs text-slate-400 dark:text-slate-500">
                                                                메뉴명
                                                            </p>
                                                            <p className="rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm font-semibold dark:border-slate-700 dark:bg-slate-950">
                                                                {reference?.name ||
                                                                    '작성된 메뉴명이 없습니다'}
                                                            </p>
                                                        </div>
                                                        <div>
                                                            <p className="mb-1.5 text-xs text-slate-400 dark:text-slate-500">
                                                                메뉴 HTML
                                                            </p>
                                                            {reference?.html ? (
                                                                <iframe
                                                                    title="참고 원문 미리보기"
                                                                    sandbox=""
                                                                    srcDoc={previewDocument(
                                                                        reference.html,
                                                                        referenceCode,
                                                                    )}
                                                                    className="h-[490px] w-full rounded-lg border border-slate-200 bg-white dark:border-slate-700 dark:bg-white"
                                                                />
                                                            ) : (
                                                                <div className="flex h-48 items-center justify-center rounded-lg border border-dashed border-slate-200 p-5 text-xs text-slate-400 dark:border-slate-700 dark:text-slate-500">
                                                                    {selectedMenu.type ===
                                                                    'folder'
                                                                        ? '폴더는 본문 없이 사용할 수 있습니다.'
                                                                        : '작성된 원문이 없습니다.'}
                                                                </div>
                                                            )}
                                                        </div>
                                                        <button
                                                            type="button"
                                                            disabled={
                                                                !reference?.name &&
                                                                !reference?.html
                                                            }
                                                            onClick={() =>
                                                                void copyReference()
                                                            }
                                                            className={`${secondary} w-full`}
                                                        >
                                                            <Copy className="h-3.5 w-3.5" />
                                                            원문을{' '}
                                                            {language.name} 작업
                                                            공간에 복사
                                                        </button>
                                                        <p className="text-[11px] text-slate-400 dark:text-slate-500">
                                                            내용만 복사하며 자동
                                                            번역하지 않습니다.
                                                        </p>
                                                    </div>
                                                )}
                                            <div className="order-1 min-w-0 space-y-5 p-4 md:p-5 xl:order-2">
                                                <div className="flex items-center justify-between">
                                                    <h3 className="text-sm font-semibold">
                                                        {language.name} 콘텐츠
                                                    </h3>
                                                    <span className="text-xs text-slate-400 dark:text-slate-500">
                                                        {language.nativeName}
                                                    </span>
                                                </div>
                                                <Field
                                                    label={`메뉴명 (${language.name})`}
                                                >
                                                    <input
                                                        value={current.name}
                                                        onChange={(event) =>
                                                            updateTranslation({
                                                                name: event
                                                                    .target
                                                                    .value,
                                                            })
                                                        }
                                                        placeholder={`${language.name} 메뉴명을 입력하세요`}
                                                        maxLength={255}
                                                        className={inputStyle}
                                                    />
                                                </Field>
                                                <div className="flex min-w-0 items-center gap-2 rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-500 dark:bg-slate-900 dark:text-slate-400">
                                                    <Globe2 className="h-3.5 w-3.5 shrink-0" />
                                                    <span
                                                        className="truncate"
                                                        title={routePath}
                                                    >
                                                        {routePath}
                                                    </span>
                                                </div>
                                                <div
                                                    role="group"
                                                    aria-labelledby="menu2-html-label"
                                                >
                                                    <div className="mb-2 flex items-center justify-between">
                                                        <h3
                                                            id="menu2-html-label"
                                                            className="text-sm font-semibold"
                                                        >
                                                            메뉴 HTML{' '}
                                                            <span className="font-normal text-slate-400 dark:text-slate-500">
                                                                ({language.name}
                                                                )
                                                            </span>
                                                        </h3>
                                                        {selectedMenu.type ===
                                                            'folder' && (
                                                            <span className="text-[10px] text-slate-400 dark:text-slate-500">
                                                                선택 입력
                                                            </span>
                                                        )}
                                                    </div>
                                                    <CkEditorRichTextEditor
                                                        ref={editorRef}
                                                        key={`${selectedKey}:${languageCode}:${editorVersion}`}
                                                        initialContent={
                                                            current.html
                                                        }
                                                        onChange={(html) => {
                                                            if (
                                                                html !==
                                                                current.html
                                                            )
                                                                updateTranslation(
                                                                    { html },
                                                                );
                                                        }}
                                                        compactToolbar
                                                        contentStyle="public-menu"
                                                        editorHeight={330}
                                                        previewHeight={400}
                                                        previewTitle={`${language.name} 편집 내용 미리보기`}
                                                    />
                                                </div>
                                                <div className="grid gap-4 sm:grid-cols-2">
                                                    <Field label="작업 상태">
                                                        <select
                                                            className={
                                                                inputStyle
                                                            }
                                                            value={
                                                                current.status
                                                            }
                                                            onChange={(
                                                                event,
                                                            ) => {
                                                                if (
                                                                    event.target
                                                                        .value ===
                                                                        'published' &&
                                                                    !isComplete(
                                                                        selectedMenu,
                                                                        {
                                                                            ...current,
                                                                            html:
                                                                                editorRef.current?.getData() ??
                                                                                current.html,
                                                                        },
                                                                    )
                                                                ) {
                                                                    notify(
                                                                        'error',
                                                                        '메뉴명과 본문을 작성한 후 게시할 수 있습니다.',
                                                                    );
                                                                    return;
                                                                }
                                                                const next =
                                                                    withEditorContent();
                                                                setData({
                                                                    ...next,
                                                                    menus: next.menus.map(
                                                                        (
                                                                            menu,
                                                                        ) =>
                                                                            menu.key !==
                                                                            selectedKey
                                                                                ? menu
                                                                                : {
                                                                                      ...menu,
                                                                                      translations:
                                                                                          {
                                                                                              ...menu.translations,
                                                                                              [languageCode]:
                                                                                                  {
                                                                                                      ...(menu
                                                                                                          .translations[
                                                                                                          languageCode
                                                                                                      ] ??
                                                                                                          blankTranslation()),
                                                                                                      status: event
                                                                                                          .target
                                                                                                          .value as DemoTranslation['status'],
                                                                                                  },
                                                                                          },
                                                                                  },
                                                                    ),
                                                                });
                                                            }}
                                                        >
                                                            <option value="draft">
                                                                작성 중
                                                            </option>
                                                            <option value="review">
                                                                검토 요청
                                                            </option>
                                                            <option value="published">
                                                                게시 완료
                                                            </option>
                                                        </select>
                                                    </Field>
                                                    <label className="flex items-center gap-3 self-end rounded-lg border border-slate-200 px-3 py-3 dark:border-slate-700">
                                                        <input
                                                            type="checkbox"
                                                            checked={
                                                                current.visible
                                                            }
                                                            onChange={(event) =>
                                                                updateTranslation(
                                                                    {
                                                                        visible:
                                                                            event
                                                                                .target
                                                                                .checked,
                                                                    },
                                                                )
                                                            }
                                                            className="h-4 w-4 accent-blue-600"
                                                        />
                                                        <span className="text-xs font-medium">
                                                            {language.name}{' '}
                                                            메뉴에 노출
                                                        </span>
                                                    </label>
                                                </div>
                                                {!language.enabled && (
                                                    <p className="rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-700 dark:bg-amber-950/30 dark:text-amber-300">
                                                        {language.name}는 준비
                                                        중입니다. 게시 완료 후
                                                        지원 언어 설정에서
                                                        공개하세요.
                                                    </p>
                                                )}
                                            </div>
                                        </div>
                                        <div className="fixed inset-x-0 bottom-0 z-30 border-t border-slate-200 bg-white/95 shadow-lg backdrop-blur dark:border-slate-700 dark:bg-slate-950/95">
                                            <div className="mx-auto flex max-w-[1600px] flex-wrap items-center justify-between gap-3 px-4 py-3 md:px-8">
                                                <div className="flex min-w-0 items-center gap-3">
                                                    <label className="flex items-center gap-2 text-xs font-semibold">
                                                        <Languages className="h-4 w-4 text-blue-600 dark:text-blue-400" />
                                                        <select
                                                            aria-label="작업 언어 빠른 전환"
                                                            className="rounded-lg border border-slate-200 bg-white px-2 py-2 text-xs dark:border-slate-700 dark:bg-slate-900"
                                                            value={languageCode}
                                                            onChange={(event) =>
                                                                navigate(
                                                                    selectedKey,
                                                                    event.target
                                                                        .value,
                                                                )
                                                            }
                                                        >
                                                            {data.languages.map(
                                                                (item) => (
                                                                    <option
                                                                        key={
                                                                            item.code
                                                                        }
                                                                        value={
                                                                            item.code
                                                                        }
                                                                    >
                                                                        {
                                                                            item.name
                                                                        }
                                                                    </option>
                                                                ),
                                                            )}
                                                        </select>
                                                    </label>
                                                    <span className="hidden text-xs text-slate-500 dark:text-slate-400 sm:inline">
                                                        {menuLabel(
                                                            selectedMenu,
                                                        )}
                                                    </span>
                                                    <button
                                                        type="button"
                                                        onClick={nextMissing}
                                                        className={secondary}
                                                    >
                                                        다음 미번역
                                                        <ArrowRight className="h-3.5 w-3.5" />
                                                    </button>
                                                </div>
                                                <div className="flex flex-wrap gap-2">
                                                    <button
                                                        type="button"
                                                        onClick={() => save()}
                                                        className={secondary}
                                                    >
                                                        <Save className="h-4 w-4" />
                                                        변경사항 저장
                                                    </button>
                                                    <button
                                                        type="button"
                                                        onClick={() =>
                                                            save(true)
                                                        }
                                                        className={primary}
                                                    >
                                                        <CheckCircle2 className="h-4 w-4" />
                                                        게시 완료로 저장
                                                    </button>
                                                </div>
                                            </div>
                                        </div>
                                    </>
                                ) : (
                                    <div className="space-y-4 bg-slate-100 p-4 dark:bg-slate-900 md:p-5">
                                        <div className="flex items-start justify-between gap-3 text-xs text-slate-500 dark:text-slate-400">
                                            <p>
                                                상단 메뉴는 언어 공개·메뉴
                                                노출·게시 상태를 반영합니다.
                                                <br />
                                                <span className="text-[11px]">
                                                    본문은 현재 작업 내용을 미리
                                                    보여줍니다. 외부 이미지·링크
                                                    이동은 체험 화면에서
                                                    제한됩니다.
                                                </span>
                                            </p>
                                            <span className="shrink-0 rounded bg-white px-2 py-1 dark:bg-slate-800">
                                                {mobilePreview
                                                    ? '모바일'
                                                    : '데스크톱'}
                                            </span>
                                        </div>
                                        <div
                                            className={`mx-auto overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-700 dark:bg-white ${mobilePreview ? 'max-w-[390px]' : 'w-full'}`}
                                        >
                                            <div className="flex items-center gap-2 border-b border-slate-100 bg-slate-50 px-3 py-2 text-[10px] text-slate-400 dark:border-slate-100 dark:bg-slate-50 dark:text-slate-400">
                                                <span className="h-2 w-2 rounded-full bg-rose-300" />
                                                <span className="h-2 w-2 rounded-full bg-amber-300" />
                                                <span className="h-2 w-2 rounded-full bg-emerald-300" />
                                                <span className="ml-2 truncate">
                                                    icms2026.example{routePath}
                                                </span>
                                            </div>
                                            <div className="p-5 text-slate-900 dark:text-slate-900">
                                                <div className="flex items-center justify-between gap-3">
                                                    <span className="font-bold tracking-wide text-blue-900 dark:text-blue-900">
                                                        ICMS 2026
                                                    </span>
                                                    <select
                                                        aria-label="미리보기 언어"
                                                        className="rounded border border-slate-200 bg-white px-2 py-1 text-xs text-slate-700 dark:border-slate-200 dark:bg-white dark:text-slate-700"
                                                        value={languageCode}
                                                        onChange={(event) =>
                                                            navigate(
                                                                selectedKey,
                                                                event.target
                                                                    .value,
                                                            )
                                                        }
                                                    >
                                                        {data.languages.map(
                                                            (item) => (
                                                                <option
                                                                    key={
                                                                        item.code
                                                                    }
                                                                    value={
                                                                        item.code
                                                                    }
                                                                >
                                                                    {
                                                                        item.nativeName
                                                                    }
                                                                    {!item.enabled
                                                                        ? ' (준비 중)'
                                                                        : ''}
                                                                </option>
                                                            ),
                                                        )}
                                                    </select>
                                                </div>
                                                <div className="mt-5 flex flex-wrap gap-x-4 gap-y-3 border-y border-slate-100 py-3 dark:border-slate-100">
                                                    {data.menus
                                                        .filter(
                                                            (menu) =>
                                                                !menu.parent &&
                                                                previewVisible(
                                                                    menu,
                                                                ),
                                                        )
                                                        .map((menu) => (
                                                            <button
                                                                type="button"
                                                                key={menu.key}
                                                                onClick={() =>
                                                                    navigate(
                                                                        menu.key,
                                                                    )
                                                                }
                                                                className="text-xs font-semibold text-slate-600 hover:text-blue-600 dark:text-slate-600 dark:hover:text-blue-600"
                                                            >
                                                                {
                                                                    menu
                                                                        .translations[
                                                                        languageCode
                                                                    ]?.name
                                                                }
                                                            </button>
                                                        ))}
                                                    {!data.menus.some(
                                                        (menu) =>
                                                            !menu.parent &&
                                                            previewVisible(
                                                                menu,
                                                            ),
                                                    ) && (
                                                        <span className="text-xs text-slate-400 dark:text-slate-400">
                                                            이 언어로 공개된
                                                            메뉴가 없습니다.
                                                        </span>
                                                    )}
                                                </div>
                                                {data.menus.some(
                                                    (menu) =>
                                                        menu.parent ===
                                                            (selectedMenu.parent ??
                                                                selectedKey) &&
                                                        previewVisible(menu),
                                                ) && (
                                                    <div className="mt-3 flex flex-wrap gap-2">
                                                        {data.menus
                                                            .filter(
                                                                (menu) =>
                                                                    menu.parent ===
                                                                        (selectedMenu.parent ??
                                                                            selectedKey) &&
                                                                    previewVisible(
                                                                        menu,
                                                                    ),
                                                            )
                                                            .map((menu) => (
                                                                <button
                                                                    type="button"
                                                                    key={
                                                                        menu.key
                                                                    }
                                                                    onClick={() =>
                                                                        navigate(
                                                                            menu.key,
                                                                        )
                                                                    }
                                                                    className={`rounded px-2 py-1 text-xs ${menu.key === selectedKey ? 'bg-blue-50 text-blue-700 dark:bg-blue-50 dark:text-blue-700' : 'text-slate-500 dark:text-slate-500'}`}
                                                                >
                                                                    {
                                                                        menu
                                                                            .translations[
                                                                            languageCode
                                                                        ]?.name
                                                                    }
                                                                </button>
                                                            ))}
                                                    </div>
                                                )}
                                                <div className="mt-5 rounded-lg bg-blue-50 p-5 dark:bg-blue-50">
                                                    <span className="text-[10px] tracking-[0.2em] text-blue-500 dark:text-blue-500">
                                                        ICMS 2026
                                                    </span>
                                                    <h3 className="mt-2 text-xl font-bold text-blue-950 dark:text-blue-950">
                                                        {current.name ||
                                                            '메뉴명을 입력해 주세요'}
                                                    </h3>
                                                </div>
                                                {!previewVisible(
                                                    selectedMenu,
                                                ) && (
                                                    <p className="mt-3 text-[11px] text-amber-700 dark:text-amber-700">
                                                        현재 비공개 상태입니다.
                                                        아래는 담당자용 작업
                                                        미리보기입니다.
                                                    </p>
                                                )}
                                            </div>
                                            <iframe
                                                title="사용자 페이지 미리보기"
                                                sandbox=""
                                                srcDoc={previewDocument(
                                                    current.html ||
                                                        `<p>${escapeHtml(selectedMenu.type === 'folder' ? '하위 메뉴에서 페이지를 선택해 주세요.' : '이 언어의 본문을 작성해 주세요.')}</p>`,
                                                    languageCode,
                                                )}
                                                className="h-[560px] w-full border-0 bg-white dark:bg-white"
                                            />
                                        </div>
                                    </div>
                                )}
                            </section>
                        </div>
                    )}
                    <p className="text-center text-[11px] text-slate-400 dark:text-slate-500">
                        ICMS 2026 · 다국어 메뉴 관리 시안 · 실제 홈페이지에는
                        반영되지 않습니다.
                    </p>
                </main>
                <NotificationToast
                    items={notifications}
                    onClose={(id) =>
                        setNotifications((items) =>
                            items.filter((item) => item.id !== id),
                        )
                    }
                />
            </div>
        </div>
    );
};

const Stat = ({
    label,
    value,
    detail,
    green = false,
}: {
    label: string;
    value: number;
    detail: string;
    green?: boolean;
}) => (
    <div className="p-3 md:p-5">
        <p className="text-[11px] font-medium sm:text-xs text-slate-500 dark:text-slate-400">
            {label}
        </p>
        <p
            className={`mt-1 text-xl font-bold ${green ? 'text-emerald-600 dark:text-emerald-400' : 'text-slate-800 dark:text-slate-100'}`}
        >
            {value}
            <span className="ml-3 hidden text-xs font-normal text-slate-400 dark:text-slate-500 sm:inline">
                {detail}
            </span>
        </p>
    </div>
);
