import { useState } from 'react';
import {
    CalendarClock,
    CalendarDays,
    ClipboardList,
    Database,
    FileText,
    Image,
    LoaderCircle,
    Mic2,
    ShieldCheck,
    TrendingUp,
    Users
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { useConfirm } from './confirmDialogContext';
import type { NotificationType } from './NotificationToast';
import { AnalyticsTestDataCard } from './AnalyticsTestDataCard';

interface TestDataPageProps {
    dashboardEnabled?: boolean;
    onNotify: (type: NotificationType, message: string) => void;
}

interface TestDataAction {
    key: string;
    title: string;
    description: string;
    buttonLabel: string;
    count: number;
    endpoint: string;
    icon: LucideIcon;
    iconClassName: string;
    buttonClassName: string;
}

interface DailyTestDataAction {
    key: string;
    title: string;
    description: string;
    buttonLabel: string;
    rangeLabel: string;
    endpoint: string;
    icon: LucideIcon;
    iconClassName: string;
    buttonClassName: string;
}

const TEST_DATA_ACTIONS: TestDataAction[] = [
    {
        key: 'reviewers',
        title: '심사자 테스트 계정',
        description: 'reviewer1부터 reviewer10까지 심사자 계정을 생성합니다. 비밀번호는 reviewer12#$로 동일하게 설정됩니다.',
        buttonLabel: '심사자 계정 생성',
        count: 10,
        endpoint: '/api/admin/accounts/test-reviewers',
        icon: ShieldCheck,
        iconClassName: 'bg-amber-50 text-amber-600 dark:bg-amber-950/50 dark:text-amber-400',
        buttonClassName: 'bg-amber-600 hover:bg-amber-700 focus-visible:ring-amber-500 dark:bg-amber-500 dark:hover:bg-amber-600'
    },
    {
        key: 'members',
        title: '회원 데이터',
        description: '국내·국제 회원 유형과 다양한 소속 정보를 포함한 테스트 회원을 생성합니다.',
        buttonLabel: '회원생성',
        count: 316,
        endpoint: '/api/admin/testdata/members',
        icon: Users,
        iconClassName: 'bg-blue-50 text-blue-600 dark:bg-blue-950/50 dark:text-blue-400',
        buttonClassName: 'bg-blue-600 hover:bg-blue-700 focus-visible:ring-blue-500 dark:bg-blue-500 dark:hover:bg-blue-600'
    },
    {
        key: 'pre-registrations',
        title: '사전등록 데이터',
        description: '회원 정보를 기준으로 얼리버드·일반, 결제·취소 상태가 다양한 사전등록을 생성합니다.',
        buttonLabel: '사전등록생성',
        count: 276,
        endpoint: '/api/admin/testdata/pre-registrations',
        icon: ClipboardList,
        iconClassName: 'bg-violet-50 text-violet-600 dark:bg-violet-950/50 dark:text-violet-400',
        buttonClassName: 'bg-violet-600 hover:bg-violet-700 focus-visible:ring-violet-500 dark:bg-violet-500 dark:hover:bg-violet-600'
    },
    {
        key: 'abstracts',
        title: '초록 데이터',
        description: '관리자 접수 초록과 2명 이상 정상 심사, 강제 결정, 최종 발표형식 표본을 워크플로우에 맞게 생성합니다.',
        buttonLabel: '초록생성',
        count: 216,
        endpoint: '/api/admin/testdata/abstracts',
        icon: FileText,
        iconClassName: 'bg-emerald-50 text-emerald-600 dark:bg-emerald-950/50 dark:text-emerald-400',
        buttonClassName: 'bg-emerald-600 hover:bg-emerald-700 focus-visible:ring-emerald-500 dark:bg-emerald-500 dark:hover:bg-emerald-600'
    },
    {
        key: 'popups',
        title: '팝업 데이터',
        description: '공통 blank 이미지를 본문에 사용하고 노출 기간과 사용 상태가 다양한 팝업을 생성합니다.',
        buttonLabel: '팝업생성',
        count: 18,
        endpoint: '/api/admin/testdata/popups',
        icon: Image,
        iconClassName: 'bg-rose-50 text-rose-600 dark:bg-rose-950/50 dark:text-rose-400',
        buttonClassName: 'bg-rose-600 hover:bg-rose-700 focus-visible:ring-rose-500 dark:bg-rose-500 dark:hover:bg-rose-600'
    },
    {
        key: 'speakers',
        title: '초청연자 데이터',
        description: '키노트·초청·특별 연자 프로필과 2MB 이하 인물 사진을 현재 학회에 생성합니다.',
        buttonLabel: '초청연자생성',
        count: 6,
        endpoint: '/api/admin/testdata/speakers',
        icon: Mic2,
        iconClassName: 'bg-cyan-50 text-cyan-600 dark:bg-cyan-950/50 dark:text-cyan-400',
        buttonClassName: 'bg-cyan-600 hover:bg-cyan-700 focus-visible:ring-cyan-500 dark:bg-cyan-500 dark:hover:bg-cyan-600'
    }
];

const DAILY_TEST_DATA_ACTIONS: DailyTestDataAction[] = [
    {
        key: 'daily-members',
        title: '일별 회원 데이터',
        description: '선택한 행사 시작 60일 전부터 전날까지 날짜별 회원 수를 생성합니다. 이미 생성된 데이터는 건너뜁니다.',
        buttonLabel: '회원데이터생성',
        rangeLabel: '매일 10~15개',
        endpoint: '/api/admin/testdata/daily/members',
        icon: Users,
        iconClassName: 'bg-sky-50 text-sky-600 dark:bg-sky-950/50 dark:text-sky-400',
        buttonClassName: 'bg-sky-600 hover:bg-sky-700 focus-visible:ring-sky-500 dark:bg-sky-500 dark:hover:bg-sky-600'
    },
    {
        key: 'daily-pre-registrations',
        title: '일별 사전등록 데이터',
        description: '일별 회원 정보를 바탕으로 얼리버드·일반 및 여러 결제 상태의 사전등록을 날짜별로 생성합니다.',
        buttonLabel: '사전등록데이터 생성',
        rangeLabel: '매일 8~13개',
        endpoint: '/api/admin/testdata/daily/pre-registrations',
        icon: CalendarDays,
        iconClassName: 'bg-purple-50 text-purple-600 dark:bg-purple-950/50 dark:text-purple-400',
        buttonClassName: 'bg-purple-600 hover:bg-purple-700 focus-visible:ring-purple-500 dark:bg-purple-500 dark:hover:bg-purple-600'
    },
    {
        key: 'daily-abstracts',
        title: '일별 초록 데이터',
        description: '일별 회원 정보로 초록을 생성합니다. 활성 심사자가 2명 미만이면 임시저장·접수완료 상태로 생성합니다.',
        buttonLabel: '초록데이터 생성',
        rangeLabel: '매일 6~9개',
        endpoint: '/api/admin/testdata/daily/abstracts',
        icon: TrendingUp,
        iconClassName: 'bg-teal-50 text-teal-600 dark:bg-teal-950/50 dark:text-teal-400',
        buttonClassName: 'bg-teal-600 hover:bg-teal-700 focus-visible:ring-teal-500 dark:bg-teal-500 dark:hover:bg-teal-600'
    }
];

interface TestDataCreationResult {
    createdCount: number;
    skippedCount?: number;
    assignmentCount: number;
    reviewCount: number;
    completedReviewCount: number;
    scoreCount: number;
    draftCount?: number;
    submittedCount?: number;
    underReviewCount?: number;
    approvedCount?: number;
    rejectedCount?: number;
    normalDecisionCount?: number;
    forcedDecisionCount?: number;
    changedPresentationTypeCount?: number;
    optionItemCount?: number;
}

interface DailyTestDataCreationResult {
    startDate: string;
    endDate: string;
    createdCount: number;
    skippedCount: number;
    desiredCount: number;
}

export const TestDataPage = ({ onNotify, dashboardEnabled }: TestDataPageProps) => {
    const confirm = useConfirm();
    const [creatingKey, setCreatingKey] = useState<string | null>(null);

    const handleAction = async (action: TestDataAction) => {
        const confirmed = await confirm({
            title: `${action.title} 생성`,
            message: action.key === 'reviewers'
                ? 'reviewer1부터 reviewer10까지 테스트 계정을 생성합니다.\n비밀번호는 모두 reviewer12#$로 설정됩니다.\n이미 존재하는 아이디는 건너뜁니다.'
                : `${action.title} ${action.count}개를 데이터베이스에 생성하시겠습니까?`,
            confirmText: `${action.count}개 생성`,
            tone: 'danger'
        });
        if (!confirmed) {
            return;
        }

        setCreatingKey(action.key);
        try {
            const response = await fetch(action.endpoint, { method: 'POST' });
            if (!response.ok) {
                throw new Error(await response.text() || `${action.title} 생성에 실패했습니다.`);
            }

            const result = await response.json() as TestDataCreationResult;
            if (action.key === 'reviewers') {
                const skippedSummary = result.skippedCount
                    ? ` 기존 계정 ${result.skippedCount}개는 건너뛰었습니다.`
                    : '';
                onNotify('success', `${action.title} ${result.createdCount}개를 생성했습니다.${skippedSummary}`);
                return;
            }
            if (action.key === 'speakers') {
                const skippedSummary = result.skippedCount
                    ? ` 이미 존재하는 ${result.skippedCount}명은 건너뛰었습니다.`
                    : '';
                onNotify('success', `${action.title} ${result.createdCount}개를 생성했습니다.${skippedSummary}`);
                return;
            }
            const reviewSummary = action.key === 'abstracts'
                ? ` 상태별 임시저장 ${result.draftCount ?? 0}건, 제출완료 ${result.submittedCount ?? 0}건, 심사중 ${result.underReviewCount ?? 0}건, 승인 ${result.approvedCount ?? 0}건, 반려 ${result.rejectedCount ?? 0}건입니다. 심사자 배정 ${result.assignmentCount}건, 제출완료 심사 ${result.completedReviewCount}건, 평가점수 ${result.scoreCount}건, 일반 결정 ${result.normalDecisionCount ?? 0}건, 강제 결정 ${result.forcedDecisionCount ?? 0}건, 최종 발표형식 변경 ${result.changedPresentationTypeCount ?? 0}건을 생성했습니다.`
                : '';
            const optionSummary = action.key === 'pre-registrations' && (result.optionItemCount ?? 0) > 0
                ? ` 등록 옵션 ${result.optionItemCount}건을 함께 생성했습니다.`
                : '';
            onNotify('success', `${action.title} ${result.createdCount}개를 생성했습니다.${optionSummary}${reviewSummary}`);
        } catch (error) {
            const message = error instanceof Error ? error.message : `${action.title} 생성에 실패했습니다.`;
            onNotify('error', message);
        } finally {
            setCreatingKey(null);
        }
    };

    const handleDailyAction = async (action: DailyTestDataAction) => {
        const confirmed = await confirm({
            title: `${action.title} 생성`,
            message: `행사 시작 60일 전부터 전날까지 ${action.rangeLabel}의 샘플 데이터를 생성하시겠습니까?\n이미 생성된 일별 샘플은 건너뜁니다.`,
            confirmText: '일별 데이터 생성',
            tone: 'danger'
        });
        if (!confirmed) {
            return;
        }

        setCreatingKey(action.key);
        try {
            const response = await fetch(action.endpoint, { method: 'POST' });
            if (!response.ok) {
                throw new Error(await response.text() || `${action.title} 생성에 실패했습니다.`);
            }

            const result = await response.json() as DailyTestDataCreationResult;
            const skippedSummary = result.skippedCount > 0
                ? ` 기존 ${result.skippedCount}개는 건너뛰었습니다.`
                : '';
            onNotify(
                'success',
                `${result.startDate}~${result.endDate} ${action.title} ${result.createdCount}개를 생성했습니다.${skippedSummary}`
            );
        } catch (error) {
            const message = error instanceof Error ? error.message : `${action.title} 생성에 실패했습니다.`;
            onNotify('error', message);
        } finally {
            setCreatingKey(null);
        }
    };

    return (
        <div className="mx-auto max-w-6xl space-y-5 pb-6">
            <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-950 md:p-6">
                <div className="flex items-center gap-3">
                    <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300">
                        <Database className="h-5 w-5" />
                    </span>
                    <h1 className="text-lg font-bold tracking-tight text-slate-900 dark:text-white md:text-xl">테스트 데이터 생성</h1>
                </div>
                <p className="mt-1 text-sm leading-6 text-slate-500 dark:text-slate-400">
                    관리자 기능 검증에 필요한 테스트 데이터를 유형별로 생성할 수 있습니다.
                </p>
            </section>

            <AnalyticsTestDataCard dashboardEnabled={dashboardEnabled} onNotify={onNotify} disabled={creatingKey !== null && creatingKey !== 'analytics'} onBusy={(busy) => setCreatingKey(current => busy ? 'analytics' : current === 'analytics' ? null : current)} />
            <section className="space-y-4">
                <div className="flex flex-col gap-2 rounded-xl border border-blue-200 bg-blue-50/70 p-4 dark:border-blue-900 dark:bg-blue-950/30 sm:flex-row sm:items-center sm:justify-between">
                    <div>
                        <h2 className="font-bold text-slate-900 dark:text-white">대시보드 일별 추이 샘플</h2>
                        <p className="mt-1 text-sm leading-6 text-slate-600 dark:text-slate-300">
                            회원 → 사전등록 → 초록 순서로 실행하세요. 행사 시작 60일 전부터 전날까지 생성하며 자동 생성은 오늘 이전 누락분만 보충합니다.
                        </p>
                    </div>
                    <span className="inline-flex shrink-0 items-center gap-2 self-start rounded-full bg-white px-3 py-1.5 text-xs font-bold text-blue-700 shadow-sm dark:bg-slate-900 dark:text-blue-300 sm:self-auto">
                        <CalendarClock className="h-4 w-4" />
                        매일 00:01
                    </span>
                </div>

                <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
                    {DAILY_TEST_DATA_ACTIONS.map((action) => {
                        const Icon = action.icon;

                        return (
                            <article
                                key={action.key}
                                className="flex min-h-56 flex-col rounded-xl border border-slate-200 bg-white p-5 shadow-sm transition-shadow hover:shadow-md dark:border-slate-800 dark:bg-slate-950"
                            >
                                <span className={`flex h-11 w-11 items-center justify-center rounded-xl ${action.iconClassName}`}>
                                    <Icon className="h-5 w-5" />
                                </span>
                                <div className="mt-4 flex items-center justify-between gap-3">
                                    <h3 className="text-base font-bold text-slate-900 dark:text-white">{action.title}</h3>
                                    <span className="whitespace-nowrap rounded-full bg-slate-100 px-2.5 py-1 text-xs font-bold text-slate-600 dark:bg-slate-800 dark:text-slate-300">
                                        {action.rangeLabel}
                                    </span>
                                </div>
                                <p className="mt-2 flex-1 text-sm leading-6 text-slate-500 dark:text-slate-400">{action.description}</p>
                                <button
                                    type="button"
                                    onClick={() => void handleDailyAction(action)}
                                    disabled={creatingKey !== null}
                                    className={`mt-5 inline-flex w-full items-center justify-center gap-2 rounded-lg px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 dark:focus-visible:ring-offset-slate-950 ${action.buttonClassName}`}
                                >
                                    {creatingKey === action.key && <LoaderCircle className="h-4 w-4 animate-spin" />}
                                    {creatingKey === action.key ? '생성 중' : action.buttonLabel}
                                </button>
                            </article>
                        );
                    })}
                </div>
            </section>

            <section className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
                {TEST_DATA_ACTIONS.map((action) => {
                    const Icon = action.icon;

                    return (
                        <article
                            key={action.key}
                            className="flex min-h-56 flex-col rounded-xl border border-slate-200 bg-white p-5 shadow-sm transition-shadow hover:shadow-md dark:border-slate-800 dark:bg-slate-950"
                        >
                            <span className={`flex h-11 w-11 items-center justify-center rounded-xl ${action.iconClassName}`}>
                                <Icon className="h-5 w-5" />
                            </span>
                            <div className="mt-4 flex items-center justify-between gap-3">
                                <h2 className="text-base font-bold text-slate-900 dark:text-white">{action.title}</h2>
                                <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-bold text-slate-600 dark:bg-slate-800 dark:text-slate-300">
                                    {action.count}개
                                </span>
                            </div>
                            <p className="mt-2 flex-1 text-sm leading-6 text-slate-500 dark:text-slate-400">{action.description}</p>
                            <button
                                type="button"
                                onClick={() => void handleAction(action)}
                                disabled={creatingKey !== null}
                                className={`mt-5 inline-flex w-full items-center justify-center gap-2 rounded-lg px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 dark:focus-visible:ring-offset-slate-950 ${action.buttonClassName}`}
                            >
                                {creatingKey === action.key && <LoaderCircle className="h-4 w-4 animate-spin" />}
                                {creatingKey === action.key ? '생성 중' : action.buttonLabel}
                            </button>
                        </article>
                    );
                })}
            </section>
        </div>
    );
};
