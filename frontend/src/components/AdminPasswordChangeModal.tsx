import { DraggableModal } from './DraggableModal';
import { useEffect, useState, type FormEvent } from 'react';
import { Eye, EyeOff, KeyRound, X } from 'lucide-react';
import type { NotificationType } from './NotificationToast';

interface Props {
    isOpen: boolean;
    onClose: () => void;
    onSuccess: () => void;
    onNotify: (type: NotificationType, message: string) => void;
}

export const AdminPasswordChangeModal = ({ isOpen, onClose, onSuccess, onNotify }: Props) => {
    const [currentPassword, setCurrentPassword] = useState('');
    const [newPassword, setNewPassword] = useState('');
    const [newPasswordConfirm, setNewPasswordConfirm] = useState('');
    const [showCurrentPassword, setShowCurrentPassword] = useState(false);
    const [showNewPassword, setShowNewPassword] = useState(false);
    const [isSubmitting, setIsSubmitting] = useState(false);

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

    const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();

        if (newPassword.length < 8) {
            onNotify('error', '새 비밀번호는 최소 8자 이상이어야 합니다.');
            return;
        }
        if (newPassword !== newPasswordConfirm) {
            onNotify('error', '새 비밀번호와 비밀번호 확인이 일치하지 않습니다.');
            return;
        }

        setIsSubmitting(true);
        try {
            const response = await fetch('/api/admin/me/password', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ currentPassword, newPassword, newPasswordConfirm })
            });
            if (!response.ok) {
                throw new Error(await response.text() || '비밀번호를 변경하지 못했습니다.');
            }

            onNotify('success', '비밀번호가 변경되었습니다. 다시 로그인해주세요.');
            onSuccess();
        } catch (error) {
            const message = error instanceof Error ? error.message : '비밀번호를 변경하지 못했습니다.';
            onNotify('error', message);
        } finally {
            setIsSubmitting(false);
        }
    };

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-[140] flex items-center justify-center p-4" role="dialog" aria-modal="true" aria-labelledby="password-change-title">
            <button type="button" className="absolute inset-0 bg-slate-950/60" onClick={onClose} aria-label="비밀번호 변경 닫기" />
            <DraggableModal className="relative z-10 w-full max-w-md overflow-hidden rounded-xl border border-slate-200 bg-white shadow-2xl dark:border-slate-800 dark:bg-slate-950">
                <div data-modal-drag-handle className="cursor-move select-none touch-none flex items-center justify-between border-b border-slate-200 p-4 dark:border-slate-800">
                    <div className="flex items-center gap-2">
                        <KeyRound className="h-5 w-5 text-blue-500" />
                        <h2 id="password-change-title" className="font-bold text-slate-900 dark:text-slate-50">비밀번호 변경</h2>
                    </div>
                    <button type="button" onClick={onClose} className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800" aria-label="닫기">
                        <X className="h-4 w-4" />
                    </button>
                </div>

                <form onSubmit={handleSubmit} className="space-y-4 p-5">
                    <PasswordField label="현재 비밀번호" value={currentPassword} onChange={setCurrentPassword} visible={showCurrentPassword} onToggle={() => setShowCurrentPassword((value) => !value)} autoComplete="current-password" />
                    <PasswordField label="새 비밀번호" value={newPassword} onChange={setNewPassword} visible={showNewPassword} onToggle={() => setShowNewPassword((value) => !value)} autoComplete="new-password" hint="최소 8자 이상" />
                    <PasswordField label="새 비밀번호 확인" value={newPasswordConfirm} onChange={setNewPasswordConfirm} visible={showNewPassword} onToggle={() => setShowNewPassword((value) => !value)} autoComplete="new-password" />

                    <div className="flex justify-end gap-2 border-t border-slate-200 pt-4 dark:border-slate-800">
                        <button type="button" onClick={onClose} className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-600 hover:bg-slate-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900">취소</button>
                        <button type="submit" disabled={isSubmitting} className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-50">
                            {isSubmitting ? '변경 중' : '비밀번호 변경'}
                        </button>
                    </div>
                </form>
            </DraggableModal>
        </div>
    );
};

const PasswordField = ({ label, value, onChange, visible, onToggle, autoComplete, hint }: {
    label: string;
    value: string;
    onChange: (value: string) => void;
    visible: boolean;
    onToggle: () => void;
    autoComplete: string;
    hint?: string;
}) => (
    <label className="block">
        <span className="mb-1.5 block text-xs font-semibold text-slate-500">{label} *</span>
        <span className="relative block">
            <input
                type={visible ? 'text' : 'password'}
                value={value}
                onChange={(event) => onChange(event.target.value)}
                autoComplete={autoComplete}
                required
                className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 pr-10 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50"
            />
            <button type="button" onClick={onToggle} className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400" aria-label={`${label} 표시 전환`}>
                {visible ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            </button>
        </span>
        {hint && <span className="mt-1 block text-xs text-slate-400">{hint}</span>}
    </label>
);
