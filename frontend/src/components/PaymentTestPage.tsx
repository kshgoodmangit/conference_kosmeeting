import { useEffect, useRef, useState } from 'react';
import {
    AlertTriangle,
    CheckCircle2,
    CreditCard,
    LoaderCircle,
    Settings2
} from 'lucide-react';
import type { NotificationType } from './NotificationToast';
import {
    startConfiguredPayment,
    type PaymentCheckoutSession,
    type PaymentResult
} from '../payment/paymentGatewayClient';

interface PaymentTestPageProps {
    onNotify: (type: NotificationType, message: string) => void;
}

interface PaymentRouteSummary {
    route: string;
    label: string;
    configured: boolean;
    merchantIdHint: string;
    payMethod: string;
    currency: string;
    language: string;
}

interface PaymentGatewaySummary {
    enabled: boolean;
    provider: string;
    providerDisplayName: string;
    message: string;
    routes: PaymentRouteSummary[];
}

type MemberType = 'domestic' | 'international';
type CardRoute = 'domestic-card' | 'international-card';

const routeDefaults: Record<CardRoute, { amount: string; label: string }> = {
    'domestic-card': { amount: '100', label: '한국에서 발급된 카드' },
    'international-card': { amount: '1.00', label: '해외에서 발급된 카드' }
};

const readErrorMessage = async (response: Response) => {
    const contentType = response.headers.get('content-type') ?? '';
    if (contentType.includes('application/json')) {
        const data = await response.json() as { message?: string };
        return data.message ?? '결제 테스트 요청에 실패했습니다.';
    }
    return await response.text() || '결제 테스트 요청에 실패했습니다.';
};

export const PaymentTestPage = ({ onNotify }: PaymentTestPageProps) => {
    const [summary, setSummary] = useState<PaymentGatewaySummary | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [isStarting, setIsStarting] = useState(false);
    const [memberType, setMemberType] = useState<MemberType>('domestic');
    const [route, setRoute] = useState<CardRoute>('domestic-card');
    const [amount, setAmount] = useState(routeDefaults['domestic-card'].amount);
    const [productName, setProductName] = useState('APDRC8 결제 테스트');
    const [buyerName, setBuyerName] = useState('김신형');
    const [buyerEmail, setBuyerEmail] = useState('shkim@bjworld21.com');
    const [buyerPhone, setBuyerPhone] = useState('01091595299');
    const [checkoutSession, setCheckoutSession] = useState<PaymentCheckoutSession | null>(null);
    const [paymentResult, setPaymentResult] = useState<PaymentResult | null>(null);
    const screenRef = useRef<HTMLDivElement>(null);
    const formRef = useRef<HTMLFormElement>(null);
    const notifyRef = useRef(onNotify);

    useEffect(() => {
        notifyRef.current = onNotify;
    }, [onNotify]);

    useEffect(() => {
        const abortController = new AbortController();
        const loadConfiguration = async () => {
            try {
                const response = await fetch('/api/admin/payment-test/config', {
                    signal: abortController.signal,
                    cache: 'no-store'
                });
                if (!response.ok) {
                    throw new Error(await readErrorMessage(response));
                }
                setSummary(await response.json() as PaymentGatewaySummary);
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') {
                    return;
                }
                notifyRef.current('error', error instanceof Error ? error.message : 'PG 설정을 불러오지 못했습니다.');
            } finally {
                if (!abortController.signal.aborted) {
                    setIsLoading(false);
                }
            }
        };

        void loadConfiguration();
        return () => abortController.abort();
    }, []);

    const handleMemberTypeChange = (nextMemberType: MemberType) => {
        const nextRoute: CardRoute = nextMemberType === 'domestic' ? 'domestic-card' : 'international-card';
        setMemberType(nextMemberType);
        setRoute(nextRoute);
        setAmount(routeDefaults[nextRoute].amount);
        setCheckoutSession(null);
        setPaymentResult(null);
    };

    const handleRouteChange = (nextRoute: CardRoute) => {
        setRoute(nextRoute);
        setAmount(routeDefaults[nextRoute].amount);
        setCheckoutSession(null);
        setPaymentResult(null);
    };

    const selectedRoute = summary?.routes.find((item) => item.route === route);
    const canStart = Boolean(summary?.enabled && selectedRoute?.configured && !isStarting);

    const handleSubmit = async (event: React.FormEvent) => {
        event.preventDefault();
        if (!screenRef.current || !formRef.current) {
            onNotify('error', '결제 화면을 초기화하지 못했습니다.');
            return;
        }

        setIsStarting(true);
        setPaymentResult(null);
        try {
            const response = await fetch('/api/admin/payment-test/checkout', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    memberType,
                    route,
                    amount: Number(amount),
                    productName,
                    buyerName,
                    buyerEmail,
                    buyerPhone
                })
            });
            if (!response.ok) {
                throw new Error(await readErrorMessage(response));
            }

            const session = await response.json() as PaymentCheckoutSession;
            setCheckoutSession(session);
            const result = await startConfiguredPayment(session, formRef.current, screenRef.current);
            setPaymentResult(result);
            if (result.replycode === '0000') {
                onNotify('success', 'PayGate 테스트 결제가 완료되었습니다.');
            } else {
                onNotify('error', result.replyMsg || 'PayGate 테스트 결제가 완료되지 않았습니다.');
            }
        } catch (error) {
            onNotify('error', error instanceof Error ? error.message : '결제 테스트를 시작하지 못했습니다.');
        } finally {
            setIsStarting(false);
        }
    };

    return (
        <div className="mx-auto max-w-6xl space-y-6">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div>
                    <h1 className="text-2xl font-bold tracking-tight">PG 카드결제 테스트</h1>
                    <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
                        application.yaml에서 선택된 PG사와 국내·해외 카드 설정을 확인하고 실제 결제창을 호출합니다.
                    </p>
                </div>
            </div>

            {isLoading && (
                <div className="flex min-h-40 items-center justify-center rounded-2xl border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-950">
                    <LoaderCircle className="h-6 w-6 animate-spin text-blue-600" />
                </div>
            )}

            {!isLoading && summary && (
                <>
                    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-950">
                        <div className="flex items-start gap-3">
                            <div className="rounded-xl bg-blue-50 p-2.5 text-blue-600 dark:bg-blue-950/50 dark:text-blue-400">
                                <Settings2 className="h-5 w-5" />
                            </div>
                            <div className="min-w-0 flex-1">
                                <div className="flex flex-wrap items-center gap-2">
                                    <h2 className="font-bold">활성 PG사: {summary.providerDisplayName}</h2>
                                    <span className={`rounded-full px-2 py-0.5 text-[11px] font-semibold ${
                                        summary.enabled
                                            ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-950/50 dark:text-emerald-300'
                                            : 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300'
                                    }`}>
                                        {summary.enabled ? '사용 가능' : '비활성'}
                                    </span>
                                </div>
                                <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{summary.message}</p>
                            </div>
                        </div>

                        <div className="mt-5 grid gap-3 md:grid-cols-2">
                            {summary.routes.map((item) => (
                                <div key={item.route} className="rounded-xl border border-slate-200 p-4 dark:border-slate-800">
                                    <div className="flex items-center justify-between gap-3">
                                        <span className="font-semibold">{item.label}</span>
                                        {item.configured
                                            ? <CheckCircle2 className="h-5 w-5 text-emerald-500" />
                                            : <AlertTriangle className="h-5 w-5 text-amber-500" />}
                                    </div>
                                    <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-2 text-xs">
                                        <dt className="text-slate-400">MID</dt><dd className="text-right font-mono">{item.merchantIdHint}</dd>
                                        <dt className="text-slate-400">paymethod</dt><dd className="text-right font-mono">{item.payMethod || '-'}</dd>
                                        <dt className="text-slate-400">통화</dt><dd className="text-right font-mono">{item.currency || '-'}</dd>
                                        <dt className="text-slate-400">언어</dt><dd className="text-right font-mono">{item.language || '-'}</dd>
                                    </dl>
                                </div>
                            ))}
                        </div>
                    </section>

                    <div className="flex gap-3 rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800 dark:border-rose-900/60 dark:bg-rose-950/40 dark:text-rose-200">
                        <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0" />
                        결제 버튼을 누르면 실제 카드 승인이 발생할 수 있습니다.
                    </div>

                    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(320px,0.8fr)]">
                        <form onSubmit={handleSubmit} className="space-y-5 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-950">
                            <div className="flex items-center gap-2">
                                <CreditCard className="h-5 w-5 text-blue-600 dark:text-blue-400" />
                                <h2 className="font-bold">결제 요청 정보</h2>
                            </div>

                            <fieldset>
                                <legend className="mb-2 text-sm font-medium">회원 유형</legend>
                                <div className="grid grid-cols-2 gap-2">
                                    {(['domestic', 'international'] as MemberType[]).map((value) => (
                                        <label key={value} className={`cursor-pointer rounded-xl border p-3 text-sm ${
                                            memberType === value
                                                ? 'border-blue-500 bg-blue-50 text-blue-700 dark:bg-blue-950/40 dark:text-blue-300'
                                                : 'border-slate-200 dark:border-slate-800'
                                        }`}>
                                            <input className="mr-2" type="radio" name="memberType" checked={memberType === value} onChange={() => handleMemberTypeChange(value)} />
                                            {value === 'domestic' ? '국내회원' : '국제회원'}
                                        </label>
                                    ))}
                                </div>
                            </fieldset>

                            <fieldset>
                                <legend className="mb-2 text-sm font-medium">카드 발급 국가</legend>
                                <div className="grid gap-2 sm:grid-cols-2">
                                    {(Object.keys(routeDefaults) as CardRoute[]).map((value) => (
                                        <label key={value} className={`cursor-pointer rounded-xl border p-3 text-sm ${
                                            route === value
                                                ? 'border-blue-500 bg-blue-50 text-blue-700 dark:bg-blue-950/40 dark:text-blue-300'
                                                : 'border-slate-200 dark:border-slate-800'
                                        }`}>
                                            <input className="mr-2" type="radio" name="route" checked={route === value} onChange={() => handleRouteChange(value)} />
                                            {routeDefaults[value].label}
                                        </label>
                                    ))}
                                </div>
                            </fieldset>

                            <div className="grid gap-4 sm:grid-cols-2">
                                <TextField
                                    label="테스트 금액"
                                    value={amount}
                                    onChange={setAmount}
                                    type="number"
                                    min={route === 'domestic-card' ? '1' : '0.01'}
                                    step={route === 'domestic-card' ? '1' : '0.01'}
                                />
                                <TextField label="상품명" value={productName} onChange={setProductName} />
                                <TextField label="결제자 이름" value={buyerName} onChange={setBuyerName} />
                                <TextField label="결제자 이메일" value={buyerEmail} onChange={setBuyerEmail} type="email" />
                                <TextField label="결제자 전화번호" value={buyerPhone} onChange={setBuyerPhone} />
                            </div>

                            <button
                                type="submit"
                                disabled={!canStart}
                                className="flex w-full items-center justify-center gap-2 rounded-xl bg-blue-600 px-4 py-3 text-sm font-semibold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-slate-300 dark:disabled:bg-slate-700"
                            >
                                {isStarting ? <LoaderCircle className="h-4 w-4 animate-spin" /> : <CreditCard className="h-4 w-4" />}
                                {isStarting ? '결제 응답 대기 중' : '설정된 PG사로 결제 테스트'}
                            </button>
                        </form>

                        <section className="space-y-4">
                            <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-950">
                                <h2 className="font-bold">PG 결제 화면</h2>
                                <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">PG사가 생성하는 인증·결제 UI가 아래 영역에 표시됩니다.</p>
                                <div ref={screenRef} className="mt-4 min-h-48 rounded-xl border border-dashed border-slate-300 bg-slate-50 p-3 dark:border-slate-700 dark:bg-slate-900" />
                                <form ref={formRef} className="hidden" />
                            </div>

                            {checkoutSession && (
                                <div className="rounded-2xl border border-slate-200 bg-white p-5 text-sm shadow-sm dark:border-slate-800 dark:bg-slate-950">
                                    <h2 className="font-bold">준비된 결제 세션</h2>
                                    <dl className="mt-3 space-y-2">
                                        <InfoRow label="PG사" value={checkoutSession.providerDisplayName} />
                                        <InfoRow label="주문번호" value={checkoutSession.orderNumber} mono />
                                        <InfoRow label="결제금액" value={`${checkoutSession.amount} ${checkoutSession.currency}`} />
                                        <InfoRow label="결제경로" value={checkoutSession.route} mono />
                                    </dl>
                                    <p className="mt-3 text-xs text-amber-700 dark:text-amber-300">{checkoutSession.notice}</p>
                                </div>
                            )}

                            {paymentResult && (
                                <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-950">
                                    <h2 className="font-bold">결제 결과</h2>
                                    <pre className="mt-3 max-h-80 overflow-auto rounded-xl bg-slate-950 p-4 text-xs text-emerald-300">{JSON.stringify(paymentResult, null, 2)}</pre>
                                </div>
                            )}
                        </section>
                    </div>
                </>
            )}
        </div>
    );
};

interface TextFieldProps {
    label: string;
    value: string;
    onChange: (value: string) => void;
    type?: string;
    min?: string;
    step?: string;
}

const TextField = ({ label, value, onChange, type = 'text', min, step }: TextFieldProps) => (
    <label className="block text-sm">
        <span className="mb-1.5 block font-medium">{label}</span>
        <input
            required
            type={type}
            step={step}
            min={min}
            value={value}
            onChange={(event) => onChange(event.target.value)}
            className="w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 dark:border-slate-700 dark:bg-slate-900"
        />
    </label>
);

const InfoRow = ({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) => (
    <div className="flex items-start justify-between gap-4">
        <dt className="text-slate-400">{label}</dt>
        <dd className={`text-right ${mono ? 'font-mono text-xs' : 'font-medium'}`}>{value}</dd>
    </div>
);
