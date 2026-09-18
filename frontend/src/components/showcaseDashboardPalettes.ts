export type ShowcasePaletteKey = 'ocean' | 'aurora' | 'solar' | 'forest' | 'royal' | 'platinum' | 'spring' | 'autumn';

export type ShowcaseTone = 'cyan' | 'violet' | 'amber' | 'emerald';

interface ShowcaseToneClass {
    icon: string;
    glow: string;
    text: string;
}

interface ShowcaseChartColors {
    primary: string;
    primaryLight: string;
    secondary: string;
    canvas: string;
    globeEnd: string;
}

export interface ShowcaseDashboardPalette {
    key: ShowcasePaletteKey;
    label: string;
    canvasClass: string;
    backgroundFilter?: string;
    overlayClass: string;
    primaryGlowClass: string;
    secondaryGlowClass: string;
    tertiaryGlowClass: string;
    headerGradientClass: string;
    liveBadgeClass: string;
    presentationButtonClass: string;
    logoGradientClass: string;
    subtitleClass: string;
    primaryTextClass: string;
    secondaryTextClass: string;
    primaryDotClass: string;
    secondaryDotClass: string;
    primaryBarClass: string;
    globeHaloClass: string;
    globeOrbitClass: string;
    globeOrbitDotClass: string;
    globeBadgeClass: string;
    partnerCardClass: string;
    deadlineCardClass: string;
    tones: Record<ShowcaseTone, ShowcaseToneClass>;
    chart: ShowcaseChartColors;
}

export const SHOWCASE_DASHBOARD_PALETTES: Record<ShowcasePaletteKey, ShowcaseDashboardPalette> = {
    ocean: {
        key: 'ocean',
        label: '오션 블루',
        canvasClass: 'bg-[#071126] dark:bg-[#071126]',
        overlayClass: 'from-[#071126]/35 via-[#071126]/65 to-[#071126]/90',
        primaryGlowClass: 'bg-blue-600/20',
        secondaryGlowClass: 'bg-violet-600/20',
        tertiaryGlowClass: 'bg-cyan-500/10',
        headerGradientClass: 'from-blue-600/25 via-indigo-500/10 to-violet-600/20',
        liveBadgeClass: 'border-cyan-300/20 bg-cyan-300/10 text-cyan-200',
        presentationButtonClass: 'hover:border-cyan-300/30 hover:text-cyan-200',
        logoGradientClass: 'from-cyan-300 to-blue-500 shadow-cyan-500/20',
        subtitleClass: 'text-blue-100/70',
        primaryTextClass: 'text-cyan-300',
        secondaryTextClass: 'text-violet-300',
        primaryDotClass: 'bg-cyan-400',
        secondaryDotClass: 'bg-violet-400',
        primaryBarClass: 'from-cyan-400 to-blue-500',
        globeHaloClass: 'bg-cyan-400/10',
        globeOrbitClass: 'border-cyan-300/20',
        globeOrbitDotClass: 'bg-cyan-300 shadow-[0_0_16px_4px_rgba(103,232,249,0.5)]',
        globeBadgeClass: 'border-cyan-300/20 text-cyan-200',
        partnerCardClass: 'from-violet-500/10 to-cyan-500/10',
        deadlineCardClass: 'from-blue-500/10 to-violet-500/10',
        tones: {
            cyan: { icon: 'bg-cyan-400/15 text-cyan-300 ring-cyan-300/20', glow: 'from-cyan-400/20', text: 'text-cyan-300' },
            violet: { icon: 'bg-violet-400/15 text-violet-300 ring-violet-300/20', glow: 'from-violet-400/20', text: 'text-violet-300' },
            amber: { icon: 'bg-amber-400/15 text-amber-300 ring-amber-300/20', glow: 'from-amber-400/20', text: 'text-amber-300' },
            emerald: { icon: 'bg-emerald-400/15 text-emerald-300 ring-emerald-300/20', glow: 'from-emerald-400/20', text: 'text-emerald-300' }
        },
        chart: { primary: '#22d3ee', primaryLight: '#a5f3fc', secondary: '#a78bfa', canvas: '#071126', globeEnd: '#2563eb' }
    },
    aurora: {
        key: 'aurora',
        label: '오로라 핑크',
        canvasClass: 'bg-[#17091f] dark:bg-[#17091f]',
        backgroundFilter: 'hue-rotate(58deg) saturate(1.25)',
        overlayClass: 'from-[#17091f]/30 via-[#17091f]/60 to-[#17091f]/90',
        primaryGlowClass: 'bg-fuchsia-600/20',
        secondaryGlowClass: 'bg-rose-500/20',
        tertiaryGlowClass: 'bg-lime-500/10',
        headerGradientClass: 'from-fuchsia-600/25 via-rose-500/10 to-violet-600/20',
        liveBadgeClass: 'border-fuchsia-300/20 bg-fuchsia-300/10 text-fuchsia-200',
        presentationButtonClass: 'hover:border-fuchsia-300/30 hover:text-fuchsia-200',
        logoGradientClass: 'from-fuchsia-300 to-rose-500 shadow-fuchsia-500/20',
        subtitleClass: 'text-fuchsia-100/70',
        primaryTextClass: 'text-fuchsia-300',
        secondaryTextClass: 'text-lime-300',
        primaryDotClass: 'bg-fuchsia-400',
        secondaryDotClass: 'bg-lime-400',
        primaryBarClass: 'from-fuchsia-400 to-rose-500',
        globeHaloClass: 'bg-fuchsia-400/10',
        globeOrbitClass: 'border-fuchsia-300/20',
        globeOrbitDotClass: 'bg-fuchsia-300 shadow-[0_0_16px_4px_rgba(249,168,212,0.5)]',
        globeBadgeClass: 'border-fuchsia-300/20 text-fuchsia-200',
        partnerCardClass: 'from-fuchsia-500/10 to-lime-500/10',
        deadlineCardClass: 'from-rose-500/10 to-violet-500/10',
        tones: {
            cyan: { icon: 'bg-fuchsia-400/15 text-fuchsia-300 ring-fuchsia-300/20', glow: 'from-fuchsia-400/20', text: 'text-fuchsia-300' },
            violet: { icon: 'bg-rose-400/15 text-rose-300 ring-rose-300/20', glow: 'from-rose-400/20', text: 'text-rose-300' },
            amber: { icon: 'bg-lime-400/15 text-lime-300 ring-lime-300/20', glow: 'from-lime-400/20', text: 'text-lime-300' },
            emerald: { icon: 'bg-sky-400/15 text-sky-300 ring-sky-300/20', glow: 'from-sky-400/20', text: 'text-sky-300' }
        },
        chart: { primary: '#f472b6', primaryLight: '#f9a8d4', secondary: '#a3e635', canvas: '#17091f', globeEnd: '#be123c' }
    },
    solar: {
        key: 'solar',
        label: '솔라 선셋',
        canvasClass: 'bg-[#1a0b0a] dark:bg-[#1a0b0a]',
        backgroundFilter: 'hue-rotate(125deg) saturate(1.4)',
        overlayClass: 'from-[#1a0b0a]/35 via-[#1a0b0a]/68 to-[#1a0b0a]/92',
        primaryGlowClass: 'bg-orange-600/20',
        secondaryGlowClass: 'bg-rose-600/20',
        tertiaryGlowClass: 'bg-amber-500/10',
        headerGradientClass: 'from-orange-600/25 via-rose-500/10 to-amber-500/15',
        liveBadgeClass: 'border-orange-300/20 bg-orange-300/10 text-orange-200',
        presentationButtonClass: 'hover:border-orange-300/30 hover:text-orange-200',
        logoGradientClass: 'from-amber-300 to-orange-500 shadow-orange-500/20',
        subtitleClass: 'text-orange-100/70',
        primaryTextClass: 'text-orange-300',
        secondaryTextClass: 'text-rose-300',
        primaryDotClass: 'bg-orange-400',
        secondaryDotClass: 'bg-rose-400',
        primaryBarClass: 'from-amber-400 to-orange-500',
        globeHaloClass: 'bg-orange-400/10',
        globeOrbitClass: 'border-orange-300/20',
        globeOrbitDotClass: 'bg-orange-300 shadow-[0_0_16px_4px_rgba(253,186,116,0.5)]',
        globeBadgeClass: 'border-orange-300/20 text-orange-200',
        partnerCardClass: 'from-orange-500/10 to-rose-500/10',
        deadlineCardClass: 'from-rose-500/10 to-amber-500/10',
        tones: {
            cyan: { icon: 'bg-orange-400/15 text-orange-300 ring-orange-300/20', glow: 'from-orange-400/20', text: 'text-orange-300' },
            violet: { icon: 'bg-rose-400/15 text-rose-300 ring-rose-300/20', glow: 'from-rose-400/20', text: 'text-rose-300' },
            amber: { icon: 'bg-amber-400/15 text-amber-300 ring-amber-300/20', glow: 'from-amber-400/20', text: 'text-amber-300' },
            emerald: { icon: 'bg-yellow-400/15 text-yellow-300 ring-yellow-300/20', glow: 'from-yellow-400/20', text: 'text-yellow-300' }
        },
        chart: { primary: '#fb923c', primaryLight: '#fed7aa', secondary: '#fb7185', canvas: '#1a0b0a', globeEnd: '#be123c' }
    },
    forest: {
        key: 'forest',
        label: '포레스트 민트',
        canvasClass: 'bg-[#061713] dark:bg-[#061713]',
        backgroundFilter: 'hue-rotate(-45deg) saturate(1.15)',
        overlayClass: 'from-[#061713]/35 via-[#061713]/66 to-[#061713]/92',
        primaryGlowClass: 'bg-emerald-600/20',
        secondaryGlowClass: 'bg-teal-500/20',
        tertiaryGlowClass: 'bg-lime-500/10',
        headerGradientClass: 'from-emerald-600/25 via-teal-500/10 to-lime-500/10',
        liveBadgeClass: 'border-emerald-300/20 bg-emerald-300/10 text-emerald-200',
        presentationButtonClass: 'hover:border-emerald-300/30 hover:text-emerald-200',
        logoGradientClass: 'from-lime-300 to-emerald-500 shadow-emerald-500/20',
        subtitleClass: 'text-emerald-100/70',
        primaryTextClass: 'text-emerald-300',
        secondaryTextClass: 'text-lime-300',
        primaryDotClass: 'bg-emerald-400',
        secondaryDotClass: 'bg-lime-400',
        primaryBarClass: 'from-emerald-400 to-teal-500',
        globeHaloClass: 'bg-emerald-400/10',
        globeOrbitClass: 'border-emerald-300/20',
        globeOrbitDotClass: 'bg-emerald-300 shadow-[0_0_16px_4px_rgba(110,231,183,0.5)]',
        globeBadgeClass: 'border-emerald-300/20 text-emerald-200',
        partnerCardClass: 'from-emerald-500/10 to-lime-500/10',
        deadlineCardClass: 'from-teal-500/10 to-emerald-500/10',
        tones: {
            cyan: { icon: 'bg-emerald-400/15 text-emerald-300 ring-emerald-300/20', glow: 'from-emerald-400/20', text: 'text-emerald-300' },
            violet: { icon: 'bg-teal-400/15 text-teal-300 ring-teal-300/20', glow: 'from-teal-400/20', text: 'text-teal-300' },
            amber: { icon: 'bg-lime-400/15 text-lime-300 ring-lime-300/20', glow: 'from-lime-400/20', text: 'text-lime-300' },
            emerald: { icon: 'bg-sky-400/15 text-sky-300 ring-sky-300/20', glow: 'from-sky-400/20', text: 'text-sky-300' }
        },
        chart: { primary: '#34d399', primaryLight: '#a7f3d0', secondary: '#a3e635', canvas: '#061713', globeEnd: '#0f766e' }
    },
    royal: {
        key: 'royal',
        label: '로열 퍼플',
        canvasClass: 'bg-[#0d0a24] dark:bg-[#0d0a24]',
        backgroundFilter: 'hue-rotate(24deg) saturate(1.35)',
        overlayClass: 'from-[#0d0a24]/30 via-[#0d0a24]/64 to-[#0d0a24]/92',
        primaryGlowClass: 'bg-indigo-600/20',
        secondaryGlowClass: 'bg-fuchsia-600/20',
        tertiaryGlowClass: 'bg-sky-500/10',
        headerGradientClass: 'from-indigo-600/25 via-violet-500/10 to-fuchsia-500/15',
        liveBadgeClass: 'border-indigo-300/20 bg-indigo-300/10 text-indigo-200',
        presentationButtonClass: 'hover:border-indigo-300/30 hover:text-indigo-200',
        logoGradientClass: 'from-indigo-300 to-violet-500 shadow-indigo-500/20',
        subtitleClass: 'text-indigo-100/70',
        primaryTextClass: 'text-indigo-300',
        secondaryTextClass: 'text-fuchsia-300',
        primaryDotClass: 'bg-indigo-400',
        secondaryDotClass: 'bg-fuchsia-400',
        primaryBarClass: 'from-indigo-400 to-violet-500',
        globeHaloClass: 'bg-indigo-400/10',
        globeOrbitClass: 'border-indigo-300/20',
        globeOrbitDotClass: 'bg-indigo-300 shadow-[0_0_16px_4px_rgba(165,180,252,0.5)]',
        globeBadgeClass: 'border-indigo-300/20 text-indigo-200',
        partnerCardClass: 'from-indigo-500/10 to-fuchsia-500/10',
        deadlineCardClass: 'from-violet-500/10 to-fuchsia-500/10',
        tones: {
            cyan: { icon: 'bg-indigo-400/15 text-indigo-300 ring-indigo-300/20', glow: 'from-indigo-400/20', text: 'text-indigo-300' },
            violet: { icon: 'bg-fuchsia-400/15 text-fuchsia-300 ring-fuchsia-300/20', glow: 'from-fuchsia-400/20', text: 'text-fuchsia-300' },
            amber: { icon: 'bg-sky-400/15 text-sky-300 ring-sky-300/20', glow: 'from-sky-400/20', text: 'text-sky-300' },
            emerald: { icon: 'bg-violet-400/15 text-violet-300 ring-violet-300/20', glow: 'from-violet-400/20', text: 'text-violet-300' }
        },
        chart: { primary: '#818cf8', primaryLight: '#c7d2fe', secondary: '#e879f9', canvas: '#0d0a24', globeEnd: '#7e22ce' }
    },
    platinum: {
        key: 'platinum',
        label: '플래티넘',
        canvasClass: 'bg-[#0b1118] dark:bg-[#0b1118]',
        backgroundFilter: 'grayscale(0.72) hue-rotate(175deg) saturate(0.75)',
        overlayClass: 'from-[#0b1118]/38 via-[#0b1118]/70 to-[#0b1118]/94',
        primaryGlowClass: 'bg-slate-400/15',
        secondaryGlowClass: 'bg-sky-500/15',
        tertiaryGlowClass: 'bg-zinc-300/10',
        headerGradientClass: 'from-slate-400/15 via-sky-500/10 to-zinc-300/10',
        liveBadgeClass: 'border-slate-300/20 bg-slate-300/10 text-slate-200',
        presentationButtonClass: 'hover:border-slate-300/30 hover:text-white',
        logoGradientClass: 'from-slate-100 to-sky-400 shadow-sky-500/20',
        subtitleClass: 'text-slate-200/70',
        primaryTextClass: 'text-sky-200',
        secondaryTextClass: 'text-slate-300',
        primaryDotClass: 'bg-sky-300',
        secondaryDotClass: 'bg-slate-300',
        primaryBarClass: 'from-slate-200 to-sky-400',
        globeHaloClass: 'bg-sky-300/10',
        globeOrbitClass: 'border-slate-300/20',
        globeOrbitDotClass: 'bg-slate-200 shadow-[0_0_16px_4px_rgba(226,232,240,0.45)]',
        globeBadgeClass: 'border-slate-300/20 text-slate-200',
        partnerCardClass: 'from-slate-300/10 to-sky-500/10',
        deadlineCardClass: 'from-slate-400/10 to-sky-500/10',
        tones: {
            cyan: { icon: 'bg-sky-300/15 text-sky-200 ring-sky-200/20', glow: 'from-sky-300/20', text: 'text-sky-200' },
            violet: { icon: 'bg-slate-300/15 text-slate-200 ring-slate-200/20', glow: 'from-slate-300/20', text: 'text-slate-200' },
            amber: { icon: 'bg-zinc-300/15 text-zinc-200 ring-zinc-200/20', glow: 'from-zinc-300/20', text: 'text-zinc-200' },
            emerald: { icon: 'bg-teal-300/15 text-teal-200 ring-teal-200/20', glow: 'from-teal-300/20', text: 'text-teal-200' }
        },
        chart: { primary: '#bae6fd', primaryLight: '#f1f5f9', secondary: '#94a3b8', canvas: '#0b1118', globeEnd: '#0369a1' }
    },
    spring: {
        key: 'spring',
        label: '스프링 블라썸',
        canvasClass: 'bg-[#101827] dark:bg-[#101827]',
        backgroundFilter: 'hue-rotate(76deg) saturate(0.92) brightness(1.08)',
        overlayClass: 'from-[#101827]/28 via-[#101827]/62 to-[#101827]/91',
        primaryGlowClass: 'bg-pink-400/20',
        secondaryGlowClass: 'bg-lime-400/15',
        tertiaryGlowClass: 'bg-sky-400/10',
        headerGradientClass: 'from-pink-400/20 via-sky-400/10 to-lime-400/12',
        liveBadgeClass: 'border-pink-200/25 bg-pink-200/10 text-pink-100',
        presentationButtonClass: 'hover:border-pink-200/35 hover:text-pink-100',
        logoGradientClass: 'from-pink-200 to-rose-400 shadow-pink-400/20',
        subtitleClass: 'text-pink-100/70',
        primaryTextClass: 'text-pink-200',
        secondaryTextClass: 'text-lime-200',
        primaryDotClass: 'bg-pink-300',
        secondaryDotClass: 'bg-lime-300',
        primaryBarClass: 'from-pink-300 to-rose-400',
        globeHaloClass: 'bg-pink-300/10',
        globeOrbitClass: 'border-pink-200/25',
        globeOrbitDotClass: 'bg-pink-200 shadow-[0_0_16px_4px_rgba(251,207,232,0.5)]',
        globeBadgeClass: 'border-pink-200/25 text-pink-100',
        partnerCardClass: 'from-pink-400/10 to-lime-400/10',
        deadlineCardClass: 'from-sky-400/10 to-pink-400/10',
        tones: {
            cyan: { icon: 'bg-pink-300/15 text-pink-200 ring-pink-200/20', glow: 'from-pink-300/20', text: 'text-pink-200' },
            violet: { icon: 'bg-sky-300/15 text-sky-200 ring-sky-200/20', glow: 'from-sky-300/20', text: 'text-sky-200' },
            amber: { icon: 'bg-lime-300/15 text-lime-200 ring-lime-200/20', glow: 'from-lime-300/20', text: 'text-lime-200' },
            emerald: { icon: 'bg-rose-300/15 text-rose-200 ring-rose-200/20', glow: 'from-rose-300/20', text: 'text-rose-200' }
        },
        chart: { primary: '#f9a8d4', primaryLight: '#fce7f3', secondary: '#bef264', canvas: '#101827', globeEnd: '#38bdf8' }
    },
    autumn: {
        key: 'autumn',
        label: '어텀 헤리티지',
        canvasClass: 'bg-[#1a100d] dark:bg-[#1a100d]',
        backgroundFilter: 'hue-rotate(142deg) saturate(1.15) brightness(0.88)',
        overlayClass: 'from-[#1a100d]/36 via-[#1a100d]/70 to-[#1a100d]/94',
        primaryGlowClass: 'bg-red-700/20',
        secondaryGlowClass: 'bg-amber-600/18',
        tertiaryGlowClass: 'bg-orange-500/10',
        headerGradientClass: 'from-red-800/25 via-orange-600/10 to-amber-500/15',
        liveBadgeClass: 'border-amber-300/20 bg-amber-300/10 text-amber-200',
        presentationButtonClass: 'hover:border-amber-300/30 hover:text-amber-200',
        logoGradientClass: 'from-amber-300 to-red-600 shadow-orange-600/20',
        subtitleClass: 'text-amber-100/70',
        primaryTextClass: 'text-amber-300',
        secondaryTextClass: 'text-red-300',
        primaryDotClass: 'bg-amber-400',
        secondaryDotClass: 'bg-red-400',
        primaryBarClass: 'from-amber-400 to-red-600',
        globeHaloClass: 'bg-amber-400/10',
        globeOrbitClass: 'border-amber-300/20',
        globeOrbitDotClass: 'bg-amber-300 shadow-[0_0_16px_4px_rgba(252,211,77,0.45)]',
        globeBadgeClass: 'border-amber-300/20 text-amber-200',
        partnerCardClass: 'from-red-700/10 to-amber-500/10',
        deadlineCardClass: 'from-orange-600/10 to-red-700/10',
        tones: {
            cyan: { icon: 'bg-amber-400/15 text-amber-300 ring-amber-300/20', glow: 'from-amber-400/20', text: 'text-amber-300' },
            violet: { icon: 'bg-red-400/15 text-red-300 ring-red-300/20', glow: 'from-red-400/20', text: 'text-red-300' },
            amber: { icon: 'bg-orange-400/15 text-orange-300 ring-orange-300/20', glow: 'from-orange-400/20', text: 'text-orange-300' },
            emerald: { icon: 'bg-yellow-500/15 text-yellow-300 ring-yellow-300/20', glow: 'from-yellow-500/20', text: 'text-yellow-300' }
        },
        chart: { primary: '#fbbf24', primaryLight: '#fde68a', secondary: '#f87171', canvas: '#1a100d', globeEnd: '#9f1239' }
    }
};

export const SHOWCASE_PALETTE_OPTIONS = Object.values(SHOWCASE_DASHBOARD_PALETTES);
