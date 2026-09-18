import { useEffect, useRef, useState } from 'react';
import { CalendarDays, CircleDollarSign, Clock3, ListChecks, MapPin, Pencil } from 'lucide-react';
import type { ConferenceSettings } from './ConferenceSettingsModal';
import type { NotificationType } from './NotificationToast';
import { ProgramModalShell } from './ProgramModalShell';

interface ConferenceDetailModalProps {
    settings: ConferenceSettings;
    onClose: () => void;
    onEdit: () => void;
    onNotify: (type: NotificationType, message: string) => void;
}

interface RegistrationFeeCategory {
    seq: number;
    categoryCode: string;
    categoryName: string;
    description?: string | null;
    sortOrder: number;
    isUsed: 'Y' | 'N';
    earlyBirdUsdFee?: number | null;
    earlyBirdKrwFee?: number | null;
    regularUsdFee?: number | null;
    regularKrwFee?: number | null;
}

interface RegistrationOption {
    seq: number;
    optionName: string;
    description?: string | null;
    krwPrice: number | null;
    usdPrice: number | null;
    saleStartsAt: string | null;
    saleEndsAt: string | null;
    changeEndsAt: string | null;
    capacity: number | null;
    maxPerPerson: number;
    enabled: boolean;
    sortOrder: number;
}

interface RegistrationOptionSummary {
    totalCount: number;
    enabledCount: number;
    disabledCount: number;
}

interface RegistrationOptionPage {
    items: RegistrationOption[];
    page: number;
    totalPages: number;
    summary: RegistrationOptionSummary;
}

type DetailTab = 'settings' | 'fees' | 'options';

const formatDateRange = (startDate?: string | null, endDate?: string | null) => {
    if (!startDate && !endDate) return '-';
    return `${startDate || '미정'} ~ ${endDate || '미정'}`;
};

const formatDateTime = (value?: string | null) => {
    if (!value) return '-';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;
    return new Intl.DateTimeFormat('ko-KR', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
    }).format(date);
};

const formatLocalDateTime = (value?: string | null) => {
    if (!value) return '제한 없음';
    const normalized = value.replace('T', ' ').slice(0, 16);
    return normalized.replace(/^(\d{4})-(\d{2})-(\d{2})/, '$1.$2.$3');
};

const formatFee = (value: number | null | undefined, currency: 'USD' | 'KRW') => {
    if (value == null) return '미제공';
    return currency === 'KRW'
        ? `₩${Number(value).toLocaleString('ko-KR', { maximumFractionDigits: 0 })}`
        : `US$${Number(value).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
};

export const ConferenceDetailModal = ({ settings, onClose, onEdit, onNotify }: ConferenceDetailModalProps) => {
    const notifyRef = useRef(onNotify);
    const [activeTab, setActiveTab] = useState<DetailTab>('settings');
    const [fees, setFees] = useState<RegistrationFeeCategory[] | null>(settings.seq == null ? [] : null);
    const [feesFailed, setFeesFailed] = useState(false);
    const [feesReloadKey, setFeesReloadKey] = useState(0);
    const [optionData, setOptionData] = useState<RegistrationOptionPage | null>(settings.seq == null ? {
        items: [],
        page: 1,
        totalPages: 1,
        summary: { totalCount: 0, enabledCount: 0, disabledCount: 0 }
    } : null);
    const [optionPage, setOptionPage] = useState(1);
    const [optionsFailed, setOptionsFailed] = useState(false);
    const [optionsReloadKey, setOptionsReloadKey] = useState(0);

    useEffect(() => {
        notifyRef.current = onNotify;
    }, [onNotify]);

    useEffect(() => {
        if (settings.seq == null) return;

        const controller = new AbortController();
        void (async () => {
            await Promise.resolve();
            if (controller.signal.aborted) return;
            setFees(null);
            setFeesFailed(false);
            try {
                const response = await fetch(
                    `/api/admin/conference-settings/${settings.seq}/registration-fees`,
                    { signal: controller.signal }
                );
                if (!response.ok) throw new Error(await response.text() || '등록비 정보를 불러오지 못했습니다.');
                if (!controller.signal.aborted) setFees(await response.json() as RegistrationFeeCategory[]);
            } catch (error) {
                if (controller.signal.aborted) return;
                setFeesFailed(true);
                notifyRef.current('error', error instanceof Error ? error.message : '등록비 정보를 불러오지 못했습니다.');
            }
        })();
        return () => controller.abort();
    }, [feesReloadKey, settings.seq]);

    useEffect(() => {
        if (settings.seq == null) return;

        const controller = new AbortController();
        void (async () => {
            await Promise.resolve();
            if (controller.signal.aborted) return;
            setOptionData(null);
            setOptionsFailed(false);
            try {
                const params = new URLSearchParams({ page: String(optionPage) });
                const response = await fetch(
                    `/api/admin/conference-settings/${settings.seq}/registration-options?${params}`,
                    { signal: controller.signal }
                );
                if (!response.ok) throw new Error(await response.text() || '등록 옵션을 불러오지 못했습니다.');
                const result = await response.json() as RegistrationOptionPage;
                if (!controller.signal.aborted) {
                    setOptionData(result);
                    setOptionPage(result.page);
                }
            } catch (error) {
                if (controller.signal.aborted) return;
                setOptionsFailed(true);
                notifyRef.current('error', error instanceof Error ? error.message : '등록 옵션을 불러오지 못했습니다.');
            }
        })();
        return () => controller.abort();
    }, [optionPage, optionsReloadKey, settings.seq]);

    const activeFeeCount = fees?.filter((fee) => fee.isUsed === 'Y').length ?? 0;
    const tabItems: Array<{ key: DetailTab; label: string; count?: number }> = [
        { key: 'settings', label: '기본 정보' },
        { key: 'fees', label: '등록 구분별 등록비', count: fees?.length },
        { key: 'options', label: '등록 옵션 설정', count: optionData?.summary.totalCount }
    ];

    return (
        <ProgramModalShell
            title={settings.eventName || '학회 상세'}
            description={settings.seq == null ? '학회 상세 정보' : `학회 번호 #${settings.seq} · 행사, 등록비와 등록 옵션을 확인합니다.`}
            onClose={onClose}
            widthClass="max-w-7xl"
        >
            <div className="flex flex-wrap gap-2 border-b border-slate-200 px-5 py-3 dark:border-slate-800" role="tablist" aria-label="학회 상세 정보 구분">
                {tabItems.map((tab) => (
                    <button
                        key={tab.key}
                        id={`conference-detail-tab-${tab.key}`}
                        type="button"
                        role="tab"
                        aria-selected={activeTab === tab.key}
                        aria-controls={`conference-detail-panel-${tab.key}`}
                        onClick={() => setActiveTab(tab.key)}
                        className={`inline-flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-semibold ${activeTab === tab.key
                            ? 'bg-blue-600 text-white dark:bg-blue-600 dark:text-white'
                            : 'text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-900'}`}
                    >
                        {tab.label}
                        {tab.count != null && (
                            <span className={`rounded-full px-2 py-0.5 text-[11px] ${activeTab === tab.key
                                ? 'bg-white/20 text-white'
                                : 'bg-slate-100 text-slate-500 dark:bg-slate-800 dark:text-slate-300'}`}>{tab.count}</span>
                        )}
                    </button>
                ))}
            </div>

            <div
                id={`conference-detail-panel-${activeTab}`}
                role="tabpanel"
                aria-labelledby={`conference-detail-tab-${activeTab}`}
                className="p-5"
            >
                {activeTab === 'settings' && <BasicSettingsDetail settings={settings} />}

                {activeTab === 'fees' && (
                    <section aria-labelledby="conference-fee-detail-title">
                        <SectionHeader
                            icon={<CircleDollarSign className="h-5 w-5 text-emerald-500 dark:text-emerald-400" />}
                            title="등록 구분별 등록비"
                            titleId="conference-fee-detail-title"
                            description="등록 구분별 Early Bird와 Regular의 달러·한화 금액을 비교합니다."
                            summary={fees == null ? '불러오는 중' : `전체 ${fees.length}개 · 사용 ${activeFeeCount}개`}
                        />
                        {fees == null && !feesFailed && <LoadingState message="등록비 정보를 불러오는 중입니다." />}
                        {feesFailed && <FailedState message="등록비 정보를 조회하지 못했습니다." onRetry={() => setFeesReloadKey((value) => value + 1)} />}
                        {fees && fees.length === 0 && <EmptyState icon={<CircleDollarSign className="h-8 w-8" />} message="등록된 등록 구분별 등록비가 없습니다." />}
                        {fees && fees.length > 0 && (
                            <div className="mt-5 grid grid-cols-1 gap-4 xl:grid-cols-2">
                                {fees.map((fee) => <FeeDetailCard key={fee.seq} fee={fee} />)}
                            </div>
                        )}
                    </section>
                )}

                {activeTab === 'options' && (
                    <section aria-labelledby="conference-option-detail-title">
                        <SectionHeader
                            icon={<ListChecks className="h-5 w-5 text-blue-500 dark:text-blue-400" />}
                            title="등록 옵션 설정"
                            titleId="conference-option-detail-title"
                            description="현재 학회에 추가된 유료·무료 옵션과 신청 조건을 확인합니다."
                            summary={optionData == null ? '불러오는 중' : `전체 ${optionData.summary.totalCount}개 · 사용 ${optionData.summary.enabledCount}개 · 미사용 ${optionData.summary.disabledCount}개`}
                        />
                        {optionData == null && !optionsFailed && <LoadingState message="등록 옵션을 불러오는 중입니다." />}
                        {optionsFailed && <FailedState message="등록 옵션을 조회하지 못했습니다." onRetry={() => setOptionsReloadKey((value) => value + 1)} />}
                        {optionData && optionData.items.length === 0 && <EmptyState icon={<ListChecks className="h-8 w-8" />} message="등록된 등록 옵션이 없습니다." />}
                        {optionData && optionData.items.length > 0 && <RegistrationOptionTable items={optionData.items} />}
                        {optionData && optionData.summary.totalCount > 0 && (
                            <div className="mt-4 flex items-center justify-end gap-3 border-t border-slate-200 pt-4 text-xs text-slate-600 dark:border-slate-800 dark:text-slate-300">
                                <button type="button" disabled={optionData.page <= 1} onClick={() => setOptionPage(optionData.page - 1)} className="rounded-lg border border-slate-200 px-3 py-2 font-semibold hover:bg-slate-50 disabled:opacity-40 dark:border-slate-800 dark:hover:bg-slate-900">이전</button>
                                <span className="tabular-nums">{optionData.page} / {optionData.totalPages}</span>
                                <button type="button" disabled={optionData.page >= optionData.totalPages} onClick={() => setOptionPage(optionData.page + 1)} className="rounded-lg border border-slate-200 px-3 py-2 font-semibold hover:bg-slate-50 disabled:opacity-40 dark:border-slate-800 dark:hover:bg-slate-900">다음</button>
                            </div>
                        )}
                    </section>
                )}
            </div>

            <div className="flex justify-end gap-2 border-t border-slate-200 px-5 py-4 dark:border-slate-800">
                <button type="button" onClick={onClose} className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900">닫기</button>
                <button type="button" onClick={onEdit} className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700 dark:bg-blue-600 dark:text-white dark:hover:bg-blue-700">
                    <Pencil className="h-4 w-4" /> 수정
                </button>
            </div>
        </ProgramModalShell>
    );
};

const BasicSettingsDetail = ({ settings }: { settings: ConferenceSettings }) => (
    <div className="space-y-6">
        <section>
            <SectionHeader icon={<MapPin className="h-5 w-5 text-blue-500 dark:text-blue-400" />} title="행사 정보" titleId="conference-event-detail-title" description="학회 개최 장소와 행사 기간입니다." />
            <dl className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
                <DetailItem label="행사 장소" value={settings.venueAddress || '-'} />
                <DetailItem label="행사 기간" value={formatDateRange(settings.eventStartDate, settings.eventEndDate)} />
            </dl>
        </section>

        <section>
            <SectionHeader icon={<CalendarDays className="h-5 w-5 text-blue-500 dark:text-blue-400" />} title="접수 및 등록 기간" titleId="conference-period-detail-title" description="등록 단계와 제출 업무별 운영 기간입니다." />
            <dl className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
                <DetailItem label="Early Bird 등록 기간" value={formatDateRange(settings.earlyBirdStartDate, settings.earlyBirdEndDate)} tone="emerald" />
                <DetailItem label="Regular 등록 기간" value={formatDateRange(settings.regularStartDate, settings.regularEndDate)} tone="blue" />
                <DetailItem label="초록 제출 기간" value={formatDateRange(settings.abstractStartDate, settings.abstractEndDate)} />
                <DetailItem label="발표자료 등록 기간" value={formatDateRange(settings.presentationMaterialStartDate, settings.presentationMaterialEndDate)} />
            </dl>
        </section>

        <section>
            <SectionHeader icon={<Clock3 className="h-5 w-5 text-slate-400" />} title="관리 정보" titleId="conference-management-detail-title" description="학회 설정의 등록 및 최종 수정 시각입니다." />
            <dl className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
                <DetailItem label="등록일" value={formatDateTime(settings.createdAt)} />
                <DetailItem label="최종 수정일" value={formatDateTime(settings.updatedAt)} />
            </dl>
        </section>
    </div>
);

const SectionHeader = ({ icon, title, titleId, description, summary }: { icon: React.ReactNode; title: string; titleId: string; description: string; summary?: string }) => (
    <header className="flex flex-col justify-between gap-3 sm:flex-row sm:items-start">
        <div>
            <div className="flex items-center gap-2">
                {icon}
                <h4 id={titleId} className="text-sm font-bold text-slate-900 dark:text-slate-50">{title}</h4>
            </div>
            <p className="mt-1 text-xs text-slate-400 dark:text-slate-400">{description}</p>
        </div>
        {summary && <p className="shrink-0 rounded-full bg-slate-100 px-3 py-1.5 text-xs font-semibold text-slate-600 dark:bg-slate-900 dark:text-slate-300">{summary}</p>}
    </header>
);

const DetailItem = ({ label, value, tone = 'slate' }: { label: string; value: string; tone?: 'slate' | 'emerald' | 'blue' }) => {
    const toneClass = tone === 'emerald'
        ? 'border-emerald-200 bg-emerald-50/50 dark:border-emerald-900/60 dark:bg-emerald-950/20'
        : tone === 'blue'
            ? 'border-blue-200 bg-blue-50/50 dark:border-blue-900/60 dark:bg-blue-950/20'
            : 'border-slate-200 bg-slate-50/60 dark:border-slate-800 dark:bg-slate-900/50';
    return (
        <div className={`rounded-lg border px-3 py-3 ${toneClass}`}>
            <dt className="text-xs font-medium text-slate-400 dark:text-slate-400">{label}</dt>
            <dd className="mt-1.5 break-words text-sm font-medium text-slate-700 dark:text-slate-200">{value}</dd>
        </div>
    );
};

const FeeDetailCard = ({ fee }: { fee: RegistrationFeeCategory }) => (
    <article className={`rounded-xl border p-4 ${fee.isUsed === 'Y'
        ? 'border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-950'
        : 'border-slate-200 bg-slate-50/70 dark:border-slate-800 dark:bg-slate-900/40'}`}>
        <div className="flex items-start justify-between gap-3 border-b border-slate-100 pb-3 dark:border-slate-800">
            <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                    <h5 className="break-words text-sm font-bold text-slate-900 dark:text-slate-50">{fee.categoryName}</h5>
                    <span className={`rounded-full px-2 py-0.5 text-[11px] font-semibold ${fee.isUsed === 'Y'
                        ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-300'
                        : 'bg-slate-200 text-slate-600 dark:bg-slate-800 dark:text-slate-300'}`}>{fee.isUsed === 'Y' ? '사용' : '미사용'}</span>
                </div>
                <p className="mt-1 font-mono text-[11px] text-slate-400 dark:text-slate-400">{fee.categoryCode}</p>
                {fee.description && <p className="mt-2 whitespace-pre-wrap break-words text-xs text-slate-500 dark:text-slate-400">{fee.description}</p>}
            </div>
            <span className="shrink-0 text-xs text-slate-400">순서 {fee.sortOrder}</span>
        </div>
        <div className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
            <FeePeriod title="Early Bird" tone="emerald" usd={fee.earlyBirdUsdFee} krw={fee.earlyBirdKrwFee} />
            <FeePeriod title="Regular" tone="blue" usd={fee.regularUsdFee} krw={fee.regularKrwFee} />
        </div>
    </article>
);

const FeePeriod = ({ title, tone, usd, krw }: { title: string; tone: 'emerald' | 'blue'; usd?: number | null; krw?: number | null }) => {
    const panelClass = tone === 'emerald'
        ? 'border-emerald-200 bg-emerald-50/60 dark:border-emerald-900/60 dark:bg-emerald-950/20'
        : 'border-blue-200 bg-blue-50/60 dark:border-blue-900/60 dark:bg-blue-950/20';
    const titleClass = tone === 'emerald' ? 'text-emerald-700 dark:text-emerald-300' : 'text-blue-700 dark:text-blue-300';
    return (
        <div className={`rounded-xl border p-3 ${panelClass}`}>
            <p className={`text-xs font-bold uppercase tracking-wide ${titleClass}`}>{title}</p>
            <dl className="mt-3 grid grid-cols-2 gap-2">
                <FeeValue label="USD" value={formatFee(usd, 'USD')} />
                <FeeValue label="KRW" value={formatFee(krw, 'KRW')} />
            </dl>
        </div>
    );
};

const FeeValue = ({ label, value }: { label: string; value: string }) => (
    <div className="rounded-lg bg-white/80 px-3 py-2 dark:bg-slate-950/60">
        <dt className="text-[11px] font-semibold text-slate-400">{label}</dt>
        <dd className="mt-1 whitespace-nowrap text-sm font-bold tabular-nums text-slate-800 dark:text-slate-100">{value}</dd>
    </div>
);

const RegistrationOptionTable = ({ items }: { items: RegistrationOption[] }) => (
    <div className="mt-5 overflow-x-auto rounded-xl border border-slate-200 dark:border-slate-800">
        <table className="w-full min-w-[960px] text-left text-xs md:text-sm">
            <thead className="border-b border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900/70">
                <tr>
                    <th className="p-4">옵션명</th>
                    <th className="p-4">가격</th>
                    <th className="p-4">신청·변경 기간</th>
                    <th className="p-4">수량 조건</th>
                    <th className="p-4">상태·순서</th>
                </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                {items.map((item) => (
                    <tr key={item.seq} className="hover:bg-slate-50/70 dark:hover:bg-slate-900/40">
                        <td className="max-w-64 p-4 align-top">
                            <p className="break-words font-semibold text-slate-900 dark:text-slate-50">{item.optionName}</p>
                            {item.description && <p className="mt-1 whitespace-pre-wrap break-words text-xs text-slate-500 dark:text-slate-400">{item.description}</p>}
                        </td>
                        <td className="p-4 align-top">
                            <dl className="space-y-1.5 whitespace-nowrap tabular-nums">
                                <div className="flex items-center justify-between gap-4"><dt className="text-xs text-slate-400">KRW</dt><dd className="font-semibold">{formatFee(item.krwPrice, 'KRW')}</dd></div>
                                <div className="flex items-center justify-between gap-4"><dt className="text-xs text-slate-400">USD</dt><dd className="font-semibold">{formatFee(item.usdPrice, 'USD')}</dd></div>
                            </dl>
                        </td>
                        <td className="min-w-64 p-4 align-top text-xs text-slate-600 dark:text-slate-300">
                            <p><span className="mr-2 font-semibold text-slate-400">신청</span>{formatLocalDateTime(item.saleStartsAt)} ~ {formatLocalDateTime(item.saleEndsAt)}</p>
                            <p className="mt-1.5"><span className="mr-2 font-semibold text-slate-400">변경·취소</span>{formatLocalDateTime(item.changeEndsAt)}까지</p>
                        </td>
                        <td className="whitespace-nowrap p-4 align-top text-slate-600 dark:text-slate-300">
                            <p>정원 {item.capacity == null ? '무제한' : `${item.capacity.toLocaleString('ko-KR')}명`}</p>
                            <p className="mt-1.5 text-xs text-slate-400">1인 최대 {item.maxPerPerson.toLocaleString('ko-KR')}개</p>
                        </td>
                        <td className="whitespace-nowrap p-4 align-top">
                            <span className={`inline-flex rounded-full px-2 py-1 text-xs font-semibold ${item.enabled
                                ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-300'
                                : 'bg-slate-200 text-slate-600 dark:bg-slate-800 dark:text-slate-300'}`}>{item.enabled ? '사용' : '미사용'}</span>
                            <p className="mt-2 text-xs text-slate-400">표시 순서 {item.sortOrder}</p>
                        </td>
                    </tr>
                ))}
            </tbody>
        </table>
    </div>
);

const LoadingState = ({ message }: { message: string }) => (
    <div className="py-14 text-center text-sm text-slate-400 dark:text-slate-400">{message}</div>
);

const FailedState = ({ message, onRetry }: { message: string; onRetry: () => void }) => (
    <div className="py-12 text-center">
        <p className="text-sm text-slate-500 dark:text-slate-400">{message}</p>
        <button type="button" onClick={onRetry} className="mt-3 rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900">다시 시도</button>
    </div>
);

const EmptyState = ({ icon, message }: { icon: React.ReactNode; message: string }) => (
    <div className="py-14 text-center text-slate-300 dark:text-slate-700">
        <div className="mx-auto w-fit">{icon}</div>
        <p className="mt-2 text-sm font-semibold text-slate-500 dark:text-slate-400">{message}</p>
    </div>
);
