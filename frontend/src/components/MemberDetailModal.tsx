import { DraggableModal } from './DraggableModal';
import { useEffect, useState } from 'react';
import {
    CalendarDays,
    CheckCircle2,
    ClipboardList,
    FileText,
    Loader2,
    Mail,
    MapPin,
    Phone,
    UserRound,
    X
} from 'lucide-react';
import type { NotificationType } from './NotificationToast';

type MemberType = 'international' | 'domestic';

interface MemberDetailMember {
    seq: number;
    memberType: MemberType;
    email: string;
    firstName: string;
    lastName: string;
    institution: string;
    department?: string | null;
    positionTitle?: string | null;
    country?: string | null;
    mobile: string;
    newsletter: boolean;
    createdAt?: string | null;
    updatedAt?: string | null;
}

interface MemberPreRegistration {
    seq: number;
    registrationNumber: string;
    categoryName: string;
    periodType: string;
    currency: string;
    feeAmount: number;
    applicationStatus: string;
    paymentStatus: string;
    paidAmount?: number | null;
    createdAt?: string | null;
}

interface MemberAbstractSubmission {
    seq: number;
    submissionNo?: string | null;
    presentationTypeName: string;
    categoryName: string;
    title: string;
    status: string;
    authorCount?: number | null;
    submittedAt?: string | null;
    createdAt?: string | null;
}

interface MemberDetailResponse {
    member: MemberDetailMember;
    preRegistrations: MemberPreRegistration[];
    abstractSubmissions: MemberAbstractSubmission[];
}

interface MemberDetailModalProps {
    memberSeq: number | null;
    onClose: () => void;
    onNotify: (type: NotificationType, message: string) => void;
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

const formatCurrency = (value: number | null | undefined, currency: string) => {
    if (value == null) return '-';
    try {
        return new Intl.NumberFormat('ko-KR', { style: 'currency', currency: currency || 'USD' }).format(value);
    } catch {
        return `${currency || ''} ${value.toLocaleString()}`.trim();
    }
};

const applicationStatusLabels: Record<string, string> = {
    SUBMITTED: '신청 완료',
    CANCELLED: '취소'
};

const paymentStatusLabels: Record<string, string> = {
    UNPAID: '결제 대기',
    PAID: '결제 완료',
    REFUNDED: '환불',
    FAILED: '결제 실패'
};

const abstractStatusLabels: Record<string, string> = {
    draft: '작성 중',
    submitted: '제출 완료',
    under_review: '심사 중',
    approved: '승인',
    rejected: '반려'
};

const statusClassNames: Record<string, string> = {
    SUBMITTED: 'border-blue-200 bg-blue-50 text-blue-700 dark:border-blue-900/60 dark:bg-blue-950/40 dark:text-blue-300',
    CANCELLED: 'border-rose-200 bg-rose-50 text-rose-700 dark:border-rose-900/60 dark:bg-rose-950/40 dark:text-rose-300',
    PAID: 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-900/60 dark:bg-emerald-950/40 dark:text-emerald-300',
    UNPAID: 'border-amber-200 bg-amber-50 text-amber-700 dark:border-amber-900/60 dark:bg-amber-950/40 dark:text-amber-300',
    REFUNDED: 'border-violet-200 bg-violet-50 text-violet-700 dark:border-violet-900/60 dark:bg-violet-950/40 dark:text-violet-300',
    FAILED: 'border-rose-200 bg-rose-50 text-rose-700 dark:border-rose-900/60 dark:bg-rose-950/40 dark:text-rose-300',
    approved: 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-900/60 dark:bg-emerald-950/40 dark:text-emerald-300',
    rejected: 'border-rose-200 bg-rose-50 text-rose-700 dark:border-rose-900/60 dark:bg-rose-950/40 dark:text-rose-300',
    under_review: 'border-violet-200 bg-violet-50 text-violet-700 dark:border-violet-900/60 dark:bg-violet-950/40 dark:text-violet-300',
    submitted: 'border-blue-200 bg-blue-50 text-blue-700 dark:border-blue-900/60 dark:bg-blue-950/40 dark:text-blue-300',
    draft: 'border-slate-200 bg-slate-50 text-slate-600 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300'
};

const StatusBadge = ({ value, label }: { value: string; label: string }) => (
    <span className={`inline-flex rounded-full border px-2.5 py-0.5 text-xs font-semibold ${statusClassNames[value] ?? statusClassNames.draft}`}>
        {label}
    </span>
);

const DetailItem = ({ label, value }: { label: string; value: React.ReactNode }) => (
    <div className="rounded-lg border border-slate-200 bg-slate-50/60 px-3 py-3 dark:border-slate-800 dark:bg-slate-900/50">
        <dt className="text-xs font-medium text-slate-400">{label}</dt>
        <dd className="mt-1.5 break-words text-sm font-medium text-slate-700 dark:text-slate-200">{value || '-'}</dd>
    </div>
);

export const MemberDetailModal = ({ memberSeq, onClose, onNotify }: MemberDetailModalProps) => {
    const [detail, setDetail] = useState<MemberDetailResponse | null>(null);
    const [isLoading, setIsLoading] = useState(false);
    const [errorMessage, setErrorMessage] = useState('');

    useEffect(() => {
        if (memberSeq == null) return;

        const controller = new AbortController();
        document.body.style.overflow = 'hidden';
        setDetail(null);
        setErrorMessage('');
        setIsLoading(true);

        const fetchDetail = async () => {
            try {
                const response = await fetch(`/api/admin/members/${memberSeq}`, { signal: controller.signal });
                if (!response.ok) {
                    throw new Error(await response.text() || '회원 상세정보를 불러오지 못했습니다.');
                }
                setDetail(await response.json() as MemberDetailResponse);
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') return;
                const message = error instanceof Error ? error.message : '회원 상세정보를 불러오지 못했습니다.';
                setErrorMessage(message);
                onNotify('error', message);
            } finally {
                setIsLoading(false);
            }
        };

        void fetchDetail();
        return () => {
            controller.abort();
            document.body.style.overflow = '';
        };
    }, [memberSeq, onNotify]);

    if (memberSeq == null) return null;

    const member = detail?.member;
    const memberName = member ? `${member.firstName} ${member.lastName}`.trim() : '회원 상세';

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" role="dialog" aria-modal="true" aria-label="회원 상세">
            <button className="absolute inset-0 bg-slate-950/60 backdrop-blur-sm" onClick={onClose} aria-label="회원 상세 닫기" />
            <DraggableModal className="relative z-10 flex max-h-[92vh] w-full max-w-5xl flex-col overflow-hidden rounded-xl border border-slate-200 bg-white shadow-2xl dark:border-slate-800 dark:bg-slate-950">
                <div data-modal-drag-handle className="cursor-move select-none touch-none flex items-center justify-between border-b border-slate-200 px-4 py-4 dark:border-slate-800 md:px-6">
                    <div className="flex min-w-0 items-center gap-3">
                        <div className="rounded-lg bg-blue-50 p-2 text-blue-600 dark:bg-blue-950/40 dark:text-blue-300">
                            <UserRound className="h-5 w-5" />
                        </div>
                        <div className="min-w-0">
                            <h3 className="truncate font-bold text-slate-900 dark:text-slate-50">{memberName}</h3>
                            <p className="mt-0.5 text-xs text-slate-400">회원번호 #{memberSeq}</p>
                        </div>
                    </div>
                    <button onClick={onClose} className="rounded-lg p-2 text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-900" aria-label="닫기">
                        <X className="h-5 w-5" />
                    </button>
                </div>

                <div className="overflow-y-auto p-4 md:p-6">
                    {isLoading && (
                        <div className="flex items-center justify-center gap-2 py-24 text-sm text-slate-400">
                            <Loader2 className="h-5 w-5 animate-spin" /> 회원 상세정보를 불러오는 중입니다.
                        </div>
                    )}

                    {!isLoading && errorMessage && (
                        <p className="py-24 text-center text-sm text-slate-500 dark:text-slate-400">
                            회원 상세정보를 불러오지 못했습니다. 창을 다시 열어 시도해주세요.
                        </p>
                    )}

                    {!isLoading && member && detail && (
                        <div className="space-y-7">
                            <section>
                                <div className="mb-3 flex items-center gap-2">
                                    <UserRound className="h-4 w-4 text-blue-500" />
                                    <h4 className="text-sm font-bold text-slate-900 dark:text-slate-50">회원 정보</h4>
                                </div>
                                <dl className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
                                    <DetailItem label="회원 구분" value={member.memberType === 'international' ? 'International' : 'Domestic'} />
                                    <DetailItem label="이메일" value={<span className="inline-flex items-center gap-1.5"><Mail className="h-3.5 w-3.5 text-slate-400" />{member.email}</span>} />
                                    <DetailItem label="소속 기관" value={member.institution} />
                                    <DetailItem label="부서 / 학과" value={member.department} />
                                    <DetailItem label="직책" value={member.positionTitle} />
                                    <DetailItem label="국가" value={<span className="inline-flex items-center gap-1.5"><MapPin className="h-3.5 w-3.5 text-slate-400" />{member.country || '-'}</span>} />
                                    <DetailItem label="모바일" value={<span className="inline-flex items-center gap-1.5"><Phone className="h-3.5 w-3.5 text-slate-400" />{member.mobile}</span>} />
                                    <DetailItem label="뉴스레터" value={member.newsletter ? <span className="inline-flex items-center gap-1.5 text-emerald-600 dark:text-emerald-400"><CheckCircle2 className="h-3.5 w-3.5" />수신</span> : '미수신'} />
                                    <DetailItem label="가입일" value={formatDate(member.createdAt)} />
                                    <DetailItem label="수정일" value={formatDate(member.updatedAt)} />
                                </dl>
                            </section>

                            <section>
                                <div className="mb-3 flex items-center justify-between">
                                    <div className="flex items-center gap-2">
                                        <ClipboardList className="h-4 w-4 text-violet-500" />
                                        <h4 className="text-sm font-bold text-slate-900 dark:text-slate-50">사전등록 내역</h4>
                                    </div>
                                    <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-bold text-slate-500 dark:bg-slate-900 dark:text-slate-300">{detail.preRegistrations.length}건</span>
                                </div>
                                {detail.preRegistrations.length === 0 ? (
                                    <div className="rounded-lg border border-dashed border-slate-200 px-4 py-8 text-center text-sm text-slate-400 dark:border-slate-800">사전등록 내역이 없습니다.</div>
                                ) : (
                                    <div className="space-y-3">
                                        {detail.preRegistrations.map((registration) => (
                                            <article key={registration.seq} className="rounded-lg border border-slate-200 p-4 dark:border-slate-800">
                                                <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-start">
                                                    <div>
                                                        <p className="font-mono text-xs font-bold text-blue-600 dark:text-blue-400">{registration.registrationNumber}</p>
                                                        <p className="mt-1.5 text-sm font-semibold text-slate-800 dark:text-slate-100">{registration.categoryName}</p>
                                                        <p className="mt-1 text-xs text-slate-400">{registration.periodType === 'EARLY_BIRD' ? 'Early Bird' : 'Regular'} · 신청일 {formatDate(registration.createdAt)}</p>
                                                    </div>
                                                    <div className="flex flex-wrap items-center gap-2 sm:justify-end">
                                                        <StatusBadge value={registration.applicationStatus} label={applicationStatusLabels[registration.applicationStatus] ?? registration.applicationStatus} />
                                                        <StatusBadge value={registration.paymentStatus} label={paymentStatusLabels[registration.paymentStatus] ?? registration.paymentStatus} />
                                                        <span className="text-sm font-bold text-slate-700 dark:text-slate-200">{formatCurrency(registration.paidAmount ?? registration.feeAmount, registration.currency)}</span>
                                                    </div>
                                                </div>
                                            </article>
                                        ))}
                                    </div>
                                )}
                            </section>

                            <section>
                                <div className="mb-3 flex items-center justify-between">
                                    <div className="flex items-center gap-2">
                                        <FileText className="h-4 w-4 text-emerald-500" />
                                        <h4 className="text-sm font-bold text-slate-900 dark:text-slate-50">초록 제출 내역</h4>
                                    </div>
                                    <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-bold text-slate-500 dark:bg-slate-900 dark:text-slate-300">{detail.abstractSubmissions.length}건</span>
                                </div>
                                {detail.abstractSubmissions.length === 0 ? (
                                    <div className="rounded-lg border border-dashed border-slate-200 px-4 py-8 text-center text-sm text-slate-400 dark:border-slate-800">초록 제출 내역이 없습니다.</div>
                                ) : (
                                    <div className="space-y-3">
                                        {detail.abstractSubmissions.map((submission) => (
                                            <article key={submission.seq} className="rounded-lg border border-slate-200 p-4 dark:border-slate-800">
                                                <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-start">
                                                    <div className="min-w-0">
                                                        <p className="font-mono text-xs font-bold text-blue-600 dark:text-blue-400">{submission.submissionNo || `임시 #${submission.seq}`}</p>
                                                        <p className="mt-1.5 break-words text-sm font-semibold text-slate-800 dark:text-slate-100">{submission.title}</p>
                                                        <p className="mt-1 text-xs text-slate-400">{submission.presentationTypeName} · {submission.categoryName} · 저자 {submission.authorCount ?? 0}명</p>
                                                    </div>
                                                    <div className="flex shrink-0 flex-col items-start gap-2 sm:items-end">
                                                        <StatusBadge value={submission.status} label={abstractStatusLabels[submission.status] ?? submission.status} />
                                                        <span className="inline-flex items-center gap-1 text-xs text-slate-400"><CalendarDays className="h-3.5 w-3.5" />{formatDate(submission.submittedAt ?? submission.createdAt)}</span>
                                                    </div>
                                                </div>
                                            </article>
                                        ))}
                                    </div>
                                )}
                            </section>
                        </div>
                    )}
                </div>
            </DraggableModal>
        </div>
    );
};
