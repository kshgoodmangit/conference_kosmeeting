import { DraggableModal } from './DraggableModal';
import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { ChevronLeft, ChevronRight, Eye, FilterX, MessageSquare, Pencil, Plus, Search, Send, Trash2, X } from 'lucide-react';
import type { NotificationType } from './NotificationToast';
import { useConfirm } from './confirmDialogContext';
import { RecipientGroupSelector } from './RecipientGroupSelector';
import { RECIPIENT_GROUPS, type RecipientGroup } from '../recipientGroups';
import { normalizeSmsPhone } from '../smsPhone';
import { RowActionMenu } from './RowActionMenu';

type Notify = (type: NotificationType, message: string) => void;
interface DirectRecipient { phoneNumber: string; fullName?: string }
interface Selection { recipientGroups: RecipientGroup[]; addressBookSeqs: number[]; directRecipients: DirectRecipient[] }
interface Campaign { seq: number; title: string; senderNumber: string; message: string; messageType: 'AUTO' | 'SMS' | 'LMS'; status: string; scheduledAt?: string | null; versionNo: number; sourceCount: number; includedCount?: number | null; excludedCount?: number | null }
interface Job { seq: number; status: string; includedCount: number; excludedCount: number; invalidCount: number; suppressionCount: number; duplicateCount: number }
interface Detail { campaign: Campaign; selection: Selection; job: Job | null }
interface Page { items: Campaign[]; page: number; totalPages: number; summary: { totalCount: number; draftCount: number; preparedCount: number } }
interface Book { seq: number; addressBookName: string; contactCount: number }
interface Recipient { seq: number; phoneNumber?: string; normalizedPhone?: string; fullName?: string; status: string; exclusionReason?: string }
interface RecipientPage { items: Recipient[]; page: number; totalPages: number; totalCount: number }
const api = '/api/admin/sms';
const input = 'w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm font-normal text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50';
const searchInput = 'w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-700 dark:bg-slate-950 dark:text-slate-50';
const secondary = 'inline-flex shrink-0 whitespace-nowrap items-center justify-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900';
const primary = 'inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-50 dark:bg-blue-600 dark:text-white dark:hover:bg-blue-700';
const addButton = 'inline-flex items-center justify-center gap-2 rounded-lg bg-emerald-600 px-3 py-2 text-xs font-semibold text-white hover:bg-emerald-700 disabled:opacity-50 dark:bg-emerald-600 dark:text-white dark:hover:bg-emerald-700';
const statusLabel = (status: string) => status === 'DRAFT' ? '작성 중' : status === 'PREPARED' ? '준비 완료 · 미발송' : status;
const errorText = (error: unknown) => error instanceof Error ? error.message : '요청을 처리하지 못했습니다.';
async function request<T>(path: string, init?: RequestInit): Promise<T> {
    const response = await fetch(api + path, init);
    if (!response.ok) {
        const message = await response.text();
        throw new Error(message && !message.startsWith('{') && !message.startsWith('<') ? message : '문자발송 정보를 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.');
    }
    return response.status === 204 ? undefined as T : await response.json() as T;
}
const json = (method: string, body: unknown): RequestInit => ({ method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });

export const SmsPage = ({ onNotify }: { onNotify: Notify }) => {
    const confirm = useConfirm();
    const [filters, setFilters] = useState({ keyword: '', status: '' });
    const [applied, setApplied] = useState(filters);
    const [page, setPage] = useState(1);
    const [reload, setReload] = useState(0);
    const [data, setData] = useState<Page | null>(null);
    const [loading, setLoading] = useState(true);
    const [failed, setFailed] = useState(false);
    const [editor, setEditor] = useState<{ detail: Detail | null } | null>(null);
    const [busy, setBusy] = useState(false);
    const busyRef = useRef(false);
    const notifyRef = useRef(onNotify);
    useEffect(() => { notifyRef.current = onNotify; }, [onNotify]);
    useEffect(() => {
        const controller = new AbortController();
        const load = async () => {
            setLoading(true); setFailed(false);
            try {
                const result = await request<Page>(`/campaigns?${new URLSearchParams({ ...applied, page: String(page) })}`, { signal: controller.signal });
                if (controller.signal.aborted) return;
                setData(result); setPage(result.page);
            } catch (error) {
                if (controller.signal.aborted) return;
                setFailed(true); notifyRef.current('error', errorText(error));
            } finally { if (!controller.signal.aborted) setLoading(false); }
        };
        void load(); return () => controller.abort();
    }, [applied, page, reload]);
    const run = async (work: () => Promise<void>) => {
        if (busyRef.current) return;
        busyRef.current = true; setBusy(true);
        try { await work(); } catch (error) { notifyRef.current('error', errorText(error)); }
        finally { busyRef.current = false; setBusy(false); }
    };
    const open = (seq: number) => run(async () => setEditor({ detail: await request<Detail>(`/campaigns/${seq}`) }));
    const prepare = (campaign: Campaign) => run(async () => {
        if (!await confirm({ title: '문자 발송 준비', message: `“${campaign.title}”의 최신 수신자를 조회하고 명단을 확정하시겠습니까?\n준비 후에는 내용을 수정할 수 없으며 실제 문자는 발송되지 않습니다.`, confirmText: '준비' })) return;
        const result = await request<Job>(`/campaigns/${campaign.seq}/prepare`, json('POST', { idempotencyKey: crypto.randomUUID() }));
        notifyRef.current('success', `발송 준비 완료: 대상 ${result.includedCount}명, 제외 ${result.excludedCount}건, 중복 ${result.duplicateCount}건. 실제 발송되지 않았습니다.`);
        setReload((value) => value + 1);
    });
    const remove = (campaign: Campaign) => run(async () => {
        if (!await confirm({ title: '문자 삭제', message: `“${campaign.title}” 작성 내역을 삭제하시겠습니까?`, tone: 'danger', confirmText: '삭제' })) return;
        await request<void>(`/campaigns/${campaign.seq}`, { method: 'DELETE' });
        notifyRef.current('success', '문자 작성 내역을 삭제했습니다.'); setReload((value) => value + 1);
    });
    const statistic = (value?: number) => loading ? '집계 중...' : failed ? '-' : (value ?? 0).toLocaleString();
    return <>
        <section className="overflow-hidden rounded-xl border border-slate-200 bg-white text-slate-900 shadow-sm dark:border-slate-800 dark:bg-slate-950 dark:text-slate-50">
            <div className="flex flex-col justify-between gap-4 border-b border-slate-200 p-4 dark:border-slate-800 sm:flex-row sm:items-center md:p-5">
                <div>
                    <div className="flex items-center gap-2">
                        <MessageSquare className="shrink-0 h-4 w-4" />
                        <h1 className="text-sm font-semibold md:text-base">문자발송</h1>
                    </div>
                    <p className="mt-1 text-xs text-slate-400 dark:text-slate-400">문자 작성과 수신자 확정을 지원합니다. 실제 발송은 아직 제공하지 않습니다.</p>
                </div>
                <div className="flex gap-2"><button type="button" disabled={busy} className={addButton} onClick={() => setEditor({ detail: null })}><Plus className="h-4 w-4" />문자 작성</button></div>
            </div>
            <div className="grid divide-y divide-slate-200 dark:divide-slate-800 sm:grid-cols-3 sm:divide-x sm:divide-y-0">
                {([['검색 결과', data?.summary.totalCount], ['작성 중', data?.summary.draftCount], ['준비 완료', data?.summary.preparedCount]] as const).map(([label, value]) => <div key={label} className="p-4 md:p-5"><p className="text-xs font-medium text-slate-500 dark:text-slate-400">{label}</p><p className="mt-1 text-xl font-bold">{statistic(value)}</p></div>)}
            </div>
            <form className="border-b border-slate-200 bg-slate-50/70 p-4 dark:border-slate-800 dark:bg-slate-900/40 md:p-5" onSubmit={(event) => { event.preventDefault(); setApplied({ ...filters }); setPage(1); }}>
                <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-4">
                    <label className="md:col-span-2"><span className="mb-1 block text-[11px] font-semibold text-slate-500 dark:text-slate-400">검색어</span><div className="relative"><Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-400 dark:text-slate-400" /><input className={`${searchInput} pl-9`} value={filters.keyword} maxLength={100} placeholder="제목, 내용, 발신번호" onChange={(event) => setFilters({ ...filters, keyword: event.target.value })} /></div></label>
                    <label><span className="mb-1 block text-[11px] font-semibold text-slate-500 dark:text-slate-400">상태</span><select className={searchInput} value={filters.status} onChange={(event) => { const next = { ...filters, status: event.target.value }; setFilters(next); setApplied({ ...next, keyword: applied.keyword }); setPage(1); }}><option value="">전체</option><option value="DRAFT">작성 중</option><option value="PREPARED">준비 완료</option></select></label>
                </div>
                <div className="mt-3 flex flex-col justify-end gap-2 sm:flex-row"><button type="button" className={`${secondary} px-4`} onClick={() => { const empty = { keyword: '', status: '' }; setFilters(empty); setApplied(empty); setPage(1); }}><FilterX className="h-4 w-4" />초기화</button><button type="submit" className={`${primary} px-5 text-xs`}><Search className="h-4 w-4" />조회</button></div>
            </form>
            <div className="overflow-x-auto"><table className="min-w-full text-left text-xs md:text-sm"><thead className="bg-slate-50 dark:bg-slate-900"><tr>{['문자 제목', '유형 / 상태', '수신 대상', '발송 희망 일시', '기능'].map((label) => <th key={label} className="whitespace-nowrap p-4">{label}</th>)}</tr></thead><tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                {failed || !data?.items.length ? <tr><td colSpan={5} className="p-8 text-center text-slate-500 dark:text-slate-400">{loading ? '불러오는 중...' : failed ? '조회하지 못했습니다. 조회 버튼으로 다시 시도해 주세요.' : '검색 결과가 없습니다.'}</td></tr> : data.items.map((campaign) => <tr key={campaign.seq} className="hover:bg-slate-50 dark:hover:bg-slate-900/60"><td className="min-w-44 p-4"><p className="font-semibold">{campaign.title}</p><p className="mt-1 text-xs text-slate-500 dark:text-slate-400">{campaign.senderNumber}</p></td><td className="whitespace-nowrap p-4">{campaign.messageType === 'AUTO' ? '자동' : campaign.messageType}<p className="mt-1 text-xs text-slate-500 dark:text-slate-400">{statusLabel(campaign.status)}</p></td><td className="whitespace-nowrap p-4">{campaign.status === 'DRAFT' ? `대상 조건 ${campaign.sourceCount}개` : `${campaign.includedCount ?? 0}명 · 제외 ${campaign.excludedCount ?? 0}건`}</td><td className="whitespace-nowrap p-4">{campaign.scheduledAt?.replace('T', ' ').slice(0, 16) || '-'}</td><td className="p-4"><RowActionMenu itemLabel={campaign.title} actions={campaign.status === 'DRAFT' ? [
                    { label: '수정', icon: <Pencil className="h-4 w-4" />, onClick: () => void open(campaign.seq), disabled: busy },
                    { label: '발송 준비', icon: <Send className="h-4 w-4" />, onClick: () => void prepare(campaign), disabled: busy },
                    { label: '삭제', icon: <Trash2 className="h-4 w-4" />, onClick: () => void remove(campaign), disabled: busy, tone: 'danger' }
                ] : [{ label: '상세', icon: <Eye className="h-4 w-4" />, onClick: () => void open(campaign.seq), disabled: busy }]} /></td></tr>)}
            </tbody></table></div>
            <Pager page={data?.page ?? page} pages={data?.totalPages ?? 1} count={data?.summary.totalCount ?? 0} disabled={loading || failed} onPage={setPage} />
        </section>
        {editor && <SmsEditor detail={editor.detail} onClose={() => setEditor(null)} onNotify={onNotify} onSaved={() => { setEditor(null); setReload((value) => value + 1); }} />}
    </>;
};

function Field({ label, children }: { label: string; children: ReactNode }) { return <label className="block space-y-1.5 text-sm font-semibold text-slate-700 dark:text-slate-200"><span>{label}</span>{children}</label>; }
function Pager({ page, pages, count, disabled, onPage }: { page: number; pages: number; count: number; disabled?: boolean; onPage: (page: number) => void }) {
    return <div className="flex items-center justify-between gap-3 border-t border-slate-200 p-4 text-xs dark:border-slate-800"><span>총 {count.toLocaleString()}건</span><div className="flex items-center gap-3"><button type="button" disabled={disabled || page <= 1} className={secondary} aria-label="이전 페이지" onClick={() => onPage(page - 1)}><ChevronLeft className="h-4 w-4" /></button><span>{page} / {pages}</span><button type="button" disabled={disabled || page >= pages} className={secondary} aria-label="다음 페이지" onClick={() => onPage(page + 1)}><ChevronRight className="h-4 w-4" /></button></div></div>;
}

function SmsEditor({ detail, onClose, onNotify, onSaved }: { detail: Detail | null; onClose: () => void; onNotify: Notify; onSaved: () => void }) {
    const readOnly = Boolean(detail && detail.campaign.status !== 'DRAFT');
    const [form, setForm] = useState(() => ({ title: detail?.campaign.title ?? '', senderNumber: detail?.campaign.senderNumber ?? '', message: detail?.campaign.message ?? '', messageType: detail?.campaign.messageType ?? 'AUTO', scheduledAt: detail?.campaign.scheduledAt?.slice(0,16) ?? '' }));
    const [selection, setSelection] = useState<Selection>(() => detail?.selection ?? { recipientGroups: [], addressBookSeqs: [], directRecipients: [] });
    const [counts, setCounts] = useState<Record<RecipientGroup, number>>();
    const [countsFailed, setCountsFailed] = useState(false);
    const [books, setBooks] = useState<Book[]>([]);
    const [booksFailed, setBooksFailed] = useState(false);
    const [bookToAdd, setBookToAdd] = useState('');
    const [direct, setDirect] = useState({ fullName: '', phoneNumber: '' });
    const [query, setQuery] = useState('');
    const [members, setMembers] = useState<DirectRecipient[]>([]);
    const [searched, setSearched] = useState(false);
    const [searching, setSearching] = useState(false);
    const [saving, setSaving] = useState(false);
    const [recipientPage, setRecipientPage] = useState(1);
    const [recipients, setRecipients] = useState<RecipientPage | null>(null);
    const [recipientsFailed, setRecipientsFailed] = useState(false);
    const [recipientsLoading, setRecipientsLoading] = useState(true);
    const dialogRef = useRef<HTMLDivElement>(null);
    const savingRef = useRef(false);
    const searchRef = useRef<AbortController | null>(null);
    const notifyRef = useRef(onNotify);
    const closeRef = useRef(onClose);
    useEffect(() => { notifyRef.current = onNotify; closeRef.current = onClose; }, [onNotify, onClose]);
    useEffect(() => {
        const trigger = document.activeElement instanceof HTMLElement ? document.activeElement : null;
        const overflow = document.body.style.overflow;
        document.body.style.overflow = 'hidden'; dialogRef.current?.focus();
        return () => { document.body.style.overflow = overflow; trigger?.focus(); searchRef.current?.abort(); };
    }, []);
    useEffect(() => {
        if (readOnly) return;
        const controller = new AbortController();
        const timeout = window.setTimeout(() => {
            void request<{ groupCounts: Record<RecipientGroup, number> }>('/recipient-groups', { signal: controller.signal })
                .then((result) => { if (!controller.signal.aborted) setCounts(result.groupCounts); })
                .catch((error: unknown) => { if (!controller.signal.aborted) { setCountsFailed(true); notifyRef.current('error', errorText(error)); } });
            void request<Book[]>('/address-books', { signal: controller.signal })
                .then((result) => { if (!controller.signal.aborted) setBooks(result); })
                .catch((error: unknown) => { if (!controller.signal.aborted) { setBooksFailed(true); notifyRef.current('error', errorText(error)); } });
        }, 0);
        return () => { window.clearTimeout(timeout); controller.abort(); };
    }, [readOnly]);
    const seq = detail?.campaign.seq;
    useEffect(() => {
        if (!readOnly || !seq) return;
        const controller = new AbortController();
        const load = async () => {
            setRecipientsLoading(true); setRecipientsFailed(false);
            try {
                const result = await request<RecipientPage>(`/campaigns/${seq}/recipients?page=${recipientPage}`, { signal: controller.signal });
                if (!controller.signal.aborted) { setRecipients(result); setRecipientPage(result.page); }
            } catch (error) { if (!controller.signal.aborted) { setRecipientsFailed(true); notifyRef.current('error', errorText(error)); } }
            finally { if (!controller.signal.aborted) setRecipientsLoading(false); }
        };
        void load(); return () => controller.abort();
    }, [readOnly, seq, recipientPage]);
    const addDirect = (recipient: DirectRecipient) => {
        const phoneNumber = normalizeSmsPhone(recipient.phoneNumber ?? '');
        if (!phoneNumber) { onNotify('error', '휴대폰 번호를 확인해 주세요. 해외 번호는 +국가번호를 포함해 입력해 주세요.'); return; }
        if (selection.directRecipients.some((item) => normalizeSmsPhone(item.phoneNumber) === phoneNumber)) { onNotify('info', '이미 추가된 번호입니다.'); return; }
        setSelection((current) => ({ ...current, directRecipients: [...current.directRecipients, { phoneNumber, fullName: recipient.fullName?.trim() }] }));
        setDirect({ fullName: '', phoneNumber: '' });
    };
    const searchMembers = async () => {
        if (query.trim().length < 2) return;
        searchRef.current?.abort();
        const controller = new AbortController(); searchRef.current = controller;
        setSearching(true); setSearched(false); setMembers([]);
        try { const result = await request<DirectRecipient[]>(`/member-search?keyword=${encodeURIComponent(query.trim())}`, { signal: controller.signal }); if (!controller.signal.aborted) { setMembers(result); setSearched(true); } }
        catch (error) { if (!controller.signal.aborted) onNotify('error', errorText(error)); }
        finally { if (!controller.signal.aborted) setSearching(false); }
    };
    const save = async (event: FormEvent) => {
        event.preventDefault();
        if (savingRef.current || readOnly) return;
        if (!form.title.trim() || !form.message.trim() || !form.senderNumber.trim()) { onNotify('error', '문자 제목, 발신번호, 내용을 입력해 주세요.'); return; }
        savingRef.current = true; setSaving(true);
        try {
            await request<Detail>(seq ? `/campaigns/${seq}` : '/campaigns', json(seq ? 'PUT' : 'POST', { ...form, ...selection, scheduledAt: form.scheduledAt || null, versionNo: detail?.campaign.versionNo }));
            onNotify('success', '문자를 저장했습니다.'); onSaved();
        } catch (error) { onNotify('error', errorText(error)); }
        finally { savingRef.current = false; setSaving(false); }
    };
    const selectedCount = (selection.recipientGroups.length > 0 && !counts) || selection.addressBookSeqs.some((id) => !books.some((book) => book.seq === id)) ? null : selection.recipientGroups.reduce((sum, group) => sum + (counts?.[group] ?? 0), 0) + selection.addressBookSeqs.reduce((sum, id) => sum + (books.find((book) => book.seq === id)?.contactCount ?? 0), 0) + selection.directRecipients.length;
    return <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4 dark:bg-slate-950/60">
        <DraggableModal ref={dialogRef} role="dialog" aria-modal="true" aria-labelledby="sms-editor-title" tabIndex={-1} className="flex max-h-[90vh] w-full max-w-2xl flex-col overflow-hidden rounded-2xl bg-white text-slate-900 shadow-2xl outline-none dark:bg-slate-950 dark:text-slate-50" onKeyDown={(event) => {
            if (event.key === 'Escape') { event.stopPropagation(); if (!savingRef.current) closeRef.current(); }
            if (event.key !== 'Tab') return;
            const nodes = Array.from(dialogRef.current?.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled)') ?? []).filter((node) => node.getClientRects().length && !node.closest('fieldset:disabled'));
            const first = nodes[0], last = nodes[nodes.length - 1];
            if (!first) { event.preventDefault(); return; }
            if (event.shiftKey && (document.activeElement === first || document.activeElement === dialogRef.current)) { event.preventDefault(); last.focus(); }
            else if (!event.shiftKey && (document.activeElement === last || document.activeElement === dialogRef.current)) { event.preventDefault(); first.focus(); }
        }}>
            <header data-modal-drag-handle className="cursor-move select-none touch-none flex shrink-0 items-center justify-between border-b border-slate-200 px-5 py-4 dark:border-slate-800"><div><h2 id="sms-editor-title" className="text-base font-bold">{readOnly ? '문자 발송 준비 상세' : seq ? '문자 수정' : '문자 작성'}</h2><p className="mt-1 text-xs text-slate-400 dark:text-slate-400">{readOnly ? '확정된 수신자 명단입니다. 실제 발송되지 않았습니다.' : '작성한 문자를 저장한 뒤 목록에서 발송 준비를 진행하세요.'}</p></div><button type="button" disabled={saving} onClick={onClose} aria-label="닫기" className="rounded-lg p-2 text-slate-400 hover:bg-slate-100 disabled:opacity-50 dark:text-slate-400 dark:hover:bg-slate-900"><X className="h-5 w-5" /></button></header>
            <form onSubmit={(event) => void save(event)} noValidate className="flex min-h-0 flex-1 flex-col">
                <div className="overflow-y-auto p-5"><fieldset disabled={saving || readOnly} className="space-y-5">
                    <Field label="문자 제목 *"><input className={input} value={form.title} maxLength={100} onChange={(event) => setForm({ ...form, title: event.target.value })} /></Field>
                    <div className="grid gap-4 sm:grid-cols-2"><Field label="발신번호 *"><input className={input} type="tel" value={form.senderNumber} maxLength={30} placeholder="예: 0212345678" onChange={(event) => setForm({ ...form, senderNumber: event.target.value })} /></Field><Field label="문자 유형"><select className={input} value={form.messageType} onChange={(event) => setForm({ ...form, messageType: event.target.value as Campaign['messageType'] })}><option value="AUTO">자동</option><option value="SMS">SMS</option><option value="LMS">LMS</option></select></Field></div>
                    <Field label="문자 내용 *"><textarea className={`${input} min-h-40`} value={form.message} maxLength={2000} onChange={(event) => setForm({ ...form, message: event.target.value })} /><span className="block text-right text-xs font-normal text-slate-500 dark:text-slate-400">{form.message.length.toLocaleString()} / 2,000자</span></Field>
                    <p className="text-xs text-slate-500 dark:text-slate-400">발신번호 등록 여부와 SMS/LMS 길이 제한은 실제 발송 서비스 연결 후 검증됩니다. 현재 글자 수는 요금이나 발송 유형을 확정하지 않습니다.</p>
                    <Field label="발송 희망 일시"><input className={input} type="datetime-local" value={form.scheduledAt} onChange={(event) => setForm({ ...form, scheduledAt: event.target.value })} /><span className="block text-xs font-normal text-slate-500 dark:text-slate-400">일시만 저장하며 현재 자동 예약 발송은 수행하지 않습니다.</span></Field>
                    {!readOnly && <>
                        <RecipientGroupSelector selected={selection.recipientGroups} counts={counts} loading={!counts && !countsFailed} failed={countsFailed} onToggle={(group) => setSelection((current) => ({ ...current, recipientGroups: current.recipientGroups.includes(group) ? current.recipientGroups.filter((value) => value !== group) : [...current.recipientGroups, group] }))} />
                        <div className="space-y-3"><Field label="주소록 추가"><div className="flex gap-2"><select className={input} disabled={booksFailed} value={bookToAdd} onChange={(event) => setBookToAdd(event.target.value)}><option value="">{booksFailed ? '주소록 조회 실패' : '주소록 선택'}</option>{books.map((book) => <option key={book.seq} value={book.seq} disabled={selection.addressBookSeqs.includes(book.seq)}>{book.addressBookName} · 번호 {book.contactCount}개</option>)}</select><button type="button" className={secondary} disabled={!bookToAdd} onClick={() => { const id = Number(bookToAdd); setSelection((current) => ({ ...current, addressBookSeqs: [...new Set([...current.addressBookSeqs, id])] })); setBookToAdd(''); }}>추가</button></div></Field><p className="text-xs text-slate-500 dark:text-slate-400">메일 주소록의 휴대폰 번호를 사용합니다. 번호가 없거나 유효하지 않으면 발송 준비 시 제외됩니다.</p></div>
                        <div className="space-y-3"><p className="text-sm font-semibold">회원 검색</p><div className="flex gap-2"><input className={input} aria-label="문자 수신 회원 검색" placeholder="이름, 이메일, 휴대폰 번호 2자 이상" maxLength={100} value={query} onChange={(event) => { searchRef.current?.abort(); setSearching(false); setQuery(event.target.value); setMembers([]); setSearched(false); }} onKeyDown={(event) => { if (event.key === 'Enter') { event.preventDefault(); void searchMembers(); } }} /><button type="button" className={secondary} disabled={searching || query.trim().length < 2} onClick={() => void searchMembers()}>검색</button></div>{searched && <p className="text-xs text-slate-500 dark:text-slate-400">{members.length ? `${members.length}명 조회 (최대 50명)` : '검색 결과가 없습니다.'}</p>}<div className="max-h-40 overflow-y-auto">{members.map((member, index) => <div key={`${member.phoneNumber}-${index}`} className="flex items-center justify-between gap-2 border-b border-slate-100 py-2 text-xs dark:border-slate-800"><div>{member.fullName || '이름 없음'}<p className="text-slate-500 dark:text-slate-400">{member.phoneNumber || '번호 없음'}</p></div><button type="button" className={secondary} disabled={!normalizeSmsPhone(member.phoneNumber ?? '')} onClick={() => addDirect(member)}>추가</button></div>)}</div></div>
                        <div className="space-y-3"><p className="text-sm font-semibold">개별 수신자 추가</p><div className="grid gap-2 sm:grid-cols-[1fr_1.4fr_auto]"><input className={input} aria-label="개별 수신자명" placeholder="이름" value={direct.fullName} maxLength={255} onChange={(event) => setDirect({ ...direct, fullName: event.target.value })} /><input className={input} type="tel" aria-label="개별 수신자 휴대폰 번호" placeholder="01012345678 / +국가번호" value={direct.phoneNumber} maxLength={50} onChange={(event) => setDirect({ ...direct, phoneNumber: event.target.value })} onKeyDown={(event) => { if (event.key === 'Enter') { event.preventDefault(); addDirect(direct); } }} /><button type="button" className={secondary} onClick={() => addDirect(direct)}>추가</button></div></div>
                        <div className="rounded-xl border border-slate-200 p-3 dark:border-slate-800"><p className="mb-2 text-sm font-semibold">선택 합계 {selectedCount === null ? '-' : `${selectedCount.toLocaleString()}명`}</p><div className="max-h-48 space-y-2 overflow-y-auto">
                            {selection.recipientGroups.map((group) => <SelectedRow key={group} label={RECIPIENT_GROUPS.find((item) => item.value === group)?.label ?? group} onRemove={() => setSelection((current) => ({ ...current, recipientGroups: current.recipientGroups.filter((value) => value !== group) }))} />)}
                            {selection.addressBookSeqs.map((id) => <SelectedRow key={id} label={books.find((book) => book.seq === id)?.addressBookName ?? `주소록 #${id}`} onRemove={() => setSelection((current) => ({ ...current, addressBookSeqs: current.addressBookSeqs.filter((value) => value !== id) }))} />)}
                            {selection.directRecipients.map((recipient) => <SelectedRow key={recipient.phoneNumber} label={`${recipient.fullName || '개별 수신자'} · ${recipient.phoneNumber}`} onRemove={() => setSelection((current) => ({ ...current, directRecipients: current.directRecipients.filter((value) => value.phoneNumber !== recipient.phoneNumber) }))} />)}
                        </div><p className="mt-2 text-xs text-slate-500 dark:text-slate-400">합계에는 대상 간 중복이 포함될 수 있습니다. 최종 명단은 발송 준비 시 최신 번호와 문자 수신 거부를 확인하여 확정합니다.</p></div>
                    </>}
                </fieldset>
                {readOnly && detail?.job && <div className="mt-5 space-y-3"><p className="text-sm font-semibold">확정 수신자 {detail.job.includedCount}명 · 제외 {detail.job.excludedCount}건</p><p className="text-xs text-slate-500 dark:text-slate-400">중복 {detail.job.duplicateCount}건 · 번호 오류 {detail.job.invalidCount}건 · 수신 거부 {detail.job.suppressionCount}명</p><div className="overflow-x-auto"><table className="min-w-full text-left text-xs"><thead className="bg-slate-50 dark:bg-slate-900"><tr><th className="p-3">이름</th><th className="p-3">번호</th><th className="p-3">상태</th></tr></thead><tbody>{recipientsFailed || !recipients?.items.length ? <tr><td colSpan={3} className="p-4">{recipientsLoading ? '불러오는 중...' : recipientsFailed ? '조회하지 못했습니다. 모달을 다시 열어 주세요.' : '수신자가 없습니다.'}</td></tr> : recipients.items.map((recipient) => <tr key={recipient.seq}><td className="p-3">{recipient.fullName || '-'}</td><td className="p-3">{recipient.normalizedPhone || recipient.phoneNumber || '-'}</td><td className="p-3">{recipient.status === 'READY' ? '준비 · 미발송' : recipient.exclusionReason === 'INVALID_PHONE' ? '번호 오류' : recipient.exclusionReason === 'SUPPRESSED' ? '수신 거부' : recipient.status}</td></tr>)}</tbody></table></div><Pager page={recipients?.page ?? recipientPage} pages={recipients?.totalPages ?? 1} count={recipients?.totalCount ?? 0} disabled={recipientsLoading || recipientsFailed} onPage={setRecipientPage} /></div>}
                </div>
                <footer className="flex shrink-0 justify-end gap-2 border-t border-slate-200 px-5 py-4 dark:border-slate-800"><button type="button" disabled={saving} className={`${secondary} px-4 text-sm`} onClick={onClose}>{readOnly ? '닫기' : '취소'}</button>{!readOnly && <button type="submit" disabled={saving} className={primary}>{saving ? '저장 중...' : '저장'}</button>}</footer>
            </form>
        </DraggableModal>
    </div>;
}
function SelectedRow({ label, onRemove }: { label: string; onRemove: () => void }) { return <div className="flex items-center justify-between gap-2 text-xs"><span className="min-w-0 break-all">{label}</span><button type="button" onClick={onRemove} aria-label={`${label} 제거`} className="rounded-lg p-2 text-slate-500 hover:bg-rose-50 hover:text-rose-600 dark:text-slate-400 dark:hover:bg-rose-950 dark:hover:text-rose-400"><X className="h-4 w-4" /></button></div>; }
