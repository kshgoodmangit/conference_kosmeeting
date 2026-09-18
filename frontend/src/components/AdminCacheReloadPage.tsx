import { useEffect, useState } from 'react';
import { AlertCircle, ArrowLeft, CheckCircle2, Eye, EyeOff, RefreshCw, ShieldCheck } from 'lucide-react';
import adminLoginBackground from '../assets/login-backgrounds/02-bright-auditorium.png';

interface CacheReloadResult {
    cacheName: string;
    cachedEntryCount: number;
    reloadedAt: string;
}

interface CacheReloadResponse {
    caches: CacheReloadResult[];
    message: string;
}

export const AdminCacheReloadPage = () => {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [showPassword, setShowPassword] = useState(false);
    const [loading, setLoading] = useState(false);
    const [errorMessage, setErrorMessage] = useState('');
    const [result, setResult] = useState<CacheReloadResponse | null>(null);

    useEffect(() => {
        const savedTheme = localStorage.getItem('theme');
        const darkMode = savedTheme === 'dark'
            || (savedTheme !== 'light' && window.matchMedia('(prefers-color-scheme: dark)').matches);
        document.documentElement.classList.toggle('dark', darkMode);
    }, []);

    const handleSubmit = async (event: React.FormEvent) => {
        event.preventDefault();
        setLoading(true);
        setErrorMessage('');
        setResult(null);

        try {
            const response = await fetch('/api/admin/cache/reload', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    email: email.trim(),
                    password
                })
            });

            if (!response.ok) {
                const message = await response.text();
                throw new Error(message || '관리자 캐시를 다시 불러오지 못했습니다.');
            }

            const data = await response.json() as CacheReloadResponse;
            setResult(data);
            setPassword('');
        } catch (error) {
            setErrorMessage(error instanceof Error
                ? error.message
                : '관리자 캐시를 다시 불러오지 못했습니다.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <main className="relative flex min-h-[100dvh] items-center justify-center overflow-hidden bg-slate-950 p-5 text-slate-900 dark:text-slate-50">
            <img
                src={adminLoginBackground}
                alt=""
                aria-hidden="true"
                className="absolute inset-0 h-full w-full object-cover object-center opacity-55"
            />
            <div className="absolute inset-0 bg-slate-950/55" />

            <a
                href="/admin"
                className="absolute left-5 top-5 z-10 inline-flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-semibold text-white transition-colors hover:bg-white/15"
            >
                <ArrowLeft className="h-4 w-4" />
                관리자 로그인
            </a>

            <form
                onSubmit={handleSubmit}
                className="relative z-10 w-full max-w-md overflow-hidden rounded-xl border border-slate-200 bg-white shadow-2xl dark:border-slate-800 dark:bg-slate-950"
            >
                <div className="border-b border-slate-200 p-6 dark:border-slate-800">
                    <div className="flex h-11 w-11 items-center justify-center rounded-lg bg-blue-50 text-blue-600 dark:bg-blue-950/40 dark:text-blue-400">
                        <RefreshCw className="h-6 w-6" />
                    </div>
                    <h1 className="mt-4 text-xl font-bold">관리자 캐시 다시 불러오기</h1>
                    <p className="mt-1 text-sm leading-6 text-slate-500 dark:text-slate-400">
                        활성 관리자 계정으로 인증한 후 등록된 모든 관리자 캐시를 다시 불러옵니다.
                    </p>
                </div>

                <div className="space-y-4 p-6">
                    {errorMessage && (
                        <div className="flex items-start gap-2 rounded-lg border border-rose-200 bg-rose-50 p-3 dark:border-rose-900/60 dark:bg-rose-950/30">
                            <AlertCircle className="mt-0.5 h-5 w-5 flex-shrink-0 text-rose-600 dark:text-rose-400" />
                            <p className="text-sm text-rose-700 dark:text-rose-300">{errorMessage}</p>
                        </div>
                    )}

                    {result && (
                        <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-3 dark:border-emerald-900/60 dark:bg-emerald-950/30">
                            <div className="flex items-start gap-2">
                                <CheckCircle2 className="mt-0.5 h-5 w-5 flex-shrink-0 text-emerald-600 dark:text-emerald-400" />
                                <div className="min-w-0">
                                    <p className="text-sm font-semibold text-emerald-800 dark:text-emerald-300">{result.message}</p>
                                    <ul className="mt-2 space-y-1 text-xs text-emerald-700 dark:text-emerald-400">
                                        {result.caches.map((cache) => (
                                            <li key={cache.cacheName}>
                                                {cache.cacheName}: {cache.cachedEntryCount}건 · {formatDateTime(cache.reloadedAt)}
                                            </li>
                                        ))}
                                    </ul>
                                </div>
                            </div>
                        </div>
                    )}

                    <div>
                        <label className="mb-1.5 block text-xs font-semibold uppercase text-slate-500">관리자 아이디</label>
                        <input
                            type="text"
                            value={email}
                            onChange={(event) => setEmail(event.target.value)}
                            className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50"
                            placeholder="admin"
                            autoComplete="username"
                            disabled={loading}
                            required
                        />
                    </div>

                    <div>
                        <label className="mb-1.5 block text-xs font-semibold uppercase text-slate-500">비밀번호</label>
                        <div className="relative">
                            <input
                                type={showPassword ? 'text' : 'password'}
                                value={password}
                                onChange={(event) => setPassword(event.target.value)}
                                className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 pr-10 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50"
                                placeholder="Password"
                                autoComplete="current-password"
                                disabled={loading}
                                required
                            />
                            <button
                                type="button"
                                onClick={() => setShowPassword((value) => !value)}
                                className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-300"
                                aria-label={showPassword ? '비밀번호 숨기기' : '비밀번호 표시'}
                                disabled={loading}
                            >
                                {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                            </button>
                        </div>
                    </div>

                    <button
                        type="submit"
                        disabled={loading}
                        className="inline-flex w-full items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-50"
                    >
                        {loading ? <RefreshCw className="h-4 w-4 animate-spin" /> : <ShieldCheck className="h-4 w-4" />}
                        {loading ? '인증 및 다시 불러오는 중' : '인증 후 캐시 다시 불러오기'}
                    </button>

                    <p className="text-center text-xs leading-5 text-slate-400">
                        이 기능은 IP 접근 제한과 관계없이 호출할 수 있으므로 운영 환경에서는 HTTPS를 사용하세요.
                    </p>
                </div>
            </form>
        </main>
    );
};

const formatDateTime = (value: string) => {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return value;
    }
    return date.toLocaleString('ko-KR');
};
