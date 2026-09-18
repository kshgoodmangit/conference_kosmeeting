import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { createPortal } from 'react-dom';
import { Globe, LoaderCircle, Network, Send, ShieldCheck, X } from 'lucide-react';
import { DraggableModalForm } from './DraggableModal';
import type { NotificationType } from './NotificationToast';

interface AdminAccessRequestModalProps {
    clientIp: string;
    onClose: () => void;
    onNotify: (type: NotificationType, message: string) => void;
}

const inputClassName = 'w-full min-w-0 rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm font-normal text-slate-900 placeholder:text-slate-400 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50 dark:placeholder:text-slate-400';
const labelClassName = 'block min-w-0 space-y-1.5 text-sm font-semibold text-slate-700 dark:text-slate-200';

const formatMobileContact = (value: string): string => {
    const digits = value.replace(/\D/g, '').slice(0, 11);
    if (digits.length <= 3) return digits;
    if (digits.length <= 7) return `${digits.slice(0, 3)}-${digits.slice(3)}`;
    const middleEnd = digits.length === 11 ? 7 : 6;
    return `${digits.slice(0, 3)}-${digits.slice(3, middleEnd)}-${digits.slice(middleEnd)}`;
};

const localDate = () => {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
};

export const AdminAccessRequestModal = ({ clientIp, onClose, onNotify }: AdminAccessRequestModalProps) => {
    const [affiliation, setAffiliation] = useState('');
    const [name, setName] = useState('');
    const [contact, setContact] = useState('');
    const [startDate, setStartDate] = useState(localDate);
    const [endDate, setEndDate] = useState('');
    const [purpose, setPurpose] = useState('');
    const [requestInfo, setRequestInfo] = useState<{ domain: string; clientIp: string } | null>(null);
    const [infoFailed, setInfoFailed] = useState(false);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const submittingRef = useRef(false);
    const notifyRef = useRef(onNotify);
    const formRef = useRef<HTMLFormElement>(null);
    const affiliationRef = useRef<HTMLInputElement>(null);
    const today = localDate();

    useEffect(() => { notifyRef.current = onNotify; }, [onNotify]);
    useEffect(() => {
        const controller = new AbortController();
        void (async () => {
            try {
                const response = await fetch('/api/access-requests/info', { signal: controller.signal, cache: 'no-store' });
                if (!response.ok) throw new Error(await response.text() || '접수 정보를 불러오지 못했습니다.');
                const info = await response.json();
                if (!controller.signal.aborted) setRequestInfo(info);
            } catch (error) {
                if (controller.signal.aborted) return;
                setInfoFailed(true);
                notifyRef.current('error', error instanceof Error ? error.message : '접수 정보를 불러오지 못했습니다.');
            }
        })();
        return () => controller.abort();
    }, []);

    useEffect(() => {
        const trigger = document.activeElement instanceof HTMLElement ? document.activeElement : null;
        const previousOverflow = document.body.style.overflow;
        document.body.style.overflow = 'hidden';
        affiliationRef.current?.focus();
        return () => {
            document.body.style.overflow = previousOverflow;
            trigger?.focus();
        };
    }, []);

    const handleKeyDown = (event: KeyboardEvent<HTMLFormElement>) => {
        if (event.key === 'Escape') {
            event.preventDefault();
            if (!submittingRef.current) onClose();
        }
        if (event.key !== 'Tab') return;
        const elements = Array.from(formRef.current?.querySelectorAll<HTMLElement>(
            'button:not(:disabled), input:not(:disabled), textarea:not(:disabled)'
        ) ?? []);
        const first = elements[0];
        const last = elements[elements.length - 1];
        if (!first) {
            event.preventDefault();
            formRef.current?.focus();
            return;
        }
        if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last?.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first?.focus();
        }
    };

    const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (submittingRef.current || !requestInfo) return;
        const fail = (message: string, field: string) => {
            onNotify('error', message);
            formRef.current?.querySelector<HTMLElement>(`[name="${field}"]`)?.focus();
        };
        if (!affiliation.trim()) return fail('소속을 입력해 주세요.', 'affiliation');
        if (!name.trim()) return fail('이름을 입력해 주세요.', 'name');
        if (!contact.trim()) return fail('연락처를 입력해 주세요.', 'contact');
        if (!/^[+\d\s().-]+$/.test(contact.trim()) || contact.replace(/\D/g, '').length < 7) {
            return fail('연락 가능한 전화번호를 입력해 주세요.', 'contact');
        }
        if (!startDate || startDate < today) return fail('사용 시작일은 오늘 이후로 선택해 주세요.', 'startDate');
        if (!endDate || endDate < startDate) return fail('사용 종료일은 시작일 이후로 선택해 주세요.', 'endDate');
        if (!purpose.trim()) return fail('접근이 필요한 목적을 입력해 주세요.', 'purpose');
        submittingRef.current = true;
        setIsSubmitting(true);
        formRef.current?.focus();
        try {
            const response = await fetch('/api/access-requests', {
                method: 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ affiliation: affiliation.trim(), name: name.trim(), contact: contact.trim(), purpose: purpose.trim(), startDate, endDate })
            });
            if (!response.ok) throw new Error(await response.text() || '요청을 접수하지 못했습니다.');
            const result = await response.json() as { duplicate: boolean; message: string };
            onNotify(result.duplicate ? 'info' : 'success', result.message);
            onClose();
        } catch (error) {
            onNotify('error', error instanceof Error ? error.message : '요청을 접수하지 못했습니다.');
        } finally {
            submittingRef.current = false;
            setIsSubmitting(false);
        }
    };

    return createPortal(
        <div className="fixed inset-0 z-[140] flex items-center justify-center bg-slate-950/60 p-4 dark:bg-slate-950/60">
            <DraggableModalForm
                ref={formRef}
                role="dialog"
                aria-modal="true"
                aria-labelledby="admin-access-request-title"
                aria-describedby="admin-access-request-description"
                tabIndex={-1}
                aria-busy={isSubmitting}
                noValidate
                onSubmit={handleSubmit}
                onKeyDown={handleKeyDown}
                className="flex max-h-[90dvh] w-full max-w-2xl flex-col overflow-hidden rounded-2xl bg-white text-slate-900 shadow-2xl dark:bg-slate-950 dark:text-slate-50"
            >
                <div data-modal-drag-handle className="flex shrink-0 cursor-move touch-none select-none items-start justify-between gap-4 border-b border-slate-200 px-5 py-4 dark:border-slate-800">
                    <div>
                        <h2 id="admin-access-request-title" className="flex items-center gap-2 text-base font-bold">
                            <ShieldCheck aria-hidden="true" className="h-5 w-5 shrink-0 text-blue-600 dark:text-blue-400" />
                            접근 허용 요청
                        </h2>
                        <p id="admin-access-request-description" className="mt-1 text-xs text-slate-500 dark:text-slate-400">관리자 페이지 접근이 필요한 사유와 사용기간을 입력해 주세요.</p>
                    </div>
                    <button type="button" onClick={onClose} disabled={isSubmitting} aria-label="접근 허용 요청 닫기" className="shrink-0 rounded-lg p-2 text-slate-400 hover:bg-slate-100 disabled:opacity-50 dark:text-slate-400 dark:hover:bg-slate-900">
                        <X className="h-5 w-5" />
                    </button>
                </div>

                <fieldset disabled={isSubmitting} className="min-h-0 min-w-0 space-y-5 overflow-y-auto p-5">
                    <dl className="grid gap-3 rounded-xl border border-slate-200 bg-slate-50 p-4 text-sm dark:border-slate-800 dark:bg-slate-900 sm:grid-cols-2">
                        <div className="min-w-0">
                            <dt className="flex items-center gap-1.5 text-xs text-slate-500 dark:text-slate-400"><Globe aria-hidden="true" className="h-4 w-4" />도메인</dt>
                            <dd className="mt-1 break-all font-semibold">{requestInfo?.domain ?? (infoFailed ? '확인 불가' : '확인 중...')}</dd>
                        </div>
                        <div className="min-w-0">
                            <dt className="flex items-center gap-1.5 text-xs text-slate-500 dark:text-slate-400"><Network aria-hidden="true" className="h-4 w-4" />요청 IP</dt>
                            <dd className="mt-1 break-all font-mono font-semibold">{requestInfo?.clientIp ?? clientIp}</dd>
                        </div>
                    </dl>

                    <div className="grid gap-4 sm:grid-cols-2">
                        <label className={labelClassName}>
                            <span>소속 <span className="text-rose-500 dark:text-rose-400">*</span></span>
                            <input ref={affiliationRef} name="affiliation" autoComplete="organization" value={affiliation} onChange={(event) => setAffiliation(event.target.value)} maxLength={200} required placeholder="회사 또는 기관명" className={inputClassName} />
                        </label>
                        <label className={labelClassName}>
                            <span>이름 <span className="text-rose-500 dark:text-rose-400">*</span></span>
                            <input name="name" autoComplete="name" value={name} onChange={(event) => setName(event.target.value)} maxLength={100} required placeholder="요청자 이름" className={inputClassName} />
                        </label>
                        <label className={`${labelClassName} sm:col-span-2`}>
                            <span>연락처 <span className="text-rose-500 dark:text-rose-400">*</span></span>
                            <input name="contact" type="tel" inputMode="numeric" autoComplete="tel" aria-describedby="access-request-contact-help" value={contact} onChange={(event) => setContact(formatMobileContact(event.target.value))} required placeholder="010-1234-5678" className={inputClassName} />
                            <span id="access-request-contact-help" className="block text-xs font-normal text-slate-400 dark:text-slate-400">실제로 연락 가능한 전화번호를 입력해 주세요.</span>
                        </label>
                    </div>

                    <fieldset className="min-w-0">
                        <legend className="mb-2 text-sm font-semibold text-slate-700 dark:text-slate-200">사용기간 <span className="text-rose-500 dark:text-rose-400">*</span></legend>
                        <div className="grid gap-3 sm:grid-cols-2">
                            <label className={labelClassName}>
                                <span className="text-xs font-normal text-slate-500 dark:text-slate-400">시작일</span>
                                <input name="startDate" type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} min={today} max="9999-12-31" required className={`${inputClassName} [color-scheme:light] dark:[color-scheme:dark]`} />
                            </label>
                            <label className={labelClassName}>
                                <span className="text-xs font-normal text-slate-500 dark:text-slate-400">종료일</span>
                                <input name="endDate" type="date" value={endDate} onChange={(event) => setEndDate(event.target.value)} min={startDate || today} max="9999-12-31" required className={`${inputClassName} [color-scheme:light] dark:[color-scheme:dark]`} />
                            </label>
                        </div>
                        <p className="mt-1.5 text-xs text-slate-500 dark:text-slate-400">접근이 필요한 시작일과 종료일을 선택해 주세요. 당일 사용도 가능합니다.</p>
                    </fieldset>

                    <label className={labelClassName}>
                        <span>목적 <span className="text-rose-500 dark:text-rose-400">*</span></span>
                        <textarea name="purpose" value={purpose} onChange={(event) => setPurpose(event.target.value)} rows={3} maxLength={1000} required placeholder="담당 업무와 관리자 페이지 접근이 필요한 사유를 입력해 주세요." className={`${inputClassName} resize-y`} />
                        <span className="block text-right text-xs font-normal text-slate-400 dark:text-slate-400">{purpose.length} / 1,000</span>
                    </label>

                    <p className="text-xs leading-5 text-slate-500 dark:text-slate-400">요청은 유지보수 담당자에게 전달됩니다. 승인 전까지 접근은 허용되지 않으며, 같은 IP의 대기 요청은 중복 접수되지 않습니다.</p>
                </fieldset>

                <div className="flex shrink-0 justify-end gap-2 border-t border-slate-200 px-5 py-4 dark:border-slate-800">
                    <button type="button" onClick={onClose} disabled={isSubmitting} className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900">취소</button>
                    <button type="submit" disabled={isSubmitting || !requestInfo} className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-50 dark:bg-blue-600 dark:text-white dark:hover:bg-blue-700">{isSubmitting ? <LoaderCircle aria-hidden="true" className="h-4 w-4 animate-spin" /> : <Send aria-hidden="true" className="h-4 w-4" />}{isSubmitting ? '접수 중...' : '접근 허용 요청'}</button>
                </div>
            </DraggableModalForm>
        </div>,
        document.body
    );
};
