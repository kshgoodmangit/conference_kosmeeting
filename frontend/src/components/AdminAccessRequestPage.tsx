import { Fragment, useEffect, useRef, useState, type FormEvent } from 'react';
import { Check, ChevronLeft, ChevronRight, Eye, FilterX, Search, ShieldCheck, X } from 'lucide-react';
import type { NotificationType } from './NotificationToast';
import { RowActionMenu } from './RowActionMenu';
import { useConfirm } from './confirmDialogContext';

interface AccessRequest {
    seq: number;
    siteUrl: string;
    requestIp: string;
    affiliation: string;
    requesterName: string;
    contact: string;
    purpose: string;
    startDate: string;
    endDate: string;
    status: string;
    createdAt: string;
    expiresAt: string;
    processedByName?: string;
    processedAt?: string;
    allowlistSeq?: number;
    mailStatus: string;
}
interface PageData {
    items: AccessRequest[];
    summary: { total: number; pending: number; approved: number };
    page: number;
    size: number;
}
interface Props { onNotify: (type: NotificationType, message: string) => void }

const statuses: Record<string, string> = { REQUESTED: '대기', APPROVED: '허용', REJECTED: '거절', EXPIRED: '만료' };
const mailStatuses: Record<string, string> = { READY: '발송 대기', SENT: '발송 완료', FAILED: '발송 실패', NO_RECIPIENT: '수신자 없음' };
const fieldClass = 'w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-700 dark:bg-slate-950 dark:text-slate-50';
const secondaryButton = 'inline-flex items-center justify-center gap-2 rounded-lg border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-900';
const dateTime = (value?: string) => value ? value.replace('T', ' ').slice(0, 16) : '-';

export const AdminAccessRequestPage = ({ onNotify }: Props) => {
    const confirm = useConfirm();
    const notifyRef = useRef(onNotify);
    const actionRef = useRef(false);
    const [keyword, setKeyword] = useState('');
    const [status, setStatus] = useState('');
    const [query, setQuery] = useState({ keyword: '', status: '', page: 1 });
    const [data, setData] = useState<PageData | null>(null);
    const [loading, setLoading] = useState(true);
    const [failed, setFailed] = useState(false);
    const [reloadKey, setReloadKey] = useState(0);
    const [expanded, setExpanded] = useState<number | null>(null);
    const [processing, setProcessing] = useState<number | null>(null);
    useEffect(() => { notifyRef.current = onNotify; }, [onNotify]);
    useEffect(() => {
        const controller = new AbortController();
        void (async () => {
            setLoading(true); setFailed(false);
            try {
                const params = new URLSearchParams({ keyword: query.keyword, status: query.status, page: String(query.page), size: '20' });
                const response = await fetch(`/api/admin/access-requests?${params}`, { signal: controller.signal, cache: 'no-store' });
                if (!response.ok) throw new Error(await response.text() || '접근 허용 요청 목록을 불러오지 못했습니다.');
                const nextData = await response.json() as PageData;
                if (!controller.signal.aborted) {
                    const lastPage = Math.max(1, Math.ceil(nextData.summary.total / nextData.size));
                    if (query.page > lastPage) setQuery(value => ({ ...value, page: lastPage }));
                    else setData(nextData);
                }
            } catch (error) {
                if (controller.signal.aborted) return;
                setData(null); setFailed(true);
                notifyRef.current('error', error instanceof Error ? error.message : '접근 허용 요청 목록을 불러오지 못했습니다.');
            } finally {
                if (!controller.signal.aborted) setLoading(false);
            }
        })();
        return () => controller.abort();
    }, [query, reloadKey]);

    const search = (event: FormEvent) => {
        event.preventDefault(); setExpanded(null); setQuery({ keyword: keyword.trim(), status, page: 1 });
    };
    const process = async (item: AccessRequest, action: 'approve' | 'reject') => {
        if (actionRef.current) return;
        actionRef.current = true;
        try {
            const approval = action === 'approve';
            const accepted = await confirm({
                title: approval ? '접근 허용 IP 등록' : '접근 허용 요청 거절',
                message: approval
                    ? `${item.requesterName} (${item.affiliation})\nIP: ${item.requestIp}\n사용기간: ${item.startDate} ~ ${item.endDate}\n이 서버의 접근허용 IP에 등록하시겠습니까?`
                    : `${item.requesterName}님의 요청(${item.requestIp})을 거절하시겠습니까?`,
                confirmText: approval ? '접근허용' : '거절',
                ...(approval ? {} : { tone: 'danger' as const })
            });
            if (!accepted) return;
            setProcessing(item.seq);
            const response = await fetch(`/api/admin/access-requests/${item.seq}/${action}`, { method: 'POST' });
            if (!response.ok) throw new Error(await response.text() || '요청을 처리하지 못했습니다.');
            const result = await response.json() as { message: string };
            onNotify('success', result.message);
            setReloadKey(value => value + 1);
        } catch (error) {
            onNotify('error', error instanceof Error ? error.message : '요청을 처리하지 못했습니다.');
        } finally {
            actionRef.current = false; setProcessing(null);
        }
    };
    const pageCount = Math.max(1, Math.ceil((data?.summary.total ?? 0) / 20));

    return <section className="overflow-hidden rounded-xl border border-slate-200 bg-white text-slate-900 shadow-sm dark:border-slate-800 dark:bg-slate-950 dark:text-slate-50">
        <header className="border-b border-slate-200 p-4 dark:border-slate-800 md:p-5">
            <h1 className="flex items-center gap-2 text-sm font-semibold md:text-base"><ShieldCheck className="h-4 w-4 text-blue-600 dark:text-blue-400" />접근허용요청</h1>
            <p className="mt-1 text-xs text-slate-400 dark:text-slate-400">접수 내역을 확인하고 요청한 사용기간 동안 관리자 페이지 접근을 허용합니다.</p>
        </header>
        <div className="grid grid-cols-1 border-b border-slate-200 dark:border-slate-800 sm:grid-cols-3">
            {([['검색 결과', 'total'], ['대기 요청', 'pending'], ['허용 완료', 'approved']] as const).map(([label, key]) =>
                <div key={key} className="border-b border-slate-200 p-4 last:border-b-0 dark:border-slate-800 sm:border-b-0 sm:border-r sm:last:border-r-0 md:p-5">
                    <p className="text-xs font-medium text-slate-500 dark:text-slate-400">{label}</p>
                    <p className={`mt-1 text-xl font-bold ${key === 'approved' ? 'text-emerald-600 dark:text-emerald-400' : key === 'pending' ? 'text-blue-600 dark:text-blue-400' : ''}`}>{loading ? '집계 중...' : failed ? '-' : (data?.summary[key] ?? 0).toLocaleString()}</p>
                </div>)}
        </div>
        <form onSubmit={search} className="border-b border-slate-200 bg-slate-50/70 p-4 dark:border-slate-800 dark:bg-slate-900/40 md:p-5">
            <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-4">
                <label className="md:col-span-2"><span className="mb-1 block text-[11px] font-semibold text-slate-500 dark:text-slate-400">검색어</span><div className="relative"><Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-400 dark:text-slate-400" /><input value={keyword} onChange={e => setKeyword(e.target.value)} maxLength={200} placeholder="요청 IP, 이름, 소속" className={`${fieldClass} pl-9`} /></div></label>
                <label><span className="mb-1 block text-[11px] font-semibold text-slate-500 dark:text-slate-400">처리상태</span><select value={status} onChange={e => setStatus(e.target.value)} className={fieldClass}><option value="">전체</option>{Object.entries(statuses).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
            </div>
            <div className="mt-3 flex flex-col justify-end gap-2 sm:flex-row">
                <button type="button" className={secondaryButton} onClick={() => { setKeyword(''); setStatus(''); setExpanded(null); setQuery({ keyword: '', status: '', page: 1 }); }}><FilterX className="h-4 w-4" />초기화</button>
                <button type="submit" className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-5 py-2 text-xs font-semibold text-white hover:bg-blue-700 dark:bg-blue-600 dark:text-white dark:hover:bg-blue-700"><Search className="h-4 w-4" />조회</button>
            </div>
        </form>
        <div className="overflow-x-auto">
            <table className="w-full min-w-[1150px] text-left text-xs md:text-sm">
                <thead className="border-b border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900/50"><tr>{['접수일', '도메인 / 요청 IP', '소속 / 이름', '연락처', '사용기간', '처리상태', '메일', '기능'].map(label => <th key={label} className="p-4">{label}</th>)}</tr></thead>
                <tbody>
                    {loading ? <tr><td colSpan={8} className="p-10 text-center text-slate-400 dark:text-slate-400">요청을 불러오는 중입니다.</td></tr>
                        : failed ? <tr><td colSpan={8} className="p-10 text-center"><p>목록을 불러오지 못했습니다.</p><button type="button" className={`${secondaryButton} mt-3`} onClick={() => setReloadKey(value => value + 1)}>다시 시도</button></td></tr>
                        : !data?.items.length ? <tr><td colSpan={8} className="p-10 text-center text-slate-400 dark:text-slate-400">조회된 요청이 없습니다.</td></tr>
                        : data.items.map(item => <Fragment key={item.seq}>
                            <tr className="border-b border-slate-200 hover:bg-slate-50 dark:border-slate-800 dark:hover:bg-slate-900/50">
                                <td className="whitespace-nowrap p-4">{dateTime(item.createdAt)}<div className="mt-1 text-xs text-slate-400 dark:text-slate-400">#{item.seq}</div></td>
                                <td className="p-4"><span className="break-all text-slate-900 dark:text-slate-50">{new URL(item.siteUrl).host}</span><p className="mt-1 font-mono">{item.requestIp}</p></td>
                                <td className="max-w-52 break-words p-4"><p>{item.affiliation}</p><p className="mt-1 font-semibold">{item.requesterName}</p></td>
                                <td className="whitespace-nowrap p-4">{item.contact}</td>
                                <td className="whitespace-nowrap p-4">{item.startDate}<br />~ {item.endDate}</td>
                                <td className="p-4"><span className={`inline-flex whitespace-nowrap rounded-full px-2.5 py-1 text-xs font-semibold ${item.status === 'APPROVED' ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300' : item.status === 'REQUESTED' ? 'bg-blue-50 text-blue-700 dark:bg-blue-950/40 dark:text-blue-300' : 'bg-slate-100 text-slate-600 dark:bg-slate-900 dark:text-slate-400'}`}>{statuses[item.status] ?? item.status}</span></td>
                                <td className="whitespace-nowrap p-4 text-xs">{mailStatuses[item.mailStatus] ?? item.mailStatus}</td>
                                <td className="p-4"><RowActionMenu itemLabel={`요청 ${item.seq}`} actions={[
                                    { label: '상세보기', icon: <Eye className="h-4 w-4" />, onClick: () => setExpanded(expanded === item.seq ? null : item.seq) },
                                    { label: '접근허용', icon: <Check className="h-4 w-4" />, disabled: item.status !== 'REQUESTED' || processing !== null, onClick: () => { void process(item, 'approve'); } },
                                    { label: '거절', icon: <X className="h-4 w-4" />, tone: 'danger', disabled: item.status !== 'REQUESTED' || processing !== null, onClick: () => { void process(item, 'reject'); } }
                                ]} /></td>
                            </tr>
                            {expanded === item.seq && <tr className="border-b border-slate-200 bg-slate-50/70 dark:border-slate-800 dark:bg-slate-900/40"><td colSpan={8} className="p-5"><p className="font-semibold">요청 목적</p><p className="mt-2 max-w-4xl whitespace-pre-wrap break-words">{item.purpose}</p><p className="mt-3 text-xs text-slate-500 dark:text-slate-400">대기 만료: {dateTime(item.expiresAt)} · 처리자: {item.processedByName ?? '-'} · 처리일: {dateTime(item.processedAt)}{item.allowlistSeq ? ` · 허용 IP 규칙 #${item.allowlistSeq}` : ''}</p></td></tr>}
                        </Fragment>)}
                </tbody>
            </table>
        </div>
        <div className="flex items-center justify-between gap-3 p-4 text-xs text-slate-500 dark:text-slate-400">
            <span>총 {data?.summary.total ?? 0}건 · {query.page} / {pageCount} 페이지</span>
            <div className="flex gap-2"><button type="button" className={secondaryButton} disabled={loading || query.page <= 1} onClick={() => setQuery(value => ({ ...value, page: value.page - 1 }))}><ChevronLeft className="h-4 w-4" />이전</button><button type="button" className={secondaryButton} disabled={loading || query.page >= pageCount} onClick={() => setQuery(value => ({ ...value, page: value.page + 1 }))}>다음<ChevronRight className="h-4 w-4" /></button></div>
        </div>
    </section>;
};
