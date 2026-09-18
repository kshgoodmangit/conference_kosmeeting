import { AlertTriangle, ArrowLeftRight, Eye, RotateCw, ScanSearch, X } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import type {
    AbstractSimilarityComparisonResponse,
    AbstractSimilarityResponse,
    AbstractSubmissionDetail
} from './abstractTypes';
import { DraggableModal } from './DraggableModal';

interface Props {
    isOpen: boolean;
    source: AbstractSubmissionDetail | null;
    result: AbstractSimilarityResponse | null;
    initialTargetSeq?: number | null;
    onClose: () => void;
}

type ComparisonSection = {
    key: 'title' | 'objective' | 'methods' | 'results' | 'conclusions';
    label: string;
    sourceText: string;
    targetText: string;
    similarity?: number | null;
};

const formatSimilarity = (value?: number | null) => value == null ? '-' : `${value.toFixed(1)}%`;
const formatText = (value?: string | null) => value?.trim() || '등록된 내용이 없습니다.';

const scoreTone = (value?: number | null) => {
    if (value == null) return 'border-slate-200 bg-slate-50 text-slate-600 dark:border-slate-800 dark:bg-slate-900/50 dark:text-slate-300';
    if (value >= 90) return 'border-rose-200 bg-rose-50 text-rose-700 dark:border-rose-900/60 dark:bg-rose-950/30 dark:text-rose-300';
    if (value >= 80) return 'border-amber-200 bg-amber-50 text-amber-700 dark:border-amber-900/60 dark:bg-amber-950/30 dark:text-amber-300';
    return 'border-blue-200 bg-blue-50 text-blue-700 dark:border-blue-900/60 dark:bg-blue-950/30 dark:text-blue-300';
};

export const AbstractSimilarityComparisonModal = ({ isOpen, source, result, initialTargetSeq, onClose }: Props) => {
    const [selectedAbstractSeq, setSelectedAbstractSeq] = useState<number | null>(null);
    const [comparison, setComparison] = useState<AbstractSimilarityComparisonResponse | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [retryToken, setRetryToken] = useState(0);

    useEffect(() => {
        if (!isOpen) return;
        const handleKeyDown = (event: KeyboardEvent) => {
            if (event.key === 'Escape') onClose();
        };
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [isOpen, onClose]);

    const selectedMatch = useMemo(
        () => result?.matches.find((match) => match.abstractSeq === (selectedAbstractSeq ?? initialTargetSeq))
            ?? result?.matches[0]
            ?? null,
        [initialTargetSeq, result, selectedAbstractSeq]
    );

    useEffect(() => {
        if (!isOpen || !source || !selectedMatch) return;

        const controller = new AbortController();
        const loadComparison = async () => {
            setLoading(true);
            setError('');
            setComparison(null);
            try {
                const response = await fetch(
                    `/api/admin/abstracts/${source.seq}/similarities/${selectedMatch.abstractSeq}`,
                    { signal: controller.signal }
                );
                if (!response.ok) {
                    throw new Error(await response.text() || '유사 초록 상세 비교 정보를 불러오지 못했습니다.');
                }
                setComparison(await response.json() as AbstractSimilarityComparisonResponse);
            } catch (loadError) {
                if (loadError instanceof DOMException && loadError.name === 'AbortError') return;
                setError(loadError instanceof Error ? loadError.message : '유사 초록 상세 비교 정보를 불러오지 못했습니다.');
            } finally {
                if (!controller.signal.aborted) setLoading(false);
            }
        };

        void loadComparison();
        return () => controller.abort();
    }, [isOpen, retryToken, selectedMatch, source]);

    if (!isOpen || !source || !result || !selectedMatch) return null;

    const score = comparison?.similarity ?? selectedMatch;
    const sections = comparison ? createSections(comparison) : [];

    return (
        <div
            className="fixed inset-0 z-[90] flex items-center justify-center bg-slate-950/70 p-2 backdrop-blur-sm sm:p-4"
            onMouseDown={(event) => {
                if (event.target === event.currentTarget) onClose();
            }}
        >
            <DraggableModal
                className="flex max-h-[calc(100dvh-1rem)] w-full max-w-[104rem] flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl dark:border-slate-800 dark:bg-slate-950"
                role="dialog"
                aria-modal="true"
                aria-labelledby="similarity-comparison-title"
                aria-busy={loading}
            >
                <header
                    data-modal-drag-handle
                    className="flex shrink-0 cursor-move select-none touch-none items-start justify-between gap-4 border-b border-slate-200 px-5 py-4 dark:border-slate-800"
                >
                    <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-2">
                            <ArrowLeftRight className="h-5 w-5 shrink-0 text-blue-600 dark:text-blue-400" />
                            <h2 id="similarity-comparison-title" className="text-base font-bold text-slate-900 dark:text-slate-50">
                                유사 초록 상세 비교
                            </h2>
                        </div>
                        <p className="mt-1 truncate text-xs text-slate-500 dark:text-slate-400">
                            {source.submissionNo || `#${source.seq}`}와 선택한 유사 초록의 항목별 원문을 나란히 비교합니다.
                        </p>
                    </div>
                    <button
                        type="button"
                        onClick={onClose}
                        className="shrink-0 rounded-lg p-2 text-slate-400 transition-colors hover:bg-slate-100 dark:hover:bg-slate-900"
                        aria-label="유사 초록 상세 비교 닫기"
                    >
                        <X className="h-5 w-5" />
                    </button>
                </header>

                <div className="flex-1 overflow-y-auto bg-slate-50/70 p-4 dark:bg-slate-950 md:p-5">
                    {comparison?.stale && (
                        <div className="mb-4 rounded-xl border border-amber-200 bg-amber-50 p-3 dark:border-amber-900/60 dark:bg-amber-950/30">
                            <div className="flex items-start gap-2 text-amber-800 dark:text-amber-200">
                                <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
                                <p className="text-xs leading-5">
                                    분석 이후 현재 초록 또는 유사 초록의 내용이 변경되었습니다. 정확한 비교를 위해 전체 유사도 분석을 다시 실행해 주세요.
                                </p>
                            </div>
                        </div>
                    )}

                    <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-slate-950">
                        <div className="grid gap-4 xl:grid-cols-[minmax(18rem,1fr)_minmax(0,2fr)] xl:items-end">
                            <label className="block">
                                <span className="text-xs font-semibold text-slate-600 dark:text-slate-300">비교 대상 유사 초록</span>
                                <select
                                    value={selectedMatch.abstractSeq}
                                    onChange={(event) => setSelectedAbstractSeq(Number(event.target.value))}
                                    className="mt-2 h-10 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm font-medium text-slate-800 outline-none transition-colors focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100"
                                >
                                    {result.matches.map((match, index) => (
                                        <option key={match.abstractSeq} value={match.abstractSeq}>
                                            {index + 1}. {match.submissionNo || `#${match.abstractSeq}`} · {formatSimilarity(match.overallSimilarity)}
                                        </option>
                                    ))}
                                </select>
                            </label>

                            <dl className="grid grid-cols-2 overflow-hidden rounded-lg border border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900/40 sm:grid-cols-3 lg:grid-cols-6">
                                <ScoreMetric label="종합" value={score.overallSimilarity} emphasized />
                                <ScoreMetric label="제목" value={score.titleSimilarity} />
                                <ScoreMetric label="Objective" value={score.objectiveSimilarity} />
                                <ScoreMetric label="Methods" value={score.methodsSimilarity} />
                                <ScoreMetric label="Results" value={score.resultsSimilarity} />
                                <ScoreMetric label="Conclusions" value={score.conclusionsSimilarity} />
                            </dl>
                        </div>
                    </section>

                    {loading && <ComparisonSkeleton />}

                    {!loading && error && (
                        <div className="mt-4 rounded-xl border border-rose-200 bg-rose-50 p-4 dark:border-rose-900/60 dark:bg-rose-950/20">
                            <p className="text-sm font-semibold text-rose-700 dark:text-rose-300">상세 비교 정보를 불러오지 못했습니다.</p>
                            <p className="mt-1 text-xs leading-5 text-rose-600 dark:text-rose-300">{error}</p>
                            <button
                                type="button"
                                onClick={() => setRetryToken((value) => value + 1)}
                                className="mt-3 inline-flex items-center gap-1.5 rounded-lg border border-rose-300 px-3 py-2 text-xs font-semibold text-rose-700 transition-colors hover:bg-rose-100 dark:border-rose-800 dark:text-rose-300 dark:hover:bg-rose-950/50"
                            >
                                <RotateCw className="h-3.5 w-3.5" />
                                다시 불러오기
                            </button>
                        </div>
                    )}

                    {!loading && comparison && (
                        <div className="mt-4 space-y-4">
                            <div className="hidden grid-cols-2 gap-4 px-1 md:grid">
                                <ComparisonColumnHeading
                                    eyebrow="현재 초록"
                                    identifier={comparison.source.submissionNo || `#${comparison.source.abstractSeq}`}
                                    title={comparison.source.title}
                                    accent="blue"
                                />
                                <ComparisonColumnHeading
                                    eyebrow="유사 초록"
                                    identifier={comparison.target.submissionNo || `#${comparison.target.abstractSeq}`}
                                    title={comparison.target.title}
                                    accent="violet"
                                />
                            </div>

                            {sections.map((section) => (
                                <section key={section.key} className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-950">
                                    <div className="flex items-center justify-between gap-3 border-b border-slate-200 bg-slate-50/80 px-4 py-3 dark:border-slate-800 dark:bg-slate-900/40">
                                        <div className="flex items-center gap-2">
                                            <ScanSearch className="h-4 w-4 text-blue-500" />
                                            <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-50">{section.label}</h3>
                                        </div>
                                        <span className={`rounded-full border px-2.5 py-1 text-xs font-bold tabular-nums ${scoreTone(section.similarity)}`}>
                                            유사도 {formatSimilarity(section.similarity)}
                                        </span>
                                    </div>
                                    <div className="grid md:grid-cols-2 md:divide-x md:divide-slate-200 dark:md:divide-slate-800">
                                        <ComparisonText label="현재 초록" value={section.sourceText} accent="blue" />
                                        <ComparisonText label="유사 초록" value={section.targetText} accent="violet" />
                                    </div>
                                </section>
                            ))}
                        </div>
                    )}
                </div>

                <footer className="flex shrink-0 items-center justify-between gap-3 border-t border-slate-200 bg-white px-5 py-4 dark:border-slate-800 dark:bg-slate-950">
                    <p className="hidden items-center gap-1.5 text-xs text-slate-400 sm:flex">
                        <Eye className="h-4 w-4" />
                        점수와 실제 원문을 함께 확인해 최종 판단의 참고 자료로 사용합니다.
                    </p>
                    <button
                        type="button"
                        onClick={onClose}
                        className="ml-auto rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-600 transition-colors hover:bg-slate-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900"
                    >
                        닫기
                    </button>
                </footer>
            </DraggableModal>
        </div>
    );
};

const createSections = (comparison: AbstractSimilarityComparisonResponse): ComparisonSection[] => [
    {
        key: 'title',
        label: '제목',
        sourceText: formatText(comparison.source.title),
        targetText: formatText(comparison.target.title),
        similarity: comparison.similarity.titleSimilarity
    },
    {
        key: 'objective',
        label: 'Objective',
        sourceText: formatText(comparison.source.objectiveText),
        targetText: formatText(comparison.target.objectiveText),
        similarity: comparison.similarity.objectiveSimilarity
    },
    {
        key: 'methods',
        label: 'Methods',
        sourceText: formatText(comparison.source.methodsText),
        targetText: formatText(comparison.target.methodsText),
        similarity: comparison.similarity.methodsSimilarity
    },
    {
        key: 'results',
        label: 'Results',
        sourceText: formatText(comparison.source.resultsText),
        targetText: formatText(comparison.target.resultsText),
        similarity: comparison.similarity.resultsSimilarity
    },
    {
        key: 'conclusions',
        label: 'Conclusions',
        sourceText: formatText(comparison.source.conclusionsText),
        targetText: formatText(comparison.target.conclusionsText),
        similarity: comparison.similarity.conclusionsSimilarity
    }
];

const ComparisonSkeleton = () => (
    <div className="mt-4 animate-pulse space-y-4" aria-label="상세 비교 정보를 불러오는 중">
        <div className="grid gap-4 md:grid-cols-2">
            <div className="h-20 rounded-xl bg-slate-200/70 dark:bg-slate-800" />
            <div className="h-20 rounded-xl bg-slate-200/70 dark:bg-slate-800" />
        </div>
        {Array.from({ length: 4 }, (_, index) => (
            <div key={index} className="h-36 rounded-xl bg-slate-200/70 dark:bg-slate-800" />
        ))}
    </div>
);

const ScoreMetric = ({ label, value, emphasized = false }: { label: string; value?: number | null; emphasized?: boolean }) => (
    <div className={`border-b border-r border-slate-200 p-3 last:border-r-0 dark:border-slate-800 sm:border-b-0 ${emphasized ? 'bg-blue-50/70 dark:bg-blue-950/20' : ''}`}>
        <dt className="text-[11px] font-semibold text-slate-400">{label}</dt>
        <dd className={`mt-1 text-sm font-bold tabular-nums ${emphasized ? 'text-blue-600 dark:text-blue-400' : 'text-slate-800 dark:text-slate-200'}`}>
            {formatSimilarity(value)}
        </dd>
    </div>
);

const ComparisonColumnHeading = ({
    eyebrow,
    identifier,
    title,
    accent
}: {
    eyebrow: string;
    identifier: string;
    title: string;
    accent: 'blue' | 'violet';
}) => (
    <div className={`rounded-xl border p-3 ${accent === 'blue'
        ? 'border-blue-200 bg-blue-50/70 dark:border-blue-900/60 dark:bg-blue-950/20'
        : 'border-violet-200 bg-violet-50/70 dark:border-violet-900/60 dark:bg-violet-950/20'}`}>
        <p className={`text-[11px] font-bold ${accent === 'blue' ? 'text-blue-600 dark:text-blue-400' : 'text-violet-600 dark:text-violet-400'}`}>{eyebrow}</p>
        <p className="mt-1 text-xs font-semibold text-slate-500 dark:text-slate-400">{identifier}</p>
        <p className="mt-1 line-clamp-2 text-sm font-semibold leading-5 text-slate-900 dark:text-slate-50">{title}</p>
    </div>
);

const ComparisonText = ({ label, value, accent }: { label: string; value: string; accent: 'blue' | 'violet' }) => (
    <div className="border-b border-slate-200 p-4 last:border-b-0 dark:border-slate-800 md:border-b-0 md:p-5">
        <p className={`mb-3 text-[11px] font-bold md:hidden ${accent === 'blue' ? 'text-blue-600 dark:text-blue-400' : 'text-violet-600 dark:text-violet-400'}`}>{label}</p>
        <p className={`whitespace-pre-wrap border-l-2 pl-3 text-sm leading-7 text-slate-700 dark:text-slate-300 ${accent === 'blue' ? 'border-blue-400' : 'border-violet-400'}`}>
            {value}
        </p>
    </div>
);
