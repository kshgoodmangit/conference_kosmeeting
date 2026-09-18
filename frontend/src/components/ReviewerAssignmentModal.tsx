import { DraggableModal } from './DraggableModal';
import { useEffect, useMemo, useRef, useState } from 'react';
import { CalendarClock, CheckCircle2, Save, Search, UserCheck, X } from 'lucide-react';
import type {
    AbstractReviewAssignmentData,
    AbstractSubmissionListItem,
    ReviewerAssignmentCandidate
} from './abstractTypes';
import type { NotificationType } from './NotificationToast';
import { useConfirm } from './confirmDialogContext';

interface Props {
    isOpen: boolean;
    abstractSubmission: AbstractSubmissionListItem | null;
    onClose: () => void;
    onSuccess: () => void;
    onNotify: (type: NotificationType, message: string) => void;
}

const ASSIGNED_STATUSES = new Set(['assigned', 'accepted', 'in_review', 'completed']);

const ASSIGNMENT_STATUS_LABEL: Record<NonNullable<ReviewerAssignmentCandidate['assignmentStatus']>, string> = {
    assigned: '배정',
    accepted: '수락',
    in_review: '심사중',
    completed: '완료',
    declined: '거절',
    cancelled: '취소'
};

const toDateTimeInputValue = (value?: string | null) => value ? value.slice(0, 16) : '';

const isAssigned = (reviewer: ReviewerAssignmentCandidate) => (
    reviewer.assignmentStatus != null && ASSIGNED_STATUSES.has(reviewer.assignmentStatus)
);

export const ReviewerAssignmentModal = ({
    isOpen,
    abstractSubmission,
    onClose,
    onSuccess,
    onNotify
}: Props) => {
    const confirm = useConfirm();
    const onNotifyRef = useRef(onNotify);
    const [data, setData] = useState<AbstractReviewAssignmentData | null>(null);
    const [selectedReviewerSeqs, setSelectedReviewerSeqs] = useState<Set<number>>(new Set());
    const [initialReviewerSeqs, setInitialReviewerSeqs] = useState<Set<number>>(new Set());
    const [dueAt, setDueAt] = useState('');
    const [keyword, setKeyword] = useState('');
    const [loading, setLoading] = useState(false);
    const [submitting, setSubmitting] = useState(false);

    useEffect(() => {
        onNotifyRef.current = onNotify;
    }, [onNotify]);

    useEffect(() => {
        if (!isOpen || !abstractSubmission) {
            return;
        }

        const controller = new AbortController();
        const load = async () => {
            setLoading(true);
            setKeyword('');
            try {
                const response = await fetch(`/api/admin/abstracts/${abstractSubmission.seq}/review-assignments`, {
                    signal: controller.signal
                });
                if (!response.ok) {
                    throw new Error(await response.text() || '심사자 정보를 불러오지 못했습니다.');
                }
                const nextData = await response.json() as AbstractReviewAssignmentData;
                const assigned = new Set(nextData.reviewers.filter(isAssigned).map((reviewer) => reviewer.reviewerSeq));
                setData(nextData);
                setSelectedReviewerSeqs(assigned);
                setInitialReviewerSeqs(new Set(assigned));
                setDueAt(toDateTimeInputValue(nextData.dueAt));
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') {
                    return;
                }
                const message = error instanceof Error ? error.message : '심사자 정보를 불러오지 못했습니다.';
                onNotifyRef.current('error', message);
            } finally {
                setLoading(false);
            }
        };

        void load();
        return () => controller.abort();
    }, [abstractSubmission, isOpen]);

    const filteredReviewers = useMemo(() => {
        const normalizedKeyword = keyword.trim().toLowerCase();
        if (!normalizedKeyword) {
            return data?.reviewers ?? [];
        }
        return (data?.reviewers ?? []).filter((reviewer) => (
            [
                reviewer.reviewerName,
                reviewer.affiliation,
                reviewer.department,
                reviewer.positionTitle,
                reviewer.contactEmail,
                reviewer.expertiseNames
            ].some((value) => value?.toLowerCase().includes(normalizedKeyword))
        ));
    }, [data, keyword]);

    const toggleReviewer = (reviewer: ReviewerAssignmentCandidate) => {
        if (reviewer.assignmentStatus === 'completed') {
            return;
        }
        setSelectedReviewerSeqs((previous) => {
            const next = new Set(previous);
            if (next.has(reviewer.reviewerSeq)) {
                next.delete(reviewer.reviewerSeq);
            } else {
                next.add(reviewer.reviewerSeq);
            }
            return next;
        });
    };

    const save = async () => {
        if (!abstractSubmission) {
            return;
        }

        const removedCount = Array.from(initialReviewerSeqs)
            .filter((reviewerSeq) => !selectedReviewerSeqs.has(reviewerSeq))
            .length;
        if (removedCount > 0 && !await confirm({
            title: '심사자 배정 변경',
            message: `기존 심사자 ${removedCount}명의 배정을 취소하고 변경사항을 저장하시겠습니까?`,
            confirmText: '변경 저장',
            tone: 'danger'
        })) {
            return;
        }

        setSubmitting(true);
        try {
            const response = await fetch(`/api/admin/abstracts/${abstractSubmission.seq}/review-assignments`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    reviewerSeqs: Array.from(selectedReviewerSeqs),
                    dueAt: dueAt ? `${dueAt}:00` : null
                })
            });
            if (!response.ok) {
                throw new Error(await response.text() || '심사자 할당 저장에 실패했습니다.');
            }
            onNotifyRef.current('success', `심사자 ${selectedReviewerSeqs.size}명을 할당했습니다.`);
            onSuccess();
        } catch (error) {
            const message = error instanceof Error ? error.message : '심사자 할당 저장에 실패했습니다.';
            onNotifyRef.current('error', message);
        } finally {
            setSubmitting(false);
        }
    };

    if (!isOpen || !abstractSubmission) {
        return null;
    }

    return (
        <div className="fixed inset-0 z-[80] flex items-center justify-center bg-slate-950/60 px-4 py-6 backdrop-blur-sm">
            <DraggableModal className="flex max-h-[calc(100vh-3rem)] w-full max-w-5xl flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl dark:border-slate-800 dark:bg-slate-950">
                <div data-modal-drag-handle className="cursor-move select-none touch-none flex items-start justify-between gap-4 border-b border-slate-200 px-5 py-4 dark:border-slate-800">
                    <div>
                        <div className="flex items-center gap-2">
                            <UserCheck className="h-5 w-5 text-violet-500" />
                            <h2 className="text-lg font-bold">심사자 할당</h2>
                        </div>
                        <p className="mt-1 text-xs text-slate-400">{data?.submissionNo || abstractSubmission.submissionNo} · {data?.title || abstractSubmission.title}</p>
                    </div>
                    <button type="button" onClick={onClose} disabled={submitting} className="rounded-lg p-2 text-slate-400 hover:bg-slate-100 disabled:opacity-50 dark:hover:bg-slate-900" aria-label="닫기">
                        <X className="h-5 w-5" />
                    </button>
                </div>

                <div className="border-b border-slate-200 p-4 dark:border-slate-800 md:p-5">
                    <div className="grid grid-cols-1 gap-3 md:grid-cols-[1fr_260px]">
                        <div className="relative">
                            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                            <input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="심사자, 소속, 전문분야 검색" className="w-full rounded-lg border border-slate-200 bg-white py-2 pl-9 pr-3 text-sm text-slate-900 focus:border-violet-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50" />
                        </div>
                        <label className="relative">
                            <CalendarClock className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                            <input type="datetime-local" value={dueAt} onChange={(event) => setDueAt(event.target.value)} className="w-full rounded-lg border border-slate-200 bg-white py-2 pl-9 pr-3 text-sm text-slate-900 focus:border-violet-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50" aria-label="심사 마감일시" />
                        </label>
                    </div>
                    <div className="mt-3 flex flex-wrap items-center justify-between gap-2 text-xs">
                        <span className="text-slate-500 dark:text-slate-400">초록 분류: <strong className="text-slate-700 dark:text-slate-200">{data?.categoryName || abstractSubmission.categoryName || '-'}</strong></span>
                        <span className="rounded-full bg-violet-50 px-3 py-1 font-semibold text-violet-700 dark:bg-violet-950/40 dark:text-violet-300">선택 {selectedReviewerSeqs.size}명</span>
                    </div>
                </div>

                <div className="flex-1 overflow-y-auto p-4 md:p-5">
                    {loading && <p className="py-12 text-center text-sm text-slate-400">심사자 정보를 불러오는 중입니다.</p>}
                    {!loading && filteredReviewers.length === 0 && <p className="py-12 text-center text-sm text-slate-400">할당 가능한 심사자가 없습니다.</p>}
                    {!loading && filteredReviewers.length > 0 && (
                        <div className="overflow-x-auto rounded-xl border border-slate-200 dark:border-slate-800">
                            <table className="min-w-[850px] w-full text-left text-sm">
                                <thead className="border-b border-slate-200 bg-slate-50 text-xs dark:border-slate-800 dark:bg-slate-900/60">
                                    <tr><th className="w-14 px-4 py-3 text-center">선택</th><th className="px-4 py-3">심사자</th><th className="px-4 py-3">소속</th><th className="px-4 py-3">전문분야</th><th className="px-4 py-3 text-center">진행 건수</th><th className="px-4 py-3 text-center">상태</th></tr>
                                </thead>
                                <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                                    {filteredReviewers.map((reviewer) => {
                                        const selected = selectedReviewerSeqs.has(reviewer.reviewerSeq);
                                        const completed = reviewer.assignmentStatus === 'completed';
                                        return (
                                            <tr key={reviewer.reviewerSeq} onClick={() => toggleReviewer(reviewer)} className={`${completed ? 'cursor-default' : 'cursor-pointer'} ${selected ? 'bg-violet-50/70 dark:bg-violet-950/20' : 'hover:bg-slate-50 dark:hover:bg-slate-900/40'}`}>
                                                <td className="px-4 py-3 text-center"><input type="checkbox" checked={selected} onChange={() => toggleReviewer(reviewer)} onClick={(event) => event.stopPropagation()} disabled={completed} className="h-4 w-4 rounded border-slate-300 text-violet-600 focus:ring-violet-500 disabled:opacity-60" /></td>
                                                <td className="px-4 py-3"><p className="font-semibold text-slate-900 dark:text-slate-50">{reviewer.reviewerName}</p><p className="mt-0.5 text-xs text-slate-400">{reviewer.contactEmail || '-'}</p></td>
                                                <td className="px-4 py-3 text-slate-600 dark:text-slate-300"><p>{reviewer.affiliation}</p><p className="mt-0.5 text-xs text-slate-400">{[reviewer.department, reviewer.positionTitle].filter(Boolean).join(' · ') || '-'}</p></td>
                                                <td className="px-4 py-3"><p className="text-slate-600 dark:text-slate-300">{reviewer.expertiseNames || '미등록'}</p>{reviewer.expertiseMatched && <span className="mt-1 inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2 py-0.5 text-[11px] font-semibold text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300"><CheckCircle2 className="h-3 w-3" />분류 일치</span>}</td>
                                                <td className="px-4 py-3 text-center font-semibold text-slate-600 dark:text-slate-300">{reviewer.activeAssignmentCount ?? 0}</td>
                                                <td className="px-4 py-3 text-center text-xs font-semibold text-slate-500 dark:text-slate-400">{reviewer.assignmentStatus ? ASSIGNMENT_STATUS_LABEL[reviewer.assignmentStatus] : '미배정'}</td>
                                            </tr>
                                        );
                                    })}
                                </tbody>
                            </table>
                        </div>
                    )}
                </div>

                <div className="flex items-center justify-end gap-2 border-t border-slate-200 px-5 py-4 dark:border-slate-800">
                    <button type="button" onClick={onClose} disabled={submitting} className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900">취소</button>
                    <button type="button" onClick={() => void save()} disabled={loading || submitting || !data} className="inline-flex items-center gap-2 rounded-lg bg-violet-600 px-4 py-2 text-sm font-semibold text-white hover:bg-violet-700 disabled:opacity-50"><Save className="h-4 w-4" />{submitting ? '저장 중' : '할당 저장'}</button>
                </div>
            </DraggableModal>
        </div>
    );
};
