import { useCallback, useEffect, useId, useRef, useState } from 'react';
import type { SetStateAction } from 'react';
import { createPortal } from 'react-dom';
import type { LucideIcon } from 'lucide-react';
import {
    Activity,
    Clock3,
    Eye,
    Globe2,
    Maximize2,
    Minimize2,
    MousePointerClick,
    RotateCcw,
    ZoomIn,
    ZoomOut,
    TrendingUp,
    Users
} from 'lucide-react';
import { ShowcaseAnimatedNumber } from './ShowcaseAnimatedNumber';
import landGeoJson from './showcase-land.geojson?raw';
import countryBoundariesGeoJson from './showcase-country-boundaries.geojson?raw';
import { useAnalytics, ratio, percent, countryLabel, dimensionLabels } from '../analytics';
import type { AnalyticsDashboard, AnalyticsProps, Dimension } from '../analytics';
import { ANALYTICS_COUNTRY_POINTS } from '../analyticsCountryPoints';

type Position = [number, number];
type PolygonCoordinates = Position[][];
type MultiPolygonCoordinates = Position[][][];

interface LandGeometry {
    type: 'Polygon' | 'MultiPolygon';
    coordinates: PolygonCoordinates | MultiPolygonCoordinates;
}

type BoundaryGeometry =
    | { type: 'LineString'; coordinates: Position[] }
    | { type: 'MultiLineString'; coordinates: Position[][] };

interface MetricCardData {
    label: string;
    value: number;
    decimals?: number;
    prefix?: string;
    unit?: string;
    change: string;
    detail: string;
    icon: LucideIcon;
    tone: 'cyan' | 'violet' | 'emerald' | 'amber';
    positive: boolean;
}

interface TrendData {
    label: string;
    visitors: number;
    pageViews: number;
}

const MAP_WIDTH = 1200;
// Extend past one world to fit Greenland on the Americas side without clipping it.
const MAP_WEST_LONGITUDE = -30;
const MAP_EAST_LONGITUDE = 350;
const MAP_SCALE = MAP_WIDTH / (MAP_EAST_LONGITUDE - MAP_WEST_LONGITUDE);
const MAP_HEIGHT = 180 * MAP_SCALE;
const MAP_PADDING = 12;
const MAP_VIEW_WIDTH = MAP_WIDTH + MAP_PADDING * 2;
const MAP_VIEW_HEIGHT = MAP_HEIGHT + MAP_PADDING * 2;
const LAND = JSON.parse(landGeoJson) as { features: { geometry: LandGeometry }[] };
const projectUnwrappedPoint = (longitude: number, latitude: number) => ({
    x: (longitude - MAP_WEST_LONGITUDE) * MAP_SCALE,
    y: (90 - latitude) * MAP_SCALE
});
const projectMapPoint = (longitude: number, latitude: number) => projectUnwrappedPoint(
    longitude < MAP_WEST_LONGITUDE ? longitude + 360 : longitude,
    latitude
);
const WORLD_LAND_PATH = LAND.features.flatMap(({ geometry }) => {
    const polygons = geometry.type === 'Polygon'
        ? [geometry.coordinates as PolygonCoordinates]
        : geometry.coordinates as MultiPolygonCoordinates;
    return polygons.flatMap((polygon) => {
        const outerRing = polygon[0];
        const longitudes = outerRing.map(([longitude]) => longitude);
        const middleLongitude = (Math.min(...longitudes) + Math.max(...longitudes)) / 2;
        // Move whole polygons, including their holes, so no coastline crosses the frame.
        // Antarctica surrounds the pole and repeats continuously along the bottom edge.
        const offsets = outerRing.some(([, latitude]) => latitude <= -89)
            ? [0, 360]
            : [middleLongitude < MAP_WEST_LONGITUDE ? 360 : 0];
        return offsets.map((offset) => polygon.map((ring) => `${ring.map(([longitude, latitude], index) => {
            const { x, y } = projectUnwrappedPoint(longitude + offset, latitude);
            return `${index === 0 ? 'M' : 'L'}${x.toFixed(1)} ${y.toFixed(1)}`;
        }).join('')}Z`).join(''));
    });
}).join('');

const COUNTRY_BOUNDARIES = JSON.parse(countryBoundariesGeoJson) as { geometries: BoundaryGeometry[] };
const WORLD_BOUNDARY_PATH = COUNTRY_BOUNDARIES.geometries.flatMap((geometry) => {
    const lines = geometry.type === 'LineString' ? [geometry.coordinates] : geometry.coordinates;
    return lines.map((line) => {
        const points = line.map(([longitude, latitude]) => projectMapPoint(longitude, latitude));
        return points.map(({ x, y }, index) => {
            // Never connect opposite edges across the longitude wrap.
            const move = index === 0 || Math.abs(x - points[index - 1].x) > MAP_WIDTH / 2;
            return `${move ? 'M' : 'L'}${x.toFixed(1)} ${y.toFixed(1)}`;
        }).join('');
    });
}).join('');

interface MapRegion { label: string; code: string; value: number; visitors: number; longitude: number; latitude: number; delay: string }
const COUNTRY_POINTS: Record<string, [number, number]> = {
    ...ANALYTICS_COUNTRY_POINTS,
    KR: [127.8,36.5], US: [-98.5,39.5], JP: [138.2,36.2], SG: [103.8,1.35],
    GB: [-2,54], DE: [10,51], AU: [134,-25], CN: [104,35], CA: [-106,56],
    FR: [2,47], IN: [79,22], VN: [106,16], TH: [101,15], ID: [118,-2], NZ: [173,-41]
};
const COLORS = ['bg-cyan-400','bg-violet-400','bg-amber-400','bg-emerald-400'];
const numberFormatter = new Intl.NumberFormat('ko-KR');

const metricToneClass: Record<MetricCardData['tone'], { icon: string; glow: string; text: string }> = {
    cyan: { icon: 'bg-cyan-400/10 text-cyan-300 ring-cyan-300/25', glow: 'from-cyan-400/20', text: 'text-cyan-300' },
    violet: { icon: 'bg-violet-400/10 text-violet-300 ring-violet-300/25', glow: 'from-violet-400/20', text: 'text-violet-300' },
    emerald: { icon: 'bg-emerald-400/10 text-emerald-300 ring-emerald-300/25', glow: 'from-emerald-400/20', text: 'text-emerald-300' },
    amber: { icon: 'bg-amber-400/10 text-amber-300 ring-amber-300/25', glow: 'from-amber-400/20', text: 'text-amber-300' }
};

const formatDuration = (seconds: number) => ({ value: Math.floor(seconds / 60), unit: `분 ${seconds % 60}초` });

// Only supplies structure; pending values are never displayed as measured zeroes.
const EMPTY_DASHBOARD: AnalyticsDashboard = {
    startDate: null, endDate: null, generatedAt: '', timezone: 'Asia/Seoul',
    totals: {visitors: 0, sessions: 0, pageViews: 0, totalDurationSeconds: 0, bouncedSessions: 0},
    previous: null, previousConference: null, trend: [], countries: [], mapCountries: [], sources: [],
    realtime: {visitors: 0, change: 0, asOf: '', pages: []}
};

export const AdminUserAnalyticsDashboardPage = (props: AnalyticsProps) => {
    const {data: loadedData, error} = useAnalytics(props, undefined, undefined, 'dashboard');
    const data = loadedData ?? EMPTY_DASHBOARD;
    const ready = loadedData !== null;
    const pendingText = props.conferenceSeq === null ? '행사를 선택해 주세요.' : error ? '잠시 후 다시 조회합니다.' : '집계 중…';
    const loading = !ready && !error && props.conferenceSeq !== null;
    const [isPresentationMode, setIsPresentationMode] = useState(false);

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

    const totals = data.totals;
    const previous = data.previous;
    const previousConference = data.previousConference;
    const comparisonChange = (value: number, previousValue?: number) => {
        if (!previousConference) return '비교할 이전 행사 없음';
        if (!previous || previous.pageViews === 0) return '이전 행사 통계 없음';
        if (previousValue === undefined || previousValue === 0) return '이전 행사 기준값 0 · 비교 불가';
        return `직전 행사 대비 ${value >= previousValue ? '+' : ''}${((value - previousValue) / previousValue * 100).toFixed(1)}%`;
    };
    const comparisonTarget = previousConference
        ? `비교: #${previousConference.conferenceSeq} ${previousConference.eventName || '이전 행사'} · 전체 누적`
        : '비교할 이전 행사 없음';
    const averageSeconds = ratio(totals.totalDurationSeconds, totals.sessions);
    const duration = formatDuration(Math.round(averageSeconds));
    const metrics: MetricCardData[] = [
        { label: '순 방문자', value: totals.visitors, unit: '명', change: comparisonChange(totals.visitors, previous?.visitors), detail: '행사 전체 기간 중복 방문 제외', icon: Users, tone: 'cyan', positive: true },
        { label: '세션', value: totals.sessions, unit: '회', change: comparisonChange(totals.sessions, previous?.sessions), detail: `방문자당 ${ratio(totals.sessions,totals.visitors).toFixed(1)}회 방문`, icon: MousePointerClick, tone: 'violet', positive: true },
        { label: '페이지뷰', value: totals.pageViews, unit: '회', change: comparisonChange(totals.pageViews, previous?.pageViews), detail: `세션당 ${ratio(totals.pageViews,totals.sessions).toFixed(1)}페이지`, icon: Eye, tone: 'emerald', positive: true },
        { label: '평균 체류시간', value: duration.value, unit: duration.unit, change: comparisonChange(averageSeconds, previous ? ratio(previous.totalDurationSeconds,previous.sessions) : undefined), detail: `이탈률 ${percent(totals.bouncedSessions,totals.sessions)}%`, icon: Clock3, tone: 'amber', positive: true }
    ];
    const countries = [...data.countries].sort((left,right) => right.visitors-left.visitors);
    const mapCountries = [...(data.mapCountries || [])].sort((left,right) => right.visitors-left.visitors);
    const countryTotal = mapCountries.reduce((sum, country) => sum + country.visitors, 0);
    const mapRegions: MapRegion[] = mapCountries.filter(row => COUNTRY_POINTS[row.dimensionKey]).map((row,index) => ({
        code: row.dimensionKey, label: countryLabel(row.dimensionKey), value: Number(percent(row.visitors,countryTotal)), visitors: row.visitors,
        longitude: COUNTRY_POINTS[row.dimensionKey][0], latitude: COUNTRY_POINTS[row.dimensionKey][1], delay: `${-(index % 7) * 0.4}s`
    }));
    const trend = data.trend.map(row => ({label: row.date, visitors: row.visitors, pageViews: row.pageViews}));
    const trendPeriod = data.startDate && data.endDate ? `${data.startDate} ~ ${data.endDate}` : '접속 기록 없음';
    const sourceTotal = data.sources.reduce((sum, row) => sum + row.sessions, 0);
    const sources = data.sources.map((row,index) => ({key: row.dimensionKey, label: dimensionLabels[row.dimensionKey] || row.dimensionKey, value: Number(percent(row.sessions,sourceTotal)), color: COLORS[index % COLORS.length]}));

    return (
        <div className={`@container ${isPresentationMode ? 'fixed inset-0 z-[100] h-dvh overflow-auto p-2' : 'relative w-full rounded-2xl p-2'} bg-[#061124] text-white shadow-2xl dark:bg-[#061124] dark:text-white`}>
            <div className={`relative isolate flex flex-col overflow-hidden rounded-2xl border border-white/10 bg-[#0b1728] dark:border-white/10 dark:bg-[#0b1728] @min-[1400px]:min-h-[960px] ${isPresentationMode ? '@min-[1400px]:h-full' : '@min-[1400px]:h-[1040px]'}`}>
                <div className="pointer-events-none absolute inset-0 -z-10 bg-cover bg-center opacity-10" style={{ backgroundImage: "url('/images/showcase-dashboard-background.png')" }} aria-hidden="true" />
                <header className="relative z-10 grid gap-3 border-b border-white/10 bg-slate-950/55 p-3 backdrop-blur-xl dark:border-white/10 dark:bg-slate-950/55 @min-[1400px]:grid-cols-[250px_minmax(0,1fr)]">
                    <div className="flex items-center gap-3">
                        <button type="button" onClick={() => setIsPresentationMode((value) => !value)} aria-label={isPresentationMode ? '프레젠테이션 모드 종료' : '프레젠테이션 모드 시작'} aria-pressed={isPresentationMode} title={isPresentationMode ? '프레젠테이션 모드 종료' : '프레젠테이션 모드 시작'} className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-full border border-cyan-300/25 bg-cyan-300/10 text-cyan-200 transition hover:bg-cyan-300/20 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-300 dark:border-cyan-300/25 dark:bg-cyan-300/10 dark:text-cyan-200 dark:hover:bg-cyan-300/20">
                            {isPresentationMode ? <Minimize2 className="h-3.5 w-3.5" /> : <Maximize2 className="h-3.5 w-3.5" />}
                        </button>
                        <div>
                            <p className="text-[9px] font-bold uppercase tracking-[0.16em] text-cyan-300">User traffic overview</p>
                            <h1 className="mt-1 text-base font-bold">사용자 접속 현황판</h1>
                            <p className="mt-1 text-[10px] text-slate-400 dark:text-slate-400">선택 행사 전체 누적 통계</p>
                            <p role="status" className="mt-1 text-[9px] text-slate-400 dark:text-slate-400">{ready ? comparisonTarget : pendingText}</p>
                            <p className="mt-1 text-[9px] text-slate-400 dark:text-slate-400">실시간 접속 · 최근 5분 <span className="mx-1 text-slate-600 dark:text-slate-600">·</span> <a href="/admin/dashboard/user-analytics-details" className="text-cyan-300 underline underline-offset-2 dark:text-cyan-300">상세 현황</a></p>
                        </div>
                    </div>
                    <section aria-label="접속 핵심 지표" className="grid grid-cols-2 gap-2 sm:grid-cols-4">
                        {metrics.map((metric, index) => <MetricCard key={metric.label} metric={metric} delay={index * 100} ready={ready} loading={loading} />)}
                    </section>
                </header>

                <WorldTrafficMap isPresentationMode={isPresentationMode} regions={mapRegions} />
                <div className="hidden @min-[1400px]:block @min-[1400px]:min-h-0 @min-[1400px]:flex-1" aria-hidden="true" />

                <section aria-label="주요 접속 현황" className="relative z-10 grid gap-3 p-3 sm:grid-cols-2 @min-[1400px]:grid-cols-[1fr_1.6fr_1fr_1fr]">
                    <CountryRanking countries={countries} ready={ready} loading={loading} />
                    <article className={dockPanelClass}>
                        <PanelHeader eyebrow="Audience trend" title="방문 추이" description={ready ? `전체 기간 · ${trendPeriod}` : '전체 기간'} icon={TrendingUp} tone="text-cyan-300" />
                        <div className="flex items-center gap-4 px-4 text-[10px] text-slate-400">
                            <span className="inline-flex items-center gap-1.5"><span className="h-1.5 w-1.5 rounded-full bg-cyan-400" />순 방문자</span>
                            <span className="inline-flex items-center gap-1.5"><span className="h-1.5 w-1.5 rounded-full bg-violet-400" />페이지뷰</span>
                        </div>
                        <div className="flex min-h-0 flex-1 items-end px-3 pb-3">{ready ? <TrendChart data={trend} /> : <div className="w-full py-6"><PendingRows loading={loading} /></div>}</div>
                    </article>
                    <article className={dockPanelClass}>
                        <PanelHeader eyebrow="Now online" title="실시간 접속" description="최근 5분 활성 사용자" icon={Activity} tone="text-emerald-300" />
                        <div className="flex items-end justify-between px-4 pb-2">
                            <p className="text-3xl font-black tabular-nums text-emerald-300">{ready ? <ShowcaseAnimatedNumber value={data.realtime.visitors} /> : <span>—</span>}<span className="ml-1 text-xs text-slate-400">명</span></p>
                            {ready && <span className="text-[10px] font-bold text-emerald-300">{data.realtime.change >= 0 ? '+' : ''}{data.realtime.change}명</span>}
                        </div>
                        <div className="mx-4 mb-3 divide-y divide-white/[0.06]">
                            {data.realtime.pages.slice(0,4).map((page) => <div key={page.path} className="flex items-center justify-between gap-3 py-1.5 text-[11px]"><span className="truncate text-slate-300">{page.title || page.path}</span><strong className="shrink-0 tabular-nums">{page.visitors}명</strong></div>)}
                            {!ready ? <PendingRows loading={loading} /> : data.realtime.pages.length === 0 && <p className="py-2 text-[11px] text-slate-500">현재 활성 접속자가 없습니다.</p>}
                        </div>
                    </article>
                    <article className={dockPanelClass}>
                        <PanelHeader eyebrow="Acquisition" title="유입경로" description="전체 기간 · 세션 시작 경로별 비중" icon={MousePointerClick} tone="text-violet-300" />
                        <div className="space-y-3 px-4 pb-4">
                            {!ready && <PendingRows loading={loading} />}
                            {ready && sources.length === 0 && <p className="text-xs text-slate-400 dark:text-slate-400">접속 기록이 없습니다.</p>}
                            {sources.map((source) => <div key={source.key}>
                                <div className="mb-1.5 flex justify-between text-[11px]"><span className="text-slate-300">{source.label}</span><strong className="tabular-nums">{source.value}%</strong></div>
                                <div className="h-1 overflow-hidden rounded-full bg-white/[0.08]"><div className={`h-full rounded-full ${source.color}`} style={{ width: `${source.value}%` }} /></div>
                            </div>)}
                        </div>
                    </article>
                </section>
            </div>
        </div>
    );
};

const dockPanelClass = 'flex min-w-0 flex-col overflow-hidden rounded-2xl border border-white/10 bg-[#071426]/90 shadow-xl backdrop-blur-xl dark:border-white/10 dark:bg-[#071426]/90';

const PendingRows = ({loading}: {loading: boolean}) => (
    <div aria-hidden="true" className={`w-full space-y-3 ${loading ? 'animate-pulse motion-reduce:animate-none' : ''}`}>
        {[0, 1, 2].map(row => <div key={row} className="h-2 rounded bg-slate-700/50 dark:bg-slate-700/50" />)}
    </div>
);

const MetricCard = ({ metric, delay, ready, loading }: { metric: MetricCardData; delay: number; ready: boolean; loading: boolean }) => {
    const Icon = metric.icon;
    const tone = metricToneClass[metric.tone];
    return (
        <article className="relative min-w-0 overflow-hidden rounded-xl border border-white/[0.07] bg-white/[0.03] px-3 py-2 dark:border-white/[0.07] dark:bg-white/[0.03]">
            <div className={`pointer-events-none absolute inset-0 bg-gradient-to-br ${tone.glow} to-transparent opacity-40`} />
            <div className="relative flex items-center justify-between gap-2">
                <span className="inline-flex items-center gap-1.5 text-[10px] font-semibold text-slate-400"><Icon className={`h-3.5 w-3.5 ${tone.text}`} />{metric.label}</span>
            </div>
            <p className="relative mt-1 text-2xl font-black leading-none tracking-tight tabular-nums">{ready ? <ShowcaseAnimatedNumber value={metric.value} decimals={metric.decimals} delay={delay} /> : <span className={loading ? 'animate-pulse motion-reduce:animate-none' : ''}>—</span>}<span className="ml-1 text-[11px] font-semibold text-slate-400">{ready ? metric.unit : metric.icon === Clock3 ? '분' : metric.unit}</span></p>
            <p className="relative mt-1 text-[9px] font-semibold text-slate-300 dark:text-slate-300">{ready ? metric.change : loading ? '집계 중…' : '—'}</p>
            <p className="relative mt-1.5 truncate text-[9px] text-slate-500">{ready ? metric.detail : '선택 행사 전체 누적'}</p>
        </article>
    );
};

const PanelHeader = ({ eyebrow, title, description, icon: Icon, tone }: { eyebrow: string; title: string; description: string; icon: LucideIcon; tone: string }) => (
    <div className="flex items-start justify-between gap-3 px-4 pb-3 pt-3"><div><p className={`text-[9px] font-bold uppercase tracking-[0.16em] ${tone}`}>{eyebrow}</p><h2 className="mt-1 text-sm font-bold">{title}</h2><p className="mt-1 text-[9px] text-slate-500">{description}</p></div><Icon className={`mt-1 h-4 w-4 shrink-0 ${tone}`} /></div>
);

const createArcPath = (fromRegion: MapRegion, lift: number) => {
    const toRegion = {longitude: 127.8, latitude: 36.5};
    const from = projectMapPoint(fromRegion.longitude, fromRegion.latitude);
    const to = projectMapPoint(toRegion.longitude, toRegion.latitude);
    const centerX = (from.x + to.x) / 2;
    const centerY = Math.max(8, Math.min(from.y, to.y) - lift);
    return `M${from.x.toFixed(1)} ${from.y.toFixed(1)} Q${centerX.toFixed(1)} ${centerY.toFixed(1)} ${to.x.toFixed(1)} ${to.y.toFixed(1)}`;
};

const CountryRanking = ({countries, ready, loading}: {countries: Dimension[]; ready: boolean; loading: boolean}) => {
    const total = countries.reduce((sum,row) => sum + row.visitors,0);
    const regions = countries.slice(0,4).map(row => ({code: row.dimensionKey,label: countryLabel(row.dimensionKey),visitors:row.visitors,value:percent(row.visitors,total)}));
    const rest = countries.slice(4).reduce((sum,row) => sum + row.visitors,0);
    if (rest > 0) regions.push({code:'ETC',label:'기타',visitors:rest,value:percent(rest,total)});
    return (
    <aside aria-label="국가별 방문 비중" className={dockPanelClass}>
        <PanelHeader eyebrow="Country distribution" title="국가별 방문 비중" description="전체 기간 · 순 방문자 기준" icon={Globe2} tone="text-cyan-300" />
        <div className="space-y-2.5 px-4 pb-3">
            {!ready && <PendingRows loading={loading} />}
            {ready && regions.length === 0 && <p className="text-xs text-slate-400 dark:text-slate-400">접속 기록이 없습니다.</p>}
            {regions.map((region, index) => (
                <div key={region.code}>
                    <div className="mb-1 flex items-center justify-between gap-3 text-[10px]">
                        <span className="min-w-0 truncate font-semibold text-slate-200"><span className="mr-2 text-slate-500">{region.code}</span>{region.label}</span>
                        <span className="shrink-0 tabular-nums"><strong><ShowcaseAnimatedNumber value={region.visitors} delay={index * 100} /></strong><span className="ml-2 text-slate-500">{region.value}%</span></span>
                    </div>
                    <div className="h-1 overflow-hidden rounded-full bg-white/[0.08]">
                        <span className="block h-full rounded-full bg-gradient-to-r from-cyan-400 to-blue-500" style={{ width: `${region.value}%` }} />
                    </div>
                </div>
            ))}
        </div>
    </aside>
    );
};

interface MapViewport { scale: number; x: number; y: number }
const INITIAL_MAP_VIEW: MapViewport = { scale: 1, x: -MAP_PADDING, y: -MAP_PADDING };
const clampMapView = (view: MapViewport): MapViewport => ({
    ...view,
    x: Math.max(-MAP_PADDING, Math.min(MAP_VIEW_WIDTH - MAP_PADDING - MAP_VIEW_WIDTH / view.scale, view.x)),
    y: Math.max(-MAP_PADDING, Math.min(MAP_VIEW_HEIGHT - MAP_PADDING - MAP_VIEW_HEIGHT / view.scale, view.y))
});
const zoomMapView = (view: MapViewport, factor: number, px = 0.5, py = 0.5): MapViewport => {
    const scale = Math.max(1, Math.min(4, view.scale * factor));
    return clampMapView({ scale,
        x: view.x + MAP_VIEW_WIDTH * px * (1 / view.scale - 1 / scale),
        y: view.y + MAP_VIEW_HEIGHT * py * (1 / view.scale - 1 / scale)
    });
};

const WorldTrafficMap = ({ isPresentationMode, regions }: { isPresentationMode: boolean; regions: MapRegion[] }) => {
    const svgRef = useRef<SVGSVGElement>(null);
    const dragRef = useRef<{ id: number; x: number; y: number } | null>(null);
    const [view, setViewport] = useState<MapViewport>(INITIAL_MAP_VIEW);
    const [dragging, setDragging] = useState(false);
    const [tooltip, setTooltip] = useState<{ code: string; x: number; y: number } | null>(null);
    const tooltipId = useId();
    const setView = useCallback((next: SetStateAction<MapViewport>) => {
        setTooltip(null);
        setViewport(next);
    }, []);
    const tooltipRegion = tooltip ? regions.find(region => region.code === tooltip.code) : undefined;
    const showTooltip = (region: MapRegion, element: SVGGElement) => {
        if (dragRef.current) return;
        const matrix = element.getScreenCTM();
        if (!matrix) return;
        const point = projectMapPoint(region.longitude, region.latitude);
        const screen = new DOMPoint(point.x, point.y).matrixTransform(matrix);
        const width = Math.min(208, window.innerWidth - 16);
        const x = screen.x + 16 + width <= window.innerWidth - 8 ? screen.x + 16 : screen.x - width - 16;
        setTooltip({ code: region.code,
            x: Math.max(8, Math.min(window.innerWidth - width - 8, x)),
            y: Math.max(8, Math.min(window.innerHeight - 164, screen.y - 64))
        });
    };
    useEffect(() => {
        const clear = () => setTooltip(null);
        window.addEventListener('resize', clear);
        window.addEventListener('scroll', clear, true);
        window.addEventListener('blur', clear);
        return () => {
            window.removeEventListener('resize', clear);
            window.removeEventListener('scroll', clear, true);
            window.removeEventListener('blur', clear);
        };
    }, []);
    useEffect(() => {
        const svg = svgRef.current;
        if (!svg) return;
        const onWheel = (event: WheelEvent) => {
            // Keep browser zoom shortcuts available; only capture ordinary map scrolling.
            if (event.ctrlKey || event.metaKey || event.deltaY === 0) return;
            event.preventDefault();
            const rect = svg.getBoundingClientRect();
            if (!rect.width || !rect.height) return;
            const unit = event.deltaMode === WheelEvent.DOM_DELTA_LINE ? 16
                : event.deltaMode === WheelEvent.DOM_DELTA_PAGE ? rect.height : 1;
            const delta = Math.max(-160, Math.min(160, event.deltaY * unit));
            setView(previous => zoomMapView(previous, Math.exp(-delta * 0.0025),
                (event.clientX - rect.left) / rect.width, (event.clientY - rect.top) / rect.height));
        };
        svg.addEventListener('wheel', onWheel, { passive: false });
        return () => svg.removeEventListener('wheel', onWheel);
    }, [setView]);
    const endDrag = () => {
        const drag = dragRef.current;
        dragRef.current = null;
        setDragging(false);
        if (drag && svgRef.current?.hasPointerCapture(drag.id)) svgRef.current.releasePointerCapture(drag.id);
    };
    const resetView = () => {
        endDrag();
        setView(INITIAL_MAP_VIEW);
    };
    const arcs = regions.filter(region => region.code !== 'KR').map((region,index) => ({
        code: region.code,
        path: createArcPath(region, 70 + (index % 5) * 12),
        duration: `${3 + (index % 6) * 0.35}s`,
        delay: `${-(index % 8) * 0.4}s`
    }));
    return (
    <div className={`relative mx-auto w-full overflow-hidden bg-[radial-gradient(circle_at_60%_42%,rgba(34,211,238,0.10),transparent_45%)] @min-[1400px]:absolute @min-[1400px]:inset-x-[2%] @min-[1400px]:top-[72px] @min-[1400px]:w-[96%] ${isPresentationMode ? '@min-[1400px]:max-w-[calc((100dvh-330px)*2.5)]' : '@min-[1400px]:max-w-[1775px]'}`} style={{ aspectRatio: `${MAP_VIEW_WIDTH} / ${MAP_VIEW_HEIGHT}` }}>
        <svg ref={svgRef} viewBox={`${view.x} ${view.y} ${MAP_VIEW_WIDTH / view.scale} ${MAP_VIEW_HEIGHT / view.scale}`}
            className={`absolute inset-0 h-full w-full select-none outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-cyan-300 dark:focus-visible:ring-cyan-300 ${view.scale > 1 ? 'touch-none' : 'touch-pan-y'} ${dragging ? 'cursor-grabbing' : view.scale > 1 ? 'cursor-grab' : 'cursor-zoom-in'}`}
            role="group" aria-label="선택 학회 전체 기간의 국가별 접속 분포 세계지도" tabIndex={0}
            aria-description="휠 또는 +와 - 키로 1~4배 확대·축소합니다. 확대 후 드래그 또는 방향키로 이동하고 0 키로 복원합니다."
            onPointerDown={event => {
                setTooltip(null);
                if (event.button !== 0 || view.scale <= 1 || dragRef.current) return;
                event.preventDefault();
                event.currentTarget.focus({ preventScroll: true });
                event.currentTarget.setPointerCapture(event.pointerId);
                dragRef.current = { id: event.pointerId, x: event.clientX, y: event.clientY };
                setDragging(true);
            }}
            onPointerMove={event => {
                const drag = dragRef.current;
                if (!drag || drag.id !== event.pointerId) return;
                const rect = event.currentTarget.getBoundingClientRect();
                if (!rect.width || !rect.height) return;
                const dx = (event.clientX - drag.x) / rect.width * MAP_VIEW_WIDTH;
                const dy = (event.clientY - drag.y) / rect.height * MAP_VIEW_HEIGHT;
                drag.x = event.clientX;
                drag.y = event.clientY;
                setView(previous => clampMapView({ ...previous, x: previous.x - dx / previous.scale, y: previous.y - dy / previous.scale }));
            }}
            onPointerUp={event => { if (dragRef.current?.id === event.pointerId) endDrag(); }}
            onPointerCancel={event => { if (dragRef.current?.id === event.pointerId) endDrag(); }}
            onLostPointerCapture={endDrag} onBlur={endDrag}
            onKeyDown={event => {
                if (event.ctrlKey || event.metaKey || event.altKey) return;
                if (event.key === 'Escape' && tooltip) {
                    event.preventDefault();
                    event.stopPropagation();
                    setTooltip(null);
                    return;
                }
                if (!['+', '=', '-', '0', 'ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(event.key)) return;
                event.preventDefault();
                if (event.key === '0') resetView();
                else if (event.key.startsWith('Arrow')) setView(previous => clampMapView({ ...previous,
                    x: previous.x + (event.key === 'ArrowLeft' ? -40 : event.key === 'ArrowRight' ? 40 : 0) / previous.scale,
                    y: previous.y + (event.key === 'ArrowUp' ? -40 : event.key === 'ArrowDown' ? 40 : 0) / previous.scale
                }));
                else setView(previous => zoomMapView(previous, event.key === '-' ? 1 / 1.25 : 1.25));
            }}>
            <defs>
                <clipPath id="analytics-land-frame"><rect width={MAP_WIDTH} height={MAP_HEIGHT} /></clipPath>
                <linearGradient id="analytics-land" x1="0" y1="0" x2="1" y2="1"><stop offset="0%" stopColor="#16445c" /><stop offset="55%" stopColor="#12354b" /><stop offset="100%" stopColor="#102b43" /></linearGradient>
                <filter id="analytics-map-glow" x="-150%" y="-150%" width="300%" height="300%"><feGaussianBlur stdDeviation="4" result="blur" /><feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge></filter>
                <pattern id="analytics-map-grid" width="100" height="100" patternUnits="userSpaceOnUse"><path d="M100 0H0V100" fill="none" stroke="#67e8f9" strokeOpacity="0.045" strokeWidth="1" /></pattern>
            </defs>
            <rect x={-MAP_PADDING} y={-MAP_PADDING} width={MAP_VIEW_WIDTH} height={MAP_VIEW_HEIGHT} fill="url(#analytics-map-grid)" />
            <g fill="none" stroke="#94a3b8" strokeOpacity="0.055" strokeWidth="1">{[-60, -30, 0, 30, 60].map((latitude) => { const y = projectMapPoint(0, latitude).y; return <line key={latitude} x1="0" x2={MAP_WIDTH} y1={y} y2={y} />; })}{[-120, -60, 0, 60, 120, 180].map((longitude) => { const x = projectMapPoint(longitude, 0).x; return <line key={longitude} x1={x} x2={x} y1="0" y2={MAP_HEIGHT} />; })}</g>
            <path d={WORLD_LAND_PATH} clipPath="url(#analytics-land-frame)" fill="url(#analytics-land)" fillRule="evenodd" stroke="#58c5df" strokeOpacity="0.34" strokeWidth="1.2" vectorEffect="non-scaling-stroke" />
            <path d={WORLD_BOUNDARY_PATH} clipPath="url(#analytics-land-frame)" fill="none" stroke="#7db7cd" strokeOpacity="0.38" strokeWidth="0.8" strokeLinejoin="round" strokeLinecap="round" vectorEffect="non-scaling-stroke" pointerEvents="none" />
            <g fill="none" stroke="#67e8f9" strokeLinecap="round" strokeWidth={1.25 / view.scale} strokeOpacity="0.52" strokeDasharray={`${6 / view.scale} ${8 / view.scale}`}>{arcs.map((arc) => <path key={arc.code} d={arc.path}><animate attributeName="stroke-dashoffset" from={28 / view.scale} to="0" dur={arc.duration} repeatCount="indefinite" /></path>)}</g>
            {arcs.map((arc) => <circle key={`${arc.code}-signal`} data-country-signal={arc.code} r={2.8 / view.scale} fill="#ecfeff" filter="url(#analytics-map-glow)" className="motion-reduce:hidden"><animateMotion path={arc.path} dur={arc.duration} begin={arc.delay} repeatCount="indefinite" /></circle>)}
            {regions.map((region) => {
                const point = projectMapPoint(region.longitude, region.latitude);
                const alignRight = region.code === 'JP';
                const selected = tooltipRegion?.code === region.code;
                const pointVisible = point.x >= view.x && point.x <= view.x + MAP_VIEW_WIDTH / view.scale
                    && point.y >= view.y && point.y <= view.y + MAP_VIEW_HEIGHT / view.scale;
                return <g key={region.code} data-country-marker={region.code}
                    transform={`translate(${point.x} ${point.y}) scale(${1 / view.scale}) translate(${-point.x} ${-point.y})`}
                    role="button" tabIndex={pointVisible ? 0 : -1}
                    aria-label={`${region.label} 접속 정보`} aria-describedby={selected ? tooltipId : undefined}
                    className="cursor-help outline-none"
                    onPointerEnter={event => showTooltip(region, event.currentTarget)}
                    onPointerMove={event => { if (!dragRef.current && !selected) showTooltip(region, event.currentTarget); }}
                    onPointerLeave={() => setTooltip(null)}
                    onFocus={event => showTooltip(region, event.currentTarget)}
                    onBlur={() => setTooltip(null)}
                    onClick={event => { if (view.scale === 1) showTooltip(region, event.currentTarget); }}
                    onKeyDown={event => {
                        if (event.key !== 'Enter' && event.key !== ' ') return;
                        event.preventDefault();
                        event.stopPropagation();
                        showTooltip(region, event.currentTarget);
                    }}>
                    <g pointerEvents="none">
                        <circle cx={point.x} cy={point.y} r={region.code === 'KR' ? 18 : 14} fill="none" stroke="#67e8f9" strokeOpacity="0.42" strokeWidth="1.2" className="animate-ping motion-reduce:animate-none" style={{ transformOrigin: `${point.x}px ${point.y}px`, animationDelay: region.delay, animationDuration: '2.8s' }} />
                        {selected && <circle cx={point.x} cy={point.y} r="9" fill="none" stroke="#fff" strokeWidth="1.4" />}
                        <circle cx={point.x} cy={point.y} r={region.code === 'KR' ? 5 : 3.5} fill="#67e8f9" filter="url(#analytics-map-glow)" />
                        <circle cx={point.x} cy={point.y} r="1.5" fill="#ffffff" />
                        <text x={point.x + (alignRight ? 13 : -13)} y={point.y - 10} textAnchor={alignRight ? 'start' : 'end'} fill="#f8fafc" fontSize="11" fontWeight="800">{region.code}</text>
                        <text x={point.x + (alignRight ? 13 : -13)} y={point.y + 4} textAnchor={alignRight ? 'start' : 'end'} fill="#94a3b8" fontSize="9">{region.value}%</text>
                    </g>
                    <circle cx={point.x} cy={point.y} r="12" fill="transparent" pointerEvents="all" />
                </g>;
            })}
        </svg>
        <div className="absolute right-2 top-2 flex items-center gap-1 rounded-lg border border-slate-600/70 bg-slate-950/90 p-1 text-slate-200 shadow-lg dark:border-slate-600/70 dark:bg-slate-950/90 dark:text-slate-200 @min-[1400px]:top-20" role="group" aria-label="세계지도 확대 도구">
            <span className="hidden px-2 text-[10px] text-slate-400 dark:text-slate-400 sm:inline">휠 확대 · 드래그 이동</span>
            <button type="button" aria-label="세계지도 축소" title="축소" disabled={view.scale <= 1} onClick={() => setView(previous => zoomMapView(previous, 1 / 1.25))} className="rounded-md p-1.5 hover:bg-slate-800 disabled:opacity-30 dark:hover:bg-slate-800"><ZoomOut className="h-4 w-4" /></button>
            <output aria-label="지도 확대 배율" className="w-10 text-center text-xs font-semibold tabular-nums">{Math.round(view.scale * 100)}%</output>
            <button type="button" aria-label="세계지도 확대" title="확대" disabled={view.scale >= 4} onClick={() => setView(previous => zoomMapView(previous, 1.25))} className="rounded-md p-1.5 hover:bg-slate-800 disabled:opacity-30 dark:hover:bg-slate-800"><ZoomIn className="h-4 w-4" /></button>
            <button type="button" aria-label="세계지도 원래 크기로 복원" title="원래 크기로 복원" onClick={resetView} className="rounded-md p-1.5 hover:bg-slate-800 dark:hover:bg-slate-800"><RotateCcw className="h-4 w-4" /></button>
        </div>
        {tooltip && tooltipRegion && !dragging && createPortal(
            <div id={tooltipId} role="tooltip"
                className="pointer-events-none fixed z-[150] w-52 max-w-[calc(100vw-16px)] rounded-xl border border-slate-600 bg-slate-950/95 px-4 py-3 text-white shadow-xl backdrop-blur-md dark:border-slate-600 dark:bg-slate-950/95 dark:text-white"
                style={{ left: tooltip.x, top: tooltip.y }}>
                <div className="flex items-center justify-between gap-3">
                    <p className="min-w-0 break-words text-sm font-semibold">{tooltipRegion.label}</p>
                    <span className="shrink-0 text-[10px] font-semibold text-slate-400 dark:text-slate-400">{tooltipRegion.code}</span>
                </div>
                <p className="mt-2 text-[11px] text-slate-400 dark:text-slate-400">순 방문자 · 전체 기간</p>
                <p className="mt-0.5 text-xl font-bold tabular-nums text-cyan-300 dark:text-cyan-300">{numberFormatter.format(tooltipRegion.visitors)}<span className="ml-1 text-xs font-medium">명</span></p>
                <p className="mt-2 border-t border-slate-800 pt-2 text-[11px] text-slate-400 dark:border-slate-800 dark:text-slate-400">방문 비중 <span className="float-right font-semibold tabular-nums text-slate-200 dark:text-slate-200">{tooltipRegion.value}%</span></p>
            </div>, document.body
        )}
    </div>
    );
};

const TrendChart = ({ data }: { data: TrendData[] }) => {
    if (data.length === 0) return <p className="w-full py-8 text-center text-xs text-slate-400 dark:text-slate-400">접속 기록이 없습니다.</p>;
    const width = 760;
    const height = 176;
    const chartTop = 14;
    const chartBottom = 140;
    const chartHeight = chartBottom - chartTop;
    const maxValue = data.reduce((max,item) => Math.max(max,item.visitors,item.pageViews),1);
    const point = (value: number, index: number) => ({ x: 20 + index * ((width - 40) / Math.max(1, data.length - 1)), y: chartBottom - value / maxValue * chartHeight });
    const visitorPoints = data.map((item, index) => point(item.visitors, index));
    const pageViewPoints = data.map((item, index) => point(item.pageViews, index));
    const polyline = (points: { x: number; y: number }[]) => points.map(({ x, y }) => `${x},${y}`).join(' ');
    const area = `${pageViewPoints.map(({ x, y }) => `${x},${y}`).join(' ')} ${pageViewPoints.at(-1)?.x ?? width - 20},${chartBottom} ${pageViewPoints[0]?.x ?? 20},${chartBottom}`;
    return <svg viewBox={`0 0 ${width} ${height}`} className="h-auto w-full" role="img" aria-label="행사 전체 기간 순 방문자와 페이지뷰 추이 그래프"><defs><linearGradient id="analytics-trend-area" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#8b5cf6" stopOpacity="0.25" /><stop offset="100%" stopColor="#8b5cf6" stopOpacity="0" /></linearGradient></defs>{[0, 0.33, 0.66, 1].map((ratio) => <line key={ratio} x1="20" x2={width - 20} y1={chartTop + ratio * chartHeight} y2={chartTop + ratio * chartHeight} stroke="#ffffff" strokeOpacity="0.07" strokeDasharray="4 6" />)}<polygon points={area} fill="url(#analytics-trend-area)" /><polyline points={polyline(pageViewPoints)} fill="none" stroke="#a78bfa" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" /><polyline points={polyline(visitorPoints)} fill="none" stroke="#22d3ee" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />{visitorPoints.map(({ x, y }, index) => <circle key={data[index].label} cx={x} cy={y} r="3" fill="#67e8f9"><title>{`${data[index].label} 순 방문자 ${numberFormatter.format(data[index].visitors)}명`}</title></circle>)}{data.map((item, index) => (index % Math.max(1, Math.ceil(data.length / 10)) === 0 || index === data.length - 1) && <text key={item.label} x={point(item.visitors, index).x} y="164" textAnchor={index === 0 ? 'start' : index === data.length - 1 ? 'end' : 'middle'} fontSize="9" fill="#64748b">{item.label.slice(2)}</text>)}</svg>;
};
