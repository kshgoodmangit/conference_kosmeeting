import { AdminDashboardHeader } from './AdminDashboardHeader';
import { AlertTriangle, ArrowUpRight, CheckCircle2, ChevronRight, FileText, Users } from 'lucide-react';
import { deadlineLabel, useOperationalDashboard, type DashboardProps, type AbstractDashboard } from './operationalDashboard';
import { DashboardLoadState } from './DashboardLoadState';

export const AdminAbstractDashboardPage = (props: DashboardProps) => {
    const { data, failed, retry } = useOperationalDashboard<AbstractDashboard>('/api/admin/dashboard/abstracts', props.onNotify);
    if (!data) return <DashboardLoadState failed={failed} retry={retry} />;
    return <AbstractContent data={data} onNavigate={props.onNavigate} />;
};

const card = 'min-w-0 rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-950';
const muted = 'text-slate-500 dark:text-slate-400';
const number = new Intl.NumberFormat('ko-KR');
const percentage = (value: number, total: number) => total > 0 ? value / total * 100 : 0;
const AbstractContent = ({ data, onNavigate }: { data: AbstractDashboard; onNavigate: DashboardProps['onNavigate'] }) => {
const rows = data.fields.map((field) => ({ ...field, total: field.unassigned + field.reviewing + field.completed }));
const totals = rows.reduce((sum, row) => ({
    total: sum.total + row.total, unassigned: sum.unassigned + row.unassigned,
    reviewing: sum.reviewing + row.reviewing, completed: sum.completed + row.completed, overdue: sum.overdue + row.overdue
}), { total: 0, unassigned: 0, reviewing: 0, completed: 0, overdue: 0 });
const rate = percentage(totals.completed, totals.total);
const latest = data.daily[data.daily.length - 1] ?? { submitted: 0 };
const maxDaily = Math.max(1, ...data.daily.map((day) => day.submitted));
const weekly = data.daily.reduce((sum, day) => sum + day.submitted, 0);
const statuses = [
    { label: '심사위원 미배정', value: totals.unassigned, color: 'bg-amber-500 dark:bg-amber-500' },
    { label: '심사 중', value: totals.reviewing, color: 'bg-blue-500 dark:bg-blue-500' },
    { label: '심사 완료', value: totals.completed, color: 'bg-emerald-500 dark:bg-emerald-500' }
];
const colors = ['bg-blue-500 dark:bg-blue-500', 'bg-violet-500 dark:bg-violet-500', 'bg-slate-400 dark:bg-slate-400', 'bg-amber-500 dark:bg-amber-500', 'bg-cyan-500 dark:bg-cyan-500'];
const decisions = data.decisions.map((item, index) => ({ ...item, color: colors[index % colors.length] }));

    const metrics = [
        { label: '접수 초록', value: number.format(totals.total), unit: '건', detail: `오늘 신규 ${latest.submitted}건 · 제출 완료 기준`, icon: FileText, color: 'bg-blue-50 text-blue-600 dark:bg-blue-950/40 dark:text-blue-400' },
        { label: '심사위원 미배정', value: number.format(totals.unassigned), unit: '건', detail: '심사위원이 한 명도 배정되지 않은 초록', icon: Users, color: 'bg-amber-50 text-amber-600 dark:bg-amber-950/40 dark:text-amber-400' },
        { label: '심사 완료율', value: rate.toFixed(1), unit: '%', detail: `전체 ${totals.total}건 중 필수 심사 완료 ${totals.completed}건`, icon: CheckCircle2, color: 'bg-emerald-50 text-emerald-600 dark:bg-emerald-950/40 dark:text-emerald-400' },
        { label: '심사기한 초과', value: number.format(totals.overdue), unit: '건', detail: `심사 중 ${totals.reviewing}건 중 기한이 지난 초록`, icon: AlertTriangle, color: 'bg-rose-50 text-rose-600 dark:bg-rose-950/40 dark:text-rose-400' }
    ];
    return (
        <div className="w-full space-y-5 pb-6 text-slate-900 dark:text-slate-50">
            <AdminDashboardHeader
                badge="초록 대시보드"
                icon={FileText}
                title={data.eventName + ' 초록 운영 현황'}
                description="접수부터 심사, 채택 결과와 제출자 등록까지 확인하세요."
                deadlines={data.deadlines.map((deadline) => ({
                    label: deadline.label,
                    value: deadlineLabel(deadline.date, data.asOf),
                    accent: deadline.label === '행사 개최' ? undefined : 'text-amber-200 dark:text-amber-200'
                }))}
            />

            {totals.total === 0 && <p className="text-sm text-slate-500 dark:text-slate-400">접수된 초록이 없습니다. 임시저장 초록은 제외합니다.</p>}
            <section aria-label="초록 주요 지표" className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
                {metrics.map(({ label, value, unit, detail, icon: Icon, color }) => <article key={label} className={`${card} p-4 md:p-5`}>
                    <span className={`flex h-9 w-9 items-center justify-center rounded-lg ${color}`}><Icon size={18} /></span>
                    <h2 className={`mt-4 text-xs font-medium ${muted}`}>{label}</h2>
                    <p className="mt-1 text-2xl font-bold tabular-nums">{value}<span className={`ml-1 text-sm font-medium ${muted}`}>{unit}</span></p>
                    <p className={`mt-2 text-[11px] ${muted}`}>{detail}</p>
                </article>)}
            </section>

            <section className="grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,1.8fr)_minmax(0,1fr)]">
                <article className={card}>
                    <SectionHeader title="분야별 접수 및 심사" description="제출 완료 초록 기준 · 임시저장·철회 제외" />
                    <div className="overflow-x-auto">
                        <table className="w-full whitespace-nowrap text-left text-xs" aria-label="분야별 초록 접수와 심사 현황">
                            <thead className="bg-slate-50 dark:bg-slate-900/60"><tr>{['초록 분야', '접수', '미배정', '심사 중', '심사 완료', '완료율'].map((label, i) => <th key={label} scope="col" className={`p-4 ${i ? 'text-right' : ''}`}>{label}</th>)}</tr></thead>
                            <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                                {rows.map((row) => <tr key={row.categorySeq} className="hover:bg-slate-50 dark:hover:bg-slate-900/40">
                                    <th scope="row" className="p-4 font-semibold">{row.name}<div className="mt-2 h-1.5 w-28 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800"><div className="h-full rounded-full bg-blue-500 dark:bg-blue-500" style={{ width: `${percentage(row.total, totals.total)}%` }} /></div></th>
                                    <td className="p-4 text-right tabular-nums">{row.total}</td>
                                    <td className="p-4 text-right tabular-nums text-amber-600 dark:text-amber-400">{row.unassigned}</td>
                                    <td className="p-4 text-right tabular-nums">{row.reviewing}</td>
                                    <td className="p-4 text-right tabular-nums text-emerald-600 dark:text-emerald-400">{row.completed}</td>
                                    <td className="p-4 text-right font-semibold tabular-nums">{percentage(row.completed, row.total).toFixed(1)}%</td>
                                </tr>)}
                            </tbody>
                            <tfoot className="border-t border-slate-200 bg-slate-50 font-semibold dark:border-slate-800 dark:bg-slate-900/50"><tr><th scope="row" className="p-4">합계</th>{[totals.total, totals.unassigned, totals.reviewing, totals.completed].map((value, i) => <td key={i} className="p-4 text-right tabular-nums">{value}</td>)}<td className="p-4 text-right">{rate.toFixed(1)}%</td></tr></tfoot>
                        </table>
                    </div>
                    <p className={`px-4 py-3 text-[11px] md:px-5 ${muted}`}>분야 아래 막대는 전체 접수 대비 비중입니다. 완료율 = 심사 완료 ÷ 해당 분야 접수.</p>
                </article>
                <article className={card}>
                    <SectionHeader title="심사 진행 현황" description="심사위원별 배정 건수가 아닌 초록 건수 기준" />
                    <div className="p-4 md:p-5">
                        <p className="text-3xl font-bold tabular-nums text-emerald-600 dark:text-emerald-400">{rate.toFixed(1)}<span className="text-base">%</span></p>
                        <p className={`mt-1 text-xs ${muted}`}>2명 이상 배정 · 모든 심사 결과 제출 초록 {totals.completed}건</p>
                        <Distribution items={statuses} total={totals.total} />
                        <p className={`mt-5 rounded-lg bg-slate-50 p-3 text-[11px] leading-relaxed dark:bg-slate-900 ${muted}`}>심사 중에는 배정 인원이 2명 미만이거나 심사 결과가 일부만 제출된 초록도 포함됩니다. 최종 결정된 초록은 기한 초과에서 제외합니다. 심사기한 초과 {totals.overdue}건은 심사 중 {totals.reviewing}건에 포함됩니다.</p>
                    </div>
                </article>
            </section>

            <section className="grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,1.8fr)_minmax(0,1fr)]">
                <article className={card}>
                    <SectionHeader title="최근 7일 초록 접수" description={`제출일 기준 · 최근 7일 ${weekly}건 / 오늘 ${latest.submitted}건`} />
                    <div className="p-4 md:p-5">
                        <div className="flex h-48 items-end gap-2 sm:gap-5" role="img" aria-label={`최근 7일 접수: ${data.daily.map((day) => `${day.date} ${day.submitted}건`).join(', ')}`}>
                            {data.daily.map((day) => <div key={day.date} className="flex h-full min-w-0 flex-1 flex-col items-center justify-end">
                                <div className="flex h-40 w-full flex-col items-center justify-end">
                                    <span className="mb-2 text-xs font-semibold tabular-nums">{day.submitted}</span>
                                    <div className={`w-6 rounded-t sm:w-10 ${day.date === data.asOf ? 'bg-indigo-500 dark:bg-indigo-400' : 'bg-blue-500 dark:bg-blue-500'}`} style={{ height: `${percentage(day.submitted, maxDaily) * 0.8}%` }} />
                                </div>
                                <span className={`mt-3 text-[11px] ${muted}`}>{day.date === data.asOf ? '오늘' : day.date.slice(5).replace('-', '/')}</span>
                            </div>)}
                        </div>
                        <p className={`mt-4 text-[11px] ${muted}`}>초록별 1건 집계 · 제출일이 없는 기존 자료는 생성일 기준입니다.</p>
                    </div>
                </article>
                <article className={card}>
                    <SectionHeader title="초록 채택 결과" description={`전체 접수 ${totals.total}건 기준 · 강제 결정 포함`} />
                    <div className="px-4 pb-5 md:px-5"><Distribution items={decisions} total={totals.total} /><p className={`mt-4 text-[11px] ${muted}`}>결과 확정 대기 {data.pendingDecisionCount}건은 심사가 끝났으나 최종 결과가 확정되지 않은 초록입니다.</p></div>
                </article>
            </section>

            <section className="grid grid-cols-1 gap-4 lg:grid-cols-2">
                <article className={card}>
                    <SectionHeader title="채택 초록 제출자 등록" description="접수 회원 ID 기준 · 유효 사전등록 신청 여부 (미입금 포함)" />
                    <div className="space-y-4 p-4 md:p-5">
                        {data.presenters.length === 0 && <p className={`text-xs ${muted}`}>채택된 초록이 없습니다.</p>}
                        {data.presenters.map((item) => <div key={item.presentationTypeSeq}>
                            <div className="mb-2 flex flex-wrap items-center justify-between gap-2 text-xs"><h3 className="font-semibold">{item.label} · {item.registered + item.unregistered}건</h3><span className={muted}>등록 {item.registered}건 / 미등록 <strong className="text-amber-600 dark:text-amber-400">{item.unregistered}건</strong></span></div>
                            <div className="h-2 overflow-hidden rounded-full bg-amber-100 dark:bg-amber-950" role="progressbar" aria-label={`${item.label} 제출자 등록률`} aria-valuemin={0} aria-valuemax={100} aria-valuenow={percentage(item.registered, item.registered + item.unregistered)}><div className="h-full bg-blue-500 dark:bg-blue-500" style={{ width: `${percentage(item.registered, item.registered + item.unregistered)}%` }} /></div>
                            <button type="button" onClick={() => onNavigate('abstract-submissions')} className="mt-2 inline-flex items-center gap-1 text-xs text-blue-600 hover:underline dark:text-blue-400">초록 접수 내역 <ArrowUpRight size={13} /></button>
                        </div>)}
                        <p className={`text-[11px] ${muted}`}>초록별 집계로 중복 회원이 포함됩니다. 제출자와 발표자는 다를 수 있으며 취소·환불 신청은 등록으로 보지 않습니다.</p>
                    </div>
                </article>
                <article className={card}>
                    <SectionHeader title="확인이 필요한 업무" description="아래 항목은 서로 중복될 수 있어 합산하지 않습니다." />
                    <dl className="divide-y divide-slate-100 px-4 dark:divide-slate-800 md:px-5">
                        {[
                            { label: '심사기한 초과', detail: '미완료 심사 확인 및 심사위원 안내', value: totals.overdue },
                            { label: '심사위원 미배정', detail: '접수 분야별 배정 필요', value: totals.unassigned },
                            { label: '결과 확정 대기', detail: '심사 완료 후 최종 채택 여부 결정', value: data.pendingDecisionCount },
                            { label: '채택 초록 제출자 미등록', detail: '제출자에게 사전등록 안내', value: data.presenters.reduce((sum, item) => sum + item.unregistered, 0) }
                        ].map((task) => <div key={task.label} className="flex items-center justify-between gap-3 py-3"><dt><p className="text-xs font-semibold">{task.label}</p><p className={`mt-1 text-[11px] ${muted}`}>{task.detail}</p></dt><dd className="text-sm font-bold tabular-nums">{task.value}건</dd></div>)}
                    </dl>
                    <div className="px-4 pb-4 md:px-5"><button type="button" onClick={() => onNavigate('abstract-submissions')} className="inline-flex items-center gap-1 rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900">초록 접수 내역 열기 <ChevronRight size={14} /></button></div>
                </article>
            </section>
        </div>
    );
};

function SectionHeader({ title, description }: { title: string; description: string }) {
    return <header className="border-b border-slate-100 p-4 dark:border-slate-800 md:p-5"><h2 className="text-sm font-semibold md:text-base">{title}</h2><p className={`mt-1 text-xs ${muted}`}>{description}</p></header>;
}

function Distribution({ items, total }: { items: { label: string; value: number; color: string }[]; total: number }) {
    return <>
        <div className="mt-5 flex h-2.5 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800" aria-hidden="true">{items.map((item) => <span key={item.label} className={item.color} style={{ width: `${percentage(item.value, total)}%` }} />)}</div>
        <dl className="mt-5 space-y-4">{items.map((item) => <div key={item.label} className="flex items-center justify-between gap-3 text-xs"><dt className="flex items-center gap-2"><span className={`h-2 w-2 rounded-full ${item.color}`} />{item.label}</dt><dd className="tabular-nums"><strong>{item.value}건</strong><span className={`ml-3 ${muted}`}>{percentage(item.value, total).toFixed(1)}%</span></dd></div>)}</dl>
    </>;
}
