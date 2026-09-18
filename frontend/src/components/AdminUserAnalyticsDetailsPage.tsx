import { useState } from 'react';
import type { FormEvent } from 'react';
import { Activity, BarChart3, Search, FilterX, Users, Eye, Clock3, MousePointerClick, Layers, LogOut, Monitor, LoaderCircle } from 'lucide-react';
import { useAnalytics, countryLabel, dimensionLabels, ratio, percent, changeLabel } from '../analytics';
import type { AnalyticsOverview, AnalyticsProps, Dimension } from '../analytics';

const format = new Intl.NumberFormat('ko-KR');
const today = () => new Intl.DateTimeFormat('sv-SE', {timeZone:'Asia/Seoul'}).format(new Date());
const daysAgo = (days: number) => { const date = new Date(today() + 'T00:00:00Z'); date.setUTCDate(date.getUTCDate() - days); return date.toISOString().slice(0,10); };
const card = 'overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-950';
const input = 'w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-700 dark:bg-slate-950 dark:text-slate-50';
const muted = 'text-slate-500 dark:text-slate-400';
const heading = 'border-b border-slate-200 p-4 text-sm font-semibold dark:border-slate-800 md:px-5';
const duration = (seconds: number) => `${Math.floor(seconds / 60)}분 ${Math.round(seconds % 60)}초`;

export const AdminUserAnalyticsDetailsPage = (props: AnalyticsProps & {dashboardEnabled?: boolean}) => {
    const [draft, setDraft] = useState({start:daysAgo(29), end:today()});
    const [period, setPeriod] = useState('30');
    const [range, setRange] = useState(draft);
    const {data,error,refresh} = useAnalytics(props,range.start,range.end);
    const submit = (event: FormEvent) => {
        event.preventDefault();
        const days = (Date.parse(draft.end) - Date.parse(draft.start)) / 86400000;
        if (!Number.isFinite(days) || days < 0 || days > 89 || draft.end > today()) {
            props.onNotify('error','조회 기간은 오늘까지 최대 90일로 선택해 주세요.'); return;
        }
        setRange({...draft});
        refresh();
    };
    const reset = () => {const next={start:daysAgo(29),end:today()};setDraft(next);setRange(next);setPeriod('30');refresh();};
    const maxHour = Math.max(1,...(data?.hourly.map(row=>row.pageViews) || []));
    const metrics = [
        {label:'순 방문자',value:data?.totals.visitors,previous:data?.previous.visitors,unit:'명',icon:Users},
        {label:'세션',value:data?.totals.sessions,previous:data?.previous.sessions,unit:'회',icon:MousePointerClick},
        {label:'페이지뷰',value:data?.totals.pageViews,previous:data?.previous.pageViews,unit:'회',icon:Eye},
        {label:'평균 체류시간',value:data ? Math.round(ratio(data.totals.totalDurationSeconds,data.totals.sessions)) : undefined,previous:data ? Math.round(ratio(data.previous.totalDurationSeconds,data.previous.sessions)) : undefined,unit:'',icon:Clock3,time:true},
        {label:'세션당 페이지뷰',value:data ? ratio(data.totals.pageViews,data.totals.sessions) : undefined,previous:data ? ratio(data.previous.pageViews,data.previous.sessions) : undefined,unit:'회',icon:Layers,decimal:true},
        {label:'이탈률',value:data ? ratio(data.totals.bouncedSessions,data.totals.sessions)*100 : undefined,previous:data ? ratio(data.previous.bouncedSessions,data.previous.sessions)*100 : undefined,unit:'%',icon:LogOut,decimal:true,percentage:true}
    ];
    return <div className="space-y-4 pb-6 text-slate-900 dark:text-slate-50">
        <section className={card}>
            <header className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 p-4 dark:border-slate-800 md:px-5">
                <div><h1 className="flex items-center gap-2 text-sm font-semibold md:text-base"><BarChart3 className="h-4 w-4" />사용자 접속 통계</h1><p className="mt-1 text-xs text-slate-400 dark:text-slate-400">공개 사용자 화면의 방문·유입·이용 현황을 기간별로 분석합니다.</p></div>
                {props.dashboardEnabled && <a href="/admin/dashboard/user-analytics" className="inline-flex items-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900"><Monitor className="h-4 w-4" />현황판 보기</a>}
            </header>
            <div className="grid grid-cols-1 gap-px bg-slate-200 dark:bg-slate-800 sm:grid-cols-3 2xl:grid-cols-6" aria-label="접속 요약 통계">
                {metrics.map(({label,value,previous,unit,icon:Icon,time,decimal,percentage}) => <div key={label} className="min-w-0 bg-white p-4 dark:bg-slate-950 md:p-5">
                    <p className={`flex items-center gap-2 text-xs font-medium ${muted}`}><Icon className="h-4 w-4" />{label}</p>
                    <p className="mt-1 text-xl font-bold tabular-nums">{value === undefined ? (props.conferenceSeq === null || error ? '-' : '집계 중…') : time ? duration(value) : decimal ? value.toFixed(1) : format.format(value)}{value !== undefined && <span className={`ml-1 text-xs font-normal ${muted}`}>{unit}</span>}</p>
                    <p className={`mt-2 text-[11px] ${muted}`}>{value !== undefined && previous !== undefined ? percentage ? data?.previous.sessions ? `이전 기간 대비 ${value >= previous ? '+' : ''}${(value-previous).toFixed(1)}%p` : '이전 기간 데이터 없음' : changeLabel(value,previous) : '동일 길이의 직전 기간과 비교'}</p>
                </div>)}
            </div>
            <form onSubmit={submit} className="border-b border-slate-200 bg-slate-50/70 p-4 dark:border-slate-800 dark:bg-slate-900/40 md:p-5">
                <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
                    <label><span className="mb-1 block text-[11px] font-semibold text-slate-500 dark:text-slate-400">기간 선택</span><select aria-label="기간 선택" value={period} className={input} onChange={event=>{setPeriod(event.target.value);if(event.target.value !== 'custom') setDraft({start:daysAgo(Number(event.target.value)-1),end:today()});}}><option value="custom">직접 설정</option>{[7,30,60,90].map(days=><option key={days} value={days}>최근 {days}일</option>)}</select></label>
                    <label><span className="mb-1 block text-[11px] font-semibold text-slate-500 dark:text-slate-400">시작일</span><input required type="date" aria-label="조회 시작일" className={input} value={draft.start} max={draft.end} onChange={e=>{setPeriod('custom');setDraft({...draft,start:e.target.value});}} /></label>
                    <label><span className="mb-1 block text-[11px] font-semibold text-slate-500 dark:text-slate-400">종료일</span><input required type="date" aria-label="조회 종료일" className={input} value={draft.end} min={draft.start} max={today()} onChange={e=>{setPeriod('custom');setDraft({...draft,end:e.target.value});}} /></label>
                </div>
                <div className="mt-3 flex flex-col justify-end gap-2 sm:flex-row">
                    <button type="button" onClick={reset} className="inline-flex items-center justify-center gap-2 rounded-lg border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-white dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-900"><FilterX className="h-4 w-4" />초기화</button>
                    <button type="submit" className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-5 py-2 text-xs font-semibold text-white hover:bg-blue-700 dark:bg-blue-600 dark:hover:bg-blue-700"><Search className="h-4 w-4" />조회</button>
                </div>
            </form>
        </section>
        {!data && !error && <div role="status" className={`flex items-center justify-center gap-2 py-12 text-sm ${muted}`}>{props.conferenceSeq === null ? '통계를 조회할 학회를 선택해 주세요.' : <><LoaderCircle className="h-4 w-4 animate-spin" />접속 통계를 집계하고 있습니다.</>}</div>}
        {error && <p role="status" className="text-xs text-amber-700 dark:text-amber-300">{data ? '통계 갱신이 지연되어 이전 조회값을 표시합니다.' : '통계를 불러오지 못했습니다. 조회 버튼으로 다시 확인해 주세요.'}</p>}
        {data && <>
            <div className={`flex flex-wrap justify-between gap-2 text-xs ${muted}`}><span>조회 기간 {data.startDate} ~ {data.endDate} · 서울 시간 · 봇 제외</span><span>집계 시각 {data.generatedAt.replace('T',' ').slice(0,19)}</span></div>
            <section className="grid min-w-0 gap-4 xl:grid-cols-[minmax(0,3fr)_minmax(260px,1fr)]">
                <VisitTrend key={`${data.startDate}:${data.endDate}`} rows={data.trend} />
                <article className={card}><h2 className={`${heading} flex items-center gap-2`}><Activity className="h-4 w-4 text-emerald-600 dark:text-emerald-400" />실시간 접속</h2><div className="p-4 md:p-5"><p className="text-3xl font-bold text-emerald-600 dark:text-emerald-400">{format.format(data.realtime.visitors)}<span className="ml-1 text-sm">명</span></p><p className={`mt-2 text-xs ${muted}`}>최근 5분 활동 · 종료한 방문자 제외</p><div className="mt-5 space-y-3">{data.realtime.pages.length === 0 && <p className={`py-6 text-center text-xs ${muted}`}>현재 활동 중인 방문자가 없습니다.</p>}{data.realtime.pages.map(page=><div key={page.path} className="flex justify-between gap-3 text-xs"><span className="min-w-0 truncate" title={page.path}>{page.title || page.path}</span><strong className="shrink-0 tabular-nums">{format.format(page.visitors)}명</strong></div>)}</div><p className={`mt-4 border-t border-slate-100 pt-3 text-[11px] dark:border-slate-800 ${muted}`}>조회 기간과 관계없이 현재 접속 상태를 표시합니다.</p></div></article>
            </section>
            <section className="grid gap-4 lg:grid-cols-3">
                <Distribution title="유입경로" rows={data.sources} metric="sessions" />
                <Distribution title="접속 국가" rows={data.countries} metric="visitors" countries />
                <Distribution title="접속 기기" rows={data.devices} metric="visitors" />
            </section>
            <section className="grid gap-4 lg:grid-cols-2">
                <Distribution title="주요 브라우저" rows={data.browsers} metric="visitors" />
                <Distribution title="운영체제" rows={data.operatingSystems} metric="visitors" />
            </section>
            <section className={card}>
                <h2 className="border-b border-slate-200 p-4 text-sm font-semibold dark:border-slate-800">접속 시간대</h2>
                <div className="overflow-x-auto p-4"><div className={`grid min-w-[760px] grid-cols-[28px_repeat(24,minmax(0,1fr))] gap-1 text-center text-[10px] ${muted}`}>
                    <span />{Array.from({length:24},(_,hour)=><span key={hour}>{String(hour).padStart(2,'0')}</span>)}
                    {['월','화','수','목','금','토','일'].flatMap((day,dayOfWeek)=>[
                        <span key={day} className="self-center">{day}</span>,
                        ...Array.from({length:24},(_,hour)=>{
                            const value=data.hourly.find(row=>row.dayOfWeek===dayOfWeek && row.hour===hour)?.pageViews || 0;
                            const label=`${day}요일 ${hour}~${hour+1}시: ${format.format(value)} 페이지뷰`;
                            return <span key={`${day}-${hour}`} aria-label={label} title={label} tabIndex={0} className="flex h-9 items-center justify-center rounded text-[9px] text-slate-900 focus:outline-2 focus:outline-blue-500 dark:text-slate-50" style={{backgroundColor:`rgb(6 182 212 / ${value ? 0.12+0.7*value/maxHour : 0.04})`}}>{value > 0 ? format.format(value) : '–'}</span>;
                        })
                    ])}
                </div></div><p className={`px-4 pb-4 text-xs ${muted}`}>선택 기간의 요일·시간별 페이지뷰 합계 · 진할수록 많은 접속 · Asia/Seoul</p>
            </section>
            <section className={card}>
                <h2 className="border-b border-slate-200 p-4 text-sm font-semibold dark:border-slate-800">인기 페이지</h2>
                <div className="overflow-x-auto"><table className="w-full min-w-[720px] text-left text-xs md:text-sm" aria-label="인기 페이지 통계">
                    <thead className="bg-slate-50 dark:bg-slate-900"><tr><th className="p-4">페이지</th><th className="p-4 text-right">페이지뷰</th><th className="p-4 text-right">순 방문자</th><th className="p-4 text-right">세션</th><th className="p-4 text-right">종료 세션</th><th className="p-4 text-right">종료율</th></tr></thead>
                    <tbody className="divide-y divide-slate-100 dark:divide-slate-800">{data.pages.length === 0 && <tr><td colSpan={6} className={`p-10 text-center ${muted}`}>조회 기간의 페이지뷰가 없습니다.</td></tr>}{data.pages.map(page=><tr key={page.dimensionKey} className="hover:bg-slate-50 dark:hover:bg-slate-900"><td className="max-w-sm p-4"><p className="break-words font-semibold">{page.pageTitle || page.dimensionKey}</p><p className={`mt-1 break-all ${muted}`}>{page.dimensionKey}</p></td><td className="p-4 text-right tabular-nums">{format.format(page.pageViews)}</td><td className="p-4 text-right tabular-nums">{format.format(page.visitors)}</td><td className="p-4 text-right tabular-nums">{format.format(page.sessions)}</td><td className="p-4 text-right tabular-nums">{format.format(page.exits || 0)}</td><td className="p-4 text-right">{percent(page.exits || 0,page.pageViews)}%</td></tr>)}</tbody>
                </table></div><p className={`p-4 text-xs ${muted}`}>종료율 = 해당 페이지에서 끝난 세션 ÷ 페이지뷰. 30분 이상 활동이 없는 세션의 마지막 페이지를 기준으로 합니다.</p>
            </section>
            <details className={card}>
                <summary className="cursor-pointer border-b border-slate-200 p-4 text-sm font-semibold dark:border-slate-800">일별 수치 전체 보기 · {data.trend.length}일</summary>
                <div className="max-h-80 overflow-auto"><table className="w-full text-left text-xs md:text-sm" aria-label="일별 방문 추이">
                    <thead className="sticky top-0 bg-slate-50 dark:bg-slate-900"><tr><th className="p-4">날짜</th><th className="p-4 text-right">순 방문자</th><th className="p-4 text-right">페이지뷰</th></tr></thead>
                    <tbody className="divide-y divide-slate-100 dark:divide-slate-800">{data.trend.map(day=><tr key={day.date} className="hover:bg-slate-50 dark:hover:bg-slate-900"><td className="whitespace-nowrap p-4">{day.date}</td><td className="p-4 text-right tabular-nums">{format.format(day.visitors)}</td><td className="p-4 text-right tabular-nums">{format.format(day.pageViews)}</td></tr>)}</tbody>
                </table></div>
            </details>
            <p className={`text-xs leading-5 ${muted}`}>순 방문자는 선택 기간 전체의 중복을 제거한 수로, 일별·분류별 합계와 다를 수 있습니다. 평균 체류시간은 세션 기준이며, 이탈은 30분 이상 활동이 없는 단일 페이지·10초 미만 체류 세션을 기준으로 합니다.</p>
        </>}
    </div>;
};

const VisitTrend = ({rows}: {rows: AnalyticsOverview['trend']}) => {
    const [metric, setMetric] = useState<'visitors' | 'pageViews'>('pageViews');
    const [selected, setSelected] = useState(Math.max(0, rows.length - 1));
    const active = rows[Math.min(selected, rows.length - 1)];
    const label = metric === 'visitors' ? '순 방문자' : '페이지뷰';
    const maximum = Math.max(1, ...rows.map(row => row[metric]));
    const ceiling = Math.ceil(maximum / 4) * 4;
    const x = (index: number) => rows.length < 2 ? 470 : 62 + index / (rows.length - 1) * 816;
    const y = (value: number) => 224 - value / ceiling * 192;
    const points = rows.map((row, index) => `${x(index)},${y(row[metric])}`).join(' ');
    const ticks = [...new Set([0, Math.floor((rows.length - 1) / 3), Math.floor((rows.length - 1) * 2 / 3), rows.length - 1])].filter(index => index >= 0);
    return <article className={`min-w-0 ${card}`}>
        <div className={`${heading} flex flex-wrap items-center justify-between gap-3`}>
            <h2>일별 방문 추이</h2>
            <div className="flex gap-1" aria-label="추이 차트 지표">
                {(['pageViews', 'visitors'] as const).map(key => <button key={key} type="button" aria-pressed={key === metric} onClick={() => setMetric(key)} className={`rounded-lg px-3 py-1.5 text-xs font-semibold ${key === metric ? 'bg-blue-50 text-blue-700 dark:bg-blue-950 dark:text-blue-300' : 'text-slate-500 hover:bg-slate-50 dark:text-slate-400 dark:hover:bg-slate-900'}`}>{key === 'pageViews' ? '페이지뷰' : '순 방문자'}</button>)}
            </div>
        </div>
        {rows.length === 0 ? <p className={`p-12 text-center text-xs ${muted}`}>조회 기간의 데이터가 없습니다.</p> : <div className="p-4 md:p-5">
            <div className="flex flex-wrap items-center justify-between gap-2 text-xs"><p className={muted}>일별 {label} · {metric === 'visitors' ? '명' : '회'}</p><p aria-live="polite" className="tabular-nums"><span className={muted}>{active.date} </span><strong className="ml-2 text-blue-700 dark:text-blue-300">{format.format(active[metric])}{metric === 'visitors' ? '명' : '회'}</strong></p></div>
            <div className="overflow-x-auto"><svg viewBox="0 0 910 265" className="mt-2 min-w-[640px] w-full" aria-hidden="true">
                {[0,1,2,3,4].map(step => <g key={step}><line x1="62" x2="878" y1={y(ceiling*step/4)} y2={y(ceiling*step/4)} className="stroke-slate-100 dark:stroke-slate-800" /><text x="50" y={y(ceiling*step/4)+4} textAnchor="end" className="fill-slate-500 text-[11px] dark:fill-slate-400">{format.format(ceiling*step/4)}</text></g>)}
                <polygon points={`${x(0)},224 ${points} ${x(rows.length-1)},224`} className="fill-blue-500/10 dark:fill-blue-400/10" />
                <polyline points={points} fill="none" strokeWidth="2.5" strokeLinejoin="round" className="stroke-blue-600 dark:stroke-blue-400" />
                <line x1={x(selected)} x2={x(selected)} y1="24" y2="224" strokeDasharray="4 4" className="stroke-blue-300 dark:stroke-blue-800" />
                <circle cx={x(selected)} cy={y(active[metric])} r="4" strokeWidth="2" className="fill-blue-600 stroke-white dark:fill-blue-400 dark:stroke-slate-950" />
                {ticks.map(index => <text key={index} x={x(index)} y="250" textAnchor="middle" className="fill-slate-500 text-[11px] dark:fill-slate-400">{rows[index].date.slice(5).replace('-','.')}</text>)}
                {rows.map((row,index) => <rect key={row.date} x={rows.length === 1 ? 62 : Math.max(62,x(index)-408/(rows.length-1))} y="16" width={rows.length === 1 ? 816 : 816/(rows.length-1)} height="212" fill="transparent" onMouseEnter={() => setSelected(index)} onClick={() => setSelected(index)} />)}
            </svg></div>
            <label className={`mt-1 flex items-center gap-3 text-[11px] ${muted}`}><span className="shrink-0">날짜 탐색</span><input type="range" min={0} max={Math.max(0,rows.length-1)} value={selected} disabled={rows.length < 2} onChange={event => setSelected(Number(event.target.value))} aria-label="추이 차트 날짜 탐색" aria-valuetext={`${active.date}, ${label} ${format.format(active[metric])}`} className="h-1.5 min-w-0 flex-1 accent-blue-600 dark:accent-blue-400" /></label>
        </div>}
    </article>;
};

const Distribution = ({title,rows,metric,countries=false}: {title:string;rows:Dimension[];metric:'visitors'|'sessions';countries?:boolean}) => {
    const total=rows.reduce((sum,row)=>sum+row[metric],0);
    return <article className={`min-w-0 ${card}`}>
        <h2 className={heading}>{title}</h2>
        <div className="max-h-72 space-y-4 overflow-auto p-4 md:p-5">
            {rows.length===0 && <p className={`text-xs ${muted}`}>조회 기간의 데이터가 없습니다.</p>}
            {rows.map(row=><div key={row.dimensionKey}>
                <div className="mb-2 flex justify-between gap-3 text-xs"><span className="min-w-0 break-words">{countries ? countryLabel(row.dimensionKey) : dimensionLabels[row.dimensionKey] || row.dimensionKey}</span><span className="shrink-0 tabular-nums"><strong>{format.format(row[metric])}{metric==='visitors' ? '명' : '회'}</strong> <span className={`ml-2 ${muted}`}>{percent(row[metric],total)}%</span></span></div>
                <div className="h-1.5 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800"><div className="h-full rounded-full bg-blue-500 dark:bg-blue-400" style={{width:`${percent(row[metric],total)}%`}} /></div>
            </div>)}
        </div>
        <p className={`px-4 pb-4 text-[11px] md:px-5 ${muted}`}>{metric==='visitors' ? '분류별 순 방문자' : '분류별 세션'} 합계 대비 비중</p>
    </article>;
};
