import { useEffect, useState } from 'react';
import { AlertCircle, ArrowLeft, Eye, EyeOff, LogIn, Network, Send, ShieldCheck } from 'lucide-react';
import { AdminAccessRequestModal } from './AdminAccessRequestModal';
import type { NotificationType } from './NotificationToast';
import adminLoginBackground from '../assets/login-backgrounds/02-bright-auditorium.png';

interface AdminLoginPageProps {
    applicationName: string;
    onSuccess: (adminData: AdminLoginData) => void;
    homeHref?: string;
    onNotify: (type: NotificationType, message: string) => void;
}

export interface AdminLoginData {
    seq: number;
    email: string;
    adminName: string;
    role: string;
    conferenceSeq?: number | null;
    message?: string;
}

interface AdminClientIpData {
    clientIp: string;
}

export const AdminLoginPage = ({ applicationName, onSuccess, homeHref, onNotify }: AdminLoginPageProps) => {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [showPassword, setShowPassword] = useState(false);
    const [loading, setLoading] = useState(false);
    const [errorMessage, setErrorMessage] = useState('');
    const [clientIp, setClientIp] = useState('확인 중...');
    const [accessRequestOpen, setAccessRequestOpen] = useState(false);
    const clientIpReady = Boolean(clientIp) && clientIp !== '확인 중...' && clientIp !== '확인할 수 없음';

    useEffect(() => {
        const controller = new AbortController();

        const loadClientIp = async () => {
            try {
                const response = await fetch('/api/admin/access-info/client-ip', {
                    cache: 'no-store',
                    signal: controller.signal
                });
                if (!response.ok) {
                    throw new Error('클라이언트 IP 조회에 실패했습니다.');
                }

                const data = await response.json() as AdminClientIpData;
                setClientIp(data.clientIp);
            } catch (error) {
                if (!(error instanceof DOMException && error.name === 'AbortError')) {
                    setClientIp('확인할 수 없음');
                }
            }
        };

        void loadClientIp();
        return () => controller.abort();
    }, []);

    const handleSubmit = async (event: React.FormEvent) => {
        event.preventDefault();
        setErrorMessage('');
        setLoading(true);

        try {
            const response = await fetch('/api/admin/login', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded'
                },
                body: new URLSearchParams({
                    email: email.trim(),
                    password: password.trim()
                })
            });

            if (!response.ok) {
                const message = await response.text();
                throw new Error(message || '로그인에 실패했습니다.');
            }

            const data = await response.json() as AdminLoginData;
            onSuccess(data);
        } catch (error) {
            setErrorMessage(error instanceof Error ? error.message : '로그인에 실패했습니다.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="relative isolate min-h-dvh bg-slate-950 text-slate-900 dark:bg-slate-950 dark:text-slate-50">
            <div aria-hidden="true" className="pointer-events-none absolute inset-0 -z-10 overflow-hidden">
                <img src={adminLoginBackground} alt="" className="h-full w-full object-cover object-center" />
                <div className="absolute inset-0 bg-slate-950/30 dark:bg-slate-950/45" />
                <div className="absolute inset-0 bg-gradient-to-r from-slate-950/75 via-slate-950/25 to-slate-950/10 dark:from-slate-950/80 dark:via-slate-950/35 dark:to-slate-950/20" />
            </div>

            <div className="mx-auto flex min-h-dvh max-w-[1440px] flex-col px-5 py-6 sm:px-8 lg:px-12 lg:py-7 xl:px-16">
                <header className="flex items-center gap-3 text-white dark:text-white">
                    <img
                        src="/images/admin-branding/logo-v3.png"
                        alt="ICMS"
                        width={40}
                        height={40}
                        className="h-10 w-10 shrink-0 rounded object-contain"
                    />
                    <div>
                        <p className="break-all text-xs text-slate-300 dark:text-slate-300">{applicationName}</p>
                        <h1 className="text-lg font-bold sm:text-xl">Conference Admin</h1>
                    </div>
                </header>

                <div className="grid flex-1 items-center gap-10 py-8 lg:grid-cols-[minmax(0,1fr)_400px] lg:gap-14 lg:pb-10 lg:pt-0 xl:grid-cols-[minmax(0,1fr)_420px] xl:gap-20">
                    <section className="hidden min-w-0 text-white dark:text-white lg:block" aria-labelledby="admin-intro-title">
                        <p className="text-xs font-semibold uppercase tracking-[0.18em] text-blue-300 dark:text-blue-300">Trusted Conference Operations</p>
                        <h2 id="admin-intro-title" className="mt-5 text-4xl font-bold leading-[1.3] tracking-tight xl:text-5xl">
                            정확한 데이터로,<br />
                            신뢰받는 학술대회를<br />
                            운영합니다.
                        </h2>
                        <p className="mt-6 max-w-md text-base leading-7 text-slate-200 dark:text-slate-200">
                            등록부터 초록 제출과 논문 심사, 운영 현황까지<br className="hidden xl:block" />
                            하나의 시스템에서 안전하고 체계적으로 관리합니다.
                        </p>
                    </section>

                    <main className="mx-auto w-full max-w-[420px] lg:mx-0 lg:-translate-y-6">
                        {homeHref && (
                            <a href={homeHref} className="mb-4 inline-flex items-center gap-2 rounded-lg py-2 text-sm font-medium text-slate-200 transition-colors hover:text-white focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-blue-300 dark:text-slate-200 dark:hover:text-white dark:focus-visible:outline-blue-300">
                                <ArrowLeft className="h-4 w-4" />
                                홈페이지
                            </a>
                        )}
                        <form onSubmit={handleSubmit} aria-labelledby="admin-login-title" className="rounded-2xl bg-white p-6 shadow-2xl shadow-slate-950/25 dark:bg-slate-950 dark:shadow-slate-950/40 sm:p-7">
                            <div>
                                <div className="flex items-center gap-2 text-xs font-semibold tracking-[0.16em] text-blue-600 dark:text-blue-400">
                                    <ShieldCheck className="h-4 w-4" />
                                    ADMIN ACCESS
                                </div>
                                <h2 id="admin-login-title" className="mt-4 text-2xl font-bold tracking-tight">관리자 로그인</h2>
                                <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">관리자 계정으로 로그인해 주세요.</p>
                            </div>

                            <div className="mt-6 space-y-4">
                                {errorMessage && (
                                    <div role="alert" className="flex items-start gap-2 rounded-lg border border-rose-200 bg-rose-50 p-3 dark:border-rose-900/60 dark:bg-rose-950/30">
                                        <AlertCircle className="mt-0.5 h-5 w-5 shrink-0 text-rose-600 dark:text-rose-400" />
                                        <p className="text-sm text-rose-700 dark:text-rose-300">{errorMessage}</p>
                                    </div>
                                )}

                                <div>
                                    <label htmlFor="admin-login-id" className="mb-1.5 block text-sm font-semibold text-slate-700 dark:text-slate-200">관리자 아이디</label>
                                    <input
                                        id="admin-login-id"
                                        type="text"
                                        autoComplete="username"
                                        value={email}
                                        onChange={(event) => setEmail(event.target.value)}
                                        className="h-12 w-full rounded-lg border border-slate-200 bg-white px-3 text-sm text-slate-900 placeholder:text-slate-400 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/15 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50 dark:placeholder:text-slate-500 dark:focus:border-blue-400 dark:focus:ring-blue-400/20"
                                        placeholder="아이디를 입력해 주세요"
                                        disabled={loading}
                                        required
                                    />
                                </div>

                                <div>
                                    <label htmlFor="admin-login-password" className="mb-1.5 block text-sm font-semibold text-slate-700 dark:text-slate-200">비밀번호</label>
                                    <div className="relative">
                                        <input
                                            id="admin-login-password"
                                            type={showPassword ? 'text' : 'password'}
                                            autoComplete="current-password"
                                            value={password}
                                            onChange={(event) => setPassword(event.target.value)}
                                            className="h-12 w-full rounded-lg border border-slate-200 bg-white px-3 pr-12 text-sm text-slate-900 placeholder:text-slate-400 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/15 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50 dark:placeholder:text-slate-500 dark:focus:border-blue-400 dark:focus:ring-blue-400/20"
                                            placeholder="비밀번호를 입력해 주세요"
                                            disabled={loading}
                                            required
                                        />
                                        <button
                                            type="button"
                                            onClick={() => setShowPassword((value) => !value)}
                                            aria-label={showPassword ? '비밀번호 숨기기' : '비밀번호 표시'}
                                            aria-pressed={showPassword}
                                            className="absolute inset-y-1 right-1 flex w-10 items-center justify-center rounded-lg text-slate-400 hover:text-slate-600 focus-visible:outline-2 focus-visible:outline-blue-500 dark:text-slate-400 dark:hover:text-slate-300 dark:focus-visible:outline-blue-400"
                                            disabled={loading}
                                        >
                                            {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                                        </button>
                                    </div>
                                </div>

                                <button
                                    type="submit"
                                    disabled={loading}
                                    className="inline-flex h-12 w-full items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 text-sm font-semibold text-white transition-colors hover:bg-blue-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-500 disabled:opacity-50 dark:bg-blue-600 dark:text-white dark:hover:bg-blue-700 dark:focus-visible:outline-blue-400"
                                >
                                    <LogIn className="h-4 w-4" />
                                    {loading ? '로그인 중' : '로그인'}
                                </button>

                                <div className="border-t border-slate-100 pt-5 text-xs leading-5 text-slate-500 dark:border-slate-800 dark:text-slate-400">
                                    <div className="min-w-0">
                                        <div className="flex flex-wrap items-center gap-x-2 gap-y-1.5">
                                            <div className="flex min-w-0 items-center gap-2">
                                                <Network aria-hidden="true" className="h-3.5 w-3.5 shrink-0" />
                                                <p className="min-w-0">현재 접속 IP <span className="break-all font-mono font-semibold text-slate-700 dark:text-slate-300">{clientIp}</span></p>
                                            </div>
                                            <button
                                                type="button"
                                                onClick={() => setAccessRequestOpen(true)}
                                                disabled={!clientIpReady}
                                                aria-haspopup="dialog"
                                                aria-describedby="admin-access-request-help"
                                                className="inline-flex shrink-0 items-center justify-center gap-1 rounded-lg border border-slate-200 px-2 py-1 text-[11px] font-semibold text-slate-600 hover:bg-slate-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-500 disabled:opacity-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900 dark:focus-visible:outline-blue-400"
                                            >
                                                <Send aria-hidden="true" className="h-3 w-3" />
                                                접근 허용 요청
                                            </button>
                                        </div>
                                        <p id="admin-access-request-help" className="mt-1">접근 허용이 필요하면 요청자 정보와 사용기간을 입력해 주세요.</p>
                                    </div>
                                </div>
                            </div>
                        </form>
                    </main>
                </div>

                <footer className="text-center text-[11px] text-slate-300 dark:text-slate-300 lg:text-left">
                    BJWorld21 Conference Management System
                </footer>
            </div>
            {accessRequestOpen && (
                <AdminAccessRequestModal clientIp={clientIp} onClose={() => setAccessRequestOpen(false)} onNotify={onNotify} />
            )}
        </div>
    );
};
