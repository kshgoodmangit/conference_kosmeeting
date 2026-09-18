import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { ArrowLeft, CheckCircle2, Eye, EyeOff, LoaderCircle, ShieldCheck } from 'lucide-react';
import { NotificationToast, type NotificationItem, type NotificationType } from './NotificationToast';
import adminLoginBackground from '../assets/login-backgrounds/02-bright-auditorium.png';

interface RequestStatus {
    seq: number;
    status: string;
}

interface PendingRequestDetail extends RequestStatus {
    status: 'REQUESTED';
    siteUrl: string;
    requestIp: string;
    affiliation: string;
    requesterName: string;
    contact: string;
    purpose: string;
    startDate: string;
    endDate: string;
    expiresAt: string;
}

type RequestDetail = PendingRequestDetail | (RequestStatus & { status: 'APPROVED' | 'REJECTED' | 'EXPIRED' });

interface Props {
    onNotify: (type: NotificationType, message: string) => void;
}

const statuses: Record<string, string> = { REQUESTED: '대기', APPROVED: '허용 완료', REJECTED: '거절', EXPIRED: '만료' };
const fieldClass = 'w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm text-slate-900 focus:border-blue-500 focus:outline-none disabled:opacity-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-50 dark:focus:border-blue-500';
const secondaryClass = 'inline-flex items-center justify-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900';

const ApprovalForm = ({ onNotify }: Props) => {
    const [link] = useState(() => {
        const params = new URLSearchParams(window.location.search);
        const seq = params.get('seq') ?? '';
        const expires = params.get('expires') ?? '';
        const signature = params.get('signature') ?? '';
        return {
            valid: /^[1-9]\d*$/.test(seq) && /^\d+$/.test(expires) && /^[A-Za-z0-9_-]{43}$/.test(signature),
            endpoint: `/api/access-requests/${seq}/mail-approval`,
            query: new URLSearchParams({ expires, signature }).toString(),
            expires: Number(expires), signature
        };
    });
    const [detail, setDetail] = useState<RequestDetail | null>(null);
    const [loading, setLoading] = useState(true);
    const [reloadKey, setReloadKey] = useState(0);
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [showPassword, setShowPassword] = useState(false);
    const [processing, setProcessing] = useState(false);
    const submitting = useRef(false);

    useEffect(() => {
        const theme = localStorage.getItem('theme');
        document.documentElement.classList.toggle('dark', theme === 'dark'
            || (theme !== 'light' && window.matchMedia('(prefers-color-scheme: dark)').matches));
        const meta = document.createElement('meta');
        meta.name = 'referrer';
        meta.content = 'no-referrer';
        document.head.appendChild(meta);
        return () => meta.remove();
    }, []);

    useEffect(() => {
        const controller = new AbortController();
        void (async () => {
            try {
                if (!link.valid) throw new Error('메일의 요청 확인 링크를 다시 확인해 주세요.');
                const response = await fetch(`${link.endpoint}?${link.query}`, {
                    signal: controller.signal, cache: 'no-store', referrerPolicy: 'no-referrer'
                });
                if (!response.ok) throw new Error(await response.text() || '요청 정보를 불러오지 못했습니다.');
                const data = await response.json() as RequestDetail;
                if (!controller.signal.aborted) setDetail(data);
            } catch (error) {
                if (controller.signal.aborted) return;
                setDetail(null);
                onNotify('error', error instanceof Error ? error.message : '요청 정보를 불러오지 못했습니다.');
            } finally {
                if (!controller.signal.aborted) setLoading(false);
            }
        })();
        return () => controller.abort();
    }, [link, onNotify, reloadKey]);

    const retry = () => { setLoading(true); setReloadKey(value => value + 1); };
    const submit = async (event: FormEvent) => {
        event.preventDefault();
        if (submitting.current || detail?.status !== 'REQUESTED') return;
        if (!email.trim() || !password) {
            onNotify('error', '관리자 아이디와 비밀번호를 입력해 주세요.');
            return;
        }
        submitting.current = true;
        setProcessing(true);
        try {
            const response = await fetch(link.endpoint, {
                method: 'POST', headers: { 'Content-Type': 'application/json' }, referrerPolicy: 'no-referrer',
                body: JSON.stringify({ email: email.trim(), password, expires: link.expires, signature: link.signature })
            });
            if (!response.ok) {
                const message = await response.text();
                if ([404, 409, 410].includes(response.status)) retry();
                throw new Error(message || '접근 허용을 처리하지 못했습니다.');
            }
            const result = await response.json() as { message: string };
            setDetail(value => value ? { seq: value.seq, status: 'APPROVED' } : value);
            setEmail('');
            setPassword('');
            onNotify('success', result.message);
        } catch (error) {
            onNotify('error', error instanceof Error ? error.message : '접근 허용을 처리하지 못했습니다.');
        } finally {
            submitting.current = false;
            setProcessing(false);
        }
    };

    return <main className="relative min-h-[100dvh] bg-slate-950 p-4 text-slate-900 dark:bg-slate-950 dark:text-slate-50 sm:p-8">
        <img src={adminLoginBackground} alt="" aria-hidden="true" className="pointer-events-none fixed inset-0 h-full w-full object-cover opacity-40" />
        <div className="relative mx-auto w-full max-w-2xl">
            <a href="/admin" referrerPolicy="no-referrer" className="mb-4 inline-flex items-center gap-2 rounded-lg px-3 py-2 text-xs font-semibold text-white hover:bg-white/15 dark:text-white dark:hover:bg-white/15"><ArrowLeft className="h-4 w-4" />관리자 로그인</a>
            <section className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-950">
                <header className="border-b border-slate-200 p-4 dark:border-slate-800 md:p-5">
                    <h1 className="flex items-center gap-2 text-sm font-semibold md:text-base"><ShieldCheck className="h-4 w-4 text-blue-600 dark:text-blue-400" />요청 확인 및 접근 허용</h1>
                    <p className="mt-1 text-xs text-slate-400 dark:text-slate-400">요청 정보를 확인한 뒤 관리자 계정으로 인증하여 접근을 허용합니다.</p>
                </header>
                {loading ? <p role="status" className="flex items-center justify-center gap-2 p-10 text-sm text-slate-500 dark:text-slate-400"><LoaderCircle className="h-4 w-4 animate-spin" />요청 정보를 불러오는 중입니다.</p>
                    : !detail ? <div className="space-y-4 p-5 text-center"><p className="text-sm">요청 정보를 확인할 수 없습니다.</p><button type="button" onClick={retry} className={secondaryClass}>다시 시도</button></div>
                        : <>
                            <div className="space-y-4 p-4 md:p-5">
                                <div className="flex items-center justify-between gap-3"><h2 className="text-sm font-semibold">접근 요청 #{detail.seq}</h2><span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-600 dark:bg-slate-900 dark:text-slate-300">{statuses[detail.status] ?? detail.status}</span></div>
                                {detail.status === 'REQUESTED' && <dl className="grid gap-4 text-sm sm:grid-cols-2">
                                    {([['접속 사이트', detail.siteUrl], ['요청 IP', detail.requestIp], ['소속 / 이름', `${detail.affiliation} / ${detail.requesterName}`], ['연락처', detail.contact], ['사용기간', `${detail.startDate} ~ ${detail.endDate}`], ['대기 만료', detail.expiresAt.replace('T', ' ').slice(0, 16)]]).map(([label, value]) => <div key={label} className="min-w-0"><dt className="mb-1 text-xs text-slate-500 dark:text-slate-400">{label}</dt><dd className="break-words">{value}</dd></div>)}
                                    <div className="min-w-0 sm:col-span-2"><dt className="mb-1 text-xs text-slate-500 dark:text-slate-400">요청 목적</dt><dd className="whitespace-pre-wrap break-words">{detail.purpose}</dd></div>
                                </dl>}
                            </div>
                            {detail.status === 'REQUESTED' ? <form onSubmit={submit} className="space-y-4 border-t border-slate-200 p-4 dark:border-slate-800 md:p-5">
                                <h2 className="text-sm font-semibold">관리자 인증</h2>
                                <div className="grid gap-4 sm:grid-cols-2">
                                    <label><span className="mb-1.5 block text-sm font-semibold text-slate-700 dark:text-slate-200">관리자 아이디</span><input value={email} onChange={event => setEmail(event.target.value)} autoComplete="username" required disabled={processing} className={fieldClass} /></label>
                                    <div><label htmlFor="approval-password" className="mb-1.5 block text-sm font-semibold text-slate-700 dark:text-slate-200">비밀번호</label><div className="relative"><input id="approval-password" type={showPassword ? 'text' : 'password'} value={password} onChange={event => setPassword(event.target.value)} autoComplete="current-password" required disabled={processing} className={`${fieldClass} pr-10`} /><button type="button" onClick={() => setShowPassword(value => !value)} disabled={processing} aria-label={showPassword ? '비밀번호 숨기기' : '비밀번호 표시'} className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 dark:text-slate-400 dark:hover:text-slate-300">{showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}</button></div></div>
                                </div>
                                <div className="flex justify-end"><button type="submit" disabled={processing} className="inline-flex w-full items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-50 dark:bg-blue-600 dark:text-white dark:hover:bg-blue-700 sm:w-auto">{processing ? <LoaderCircle className="h-4 w-4 animate-spin" /> : <ShieldCheck className="h-4 w-4" />}{processing ? '인증 및 접근 허용 중...' : '인증 후 접근 허용'}</button></div>
                            </form> : <div role="status" className="flex items-start gap-2 border-t border-slate-200 p-4 text-sm dark:border-slate-800 md:p-5">{detail.status === 'APPROVED' && <CheckCircle2 className="h-5 w-5 shrink-0 text-emerald-600 dark:text-emerald-400" />}<p>{detail.status === 'APPROVED' ? '접근이 허용된 요청입니다.' : detail.status === 'REJECTED' ? '이미 거절된 요청입니다.' : '처리할 수 없는 요청입니다. 관리자 화면에서 상태를 확인해 주세요.'}</p></div>}
                        </>}
            </section>
        </div>
    </main>;
};

export const AdminAccessRequestApprovalPage = () => {
    const [notifications, setNotifications] = useState<NotificationItem[]>([]);
    const nextId = useRef(0);
    const notify = useCallback((type: NotificationType, message: string) => {
        setNotifications([{ id: ++nextId.current, type, message }]);
    }, []);
    return <><ApprovalForm onNotify={notify} /><NotificationToast items={notifications} onClose={id => setNotifications(items => items.filter(item => item.id !== id))} /></>;
};
