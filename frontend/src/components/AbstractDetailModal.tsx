import {
    AlertTriangle,
    BarChart3,
    CheckCircle2,
    ClipboardCheck,
    Clock3,
    Download,
    Eye,
    FileText,
    Paperclip,
    Pencil,
    ScanSearch,
    ShieldCheck,
    Users,
    X
} from 'lucide-react';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import type { AbstractSimilarityMatch, AbstractSimilarityResponse, AbstractSubmissionDetail, AbstractTitleSimilarityResponse } from './abstractTypes';
import type { AbstractReviewResultData, AbstractReviewResultReviewer, ReviewRecommendation } from './abstractReviewResultTypes';
import type { NotificationType } from './NotificationToast';
import { useModalDrag } from '../hooks/useModalDrag';
import { AbstractSimilarityComparisonModal } from './AbstractSimilarityComparisonModal';

interface Props {
    isOpen: boolean;
    abstractSubmission: AbstractSubmissionDetail | null;
    similarityEnabled?: boolean;
    similarityAnalysisActive?: boolean;
    onClose: () => void;
    onEdit?: (seq: number) => void;
    onDecision?: (abstractSubmission: AbstractSubmissionDetail) => void;
    onNotify?: (type: NotificationType, message: string) => void;
}

const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
});

const formatDate = (value?: string | null) => {
    if (!value) return '-';
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? '-' : dateFormatter.format(date);
};

const formatText = (value?: string | null) => value?.trim() || '-';

const readSimilarityResult = async (response: Response): Promise<AbstractSimilarityResponse | null> => {
    if (!response.ok) {
        const message = await response.text();
        if (response.status === 404 && message.trim().startsWith('저장된 유사도 분석 결과가 없습니다.')) {
            return null;
        }
        throw new Error(message || '유사도 분석 결과를 불러오지 못했습니다.');
    }
    return await response.json() as AbstractSimilarityResponse;
};

const formatFileSize = (value: number) => {
    if (value < 1024) return `${value.toLocaleString()} B`;
    if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
    return `${(value / (1024 * 1024)).toFixed(1)} MB`;
};

const formatAiTools = (abstractSubmission: AbstractSubmissionDetail) => {
    if (!abstractSubmission.aiTools?.length) return '-';
    return abstractSubmission.aiTools
        .map((tool) => tool.isEtc === 'Y'
            ? [tool.otherToolName, tool.otherProviderName].filter(Boolean).join(' / ')
            : tool.aiToolName)
        .filter(Boolean)
        .join(', ') || '-';
};

const formatAiScopes = (abstractSubmission: AbstractSubmissionDetail) => {
    if (!abstractSubmission.aiScopes?.length) return '-';
    return abstractSubmission.aiScopes
        .map((scope) => scope.isEtc === 'Y' ? scope.otherScopeText : scope.aiScopeName)
        .filter(Boolean)
        .join(', ') || '-';
};

const STATUS_LABEL: Record<AbstractSubmissionDetail['status'], string> = {
    draft: '임시저장',
    submitted: '제출완료',
    under_review: '심사중',
    approved: '승인',
    rejected: '반려'
};

const STATUS_CLASS: Record<AbstractSubmissionDetail['status'], string> = {
    draft: 'bg-slate-100 text-slate-700 border-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:border-slate-700',
    submitted: 'bg-blue-50 text-blue-700 border-blue-200 dark:bg-blue-950/40 dark:text-blue-300 dark:border-blue-900/60',
    under_review: 'bg-amber-50 text-amber-700 border-amber-200 dark:bg-amber-950/40 dark:text-amber-300 dark:border-amber-900/60',
    approved: 'bg-emerald-50 text-emerald-700 border-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-300 dark:border-emerald-900/60',
    rejected: 'bg-rose-50 text-rose-700 border-rose-200 dark:bg-rose-950/40 dark:text-rose-300 dark:border-rose-900/60'
};

export const AbstractDetailModal = ({
    isOpen,
    abstractSubmission,
    similarityEnabled = false,
    similarityAnalysisActive = false,
    onClose,
    onEdit,
    onDecision,
    onNotify = () => undefined
}: Props) => {
    const { modalRef, headerRef } = useModalDrag(isOpen && Boolean(abstractSubmission));
    const onNotifyRef = useRef(onNotify);
    const [isLoadingReviewResult, setIsLoadingReviewResult] = useState(false);
    const [reviewResultError, setReviewResultError] = useState('');
    const [reviewResult, setReviewResult] = useState<AbstractReviewResultData | null>(null);
    const [isCheckingSimilarity, setIsCheckingSimilarity] = useState(false);
    const [similarityResultError, setSimilarityResultError] = useState('');
    const [similarityResult, setSimilarityResult] = useState<AbstractSimilarityResponse | null>(null);
    const [isSimilarityComparisonOpen, setIsSimilarityComparisonOpen] = useState(false);
    const [isLoadingTitleSimilarity, setIsLoadingTitleSimilarity] = useState(false);
    const [titleSimilarityResult, setTitleSimilarityResult] = useState<AbstractTitleSimilarityResponse | null>(null);
    const [titleSimilarityError, setTitleSimilarityError] = useState('');
    const hasSimilarityFeature = similarityEnabled === true;

    useEffect(() => {
        onNotifyRef.current = onNotify;
    }, [onNotify]);

    useEffect(() => {
        if (!isOpen || !abstractSubmission) return;

        const controller = new AbortController();
        const loadReviewResult = async () => {
            setIsLoadingReviewResult(true);
            setReviewResultError('');
            setReviewResult(null);
            try {
                const response = await fetch(`/api/admin/abstracts/${abstractSubmission.seq}/review-results`, {
                    signal: controller.signal
                });
                if (!response.ok) {
                    throw new Error(await response.text() || '심사 결과를 불러오지 못했습니다.');
                }
                setReviewResult(await response.json() as AbstractReviewResultData);
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') return;
                const message = error instanceof Error ? error.message : '심사 결과를 불러오지 못했습니다.';
                setReviewResultError(message);
                onNotifyRef.current('error', message);
            } finally {
                if (!controller.signal.aborted) setIsLoadingReviewResult(false);
            }
        };

        const loadSimilarityResult = async () => {
            if (!hasSimilarityFeature || similarityAnalysisActive || abstractSubmission.status === 'draft') return;
            setIsCheckingSimilarity(true);
            setSimilarityResultError('');
            setSimilarityResult(null);
            try {
                const response = await fetch(`/api/admin/abstracts/${abstractSubmission.seq}/similarities`, {
                    signal: controller.signal
                });
                setSimilarityResult(await readSimilarityResult(response));
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') return;
                const message = error instanceof Error ? error.message : '유사도 분석 결과를 불러오지 못했습니다.';
                setSimilarityResultError(message);
                onNotifyRef.current('error', message);
            } finally {
                if (!controller.signal.aborted) setIsCheckingSimilarity(false);
            }
        };

        const loadTitleSimilarityResult = async () => {
            if (abstractSubmission.status === 'draft') return;
            setIsLoadingTitleSimilarity(true);
            setTitleSimilarityError('');
            setTitleSimilarityResult(null);
            try {
                const response = await fetch(`/api/admin/abstracts/${abstractSubmission.seq}/title-similarities`, {
                    signal: controller.signal
                });
                if (!response.ok) {
                    throw new Error(await response.text() || '제목 유사도 검사 결과를 불러오지 못했습니다.');
                }
                setTitleSimilarityResult(await response.json() as AbstractTitleSimilarityResponse);
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') return;
                const message = error instanceof Error ? error.message : '제목 유사도 검사 결과를 불러오지 못했습니다.';
                setTitleSimilarityError(message);
            } finally {
                if (!controller.signal.aborted) setIsLoadingTitleSimilarity(false);
            }
        };

        void loadReviewResult();
        void loadSimilarityResult();
        void loadTitleSimilarityResult();
        return () => controller.abort();
    }, [abstractSubmission, hasSimilarityFeature, isOpen, similarityAnalysisActive]);

    const handleClose = () => {
        setIsCheckingSimilarity(false);
        setIsLoadingReviewResult(false);
        setReviewResultError('');
        setReviewResult(null);
        setSimilarityResultError('');
        setSimilarityResult(null);
        setIsSimilarityComparisonOpen(false);
        setIsLoadingTitleSimilarity(false);
        setTitleSimilarityResult(null);
        setTitleSimilarityError('');
        onClose();
    };

    if (!isOpen || !abstractSubmission) {
        return null;
    }

    const handleEdit = () => {
        handleClose();
        onEdit?.(abstractSubmission.seq);
    };

    const handleDecision = () => {
        const target = abstractSubmission;
        handleClose();
        onDecision?.(target);
    };

    const handleSimilarityCheck = async () => {
        if (!abstractSubmission || isCheckingSimilarity) return;

        setIsCheckingSimilarity(true);
        setSimilarityResultError('');
        setSimilarityResult(null);
        try {
            const response = await fetch(`/api/admin/abstracts/${abstractSubmission.seq}/similarities`);
            setSimilarityResult(await readSimilarityResult(response));
        } catch (error) {
            const message = error instanceof Error ? error.message : '유사도 분석 결과를 불러오지 못했습니다.';
            setSimilarityResultError(message);
            onNotifyRef.current('error', message);
        } finally {
            setIsCheckingSimilarity(false);
        }
    };

    const handleReviewResultCheck = async () => {
        if (isLoadingReviewResult) return;

        setIsLoadingReviewResult(true);
        setReviewResultError('');
        setReviewResult(null);
        try {
            const response = await fetch(`/api/admin/abstracts/${abstractSubmission.seq}/review-results`);
            if (!response.ok) {
                throw new Error(await response.text() || '심사 결과를 불러오지 못했습니다.');
            }
            setReviewResult(await response.json() as AbstractReviewResultData);
        } catch (error) {
            const message = error instanceof Error ? error.message : '심사 결과를 불러오지 못했습니다.';
            setReviewResultError(message);
            onNotifyRef.current('error', message);
        } finally {
            setIsLoadingReviewResult(false);
        }
    };

    const assignedReviewCount = abstractSubmission.reviewerAssignmentCount ?? 0;
    const completedReviewCount = abstractSubmission.completedReviewCount ?? 0;
    const reviewReady = assignedReviewCount >= 2 && completedReviewCount === assignedReviewCount;
    const reviewProgress = assignedReviewCount === 0
        ? 0
        : Math.min(100, Math.round((completedReviewCount / assignedReviewCount) * 100));
    const canMakeDecision = abstractSubmission.status === 'submitted' || abstractSubmission.status === 'under_review';
    const isFinalized = abstractSubmission.status === 'approved' || abstractSubmission.status === 'rejected';
    const requestedPresentationType = abstractSubmission.presentationTypeName || String(abstractSubmission.presentationTypeCode);
    const acceptedPresentationType = abstractSubmission.acceptedPresentationTypeName
        || (abstractSubmission.acceptedPresentationTypeCode ? String(abstractSubmission.acceptedPresentationTypeCode) : '-');
    const linkedInstitutionNumbers = new Set(abstractSubmission.authors.map((author) => author.institutionNo));
    const unlinkedInstitutions = abstractSubmission.institutions.filter(
        (institution) => !linkedInstitutionNumbers.has(institution.institutionNo)
    );

    return (
        <>
            <div className="fixed inset-0 z-[75] flex items-center justify-center bg-slate-950/60 px-4 py-6 backdrop-blur-sm">
            <div
                ref={modalRef}
                className={`absolute left-1/2 top-1/2 flex max-h-[calc(100dvh-1rem)] w-[calc(100%-1rem)] flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl dark:border-slate-800 dark:bg-slate-950 ${hasSimilarityFeature ? 'max-w-[112rem]' : 'max-w-[92rem]'}`}
                style={{ transform: 'translate(-50%, -50%)' }}
                role="dialog"
                aria-modal="true"
                aria-labelledby="abstract-detail-title"
            >
                <div
                    ref={headerRef}
                    className="flex shrink-0 cursor-move select-none touch-none items-start justify-between gap-4 border-b border-slate-200 px-5 py-4 dark:border-slate-800"
                >
                    <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-2">
                            <ClipboardCheck className="h-5 w-5 shrink-0 text-blue-600 dark:text-blue-400" />
                            <h2 id="abstract-detail-title" className="text-base font-bold text-slate-900 dark:text-slate-50">초록 검토</h2>
                            <span className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold ${STATUS_CLASS[abstractSubmission.status]}`}>
                                {STATUS_LABEL[abstractSubmission.status]}
                            </span>
                        </div>
                        <p className="mt-1 truncate text-xs text-slate-500 dark:text-slate-400">
                            {abstractSubmission.submissionNo || `#${abstractSubmission.seq}`} · 채택 판단에 필요한 내용과 심사 현황을 검토합니다.
                        </p>
                    </div>
                    <div className="flex items-center gap-2">
                        <button type="button" onClick={handleClose} className="shrink-0 rounded-lg p-2 text-slate-400 transition-colors hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-900" aria-label="초록 검토 닫기">
                            <X className="h-5 w-5" />
                        </button>
                    </div>
                </div>

                <div className="flex-1 overflow-y-auto bg-slate-50/70 p-4 dark:bg-slate-950 md:p-5">
                    <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-slate-950 md:p-5">
                        <div className="flex flex-wrap items-center gap-2 text-xs font-medium text-slate-500 dark:text-slate-400">
                            <span className="font-mono font-semibold text-blue-600 dark:text-blue-400">{abstractSubmission.submissionNo || `#${abstractSubmission.seq}`}</span>
                            <span aria-hidden="true">·</span>
                            <span>{abstractSubmission.categoryName || String(abstractSubmission.categoryCode)}</span>
                            <span aria-hidden="true">·</span>
                            <span>{requestedPresentationType}</span>
                        </div>
                        <h1 className="mt-2 max-w-4xl text-xl font-bold leading-snug tracking-tight text-slate-950 dark:text-white md:text-2xl">
                            {abstractSubmission.title}
                        </h1>
                        <p className="mt-3 text-sm text-slate-500 dark:text-slate-400">
                            {abstractSubmission.mainAuthorName || abstractSubmission.memberFullName || '작성자 정보 없음'}
                            {' · '}{abstractSubmission.authorCount ?? abstractSubmission.authors.length}명
                            {' · '}{abstractSubmission.institutionCount ?? abstractSubmission.institutions.length}개 기관
                        </p>

                        <dl className="mt-5 grid grid-cols-2 overflow-hidden rounded-lg border border-slate-200 bg-slate-50/80 dark:border-slate-800 dark:bg-slate-900/40 lg:grid-cols-4">
                            <SummaryMetric label="심사 진행" value={`${completedReviewCount} / ${assignedReviewCount}명`} tone={reviewReady ? 'positive' : 'default'} />
                            <SummaryMetric label="평균 점수" value={abstractSubmission.averageReviewScore == null ? '-' : `${abstractSubmission.averageReviewScore.toFixed(2)} / 6`} />
                            <SummaryMetric label="신청 발표형식" value={requestedPresentationType} />
                            <SummaryMetric label="최종 발표형식" value={acceptedPresentationType} />
                        </dl>
                    </article>

                    <div className={`mt-5 grid grid-cols-1 items-start gap-5 ${hasSimilarityFeature
                        ? 'xl:grid-cols-[minmax(0,1.08fr)_minmax(28rem,0.92fr)] 2xl:grid-cols-[minmax(0,1fr)_minmax(25rem,0.75fr)_minmax(26rem,0.68fr)]'
                        : '2xl:grid-cols-[minmax(0,1.08fr)_minmax(30rem,0.92fr)]'}`}>
                        <main className="space-y-5">
                            <section className="rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-950">
                                <div className="flex items-center gap-3 border-b border-slate-200 px-4 py-3.5 dark:border-slate-800 md:px-5">
                                    <div className="flex items-center gap-2">
                                        <FileText className="h-4 w-4 text-blue-500" />
                                        <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-50">초록 본문</h3>
                                    </div>
                                </div>
                                <div className="divide-y divide-slate-200 px-4 dark:divide-slate-800 md:px-5">
                                    <AbstractSection label="Objective" value={formatText(abstractSubmission.objectiveText)} />
                                    <AbstractSection label="Methods" value={formatText(abstractSubmission.methodsText)} />
                                    <AbstractSection label="Results" value={formatText(abstractSubmission.resultsText)} />
                                    <AbstractSection label="Conclusions" value={formatText(abstractSubmission.conclusionsText)} />
                                </div>
                            </section>

                            <PeoplePanel title="저자 및 소속 정보" icon={<Users className="h-4 w-4" />} count={abstractSubmission.authors.length} countUnit="명">
                                {abstractSubmission.authors.length === 0 ? (
                                    <EmptyText>등록된 저자 정보가 없습니다.</EmptyText>
                                ) : (
                                    <div className="divide-y divide-slate-200 dark:divide-slate-800">
                                        {abstractSubmission.authors.map((author) => {
                                            const institution = abstractSubmission.institutions.find(
                                                (item) => item.institutionNo === author.institutionNo
                                            );
                                            return (
                                                <div key={author.seq ?? author.authorOrder} className="grid gap-3 py-3 first:pt-0 last:pb-0 sm:grid-cols-[2rem_minmax(0,1fr)_auto] sm:items-start">
                                                    <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-slate-100 font-mono text-xs font-semibold text-slate-500 dark:bg-slate-900 dark:text-slate-400">
                                                        {author.authorOrder}
                                                    </span>
                                                    <div className="min-w-0">
                                                        <p className="text-sm font-semibold text-slate-900 dark:text-slate-50">{author.authorName}</p>
                                                        <p className="mt-1 text-sm font-medium text-slate-700 dark:text-slate-300">{institution?.institutionName || '기관 정보 없음'}</p>
                                                        <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                                                            {institution
                                                                ? [institution.department, institution.country].filter(Boolean).join(' · ') || '-'
                                                                : `${author.country || '-'} · 기관 상세정보 없음`}
                                                        </p>
                                                    </div>
                                                    <div className="flex flex-wrap gap-1.5 sm:justify-end">
                                                        {author.isPresentingAuthor && <RoleBadge tone="blue">발표저자</RoleBadge>}
                                                        {author.isCorrespondingAuthor && <RoleBadge tone="emerald">교신저자</RoleBadge>}
                                                    </div>
                                                </div>
                                            );
                                        })}
                                    </div>
                                )}

                                {unlinkedInstitutions.length > 0 && (
                                    <div className="mt-4 rounded-lg border border-amber-200 bg-amber-50/60 p-3 dark:border-amber-900/60 dark:bg-amber-950/20">
                                        <p className="text-xs font-semibold text-amber-700 dark:text-amber-300">저자와 연결되지 않은 기관 {unlinkedInstitutions.length}개</p>
                                        <div className="mt-2 space-y-1.5">
                                            {unlinkedInstitutions.map((institution) => (
                                                <p key={institution.seq ?? institution.institutionNo} className="text-xs text-slate-600 dark:text-slate-300">
                                                    {institution.institutionName} · {[institution.department, institution.country].filter(Boolean).join(' · ') || '-'}
                                                </p>
                                            ))}
                                        </div>
                                    </div>
                                )}
                            </PeoplePanel>

                            <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
                                <SidePanel title="첨부파일" icon={<Paperclip className="h-4 w-4" />} count={abstractSubmission.attachments?.length ?? 0}>
                                    {!abstractSubmission.attachments?.length ? (
                                        <EmptyText>등록된 첨부파일이 없습니다.</EmptyText>
                                    ) : (
                                        <div className="space-y-2">
                                            {abstractSubmission.attachments.map((attachment) => {
                                                const extension = attachment.fileExtension.replace(/^\./, '').toLowerCase();
                                                const isPdf = extension === 'pdf' || attachment.contentType === 'application/pdf';
                                                return (
                                                    <a
                                                        key={attachment.seq}
                                                        href={`/api/admin/abstracts/${abstractSubmission.seq}/attachments/${attachment.seq}`}
                                                        target={isPdf ? '_blank' : undefined}
                                                        rel={isPdf ? 'noreferrer' : undefined}
                                                        className="group flex items-center gap-3 rounded-lg border border-slate-200 p-3 transition-colors hover:border-blue-300 hover:bg-blue-50/50 dark:border-slate-800 dark:hover:border-blue-900 dark:hover:bg-blue-950/20"
                                                    >
                                                        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-slate-100 text-slate-500 group-hover:text-blue-600 dark:bg-slate-900 dark:text-slate-400 dark:group-hover:text-blue-400">
                                                            <Download className="h-4 w-4" />
                                                        </span>
                                                        <span className="min-w-0">
                                                            <span className="block truncate text-xs font-semibold text-slate-700 dark:text-slate-200">{attachment.originalFilename}</span>
                                                            <span className="mt-0.5 block text-[11px] text-slate-400">{extension ? extension.toUpperCase() : 'FILE'} · {formatFileSize(attachment.fileSize)}</span>
                                                        </span>
                                                    </a>
                                                );
                                            })}
                                        </div>
                                    )}
                                </SidePanel>

                                <SidePanel title="AI 및 정책 확인" icon={<ShieldCheck className="h-4 w-4" />}>
                                    <div className="space-y-3">
                                        <PolicyRow label="AI 사용" value={abstractSubmission.aiUsage ? '사용함' : '사용 안 함'} warning={abstractSubmission.aiUsage} />
                                        {abstractSubmission.aiUsage && (
                                            <>
                                                <InfoRow label="사용 도구" value={formatAiTools(abstractSubmission)} />
                                                <InfoRow label="활용 범위" value={formatAiScopes(abstractSubmission)} />
                                                <InfoRow label="모델 정보" value={formatText(abstractSubmission.aiVersionInfo)} />
                                            </>
                                        )}
                                        <PolicyRow label="분석·해석 활용" value={abstractSubmission.aiDataAnalysisUsed ? '사용함 · 검토 필요' : '사용 안 함'} warning={abstractSubmission.aiDataAnalysisUsed} />
                                        <PolicyRow label="정책 준수 확인" value={abstractSubmission.plagiarismPolicyConfirmed ? '확인 완료' : '미확인'} warning={!abstractSubmission.plagiarismPolicyConfirmed} />
                                    </div>
                                </SidePanel>

                                <div className="lg:col-span-2">
                                    <SidePanel title="접수 정보" icon={<Clock3 className="h-4 w-4" />}>
                                        <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
                                            <InfoRow label="접수자" value={abstractSubmission.memberFullName || '-'} subValue={abstractSubmission.memberEmail || undefined} />
                                            <InfoRow label="접수 경로" value={abstractSubmission.submissionSource === 'member' ? '회원 접수' : '관리자 등록'} subValue={abstractSubmission.createdByAdminName || undefined} />
                                            <InfoRow label="작성일" value={formatDate(abstractSubmission.createdAt)} />
                                            <InfoRow label="수정일" value={formatDate(abstractSubmission.updatedAt)} />
                                            <InfoRow label="제출일" value={formatDate(abstractSubmission.submittedAt)} />
                                        </dl>
                                    </SidePanel>
                                </div>
                            </div>
                        </main>

                        <aside className="space-y-5">
                            <TitleSimilarityPanel
                                loading={isLoadingTitleSimilarity}
                                excluded={abstractSubmission.status === 'draft'}
                                error={titleSimilarityError}
                                result={titleSimilarityResult}
                            />

                            <SidePanel title="심사 완료 현황" icon={<ClipboardCheck className="h-4 w-4" />}>
                                {isFinalized ? (
                                    <div className={`rounded-lg border p-3 ${abstractSubmission.status === 'approved' ? 'border-emerald-200 bg-emerald-50 dark:border-emerald-900/60 dark:bg-emerald-950/30' : 'border-rose-200 bg-rose-50 dark:border-rose-900/60 dark:bg-rose-950/30'}`}>
                                        <div className="flex items-center justify-between gap-2">
                                            <p className="text-sm font-semibold text-slate-900 dark:text-slate-50">최종 {STATUS_LABEL[abstractSubmission.status]}</p>
                                            {abstractSubmission.forcedDecision && <span className="text-xs font-semibold text-amber-700 dark:text-amber-300">강제 결정</span>}
                                        </div>
                                        <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">{abstractSubmission.decisionByAdminName || '-'} · {formatDate(abstractSubmission.decisionAt)}</p>
                                        <p className="mt-3 whitespace-pre-wrap text-sm leading-6 text-slate-700 dark:text-slate-300">{formatText(abstractSubmission.decisionReason)}</p>
                                    </div>
                                ) : (
                                    <>
                                        <div className="flex items-start gap-3">
                                            {reviewReady
                                                ? <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0 text-emerald-600 dark:text-emerald-400" />
                                                : <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-amber-600 dark:text-amber-400" />}
                                            <div>
                                                <p className="text-sm font-semibold text-slate-900 dark:text-slate-50">{reviewReady ? '정상 최종 결정 가능' : assignedReviewCount === 0 ? '심사자 배정 필요' : '심사 진행 중'}</p>
                                                <p className="mt-1 text-xs leading-5 text-slate-500 dark:text-slate-400">정상 결정 기준은 심사자 2명 이상이 모두 결과를 제출한 상태입니다.</p>
                                            </div>
                                        </div>
                                        <div className="mt-4">
                                            <div className="flex justify-between text-xs text-slate-500 dark:text-slate-400">
                                                <span>완료 {completedReviewCount}명</span>
                                                <span className="tabular-nums">{reviewProgress}%</span>
                                            </div>
                                            <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-slate-200 dark:bg-slate-800">
                                                <div className={`h-full rounded-full ${reviewReady ? 'bg-emerald-500' : 'bg-amber-500'}`} style={{ width: `${reviewProgress}%` }} />
                                            </div>
                                        </div>
                                    </>
                                )}
                                <div className="mt-4 grid grid-cols-2 gap-3 border-t border-slate-200 pt-4 dark:border-slate-800">
                                    <InfoStat label="배정" value={`${assignedReviewCount}명`} />
                                    <InfoStat label="평균" value={abstractSubmission.averageReviewScore == null ? '-' : abstractSubmission.averageReviewScore.toFixed(2)} />
                                </div>
                            </SidePanel>

                            <ReviewResultPanel
                                loading={isLoadingReviewResult}
                                error={reviewResultError}
                                result={reviewResult}
                                onRetry={() => void handleReviewResultCheck()}
                            />

                        </aside>

                        {hasSimilarityFeature && (
                            <aside className="xl:col-span-2 2xl:col-span-1">
                                <SimilarityEvidencePanel
                                    loading={isCheckingSimilarity}
                                    analysisActive={similarityAnalysisActive}
                                    excluded={abstractSubmission.status === 'draft'}
                                    error={similarityResultError}
                                    result={similarityResult}
                                    onRetry={() => void handleSimilarityCheck()}
                                    onOpenDetail={() => setIsSimilarityComparisonOpen(true)}
                                />
                            </aside>
                        )}
                    </div>
                </div>

                <div className="flex shrink-0 flex-wrap items-center justify-end gap-2 border-t border-slate-200 bg-white px-5 py-4 dark:border-slate-800 dark:bg-slate-950">
                    <button type="button" onClick={handleClose} className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-600 transition-colors hover:bg-slate-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900">
                        닫기
                    </button>
                    {onEdit && abstractSubmission.submissionSource === 'admin' && (
                        <button
                            type="button"
                            onClick={handleEdit}
                            className="inline-flex items-center gap-2 rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-600 transition-colors hover:bg-slate-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900"
                        >
                            <Pencil className="h-4 w-4 text-blue-500" />
                            수정
                        </button>
                    )}
                    {onDecision && (
                        <button
                            type="button"
                            onClick={handleDecision}
                            disabled={!canMakeDecision}
                            title={canMakeDecision ? '승인 또는 반려를 최종 결정합니다.' : '이미 최종 결정되었거나 결정할 수 없는 상태입니다.'}
                            className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-40 dark:bg-blue-600 dark:text-white dark:hover:bg-blue-700"
                        >
                            <CheckCircle2 className="h-4 w-4" />
                            최종 결정
                        </button>
                    )}
                </div>
            </div>
            </div>
            {hasSimilarityFeature && (
                <AbstractSimilarityComparisonModal
                    isOpen={isSimilarityComparisonOpen}
                    source={abstractSubmission}
                    result={similarityResult}
                    onClose={() => setIsSimilarityComparisonOpen(false)}
                />
            )}
        </>
    );
};

const SummaryMetric = ({ label, value, tone = 'default' }: { label: string; value: string; tone?: 'default' | 'positive' }) => (
    <div className="border-b border-r border-slate-200 p-3 last:border-r-0 dark:border-slate-800 lg:border-b-0">
        <dt className="text-[11px] font-semibold text-slate-500 dark:text-slate-400">{label}</dt>
        <dd className={`mt-1 text-sm font-bold ${tone === 'positive' ? 'text-emerald-600 dark:text-emerald-400' : 'text-slate-900 dark:text-slate-50'}`}>{value}</dd>
    </div>
);

const AbstractSection = ({ label, value }: { label: string; value: string }) => (
    <section className="grid gap-2 py-5 md:grid-cols-[7rem_minmax(0,1fr)] md:gap-5">
        <h4 className="text-xs font-bold tracking-wide text-blue-600 dark:text-blue-400">{label}</h4>
        <p className="whitespace-pre-wrap text-sm leading-7 text-slate-700 dark:text-slate-300">{value}</p>
    </section>
);

const TitleSimilarityPanel = ({
    loading,
    excluded,
    error,
    result
}: {
    loading: boolean;
    excluded: boolean;
    error: string;
    result: AbstractTitleSimilarityResponse | null;
}) => (
    <SidePanel title="제목 유사도 검사" icon={<ScanSearch className="h-4 w-4" />}>
        {excluded ? (
            <p className="text-sm text-slate-500 dark:text-slate-400">임시저장 초록은 제목 유사도 검사 대상이 아닙니다.</p>
        ) : loading ? (
            <p className="text-sm text-slate-400">저장 시 검사 결과를 불러오는 중입니다.</p>
        ) : error ? (
            <p className="text-sm text-rose-600 dark:text-rose-400">{error}</p>
        ) : !result?.checkedAt ? (
            <p className="text-sm text-slate-500 dark:text-slate-400">아직 저장 시 제목 유사도 검사를 실행하지 않은 초록입니다.</p>
        ) : result.matches.length === 0 ? (
            <div>
                <p className="flex items-center gap-2 text-sm font-semibold text-emerald-700 dark:text-emerald-300"><ShieldCheck className="h-4 w-4" />경고 기준 이상의 유사 제목이 없습니다.</p>
                <p className="mt-1 text-xs text-slate-400">검사 {formatDate(result.checkedAt)} · 기준 {result.warningThreshold.toFixed(0)}%</p>
            </div>
        ) : (
            <div className="space-y-3">
                <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 dark:border-amber-900/60 dark:bg-amber-950/30">
                    <p className="flex items-center gap-2 text-sm font-semibold text-amber-800 dark:text-amber-300"><AlertTriangle className="h-4 w-4" />유사 제목 {result.matchCount}건을 확인해 주세요.</p>
                    <p className="mt-1 text-xs text-amber-700/80 dark:text-amber-300/80">최고 {result.maxSimilarity?.toFixed(1)}% · 검사 {formatDate(result.checkedAt)}</p>
                </div>
                <div className="space-y-2">
                    {result.matches.map((match) => (
                        <div key={match.abstractSeq} className="rounded-lg border border-slate-200 p-3 dark:border-slate-800">
                            <div className="flex items-start justify-between gap-3">
                                <div className="min-w-0">
                                    <p className="font-mono text-[11px] font-semibold text-blue-600 dark:text-blue-400">{match.submissionNo || `#${match.abstractSeq}`}</p>
                                    <p className="mt-1 text-sm font-semibold leading-5 text-slate-900 dark:text-slate-50">{match.title}</p>
                                </div>
                                <span className="shrink-0 rounded-full bg-amber-100 px-2 py-1 text-xs font-bold text-amber-800 dark:bg-amber-950/60 dark:text-amber-300">{match.similarityScore.toFixed(1)}%</span>
                            </div>
                            <p className="mt-2 text-[11px] text-slate-400">편집거리 {match.levenshteinSimilarity.toFixed(1)} · 3-gram {match.trigramSimilarity.toFixed(1)} · 단어 {match.jaccardSimilarity.toFixed(1)}{match.exactMatch ? ' · 정규화 제목 동일' : ''}</p>
                        </div>
                    ))}
                </div>
                {result.matchCount > result.matches.length && <p className="text-[11px] text-slate-400">유사도순 상위 {result.matches.length}건을 표시합니다.</p>}
                <p className="text-[11px] leading-5 text-slate-400">이 점수는 표절률이 아닌 제목 문자열 유사도입니다. 초록 내용을 함께 확인해 중복 여부를 판단하세요.</p>
            </div>
        )}
    </SidePanel>
);

const PeoplePanel = ({
    title,
    icon,
    count,
    countUnit = '개',
    children
}: {
    title: string;
    icon: ReactNode;
    count: number;
    countUnit?: string;
    children: ReactNode;
}) => (
    <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-slate-950">
        <div className="mb-4 flex items-center justify-between gap-2">
            <div className="flex items-center gap-2 text-slate-500 dark:text-slate-400">
                {icon}
                <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-50">{title}</h3>
            </div>
            <span className="text-xs tabular-nums text-slate-400">{count}{countUnit}</span>
        </div>
        {children}
    </section>
);

const SidePanel = ({
    title,
    icon,
    count,
    children
}: {
    title: string;
    icon: ReactNode;
    count?: number;
    children: ReactNode;
}) => (
    <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-slate-950">
        <div className="mb-4 flex items-center justify-between gap-2">
            <div className="flex items-center gap-2 text-slate-500 dark:text-slate-400">
                {icon}
                <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-50">{title}</h3>
            </div>
            {count != null && <span className="text-xs tabular-nums text-slate-400">{count}개</span>}
        </div>
        {children}
    </section>
);

const InfoStat = ({ label, value }: { label: string; value: string }) => (
    <div>
        <p className="text-[11px] font-semibold text-slate-400">{label}</p>
        <p className="mt-1 text-sm font-bold tabular-nums text-slate-900 dark:text-slate-50">{value}</p>
    </div>
);

const InfoRow = ({ label, value, subValue }: { label: string; value: string; subValue?: string }) => (
    <div>
        <dt className="text-[11px] font-semibold text-slate-400">{label}</dt>
        <dd className="mt-1 break-words text-sm font-medium text-slate-700 dark:text-slate-300">{value}</dd>
        {subValue && <dd className="mt-0.5 break-all text-xs text-slate-400">{subValue}</dd>}
    </div>
);

const PolicyRow = ({ label, value, warning }: { label: string; value: string; warning: boolean }) => (
    <div className="flex items-start justify-between gap-3">
        <span className="text-xs text-slate-500 dark:text-slate-400">{label}</span>
        <span className={`text-right text-xs font-semibold ${warning ? 'text-amber-700 dark:text-amber-300' : 'text-emerald-700 dark:text-emerald-300'}`}>{value}</span>
    </div>
);

const RoleBadge = ({ children, tone }: { children: ReactNode; tone: 'blue' | 'emerald' }) => (
    <span className={`rounded-full px-2 py-0.5 text-[11px] font-semibold ${tone === 'blue' ? 'bg-blue-50 text-blue-700 dark:bg-blue-950/40 dark:text-blue-300' : 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300'}`}>
        {children}
    </span>
);

const EmptyText = ({ children }: { children: ReactNode }) => (
    <p className="text-sm text-slate-400">{children}</p>
);

const RECOMMENDATION_LABEL: Record<ReviewRecommendation, string> = {
    accept: '추천',
    reject: '반려'
};

const ASSIGNMENT_STATUS_LABEL: Record<AbstractReviewResultReviewer['assignmentStatus'], string> = {
    assigned: '배정됨',
    accepted: '수락',
    in_review: '작성 중',
    completed: '제출 완료'
};

const formatReviewScore = (value?: number | null) => value == null ? '-' : `${value.toFixed(2)} / 6`;

interface ReviewResultPanelProps {
    loading: boolean;
    error: string;
    result: AbstractReviewResultData | null;
    onRetry: () => void;
}

const ReviewResultPanel = ({ loading, error, result, onRetry }: ReviewResultPanelProps) => {
    const recommendationSummary = result
        ? (Object.entries(result.recommendationCounts) as [ReviewRecommendation, number][])
            .filter(([, count]) => count > 0)
            .map(([recommendation, count]) => `${RECOMMENDATION_LABEL[recommendation]} ${count}`)
            .join(' · ') || '-'
        : '-';

    return (
        <section className="rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-950">
            <div className="flex items-start justify-between gap-3 border-b border-slate-200 px-4 py-3.5 dark:border-slate-800">
                <div>
                    <div className="flex items-center gap-2">
                        <BarChart3 className="h-4 w-4 text-blue-500" />
                        <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-50">심사자 심사 결과</h3>
                    </div>
                    <p className="mt-1 text-xs text-slate-400">평가점수와 추천, 공개·비공개 의견을 함께 검토합니다.</p>
                </div>
                {result && <span className="shrink-0 text-xs tabular-nums text-slate-400">{result.completedCount} / {result.assignedCount}명</span>}
            </div>

            <div className="p-4">
                {loading && <EvidenceSkeleton lines={5} />}

                {!loading && error && (
                    <EvidenceError message="심사 결과를 불러오지 못했습니다." onRetry={onRetry} />
                )}

                {!loading && !error && result && (
                    <div className="space-y-4">
                        <dl className="grid grid-cols-3 overflow-hidden rounded-lg border border-slate-200 bg-slate-50/80 dark:border-slate-800 dark:bg-slate-900/40">
                            <EvidenceMetric label="제출 완료" value={`${result.completedCount} / ${result.assignedCount}`} />
                            <EvidenceMetric label="전체 평균" value={formatReviewScore(result.averageScore)} accent />
                            <EvidenceMetric label="추천 결과" value={recommendationSummary} />
                        </dl>

                        {result.evaluationSummaries.length > 0 && (
                            <div>
                                <p className="text-xs font-semibold text-slate-500 dark:text-slate-400">평가항목별 평균</p>
                                <div className="mt-2 space-y-2">
                                    {result.evaluationSummaries.map((item) => (
                                        <div key={item.evaluationItemSeq} className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-3 rounded-lg bg-slate-50 px-3 py-2 dark:bg-slate-900/50">
                                            <div className="min-w-0">
                                                <p className="truncate text-xs font-medium text-slate-700 dark:text-slate-300">{item.itemName}</p>
                                                <div className="mt-1.5 h-1 overflow-hidden rounded-full bg-slate-200 dark:bg-slate-800">
                                                    <div className="h-full rounded-full bg-blue-500" style={{ width: `${Math.min(100, (item.averageScore / 6) * 100)}%` }} />
                                                </div>
                                            </div>
                                            <span className="text-xs font-bold tabular-nums text-blue-600 dark:text-blue-400">{item.averageScore.toFixed(2)}</span>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        )}

                        <div className="space-y-3">
                            {result.reviews.length === 0 ? (
                                <p className="rounded-lg border border-dashed border-slate-300 p-5 text-center text-sm text-slate-400 dark:border-slate-700">배정된 심사자가 없습니다.</p>
                            ) : result.reviews.map((review) => (
                                <ReviewerEvidence key={review.assignmentSeq} review={review} />
                            ))}
                        </div>
                    </div>
                )}
            </div>
        </section>
    );
};

const ReviewerEvidence = ({ review }: { review: AbstractReviewResultReviewer }) => {
    const submitted = review.reviewStatus === 'submitted';

    return (
        <article className="rounded-lg border border-slate-200 p-3 dark:border-slate-800">
            <div className="flex flex-wrap items-start justify-between gap-2">
                <div>
                    <p className="text-sm font-semibold text-slate-900 dark:text-slate-50">{review.reviewerName}</p>
                    <p className="mt-0.5 text-xs text-slate-400">{[review.affiliation, review.department].filter(Boolean).join(' · ') || '-'}</p>
                </div>
                <div className="flex flex-wrap items-center gap-1.5">
                    <span className={`rounded-full px-2 py-0.5 text-[11px] font-semibold ${submitted ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300' : 'bg-amber-50 text-amber-700 dark:bg-amber-950/40 dark:text-amber-300'}`}>
                        {submitted ? '제출 완료' : ASSIGNMENT_STATUS_LABEL[review.assignmentStatus]}
                    </span>
                    {submitted && review.recommendation && (
                        <span className={`rounded-full px-2 py-0.5 text-[11px] font-semibold ${review.recommendation === 'accept' ? 'bg-blue-50 text-blue-700 dark:bg-blue-950/40 dark:text-blue-300' : 'bg-rose-50 text-rose-700 dark:bg-rose-950/40 dark:text-rose-300'}`}>
                            {RECOMMENDATION_LABEL[review.recommendation]}
                        </span>
                    )}
                </div>
            </div>

            {!submitted ? (
                <p className="mt-3 rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-500 dark:bg-slate-900/50 dark:text-slate-400">아직 심사 결과를 제출하지 않았습니다.</p>
            ) : (
                <div className="mt-3 space-y-3">
                    <div className="flex items-center justify-between border-y border-slate-200 py-2 dark:border-slate-800">
                        <span className="text-xs text-slate-500 dark:text-slate-400">심사자 평균</span>
                        <span className="text-sm font-bold tabular-nums text-blue-600 dark:text-blue-400">{formatReviewScore(review.averageScore)}</span>
                    </div>

                    {review.scores.length > 0 && (
                        <div className="overflow-x-auto rounded-lg border border-slate-200 dark:border-slate-800">
                            <table className="w-full min-w-[30rem] text-left text-xs">
                                <thead className="bg-slate-50 dark:bg-slate-900/60">
                                    <tr><th className="px-3 py-2">평가항목</th><th className="px-3 py-2 text-center">점수</th><th className="px-3 py-2">항목 의견</th></tr>
                                </thead>
                                <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                                    {review.scores.map((score) => (
                                        <tr key={score.evaluationItemSeq}>
                                            <td className="px-3 py-2 font-medium text-slate-700 dark:text-slate-300">{score.itemName}</td>
                                            <td className="px-3 py-2 text-center font-bold tabular-nums text-blue-600 dark:text-blue-400">{score.score} / 6</td>
                                            <td className="px-3 py-2 whitespace-pre-wrap text-slate-500 dark:text-slate-400">{score.itemComment || '-'}</td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}

                    <div className="grid grid-cols-1 gap-2 lg:grid-cols-2">
                        <ReviewComment label="종합 의견" value={review.overallComment} />
                        <ReviewComment label="관리자 전용 의견" value={review.confidentialComment} privateComment />
                    </div>
                </div>
            )}
        </article>
    );
};

const ReviewComment = ({ label, value, privateComment = false }: { label: string; value?: string | null; privateComment?: boolean }) => (
    <div className={`rounded-lg border p-3 ${privateComment ? 'border-amber-200 bg-amber-50/50 dark:border-amber-900/60 dark:bg-amber-950/20' : 'border-slate-200 dark:border-slate-800'}`}>
        <p className={`text-[11px] font-semibold ${privateComment ? 'text-amber-700 dark:text-amber-300' : 'text-slate-500 dark:text-slate-400'}`}>{label}</p>
        <p className="mt-1.5 whitespace-pre-wrap text-xs leading-5 text-slate-700 dark:text-slate-300">{formatText(value)}</p>
    </div>
);

const EvidenceMetric = ({ label, value, accent = false }: { label: string; value: string; accent?: boolean }) => (
    <div className="border-r border-slate-200 p-3 last:border-r-0 dark:border-slate-800">
        <dt className="text-[11px] font-semibold text-slate-400">{label}</dt>
        <dd className={`mt-1 text-xs font-bold tabular-nums ${accent ? 'text-blue-600 dark:text-blue-400' : 'text-slate-800 dark:text-slate-200'}`}>{value}</dd>
    </div>
);

interface SimilarityEvidencePanelProps {
    loading: boolean;
    analysisActive: boolean;
    excluded: boolean;
    error: string;
    result: AbstractSimilarityResponse | null;
    onRetry: () => void;
    onOpenDetail: () => void;
}

const SimilarityEvidencePanel = ({ loading, analysisActive, excluded, error, result, onRetry, onOpenDetail }: SimilarityEvidencePanelProps) => {
    if (loading) {
        return (
            <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-slate-950">
                <EvidenceSkeleton lines={4} />
            </section>
        );
    }

    if (analysisActive || excluded) {
        return (
            <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-slate-950">
                <div className="flex items-center gap-2"><ScanSearch className="h-4 w-4 text-blue-500" /><h3 className="text-sm font-semibold">유사 초록 분석 결과</h3></div>
                <p className="mt-3 rounded-lg bg-slate-50 p-3 text-sm text-slate-500 dark:bg-slate-900/50 dark:text-slate-400">
                    {analysisActive ? '전체 유사도 분석이 진행 중입니다. 완료 후 결과를 확인할 수 있습니다.' : '임시저장 상태의 초록은 유사도 분석 대상이 아닙니다.'}
                </p>
            </section>
        );
    }

    if (error) {
        return (
            <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-slate-950">
                <div className="mb-3 flex items-center gap-2"><ScanSearch className="h-4 w-4 text-blue-500" /><h3 className="text-sm font-semibold">유사 초록 분석 결과</h3></div>
                <EvidenceError message="유사도 분석 결과를 불러오지 못했습니다." onRetry={onRetry} />
            </section>
        );
    }

    if (!result) {
        return (
            <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-slate-950">
                <div className="flex items-center gap-2"><ScanSearch className="h-4 w-4 text-blue-500 dark:text-blue-400" /><h3 className="text-sm font-semibold">유사 초록 분석 결과</h3></div>
                <p className="mt-3 rounded-lg bg-slate-50 p-3 text-sm text-slate-500 dark:bg-slate-900/50 dark:text-slate-400">
                    분석된 유사도가 없습니다.
                </p>
            </section>
        );
    }

    return <SimilarityResultPanel result={result} onOpenDetail={onOpenDetail} />;
};

const EvidenceSkeleton = ({ lines }: { lines: number }) => (
    <div className="animate-pulse space-y-3" aria-label="결과 불러오는 중">
        <div className="h-4 w-40 rounded bg-slate-200 dark:bg-slate-800" />
        {Array.from({ length: lines }, (_, index) => <div key={index} className="h-10 rounded-lg bg-slate-100 dark:bg-slate-900" />)}
    </div>
);

const EvidenceError = ({ message, onRetry }: { message: string; onRetry: () => void }) => (
    <div className="rounded-lg border border-rose-200 bg-rose-50 p-3 dark:border-rose-900/60 dark:bg-rose-950/20">
        <p className="text-xs text-rose-700 dark:text-rose-300">{message}</p>
        <button type="button" onClick={onRetry} className="mt-2 text-xs font-semibold text-rose-700 underline underline-offset-2 dark:text-rose-300">다시 불러오기</button>
    </div>
);

const SECTION_LABEL: Record<AbstractSimilarityMatch['highestSection'], string> = {
    title: '제목',
    objective: 'Objective',
    methods: 'Methods',
    results: 'Results',
    conclusions: 'Conclusions'
};

const formatSimilarity = (value?: number | null) => (
    value == null ? '-' : `${value.toFixed(1)}%`
);

const similarityTone = (value: number) => {
    if (value >= 90) return 'border-rose-200 bg-rose-50 text-rose-700 dark:border-rose-900/60 dark:bg-rose-950/30 dark:text-rose-300';
    if (value >= 80) return 'border-amber-200 bg-amber-50 text-amber-700 dark:border-amber-900/60 dark:bg-amber-950/30 dark:text-amber-300';
    return 'border-slate-200 bg-slate-50 text-slate-700 dark:border-slate-800 dark:bg-slate-900/40 dark:text-slate-300';
};

const SimilarityResultPanel = ({ result, onOpenDetail }: { result: AbstractSimilarityResponse; onOpenDetail: () => void }) => (
    <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-slate-950">
        <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
                <div className="flex items-center gap-2">
                    <ScanSearch className="h-4 w-4 text-blue-500" />
                    <h3 className="text-sm font-semibold">유사 초록 분석 결과</h3>
                </div>
                <p className="mt-1 text-xs text-slate-400">
                    기존 초록 {result.comparedCount.toLocaleString()}건을 비교했습니다. 점수는 표절률이 아닌 AI 의미 유사도입니다.
                </p>
            </div>
            <button
                type="button"
                onClick={onOpenDetail}
                disabled={result.matches.length === 0}
                className="inline-flex shrink-0 items-center gap-1.5 rounded-lg bg-blue-600 px-3 py-2 text-xs font-semibold text-white shadow-sm shadow-blue-600/20 transition-all hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500/40 focus:ring-offset-2 active:translate-y-px disabled:cursor-not-allowed disabled:opacity-40 dark:bg-blue-600 dark:text-white dark:hover:bg-blue-700 dark:focus:ring-offset-slate-950"
            >
                <Eye className="h-3.5 w-3.5" />
                상세보기
            </button>
        </div>

        {result.stale && (
            <div className="mt-4 flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2.5 text-amber-800 dark:border-amber-900/60 dark:bg-amber-950/30 dark:text-amber-200">
                <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
                <p className="text-xs leading-5">분석 이후 초록 내용이 변경되었습니다. 정확한 결과를 위해 전체 유사도 분석을 다시 실행해 주세요.</p>
            </div>
        )}

        <div className="mt-4 space-y-3">
            {result.matches.length === 0 ? (
                <p className="rounded-lg bg-slate-50 p-3 text-sm text-slate-500 dark:bg-slate-900/40 dark:text-slate-400">
                    비교할 수 있는 기존 초록이 없습니다.
                </p>
            ) : result.matches.map((match, index) => (
                <div key={match.abstractSeq} className="rounded-lg border border-slate-200 p-3 dark:border-slate-800">
                    <div className="flex flex-col justify-between gap-2 sm:flex-row sm:items-start">
                        <div className="min-w-0">
                            <p className="text-xs font-semibold text-slate-500 dark:text-slate-400">
                                {index + 1}. {match.submissionNo || `#${match.abstractSeq}`}
                            </p>
                            <p className="mt-1 text-sm font-semibold text-slate-900 dark:text-slate-50">{match.title}</p>
                            <p className="mt-1 text-xs text-slate-400">
                                최고 항목 {SECTION_LABEL[match.highestSection]} {formatSimilarity(match.highestSimilarity)}
                            </p>
                        </div>
                        <span className={`shrink-0 rounded-full border px-3 py-1 text-xs font-bold ${similarityTone(match.overallSimilarity)}`}>
                            종합 {formatSimilarity(match.overallSimilarity)}
                        </span>
                    </div>
                    <div className="mt-3 grid grid-cols-2 gap-2 text-xs sm:grid-cols-5">
                        <SimilaritySection label="제목" value={match.titleSimilarity} />
                        <SimilaritySection label="Objective" value={match.objectiveSimilarity} />
                        <SimilaritySection label="Methods" value={match.methodsSimilarity} />
                        <SimilaritySection label="Results" value={match.resultsSimilarity} />
                        <SimilaritySection label="Conclusions" value={match.conclusionsSimilarity} />
                    </div>
                </div>
            ))}
        </div>
    </section>
);

const SimilaritySection = ({ label, value }: { label: string; value?: number | null }) => (
    <div className="rounded-lg bg-slate-50 p-2 dark:bg-slate-900/40">
        <p className="text-slate-400">{label}</p>
        <p className="mt-1 font-semibold text-slate-700 dark:text-slate-300">{formatSimilarity(value)}</p>
    </div>
);
