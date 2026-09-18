import { useCallback, useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import {
    CheckCircle2,
    ChevronLeft,
    ChevronRight,
    Download,
    Eye,
    FileText,
    FilterX,
    LoaderCircle,
    Paperclip,
    Pencil,
    Plus,
    Search,
    Trash2,
    Wrench,
    X
} from 'lucide-react';
import { CkEditorRichTextEditor } from './CkEditorRichTextEditor';
import { DraggableModal, DraggableModalForm } from './DraggableModal';
import { useConfirm } from './confirmDialogContext';
import type { NotificationType } from './NotificationToast';
import { RowActionMenu } from './RowActionMenu';

type RequestStatus = 'REQUESTED' | 'IN_PROGRESS' | 'COMPLETED';

interface Attachment {
    seq: number;
    attachmentType: 'REQUEST' | 'ANSWER';
    originalFilename: string;
    contentType?: string | null;
    fileSize: number;
}

interface MaintenanceRequest {
    seq: number;
    title: string;
    content: string;
    status: RequestStatus;
    requestedByAdminSeq: number;
    requestedByName: string;
    assignedToAdminSeq?: number | null;
    assignedToName?: string | null;
    answerContent?: string | null;
    answeredByName?: string | null;
    answeredAt?: string | null;
    createdAt: string;
    updatedAt: string;
    attachmentCount: number;
    attachments?: Attachment[];
}

interface PageResponse {
    items: MaintenanceRequest[];
    page: number;
    size: number;
    totalCount: number;
    requestedCount: number;
    inProgressCount: number;
    completedCount: number;
}

interface Props {
    onNotify: (type: NotificationType, message: string) => void;
}

const PAGE_SIZE = 10;
const STATUS_LABEL: Record<RequestStatus, string> = {
    REQUESTED: '접수',
    IN_PROGRESS: '처리 중',
    COMPLETED: '처리 완료'
};
const STATUS_OPTIONS = (Object.entries(STATUS_LABEL) as [RequestStatus, string][]);
const searchInputClass = 'w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-700 dark:bg-slate-950 dark:text-slate-50';
const modalInputClass = 'w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm font-normal text-slate-900 placeholder:text-slate-400 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50';

export const MaintenanceRequestPage = ({ onNotify }: Props) => {
    const confirm = useConfirm();
    const notifyRef = useRef(onNotify);
    const [items, setItems] = useState<MaintenanceRequest[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [loadFailed, setLoadFailed] = useState(false);
    const [currentPage, setCurrentPage] = useState(1);
    const [totalCount, setTotalCount] = useState(0);
    const [requestedCount, setRequestedCount] = useState(0);
    const [inProgressCount, setInProgressCount] = useState(0);
    const [completedCount, setCompletedCount] = useState(0);
    const [draftKeyword, setDraftKeyword] = useState('');
    const [draftStatus, setDraftStatus] = useState('');
    const [keyword, setKeyword] = useState('');
    const [status, setStatus] = useState('');
    const [isCreateOpen, setIsCreateOpen] = useState(false);
    const [selectedSeq, setSelectedSeq] = useState<number | null>(null);
    const [editOnOpen, setEditOnOpen] = useState(false);

    useEffect(() => {
        notifyRef.current = onNotify;
    }, [onNotify]);

    const loadRequests = useCallback(async () => {
        setIsLoading(true);
        setLoadFailed(false);
        try {
            const params = new URLSearchParams({
                page: String(currentPage),
                size: String(PAGE_SIZE),
                keyword,
                status
            });
            const response = await fetch(`/api/maintenance/requests?${params.toString()}`);
            if (!response.ok) throw new Error(await response.text() || '유지보수 요청을 불러오지 못했습니다.');
            const data = await response.json() as PageResponse;
            setItems(data.items);
            setTotalCount(data.totalCount);
            setRequestedCount(data.requestedCount);
            setInProgressCount(data.inProgressCount);
            setCompletedCount(data.completedCount);
            if (data.page !== currentPage) setCurrentPage(data.page);
        } catch (error) {
            setLoadFailed(true);
            notifyRef.current('error', error instanceof Error ? error.message : '유지보수 요청을 불러오지 못했습니다.');
        } finally {
            setIsLoading(false);
        }
    }, [currentPage, keyword, status]);

    useEffect(() => {
        const timeoutId = window.setTimeout(() => void loadRequests(), 0);
        return () => window.clearTimeout(timeoutId);
    }, [loadRequests]);

    const handleSearch = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        setCurrentPage(1);
        setKeyword(draftKeyword.trim());
        setStatus(draftStatus);
    };

    const resetSearch = () => {
        setDraftKeyword('');
        setDraftStatus('');
        setKeyword('');
        setStatus('');
        setCurrentPage(1);
    };

    const deleteRequest = async (request: MaintenanceRequest) => {
        const confirmed = await confirm({
            title: '유지보수 요청 삭제',
            message: `“${request.title}” 요청을 삭제하시겠습니까?`,
            confirmText: '삭제',
            tone: 'danger'
        });
        if (!confirmed) return;

        try {
            const response = await fetch(`/api/maintenance/requests/${request.seq}`, { method: 'DELETE' });
            if (!response.ok) throw new Error(await response.text() || '요청을 삭제하지 못했습니다.');
            notifyRef.current('success', '유지보수 요청을 삭제했습니다.');
            void loadRequests();
        } catch (error) {
            notifyRef.current('error', error instanceof Error ? error.message : '요청을 삭제하지 못했습니다.');
        }
    };

    const totalPages = Math.max(1, Math.ceil(totalCount / PAGE_SIZE));
    const pageNumbers = pageRange(currentPage, totalPages);
    const summaryValue = (value: number) => isLoading ? '집계 중...' : loadFailed ? '-' : `${value.toLocaleString()}건`;

    return (
        <section className="rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-950">
            <div className="flex flex-col justify-between gap-4 border-b border-slate-200 p-4 dark:border-slate-800 sm:flex-row sm:items-center md:p-5">
                <div>
                    <div className="flex items-center gap-2">
                        <Wrench className="h-5 w-5 shrink-0 text-blue-500" />
                        <h1 className="text-sm font-semibold md:text-base">유지보수 요청 관리</h1>
                    </div>
                    <p className="mt-1 text-xs text-slate-400 dark:text-slate-400">홈페이지 수정 요청과 유지보수 처리 결과를 관리합니다.</p>
                </div>
                <button type="button" onClick={() => setIsCreateOpen(true)} className="inline-flex items-center justify-center gap-2 rounded-lg bg-emerald-600 px-3 py-2 text-xs font-semibold text-white hover:bg-emerald-700 disabled:opacity-50 dark:bg-emerald-600 dark:text-white dark:hover:bg-emerald-700">
                    <Plus className="h-4 w-4" /> 유지보수 요청 등록
                </button>
            </div>

            <div className="grid grid-cols-1 border-b border-slate-200 dark:border-slate-800 sm:grid-cols-4">
                <SummaryCard label="검색 결과" value={summaryValue(totalCount)} />
                <SummaryCard label="접수" value={summaryValue(requestedCount)} accent="text-amber-600 dark:text-amber-400" />
                <SummaryCard label="처리 중" value={summaryValue(inProgressCount)} accent="text-blue-600 dark:text-blue-400" />
                <SummaryCard label="처리 완료" value={summaryValue(completedCount)} accent="text-emerald-600 dark:text-emerald-400" />
            </div>

            <form onSubmit={handleSearch} className="border-b border-slate-200 bg-slate-50/70 p-4 dark:border-slate-800 dark:bg-slate-900/40 md:p-5">
                <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-4">
                    <label className="md:col-span-2">
                        <span className="mb-1 block text-[11px] font-semibold text-slate-500 dark:text-slate-400">검색어</span>
                        <div className="relative">
                            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                            <input value={draftKeyword} onChange={(event) => setDraftKeyword(event.target.value)} className={`${searchInputClass} pl-9`} placeholder="제목 또는 요청 내용 검색" />
                        </div>
                    </label>
                    <label>
                        <span className="mb-1 block text-[11px] font-semibold text-slate-500 dark:text-slate-400">처리 상태</span>
                        <select value={draftStatus} onChange={(event) => setDraftStatus(event.target.value)} className={searchInputClass}>
                            <option value="">전체 상태</option>
                            <option value="REQUESTED">접수</option>
                            <option value="IN_PROGRESS">처리 중</option>
                            <option value="COMPLETED">처리 완료</option>
                        </select>
                    </label>
                </div>
                <div className="mt-3 flex flex-col justify-end gap-2 sm:flex-row">
                    <button type="button" onClick={resetSearch} className="inline-flex items-center justify-center gap-2 rounded-lg border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-white dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-900">
                        <FilterX className="h-4 w-4" /> 초기화
                    </button>
                    <button type="submit" className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-5 py-2 text-xs font-semibold text-white hover:bg-blue-700 dark:bg-blue-600 dark:text-white dark:hover:bg-blue-700">
                        <Search className="h-4 w-4" /> 조회
                    </button>
                </div>
            </form>

            <div className="overflow-x-auto">
                <table className="w-full min-w-[960px] border-collapse text-left text-xs md:text-sm">
                    <thead className="border-b border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900/50">
                        <tr>
                            <th className="w-24 p-4">상태</th>
                            <th className="p-4">제목</th>
                            <th className="w-36 p-4">요청자</th>
                            <th className="w-36 p-4">담당자</th>
                            <th className="w-44 p-4">등록일</th>
                            <th className="w-28 p-4 text-center">기능</th>
                        </tr>
                    </thead>
                    <tbody>
                        {isLoading && items.length === 0 && <tr><td colSpan={6} className="p-10 text-center text-slate-400">유지보수 요청을 불러오는 중입니다.</td></tr>}
                        {!isLoading && items.length === 0 && (
                            <tr><td colSpan={6} className="p-10 text-center text-slate-400"><FileText className="mx-auto mb-2 h-8 w-8 text-slate-300 dark:text-slate-700" />조회된 유지보수 요청이 없습니다.</td></tr>
                        )}
                        {items.map((request) => (
                            <tr key={request.seq} className="border-b border-slate-200 hover:bg-slate-50/60 dark:border-slate-800 dark:hover:bg-slate-900/40">
                                <td className="p-4"><StatusBadge status={request.status} /></td>
                                <td className="p-4">
                                    <button type="button" onClick={() => { setEditOnOpen(false); setSelectedSeq(request.seq); }} className="flex max-w-full items-center gap-2 text-left font-semibold text-blue-600 hover:underline dark:text-blue-400">
                                        <span className="truncate">{request.title}</span>{request.attachmentCount > 0 && <Paperclip className="h-3.5 w-3.5 shrink-0 text-slate-400" />}
                                    </button>
                                </td>
                                <td className="p-4 text-slate-600 dark:text-slate-300">{request.requestedByName}</td>
                                <td className="p-4 text-slate-500 dark:text-slate-400">{request.assignedToName || '-'}</td>
                                <td className="p-4 text-slate-500 dark:text-slate-400">{formatDateTime(request.createdAt)}</td>
                                <td className="p-4 text-center"><RowActionMenu itemLabel={request.title} actions={[
                                    { label: '상세보기', icon: <Eye className="h-4 w-4" />, onClick: () => { setEditOnOpen(false); setSelectedSeq(request.seq); } },
                                    { label: '수정', icon: <Pencil className="h-4 w-4" />, onClick: () => { setEditOnOpen(true); setSelectedSeq(request.seq); } },
                                    { label: '삭제', icon: <Trash2 className="h-4 w-4" />, onClick: () => void deleteRequest(request), tone: 'danger' }
                                ]} /></td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            <div className="flex flex-col items-center justify-between gap-4 border-t border-slate-200 p-4 dark:border-slate-800 md:flex-row md:p-5">
                <div className="text-xs font-medium text-slate-400">전체 <span className="font-bold text-slate-900 dark:text-slate-50">{totalCount.toLocaleString()}</span>건</div>
                <div className="flex items-center gap-1.5">
                    <PageButton disabled={currentPage === 1} onClick={() => setCurrentPage((page) => Math.max(1, page - 1))} label="이전 페이지"><ChevronLeft className="h-4 w-4" /></PageButton>
                    {pageNumbers.map((page) => <button key={page} type="button" onClick={() => setCurrentPage(page)} className={`h-8 w-8 rounded-lg border text-xs font-bold ${currentPage === page ? 'border-blue-600 bg-blue-600 text-white dark:border-blue-600 dark:bg-blue-600 dark:text-white' : 'border-slate-200 text-slate-600 hover:bg-slate-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900'}`}>{page}</button>)}
                    <PageButton disabled={currentPage === totalPages} onClick={() => setCurrentPage((page) => Math.min(totalPages, page + 1))} label="다음 페이지"><ChevronRight className="h-4 w-4" /></PageButton>
                </div>
            </div>

            {isCreateOpen && <CreateRequestModal onClose={() => setIsCreateOpen(false)} onSaved={() => { setIsCreateOpen(false); setCurrentPage(1); void loadRequests(); }} onNotify={onNotify} />}
            {selectedSeq !== null && <RequestDetailModal seq={selectedSeq} startEditing={editOnOpen} onClose={() => { setSelectedSeq(null); setEditOnOpen(false); }} onSaved={() => void loadRequests()} onNotify={onNotify} />}
        </section>
    );
};

const CreateRequestModal = ({ onClose, onSaved, onNotify }: { onClose: () => void; onSaved: () => void; onNotify: Props['onNotify'] }) => {
    const [title, setTitle] = useState('');
    const [content, setContent] = useState('');
    const [files, setFiles] = useState<File[]>([]);
    const [isSaving, setIsSaving] = useState(false);

    const submit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (!title.trim() || !content.trim()) {
            onNotify('error', '제목과 요청 내용을 입력해 주세요.');
            return;
        }
        setIsSaving(true);
        try {
            const body = new FormData();
            body.append('title', title.trim());
            body.append('content', content);
            files.forEach((file) => body.append('files', file));
            const response = await fetch('/api/maintenance/requests', { method: 'POST', body });
            if (!response.ok) throw new Error(await response.text() || '요청을 등록하지 못했습니다.');
            onNotify('success', '유지보수 요청을 등록했습니다.');
            onSaved();
        } catch (error) {
            onNotify('error', error instanceof Error ? error.message : '요청을 등록하지 못했습니다.');
        } finally {
            setIsSaving(false);
        }
    };

    return (
        <div className="fixed inset-0 z-[110] flex items-center justify-center bg-slate-950/60 p-4 dark:bg-slate-950/60">
            <DraggableModalForm role="dialog" aria-modal="true" aria-labelledby="maintenance-request-create-title" onSubmit={(event) => void submit(event)} className="flex max-h-[90vh] w-full max-w-5xl flex-col overflow-hidden rounded-2xl bg-white shadow-2xl dark:bg-slate-950">
                <ModalHeader id="maintenance-request-create-title" title="유지보수 요청 등록" description="수정이 필요한 화면과 작업 내용을 자세히 입력해 주세요." onClose={onClose} disabled={isSaving} />
                <div className="space-y-5 overflow-y-auto p-5">
                    <Field label="제목" required><input value={title} onChange={(event) => setTitle(event.target.value)} maxLength={255} className={modalInputClass} /></Field>
                    <Field label="요청 내용" required><CkEditorRichTextEditor initialContent={content} onChange={setContent} previewTitle="요청 내용 미리보기" editorHeight={280} /></Field>
                    <FilePicker files={files} onChange={setFiles} disabled={isSaving} />
                </div>
                <div className="flex shrink-0 justify-end gap-2 border-t border-slate-200 px-5 py-4 dark:border-slate-800">
                    <button type="button" onClick={onClose} disabled={isSaving} className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-40 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900">취소</button>
                    <button type="submit" disabled={isSaving} className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-50 dark:bg-blue-600 dark:text-white dark:hover:bg-blue-700">{isSaving && <LoaderCircle className="h-4 w-4 animate-spin" />}{isSaving ? '저장 중...' : '저장'}</button>
                </div>
            </DraggableModalForm>
        </div>
    );
};

const RequestDetailModal = ({ seq, startEditing, onClose, onSaved, onNotify }: { seq: number; startEditing: boolean; onClose: () => void; onSaved: () => void; onNotify: Props['onNotify'] }) => {
    const [request, setRequest] = useState<MaintenanceRequest | null>(null);
    const [answer, setAnswer] = useState('');
    const [status, setStatus] = useState<RequestStatus>('IN_PROGRESS');
    const [answerFiles, setAnswerFiles] = useState<File[]>([]);
    const [isEditingRequest, setIsEditingRequest] = useState(startEditing);
    const [editTitle, setEditTitle] = useState('');
    const [editContent, setEditContent] = useState('');
    const [editFiles, setEditFiles] = useState<File[]>([]);
    const [isSaving, setIsSaving] = useState(false);

    useEffect(() => {
        const controller = new AbortController();
        void (async () => {
            try {
                const response = await fetch(`/api/maintenance/requests/${seq}`, { signal: controller.signal });
                if (!response.ok) throw new Error(await response.text() || '요청을 불러오지 못했습니다.');
                const data = await response.json() as MaintenanceRequest;
                setRequest(data);
                setAnswer(data.answerContent ?? '');
                setStatus(data.status);
                setEditTitle(data.title);
                setEditContent(data.content);
                setIsEditingRequest(startEditing);
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') return;
                onNotify('error', error instanceof Error ? error.message : '요청을 불러오지 못했습니다.');
                onClose();
            }
        })();
        return () => controller.abort();
    }, [seq, startEditing, onClose, onNotify]);

    const beginRequestEdit = () => {
        if (!request) return;
        setEditTitle(request.title);
        setEditContent(request.content);
        setEditFiles([]);
        setIsEditingRequest(true);
    };

    const cancelRequestEdit = () => {
        if (!request || isSaving) return;
        setEditTitle(request.title);
        setEditContent(request.content);
        setEditFiles([]);
        setIsEditingRequest(false);
    };

    const saveRequest = async () => {
        if (!request || isSaving) return;
        if (!editTitle.trim() || !editContent.trim()) {
            onNotify('error', '제목과 요청 내용을 입력해 주세요.');
            return;
        }
        setIsSaving(true);
        try {
            const body = new FormData();
            body.append('title', editTitle.trim());
            body.append('content', editContent);
            editFiles.forEach((file) => body.append('files', file));
            const response = await fetch(`/api/maintenance/requests/${seq}`, { method: 'PUT', body });
            if (!response.ok) throw new Error(await response.text() || '유지보수 요청을 수정하지 못했습니다.');
            const data = await response.json() as MaintenanceRequest;
            setRequest(data);
            setEditTitle(data.title);
            setEditContent(data.content);
            setEditFiles([]);
            setIsEditingRequest(false);
            onNotify('success', '유지보수 요청을 수정했습니다.');
            onSaved();
        } catch (error) {
            onNotify('error', error instanceof Error ? error.message : '유지보수 요청을 수정하지 못했습니다.');
        } finally {
            setIsSaving(false);
        }
    };

    const saveAnswer = async () => {
        if (!request || isSaving) return;
        setIsSaving(true);
        try {
            const body = new FormData();
            body.append('status', status);
            body.append('answerContent', answer);
            answerFiles.forEach((file) => body.append('files', file));
            const response = await fetch(`/api/maintenance/requests/${seq}/answer`, { method: 'PUT', body });
            if (!response.ok) throw new Error(await response.text() || '처리 결과를 저장하지 못했습니다.');
            const data = await response.json() as MaintenanceRequest;
            setRequest(data);
            setAnswer(data.answerContent ?? '');
            setAnswerFiles([]);
            onNotify('success', '처리 결과를 저장했습니다.');
            onSaved();
        } catch (error) {
            onNotify('error', error instanceof Error ? error.message : '처리 결과를 저장하지 못했습니다.');
        } finally {
            setIsSaving(false);
        }
    };

    const download = async (attachment: Attachment) => {
        try {
            const response = await fetch(`/api/maintenance/requests/attachments/${attachment.seq}`);
            if (!response.ok) throw new Error(await response.text() || '첨부파일을 내려받지 못했습니다.');
            const url = URL.createObjectURL(await response.blob());
            const anchor = document.createElement('a');
            anchor.href = url;
            anchor.download = attachment.originalFilename;
            anchor.click();
            URL.revokeObjectURL(url);
        } catch (error) {
            onNotify('error', error instanceof Error ? error.message : '첨부파일을 내려받지 못했습니다.');
        }
    };

    const requestAttachments = (request?.attachments ?? []).filter((item) => item.attachmentType === 'REQUEST');
    const answerAttachments = (request?.attachments ?? []).filter((item) => item.attachmentType === 'ANSWER');

    return (
        <div className="fixed inset-0 z-[110] flex items-center justify-center bg-slate-950/60 p-4 dark:bg-slate-950/60">
            <DraggableModal role="dialog" aria-modal="true" aria-labelledby="maintenance-request-detail-title" className="flex max-h-[90vh] w-full max-w-5xl flex-col overflow-hidden rounded-2xl bg-white shadow-2xl dark:bg-slate-950">
                <ModalHeader id="maintenance-request-detail-title" title={request ? `#${request.seq} ${request.title}` : '유지보수 요청 상세'} description={request ? `${request.requestedByName} · ${formatDateTime(request.createdAt)}` : '요청 정보를 불러오는 중입니다.'} onClose={onClose} disabled={isSaving} />
                <div className="space-y-5 overflow-y-auto p-5">
                    {!request ? <div className="flex min-h-56 items-center justify-center text-sm text-slate-400">요청 정보를 불러오는 중입니다.</div> : (
                        <>
                            <div className="grid gap-3 rounded-lg border border-slate-200 bg-slate-50 p-4 text-xs dark:border-slate-800 dark:bg-slate-900/50 sm:grid-cols-3">
                                <DetailValue label="처리 상태"><StatusBadge status={request.status} /></DetailValue>
                                <DetailValue label="요청자" value={request.requestedByName} />
                                <DetailValue label="담당자" value={request.assignedToName || '-'} />
                            </div>
                            {isEditingRequest ? (
                                <section className="space-y-5">
                                    <div className="flex items-center gap-2"><Pencil className="h-4 w-4 text-blue-500" /><h3 className="text-sm font-bold text-slate-900 dark:text-slate-50">요청 내용 수정</h3></div>
                                    <Field label="제목" required><input value={editTitle} onChange={(event) => setEditTitle(event.target.value)} maxLength={255} className={modalInputClass} /></Field>
                                    <Field label="요청 내용" required><CkEditorRichTextEditor key={`request-edit-${seq}-${request.updatedAt}`} initialContent={editContent} onChange={setEditContent} previewTitle="요청 내용 미리보기" editorHeight={280} /></Field>
                                    <AttachmentList title="기존 요청 첨부파일" items={requestAttachments} onDownload={download} />
                                    <FilePicker files={editFiles} onChange={setEditFiles} disabled={isSaving} />
                                    <p className="text-xs text-slate-400 dark:text-slate-400">새 첨부파일은 기존 파일에 추가되며 요청 첨부파일은 누적 5개, 20MB까지 저장할 수 있습니다.</p>
                                </section>
                            ) : (
                                <>
                                    <ContentBox title="요청 내용" html={request.content} />
                                    <AttachmentList title="요청 첨부파일" items={requestAttachments} onDownload={download} />
                                </>
                            )}
                            {!isEditingRequest && (
                                <section className="space-y-5 border-t border-slate-200 pt-5 dark:border-slate-800">
                                    <div className="flex items-center gap-2"><CheckCircle2 className="h-4 w-4 text-blue-500" /><h3 className="text-sm font-bold text-slate-900 dark:text-slate-50">처리 결과 작성</h3></div>
                                    <fieldset>
                                        <legend className="mb-1.5 text-sm font-semibold text-slate-700 dark:text-slate-200">처리 상태<span className="ml-1 text-rose-500 dark:text-rose-400">*</span></legend>
                                        <div className="grid gap-2 sm:grid-cols-3">
                                            {STATUS_OPTIONS.map(([value, label]) => (
                                                <label key={value} className={`flex items-center gap-2 rounded-lg border px-3 py-2.5 text-sm font-semibold transition-colors ${status === value ? 'border-blue-500 bg-blue-50 text-blue-700 dark:border-blue-500 dark:bg-blue-950/40 dark:text-blue-300' : 'border-slate-200 bg-white text-slate-600 hover:bg-slate-50 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-300 dark:hover:bg-slate-800'}`}>
                                                    <input type="radio" name={`maintenance-request-status-${seq}`} value={value} checked={status === value} onChange={() => setStatus(value)} disabled={isSaving} className="h-4 w-4 shrink-0 accent-blue-600 disabled:opacity-50" />
                                                    <span>{label}</span>
                                                </label>
                                            ))}
                                        </div>
                                    </fieldset>
                                    <Field label="처리 결과"><CkEditorRichTextEditor key={`${seq}-${request.updatedAt}`} initialContent={answer} onChange={setAnswer} previewTitle="처리 결과 미리보기" editorHeight={260} /></Field>
                                    <FilePicker files={answerFiles} onChange={setAnswerFiles} disabled={isSaving} />
                                    <AttachmentList title="기존 답변 첨부파일" items={answerAttachments} onDownload={download} />
                                </section>
                            )}
                        </>
                    )}
                </div>
                <div className="flex shrink-0 justify-end gap-2 border-t border-slate-200 px-5 py-4 dark:border-slate-800">
                    {isEditingRequest ? (
                        <>
                            <button type="button" onClick={cancelRequestEdit} disabled={isSaving} className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-40 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900">취소</button>
                            <button type="button" onClick={() => void saveRequest()} disabled={!request || isSaving} className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-50 dark:bg-blue-600 dark:text-white dark:hover:bg-blue-700">{isSaving && <LoaderCircle className="h-4 w-4 animate-spin" />}{isSaving ? '저장 중...' : '요청 저장'}</button>
                        </>
                    ) : (
                        <>
                            <button type="button" onClick={beginRequestEdit} disabled={!request || isSaving} className="inline-flex items-center gap-2 rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-40 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900"><Pencil className="h-4 w-4" />요청 수정</button>
                            <button type="button" onClick={onClose} disabled={isSaving} className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-40 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900">닫기</button>
                            <button type="button" onClick={() => void saveAnswer()} disabled={!request || isSaving} className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-50 dark:bg-blue-600 dark:text-white dark:hover:bg-blue-700">{isSaving && <LoaderCircle className="h-4 w-4 animate-spin" />}{isSaving ? '저장 중...' : '처리 결과 저장'}</button>
                        </>
                    )}
                </div>
            </DraggableModal>
        </div>
    );
};

const ModalHeader = ({ id, title, description, onClose, disabled = false }: { id: string; title: string; description: string; onClose: () => void; disabled?: boolean }) => (
    <div data-modal-drag-handle className="flex shrink-0 cursor-move select-none touch-none items-start justify-between gap-4 border-b border-slate-200 px-5 py-4 dark:border-slate-800">
        <div><h2 id={id} className="text-base font-bold text-slate-900 dark:text-slate-50">{title}</h2><p className="mt-1 text-xs text-slate-500 dark:text-slate-400">{description}</p></div>
        <button type="button" onClick={onClose} disabled={disabled} aria-label="닫기" className="shrink-0 rounded-lg p-2 text-slate-400 hover:bg-slate-100 disabled:opacity-40 dark:text-slate-400 dark:hover:bg-slate-900"><X className="h-5 w-5" /></button>
    </div>
);

const SummaryCard = ({ label, value, accent = 'text-slate-900 dark:text-slate-50' }: { label: string; value: string; accent?: string }) => <div className="border-b border-slate-200 p-4 last:border-b-0 sm:border-b-0 sm:border-r sm:last:border-r-0 dark:border-slate-800 md:p-5"><p className="text-xs font-medium text-slate-400 dark:text-slate-400">{label}</p><p className={`mt-1 text-xl font-bold ${accent}`}>{value}</p></div>;
const PageButton = ({ disabled, onClick, label, children }: { disabled: boolean; onClick: () => void; label: string; children: ReactNode }) => <button type="button" disabled={disabled} onClick={onClick} aria-label={label} className="flex h-8 w-8 items-center justify-center rounded-lg border border-slate-200 text-slate-500 hover:bg-slate-50 disabled:opacity-40 dark:border-slate-800 dark:text-slate-400 dark:hover:bg-slate-900">{children}</button>;
const Field = ({ label, required = false, children }: { label: string; required?: boolean; children: ReactNode }) => <label className="block space-y-1.5 text-sm font-semibold text-slate-700 dark:text-slate-200"><span>{label}{required && <span className="ml-1 text-rose-500 dark:text-rose-400">*</span>}</span>{children}</label>;

const FilePicker = ({ files, onChange, disabled = false }: { files: File[]; onChange: (files: File[]) => void; disabled?: boolean }) => (
    <div className="space-y-2">
        <div className="flex items-center justify-between gap-3"><span className="text-sm font-semibold text-slate-700 dark:text-slate-200">첨부파일</span><span className="text-xs text-slate-400 dark:text-slate-400">최대 5개 · 합계 20MB</span></div>
        <input type="file" multiple disabled={disabled} onChange={(event) => onChange(Array.from(event.currentTarget.files ?? []).slice(0, 5))} className="block w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm font-normal text-slate-600 file:mr-3 file:rounded-md file:border-0 file:bg-slate-100 file:px-3 file:py-1.5 file:text-xs file:font-semibold file:text-slate-700 hover:file:bg-slate-200 disabled:opacity-50 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-300 dark:file:bg-slate-800 dark:file:text-slate-200 dark:hover:file:bg-slate-700" />
        {files.length > 0 && <div className="space-y-2">{files.map((file) => <div key={`${file.name}-${file.lastModified}`} className="flex items-center justify-between gap-3 rounded-lg border border-slate-200 px-3 py-2 text-xs dark:border-slate-800"><span className="min-w-0 truncate text-slate-600 dark:text-slate-300">{file.name} ({formatBytes(file.size)})</span><button type="button" disabled={disabled} onClick={() => onChange(files.filter((item) => item !== file))} className="shrink-0 font-semibold text-rose-600 hover:underline disabled:opacity-40 dark:text-rose-400">제외</button></div>)}</div>}
    </div>
);

const DetailValue = ({ label, value, children }: { label: string; value?: string; children?: ReactNode }) => <div><p className="text-[11px] font-semibold text-slate-400 dark:text-slate-400">{label}</p><div className="mt-1 font-semibold text-slate-700 dark:text-slate-200">{children ?? value ?? '-'}</div></div>;
const ContentBox = ({ title, html }: { title: string; html: string }) => <section><h3 className="mb-2 text-sm font-semibold text-slate-700 dark:text-slate-200">{title}</h3><div className="prose min-h-24 max-w-none rounded-lg border border-slate-200 bg-white p-4 text-sm dark:prose-invert dark:border-slate-800 dark:bg-slate-900" dangerouslySetInnerHTML={{ __html: html }} /></section>;

const AttachmentList = ({ title, items, onDownload }: { title: string; items: Attachment[]; onDownload: (item: Attachment) => void }) => items.length === 0 ? null : <section><h3 className="mb-2 text-sm font-semibold text-slate-700 dark:text-slate-200">{title}</h3><div className="space-y-2">{items.map((item) => <button key={item.seq} type="button" onClick={() => onDownload(item)} className="flex w-full items-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-left text-sm text-blue-600 hover:bg-blue-50 dark:border-slate-800 dark:text-blue-400 dark:hover:bg-blue-950/30"><Paperclip className="h-4 w-4 shrink-0" /><span className="min-w-0 flex-1 truncate">{item.originalFilename}</span><span className="text-xs text-slate-400">{formatBytes(item.fileSize)}</span><Download className="h-4 w-4 shrink-0" /></button>)}</div></section>;

const StatusBadge = ({ status }: { status: RequestStatus }) => <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${status === 'COMPLETED' ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300' : status === 'IN_PROGRESS' ? 'bg-blue-50 text-blue-700 dark:bg-blue-950/40 dark:text-blue-300' : 'bg-amber-50 text-amber-700 dark:bg-amber-950/40 dark:text-amber-300'}`}>{STATUS_LABEL[status]}</span>;

const pageRange = (currentPage: number, totalPages: number) => {
    const start = Math.max(1, Math.min(currentPage - 2, totalPages - 4));
    const end = Math.min(totalPages, start + 4);
    return Array.from({ length: end - start + 1 }, (_, index) => start + index);
};

const formatDateTime = (value?: string | null) => {
    if (!value) return '-';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '-';
    return new Intl.DateTimeFormat('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(date);
};

const formatBytes = (value: number) => value < 1024 ? `${value} B` : value < 1024 * 1024 ? `${(value / 1024).toFixed(1)} KB` : `${(value / 1024 / 1024).toFixed(1)} MB`;
