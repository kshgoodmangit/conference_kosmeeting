import { AdminDashboardHeader } from './AdminDashboardHeader';
import { useEffect, useState } from 'react';
import type { LucideIcon } from 'lucide-react';
import {
    AlertCircle,
    AlertTriangle,
    CalendarDays,
    CheckCircle2,
    ChevronRight,
    CircleDollarSign,
    Clock3,
    FileCheck2,
    FileText,
    HandHeart,
    RefreshCw,
    Sparkles,
    Users,
    WalletCards
} from 'lucide-react';

interface AdminDashboardPageProps {
    onNavigate: (nav: string) => void;
}

interface EventSummary {
    eventName: string;
    eventStartDate?: string | null;
    eventDday?: number | null;
    abstractEndDate?: string | null;
    abstractDday?: number | null;
    earlyBirdEndDate?: string | null;
    earlyBirdDday?: number | null;
    registrationEndDate?: string | null;
    registrationDday?: number | null;
    registrationCurrency: string;
}

interface RegistrationSummary {
    memberCount: number;
    todayMemberCount: number;
    preRegistrationCount: number;
    todayPreRegistrationCount: number;
    paidCount: number;
    todayPaidCount: number;
    unpaidCount: number;
    failedCount: number;
    cancelledCount: number;
    refundedCount: number;
    paidAmount: number;
    paymentRate: number;
}

interface AbstractReviewSummary {
    submittedCount: number;
    todaySubmittedCount: number;
    unassignedCount: number;
    assignedCount: number;
    completedCount: number;
    resultConfirmedCount: number;
    overdueCount: number;
    completionRate: number;
}

interface SponsorshipSummary {
    applicationCount: number;
    depositedCount: number;
    unpaidCount: number;
    taxInvoiceIssuedCount: number;
    overdueDepositCount: number;
    taxInvoicePendingCount: number;
    totalAmount: number;
    depositedAmount: number;
    depositRate: number;
}

interface DailyTrend {
    activityDate: string;
    abstractCount: number;
    registrationCount: number;
}

interface UpcomingSchedule {
    type: 'ABSTRACT' | 'EARLY_BIRD' | 'REGISTRATION' | 'MAIL';
    label: string;
    date?: string | null;
    scheduledAt?: string | null;
    dday?: number | null;
}

interface DashboardData {
    event: EventSummary;
    registration: RegistrationSummary;
    abstractReview: AbstractReviewSummary;
    sponsorship: SponsorshipSummary;
    trends: DailyTrend[];
    upcomingSchedules: UpcomingSchedule[];
}

interface OverviewItem {
    label: string;
    description: string;
    value: string;
    unit?: string;
    icon: LucideIcon;
    nav: string;
    tone: Tone;
}

interface MetricItem {
    label: string;
    value: string;
    badge: string;
    detail: string;
    icon: LucideIcon;
    tone: Tone;
    alert?: boolean;
}

type Tone = 'rose' | 'amber' | 'blue' | 'violet' | 'emerald';

const EMPTY_DASHBOARD: DashboardData = {
    event: {
        eventName: '학회명',
        registrationCurrency: 'USD'
    },
    registration: {
        memberCount: 0,
        todayMemberCount: 0,
        preRegistrationCount: 0,
        todayPreRegistrationCount: 0,
        paidCount: 0,
        todayPaidCount: 0,
        unpaidCount: 0,
        failedCount: 0,
        cancelledCount: 0,
        refundedCount: 0,
        paidAmount: 0,
        paymentRate: 0
    },
    abstractReview: {
        submittedCount: 0,
        todaySubmittedCount: 0,
        unassignedCount: 0,
        assignedCount: 0,
        completedCount: 0,
        resultConfirmedCount: 0,
        overdueCount: 0,
        completionRate: 0
    },
    sponsorship: {
        applicationCount: 0,
        depositedCount: 0,
        unpaidCount: 0,
        taxInvoiceIssuedCount: 0,
        overdueDepositCount: 0,
        taxInvoicePendingCount: 0,
        totalAmount: 0,
        depositedAmount: 0,
        depositRate: 0
    },
    trends: [],
    upcomingSchedules: []
};

const TONE_CLASS: Record<Tone, { icon: string }> = {
    rose: { icon: 'bg-rose-50 text-rose-600 dark:bg-rose-950/50 dark:text-rose-400' },
    amber: { icon: 'bg-amber-50 text-amber-600 dark:bg-amber-950/50 dark:text-amber-400' },
    blue: { icon: 'bg-blue-50 text-blue-600 dark:bg-blue-950/50 dark:text-blue-400' },
    violet: { icon: 'bg-violet-50 text-violet-600 dark:bg-violet-950/50 dark:text-violet-400' },
    emerald: { icon: 'bg-emerald-50 text-emerald-600 dark:bg-emerald-950/50 dark:text-emerald-400' }
};

const numberFormatter = new Intl.NumberFormat('ko-KR');
const percentFormatter = new Intl.NumberFormat('ko-KR', {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1
});

export const AdminDashboardPage = ({ onNavigate }: AdminDashboardPageProps) => {
    const [dashboard, setDashboard] = useState<DashboardData>(EMPTY_DASHBOARD);
    const [isLoading, setIsLoading] = useState(true);
    const [errorMessage, setErrorMessage] = useState('');
    const [reloadKey, setReloadKey] = useState(0);

    useEffect(() => {
        const controller = new AbortController();

        const loadDashboard = async () => {
            setIsLoading(true);
            setErrorMessage('');
            try {
                const response = await fetch('/api/admin/dashboard', { signal: controller.signal });
                if (!response.ok) {
                    throw new Error(await response.text() || '대시보드 정보를 불러오지 못했습니다.');
                }
                setDashboard(await response.json() as DashboardData);
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') return;
                setErrorMessage(error instanceof Error ? error.message : '대시보드 정보를 불러오지 못했습니다.');
            } finally {
                if (!controller.signal.aborted) setIsLoading(false);
            }
        };

        void loadDashboard();
        return () => controller.abort();
    }, [reloadKey]);

    const registration = dashboard.registration;
    const abstractReview = dashboard.abstractReview;
    const sponsorship = dashboard.sponsorship;
    const metricValue = (value: string) => isLoading ? '—' : value;
    const paidAmount = formatCurrency(registration.paidAmount, dashboard.event.registrationCurrency);
    const maxTrendValue = Math.max(
        1,
        ...dashboard.trends.flatMap((item) => [item.abstractCount, item.registrationCount])
    );

    const overviewItems: OverviewItem[] = [
        {
            label: '회원 수',
            description: `오늘 신규 가입 ${numberFormatter.format(registration.todayMemberCount)}명`,
            value: metricValue(numberFormatter.format(registration.memberCount)),
            unit: isLoading ? undefined : '명',
            icon: Users,
            nav: 'members',
            tone: 'violet'
        },
        {
            label: '사전등록 회원 수',
            description: `오늘 신규 등록 ${numberFormatter.format(registration.todayPreRegistrationCount)}명`,
            value: metricValue(numberFormatter.format(registration.preRegistrationCount)),
            unit: isLoading ? undefined : '명',
            icon: WalletCards,
            nav: 'pre-registrations',
            tone: 'blue'
        },
        {
            label: '사전등록 미입금',
            description: '결제 확인이 필요한 등록자',
            value: metricValue(numberFormatter.format(registration.unpaidCount)),
            unit: isLoading ? undefined : '명',
            icon: Clock3,
            nav: 'pre-registrations',
            tone: 'amber'
        },
        {
            label: '사전등록 입금액',
            description: '결제 완료 기준 누적 금액',
            value: metricValue(paidAmount),
            icon: CircleDollarSign,
            nav: 'pre-registrations',
            tone: 'emerald'
        }
    ];

    const metricItems: MetricItem[] = [
        {
            label: '초록 접수 수',
            value: metricValue(`${numberFormatter.format(abstractReview.submittedCount)}건`),
            badge: `오늘 ${numberFormatter.format(abstractReview.todaySubmittedCount)}건`,
            detail: '임시저장 제외',
            icon: FileText,
            tone: 'blue'
        },
        {
            label: '심사위원 미배정',
            value: metricValue(`${numberFormatter.format(abstractReview.unassignedCount)}건`),
            badge: '확인 필요',
            detail: '유효한 심사 배정이 없는 초록',
            icon: FileCheck2,
            tone: 'rose',
            alert: true
        },
        {
            label: '심사 완료율',
            value: metricValue(`${percentFormatter.format(abstractReview.completionRate)}%`),
            badge: '초록 기준',
            detail: `${numberFormatter.format(abstractReview.completedCount)} / ${numberFormatter.format(abstractReview.assignedCount)}건 완료`,
            icon: CheckCircle2,
            tone: 'emerald'
        },
        {
            label: '심사 기한 초과',
            value: metricValue(`${numberFormatter.format(abstractReview.overdueCount)}건`),
            badge: '기한 초과',
            detail: '미완료 심사 배정 기준',
            icon: Clock3,
            tone: 'amber',
            alert: true
        }
    ];

    return (
        <div className="w-full space-y-5 pb-6">
            {errorMessage && (
                <div className="flex flex-col gap-3 rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700 dark:border-rose-900/60 dark:bg-rose-950/30 dark:text-rose-300 sm:flex-row sm:items-center sm:justify-between">
                    <div className="flex items-center gap-2"><AlertCircle size={17} /><span>{errorMessage}</span></div>
                    <button type="button" onClick={() => setReloadKey((value) => value + 1)} className="inline-flex items-center justify-center gap-1.5 rounded-lg border border-rose-300 px-3 py-1.5 text-xs font-semibold hover:bg-rose-100 dark:border-rose-800 dark:hover:bg-rose-950/60">
                        <RefreshCw size={14} /> 다시 시도
                    </button>
                </div>
            )}

            <AdminDashboardHeader
                badge="운영 대시보드"
                icon={Sparkles}
                title={(dashboard.event.eventName || '학회명') + ' 행사 운영 현황'}
                description="주요 접수 및 심사 현황을 한눈에 확인하세요."
                deadlines={[
                    { label: '행사까지', value: formatDday(dashboard.event.eventDday) },
                    { label: '초록 접수 마감', value: formatDday(dashboard.event.abstractDday), accent: 'text-amber-200 dark:text-amber-200' },
                    { label: '사전접수 마감', value: formatDday(dashboard.event.registrationDday), accent: 'text-cyan-200 dark:text-cyan-200' }
                ]}
            />

            <section className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
                {overviewItems.map((item) => {
                    const Icon = item.icon;
                    const tone = TONE_CLASS[item.tone];
                    return (
                        <article key={item.label} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-slate-950 md:p-5">
                            <div className="flex items-start justify-between">
                                <span className={`flex h-9 w-9 items-center justify-center rounded-lg ${tone.icon}`}><Icon size={18} /></span>
                                <button
                                    type="button"
                                    onClick={() => onNavigate(item.nav)}
                                    aria-label={`${item.label} 관리 화면으로 이동`}
                                    className="rounded-lg p-1.5 text-slate-400 transition-colors hover:bg-slate-100 hover:text-blue-600 dark:hover:bg-slate-800 dark:hover:text-blue-400"
                                >
                                    <ChevronRight size={16} />
                                </button>
                            </div>
                            <p className="mt-4 text-xs font-medium text-slate-500 dark:text-slate-400">{item.label}</p>
                            <div className="mt-1 flex items-end justify-between gap-2">
                                <strong className="text-2xl font-bold tracking-tight text-slate-900 dark:text-white">
                                    {item.value}{item.unit && <span className="ml-0.5">{item.unit}</span>}
                                </strong>
                                <span className="pb-0.5 text-right text-[11px] text-slate-400">{item.description}</span>
                            </div>
                        </article>
                    );
                })}
            </section>

            <section className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
                {metricItems.map((item) => {
                    const Icon = item.icon;
                    const tone = TONE_CLASS[item.tone];
                    return (
                        <article key={item.label} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-slate-950 md:p-5">
                            <div className="flex items-start justify-between">
                                <span className={`flex h-9 w-9 items-center justify-center rounded-lg ${tone.icon}`}><Icon size={18} /></span>
                                <span className={`inline-flex items-center gap-1 rounded-full px-2 py-1 text-[11px] font-semibold ${item.alert ? 'bg-rose-50 text-rose-600 dark:bg-rose-950/50 dark:text-rose-400' : 'bg-blue-50 text-blue-600 dark:bg-blue-950/50 dark:text-blue-400'}`}>
                                    {item.alert && <AlertTriangle size={11} />} {item.badge}
                                </span>
                            </div>
                            <p className="mt-4 text-xs font-medium text-slate-500 dark:text-slate-400">{item.label}</p>
                            <div className="mt-1 flex items-end justify-between gap-2">
                                <strong className="text-2xl font-bold tracking-tight text-slate-900 dark:text-white">{item.value}</strong>
                                <span className="pb-0.5 text-right text-[11px] text-slate-400">{item.detail}</span>
                            </div>
                        </article>
                    );
                })}
            </section>

            <section className="grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,1.65fr)_minmax(320px,0.75fr)]">
                <article className="rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-950">
                    <div className="flex flex-col gap-3 border-b border-slate-100 p-4 dark:border-slate-800 sm:flex-row sm:items-center sm:justify-between md:px-5">
                        <div>
                            <h2 className="text-sm font-bold text-slate-900 dark:text-white">접수 및 등록 추이</h2>
                            <p className="mt-1 text-xs text-slate-400">최근 7일 기준 일별 신규 건수</p>
                        </div>
                        <div className="flex items-center gap-4 text-xs font-medium text-slate-500">
                            <span className="flex items-center gap-1.5"><span className="h-2 w-2 rounded-full bg-blue-500" />초록 접수</span>
                            <span className="flex items-center gap-1.5"><span className="h-2 w-2 rounded-full bg-violet-500" />사전등록</span>
                            <span className="rounded-lg border border-slate-200 px-2.5 py-1.5 text-[11px] font-semibold dark:border-slate-700">최근 7일</span>
                        </div>
                    </div>
                    <div className="p-4 md:p-5">
                        <div className="flex h-56 items-end gap-2 sm:gap-4">
                            {dashboard.trends.map((item, index) => (
                                <div key={item.activityDate} className="group flex h-full min-w-0 flex-1 flex-col justify-end">
                                    <div className="mb-2 hidden justify-center gap-1 text-[10px] font-semibold text-slate-500 group-hover:flex dark:text-slate-300 sm:flex">
                                        <span>{item.abstractCount}</span><span className="text-slate-300">/</span><span>{item.registrationCount}</span>
                                    </div>
                                    <div className="flex h-[172px] items-end justify-center gap-1 sm:gap-2">
                                        <div className="w-2.5 rounded-t bg-gradient-to-t from-blue-600 to-blue-400 transition-opacity group-hover:opacity-80 sm:w-4" style={{ height: `${barHeight(item.abstractCount, maxTrendValue)}%` }} />
                                        <div className="w-2.5 rounded-t bg-gradient-to-t from-violet-600 to-violet-400 transition-opacity group-hover:opacity-80 sm:w-4" style={{ height: `${barHeight(item.registrationCount, maxTrendValue)}%` }} />
                                    </div>
                                    <span className="mt-2 text-center text-[10px] font-medium text-slate-400 sm:text-xs">{formatTrendDate(item.activityDate, index === dashboard.trends.length - 1)}</span>
                                </div>
                            ))}
                            {!isLoading && dashboard.trends.length === 0 && (
                                <div className="flex h-full w-full items-center justify-center text-sm text-slate-400">표시할 추이 데이터가 없습니다.</div>
                            )}
                        </div>
                    </div>
                </article>

                <article className="rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-950">
                    <div className="flex items-center justify-between border-b border-slate-100 p-4 dark:border-slate-800 md:px-5">
                        <div>
                            <h2 className="text-sm font-bold text-slate-900 dark:text-white">다가오는 일정</h2>
                            <p className="mt-1 text-xs text-slate-400">주요 마감 및 예약 일정</p>
                        </div>
                        <CalendarDays size={19} className="text-slate-400" />
                    </div>
                    <div className="divide-y divide-slate-100 px-4 dark:divide-slate-800 md:px-5">
                        {dashboard.upcomingSchedules.map((item) => {
                            const scheduleDate = item.date ?? item.scheduledAt;
                            const parts = formatScheduleDate(scheduleDate);
                            return (
                                <div key={`${item.type}-${scheduleDate ?? item.label}`} className="flex items-center gap-3 py-3.5">
                                    <div className="w-11 shrink-0 text-center">
                                        <span className="block text-[10px] font-medium text-slate-400">{parts.year}</span>
                                        <span className="block text-sm font-bold text-slate-700 dark:text-slate-200">{parts.date}</span>
                                    </div>
                                    <span className={`h-8 w-0.5 rounded-full ${scheduleTone(item.type)}`} />
                                    <div className="min-w-0 flex-1">
                                        <p className="truncate text-xs font-semibold text-slate-700 dark:text-slate-200">{item.type === 'MAIL' ? `${item.label} 예약 발송` : item.label}</p>
                                        <p className="mt-0.5 text-[11px] text-slate-400">{item.type === 'MAIL' ? formatScheduleTime(item.scheduledAt) : formatDday(item.dday)}</p>
                                    </div>
                                </div>
                            );
                        })}
                        {!isLoading && dashboard.upcomingSchedules.length === 0 && (
                            <div className="py-12 text-center text-sm text-slate-400">등록된 예정 일정이 없습니다.</div>
                        )}
                    </div>
                </article>
            </section>

            <section className="grid grid-cols-1 gap-4 xl:grid-cols-2 2xl:grid-cols-3">
                <WorkflowCard
                    title="초록 심사 진행"
                    description="접수부터 결과 확정까지"
                    icon={FileCheck2}
                    stages={[
                        { label: '제출완료', value: abstractReview.submittedCount, color: 'bg-blue-500' },
                        { label: '심사배정', value: abstractReview.assignedCount, color: 'bg-cyan-500' },
                        { label: '심사완료', value: abstractReview.completedCount, color: 'bg-violet-500' },
                        { label: '결과확정', value: abstractReview.resultConfirmedCount, color: 'bg-emerald-500' }
                    ]}
                    progress={{
                        label: '심사 완료율',
                        value: abstractReview.completedCount,
                        total: abstractReview.assignedCount,
                        color: 'bg-violet-500',
                        completeLabel: '심사 완료',
                        remainingLabel: '미완료'
                    }}
                    footer={`미배정 ${numberFormatter.format(abstractReview.unassignedCount)}건 · 결과 확정 대기 ${numberFormatter.format(Math.max(0, abstractReview.completedCount - abstractReview.resultConfirmedCount))}건`}
                    onClick={() => onNavigate('abstracts')}
                />
                <WorkflowCard
                    title="사전등록 및 결제"
                    description="신청 이후 결제 처리 현황"
                    icon={WalletCards}
                    stages={[
                        { label: '전체신청', value: registration.preRegistrationCount, color: 'bg-blue-500' },
                        { label: '결제완료', value: registration.paidCount, color: 'bg-emerald-500' },
                        { label: '미입금', value: registration.unpaidCount, color: 'bg-amber-500' },
                        { label: '취소', value: registration.cancelledCount, color: 'bg-slate-400' }
                    ]}
                    progress={{
                        label: '결제 상태 구성',
                        total: registration.preRegistrationCount,
                        segments: [
                            { label: '결제완료', value: registration.paidCount, color: 'bg-emerald-500', dot: 'bg-emerald-500' },
                            { label: '미입금', value: registration.unpaidCount, color: 'bg-amber-500', dot: 'bg-amber-500' },
                            { label: '결제실패', value: registration.failedCount, color: 'bg-rose-500', dot: 'bg-rose-500' },
                            { label: '환불', value: registration.refundedCount, color: 'bg-violet-500', dot: 'bg-violet-500' },
                            { label: '취소', value: registration.cancelledCount, color: 'bg-slate-400', dot: 'bg-slate-400' }
                        ]
                    }}
                    footer={`결제율 ${percentFormatter.format(registration.paymentRate)}% · 오늘 결제 ${numberFormatter.format(registration.todayPaidCount)}명 · 실패 ${numberFormatter.format(registration.failedCount)}건`}
                    onClick={() => onNavigate('pre-registrations')}
                />
                <WorkflowCard
                    title="후원 진행 현황"
                    description="후원 신청부터 입금·증빙 처리까지"
                    icon={HandHeart}
                    stages={[
                        { label: '후원신청', value: sponsorship.applicationCount, color: 'bg-blue-500' },
                        { label: '입금완료', value: sponsorship.depositedCount, color: 'bg-emerald-500' },
                        { label: '미입금', value: sponsorship.unpaidCount, color: 'bg-amber-500' },
                        { label: '계산서발행', value: sponsorship.taxInvoiceIssuedCount, color: 'bg-violet-500' }
                    ]}
                    progress={{
                        label: '후원금 입금률',
                        value: sponsorship.depositedAmount,
                        total: sponsorship.totalAmount,
                        color: 'bg-emerald-500',
                        completeLabel: '입금 완료',
                        remainingLabel: '미입금 예정',
                        valueFormatter: formatWon
                    }}
                    footer={`입금 예정일 경과 ${numberFormatter.format(sponsorship.overdueDepositCount)}개사 · 세금계산서 미발행 ${numberFormatter.format(sponsorship.taxInvoicePendingCount)}개사`}
                    onClick={() => onNavigate('sponsorship')}
                />
            </section>
        </div>
    );
};

interface WorkflowStage {
    label: string;
    value: number;
    color: string;
}

interface WorkflowCardProps {
    title: string;
    description: string;
    icon: LucideIcon;
    stages: WorkflowStage[];
    progress: WorkflowProgress;
    footer: string;
    onClick: () => void;
}

interface WorkflowProgressSegment {
    label: string;
    value: number;
    color: string;
    dot: string;
}

interface WorkflowProgress {
    label: string;
    total: number;
    value?: number;
    color?: string;
    completeLabel?: string;
    remainingLabel?: string;
    valueFormatter?: (value: number) => string;
    segments?: WorkflowProgressSegment[];
}


const WorkflowCard = ({ title, description, icon: Icon, stages, progress, footer, onClick }: WorkflowCardProps) => {
    const progressRate = toPercent(progress.value ?? 0, progress.total);
    const remainingValue = Math.max(0, progress.total - (progress.value ?? 0));
    const formatProgressValue = progress.valueFormatter ?? numberFormatter.format;

    return (
        <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-slate-950 md:p-5">
            <div className="flex items-start justify-between">
                <div className="flex items-center gap-3">
                    <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300"><Icon size={18} /></span>
                    <div>
                        <h2 className="text-sm font-bold text-slate-900 dark:text-white">{title}</h2>
                        <p className="mt-0.5 text-[11px] text-slate-400">{description}</p>
                    </div>
                </div>
                <button type="button" onClick={onClick} className="inline-flex items-center gap-1 text-[11px] font-semibold text-blue-600 hover:text-blue-700 dark:text-blue-400">관리 <ChevronRight size={13} /></button>
            </div>
            <div className="mt-5 grid grid-cols-4 gap-2">
                {stages.map((stage, index) => (
                    <div key={stage.label} className="relative min-w-0">
                        <div className="mb-2 flex items-center">
                            <span className={`h-2.5 w-2.5 shrink-0 rounded-full ${stage.color}`} />
                            {index < stages.length - 1 && <span className="h-px flex-1 bg-slate-200 dark:bg-slate-700" />}
                        </div>
                        <p className="truncate text-[10px] font-medium text-slate-400 sm:text-[11px]">{stage.label}</p>
                        <p className="mt-0.5 text-base font-bold text-slate-800 dark:text-slate-100 sm:text-lg">{stage.value.toLocaleString()}</p>
                    </div>
                ))}
            </div>
            <div className="mt-4">
                <div className="mb-2 flex items-center justify-between text-[11px] font-medium text-slate-500 dark:text-slate-400">
                    <span>{progress.label}</span>
                    {!progress.segments && <span>{percentFormatter.format(progressRate)}%</span>}
                </div>
                <div className="flex h-2.5 overflow-hidden rounded-full bg-slate-200 dark:bg-slate-800">
                    {progress.segments ? progress.segments.map((segment) => (
                        <span
                            key={segment.label}
                            className={segment.color}
                            style={{ width: `${toPercent(segment.value, progress.total)}%` }}
                        />
                    )) : (
                        <span className={progress.color ?? 'bg-blue-500'} style={{ width: `${progressRate}%` }} />
                    )}
                </div>
                {progress.segments ? (
                    <div className="mt-2 flex flex-wrap gap-x-3 gap-y-1.5 text-[10px] text-slate-500 dark:text-slate-400">
                        {progress.segments.map((segment) => (
                            <span key={segment.label} className="inline-flex items-center gap-1">
                                <span className={`h-1.5 w-1.5 rounded-full ${segment.dot}`} />
                                {segment.label} {numberFormatter.format(segment.value)}
                            </span>
                        ))}
                    </div>
                ) : (
                    <div className="mt-2 flex items-center gap-3 text-[10px] text-slate-500 dark:text-slate-400">
                        <span className="inline-flex items-center gap-1">
                            <span className={`h-1.5 w-1.5 rounded-full ${progress.color ?? 'bg-blue-500'}`} />
                            {progress.completeLabel} {formatProgressValue(progress.value ?? 0)}
                        </span>
                        <span className="inline-flex items-center gap-1">
                            <span className="h-1.5 w-1.5 rounded-full bg-slate-300 dark:bg-slate-700" />
                            {progress.remainingLabel} {formatProgressValue(remainingValue)}
                        </span>
                    </div>
                )}
            </div>
            <p className="mt-3 text-[11px] font-medium text-slate-500 dark:text-slate-400">{footer}</p>
        </article>
    );
};

const formatCurrency = (value: number, currency: string) => {
    try {
        return new Intl.NumberFormat('ko-KR', {
            style: 'currency',
            currency: currency || 'USD',
            currencyDisplay: 'narrowSymbol',
            maximumFractionDigits: 0
        }).format(value);
    } catch {
        return `${numberFormatter.format(value)} ${currency || ''}`.trim();
    }
};

const formatWon = (value: number) => `${numberFormatter.format(value)}원`;

const formatDday = (value?: number | null) => {
    if (value === null || value === undefined) return '일정 미설정';
    if (value === 0) return 'D-Day';
    if (value < 0) return '마감';
    return `D-${value}`;
};

const barHeight = (value: number, maxValue: number) => value === 0 ? 0 : Math.max(10, (value / maxValue) * 100);

const toPercent = (value: number, total: number) => total > 0 ? Math.min(100, Math.max(0, (value / total) * 100)) : 0;

const formatTrendDate = (value: string, isLast: boolean) => {
    if (isLast) return '오늘';
    const [, month, day] = value.slice(0, 10).split('-');
    return `${Number(month)}/${Number(day)}`;
};

const formatScheduleDate = (value?: string | null) => {
    if (!value) return { year: '-', date: '-' };
    const [year, month, day] = value.slice(0, 10).split('-');
    return { year, date: `${month}.${day}` };
};

const formatScheduleTime = (value?: string | null) => value?.slice(11, 16) || '-';

const scheduleTone = (type: UpcomingSchedule['type']) => {
    if (type === 'ABSTRACT') return 'bg-rose-400';
    if (type === 'EARLY_BIRD') return 'bg-blue-400';
    if (type === 'REGISTRATION') return 'bg-emerald-400';
    return 'bg-violet-400';
};
