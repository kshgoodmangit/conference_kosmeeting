import { DraggableModalForm } from './DraggableModal';
import { useEffect, useState } from 'react';
import { Eye, EyeOff, X } from 'lucide-react';
import type { NotificationType } from './NotificationToast';

export interface EditableAdminAccount {
    seq: number;
    email: string;
    adminName: string;
    affiliation?: string | null;
    department?: string | null;
    positionTitle?: string | null;
    phoneNumber?: string | null;
    contactEmail?: string | null;
    role: 'admin' | 'reviewer' | 'maintenance';
    status: 'active' | 'inactive';
    reviewerProfile?: ReviewerProfileForm | null;
}

interface ReviewerProfileForm {
    affiliation: string;
    department: string;
    positionTitle?: string | null;
    phoneNumber?: string | null;
    contactEmail?: string | null;
    isUsed: 'Y' | 'N';
    expertiseCodes: number[];
}

interface ReviewerCategory {
    code: number;
    name: string;
}

interface AdminAccountCreateModalProps {
    isOpen: boolean;
    admin?: EditableAdminAccount | null;
    onClose: () => void;
    onSuccess: () => void;
    onNotify: (type: NotificationType, message: string) => void;
}

export const AdminAccountCreateModal = ({ isOpen, admin, onClose, onSuccess, onNotify }: AdminAccountCreateModalProps) => {
    const isEditMode = Boolean(admin);
    const [adminId, setAdminId] = useState('');
    const [adminName, setAdminName] = useState('');
    const [password, setPassword] = useState('');
    const [passwordConfirm, setPasswordConfirm] = useState('');
    const [role, setRole] = useState<'admin' | 'reviewer' | 'maintenance'>('admin');
    const [status, setStatus] = useState<'active' | 'inactive'>('active');
    const [affiliation, setAffiliation] = useState('');
    const [department, setDepartment] = useState('');
    const [positionTitle, setPositionTitle] = useState('');
    const [phoneNumber, setPhoneNumber] = useState('');
    const [contactEmail, setContactEmail] = useState('');
    const [isUsed, setIsUsed] = useState<'Y' | 'N'>('Y');
    const [expertiseCodes, setExpertiseCodes] = useState<number[]>([]);
    const [reviewerCategories, setReviewerCategories] = useState<ReviewerCategory[]>([]);
    const [showPassword, setShowPassword] = useState(false);
    const [showPasswordConfirm, setShowPasswordConfirm] = useState(false);
    const [isAdminIdChecked, setIsAdminIdChecked] = useState(false);
    const [isCheckingAdminId, setIsCheckingAdminId] = useState(false);
    const [adminIdCheckMessage, setAdminIdCheckMessage] = useState('');
    const [isSubmitting, setIsSubmitting] = useState(false);

    useEffect(() => {
        if (!isOpen) {
            setAdminId('');
            setAdminName('');
            setPassword('');
            setPasswordConfirm('');
            setRole('admin');
            setStatus('active');
            setAffiliation('');
            setDepartment('');
            setPositionTitle('');
            setPhoneNumber('');
            setContactEmail('');
            setIsUsed('Y');
            setExpertiseCodes([]);
            setShowPassword(false);
            setShowPasswordConfirm(false);
            setIsAdminIdChecked(false);
            setIsCheckingAdminId(false);
            setAdminIdCheckMessage('');
            setIsSubmitting(false);
            return;
        }

        if (admin) {
            setAdminId(admin.email);
            setAdminName(admin.adminName);
            setRole(admin.role);
            setStatus(admin.status);
            setPassword('');
            setPasswordConfirm('');
            setAffiliation(admin.affiliation ?? admin.reviewerProfile?.affiliation ?? '');
            setDepartment(admin.department ?? admin.reviewerProfile?.department ?? '');
            setPositionTitle(admin.positionTitle ?? admin.reviewerProfile?.positionTitle ?? '');
            setPhoneNumber(admin.phoneNumber ?? admin.reviewerProfile?.phoneNumber ?? '');
            setContactEmail(admin.contactEmail ?? admin.reviewerProfile?.contactEmail ?? '');
            setIsUsed(admin.reviewerProfile?.isUsed ?? 'Y');
            setExpertiseCodes(admin.reviewerProfile?.expertiseCodes ?? []);
        }
    }, [admin, isOpen]);

    useEffect(() => {
        if (!isOpen) {
            return;
        }

        const abortController = new AbortController();
        const loadReviewerCategories = async () => {
            try {
                const response = await fetch('/api/admin/reviewer-categories', { signal: abortController.signal });
                if (!response.ok) {
                    throw new Error(await response.text() || 'Reviewer 전문분야를 불러오지 못했습니다.');
                }
                setReviewerCategories(await response.json() as ReviewerCategory[]);
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') {
                    return;
                }
                const message = error instanceof Error ? error.message : 'Reviewer 전문분야를 불러오지 못했습니다.';
                onNotify('error', message);
            }
        };

        void loadReviewerCategories();
        return () => abortController.abort();
    }, [isOpen, onNotify]);

    const toggleExpertise = (categoryCode: number) => {
        setExpertiseCodes((current) => current.includes(categoryCode)
            ? current.filter((code) => code !== categoryCode)
            : [...current, categoryCode]);
    };

    const handleAdminIdChange = (value: string) => {
        setAdminId(value);
        setIsAdminIdChecked(false);
        setAdminIdCheckMessage('');
    };

    const handleCheckAdminId = async () => {
        const normalizedAdminId = adminId.trim();
        if (!normalizedAdminId) {
            setIsAdminIdChecked(false);
            setAdminIdCheckMessage('관리자 아이디를 입력해주세요.');
            return;
        }

        setIsCheckingAdminId(true);
        setIsAdminIdChecked(false);
        setAdminIdCheckMessage('');

        try {
            const params = new URLSearchParams({ email: normalizedAdminId });
            const response = await fetch(`/api/admin/accounts/check-id?${params.toString()}`);
            if (!response.ok) {
                throw new Error(await response.text() || '아이디 중복확인에 실패했습니다.');
            }

            const available = await response.json() as boolean;
            setIsAdminIdChecked(available);
            setAdminIdCheckMessage(available ? '사용 가능한 아이디입니다.' : '이미 사용 중인 아이디입니다.');
        } catch (error) {
            setAdminIdCheckMessage(error instanceof Error ? error.message : '아이디 중복확인에 실패했습니다.');
        } finally {
            setIsCheckingAdminId(false);
        }
    };

    const handleSubmit = async (event: React.FormEvent) => {
        event.preventDefault();

        if (!isEditMode && !isAdminIdChecked) {
            const message = '관리자 아이디 중복확인을 완료해주세요.';
            onNotify('error', message);
            return;
        }
        if (!isEditMode && password !== passwordConfirm) {
            const message = '비밀번호와 비밀번호 확인이 일치하지 않습니다.';
            onNotify('error', message);
            return;
        }
        const requiredContactFields = [
            ['소속기관', affiliation],
            ['부서·학과·진료과', department],
            ['직위', positionTitle],
            ['연락처', phoneNumber],
            ['이메일', contactEmail]
        ] as const;
        const missingField = requiredContactFields.find(([, value]) => !value.trim());
        if (missingField) {
            onNotify('error', `${missingField[0]}을(를) 입력해 주세요.`);
            return;
        }

        setIsSubmitting(true);

        try {
            const body = new URLSearchParams({
                adminName: adminName.trim(),
                role
            });
            if (isEditMode) {
                body.set('status', status);
            } else {
                body.set('email', adminId.trim());
                body.set('password', password);
            }
            body.set('affiliation', affiliation.trim());
            body.set('department', department.trim());
            body.set('positionTitle', positionTitle.trim());
            body.set('phoneNumber', phoneNumber.trim());
            body.set('contactEmail', contactEmail.trim());
            if (role === 'reviewer') {
                body.set('isUsed', isUsed);
                if (expertiseCodes.length === 0) {
                    body.append('expertiseCodes', '');
                } else {
                    expertiseCodes.forEach((code) => body.append('expertiseCodes', String(code)));
                }
            }

            const response = await fetch(isEditMode ? `/api/admin/accounts/${admin?.seq}` : '/api/admin/accounts', {
                method: isEditMode ? 'PUT' : 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded'
                },
                body
            });

            if (!response.ok) {
                const message = await response.text();
                throw new Error(message || '관리자 계정 저장에 실패했습니다.');
            }

            onSuccess();
            onNotify('success', isEditMode ? '관리자 계정이 수정되었습니다.' : '관리자 계정이 추가되었습니다.');
            onClose();
        } catch (error) {
            const message = error instanceof Error ? error.message : '관리자 계정 저장에 실패했습니다.';
            onNotify('error', message);
        } finally {
            setIsSubmitting(false);
        }
    };

    if (!isOpen) {
        return null;
    }

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
            <div onClick={onClose} className="absolute inset-0 bg-slate-900/60" />
            <DraggableModalForm onSubmit={handleSubmit} className="relative z-10 flex max-h-[90vh] w-full max-w-2xl flex-col overflow-hidden rounded-xl border border-slate-200 bg-white shadow-2xl dark:border-slate-800 dark:bg-slate-950">
                <div data-modal-drag-handle className="cursor-move select-none touch-none flex shrink-0 items-center justify-between border-b border-slate-200 p-4 dark:border-slate-800 md:p-5">
                    <div>
                        <h3 className="font-bold text-base">{isEditMode ? '관리자 계정 수정' : '관리자 계정 추가'}</h3>
                        <p className="text-xs text-slate-400 mt-1">{isEditMode ? '관리자 기본정보, 권한, 상태를 수정합니다.' : '관리자 계정과 기본 연락처 정보를 등록합니다.'}</p>
                    </div>
                    <button type="button" onClick={onClose} className="p-1.5 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-400">
                        <X className="w-4 h-4" />
                    </button>
                </div>

                <div className="flex-1 space-y-4 overflow-y-auto p-4 md:p-5">

                    <div>
                        <label className="block text-xs font-semibold text-slate-500 uppercase mb-1.5">관리자 아이디</label>
                        <div className="flex gap-2">
                            <input
                                value={adminId}
                                onChange={(event) => handleAdminIdChange(event.target.value)}
                                className="min-w-0 flex-1 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none disabled:opacity-60 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50"
                                disabled={isEditMode || isCheckingAdminId}
                                required
                            />
                            {!isEditMode && (
                                <button type="button" onClick={handleCheckAdminId} disabled={isCheckingAdminId || !adminId.trim()} className="shrink-0 rounded-lg border border-blue-200 px-3 py-2 text-xs font-semibold text-blue-600 hover:bg-blue-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-blue-900 dark:text-blue-400 dark:hover:bg-blue-950/30">
                                    {isCheckingAdminId ? '확인 중' : '중복확인'}
                                </button>
                            )}
                        </div>
                        {!isEditMode && adminIdCheckMessage && (
                            <p className={`mt-1.5 text-xs ${isAdminIdChecked ? 'text-emerald-600 dark:text-emerald-400' : 'text-rose-600 dark:text-rose-400'}`}>
                                {adminIdCheckMessage}
                            </p>
                        )}
                    </div>

                    <div>
                        <label className="block text-xs font-semibold text-slate-500 uppercase mb-1.5">관리자명</label>
                        <input
                            value={adminName}
                            onChange={(event) => setAdminName(event.target.value)}
                            className="w-full px-3 py-2 rounded-lg border bg-white dark:bg-slate-900 text-sm text-slate-900 dark:text-slate-50 border-slate-200 dark:border-slate-800 focus:outline-none focus:border-blue-500"
                            required
                        />
                    </div>

                    {!isEditMode && (
                        <div className="grid gap-4 md:grid-cols-2">
                            <div>
                                <label className="block text-xs font-semibold text-slate-500 uppercase mb-1.5">비밀번호</label>
                                <div className="relative">
                                    <input
                                        type={showPassword ? 'text' : 'password'}
                                        value={password}
                                        onChange={(event) => setPassword(event.target.value)}
                                        className="w-full px-3 py-2 pr-10 rounded-lg border bg-white dark:bg-slate-900 text-sm text-slate-900 dark:text-slate-50 border-slate-200 dark:border-slate-800 focus:outline-none focus:border-blue-500"
                                        minLength={8}
                                        required
                                    />
                                    <button type="button" onClick={() => setShowPassword((value) => !value)} className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400" aria-label="비밀번호 표시 전환">
                                        {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                                    </button>
                                </div>
                            </div>
                            <div>
                                <label className="block text-xs font-semibold text-slate-500 uppercase mb-1.5">비밀번호 확인</label>
                                <div className="relative">
                                    <input
                                        type={showPasswordConfirm ? 'text' : 'password'}
                                        value={passwordConfirm}
                                        onChange={(event) => setPasswordConfirm(event.target.value)}
                                        className="w-full px-3 py-2 pr-10 rounded-lg border bg-white dark:bg-slate-900 text-sm text-slate-900 dark:text-slate-50 border-slate-200 dark:border-slate-800 focus:outline-none focus:border-blue-500"
                                        minLength={8}
                                        required
                                    />
                                    <button type="button" onClick={() => setShowPasswordConfirm((value) => !value)} className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400" aria-label="비밀번호 확인 표시 전환">
                                        {showPasswordConfirm ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                                    </button>
                                </div>
                                {passwordConfirm && password !== passwordConfirm && <p className="mt-1.5 text-xs text-rose-600 dark:text-rose-400">비밀번호가 일치하지 않습니다.</p>}
                            </div>
                        </div>
                    )}

                    <div className="grid gap-4 md:grid-cols-2">
                            <label className="space-y-1.5">
                                <span className="text-xs font-semibold uppercase text-slate-500">소속기관 *</span>
                                <input value={affiliation} onChange={(event) => setAffiliation(event.target.value)} maxLength={255} required className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-950 dark:text-slate-50" />
                            </label>
                            <label className="space-y-1.5">
                                <span className="text-xs font-semibold uppercase text-slate-500">부서·학과·진료과 *</span>
                                <input value={department} onChange={(event) => setDepartment(event.target.value)} maxLength={255} required className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-950 dark:text-slate-50" />
                            </label>
                            <label className="space-y-1.5">
                                <span className="text-xs font-semibold uppercase text-slate-500">직위 *</span>
                                <input value={positionTitle} onChange={(event) => setPositionTitle(event.target.value)} maxLength={100} required className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-950 dark:text-slate-50" />
                            </label>
                            <label className="space-y-1.5">
                                <span className="text-xs font-semibold uppercase text-slate-500">연락처 *</span>
                                <input type="tel" value={phoneNumber} onChange={(event) => setPhoneNumber(event.target.value)} maxLength={100} required className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-950 dark:text-slate-50" />
                            </label>
                            <label className="space-y-1.5 md:col-span-2">
                                <span className="text-xs font-semibold uppercase text-slate-500">이메일 *</span>
                                <input type="email" value={contactEmail} onChange={(event) => setContactEmail(event.target.value)} maxLength={255} required className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-950 dark:text-slate-50" />
                            </label>
                    </div>

                    <div>
                        <label className="block text-xs font-semibold text-slate-500 uppercase mb-1.5">권한</label>
                        <select
                            value={role}
                            onChange={(event) => setRole(event.target.value as 'admin' | 'reviewer' | 'maintenance')}
                            className="w-full px-3 py-2 rounded-lg border bg-white dark:bg-slate-900 text-sm text-slate-900 dark:text-slate-50 border-slate-200 dark:border-slate-800 focus:outline-none focus:border-blue-500"
                        >
                            <option value="admin">admin</option>
                            <option value="reviewer">reviewer</option>
                            <option value="maintenance">maintenance</option>
                        </select>
                    </div>

                    {role === 'reviewer' && (
                        <div className="space-y-4 rounded-xl border border-violet-200 bg-violet-50/60 p-4 dark:border-violet-900/60 dark:bg-violet-950/20">
                            <div>
                                <h4 className="text-sm font-bold text-violet-800 dark:text-violet-300">Reviewer 추가 정보</h4>
                                <p className="mt-1 text-xs text-violet-600/80 dark:text-violet-400">심사 배정 사용 여부와 전문분야를 입력합니다.</p>
                            </div>
                            <div className="grid gap-4 md:grid-cols-2">
                                <label className="space-y-1.5">
                                    <span className="text-xs font-semibold uppercase text-slate-500">심사 배정 사용 여부</span>
                                    <select value={isUsed} onChange={(event) => setIsUsed(event.target.value as 'Y' | 'N')} className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-violet-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50">
                                        <option value="Y">사용</option>
                                        <option value="N">미사용</option>
                                    </select>
                                </label>
                            </div>
                            <fieldset>
                                <legend className="text-xs font-semibold uppercase text-slate-500">심사 전문분야</legend>
                                <div className="mt-2 grid gap-2 sm:grid-cols-2">
                                    {reviewerCategories.map((category) => (
                                        <label key={category.code} className="flex cursor-pointer items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm dark:border-slate-800 dark:bg-slate-900">
                                            <input type="checkbox" checked={expertiseCodes.includes(category.code)} onChange={() => toggleExpertise(category.code)} className="h-4 w-4 rounded border-slate-300 text-violet-600 focus:ring-violet-500" />
                                            <span>{category.name}</span>
                                        </label>
                                    ))}
                                    {reviewerCategories.length === 0 && <p className="text-xs text-slate-400">등록된 전문분야가 없습니다.</p>}
                                </div>
                            </fieldset>
                        </div>
                    )}

                    {isEditMode && (
                        <div>
                            <label className="block text-xs font-semibold text-slate-500 uppercase mb-1.5">상태</label>
                            <select
                                value={status}
                                onChange={(event) => setStatus(event.target.value as 'active' | 'inactive')}
                                className="w-full px-3 py-2 rounded-lg border bg-white dark:bg-slate-900 text-sm text-slate-900 dark:text-slate-50 border-slate-200 dark:border-slate-800 focus:outline-none focus:border-blue-500"
                            >
                                <option value="active">active</option>
                                <option value="inactive">inactive</option>
                            </select>
                        </div>
                    )}
                </div>

                <div className="flex shrink-0 items-center justify-end gap-2 border-t border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-950 md:p-5">
                    <button type="button" onClick={onClose} className="px-4 py-2 rounded-lg text-xs font-semibold border border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-900">
                        취소
                    </button>
                    <button type="submit" disabled={isSubmitting} className="px-4 py-2 rounded-lg text-xs font-semibold bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50">
                        {isSubmitting ? (isEditMode ? '저장 중' : '등록 중') : (isEditMode ? '저장' : '등록')}
                    </button>
                </div>
            </DraggableModalForm>
        </div>
    );
};
