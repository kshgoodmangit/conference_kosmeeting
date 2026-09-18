import { DraggableModal } from './DraggableModal';
import { useEffect, useRef, useState } from 'react';
import {
    CalendarDays,
    ChevronLeft,
    ChevronRight,
    Image,
    Pencil,
    Plus,
    Search,
    Trash2,
    Upload,
    X
} from 'lucide-react';
import type { NotificationType } from './NotificationToast';
import { CkEditorRichTextEditor } from './CkEditorRichTextEditor';
import { useConfirm } from './confirmDialogContext';

interface PopupItem {
    seq: number;
    title: string;
    enabled: boolean;
    useStartDate?: string | null;
    useEndDate?: string | null;
    content?: string | null;
    popupImageOriFilename?: string | null;
    imageUrl?: string | null;
    createdAt?: string | null;
    updatedAt?: string | null;
    webRiskWarning?: string | null;
}

interface PopupPageResponse {
    items: PopupItem[];
    page: number;
    size: number;
    totalCount: number;
    enabledCount: number;
    totalPages: number;
}

interface PopupPageProps {
    onNotify: (type: NotificationType, message: string) => void;
}

interface CkEditorImageUploadResponse {
    uploaded?: number;
    fileName?: string;
    url?: string;
    error?: {
        message?: string;
    };
}

const PAGE_SIZE = 10;
const DROPPABLE_IMAGE_EXTENSIONS = ['jpg', 'jpeg', 'png', 'gif'];
const dateTimeFormatter = new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
});

export const PopupPage = ({ onNotify }: PopupPageProps) => {
    const confirm = useConfirm();
    const [items, setItems] = useState<PopupItem[]>([]);
    const [searchKeyword, setSearchKeyword] = useState('');
    const [currentPage, setCurrentPage] = useState(1);
    const [totalCount, setTotalCount] = useState(0);
    const [enabledCount, setEnabledCount] = useState(0);
    const [totalPages, setTotalPages] = useState(1);
    const [isLoading, setIsLoading] = useState(false);
    const [errorMessage, setErrorMessage] = useState('');
    const [reloadKey, setReloadKey] = useState(0);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [selectedPopup, setSelectedPopup] = useState<PopupItem | null>(null);
    const [createInitialContent, setCreateInitialContent] = useState('');
    const [isImageDragActive, setIsImageDragActive] = useState(false);
    const [isDroppedImageUploading, setIsDroppedImageUploading] = useState(false);
    const onNotifyRef = useRef(onNotify);
    const dragDepthRef = useRef(0);

    useEffect(() => {
        onNotifyRef.current = onNotify;
    }, [onNotify]);

    useEffect(() => {
        const controller = new AbortController();

        const load = async () => {
            setIsLoading(true);
            setErrorMessage('');

            try {
                const params = new URLSearchParams({
                    page: String(currentPage),
                    size: String(PAGE_SIZE),
                    keyword: searchKeyword.trim()
                });
                const response = await fetch(`/api/admin/popups?${params.toString()}`, { signal: controller.signal });
                if (!response.ok) {
                    throw new Error(await response.text() || '팝업 목록을 불러오지 못했습니다.');
                }

                const data = await response.json() as PopupPageResponse;
                setItems(data.items);
                setTotalCount(data.totalCount);
                setEnabledCount(data.enabledCount);
                setTotalPages(data.totalPages);
                if (data.page !== currentPage) {
                    setCurrentPage(data.page);
                }
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') {
                    return;
                }
                const message = error instanceof Error ? error.message : '팝업 목록을 불러오지 못했습니다.';
                setErrorMessage(message);
                onNotifyRef.current('error', message);
            } finally {
                setIsLoading(false);
            }
        };

        void load();
        return () => controller.abort();
    }, [currentPage, reloadKey, searchKeyword]);

    const safeCurrentPage = Math.min(currentPage, totalPages);
    const startIndex = totalCount === 0 ? 0 : (safeCurrentPage - 1) * PAGE_SIZE + 1;
    const endIndex = Math.min((safeCurrentPage - 1) * PAGE_SIZE + items.length, totalCount);
    const firstPage = Math.max(1, Math.min(safeCurrentPage - 2, Math.max(1, totalPages - 4)));
    const pageNumbers = Array.from({ length: Math.min(5, totalPages - firstPage + 1) }, (_, index) => firstPage + index);

    const refresh = () => setReloadKey((value) => value + 1);

    const openCreate = () => {
        setSelectedPopup(null);
        setCreateInitialContent('');
        setIsModalOpen(true);
    };

    const openEdit = (popup: PopupItem) => {
        setSelectedPopup(popup);
        setCreateInitialContent('');
        setIsModalOpen(true);
    };

    const handleDragEnter = (event: React.DragEvent<HTMLElement>) => {
        if (!hasDraggedFile(event.dataTransfer)) return;
        event.preventDefault();
        dragDepthRef.current += 1;
        setIsImageDragActive(true);
    };

    const handleDragOver = (event: React.DragEvent<HTMLElement>) => {
        if (!hasDraggedFile(event.dataTransfer)) return;
        event.preventDefault();
        event.dataTransfer.dropEffect = 'copy';
    };

    const handleDragLeave = (event: React.DragEvent<HTMLElement>) => {
        if (!hasDraggedFile(event.dataTransfer)) return;
        event.preventDefault();
        dragDepthRef.current = Math.max(0, dragDepthRef.current - 1);
        if (dragDepthRef.current === 0) {
            setIsImageDragActive(false);
        }
    };

    const handleImageDrop = async (event: React.DragEvent<HTMLElement>) => {
        event.preventDefault();
        dragDepthRef.current = 0;
        setIsImageDragActive(false);
        if (isDroppedImageUploading) return;

        const file = event.dataTransfer.files[0];
        if (!file) {
            onNotifyRef.current('error', '등록할 이미지 파일을 끌어다 놓아 주세요.');
            return;
        }

        const extension = file.name.split('.').pop()?.toLowerCase() ?? '';
        if (!DROPPABLE_IMAGE_EXTENSIONS.includes(extension)) {
            onNotifyRef.current('error', '팝업 본문 이미지는 jpg, jpeg, png, gif 파일만 등록할 수 있습니다.');
            return;
        }

        setIsDroppedImageUploading(true);
        try {
            const formData = new FormData();
            formData.append('upload', file);
            const response = await fetch('/api/admin/popups/images', {
                method: 'POST',
                body: formData
            });
            const result = await readImageUploadResponse(response);
            if (!response.ok || !result.url) {
                throw new Error(result.error?.message || '이미지 업로드에 실패했습니다.');
            }

            setSelectedPopup(null);
            setCreateInitialContent(`<p><img src="${escapeHtmlAttribute(result.url)}" alt="${escapeHtmlAttribute(file.name)}"></p>`);
            setIsModalOpen(true);
            onNotifyRef.current('success', '이미지를 팝업 본문에 추가했습니다.');
        } catch (error) {
            const message = error instanceof Error ? error.message : '이미지 업로드에 실패했습니다.';
            onNotifyRef.current('error', message);
        } finally {
            setIsDroppedImageUploading(false);
        }
    };

    const deletePopup = async (popup: PopupItem) => {
        if (!await confirm({
            title: '팝업 삭제',
            message: `팝업 "${popup.title}"을(를) 삭제하시겠습니까?`,
            confirmText: '삭제',
            tone: 'danger'
        })) {
            return;
        }

        try {
            const response = await fetch(`/api/admin/popups/${popup.seq}`, { method: 'DELETE' });
            if (!response.ok) {
                throw new Error(await response.text() || '팝업 삭제에 실패했습니다.');
            }
            onNotifyRef.current('success', '팝업을 삭제했습니다.');
            refresh();
        } catch (error) {
            const message = error instanceof Error ? error.message : '팝업 삭제에 실패했습니다.';
            setErrorMessage(message);
            onNotifyRef.current('error', message);
        }
    };

    const handleModalSuccess = () => {
        setIsModalOpen(false);
        setSelectedPopup(null);
        setCreateInitialContent('');
        refresh();
    };

    return (
        <section
            onDragEnter={handleDragEnter}
            onDragOver={handleDragOver}
            onDragLeave={handleDragLeave}
            onDrop={(event) => void handleImageDrop(event)}
            className="relative rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-950"
        >
            {(isImageDragActive || isDroppedImageUploading) && (
                <div className="absolute inset-0 z-40 flex items-center justify-center rounded-xl border-2 border-dashed border-blue-500 bg-blue-50/95 p-6 backdrop-blur-sm dark:bg-blue-950/95">
                    <div className="text-center text-blue-700 dark:text-blue-200">
                        <Upload className={`mx-auto h-12 w-12 ${isDroppedImageUploading ? 'animate-bounce' : ''}`} />
                        <p className="mt-4 text-base font-bold">
                            {isDroppedImageUploading ? '이미지를 업로드하고 있습니다.' : '여기에 이미지를 놓아 주세요.'}
                        </p>
                        <p className="mt-1 text-xs text-blue-500 dark:text-blue-300">
                            {isDroppedImageUploading ? '완료되면 팝업 등록 창이 자동으로 열립니다.' : '등록 창의 CKEditor 본문에 자동으로 추가됩니다.'}
                        </p>
                    </div>
                </div>
            )}
            <div className="flex flex-col justify-between gap-4 border-b border-slate-200 p-4 dark:border-slate-800 lg:flex-row lg:items-center md:p-5">
                <div>
                    <div className="flex items-center gap-2">
                        <Image className="shrink-0 h-4 w-4 text-slate-400" />
                        <h3 className="text-sm font-semibold md:text-base">팝업 관리</h3>
                    </div>
                    <p className="mt-1 text-xs text-slate-400">이미지를 화면에 끌어놓으면 등록 창의 본문에 자동으로 추가됩니다.</p>
                </div>
                <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
                    <div className="relative">
                        <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                        <input
                            value={searchKeyword}
                            onChange={(event) => {
                                setSearchKeyword(event.target.value);
                                setCurrentPage(1);
                            }}
                            className="w-full rounded-lg border border-slate-200 bg-white py-2 pl-9 pr-3 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50 sm:w-72"
                            placeholder="제목, 파일명 검색"
                        />
                    </div>
                    <button
                        onClick={openCreate}
                        className="inline-flex items-center justify-center gap-2 rounded-lg bg-emerald-600 px-3 py-2 text-xs font-semibold text-white hover:bg-emerald-700"
                    >
                        <Plus className="h-4 w-4" />
                        팝업 등록
                    </button>
                </div>
            </div>

            <div className="grid grid-cols-1 border-b border-slate-200 sm:grid-cols-3 dark:border-slate-800">
                <SummaryCard label="전체 팝업" value={`${totalCount.toLocaleString()}건`} />
                <SummaryCard label="사용 팝업" value={`${enabledCount.toLocaleString()}건`} accent="text-emerald-600 dark:text-emerald-400" />
                <SummaryCard label="표시 범위" value={totalCount === 0 ? '0' : `${startIndex}-${endIndex}`} accent="text-blue-600 dark:text-blue-400" />
            </div>

            {errorMessage && (
                <div className="m-4 rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 dark:border-rose-900/60 dark:bg-rose-950/30 dark:text-rose-300 md:m-5">
                    {errorMessage}
                </div>
            )}

            <div className="p-4 md:p-5">
                {isLoading && items.length === 0 && (
                    <div className="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-3">
                        {Array.from({ length: 3 }, (_, index) => (
                            <div key={index} className="overflow-hidden rounded-xl border border-slate-200 dark:border-slate-800">
                                <div className="h-64 animate-pulse bg-slate-100 dark:bg-slate-900" />
                                <div className="space-y-3 p-4">
                                    <div className="h-4 w-2/3 animate-pulse rounded bg-slate-100 dark:bg-slate-900" />
                                    <div className="h-3 w-full animate-pulse rounded bg-slate-100 dark:bg-slate-900" />
                                </div>
                            </div>
                        ))}
                    </div>
                )}

                {!isLoading && items.length === 0 && (
                    <div className="flex min-h-72 flex-col items-center justify-center rounded-xl border border-dashed border-slate-300 bg-slate-50/60 text-center dark:border-slate-700 dark:bg-slate-900/40">
                        <Image className="h-10 w-10 text-slate-300 dark:text-slate-600" />
                        <p className="mt-3 text-sm font-semibold text-slate-600 dark:text-slate-300">등록된 팝업이 없습니다.</p>
                        <p className="mt-1 text-xs text-slate-400">팝업을 등록하면 이곳에서 내용을 바로 확인할 수 있습니다.</p>
                    </div>
                )}

                {items.length > 0 && (
                    <div className="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-3">
                        {items.map((popup) => (
                            <article key={popup.seq} className="group overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm transition hover:-translate-y-0.5 hover:shadow-md dark:border-slate-800 dark:bg-slate-950">
                                <div className="relative h-64 overflow-hidden border-b border-slate-200 bg-white dark:border-slate-800">
                                    {popup.content ? (
                                        <iframe
                                            title={`${popup.title} 내용 미리보기`}
                                            srcDoc={createPopupPreviewDocument(popup.content)}
                                            sandbox=""
                                            loading="lazy"
                                            tabIndex={-1}
                                            className="pointer-events-none h-full w-full bg-white"
                                        />
                                    ) : popup.imageUrl ? (
                                        <img src={popup.imageUrl} alt={popup.title} className="h-full w-full object-contain" />
                                    ) : (
                                        <div className="flex h-full flex-col items-center justify-center bg-slate-50 text-slate-400 dark:bg-slate-900">
                                            <Image className="h-10 w-10" />
                                            <span className="mt-2 text-xs">등록된 내용이 없습니다.</span>
                                        </div>
                                    )}
                                    <span className={`absolute right-3 top-3 inline-flex rounded-full border px-2.5 py-1 text-xs font-semibold shadow-sm backdrop-blur ${
                                        popup.enabled
                                            ? 'border-emerald-200 bg-emerald-50/95 text-emerald-700 dark:border-emerald-900/60 dark:bg-emerald-950/90 dark:text-emerald-300'
                                            : 'border-slate-200 bg-white/95 text-slate-500 dark:border-slate-700 dark:bg-slate-900/90 dark:text-slate-300'
                                    }`}>
                                        {popup.enabled ? '사용' : '미사용'}
                                    </span>
                                </div>

                                <div className="p-4">
                                    <h4 className="truncate text-sm font-bold text-slate-900 dark:text-slate-50" title={popup.title}>{popup.title}</h4>
                                    <div className="mt-3 space-y-1.5 text-xs text-slate-500 dark:text-slate-400">
                                        <p className="flex items-center gap-1.5">
                                            <CalendarDays className="h-3.5 w-3.5 shrink-0 text-slate-400" />
                                            <span className="truncate">{formatPeriod(popup)}</span>
                                        </p>
                                        <p>등록일 {formatDateTime(popup.createdAt)}</p>
                                    </div>

                                    <div className="mt-4 flex gap-2 border-t border-slate-100 pt-4 dark:border-slate-800">
                                        <button onClick={() => openEdit(popup)} className="inline-flex flex-1 items-center justify-center gap-1.5 rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900">
                                            <Pencil className="h-4 w-4 text-blue-500" />수정
                                        </button>
                                        <button onClick={() => void deletePopup(popup)} className="inline-flex flex-1 items-center justify-center gap-1.5 rounded-lg border border-rose-200 px-3 py-2 text-xs font-semibold text-rose-600 hover:bg-rose-50 dark:border-rose-900/60 dark:hover:bg-rose-950/30">
                                            <Trash2 className="h-4 w-4" />삭제
                                        </button>
                                    </div>
                                </div>
                            </article>
                        ))}
                    </div>
                )}
            </div>

            <div className="flex flex-col items-center justify-between gap-4 border-t border-slate-200 p-4 dark:border-slate-800 md:flex-row md:p-5">
                <div className="text-xs font-medium text-slate-400">
                    전체 <span className="font-bold text-slate-900 dark:text-slate-50">{totalCount.toLocaleString()}</span>건 중
                    <span className="font-bold text-slate-900 dark:text-slate-50"> {totalCount === 0 ? '0' : `${startIndex}-${endIndex}`} </span>
                    표시 중입니다.
                </div>
                <div className="flex items-center gap-1.5">
                    <button disabled={safeCurrentPage === 1} onClick={() => setCurrentPage((page) => Math.max(page - 1, 1))} className="rounded-lg border border-slate-200 p-2 hover:bg-slate-50 disabled:opacity-40 dark:border-slate-800 dark:hover:bg-slate-900"><ChevronLeft className="h-4 w-4" /></button>
                    {pageNumbers.map((page) => (
                        <button key={page} onClick={() => setCurrentPage(page)} className={`h-8 w-8 rounded-lg border text-xs font-bold transition-colors ${safeCurrentPage === page ? 'border-blue-600 bg-blue-600 text-white' : 'border-slate-200 hover:bg-slate-50 dark:border-slate-800 dark:hover:bg-slate-900'}`}>{page}</button>
                    ))}
                    <button disabled={safeCurrentPage === totalPages} onClick={() => setCurrentPage((page) => Math.min(page + 1, totalPages))} className="rounded-lg border border-slate-200 p-2 hover:bg-slate-50 disabled:opacity-40 dark:border-slate-800 dark:hover:bg-slate-900"><ChevronRight className="h-4 w-4" /></button>
                </div>
            </div>

            <PopupEditModal
                isOpen={isModalOpen}
                popup={selectedPopup}
                createInitialContent={createInitialContent}
                onClose={() => {
                    setIsModalOpen(false);
                    setSelectedPopup(null);
                    setCreateInitialContent('');
                }}
                onSuccess={handleModalSuccess}
                onNotify={onNotifyRef.current}
            />
        </section>
    );
};

interface PopupEditModalProps {
    isOpen: boolean;
    popup: PopupItem | null;
    createInitialContent: string;
    onClose: () => void;
    onSuccess: () => void;
    onNotify: (type: NotificationType, message: string) => void;
}

const PopupEditModal = ({ isOpen, popup, createInitialContent, onClose, onSuccess, onNotify }: PopupEditModalProps) => {
    const [title, setTitle] = useState('');
    const [enabled, setEnabled] = useState(true);
    const [useStartDate, setUseStartDate] = useState('');
    const [useEndDate, setUseEndDate] = useState('');
    const [content, setContent] = useState('');
    const [isSaving, setIsSaving] = useState(false);

    useEffect(() => {
        if (!isOpen) {
            return;
        }

        setTitle(popup?.title ?? '');
        setEnabled(popup?.enabled ?? true);
        if (popup) {
            setUseStartDate(popup.useStartDate ?? '');
            setUseEndDate(popup.useEndDate ?? '');
        } else {
            const defaultPeriod = getDefaultPopupPeriod();
            setUseStartDate(defaultPeriod.startDate);
            setUseEndDate(defaultPeriod.endDate);
        }
        setContent(popup?.content ?? createInitialContent);
    }, [createInitialContent, isOpen, popup]);

    if (!isOpen) {
        return null;
    }

    const handleSubmit = async (event: React.FormEvent) => {
        event.preventDefault();
        setIsSaving(true);

        try {
            const formData = new FormData();
            formData.append('title', title.trim());
            formData.append('enabled', String(enabled));
            formData.append('content', content);
            if (useStartDate) {
                formData.append('useStartDate', useStartDate);
            }
            if (useEndDate) {
                formData.append('useEndDate', useEndDate);
            }
            const response = await fetch(popup ? `/api/admin/popups/${popup.seq}` : '/api/admin/popups', {
                method: popup ? 'PUT' : 'POST',
                body: formData
            });

            if (!response.ok) {
                throw new Error(await response.text() || '팝업 저장에 실패했습니다.');
            }

            const savedPopup = await response.json() as PopupItem;
            const successMessage = popup ? '팝업을 수정했습니다.' : '팝업을 등록했습니다.';
            onNotify(savedPopup.webRiskWarning ? 'info' : 'success',
                savedPopup.webRiskWarning ? `${successMessage} ${savedPopup.webRiskWarning}` : successMessage);
            onSuccess();
        } catch (error) {
            const message = error instanceof Error ? error.message : '팝업 저장에 실패했습니다.';
            onNotify('error', message);
        } finally {
            setIsSaving(false);
        }
    };

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/50 p-4">
            <DraggableModal className="max-h-[92vh] w-full max-w-4xl overflow-y-auto rounded-xl border border-slate-200 bg-white shadow-xl dark:border-slate-800 dark:bg-slate-950">
                <div data-modal-drag-handle className="cursor-move select-none touch-none flex items-center justify-between border-b border-slate-200 p-4 dark:border-slate-800">
                    <div>
                        <h3 className="text-base font-bold">{popup ? '팝업 수정' : '팝업 등록'}</h3>
                        <p className="mt-1 text-xs text-slate-400">CKEditor에서 이미지와 일반 텍스트를 함께 작성할 수 있습니다.</p>
                    </div>
                    <button onClick={onClose} className="rounded-lg p-2 text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-900">
                        <X className="h-5 w-5" />
                    </button>
                </div>

                <form onSubmit={handleSubmit} className="space-y-5 p-4 md:p-5">

                    <div>
                        <label className="mb-1.5 block text-xs font-semibold uppercase text-slate-500">팝업 제목</label>
                        <input
                            value={title}
                            onChange={(event) => setTitle(event.target.value)}
                            className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50"
                            required
                        />
                    </div>

                    <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                        <div>
                            <label className="mb-1.5 block text-xs font-semibold uppercase text-slate-500">사용 시작일</label>
                            <input type="date" value={useStartDate} onChange={(event) => setUseStartDate(event.target.value)} className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50" />
                        </div>
                        <div>
                            <label className="mb-1.5 block text-xs font-semibold uppercase text-slate-500">사용 종료일</label>
                            <input type="date" value={useEndDate} onChange={(event) => setUseEndDate(event.target.value)} className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50" />
                        </div>
                    </div>

                    <label className="flex cursor-pointer items-center gap-3 rounded-lg border border-slate-200 px-4 py-3 dark:border-slate-800">
                        <input type="checkbox" checked={enabled} onChange={(event) => setEnabled(event.target.checked)} className="h-4 w-4 rounded" />
                        <span className="text-sm font-medium text-slate-700 dark:text-slate-300">사용 여부</span>
                    </label>

                    <div>
                        <label className="mb-1.5 block text-xs font-semibold uppercase text-slate-500">팝업 내용</label>
                        <CkEditorRichTextEditor
                            initialContent={content}
                            onChange={setContent}
                            uploadUrl="/api/admin/popups/images"
                            editorHeight={420}
                            previewHeight={460}
                            previewTitle="팝업 HTML 미리보기"
                            allowYoutube
                        />
                    </div>

                    <div className="flex justify-end gap-2 border-t border-slate-200 pt-4 dark:border-slate-800">
                        <button type="button" onClick={onClose} className="rounded-lg border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900">취소</button>
                        <button type="submit" disabled={isSaving} className="rounded-lg bg-blue-600 px-4 py-2 text-xs font-semibold text-white hover:bg-blue-700 disabled:opacity-50">
                            {isSaving ? '저장 중' : '저장'}
                        </button>
                    </div>
                </form>
            </DraggableModal>
        </div>
    );
};

const SummaryCard = ({ label, value, accent = '' }: { label: string; value: string; accent?: string }) => (
    <div className="p-4 md:p-5">
        <p className="text-xs font-medium text-slate-400">{label}</p>
        <p className={`mt-1 text-2xl font-bold ${accent}`}>{value}</p>
    </div>
);

const formatDateTime = (value?: string | null) => {
    if (!value) {
        return '-';
    }
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? '-' : dateTimeFormatter.format(date);
};

const formatPeriod = (popup: PopupItem) => {
    const start = popup.useStartDate || '시작일 없음';
    const end = popup.useEndDate || '종료일 없음';
    return `${start} ~ ${end}`;
};

const getDefaultPopupPeriod = () => {
    const today = new Date();
    const oneMonthLater = new Date(today);
    const dayOfMonth = oneMonthLater.getDate();
    oneMonthLater.setDate(1);
    oneMonthLater.setMonth(oneMonthLater.getMonth() + 1);
    const lastDayOfTargetMonth = new Date(
        oneMonthLater.getFullYear(),
        oneMonthLater.getMonth() + 1,
        0
    ).getDate();
    oneMonthLater.setDate(Math.min(dayOfMonth, lastDayOfTargetMonth));

    return {
        startDate: formatDateInputValue(today),
        endDate: formatDateInputValue(oneMonthLater)
    };
};

const formatDateInputValue = (date: Date) => {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
};

const createPopupPreviewDocument = (content: string) => `<!doctype html>
<html lang="ko">
<head>
    <meta charset="utf-8">
    <style>
        * { box-sizing: border-box; }
        html, body { margin: 0; min-height: 100%; }
        body { padding: 16px; overflow: hidden; color: #0f172a; font: 14px/1.5 Arial, sans-serif; }
        img { max-width: 100%; height: auto; }
        table { max-width: 100%; border-collapse: collapse; }
    </style>
</head>
<body>${content}</body>
</html>`;

const hasDraggedFile = (dataTransfer: DataTransfer) => Array.from(dataTransfer.types).includes('Files');

const readImageUploadResponse = async (response: Response): Promise<CkEditorImageUploadResponse> => {
    const body = await response.text();
    try {
        return JSON.parse(body) as CkEditorImageUploadResponse;
    } catch {
        return body.trim() ? { error: { message: body.trim() } } : {};
    }
};

const escapeHtmlAttribute = (value: string) => value
    .replaceAll('&', '&amp;')
    .replaceAll('"', '&quot;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;');
