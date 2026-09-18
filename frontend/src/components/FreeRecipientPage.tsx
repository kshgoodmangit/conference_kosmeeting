import { DraggableModal } from './DraggableModal';
import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { Download, FileSpreadsheet, FilterX, LoaderCircle, Pencil, Plus, Search, Trash2, Upload, Users, X } from 'lucide-react';
import type { NotificationType } from './NotificationToast';
import { useConfirm } from './confirmDialogContext';
import { rowActionButtonClass } from './RowActionMenu';

interface Member { seq?: number; phoneNumber: string; fullName: string; affiliation: string; position: string; email: string; recipientType: string; isUsed: string; adminMemo: string }
interface Page { items: Member[]; summary: { totalCount: number; activeCount: number; inactiveCount: number }; page: number; totalPages: number }
interface ImportResult { totalCount: number; successCount: number; skippedCount: number; failureCount: number; results: { rowNumber: number; fullName: string; status: string; message: string }[] }
type Notify = (type: NotificationType, message: string) => void;
const TYPES = ['초청', '임원', '연자', '기타'];
const API = '/api/admin/free-recipients';
const TEMPLATE = '/templates/free_recipient_bulk_import_template.xlsx';
const input = 'w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm font-normal text-slate-900 placeholder:text-slate-400 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50';
const searchInput = 'w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-700 dark:bg-slate-950 dark:text-slate-50';
const secondary = 'inline-flex items-center justify-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900';
const primary = 'inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-50 dark:bg-blue-600 dark:text-white dark:hover:bg-blue-700';
const label = 'space-y-1.5 text-sm font-semibold text-slate-700 dark:text-slate-200';
const emptyMember: Member = { phoneNumber: '', fullName: '', affiliation: '', position: '', email: '', recipientType: '기타', isUsed: 'Y', adminMemo: '' };

async function request<T>(url: string, init?: RequestInit): Promise<T> {
    const response = await fetch(url, init);
    if (!response.ok) throw new Error(await response.text() || '요청을 처리하지 못했습니다.');
    return (response.status === 204 ? undefined : await response.json()) as T;
}
const json = (method: string, value: unknown): RequestInit => ({ method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(value) });
const errorText = (error: unknown) => error instanceof Error ? error.message : '요청을 처리하지 못했습니다.';

export function FreeRecipientPage({ onNotify }: { onNotify: Notify }) {
    const confirm = useConfirm();
    const [draft, setDraft] = useState({ keyword: '', recipientType: '', isUsed: '' });
    const [filters, setFilters] = useState(draft);
    const [page, setPage] = useState(1);
    const [reload, setReload] = useState(0);
    const [data, setData] = useState<Page | null>(null);
    const [loading, setLoading] = useState(true);
    const [failed, setFailed] = useState(false);
    const [editor, setEditor] = useState<Member | null>(null);
    const [modal, setModal] = useState<'import' | null>(null);
    const [deleting, setDeleting] = useState(false);
    const deleteLock = useRef(false);
    const notifyRef = useRef(onNotify);
    useEffect(() => { notifyRef.current = onNotify; }, [onNotify]);
    useEffect(() => {
        const controller = new AbortController();
        request<Page>(`${API}?${new URLSearchParams({ ...filters, page: String(page), size: '20' })}`, { signal: controller.signal })
            .then((result) => { if (!controller.signal.aborted) { setData(result); setPage(result.page); } })
            .catch((error: unknown) => { if (!controller.signal.aborted) { setFailed(true); notifyRef.current('error', errorText(error)); } })
            .finally(() => { if (!controller.signal.aborted) setLoading(false); });
        return () => controller.abort();
    }, [filters, page, reload]);
    const refresh = () => { setLoading(true); setFailed(false); setReload((value) => value + 1); };
    const remove = async (member: Member) => {
        if (deleteLock.current) return;
        deleteLock.current = true; setDeleting(true);
        try {
            if (!await confirm({ title: '무료 대상자 삭제', message: `${member.fullName} (${member.phoneNumber}) 대상자를 명단에서 삭제하시겠습니까?`, confirmText: '삭제', tone: 'danger' })) return;
            await request(`${API}/${member.seq}`, { method: 'DELETE' });
            onNotify('success', '무료 대상자를 삭제했습니다.'); refresh();
        } catch (error) { onNotify('error', errorText(error)); }
        finally { deleteLock.current = false; setDeleting(false); }
    };
    const search = (event: FormEvent) => { event.preventDefault(); setFilters({ ...draft, keyword: draft.keyword.trim() }); setPage(1); refresh(); };
    const applySelectFilter = (key: 'recipientType' | 'isUsed', value: string) => {
        const next = { ...draft, [key]: value };
        setDraft(next); setFilters({ ...next, keyword: filters.keyword }); setPage(1); refresh();
    };
    return <section className="overflow-hidden rounded-xl border border-slate-200 bg-white text-slate-900 shadow-sm dark:border-slate-800 dark:bg-slate-950 dark:text-slate-50">
        <div className="flex flex-col justify-between gap-4 border-b border-slate-200 p-4 dark:border-slate-800 xl:flex-row xl:items-center md:p-5">
            <div>
                <div className="flex items-center gap-2">
                    <Users className="shrink-0 h-4 w-4" />
                    <h1 className="text-sm font-semibold md:text-base">무료 대상자 관리</h1>
                </div>
                <p className="mt-1 text-xs text-slate-400 dark:text-slate-400">학회가 지정한 무료 대상자의 명단과 사용 여부를 관리합니다.</p>
            </div>
            <div className="flex flex-wrap gap-2">
                <a href={TEMPLATE} download className={secondary}><Download className="h-4 w-4" />양식 다운로드</a>
                <button type="button" className="inline-flex items-center justify-center gap-2 rounded-lg border border-violet-200 bg-violet-50 px-3 py-2 text-xs font-semibold text-violet-700 hover:bg-violet-100 disabled:opacity-50 dark:border-violet-900/60 dark:bg-violet-950/30 dark:text-violet-300 dark:hover:bg-violet-950/50" onClick={() => setModal('import')}><FileSpreadsheet className="h-4 w-4" />엑셀 일괄등록</button>
                <button type="button" onClick={() => setEditor({ ...emptyMember })} className="inline-flex items-center justify-center gap-2 rounded-lg bg-emerald-600 px-3 py-2 text-xs font-semibold text-white hover:bg-emerald-700 dark:bg-emerald-600 dark:text-white dark:hover:bg-emerald-700"><Plus className="h-4 w-4" />무료 대상자 등록</button>
            </div>
        </div>
        <div className="grid grid-cols-1 divide-y divide-slate-200 dark:divide-slate-800 sm:grid-cols-3 sm:divide-x sm:divide-y-0">
            {([['검색 결과', 'totalCount'], ['사용', 'activeCount'], ['미사용', 'inactiveCount']] as const).map(([title, key]) => <div key={key} className="p-4 md:p-5"><p className="text-xs font-medium text-slate-500 dark:text-slate-400">{title}</p><p className={`mt-1 text-xl font-bold ${key === 'activeCount' ? 'text-emerald-600 dark:text-emerald-400' : key === 'inactiveCount' ? 'text-blue-600 dark:text-blue-400' : 'text-slate-900 dark:text-slate-50'}`}>{loading ? '집계 중...' : failed ? '-' : (data?.summary[key] ?? 0).toLocaleString()}</p></div>)}
        </div>
        <form onSubmit={search} className="border-b border-slate-200 bg-slate-50/70 p-4 dark:border-slate-800 dark:bg-slate-900/40 md:p-5">
            <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-4">
                <label className="md:col-span-2"><span className="mb-1 block text-[11px] font-semibold text-slate-500 dark:text-slate-400">검색어</span><div className="relative"><Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-400 dark:text-slate-400" /><input value={draft.keyword} maxLength={255} onChange={(e) => setDraft({ ...draft, keyword: e.target.value })} placeholder="이름, 소속, 직책, 연락처, 이메일" className={`${searchInput} pl-9`} /></div></label>
                <label><span className="mb-1 block text-[11px] font-semibold text-slate-500 dark:text-slate-400">대상 구분</span><select value={draft.recipientType} onChange={(e) => applySelectFilter('recipientType', e.target.value)} className={searchInput}><option value="">전체</option>{TYPES.map((type) => <option key={type}>{type}</option>)}</select></label>
                <label><span className="mb-1 block text-[11px] font-semibold text-slate-500 dark:text-slate-400">사용 여부</span><select value={draft.isUsed} onChange={(e) => applySelectFilter('isUsed', e.target.value)} className={searchInput}><option value="">전체</option><option value="Y">사용</option><option value="N">미사용</option></select></label>
            </div>
            <div className="mt-3 flex flex-col justify-end gap-2 sm:flex-row"><button type="button" className={`${secondary} px-4`} onClick={() => { const reset = { keyword: '', recipientType: '', isUsed: '' }; setDraft(reset); setFilters(reset); setPage(1); refresh(); }}><FilterX className="h-4 w-4" />초기화</button><button type="submit" className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-5 py-2 text-xs font-semibold text-white hover:bg-blue-700 dark:bg-blue-600 dark:text-white dark:hover:bg-blue-700"><Search className="h-4 w-4" />조회</button></div>
        </form>
        <div className="overflow-x-auto"><table className="w-full min-w-[1000px] text-left text-xs md:text-sm"><thead className="bg-slate-50 dark:bg-slate-900"><tr>{['이름', '소속', '직책', '연락처', '이메일', '대상 구분', '사용 여부', '기능'].map((name) => <th key={name} className="whitespace-nowrap p-4">{name}</th>)}</tr></thead><tbody className="divide-y divide-slate-200 dark:divide-slate-800">
            {failed || !data?.items.length ? <tr><td colSpan={8} className="p-8 text-center text-slate-500 dark:text-slate-400">{loading ? '불러오는 중...' : failed ? <><span>목록을 불러오지 못했습니다. </span><button type="button" onClick={refresh} className={secondary}>다시 시도</button></> : '검색 결과가 없습니다.'}</td></tr> : data.items.map((member) => <tr key={member.seq} className="hover:bg-slate-50 dark:hover:bg-slate-900"><td className="p-4 font-semibold">{member.fullName}</td><td className="max-w-xs break-words p-4">{member.affiliation || '-'}</td><td className="p-4">{member.position || '-'}</td><td className="whitespace-nowrap p-4">{member.phoneNumber}</td><td className="max-w-xs break-all p-4">{member.email || '-'}</td><td className="whitespace-nowrap p-4">{member.recipientType}</td><td className="whitespace-nowrap p-4">{member.isUsed === 'Y' ? '사용' : '미사용'}</td><td className="p-4"><div className="flex gap-2"><button type="button" aria-label={`${member.fullName} 수정`} onClick={() => setEditor({ ...member })} className={rowActionButtonClass}><Pencil className="h-4 w-4" /></button><button type="button" disabled={deleting} aria-label={`${member.fullName} 삭제`} onClick={() => void remove(member)} className={`${rowActionButtonClass} !text-rose-600 dark:!text-rose-400`}><Trash2 className="h-4 w-4" /></button></div></td></tr>)}
        </tbody></table></div>
        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 p-4 text-xs dark:border-slate-800"><span>{failed ? '조회 실패' : `총 ${(data?.summary.totalCount ?? 0).toLocaleString()}명 · ${data?.page ?? 1} / ${data?.totalPages ?? 1} 페이지`}</span><div className="flex gap-2"><button type="button" className={secondary} disabled={loading || failed || page <= 1} onClick={() => { setLoading(true); setPage(page - 1); }}>이전</button><button type="button" className={secondary} disabled={loading || failed || page >= (data?.totalPages ?? 1)} onClick={() => { setLoading(true); setPage(page + 1); }}>다음</button></div></div>
        {editor && <MemberEditor initial={editor} onClose={() => setEditor(null)} onSuccess={refresh} onNotify={onNotify} />}
        {modal === 'import' && <ImportModal onClose={() => setModal(null)} onSuccess={refresh} onNotify={onNotify} />}
    </section>;
}

function Modal({ title, description, icon, busy, onClose, children }: { title: string; description?: string; icon?: ReactNode; busy: boolean; onClose: () => void; children: ReactNode }) {
    const ref = useRef<HTMLDivElement>(null);
    useEffect(() => {
        const focus = document.activeElement as HTMLElement | null;
        const overflow = document.body.style.overflow;
        document.body.style.overflow = 'hidden'; ref.current?.focus();
        return () => { document.body.style.overflow = overflow; focus?.focus(); };
    }, []);
    return <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4 dark:bg-slate-950/60"><DraggableModal ref={ref} role="dialog" aria-modal="true" aria-labelledby="free-recipient-modal-title" tabIndex={-1} onKeyDown={(event) => {
        if (event.key === 'Escape') { event.stopPropagation(); if (!busy) onClose(); }
        if (event.key !== 'Tab') return;
        const elements = Array.from(ref.current?.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), select:not(:disabled), a[href]') ?? []).filter((el) => el.getClientRects().length && !el.closest('fieldset:disabled'));
        const first = elements[0], last = elements[elements.length - 1];
        if (!first) { event.preventDefault(); return; }
        if (event.shiftKey && (document.activeElement === first || document.activeElement === ref.current)) { event.preventDefault(); last.focus(); }
        else if (!event.shiftKey && (document.activeElement === last || document.activeElement === ref.current)) { event.preventDefault(); first.focus(); }
    }} className="flex max-h-[90vh] w-full max-w-2xl flex-col overflow-hidden rounded-2xl bg-white text-slate-900 shadow-2xl outline-none dark:bg-slate-950 dark:text-slate-50">
        <div data-modal-drag-handle className={`flex shrink-0 cursor-move select-none touch-none justify-between border-b border-slate-200 px-5 py-4 dark:border-slate-800 ${description ? 'items-start gap-4' : 'items-center'}`}>
            <div>
                <h2 id="free-recipient-modal-title" className="flex items-center gap-2 text-base font-bold text-slate-900 dark:text-slate-50">{icon}{title}</h2>
                {description && <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">{description}</p>}
            </div>
            <button type="button" onClick={onClose} disabled={busy} aria-label="닫기" className={description ? 'shrink-0 rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 disabled:opacity-40 dark:text-slate-400 dark:hover:bg-slate-900' : 'rounded-lg p-2 hover:bg-slate-100 disabled:opacity-50 dark:hover:bg-slate-900'}><X className="h-5 w-5" /></button>
        </div>
        <div className="overflow-y-auto p-5">{children}</div>
    </DraggableModal></div>;
}

function MemberEditor({ initial, onClose, onSuccess, onNotify }: { initial: Member; onClose: () => void; onSuccess: () => void; onNotify: Notify }) {
    const [member, setMember] = useState(initial);
    const [busy, setBusy] = useState(false);
    const lock = useRef(false);
    const save = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault(); if (lock.current) return;
        if (!member.phoneNumber.trim() || !member.fullName.trim()) { onNotify('error', '연락처와 이름을 입력해 주세요.'); return; }
        lock.current = true; setBusy(true);
        try { await request(initial.seq ? `${API}/${initial.seq}` : API, json(initial.seq ? 'PUT' : 'POST', member)); onNotify('success', '무료 대상자 정보를 저장했습니다.'); onSuccess(); onClose(); }
        catch (error) { onNotify('error', errorText(error)); }
        finally { lock.current = false; setBusy(false); }
    };
    return <Modal title={initial.seq ? '무료 대상자 수정' : '무료 대상자 등록'} busy={busy} onClose={onClose}><form onSubmit={(event) => void save(event)} noValidate><fieldset disabled={busy}><div className="grid gap-4 sm:grid-cols-2">
        {([['이름', 'fullName', 100], ['소속', 'affiliation', 255], ['직책', 'position', 100], ['연락처', 'phoneNumber', 30], ['이메일', 'email', 255], ['관리자 메모', 'adminMemo', 1000]] as const).map(([title, key, max]) => <label key={key} className={label}><span>{title}{(key === 'fullName' || key === 'phoneNumber') && <span className="text-rose-500 dark:text-rose-400"> *</span>}</span><input value={member[key]} maxLength={max} required={(key === 'fullName' || key === 'phoneNumber')} onChange={(e) => setMember({ ...member, [key]: e.target.value })} className={input} /></label>)}
        <label className={label}><span>대상 구분 <span className="text-rose-500 dark:text-rose-400">*</span></span><select value={member.recipientType} onChange={(e) => setMember({ ...member, recipientType: e.target.value })} className={input}>{TYPES.map((type) => <option key={type}>{type}</option>)}</select></label>
        <label className="flex items-center gap-3 rounded-lg border border-slate-200 p-3 text-sm dark:border-slate-800"><input type="checkbox" checked={member.isUsed === 'Y'} onChange={(e) => setMember({ ...member, isUsed: e.target.checked ? 'Y' : 'N' })} className="h-5 w-5" />사용</label>
        <p className="text-xs text-slate-500 dark:text-slate-400 sm:col-span-2">이름과 연락처가 같으면 중복입니다. 연락처의 공백·하이픈을 제거하고 한국 국가번호(+82)는 국내 형식으로 대조합니다.</p>
    </div></fieldset><Footer busy={busy} onClose={onClose} /></form></Modal>;
}

function Footer({ busy, onClose, disabled = false }: { busy: boolean; onClose: () => void; disabled?: boolean }) {
    return <div className="mt-5 flex justify-end gap-2 border-t border-slate-200 pt-5 dark:border-slate-800"><button type="button" onClick={onClose} disabled={busy} className={`${secondary} px-4 text-sm`}>취소</button><button type="submit" disabled={busy || disabled} className={primary}>{busy ? '저장 중...' : '저장'}</button></div>;
}

function ImportModal({ onClose, onSuccess, onNotify }: { onClose: () => void; onSuccess: () => void; onNotify: Notify }) {
    const [file, setFile] = useState<File | null>(null);
    const [busy, setBusy] = useState(false);
    const [result, setResult] = useState<ImportResult | null>(null);
    const fileRef = useRef<HTMLInputElement>(null);
    const lock = useRef(false);
    const upload = async (event: FormEvent) => {
        event.preventDefault(); if (!file || lock.current || result) return;
        lock.current = true; setBusy(true);
        try {
            const body = new FormData(); body.append('file', file);
            const imported = await request<ImportResult>(`${API}/import`, { method: 'POST', body }); setResult(imported);
            onNotify(imported.failureCount ? 'info' : 'success', `등록 ${imported.successCount}명 · 건너뜀 ${imported.skippedCount}명 · 실패 ${imported.failureCount}명`);
            if (imported.successCount) onSuccess();
        } catch (error) { onNotify('error', errorText(error)); }
        finally { lock.current = false; setBusy(false); }
    };
    return <Modal title="무료 대상자 엑셀 일괄등록" description="엑셀 명단을 이용해 기존 대상자를 유지하면서 무료 대상자를 추가합니다." icon={<FileSpreadsheet className="h-5 w-5 shrink-0 text-violet-600 dark:text-violet-400" />} busy={busy} onClose={onClose}><form onSubmit={(event) => void upload(event)} className="space-y-5">
        <p className="text-sm text-slate-600 dark:text-slate-300">첫 번째 시트의 5행부터 입력해 주세요. .xlsx, 최대 5MB, 1,000명까지 등록할 수 있습니다. 동일 이름과 정규화한 연락처의 기존 명단 및 파일 내 중복은 건너뛰며, 오류가 있는 행을 제외한 정상 행은 등록합니다.</p>
        <a href={TEMPLATE} download className={secondary}><Download className="h-4 w-4" />양식 다운로드</a>
        <input ref={fileRef} type="file" accept=".xlsx" className="hidden" disabled={busy} onChange={(event) => {
            const selected = event.target.files?.[0]; event.target.value = ''; setResult(null); setFile(null);
            if (!selected) return;
            if (!selected.name.toLowerCase().endsWith('.xlsx') || selected.size > 5 * 1024 * 1024 || selected.size === 0) { onNotify('error', '비어 있지 않은 5MB 이하의 .xlsx 파일을 선택해 주세요.'); return; }
            setFile(selected);
        }} />
        <div className="flex flex-wrap items-center gap-3"><button type="button" className={secondary} disabled={busy} onClick={() => fileRef.current?.click()}><Upload className="h-4 w-4" />파일 선택</button><span className="break-all text-sm">{file?.name ?? '선택한 파일 없음'}</span></div>
        {result && <div aria-live="polite"><p className="mb-3 text-sm font-semibold">총 {result.totalCount}명 · 등록 {result.successCount}명 · 건너뜀 {result.skippedCount}명 · 실패 {result.failureCount}명</p><div className="max-h-60 overflow-auto"><table className="w-full text-left text-xs"><thead className="bg-slate-50 dark:bg-slate-900"><tr><th className="p-3">행</th><th className="p-3">이름</th><th className="p-3">결과</th></tr></thead><tbody>{result.results.map((row) => <tr key={row.rowNumber} className="border-b border-slate-200 dark:border-slate-800"><td className="p-3">{row.rowNumber}</td><td className="p-3">{row.fullName || '-'}</td><td className="p-3">{row.message}</td></tr>)}</tbody></table></div><p className="mt-3 text-xs text-slate-500 dark:text-slate-400">수정한 파일을 다시 선택하여 등록할 수 있습니다. 등록된 회원의 정보 변경은 목록의 수정 기능을 이용해 주세요.</p></div>}
        <div className="flex justify-end gap-2 border-t border-slate-200 pt-5 dark:border-slate-800">
            <button type="button" disabled={busy} onClick={onClose} className="rounded-lg border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-40 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900">닫기</button>
            <button type="submit" disabled={busy || !file || !!result} className="inline-flex items-center gap-2 rounded-lg bg-violet-600 px-4 py-2 text-xs font-semibold text-white hover:bg-violet-700 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-violet-600 dark:text-white dark:hover:bg-violet-700">{busy ? <LoaderCircle className="h-4 w-4 animate-spin" /> : <Upload className="h-4 w-4" />}{busy ? '등록 중' : '일괄등록'}</button>
        </div>
    </form></Modal>;
}

