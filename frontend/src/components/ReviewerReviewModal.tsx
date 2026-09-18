import { DraggableModal } from './DraggableModal';
import { useEffect, useMemo, useRef, useState } from 'react';
import { CheckCircle2, FileText, Save, Send, X } from 'lucide-react';
import type { NotificationType } from './NotificationToast';
import { useConfirm } from './confirmDialogContext';
import type {
    ReviewRecommendation,
    ReviewerReviewDetail,
    ReviewerReviewEvaluationItem
} from './reviewerReviewTypes';

interface Props {
    assignmentSeq: number | null;
    onClose: () => void;
    onSaved: (submitted: boolean) => void;
    onNotify: (type: NotificationType, message: string) => void;
}

interface ScoreForm {
    score: string;
    itemComment: string;
}

const RECOMMENDATIONS: { value: ReviewRecommendation; label: string }[] = [
    { value: 'accept', label: '추천' },
    { value: 'reject', label: '반려' }
];

const guideForScore = (item: ReviewerReviewEvaluationItem, score: string) => {
    if (!score) return null;
    return item[`score${score}Guide` as keyof ReviewerReviewEvaluationItem] as string | null | undefined;
};

export const ReviewerReviewModal = ({ assignmentSeq, onClose, onSaved, onNotify }: Props) => {
    const confirm = useConfirm();
    const onNotifyRef = useRef(onNotify);
    const [detail, setDetail] = useState<ReviewerReviewDetail | null>(null);
    const [scores, setScores] = useState<Record<number, ScoreForm>>({});
    const [recommendation, setRecommendation] = useState<ReviewRecommendation | ''>('');
    const [overallComment, setOverallComment] = useState('');
    const [confidentialComment, setConfidentialComment] = useState('');
    const [loading, setLoading] = useState(false);
    const [submitting, setSubmitting] = useState(false);

    useEffect(() => {
        onNotifyRef.current = onNotify;
    }, [onNotify]);

    useEffect(() => {
        if (assignmentSeq == null) return;
        const controller = new AbortController();
        const load = async () => {
            setLoading(true);
            try {
                const response = await fetch(`/api/reviewer/reviews/${assignmentSeq}`, { signal: controller.signal });
                if (!response.ok) throw new Error(await response.text() || '심사 정보를 불러오지 못했습니다.');
                const nextDetail = await response.json() as ReviewerReviewDetail;
                setDetail(nextDetail);
                setRecommendation(nextDetail.recommendation ?? '');
                setOverallComment(nextDetail.overallComment ?? '');
                setConfidentialComment(nextDetail.confidentialComment ?? '');
                setScores(Object.fromEntries(nextDetail.evaluationItems.map((item) => [
                    item.evaluationItemSeq,
                    { score: item.score ? String(item.score) : '', itemComment: item.itemComment ?? '' }
                ])));
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') return;
                const message = error instanceof Error ? error.message : '심사 정보를 불러오지 못했습니다.';
                onNotifyRef.current('error', message);
            } finally {
                setLoading(false);
            }
        };
        void load();
        return () => controller.abort();
    }, [assignmentSeq]);

    const readOnly = detail?.assignmentStatus === 'completed' || detail?.reviewStatus === 'submitted';
    const scoreCount = useMemo(() => Object.values(scores).filter((score) => score.score).length, [scores]);

    const updateScore = (evaluationItemSeq: number, patch: Partial<ScoreForm>) => {
        setScores((previous) => ({
            ...previous,
            [evaluationItemSeq]: { ...(previous[evaluationItemSeq] ?? { score: '', itemComment: '' }), ...patch }
        }));
    };

    const save = async (submit: boolean) => {
        if (assignmentSeq == null || !detail || readOnly) return;
        if (submit) {
            if (detail.evaluationItems.some((item) => !scores[item.evaluationItemSeq]?.score)) {
                onNotifyRef.current('error', '모든 평가항목의 점수를 입력하세요.');
                return;
            }
            if (!recommendation) {
                onNotifyRef.current('error', '최종 추천을 선택하세요.');
                return;
            }
            if (!overallComment.trim()) {
                onNotifyRef.current('error', '종합의견을 입력하세요.');
                return;
            }
            if (!await confirm({
                title: '심사 최종 제출',
                message: '심사를 제출하면 더 이상 수정할 수 없습니다. 최종 제출하시겠습니까?',
                confirmText: '심사 제출'
            })) return;
        }

        setSubmitting(true);
        try {
            const response = await fetch(`/api/reviewer/reviews/${assignmentSeq}/${submit ? 'submit' : 'draft'}`, {
                method: submit ? 'POST' : 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    recommendation: recommendation || null,
                    overallComment: overallComment.trim() || null,
                    confidentialComment: confidentialComment.trim() || null,
                    scores: detail.evaluationItems.map((item) => ({
                        evaluationItemSeq: item.evaluationItemSeq,
                        score: scores[item.evaluationItemSeq]?.score
                            ? Number.parseInt(scores[item.evaluationItemSeq].score, 10)
                            : null,
                        itemComment: scores[item.evaluationItemSeq]?.itemComment.trim() || null
                    }))
                })
            });
            if (!response.ok) throw new Error(await response.text() || (submit ? '심사 제출에 실패했습니다.' : '임시저장에 실패했습니다.'));
            const nextDetail = await response.json() as ReviewerReviewDetail;
            setDetail(nextDetail);
            setRecommendation(nextDetail.recommendation ?? '');
            setOverallComment(nextDetail.overallComment ?? '');
            setConfidentialComment(nextDetail.confidentialComment ?? '');
            setScores(Object.fromEntries(nextDetail.evaluationItems.map((item) => [
                item.evaluationItemSeq,
                { score: item.score ? String(item.score) : '', itemComment: item.itemComment ?? '' }
            ])));
            onNotifyRef.current('success', submit ? '심사를 최종 제출했습니다.' : '심사 내용을 임시저장했습니다.');
            onSaved(submit);
        } catch (error) {
            const message = error instanceof Error ? error.message : (submit ? '심사 제출에 실패했습니다.' : '임시저장에 실패했습니다.');
            onNotifyRef.current('error', message);
        } finally {
            setSubmitting(false);
        }
    };

    if (assignmentSeq == null) return null;

    return (
        <div className="fixed inset-0 z-[80] flex items-center justify-center bg-slate-950/60 px-4 py-6 backdrop-blur-sm">
            <DraggableModal className="flex max-h-[calc(100vh-3rem)] w-full max-w-6xl flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl dark:border-slate-800 dark:bg-slate-950">
                <div data-modal-drag-handle className="cursor-move select-none touch-none flex items-start justify-between gap-4 border-b border-slate-200 px-5 py-4 dark:border-slate-800">
                    <div><div className="flex items-center gap-2"><FileText className="h-5 w-5 text-violet-500" /><h2 className="text-lg font-bold">초록 심사</h2></div><p className="mt-1 text-xs text-slate-400">평가점수와 심사의견을 작성해 주세요.</p></div>
                    <button type="button" onClick={onClose} disabled={submitting} className="rounded-lg p-2 text-slate-400 hover:bg-slate-100 disabled:opacity-50 dark:hover:bg-slate-900" aria-label="닫기"><X className="h-5 w-5" /></button>
                </div>

                {loading && <div className="flex-1 p-12 text-center text-sm text-slate-400">심사 정보를 불러오는 중입니다.</div>}
                {!loading && detail && (
                    <div className="flex-1 overflow-y-auto p-4 md:p-5">
                        {readOnly && <div className="mb-4 flex items-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm font-semibold text-emerald-700 dark:border-emerald-900/60 dark:bg-emerald-950/30 dark:text-emerald-300"><CheckCircle2 className="h-4 w-4" />제출 완료된 심사입니다.</div>}

                        <AbstractContent detail={detail} />

                        <div className="mt-5 space-y-4">
                            <div className="flex items-center justify-between"><h3 className="text-base font-bold">평가항목</h3><span className="text-xs font-semibold text-violet-600 dark:text-violet-400">입력 {scoreCount} / {detail.evaluationItems.length}</span></div>
                            {detail.evaluationItems.map((item) => {
                                const form = scores[item.evaluationItemSeq] ?? { score: '', itemComment: '' };
                                const guide = guideForScore(item, form.score);
                                return (
                                    <div key={item.evaluationItemSeq} className="rounded-xl border border-slate-200 p-4 dark:border-slate-800">
                                        <h4 className="font-semibold text-slate-900 dark:text-slate-50">{item.itemName}</h4>
                                        {item.description && <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{item.description}</p>}
                                        <div className="mt-3 grid grid-cols-3 gap-2 sm:grid-cols-6">
                                            {[1, 2, 3, 4, 5, 6].map((score) => <label key={score} className={`flex cursor-pointer items-center justify-center gap-2 rounded-lg border px-3 py-2 text-sm font-semibold ${form.score === String(score) ? 'border-violet-500 bg-violet-50 text-violet-700 dark:bg-violet-950/30 dark:text-violet-300' : 'border-slate-200 text-slate-600 dark:border-slate-800 dark:text-slate-300'} ${readOnly ? 'cursor-default opacity-80' : ''}`}><input type="radio" name={`score-${item.evaluationItemSeq}`} value={score} checked={form.score === String(score)} onChange={(event) => updateScore(item.evaluationItemSeq, { score: event.target.value })} disabled={readOnly} className="sr-only" />{score}점</label>)}
                                        </div>
                                        {guide && <p className="mt-2 rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-500 dark:bg-slate-900 dark:text-slate-400">{guide}</p>}
                                        <textarea value={form.itemComment} onChange={(event) => updateScore(item.evaluationItemSeq, { itemComment: event.target.value })} disabled={readOnly} rows={2} maxLength={1000} placeholder="항목별 의견 (선택)" className="mt-3 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-violet-500 focus:outline-none disabled:bg-slate-50 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50 dark:disabled:bg-slate-900/50" />
                                    </div>
                                );
                            })}
                            {detail.evaluationItems.length === 0 && <p className="rounded-xl border border-dashed border-slate-300 p-8 text-center text-sm text-slate-400 dark:border-slate-700">등록된 평가항목이 없습니다.</p>}
                        </div>

                        <div className="mt-5 rounded-xl border border-slate-200 p-4 dark:border-slate-800">
                            <h3 className="text-base font-bold">종합 평가</h3>
                            <div className="mt-4 grid grid-cols-1 gap-4 md:grid-cols-2">
                                <label className="space-y-1.5"><span className="text-xs font-semibold uppercase text-slate-500">최종 추천 *</span><select value={recommendation} onChange={(event) => setRecommendation(event.target.value as ReviewRecommendation | '')} disabled={readOnly} className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-violet-500 focus:outline-none disabled:bg-slate-50 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50"><option value="">선택</option>{RECOMMENDATIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select></label>
                                <div className="hidden md:block" />
                                <label className="space-y-1.5 md:col-span-2"><span className="text-xs font-semibold uppercase text-slate-500">종합의견 *</span><textarea value={overallComment} onChange={(event) => setOverallComment(event.target.value)} disabled={readOnly} rows={5} maxLength={10000} placeholder="저자에게 전달할 종합의견을 입력하세요." className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-violet-500 focus:outline-none disabled:bg-slate-50 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50" /></label>
                                <label className="space-y-1.5 md:col-span-2"><span className="text-xs font-semibold uppercase text-slate-500">관리자 전용 비공개 의견</span><textarea value={confidentialComment} onChange={(event) => setConfidentialComment(event.target.value)} disabled={readOnly} rows={3} maxLength={10000} placeholder="관리자에게만 전달할 의견이 있으면 입력하세요." className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-violet-500 focus:outline-none disabled:bg-slate-50 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50" /></label>
                            </div>
                        </div>
                    </div>
                )}

                <div className="flex flex-wrap items-center justify-end gap-2 border-t border-slate-200 px-5 py-4 dark:border-slate-800">
                    <button type="button" onClick={onClose} disabled={submitting} className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900">닫기</button>
                    {!readOnly && detail && <><button type="button" onClick={() => void save(false)} disabled={submitting} className="inline-flex items-center gap-2 rounded-lg border border-violet-200 px-4 py-2 text-sm font-semibold text-violet-700 hover:bg-violet-50 disabled:opacity-50 dark:border-violet-900/60 dark:text-violet-300 dark:hover:bg-violet-950/30"><Save className="h-4 w-4" />{submitting ? '저장 중' : '임시저장'}</button><button type="button" onClick={() => void save(true)} disabled={submitting || detail.evaluationItems.length === 0} className="inline-flex items-center gap-2 rounded-lg bg-violet-600 px-4 py-2 text-sm font-semibold text-white hover:bg-violet-700 disabled:opacity-50"><Send className="h-4 w-4" />심사 제출</button></>}
                </div>
            </DraggableModal>
        </div>
    );
};

const AbstractContent = ({ detail }: { detail: ReviewerReviewDetail }) => {
    const abstractSubmission = detail.abstractSubmission;
    return (
        <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-900/40">
            <div className="flex flex-wrap items-center gap-2 text-xs text-slate-500"><span className="font-mono">{abstractSubmission.submissionNo}</span><span>·</span><span>{abstractSubmission.categoryName}</span><span>·</span><span>{abstractSubmission.presentationTypeName}</span></div>
            <h3 className="mt-2 text-lg font-bold text-slate-900 dark:text-slate-50">{abstractSubmission.title}</h3>
            {detail.showAuthorInformation && (
                <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">저자: {abstractSubmission.authors.map((author) => author.authorName).join(', ') || '-'}</p>
            )}
            <div className="mt-4 grid grid-cols-1 gap-4 md:grid-cols-2">
                <TextSection title="Objective" value={abstractSubmission.objectiveText} />
                <TextSection title="Methods" value={abstractSubmission.methodsText} />
                <TextSection title="Results" value={abstractSubmission.resultsText} />
                <TextSection title="Conclusions" value={abstractSubmission.conclusionsText} />
            </div>
        </div>
    );
};

const TextSection = ({ title, value }: { title: string; value?: string | null }) => (
    <div><h4 className="text-xs font-bold uppercase tracking-wide text-slate-500">{title}</h4><p className="mt-1 whitespace-pre-wrap text-sm leading-6 text-slate-700 dark:text-slate-300">{value || '-'}</p></div>
);
