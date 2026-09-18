import { useEffect, useId, useMemo, useRef, useState, type DragEvent, type KeyboardEvent } from 'react';
import { createPortal } from 'react-dom';
import { AlertCircle, Eye, FileText, Info, LoaderCircle, Mail, Paperclip, Pencil, RotateCcw, Save, Search, Upload, Users, X } from 'lucide-react';
import { DraggableModal } from './DraggableModal';
import type { NotificationType } from './NotificationToast';
import { useConfirm } from './confirmDialogContext';
import { CkEditorRichTextEditor, type CkEditorRichTextEditorHandle } from './CkEditorRichTextEditor';
import type { MailSourceMenu } from './mailHistoryTypes';

export interface MailRecipient {
    /** Unique within the supplied list; use a source prefix when combining lists. */
    id: string;
    sourceSeq: number;
    name: string;
    email?: string | null;
    affiliation?: string | null;
}

interface MailComposeModalProps {
    isOpen: boolean;
    sourceMenu: MailSourceMenu;
    /** The list is copied on opening. Removing a recipient never mutates the caller's list. */
    recipients: readonly MailRecipient[];
    onClose: () => void;
    onNotify: (type: NotificationType, message: string) => void;
}

const MAX_FILES = 5;
const MAX_TOTAL_BYTES = 10 * 1024 * 1024;
const inputClass = 'w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm font-normal text-slate-900 placeholder:text-slate-400 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50 dark:placeholder:text-slate-400 dark:focus:border-blue-500';
const secondaryButtonClass = 'inline-flex items-center justify-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900';
const removeButtonClass = 'shrink-0 rounded-lg p-2 text-slate-400 hover:bg-rose-50 hover:text-rose-600 dark:text-slate-400 dark:hover:bg-rose-950/40 dark:hover:text-rose-400';
const fileKey = (file: File) => JSON.stringify([file.name, file.size, file.lastModified]);
const formatBytes = (bytes: number) => bytes >= 1024 * 1024
    ? `${(bytes / (1024 * 1024)).toFixed(1)} MB`
    : bytes >= 1024 ? `${(bytes / 1024).toFixed(1)} KB` : `${bytes} B`;

const hasMailContent = (html: string) => {
    const document = new DOMParser().parseFromString(html, 'text/html');
    return Boolean(document.body.textContent?.replace(/[\s\u200B-\u200D\uFEFF]/g, '') || document.body.querySelector('img[src], hr'));
};

/** Saves an immutable history snapshot without sending mail. */
export const MailComposeModal = (props: MailComposeModalProps) => props.isOpen
    ? <MailComposeSession {...props} />
    : null;

const MailComposeSession = ({ sourceMenu, recipients, onClose, onNotify }: MailComposeModalProps) => {
    const confirm = useConfirm();
    const id = useId();
    const [initialRecipients] = useState(() => recipients.map((recipient) => ({ ...recipient, email: recipient.email?.trim() ?? '' })));
    const [removedIds, setRemovedIds] = useState<Set<string>>(() => new Set());
    const [recipientQuery, setRecipientQuery] = useState('');
    const [subject, setSubject] = useState('');
    const [content, setContent] = useState('');
    const [files, setFiles] = useState<File[]>([]);
    const [isPreview, setIsPreview] = useState(false);
    const [isDraggingFiles, setIsDraggingFiles] = useState(false);
    const [isSaving, setIsSaving] = useState(false);
    const savingRef = useRef(false);
    const saveAttemptRef = useRef<{ signature: string; key: string } | null>(null);
    const dialogRef = useRef<HTMLDivElement>(null);
    const subjectRef = useRef<HTMLInputElement>(null);
    const editorRef = useRef<CkEditorRichTextEditorHandle>(null);
    const searchRef = useRef<HTMLInputElement>(null);
    const fileInputRef = useRef<HTMLInputElement>(null);
    const dragDepthRef = useRef(0);
    const closePendingRef = useRef(false);
    const hasChanges = subject.length > 0 || content.length > 0 || files.length > 0 || removedIds.size > 0;

    const recipientRows = useMemo(() => {
        const seen = new Set<string>();
        return initialRecipients.filter((recipient) => !removedIds.has(recipient.id)).map((recipient) => {
            const email = recipient.email.toLowerCase();
            const invalid = !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
            const duplicate = !invalid && seen.has(email);
            if (!invalid) seen.add(email);
            return { ...recipient, invalid, duplicate };
        });
    }, [initialRecipients, removedIds]);
    const validCount = recipientRows.filter((recipient) => !recipient.invalid && !recipient.duplicate).length;
    const invalidCount = recipientRows.filter((recipient) => recipient.invalid).length;
    const duplicateCount = recipientRows.filter((recipient) => recipient.duplicate).length;
    const query = recipientQuery.trim().toLowerCase();
    const visibleRecipients = recipientRows.filter((recipient) => [recipient.name, recipient.email, recipient.affiliation ?? '']
        .some((value) => value.toLowerCase().includes(query)));
    const totalBytes = files.reduce((total, file) => total + file.size, 0);

    useEffect(() => {
        const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
        const previousOverflow = document.body.style.overflow;
        document.body.style.overflow = 'hidden';
        dialogRef.current?.focus();
        return () => {
            document.body.style.overflow = previousOverflow;
            previousFocus?.focus();
        };
    }, []);

    const requestClose = async () => {
        if (savingRef.current || closePendingRef.current) return;
        closePendingRef.current = true;
        try {
            if ((hasChanges || editorRef.current?.hasChanges()) && !await confirm({
                title: '메일 작성 닫기',
                message: '작성한 내용과 첨부파일, 수신자 변경사항이 저장되지 않습니다. 닫으시겠습니까?',
                confirmText: '작성 취소',
                cancelText: '계속 작성',
                tone: 'danger'
            })) return;
            onClose();
        } finally {
            closePendingRef.current = false;
        }
    };

    const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
        if (event.defaultPrevented) return;
        if (event.key === 'Escape') {
            event.preventDefault();
            event.stopPropagation();
            void requestClose();
            return;
        }
        if (event.key !== 'Tab' || !dialogRef.current) return;
        const focusable = Array.from(dialogRef.current.querySelectorAll<HTMLElement>(
            'button:not(:disabled), input:not(:disabled), textarea:not(:disabled), select:not(:disabled), iframe, a[href], [tabindex="0"]'
        )).filter((element) => !element.closest('[inert]') && element.tabIndex >= 0 && element.getClientRects().length > 0);
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (!first || !last) { event.preventDefault(); return; }
        if (event.shiftKey && (document.activeElement === first || document.activeElement === dialogRef.current)) {
            event.preventDefault(); last.focus();
        } else if (!event.shiftKey && (document.activeElement === last || document.activeElement === dialogRef.current)) {
            event.preventDefault(); first.focus();
        }
    };

    const selectFiles = (incoming: FileList | null) => {
        if (savingRef.current) return;
        const next = [...files];
        let duplicateFiles = 0;
        for (const file of Array.from(incoming ?? [])) {
            if (next.some((existing) => fileKey(existing) === fileKey(file))) duplicateFiles++;
            else next.push(file);
        }
        if (next.length > MAX_FILES) {
            onNotify('error', `첨부파일은 최대 ${MAX_FILES}개까지 선택할 수 있습니다.`);
        } else if (next.reduce((total, file) => total + file.size, 0) > MAX_TOTAL_BYTES) {
            onNotify('error', '첨부파일의 전체 용량은 10MB 이하여야 합니다.');
        } else {
            setFiles(next);
            if (duplicateFiles) onNotify('info', '이미 선택한 첨부파일은 중복 추가하지 않았습니다.');
        }
        if (fileInputRef.current) fileInputRef.current.value = '';
    };

    const handleFileDragEnter = (event: DragEvent<HTMLDivElement>) => {
        event.preventDefault();
        event.stopPropagation();
        if (!event.dataTransfer.types.includes('Files')) return;
        dragDepthRef.current += 1;
        setIsDraggingFiles(true);
    };

    const handleFileDragOver = (event: DragEvent<HTMLDivElement>) => {
        event.preventDefault();
        event.stopPropagation();
        event.dataTransfer.dropEffect = event.dataTransfer.types.includes('Files') ? 'copy' : 'none';
    };

    const handleFileDragLeave = (event: DragEvent<HTMLDivElement>) => {
        event.preventDefault();
        event.stopPropagation();
        dragDepthRef.current = Math.max(0, dragDepthRef.current - 1);
        if (dragDepthRef.current === 0) setIsDraggingFiles(false);
    };

    const handleFileDrop = (event: DragEvent<HTMLDivElement>) => {
        event.preventDefault();
        event.stopPropagation();
        dragDepthRef.current = 0;
        setIsDraggingFiles(false);
        selectFiles(event.dataTransfer.files);
    };

    const validatedHtml = () => {
        // Read directly from CKEditor so edits in HTML source mode are included too.
        const html = editorRef.current?.getData() ?? content;
        if (!validCount) {
            onNotify('error', '올바른 이메일 주소가 있는 수신자가 필요합니다. 수신자 목록을 확인해 주세요.');
            searchRef.current?.focus();
        } else if (!subject.trim()) {
            onNotify('error', '메일 제목을 입력해 주세요.');
            subjectRef.current?.focus();
        } else if (!hasMailContent(html)) {
            onNotify('error', '메일 내용을 입력해 주세요.');
            editorRef.current?.focus();
        } else {
            return html;
        }
        return null;
    };
    const openPreview = () => {
        const html = validatedHtml();
        if (html === null) return;
        setContent(html);
        setIsPreview(true);
    };
    const saveHistory = async () => {
        if (savingRef.current) return;
        const html = validatedHtml();
        if (html === null) return;
        savingRef.current = true;
        setIsSaving(true);
        try {
            const payload = { sourceMenu, subject: subject.trim(), htmlContent: html, sourceSeqs: recipientRows.map((recipient) => recipient.sourceSeq) };
            const signature = JSON.stringify([payload, files.map(fileKey)]);
            if (saveAttemptRef.current?.signature !== signature) saveAttemptRef.current = { signature, key: crypto.randomUUID() };
            const body = new FormData();
            body.append('request', new Blob([JSON.stringify({ ...payload, requestKey: saveAttemptRef.current.key })], { type: 'application/json' }));
            files.forEach((file) => body.append('files', file));
            const response = await fetch('/api/admin/mail-history', { method: 'POST', body });
            if (!response.ok) throw new Error(await response.text() || '메일 이력 저장에 실패했습니다.');
            const saved = await response.json() as { seq: number; invalidCount: number; suppressionCount: number };
            onNotify('success', `메일 이력 #${saved.seq}을 저장했습니다. 실제 메일은 발송되지 않았습니다.${saved.invalidCount || saved.suppressionCount ? ` 이메일 오류 ${saved.invalidCount}건, 수신 거부 ${saved.suppressionCount}건이 기록되었습니다.` : ''}`);
            onClose();
        } catch (error) {
            onNotify('error', error instanceof Error ? error.message : '메일 이력 저장에 실패했습니다.');
        } finally {
            savingRef.current = false;
            setIsSaving(false);
        }
    };

    return createPortal(
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4 dark:bg-slate-950/60">
            <DraggableModal ref={dialogRef} role="dialog" aria-modal="true" aria-labelledby={`${id}-title`} aria-describedby={`${id}-description`}
                tabIndex={-1} onKeyDown={handleKeyDown}
                className="flex max-h-[90vh] w-full max-w-4xl flex-col overflow-hidden rounded-2xl bg-white text-slate-900 shadow-2xl outline-none dark:bg-slate-950 dark:text-slate-50">
                <div data-modal-drag-handle className="flex shrink-0 cursor-move select-none touch-none items-start justify-between gap-4 border-b border-slate-200 px-5 py-4 dark:border-slate-800">
                    <div>
                        <h2 id={`${id}-title`} className="flex items-center gap-2 text-base font-bold"><Mail className="h-5 w-5 text-blue-600 dark:text-blue-400" />메일 발송</h2>
                        <p id={`${id}-description`} className="mt-1 text-xs text-slate-500 dark:text-slate-400">선택한 수신자에게 보낼 안내메일을 작성합니다.</p>
                    </div>
                    <button type="button" disabled={isSaving} onClick={() => void requestClose()} aria-label="메일 작성 닫기" className="shrink-0 rounded-lg p-2 text-slate-400 hover:bg-slate-100 disabled:opacity-50 dark:text-slate-400 dark:hover:bg-slate-900"><X className="h-5 w-5" /></button>
                </div>

                <div inert={isSaving} className="min-h-0 space-y-5 overflow-y-auto p-5">
                    <p className="flex items-start gap-2 rounded-lg bg-blue-50 px-3 py-2.5 text-xs leading-5 text-blue-700 dark:bg-blue-950/40 dark:text-blue-300">
                        <Info className="mt-0.5 h-4 w-4 shrink-0" />이력 저장 시 제목·본문·수신자·첨부파일이 저장됩니다. 실제 메일은 발송되지 않으며, 시스템관리의 메일발송이력에서 확인할 수 있습니다. 수신자 정보는 저장 시 최신 정보로 확인합니다.
                    </p>

                    <section aria-labelledby={`${id}-recipients`} className="space-y-3">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                            <h3 id={`${id}-recipients`} className="flex items-center gap-2 text-sm font-semibold text-slate-700 dark:text-slate-200"><Users className="h-4 w-4" />수신자 <span className="text-blue-600 dark:text-blue-400">{recipientRows.length}명</span></h3>
                            <button type="button" onClick={() => { setRemovedIds(new Set()); setRecipientQuery(''); }} disabled={!removedIds.size} className={secondaryButtonClass}><RotateCcw className="h-4 w-4" />처음 선택으로 복원</button>
                        </div>
                        <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-slate-500 dark:text-slate-400" aria-live="polite">
                            <span>유효 이메일 <strong className="text-slate-700 dark:text-slate-200">{validCount}개</strong></span>
                            <span>확인 필요 <strong className={invalidCount ? 'text-amber-700 dark:text-amber-300' : 'text-slate-700 dark:text-slate-200'}>{invalidCount}명</strong></span>
                            <span>중복 이메일 <strong className="text-slate-700 dark:text-slate-200">{duplicateCount}명</strong></span>
                        </div>
                        <label className="relative block">
                            <span className="sr-only">수신자 검색</span>
                            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400 dark:text-slate-400" />
                            <input ref={searchRef} value={recipientQuery} onChange={(event) => setRecipientQuery(event.target.value)} placeholder="이름, 이메일, 소속으로 수신자 검색" className={`${inputClass} pl-9`} />
                        </label>
                        <ul aria-label="메일 수신자 목록" className="max-h-44 overflow-y-auto rounded-lg border border-slate-200 dark:border-slate-800">
                            {visibleRecipients.map((recipient) => (
                                <li key={recipient.id} className="flex items-center justify-between gap-3 border-b border-slate-200 px-3 py-2.5 last:border-b-0 dark:border-slate-800">
                                    <div className="min-w-0 space-y-0.5">
                                        <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs">
                                            <span className="break-all font-semibold">{recipient.name || '이름 없음'}</span>
                                            {recipient.affiliation && <span className="break-all text-slate-500 dark:text-slate-400">{recipient.affiliation}</span>}
                                            {recipient.invalid && <span className="inline-flex items-center gap-1 text-amber-700 dark:text-amber-300"><AlertCircle className="h-3 w-3" />{recipient.email ? '이메일 형식 확인' : '이메일 없음'}</span>}
                                            {recipient.duplicate && <span className="text-amber-700 dark:text-amber-300">중복 이메일</span>}
                                        </div>
                                        <p className="break-all text-xs text-slate-500 dark:text-slate-400">{recipient.email || '등록된 이메일이 없습니다.'}</p>
                                    </div>
                                    <button type="button" onClick={() => {
                                        setRemovedIds((current) => new Set(current).add(recipient.id));
                                        searchRef.current?.focus();
                                    }} aria-label={`${recipient.name || recipient.email || '이름 없는 수신자'} 수신자 삭제`} className={removeButtonClass}><X className="h-4 w-4" /></button>
                                </li>
                            ))}
                            {!visibleRecipients.length && <li className="px-3 py-6 text-center text-xs text-slate-500 dark:text-slate-400">{recipientRows.length ? '검색 조건에 맞는 수신자가 없습니다.' : '수신자가 없습니다. 처음 선택으로 복원하거나 목록에서 다시 선택해 주세요.'}</li>}
                        </ul>
                        <p className="text-xs text-slate-400 dark:text-slate-400">수신자 삭제는 이 메일에만 적용됩니다. 유효 이메일 수는 중복 주소를 제외한 개수입니다.</p>
                    </section>

                    {isPreview ? (
                        <section aria-label="메일 내용 미리보기" className="space-y-3">
                            <h3 className="flex items-center gap-2 text-sm font-semibold text-slate-700 dark:text-slate-200"><Eye className="h-4 w-4" />내용 미리보기</h3>
                            <article className="overflow-hidden rounded-xl border border-slate-200 dark:border-slate-800">
                                <div className="space-y-2 border-b border-slate-200 bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-900/50">
                                    <p className="break-words text-sm font-semibold">{subject}</p>
                                    <p className="text-xs text-slate-500 dark:text-slate-400">수신자 {recipientRows.length}명 · 유효 이메일 {validCount}개 · 첨부파일 {files.length}개</p>
                                </div>
                                <iframe title="메일 HTML 미리보기" sandbox="" srcDoc={content} className="h-[420px] w-full border-0 bg-white dark:bg-white" />
                            </article>
                        </section>
                    ) : (
                        <>
                            <label className="block space-y-1.5 text-sm font-semibold text-slate-700 dark:text-slate-200">
                                <span>제목 <span className="text-rose-500 dark:text-rose-400">*</span></span>
                                <input ref={subjectRef} value={subject} maxLength={500} onChange={(event) => setSubject(event.target.value)} required placeholder="안내메일 제목을 입력해 주세요" className={inputClass} />
                            </label>
                            <div role="group" aria-labelledby={`${id}-content-label`} className="space-y-1.5">
                                <span id={`${id}-content-label`} className="block text-sm font-semibold text-slate-700 dark:text-slate-200">내용 <span className="text-rose-500 dark:text-rose-400">*</span></span>
                                <CkEditorRichTextEditor
                                    ref={editorRef}
                                    initialContent={content}
                                    onChange={setContent}
                                    previewTitle="메일 HTML 미리보기"
                                    contentStyle="default"
                                    showPreviewToggle={false}
                                    onEscape={() => void requestClose()}
                                />
                            </div>
                        </>
                    )}

                    <section aria-labelledby={`${id}-files`} className="space-y-3">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                            <h3 id={`${id}-files`} className="flex items-center gap-2 text-sm font-semibold text-slate-700 dark:text-slate-200"><Paperclip className="h-4 w-4" />첨부파일 <span className="text-xs font-normal text-slate-500 dark:text-slate-400">{files.length}/{MAX_FILES}개 · {formatBytes(totalBytes)}</span></h3>
                            <input ref={fileInputRef} type="file" multiple aria-label="메일 첨부파일 선택" className="hidden" onChange={(event) => selectFiles(event.target.files)} />
                        </div>
                        <div
                            onDragEnter={handleFileDragEnter}
                            onDragOver={handleFileDragOver}
                            onDragLeave={handleFileDragLeave}
                            onDrop={handleFileDrop}
                            className={`rounded-xl border-2 border-dashed px-5 py-7 text-center transition-colors ${isDraggingFiles
                                ? 'border-blue-500 bg-blue-50 dark:border-blue-400 dark:bg-blue-950/30'
                                : 'border-slate-300 bg-slate-50 dark:border-slate-700 dark:bg-slate-900/60'}`}
                        >
                            <Upload className={`mx-auto h-8 w-8 ${isDraggingFiles ? 'text-blue-600 dark:text-blue-400' : 'text-slate-400 dark:text-slate-400'}`} />
                            <p className="mt-2 text-sm font-semibold text-slate-700 dark:text-slate-200">{isDraggingFiles ? '여기에 첨부파일을 놓으세요' : '파일을 이곳에 끌어다 놓으세요'}</p>
                            <p className="mt-1 text-xs text-slate-400 dark:text-slate-400">여러 파일을 한 번에 추가하거나 파일 선택 버튼을 이용하세요.</p>
                            <button type="button" onClick={() => fileInputRef.current?.click()}
                                className="mt-3 rounded-lg border border-slate-200 bg-white px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-100 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-200 dark:hover:bg-slate-800">파일 선택</button>
                        </div>
                        <div className="rounded-lg border border-slate-200 px-3 py-2 dark:border-slate-800">
                            {files.length ? <ul>{files.map((file) => (
                                <li key={fileKey(file)} className="flex items-center gap-2 py-1">
                                    <FileText className="h-4 w-4 shrink-0 text-slate-400 dark:text-slate-400" />
                                    <span className="min-w-0 flex-1 break-all text-xs">{file.name} <span className="text-slate-500 dark:text-slate-400">({formatBytes(file.size)})</span></span>
                                    <button type="button" onClick={(event) => {
                                        setFiles((current) => current.filter((entry) => fileKey(entry) !== fileKey(file)));
                                        event.currentTarget.closest('section')?.querySelector<HTMLButtonElement>('button')?.focus();
                                    }} aria-label={`${file.name} 첨부파일 삭제`} className={removeButtonClass}><X className="h-4 w-4" /></button>
                                </li>
                            ))}</ul> : <p className="py-3 text-center text-xs text-slate-500 dark:text-slate-400">선택한 첨부파일이 없습니다.</p>}
                        </div>
                        <p className="text-xs text-slate-400 dark:text-slate-400">최대 5개 · 전체 10MB 이하 · PDF, Office 문서, ZIP, 이미지, TXT · 이력 저장 시 함께 보관됩니다.</p>
                    </section>
                </div>

                <div className="flex shrink-0 flex-wrap justify-end gap-2 border-t border-slate-200 px-5 py-4 dark:border-slate-800">
                    <button type="button" disabled={isSaving} onClick={() => void requestClose()} className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900">닫기</button>
                    <button type="button" disabled={isSaving} onClick={() => isPreview ? setIsPreview(false) : openPreview()} className="inline-flex items-center justify-center gap-2 rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900">
                        {isPreview ? <Pencil className="h-4 w-4" /> : <Eye className="h-4 w-4" />}{isPreview ? '계속 작성' : '미리보기'}
                    </button>
                    <button type="button" disabled={isSaving} onClick={() => void saveHistory()} className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-50 dark:bg-blue-600 dark:text-white dark:hover:bg-blue-700">{isSaving ? <LoaderCircle className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}{isSaving ? '저장 중...' : '이력 저장'}</button>
                </div>
            </DraggableModal>
        </div>, document.body
    );
};
