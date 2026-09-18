import { AdminDashboardHeader } from './AdminDashboardHeader';
import type { LucideIcon } from 'lucide-react';
import { AlertTriangle, CalendarDays, CheckCircle2, ChevronRight, Clock3, CreditCard, Users, WalletCards } from 'lucide-react';
import { deadlineLabel, useOperationalDashboard, type DashboardProps, type RegistrationDashboard } from './operationalDashboard';
import { DashboardLoadState } from './DashboardLoadState';

export const AdminPreRegistrationDashboardPage = (props: DashboardProps) => {
    const { data, failed, retry } = useOperationalDashboard<RegistrationDashboard>('/api/admin/dashboard/registration', props.onNotify);
    if (!data) return <DashboardLoadState failed={failed} retry={retry} />;
    return <RegistrationContent data={data} onNavigate={props.onNavigate} />;
};

const number = new Intl.NumberFormat('ko-KR');
const percent = (value: number, total: number) => total > 0 ? value / total * 100 : 0;
const money = (value: number, currency: string) => new Intl.NumberFormat('ko-KR', {
    style: 'currency', currency, currencyDisplay: 'narrowSymbol', minimumFractionDigits: currency === 'USD' ? 2 : 0,
    maximumFractionDigits: currency === 'USD' ? 2 : 0
}).format(value);
const cardClass = 'rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-950';
const RegistrationContent = ({ data, onNavigate }: { data: RegistrationDashboard; onNavigate: DashboardProps['onNavigate'] }) => {
const categoryRows = data.categories.map((category) => ({
    ...category,
    total: category.paid + category.unpaid + category.cancelled + category.refunded,
    active: category.paid + category.unpaid
}));
const totals = categoryRows.reduce((sum, row) => ({
    total: sum.total + row.total, active: sum.active + row.active,
    paid: sum.paid + row.paid, unpaid: sum.unpaid + row.unpaid, longUnpaid: sum.longUnpaid + row.longUnpaid,
    cancelled: sum.cancelled + row.cancelled, refunded: sum.refunded + row.refunded
}), { total: 0, active: 0, paid: 0, unpaid: 0, longUnpaid: 0, cancelled: 0, refunded: 0 });
const latest = data.daily[data.daily.length - 1] ?? { registered: 0, paid: 0 };
const maxDaily = Math.max(1, ...data.daily.flatMap((day) => [day.registered, day.paid]));
const maxCategory = Math.max(1, ...categoryRows.map((row) => row.total));
const paymentRate = percent(totals.paid, totals.active);
const statuses = [
    { label: '입금 완료', value: totals.paid, color: 'bg-emerald-500 dark:bg-emerald-500' },
    { label: '미입금', value: totals.unpaid, color: 'bg-amber-500 dark:bg-amber-500' },
    { label: '취소', value: totals.cancelled, color: 'bg-slate-400 dark:bg-slate-400' },
    { label: '환불', value: totals.refunded, color: 'bg-violet-500 dark:bg-violet-500' }
];

    const openRegistrations = () => onNavigate('pre-registrations');
    const metrics: { label: string; value: number; detail: string; icon: LucideIcon; color: string }[] = [
        { label: '전체 사전등록', value: totals.total, detail: `오늘 신규 ${latest.registered}건 · 취소·환불 포함`, icon: Users, color: 'bg-blue-50 text-blue-600 dark:bg-blue-950/40 dark:text-blue-400' },
        { label: '입금 완료', value: totals.paid, detail: `오늘 결제 완료 ${latest.paid}건`, icon: CheckCircle2, color: 'bg-emerald-50 text-emerald-600 dark:bg-emerald-950/40 dark:text-emerald-400' },
        { label: '사전등록 미입금', value: totals.unpaid, detail: '결제 미완료 신청 · 결제 실패 포함, 취소·환불 제외', icon: Clock3, color: 'bg-amber-50 text-amber-600 dark:bg-amber-950/40 dark:text-amber-400' },
        { label: '7일 이상 미입금', value: totals.longUnpaid, detail: `미입금 ${totals.unpaid}건 중 신청 후 7일 이상 경과`, icon: AlertTriangle, color: 'bg-rose-50 text-rose-600 dark:bg-rose-950/40 dark:text-rose-400' }
    ];

    return (
        <div className="w-full space-y-5 pb-6 text-slate-900 dark:text-slate-50">
            <AdminDashboardHeader
                badge="사전등록 대시보드"
                icon={WalletCards}
                title={data.eventName + ' 사전등록 운영 현황'}
                description="등록구분별 참가 규모와 등록비 입금 현황을 확인하세요."
                deadlines={data.deadlines.map((deadline) => ({
                    label: deadline.label,
                    value: deadlineLabel(deadline.date, data.asOf),
                    accent: deadline.label === '행사 개최' ? undefined : 'text-cyan-200 dark:text-cyan-200'
                }))}
            />

            {totals.total === 0 && <p className="text-sm text-slate-500 dark:text-slate-400">등록된 사전등록 신청이 없습니다.</p>}
            <section className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
                {metrics.map(({ label, value, detail, icon: Icon, color }) => (
                    <article key={label} className={`${cardClass} p-4 md:p-5`}>
                        <div className="flex items-center justify-between">
                            <span className={`flex h-9 w-9 items-center justify-center rounded-lg ${color}`}><Icon size={18} /></span>
                            <button type="button" onClick={openRegistrations} aria-label={`${label}: 사전등록 관리로 이동`} className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-blue-600 dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-blue-400"><ChevronRight size={16} /></button>
                        </div>
                        <p className="mt-4 text-xs font-medium text-slate-500 dark:text-slate-400">{label}</p>
                        <p className="mt-1 text-2xl font-bold tabular-nums">{number.format(value)}<span className="ml-1 text-sm font-medium text-slate-400 dark:text-slate-400">건</span></p>
                        <p className="mt-2 text-[11px] text-slate-400 dark:text-slate-400">{detail}</p>
                    </article>
                ))}
            </section>

            <section className="grid grid-cols-1 gap-4 lg:grid-cols-3">
                {data.amounts.map((amount) => (
                    <article key={amount.currency} className={`${cardClass} p-4 md:p-5`}>
                        <div className="flex items-center justify-between">
                            <h2 className="text-sm font-bold">등록비 입금액 · {amount.currency === 'KRW' ? '원화' : amount.currency === 'USD' ? '달러' : amount.currency}</h2>
                            <span className="rounded-lg bg-blue-50 px-2 py-1 text-[11px] font-semibold text-blue-600 dark:bg-blue-950/40 dark:text-blue-400">{amount.currency}</span>
                        </div>
                        <p className="mt-3 text-2xl font-bold tabular-nums">{money(amount.paid, amount.currency)}</p>
                        <dl className="mt-4 space-y-2 text-xs">
                            <div className="flex justify-between gap-3"><dt className="text-slate-500 dark:text-slate-400">오늘 입금액</dt><dd className="font-semibold tabular-nums text-emerald-600 dark:text-emerald-400">{money(amount.todayPaid, amount.currency)}</dd></div>
                            <div className="flex justify-between gap-3"><dt className="text-slate-500 dark:text-slate-400">미입금 등록비</dt><dd className="font-semibold tabular-nums text-amber-600 dark:text-amber-400">{money(amount.unpaid, amount.currency)}</dd></div>
                        </dl>
                        <p className="mt-3 text-[10px] text-slate-400 dark:text-slate-400">취소·환불 제외 · 결제금액 미기록 시 신청 등록비 기준</p>
                    </article>
                ))}
                <article className={`${cardClass} p-4 md:p-5`}>
                    <h2 className="text-sm font-bold">결제 완료율</h2>
                    <p className="mt-3 text-2xl font-bold tabular-nums text-emerald-600 dark:text-emerald-400">{paymentRate.toFixed(1)}%</p>
                    <div className="mt-4 h-2.5 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800" role="progressbar" aria-label="유효 신청 결제 완료율" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Number(paymentRate.toFixed(1))}>
                        <div className="h-full rounded-full bg-emerald-500 dark:bg-emerald-500" style={{ width: `${paymentRate}%` }} />
                    </div>
                    <p className="mt-3 text-xs text-slate-500 dark:text-slate-400">유효 등록 {number.format(totals.active)}건 중 {number.format(totals.paid)}건 완료</p>
                    <p className="mt-2 text-[10px] text-slate-400 dark:text-slate-400">취소 {totals.cancelled}건 · 환불 {totals.refunded}건은 분모에서 제외</p>
                </article>
            </section>

            <section className="grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,1.65fr)_minmax(0,1fr)]">
                <article className={cardClass}>
                    <SectionHeader title="등록구분별 현황" description="구분별 등록 규모와 입금 상태 · 전체 신청 기준" />
                    <div className="space-y-4 p-4 md:p-5">
                        {categoryRows.map((row) => (
                            <div key={row.categorySeq}>
                                <div className="mb-2 flex justify-between gap-3 text-xs"><span className="font-semibold">{row.categoryName}</span><span className="tabular-nums"><strong>{number.format(row.total)}건</strong><span className="ml-2 text-slate-400 dark:text-slate-400">{percent(row.total, totals.total).toFixed(1)}%</span></span></div>
                                <div className="h-2 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800" aria-hidden="true"><div className="h-full rounded-full bg-blue-500 dark:bg-blue-500" style={{ width: `${percent(row.total, maxCategory)}%` }} /></div>
                            </div>
                        ))}
                        <p className="text-[10px] text-slate-400 dark:text-slate-400">막대 길이는 최다 등록구분 대비, 비율은 전체 신청 대비입니다.</p>
                    </div>
                    <div className="overflow-x-auto">
                        <table className="w-full whitespace-nowrap text-left text-xs">
                            <caption className="sr-only">등록구분별 신청·입금·취소 현황</caption>
                            <thead className="border-y border-slate-100 bg-slate-50 dark:border-slate-800 dark:bg-slate-900/50"><tr>{['등록구분', '등록 수', '입금 완료', '미입금', '7일 이상 미입금', '취소 / 환불', '결제율'].map((label, index) => <th key={label} className={`px-4 py-3 ${index ? 'text-right' : ''}`}>{label}</th>)}</tr></thead>
                            <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                                {categoryRows.map((row) => (
                                    <tr key={row.categorySeq} className="hover:bg-slate-50 dark:hover:bg-slate-900/40">
                                        <td className="px-4 py-3 font-semibold">{row.categoryName}</td>
                                        <td className="px-4 py-3 text-right tabular-nums">{number.format(row.total)}</td>
                                        <td className="px-4 py-3 text-right tabular-nums text-emerald-600 dark:text-emerald-400">{number.format(row.paid)}</td>
                                        <td className="px-4 py-3 text-right tabular-nums text-amber-600 dark:text-amber-400">{row.unpaid}</td>
                                        <td className="px-4 py-3 text-right tabular-nums text-rose-600 dark:text-rose-400">{row.longUnpaid}</td>
                                        <td className="px-4 py-3 text-right tabular-nums">{row.cancelled} / {row.refunded}</td>
                                        <td className="px-4 py-3 text-right font-semibold tabular-nums">{percent(row.paid, row.active).toFixed(1)}%</td>
                                    </tr>
                                ))}
                            </tbody>
                            <tfoot className="border-t border-slate-200 bg-slate-50 font-semibold dark:border-slate-800 dark:bg-slate-900/50"><tr><th className="px-4 py-3">합계</th>{[totals.total, totals.paid, totals.unpaid, totals.longUnpaid].map((value, index) => <td key={index} className="px-4 py-3 text-right tabular-nums">{number.format(value)}</td>)}<td className="px-4 py-3 text-right">{totals.cancelled} / {totals.refunded}</td><td className="px-4 py-3 text-right">{paymentRate.toFixed(1)}%</td></tr></tfoot>
                        </table>
                    </div>
                    <p className="px-4 py-3 text-[10px] text-slate-400 dark:text-slate-400 md:px-5">7일 이상 미입금은 미입금 인원에 포함됩니다. · 결제율 = 입금 완료 ÷ (전체 신청 − 취소 − 환불)</p>
                </article>
                <article className={cardClass}>
                    <SectionHeader title="신청·결제 상태" description="환불은 취소와 중복 집계하지 않고 환불 상태로 표시합니다." />
                    <div className="p-4 md:p-5">
                        <div className="flex h-3 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800" aria-hidden="true">{statuses.map((status) => <span key={status.label} className={status.color} style={{ width: `${percent(status.value, totals.total)}%` }} />)}</div>
                        <dl className="mt-5 space-y-4">
                            {statuses.map((status) => (
                                <div key={status.label} className="flex items-center justify-between gap-3 text-xs"><dt className="flex items-center gap-2"><span className={`h-2 w-2 rounded-full ${status.color}`} />{status.label}</dt><dd className="tabular-nums"><strong>{number.format(status.value)}건</strong><span className="ml-3 text-slate-400 dark:text-slate-400">{percent(status.value, totals.total).toFixed(1)}%</span></dd></div>
                            ))}
                        </dl>
                    </div>
                </article>
            </section>

            <section className="grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,1.65fr)_minmax(0,1fr)]">
                <article className={cardClass}>
                    <SectionHeader title="최근 7일 등록 및 결제" description="신청일 및 현재 입금 완료 상태의 결제일 기준" />
                    <div className="p-4 md:p-5">
                        <div className="mb-5 flex flex-wrap items-center justify-between gap-3 text-[11px] text-slate-500 dark:text-slate-400">
                            <div className="flex gap-4"><span className="flex items-center gap-1.5"><span className="h-2 w-2 rounded-full bg-blue-500 dark:bg-blue-500" />신규 등록</span><span className="flex items-center gap-1.5"><span className="h-2 w-2 rounded-full bg-emerald-500 dark:bg-emerald-500" />결제 완료</span></div>
                            <span>오늘 등록 {latest.registered}건 · 결제 {latest.paid}건</span>
                        </div>
                        <div className="flex h-52 items-end gap-2 sm:gap-4">
                            {data.daily.map((day) => (
                                <div key={day.date} className="flex h-full min-w-0 flex-1 flex-col justify-end">
                                    <div className="mb-2 text-center text-[10px] font-semibold tabular-nums text-slate-500 dark:text-slate-400">{day.registered} / {day.paid}</div>
                                    <div className="flex h-40 items-end justify-center gap-1.5 sm:gap-2">
                                        <div className="w-3 rounded-t bg-blue-500 dark:bg-blue-500 sm:w-5" style={{ height: `${percent(day.registered, maxDaily)}%` }} title={`신규 등록 ${day.registered}건`} />
                                        <div className="w-3 rounded-t bg-emerald-500 dark:bg-emerald-500 sm:w-5" style={{ height: `${percent(day.paid, maxDaily)}%` }} title={`결제 완료 ${day.paid}건`} />
                                    </div>
                                    <span className="mt-2 text-center text-[10px] text-slate-400 dark:text-slate-400">{day.date === data.asOf ? '오늘' : day.date.slice(5).replace('-', '/')}</span>
                                </div>
                            ))}
                        </div>
                        <p className="mt-4 border-t border-slate-100 pt-3 text-[10px] text-slate-400 dark:border-slate-800 dark:text-slate-400">당일 결제에는 이전 신청 건도 포함됩니다. 현재 취소·환불된 결제는 제외합니다.</p>
                    </div>
                </article>
                <div className="space-y-4">
                    <article className={`${cardClass} p-4 md:p-5`}>
                        <h2 className="flex items-center gap-2 text-sm font-bold"><CalendarDays className="h-4 w-4 text-blue-500 dark:text-blue-400" />등록 시기별 현황</h2>
                        <dl className="mt-4 space-y-4">{data.periods.map((period) => <div key={period.label}><div className="flex justify-between text-xs"><dt>{period.label}</dt><dd className="font-semibold tabular-nums">{number.format(period.count)}건 · {percent(period.count, totals.total).toFixed(1)}%</dd></div><div className="mt-2 h-1.5 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800" aria-hidden="true"><div className="h-full rounded-full bg-violet-500 dark:bg-violet-500" style={{ width: `${percent(period.count, totals.total)}%` }} /></div></div>)}</dl>
                        <p className="mt-3 text-[10px] text-slate-400 dark:text-slate-400">신청 시 적용된 등록 시기 · 취소·환불 포함</p>
                    </article>
                    <article className={`${cardClass} p-4 md:p-5`}>
                        <h2 className="flex items-center gap-2 text-sm font-bold"><AlertTriangle className="h-4 w-4 text-amber-500 dark:text-amber-400" />확인할 업무</h2>
                        <ul className="mt-3 space-y-3 text-xs">
                            <li className="flex justify-between gap-3"><span className="text-slate-500 dark:text-slate-400">7일 이상 미입금 · 우선 안내</span><strong className="text-rose-600 dark:text-rose-400">{totals.longUnpaid}건</strong></li>
                            <li className="flex justify-between gap-3"><span className="text-slate-500 dark:text-slate-400">7일 미만 미입금 · 입금 확인</span><strong className="text-amber-600 dark:text-amber-400">{totals.unpaid - totals.longUnpaid}건</strong></li>
                        </ul>
                        <button type="button" onClick={openRegistrations} className="mt-4 inline-flex w-full items-center justify-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900"><CreditCard className="h-4 w-4" />사전등록 관리 열기<ChevronRight className="h-3.5 w-3.5" /></button>
                    </article>
                </div>
            </section>
        </div>
    );
};

const SectionHeader = ({ title, description }: { title: string; description: string }) => (
    <div className="border-b border-slate-100 p-4 dark:border-slate-800 md:px-5">
        <h2 className="text-sm font-bold">{title}</h2>
        <p className="mt-1 text-[11px] text-slate-400 dark:text-slate-400">{description}</p>
    </div>
);
