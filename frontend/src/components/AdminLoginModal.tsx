import { DraggableModal } from './DraggableModal';
import React, { useState, useEffect } from 'react';
import { LogIn, Eye, EyeOff, AlertCircle } from 'lucide-react';

interface AdminLoginModalProps {
    isOpen: boolean;
    onClose: () => void;
    onSuccess: (adminData: any) => void;
}

export const AdminLoginModal: React.FC<AdminLoginModalProps> = ({ isOpen, onClose, onSuccess }) => {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [showPassword, setShowPassword] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    useEffect(() => {
        if (isOpen) {
            document.body.style.overflow = 'hidden';
        } else {
            document.body.style.overflow = '';
            resetForm();
        }
        return () => { document.body.style.overflow = ''; };
    }, [isOpen]);

    const resetForm = () => {
        setEmail('');
        setPassword('');
        setShowPassword(false);
        setError('');
        setLoading(false);
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');
        setLoading(true);

        if (!email.trim()) {
            setError('이메일을 입력해주세요.');
            setLoading(false);
            return;
        }

        if (!password.trim()) {
            setError('비밀번호를 입력해주세요.');
            setLoading(false);
            return;
        }

        try {
            const response = await fetch('/api/admin/login', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                },
                body: new URLSearchParams({
                    email: email.trim(),
                    password: password.trim(),
                }),
            });

            if (response.ok) {
                const data = await response.json();
                onSuccess(data);
                onClose();
            } else {
                const errText = await response.text();
                setError(errText || '로그인에 실패했습니다.');
            }
        } catch (err) {
            console.error('API Error:', err);
            setError('서버 통신 중 오류가 발생했습니다.');
        } finally {
            setLoading(false);
        }
    };

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
            <div onClick={onClose} className="absolute inset-0 bg-slate-900/60" />

            <DraggableModal className="absolute theme-card border rounded-xl shadow-2xl w-full max-w-md z-10 overflow-hidden bg-white dark:bg-slate-950 border-slate-200 dark:border-slate-800">
                {/* HEADER */}
                <div data-modal-drag-handle className="cursor-move select-none touch-none p-6 border-b border-slate-200 dark:border-slate-800 bg-gradient-to-r from-blue-600 to-blue-700">
                    <div className="flex items-center gap-3">
                        <LogIn className="w-6 h-6 text-white" />
                        <h2 className="text-xl font-bold text-white">관리자 로그인</h2>
                    </div>
                </div>

                {/* FORM */}
                <form onSubmit={handleSubmit} className="p-6 space-y-4">
                    {/* ERROR MESSAGE */}
                    {error && (
                        <div className="p-3 rounded-lg bg-red-50 dark:bg-red-950/30 border border-red-200 dark:border-red-800 flex items-start gap-2">
                            <AlertCircle className="w-5 h-5 text-red-600 dark:text-red-400 flex-shrink-0 mt-0.5" />
                            <p className="text-sm text-red-700 dark:text-red-300">{error}</p>
                        </div>
                    )}

                    {/* EMAIL */}
                    <div>
                        <label className="block text-sm font-semibold text-slate-700 dark:text-slate-300 mb-2">
                            이메일
                        </label>
                        <input
                            type="email"
                            value={email}
                            onChange={(e) => setEmail(e.target.value)}
                            className="w-full px-4 py-2.5 rounded-lg border bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-50 border-slate-200 dark:border-slate-800 focus:outline-none focus:border-blue-500 dark:focus:border-blue-500"
                            placeholder="admin@example.com"
                            disabled={loading}
                        />
                    </div>

                    {/* PASSWORD */}
                    <div>
                        <label className="block text-sm font-semibold text-slate-700 dark:text-slate-300 mb-2">
                            비밀번호
                        </label>
                        <div className="relative">
                            <input
                                type={showPassword ? 'text' : 'password'}
                                value={password}
                                onChange={(e) => setPassword(e.target.value)}
                                className="w-full px-4 py-2.5 rounded-lg border bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-50 border-slate-200 dark:border-slate-800 focus:outline-none focus:border-blue-500 dark:focus:border-blue-500"
                                placeholder="••••••••"
                                disabled={loading}
                            />
                            <button
                                type="button"
                                onClick={() => setShowPassword(!showPassword)}
                                className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-300"
                                disabled={loading}
                            >
                                {showPassword ? <EyeOff className="w-5 h-5" /> : <Eye className="w-5 h-5" />}
                            </button>
                        </div>
                    </div>

                    {/* BUTTONS */}
                    <div className="flex gap-3 pt-4">
                        <button
                            type="button"
                            onClick={onClose}
                            disabled={loading}
                            className="flex-1 px-4 py-2.5 rounded-lg border border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300 font-semibold hover:bg-slate-50 dark:hover:bg-slate-900 disabled:opacity-50"
                        >
                            취소
                        </button>
                        <button
                            type="submit"
                            disabled={loading}
                            className="flex-1 px-4 py-2.5 rounded-lg bg-blue-600 text-white font-semibold hover:bg-blue-700 disabled:opacity-50 transition-all"
                        >
                            {loading ? '로그인 중...' : '로그인'}
                        </button>
                    </div>
                </form>
            </DraggableModal>
        </div>
    );
};

