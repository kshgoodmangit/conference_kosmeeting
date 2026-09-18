import { useCallback, useEffect, useRef, useState, type KeyboardEvent, type RefObject } from 'react';
import { createPortal } from 'react-dom';
import { ChevronLeft, ChevronRight, Eye, X } from 'lucide-react';
import conferenceKeynoteLandscape from '../assets/popup-samples/conference-keynote-landscape.png';
import conferenceLandscape from '../assets/popup-samples/conference-landscape.png';
import conferenceNetworking from '../assets/popup-samples/conference-networking.png';
import conferencePortrait from '../assets/popup-samples/conference-portrait.png';
import conferenceProgram from '../assets/popup-samples/conference-program.png';
import conferenceSquare from '../assets/popup-samples/conference-square.png';
import conferenceTall from '../assets/popup-samples/conference-tall.png';
import { DraggableModal } from './DraggableModal';

export type PopupLayoutKey =
    | 'popup1'
    | 'popup4'
    | 'popup6'
    | 'popup7'
    | 'popup8'
    | 'popup11'
    | 'popup12'
    | 'popup15';

export interface PopupPreviewLayout {
    key: PopupLayoutKey;
    number: string;
    name: string;
}

interface PopupLayoutPreviewModalProps {
    layout: PopupPreviewLayout | null;
    onClose: () => void;
    returnFocusRef: RefObject<HTMLButtonElement | null>;
}

const PopupFooter = ({ compact = false }: { compact?: boolean }) => (
    <div className={`flex items-center justify-between border-t border-slate-200 bg-white ${compact ? 'px-2 py-1' : 'px-3 py-2'}`}>
        <span className={`${compact ? 'h-1.5 w-16' : 'h-2 w-24'} rounded-full bg-slate-300`} />
        <span className="flex gap-1">
            <span className={`${compact ? 'h-3 w-3' : 'h-4 w-4'} rounded bg-slate-200`} />
            <span className={`${compact ? 'h-3 w-3' : 'h-4 w-4'} rounded bg-slate-300`} />
        </span>
    </div>
);

const PopupCard = ({
    src,
    className,
    imageClassName = ''
}: {
    src: string;
    className: string;
    imageClassName?: string;
}) => (
    <div className={`overflow-hidden rounded-lg border border-slate-300 bg-white shadow-2xl ${className}`}>
        <div className="relative">
            <img src={src} alt="" className={`block w-full ${imageClassName}`} />
            <span className="absolute right-2 top-2 grid h-5 w-5 place-items-center rounded-md bg-slate-950/70 text-xs text-white">×</span>
        </div>
        <PopupFooter compact />
    </div>
);

const SampleHomepage = () => (
    <div className="absolute inset-0 bg-white">
        <div className="absolute inset-x-0 top-0 h-[12%] border-b border-slate-200 bg-white">
            <div className="absolute left-[4%] top-1/2 h-[20%] w-[13%] -translate-y-1/2 rounded-full bg-blue-600" />
            <div className="absolute right-[4%] top-1/2 flex -translate-y-1/2 gap-2">
                <span className="h-2 w-12 rounded-full bg-slate-300" />
                <span className="h-2 w-12 rounded-full bg-slate-300" />
                <span className="h-2 w-12 rounded-full bg-slate-300" />
            </div>
        </div>
        <div className="absolute inset-x-0 top-[12%] h-[34%] bg-gradient-to-br from-blue-950 via-blue-800 to-cyan-700">
            <div className="absolute left-[6%] top-[24%] h-[7%] w-[28%] rounded-full bg-white/80" />
            <div className="absolute left-[6%] top-[42%] h-[4%] w-[42%] rounded-full bg-white/35" />
            <div className="absolute left-[6%] top-[54%] h-[4%] w-[34%] rounded-full bg-white/25" />
        </div>
        <div className="absolute inset-x-[5%] top-[52%] grid h-[20%] grid-cols-3 gap-[3%]">
            <div className="rounded-lg bg-slate-100" />
            <div className="rounded-lg bg-slate-100" />
            <div className="rounded-lg bg-slate-100" />
        </div>
        <div className="absolute inset-x-[5%] bottom-[7%] h-[12%] rounded-lg bg-slate-50" />
    </div>
);

const LivePopupLayout = ({ layoutKey }: { layoutKey: PopupLayoutKey }) => (
    <div className="relative aspect-video w-full overflow-hidden rounded-xl border border-slate-300 bg-white shadow-inner dark:border-slate-700">
        <SampleHomepage />

        {layoutKey === 'popup1' && <PopupCard src={conferenceLandscape} className="absolute left-[3%] top-[4%] w-[42%]" />}
        {layoutKey === 'popup11' && <PopupCard src={conferenceLandscape} className="absolute left-1/2 top-1/2 w-[42%] -translate-x-1/2 -translate-y-1/2" />}
        {layoutKey === 'popup12' && <PopupCard src={conferenceLandscape} className="absolute right-[3%] top-[4%] w-[42%]" />}

        {layoutKey === 'popup8' && (
            <div className="absolute inset-0 grid place-items-center bg-slate-950/20">
                <div className="relative h-[72%] w-[26%]">
                    <div className="absolute inset-0 translate-y-5 scale-90 overflow-hidden rounded-lg border border-slate-300 bg-white opacity-50 shadow-xl">
                        <img src={conferenceTall} alt="" className="h-full w-full object-cover" />
                    </div>
                    <div className="absolute inset-0 translate-y-2.5 scale-95 overflow-hidden rounded-lg border border-slate-300 bg-white opacity-75 shadow-xl">
                        <img src={conferencePortrait} alt="" className="h-full w-full object-cover" />
                    </div>
                    <PopupCard src={conferenceSquare} className="absolute inset-x-0 top-0" imageClassName="aspect-square object-cover" />
                </div>
            </div>
        )}

        {layoutKey === 'popup15' && (
            <div className="absolute inset-0 flex items-center justify-center gap-[2.5%] bg-slate-950/70 p-[4%]">
                <PopupCard src={conferenceSquare} className="w-[29%]" imageClassName="aspect-[4/5] object-cover" />
                <PopupCard src={conferencePortrait} className="w-[29%]" imageClassName="aspect-[4/5] object-cover" />
                <PopupCard src={conferenceTall} className="w-[29%]" imageClassName="aspect-[4/5] object-cover" />
            </div>
        )}

        {layoutKey === 'popup4' && (
            <div className="absolute inset-0 bg-slate-950/70">
                <PopupCard src={conferenceSquare} className="absolute left-[8%] top-[24%] w-[32%] -rotate-2 opacity-80" imageClassName="aspect-[4/3] object-cover" />
                <PopupCard src={conferenceTall} className="absolute right-[8%] top-[24%] w-[32%] rotate-2 opacity-80" imageClassName="aspect-[4/3] object-cover" />
                <PopupCard src={conferenceLandscape} className="absolute left-1/2 top-1/2 z-10 w-[38%] -translate-x-1/2 -translate-y-1/2" />
            </div>
        )}

        {layoutKey === 'popup7' && (
            <div className="absolute inset-0 bg-slate-950/70 [perspective:900px]">
                <PopupCard src={conferencePortrait} className="absolute left-[9%] top-[25%] w-[32%] -rotate-6 opacity-70" imageClassName="aspect-[4/3] object-cover" />
                <PopupCard src={conferenceTall} className="absolute right-[9%] top-[25%] w-[32%] rotate-6 opacity-70" imageClassName="aspect-[4/3] object-cover" />
                <PopupCard src={conferenceSquare} className="absolute left-1/2 top-1/2 z-10 w-[36%] -translate-x-1/2 -translate-y-1/2" imageClassName="aspect-[4/3] object-cover" />
                <div className="absolute bottom-[7%] left-1/2 h-2 w-[20%] -translate-x-1/2 rounded-full bg-white/25" />
            </div>
        )}

        {layoutKey === 'popup6' && (
            <div className="absolute inset-0 bg-slate-950/35">
                <aside className="absolute inset-y-0 right-0 flex w-[34%] flex-col border-l border-slate-300 bg-white shadow-2xl">
                    <div className="flex h-[11%] items-center justify-between border-b border-slate-200 px-4">
                        <span className="h-2 w-20 rounded-full bg-slate-400" />
                        <span className="text-lg text-slate-500">×</span>
                    </div>
                    <div className="min-h-0 flex-1 bg-slate-100 p-[8%]">
                        <img src={conferenceTall} alt="" className="h-full w-full rounded-md object-cover shadow-lg" />
                    </div>
                    <div className="h-[12%] border-t border-slate-200 bg-white px-4 py-3">
                        <span className="block h-2 w-24 rounded-full bg-slate-300" />
                    </div>
                </aside>
            </div>
        )}

    </div>
);

const operationalPopupSamples = [
    { src: conferenceSquare, alt: 'ICMS 2026 초록 접수 안내' },
    { src: conferenceLandscape, alt: 'ICMS 2026 조기등록 및 서울 개최 안내' },
    { src: conferencePortrait, alt: 'ICMS 2026 기조강연 안내' },
    { src: conferenceTall, alt: 'ICMS 2026 전체 프로그램 공개 안내' }
];

const deckPopupSamples = [
    { id: 'deck-popup-1', src: conferenceLandscape, alt: 'ICMS 2026 조기등록 안내 첫 번째 카드' },
    { id: 'deck-popup-2', src: conferenceLandscape, alt: 'ICMS 2026 조기등록 안내 두 번째 카드' },
    { id: 'deck-popup-3', src: conferenceLandscape, alt: 'ICMS 2026 조기등록 안내 세 번째 카드' },
    { id: 'deck-popup-4', src: conferenceLandscape, alt: 'ICMS 2026 조기등록 안내 네 번째 카드' }
];

const spatialPopupSamples = [
    { id: 'spatial-popup-1', src: conferenceLandscape, alt: 'ICMS 2026 조기등록 안내' },
    { id: 'spatial-popup-2', src: conferenceProgram, alt: 'ICMS 2026 프로그램 공개 안내' },
    { id: 'spatial-popup-3', src: conferenceKeynoteLandscape, alt: 'ICMS 2026 기조강연자 안내' },
    { id: 'spatial-popup-4', src: conferenceNetworking, alt: 'ICMS 2026 환영 리셉션 안내' }
];

const drawerPopupSamples = [
    { id: 'drawer-popup-1', src: conferenceLandscape, alt: 'ICMS 2026 조기등록 안내 가로형 팝업' },
    { id: 'drawer-popup-2', src: conferencePortrait, alt: 'ICMS 2026 기조강연 안내 세로형 팝업' },
    { id: 'drawer-popup-3', src: conferenceProgram, alt: 'ICMS 2026 프로그램 공개 안내 가로형 팝업' },
    { id: 'drawer-popup-4', src: conferenceTall, alt: 'ICMS 2026 전체 프로그램 안내 긴 세로형 팝업' }
];

type OperationalPopupVariant = 'top-left' | 'center' | 'top-right';
type SpatialPopupVariant = 'row' | 'overlay' | 'coverflow';
type DirectPreviewVariant = OperationalPopupVariant | SpatialPopupVariant | 'deck' | 'drawer';

const directPreviewVariants: Partial<Record<PopupLayoutKey, DirectPreviewVariant>> = {
    popup1: 'top-left',
    popup11: 'center',
    popup12: 'top-right',
    popup8: 'deck',
    popup15: 'row',
    popup4: 'overlay',
    popup7: 'coverflow',
    popup6: 'drawer'
};

const operationalPopupConfig: Record<OperationalPopupVariant, { title: string; description: string; positionClass: string }> = {
    'top-left': {
        title: 'ICMS 2026 좌측 상단 주요 안내',
        description: '사용자 메인 화면 왼쪽 상단의 한 개 팝업존에서 네 개의 안내 이미지가 순서대로 표시됩니다.',
        positionClass: 'left-4 top-4 sm:left-6 sm:top-6'
    },
    center: {
        title: 'ICMS 2026 중앙 주요 안내',
        description: '사용자 메인 화면 중앙의 한 개 팝업존에서 네 개의 안내 이미지가 순서대로 표시됩니다.',
        positionClass: 'left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2'
    },
    'top-right': {
        title: 'ICMS 2026 우측 상단 주요 안내',
        description: '사용자 메인 화면 오른쪽 상단의 한 개 팝업존에서 네 개의 안내 이미지가 순서대로 표시됩니다.',
        positionClass: 'right-4 top-4 sm:right-6 sm:top-6'
    }
};

const OperationalPopupPreview = ({
    dialogRef,
    onClose,
    variant
}: {
    dialogRef: RefObject<HTMLDivElement | null>;
    onClose: () => void;
    variant: OperationalPopupVariant;
}) => {
    const [activeIndex, setActiveIndex] = useState(0);
    const [isPaused, setIsPaused] = useState(false);
    const config = operationalPopupConfig[variant];

    useEffect(() => {
        if (isPaused) return;

        const timer = window.setInterval(() => {
            setActiveIndex((current) => (current + 1) % operationalPopupSamples.length);
        }, 4500);

        return () => window.clearInterval(timer);
    }, [isPaused]);

    const showPrevious = () => {
        setActiveIndex((current) => (current - 1 + operationalPopupSamples.length) % operationalPopupSamples.length);
    };

    const showNext = () => {
        setActiveIndex((current) => (current + 1) % operationalPopupSamples.length);
    };

    return (
        <div
            ref={dialogRef}
            role="dialog"
            aria-labelledby="popup-preview-title"
            aria-describedby="popup-preview-description"
            tabIndex={-1}
            onMouseEnter={() => setIsPaused(true)}
            onMouseLeave={() => setIsPaused(false)}
            className={`fixed z-[60] w-[calc(100vw-2rem)] outline-none sm:w-[min(560px,calc(100vw-3rem))] ${config.positionClass}`}
        >
            <h2 id="popup-preview-title" className="sr-only">{config.title}</h2>
            <p id="popup-preview-description" className="sr-only">{config.description}</p>

            <div className="relative z-20 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-[0_24px_80px_rgba(15,23,42,0.30)] dark:border-slate-700 dark:bg-slate-900">
                <div className="relative overflow-hidden bg-slate-950">
                <img
                    key={operationalPopupSamples[activeIndex].src}
                    src={operationalPopupSamples[activeIndex].src}
                    alt={operationalPopupSamples[activeIndex].alt}
                    className="block h-auto w-full"
                />

                <button
                    type="button"
                    onClick={onClose}
                    className="absolute right-3 top-3 grid h-9 w-9 place-items-center rounded-full bg-slate-950/75 text-white shadow-lg backdrop-blur-sm transition hover:bg-slate-950 focus:outline-none focus:ring-2 focus:ring-white"
                    aria-label="팝업 닫기"
                >
                    <X className="h-5 w-5" />
                </button>

                <button
                    type="button"
                    onClick={showPrevious}
                    className="absolute left-3 top-1/2 grid h-10 w-10 -translate-y-1/2 place-items-center rounded-full bg-slate-950/65 text-white shadow-lg backdrop-blur-sm transition hover:bg-slate-950 focus:outline-none focus:ring-2 focus:ring-white"
                    aria-label="이전 팝업"
                >
                    <ChevronLeft className="h-5 w-5" />
                </button>
                <button
                    type="button"
                    onClick={showNext}
                    className="absolute right-3 top-1/2 grid h-10 w-10 -translate-y-1/2 place-items-center rounded-full bg-slate-950/65 text-white shadow-lg backdrop-blur-sm transition hover:bg-slate-950 focus:outline-none focus:ring-2 focus:ring-white"
                    aria-label="다음 팝업"
                >
                    <ChevronRight className="h-5 w-5" />
                </button>

                <div className="absolute inset-x-0 bottom-4 flex items-center justify-center gap-2" aria-label="팝업 페이지 선택">
                    {operationalPopupSamples.map((sample, index) => (
                        <button
                            key={sample.src}
                            type="button"
                            onClick={() => setActiveIndex(index)}
                            className={`h-2 rounded-full shadow-sm transition-all focus:outline-none focus:ring-2 focus:ring-white ${
                                activeIndex === index ? 'w-6 bg-white' : 'w-2 bg-white/55 hover:bg-white/80'
                            }`}
                            aria-label={`${index + 1}번째 팝업 보기`}
                            aria-current={activeIndex === index ? 'true' : undefined}
                        />
                    ))}
                </div>
                </div>

                <footer className="flex min-h-14 items-center justify-between gap-3 border-t border-slate-200 bg-white px-3 py-2 sm:px-4 dark:border-slate-700 dark:bg-slate-900">
                    <button
                        type="button"
                        onClick={onClose}
                        className="rounded-lg px-2 py-2 text-xs font-semibold text-slate-600 transition hover:bg-slate-100 hover:text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 dark:text-slate-300 dark:hover:bg-slate-800 dark:hover:text-white"
                    >
                        오늘 하루 보지 않기
                    </button>
                    <div className="flex items-center gap-2">
                        <span
                            className="text-[11px] font-semibold tabular-nums text-slate-400"
                            aria-label={`전체 ${operationalPopupSamples.length}개 중 ${activeIndex + 1}번째 팝업`}
                        >
                            {activeIndex + 1} / {operationalPopupSamples.length}
                        </span>
                        <button
                            type="button"
                            onClick={onClose}
                            className="rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-700 transition hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-blue-500 dark:border-slate-700 dark:text-slate-200 dark:hover:bg-slate-800"
                        >
                            닫기
                        </button>
                    </div>
                </footer>
            </div>
        </div>
    );
};

const DeckPopupPreview = ({
    dialogRef,
    onClose
}: {
    dialogRef: RefObject<HTMLDivElement | null>;
    onClose: () => void;
}) => {
    const [activeIndex, setActiveIndex] = useState(0);
    const [exitingIndex, setExitingIndex] = useState<number | null>(null);
    const completionTimerRef = useRef<number | null>(null);
    const transitionLockRef = useRef(false);

    const showNextCard = useCallback(() => {
        if (transitionLockRef.current) return;

        transitionLockRef.current = true;
        setExitingIndex(activeIndex);

        completionTimerRef.current = window.setTimeout(() => {
            setActiveIndex((activeIndex + 1) % deckPopupSamples.length);
            setExitingIndex(null);
            transitionLockRef.current = false;
            completionTimerRef.current = null;
        }, 620);
    }, [activeIndex]);

    useEffect(() => () => {
        if (completionTimerRef.current !== null) window.clearTimeout(completionTimerRef.current);
        transitionLockRef.current = false;
    }, []);

    return (
        <div
            ref={dialogRef}
            role="dialog"
            aria-labelledby="popup-preview-title"
            aria-describedby="popup-preview-description"
            tabIndex={-1}
            className="fixed left-1/2 top-1/2 z-[60] w-[calc(100vw-3rem)] -translate-x-1/2 -translate-y-1/2 outline-none sm:w-[min(620px,calc(100vw-3rem))]"
        >
            <h2 id="popup-preview-title" className="sr-only">ICMS 2026 중앙 카드 덱 안내</h2>
            <p id="popup-preview-description" className="sr-only">
                사용자 메인 화면 중앙에서 네 개의 안내 카드가 포개져 표시되며 다음 카드를 선택하면 앞 카드가 오른쪽으로 넘어갑니다.
            </p>

            <div className="relative">
                <div aria-hidden="true" className="invisible">
                    <img src={deckPopupSamples[activeIndex].src} alt="" className="block h-auto w-full" />
                    <div className="h-[58px]" />
                </div>

                {deckPopupSamples.map((sample, index) => {
                    const relativeIndex = (index - activeIndex + deckPopupSamples.length) % deckPopupSamples.length;
                    const isFront = relativeIndex === 0;
                    const isExiting = exitingIndex === index;
                    const promotedRelativeIndex = exitingIndex !== null && !isExiting
                        ? Math.max(0, relativeIndex - 1)
                        : relativeIndex;
                    const cardPositionClass = isExiting
                        ? 'pointer-events-none z-40 translate-x-[14%] translate-y-1 rotate-[0.6deg] scale-[0.98] opacity-0'
                        : promotedRelativeIndex === 0
                            ? 'z-30 translate-y-0 scale-100 opacity-100'
                            : promotedRelativeIndex === 1
                                ? 'pointer-events-none z-20 translate-y-3.5 scale-[0.965] opacity-[0.78]'
                                : 'pointer-events-none z-10 translate-y-7 scale-[0.93] opacity-[0.48]';

                    return (
                        <article
                            key={sample.id}
                            aria-hidden={!isFront}
                            className={`absolute inset-x-0 top-0 will-change-transform overflow-hidden rounded-xl border border-slate-200 bg-white shadow-[0_24px_70px_rgba(15,23,42,0.28)] transition-[transform,opacity] duration-[620ms] ease-[cubic-bezier(0.4,0,0.2,1)] motion-reduce:transition-none dark:border-slate-700 dark:bg-slate-900 ${cardPositionClass}`}
                        >
                            <img src={sample.src} alt={isFront ? sample.alt : ''} className="block h-auto w-full" />
                            <footer className="flex min-h-[58px] items-center justify-between gap-2 border-t border-slate-200 bg-white p-2.5 dark:border-slate-700 dark:bg-slate-900">
                                <button
                                    type="button"
                                    onClick={onClose}
                                    tabIndex={isFront ? 0 : -1}
                                    className="rounded-lg px-2 py-2 text-xs font-semibold text-slate-600 transition hover:bg-slate-100 hover:text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 dark:text-slate-300 dark:hover:bg-slate-800 dark:hover:text-white"
                                >
                                    오늘 하루 보지 않기
                                </button>
                                <div className="flex items-center gap-2">
                                    <span className="text-[11px] font-semibold tabular-nums text-slate-400" aria-label={`전체 ${deckPopupSamples.length}개 중 ${index + 1}번째 카드`}>
                                        {index + 1} / {deckPopupSamples.length}
                                    </span>
                                    <button
                                        type="button"
                                        onClick={showNextCard}
                                        disabled={!isFront || exitingIndex !== null}
                                        tabIndex={isFront ? 0 : -1}
                                        className="min-h-9 rounded-lg bg-slate-900 px-4 py-2 text-xs font-bold text-white transition hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:cursor-wait disabled:opacity-60 dark:bg-blue-600 dark:hover:bg-blue-500"
                                    >
                                        다음 카드
                                    </button>
                                    <button
                                        type="button"
                                        onClick={onClose}
                                        tabIndex={isFront ? 0 : -1}
                                        className="grid h-9 w-9 place-items-center rounded-lg border border-slate-200 text-slate-600 transition hover:bg-slate-100 hover:text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-800 dark:hover:text-white"
                                        aria-label="팝업 닫기"
                                    >
                                        <X className="h-4 w-4" />
                                    </button>
                                </div>
                            </footer>
                        </article>
                    );
                })}
            </div>
        </div>
    );
};

const spatialPopupConfig: Record<SpatialPopupVariant, { title: string; description: string; interval: number }> = {
    row: {
        title: 'ICMS 2026 중앙 3장 나란히 안내',
        description: '같은 크기의 팝업 이미지 세 장을 겹치지 않고 화면 중앙에 나란히 표시합니다.',
        interval: 4400
    },
    overlay: {
        title: 'ICMS 2026 중앙 3장 오버레이 안내',
        description: '같은 크기의 팝업 이미지 세 장을 화면 중앙에 겹쳐 표시하고 순서대로 전환합니다.',
        interval: 4000
    },
    coverflow: {
        title: 'ICMS 2026 중앙 커버플로 안내',
        description: '같은 크기의 팝업 이미지 세 장을 입체적인 커버플로 형태로 표시하고 순서대로 전환합니다.',
        interval: 4200
    }
};

const SpatialPopupPreview = ({
    dialogRef,
    onClose,
    variant
}: {
    dialogRef: RefObject<HTMLDivElement | null>;
    onClose: () => void;
    variant: SpatialPopupVariant;
}) => {
    const [activeIndex, setActiveIndex] = useState(0);
    const [isPaused, setIsPaused] = useState(false);
    const config = spatialPopupConfig[variant];

    const move = useCallback((amount: number) => {
        setActiveIndex((current) => (current + amount + spatialPopupSamples.length) % spatialPopupSamples.length);
    }, []);

    useEffect(() => {
        if (isPaused) return;

        const timer = window.setInterval(() => move(1), config.interval);
        return () => window.clearInterval(timer);
    }, [config.interval, isPaused, move]);

    const getPositionClass = (index: number) => {
        const relative = (index - activeIndex + spatialPopupSamples.length) % spatialPopupSamples.length;
        const position = relative === 0 ? 'center' : relative === 1 ? 'right' : relative === spatialPopupSamples.length - 1 ? 'left' : 'hidden';

        if (variant === 'row') {
            if (position === 'left') return 'left-1/2 top-1/2 z-10 opacity-100 [transform:translate(-156%,-50%)]';
            if (position === 'center') return 'left-1/2 top-1/2 z-20 opacity-100 [transform:translate(-50%,-50%)]';
            if (position === 'right') return 'left-1/2 top-1/2 z-10 opacity-100 [transform:translate(56%,-50%)]';
            return 'pointer-events-none left-1/2 top-1/2 opacity-0 [transform:translate(-50%,-50%)_scale(0.9)]';
        }

        if (variant === 'overlay') {
            if (position === 'left') return 'left-1/2 top-1/2 z-10 -translate-x-[92%] -translate-y-1/2 -rotate-2 scale-[0.8] opacity-65';
            if (position === 'center') return 'left-1/2 top-1/2 z-30 -translate-x-1/2 -translate-y-1/2 scale-100 opacity-100';
            if (position === 'right') return 'left-1/2 top-1/2 z-10 -translate-x-[8%] -translate-y-1/2 rotate-2 scale-[0.8] opacity-65';
            return 'pointer-events-none left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 scale-75 opacity-0';
        }

        if (position === 'left') return 'z-10 opacity-55 [transform:translate(-88%,-50%)_rotateY(42deg)_translateZ(-130px)_scale(0.82)]';
        if (position === 'center') return 'z-30 opacity-100 [transform:translate(-50%,-50%)_rotateY(0deg)_translateZ(0)_scale(1)]';
        if (position === 'right') return 'z-10 opacity-55 [transform:translate(-12%,-50%)_rotateY(-42deg)_translateZ(-130px)_scale(0.82)]';
        return 'pointer-events-none opacity-0 [transform:translate(-50%,-50%)_translateZ(-220px)_scale(0.7)]';
    };

    const cardWidthClass = variant === 'row'
        ? 'w-[32%]'
        : variant === 'overlay'
            ? 'w-[min(620px,58vw)]'
            : 'w-[min(640px,66vw)]';

    return (
        <div
            className="fixed inset-0 z-[60] grid place-items-center overflow-hidden bg-slate-950/75 p-4 backdrop-blur-[2px] sm:p-6"
            onMouseDown={(event) => {
                if (event.target === event.currentTarget) onClose();
            }}
        >
            <div
                ref={dialogRef}
                role="dialog"
                aria-modal="true"
                aria-labelledby="popup-preview-title"
                aria-describedby="popup-preview-description"
                tabIndex={-1}
                onMouseEnter={() => setIsPaused(true)}
                onMouseLeave={() => setIsPaused(false)}
                className={`${variant === 'row' ? 'w-[min(1488px,calc(100vw-2rem))] sm:w-[min(1488px,calc(100vw-3rem))]' : 'w-[min(1240px,calc(100vw-2rem))] sm:w-[min(1240px,calc(100vw-3rem))]'} outline-none`}
            >
                <h2 id="popup-preview-title" className="sr-only">{config.title}</h2>
                <p id="popup-preview-description" className="sr-only">{config.description}</p>

                <div
                    className={`relative ${variant === 'row' ? 'aspect-[3.25/1]' : 'h-[min(66vw,650px)] min-h-[280px] [perspective:1200px] sm:h-[min(52vw,610px)]'}`}
                >
                    {spatialPopupSamples.map((sample, index) => {
                        const relative = (index - activeIndex + spatialPopupSamples.length) % spatialPopupSamples.length;
                        const isCenter = relative === 0;

                        return (
                            <article
                                key={sample.id}
                                aria-hidden={!isCenter}
                                className={`absolute overflow-hidden rounded-xl border border-white/20 bg-slate-900 shadow-[0_28px_80px_rgba(0,0,0,0.42)] transition-[transform,opacity] duration-700 ease-[cubic-bezier(0.22,1,0.36,1)] will-change-transform motion-reduce:transition-none ${variant === 'coverflow' ? 'left-1/2 top-1/2' : ''} ${cardWidthClass} ${getPositionClass(index)}`}
                            >
                                <img
                                    src={sample.src}
                                    alt={isCenter ? sample.alt : ''}
                                    className="block aspect-[4/3] w-full object-cover"
                                />
                            </article>
                        );
                    })}
                </div>

                <div className="mt-5 flex flex-wrap items-center justify-center gap-2 text-white">
                    <button
                        type="button"
                        onClick={() => move(-1)}
                        className="grid h-10 w-10 place-items-center rounded-full bg-white/12 transition hover:bg-white/20 focus:outline-none focus:ring-2 focus:ring-white"
                        aria-label="이전 팝업"
                    >
                        <ChevronLeft className="h-5 w-5" />
                    </button>
                    <span
                        className="min-w-14 text-center text-xs font-semibold tabular-nums text-white/75"
                        aria-label={`전체 ${spatialPopupSamples.length}개 중 ${activeIndex + 1}번째 팝업`}
                    >
                        {activeIndex + 1} / {spatialPopupSamples.length}
                    </span>
                    <button
                        type="button"
                        onClick={() => move(1)}
                        className="grid h-10 w-10 place-items-center rounded-full bg-white/12 transition hover:bg-white/20 focus:outline-none focus:ring-2 focus:ring-white"
                        aria-label="다음 팝업"
                    >
                        <ChevronRight className="h-5 w-5" />
                    </button>
                    <span className="mx-1 hidden h-5 w-px bg-white/20 sm:block" aria-hidden="true" />
                    <button
                        type="button"
                        onClick={onClose}
                        className="rounded-lg px-3 py-2.5 text-xs font-semibold text-white/80 transition hover:bg-white/12 hover:text-white focus:outline-none focus:ring-2 focus:ring-white"
                    >
                        오늘 하루 보지 않기
                    </button>
                    <button
                        type="button"
                        onClick={onClose}
                        className="flex min-h-10 items-center gap-1.5 rounded-lg bg-white/12 px-3 py-2 text-xs font-semibold text-white transition hover:bg-white/20 focus:outline-none focus:ring-2 focus:ring-white"
                    >
                        <X className="h-4 w-4" />
                        닫기
                    </button>
                </div>
            </div>
        </div>
    );
};

const drawerTrackPositionClasses = [
    'translate-x-0',
    '-translate-x-1/4',
    '-translate-x-[50%]',
    '-translate-x-[75%]'
];

const DrawerPopupPreview = ({
    dialogRef,
    onClose
}: {
    dialogRef: RefObject<HTMLDivElement | null>;
    onClose: () => void;
}) => {
    const [activeIndex, setActiveIndex] = useState(0);
    const [isPaused, setIsPaused] = useState(false);
    const [isVisible, setIsVisible] = useState(false);
    const closeTimerRef = useRef<number | null>(null);

    useEffect(() => {
        const frame = window.requestAnimationFrame(() => setIsVisible(true));

        return () => {
            window.cancelAnimationFrame(frame);
            if (closeTimerRef.current !== null) window.clearTimeout(closeTimerRef.current);
        };
    }, []);

    useEffect(() => {
        if (isPaused || !isVisible) return;

        const timer = window.setInterval(() => {
            setActiveIndex((current) => (current + 1) % drawerPopupSamples.length);
        }, 3900);

        return () => window.clearInterval(timer);
    }, [isPaused, isVisible]);

    const requestClose = useCallback(() => {
        if (closeTimerRef.current !== null) return;

        setIsVisible(false);
        closeTimerRef.current = window.setTimeout(onClose, 340);
    }, [onClose]);

    const move = (amount: number) => {
        setActiveIndex((current) => (current + amount + drawerPopupSamples.length) % drawerPopupSamples.length);
    };

    return (
        <div
            className={`fixed inset-0 z-[60] flex justify-end transition-colors duration-300 motion-reduce:transition-none ${isVisible ? 'bg-slate-950/40' : 'bg-slate-950/0'}`}
            onMouseDown={(event) => {
                if (event.target === event.currentTarget) requestClose();
            }}
        >
            <aside
                ref={dialogRef}
                role="dialog"
                aria-modal="true"
                aria-labelledby="popup-preview-title"
                aria-describedby="popup-preview-description"
                tabIndex={-1}
                onMouseEnter={() => setIsPaused(true)}
                onMouseLeave={() => setIsPaused(false)}
                className={`flex h-dvh w-full flex-col border-l border-slate-200 bg-white shadow-[-24px_0_70px_rgba(15,23,42,0.24)] outline-none transition-transform duration-[340ms] ease-[cubic-bezier(0.22,1,0.36,1)] motion-reduce:transition-none sm:w-[min(500px,100vw)] dark:border-slate-700 dark:bg-slate-900 ${isVisible ? 'translate-x-0' : 'translate-x-full'}`}
            >
                <header className="flex min-h-16 shrink-0 items-center justify-between gap-4 border-b border-slate-200 px-5 dark:border-slate-700">
                    <div>
                        <h2 id="popup-preview-title" className="text-base font-bold text-slate-900 dark:text-white">ICMS 2026 새 소식</h2>
                        <p id="popup-preview-description" className="sr-only">
                            사용자 메인 화면 오른쪽에서 열리며 네 개의 학술대회 안내 이미지가 순서대로 표시되는 팝업입니다.
                        </p>
                    </div>
                    <button
                        type="button"
                        onClick={requestClose}
                        className="grid h-9 w-9 place-items-center rounded-lg text-slate-500 transition hover:bg-slate-100 hover:text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 dark:text-slate-300 dark:hover:bg-slate-800 dark:hover:text-white"
                        aria-label="팝업 닫기"
                    >
                        <X className="h-5 w-5" />
                    </button>
                </header>

                <div className="relative min-h-0 flex-1 overflow-hidden bg-slate-950">
                    <div className={`flex h-full w-[400%] transition-transform duration-700 ease-[cubic-bezier(0.22,1,0.36,1)] motion-reduce:transition-none ${drawerTrackPositionClasses[activeIndex]}`}>
                        {drawerPopupSamples.map((sample, index) => (
                            <div key={sample.id} className="flex h-full w-1/4 shrink-0 items-center justify-center bg-slate-950">
                                <img
                                    src={sample.src}
                                    alt={index === activeIndex ? sample.alt : ''}
                                    className="block max-h-full max-w-full object-contain"
                                />
                            </div>
                        ))}
                    </div>

                    <div className="absolute bottom-4 left-1/2 flex -translate-x-1/2 items-center gap-2 rounded-full bg-slate-950/55 px-3 py-2 shadow-lg backdrop-blur-sm">
                        {drawerPopupSamples.map((sample, index) => (
                            <button
                                key={sample.id}
                                type="button"
                                onClick={() => setActiveIndex(index)}
                                className={`h-2 rounded-full transition-all focus:outline-none focus:ring-2 focus:ring-white ${activeIndex === index ? 'w-6 bg-white' : 'w-2 bg-white/45 hover:bg-white/75'}`}
                                aria-label={`${index + 1}번째 팝업 보기`}
                                aria-current={activeIndex === index ? 'true' : undefined}
                            />
                        ))}
                    </div>
                </div>

                <footer className="flex min-h-16 shrink-0 items-center justify-between gap-3 border-t border-slate-200 px-4 dark:border-slate-700">
                    <button
                        type="button"
                        onClick={requestClose}
                        className="rounded-lg px-2 py-2 text-xs font-semibold text-slate-600 transition hover:bg-slate-100 hover:text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 dark:text-slate-300 dark:hover:bg-slate-800 dark:hover:text-white"
                    >
                        오늘 하루 보지 않기
                    </button>
                    <div className="flex items-center gap-2">
                        <span className="text-[11px] font-semibold tabular-nums text-slate-400">
                            {activeIndex + 1} / {drawerPopupSamples.length}
                        </span>
                        <button
                            type="button"
                            onClick={() => move(-1)}
                            className="grid h-9 w-9 place-items-center rounded-lg border border-slate-200 text-slate-600 transition hover:bg-slate-100 focus:outline-none focus:ring-2 focus:ring-blue-500 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-800"
                            aria-label="이전 팝업"
                        >
                            <ChevronLeft className="h-4 w-4" />
                        </button>
                        <button
                            type="button"
                            onClick={() => move(1)}
                            className="grid h-9 w-9 place-items-center rounded-lg bg-slate-900 text-white transition hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-blue-600 dark:hover:bg-blue-500"
                            aria-label="다음 팝업"
                        >
                            <ChevronRight className="h-4 w-4" />
                        </button>
                    </div>
                </footer>
            </aside>
        </div>
    );
};

export const PopupLayoutPreviewModal = ({ layout, onClose, returnFocusRef }: PopupLayoutPreviewModalProps) => {
    const dialogRef = useRef<HTMLDivElement>(null);
    const directPreviewVariant = layout ? directPreviewVariants[layout.key] : undefined;
    const isDirectPreview = directPreviewVariant !== undefined;
    const isSpatialPreview = directPreviewVariant === 'row' || directPreviewVariant === 'overlay' || directPreviewVariant === 'coverflow';
    const hasFullscreenBackdrop = isSpatialPreview || directPreviewVariant === 'drawer';

    useEffect(() => {
        if (!layout) return;

        const previousOverflow = document.body.style.overflow;
        const returnFocusElement = returnFocusRef.current;
        if (!isDirectPreview || hasFullscreenBackdrop) document.body.style.overflow = 'hidden';
        dialogRef.current?.focus();

        const handleEscape = (event: globalThis.KeyboardEvent) => {
            if (event.key === 'Escape') onClose();
        };
        document.addEventListener('keydown', handleEscape);

        return () => {
            if (!isDirectPreview) document.body.style.overflow = previousOverflow;
            document.removeEventListener('keydown', handleEscape);
            returnFocusElement?.focus();
        };
    }, [hasFullscreenBackdrop, isDirectPreview, layout, onClose, returnFocusRef]);

    if (!layout) return null;

    if (directPreviewVariant) {
        if (directPreviewVariant === 'drawer') {
            return createPortal(
                <DrawerPopupPreview dialogRef={dialogRef} onClose={onClose} />,
                document.body
            );
        }

        if (directPreviewVariant === 'row' || directPreviewVariant === 'overlay' || directPreviewVariant === 'coverflow') {
            return createPortal(
                <SpatialPopupPreview dialogRef={dialogRef} onClose={onClose} variant={directPreviewVariant} />,
                document.body
            );
        }

        return createPortal(
            directPreviewVariant === 'deck'
                ? <DeckPopupPreview dialogRef={dialogRef} onClose={onClose} />
                : <OperationalPopupPreview dialogRef={dialogRef} onClose={onClose} variant={directPreviewVariant} />,
            document.body
        );
    }

    const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
        if (event.key !== 'Tab' || !dialogRef.current) return;

        const focusable = Array.from(dialogRef.current.querySelectorAll<HTMLElement>('button:not([disabled]), [tabindex]:not([tabindex="-1"])'));
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (!first || !last) return;

        if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first.focus();
        }
    };

    return createPortal(
        <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 p-3 dark:bg-slate-950/75 md:p-6"
            onMouseDown={(event) => {
                if (event.target === event.currentTarget) onClose();
            }}
        >
            <DraggableModal
                ref={dialogRef}
                role="dialog"
                aria-modal="true"
                aria-labelledby="popup-layout-preview-title"
                tabIndex={-1}
                onKeyDown={handleKeyDown}
                className="flex max-h-[94vh] w-full max-w-6xl flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl dark:border-slate-800 dark:bg-slate-950"
            >
                <header
                    className="flex shrink-0 cursor-move select-none touch-none items-start justify-between gap-4 border-b border-slate-200 px-5 py-4 dark:border-slate-800"
                    data-modal-drag-handle
                >
                    <div>
                        <div className="flex items-center gap-2">
                            <Eye className="h-5 w-5 shrink-0 text-blue-600 dark:text-blue-400" aria-hidden="true" />
                            <h2 id="popup-layout-preview-title" className="text-base font-bold text-slate-900 dark:text-slate-50">
                                {layout.name} 미리보기
                            </h2>
                        </div>
                        <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                            LAYOUT {layout.number} 레이아웃을 사용자 메인 화면에 적용했을 때의 예시입니다.
                        </p>
                    </div>
                    <button
                        type="button"
                        onClick={onClose}
                        className="shrink-0 rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-900"
                        aria-label="미리보기 닫기"
                    >
                        <X className="h-5 w-5" />
                    </button>
                </header>

                <div className="min-h-0 flex-1 overflow-y-auto bg-slate-100 p-4 dark:bg-slate-900/60 md:p-5">
                    <LivePopupLayout layoutKey={layout.key} />
                    <p className="mt-3 text-center text-xs text-slate-500 dark:text-slate-400">
                        실제 사용자 화면의 크기와 등록 이미지 비율에 따라 팝업 크기는 달라질 수 있습니다.
                    </p>
                </div>

                <footer className="flex shrink-0 justify-end border-t border-slate-200 px-5 py-4 dark:border-slate-800">
                    <button
                        type="button"
                        onClick={onClose}
                        className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900"
                    >
                        닫기
                    </button>
                </footer>
            </DraggableModal>
        </div>,
        document.body
    );
};
