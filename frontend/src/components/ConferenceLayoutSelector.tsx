import { useEffect, useId, useRef, useState, type KeyboardEvent, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { Check, Eye, Info, Menu, Monitor, Smartphone, X } from 'lucide-react';
import { DraggableModal } from './DraggableModal';
import { conferenceLayoutOptions, type ConferenceLayoutKey, type ConferenceLayoutOption } from './conferenceLayoutOptions';
import { defaultConferenceColorPalette, type ConferenceColorPalette } from './conferenceColorPalettes';

interface DiagramProps {
    layout: ConferenceLayoutOption;
    eventName?: string;
    compact?: boolean;
    mobile?: boolean;
    palette?: ConferenceColorPalette;
}

const DiagramBlock = ({ children, className, prominent = false, action, palette }: { children: ReactNode; className: string; prominent?: boolean; action?: 'primary' | 'accent'; palette: ConferenceColorPalette }) => (
    <div className={`absolute flex items-center justify-center overflow-hidden rounded border px-1 text-center font-medium ${action
        ? `border-transparent dark:border-transparent ${palette[action]}` : prominent
        ? palette.emphasis
        : 'border-slate-200 bg-slate-50 text-slate-500 dark:border-slate-700 dark:bg-slate-800/70 dark:text-slate-400'} ${className}`}>{children}</div>
);

export const ConferenceLayoutDiagram = ({ layout, eventName, compact = false, mobile = false, palette = defaultConferenceColorPalette }: DiagramProps) => (
    <div role="img" aria-label={`${layout.name} · ${palette.name} 화면 배치 예시: ${layout.structure}`}
        className={`relative w-full overflow-hidden rounded-lg border border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-900 ${mobile ? 'aspect-[3/4]' : 'aspect-[16/11]'} ${compact ? 'text-[9px]' : 'text-xs sm:text-sm'}`}>
        {layout.key !== 'program' || mobile ? <div className={`absolute inset-x-0 top-0 flex h-[12%] items-center justify-between gap-2 px-[5%] ${palette.primary}`}>
            <span className="truncate font-bold">{compact ? '학회 홈페이지' : eventName || '새 학회 홈페이지'}</span>
            <span className="shrink-0 opacity-90">{mobile ? <Menu className="h-4 w-4" aria-hidden="true" /> : '소개 · 일정 · 등록'}</span>
        </div> : <>
            <div className={`absolute inset-y-0 left-0 w-[25%] px-[3%] pt-[6%] ${palette.primary}`}>
                <span className="block font-bold leading-relaxed">학회<br />홈페이지</span>
                <span className="mt-[30%] block leading-loose">학회 소개<br /><span className="font-semibold underline underline-offset-4">프로그램</span><br />초청 연자<br />참가 등록</span>
            </div>
            <div className="absolute left-[29%] right-[4%] top-[5%] truncate font-bold text-slate-700 dark:text-slate-200">{compact ? '행사 안내' : eventName || '새 학회 홈페이지'}</div>
        </>}

        {layout.key === 'visual' && <>
            <DiagramBlock palette={palette} prominent className={mobile ? 'inset-x-[5%] top-[16%] h-[30%]' : 'inset-x-[5%] top-[17%] h-[35%]'}>
                <span><span className="block font-semibold">대표 이미지</span>{!compact && <span className="mt-2 block text-[11px] font-normal">학회명 · 개최일 · 장소</span>}</span>
            </DiagramBlock>
            <DiagramBlock action="primary" palette={palette} className={mobile ? 'left-[5%] top-[50%] h-[10%] w-[43%]' : 'left-[5%] top-[57%] h-[10%] w-[43%]'}>참가 등록</DiagramBlock>
            <DiagramBlock action="accent" palette={palette} className={mobile ? 'right-[5%] top-[50%] h-[10%] w-[43%]' : 'right-[5%] top-[57%] h-[10%] w-[43%]'}>초록 제출</DiagramBlock>
            <DiagramBlock palette={palette} className={mobile ? 'inset-x-[5%] top-[64%] h-[12%]' : 'left-[5%] top-[72%] h-[21%] w-[54%]'}>학회 소개</DiagramBlock>
            <DiagramBlock palette={palette} className={mobile ? 'inset-x-[5%] top-[80%] h-[14%]' : 'right-[5%] top-[72%] h-[21%] w-[32%]'}>주요 일정</DiagramBlock>
        </>}
        {layout.key === 'information' && <>
            <DiagramBlock palette={palette} className={mobile ? 'inset-x-[5%] top-[16%] h-[17%]' : 'left-[5%] top-[17%] h-[26%] w-[52%]'}>행사 안내</DiagramBlock>
            <DiagramBlock palette={palette} prominent className={mobile ? 'inset-x-[5%] top-[37%] h-[25%]' : 'right-[5%] top-[17%] h-[51%] w-[34%]'}>
                <span><span className="block font-semibold">주요 접수 일정</span>{!compact && <span className="mt-3 block text-[11px] font-normal leading-loose">Early Bird 등록<br />초록 제출 마감</span>}</span>
            </DiagramBlock>
            <DiagramBlock action="primary" palette={palette} prominent className={mobile ? 'left-[5%] top-[66%] h-[10%] w-[43%]' : 'left-[5%] top-[48%] h-[20%] w-[24%]'}>참가 등록</DiagramBlock>
            <DiagramBlock action="accent" palette={palette} prominent className={mobile ? 'right-[5%] top-[66%] h-[10%] w-[43%]' : 'left-[33%] top-[48%] h-[20%] w-[24%]'}>초록 제출</DiagramBlock>
            <DiagramBlock palette={palette} className={mobile ? 'inset-x-[5%] top-[80%] h-[14%]' : 'inset-x-[5%] top-[73%] h-[20%]'}>공지사항 · 참가 안내</DiagramBlock>
        </>}
        {layout.key === 'program' && <>
            <DiagramBlock palette={palette} className={mobile ? 'inset-x-[5%] top-[16%] h-[12%]' : 'left-[29%] right-[4%] top-[15%] h-[16%]'}>{mobile ? '행사 안내' : '개최일 · 장소'}</DiagramBlock>
            <DiagramBlock palette={palette} prominent className={mobile ? 'inset-x-[5%] top-[32%] h-[30%]' : 'left-[29%] top-[36%] h-[39%] w-[40%]'}>
                <span><span className="block font-semibold">프로그램</span><span className={`mt-2 block font-normal ${compact ? 'text-[8px]' : 'text-[11px]'}`}>Day 1 · Day 2 · Day 3</span>{!compact && <span className="mt-3 block border-t border-current/20 pt-2 text-[11px] font-normal">세션별 일정 안내</span>}</span>
            </DiagramBlock>
            <DiagramBlock palette={palette} action="accent" className={mobile ? 'inset-x-[5%] top-[66%] h-[16%]' : 'right-[4%] top-[36%] h-[39%] w-[23%]'}>초청 연자</DiagramBlock>
            <DiagramBlock action="primary" palette={palette} className={mobile ? 'inset-x-[5%] top-[86%] h-[9%]' : 'left-[29%] right-[4%] top-[80%] h-[13%]'}>참가 등록</DiagramBlock>
        </>}
    </div>
);

interface SelectorProps {
    value: ConferenceLayoutKey | null;
    onChange: (value: ConferenceLayoutKey) => void;
    onPreview: (value: ConferenceLayoutKey) => void;
    palette: ConferenceColorPalette;
}

export const ConferenceLayoutSelector = ({ value, onChange, onPreview, palette }: SelectorProps) => {
    const id = useId();
    const selected = conferenceLayoutOptions.find(option => option.key === value);
    return <div>
        <fieldset>
            <legend className="mb-3 text-sm font-semibold">1. 레이아웃 선택</legend>
            <div className="mb-4 flex flex-wrap items-center justify-between gap-2 text-xs">
                <p className="text-slate-500 dark:text-slate-400">카드를 눌러 선택하고, 크게 보기로 화면 배치를 확인하세요.</p>
                <p aria-live="polite" className="font-semibold text-blue-600 dark:text-blue-400">{selected ? `선택됨 · LAYOUT ${selected.number}` : '3가지 중 1개 선택'}</p>
            </div>
            <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
                {conferenceLayoutOptions.map(option => {
                    const isSelected = option.key === value;
                    return <article key={option.key}
                        className={`relative flex flex-col overflow-hidden rounded-xl border transition-colors focus-within:ring-2 focus-within:ring-blue-500 focus-within:ring-offset-2 dark:focus-within:ring-offset-slate-950 ${isSelected
                            ? 'border-blue-500 bg-blue-50/50 ring-1 ring-blue-500 dark:border-blue-400 dark:bg-blue-950/25 dark:ring-blue-400'
                            : 'border-slate-200 bg-white hover:border-slate-300 dark:border-slate-800 dark:bg-slate-950 dark:hover:border-slate-700'}`}>
                        <label className="flex flex-1 cursor-pointer flex-col p-3">
                            <input type="radio" name="conferenceCopyUserLayout" value={option.key} checked={isSelected} onChange={() => onChange(option.key)}
                                aria-labelledby={`${id}-${option.key}-name`} aria-describedby={`${id}-${option.key}-description`} className="sr-only" />
                            <div className="mb-2.5 flex items-center justify-between gap-2">
                                <span className="text-[10px] font-bold tracking-wider text-slate-400 dark:text-slate-500">LAYOUT {option.number}</span>
                                <span className={`flex items-center gap-1 text-[11px] font-semibold ${isSelected ? 'text-blue-600 dark:text-blue-400' : 'text-slate-400 dark:text-slate-500'}`}><span className={`flex h-4 w-4 items-center justify-center rounded-full border ${isSelected ? 'border-blue-600 bg-blue-600 text-white dark:border-blue-500 dark:bg-blue-500 dark:text-white' : 'border-slate-300 dark:border-slate-600'}`}>{isSelected && <Check className="h-3 w-3" aria-hidden="true" />}</span>{isSelected ? '선택됨' : '선택하기'}</span>
                            </div>
                            <ConferenceLayoutDiagram layout={option} palette={palette} compact />
                            <h4 id={`${id}-${option.key}-name`} className="mt-4 text-sm font-semibold">{option.name}</h4>
                            <p id={`${id}-${option.key}-description`} className="mt-1.5 text-xs leading-5 text-slate-500 dark:text-slate-400">{option.description}</p>
                            <dl className="mt-4 space-y-2 border-t border-slate-200/80 pt-3 text-[11px] dark:border-slate-800">
                                <div><dt className="text-slate-400 dark:text-slate-500">먼저 보여주는 정보</dt><dd className="mt-0.5 font-semibold text-blue-600 dark:text-blue-400">{option.emphasis}</dd></div>
                                <div><dt className="text-slate-400 dark:text-slate-500">메뉴 위치</dt><dd className="mt-0.5 text-slate-600 dark:text-slate-300">{option.navigation}</dd></div>
                            </dl>
                        </label>
                        <div className="px-3 pb-3"><button type="button" onClick={() => onPreview(option.key)} aria-label={`${option.name} 크게 보기`}
                            className="inline-flex w-full items-center justify-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-300 dark:hover:bg-slate-900"><Eye className="h-3.5 w-3.5" aria-hidden="true" />크게 보기</button></div>
                    </article>;
                })}
            </div>
        </fieldset>
        <div className="mt-5 rounded-lg bg-slate-50 p-4 dark:bg-slate-900/50" aria-live="polite">
            <p className="text-xs font-semibold">{selected ? `${selected.name}은 이럴 때 어울립니다` : '어떤 레이아웃을 고르면 좋을까요?'}</p>
            <p className="mt-1.5 text-xs leading-5 text-slate-500 dark:text-slate-400">{selected ? selected.suitableFor : '행사 소개가 중요하면 01, 등록·제출 안내가 중요하면 02, 프로그램 탐색이 중요하면 03을 살펴보세요.'}</p>
        </div>
        <p className="mt-4 flex items-start gap-2 text-xs leading-5 text-slate-500 dark:text-slate-400"><Info className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" /><span>세 가지 안은 화면 배치를 비교하기 위한 예시입니다. 실제 디자인은 추후 확정하며, 지금 선택한 레이아웃은 최종 확인 단계에서 다시 볼 수 있습니다.</span></p>
    </div>;
};

interface PreviewProps {
    layout: ConferenceLayoutOption;
    eventName: string;
    selected: boolean;
    onSelect: () => void;
    onClose: () => void;
    palette: ConferenceColorPalette;
}

export const ConferenceLayoutPreviewModal = ({ layout, eventName, selected, onSelect, onClose, palette }: PreviewProps) => {
    const id = useId();
    const modalRef = useRef<HTMLDivElement>(null);
    const [device, setDevice] = useState<'desktop' | 'mobile'>('desktop');
    useEffect(() => {
        const trigger = document.activeElement instanceof HTMLElement ? document.activeElement : null;
        modalRef.current?.querySelector<HTMLButtonElement>('button')?.focus();
        return () => { if (trigger?.isConnected) trigger.focus(); };
    }, []);

    const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
        if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); onClose(); return; }
        if (event.key !== 'Tab') return;
        event.stopPropagation();
        const controls = Array.from(modalRef.current?.querySelectorAll<HTMLButtonElement>('button:not(:disabled)') ?? []);
        if (event.shiftKey && document.activeElement === controls[0]) { event.preventDefault(); controls.at(-1)?.focus(); }
        if (!event.shiftKey && document.activeElement === controls.at(-1)) { event.preventDefault(); controls[0]?.focus(); }
    };

    return createPortal(<div className="fixed inset-0 z-[100] flex items-center justify-center bg-slate-950/65 p-3 dark:bg-slate-950/75 sm:p-6">
        <DraggableModal ref={modalRef} role="dialog" aria-modal="true" aria-labelledby={`${id}-title`} aria-describedby={`${id}-description`} onKeyDown={handleKeyDown}
            className="flex max-h-[90dvh] w-full max-w-4xl flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white text-slate-900 shadow-2xl dark:border-slate-700 dark:bg-slate-950 dark:text-slate-50">
            <header data-modal-drag-handle className="flex shrink-0 cursor-move touch-none select-none items-start justify-between gap-4 border-b border-slate-200 px-5 py-4 dark:border-slate-800">
                <div><h3 id={`${id}-title`} className="text-base font-bold">LAYOUT {layout.number} · {layout.name}</h3><p id={`${id}-description`} className="mt-1 text-xs text-slate-500 dark:text-slate-400">{layout.description}</p></div>
                <button type="button" onClick={onClose} aria-label="레이아웃 미리보기 닫기" className="shrink-0 rounded-lg p-2 text-slate-400 hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-900"><X className="h-5 w-5" /></button>
            </header>
            <div className="min-h-0 overflow-y-auto overscroll-contain p-5">
                <div className="mb-4 flex flex-wrap items-center justify-between gap-3"><span className="text-xs font-semibold text-slate-500 dark:text-slate-400">화면 배치 예시</span><div className="flex gap-1 rounded-lg bg-slate-100 p-1 dark:bg-slate-900" role="group" aria-label="미리보기 화면 크기">
                    {(['desktop', 'mobile'] as const).map(mode => <button key={mode} type="button" aria-pressed={device === mode} onClick={() => setDevice(mode)} className={`inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-semibold ${device === mode ? 'bg-white text-blue-600 shadow-sm dark:bg-slate-800 dark:text-blue-400' : 'text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200'}`}>{mode === 'desktop' ? <Monitor className="h-3.5 w-3.5" aria-hidden="true" /> : <Smartphone className="h-3.5 w-3.5" aria-hidden="true" />}{mode === 'desktop' ? 'PC' : '모바일'}</button>)}
                </div></div>
                <div className="mb-3 flex flex-wrap items-center justify-center gap-2 text-xs"><span className="inline-flex overflow-hidden rounded border border-slate-200 dark:border-slate-600" aria-hidden="true">{[palette.primary, palette.accent, palette.surface].map((color, index) => <span key={index} className={`h-3 w-5 ${color}`} />)}</span><span className="font-semibold">{palette.name}</span></div>
                <div className={`mx-auto ${device === 'mobile' ? 'max-w-[300px]' : 'max-w-2xl'}`}><ConferenceLayoutDiagram layout={layout} palette={palette} eventName={eventName} mobile={device === 'mobile'} /></div>
                <p className="mt-4 text-center text-xs leading-5 text-slate-500 dark:text-slate-400">{layout.structure}</p>
                <p className="mt-1 text-center text-[11px] leading-5 text-slate-400 dark:text-slate-500">선택한 팔레트를 반영한 구성 예시입니다. 모바일에서는 메뉴를 접고 콘텐츠를 세로로 배치합니다.</p>
            </div>
            <footer className="flex shrink-0 justify-end gap-2 border-t border-slate-200 px-5 py-4 dark:border-slate-800">
                <button type="button" onClick={onClose} className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-600 hover:bg-slate-50 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-900">닫기</button>
                <button type="button" onClick={onSelect} className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700 dark:bg-blue-600 dark:text-white dark:hover:bg-blue-700"><Check className="h-4 w-4" aria-hidden="true" />{selected ? '선택 유지' : '이 레이아웃 선택'}</button>
            </footer>
        </DraggableModal>
    </div>, document.body);
};
