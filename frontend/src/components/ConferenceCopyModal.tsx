import { useEffect, useId, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { createPortal } from 'react-dom';
import { Copy, LoaderCircle, X } from 'lucide-react';
import { DraggableModal } from './DraggableModal';
import type { ConferenceSettings } from './ConferenceSettingsModal';
import type { NotificationType } from './NotificationToast';
import { useConfirm } from './confirmDialogContext';
import { copyDateRanges, createCopyDraft, formatCopyRange, shiftCopyDates, validateCopyDraft, type CopyDateField } from './conferenceCopyDraft';

interface ConferenceCopyModalProps {
    source: ConferenceSettings;
    onClose: () => void;
    onSuccess: () => void;
    onNotify: (type: NotificationType, message: string) => void;
}
const sections = [
    ['fees', '등록 구분과 등록비', '참가 구분 및 통화별 Early Bird·Regular 등록비'],
    ['options', '추가 등록 옵션', '옵션 이름·가격·정원·판매 일정'],
    ['menus', '홈페이지 메뉴와 게시글', '다국어 안내 페이지·메뉴·게시글과 첨부파일'],
    ['review', '초록 심사 기준', '평가항목과 점수별 안내'],
    ['program', '프로그램 구성', '행사 일자·장소·세션·발표자 정보'],
    ['speakers', '초청연자', '연자 소개와 프로필 이미지'],
    ['promotion', '팝업과 후원사', '팝업 본문·후원사 소개와 이미지']
] as const;
const inputClass = 'w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm font-normal text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50 dark:[color-scheme:dark]';

export const ConferenceCopyModal = ({ source, onClose, onSuccess, onNotify }: ConferenceCopyModalProps) => {
    const id = useId();
    const dialog = useRef<HTMLDivElement>(null);
    const submitting = useRef(false);
    const [draft, setDraft] = useState(() => createCopyDraft(source));
    const [relative, setRelative] = useState(true);
    const [selected, setSelected] = useState<string[]>(sections.map(section => section[0]));
    const [saving, setSaving] = useState(false);
    const confirm = useConfirm();
    useEffect(() => {
        const trigger = document.activeElement instanceof HTMLElement ? document.activeElement : null;
        const overflow = document.body.style.overflow;
        document.body.style.overflow = 'hidden';
        dialog.current?.querySelector<HTMLInputElement>('[name="eventName"]')?.focus();
        return () => { document.body.style.overflow = overflow; trigger?.focus(); };
    }, []);
    const keyDown = (event: KeyboardEvent<HTMLDivElement>) => {
        if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); if (!submitting.current) onClose(); }
        if (event.key !== 'Tab') return;
        const focusable = Array.from(dialog.current?.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), select:not(:disabled)') ?? []);
        const first = focusable[0]; const last = focusable.at(-1);
        if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
        else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
    };
    const changeDate = (field: CopyDateField, value: string) => setDraft(previous => field === 'eventStartDate' && relative
        ? { ...previous, ...shiftCopyDates(source, value) } : { ...previous, [field]: value });
    const submit = async (event: FormEvent) => {
        event.preventDefault();
        if (submitting.current) return;
        const error = validateCopyDraft(draft);
        if (error) { onNotify('error', error.message); dialog.current?.querySelector<HTMLInputElement>(`[name="${error.field}"]`)?.focus(); return; }
        if (!/^[a-z0-9]+(?:[-_][a-z0-9]+)*$/.test(draft.sitePath)) { onNotify('error', '학회 경로를 영문 소문자·숫자·하이픈·밑줄로 입력해 주세요.'); return; }
        submitting.current = true;
        const approved = await confirm({ title: '학회 복사', message: `${draft.eventName}\n${formatCopyRange(draft.eventStartDate, draft.eventEndDate)}\n/${draft.sitePath}\n선택한 ${selected.length}개 항목을 새 행사에 복사합니다.`, confirmText: '복사하여 생성' });
        if (!approved) { submitting.current = false; return; }
        setSaving(true);
        try {
            const settings = Object.fromEntries(Object.entries(draft).map(([key, value]) => [key, key.endsWith('Date') && value === '' ? null : value]));
            const response = await fetch(`/api/admin/conference-settings/${source.seq}/copy`, {
                method: 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ settings: { ...settings, registrationCurrency: source.registrationCurrency || 'USD' }, sections: selected })
            });
            if (!response.ok) throw new Error(await response.text() || '학회 복사에 실패했습니다.');
            const result = await response.json() as { copiedCounts: Record<string, number>; skippedFiles: string[] };
            const count = Object.values(result.copiedCounts).reduce((total, value) => total + value, 0);
            onNotify('success', `${draft.eventName} 생성 완료 · 설정과 콘텐츠 ${count}건을 복사했습니다.${result.skippedFiles.length ? ` 원본 파일이 없는 이미지·첨부 ${result.skippedFiles.length}개는 제외했습니다.` : ''}`);
            onSuccess();
        } catch (error) { onNotify('error', error instanceof Error ? error.message : '학회 복사에 실패했습니다.'); }
        finally { submitting.current = false; setSaving(false); }
    };
    return createPortal(<div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4 dark:bg-slate-950/60">
        <DraggableModal ref={dialog} role="dialog" aria-modal="true" aria-labelledby={`${id}-title`} onKeyDown={keyDown}
            className="flex max-h-[90vh] w-full max-w-2xl flex-col overflow-hidden rounded-2xl bg-white shadow-2xl dark:bg-slate-950">
            <header data-modal-drag-handle className="flex shrink-0 cursor-move touch-none select-none items-start justify-between border-b border-slate-200 px-5 py-4 dark:border-slate-800">
                <div><h2 id={`${id}-title`} className="flex items-center gap-2 text-base font-bold text-slate-900 dark:text-slate-50"><Copy className="h-5 w-5" />학회 복사</h2>
                    <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">복사 원본: {source.eventName}</p></div>
                <button type="button" disabled={saving} onClick={() => { if (!submitting.current) onClose(); }} aria-label="학회 복사 닫기" className="rounded-lg p-2 text-slate-400 hover:bg-slate-100 disabled:opacity-50 dark:text-slate-400 dark:hover:bg-slate-900"><X className="h-5 w-5" /></button>
            </header>
            <form onSubmit={event => void submit(event)} className="overflow-y-auto p-5">
                <fieldset disabled={saving} className="space-y-5">
                    <div className="grid gap-4 sm:grid-cols-2">
                        <label className="space-y-1.5 text-sm font-semibold text-slate-700 dark:text-slate-200 sm:col-span-2"><span className="block">새 학회명 *</span><input name="eventName" required maxLength={255} className={inputClass} value={draft.eventName} onChange={e => setDraft({ ...draft, eventName: e.target.value })} /></label>
                        <label className="space-y-1.5 text-sm font-semibold text-slate-700 dark:text-slate-200"><span className="block">학회 경로 *</span><input name="sitePath" required maxLength={100} placeholder="2026_135" className={inputClass} value={draft.sitePath} onChange={e => setDraft({ ...draft, sitePath: e.target.value })} /></label>
                        <label className="space-y-1.5 text-sm font-semibold text-slate-700 dark:text-slate-200"><span className="block">개최 장소</span><input name="venueAddress" maxLength={500} className={inputClass} value={draft.venueAddress} onChange={e => setDraft({ ...draft, venueAddress: e.target.value })} /></label>
                    </div>
                    <label className="flex items-center gap-2 text-sm text-slate-700 dark:text-slate-200"><input type="checkbox" checked={relative} onChange={e => setRelative(e.target.checked)} />시작일 변경 시 관련 일정을 원본과 같은 간격으로 이동</label>
                    <div className="space-y-4">{copyDateRanges.map(range => <div key={range.key} className="grid gap-4 sm:grid-cols-2">
                        <label className="space-y-1.5 text-sm font-semibold text-slate-700 dark:text-slate-200"><span className="block">{range.label} 시작일</span><input type="date" name={range.start} required={range.key === 'event'} className={inputClass} value={draft[range.start]} onChange={e => changeDate(range.start, e.target.value)} /></label>
                        <label className="space-y-1.5 text-sm font-semibold text-slate-700 dark:text-slate-200"><span className="block">{range.label} 종료일</span><input type="date" name={range.end} required={range.key === 'event'} className={inputClass} value={draft[range.end]} onChange={e => changeDate(range.end, e.target.value)} /></label>
                    </div>)}</div>
                    <div><h3 className="text-sm font-semibold text-slate-900 dark:text-slate-50">복사할 항목</h3><div className="mt-3 grid gap-3 sm:grid-cols-2">{sections.map(([key, title, description]) => <label key={key} className="flex items-start gap-2 rounded-lg border border-slate-200 p-3 text-sm text-slate-700 dark:border-slate-800 dark:text-slate-200">
                        <input type="checkbox" className="mt-0.5 h-4 w-4" checked={selected.includes(key)} onChange={e => setSelected(previous => e.target.checked ? [...previous, key] : previous.filter(value => value !== key))} />
                        <span><span className="font-semibold">{title}</span><span className="mt-1 block text-xs text-slate-500 dark:text-slate-400">{description}</span></span></label>)}</div></div>
                    <p className="text-xs leading-5 text-slate-500 dark:text-slate-400">지원 언어({draft.supportedLanguages.join(', ')})와 기본 언어({draft.defaultLanguage})는 원본을 따릅니다. 회원·사전등록·접수 초록·심사 결과는 복사하지 않습니다. 프로그램의 접수 초록 연결은 해제되며, 콘텐츠의 노출 일정도 새 개최일에 맞춰 이동합니다. 원본 파일이 없는 이미지·첨부파일과 로고 없는 후원사는 제외합니다.</p>
                </fieldset>
                <footer className="mt-5 flex justify-end gap-2 border-t border-slate-200 pt-5 dark:border-slate-800">
                    <button type="button" disabled={saving} onClick={onClose} className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-700 disabled:opacity-50 dark:border-slate-800 dark:text-slate-300">취소</button>
                    <button type="submit" disabled={saving} className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-50 dark:bg-blue-600 dark:text-white dark:hover:bg-blue-700">{saving ? <LoaderCircle className="h-4 w-4 animate-spin" /> : <Copy className="h-4 w-4" />}{saving ? '복사 중...' : '복사하여 생성'}</button>
                </footer>
            </form>
        </DraggableModal>
    </div>, document.body);
};
