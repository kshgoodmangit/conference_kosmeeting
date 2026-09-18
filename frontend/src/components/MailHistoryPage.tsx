import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { createPortal } from 'react-dom';
import { ArrowLeft, AtSign, ChevronLeft, ChevronRight, Download, Eye, FilterX, Mail, Search, X } from 'lucide-react';
import { DraggableModal } from './DraggableModal';
import type { NotificationType } from './NotificationToast';
import { MAIL_HISTORY_STATUS, MAIL_SOURCE_MENUS, type MailEmailHistoryItem, type MailEmailItem, type MailEmailSummary, type MailHistoryDetail, type MailHistoryItem } from './mailHistoryTypes';

interface Props { onNotify: (type: NotificationType, message: string) => void }
interface Filters { keyword: string; sourceMenu: string; status: string; dateFrom: string; dateTo: string }
interface HistoryPage { items: MailHistoryItem[]; page: number; totalPages: number; summary: { totalCount: number; savedCount: number; recipientCount: number } }
interface EmailPage { items: MailEmailItem[]; page: number; totalPages: number; summary: MailEmailSummary }
interface EmailHistoryPage { email: string; items: MailEmailHistoryItem[]; page: number; totalPages: number; summary: MailEmailSummary }
type LoadedPage = { kind: 'mail'; result: HistoryPage } | { kind: 'email'; result: EmailPage } | { kind: 'email-history'; result: EmailHistoryPage };
type View = 'mail' | 'email';
const EMPTY: Filters = { keyword: '', sourceMenu: '', status: '', dateFrom: '', dateTo: '' };
const inputClass = 'w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-700 dark:bg-slate-950 dark:text-slate-50';
const buttonClass = 'inline-flex items-center justify-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900';
const labelClass = 'mb-1 block text-[11px] font-semibold text-slate-500 dark:text-slate-400';
const dateText = (value?: string) => value ? value.replace('T', ' ').slice(0, 19) : '-';
const exclusionText = (reason?: string) => reason === 'INVALID_EMAIL' ? '이메일 누락·형식 오류' : reason === 'SUPPRESSED' ? '수신 거부' : reason;

export const MailHistoryPage = ({ onNotify }: Props) => {
    const [draft, setDraft] = useState<Filters>(EMPTY);
    const [filters, setFilters] = useState<Filters>(EMPTY);
    const [page, setPage] = useState(1);
    const [reload, setReload] = useState(0);
    const [view, setView] = useState<View>('mail');
    const [emailSelection, setEmailSelection] = useState<MailEmailItem | null>(null);
    const [data, setData] = useState<LoadedPage | null>(null);
    const [loading, setLoading] = useState(true);
    const [failed, setFailed] = useState(false);
    const [selected, setSelected] = useState<{ seq: number; recipientSeq?: number } | null>(null);
    const notifyRef = useRef(onNotify);
    useEffect(() => { notifyRef.current = onNotify; }, [onNotify]);
    useEffect(() => {
        const controller = new AbortController();
        const load = async () => {
            setLoading(true); setFailed(false); setData(null);
            try {
                const params = new URLSearchParams({ ...filters, page: String(page), size: '20' });
                const path = view === 'mail' ? '' : emailSelection ? `/emails/${emailSelection.recipientSeq}` : '/emails';
                const response = await fetch(`/api/admin/mail-history${path}?${params}`, { signal: controller.signal });
                if (!response.ok) throw new Error(await response.text() || '메일 이력을 불러오지 못했습니다.');
                const result = await response.json();
                if (!controller.signal.aborted) {
                    setData(view === 'mail' ? { kind: 'mail', result: result as HistoryPage }
                        : emailSelection ? { kind: 'email-history', result: result as EmailHistoryPage } : { kind: 'email', result: result as EmailPage });
                    setPage(result.page);
                }
            } catch (error) {
                if (!controller.signal.aborted) { setFailed(true); notifyRef.current('error', error instanceof Error ? error.message : '메일 이력 조회에 실패했습니다.'); }
            } finally { if (!controller.signal.aborted) setLoading(false); }
        };
        void load();
        return () => controller.abort();
    }, [filters, page, reload, view, emailSelection]);
    const search = (event: FormEvent) => {
        event.preventDefault();
        if (draft.dateFrom && draft.dateTo && draft.dateFrom > draft.dateTo) { onNotify('error', '조회 종료일은 시작일보다 빠를 수 없습니다.'); return; }
        setFilters({ ...draft, keyword: draft.keyword.trim() }); setPage(1); setReload((value) => value + 1);
    };
    const reset = () => { setDraft(EMPTY); setFilters(EMPTY); setPage(1); setReload((value) => value + 1); };
    const changeView = (next: View) => {
        if (next === view) return;
        setView(next); setEmailSelection(null); setSelected(null); setPage(1); setData(null);
        // Mail status and individual recipient status have different meanings.
        setDraft((value) => ({ ...value, status: '' })); setFilters((value) => ({ ...value, status: '' }));
    };
    const openEmail = (item: MailEmailItem) => { setEmailSelection(item); setPage(1); setData(null); };
    const backToEmails = () => { setEmailSelection(null); setPage(1); setData(null); };
    const rows = failed || data?.kind !== 'mail' ? [] : data.result.items;
    const emails = failed || data?.kind !== 'email' ? [] : data.result.items;
    const emailHistories = failed || data?.kind !== 'email-history' ? [] : data.result.items;
    const result = data?.result;
    const stats: [string, number | undefined][] = view === 'mail'
        ? [['검색 결과', result?.summary.totalCount], ['저장됨 · 미발송', result?.summary.savedCount], ['수신자 기록', data?.kind === 'mail' ? data.result.summary.recipientCount : undefined]]
        : emailSelection
            ? [['메일 이력', result?.summary.totalCount], ['저장됨 · 미발송', result?.summary.savedCount], ['제외', data?.kind === 'email-history' ? data.result.summary.excludedCount : undefined]]
            : [['이메일 주소', result?.summary.totalCount], ['메일 이력', data?.kind === 'email' ? data.result.summary.historyCount : undefined], ['제외 이력', data?.kind === 'email' ? data.result.summary.excludedCount : undefined]];
    const emptyMessage = loading || !data && !failed ? '메일 이력을 불러오는 중입니다.' : failed ? '목록을 표시할 수 없습니다. 조회 버튼으로 다시 시도해 주세요.' : '검색 조건에 해당하는 메일 이력이 없습니다.';
    return (
        <section className="overflow-hidden rounded-xl border border-slate-200 bg-white text-slate-900 shadow-sm dark:border-slate-800 dark:bg-slate-950 dark:text-slate-50">
            <div className="border-b border-slate-200 p-4 dark:border-slate-800 md:p-5">
                <h1 className="flex items-center gap-2 text-sm font-semibold md:text-base"><Mail className="h-4 w-4 text-slate-400 dark:text-slate-400" />메일발송이력</h1>
                <p className="mt-1 text-xs text-slate-400 dark:text-slate-400">선택한 학회의 안내메일 저장 내역을 확인합니다. 현재 저장된 메일은 실제 발송되지 않습니다.</p>
                <div role="group" aria-label="이력 보기 기준" className="mt-4 flex flex-wrap gap-2">
                    {(['mail', 'email'] as const).map((value) => <button key={value} type="button" aria-pressed={view === value} onClick={() => changeView(value)} className={view === value ? 'inline-flex items-center justify-center gap-2 rounded-lg border border-blue-200 bg-blue-50 px-3 py-2 text-xs font-semibold text-blue-700 dark:border-blue-900 dark:bg-blue-950/50 dark:text-blue-300' : buttonClass}>{value === 'mail' ? <Mail className="h-4 w-4" /> : <AtSign className="h-4 w-4" />}{value === 'mail' ? '메일별 보기' : '이메일별 보기'}</button>)}
                </div>
                {view === 'email' && emailSelection && <div className="mt-4 flex flex-wrap items-center gap-3">
                    <button type="button" onClick={backToEmails} className={buttonClass}><ArrowLeft className="h-4 w-4" />이메일 목록</button>
                    <p className="min-w-0 break-all text-sm font-semibold">{emailSelection.email || '이메일 없음'} <span className="font-normal text-slate-500 dark:text-slate-400">메일 이력</span></p>
                </div>}
            </div>
            <dl className="grid grid-cols-1 divide-y divide-slate-200 border-b border-slate-200 dark:divide-slate-800 dark:border-slate-800 sm:grid-cols-3 sm:divide-x sm:divide-y-0">
                {stats.map(([label, count], index) => (
                    <div key={label} className="p-4 md:p-5"><dt className="text-xs font-medium text-slate-500 dark:text-slate-400">{label}</dt><dd className={`mt-1 text-xl font-bold ${index === 1 ? 'text-blue-600 dark:text-blue-400' : 'text-slate-900 dark:text-slate-50'}`}>{loading ? '집계 중...' : failed || count === undefined ? '-' : `${count?.toLocaleString()}건`}</dd></div>
                ))}
            </dl>
            <form onSubmit={search} className="border-b border-slate-200 bg-slate-50/70 p-4 dark:border-slate-800 dark:bg-slate-900/40 md:p-5">
                <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-4">
                    <label className="md:col-span-2"><span className={labelClass}>검색어</span><div className="relative"><Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400 dark:text-slate-400" /><input value={draft.keyword} maxLength={200} onChange={(e) => setDraft({ ...draft, keyword: e.target.value })} className={`${inputClass} pl-9`} placeholder="제목, 수신자 이름·이메일, 저장 관리자" /></div></label>
                    <label><span className={labelClass}>발송 메뉴</span><select value={draft.sourceMenu} onChange={(e) => setDraft({ ...draft, sourceMenu: e.target.value })} className={inputClass}><option value="">전체 메뉴</option>{Object.entries(MAIL_SOURCE_MENUS).map(([value, name]) => <option key={value} value={value}>{name}</option>)}</select></label>
                    <label><span className={labelClass}>{view === 'email' ? '수신자 상태' : '상태'}</span><select value={draft.status} onChange={(e) => setDraft({ ...draft, status: e.target.value })} className={inputClass}><option value="">전체 상태</option><option value="SAVED">저장됨 · 미발송</option>{view === 'email' && <option value="EXCLUDED">제외</option>}</select></label>
                    <label><span className={labelClass}>저장일 시작</span><input type="date" value={draft.dateFrom} onChange={(e) => setDraft({ ...draft, dateFrom: e.target.value })} className={inputClass} /></label>
                    <label><span className={labelClass}>저장일 종료</span><input type="date" value={draft.dateTo} onChange={(e) => setDraft({ ...draft, dateTo: e.target.value })} className={inputClass} /></label>
                </div>
                <div className="mt-3 flex flex-col justify-end gap-2 sm:flex-row">
                    <button type="button" onClick={reset} className={`${buttonClass} px-4`}><FilterX className="h-4 w-4" />초기화</button>
                    <button type="submit" disabled={loading} className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-5 py-2 text-xs font-semibold text-white hover:bg-blue-700 disabled:opacity-50 dark:bg-blue-600 dark:text-white dark:hover:bg-blue-700"><Search className="h-4 w-4" />조회</button>
                </div>
            </form>
            <div className="overflow-x-auto" aria-busy={loading}>
                {view === 'mail' ? <table className="w-full min-w-[1000px] text-left text-xs md:text-sm">
                    <thead className="border-b border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900/50"><tr>{['번호', '발송 메뉴', '제목', '수신자 / 제외', '상태', '저장 관리자', '저장일', '상세'].map((label) => <th key={label} className="p-4">{label}</th>)}</tr></thead>
                    <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                        {rows.map((item) => <tr key={item.seq} className="hover:bg-slate-50/60 dark:hover:bg-slate-900/40">
                            <td className="p-4">{item.seq}</td><td className="p-4">{MAIL_SOURCE_MENUS[item.sourceMenu] ?? item.sourceMenu}</td>
                            <td className="max-w-sm break-words p-4"><button type="button" className="text-left font-semibold text-blue-600 hover:underline dark:text-blue-400" onClick={() => setSelected({ seq: item.seq })}>{item.subject}</button></td>
                            <td className="p-4">{item.recipientCount} / {item.excludedCount}</td><td className="p-4 text-blue-600 dark:text-blue-400">{MAIL_HISTORY_STATUS[item.status] ?? item.status}</td><td className="p-4">{item.adminName}</td><td className="whitespace-nowrap p-4">{dateText(item.createdAt)}</td>
                            <td className="p-4"><button type="button" onClick={() => setSelected({ seq: item.seq })} aria-label={`메일 이력 ${item.seq} 상세`} className={buttonClass}><Eye className="h-4 w-4" /></button></td>
                        </tr>)}
                        {!rows.length && <tr><td colSpan={8} className="p-10 text-center text-slate-500 dark:text-slate-400">{emptyMessage}</td></tr>}
                    </tbody>
                </table> : !emailSelection ? <table className="w-full min-w-[850px] text-left text-xs md:text-sm">
                    <thead className="border-b border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900/50"><tr>{['이메일', '최근 수신자명', '메일 이력', '저장됨 · 미발송', '제외', '최근 저장일', '이력 보기'].map((label) => <th key={label} className="p-4">{label}</th>)}</tr></thead>
                    <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                        {emails.map((item) => <tr key={item.recipientSeq} className="hover:bg-slate-50/60 dark:hover:bg-slate-900/40">
                            <td className="max-w-xs break-all p-4"><button type="button" className="text-left font-semibold text-blue-600 hover:underline dark:text-blue-400" onClick={() => openEmail(item)}>{item.email || '이메일 없음'}</button></td>
                            <td className="max-w-48 break-words p-4">{item.fullName || '-'}</td><td className="p-4">{item.historyCount}</td><td className="p-4">{item.savedCount}</td><td className="p-4">{item.excludedCount}</td><td className="whitespace-nowrap p-4">{dateText(item.lastCreatedAt)}</td>
                            <td className="p-4"><button type="button" onClick={() => openEmail(item)} aria-label={`${item.email || '이메일 없음'} 메일 이력 보기`} className={buttonClass}><Eye className="h-4 w-4" /></button></td>
                        </tr>)}
                        {!emails.length && <tr><td colSpan={7} className="p-10 text-center text-slate-500 dark:text-slate-400">{emptyMessage}</td></tr>}
                    </tbody>
                </table> : <table className="w-full min-w-[1000px] text-left text-xs md:text-sm">
                    <thead className="border-b border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900/50"><tr>{['번호', '발송 메뉴', '제목', '저장 당시 수신자명', '수신자 상태 / 제외 사유', '저장 관리자', '저장일', '상세'].map((label) => <th key={label} className="p-4">{label}</th>)}</tr></thead>
                    <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                        {emailHistories.map((item) => <tr key={item.recipientSeq} className="hover:bg-slate-50/60 dark:hover:bg-slate-900/40">
                            <td className="p-4">{item.seq}</td><td className="p-4">{MAIL_SOURCE_MENUS[item.sourceMenu] ?? item.sourceMenu}</td>
                            <td className="max-w-sm break-words p-4"><button type="button" className="text-left font-semibold text-blue-600 hover:underline dark:text-blue-400" onClick={() => setSelected({ seq: item.seq, recipientSeq: item.recipientSeq })}>{item.subject}</button></td>
                            <td className="max-w-48 break-words p-4">{item.fullName || '-'}</td><td className="p-4">{MAIL_HISTORY_STATUS[item.status] ?? item.status}<p className="mt-1 text-xs text-amber-700 dark:text-amber-300">{exclusionText(item.exclusionReason)}</p></td>
                            <td className="p-4">{item.adminName}</td><td className="whitespace-nowrap p-4">{dateText(item.createdAt)}</td><td className="p-4"><button type="button" onClick={() => setSelected({ seq: item.seq, recipientSeq: item.recipientSeq })} aria-label={`메일 이력 ${item.seq} 상세`} className={buttonClass}><Eye className="h-4 w-4" /></button></td>
                        </tr>)}
                        {!emailHistories.length && <tr><td colSpan={8} className="p-10 text-center text-slate-500 dark:text-slate-400">{emptyMessage}</td></tr>}
                    </tbody>
                </table>}
            </div>
            <div className="flex items-center justify-between gap-3 border-t border-slate-200 p-4 text-xs dark:border-slate-800 md:p-5">
                <span>전체 {failed || loading || !result ? '-' : result.summary.totalCount.toLocaleString()}건</span>
                <div className="flex items-center gap-3"><button type="button" disabled={loading || failed || page <= 1} onClick={() => setPage((value) => value - 1)} aria-label="이전 페이지" className={buttonClass}><ChevronLeft className="h-4 w-4" /></button><span>{page} / {result?.totalPages ?? 1}</span><button type="button" disabled={loading || failed || page >= (result?.totalPages ?? 1)} onClick={() => setPage((value) => value + 1)} aria-label="다음 페이지" className={buttonClass}><ChevronRight className="h-4 w-4" /></button></div>
            </div>
            {selected !== null && <HistoryDetail key={selected.seq} seq={selected.seq} recipientSeq={selected.recipientSeq} onClose={() => setSelected(null)} onNotify={onNotify} />}
        </section>
    );
};

const HistoryDetail = ({ seq, recipientSeq, onClose, onNotify }: Props & { seq: number; recipientSeq?: number; onClose: () => void }) => {
    const [detail, setDetail] = useState<MailHistoryDetail | null>(null);
    const [failed, setFailed] = useState(false);
    const [downloading, setDownloading] = useState<number | null>(null);
    const [showAllRecipients, setShowAllRecipients] = useState(recipientSeq === undefined);
    const dialogRef = useRef<HTMLDivElement>(null);
    const notifyRef = useRef(onNotify);
    useEffect(() => { notifyRef.current = onNotify; }, [onNotify]);
    useEffect(() => {
        const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
        const previousOverflow = document.body.style.overflow;
        document.body.style.overflow = 'hidden'; dialogRef.current?.focus();
        const controller = new AbortController();
        const load = async () => {
            try {
                const response = await fetch(`/api/admin/mail-history/${seq}`, { signal: controller.signal });
                if (!response.ok) throw new Error(await response.text() || '메일 상세 이력을 불러오지 못했습니다.');
                const result = await response.json() as MailHistoryDetail;
                if (!controller.signal.aborted) setDetail(result);
            } catch (error) { if (!controller.signal.aborted) { setFailed(true); notifyRef.current('error', error instanceof Error ? error.message : '메일 상세 이력 조회에 실패했습니다.'); } }
        };
        void load();
        return () => { controller.abort(); document.body.style.overflow = previousOverflow; previousFocus?.focus(); };
    }, [seq]);
    const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
        if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); onClose(); }
        if (event.key !== 'Tab') return;
        const targets = Array.from(dialogRef.current?.querySelectorAll<HTMLElement>('button:not(:disabled), iframe, [tabindex="0"]') ?? []).filter((element) => element.getClientRects().length);
        const first = targets[0], last = targets[targets.length - 1];
        if (!first || !last) { event.preventDefault(); return; }
        if (event.shiftKey && (document.activeElement === first || document.activeElement === dialogRef.current)) { event.preventDefault(); last.focus(); }
        else if (!event.shiftKey && (document.activeElement === last || document.activeElement === dialogRef.current)) { event.preventDefault(); first.focus(); }
    };
    const download = async (file: MailHistoryDetail['attachments'][number]) => {
        if (downloading !== null) return;
        setDownloading(file.seq);
        try {
            const response = await fetch(`/api/admin/mail-history/${seq}/attachments/${file.seq}`);
            if (!response.ok) throw new Error(await response.text() || '첨부파일 다운로드에 실패했습니다.');
            const url = URL.createObjectURL(await response.blob());
            const anchor = document.createElement('a'); anchor.href = url; anchor.download = file.originalFilename;
            document.body.appendChild(anchor); anchor.click(); anchor.remove();
            window.setTimeout(() => URL.revokeObjectURL(url), 1000);
        } catch (error) { onNotify('error', error instanceof Error ? error.message : '첨부파일 다운로드에 실패했습니다.'); }
        finally { setDownloading(null); }
    };
    return createPortal(
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4 dark:bg-slate-950/60">
            <DraggableModal ref={dialogRef} tabIndex={-1} onKeyDown={onKeyDown} role="dialog" aria-modal="true" aria-labelledby="mail-history-title" className="flex max-h-[90vh] w-full max-w-5xl flex-col overflow-hidden rounded-2xl bg-white text-slate-900 shadow-2xl outline-none dark:bg-slate-950 dark:text-slate-50">
                <div data-modal-drag-handle className="flex shrink-0 cursor-move select-none touch-none items-start justify-between gap-4 border-b border-slate-200 px-5 py-4 dark:border-slate-800">
                    <div><h2 id="mail-history-title" className="flex items-center gap-2 text-base font-bold"><Mail className="h-5 w-5 text-blue-600 dark:text-blue-400" />메일발송이력 #{seq}</h2><p className="mt-1 text-xs text-slate-500 dark:text-slate-400">저장 당시 내용과 수신자별 상태를 확인합니다.</p></div>
                    <button type="button" onClick={onClose} aria-label="메일 이력 닫기" className="rounded-lg p-2 text-slate-400 hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-900"><X className="h-5 w-5" /></button>
                </div>
                <div className="min-h-0 space-y-5 overflow-y-auto p-5">
                    {!detail ? <p className="text-sm text-slate-500 dark:text-slate-400">{failed ? '이력을 표시할 수 없습니다. 닫은 뒤 다시 시도해 주세요.' : '이력을 불러오는 중입니다.'}</p> : <>
                        <div><h3 className="break-words font-semibold">{detail.history.subject}</h3><p className="mt-2 text-xs text-slate-500 dark:text-slate-400">{MAIL_SOURCE_MENUS[detail.history.sourceMenu]} · {detail.history.adminName} · {dateText(detail.history.createdAt)}</p><p className="mt-2 text-sm font-semibold text-blue-600 dark:text-blue-400">{MAIL_HISTORY_STATUS[detail.history.status] ?? detail.history.status}</p></div>
                        <p className="text-xs text-slate-500 dark:text-slate-400">선택 항목 {detail.history.selectedCount}건 · 중복 이메일 {detail.history.duplicateCount}건 · 이메일 오류 {detail.history.invalidCount}건 · 수신 거부 {detail.history.suppressionCount}건</p>
                        <iframe title="저장된 메일 본문" sandbox="" referrerPolicy="no-referrer" srcDoc={detail.history.htmlContent ?? ''} className="h-72 w-full rounded-lg border border-slate-200 bg-white dark:border-slate-800 dark:bg-white" />
                        {recipientSeq !== undefined && <div className="flex flex-wrap items-center justify-between gap-2"><h3 className="text-sm font-semibold">{showAllRecipients ? '전체 수신자' : '선택한 이메일의 수신자 기록'}</h3><button type="button" onClick={() => setShowAllRecipients((value) => !value)} className={buttonClass}>{showAllRecipients ? '선택 이메일만 보기' : '전체 수신자 보기'}</button></div>}
                        <div className="overflow-x-auto"><table className="w-full min-w-[720px] text-left text-xs"><thead className="bg-slate-50 dark:bg-slate-900"><tr>{['수신자', '이메일 / 소속', '원본 항목', '상태 / 제외 사유', '전송 결과'].map((name) => <th key={name} className="p-3">{name}</th>)}</tr></thead><tbody className="divide-y divide-slate-200 dark:divide-slate-800">{detail.recipients.filter((recipient) => showAllRecipients || recipient.seq === recipientSeq).map((recipient) => <tr key={recipient.seq}>
                            <td className="p-3">{recipient.fullName || '-'}</td><td className="max-w-60 break-all p-3">{recipient.email || '이메일 없음'}<p className="mt-1 text-slate-500 dark:text-slate-400">{recipient.affiliation}</p></td>
                            <td className="max-w-60 break-words p-3">{detail.origins.filter((origin) => origin.recipientSeq === recipient.seq).map((origin) => <p key={`${origin.sourceType}-${origin.sourceSeq}`}>{origin.sourceLabel || origin.sourceSeq}</p>)}</td>
                            <td className="p-3">{MAIL_HISTORY_STATUS[recipient.status] ?? recipient.status}<p className="mt-1 text-amber-700 dark:text-amber-300">{exclusionText(recipient.exclusionReason)}</p></td>
                            <td className="p-3">{recipient.deliveredAt ? `전달 ${dateText(recipient.deliveredAt)}` : recipient.acceptedAt ? `접수 ${dateText(recipient.acceptedAt)}` : '-'}{recipient.failureReason && <p className="mt-1 text-rose-600 dark:text-rose-400">{recipient.failureReason}</p>}</td>
                        </tr>)}</tbody></table></div>
                        <section><h3 className="text-sm font-semibold">첨부파일 {detail.attachments.length}개</h3><div className="mt-2 flex flex-wrap gap-2">{detail.attachments.map((file) => <button key={file.seq} type="button" disabled={downloading !== null} onClick={() => void download(file)} className={buttonClass}><Download className="h-4 w-4" />{file.originalFilename} ({Math.ceil(file.fileSize / 1024)} KB)</button>)}</div></section>
                    </>}
                </div>
                <div className="flex shrink-0 justify-end border-t border-slate-200 px-5 py-4 dark:border-slate-800"><button type="button" onClick={onClose} className={`${buttonClass} px-4 text-sm`}>닫기</button></div>
            </DraggableModal>
        </div>, document.body
    );
};
