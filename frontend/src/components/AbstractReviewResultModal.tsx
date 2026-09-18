import { DraggableModal } from './DraggableModal';
import { useEffect, useRef, useState } from 'react';
import { BarChart3, CalendarClock, CheckCircle2, LockKeyhole, MessageSquareText, X } from 'lucide-react';
import type { AbstractSubmissionListItem } from './abstractTypes';
import type { NotificationType } from './NotificationToast';
import type {
    AbstractReviewResultData,
    AbstractReviewResultReviewer,
    ReviewAssignmentStatus,
    ReviewRecommendation
} from './abstractReviewResultTypes';

interface Props {
    abstractSubmission: AbstractSubmissionListItem | null;
    onClose: () => void;
    onNotify: (type: NotificationType, message: string) => void;
}

const RECOMMENDATION_LABEL: Record<ReviewRecommendation, string> = {
    accept: '추천',
    reject: '반려'
};

const ASSIGNMENT_STATUS_LABEL: Record<ReviewAssignmentStatus, string> = {
    assigned: '배정됨',
    accepted: '수락',
    in_review: '작성중',
    completed: '제출완료'
};

const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
});

const formatDate = (value?: string | null) => {
    if (!value) return '-';
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? '-' : dateFormatter.format(date);
};

const formatScore = (value?: number | null) => value == null ? '-' : `${value.toFixed(2)} / 6`;

export const AbstractReviewResultModal = ({ abstractSubmission, onClose, onNotify }: Props) => {
    const onNotifyRef = useRef(onNotify);
    const [data, setData] = useState<AbstractReviewResultData | null>(null);
    const [loading, setLoading] = useState(false);
    const [errorMessage, setErrorMessage] = useState('');

    useEffect(() => {
        onNotifyRef.current = onNotify;
    }, [onNotify]);

    useEffect(() => {
        if (!abstractSubmission) return;
        const controller = new AbortController();
        const load = async () => {
            setLoading(true);
            setData(null);
            setErrorMessage('');
            try {
                const response = await fetch(`/api/admin/abstracts/${abstractSubmission.seq}/review-results`, {
                    signal: controller.signal
                });
                if (!response.ok) {
                    throw new Error(await response.text() || '심사 결과를 불러오지 못했습니다.');
                }
                setData(await response.json() as AbstractReviewResultData);
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') return;
                const message = error instanceof Error ? error.message : '심사 결과를 불러오지 못했습니다.';
                setErrorMessage(message);
                onNotifyRef.current('error', message);
            } finally {
                setLoading(false);
            }
        };
        void load();
        return () => controller.abort();
    }, [abstractSubmission]);

    if (!abstractSubmission) return null;

    const recommendationText = data
        ? (Object.entries(data.recommendationCounts) as [ReviewRecommendation, number][])
            .filter(([, count]) => count > 0)
            .map(([recommendation, count]) => `${RECOMMENDATION_LABEL[recommendation]} ${count}`)
            .join(' · ') || '-'
        : '-';

    return (
        <div className="fixed inset-0 z-[85] flex items-center justify-center bg-slate-950/60 px-4 py-6 backdrop-blur-sm">
            <DraggableModal className="flex max-h-[calc(100vh-3rem)] w-full max-w-6xl flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl dark:border-slate-800 dark:bg-slate-950">
                <div data-modal-drag-handle className="cursor-move select-none touch-none flex items-start justify-between gap-4 border-b border-slate-200 px-5 py-4 dark:border-slate-800">
                    <div className="min-w-0">
                        <div className="flex items-center gap-2"><BarChart3 className="h-5 w-5 text-violet-500" /><h2 className="text-lg font-bold">심사 결과</h2></div>
                        <p className="mt-1 truncate text-xs text-slate-400">{data?.submissionNo || abstractSubmission.submissionNo} · {data?.title || abstractSubmission.title}</p>
                    </div>
                    <button type="button" onClick={onClose} className="rounded-lg p-2 text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-900" aria-label="닫기"><X className="h-5 w-5" /></button>
                </div>

                <div className="flex-1 overflow-y-auto p-4 md:p-5">
                    {loading && <p className="py-16 text-center text-sm text-slate-400">심사 결과를 불러오는 중입니다.</p>}
                    {errorMessage && <p className="py-16 text-center text-sm text-slate-500 dark:text-slate-400">심사 결과를 불러오지 못했습니다. 창을 다시 열어 시도해주세요.</p>}

                    {!loading && data && (
                        <div className="space-y-4">
                            <div className="grid grid-cols-2 gap-3 xl:grid-cols-4">
                                <SummaryCard label="배정 인원" value={`${data.assignedCount}명`} />
                                <SummaryCard label="제출 완료" value={`${data.completedCount} / ${data.assignedCount}명`} accent="text-emerald-600 dark:text-emerald-400" />
                                <SummaryCard label="전체 평균" value={formatScore(data.averageScore)} accent="text-blue-600 dark:text-blue-400" />
                                <SummaryCard label="추천 결과" value={recommendationText} accent="text-violet-600 dark:text-violet-400" small />
                            </div>

                            <section className="rounded-xl border border-slate-200 p-4 dark:border-slate-800">
                                <h3 className="flex items-center gap-2 text-sm font-semibold"><BarChart3 className="h-4 w-4 text-slate-400" />평가항목별 평균</h3>
                                {data.evaluationSummaries.length === 0 ? <p className="mt-4 text-sm text-slate-400">제출 완료된 평가점수가 없습니다.</p> : (
                                    <div className="mt-4 overflow-x-auto rounded-lg border border-slate-200 dark:border-slate-800">
                                        <table className="w-full min-w-[560px] text-left text-sm">
                                            <thead className="bg-slate-50 text-xs dark:bg-slate-900/60"><tr><th className="px-4 py-3">평가항목</th><th className="px-4 py-3 text-center">평가 인원</th><th className="px-4 py-3 text-center">평균점수</th></tr></thead>
                                            <tbody className="divide-y divide-slate-200 dark:divide-slate-800">{data.evaluationSummaries.map((item) => <tr key={item.evaluationItemSeq}><td className="px-4 py-3 font-medium">{item.itemName}</td><td className="px-4 py-3 text-center text-slate-500">{item.reviewerCount}명</td><td className="px-4 py-3 text-center font-bold text-blue-600 dark:text-blue-400">{formatScore(item.averageScore)}</td></tr>)}</tbody>
                                        </table>
                                    </div>
                                )}
                            </section>

                            <section className="space-y-3">
                                <h3 className="flex items-center gap-2 text-sm font-semibold"><MessageSquareText className="h-4 w-4 text-slate-400" />심사자별 결과</h3>
                                {data.reviews.length === 0 ? <div className="rounded-xl border border-dashed border-slate-300 p-8 text-center text-sm text-slate-400 dark:border-slate-700">배정된 심사자가 없습니다.</div> : data.reviews.map((review) => <ReviewerResultCard key={review.assignmentSeq} review={review} />)}
                            </section>
                        </div>
                    )}
                </div>

                <div className="flex justify-end border-t border-slate-200 px-5 py-4 dark:border-slate-800"><button type="button" onClick={onClose} className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-600 hover:bg-slate-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900">닫기</button></div>
            </DraggableModal>
        </div>
    );
};

const SummaryCard = ({ label, value, accent = '', small = false }: { label: string; value: string; accent?: string; small?: boolean }) => <div className="rounded-xl border border-slate-200 p-4 dark:border-slate-800"><p className="text-xs font-semibold text-slate-400">{label}</p><p className={`mt-2 font-bold ${small ? 'text-sm' : 'text-xl'} ${accent}`}>{value}</p></div>;

const ReviewerResultCard = ({ review }: { review: AbstractReviewResultReviewer }) => {
    const submitted = review.reviewStatus === 'submitted';
    return (
        <article className="rounded-xl border border-slate-200 p-4 dark:border-slate-800">
            <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-start">
                <div><p className="font-semibold text-slate-900 dark:text-slate-50">{review.reviewerName}</p><p className="mt-1 text-xs text-slate-400">{[review.affiliation, review.department].filter(Boolean).join(' · ') || '-'}</p></div>
                <div className="flex flex-wrap items-center gap-2 text-xs"><span className={`rounded-full px-2.5 py-1 font-semibold ${submitted ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300' : 'bg-amber-50 text-amber-700 dark:bg-amber-950/40 dark:text-amber-300'}`}>{submitted ? '제출완료' : ASSIGNMENT_STATUS_LABEL[review.assignmentStatus]}</span>{submitted && review.recommendation && <span className="rounded-full bg-violet-50 px-2.5 py-1 font-semibold text-violet-700 dark:bg-violet-950/40 dark:text-violet-300">{RECOMMENDATION_LABEL[review.recommendation]}</span>}</div>
            </div>
            <div className="mt-3 flex flex-wrap gap-x-5 gap-y-1 text-xs text-slate-400"><span className="inline-flex items-center gap-1"><CalendarClock className="h-3.5 w-3.5" />마감 {formatDate(review.dueAt)}</span>{submitted && <span className="inline-flex items-center gap-1"><CheckCircle2 className="h-3.5 w-3.5" />제출 {formatDate(review.submittedAt)}</span>}</div>
            {!submitted ? <div className="mt-4 rounded-lg bg-slate-50 px-3 py-3 text-sm text-slate-500 dark:bg-slate-900/50 dark:text-slate-400">심사자가 결과를 제출하면 점수와 의견이 표시됩니다.</div> : (
                <div className="mt-4 space-y-4">
                    <div className="overflow-x-auto rounded-lg border border-slate-200 dark:border-slate-800"><table className="w-full min-w-[620px] text-left text-xs"><thead className="bg-slate-50 dark:bg-slate-900/60"><tr><th className="px-3 py-2">평가항목</th><th className="px-3 py-2 text-center">점수</th><th className="px-3 py-2">항목 의견</th></tr></thead><tbody className="divide-y divide-slate-200 dark:divide-slate-800">{review.scores.map((score) => <tr key={score.evaluationItemSeq}><td className="px-3 py-2 font-medium">{score.itemName}</td><td className="px-3 py-2 text-center font-bold text-blue-600 dark:text-blue-400">{score.score} / 6</td><td className="px-3 py-2 whitespace-pre-wrap text-slate-500 dark:text-slate-400">{score.itemComment || '-'}</td></tr>)}</tbody></table></div>
                    <p className="text-right text-sm font-bold text-blue-600 dark:text-blue-400">심사자 평균 {formatScore(review.averageScore)}</p>
                    <div className="grid grid-cols-1 gap-3 lg:grid-cols-2"><div className="rounded-lg border border-slate-200 p-3 dark:border-slate-800"><p className="text-xs font-semibold text-slate-500">종합의견</p><p className="mt-2 whitespace-pre-wrap text-sm text-slate-700 dark:text-slate-300">{review.overallComment || '-'}</p></div><div className="rounded-lg border border-amber-200 bg-amber-50/50 p-3 dark:border-amber-900/60 dark:bg-amber-950/20"><p className="flex items-center gap-1.5 text-xs font-semibold text-amber-700 dark:text-amber-300"><LockKeyhole className="h-3.5 w-3.5" />관리자 전용 비공개 의견</p><p className="mt-2 whitespace-pre-wrap text-sm text-slate-700 dark:text-slate-300">{review.confidentialComment || '-'}</p></div></div>
                </div>
            )}
        </article>
    );
};
