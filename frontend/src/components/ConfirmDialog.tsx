import { DraggableModal } from './DraggableModal';
import {
    useCallback,
    useEffect,
    useRef,
    useState,
    type KeyboardEvent,
    type ReactNode
} from 'react';
import { AlertTriangle, CircleHelp, X } from 'lucide-react';
import { ConfirmDialogContext, type ConfirmFunction, type ConfirmOptions } from './confirmDialogContext';

export const ConfirmDialogProvider = ({ children }: { children: ReactNode }) => {
    const [request, setRequest] = useState<ConfirmOptions | null>(null);
    const resolverRef = useRef<((confirmed: boolean) => void) | null>(null);
    const triggerRef = useRef<HTMLElement | null>(null);
    const dialogRef = useRef<HTMLDivElement | null>(null);

    const close = useCallback((confirmed: boolean) => {
        const resolve = resolverRef.current;
        resolverRef.current = null;
        setRequest(null);
        resolve?.(confirmed);
        window.setTimeout(() => triggerRef.current?.focus(), 0);
    }, []);

    const confirm = useCallback<ConfirmFunction>((options) => {
        resolverRef.current?.(false);
        triggerRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
        setRequest({
            tone: 'default',
            confirmText: '확인',
            cancelText: '취소',
            ...options
        });
        return new Promise<boolean>((resolve) => {
            resolverRef.current = resolve;
        });
    }, []);

    useEffect(() => () => resolverRef.current?.(false), []);

    useEffect(() => {
        if (!request) return;
        const previousOverflow = document.body.style.overflow;
        document.body.style.overflow = 'hidden';
        return () => {
            document.body.style.overflow = previousOverflow;
        };
    }, [request]);

    const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
        if (event.key === 'Escape') {
            event.preventDefault();
            close(false);
            return;
        }
        if (event.key !== 'Tab' || !dialogRef.current) return;

        const focusable = Array.from(dialogRef.current.querySelectorAll<HTMLElement>('button:not(:disabled)'));
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

    const isDanger = request?.tone === 'danger';

    return (
        <ConfirmDialogContext.Provider value={confirm}>
            {children}
            {request && (
                <div
                    className="fixed inset-0 z-[200] flex items-center justify-center bg-slate-950/55 p-4 backdrop-blur-[2px]"
                    onMouseDown={(event) => { if (event.target === event.currentTarget) close(false); }}
                >
                    <DraggableModal
                        ref={dialogRef}
                        role="alertdialog"
                        aria-modal="true"
                        aria-labelledby="confirm-dialog-title"
                        aria-describedby="confirm-dialog-message"
                        onKeyDown={handleKeyDown}
                        className="w-full max-w-md overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl dark:border-slate-700 dark:bg-slate-950"
                    >
                        <div className="flex items-start gap-4 p-5 sm:p-6">
                            <div className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-full ${isDanger ? 'bg-rose-100 text-rose-600 dark:bg-rose-950 dark:text-rose-300' : 'bg-blue-100 text-blue-600 dark:bg-blue-950 dark:text-blue-300'}`}>
                                {isDanger ? <AlertTriangle className="h-5 w-5" /> : <CircleHelp className="h-5 w-5" />}
                            </div>
                            <div className="min-w-0 flex-1">
                                <h2 id="confirm-dialog-title" data-modal-drag-handle className="cursor-move select-none touch-none pr-6 text-base font-bold text-slate-900 dark:text-slate-100">{request.title}</h2>
                                <p id="confirm-dialog-message" className="mt-2 whitespace-pre-line break-words text-sm leading-6 text-slate-600 dark:text-slate-300">{request.message}</p>
                            </div>
                            <button type="button" aria-label="닫기" onClick={() => close(false)} className="-ml-10 rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600 dark:hover:bg-slate-800 dark:hover:text-slate-200"><X className="h-4 w-4" /></button>
                        </div>
                        <div className="flex justify-end gap-2 border-t border-slate-200 bg-slate-50 px-5 py-4 dark:border-slate-800 dark:bg-slate-900/70">
                            <button
                                type="button"
                                autoFocus={isDanger}
                                onClick={() => close(false)}
                                className="rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-100 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-200 dark:hover:bg-slate-800"
                            >
                                {request.cancelText}
                            </button>
                            <button
                                type="button"
                                autoFocus={!isDanger}
                                onClick={() => close(true)}
                                className={`rounded-lg px-4 py-2 text-sm font-semibold text-white shadow-sm ${isDanger ? 'bg-rose-600 hover:bg-rose-700' : 'bg-blue-600 hover:bg-blue-700'}`}
                            >
                                {request.confirmText}
                            </button>
                        </div>
                    </DraggableModal>
                </div>
            )}
        </ConfirmDialogContext.Provider>
    );
};
