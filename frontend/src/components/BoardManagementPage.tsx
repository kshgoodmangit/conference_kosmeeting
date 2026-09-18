import { DraggableModalForm } from './DraggableModal';
import {
    useEffect,
    useRef,
    useState,
    type DragEvent as ReactDragEvent,
    type FormEvent,
    type ReactNode
} from 'react';
import {
    ChevronLeft,
    ChevronRight,
    Download,
    FileText,
    Paperclip,
    Pencil,
    Pin,
    Plus,
    Search,
    Trash2,
    Upload,
    X
} from 'lucide-react';
import {
    CkEditorRichTextEditor,
    type CkEditorRichTextEditorHandle
} from './CkEditorRichTextEditor';
import type { NotificationType } from './NotificationToast';
import { rowActionButtonClass } from './RowActionMenu';
import { useConfirm } from './confirmDialogContext';

type BoardCode = 'NOTICE' | 'RESOURCE' | 'FAQ';
type PostStatus = 'DRAFT' | 'PUBLISHED' | 'HIDDEN';

interface BoardAttachment {
    seq: number;
    boardPostSeq: number;
    originalFilename: string;
    contentType?: string | null;
    fileSize: number;
    sortOrder: number;
    downloadCount: number;
    downloadUrl: string;
    createdAt?: string | null;
}

interface BoardPost {
    seq: number;
    boardSeq: number;
    categorySeq?: number | null;
    categoryName?: string | null;
    categoryCode?: string | null;
    title: string;
    content: string;
    status: PostStatus;
    isPinned: boolean;
    sortOrder: number;
    viewCount: number;
    publishedAt?: string | null;
    publishEndAt?: string | null;
    createdBy?: number | null;
    createdByName?: string | null;
    updatedBy?: number | null;
    updatedByName?: string | null;
    attachmentCount: number;
    attachments: BoardAttachment[];
    createdAt?: string | null;
    updatedAt?: string | null;
}

interface BoardPostPageResponse {
    items: BoardPost[];
    page: number;
    size: number;
    totalCount: number;
    totalPages: number;
}

interface BoardCategory {
    seq: number;
    categoryCode: string;
    categoryName: string;
    sortOrder: number;
}

interface BoardManagementPageProps {
    boardSeq: number;
    boardCode: BoardCode;
    boardName: string;
    onNotify: (type: NotificationType, message: string) => void;
}

interface BoardPostForm {
    title: string;
    content: string;
    categorySeq: string;
    status: PostStatus;
    isPinned: boolean;
    sortOrder: string;
    publishedAt: string;
    publishEndAt: string;
}

const PAGE_SIZE = 10;
const MAX_ATTACHMENT_COUNT = 5;
const MAX_ATTACHMENT_TOTAL_BYTES = 10 * 1024 * 1024;
const ALLOWED_EXTENSIONS = new Set([
    'pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'zip',
    'png', 'jpg', 'jpeg', 'gif', 'webp', 'txt', 'hwp', 'hwpx'
]);
const inputClassName = 'w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-700 dark:bg-slate-950 dark:text-slate-50';

const dateTimeFormatter = new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
});

export const BoardManagementPage = ({
    boardSeq,
    boardCode,
    boardName,
    onNotify
}: BoardManagementPageProps) => {
    const confirm = useConfirm();
    const [items, setItems] = useState<BoardPost[]>([]);
    const [categories, setCategories] = useState<BoardCategory[]>([]);
    const [keyword, setKeyword] = useState('');
    const [status, setStatus] = useState('');
    const [page, setPage] = useState(1);
    const [totalCount, setTotalCount] = useState(0);
    const [totalPages, setTotalPages] = useState(1);
    const [isLoading, setIsLoading] = useState(false);
    const [errorMessage, setErrorMessage] = useState('');
    const [reloadKey, setReloadKey] = useState(0);
    const [isEditorOpen, setIsEditorOpen] = useState(false);
    const [selectedPostSeq, setSelectedPostSeq] = useState<number | null>(null);
    const onNotifyRef = useRef(onNotify);

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
                    page: String(page),
                    size: String(PAGE_SIZE),
                    keyword: keyword.trim(),
                    status
                });
                const response = await fetch(`/api/admin/boards/${boardSeq}/posts?${params.toString()}`, {
                    signal: controller.signal
                });
                if (!response.ok) {
                    throw new Error(await response.text() || `${boardName} 목록을 불러오지 못했습니다.`);
                }

                const data = await response.json() as BoardPostPageResponse;
                setItems(data.items);
                setTotalCount(data.totalCount);
                setTotalPages(data.totalPages);
                if (data.page !== page) setPage(data.page);
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') return;
                const message = error instanceof Error ? error.message : `${boardName} 목록을 불러오지 못했습니다.`;
                setErrorMessage(message);
                onNotifyRef.current('error', message);
            } finally {
                setIsLoading(false);
            }
        };

        void load();
        return () => controller.abort();
    }, [boardName, boardSeq, keyword, page, reloadKey, status]);

    useEffect(() => {
        if (boardCode !== 'FAQ') {
            return;
        }

        const controller = new AbortController();
        const loadCategories = async () => {
            try {
                const response = await fetch('/api/admin/boards/faq-categories', {
                    signal: controller.signal
                });
                if (!response.ok) {
                    throw new Error(await response.text() || 'FAQ 구분을 불러오지 못했습니다.');
                }
                setCategories(await response.json() as BoardCategory[]);
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') return;
                const message = error instanceof Error ? error.message : 'FAQ 구분을 불러오지 못했습니다.';
                setErrorMessage(message);
                onNotifyRef.current('error', message);
            }
        };

        void loadCategories();
        return () => controller.abort();
    }, [boardCode]);

    const refresh = () => setReloadKey((value) => value + 1);

    const openCreate = () => {
        setSelectedPostSeq(null);
        setIsEditorOpen(true);
    };

    const openEdit = (post: BoardPost) => {
        setSelectedPostSeq(post.seq);
        setIsEditorOpen(true);
    };

    const deletePost = async (post: BoardPost) => {
        if (!await confirm({
            title: `${boardName} 삭제`,
            message: `“${post.title}” 게시글과 첨부파일을 모두 삭제하시겠습니까?`,
            confirmText: '삭제',
            tone: 'danger'
        })) return;

        try {
            const response = await fetch(`/api/admin/boards/${boardSeq}/posts/${post.seq}`, {
                method: 'DELETE'
            });
            if (!response.ok) {
                throw new Error(await response.text() || '게시글 삭제에 실패했습니다.');
            }
            onNotifyRef.current('success', '게시글을 삭제했습니다.');
            refresh();
        } catch (error) {
            const message = error instanceof Error ? error.message : '게시글 삭제에 실패했습니다.';
            setErrorMessage(message);
            onNotifyRef.current('error', message);
        }
    };

    const safePage = Math.min(page, totalPages);
    const firstPage = Math.max(1, Math.min(safePage - 2, Math.max(1, totalPages - 4)));
    const pageNumbers = Array.from(
        { length: Math.min(5, totalPages - firstPage + 1) },
        (_, index) => firstPage + index
    );

    return (
        <section className="rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-950">
            <div className="flex flex-col justify-between gap-4 border-b border-slate-200 p-4 dark:border-slate-800 lg:flex-row lg:items-center md:p-5">
                <div>
                    <div className="flex items-center gap-2">
                        <FileText className="shrink-0 h-4 w-4 text-slate-400" />
                        <h3 className="text-sm font-semibold md:text-base">{boardName} 관리</h3>
                    </div>
                    <p className="mt-1 text-xs text-slate-400">
                        {boardCode === 'FAQ'
                            ? 'FAQ 질문, 답변, 구분과 노출 순서를 관리합니다.'
                            : `${boardName} 게시글과 첨부파일을 관리합니다.`}
                    </p>
                </div>
                <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
                    <div className="relative">
                        <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                        <input
                            value={keyword}
                            onChange={(event) => {
                                setKeyword(event.target.value);
                                setPage(1);
                            }}
                            className="w-full rounded-lg border border-slate-200 bg-white py-2 pl-9 pr-3 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50 sm:w-64"
                            placeholder={boardCode === 'FAQ' ? '질문 또는 답변 검색' : '제목 또는 본문 검색'}
                        />
                    </div>
                    <select
                        value={status}
                        onChange={(event) => {
                            setStatus(event.target.value);
                            setPage(1);
                        }}
                        className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm dark:border-slate-800 dark:bg-slate-900"
                    >
                        <option value="">전체 상태</option>
                        <option value="DRAFT">작성중</option>
                        <option value="PUBLISHED">공개</option>
                        <option value="HIDDEN">숨김</option>
                    </select>
                    <button
                        type="button"
                        onClick={openCreate}
                        className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-3 py-2 text-xs font-semibold text-white hover:bg-blue-700"
                    >
                        <Plus className="h-4 w-4" />
                        {boardCode === 'FAQ' ? 'FAQ 등록' : '게시글 등록'}
                    </button>
                </div>
            </div>

            <div className="grid grid-cols-2 border-b border-slate-200 dark:border-slate-800">
                <SummaryCard label={`전체 ${boardName}`} value={`${totalCount.toLocaleString()}건`} />
                <SummaryCard label="현재 페이지" value={`${safePage} / ${totalPages}`} accent="text-blue-600 dark:text-blue-400" />
            </div>

            {errorMessage && (
                <div className="m-4 rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 dark:border-rose-900/60 dark:bg-rose-950/30 dark:text-rose-300 md:m-5">
                    {errorMessage}
                </div>
            )}

            <div className="overflow-x-auto">
                <table className="w-full min-w-[980px] border-collapse text-left text-xs md:text-sm">
                    <thead className="border-b border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900/50">
                        <tr>
                            <th className="p-4">번호</th>
                            {boardCode === 'FAQ' && <th className="p-4">구분</th>}
                            <th className="p-4">{boardCode === 'FAQ' ? '질문' : '제목'}</th>
                            <th className="p-4">상태</th>
                            <th className="p-4 text-center">첨부</th>
                            <th className="p-4">공개일시</th>
                            <th className="p-4">작성자</th>
                            <th className="p-4 text-center">기능</th>
                        </tr>
                    </thead>
                    <tbody>
                        {isLoading && items.length === 0 && (
                            <tr><td colSpan={boardCode === 'FAQ' ? 8 : 7} className="p-10 text-center text-slate-400">목록을 불러오는 중입니다.</td></tr>
                        )}
                        {!isLoading && items.length === 0 && (
                            <tr><td colSpan={boardCode === 'FAQ' ? 8 : 7} className="p-10 text-center text-slate-400">등록된 게시글이 없습니다.</td></tr>
                        )}
                        {items.map((post) => (
                            <tr key={post.seq} className="border-b border-slate-200 hover:bg-slate-50/60 dark:border-slate-800 dark:hover:bg-slate-900/40">
                                <td className="p-4 text-slate-500">
                                    {boardCode === 'FAQ' ? post.sortOrder : post.seq}
                                </td>
                                {boardCode === 'FAQ' && (
                                    <td className="p-4">
                                        <span className="rounded-full bg-violet-50 px-2 py-1 text-xs font-semibold text-violet-700 dark:bg-violet-950/40 dark:text-violet-300">
                                            {post.categoryName || '미분류'}
                                        </span>
                                    </td>
                                )}
                                <td className="max-w-md p-4">
                                    <button type="button" onClick={() => openEdit(post)} className="flex max-w-full items-center gap-2 text-left font-semibold text-slate-900 hover:text-blue-600 dark:text-slate-50 dark:hover:text-blue-400">
                                        {post.isPinned && <Pin className="h-3.5 w-3.5 shrink-0 text-rose-500" />}
                                        <span className="truncate">{post.title}</span>
                                    </button>
                                    <div className="mt-1 text-xs text-slate-400">수정 {formatDateTime(post.updatedAt)}</div>
                                </td>
                                <td className="p-4"><StatusBadge status={post.status} /></td>
                                <td className="p-4 text-center text-slate-500">{post.attachmentCount || 0}</td>
                                <td className="p-4 text-slate-500">{formatDateTime(post.publishedAt)}</td>
                                <td className="p-4 text-slate-500">{post.createdByName || '-'}</td>
                                <td className="p-4">
                                    <div className="flex justify-center gap-1">
                                        <button type="button" aria-label={`${post.title} 수정`} onClick={() => openEdit(post)} className={rowActionButtonClass}>
                                            <Pencil className="h-4 w-4" />
                                        </button>
                                        <button type="button" aria-label={`${post.title} 삭제`} onClick={() => void deletePost(post)} className={`${rowActionButtonClass} !text-rose-600 dark:!text-rose-400`}>
                                            <Trash2 className="h-4 w-4" />
                                        </button>
                                    </div>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            <div className="flex items-center justify-between border-t border-slate-200 p-4 dark:border-slate-800">
                <span className="text-xs text-slate-400">총 {totalCount.toLocaleString()}건</span>
                <div className="flex items-center gap-1">
                    <PaginationButton disabled={safePage <= 1} onClick={() => setPage((value) => Math.max(1, value - 1))}>
                        <ChevronLeft className="h-4 w-4" />
                    </PaginationButton>
                    {pageNumbers.map((pageNumber) => (
                        <button
                            key={pageNumber}
                            type="button"
                            onClick={() => setPage(pageNumber)}
                            className={`h-8 min-w-8 rounded-lg px-2 text-xs font-semibold ${
                                pageNumber === safePage
                                    ? 'bg-blue-600 text-white'
                                    : 'text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-800'
                            }`}
                        >
                            {pageNumber}
                        </button>
                    ))}
                    <PaginationButton disabled={safePage >= totalPages} onClick={() => setPage((value) => Math.min(totalPages, value + 1))}>
                        <ChevronRight className="h-4 w-4" />
                    </PaginationButton>
                </div>
            </div>

            {isEditorOpen && (
                <BoardPostEditorModal
                    boardSeq={boardSeq}
                    boardCode={boardCode}
                    boardName={boardName}
                    postSeq={selectedPostSeq}
                    categories={categories}
                    onClose={() => setIsEditorOpen(false)}
                    onSuccess={() => {
                        setIsEditorOpen(false);
                        setSelectedPostSeq(null);
                        refresh();
                    }}
                    onNotify={onNotify}
                />
            )}
        </section>
    );
};

interface BoardPostEditorModalProps extends BoardManagementPageProps {
    postSeq: number | null;
    categories: BoardCategory[];
    onClose: () => void;
    onSuccess: () => void;
}

const BoardPostEditorModal = ({
    boardSeq,
    boardCode,
    boardName,
    postSeq,
    categories,
    onClose,
    onSuccess,
    onNotify
}: BoardPostEditorModalProps) => {
    const confirm = useConfirm();
    const fileInputRef = useRef<HTMLInputElement>(null);
    const richTextEditorRef = useRef<CkEditorRichTextEditorHandle>(null);
    const [persistedPostSeq, setPersistedPostSeq] = useState<number | null>(postSeq);
    const [form, setForm] = useState<BoardPostForm>(() => emptyForm());
    const [attachments, setAttachments] = useState<BoardAttachment[]>([]);
    const [pendingFiles, setPendingFiles] = useState<File[]>([]);
    const [isDragging, setIsDragging] = useState(false);
    const [isLoading, setIsLoading] = useState(postSeq !== null);
    const [isSaving, setIsSaving] = useState(false);
    const [editorVersion, setEditorVersion] = useState(0);
    const onNotifyRef = useRef(onNotify);

    useEffect(() => {
        onNotifyRef.current = onNotify;
    }, [onNotify]);

    useEffect(() => {
        if (postSeq == null) {
            return;
        }

        const controller = new AbortController();
        const load = async () => {
            setIsLoading(true);
            try {
                const response = await fetch(`/api/admin/boards/${boardSeq}/posts/${postSeq}`, {
                    signal: controller.signal
                });
                if (!response.ok) {
                    throw new Error(await response.text() || '게시글 정보를 불러오지 못했습니다.');
                }
                const detail = await response.json() as BoardPost;
                setForm({
                    title: detail.title,
                    content: detail.content || '',
                    categorySeq: detail.categorySeq != null ? String(detail.categorySeq) : '',
                    status: detail.status,
                    isPinned: detail.isPinned,
                    sortOrder: String(detail.sortOrder ?? 0),
                    publishedAt: toDateTimeInput(detail.publishedAt),
                    publishEndAt: toDateTimeInput(detail.publishEndAt)
                });
                setAttachments(detail.attachments ?? []);
                setEditorVersion((value) => value + 1);
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') return;
                const message = error instanceof Error ? error.message : '게시글 정보를 불러오지 못했습니다.';
                onNotifyRef.current('error', message);
            } finally {
                setIsLoading(false);
            }
        };

        void load();
        return () => controller.abort();
    }, [boardSeq, postSeq]);

    const addFiles = (files: File[]) => {
        const invalidFile = files.find((file) => !ALLOWED_EXTENSIONS.has(fileExtension(file.name)) || file.size <= 0);
        if (invalidFile) {
            onNotifyRef.current('error', `${invalidFile.name}: 허용되지 않는 파일 형식입니다.`);
            return;
        }

        const uniqueFiles = files.filter((file) => !pendingFiles.some((existing) => (
            existing.name === file.name
            && existing.size === file.size
            && existing.lastModified === file.lastModified
        )));
        const nextFiles = [...pendingFiles, ...uniqueFiles];
        if (attachments.length + nextFiles.length > MAX_ATTACHMENT_COUNT) {
            onNotifyRef.current('error', `첨부파일은 최대 ${MAX_ATTACHMENT_COUNT}개까지 등록할 수 있습니다.`);
            return;
        }

        const totalBytes = attachments.reduce((sum, file) => sum + file.fileSize, 0)
            + nextFiles.reduce((sum, file) => sum + file.size, 0);
        if (totalBytes > MAX_ATTACHMENT_TOTAL_BYTES) {
            onNotifyRef.current('error', '첨부파일 전체 크기는 10MB를 초과할 수 없습니다.');
            return;
        }
        setPendingFiles(nextFiles);
    };

    const handleDrop = (event: ReactDragEvent<HTMLDivElement>) => {
        event.preventDefault();
        setIsDragging(false);
        addFiles(Array.from(event.dataTransfer.files));
    };

    const deleteAttachment = async (attachment: BoardAttachment) => {
        if (persistedPostSeq == null || !await confirm({
            title: '첨부파일 삭제',
            message: `“${attachment.originalFilename}” 파일을 삭제하시겠습니까?`,
            confirmText: '삭제',
            tone: 'danger'
        })) return;

        try {
            const response = await fetch(
                `/api/admin/boards/${boardSeq}/posts/${persistedPostSeq}/attachments/${attachment.seq}`,
                { method: 'DELETE' }
            );
            if (!response.ok) {
                throw new Error(await response.text() || '첨부파일 삭제에 실패했습니다.');
            }
            setAttachments((items) => items.filter((item) => item.seq !== attachment.seq));
            onNotifyRef.current('success', '첨부파일을 삭제했습니다.');
        } catch (error) {
            const message = error instanceof Error ? error.message : '첨부파일 삭제에 실패했습니다.';
            onNotifyRef.current('error', message);
        }
    };

    const handleSubmit = async (event: FormEvent) => {
        event.preventDefault();
        setIsSaving(true);

        try {
            const payload = {
                title: form.title.trim(),
                content: richTextEditorRef.current?.getData() ?? form.content,
                categorySeq: boardCode === 'FAQ' && form.categorySeq ? Number(form.categorySeq) : null,
                status: form.status,
                isPinned: boardCode === 'NOTICE' && form.isPinned,
                sortOrder: boardCode === 'FAQ' ? Number(form.sortOrder || 0) : 0,
                publishedAt: toApiDateTime(form.publishedAt),
                publishEndAt: toApiDateTime(form.publishEndAt)
            };
            const metadataResponse = await fetch(
                persistedPostSeq == null
                    ? `/api/admin/boards/${boardSeq}/posts`
                    : `/api/admin/boards/${boardSeq}/posts/${persistedPostSeq}`,
                {
                    method: persistedPostSeq == null ? 'POST' : 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                }
            );
            if (!metadataResponse.ok) {
                throw new Error(await metadataResponse.text() || '게시글 저장에 실패했습니다.');
            }

            const savedPost = await metadataResponse.json() as BoardPost;
            setPersistedPostSeq(savedPost.seq);
            let remainingFiles = [...pendingFiles];
            while (remainingFiles.length > 0) {
                const file = remainingFiles[0];
                const body = new FormData();
                body.append('file', file);
                const uploadResponse = await fetch(
                    `/api/admin/boards/${boardSeq}/posts/${savedPost.seq}/attachments`,
                    { method: 'POST', body }
                );
                if (!uploadResponse.ok) {
                    setPendingFiles(remainingFiles);
                    throw new Error(await uploadResponse.text() || `${file.name} 업로드에 실패했습니다.`);
                }
                remainingFiles = remainingFiles.slice(1);
                setPendingFiles(remainingFiles);
            }

            onNotifyRef.current('success', `${boardCode === 'FAQ' ? 'FAQ' : '게시글'}을 저장했습니다.`);
            onSuccess();
        } catch (error) {
            const message = error instanceof Error ? error.message : '게시글 저장에 실패했습니다.';
            onNotifyRef.current('error', message);
        } finally {
            setIsSaving(false);
        }
    };

    return (
        <div className="fixed inset-0 z-[110] flex items-center justify-center p-3 md:p-5">
            <button type="button" aria-label="게시글 편집 닫기" onClick={isSaving ? undefined : onClose} className="absolute inset-0 bg-slate-900/60" />
            <DraggableModalForm onSubmit={(event) => void handleSubmit(event)} className="relative z-10 flex max-h-[94vh] w-full max-w-6xl flex-col overflow-hidden rounded-xl border border-slate-200 bg-white shadow-2xl dark:border-slate-800 dark:bg-slate-950">
                <div data-modal-drag-handle className="cursor-move select-none touch-none flex items-center justify-between gap-4 border-b border-slate-200 px-5 py-4 dark:border-slate-800">
                    <div>
                        <h3 className="font-bold text-slate-900 dark:text-slate-50">
                            {persistedPostSeq == null ? `${boardName} 등록` : `${boardName} 수정`}
                        </h3>
                        <p className="mt-1 text-xs text-slate-400">본문은 CKEditor로 작성하고 파일은 하단 영역에 끌어다 놓을 수 있습니다.</p>
                    </div>
                    <button type="button" onClick={onClose} disabled={isSaving} className="rounded-lg p-2 text-slate-400 hover:bg-slate-100 disabled:opacity-40 dark:hover:bg-slate-900">
                        <X className="h-5 w-5" />
                    </button>
                </div>

                <div className="overflow-y-auto p-5">
                    {isLoading ? (
                        <div className="flex min-h-72 items-center justify-center text-sm text-slate-400">게시글 정보를 불러오는 중입니다.</div>
                    ) : (
                        <div className="space-y-5">
                            <div className="grid gap-4 md:grid-cols-12">
                                {boardCode === 'FAQ' && (
                                    <Field label="FAQ 구분" required className="md:col-span-3">
                                        <select value={form.categorySeq} onChange={(event) => setForm((current) => ({ ...current, categorySeq: event.target.value }))} className={inputClassName}>
                                            <option value="">구분 선택</option>
                                            {categories.map((category) => <option key={category.seq} value={category.seq}>{category.categoryName}</option>)}
                                        </select>
                                        {categories.length === 0 && <p className="mt-1 text-xs text-amber-600 dark:text-amber-400">FAQ_CATEGORY 공통코드를 먼저 등록해 주세요.</p>}
                                    </Field>
                                )}
                                <Field label={boardCode === 'FAQ' ? '질문' : '제목'} required className={boardCode === 'FAQ' ? 'md:col-span-9' : 'md:col-span-12'}>
                                    <input value={form.title} onChange={(event) => setForm((current) => ({ ...current, title: event.target.value }))} maxLength={500} className={inputClassName} />
                                </Field>
                                <Field label="게시 상태" required className="md:col-span-3">
                                    <select value={form.status} onChange={(event) => setForm((current) => ({ ...current, status: event.target.value as PostStatus }))} className={inputClassName}>
                                        <option value="DRAFT">작성중</option>
                                        <option value="PUBLISHED">공개</option>
                                        <option value="HIDDEN">숨김</option>
                                    </select>
                                </Field>
                                {boardCode === 'FAQ' && (
                                    <Field label="노출 순서" className="md:col-span-3">
                                        <input type="number" value={form.sortOrder} onChange={(event) => setForm((current) => ({ ...current, sortOrder: event.target.value }))} className={inputClassName} />
                                    </Field>
                                )}
                                {boardCode === 'NOTICE' && (
                                    <Field label="상단 고정" className="md:col-span-3">
                                        <label className="flex h-10 items-center gap-2 rounded-lg border border-slate-200 px-3 text-sm dark:border-slate-700">
                                            <input type="checkbox" checked={form.isPinned} onChange={(event) => setForm((current) => ({ ...current, isPinned: event.target.checked }))} />
                                            중요 공지로 고정
                                        </label>
                                    </Field>
                                )}
                                <Field label="공개 시작일시" className="md:col-span-3">
                                    <input type="datetime-local" value={form.publishedAt} onChange={(event) => setForm((current) => ({ ...current, publishedAt: event.target.value }))} className={inputClassName} />
                                </Field>
                                <Field label="공개 종료일시" className="md:col-span-3">
                                    <input type="datetime-local" value={form.publishEndAt} onChange={(event) => setForm((current) => ({ ...current, publishEndAt: event.target.value }))} className={inputClassName} />
                                </Field>
                            </div>

                            <Field label={boardCode === 'FAQ' ? '답변' : '본문'} required={boardCode !== 'RESOURCE'}>
                                <CkEditorRichTextEditor
                                    ref={richTextEditorRef}
                                    key={`${boardSeq}-${persistedPostSeq ?? 'new'}-${editorVersion}`}
                                    initialContent={form.content}
                                    onChange={(content) => setForm((current) => ({ ...current, content }))}
                                    uploadUrl="/api/admin/boards/images"
                                    previewTitle="게시글 HTML 미리보기"
                                    contentStyle="public-menu"
                                    allowYoutube
                                />
                            </Field>

                            <div>
                                <div className="mb-2 flex items-center justify-between gap-3">
                                    <label className="text-sm font-semibold text-slate-700 dark:text-slate-200">첨부파일</label>
                                    <span className="text-xs text-slate-400">최대 5개 · 합계 10MB</span>
                                </div>
                                {attachments.length > 0 && (
                                    <div className="mb-3 space-y-2">
                                        {attachments.map((attachment) => (
                                            <div key={attachment.seq} className="flex items-center justify-between gap-3 rounded-lg border border-slate-200 px-3 py-2 dark:border-slate-800">
                                                <a href={attachment.downloadUrl} className="flex min-w-0 items-center gap-2 text-sm text-blue-600 hover:underline dark:text-blue-400">
                                                    <Paperclip className="h-4 w-4 shrink-0" />
                                                    <span className="truncate">{attachment.originalFilename}</span>
                                                    <span className="shrink-0 text-xs text-slate-400">({formatBytes(attachment.fileSize)})</span>
                                                    <Download className="h-3.5 w-3.5 shrink-0" />
                                                </a>
                                                <button type="button" title="첨부파일 삭제" onClick={() => void deleteAttachment(attachment)} disabled={isSaving} className="rounded p-1.5 text-rose-500 hover:bg-rose-50 disabled:opacity-40 dark:hover:bg-rose-950/40">
                                                    <X className="h-4 w-4" />
                                                </button>
                                            </div>
                                        ))}
                                    </div>
                                )}

                                <input
                                    ref={fileInputRef}
                                    type="file"
                                    multiple
                                    className="hidden"
                                    accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.zip,.png,.jpg,.jpeg,.gif,.webp,.txt,.hwp,.hwpx"
                                    onChange={(event) => {
                                        addFiles(Array.from(event.currentTarget.files ?? []));
                                        event.currentTarget.value = '';
                                    }}
                                />
                                <div
                                    onDragEnter={(event) => {
                                        event.preventDefault();
                                        setIsDragging(true);
                                    }}
                                    onDragOver={(event) => event.preventDefault()}
                                    onDragLeave={() => setIsDragging(false)}
                                    onDrop={handleDrop}
                                    className={`rounded-xl border-2 border-dashed px-5 py-7 text-center transition-colors ${
                                        isDragging
                                            ? 'border-blue-500 bg-blue-50 dark:bg-blue-950/30'
                                            : 'border-slate-300 bg-slate-50 dark:border-slate-700 dark:bg-slate-900/60'
                                    }`}
                                >
                                    <Upload className={`mx-auto h-8 w-8 ${isDragging ? 'text-blue-600' : 'text-slate-400'}`} />
                                    <p className="mt-2 text-sm font-semibold text-slate-700 dark:text-slate-200">파일을 이곳에 끌어다 놓으세요</p>
                                    <p className="mt-1 text-xs text-slate-400">PDF, Office, HWP, 이미지, ZIP, TXT 파일을 등록할 수 있습니다.</p>
                                    <button type="button" onClick={() => fileInputRef.current?.click()} disabled={isSaving} className="mt-3 rounded-lg border border-slate-200 bg-white px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-100 disabled:opacity-40 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-200 dark:hover:bg-slate-800">
                                        파일 선택
                                    </button>
                                </div>

                                {pendingFiles.length > 0 && (
                                    <div className="mt-3 space-y-2">
                                        {pendingFiles.map((file) => (
                                            <div key={`${file.name}-${file.size}-${file.lastModified}`} className="flex items-center justify-between gap-3 rounded-lg border border-blue-100 bg-blue-50/60 px-3 py-2 dark:border-blue-900/50 dark:bg-blue-950/20">
                                                <span className="min-w-0 truncate text-sm text-slate-700 dark:text-slate-200">{file.name} ({formatBytes(file.size)})</span>
                                                <button type="button" onClick={() => setPendingFiles((items) => items.filter((item) => item !== file))} disabled={isSaving} className="shrink-0 text-xs font-semibold text-rose-600 hover:underline disabled:opacity-40 dark:text-rose-400">선택 해제</button>
                                            </div>
                                        ))}
                                    </div>
                                )}
                            </div>
                        </div>
                    )}
                </div>

                <div className="flex justify-end gap-2 border-t border-slate-200 px-5 py-4 dark:border-slate-800">
                    <button type="button" onClick={onClose} disabled={isSaving} className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-40 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-900">취소</button>
                    <button type="submit" disabled={isLoading || isSaving} className="rounded-lg bg-blue-600 px-5 py-2 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-50">
                        {isSaving ? '저장 중...' : '저장'}
                    </button>
                </div>
            </DraggableModalForm>
        </div>
    );
};

const emptyForm = (): BoardPostForm => ({
    title: '',
    content: '',
    categorySeq: '',
    status: 'DRAFT',
    isPinned: false,
    sortOrder: '0',
    publishedAt: '',
    publishEndAt: ''
});

const fileExtension = (filename: string) => {
    const dotIndex = filename.lastIndexOf('.');
    return dotIndex < 0 ? '' : filename.slice(dotIndex + 1).toLowerCase();
};

const toDateTimeInput = (value?: string | null) => value ? value.slice(0, 16) : '';
const toApiDateTime = (value: string) => value ? `${value}:00` : null;

const formatDateTime = (value?: string | null) => {
    if (!value) return '-';
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? value : dateTimeFormatter.format(date);
};

const formatBytes = (bytes: number) => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};

const StatusBadge = ({ status }: { status: PostStatus }) => {
    const styles: Record<PostStatus, string> = {
        DRAFT: 'bg-amber-50 text-amber-700 dark:bg-amber-950/40 dark:text-amber-300',
        PUBLISHED: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300',
        HIDDEN: 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300'
    };
    const labels: Record<PostStatus, string> = {
        DRAFT: '작성중',
        PUBLISHED: '공개',
        HIDDEN: '숨김'
    };
    return <span className={`rounded-full px-2 py-1 text-xs font-semibold ${styles[status]}`}>{labels[status]}</span>;
};

const SummaryCard = ({ label, value, accent = 'text-slate-900 dark:text-slate-50' }: { label: string; value: string; accent?: string }) => (
    <div className="border-r border-slate-200 p-4 last:border-r-0 dark:border-slate-800">
        <div className="text-xs text-slate-400">{label}</div>
        <div className={`mt-1 text-lg font-bold ${accent}`}>{value}</div>
    </div>
);

const PaginationButton = ({ children, disabled, onClick }: { children: ReactNode; disabled: boolean; onClick: () => void }) => (
    <button type="button" disabled={disabled} onClick={onClick} className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-500 hover:bg-slate-100 disabled:opacity-30 dark:hover:bg-slate-800">
        {children}
    </button>
);

const Field = ({ label, required = false, className = '', children }: { label: string; required?: boolean; className?: string; children: ReactNode }) => (
    <div className={className}>
        <label className="mb-1.5 block text-sm font-semibold text-slate-700 dark:text-slate-200">
            {label}{required && <span className="ml-1 text-rose-500">*</span>}
        </label>
        {children}
    </div>
);
