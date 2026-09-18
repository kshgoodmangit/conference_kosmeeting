import { DraggableModal } from './DraggableModal';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { BadgeCheck, LoaderCircle, UserRoundPen, X } from 'lucide-react';
import type { NotificationType } from './NotificationToast';

interface ReviewerCategory {
    code: number;
    name: string;
}

interface ReviewerProfile {
    affiliation: string;
    department: string;
    positionTitle?: string | null;
    phoneNumber?: string | null;
    contactEmail?: string | null;
    isUsed: 'Y' | 'N';
    expertiseCodes: number[];
}

interface ReviewerOwnProfile {
    seq: number;
    email: string;
    adminName: string;
    role: string;
    status: string;
    reviewerProfile?: ReviewerProfile | null;
}

interface Props {
    isOpen: boolean;
    onClose: () => void;
    onSaved: (adminName: string) => void;
    onNotify: (type: NotificationType, message: string) => void;
}

const inputClassName = 'w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-violet-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50';

export const ReviewerProfileModal = ({ isOpen, onClose, onSaved, onNotify }: Props) => {
    const [profile, setProfile] = useState<ReviewerOwnProfile | null>(null);
    const [categories, setCategories] = useState<ReviewerCategory[]>([]);
    const [adminName, setAdminName] = useState('');
    const [affiliation, setAffiliation] = useState('');
    const [department, setDepartment] = useState('');
    const [positionTitle, setPositionTitle] = useState('');
    const [phoneNumber, setPhoneNumber] = useState('');
    const [contactEmail, setContactEmail] = useState('');
    const [expertiseCodes, setExpertiseCodes] = useState<number[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const onNotifyRef = useRef(onNotify);

    useEffect(() => {
        onNotifyRef.current = onNotify;
    }, [onNotify]);

    useEffect(() => {
        if (!isOpen) return;
        const previousOverflow = document.body.style.overflow;
        const handleKeyDown = (event: KeyboardEvent) => {
            if (event.key === 'Escape') onClose();
        };
        document.body.style.overflow = 'hidden';
        document.addEventListener('keydown', handleKeyDown);
        return () => {
            document.body.style.overflow = previousOverflow;
            document.removeEventListener('keydown', handleKeyDown);
        };
    }, [isOpen, onClose]);

    useEffect(() => {
        if (!isOpen) return;

        const controller = new AbortController();
        const load = async () => {
            try {
                const [profileResponse, categoryResponse] = await Promise.all([
                    fetch('/api/reviewer/profile', { signal: controller.signal }),
                    fetch('/api/reviewer/profile/categories', { signal: controller.signal })
                ]);
                if (!profileResponse.ok) {
                    throw new Error(await profileResponse.text() || 'Reviewer 정보를 불러오지 못했습니다.');
                }
                if (!categoryResponse.ok) {
                    throw new Error(await categoryResponse.text() || 'Reviewer 전문분야를 불러오지 못했습니다.');
                }

                const data = await profileResponse.json() as ReviewerOwnProfile;
                const reviewerProfile = data.reviewerProfile;
                setProfile(data);
                setCategories(await categoryResponse.json() as ReviewerCategory[]);
                setAdminName(data.adminName ?? '');
                setAffiliation(reviewerProfile?.affiliation ?? '');
                setDepartment(reviewerProfile?.department ?? '');
                setPositionTitle(reviewerProfile?.positionTitle ?? '');
                setPhoneNumber(reviewerProfile?.phoneNumber ?? '');
                setContactEmail(reviewerProfile?.contactEmail ?? '');
                setExpertiseCodes(reviewerProfile?.expertiseCodes ?? []);
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') return;
                const message = error instanceof Error ? error.message : 'Reviewer 정보를 불러오지 못했습니다.';
                onNotifyRef.current('error', message);
            } finally {
                if (!controller.signal.aborted) setIsLoading(false);
            }
        };
        void load();
        return () => controller.abort();
    }, [isOpen]);

    const toggleExpertise = (code: number) => {
        setExpertiseCodes((current) => current.includes(code)
            ? current.filter((item) => item !== code)
            : [...current, code]);
    };

    const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const requiredFields = [
            ['이름', adminName],
            ['소속기관', affiliation],
            ['부서·학과·진료과', department],
            ['직위', positionTitle],
            ['연락처', phoneNumber],
            ['이메일', contactEmail]
        ] as const;
        const missingField = requiredFields.find(([, value]) => !value.trim());
        if (missingField) {
            onNotify('error', `${missingField[0]}을(를) 입력해 주세요.`);
            return;
        }
        setIsSubmitting(true);
        try {
            const response = await fetch('/api/reviewer/profile', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    adminName: adminName.trim(),
                    affiliation: affiliation.trim(),
                    department: department.trim(),
                    positionTitle: positionTitle.trim(),
                    phoneNumber: phoneNumber.trim(),
                    contactEmail: contactEmail.trim(),
                    expertiseCodes
                })
            });
            if (!response.ok) {
                throw new Error(await response.text() || 'Reviewer 정보를 수정하지 못했습니다.');
            }

            const updated = await response.json() as ReviewerOwnProfile;
            onSaved(updated.adminName);
            onNotify('success', '내 정보가 수정되었습니다.');
            onClose();
        } catch (error) {
            const message = error instanceof Error ? error.message : 'Reviewer 정보를 수정하지 못했습니다.';
            onNotify('error', message);
        } finally {
            setIsSubmitting(false);
        }
    };

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-[140] flex items-center justify-center p-4" role="dialog" aria-modal="true" aria-labelledby="reviewer-profile-title">
            <button type="button" className="absolute inset-0 bg-slate-950/60" onClick={onClose} aria-label="내 정보 수정 닫기" />
            <DraggableModal className="relative z-10 flex max-h-[90vh] w-full max-w-2xl flex-col overflow-hidden rounded-xl border border-slate-200 bg-white shadow-2xl dark:border-slate-800 dark:bg-slate-950">
                <div data-modal-drag-handle className="cursor-move select-none touch-none flex shrink-0 items-center justify-between border-b border-slate-200 p-4 dark:border-slate-800 md:p-5">
                    <div className="flex items-center gap-2">
                        <UserRoundPen className="h-5 w-5 text-violet-500" />
                        <div>
                            <h2 id="reviewer-profile-title" className="font-bold text-slate-900 dark:text-slate-50">내 정보 수정</h2>
                            <p className="mt-0.5 text-xs text-slate-400">심사자 연락처와 전문분야를 관리합니다.</p>
                        </div>
                    </div>
                    <button type="button" onClick={onClose} className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800" aria-label="닫기">
                        <X className="h-4 w-4" />
                    </button>
                </div>

                {isLoading ? (
                    <div className="flex min-h-72 items-center justify-center text-sm text-slate-500">
                        <LoaderCircle className="mr-2 h-5 w-5 animate-spin" /> 정보를 불러오는 중입니다.
                    </div>
                ) : (
                    <form onSubmit={handleSubmit} className="overflow-y-auto">
                        <div className="space-y-5 p-4 md:p-5">

                            <div className="grid gap-4 rounded-xl border border-slate-200 bg-slate-50/70 p-4 dark:border-slate-800 dark:bg-slate-900/40 md:grid-cols-2">
                                <ReadOnlyItem label="로그인 아이디" value={profile?.email ?? '-'} />
                                <ReadOnlyItem label="계정 권한" value="Reviewer" />
                                <ReadOnlyItem label="계정 상태" value={profile?.status === 'active' ? '사용' : '미사용'} />
                                <ReadOnlyItem label="심사 배정 상태" value={profile?.reviewerProfile?.isUsed === 'Y' ? '배정 가능' : '배정 중지'} />
                            </div>

                            <div className="grid gap-4 md:grid-cols-2">
                                <Field label="이름" value={adminName} onChange={setAdminName} maxLength={255} required />
                                <Field label="소속기관" value={affiliation} onChange={setAffiliation} maxLength={255} required />
                                <Field label="부서·학과·진료과" value={department} onChange={setDepartment} maxLength={255} required />
                                <Field label="직위" value={positionTitle} onChange={setPositionTitle} maxLength={100} required />
                                <Field label="연락처" value={phoneNumber} onChange={setPhoneNumber} maxLength={100} type="tel" required />
                                <Field label="이메일" value={contactEmail} onChange={setContactEmail} maxLength={255} type="email" required />
                            </div>

                            <fieldset>
                                <legend className="flex items-center gap-2 text-xs font-semibold text-slate-500">
                                    <BadgeCheck className="h-4 w-4 text-violet-500" /> 심사 전문분야
                                </legend>
                                <div className="mt-2 grid gap-2 sm:grid-cols-2">
                                    {categories.map((category) => (
                                        <label key={category.code} className="flex cursor-pointer items-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-sm hover:bg-slate-50 dark:border-slate-800 dark:hover:bg-slate-900">
                                            <input type="checkbox" checked={expertiseCodes.includes(category.code)} onChange={() => toggleExpertise(category.code)} className="h-4 w-4 rounded border-slate-300 text-violet-600 focus:ring-violet-500" />
                                            <span>{category.name}</span>
                                        </label>
                                    ))}
                                    {categories.length === 0 && <p className="text-xs text-slate-400">등록된 전문분야가 없습니다.</p>}
                                </div>
                            </fieldset>
                        </div>

                        <div className="flex shrink-0 justify-end gap-2 border-t border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-950">
                            <button type="button" onClick={onClose} className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-600 hover:bg-slate-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900">취소</button>
                            <button type="submit" disabled={isSubmitting || !profile} className="rounded-lg bg-violet-600 px-4 py-2 text-sm font-semibold text-white hover:bg-violet-700 disabled:opacity-50">
                                {isSubmitting ? '저장 중' : '저장'}
                            </button>
                        </div>
                    </form>
                )}
            </DraggableModal>
        </div>
    );
};

const ReadOnlyItem = ({ label, value }: { label: string; value: string }) => (
    <div>
        <p className="text-[11px] font-semibold text-slate-400">{label}</p>
        <p className="mt-1 truncate text-sm font-medium text-slate-700 dark:text-slate-200">{value}</p>
    </div>
);

const Field = ({ label, value, onChange, maxLength, required = false, type = 'text' }: {
    label: string;
    value: string;
    onChange: (value: string) => void;
    maxLength: number;
    required?: boolean;
    type?: string;
}) => (
    <label>
        <span className="mb-1.5 block text-xs font-semibold text-slate-500">{label}{required ? ' *' : ''}</span>
        <input type={type} value={value} onChange={(event) => onChange(event.target.value)} maxLength={maxLength} required={required} className={inputClassName} />
    </label>
);
