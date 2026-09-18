import { useEffect, useMemo, useRef, useState, type DragEvent as ReactDragEvent, type SelectHTMLAttributes } from 'react';
import {
    CalendarRange,
    CheckCircle2,
    ChevronDown,
    ChevronRight,
    GripVertical,
    History,
    ListTree,
    Plus,
    RefreshCw,
    Save,
    Settings2,
    Trash2,
    X
} from 'lucide-react';
import type { NotificationType } from './NotificationToast';
import { useConfirm } from './confirmDialogContext';
import { CkEditorRichTextEditor, type CkEditorRichTextEditorHandle } from './CkEditorRichTextEditor';
import { MenuHtmlHistoryModal, type RestoredMenuHtml } from './MenuHtmlHistoryModal';
import { getStoredAdminConferenceSeq } from '../adminSession';
import type { ConferenceSettings } from './ConferenceSettingsModal';

interface MenuNode {
    seq: number;
    languageCode?: string;
    translationReady?: boolean;
    menuScope: 'admin' | 'user';
    menuKey: string;
    parentKey?: string | null;
    menuName: string;
    menuPath?: string | null;
    menuType?: 'folder' | 'page' | 'board' | 'link' | null;
    pathType?: 'segment' | 'full' | 'external' | null;
    routePath?: string | null;
    depth?: number | null;
    boardSeq?: number | null;
    linkUrl?: string | null;
    targetType?: 'self' | 'blank' | null;
    authRequired?: boolean | null;
    navigationVisible?: boolean | null;
    menuHtml?: string | null;
    htmlRevisionNo?: number;
    sortOrder: number;
    useStartDate?: string | null;
    useEndDate?: string | null;
    enabled: boolean;
    children?: MenuNode[];
}

interface MenuPageProps {
    onNotify: (type: NotificationType, message: string) => void;
    onSaved?: () => void;
}

type MenuScope = MenuNode['menuScope'];
type MenuType = NonNullable<MenuNode['menuType']>;
type TargetType = NonNullable<MenuNode['targetType']>;
type DropPosition = 'before' | 'inside' | 'after';

interface DragPreview {
    draggedId: string;
    targetId: string;
    position: DropPosition;
}

const scopeLabels: Record<MenuScope, string> = {
    admin: '관리자',
    user: '사용자'
};

const MAX_MENU_DEPTH_BY_SCOPE: Record<MenuScope, number> = {
    admin: 3,
    user: 3
};

const menuTypeLabels: Record<MenuType, string> = {
    folder: '폴더',
    page: '일반 페이지',
    board: '게시판',
    link: '링크'
};

const targetTypeLabels: Record<TargetType, string> = {
    self: 'self',
    blank: 'blank'
};

const selectClassName = 'w-full appearance-none rounded-lg border border-slate-200 bg-white px-3 py-2 pr-12 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-950 dark:text-slate-50';

const SelectField = ({
    children,
    className = '',
    ...props
}: SelectHTMLAttributes<HTMLSelectElement>) => (
    <div className="relative">
        <select
            {...props}
            className={`${selectClassName} ${className}`.trim()}
        >
            {children}
        </select>
        <span className="pointer-events-none absolute inset-y-0 right-4 flex items-center text-slate-400 dark:text-slate-500">
            <ChevronDown className="h-4 w-4" />
        </span>
    </div>
);

export const MenuPage = ({ onNotify, onSaved }: MenuPageProps) => {
    const confirm = useConfirm();
    const menuHtmlEditorRef = useRef<CkEditorRichTextEditorHandle>(null);
    const [language, setLanguage] = useState('en');
    const [supportedLanguages, setSupportedLanguages] = useState<string[]>([]);
    const [menus, setMenus] = useState<MenuNode[]>([]);
    const [selectedMenuSeq, setSelectedMenuSeq] = useState<number | null>(null);
    const [expandedKeys, setExpandedKeys] = useState<Record<string, boolean>>({});
    const [isCreateMode, setIsCreateMode] = useState(false);
    const [menuScope, setMenuScope] = useState<MenuScope>('admin');
    const [menuKey, setMenuKey] = useState('');
    const [parentKey, setParentKey] = useState('');
    const [menuName, setMenuName] = useState('');
    const [menuPath, setMenuPath] = useState('');
    const [menuType, setMenuType] = useState<MenuType>('folder');
    const [routePath, setRoutePath] = useState('');
    const [boardSeq, setBoardSeq] = useState('');
    const [linkUrl, setLinkUrl] = useState('');
    const [targetType, setTargetType] = useState<TargetType>('self');
    const [authRequired, setAuthRequired] = useState(false);
    const [navigationVisible, setNavigationVisible] = useState(true);
    const [menuHtml, setMenuHtml] = useState('');
    const [changeMemo, setChangeMemo] = useState('');
    const [historyOpen, setHistoryOpen] = useState(false);
    const [editorGeneration, setEditorGeneration] = useState(0);
    const [sortOrder, setSortOrder] = useState('0');
    const [useStartDate, setUseStartDate] = useState('');
    const [useEndDate, setUseEndDate] = useState('');
    const [enabled, setEnabled] = useState(true);
    const [isLoading, setIsLoading] = useState(false);
    const [isSaving, setIsSaving] = useState(false);
    const [isDeleting, setIsDeleting] = useState(false);
    const [isOrderSaving, setIsOrderSaving] = useState(false);
    const [hasOrderChanges, setHasOrderChanges] = useState(false);
    const [draggedNodeId, setDraggedNodeId] = useState<string | null>(null);
    const [dragPreview, setDragPreview] = useState<DragPreview | null>(null);
    const [errorMessage, setErrorMessage] = useState('');
    const [reloadKey, setReloadKey] = useState(0);
    const onNotifyRef = useRef(onNotify);

    useEffect(() => {
        onNotifyRef.current = onNotify;
    }, [onNotify]);

    useEffect(() => {
        const abort = new AbortController();
        void (async () => {
            try {
                const response = await fetch('/api/admin/conference-settings', { signal: abort.signal });
                if (!response.ok) throw new Error(await response.text());
                const conferences = await response.json() as ConferenceSettings[];
                const selected = conferences.find(conference => conference.seq === getStoredAdminConferenceSeq());
                if (!selected?.supportedLanguages?.length) throw new Error('학회 지원 언어를 먼저 설정해 주세요.');
                setSupportedLanguages(selected.supportedLanguages);
                setLanguage(selected.supportedLanguages.includes('en') ? 'en' : selected.defaultLanguage ?? selected.supportedLanguages[0]);
            } catch (error) {
                if (!abort.signal.aborted) onNotifyRef.current('error', error instanceof Error ? error.message : '지원 언어를 불러오지 못했습니다.');
            }
        })();
        return () => abort.abort();
    }, []);

    useEffect(() => {
        if (!supportedLanguages.length) return;
        const abortController = new AbortController();

        const fetchMenus = async () => {
            setIsLoading(true);
            setErrorMessage('');

            try {
                const response = await fetch(`/api/admin/menu-settings/tree?language=${encodeURIComponent(language)}`, { signal: abortController.signal });

                if (!response.ok) {
                    const message = await response.text();
                    throw new Error(message || '메뉴 목록을 불러오지 못했습니다.');
                }

                const data = normalizeMenuTree(await response.json() as MenuNode[]);
                setMenus(data);
                setHasOrderChanges(false);
                setDraggedNodeId(null);
                setDragPreview(null);

                setExpandedKeys((previous) => {
                    if (Object.keys(previous).length > 0) {
                        return previous;
                    }
                    return data.reduce<Record<string, boolean>>((acc, node) => {
                        acc[nodeId(node)] = true;
                        return acc;
                    }, {});
                });

                if (!isCreateMode) {
                    setSelectedMenuSeq((current) => {
                        if (current == null) {
                            return data[0]?.seq ?? null;
                        }
                        return findNodeBySeq(data, current)?.seq ?? data[0]?.seq ?? null;
                    });
                }
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') {
                    return;
                }
                const message = error instanceof Error ? error.message : '메뉴 목록을 불러오지 못했습니다.';
                setErrorMessage(message);
                onNotifyRef.current('error', message);
            } finally {
                setIsLoading(false);
            }
        };

        void fetchMenus();
        return () => abortController.abort();
    }, [isCreateMode, reloadKey, language, supportedLanguages]);

    const selectedMenu = useMemo(
        () => (selectedMenuSeq != null ? findNodeBySeq(menus, selectedMenuSeq) : null),
        [menus, selectedMenuSeq]
    );

    // Reset the editable draft when the selected server snapshot changes. Doing
    // this during the guarded render keeps the old language out of the editor.
    const [draftSource, setDraftSource] = useState({ menu: selectedMenu, creating: isCreateMode });
    const resetDraft = () => {
        if (isCreateMode) {
            return;
        }

        if (!selectedMenu) {
            setMenuScope('admin');
            setMenuKey('');
            setParentKey('');
            setMenuName('');
            setMenuPath('');
            setMenuType('folder');
            setRoutePath('');
            setBoardSeq('');
            setLinkUrl('');
            setTargetType('self');
            setAuthRequired(false);
            setNavigationVisible(true);
            setMenuHtml('');
            setSortOrder('0');
            setUseStartDate('');
            setUseEndDate('');
            setEnabled(true);
            return;
        }

        setMenuScope(selectedMenu.menuScope);
        setMenuKey(selectedMenu.menuKey);
        setParentKey(selectedMenu.parentKey ?? '');
        setMenuName(selectedMenu.translationReady === false ? '' : selectedMenu.menuName);
        setMenuPath(selectedMenu.menuPath ?? '');
        setMenuType(selectedMenu.menuType ?? 'folder');
        setRoutePath(selectedMenu.routePath ?? '');
        setBoardSeq(selectedMenu.boardSeq != null ? String(selectedMenu.boardSeq) : '');
        setLinkUrl(selectedMenu.linkUrl ?? '');
        setTargetType(selectedMenu.targetType ?? 'self');
        setAuthRequired(Boolean(selectedMenu.authRequired));
        setNavigationVisible(selectedMenu.navigationVisible !== false);
        setMenuHtml(selectedMenu.menuHtml ?? '');
        setSortOrder(String(selectedMenu.sortOrder ?? 0));
        setUseStartDate(selectedMenu.useStartDate ?? '');
        setUseEndDate(selectedMenu.useEndDate ?? '');
        setEnabled(Boolean(selectedMenu.enabled));
    };
    if (draftSource.menu !== selectedMenu || draftSource.creating !== isCreateMode) {
        setDraftSource({ menu: selectedMenu, creating: isCreateMode });
        resetDraft();
    }


    const flatMenus = useMemo(() => flattenMenus(menus), [menus]);

    const parentOptions = useMemo(
        () => flatMenus.filter((menu) => (
            menu.menuScope === menuScope
            && getMenuDepth(menu, menus) < MAX_MENU_DEPTH_BY_SCOPE[menuScope]
        )),
        [flatMenus, menuScope, menus]
    );

    const selectedPathLabel = useMemo(() => {
        if (!selectedMenu) {
            return '';
        }
        return `${scopeLabels[selectedMenu.menuScope]} / ${selectedMenu.menuName}`;
    }, [selectedMenu]);

    const isUserMenu = menuScope === 'user';
    const displayDepth = selectedMenu?.depth ?? (parentKey ? Math.min(getMenuDepthByKey(parentKey, menus) + 1, MAX_MENU_DEPTH_BY_SCOPE[menuScope]) : 0);

    const handleToggle = (node: MenuNode) => {
        const id = nodeId(node);
        setExpandedKeys((previous) => ({
            ...previous,
            [id]: !previous[id]
        }));
    };

    const handleLanguageChange = async (next: string) => {
        if (next === language || isSaving || isDeleting || isLoading) return;
        if (hasUnsavedChanges() && !await confirm({ title: '편집 언어 변경', message: '저장하지 않은 변경사항을 버리고 다른 언어로 이동하시겠습니까?', confirmText: '이동' })) return;
        setChangeMemo(''); setHistoryOpen(false); setIsCreateMode(false); setMenus([]);
        setEditorGeneration(value => value + 1); setLanguage(next);
    };

    const handleSelect = async (node: MenuNode) => {
        if (node.seq !== selectedMenuSeq && hasUnsavedChanges() && !await confirm({ title: '메뉴 변경', message: '저장하지 않은 변경사항을 버리고 이동하시겠습니까?', confirmText: '이동' })) return;
        setChangeMemo('');
        setIsCreateMode(false);
        setSelectedMenuSeq(node.seq);
    };

    const handleStartCreate = () => {
        setChangeMemo('');
        const defaultScope = selectedMenu?.menuScope ?? 'admin';
        setIsCreateMode(true);
        setMenuScope(defaultScope);
        setMenuKey('');
        setParentKey(selectedMenu?.menuKey ?? '');
        setMenuName('');
        setMenuPath('');
        setMenuType('folder');
        setRoutePath('');
        setBoardSeq('');
        setLinkUrl('');
        setTargetType('self');
        setAuthRequired(false);
        setNavigationVisible(true);
        setMenuHtml('');
        setSortOrder('0');
        setUseStartDate('');
        setUseEndDate('');
        setEnabled(true);
        setErrorMessage('');
    };

    const handleCancelCreate = () => {
        setIsCreateMode(false);
        setErrorMessage('');
    };

    const handleSave = async (event: React.FormEvent) => {
        event.preventDefault();
        if (isSaving || isDeleting) return;

        if (!isCreateMode && !selectedMenu) {
            return;
        }

        setIsSaving(true);
        setErrorMessage('');

        try {
            const formData = new URLSearchParams();
            const currentMenuHtml = menuHtmlEditorRef.current?.getData() ?? menuHtml;
            // CKEditor formats HTML while loading. Do not treat that as a user
            // edit, and do not overwrite a newer HTML when only settings change.
            const htmlChanged = isCreateMode || (menuHtmlEditorRef.current
                ? menuHtmlEditorRef.current.hasChanges()
                : menuHtml.trim() !== (selectedMenu?.menuHtml ?? '').trim());
            setMenuHtml(currentMenuHtml);
            if (isUserMenu) formData.append('language', language);
            formData.append('menuHtmlChanged', String(htmlChanged));
            formData.append('changeMemo', changeMemo);
            formData.append('menuName', menuName.trim());
            formData.append('menuPath', menuPath.trim());
            formData.append('menuType', menuType);
            formData.append('targetType', targetType);
            formData.append('authRequired', String(authRequired));
            formData.append('navigationVisible', String(navigationVisible));
            if (routePath.trim()) {
                formData.append('routePath', routePath.trim());
            }
            if (boardSeq.trim()) {
                formData.append('boardSeq', boardSeq.trim());
            }
            if (linkUrl.trim()) {
                formData.append('linkUrl', linkUrl.trim());
            }
            if (currentMenuHtml.trim()) {
                formData.append('menuHtml', currentMenuHtml);
            }
            if (useStartDate) {
                formData.append('useStartDate', useStartDate);
            }
            if (useEndDate) {
                formData.append('useEndDate', useEndDate);
            }
            formData.append('enabled', String(enabled));

            if (isCreateMode) {
                formData.append('menuScope', menuScope);
                formData.append('menuKey', menuKey.trim());
                formData.append('sortOrder', sortOrder.trim() || '0');
                if (parentKey) {
                    formData.append('parentKey', parentKey);
                }
            }

            const response = await fetch(isCreateMode ? '/api/admin/menu-settings' : `/api/admin/menu-settings/${selectedMenu?.seq}`, {
                method: isCreateMode ? 'POST' : 'PUT',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded'
                },
                body: formData
            });

            if (!response.ok) {
                const message = await response.text();
                throw new Error(message || (isCreateMode ? '메뉴 등록에 실패했습니다.' : '메뉴 설정 저장에 실패했습니다.'));
            }

            const savedMenu = await response.json() as MenuNode;
            setChangeMemo('');
            setEditorGeneration((value) => value + 1);
            setSelectedMenuSeq(savedMenu.seq);
            setIsCreateMode(false);
            setExpandedKeys((previous) => ({
                ...previous,
                [nodeId(savedMenu)]: true
            }));
            setReloadKey((value) => value + 1);
            onSaved?.();
            onNotify('success', isCreateMode ? '메뉴가 등록되었습니다.' : '메뉴 설정이 저장되었습니다.');
        } catch (error) {
            const message = error instanceof Error
                ? error.message
                : (isCreateMode ? '메뉴 등록에 실패했습니다.' : '메뉴 설정 저장에 실패했습니다.');
            setErrorMessage(message);
            onNotify('error', message);
        } finally {
            setIsSaving(false);
        }
    };

    const hasUnsavedChanges = () => {
        if (isCreateMode) return true;
        if (!selectedMenu) return false;
        const htmlDirty = menuHtmlEditorRef.current
            ? menuHtmlEditorRef.current.hasChanges()
            : menuHtml.trim() !== (selectedMenu.menuHtml ?? '').trim();
        const settingsDirty = menuName !== (selectedMenu.translationReady === false ? '' : selectedMenu.menuName)
            || menuPath !== (selectedMenu.menuPath ?? '')
            || menuType !== (selectedMenu.menuType ?? 'folder')
            || routePath !== (selectedMenu.routePath ?? '')
            || boardSeq !== (selectedMenu.boardSeq != null ? String(selectedMenu.boardSeq) : '')
            || linkUrl !== (selectedMenu.linkUrl ?? '')
            || targetType !== (selectedMenu.targetType ?? 'self')
            || authRequired !== Boolean(selectedMenu.authRequired)
            || navigationVisible !== (selectedMenu.navigationVisible !== false)
            || useStartDate !== (selectedMenu.useStartDate ?? '')
            || useEndDate !== (selectedMenu.useEndDate ?? '')
            || enabled !== Boolean(selectedMenu.enabled);
        return Boolean(htmlDirty || settingsDirty || hasOrderChanges || changeMemo.trim());
    };

    const canRestoreHtml = () => {
        if (!selectedMenu || isSaving || isDeleting || isOrderSaving) return false;
        if (hasUnsavedChanges()) {
            onNotify('info', '저장하지 않은 변경사항이 있습니다. 이력 창을 닫고 먼저 저장한 뒤 복원해 주세요.');
            return false;
        }
        return true;
    };

    const handleHtmlRestored = (restored: RestoredMenuHtml) => {
        const replace = (nodes: MenuNode[]): MenuNode[] => nodes.map((node) => ({
            ...node,
            ...(node.seq === restored.seq ? { menuHtml: restored.menuHtml, htmlRevisionNo: restored.htmlRevisionNo } : {}),
            children: node.children ? replace(node.children) : undefined
        }));
        setMenus(replace);
        setMenuHtml(restored.menuHtml ?? '');
        setHistoryOpen(false);
        onSaved?.();
    };

    const handleDelete = async () => {
        if (!selectedMenu || isCreateMode) {
            return;
        }

        const confirmed = await confirm({
            title: '메뉴 삭제',
            message: `'${selectedMenu.menuName}' 메뉴를 삭제하시겠습니까?\n하위 메뉴가 있으면 삭제할 수 없습니다.`,
            confirmText: '삭제',
            tone: 'danger'
        });
        if (!confirmed) {
            return;
        }

        setIsDeleting(true);
        setErrorMessage('');

        try {
            const response = await fetch(`/api/admin/menu-settings/${selectedMenu.seq}`, {
                method: 'DELETE'
            });

            if (!response.ok) {
                const message = await response.text();
                throw new Error(message || '메뉴 삭제에 실패했습니다.');
            }

            setSelectedMenuSeq(null);
            setReloadKey((value) => value + 1);
            onSaved?.();
            onNotify('success', '메뉴가 삭제되었습니다.');
        } catch (error) {
            const message = error instanceof Error ? error.message : '메뉴 삭제에 실패했습니다.';
            setErrorMessage(message);
            onNotify('error', message);
        } finally {
            setIsDeleting(false);
        }
    };

    const handleOrderSave = async () => {
        setIsOrderSaving(true);
        setErrorMessage('');

        try {
            const response = await fetch(`/api/admin/menu-settings/reorder?language=${encodeURIComponent(language)}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    items: buildReorderPayload(menus)
                })
            });

            if (!response.ok) {
                const message = await response.text();
                throw new Error(message || '메뉴 정렬 저장에 실패했습니다.');
            }

            const data = normalizeMenuTree(await response.json() as MenuNode[]);
            setMenus(data);
            setHasOrderChanges(false);
            setDraggedNodeId(null);
            setDragPreview(null);
            onSaved?.();
            onNotify('success', '메뉴 정렬이 저장되었습니다.');
        } catch (error) {
            const message = error instanceof Error ? error.message : '메뉴 정렬 저장에 실패했습니다.';
            setErrorMessage(message);
            onNotify('error', message);
        } finally {
            setIsOrderSaving(false);
        }
    };

    const handleOrderReset = () => {
        setReloadKey((value) => value + 1);
    };

    const handleDragStart = (node: MenuNode) => {
        if (node.menuKey === 'root') {
            return;
        }
        setDraggedNodeId(nodeId(node));
        setDragPreview(null);
    };

    const handleDragOver = (node: MenuNode, event: ReactDragEvent<HTMLDivElement>) => {
        if (!draggedNodeId) {
            return;
        }

        event.preventDefault();
        const draggedNode = findNodeById(menus, draggedNodeId);
        if (!draggedNode) {
            return;
        }

        const nextPosition = resolveDropPosition(node, event);
        const nextPreview = {
            draggedId: draggedNodeId,
            targetId: nodeId(node),
            position: nextPosition
        };

        setDragPreview((previous) => (
            previous?.draggedId === nextPreview.draggedId
                && previous.targetId === nextPreview.targetId
                && previous.position === nextPreview.position
                ? previous
                : nextPreview
        ));
    };

    const handleDrop = (node: MenuNode, event: ReactDragEvent<HTMLDivElement>) => {
        event.preventDefault();

        if (!draggedNodeId) {
            return;
        }

        const dropPosition = dragPreview?.targetId === nodeId(node)
            ? dragPreview.position
            : resolveDropPosition(node, event);
        const nextMenus = moveMenuNode(menus, draggedNodeId, nodeId(node), dropPosition);

        setDraggedNodeId(null);
        setDragPreview(null);

        if (!nextMenus) {
            onNotify('error', '이 위치로는 메뉴를 이동할 수 없습니다.');
            return;
        }

        setMenus(nextMenus);
        setHasOrderChanges(true);
        setExpandedKeys((previous) => ({
            ...previous,
            [nodeId(node)]: true
        }));
    };

    const handleDragEnd = () => {
        setDraggedNodeId(null);
        setDragPreview(null);
    };

    return (
        <section className="bg-white dark:bg-slate-950 rounded-xl border border-slate-200 dark:border-slate-800 shadow-sm">
            <div className="p-4 md:p-5 border-b border-slate-200 dark:border-slate-800 flex flex-col sm:flex-row sm:items-start justify-between gap-3">
                <div>
                    <div className="flex items-center gap-2">
                        <ListTree className="shrink-0 h-4 w-4 text-slate-400" />
                        <h3 className="font-semibold text-sm md:text-base">메뉴 관리</h3>
                    </div>
                    <p className="text-xs text-slate-400 mt-1">관리자 화면과 사용자 화면에서 사용하는 메뉴를 트리 구조로 관리합니다.</p>
                    <p className="text-xs text-amber-600 dark:text-amber-300 mt-1">드래그로 정렬을 바꾸고 저장하면 관리자 GNB 3단계 메뉴에도 바로 반영됩니다.</p>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                    {hasOrderChanges && (
                        <button
                            type="button"
                            onClick={handleOrderReset}
                            disabled={isOrderSaving}
                            className="inline-flex items-center justify-center gap-2 px-3 py-2 rounded-lg text-xs font-semibold border border-slate-200 dark:border-slate-800 text-slate-600 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-900 disabled:opacity-50"
                        >
                            <RefreshCw className="w-4 h-4" />
                            정렬 되돌리기
                        </button>
                    )}
                    <button
                        type="button"
                        onClick={handleOrderSave}
                        disabled={!hasOrderChanges || isOrderSaving}
                        className="inline-flex items-center justify-center gap-2 px-3 py-2 rounded-lg text-xs font-semibold bg-emerald-600 text-white hover:bg-emerald-700 disabled:opacity-50"
                    >
                        <Save className="w-4 h-4" />
                        {isOrderSaving ? '정렬 저장 중' : '정렬 저장'}
                    </button>
                    <button
                        type="button"
                        onClick={handleStartCreate}
                        className="inline-flex items-center justify-center gap-2 px-3 py-2 rounded-lg text-xs font-semibold bg-blue-600 text-white hover:bg-blue-700"
                    >
                        <Plus className="w-4 h-4" />
                        메뉴 추가
                    </button>
                </div>
            </div>

            {errorMessage && (
                <div className="m-4 md:m-5 rounded-lg border border-rose-200 dark:border-rose-900/60 bg-rose-50 dark:bg-rose-950/30 px-4 py-3 text-sm text-rose-700 dark:text-rose-300">
                    {errorMessage}
                </div>
            )}

            <div className="grid grid-cols-1 lg:grid-cols-[360px_minmax(0,1fr)]">
                <div className="border-b lg:border-b-0 lg:border-r border-slate-200 dark:border-slate-800 p-4 md:p-5">
                    <div className="rounded-lg border border-slate-200 dark:border-slate-800 overflow-hidden">
                        <div className="px-4 py-3 bg-slate-50 dark:bg-slate-900/50 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between gap-2">
                            <div className="flex items-center gap-2">
                                <Settings2 className="w-4 h-4 text-slate-400" />
                                <span className="text-sm font-semibold">메뉴 트리</span>
                            </div>
                            {hasOrderChanges && (
                                <span className="text-[11px] font-semibold text-amber-600 dark:text-amber-300">정렬 변경 있음</span>
                            )}
                        </div>
                        <div className="p-2 min-h-[420px]">
                            {isLoading && menus.length === 0 && <p className="p-4 text-sm text-slate-400">메뉴를 불러오는 중입니다.</p>}
                            {!isLoading && menus.length === 0 && <p className="p-4 text-sm text-slate-400">표시할 메뉴가 없습니다.</p>}
                            {menus.map((node) => (
                                <MenuTreeNode
                                    key={nodeId(node)}
                                    node={node}
                                    depth={0}
                                    selectedSeq={isCreateMode ? undefined : selectedMenuSeq ?? undefined}
                                    expandedKeys={expandedKeys}
                                    draggedNodeId={draggedNodeId}
                                    dragPreview={dragPreview}
                                    onToggle={handleToggle}
                                    onSelect={handleSelect}
                                    onDragStart={handleDragStart}
                                    onDragOver={handleDragOver}
                                    onDrop={handleDrop}
                                    onDragEnd={handleDragEnd}
                                />
                            ))}
                        </div>
                    </div>
                </div>

                <form onSubmit={handleSave} className="min-w-0 p-4 md:p-5 space-y-5">
                    {!isCreateMode && !selectedMenu ? (
                        <div className="h-full min-h-[420px] flex items-center justify-center text-sm text-slate-400">
                            설정할 메뉴를 선택하거나 메뉴를 추가하세요.
                        </div>
                    ) : (
                        <>
                            {isUserMenu && <div className="space-y-2 rounded-lg border border-slate-200 bg-slate-50 p-3 dark:border-slate-800 dark:bg-slate-900">
                                <label className="block space-y-1.5 text-sm font-semibold text-slate-700 dark:text-slate-200"><span>편집 언어</span>
                                    <SelectField value={language} disabled={isSaving || isDeleting || isLoading} onChange={event => void handleLanguageChange(event.target.value)}>
                                        {supportedLanguages.map(code => <option key={code} value={code}>{code === 'ko' ? '국문 (ko)' : code === 'en' ? '영문 (en)' : code}</option>)}
                                    </SelectField>
                                </label>
                                <p className="text-xs text-slate-500 dark:text-slate-400">메뉴명과 HTML은 선택한 언어에만 저장됩니다. 경로·순서·노출 설정은 모든 언어에 공통으로 적용됩니다.</p>
                                {!isCreateMode && selectedMenu?.translationReady === false && <p className="text-xs font-semibold text-amber-700 dark:text-amber-300">이 언어의 메뉴가 아직 작성되지 않았습니다. 사용자 화면에는 준비 중으로 표시됩니다.</p>}
                            </div>}
                            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-4 border-b border-slate-200 dark:border-slate-800">
                                <div>
                                    <p className="text-xs text-slate-400 font-medium">{isCreateMode ? '신규 메뉴' : '선택 메뉴'}</p>
                                    <h4 className="font-bold text-base mt-1">
                                        {isCreateMode ? '메뉴 추가' : selectedPathLabel}
                                    </h4>
                                </div>
                                <div className="flex flex-wrap items-center gap-2">
                                    <span className={`inline-flex w-fit items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold border ${
                                        enabled
                                            ? 'bg-emerald-50 text-emerald-700 border-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-300 dark:border-emerald-900/60'
                                            : 'bg-slate-100 text-slate-500 border-slate-200 dark:bg-slate-900 dark:text-slate-400 dark:border-slate-800'
                                    }`}>
                                        <CheckCircle2 className="w-3.5 h-3.5" />
                                        {enabled ? '사용' : '미사용'}
                                    </span>
                                    {isCreateMode && (
                                        <button
                                            type="button"
                                            onClick={handleCancelCreate}
                                            className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold border border-slate-200 dark:border-slate-800 text-slate-500 hover:bg-slate-50 dark:hover:bg-slate-900"
                                        >
                                            <X className="w-3.5 h-3.5" />
                                            취소
                                        </button>
                                    )}
                                </div>
                            </div>

                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                <div>
                                    <label className="block text-xs font-semibold text-slate-500 uppercase mb-1.5">메뉴 영역</label>
                                    {isCreateMode ? (
                                            <SelectField
                                                value={menuScope}
                                                onChange={(event) => {
                                                    setMenuScope(event.target.value as MenuScope);
                                                    setParentKey('');
                                                }}
                                                className="dark:bg-slate-900"
                                            >
                                            <option value="admin">관리자</option>
                                            <option value="user">사용자</option>
                                        </SelectField>
                                    ) : (
                                        <input
                                            value={scopeLabels[menuScope]}
                                            disabled
                                            className="w-full px-3 py-2 rounded-lg border bg-slate-50 dark:bg-slate-900 text-sm text-slate-500 border-slate-200 dark:border-slate-800"
                                        />
                                    )}
                                </div>
                                <div>
                                    <label className="block text-xs font-semibold text-slate-500 uppercase mb-1.5">메뉴 키</label>
                                    <input
                                        value={menuKey}
                                        onChange={(event) => setMenuKey(event.target.value)}
                                        disabled={!isCreateMode}
                                        required
                                        placeholder="예: popup"
                                        className="w-full px-3 py-2 rounded-lg border bg-white dark:bg-slate-900 text-sm text-slate-900 dark:text-slate-50 border-slate-200 dark:border-slate-800 focus:outline-none focus:border-blue-500 disabled:bg-slate-50 disabled:dark:bg-slate-900 disabled:text-slate-500"
                                    />
                                </div>
                            </div>

                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                <div>
                                    <label className="block text-xs font-semibold text-slate-500 uppercase mb-1.5">상위 메뉴</label>
                                    {isCreateMode ? (
                                        <SelectField
                                            value={parentKey}
                                            onChange={(event) => setParentKey(event.target.value)}
                                            className="dark:bg-slate-900"
                                        >
                                            <option value="">최상위 메뉴</option>
                                            {parentOptions.map((menu) => (
                                                <option key={nodeId(menu)} value={menu.menuKey}>
                                                    {menu.menuName} ({menu.menuKey})
                                                </option>
                                            ))}
                                        </SelectField>
                                    ) : (
                                        <input
                                            value={parentKey || '최상위 메뉴'}
                                            disabled
                                            className="w-full px-3 py-2 rounded-lg border bg-slate-50 dark:bg-slate-900 text-sm text-slate-500 border-slate-200 dark:border-slate-800"
                                        />
                                    )}
                                </div>
                                <div>
                                    <label className="block text-xs font-semibold text-slate-500 uppercase mb-1.5">정렬 순서</label>
                                    <input
                                        type="number"
                                        value={sortOrder}
                                        onChange={(event) => setSortOrder(event.target.value)}
                                        disabled={!isCreateMode}
                                        className="w-full px-3 py-2 rounded-lg border bg-white dark:bg-slate-900 text-sm text-slate-900 dark:text-slate-50 border-slate-200 dark:border-slate-800 focus:outline-none focus:border-blue-500 disabled:bg-slate-50 disabled:dark:bg-slate-900 disabled:text-slate-500"
                                    />
                                </div>
                            </div>

                            <div>
                                <label className="block text-xs font-semibold text-slate-500 uppercase mb-1.5">메뉴명</label>
                                <input
                                    value={menuName}
                                    onChange={(event) => setMenuName(event.target.value)}
                                    className="w-full px-3 py-2 rounded-lg border bg-white dark:bg-slate-900 text-sm text-slate-900 dark:text-slate-50 border-slate-200 dark:border-slate-800 focus:outline-none focus:border-blue-500"
                                    required
                                />
                            </div>

                            <div className="space-y-4 rounded-lg border border-slate-200 dark:border-slate-800 p-4 bg-slate-50/70 dark:bg-slate-900/40">
                                <div className="flex items-center justify-between gap-3">
                                    <div>
                                        <h5 className="font-semibold text-sm">메뉴 경로와 동작</h5>
                                        <p className="text-xs text-slate-400 mt-1 dark:text-slate-400">메뉴 주소, 유형과 열기 방식을 설정합니다.</p>
                                    </div>
                                    <span className="inline-flex items-center rounded-full border border-slate-200 dark:border-slate-800 px-2.5 py-1 text-[11px] font-semibold text-slate-500 dark:text-slate-300">
                                        depth {displayDepth}
                                    </span>
                                </div>

                                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                    <div>
                                        <label className="block text-xs font-semibold text-slate-500 uppercase mb-1.5">메뉴 경로</label>
                                        <input
                                            value={menuPath}
                                            onChange={(event) => setMenuPath(event.target.value)}
                                            placeholder={isUserMenu ? '예: communityboard' : '예: popup'}
                                            className="w-full px-3 py-2 rounded-lg border bg-white dark:bg-slate-950 text-sm text-slate-900 dark:text-slate-50 border-slate-200 dark:border-slate-800 focus:outline-none focus:border-blue-500"
                                        />
                                    </div>
                                    <div>
                                        <label className="block text-xs font-semibold text-slate-500 uppercase mb-1.5 dark:text-slate-400">{isUserMenu ? '페이지 경로' : '최종 URL'}</label>
                                        <input
                                            value={routePath}
                                            onChange={(event) => setRoutePath(event.target.value)}
                                            placeholder={isUserMenu ? '예: /welcome-message' : '관리자 메뉴는 비워둘 수 있음'}
                                            className="w-full px-3 py-2 rounded-lg border bg-white dark:bg-slate-950 text-sm text-slate-900 dark:text-slate-50 border-slate-200 dark:border-slate-800 focus:outline-none focus:border-blue-500"
                                        />
                                        {isUserMenu && <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">학회·언어 경로는 자동으로 붙습니다. 상위 메뉴 폴더는 입력하지 않습니다.</p>}
                                    </div>
                                </div>

                                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                    <div>
                                        <label className="block text-xs font-semibold text-slate-500 uppercase mb-1.5">메뉴 유형</label>
                                        <SelectField
                                            value={menuType}
                                            onChange={(event) => setMenuType(event.target.value as MenuType)}
                                        >
                                            {Object.entries(menuTypeLabels).map(([value, label]) => (
                                                <option key={value} value={value}>{label}</option>
                                            ))}
                                        </SelectField>
                                    </div>
                                </div>

                                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                    <div>
                                        <label className="block text-xs font-semibold text-slate-500 uppercase mb-1.5">링크 타겟</label>
                                        <SelectField
                                            value={targetType}
                                            onChange={(event) => setTargetType(event.target.value as TargetType)}
                                        >
                                            {Object.entries(targetTypeLabels).map(([value, label]) => (
                                                <option key={value} value={value}>{label}</option>
                                            ))}
                                        </SelectField>
                                    </div>
                                    <div>
                                        <label className="block text-xs font-semibold text-slate-500 uppercase mb-1.5">현재 depth</label>
                                        <input
                                            value={String(displayDepth)}
                                            disabled
                                            className="w-full px-3 py-2 rounded-lg border bg-slate-100 dark:bg-slate-900 text-sm text-slate-500 border-slate-200 dark:border-slate-800"
                                        />
                                    </div>
                                </div>

                                {(menuType === 'board' || menuType === 'link') && (
                                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                        {menuType === 'board' ? (
                                            <div>
                                                <label className="block text-xs font-semibold text-slate-500 uppercase mb-1.5">게시판 ID</label>
                                                <input
                                                    type="number"
                                                    value={boardSeq}
                                                    onChange={(event) => setBoardSeq(event.target.value)}
                                                    className="w-full px-3 py-2 rounded-lg border bg-white dark:bg-slate-950 text-sm text-slate-900 dark:text-slate-50 border-slate-200 dark:border-slate-800 focus:outline-none focus:border-blue-500"
                                                />
                                            </div>
                                        ) : (
                                            <div>
                                                <label className="block text-xs font-semibold text-slate-500 uppercase mb-1.5">링크 URL</label>
                                                <input
                                                    value={linkUrl}
                                                    onChange={(event) => setLinkUrl(event.target.value)}
                                                    placeholder="https://example.com"
                                                    className="w-full px-3 py-2 rounded-lg border bg-white dark:bg-slate-950 text-sm text-slate-900 dark:text-slate-50 border-slate-200 dark:border-slate-800 focus:outline-none focus:border-blue-500"
                                                />
                                            </div>
                                        )}
                                        <div>
                                            <label className="block text-xs font-semibold text-slate-500 uppercase mb-1.5">설명</label>
                                            <input
                                                value={menuType === 'board' ? 'boardSeq 사용' : 'linkUrl 사용'}
                                                disabled
                                                className="w-full px-3 py-2 rounded-lg border bg-slate-100 dark:bg-slate-900 text-sm text-slate-500 border-slate-200 dark:border-slate-800"
                                            />
                                        </div>
                                    </div>
                                )}

                                {isUserMenu && (
                                    <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                                        <label className="flex cursor-pointer items-center gap-3 rounded-lg border border-slate-200 bg-white px-4 py-3 dark:border-slate-800 dark:bg-slate-950">
                                            <input
                                                type="checkbox"
                                                checked={authRequired}
                                                onChange={(event) => setAuthRequired(event.target.checked)}
                                                className="h-4 w-4 rounded"
                                            />
                                            <span className="text-sm font-medium text-slate-700 dark:text-slate-300">로그인 필요 여부</span>
                                        </label>
                                        <label className="flex cursor-pointer items-center gap-3 rounded-lg border border-slate-200 bg-white px-4 py-3 dark:border-slate-800 dark:bg-slate-950">
                                            <input
                                                type="checkbox"
                                                checked={navigationVisible}
                                                onChange={(event) => setNavigationVisible(event.target.checked)}
                                                className="h-4 w-4 rounded"
                                            />
                                            <span className="text-sm font-medium text-slate-700 dark:text-slate-300">내비게이션 메뉴 노출</span>
                                        </label>
                                    </div>
                                )}

                                {isUserMenu && (menuType === 'page' || menuType === 'folder') && (
                                    <div role="group" aria-labelledby="menu-html-label">
                                        <div className="mb-1.5 flex flex-wrap items-center justify-between gap-2">
                                            <div id="menu-html-label" className="text-sm font-semibold text-slate-700 dark:text-slate-200">메뉴 HTML {!isCreateMode && <span className="ml-2 text-xs font-normal text-slate-500 dark:text-slate-400">현재 v{selectedMenu?.htmlRevisionNo ?? 0}</span>}</div>
                                            {!isCreateMode && selectedMenu && <button type="button" onClick={() => setHistoryOpen(true)} disabled={isSaving || isDeleting || isLoading} className="inline-flex items-center justify-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900"><History className="h-4 w-4" />변경 이력</button>}
                                        </div>
                                        <CkEditorRichTextEditor
                                            ref={menuHtmlEditorRef}
                                            key={`${language}:${isCreateMode ? 'new' : selectedMenuSeq}:${selectedMenu?.htmlRevisionNo ?? 0}:${editorGeneration}`}
                                            initialContent={menuHtml}
                                            onChange={setMenuHtml}
                                            uploadUrl="/api/admin/boards/images"
                                            previewTitle="메뉴 HTML 미리보기"
                                            contentStyle="public-menu"
                                            allowYoutube
                                        />
                                        <label className="mt-3 block space-y-1.5 text-sm font-semibold text-slate-700 dark:text-slate-200">HTML 변경 메모 <span className="text-xs font-normal text-slate-500 dark:text-slate-400">(선택 · HTML 변경 시에만 이력 저장)</span><input value={changeMemo} onChange={(event) => setChangeMemo(event.target.value)} maxLength={500} className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm font-normal text-slate-900 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50" /></label>
                                    </div>
                                )}
                            </div>

                            {isUserMenu && (
                                <div className="rounded-lg border border-slate-200 dark:border-slate-800 p-4 bg-slate-50/70 dark:bg-slate-900/40">
                                    <div className="flex items-center gap-2 mb-3">
                                        <CalendarRange className="w-4 h-4 text-blue-500" />
                                        <h5 className="font-semibold text-sm">사용 기간</h5>
                                    </div>
                                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                        <div>
                                            <label className="block text-xs font-semibold text-slate-500 uppercase mb-1.5">시작일</label>
                                            <input
                                                type="date"
                                                value={useStartDate}
                                                onChange={(event) => setUseStartDate(event.target.value)}
                                                className="w-full px-3 py-2 rounded-lg border bg-white dark:bg-slate-950 text-sm text-slate-900 dark:text-slate-50 border-slate-200 dark:border-slate-800 focus:outline-none focus:border-blue-500"
                                            />
                                        </div>
                                        <div>
                                            <label className="block text-xs font-semibold text-slate-500 uppercase mb-1.5">종료일</label>
                                            <input
                                                type="date"
                                                value={useEndDate}
                                                onChange={(event) => setUseEndDate(event.target.value)}
                                                className="w-full px-3 py-2 rounded-lg border bg-white dark:bg-slate-950 text-sm text-slate-900 dark:text-slate-50 border-slate-200 dark:border-slate-800 focus:outline-none focus:border-blue-500"
                                            />
                                        </div>
                                    </div>
                                </div>
                            )}

                            <label className="flex items-center gap-3 rounded-lg border border-slate-200 dark:border-slate-800 px-4 py-3 cursor-pointer">
                                <input
                                    type="checkbox"
                                    checked={enabled}
                                    onChange={(event) => setEnabled(event.target.checked)}
                                    className="w-4 h-4 rounded"
                                />
                                <span className="text-sm font-medium text-slate-700 dark:text-slate-300">사용 여부</span>
                            </label>

                            <div className="pt-4 border-t border-slate-200 dark:border-slate-800 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                                {!isCreateMode && selectedMenu && (
                                    <button
                                        type="button"
                                        onClick={handleDelete}
                                        disabled={isDeleting || isSaving}
                                        className="inline-flex items-center justify-center gap-2 px-4 py-2 rounded-lg text-xs font-semibold border border-rose-200 dark:border-rose-900/70 text-rose-600 dark:text-rose-300 hover:bg-rose-50 dark:hover:bg-rose-950/30 disabled:opacity-50"
                                    >
                                        <Trash2 className="w-4 h-4" />
                                        {isDeleting ? '삭제 중' : '삭제'}
                                    </button>
                                )}
                                <button
                                    type="submit"
                                    disabled={isSaving || isDeleting}
                                    className="inline-flex items-center justify-center gap-2 px-4 py-2 rounded-lg text-xs font-semibold bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50 sm:ml-auto"
                                >
                                    <Save className="w-4 h-4" />
                                    {isSaving ? '저장 중' : isCreateMode ? '등록' : '저장'}
                                </button>
                            </div>
                        </>
                    )}
                </form>
            </div>
            {historyOpen && selectedMenu && !isCreateMode && <MenuHtmlHistoryModal key={`${selectedMenu.seq}-${language}`} language={language} menuSeq={selectedMenu.seq} menuName={selectedMenu.menuName} canRestore={canRestoreHtml} onClose={() => setHistoryOpen(false)} onRestored={handleHtmlRestored} onNotify={onNotify} />}
        </section>
    );
};

interface MenuTreeNodeProps {
    node: MenuNode;
    depth: number;
    selectedSeq?: number;
    expandedKeys: Record<string, boolean>;
    draggedNodeId: string | null;
    dragPreview: DragPreview | null;
    onToggle: (node: MenuNode) => void;
    onSelect: (node: MenuNode) => void;
    onDragStart: (node: MenuNode) => void;
    onDragOver: (node: MenuNode, event: ReactDragEvent<HTMLDivElement>) => void;
    onDrop: (node: MenuNode, event: ReactDragEvent<HTMLDivElement>) => void;
    onDragEnd: () => void;
}

const MenuTreeNode = ({
    node,
    depth,
    selectedSeq,
    expandedKeys,
    draggedNodeId,
    dragPreview,
    onToggle,
    onSelect,
    onDragStart,
    onDragOver,
    onDrop,
    onDragEnd
}: MenuTreeNodeProps) => {
    const hasChildren = Boolean(node.children?.length);
    const expanded = expandedKeys[nodeId(node)] ?? false;
    const selected = selectedSeq === node.seq;
    const isDragging = draggedNodeId === nodeId(node);
    const isDropTarget = dragPreview?.targetId === nodeId(node);
    const isRoot = node.menuKey === 'root';

    return (
        <div>
            <div
                draggable={!isRoot}
                onDragStart={() => onDragStart(node)}
                onDragOver={(event) => onDragOver(node, event)}
                onDrop={(event) => onDrop(node, event)}
                onDragEnd={onDragEnd}
                className={`relative rounded-md ${
                    isDropTarget && dragPreview?.position === 'before' ? 'before:absolute before:left-2 before:right-2 before:top-0 before:border-t-2 before:border-blue-500 before:content-[\'\']' : ''
                } ${
                    isDropTarget && dragPreview?.position === 'after' ? 'after:absolute after:left-2 after:right-2 after:bottom-0 after:border-b-2 after:border-blue-500 after:content-[\'\']' : ''
                }`}
            >
                <button
                    type="button"
                    onClick={() => onSelect(node)}
                    onDoubleClick={() => {
                        if (hasChildren) {
                            onToggle(node);
                        }
                    }}
                    aria-expanded={hasChildren ? expanded : undefined}
                    className={`w-full flex items-center gap-2 rounded-md px-2 py-2 text-left text-sm transition-colors ${
                        isDropTarget && dragPreview?.position === 'inside'
                            ? 'ring-1 ring-blue-500 bg-blue-50/70 dark:bg-blue-950/30'
                            : ''
                    } ${
                        selected
                            ? 'bg-blue-50 text-blue-700 dark:bg-blue-950/40 dark:text-blue-300'
                            : 'text-slate-600 hover:bg-slate-50 dark:text-slate-300 dark:hover:bg-slate-900'
                    } ${isDragging ? 'opacity-50' : ''}`}
                    style={{ paddingLeft: `${8 + depth * 18}px` }}
                >
                    {!isRoot ? <GripVertical className="w-4 h-4 text-slate-300 dark:text-slate-600 cursor-grab" /> : <span className="w-4 h-4" />}
                    {hasChildren ? (
                        <span
                            onClick={(event) => {
                                event.stopPropagation();
                                onToggle(node);
                            }}
                            className="rounded hover:bg-slate-100 dark:hover:bg-slate-800"
                        >
                            {expanded ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
                        </span>
                    ) : (
                        <span className="w-4 h-4" />
                    )}
                    <span className="flex-1 truncate font-medium">{node.menuName}{node.translationReady === false && <span className="ml-1 text-xs text-amber-700 dark:text-amber-300">· 미작성</span>}</span>
                    {node.menuType && (
                        <span className="rounded bg-slate-100 dark:bg-slate-900 px-1.5 py-0.5 text-[10px] text-slate-500 dark:text-slate-300">
                            {node.menuType}
                        </span>
                    )}
                    <span className="text-[10px] text-slate-400">{scopeLabels[node.menuScope]}</span>
                    <span className={`w-2 h-2 rounded-full ${node.enabled ? 'bg-emerald-500' : 'bg-slate-300 dark:bg-slate-700'}`} />
                </button>
            </div>
            {hasChildren && expanded && node.children?.map((child) => (
                <MenuTreeNode
                    key={nodeId(child)}
                    node={child}
                    depth={depth + 1}
                    selectedSeq={selectedSeq}
                    expandedKeys={expandedKeys}
                    draggedNodeId={draggedNodeId}
                    dragPreview={dragPreview}
                    onToggle={onToggle}
                    onSelect={onSelect}
                    onDragStart={onDragStart}
                    onDragOver={onDragOver}
                    onDrop={onDrop}
                    onDragEnd={onDragEnd}
                />
            ))}
        </div>
    );
};

const nodeId = (node: Pick<MenuNode, 'menuScope' | 'menuKey'>) => `${node.menuScope}:${node.menuKey}`;

const findNodeBySeq = (nodes: MenuNode[], seq: number): MenuNode | null => {
    for (const node of nodes) {
        if (node.seq === seq) {
            return node;
        }

        const found = findNodeBySeq(node.children ?? [], seq);
        if (found) {
            return found;
        }
    }

    return null;
};

const findNodeById = (nodes: MenuNode[], id: string): MenuNode | null => {
    for (const node of nodes) {
        if (nodeId(node) === id) {
            return node;
        }

        const found = findNodeById(node.children ?? [], id);
        if (found) {
            return found;
        }
    }

    return null;
};

const flattenMenus = (nodes: MenuNode[]): MenuNode[] => {
    const result: MenuNode[] = [];

    for (const node of nodes) {
        result.push(node);
        result.push(...flattenMenus(node.children ?? []));
    }

    return result;
};

const cloneMenus = (nodes: MenuNode[]): MenuNode[] => (
    nodes.map((node) => ({
        ...node,
        children: cloneMenus(node.children ?? [])
    }))
);

const removeNodeById = (nodes: MenuNode[], id: string): MenuNode | null => {
    for (let index = 0; index < nodes.length; index += 1) {
        if (nodeId(nodes[index]) === id) {
            return nodes.splice(index, 1)[0];
        }

        const removed = removeNodeById(nodes[index].children ?? [], id);
        if (removed) {
            return removed;
        }
    }

    return null;
};

const insertNodeByTarget = (nodes: MenuNode[], movingNode: MenuNode, targetId: string, position: DropPosition): boolean => {
    for (let index = 0; index < nodes.length; index += 1) {
        const current = nodes[index];
        const currentId = nodeId(current);

        if (currentId === targetId) {
            if (position === 'inside') {
                movingNode.parentKey = current.menuKey;
                current.children = current.children ?? [];
                current.children.push(movingNode);
                return true;
            }

            movingNode.parentKey = current.parentKey ?? null;
            nodes.splice(position === 'before' ? index : index + 1, 0, movingNode);
            return true;
        }

        if (insertNodeByTarget(current.children ?? [], movingNode, targetId, position)) {
            return true;
        }
    }

    return false;
};

const normalizeMenuTree = (nodes: MenuNode[]): MenuNode[] => (
    nodes.map((node, index) => ({
        ...node,
        parentKey: node.parentKey ?? null,
        sortOrder: index * 10,
        children: normalizeMenuTree(node.children ?? [])
    }))
);

const resolveDropPosition = (node: MenuNode, event: ReactDragEvent<HTMLDivElement>): DropPosition => {
    if (node.menuKey === 'root') {
        return 'inside';
    }

    const rect = event.currentTarget.getBoundingClientRect();
    const pointerY = event.clientY - rect.top;
    if (pointerY < rect.height / 3) {
        return 'before';
    }
    if (pointerY > rect.height * 2 / 3) {
        return 'after';
    }
    return 'inside';
};

const buildReorderPayload = (nodes: MenuNode[]): Array<{ seq: number; parentKey: string | null; sortOrder: number }> => {
    const result: Array<{ seq: number; parentKey: string | null; sortOrder: number }> = [];

    const traverse = (items: MenuNode[]) => {
        items.forEach((node, index) => {
            if (node.menuKey !== 'root') {
                result.push({
                    seq: node.seq,
                    parentKey: node.parentKey ?? null,
                    sortOrder: index * 10
                });
            }
            traverse(node.children ?? []);
        });
    };

    traverse(nodes);
    return result;
};

const getMenuDepth = (target: MenuNode, roots: MenuNode[]): number => {
    const visit = (nodes: MenuNode[], depth: number): number | null => {
        for (const node of nodes) {
            if (node.seq === target.seq) {
                return node.menuKey === 'root' ? 0 : depth;
            }

            const found = visit(node.children ?? [], node.menuKey === 'root' ? 1 : depth + 1);
            if (found != null) {
                return found;
            }
        }

        return null;
    };

    return visit(roots, 0) ?? 0;
};

const getMenuDepthByKey = (menuKey: string, roots: MenuNode[]): number => {
    const target = flattenMenus(roots).find((menu) => menu.menuKey === menuKey);
    return target ? getMenuDepth(target, roots) : 0;
};

const isDescendant = (node: MenuNode, targetId: string): boolean => {
    return (node.children ?? []).some((child) => nodeId(child) === targetId || isDescendant(child, targetId));
};

const getMaxDepth = (nodes: MenuNode[], depth: number): number => {
    // Empty child lists do not add another menu level.
    let maxDepth = 0;

    for (const node of nodes) {
        const nextDepth = node.menuKey === 'root' ? 0 : depth;
        maxDepth = Math.max(maxDepth, nextDepth);
        maxDepth = Math.max(maxDepth, getMaxDepth(node.children ?? [], node.menuKey === 'root' ? 1 : depth + 1));
    }

    return maxDepth;
};

const moveMenuNode = (sourceNodes: MenuNode[], draggedId: string, targetId: string, rawPosition: DropPosition): MenuNode[] | null => {
    if (draggedId === targetId) {
        return null;
    }

    const draggedNode = findNodeById(sourceNodes, draggedId);
    const targetNode = findNodeById(sourceNodes, targetId);
    if (!draggedNode || !targetNode) {
        return null;
    }
    if (draggedNode.menuScope !== targetNode.menuScope) {
        return null;
    }
    if (isDescendant(draggedNode, targetId)) {
        return null;
    }

    const nextNodes = cloneMenus(sourceNodes);
    const nextDraggedNode = findNodeById(nextNodes, draggedId);
    const nextTargetNode = findNodeById(nextNodes, targetId);
    if (!nextDraggedNode || !nextTargetNode) {
        return null;
    }

    const position = nextTargetNode.menuKey === 'root' ? 'inside' : rawPosition;
    const removedNode = removeNodeById(nextNodes, draggedId);
    if (!removedNode) {
        return null;
    }

    const inserted = insertNodeByTarget(nextNodes, removedNode, targetId, position);
    if (!inserted) {
        return null;
    }

    const normalized = normalizeMenuTree(nextNodes);
    if (getMaxDepth(normalized, 0) > MAX_MENU_DEPTH_BY_SCOPE[draggedNode.menuScope]) {
        return null;
    }

    return normalized;
};
