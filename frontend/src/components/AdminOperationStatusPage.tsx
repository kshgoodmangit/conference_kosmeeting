import {
    Activity,
    Clock3,
    CreditCard,
    ExternalLink,
    FileText,
    Globe2,
    Presentation,
    UserRoundCheck,
} from 'lucide-react';

const card = 'min-w-0 rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-950';
const muted = 'text-slate-500 dark:text-slate-400';
const tones = {
    normal: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300',
    warning: 'bg-amber-50 text-amber-800 dark:bg-amber-950/40 dark:text-amber-300',
    urgent: 'bg-rose-50 text-rose-700 dark:bg-rose-950/40 dark:text-rose-300',
    unknown: 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300',
};
type Tone = keyof typeof tones;
const attentionPulse = (tone: Tone) => tone === 'warning' || tone === 'urgent'
    ? 'motion-safe:animate-pulse motion-reduce:animate-none'
    : '';

const Badge = ({ tone, children }: { tone: Tone; children: React.ReactNode }) => (
    <span className={`inline-flex shrink-0 rounded-md px-2 py-1 text-[12px] font-semibold ${tones[tone]} ${attentionPulse(tone)}`}>{children}</span>
);

import { pageGroups, type PageStatus, type MonitoredPage, type PageGroup } from './operationStatusPreview';

const allPages = pageGroups.flatMap(group => group.pages.map(page => ({ ...page, groupName: group.name })));
const pageCounts = {
    total: allPages.length,
    normal: allPages.filter(page => page.status === 'normal').length,
    warning: allPages.filter(page => page.status === 'warning').length,
    urgent: allPages.filter(page => page.status === 'urgent').length,
};

const statusInfo: Record<PageStatus, { label: string; tone: Tone }> = {
    normal: { label: '정상', tone: 'normal' },
    warning: { label: '느림', tone: 'warning' },
    urgent: { label: '오류', tone: 'urgent' },
};

const featureHealthCards: {
    title: string;
    scope: string;
    status: PageStatus;
    statusLabel: string;
    errorCount: number;
    lastSuccessAt: string;
    message: string;
    icon: typeof Activity;
}[] = [
    { title: '회원·인증', scope: '로그인 · 회원가입 · 개인정보', status: 'normal', statusLabel: '정상', errorCount: 0, lastSuccessAt: '14:29', message: '모든 주요 기능이 정상 처리되고 있습니다.', icon: UserRoundCheck },
    { title: '사전등록·결제', scope: '등록 · 결제 승인 · 결과 반영', status: 'warning', statusLabel: '주의', errorCount: 8, lastSuccessAt: '14:02', message: '결제 승인 결과 반영이 지연되고 있습니다.', icon: CreditCard },
    { title: '초록 제출', scope: '임시저장 · 최종 제출 · 수정', status: 'urgent', statusLabel: '오류', errorCount: 3, lastSuccessAt: '13:42', message: '첨부파일 저장 실패가 발생했습니다.', icon: FileText },
    { title: '발표자료', scope: '자료 등록 · 수정 · 다운로드', status: 'normal', statusLabel: '정상', errorCount: 0, lastSuccessAt: '14:27', message: '모든 주요 기능이 정상 처리되고 있습니다.', icon: Presentation },
];

const slowestPage = [...allPages].sort((a, b) => b.responseMs - a.responseMs)[0];

const statusVisuals: Record<PageStatus, {
    dot: string;
    border: string;
}> = {
    urgent: {
        dot: 'bg-rose-500',
        border: 'border-l-rose-500',
    },
    warning: {
        dot: 'bg-amber-500',
        border: 'border-l-amber-500',
    },
    normal: {
        dot: 'bg-emerald-500',
        border: 'border-l-emerald-500',
    },
};

const memberPages = pageGroups.find(group => group.name === '회원')?.pages ?? [];
const rootPage = pageGroups.find(group => group.name === '홈')?.pages[0] ?? allPages[0];
const topMenuGroups: PageGroup[] = [
    ...pageGroups.filter(group => !['홈', '회원', '정책'].includes(group.name)),
    { name: '마이페이지', pages: memberPages.filter(page => page.path.startsWith('/mypage')) },
];
const standalonePages = [
    ...memberPages.filter(page => !page.path.startsWith('/mypage')),
    ...(pageGroups.find(group => group.name === '정책')?.pages ?? []),
];

const getGroupStatus = (pages: MonitoredPage[]): PageStatus => {
    if (pages.some(page => page.status === 'urgent')) return 'urgent';
    if (pages.some(page => page.status === 'warning')) return 'warning';
    return 'normal';
};

const getAverageResponseMs = (pages: MonitoredPage[]) => (
    Math.round(pages.reduce((sum, page) => sum + page.responseMs, 0) / pages.length)
);

const PageHealthNode = ({ page }: { page: MonitoredPage }) => {
    const visual = statusVisuals[page.status];
    return (
        <div className={`group min-w-0 rounded-md border border-slate-200 border-l-[3px] ${visual.border} bg-white px-2 py-1.5 dark:border-y-slate-800 dark:border-r-slate-800 dark:bg-slate-950 ${attentionPulse(statusInfo[page.status].tone)}`}>
            <div className="flex items-center gap-1.5">
                <span className={`h-1.5 w-1.5 shrink-0 rounded-full ${visual.dot} ${attentionPulse(statusInfo[page.status].tone)}`} />
                <a href={page.path} target="_blank" rel="noopener noreferrer" className="flex min-w-0 flex-1 items-center gap-1 truncate text-[11px] font-semibold text-slate-800 underline-offset-2 hover:text-blue-700 hover:underline dark:text-slate-200 dark:hover:text-blue-300" title={`${page.name} 새 창으로 열기`}><span className="truncate">{page.name}</span><ExternalLink className="h-2.5 w-2.5 shrink-0 text-slate-400 opacity-0 transition-opacity group-hover:opacity-100" /></a>
                <strong className={`ml-auto shrink-0 text-right text-[11px] tabular-nums ${page.status === 'urgent' ? 'text-rose-600 dark:text-rose-400' : page.status === 'warning' ? 'text-amber-700 dark:text-amber-300' : 'text-slate-600 dark:text-slate-300'}`}>{page.responseMs.toLocaleString('ko-KR')}ms</strong>
            </div>
        </div>
    );
};

export const AdminOperationStatusPage = () => (
    <div className="w-full space-y-5 pb-6 text-slate-900 dark:text-slate-50">
        <section className={`${card} overflow-hidden`} aria-label="사용자 화면 접속 상태">
            <div className="border-b border-slate-200 p-4 dark:border-slate-800 md:p-5">
                <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-end">
                    <div><h2 className="flex items-center gap-2 text-sm font-semibold"><Globe2 className="h-4 w-4 text-blue-600 dark:text-blue-400" />홈페이지 상태모니터링</h2><p className={`mt-1 text-xs leading-5 ${muted}`}>홈페이지 메뉴 구조를 따라 상위 메뉴와 연결된 하위 화면 상태를 표시합니다.</p></div>
                    <div className="flex flex-wrap items-center gap-2 text-[12px]"><Badge tone="urgent">오류 {pageCounts.urgent}</Badge><Badge tone="warning">느림 {pageCounts.warning}</Badge><Badge tone="normal">정상 {pageCounts.normal}</Badge><span className={`ml-1 hidden sm:inline ${muted}`}>가장 느림: <strong className="text-rose-600 dark:text-rose-400">{slowestPage.responseMs.toLocaleString('ko-KR')}ms</strong></span></div>
                </div>
            </div>

            <div className="bg-slate-50/60 p-3 dark:bg-slate-900/20 md:p-4">
                <div className="mx-auto flex w-full flex-col items-center">
                    <a
                        href={rootPage.path}
                        target="_blank"
                        rel="noopener noreferrer"
                        aria-label={`메인 화면, ${statusInfo[rootPage.status].label}, ${rootPage.responseMs}밀리초, 새 창으로 열기`}
                        className={`relative z-10 flex w-full max-w-xs items-center justify-center gap-2 rounded-xl border border-blue-200 bg-blue-50 px-4 py-2.5 text-center shadow-sm hover:border-blue-300 hover:bg-blue-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-500 dark:border-blue-900/60 dark:bg-blue-950/30 dark:hover:border-blue-800 dark:hover:bg-blue-950/50 ${attentionPulse(statusInfo[rootPage.status].tone)}`}
                    >
                        <Globe2 className="h-4 w-4 shrink-0 text-blue-600 dark:text-blue-400" />
                        <strong className="text-xs">메인</strong>
                        <Badge tone={statusInfo[rootPage.status].tone}>{statusInfo[rootPage.status].label}</Badge>
                        <span className="h-3 w-px bg-blue-200 dark:bg-blue-800" aria-hidden="true" />
                        <span className="font-mono text-[11px] text-blue-700 dark:text-blue-300">{rootPage.path}</span>
                        <span className="text-[11px] font-bold tabular-nums">{rootPage.responseMs}ms</span>
                    </a>
                    <div className="h-5 w-px bg-slate-300 dark:bg-slate-700" />
                    <div className="relative w-full pt-3 before:absolute before:left-[7.14%] before:right-[7.14%] before:top-0 before:hidden before:h-px before:bg-slate-300 dark:before:bg-slate-700 xl:before:block">
                        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4 xl:grid-cols-7">
                            {topMenuGroups.map(group => {
                                const groupStatus = getGroupStatus(group.pages);
                                const groupVisual = statusVisuals[groupStatus];
                                return <article key={group.name} className={`relative rounded-xl border border-slate-200 border-t-4 bg-white p-2.5 shadow-sm before:absolute before:-top-4 before:left-1/2 before:hidden before:h-3 before:w-px before:-translate-x-1/2 before:bg-slate-300 dark:border-slate-800 dark:bg-slate-950 dark:before:bg-slate-700 xl:before:block ${groupStatus === 'urgent' ? 'border-t-rose-500' : groupStatus === 'warning' ? 'border-t-amber-500' : 'border-t-emerald-500'}`}>
                                    <header className="mb-2 flex items-center gap-2 border-b border-slate-100 pb-2 dark:border-slate-800">
                                        <span className={`grid h-6 w-6 place-items-center rounded-md ${tones[statusInfo[groupStatus].tone]}`}><span className={`h-2 w-2 rounded-full ${groupVisual.dot} ${attentionPulse(statusInfo[groupStatus].tone)}`} /></span>
                                        <div className="min-w-0 flex-1"><h3 className="truncate text-[12px] font-bold">{group.name}</h3></div>
                                        <div className="text-right"><Badge tone={statusInfo[groupStatus].tone}>{statusInfo[groupStatus].label}</Badge><p className={`mt-0.5 text-[9px] tabular-nums ${muted}`}>평균 {getAverageResponseMs(group.pages)}ms</p></div>
                                    </header>
                                    <div className="space-y-1.5">{group.pages.map(page => <PageHealthNode key={page.path} page={page} />)}</div>
                                </article>;
                            })}
                        </div>
                    </div>
                </div>
            </div>

            <div className="border-t border-slate-200 px-3 py-3 dark:border-slate-800 md:px-4">
                <div className="mb-2 flex items-center justify-between gap-3"><div><h3 className="text-[12px] font-bold">독립 화면</h3><p className={`mt-0.5 text-[10px] ${muted}`}>상위 사용자 메뉴에 연결되지 않는 로그인·회원가입·정책 화면</p></div><span className={`text-[10px] ${muted}`}>{standalonePages.length}개</span></div>
                <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-5">
                    {standalonePages.map(page => {
                        const visual = statusVisuals[page.status];
                        return <a key={page.path} href={page.path} target="_blank" rel="noopener noreferrer" className={`group flex min-w-0 items-center gap-2 rounded-lg border border-slate-200 border-l-[3px] ${visual.border} bg-slate-50 px-2.5 py-2 hover:bg-white hover:shadow-sm dark:border-y-slate-800 dark:border-r-slate-800 dark:bg-slate-900/50 dark:hover:bg-slate-900 ${attentionPulse(statusInfo[page.status].tone)}`}>
                            <span className={`h-2 w-2 shrink-0 rounded-full ${visual.dot} ${attentionPulse(statusInfo[page.status].tone)}`} />
                            <span className="min-w-0 flex-1 truncate text-[11px] font-semibold">{page.name}</span>
                            <ExternalLink className="h-2.5 w-2.5 shrink-0 text-slate-400" />
                            <strong className="ml-auto shrink-0 text-right text-[11px] tabular-nums">{page.responseMs}ms</strong>
                        </a>;
                    })}
                </div>
            </div>
            <p className={`border-t border-slate-200 px-4 py-2 text-[11px] leading-4 dark:border-slate-800 md:px-5 ${muted}`}>상위 메뉴 상태는 하위 화면 중 가장 심각한 상태를 따릅니다. 500ms 미만 정상 · 500–1,499ms 느림 · 1,500ms 이상 또는 화면 확인 실패 시 오류</p>
        </section>

        <section className={`${card} overflow-hidden`} aria-label="주요 기능 오류 발생 정보">
            <div className="flex flex-col justify-between gap-3 border-b border-slate-200 p-4 dark:border-slate-800 sm:flex-row sm:items-end md:p-5">
                <div><h2 className="flex items-center gap-2 text-sm font-semibold"><Activity className="h-4 w-4 text-blue-600 dark:text-blue-400" />주요 기능 오류</h2><p className={`mt-1 text-xs leading-5 ${muted}`}>사용자의 정보 저장과 결제에 직접 영향을 주는 기능을 네 개 업무로 묶어 표시합니다.</p></div>
                <div className="flex items-center gap-2"><Badge tone="urgent">오류 1</Badge><Badge tone="warning">주의 1</Badge><Badge tone="normal">정상 2</Badge></div>
            </div>

            <div className="grid gap-3 p-3 sm:grid-cols-2 xl:grid-cols-4 md:p-4">
                {featureHealthCards.map(feature => {
                    const Icon = feature.icon;
                    const isUrgent = feature.status === 'urgent';
                    const isWarning = feature.status === 'warning';
                    return <article key={feature.title} className={`overflow-hidden rounded-xl border border-t-4 bg-white shadow-sm dark:bg-slate-950 ${isUrgent ? 'border-rose-200 border-t-rose-500 dark:border-rose-900/60 dark:border-t-rose-500' : isWarning ? 'border-amber-200 border-t-amber-500 dark:border-amber-900/60 dark:border-t-amber-500' : 'border-emerald-200 border-t-emerald-500 dark:border-emerald-900/60 dark:border-t-emerald-500'} ${attentionPulse(statusInfo[feature.status].tone)}`}>
                        <div className="p-4">
                            <div className="flex items-start gap-3">
                                <span className={`grid h-10 w-10 shrink-0 place-items-center rounded-xl ${tones[statusInfo[feature.status].tone]}`}><Icon className="h-5 w-5" /></span>
                                <div className="min-w-0 flex-1"><div className="flex items-center justify-between gap-2"><h3 className="text-sm font-bold">{feature.title}</h3><Badge tone={statusInfo[feature.status].tone}>{feature.statusLabel}</Badge></div><p className={`mt-1 truncate text-[10px] ${muted}`} title={feature.scope}>{feature.scope}</p></div>
                            </div>

                            <div className={`mt-4 rounded-lg px-3 py-2.5 text-xs font-medium leading-5 ${tones[statusInfo[feature.status].tone]}`}>{feature.message}</div>

                            <dl className="mt-4 grid grid-cols-2 divide-x divide-slate-200 border-t border-slate-100 pt-3 dark:divide-slate-800 dark:border-slate-800">
                                <div><dt className={`text-[10px] ${muted}`}>최근 24시간 오류</dt><dd className={`mt-1 text-lg font-bold tabular-nums ${feature.errorCount > 0 ? isUrgent ? 'text-rose-600 dark:text-rose-400' : 'text-amber-700 dark:text-amber-300' : 'text-emerald-600 dark:text-emerald-400'}`}>{feature.errorCount}<span className="ml-0.5 text-[10px] font-normal">건</span></dd></div>
                                <div className="pl-4"><dt className={`flex items-center gap-1 text-[10px] ${muted}`}><Clock3 className="h-3 w-3" />마지막 정상 처리</dt><dd className="mt-1 text-lg font-bold tabular-nums">{feature.lastSuccessAt}</dd></div>
                            </dl>
                        </div>
                    </article>;
                })}
            </div>
            <p className={`border-t border-slate-200 px-4 py-2 text-[11px] leading-4 dark:border-slate-800 md:px-5 ${muted}`}>화면 검토용 가상 데이터입니다. 실제 기능 상태와 오류 건수는 백엔드 모니터링 연결 후 표시됩니다.</p>
        </section>
    </div>
);
