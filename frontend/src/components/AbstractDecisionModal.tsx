import { DraggableModal } from './DraggableModal';
import { useState, type FormEvent } from 'react';
import { AlertTriangle, CheckCircle2, X, XCircle } from 'lucide-react';
import type { AbstractPresentationType, AbstractSubmissionListItem } from './abstractTypes';
import type { NotificationType } from './NotificationToast';
import { useConfirm } from './confirmDialogContext';

interface Props {
    abstractSubmission: AbstractSubmissionListItem | null;
    presentationTypes: AbstractPresentationType[];
    onClose: () => void;
    onSuccess: () => void;
    onNotify: (type: NotificationType, message: string) => void;
}

type Decision = 'approved' | 'rejected';

export const AbstractDecisionModal = ({ abstractSubmission, presentationTypes, onClose, onSuccess, onNotify }: Props) => {
    const confirm = useConfirm();
    const [decision, setDecision] = useState<Decision>('approved');
    const [acceptedPresentationTypeCode, setAcceptedPresentationTypeCode] = useState(
        abstractSubmission ? String(abstractSubmission.presentationTypeCode) : ''
    );
    const [reason, setReason] = useState('');
    const [submitting, setSubmitting] = useState(false);

    const assignedReviewCount = abstractSubmission?.reviewerAssignmentCount ?? 0;
    const completedReviewCount = abstractSubmission?.completedReviewCount ?? 0;
    const reviewCompleted = assignedReviewCount >= 2 && completedReviewCount === assignedReviewCount;

    if (!abstractSubmission) return null;

    const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (decision === 'approved' && !acceptedPresentationTypeCode) {
            onNotify('error', '승인할 발표형식을 선택해 주세요.');
            return;
        }
        if (!reviewCompleted && !reason.trim()) {
            onNotify('error', '강제 결정 사유를 입력해 주세요.');
            return;
        }

        const confirmed = await confirm({
            title: reviewCompleted ? '초록 최종 결정' : '초록 강제 결정',
            message: reviewCompleted
                ? `이 초록을 ${decision === 'approved' ? '승인' : '반려'}하시겠습니까?`
                : `심사가 완료되지 않았습니다. 사유를 기록하고 강제로 ${decision === 'approved' ? '승인' : '반려'}하시겠습니까?`,
            confirmText: decision === 'approved' ? '승인' : '반려',
            tone: decision === 'rejected' || !reviewCompleted ? 'danger' : undefined
        });
        if (!confirmed) return;

        setSubmitting(true);
        try {
            const response = await fetch(`/api/admin/abstracts/${abstractSubmission.seq}/decision`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    decision,
                    acceptedPresentationTypeCode: decision === 'approved' ? Number(acceptedPresentationTypeCode) : null,
                    reason: reason.trim() || null
                })
            });
            if (!response.ok) throw new Error(await response.text() || '최종 결정을 저장하지 못했습니다.');
            onNotify('success', reviewCompleted ? '최종 결정을 저장했습니다.' : '강제 최종 결정을 저장했습니다.');
            onSuccess();
        } catch (error) {
            onNotify('error', error instanceof Error ? error.message : '최종 결정을 저장하지 못했습니다.');
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <div className="fixed inset-0 z-[85] flex items-center justify-center bg-slate-950/60 px-4 py-6 backdrop-blur-sm">
            <DraggableModal className="w-full max-w-xl overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl dark:border-slate-800 dark:bg-slate-950">
                <div data-modal-drag-handle className="cursor-move select-none touch-none flex items-start justify-between border-b border-slate-200 px-5 py-4 dark:border-slate-800">
                    <div>
                        <h2 className="text-lg font-bold">초록 최종 결정</h2>
                        <p className="mt-1 text-xs text-slate-400">{abstractSubmission.submissionNo} · {abstractSubmission.title}</p>
                    </div>
                    <button type="button" onClick={onClose} className="rounded-lg p-2 text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-900" aria-label="닫기"><X className="h-5 w-5" /></button>
                </div>

                <form onSubmit={handleSubmit} className="space-y-5 p-5">
                    <div className={`rounded-xl border p-4 ${reviewCompleted ? 'border-emerald-200 bg-emerald-50 dark:border-emerald-900/60 dark:bg-emerald-950/30' : 'border-amber-200 bg-amber-50 dark:border-amber-900/60 dark:bg-amber-950/30'}`}>
                        <div className="flex items-start gap-3">
                            {reviewCompleted ? <CheckCircle2 className="mt-0.5 h-5 w-5 text-emerald-600" /> : <AlertTriangle className="mt-0.5 h-5 w-5 text-amber-600" />}
                            <div>
                                <p className="text-sm font-semibold">{reviewCompleted ? '정상 최종 결정 가능' : '강제 결정으로 기록됩니다'}</p>
                                <p className="mt-1 text-xs text-slate-600 dark:text-slate-300">심사 완료 {abstractSubmission.completedReviewCount ?? 0} / 배정 {abstractSubmission.reviewerAssignmentCount ?? 0}명 · 정상 완료 기준은 심사자 2명 이상 전원 제출입니다.</p>
                            </div>
                        </div>
                    </div>

                    <fieldset>
                        <legend className="text-xs font-semibold uppercase text-slate-500">최종 결정 *</legend>
                        <div className="mt-2 grid grid-cols-2 gap-3">
                            <label className={`flex cursor-pointer items-center gap-2 rounded-xl border p-3 ${decision === 'approved' ? 'border-emerald-500 bg-emerald-50 dark:bg-emerald-950/30' : 'border-slate-200 dark:border-slate-800'}`}><input type="radio" name="decision" checked={decision === 'approved'} onChange={() => setDecision('approved')} /><CheckCircle2 className="h-4 w-4 text-emerald-600" /><span className="text-sm font-semibold">승인</span></label>
                            <label className={`flex cursor-pointer items-center gap-2 rounded-xl border p-3 ${decision === 'rejected' ? 'border-rose-500 bg-rose-50 dark:bg-rose-950/30' : 'border-slate-200 dark:border-slate-800'}`}><input type="radio" name="decision" checked={decision === 'rejected'} onChange={() => setDecision('rejected')} /><XCircle className="h-4 w-4 text-rose-600" /><span className="text-sm font-semibold">반려</span></label>
                        </div>
                    </fieldset>

                    {decision === 'approved' && (
                        <label className="block space-y-1.5">
                            <span className="text-xs font-semibold uppercase text-slate-500">최종 발표형식 *</span>
                            <select value={acceptedPresentationTypeCode} onChange={(event) => setAcceptedPresentationTypeCode(event.target.value)} className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900" required>
                                <option value="">선택하세요</option>
                                {presentationTypes.map((item) => <option key={item.code} value={item.code}>{item.name}</option>)}
                            </select>
                            <p className="text-xs text-slate-400">접수 시 희망 형식과 다르게 최종 배정할 수 있습니다.</p>
                        </label>
                    )}

                    <label className="block space-y-1.5">
                        <span className="text-xs font-semibold uppercase text-slate-500">결정 사유 {!reviewCompleted && <span className="text-rose-500">*</span>}</span>
                        <textarea value={reason} onChange={(event) => setReason(event.target.value)} maxLength={1000} className="min-h-24 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900" placeholder={reviewCompleted ? '필요한 경우 결정 근거를 입력하세요.' : '강제 결정 사유를 반드시 입력하세요.'} />
                        <p className="text-right text-xs text-slate-400">{reason.length} / 1000</p>
                    </label>

                    <div className="flex justify-end gap-2 border-t border-slate-200 pt-4 dark:border-slate-800">
                        <button type="button" onClick={onClose} className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-600 dark:border-slate-800 dark:text-slate-300">취소</button>
                        <button type="submit" disabled={submitting} className={`rounded-lg px-4 py-2 text-sm font-semibold text-white disabled:opacity-50 ${decision === 'approved' ? 'bg-emerald-600 hover:bg-emerald-700' : 'bg-rose-600 hover:bg-rose-700'}`}>{submitting ? '저장 중' : reviewCompleted ? '최종 결정 저장' : '강제 결정 저장'}</button>
                    </div>
                </form>
            </DraggableModal>
        </div>
    );
};
