import { DraggableModal } from './DraggableModal';
import {
    useCallback,
    useEffect,
    useRef,
    useState,
    type KeyboardEvent,
    type ReactNode
} from 'react';
import { Download, ShieldAlert, X } from 'lucide-react';
import {
    ExcelDownloadDialogContext,
    type ExcelDownloadDialogFunction,
    type ExcelDownloadDialogOptions
} from './excelDownloadDialogContext';

const MIN_REASON_LENGTH = 5;
const MAX_REASON_LENGTH = 500;

export const ExcelDownloadReasonDialogProvider = ({ children }: { children: ReactNode }) => {
    const [request, setRequest] = useState<ExcelDownloadDialogOptions | null>(null);
    const [reason, setReason] = useState('');
    const [isSubmitting, setIsSubmitting] = useState(false);
    const resolverRef = useRef<((completed: boolean) => void) | null>(null);
    const triggerRef = useRef<HTMLElement | null>(null);
    const dialogRef = useRef<HTMLDivElement | null>(null);
    const textareaRef = useRef<HTMLTextAreaElement | null>(null);

    const close = useCallback((completed: boolean) => {
        if (isSubmitting) return;
        const resolve = resolverRef.current;
        resolverRef.current = null;
        setRequest(null);
        setReason('');
        resolve?.(completed);
        window.setTimeout(() => triggerRef.current?.focus(), 0);
    }, [isSubmitting]);

    const open = useCallback<ExcelDownloadDialogFunction>((options) => {
        resolverRef.current?.(false);
        triggerRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
        setReason('');
        setRequest(options);
        return new Promise<boolean>((resolve) => {
            resolverRef.current = resolve;
        });
    }, []);

    useEffect(() => () => resolverRef.current?.(false), []);

    useEffect(() => {
        if (!request) return;
        const previousOverflow = document.body.style.overflow;
        document.body.style.overflow = 'hidden';
        window.setTimeout(() => textareaRef.current?.focus(), 0);
        return () => {
            document.body.style.overflow = previousOverflow;
        };
    }, [request]);

    const submit = async () => {
        if (!request || isSubmitting) return;
        const normalizedReason = reason.trim();
        if (normalizedReason.length < MIN_REASON_LENGTH) {
            request.onError('다운로드 사유를 5자 이상 입력해 주세요.');
            textareaRef.current?.focus();
            return;
        }

        setIsSubmitting(true);
        try {
            await request.execute(normalizedReason);
            setIsSubmitting(false);
            close(true);
        } catch (error) {
            request.onError(error instanceof Error ? error.message : '엑셀 다운로드에 실패했습니다.');
            setIsSubmitting(false);
            window.setTimeout(() => textareaRef.current?.focus(), 0);
        }
    };

    const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
        if (event.key === 'Escape' && !isSubmitting) {
            event.preventDefault();
            close(false);
            return;
        }
        if (event.key !== 'Tab' || !dialogRef.current) return;

        const focusable = Array.from(dialogRef.current.querySelectorAll<HTMLElement>(
            'textarea:not(:disabled), button:not(:disabled)'
        ));
        if (focusable.length === 0) return;
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first.focus();
        }
    };

    return (
        <ExcelDownloadDialogContext.Provider value={open}>
            {children}
            {request && (
                <div
                    className="fixed inset-0 z-[210] flex items-center justify-center bg-slate-950/60 p-4 backdrop-blur-[2px]"
                    onMouseDown={(event) => {
                        if (event.target === event.currentTarget && !isSubmitting) close(false);
                    }}
                >
                    <DraggableModal
                        ref={dialogRef}
                        role="dialog"
                        aria-modal="true"
                        aria-labelledby="excel-download-dialog-title"
                        onKeyDown={handleKeyDown}
                        className="w-full max-w-xl overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl dark:border-slate-700 dark:bg-slate-950"
                    >
                        <div data-modal-drag-handle className="cursor-move select-none touch-none flex items-start gap-4 border-b border-slate-200 p-5 dark:border-slate-800 sm:p-6">
                            <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-amber-100 text-amber-700 dark:bg-amber-950 dark:text-amber-300">
                                <ShieldAlert className="h-5 w-5" />
                            </div>
                            <div className="min-w-0 flex-1">
                                <h2 id="excel-download-dialog-title" className="text-base font-bold text-slate-900 dark:text-slate-100">엑셀 다운로드 사유 입력</h2>
                                <p className="mt-1.5 text-sm leading-6 text-slate-600 dark:text-slate-300">개인정보가 포함될 수 있습니다. 업무상 필요한 다운로드 사유를 입력해 주세요.</p>
                            </div>
                            <button type="button" aria-label="닫기" disabled={isSubmitting} onClick={() => close(false)} className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600 disabled:opacity-50 dark:hover:bg-slate-800 dark:hover:text-slate-200"><X className="h-4 w-4" /></button>
                        </div>

                        <div className="space-y-4 p-5 sm:p-6">
                            <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 text-sm dark:border-slate-800 dark:bg-slate-900/60">
                                <div className="font-semibold text-slate-800 dark:text-slate-100">{request.menuName}</div>
                                {request.filters && request.filters.length > 0 && (
                                    <dl className="mt-3 grid grid-cols-[auto_1fr] gap-x-3 gap-y-1.5 text-xs">
                                        {request.filters.map((filter) => (
                                            <div key={`${filter.label}-${filter.value}`} className="contents">
                                                <dt className="text-slate-400">{filter.label}</dt>
                                                <dd className="break-words text-slate-600 dark:text-slate-300">{filter.value || '전체'}</dd>
                                            </div>
                                        ))}
                                    </dl>
                                )}
                            </div>

                            <label className="block space-y-2">
                                <span className="text-sm font-semibold text-slate-700 dark:text-slate-200">다운로드 사유 *</span>
                                <textarea
                                    ref={textareaRef}
                                    value={reason}
                                    onChange={(event) => setReason(event.target.value.slice(0, MAX_REASON_LENGTH))}
                                    disabled={isSubmitting}
                                    rows={5}
                                    maxLength={MAX_REASON_LENGTH}
                                    placeholder="예: 운영위원회 주간 보고 자료 작성을 위한 등록 현황 확인"
                                    className="w-full resize-y rounded-xl border border-slate-200 bg-white px-3.5 py-3 text-sm leading-6 text-slate-900 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/15 disabled:opacity-60 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100"
                                />
                                <span className="block text-right text-xs text-slate-400">{reason.length} / {MAX_REASON_LENGTH}</span>
                            </label>
                        </div>

                        <div className="flex justify-end gap-2 border-t border-slate-200 bg-slate-50 px-5 py-4 dark:border-slate-800 dark:bg-slate-900/70">
                            <button type="button" onClick={() => close(false)} disabled={isSubmitting} className="rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-100 disabled:opacity-50 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-200 dark:hover:bg-slate-800">취소</button>
                            <button type="button" onClick={() => void submit()} disabled={isSubmitting} className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-50">
                                <Download className="h-4 w-4" />
                                {isSubmitting ? '생성 중...' : '다운로드'}
                            </button>
                        </div>
                    </DraggableModal>
                </div>
            )}
        </ExcelDownloadDialogContext.Provider>
    );
};
