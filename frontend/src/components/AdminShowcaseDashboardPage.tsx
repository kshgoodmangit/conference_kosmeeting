import { useEffect, useRef, useState } from 'react';
import { ShowcaseAnimatedNumber } from './ShowcaseAnimatedNumber';
import { ShowcaseGlobe } from './ShowcaseGlobe';
import { ANALYTICS_COUNTRY_POINTS } from '../analyticsCountryPoints';
import type { LucideIcon } from 'lucide-react';
import {
    Activity,
    AlertTriangle,
    CalendarClock,
    CalendarDays,
    CheckCircle2,
    Clock3,
    FileText,
    Globe2,
    HandHeart,
    MapPin,
    Maximize2,
    Minimize2,
    Palette,
    Radio,
    Sparkles,
    TrendingUp,
    Users,
    WalletCards
} from 'lucide-react';
import {
    SHOWCASE_DASHBOARD_PALETTES,
    SHOWCASE_PALETTE_OPTIONS,
    type ShowcaseDashboardPalette,
    type ShowcasePaletteKey,
    type ShowcaseTone
} from './showcaseDashboardPalettes';

type ShowcaseStat = {
    label: string;
    change: string;
    icon: LucideIcon;
    tone: ShowcaseTone;
} & ({ value: number; decimals?: number; unit: string; amounts?: never }
    | { amounts: Record<'KRW' | 'USD', number>; value?: never; decimals?: never; unit?: never });

interface AdminShowcaseDashboardPageProps {
    palette?: ShowcasePaletteKey;
}

const SHOWCASE_PALETTE_STORAGE_KEY = 'adminShowcaseDashboardPalette';

const getInitialPaletteKey = (fallback: ShowcasePaletteKey): ShowcasePaletteKey => {
    try {
        const savedPaletteKey = window.localStorage.getItem(SHOWCASE_PALETTE_STORAGE_KEY);
        if (savedPaletteKey && Object.hasOwn(SHOWCASE_DASHBOARD_PALETTES, savedPaletteKey)) {
            return savedPaletteKey as ShowcasePaletteKey;
        }
    } catch {
        // 브라우저 설정으로 저장소 접근이 제한된 경우 기본 팔레트를 사용합니다.
    }

    return fallback;
};

// 취소·환불을 제외한 유효 사전등록의 시안 집계입니다.
const REGISTRATION_PAYMENT = {
    paidCount: 1242,
    unpaidCount: 42,
    paidAmounts: { KRW: 186_300_000, USD: 38_400 }
};

const SHOWCASE_STATS: ShowcaseStat[] = [
    { label: '사전등록', value: 1284, unit: '명', change: '직전 행사 대비 +146명', icon: Users, tone: 'cyan' },
    { label: '초록 접수', value: 486, unit: '건', change: '직전 행사 대비 -18건', icon: FileText, tone: 'amber' },
    { label: '전체 등록비', amounts: REGISTRATION_PAYMENT.paidAmounts, change: '누적 결제 완료 기준', icon: WalletCards, tone: 'violet' },
    { label: '후원액', value: 2.4, decimals: 1, unit: '억원', change: '직전 행사 대비 +0.3억원', icon: HandHeart, tone: 'emerald' }
];

// 화면 검토용 32개국·1,284명 시안 데이터이며 실제 참가국 집계가 아닙니다.
// 지구본은 전체 목록을 사용하고, 옆의 순위 목록만 상위 4개국으로 제한합니다.
const PARTICIPATING_COUNTRIES = [
    { name: '대한민국', code: 'KR', value: 648 },
    { name: '일본', code: 'JP', value: 184 },
    { name: '미국', code: 'US', value: 126 },
    { name: '싱가포르', code: 'SG', value: 92 },
    { name: '중국', code: 'CN', value: 35 },
    { name: '호주', code: 'AU', value: 28 },
    { name: '영국', code: 'GB', value: 24 },
    { name: '독일', code: 'DE', value: 20 },
    { name: '캐나다', code: 'CA', value: 16 },
    { name: '프랑스', code: 'FR', value: 14 },
    { name: '인도', code: 'IN', value: 12 },
    { name: '대만', code: 'TW', value: 10 },
    { name: '태국', code: 'TH', value: 9 },
    { name: '말레이시아', code: 'MY', value: 8 },
    { name: '베트남', code: 'VN', value: 7 },
    { name: '인도네시아', code: 'ID', value: 6 },
    { name: '필리핀', code: 'PH', value: 5 },
    { name: '뉴질랜드', code: 'NZ', value: 5 },
    { name: '네덜란드', code: 'NL', value: 4 },
    { name: '스위스', code: 'CH', value: 4 },
    { name: '이탈리아', code: 'IT', value: 3 },
    { name: '스페인', code: 'ES', value: 3 },
    { name: '스웨덴', code: 'SE', value: 3 },
    { name: '덴마크', code: 'DK', value: 3 },
    { name: '브라질', code: 'BR', value: 3 },
    { name: '멕시코', code: 'MX', value: 2 },
    { name: '남아프리카공화국', code: 'ZA', value: 2 },
    { name: '아랍에미리트', code: 'AE', value: 2 },
    { name: '사우디아라비아', code: 'SA', value: 2 },
    { name: '튀르키예', code: 'TR', value: 2 },
    { name: '이집트', code: 'EG', value: 1 },
    { name: '아르헨티나', code: 'AR', value: 1 }
].map(country => {
    const [longitude, latitude] = ANALYTICS_COUNTRY_POINTS[country.code];
    return { ...country, longitude, latitude };
});
const COUNTRY_RANKING = [...PARTICIPATING_COUNTRIES].sort((a, b) => b.value - a.value).slice(0, 4);
const MAX_COUNTRY_PARTICIPANTS = Math.max(1, ...COUNTRY_RANKING.map((country) => country.value));

const DAILY_INTAKE_TRENDS = [
    { label: '09.02', abstractCount: 54, registrationCount: 112 },
    { label: '09.03', abstractCount: 68, registrationCount: 126 },
    { label: '09.04', abstractCount: 61, registrationCount: 118 },
    { label: '09.05', abstractCount: 79, registrationCount: 142 },
    { label: '09.06', abstractCount: 72, registrationCount: 135 },
    { label: '09.07', abstractCount: 91, registrationCount: 158 },
    { label: '오늘', abstractCount: 84, registrationCount: 176 }
];

const PAYMENT_TOTAL = REGISTRATION_PAYMENT.paidCount + REGISTRATION_PAYMENT.unpaidCount;
const PAYMENT_RATE = PAYMENT_TOTAL > 0 ? REGISTRATION_PAYMENT.paidCount / PAYMENT_TOTAL * 100 : 0;

const ABSTRACT_FIELDS = [
    { label: '임상 연구', value: 34, color: 'bg-cyan-400' },
    { label: '디지털 헬스케어', value: 27, color: 'bg-violet-400' },
    { label: '기초 연구', value: 23, color: 'bg-amber-400' },
    { label: '기타', value: 16, color: 'bg-emerald-400' }
];

const PARTNERS = ['MEDITECH', 'BIOCORE', 'NOVAGEN', 'HEALTH+', 'CELLWORKS'];

const TODAY_OPERATIONS = [
    { label: '신규 사전등록', value: '28명', note: '전일 대비 +6', color: 'text-cyan-300' },
    { label: '오늘 등록비 입금액', amounts: { KRW: 3_150_000, USD: 2_400 }, note: '오늘 결제 완료 기준', color: 'text-emerald-300' },
    { label: '신규 초록', value: '14건', note: '누적 486건', color: 'text-violet-300' },
    { label: '신규 회원 수', value: '36명', note: '전일 대비 +8명', color: 'text-amber-300' }
];

const ACTION_ITEMS = [
    { label: '사전등록 미입금', value: '42명', detail: '입금 안내 필요', dot: 'bg-amber-400' },
    { label: '심사위원 미배정', value: '18건', detail: '심사 배정 필요', dot: 'bg-rose-400' },
    { label: '심사기한 초과', value: '7건', detail: '위원 확인 필요', dot: 'bg-violet-400' },
    { label: '세금계산서 미발행', value: '3개사', detail: '입금 완료 기준', dot: 'bg-cyan-400' }
];

const UPCOMING_DEADLINES = [
    { label: '초록 접수 마감', date: '09.26', dday: 'D-18', color: 'text-rose-300 border-rose-300/20 bg-rose-300/10' },
    { label: '얼리버드 등록 마감', date: '10.11', dday: 'D-33', color: 'text-amber-300 border-amber-300/20 bg-amber-300/10' },
    { label: '사전등록 마감', date: '10.11', dday: 'D-33', color: 'text-emerald-300 border-emerald-300/20 bg-emerald-300/10' },
    { label: '초록 심사 완료', date: '10.25', dday: 'D-47', color: 'text-violet-300 border-violet-300/20 bg-violet-300/10' },
    { label: '행사 개최', date: '11.12', dday: 'D-65', color: 'text-cyan-300 border-cyan-300/20 bg-cyan-300/10' }
];

const HERO_MILESTONES = [
    { label: '행사까지', date: '11.12', dday: 'D-65', color: 'text-cyan-300' },
    { label: '초록 접수 마감', date: '09.26', dday: 'D-18', color: 'text-amber-300' },
    { label: '사전등록 마감', date: '10.11', dday: 'D-33', color: 'text-violet-300' }
];

const timeFormatter = new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    weekday: 'short',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23'
});

export const AdminShowcaseDashboardPage = ({ palette = 'ocean' }: AdminShowcaseDashboardPageProps) => {
    const [currentTime, setCurrentTime] = useState(() => new Date());
    const [isPresentationMode, setIsPresentationMode] = useState(false);
    const [selectedPaletteKey, setSelectedPaletteKey] = useState<ShowcasePaletteKey>(() => getInitialPaletteKey(palette));
    const cardPadding = isPresentationMode ? 'p-3' : 'p-5';
    const sectionGap = isPresentationMode ? 'gap-3' : 'gap-4';
    const selectedPalette = SHOWCASE_DASHBOARD_PALETTES[selectedPaletteKey];

    useEffect(() => {
        const intervalId = window.setInterval(() => setCurrentTime(new Date()), 1_000);
        return () => window.clearInterval(intervalId);
    }, []);

    useEffect(() => {
        try {
            window.localStorage.setItem(SHOWCASE_PALETTE_STORAGE_KEY, selectedPaletteKey);
        } catch {
            // 저장소를 사용할 수 없어도 팔레트 변경 자체는 유지합니다.
        }
    }, [selectedPaletteKey]);

    useEffect(() => {
        if (!isPresentationMode) return;

        const previousOverflow = document.body.style.overflow;
        const closeOnEscape = (event: KeyboardEvent) => {
            if (event.key === 'Escape') setIsPresentationMode(false);
        };

        document.body.style.overflow = 'hidden';
        window.addEventListener('keydown', closeOnEscape);
        return () => {
            document.body.style.overflow = previousOverflow;
            window.removeEventListener('keydown', closeOnEscape);
        };
    }, [isPresentationMode]);

    return (
        <div className={`${isPresentationMode
            ? 'fixed inset-0 z-[100] h-dvh overflow-auto p-2'
            : 'relative w-full overflow-hidden rounded-2xl p-4 md:p-5'
        } ${selectedPalette.canvasClass} text-white shadow-2xl`}>
            <div className="pointer-events-none absolute inset-0 overflow-hidden">
                <div
                    className="pointer-events-none absolute inset-0 bg-cover bg-center bg-no-repeat opacity-55"
                    style={{
                        backgroundImage: "url('/images/showcase-dashboard-background.png')",
                        filter: selectedPalette.backgroundFilter
                    }}
                />
                <div className={`pointer-events-none absolute inset-0 bg-gradient-to-b ${selectedPalette.overlayClass}`} />
                <div className={`pointer-events-none absolute -left-32 -top-40 h-[520px] w-[520px] rounded-full ${selectedPalette.primaryGlowClass} blur-3xl`} />
                <div className={`pointer-events-none absolute right-[-140px] top-20 h-[460px] w-[460px] rounded-full ${selectedPalette.secondaryGlowClass} blur-3xl`} />
                <div className={`pointer-events-none absolute bottom-[-240px] left-1/3 h-[520px] w-[520px] rounded-full ${selectedPalette.tertiaryGlowClass} blur-3xl`} />
            </div>

            <div className={`${isPresentationMode ? 'grid min-h-full w-full gap-3 2xl:h-full 2xl:min-h-[1064px] 2xl:grid-rows-[auto_auto_minmax(0,1fr)_auto_auto]' : 'max-w-[1800px] space-y-4'} relative mx-auto`}>
                <header className={`${isPresentationMode ? 'p-3' : 'p-5 md:p-6'} relative overflow-hidden rounded-2xl border border-white/10 bg-gradient-to-br ${selectedPalette.headerGradientClass} shadow-[0_24px_80px_-36px_rgba(59,130,246,0.8)] backdrop-blur-xl`}>
                    <div className="pointer-events-none absolute inset-0 opacity-30 [background-image:radial-gradient(circle_at_center,rgba(255,255,255,0.32)_1px,transparent_1px)] [background-size:22px_22px]" />
                    <div className="relative flex flex-col justify-between gap-5 lg:flex-row lg:items-start">
                        <div>
                            <div className="flex flex-wrap items-center gap-2">
                                <button
                                    type="button"
                                    onClick={() => setIsPresentationMode((value) => !value)}
                                    aria-label={isPresentationMode ? '프레젠테이션 모드 종료' : '프레젠테이션 모드 시작'}
                                    aria-pressed={isPresentationMode}
                                    title={isPresentationMode ? '프레젠테이션 모드 종료' : '프레젠테이션 모드 시작'}
                                    className={`inline-flex h-7 w-7 items-center justify-center rounded-full border border-white/10 bg-white/5 text-slate-300 transition hover:bg-white/10 ${selectedPalette.presentationButtonClass}`}
                                >
                                    {isPresentationMode ? <Minimize2 className="h-3.5 w-3.5" /> : <Maximize2 className="h-3.5 w-3.5" />}
                                </button>
                                <span className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-[11px] font-bold tracking-[0.16em] ${selectedPalette.liveBadgeClass}`}>
                                    <Radio className="h-3 w-3 animate-pulse" /> LIVE CONGRESS OVERVIEW
                                </span>
                                <label className="relative inline-flex h-7 items-center gap-1.5 rounded-full border border-white/10 bg-slate-950/30 px-2 text-[10px] font-semibold text-slate-300 backdrop-blur-md transition hover:border-white/20">
                                    <Palette className={`h-3.5 w-3.5 ${selectedPalette.primaryTextClass}`} />
                                    <span className="sr-only">대시보드 컬러 팔레트 선택</span>
                                    <select
                                        value={selectedPaletteKey}
                                        onChange={(event) => setSelectedPaletteKey(event.target.value as ShowcasePaletteKey)}
                                        aria-label="대시보드 컬러 팔레트 선택"
                                        className="h-full cursor-pointer appearance-none border-0 bg-transparent py-0 pl-0 pr-3 text-[10px] font-semibold text-slate-200 outline-none focus:ring-0 dark:bg-transparent dark:text-slate-200"
                                    >
                                        {SHOWCASE_PALETTE_OPTIONS.map((paletteOption) => (
                                            <option key={paletteOption.key} value={paletteOption.key} className="bg-slate-950 text-slate-100">
                                                {paletteOption.label}
                                            </option>
                                        ))}
                                    </select>
                                    <span className="pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 text-[8px] text-slate-500">▼</span>
                                </label>
                            </div>
                            <div className="mt-4 flex items-center gap-3">
                                <span className={`flex h-12 w-12 items-center justify-center rounded-2xl bg-gradient-to-br text-sm font-black text-slate-950 shadow-lg ${selectedPalette.logoGradientClass}`}>IC</span>
                                <div>
                                    <h1 className="text-2xl font-black tracking-tight md:text-3xl">ICMS 2026</h1>
                                    <p className={`mt-1 text-xs font-medium tracking-wide md:text-sm ${selectedPalette.subtitleClass}`}>International Congress of Medical Science</p>
                                </div>
                            </div>
                        </div>

                        <div className="w-full lg:w-[540px] lg:self-center">
                            <div className="flex flex-wrap items-center justify-between gap-2 px-1">
                                <p className="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400">Key milestones</p>
                                <p className="inline-flex items-center gap-1.5 text-[10px] font-semibold tabular-nums text-slate-300">
                                    <Clock3 className={`h-3 w-3 ${selectedPalette.secondaryTextClass}`} /> SEOUL {timeFormatter.format(currentTime)}
                                </p>
                            </div>
                            <div className="mt-2 grid grid-cols-3 gap-2">
                                {HERO_MILESTONES.map((milestone) => (
                                    <div key={milestone.label} className="rounded-xl border border-white/10 bg-slate-950/25 px-3 py-2.5 backdrop-blur-md">
                                        <p className="truncate text-[10px] font-semibold text-slate-400">{milestone.label}</p>
                                        <div className="mt-1.5 flex items-end justify-between gap-2">
                                            <strong className={`text-xl font-black tracking-tight ${milestone.color}`}>{milestone.dday}</strong>
                                            <span className="pb-0.5 text-[9px] font-semibold tabular-nums text-slate-500">{milestone.date}</span>
                                        </div>
                                    </div>
                                ))}
                            </div>
                            <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 px-1 text-[10px] font-medium text-slate-400 lg:justify-end">
                                <span className="inline-flex items-center gap-1.5"><CalendarDays className={`h-3 w-3 ${selectedPalette.primaryTextClass}`} /> 2026. 11. 12 — 14</span>
                                <span className="inline-flex items-center gap-1.5"><MapPin className={`h-3 w-3 ${selectedPalette.secondaryTextClass}`} /> Seoul Convention Center</span>
                            </div>
                        </div>
                    </div>
                </header>

                <section className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
                    {SHOWCASE_STATS.map((stat) => (
                        <ShowcaseStatCard key={stat.label} stat={stat} compact={isPresentationMode} palette={selectedPalette} />
                    ))}
                </section>

                <section className={`grid grid-cols-1 ${sectionGap} xl:grid-cols-[minmax(0,0.92fr)_minmax(0,1.35fr)] ${isPresentationMode ? 'min-h-0' : ''}`}>
                    <article className={`${cardPadding} ${isPresentationMode ? 'flex min-h-0 flex-col' : ''} overflow-hidden rounded-2xl border border-white/10 bg-white/[0.045] shadow-xl backdrop-blur-xl`}>
                        <div className="flex items-start justify-between gap-3">
                            <div>
                                <p className={`text-[10px] font-bold uppercase tracking-[0.18em] ${selectedPalette.primaryTextClass}`}>Global reach</p>
                                <h2 className="mt-1 text-base font-bold">글로벌 참가 현황</h2>
                                <p className="mt-1 text-[10px] text-slate-400 dark:text-slate-400">시안 데이터 · 지구본 전체 {PARTICIPATING_COUNTRIES.length}개국 · 순위 상위 4개국</p>
                            </div>
                            <span className={`rounded-xl bg-white/[0.055] p-2 ring-1 ring-inset ring-white/10 ${selectedPalette.primaryTextClass}`}><Globe2 className="h-4 w-4" /></span>
                        </div>
                        <div className={`${isPresentationMode ? 'mt-3 min-h-0 flex-1' : 'mt-4'} grid gap-5 md:grid-cols-[0.9fr_1.1fr] md:items-center`}>
                            <ShowcaseGlobe compact={isPresentationMode} palette={selectedPalette} countries={PARTICIPATING_COUNTRIES} />
                            <div className="space-y-3">
                                {COUNTRY_RANKING.map((country, index) => (
                                    <div key={country.code}>
                                        <div className="mb-1.5 flex items-center justify-between text-[11px]">
                                            <span className="font-semibold text-slate-200"><span className="mr-2 text-slate-500">{country.code}</span>{country.name}</span>
                                            <strong className="tabular-nums text-white"><ShowcaseAnimatedNumber value={country.value} delay={index * 100} />명</strong>
                                        </div>
                                        <div className="h-1.5 overflow-hidden rounded-full bg-white/[0.07]">
                                            <ShowcaseHorizontalBar widthPercent={country.value / MAX_COUNTRY_PARTICIPANTS * 100} delay={index * 100} className={`bg-gradient-to-r ${selectedPalette.primaryBarClass}`} />
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </div>
                    </article>

                    <article className={`${cardPadding} ${isPresentationMode ? 'flex min-h-0 flex-col' : ''} overflow-hidden rounded-2xl border border-white/10 bg-white/[0.045] shadow-xl backdrop-blur-xl`}>
                        <div className="flex flex-wrap items-start justify-between gap-3">
                            <div>
                                <p className={`text-[10px] font-bold uppercase tracking-[0.18em] ${selectedPalette.secondaryTextClass}`}>Daily intake</p>
                                <h2 className="mt-1 text-base font-bold">최근 7일 접수 및 등록 현황</h2>
                            </div>
                            <div className="flex items-center gap-3 text-[10px] font-semibold text-slate-400">
                                <span className="inline-flex items-center gap-1.5"><span className={`h-2 w-2 rounded-full ${selectedPalette.primaryDotClass}`} /> 초록 접수</span>
                                <span className="inline-flex items-center gap-1.5"><span className={`h-2 w-2 rounded-full ${selectedPalette.secondaryDotClass}`} /> 사전등록</span>
                            </div>
                        </div>
                        <ShowcaseBarChart compact={isPresentationMode} palette={selectedPalette} />
                        <div className="mt-1 flex items-center justify-between border-t border-white/[0.07] pt-3 text-[11px] text-slate-400">
                            <span>최근 7일 기준 일별 신규 건수</span>
                            <span className="inline-flex items-center gap-1 font-bold text-emerald-300"><TrendingUp className="h-3 w-3" /> 오늘 총 260건</span>
                        </div>
                    </article>
                </section>

                <section className={`grid grid-cols-1 ${sectionGap} xl:grid-cols-[1.1fr_0.9fr_1.2fr]`}>
                    <article className={`${cardPadding} rounded-2xl border border-white/10 bg-white/[0.045] backdrop-blur-xl`}>
                        <div className="flex items-center justify-between">
                            <div>
                                <p className={`text-[10px] font-bold uppercase tracking-[0.18em] ${selectedPalette.primaryTextClass}`}>Registration payments</p>
                                <h2 className="mt-1 text-sm font-bold text-white dark:text-white">사전등록 결제 현황</h2>
                            </div>
                            <WalletCards className={`h-4 w-4 ${selectedPalette.primaryTextClass}`} />
                        </div>
                        <dl className={`${isPresentationMode ? 'mt-3' : 'mt-4'} grid grid-cols-2 gap-3 sm:grid-cols-[1fr_1fr_1fr_2fr]`}>
                            <div>
                                <dt className="text-[10px] font-semibold text-slate-400 dark:text-slate-400">입금 완료</dt>
                                <dd className={`mt-1 text-xl font-black tabular-nums ${selectedPalette.primaryTextClass}`}>
                                    <ShowcaseAnimatedNumber value={REGISTRATION_PAYMENT.paidCount} /><span className="ml-1 text-[10px] font-semibold">명</span>
                                </dd>
                            </div>
                            <div>
                                <dt className="text-[10px] font-semibold text-slate-400 dark:text-slate-400">미입금</dt>
                                <dd className={`mt-1 text-xl font-black tabular-nums ${selectedPalette.secondaryTextClass}`}>
                                    <ShowcaseAnimatedNumber value={REGISTRATION_PAYMENT.unpaidCount} /><span className="ml-1 text-[10px] font-semibold">명</span>
                                </dd>
                            </div>
                            <div>
                                <dt className="text-[10px] font-semibold text-slate-400 dark:text-slate-400">결제 완료율</dt>
                                <dd className="mt-1 text-xl font-black tabular-nums text-white dark:text-white">
                                    <ShowcaseAnimatedNumber value={PAYMENT_RATE} decimals={1} /><span className="ml-1 text-[10px] font-semibold">%</span>
                                </dd>
                            </div>
                            <div>
                                <dt className="text-[10px] font-semibold text-slate-400 dark:text-slate-400">입금 완료 금액</dt>
                                <dd className="mt-1">
                                    <ShowcaseCurrencyAmounts amounts={REGISTRATION_PAYMENT.paidAmounts} className={selectedPalette.primaryTextClass} />
                                </dd>
                            </div>
                        </dl>
                        <div
                            className="mt-3 flex h-2 overflow-hidden rounded-full bg-white/[0.07] dark:bg-white/[0.07]"
                            role="img"
                            aria-label={`입금 완료 ${PAYMENT_RATE.toFixed(1)}%, 미입금 ${(100 - PAYMENT_RATE).toFixed(1)}%`}
                        >
                            <span className={selectedPalette.primaryDotClass} style={{ width: `${PAYMENT_RATE}%` }} />
                            <span className={selectedPalette.secondaryDotClass} style={{ width: `${100 - PAYMENT_RATE}%` }} />
                        </div>
                        <p className="mt-2 text-[9px] text-slate-400 dark:text-slate-400">취소·환불 제외 · 유효 등록 {PAYMENT_TOTAL.toLocaleString('ko-KR')}명 기준</p>
                    </article>

                    <article className={`${cardPadding} rounded-2xl border border-white/10 bg-white/[0.045] backdrop-blur-xl`}>
                        <p className="text-[10px] font-bold uppercase tracking-[0.18em] text-amber-300">Abstract mix</p>
                        <h2 className="mt-1 text-sm font-bold">초록 분야별 구성</h2>
                        <div className={`${isPresentationMode ? 'mt-3 space-y-2' : 'mt-4 space-y-2.5'}`}>
                            {ABSTRACT_FIELDS.map((field, index) => (
                                <div key={field.label} className="grid grid-cols-[minmax(0,1fr)_3fr_32px] items-center gap-2 text-[10px]">
                                    <span className="truncate text-slate-300">{field.label}</span>
                                    <span className="h-1.5 overflow-hidden rounded-full bg-white/[0.07]"><ShowcaseHorizontalBar widthPercent={field.value * 2.5} delay={index * 100} className={field.color} /></span>
                                    <strong className="text-right tabular-nums text-white"><ShowcaseAnimatedNumber value={field.value} delay={index * 100} />%</strong>
                                </div>
                            ))}
                        </div>
                    </article>

                    <article className={`${cardPadding} relative overflow-hidden rounded-2xl border border-white/10 bg-gradient-to-br ${selectedPalette.partnerCardClass} backdrop-blur-xl`}>
                        <div className="pointer-events-none absolute -right-12 -top-12 h-36 w-36 rounded-full bg-violet-500/20 blur-3xl" />
                        <div className="relative">
                            <div className="flex items-center justify-between">
                                <div>
                                    <p className="text-[10px] font-bold uppercase tracking-[0.18em] text-violet-300">Official partners</p>
                                    <h2 className="mt-1 text-sm font-bold">함께하는 후원사</h2>
                                </div>
                                <Sparkles className="h-4 w-4 text-cyan-300" />
                            </div>
                            <div className={`${isPresentationMode ? 'mt-3' : 'mt-4'} grid grid-cols-2 gap-2 sm:grid-cols-3 xl:grid-cols-2 2xl:grid-cols-3`}>
                                {PARTNERS.map((partner, index) => (
                                    <span
                                        key={partner}
                                        className="flex h-10 items-center justify-center rounded-xl border border-white/[0.08] bg-white/[0.055] px-3 text-[10px] font-black tracking-wider text-slate-200 shadow-inner motion-safe:animate-[pulse_4s_ease-in-out_infinite] motion-reduce:animate-none"
                                        style={{ animationDelay: `${index * 0.55}s` }}
                                    >
                                        {partner}
                                    </span>
                                ))}
                                <span className="flex h-10 items-center justify-center rounded-xl border border-dashed border-cyan-300/20 bg-cyan-300/[0.04] px-3 text-[10px] font-bold text-cyan-300">+13 PARTNERS</span>
                            </div>
                        </div>
                    </article>
                </section>

                <section className={`grid grid-cols-1 ${sectionGap} xl:grid-cols-[0.95fr_1.05fr_1.2fr]`}>
                    <article className={`${cardPadding} rounded-2xl border border-white/10 bg-white/[0.045] backdrop-blur-xl`}>
                        <div className="flex items-center justify-between">
                            <div>
                                <p className="text-[10px] font-bold uppercase tracking-[0.18em] text-cyan-300">Today&apos;s operations</p>
                                <h2 className="mt-1 text-sm font-bold">오늘 처리 현황</h2>
                            </div>
                            <Activity className="h-4 w-4 text-cyan-300" />
                        </div>
                        <div className={`${isPresentationMode ? 'mt-3 gap-2' : 'mt-4 gap-2.5'} grid grid-cols-2`}>
                            {TODAY_OPERATIONS.map((item) => (
                                <div key={item.label} className="rounded-xl border border-white/[0.07] bg-slate-950/25 p-3">
                                    <p className="text-[10px] font-semibold text-slate-400">{item.label}</p>
                                    {item.amounts ? (
                                        <div className="mt-1.5">
                                            <ShowcaseCurrencyAmounts amounts={item.amounts} className={item.color} />
                                            <p className="mt-1 text-[9px] text-slate-500 dark:text-slate-500">{item.note}</p>
                                        </div>
                                    ) : (
                                        <div className="mt-1.5 flex flex-wrap items-end justify-between gap-1">
                                            <strong className={`text-lg font-black tabular-nums ${item.color}`}>{item.value}</strong>
                                            <span className="text-[9px] text-slate-500">{item.note}</span>
                                        </div>
                                    )}
                                </div>
                            ))}
                        </div>
                    </article>

                    <article className={`${cardPadding} rounded-2xl border border-white/10 bg-white/[0.045] backdrop-blur-xl`}>
                        <div className="flex items-center justify-between">
                            <div>
                                <p className="text-[10px] font-bold uppercase tracking-[0.18em] text-rose-300">Action required</p>
                                <h2 className="mt-1 text-sm font-bold">확인이 필요한 업무</h2>
                            </div>
                            <AlertTriangle className="h-4 w-4 text-rose-300" />
                        </div>
                        <div className="mt-3 space-y-1.5">
                            {ACTION_ITEMS.map((item) => (
                                <div key={item.label} className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-3 rounded-lg px-2 py-1.5 transition hover:bg-white/[0.045]">
                                    <div className="flex min-w-0 items-center gap-2.5">
                                        <span className={`h-2 w-2 shrink-0 rounded-full ${item.dot}`} />
                                        <div className="min-w-0">
                                            <p className="truncate text-[11px] font-semibold text-slate-200">{item.label}</p>
                                            <p className="mt-0.5 text-[9px] text-slate-500">{item.detail}</p>
                                        </div>
                                    </div>
                                    <strong className="text-xs font-black tabular-nums text-white">{item.value}</strong>
                                </div>
                            ))}
                        </div>
                    </article>

                    <article className={`${cardPadding} relative overflow-hidden rounded-2xl border border-white/10 bg-gradient-to-br ${selectedPalette.deadlineCardClass} backdrop-blur-xl`}>
                        <div className="pointer-events-none absolute -right-16 -top-16 h-40 w-40 rounded-full bg-blue-500/15 blur-3xl" />
                        <div className="relative flex items-center justify-between">
                            <div>
                                <p className="text-[10px] font-bold uppercase tracking-[0.18em] text-violet-300">Upcoming deadlines</p>
                                <h2 className="mt-1 text-sm font-bold">다가오는 주요 마감</h2>
                            </div>
                            <CalendarClock className="h-4 w-4 text-violet-300" />
                        </div>
                        <div className={`relative ${isPresentationMode ? 'mt-3' : 'mt-4'} grid grid-cols-2 gap-2 sm:grid-cols-5 xl:grid-cols-2 2xl:grid-cols-5`}>
                            {UPCOMING_DEADLINES.map((item, index) => (
                                <div key={item.label} className="relative rounded-xl border border-white/[0.07] bg-slate-950/25 p-3">
                                    {index < UPCOMING_DEADLINES.length - 1 && <span className="pointer-events-none absolute -right-2.5 top-1/2 hidden h-px w-3 bg-white/10 2xl:block" />}
                                    <span className={`inline-flex rounded-full border px-2 py-0.5 text-[9px] font-black ${item.color}`}>{item.dday}</span>
                                    <p className="mt-2 truncate text-[10px] font-semibold text-slate-200">{item.label}</p>
                                    <p className="mt-1 text-xs font-black tabular-nums text-white">2026.{item.date}</p>
                                </div>
                            ))}
                        </div>
                        <div className="relative mt-3 flex items-center gap-1.5 text-[9px] font-semibold text-emerald-300">
                            <CheckCircle2 className="h-3 w-3" /> 모든 일정은 서울 시간 기준입니다.
                        </div>
                    </article>
                </section>
            </div>
        </div>
    );
};

const ShowcaseCurrencyAmounts = ({ amounts, className, prominent = false }: { amounts: Record<'KRW' | 'USD', number>; className: string; prominent?: boolean }) => (
    <div className="space-y-1">
        <div className="flex flex-wrap items-baseline justify-between gap-x-2">
            <span className="text-[9px] font-semibold text-slate-400 dark:text-slate-400">KRW</span>
            <strong className={`whitespace-nowrap ${prominent ? 'text-lg leading-5' : 'text-xs'} font-black tabular-nums ${className}`}>
                <ShowcaseAnimatedNumber value={amounts.KRW} /><span className="ml-0.5 text-[10px] font-semibold">원</span>
            </strong>
        </div>
        <div className="flex flex-wrap items-baseline justify-between gap-x-2">
            <span className="text-[9px] font-semibold text-slate-400 dark:text-slate-400">USD</span>
            <strong className={`whitespace-nowrap ${prominent ? 'text-lg leading-5' : 'text-xs'} font-black tabular-nums ${className}`}>
                $<ShowcaseAnimatedNumber value={amounts.USD} decimals={2} />
            </strong>
        </div>
    </div>
);

const ShowcaseStatCard = ({ stat, compact, palette }: { stat: ShowcaseStat; compact: boolean; palette: ShowcaseDashboardPalette }) => {
    const Icon = stat.icon;
    const tone = palette.tones[stat.tone];

    return (
        <article className={`${compact ? 'p-3' : 'p-4 md:p-5'} group relative overflow-hidden rounded-2xl border border-white/10 bg-white/[0.045] shadow-xl backdrop-blur-xl transition duration-300 hover:-translate-y-1 hover:border-white/20 hover:bg-white/[0.065]`}>
            <div className={`pointer-events-none absolute inset-x-0 top-0 h-24 bg-gradient-to-b ${tone.glow} to-transparent opacity-50 transition-opacity group-hover:opacity-80`} />
            <div className="relative flex items-start justify-between">
                <span className={`flex ${compact ? 'h-9 w-9' : 'h-10 w-10'} items-center justify-center rounded-xl ring-1 ring-inset ${tone.icon}`}><Icon className="h-5 w-5" /></span>
                <span className={`rounded-full bg-white/[0.06] px-2 py-1 text-[10px] font-bold ${tone.text}`}>{stat.change}</span>
            </div>
            <div className={`relative ${compact ? 'mt-3' : 'mt-4'} flex items-end justify-between`}>
                <div className={stat.amounts ? 'w-full min-w-0' : undefined}>
                    <p className="text-[11px] font-semibold text-slate-400">{stat.label}</p>
                    {stat.amounts ? (
                        <div className="mt-1">
                            <ShowcaseCurrencyAmounts amounts={stat.amounts} className="text-white dark:text-white" prominent />
                        </div>
                    ) : (
                        <p className={`mt-1 ${compact ? 'text-2xl' : 'text-3xl'} font-black tracking-tight text-white`}><ShowcaseAnimatedNumber value={stat.value} decimals={stat.decimals} /><span className="ml-1 text-sm font-bold text-slate-400">{stat.unit}</span></p>
                    )}
                </div>
                {!stat.amounts && <TrendingUp className={`mb-1 h-4 w-4 ${tone.text}`} />}
            </div>
        </article>
    );
};

const ShowcaseHorizontalBar = ({ widthPercent, delay, className }: { widthPercent: number; delay: number; className: string }) => {
    const barRef = useRef<HTMLSpanElement>(null);

    useEffect(() => {
        const bar = barRef.current;
        if (!bar) return;
        const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
        let animation: Animation | undefined;
        const handleMotionChange = () => {
            if (reducedMotion.matches) animation?.cancel();
        };
        const observer = new IntersectionObserver((entries) => {
            if (!entries.some((entry) => entry.isIntersecting)) return;
            observer.disconnect();
            if (reducedMotion.matches) return;
            animation = bar.animate([
                { transform: 'scaleX(0)' },
                { transform: 'scaleX(1)' }
            ], {
                duration: 1_200,
                delay,
                easing: 'cubic-bezier(0.22, 1, 0.36, 1)',
                fill: 'backwards'
            });
        }, { threshold: 0.25 });
        observer.observe(bar);
        reducedMotion.addEventListener('change', handleMotionChange);
        return () => {
            observer.disconnect();
            animation?.cancel();
            reducedMotion.removeEventListener('change', handleMotionChange);
        };
    }, [widthPercent, delay]);

    return (
        <span
            ref={barRef}
            className={`block h-full origin-left rounded-full ${className}`}
            style={{ width: `${widthPercent}%` }}
            aria-hidden="true"
        />
    );
};

const ShowcaseBarChart = ({ compact, palette }: { compact: boolean; palette: ShowcaseDashboardPalette }) => {
    const chartRef = useRef<HTMLDivElement>(null);
    const { primary, primaryLight, secondary } = palette.chart;
    const maxValue = Math.max(...DAILY_INTAKE_TRENDS.flatMap((item) => [item.abstractCount, item.registrationCount]));

    useEffect(() => {
        const chart = chartRef.current;
        if (!chart) return;
        const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
        const animations: Animation[] = [];
        const cancelAnimations = () => animations.forEach((animation) => animation.cancel());
        const handleMotionChange = () => {
            if (reducedMotion.matches) cancelAnimations();
        };
        const observer = new IntersectionObserver((entries) => {
            if (!entries.some((entry) => entry.isIntersecting)) return;
            observer.disconnect();
            if (reducedMotion.matches) return;
            chart.querySelectorAll<HTMLElement>('[data-intake-bar]').forEach((bar, index) => {
                animations.push(bar.animate([
                    { transform: 'scaleY(0)' },
                    { transform: 'scaleY(1)' }
                ], {
                    duration: 1_200,
                    delay: Math.floor(index / 2) * 80,
                    easing: 'cubic-bezier(0.22, 1, 0.36, 1)',
                    fill: 'backwards'
                }));
            });
        }, { threshold: 0.25 });
        observer.observe(chart);
        reducedMotion.addEventListener('change', handleMotionChange);
        return () => {
            observer.disconnect();
            cancelAnimations();
            reducedMotion.removeEventListener('change', handleMotionChange);
        };
    }, []);

    return (
        <div
            ref={chartRef}
            className={`${compact ? 'mt-3 h-40 min-h-0 flex-1' : 'mt-5 h-48'} relative flex w-full items-end gap-2 sm:gap-4`}
            role="img"
            aria-label="최근 7일 일별 초록 접수와 사전등록 건수 막대 차트"
        >
            <div className="pointer-events-none absolute inset-x-0 bottom-6 top-5 flex flex-col justify-between">
                {[0, 1, 2, 3].map((line) => <span key={line} className="block border-t border-dashed border-white/[0.07]" />)}
            </div>
            {DAILY_INTAKE_TRENDS.map((item, index) => (
                <div key={item.label} className="group relative flex h-full min-w-0 flex-1 flex-col justify-end">
                    <div className="mb-1.5 flex justify-center gap-1 text-[9px] font-bold tabular-nums text-slate-400 transition group-hover:text-white sm:text-[10px]">
                        <ShowcaseAnimatedNumber value={item.abstractCount} delay={index * 80} />
                        <span className="text-slate-600">/</span>
                        <ShowcaseAnimatedNumber value={item.registrationCount} delay={index * 80} />
                    </div>
                    <div className={`${compact ? 'min-h-0 flex-1' : 'h-[142px]'} flex items-end justify-center gap-1.5 sm:gap-2.5`}>
                        <div
                            data-intake-bar
                            className="w-3 origin-bottom rounded-t-md opacity-90 shadow-[0_0_18px_-4px_currentColor] transition duration-300 group-hover:opacity-100 sm:w-5"
                            style={{
                                height: `${barHeight(item.abstractCount, maxValue)}%`,
                                color: primary,
                                background: `linear-gradient(to top, ${primary}, ${primaryLight})`
                            }}
                            title={`초록 접수 ${item.abstractCount}건`}
                        />
                        <div
                            data-intake-bar
                            className="w-3 origin-bottom rounded-t-md opacity-80 shadow-[0_0_18px_-4px_currentColor] transition duration-300 group-hover:opacity-100 sm:w-5"
                            style={{
                                height: `${barHeight(item.registrationCount, maxValue)}%`,
                                color: secondary,
                                background: `linear-gradient(to top, ${secondary}, color-mix(in srgb, ${secondary} 72%, white))`
                            }}
                            title={`사전등록 ${item.registrationCount}명`}
                        />
                    </div>
                    <span className={`mt-1.5 text-center text-[9px] font-semibold tabular-nums sm:text-[10px] ${item.label === '오늘' ? palette.primaryTextClass : 'text-slate-500'}`}>
                        {item.label}
                    </span>
                </div>
            ))}
        </div>
    );
};

const barHeight = (value: number, maxValue: number) => value === 0 ? 0 : Math.max(10, (value / maxValue) * 100);
