import { useEffect, useMemo, useRef, useState, type FormEvent, type ReactNode } from 'react';
import {
    AlertTriangle,
    ChevronLeft,
    ChevronRight,
    Clock3,
    Eye,
    FileSearch,
    FilterX,
    History,
    Save,
    ScanSearch,
    Search,
    ShieldAlert,
    X
} from 'lucide-react';
import type { NotificationType } from './NotificationToast';
import type { AbstractSimilarityResponse, AbstractSubmissionDetail } from './abstractTypes';
import { AbstractSimilarityComparisonModal } from './AbstractSimilarityComparisonModal';
import { DraggableModal } from './DraggableModal';
import { useConfirm } from './confirmDialogContext';

type ReviewStatus =
    | 'PENDING'
    | 'IN_REVIEW'
    | 'EXPLANATION_REQUESTED'
    | 'EXPLANATION_RECEIVED'
    | 'CLEARED'
    | 'VIOLATION_SUSPECTED'
    | 'VIOLATION_CONFIRMED';

interface ReviewItem {
    abstractSeq: number;
    submissionNo?: string | null;
    title: string;
    abstractStatus: string;
    targetAbstractSeq: number;
    targetSubmissionNo?: string | null;
    targetTitle: string;
    overallSimilarity: number;
    highestSection: string;
    highestSimilarity: number;
    analyzedAt?: string | null;
    stale: boolean;
    riskLevel: 'HIGH' | 'CRITICAL';
    reviewSeq?: number | null;
    reviewStatus: ReviewStatus;
    reviewOpinion?: string | null;
    rejectionRecommended: boolean;
    handledByAdminSeq?: number | null;
    handledByAdminName?: string | null;
    handledAt?: string | null;
    updatedAt?: string | null;
}

interface ReviewHistoryItem {
    seq: number;
    previousStatus?: ReviewStatus | null;
    status: ReviewStatus;
    reviewOpinion?: string | null;
    rejectionRecommended: boolean;
    handledByAdminSeq: number;
    handledByAdminName?: string | null;
    handledAt: string;
}

interface ReviewPageResponse {
    items: ReviewItem[];
    page: number;
    size: number;
    totalPages: number;
    totalCount: number;
    openCount: number;
    concernCount: number;
    overallThreshold: number;
    sectionThreshold: number;
    criticalOverallThreshold: number;
    criticalSectionThreshold: number;
}

interface Filters {
    keyword: string;
    status: string;
    riskLevel: string;
    stale: string;
}

interface Props {
    onNotify: (type: NotificationType, message: string) => void;
}

const PAGE_SIZE = 20;
const EMPTY_FILTERS: Filters = { keyword: '', status: '', riskLevel: '', stale: '' };
const DEFAULT_THRESHOLDS = {
    overallThreshold: 80,
    sectionThreshold: 90,
    criticalOverallThreshold: 90,
    criticalSectionThreshold: 95
};

const STATUS_OPTIONS: Array<{ value: ReviewStatus; label: string }> = [
    { value: 'PENDING', label: '검토 대기' },
    { value: 'IN_REVIEW', label: '검토 중' },
    { value: 'EXPLANATION_REQUESTED', label: '소명 요청' },
    { value: 'EXPLANATION_RECEIVED', label: '소명 접수' },
    { value: 'CLEARED', label: '문제없음' },
    { value: 'VIOLATION_SUSPECTED', label: '위반 의심' },
    { value: 'VIOLATION_CONFIRMED', label: '위반 확인' }
];

const dateTimeFormatter = new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
});

export const AbstractSimilarityReviewPage = ({ onNotify }: Props) => {
    const [items, setItems] = useState<ReviewItem[]>([]);
    const [draftFilters, setDraftFilters] = useState<Filters>(EMPTY_FILTERS);
    const [filters, setFilters] = useState<Filters>(EMPTY_FILTERS);
    const [currentPage, setCurrentPage] = useState(1);
    const [totalPages, setTotalPages] = useState(1);
    const [stats, setStats] = useState({ totalCount: 0, openCount: 0, concernCount: 0 });
    const [thresholds, setThresholds] = useState(DEFAULT_THRESHOLDS);
    const [isLoading, setIsLoading] = useState(false);
    const [hasError, setHasError] = useState(false);
    const [reloadKey, setReloadKey] = useState(0);
    const [selectedItem, setSelectedItem] = useState<ReviewItem | null>(null);
    const onNotifyRef = useRef(onNotify);

    useEffect(() => {
        onNotifyRef.current = onNotify;
    }, [onNotify]);

    useEffect(() => {
        const controller = new AbortController();
        const load = async () => {
            setIsLoading(true);
            setHasError(false);
            try {
                const params = new URLSearchParams({
                    page: String(currentPage),
                    size: String(PAGE_SIZE),
                    keyword: filters.keyword,
                    status: filters.status,
                    riskLevel: filters.riskLevel
                });
                if (filters.stale) params.set('stale', filters.stale);
                const response = await fetch(`/api/admin/abstract-similarity-reviews?${params.toString()}`, {
                    signal: controller.signal
                });
                if (!response.ok) {
                    throw new Error(await response.text() || '유사도 검토 대상을 불러오지 못했습니다.');
                }
                const data = await response.json() as ReviewPageResponse;
                setItems(data.items);
                setCurrentPage(data.page);
                setTotalPages(data.totalPages);
                setStats({ totalCount: data.totalCount, openCount: data.openCount, concernCount: data.concernCount });
                setThresholds({
                    overallThreshold: data.overallThreshold,
                    sectionThreshold: data.sectionThreshold,
                    criticalOverallThreshold: data.criticalOverallThreshold,
                    criticalSectionThreshold: data.criticalSectionThreshold
                });
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') return;
                setItems([]);
                setHasError(true);
                onNotifyRef.current('error', error instanceof Error ? error.message : '유사도 검토 대상을 불러오지 못했습니다.');
            } finally {
                if (!controller.signal.aborted) setIsLoading(false);
            }
        };
        void load();
        return () => controller.abort();
    }, [currentPage, filters, reloadKey]);

    const pageNumbers = useMemo(() => {
        const first = Math.max(1, Math.min(currentPage - 2, Math.max(totalPages - 4, 1)));
        return Array.from({ length: Math.min(5, totalPages - first + 1) }, (_, index) => first + index);
    }, [currentPage, totalPages]);

    const handleSearch = (event: FormEvent) => {
        event.preventDefault();
        setCurrentPage(1);
        setFilters({ ...draftFilters, keyword: draftFilters.keyword.trim() });
    };

    const applySelectFilter = (key: 'status' | 'riskLevel' | 'stale', value: string) => {
        const nextFilters = { ...draftFilters, [key]: value };
        setDraftFilters(nextFilters);
        setCurrentPage(1);
        setFilters({ ...nextFilters, keyword: nextFilters.keyword.trim() });
    };

    const resetFilters = () => {
        setDraftFilters(EMPTY_FILTERS);
        setFilters(EMPTY_FILTERS);
        setCurrentPage(1);
    };

    const handleSaved = (saved: ReviewItem) => {
        setSelectedItem(saved);
        setReloadKey((value) => value + 1);
    };

    return (
        <section className="overflow-hidden rounded-xl border border-slate-200 bg-white text-slate-900 shadow-sm dark:border-slate-800 dark:bg-slate-950 dark:text-slate-50">
            <div className="border-b border-slate-200 p-4 dark:border-slate-800 md:p-5">
                <div className="flex items-center gap-2">
                    <ScanSearch className="h-4 w-4 shrink-0 text-blue-600 dark:text-blue-400" />
                    <h1 className="text-sm font-semibold md:text-base">유사도 검토</h1>
                </div>
                <p className="mt-1 text-xs text-slate-400 dark:text-slate-400">
                    저장된 AI 의미 유사도 결과를 바탕으로 연구윤리 검토 결과와 반려 권고를 기록합니다. 최종 초록 승인·반려 상태는 변경하지 않습니다.
                </p>
                <p className="mt-2 text-xs font-medium text-amber-700 dark:text-amber-300">
                    업무 대상 기준: 종합 {thresholds.overallThreshold.toFixed(0)}점 이상 또는 항목별 최고 {thresholds.sectionThreshold.toFixed(0)}점 이상
                </p>
            </div>

            <div className="grid grid-cols-1 divide-y divide-slate-200 border-b border-slate-200 dark:divide-slate-800 dark:border-slate-800 sm:grid-cols-3 sm:divide-x sm:divide-y-0">
                <Stat label="검색 결과" value={stats.totalCount} loading={isLoading} error={hasError} tone="slate" />
                <Stat label="검토 진행 필요" value={stats.openCount} loading={isLoading} error={hasError} tone="blue" />
                <Stat label="위반 우려·반려 권고" value={stats.concernCount} loading={isLoading} error={hasError} tone="rose" />
            </div>

            <form onSubmit={handleSearch} className="border-b border-slate-200 bg-slate-50/70 p-4 dark:border-slate-800 dark:bg-slate-900/40 md:p-5">
                <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-4">
                    <label className="md:col-span-2">
                        <span className="mb-1 block text-[11px] font-semibold text-slate-500 dark:text-slate-400">검색어</span>
                        <div className="relative">
                            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                            <input
                                value={draftFilters.keyword}
                                onChange={(event) => setDraftFilters((value) => ({ ...value, keyword: event.target.value }))}
                                placeholder="현재·비교 초록의 접수번호 또는 제목"
                                className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 pl-9 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-700 dark:bg-slate-950 dark:text-slate-50"
                            />
                        </div>
                    </label>
                    <FilterSelect label="검토 상태" value={draftFilters.status} onChange={(value) => applySelectFilter('status', value)}>
                        <option value="">전체</option>
                        {STATUS_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
                    </FilterSelect>
                    <FilterSelect label="유사도 구간" value={draftFilters.riskLevel} onChange={(value) => applySelectFilter('riskLevel', value)}>
                        <option value="">전체</option>
                        <option value="CRITICAL">매우 높음</option>
                        <option value="HIGH">높음</option>
                    </FilterSelect>
                    <FilterSelect label="분석 최신 여부" value={draftFilters.stale} onChange={(value) => applySelectFilter('stale', value)}>
                        <option value="">전체</option>
                        <option value="false">최신</option>
                        <option value="true">재분석 필요</option>
                    </FilterSelect>
                </div>
                <div className="mt-3 flex flex-col justify-end gap-2 sm:flex-row">
                    <button type="button" onClick={resetFilters} className="inline-flex items-center justify-center gap-2 rounded-lg border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900">
                        <FilterX className="h-4 w-4" /> 초기화
                    </button>
                    <button type="submit" className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-5 py-2 text-xs font-semibold text-white hover:bg-blue-700 dark:bg-blue-600 dark:text-white dark:hover:bg-blue-700">
                        <Search className="h-4 w-4" /> 조회
                    </button>
                </div>
            </form>

            <div className="overflow-x-auto">
                <table className="w-full min-w-[1040px] text-left text-xs md:text-sm">
                    <thead className="border-b border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900/60">
                        <tr>
                            <th className="p-4">현재 초록</th>
                            <th className="p-4">대표 유사 초록</th>
                            <th className="p-4">AI 의미 유사도</th>
                            <th className="p-4">분석 상태</th>
                            <th className="p-4">검토 상태</th>
                            <th className="p-4">최근 처리</th>
                            <th className="p-4 text-right">업무</th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                        {isLoading && <TableMessage message="유사도 검토 대상을 불러오는 중입니다." />}
                        {!isLoading && hasError && <TableMessage message="조회에 실패했습니다. 검색 조건을 다시 적용해 재시도해 주세요." />}
                        {!isLoading && !hasError && items.length === 0 && <TableMessage message="현재 조건에 해당하는 유사도 검토 대상이 없습니다." />}
                        {!isLoading && !hasError && items.map((item) => (
                            <tr key={item.abstractSeq} className="hover:bg-slate-50/70 dark:hover:bg-slate-900/40">
                                <td className="max-w-[260px] p-4">
                                    <p className="font-semibold text-slate-900 dark:text-slate-50">{item.submissionNo || `#${item.abstractSeq}`}</p>
                                    <p className="mt-1 line-clamp-2 text-xs text-slate-500 dark:text-slate-400">{item.title}</p>
                                </td>
                                <td className="max-w-[260px] p-4">
                                    <p className="font-semibold text-slate-700 dark:text-slate-200">{item.targetSubmissionNo || `#${item.targetAbstractSeq}`}</p>
                                    <p className="mt-1 line-clamp-2 text-xs text-slate-500 dark:text-slate-400">{item.targetTitle}</p>
                                </td>
                                <td className="p-4">
                                    <p className={`text-base font-bold tabular-nums ${item.riskLevel === 'CRITICAL' ? 'text-rose-600 dark:text-rose-400' : 'text-amber-600 dark:text-amber-400'}`}>
                                        종합 {formatScore(item.overallSimilarity)}
                                    </p>
                                    <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">{sectionLabel(item.highestSection)} {formatScore(item.highestSimilarity)}</p>
                                </td>
                                <td className="p-4">
                                    {item.stale
                                        ? <span className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2.5 py-1 text-xs font-semibold text-amber-700 dark:bg-amber-950/40 dark:text-amber-300"><AlertTriangle className="h-3.5 w-3.5" />재분석 필요</span>
                                        : <span className="inline-flex rounded-full bg-emerald-100 px-2.5 py-1 text-xs font-semibold text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300">최신</span>}
                                    <p className="mt-1 text-[11px] text-slate-400">{formatDateTime(item.analyzedAt)}</p>
                                </td>
                                <td className="p-4"><StatusBadge status={item.reviewStatus} />{item.rejectionRecommended && <p className="mt-1 text-[11px] font-semibold text-rose-600 dark:text-rose-400">반려 권고</p>}</td>
                                <td className="p-4 text-xs text-slate-500 dark:text-slate-400">
                                    <p>{item.handledByAdminName || '-'}</p>
                                    <p className="mt-1">{formatDateTime(item.handledAt)}</p>
                                </td>
                                <td className="p-4 text-right">
                                    <button type="button" onClick={() => setSelectedItem(item)} className="inline-flex items-center gap-1.5 rounded-lg border border-blue-200 px-3 py-2 text-xs font-semibold text-blue-700 hover:bg-blue-50 dark:border-blue-900/70 dark:text-blue-300 dark:hover:bg-blue-950/30">
                                        <Eye className="h-4 w-4" /> {item.reviewStatus === 'PENDING' ? '검토 시작' : '검토 계속'}
                                    </button>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            <div className="flex flex-col items-center justify-between gap-3 border-t border-slate-200 p-4 dark:border-slate-800 sm:flex-row">
                <p className="text-xs text-slate-500 dark:text-slate-400">총 {stats.totalCount.toLocaleString()}건 · {currentPage}/{totalPages} 페이지</p>
                <div className="flex items-center gap-1">
                    <PageButton label="이전 페이지" disabled={currentPage <= 1} onClick={() => setCurrentPage((page) => page - 1)}><ChevronLeft className="h-4 w-4" /></PageButton>
                    {pageNumbers.map((page) => <button key={page} type="button" onClick={() => setCurrentPage(page)} aria-current={page === currentPage ? 'page' : undefined} className={`h-8 min-w-8 rounded-lg px-2 text-xs font-semibold ${page === currentPage ? 'bg-blue-600 text-white' : 'text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-900'}`}>{page}</button>)}
                    <PageButton label="다음 페이지" disabled={currentPage >= totalPages} onClick={() => setCurrentPage((page) => page + 1)}><ChevronRight className="h-4 w-4" /></PageButton>
                </div>
            </div>

            {selectedItem && (
                <SimilarityReviewDetailModal
                    key={selectedItem.abstractSeq}
                    item={selectedItem}
                    thresholds={thresholds}
                    onClose={() => setSelectedItem(null)}
                    onSaved={handleSaved}
                    onNotify={onNotify}
                />
            )}
        </section>
    );
};

interface DetailProps extends Props {
    item: ReviewItem;
    thresholds: typeof DEFAULT_THRESHOLDS;
    onClose: () => void;
    onSaved: (saved: ReviewItem) => void;
}

const SimilarityReviewDetailModal = ({ item, thresholds, onClose, onSaved, onNotify }: DetailProps) => {
    const confirm = useConfirm();
    const [source, setSource] = useState<AbstractSubmissionDetail | null>(null);
    const [analysis, setAnalysis] = useState<AbstractSimilarityResponse | null>(null);
    const [history, setHistory] = useState<ReviewHistoryItem[]>([]);
    const [status, setStatus] = useState<ReviewStatus>(item.reviewStatus);
    const [opinion, setOpinion] = useState(item.reviewOpinion ?? '');
    const [rejectionRecommended, setRejectionRecommended] = useState(item.rejectionRecommended);
    const [isLoading, setIsLoading] = useState(false);
    const [isSaving, setIsSaving] = useState(false);
    const [loadFailed, setLoadFailed] = useState(false);
    const [comparisonOpen, setComparisonOpen] = useState(false);
    const [comparisonTargetSeq, setComparisonTargetSeq] = useState<number | null>(null);
    const onNotifyRef = useRef(onNotify);
    const savingRef = useRef(false);

    useEffect(() => { onNotifyRef.current = onNotify; }, [onNotify]);

    useEffect(() => {
        const controller = new AbortController();
        const load = async () => {
            setIsLoading(true);
            setLoadFailed(false);
            try {
                const [sourceResponse, analysisResponse, historyResponse] = await Promise.all([
                    fetch(`/api/admin/abstracts/${item.abstractSeq}`, { signal: controller.signal }),
                    fetch(`/api/admin/abstracts/${item.abstractSeq}/similarities`, { signal: controller.signal }),
                    fetch(`/api/admin/abstract-similarity-reviews/${item.abstractSeq}/history`, { signal: controller.signal })
                ]);
                if (!sourceResponse.ok) throw new Error(await sourceResponse.text() || '현재 초록을 불러오지 못했습니다.');
                if (!analysisResponse.ok) throw new Error(await analysisResponse.text() || '유사도 분석 결과를 불러오지 못했습니다.');
                if (!historyResponse.ok) throw new Error(await historyResponse.text() || '검토 이력을 불러오지 못했습니다.');
                setSource(await sourceResponse.json() as AbstractSubmissionDetail);
                setAnalysis(await analysisResponse.json() as AbstractSimilarityResponse);
                setHistory(await historyResponse.json() as ReviewHistoryItem[]);
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') return;
                setLoadFailed(true);
                onNotifyRef.current('error', error instanceof Error ? error.message : '유사도 검토 상세를 불러오지 못했습니다.');
            } finally {
                if (!controller.signal.aborted) setIsLoading(false);
            }
        };
        void load();
        return () => controller.abort();
    }, [item.abstractSeq]);

    useEffect(() => {
        if (isSaving) return;
        const handleKeyDown = (event: KeyboardEvent) => {
            if (event.key === 'Escape' && !comparisonOpen) onClose();
        };
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [comparisonOpen, isSaving, item, onClose]);

    const loadHistory = async () => {
        const response = await fetch(`/api/admin/abstract-similarity-reviews/${item.abstractSeq}/history`);
        if (!response.ok) throw new Error(await response.text() || '검토 이력을 불러오지 못했습니다.');
        setHistory(await response.json() as ReviewHistoryItem[]);
    };

    const save = async () => {
        if (savingRef.current) return;
        const trimmedOpinion = opinion.trim();
        if (['EXPLANATION_REQUESTED', 'VIOLATION_SUSPECTED', 'VIOLATION_CONFIRMED'].includes(status) && !trimmedOpinion) {
            onNotifyRef.current('error', '선택한 검토 상태에는 관리자 검토 의견이 필요합니다.');
            return;
        }
        if (opinion.length > 4000) {
            onNotifyRef.current('error', '관리자 검토 의견은 4,000자 이하로 입력해 주세요.');
            return;
        }
        if (status === 'VIOLATION_CONFIRMED' || rejectionRecommended) {
            const confirmed = await confirm({
                title: '유사도 검토 결과 저장',
                message: rejectionRecommended
                    ? '위반 관련 상태와 반려 권고를 기록하시겠습니까? 이 작업은 최종 초록 승인·반려 상태를 변경하지 않습니다.'
                    : '위반 확인 상태를 기록하시겠습니까? 이 작업은 최종 초록 승인·반려 상태를 변경하지 않습니다.',
                confirmText: '저장',
                tone: 'danger'
            });
            if (!confirmed) return;
        }

        savingRef.current = true;
        setIsSaving(true);
        try {
            const response = await fetch(`/api/admin/abstract-similarity-reviews/${item.abstractSeq}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ status, reviewOpinion: trimmedOpinion || null, rejectionRecommended })
            });
            if (!response.ok) throw new Error(await response.text() || '유사도 검토 결과를 저장하지 못했습니다.');
            const saved = await response.json() as ReviewItem;
            setStatus(saved.reviewStatus);
            setOpinion(saved.reviewOpinion ?? '');
            setRejectionRecommended(saved.rejectionRecommended);
            onSaved(saved);
            await loadHistory();
            onNotifyRef.current('success', '유사도 검토 결과와 처리 이력을 저장했습니다.');
        } catch (error) {
            onNotifyRef.current('error', error instanceof Error ? error.message : '유사도 검토 결과를 저장하지 못했습니다.');
        } finally {
            savingRef.current = false;
            setIsSaving(false);
        }
    };

    const openComparison = (targetSeq: number) => {
        setComparisonTargetSeq(targetSeq);
        setComparisonOpen(true);
    };

    return (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-slate-950/60 p-2 dark:bg-slate-950/60 sm:p-4" onMouseDown={(event) => { if (event.target === event.currentTarget && !isSaving) onClose(); }}>
            <DraggableModal className="flex max-h-[95vh] w-full max-w-6xl flex-col overflow-hidden rounded-2xl bg-white shadow-2xl dark:bg-slate-950" role="dialog" aria-modal="true" aria-labelledby="similarity-review-title" aria-busy={isLoading || isSaving}>
                <header data-modal-drag-handle className="flex shrink-0 cursor-move select-none touch-none items-start justify-between gap-4 border-b border-slate-200 px-5 py-4 dark:border-slate-800">
                    <div>
                        <div className="flex items-center gap-2"><FileSearch className="h-5 w-5 text-blue-600 dark:text-blue-400" /><h2 id="similarity-review-title" className="text-base font-bold">유사도 상세 검토</h2></div>
                        <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">{item.submissionNo || `#${item.abstractSeq}`} · 점수와 원문을 확인하고 연구윤리 검토 결과를 기록합니다.</p>
                    </div>
                    <button type="button" onClick={onClose} disabled={isSaving} aria-label="유사도 상세 검토 닫기" className="rounded-lg p-2 text-slate-400 hover:bg-slate-100 disabled:opacity-40 dark:hover:bg-slate-900"><X className="h-5 w-5" /></button>
                </header>

                <div className="flex-1 overflow-y-auto p-5">
                    {isLoading && <p className="py-16 text-center text-sm text-slate-500 dark:text-slate-400">상세 검토 정보를 불러오는 중입니다.</p>}
                    {!isLoading && loadFailed && <p className="py-16 text-center text-sm text-slate-500 dark:text-slate-400">상세 조회에 실패했습니다. 창을 닫고 다시 검토해 주세요.</p>}
                    {!isLoading && !loadFailed && analysis && source && (
                        <div className="space-y-5">
                            {analysis.stale && <div className="flex items-start gap-2 rounded-xl border border-amber-200 bg-amber-50 p-3 text-xs leading-5 text-amber-800 dark:border-amber-900/60 dark:bg-amber-950/30 dark:text-amber-200"><AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />분석 이후 현재 초록 내용이 변경되었습니다. 결과를 참고하되 전체 유사도 분석을 다시 실행해 최신 결과로 검토해 주세요.</div>}

                            <section className="grid gap-4 lg:grid-cols-2">
                                <AbstractCard eyebrow="현재 초록" identifier={source.submissionNo || `#${source.seq}`} title={source.title} status={source.status} accent="blue" />
                                <AbstractCard eyebrow="대표 비교 대상" identifier={item.targetSubmissionNo || `#${item.targetAbstractSeq}`} title={item.targetTitle} accent="violet" />
                            </section>

                            <section className="overflow-hidden rounded-xl border border-slate-200 dark:border-slate-800">
                                <div className="grid grid-cols-2 divide-x divide-y divide-slate-200 dark:divide-slate-800 md:grid-cols-4 md:divide-y-0">
                                    <Metric label="대표 종합 유사도" value={formatScore(item.overallSimilarity)} tone={item.riskLevel === 'CRITICAL' ? 'rose' : 'amber'} />
                                    <Metric label="대표 최고 항목" value={`${sectionLabel(item.highestSection)} ${formatScore(item.highestSimilarity)}`} tone="amber" />
                                    <Metric label="분석 시점" value={formatDateTime(analysis.analyzedAt)} tone="slate" />
                                    <Metric label="분석 데이터" value={analysis.stale ? '재분석 필요' : '최신'} tone={analysis.stale ? 'amber' : 'emerald'} />
                                </div>
                                <p className="border-t border-slate-200 bg-slate-50/70 px-4 py-2 text-[11px] text-slate-500 dark:border-slate-800 dark:bg-slate-900/40 dark:text-slate-400">
                                    매우 높음 기준은 종합 {thresholds.criticalOverallThreshold.toFixed(0)}점 이상 또는 항목별 최고 {thresholds.criticalSectionThreshold.toFixed(0)}점 이상입니다. 점수는 표절률이 아닌 AI 의미 유사도입니다.
                                </p>
                            </section>

                            <section>
                                <div className="mb-3 flex items-center gap-2"><ScanSearch className="h-4 w-4 text-blue-600 dark:text-blue-400" /><h3 className="text-sm font-semibold">유사 비교 결과</h3></div>
                                <div className="overflow-x-auto rounded-xl border border-slate-200 dark:border-slate-800">
                                    <table className="w-full min-w-[760px] text-left text-xs md:text-sm">
                                        <thead className="border-b border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900/60"><tr><th className="p-3">비교 초록</th><th className="p-3">종합</th><th className="p-3">제목</th><th className="p-3">Objective</th><th className="p-3">Methods</th><th className="p-3">Results</th><th className="p-3">Conclusions</th><th className="p-3 text-right">원문</th></tr></thead>
                                        <tbody className="divide-y divide-slate-200 dark:divide-slate-800">{analysis.matches.map((match) => <tr key={match.abstractSeq}><td className="max-w-[220px] p-3"><p className="font-semibold">{match.submissionNo || `#${match.abstractSeq}`}</p><p className="mt-1 truncate text-[11px] text-slate-500 dark:text-slate-400">{match.title}</p></td><ScoreCell value={match.overallSimilarity} strong /><ScoreCell value={match.titleSimilarity} /><ScoreCell value={match.objectiveSimilarity} /><ScoreCell value={match.methodsSimilarity} /><ScoreCell value={match.resultsSimilarity} /><ScoreCell value={match.conclusionsSimilarity} /><td className="p-3 text-right"><button type="button" onClick={() => openComparison(match.abstractSeq)} className="inline-flex items-center gap-1 rounded-lg border border-slate-200 px-2.5 py-1.5 text-xs font-semibold text-slate-600 hover:bg-slate-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900"><Eye className="h-3.5 w-3.5" />비교</button></td></tr>)}</tbody>
                                    </table>
                                </div>
                            </section>

                            <section className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(22rem,0.85fr)]">
                                <div className="rounded-xl border border-slate-200 p-4 dark:border-slate-800">
                                    <div className="flex items-center gap-2"><ShieldAlert className="h-4 w-4 text-blue-600 dark:text-blue-400" /><h3 className="text-sm font-semibold">검토 처리</h3></div>
                                    <div className="mt-4 space-y-4">
                                        <label className="block space-y-1.5 text-sm font-semibold text-slate-700 dark:text-slate-200">검토 상태<select value={status} onChange={(event) => { const next = event.target.value as ReviewStatus; setStatus(next); if (!['VIOLATION_SUSPECTED', 'VIOLATION_CONFIRMED'].includes(next)) setRejectionRecommended(false); }} disabled={isSaving} className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm font-normal text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50">{STATUS_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select></label>
                                        <label className="block space-y-1.5 text-sm font-semibold text-slate-700 dark:text-slate-200">관리자 검토 의견<textarea value={opinion} onChange={(event) => setOpinion(event.target.value)} maxLength={4000} rows={6} disabled={isSaving} placeholder="판단 근거, 소명 요청 내용, 후속 처리 참고사항을 기록합니다." className="w-full resize-y rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm font-normal text-slate-900 placeholder:text-slate-400 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50" /><span className="block text-right text-[11px] font-normal text-slate-400">{opinion.length}/4,000</span></label>
                                        <label className={`flex items-start gap-3 rounded-lg border p-3 ${['VIOLATION_SUSPECTED', 'VIOLATION_CONFIRMED'].includes(status) ? 'border-rose-200 bg-rose-50/60 dark:border-rose-900/60 dark:bg-rose-950/20' : 'border-slate-200 bg-slate-50/60 opacity-60 dark:border-slate-800 dark:bg-slate-900/40'}`}><input type="checkbox" checked={rejectionRecommended} onChange={(event) => setRejectionRecommended(event.target.checked)} disabled={isSaving || !['VIOLATION_SUSPECTED', 'VIOLATION_CONFIRMED'].includes(status)} className="mt-0.5 h-5 w-5 rounded" /><span><span className="block text-sm font-semibold text-slate-700 dark:text-slate-200">최종 반려 권고</span><span className="mt-1 block text-xs font-normal leading-5 text-slate-500 dark:text-slate-400">연구윤리 검토 의견으로만 기록되며 초록의 최종 승인·반려 상태는 바꾸지 않습니다.</span></span></label>
                                    </div>
                                </div>

                                <div className="rounded-xl border border-slate-200 p-4 dark:border-slate-800">
                                    <div className="flex items-center gap-2"><History className="h-4 w-4 text-blue-600 dark:text-blue-400" /><h3 className="text-sm font-semibold">처리 이력</h3></div>
                                    <div className="mt-4 max-h-[25rem] space-y-3 overflow-y-auto pr-1">{history.length === 0 && <p className="rounded-lg bg-slate-50 p-4 text-center text-xs text-slate-500 dark:bg-slate-900/40 dark:text-slate-400">아직 저장된 처리 이력이 없습니다.</p>}{history.map((entry) => <article key={entry.seq} className="rounded-lg border border-slate-200 p-3 dark:border-slate-800"><div className="flex flex-wrap items-center justify-between gap-2"><StatusBadge status={entry.status} /><span className="inline-flex items-center gap-1 text-[11px] text-slate-400"><Clock3 className="h-3.5 w-3.5" />{formatDateTime(entry.handledAt)}</span></div><p className="mt-2 text-xs font-semibold text-slate-600 dark:text-slate-300">처리자: {entry.handledByAdminName || `관리자 #${entry.handledByAdminSeq}`}</p>{entry.previousStatus && <p className="mt-1 text-[11px] text-slate-400">{statusLabel(entry.previousStatus)} → {statusLabel(entry.status)}</p>}{entry.reviewOpinion && <p className="mt-2 whitespace-pre-wrap text-xs leading-5 text-slate-600 dark:text-slate-300">{entry.reviewOpinion}</p>}{entry.rejectionRecommended && <p className="mt-2 text-xs font-semibold text-rose-600 dark:text-rose-400">반려 권고 기록</p>}</article>)}</div>
                                </div>
                            </section>
                        </div>
                    )}
                </div>

                <footer className="flex shrink-0 justify-end gap-2 border-t border-slate-200 px-5 py-4 dark:border-slate-800">
                    <button type="button" onClick={onClose} disabled={isSaving} className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-40 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900">취소</button>
                    <button type="button" onClick={() => void save()} disabled={isLoading || loadFailed || isSaving} className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-50 dark:bg-blue-600 dark:text-white dark:hover:bg-blue-700"><Save className="h-4 w-4" />{isSaving ? '저장 중...' : '검토 결과 저장'}</button>
                </footer>
            </DraggableModal>

            <AbstractSimilarityComparisonModal key={comparisonTargetSeq ?? 'comparison'} isOpen={comparisonOpen} source={source} result={analysis} initialTargetSeq={comparisonTargetSeq} onClose={() => setComparisonOpen(false)} />
        </div>
    );
};

const Stat = ({ label, value, loading, error, tone }: { label: string; value: number; loading: boolean; error: boolean; tone: 'slate' | 'blue' | 'rose' }) => {
    const color = tone === 'blue' ? 'text-blue-600 dark:text-blue-400' : tone === 'rose' ? 'text-rose-600 dark:text-rose-400' : 'text-slate-900 dark:text-slate-50';
    return <div className="p-4 md:p-5"><p className="text-xs font-medium text-slate-500 dark:text-slate-400">{label}</p><p className={`mt-1 text-xl font-bold ${color}`}>{loading ? '집계 중...' : error ? '-' : value.toLocaleString()}</p></div>;
};

const FilterSelect = ({ label, value, onChange, children }: { label: string; value: string; onChange: (value: string) => void; children: ReactNode }) => <label><span className="mb-1 block text-[11px] font-semibold text-slate-500 dark:text-slate-400">{label}</span><select value={value} onChange={(event) => onChange(event.target.value)} className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-700 dark:bg-slate-950 dark:text-slate-50">{children}</select></label>;
const TableMessage = ({ message }: { message: string }) => <tr><td colSpan={7} className="p-12 text-center text-sm text-slate-500 dark:text-slate-400">{message}</td></tr>;
const PageButton = ({ label, disabled, onClick, children }: { label: string; disabled: boolean; onClick: () => void; children: ReactNode }) => <button type="button" aria-label={label} disabled={disabled} onClick={onClick} className="rounded-lg p-2 text-slate-500 hover:bg-slate-100 disabled:opacity-30 dark:text-slate-400 dark:hover:bg-slate-900">{children}</button>;
const formatScore = (value?: number | null) => value == null ? '-' : `${value.toFixed(1)}점`;
const formatDateTime = (value?: string | null) => value ? dateTimeFormatter.format(new Date(value)) : '-';
const statusLabel = (status: ReviewStatus) => STATUS_OPTIONS.find((option) => option.value === status)?.label ?? status;
const sectionLabel = (section?: string | null) => ({ title: '제목', objective: 'Objective', methods: 'Methods', results: 'Results', conclusions: 'Conclusions' }[section ?? ''] ?? section ?? '-');

const StatusBadge = ({ status }: { status: ReviewStatus }) => {
    const tone = status === 'CLEARED'
        ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300'
        : ['VIOLATION_SUSPECTED', 'VIOLATION_CONFIRMED'].includes(status)
            ? 'bg-rose-100 text-rose-700 dark:bg-rose-950/40 dark:text-rose-300'
            : ['EXPLANATION_REQUESTED', 'EXPLANATION_RECEIVED'].includes(status)
                ? 'bg-amber-100 text-amber-700 dark:bg-amber-950/40 dark:text-amber-300'
                : status === 'IN_REVIEW'
                    ? 'bg-blue-100 text-blue-700 dark:bg-blue-950/40 dark:text-blue-300'
                    : 'bg-slate-100 text-slate-600 dark:bg-slate-900 dark:text-slate-300';
    return <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${tone}`}>{statusLabel(status)}</span>;
};

const AbstractCard = ({ eyebrow, identifier, title, status, accent }: { eyebrow: string; identifier: string; title: string; status?: string; accent: 'blue' | 'violet' }) => <div className={`rounded-xl border p-4 ${accent === 'blue' ? 'border-blue-200 bg-blue-50/60 dark:border-blue-900/60 dark:bg-blue-950/20' : 'border-violet-200 bg-violet-50/60 dark:border-violet-900/60 dark:bg-violet-950/20'}`}><p className={`text-[11px] font-bold ${accent === 'blue' ? 'text-blue-600 dark:text-blue-400' : 'text-violet-600 dark:text-violet-400'}`}>{eyebrow}</p><p className="mt-1 text-xs font-semibold text-slate-500 dark:text-slate-400">{identifier}{status ? ` · ${status}` : ''}</p><p className="mt-2 text-sm font-semibold leading-6 text-slate-900 dark:text-slate-50">{title}</p></div>;
const Metric = ({ label, value, tone }: { label: string; value: string; tone: 'slate' | 'amber' | 'rose' | 'emerald' }) => { const color = tone === 'rose' ? 'text-rose-600 dark:text-rose-400' : tone === 'amber' ? 'text-amber-600 dark:text-amber-400' : tone === 'emerald' ? 'text-emerald-600 dark:text-emerald-400' : 'text-slate-800 dark:text-slate-200'; return <div className="p-4"><p className="text-[11px] font-semibold text-slate-400">{label}</p><p className={`mt-1 text-sm font-bold ${color}`}>{value}</p></div>; };
const ScoreCell = ({ value, strong = false }: { value?: number | null; strong?: boolean }) => <td className={`p-3 tabular-nums ${strong ? 'font-bold text-blue-600 dark:text-blue-400' : 'text-slate-600 dark:text-slate-300'}`}>{value == null ? '-' : value.toFixed(1)}</td>;
