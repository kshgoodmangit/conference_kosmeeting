import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react';
import { createPortal } from 'react-dom';
import {
    ArrowLeft, ArrowRight, CalendarDays, Check, CheckCheck, CheckCircle2,
    CircleDollarSign, Copy, FileCheck2, Globe2, Info,
    ListChecks, MapPin, Monitor, PanelsTopLeft, Pencil, RotateCcw, X
} from 'lucide-react';
import { DraggableModal } from './DraggableModal';
import type { ConferenceSettings } from './ConferenceSettingsModal';
import type { NotificationType } from './NotificationToast';
import { ConferenceLayoutDiagram, ConferenceLayoutPreviewModal, ConferenceLayoutSelector } from './ConferenceLayoutSelector';
import { conferenceLayoutOptions, type ConferenceLayoutKey } from './conferenceLayoutOptions';
import { defaultConferenceColorPalette } from './conferenceColorPalettes';
import { ConferencePaletteSelector, ConferencePaletteSwatches } from './ConferencePaletteSelector';
import {
    copyDateRanges, createCopyDraft, dateOffset, formatCopyDate, formatCopyRange,
    shiftCopyDates, validateCopyDraft, type CopyDateField, type CopyScheduleMode
} from './conferenceCopyDraft';

interface ConferenceCopyModalProps {
    source: ConferenceSettings;
    onClose: () => void;
    onNotify: (type: NotificationType, message: string) => void;
}

const steps = [
    { title: '새 행사 정보', mobileTitle: '행사 정보', detail: '학회명과 개최 일정' },
    { title: '홈페이지 디자인', mobileTitle: '디자인', detail: '레이아웃과 컬러 팔레트' },
    { title: '복사할 항목', mobileTitle: '복사 항목', detail: '이어 사용할 설정 선택' },
    { title: '최종 확인', mobileTitle: '최종 확인', detail: '원본과 변경 내용 비교' }
] as const;

const copySections = [
    { key: 'fees', title: '등록 구분과 등록비', description: '참가 구분, Early Bird·Regular 금액, USD·KRW 통화별 설정', icon: CircleDollarSign, group: '참가 등록', recommended: true },
    { key: 'options', title: '추가 등록 옵션', description: '부대행사 등 옵션의 이름, 가격, 정원과 1인당 신청 수량', icon: ListChecks, group: '참가 등록', recommended: true },
    { key: 'menus', title: '홈페이지 메뉴와 안내 페이지', description: '사용자 메뉴 구조, 페이지 본문과 링크 설정', icon: Globe2, group: '행사 운영', recommended: false },
    { key: 'review', title: '초록 심사 기준', description: '심사 평가항목, 배점과 표시 순서', icon: FileCheck2, group: '행사 운영', recommended: false },
    { key: 'program', title: '프로그램 구성', description: '행사 일자, 장소와 세션 구성', icon: PanelsTopLeft, group: '행사 콘텐츠', recommended: false },
    { key: 'promotion', title: '팝업과 후원사 정보', description: '안내 팝업, 후원사 소개와 이미지', icon: Monitor, group: '행사 콘텐츠', recommended: false }
] as const;

type CopySectionKey = typeof copySections[number]['key'];
const recommendedSections = (): CopySectionKey[] => ['fees', 'options'];
const inputClass = 'w-full min-w-0 rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm font-normal text-slate-900 placeholder:text-slate-400 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/10 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50 dark:[color-scheme:dark]';
const secondaryButton = 'inline-flex items-center justify-center gap-2 rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-600 hover:bg-slate-50 focus-visible:outline-2 focus-visible:outline-blue-500 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-900';
const primaryButton = 'inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-500 dark:bg-blue-600 dark:text-white dark:hover:bg-blue-700';
const linkButton = 'inline-flex shrink-0 items-center gap-1 rounded text-xs font-semibold text-blue-600 hover:underline focus-visible:outline-2 focus-visible:outline-blue-500 dark:text-blue-400';

/** UI-only preview: no source-detail requests, mutations, or persistent storage. */
export const ConferenceCopyModal = ({ source, onClose, onNotify }: ConferenceCopyModalProps) => {
    const id = useId();
    const dialogRef = useRef<HTMLDivElement>(null);
    const contentRef = useRef<HTMLDivElement>(null);
    const headingRef = useRef<HTMLHeadingElement>(null);
    const initialFocusDone = useRef(false);
    const [step, setStep] = useState(0);
    const [furthestStep, setFurthestStep] = useState(0);
    const [draft, setDraft] = useState(() => createCopyDraft(source));
    const canShift = dateOffset(source.eventStartDate, source.eventStartDate) !== null;
    const [scheduleMode, setScheduleMode] = useState<CopyScheduleMode>(canShift ? 'relative' : 'manual');
    const [selectedSections, setSelectedSections] = useState<CopySectionKey[]>(recommendedSections);
    const [selectedLayout, setSelectedLayout] = useState<ConferenceLayoutKey | null>(null);
    const [selectedPalette, setSelectedPalette] = useState(defaultConferenceColorPalette);
    const [previewLayout, setPreviewLayout] = useState<ConferenceLayoutKey | null>(null);
    const [completed, setCompleted] = useState(false);
    const selectedItems = copySections.filter(item => selectedSections.includes(item.key));
    const layoutOption = conferenceLayoutOptions.find(option => option.key === selectedLayout);
    const previewOption = conferenceLayoutOptions.find(option => option.key === previewLayout);
    const shiftDays = dateOffset(source.eventStartDate, draft.eventStartDate);
    const duration = dateOffset(draft.eventStartDate, draft.eventEndDate);

    useEffect(() => {
        const trigger = document.activeElement instanceof HTMLElement ? document.activeElement : null;
        const previousOverflow = document.body.style.overflow;
        document.body.style.overflow = 'hidden';
        dialogRef.current?.querySelector<HTMLInputElement>('[name="eventName"]')?.focus();
        return () => {
            document.body.style.overflow = previousOverflow;
            if (trigger?.isConnected) trigger.focus();
        };
    }, []);

    useEffect(() => {
        if (!initialFocusDone.current) {
            initialFocusDone.current = true;
            return;
        }
        contentRef.current?.scrollTo({ top: 0 });
        headingRef.current?.focus();
    }, [step, completed]);

    const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
        if (event.key === 'Escape') {
            event.preventDefault();
            event.stopPropagation();
            onClose();
        }
        if (event.key !== 'Tab' || !dialogRef.current) return;
        const focusable = Array.from(dialogRef.current.querySelectorAll<HTMLElement>(
            'button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex="0"]'
        )).filter(element => element.tabIndex >= 0 && element.getClientRects().length > 0);
        const first = focusable[0];
        const last = focusable.at(-1);
        if (event.shiftKey && (document.activeElement === first || document.activeElement === headingRef.current)) {
            event.preventDefault(); last?.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault(); first?.focus();
        }
    };

    const changeDate = (field: CopyDateField, value: string) => {
        setDraft(previous => field === 'eventStartDate' && scheduleMode === 'relative'
            ? { ...previous, ...shiftCopyDates(source, value) }
            : { ...previous, [field]: value });
    };

    const changeScheduleMode = (mode: CopyScheduleMode) => {
        setScheduleMode(mode);
        if (mode === 'relative') setDraft(previous => ({ ...previous, ...shiftCopyDates(source, previous.eventStartDate) }));
    };

    const navigate = (nextStep: number) => {
        if (nextStep > step) {
            const error = validateCopyDraft(draft);
            if (error) {
                onNotify('error', error.message);
                setStep(0);
                requestAnimationFrame(() => dialogRef.current?.querySelector<HTMLInputElement>(`[name="${error.field}"]`)?.focus());
                return;
            }
            if (nextStep > 1 && !selectedLayout) {
                onNotify('error', '사용자 홈페이지 레이아웃을 한 가지 선택해 주세요.');
                setStep(1);
                requestAnimationFrame(() => dialogRef.current?.querySelector<HTMLInputElement>('[name="conferenceCopyUserLayout"]')?.focus());
                return;
            }
        }
        setStep(nextStep);
        setFurthestStep(previous => Math.max(previous, nextStep));
    };

    const toggleSection = (key: CopySectionKey) => setSelectedSections(previous =>
        previous.includes(key) ? previous.filter(item => item !== key) : [...previous, key]);

    const finishPreview = () => {
        const error = validateCopyDraft(draft);
        if (error) { onNotify('error', error.message); setStep(0); return; }
        if (!selectedLayout) { onNotify('error', '사용자 홈페이지 레이아웃을 한 가지 선택해 주세요.'); setStep(1); return; }
        setCompleted(true);
        onNotify('info', '복사 화면 미리보기를 완료했습니다. 실제 학회는 생성되지 않았습니다.');
    };

    return createPortal(
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-3 dark:bg-slate-950/60 sm:p-6">
            <DraggableModal ref={dialogRef} role="dialog" aria-modal="true" aria-labelledby={`${id}-title`} aria-describedby={`${id}-description`}
                inert={previewOption ? true : undefined} aria-hidden={previewOption ? true : undefined}
                onKeyDown={handleKeyDown}
                className="flex max-h-[90dvh] w-full max-w-6xl flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white text-slate-900 shadow-2xl dark:border-slate-800 dark:bg-slate-950 dark:text-slate-50">
                <header data-modal-drag-handle className="flex shrink-0 cursor-move touch-none select-none items-start justify-between gap-4 border-b border-slate-200 px-5 py-4 dark:border-slate-800">
                    <div>
                        <h2 id={`${id}-title`} className="flex items-center gap-2 text-base font-bold"><Copy className="h-5 w-5 text-blue-600 dark:text-blue-400" aria-hidden="true" />학회 복사</h2>
                        <p id={`${id}-description`} className="mt-1 text-xs text-slate-500 dark:text-slate-400">기존 행사의 설정을 가져와 새로운 행사를 준비합니다.</p>
                    </div>
                    <div className="flex shrink-0 items-center gap-3">
                        <span className="hidden rounded-md bg-slate-100 px-2 py-1 text-[11px] font-semibold text-slate-500 dark:bg-slate-800 dark:text-slate-400 sm:inline">화면 미리보기</span>
                        <button type="button" onClick={onClose} className="rounded-lg p-2 text-slate-400 hover:bg-slate-100 hover:text-slate-700 dark:text-slate-400 dark:hover:bg-slate-900 dark:hover:text-slate-200" aria-label="학회 복사 닫기"><X className="h-5 w-5" /></button>
                    </div>
                </header>

                <div className="flex min-h-0 flex-1 flex-col md:flex-row">
                    <aside className="shrink-0 border-b border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900/40 md:w-56 md:border-r md:border-b-0">
                        <nav aria-label="학회 복사 단계" className="p-3 md:p-5">
                            <ol className="grid grid-cols-4 gap-1 md:grid-cols-1 md:gap-3">
                                {steps.map((item, index) => {
                                    const done = completed || index < step;
                                    const active = !completed && index === step;
                                    return <li key={item.title}>
                                        <button type="button" onClick={() => navigate(index)} disabled={completed || index > furthestStep}
                                            aria-current={active ? 'step' : undefined}
                                            className={`flex w-full flex-col items-center gap-1.5 rounded-lg px-1 py-2.5 text-center focus-visible:outline-2 focus-visible:outline-blue-500 md:flex-row md:gap-3 md:px-3 md:text-left ${active ? 'bg-blue-50 text-blue-700 dark:bg-blue-950/50 dark:text-blue-300' : 'text-slate-500 enabled:hover:bg-slate-100 dark:text-slate-400 dark:enabled:hover:bg-slate-800'} disabled:cursor-default`}>
                                            <span className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-xs font-bold ${active ? 'bg-blue-600 text-white dark:bg-blue-500 dark:text-white' : done ? 'bg-blue-100 text-blue-700 dark:bg-blue-950 dark:text-blue-300' : 'border border-slate-300 text-slate-400 dark:border-slate-700 dark:text-slate-500'}`}>
                                                {done ? <Check className="h-3.5 w-3.5" aria-hidden="true" /> : index + 1}
                                            </span>
                                            <span className="min-w-0"><span className="block whitespace-nowrap text-[11px] font-semibold sm:text-xs md:text-sm"><span className="md:hidden">{item.mobileTitle}</span><span className="hidden md:inline">{item.title}</span></span><span className="mt-1 hidden text-[11px] text-slate-400 dark:text-slate-500 md:block">{item.detail}</span></span>
                                        </button>
                                    </li>;
                                })}
                            </ol>
                        </nav>
                        <div className="mx-5 mb-5 hidden border-t border-slate-200 pt-6 dark:border-slate-800 md:block">
                            <p className="text-[11px] font-semibold text-slate-400 dark:text-slate-500">복사 원본</p>
                            <p className="mt-2 break-words text-sm font-semibold leading-6">{source.eventName || '학회명 미정'}</p>
                            <p className="mt-3 flex items-start gap-2 text-xs leading-5 text-slate-500 dark:text-slate-400"><CalendarDays className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" /><span>{formatCopyDate(source.eventStartDate)}<br />– {formatCopyDate(source.eventEndDate)}</span></p>
                            <p className="mt-3 flex items-start gap-2 text-xs leading-5 text-slate-500 dark:text-slate-400"><MapPin className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" /><span className="break-words">{source.venueAddress || '장소 미정'}</span></p>
                        </div>
                    </aside>

                    <div ref={contentRef} className="min-h-0 min-w-0 flex-1 overflow-y-auto overscroll-contain">
                        <div className="px-5 py-5 sm:px-7 sm:py-6">
                            <p className="mb-5 flex items-start gap-2 text-xs leading-5 text-slate-500 dark:text-slate-400"><Info className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />화면 검토용입니다. 입력한 내용은 저장되지 않으며 실제 학회는 생성되지 않습니다.</p>
                            {completed ? <div className="py-8 text-center sm:py-14">
                                <span className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-blue-50 text-blue-600 dark:bg-blue-950 dark:text-blue-400"><CheckCheck className="h-7 w-7" aria-hidden="true" /></span>
                                <h3 ref={headingRef} tabIndex={-1} className="mt-5 text-xl font-bold outline-none">생성 화면 검토를 마쳤습니다</h3>
                                <p className="mt-3 break-words text-sm font-semibold">{draft.eventName}</p>
                                <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">{formatCopyRange(draft.eventStartDate, draft.eventEndDate)}</p>
                                {layoutOption && <p className="mt-2 text-xs font-semibold text-blue-600 dark:text-blue-400">LAYOUT {layoutOption.number} · {layoutOption.name}</p>}
                                <p className="mt-2 flex items-center justify-center gap-2 text-xs"><ConferencePaletteSwatches palette={selectedPalette} />{selectedPalette.name}</p>
                                <p className="mt-5 text-sm leading-6 text-slate-500 dark:text-slate-400">선택한 {selectedItems.length}개 항목과 행사 정보를 확인했습니다.<br />실제 복사 및 생성은 실행되지 않았습니다.</p>
                                <button type="button" className={`${secondaryButton} mt-6`} onClick={() => setCompleted(false)}><ArrowLeft className="h-4 w-4" aria-hidden="true" />검토 내용으로 돌아가기</button>
                            </div> : <>
                                <div className="mb-6">
                                    <p className="text-[11px] font-bold tracking-wider text-blue-600 dark:text-blue-400">STEP 0{step + 1}</p>
                                    <h3 ref={headingRef} tabIndex={-1} className="mt-1.5 text-lg font-bold tracking-tight outline-none sm:text-xl">{['새 행사는 언제 열리나요?', '참가자에게 어떤 화면을 보여줄까요?', '어떤 설정을 이어서 사용할까요?', '새 행사에 반영할 내용을 확인하세요'][step]}</h3>
                                    <p className="mt-2 text-xs leading-5 text-slate-500 dark:text-slate-400">{['행사 정보를 정하고, 새 개최일에 맞춰 관련 일정을 조정합니다.', '먼저 레이아웃을 고르고, 아래에서 행사에 어울리는 컬러 팔레트를 선택하세요.', '매년 반복되는 설정을 선택하고, 행사별 콘텐츠는 필요한 것만 가져옵니다.', '학회명과 일정, 홈페이지 디자인과 복사 범위를 마지막으로 확인합니다.'][step]}</p>
                                </div>

                                {step === 0 && <form noValidate onSubmit={event => { event.preventDefault(); navigate(1); }}>
                                    <div className="mb-5 border-l-2 border-slate-200 pl-3 text-xs text-slate-500 dark:border-slate-700 dark:text-slate-400 md:hidden">복사 원본 · {source.eventName || '학회명 미정'}<span className="mt-1 block">{formatCopyRange(source.eventStartDate, source.eventEndDate)}</span></div>
                                    <div className="grid gap-4 sm:grid-cols-2">
                                        <label className="block space-y-1.5 text-sm font-semibold sm:col-span-2"><span className="block">새 학회명 <span className="text-rose-500 dark:text-rose-400">*</span></span>
                                            <input name="eventName" value={draft.eventName} onChange={event => setDraft(previous => ({ ...previous, eventName: event.target.value }))} maxLength={255} className={inputClass} placeholder="예: ICMS 2027" required />
                                        </label>
                                        <label className="block space-y-1.5 text-sm font-semibold sm:col-span-2"><span className="block">행사장소</span>
                                            <input name="venueAddress" value={draft.venueAddress} onChange={event => setDraft(previous => ({ ...previous, venueAddress: event.target.value }))} maxLength={500} className={inputClass} placeholder="행사장 이름 또는 주소" />
                                        </label>
                                        {(['eventStartDate', 'eventEndDate'] as const).map((field, index) => <label key={field} className="block space-y-1.5 text-sm font-semibold"><span className="block">행사 {index === 0 ? '시작일' : '종료일'} <span className="text-rose-500 dark:text-rose-400">*</span></span>
                                            <input type="date" name={field} value={draft[field]} onChange={event => changeDate(field, event.target.value)} min="1000-01-01" max="9999-12-31" className={inputClass} required />
                                        </label>)}
                                    </div>
                                    <div className="mt-6 border-t border-slate-200 pt-5 dark:border-slate-800">
                                        <div className="flex flex-wrap items-center justify-between gap-3">
                                            <h4 className="text-sm font-semibold">등록·제출 일정</h4>
                                            <fieldset className="flex flex-wrap gap-4 text-xs text-slate-600 dark:text-slate-300">
                                                <legend className="sr-only">일정 조정 방식</legend>
                                                <label className={`flex items-center gap-1.5 ${!canShift ? 'opacity-50' : ''}`}><input type="radio" name="scheduleMode" value="relative" checked={scheduleMode === 'relative'} disabled={!canShift} onChange={() => changeScheduleMode('relative')} className="accent-blue-600 dark:accent-blue-400" />개최일에 맞춰 이동</label>
                                                <label className="flex items-center gap-1.5"><input type="radio" name="scheduleMode" value="manual" checked={scheduleMode === 'manual'} onChange={() => changeScheduleMode('manual')} className="accent-blue-600 dark:accent-blue-400" />직접 입력</label>
                                            </fieldset>
                                        </div>
                                        <p className="mt-2 text-xs leading-5 text-slate-500 dark:text-slate-400" aria-live="polite">{scheduleMode === 'relative'
                                            ? shiftDays == null ? '새 행사 시작일을 입력하면 원본의 일정 간격을 유지해 자동으로 이동합니다.' : `원본 일정에서 ${Math.abs(shiftDays)}일 ${shiftDays < 0 ? '앞당겨' : '이동하여'} 반영했습니다. 개별 조정은 직접 입력을 선택해 주세요.`
                                            : canShift ? '각 기간을 직접 입력합니다. 비워 둔 접수 기간은 미정으로 표시합니다.' : '원본 개최일이 없어 자동 이동할 수 없습니다. 각 기간을 직접 입력해 주세요.'}</p>
                                        <div className="mt-4 divide-y divide-slate-100 dark:divide-slate-800">
                                            {copyDateRanges.slice(1).map(range => <div key={range.key} className="grid gap-2 py-3 first:pt-0 sm:grid-cols-[140px_minmax(0,1fr)] sm:items-center sm:gap-4">
                                                <div><p className="text-xs font-semibold">{range.label}</p><p className="mt-1 text-[10px] leading-4 text-slate-400 dark:text-slate-500">원본 {formatCopyRange(source[range.start], source[range.end])}</p></div>
                                                <div className="grid grid-cols-2 gap-2">
                                                    {[range.start, range.end].map((field, index) => <label key={field} className="min-w-0"><span className="sr-only">{range.label} {index === 0 ? '시작일' : '종료일'}</span>
                                                        <input type="date" name={field} value={draft[field]} onChange={event => changeDate(field, event.target.value)} readOnly={scheduleMode === 'relative'} min="1000-01-01" max="9999-12-31"
                                                            className={`${inputClass} py-2 text-xs read-only:bg-slate-50 read-only:text-slate-500 dark:read-only:bg-slate-900/50 dark:read-only:text-slate-400`} />
                                                    </label>)}
                                                </div>
                                            </div>)}
                                        </div>
                                    </div>
                                    <button type="submit" className="sr-only" tabIndex={-1}>레이아웃 선택으로 이동</button>
                                </form>}

                                {step === 1 && <>
                                    <ConferenceLayoutSelector value={selectedLayout} palette={selectedPalette} onChange={setSelectedLayout} onPreview={setPreviewLayout} />
                                    {layoutOption ? <ConferencePaletteSelector value={selectedPalette} layout={layoutOption} eventName={draft.eventName}
                                        onChange={setSelectedPalette} onPreview={() => setPreviewLayout(layoutOption.key)} />
                                        : <p className="mt-5 border-t border-slate-200 pt-5 text-xs leading-5 text-slate-500 dark:border-slate-800 dark:text-slate-400">레이아웃을 선택하면 봄·가을 행사, 공공기관·의료학회를 위한 8가지 컬러 팔레트를 비교할 수 있습니다.</p>}
                                </>}

                                {step === 2 && <div>
                                    <div className="mb-5 flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 pb-4 dark:border-slate-800">
                                        <p className="text-sm font-semibold" aria-live="polite">선택 항목 <span className="ml-1 text-blue-600 dark:text-blue-400">{selectedItems.length}</span><span className="text-slate-400 dark:text-slate-500"> / {copySections.length}</span></p>
                                        <div className="flex flex-wrap gap-4"><button type="button" className={linkButton} onClick={() => setSelectedSections(copySections.map(item => item.key))}>전체 선택</button><button type="button" className={linkButton} onClick={() => setSelectedSections([])}>선택 해제</button><button type="button" className={linkButton} onClick={() => setSelectedSections(recommendedSections())}><RotateCcw className="h-3 w-3" aria-hidden="true" />기본 선택</button></div>
                                    </div>
                                    <p className="mb-5 flex items-center gap-2 text-xs text-slate-500 dark:text-slate-400"><CheckCircle2 className="h-4 w-4 text-blue-600 dark:text-blue-400" aria-hidden="true" />앞 단계에서 입력한 행사 정보와 일정은 기본으로 포함됩니다.</p>
                                    {['참가 등록', '행사 운영', '행사 콘텐츠'].map(group => <fieldset key={group} className="mb-5">
                                        <legend className="mb-2 text-[11px] font-semibold text-slate-400 dark:text-slate-500">{group}</legend>
                                        <div className="divide-y divide-slate-100 overflow-hidden rounded-xl border border-slate-200 dark:divide-slate-800 dark:border-slate-800">
                                            {copySections.filter(item => item.group === group).map(item => {
                                                const checked = selectedSections.includes(item.key);
                                                return <label key={item.key} className={`flex cursor-pointer items-start gap-3 px-4 py-3.5 transition-colors ${checked ? 'bg-blue-50/40 dark:bg-blue-950/20' : 'bg-white hover:bg-slate-50 dark:bg-slate-950 dark:hover:bg-slate-900'}`}>
                                                    <input type="checkbox" checked={checked} onChange={() => toggleSection(item.key)} className="mt-0.5 h-4 w-4 shrink-0 accent-blue-600 dark:accent-blue-400" />
                                                    <item.icon className={`mt-0.5 hidden h-4 w-4 shrink-0 sm:block ${checked ? 'text-blue-600 dark:text-blue-400' : 'text-slate-400 dark:text-slate-500'}`} aria-hidden="true" />
                                                    <span><span className="flex flex-wrap items-center gap-2 text-sm font-semibold">{item.title}{item.recommended && <span className="text-[10px] font-medium text-blue-600 dark:text-blue-400">기본 선택</span>}</span><span className="mt-1 block text-xs leading-5 text-slate-500 dark:text-slate-400">{item.description}</span></span>
                                                </label>;
                                            })}
                                        </div>
                                    </fieldset>)}
                                    <div className="border-t border-slate-200 pt-4 dark:border-slate-800"><p className="text-xs font-semibold">참가자와 접수 내역은 새로 시작합니다</p><p className="mt-1.5 text-xs leading-5 text-slate-500 dark:text-slate-400">회원, 사전등록·결제, 제출 초록, 심사 결과와 발송 이력은 복사 범위에 포함하지 않습니다.</p></div>
                                </div>}

                                {step === 3 && <div className="space-y-6">
                                    <div className="grid items-start gap-4 rounded-xl border border-slate-200 p-4 dark:border-slate-800 sm:grid-cols-[1fr_auto_1fr] sm:p-5">
                                        <div><p className="text-[11px] font-semibold text-slate-400 dark:text-slate-500">원본 학회</p><p className="mt-2 break-words text-sm font-semibold leading-6 text-slate-500 dark:text-slate-400">{source.eventName || '학회명 미정'}</p></div>
                                        <ArrowRight className="hidden h-4 w-4 text-slate-300 dark:text-slate-600 sm:mt-7 sm:block" aria-hidden="true" />
                                        <div><p className="text-[11px] font-semibold text-blue-600 dark:text-blue-400">새 학회</p><p className="mt-2 break-words text-base font-bold leading-6">{draft.eventName}</p><p className="mt-2 text-xs text-slate-500 dark:text-slate-400">{formatCopyRange(draft.eventStartDate, draft.eventEndDate)}{duration != null && duration >= 0 ? ` · ${duration + 1}일간` : ''}</p><p className="mt-1 break-words text-xs text-slate-500 dark:text-slate-400">{draft.venueAddress || '장소 미정'}</p></div>
                                    </div>
                                    {layoutOption && <section aria-label="선택한 사용자 레이아웃">
                                        <div className="mb-3 flex items-center justify-between gap-3"><h4 className="text-sm font-semibold">사용자 홈페이지 디자인</h4><button type="button" onClick={() => navigate(1)} className={linkButton}><Pencil className="h-3 w-3" aria-hidden="true" />디자인 변경</button></div>
                                        <div className="grid gap-4 rounded-lg border border-slate-200 p-4 dark:border-slate-800 sm:grid-cols-[160px_1fr] sm:items-center">
                                            <div className="max-w-48"><ConferenceLayoutDiagram layout={layoutOption} palette={selectedPalette} compact /></div>
                                            <div><p className="text-[10px] font-bold tracking-wide text-blue-600 dark:text-blue-400">LAYOUT {layoutOption.number}</p><p className="mt-1 text-sm font-semibold">{layoutOption.name}</p><p className="mt-1 text-xs leading-5 text-slate-500 dark:text-slate-400">{layoutOption.description}</p><p className="mt-3 flex flex-wrap items-center gap-2 text-xs"><ConferencePaletteSwatches palette={selectedPalette} /><span>컬러 · {selectedPalette.name}</span></p><button type="button" className={`${linkButton} mt-3`} onClick={() => setPreviewLayout(layoutOption.key)}>선택한 디자인 크게 보기</button></div>
                                        </div>
                                    </section>}
                                    <section>
                                        <div className="mb-3 flex items-center justify-between gap-3"><h4 className="text-sm font-semibold">행사 및 접수 일정</h4><button type="button" onClick={() => navigate(0)} className={linkButton}><Pencil className="h-3 w-3" aria-hidden="true" />정보 수정</button></div>
                                        <dl className="divide-y divide-slate-100 rounded-lg border border-slate-200 px-4 dark:divide-slate-800 dark:border-slate-800 lg:hidden">
                                            {copyDateRanges.map(range => <div key={range.key} className="py-3"><dt className="text-xs font-semibold">{range.label}</dt><dd className="mt-1.5 space-y-1 text-xs"><p className="text-slate-400 dark:text-slate-500">원본 · {formatCopyRange(source[range.start], source[range.end])}</p><p className="font-medium text-slate-700 dark:text-slate-200">새 일정 · {formatCopyRange(draft[range.start], draft[range.end])}</p></dd></div>)}
                                        </dl>
                                        <div className="hidden overflow-x-auto rounded-lg border border-slate-200 dark:border-slate-800 lg:block"><table className="w-full min-w-[520px] text-left text-xs">
                                            <thead className="bg-slate-50 dark:bg-slate-900"><tr><th scope="col" className="p-3">구분</th><th scope="col" className="p-3">원본 일정</th><th scope="col" className="p-3">새 일정</th></tr></thead>
                                            <tbody className="divide-y divide-slate-100 dark:divide-slate-800">{copyDateRanges.map(range => <tr key={range.key}><th scope="row" className="whitespace-nowrap p-3 font-medium">{range.label}</th><td className="whitespace-nowrap p-3 text-slate-400 dark:text-slate-500">{formatCopyRange(source[range.start], source[range.end])}</td><td className="whitespace-nowrap p-3 font-medium text-slate-700 dark:text-slate-200">{formatCopyRange(draft[range.start], draft[range.end])}</td></tr>)}</tbody>
                                        </table></div>
                                    </section>
                                    <section>
                                        <div className="mb-3 flex items-center justify-between gap-3"><h4 className="text-sm font-semibold">복사할 항목 <span className="ml-1 text-blue-600 dark:text-blue-400">{selectedItems.length}</span></h4><button type="button" onClick={() => navigate(2)} className={linkButton}><Pencil className="h-3 w-3" aria-hidden="true" />선택 변경</button></div>
                                        {selectedItems.length ? <ul className="grid gap-x-5 gap-y-3 sm:grid-cols-2">{selectedItems.map(item => <li key={item.key} className="flex items-center gap-2 text-xs"><Check className="h-4 w-4 shrink-0 text-blue-600 dark:text-blue-400" aria-hidden="true" />{item.title}</li>)}</ul> : <p className="text-xs text-slate-500 dark:text-slate-400">추가 복사 항목 없이 기본 행사 정보와 일정으로 시작합니다.</p>}
                                        {selectedSections.includes('options') && <p className="mt-3 text-xs leading-5 text-slate-500 dark:text-slate-400">등록 옵션의 개별 신청·변경 마감은 새 행사에 맞춰 별도로 확인해야 합니다.</p>}
                                        {(selectedSections.includes('menus') || selectedSections.includes('program') || selectedSections.includes('promotion')) && <p className="mt-2 text-xs leading-5 text-slate-500 dark:text-slate-400">콘텐츠에 포함된 연도, 날짜, 링크와 이미지는 새 행사 기준으로 검토해야 합니다.</p>}
                                    </section>
                                    <p className="flex items-start gap-2 border-t border-slate-200 pt-4 text-xs leading-5 text-slate-500 dark:border-slate-800 dark:text-slate-400"><Info className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" /><span>현재 운영 방식에서는 새 학회를 생성하면 공개 홈페이지의 대상 학회도 바뀝니다. 이번 미리보기는 홈페이지에 영향을 주지 않습니다.</span></p>
                                </div>}
                            </>}
                        </div>
                    </div>
                </div>

                <footer className="flex shrink-0 flex-wrap items-center justify-between gap-3 border-t border-slate-200 px-5 py-4 dark:border-slate-800">
                    <span className="text-xs text-slate-400 dark:text-slate-500">{completed ? '화면 미리보기 완료' : `${step + 1} / ${steps.length} 단계`}</span>
                    <div className="flex items-center gap-2">
                        {completed ? <button type="button" className={primaryButton} onClick={onClose}>닫기</button> : <>
                            <button type="button" className={secondaryButton} onClick={() => step === 0 ? onClose() : navigate(step - 1)}>{step === 0 ? '취소' : <><ArrowLeft className="h-4 w-4" aria-hidden="true" />이전</>}</button>
                            <button type="button" className={primaryButton} onClick={() => step === 3 ? finishPreview() : navigate(step + 1)}>
                                {step === 3 ? <><CheckCheck className="h-4 w-4" aria-hidden="true" />미리보기 완료</> : <>{['레이아웃 선택', '복사할 항목 선택', '최종 확인'][step]}<ArrowRight className="h-4 w-4" aria-hidden="true" /></>}
                            </button>
                        </>}
                    </div>
                </footer>
            </DraggableModal>
            {previewOption && <ConferenceLayoutPreviewModal
                                layout={previewOption} palette={selectedPalette} eventName={draft.eventName} selected={selectedLayout === previewOption.key}
                onClose={() => setPreviewLayout(null)}
                onSelect={() => { setSelectedLayout(previewOption.key); setPreviewLayout(null); }}
            />}
        </div>, document.body
    );
};
