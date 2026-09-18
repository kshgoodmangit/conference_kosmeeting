import { DraggableModal } from './DraggableModal';
import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { Download, FileSpreadsheet, LoaderCircle, FilterX, Pencil, Plus, Search, Settings, Trash2, Upload, Users, X } from 'lucide-react';
import type { NotificationType } from './NotificationToast';
import { useConfirm } from './confirmDialogContext';
import { rowActionButtonClass } from './RowActionMenu';

interface Member { seq?: number; licenseNumber: string; fullName: string; affiliation: string; memberType: string }
interface Page { items: Member[]; summary: { totalCount: number; regularCount: number; associateCount: number; otherCount: number }; page: number; totalPages: number }
interface ImportResult { totalCount: number; successCount: number; skippedCount: number; failureCount: number; results: { rowNumber: number; licenseNumber: string; status: string; message: string }[] }
interface Mapping { memberType: string; categorySeq: number | null }
interface Category { seq: number; categoryName: string; isUsed: string }
type Notify = (type: NotificationType, message: string) => void;
const TYPES = ['정회원', '준회원', '기타'];
const API = '/api/admin/society-members';
const TEMPLATE = '/templates/society_member_bulk_import_template.xlsx';
const input = 'w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm font-normal text-slate-900 placeholder:text-slate-400 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50';
const searchInput = 'w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-700 dark:bg-slate-950 dark:text-slate-50';
const secondary = 'inline-flex items-center justify-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900';
const primary = 'inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-50 dark:bg-blue-600 dark:text-white dark:hover:bg-blue-700';
const label = 'space-y-1.5 text-sm font-semibold text-slate-700 dark:text-slate-200';
const emptyMember: Member = { licenseNumber: '', fullName: '', affiliation: '', memberType: '정회원' };

async function request<T>(url: string, init?: RequestInit): Promise<T> {
    const response = await fetch(url, init);
    if (!response.ok) throw new Error(await response.text() || '요청을 처리하지 못했습니다.');
    return (response.status === 204 ? undefined : await response.json()) as T;
}
const json = (method: string, value: unknown): RequestInit => ({ method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(value) });
const errorText = (error: unknown) => error instanceof Error ? error.message : '요청을 처리하지 못했습니다.';

export function SocietyMemberPage({ onNotify }: { onNotify: Notify }) {
    const confirm = useConfirm();
    const [draft, setDraft] = useState({ keyword: '', memberType: '' });
    const [filters, setFilters] = useState(draft);
    const [page, setPage] = useState(1);
    const [reload, setReload] = useState(0);
    const [data, setData] = useState<Page | null>(null);
    const [loading, setLoading] = useState(true);
    const [failed, setFailed] = useState(false);
    const [editor, setEditor] = useState<Member | null>(null);
    const [modal, setModal] = useState<'import' | 'fees' | null>(null);
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
            if (!await confirm({ title: '학회회원 삭제', message: `${member.fullName} (${member.licenseNumber}) 회원을 명부에서 삭제하시겠습니까? 이후 학회회원 확인에 사용되지 않습니다.`, confirmText: '삭제', tone: 'danger' })) return;
            await request(`${API}/${member.seq}`, { method: 'DELETE' });
            onNotify('success', '학회회원을 삭제했습니다.'); refresh();
        } catch (error) { onNotify('error', errorText(error)); }
        finally { deleteLock.current = false; setDeleting(false); }
    };
    const search = (event: FormEvent) => { event.preventDefault(); setFilters({ ...draft, keyword: draft.keyword.trim() }); setPage(1); refresh(); };
    const applyMemberTypeFilter = (value: string) => {
        const next = { ...draft, memberType: value };
        setDraft(next); setFilters({ ...next, keyword: filters.keyword }); setPage(1); refresh();
    };
    return <section className="overflow-hidden rounded-xl border border-slate-200 bg-white text-slate-900 shadow-sm dark:border-slate-800 dark:bg-slate-950 dark:text-slate-50">
        <div className="flex flex-col justify-between gap-4 border-b border-slate-200 p-4 dark:border-slate-800 xl:flex-row xl:items-center md:p-5">
            <div>
                <div className="flex items-center gap-2">
                    <Users className="h-4 w-4 shrink-0" />
                    <h1 className="text-sm font-semibold md:text-base">학회회원관리</h1>
                </div>
                <p className="mt-1 text-xs text-slate-400 dark:text-slate-400">학회회원 명부와 회원구분별 등록비 연결을 관리합니다.</p>
            </div>
            <div className="flex flex-wrap gap-2">
                <button type="button" className={secondary} onClick={() => setModal('fees')}><Settings className="h-4 w-4" />등록비 연결</button>
                <a href={TEMPLATE} download className={secondary}><Download className="h-4 w-4" />양식 다운로드</a>
                <button type="button" className="inline-flex items-center justify-center gap-2 rounded-lg border border-violet-200 bg-violet-50 px-3 py-2 text-xs font-semibold text-violet-700 hover:bg-violet-100 disabled:opacity-50 dark:border-violet-900/60 dark:bg-violet-950/30 dark:text-violet-300 dark:hover:bg-violet-950/50" onClick={() => setModal('import')}><FileSpreadsheet className="h-4 w-4" />엑셀 일괄등록</button>
                <button type="button" onClick={() => setEditor({ ...emptyMember })} className="inline-flex items-center justify-center gap-2 rounded-lg bg-emerald-600 px-3 py-2 text-xs font-semibold text-white hover:bg-emerald-700 dark:bg-emerald-600 dark:text-white dark:hover:bg-emerald-700"><Plus className="h-4 w-4" />학회회원 등록</button>
            </div>
        </div>
        <div className="grid grid-cols-1 divide-y divide-slate-200 dark:divide-slate-800 sm:grid-cols-4 sm:divide-x sm:divide-y-0">
            {([['검색 결과', 'totalCount'], ['정회원', 'regularCount'], ['준회원', 'associateCount'], ['기타', 'otherCount']] as const).map(([title, key]) => <div key={key} className="p-4 md:p-5"><p className="text-xs font-medium text-slate-500 dark:text-slate-400">{title}</p><p className={`mt-1 text-xl font-bold ${key === 'regularCount' ? 'text-emerald-600 dark:text-emerald-400' : key === 'associateCount' ? 'text-blue-600 dark:text-blue-400' : 'text-slate-900 dark:text-slate-50'}`}>{loading ? '집계 중...' : failed ? '-' : (data?.summary[key] ?? 0).toLocaleString()}</p></div>)}
        </div>
        <form onSubmit={search} className="border-b border-slate-200 bg-slate-50/70 p-4 dark:border-slate-800 dark:bg-slate-900/40 md:p-5">
            <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-4">
                <label className="md:col-span-2"><span className="mb-1 block text-[11px] font-semibold text-slate-500 dark:text-slate-400">검색어</span><div className="relative"><Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-400 dark:text-slate-400" /><input value={draft.keyword} maxLength={255} onChange={(e) => setDraft({ ...draft, keyword: e.target.value })} placeholder="면허번호, 이름, 소속" className={`${searchInput} pl-9`} /></div></label>
                <label><span className="mb-1 block text-[11px] font-semibold text-slate-500 dark:text-slate-400">회원구분</span><select value={draft.memberType} onChange={(e) => applyMemberTypeFilter(e.target.value)} className={searchInput}><option value="">전체</option>{TYPES.map((type) => <option key={type}>{type}</option>)}</select></label>
            </div>
            <div className="mt-3 flex flex-col justify-end gap-2 sm:flex-row"><button type="button" className={`${secondary} px-4`} onClick={() => { const reset = { keyword: '', memberType: '' }; setDraft(reset); setFilters(reset); setPage(1); refresh(); }}><FilterX className="h-4 w-4" />초기화</button><button type="submit" className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-5 py-2 text-xs font-semibold text-white hover:bg-blue-700 dark:bg-blue-600 dark:text-white dark:hover:bg-blue-700"><Search className="h-4 w-4" />조회</button></div>
        </form>
        <div className="overflow-x-auto"><table className="w-full min-w-[640px] text-left text-xs md:text-sm"><thead className="bg-slate-50 dark:bg-slate-900"><tr>{['면허번호', '이름', '소속', '회원구분', '기능'].map((name) => <th key={name} className="whitespace-nowrap p-4">{name}</th>)}</tr></thead><tbody className="divide-y divide-slate-200 dark:divide-slate-800">
            {failed || !data?.items.length ? <tr><td colSpan={5} className="p-8 text-center text-slate-500 dark:text-slate-400">{loading ? '불러오는 중...' : failed ? <><span>목록을 불러오지 못했습니다. </span><button type="button" onClick={refresh} className={secondary}>다시 시도</button></> : '검색 결과가 없습니다.'}</td></tr> : data.items.map((member) => <tr key={member.seq} className="hover:bg-slate-50 dark:hover:bg-slate-900"><td className="p-4">{member.licenseNumber}</td><td className="p-4 font-semibold">{member.fullName}</td><td className="max-w-xs break-words p-4">{member.affiliation || '-'}</td><td className="whitespace-nowrap p-4">{member.memberType}</td><td className="p-4"><div className="flex gap-2"><button type="button" aria-label={`${member.fullName} 수정`} onClick={() => setEditor({ ...member })} className={rowActionButtonClass}><Pencil className="h-4 w-4" /></button><button type="button" disabled={deleting} aria-label={`${member.fullName} 삭제`} onClick={() => void remove(member)} className={`${rowActionButtonClass} !text-rose-600 dark:!text-rose-400`}><Trash2 className="h-4 w-4" /></button></div></td></tr>)}
        </tbody></table></div>
        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 p-4 text-xs dark:border-slate-800"><span>{failed ? '조회 실패' : `총 ${(data?.summary.totalCount ?? 0).toLocaleString()}명 · ${data?.page ?? 1} / ${data?.totalPages ?? 1} 페이지`}</span><div className="flex gap-2"><button type="button" className={secondary} disabled={loading || failed || page <= 1} onClick={() => { setLoading(true); setPage(page - 1); }}>이전</button><button type="button" className={secondary} disabled={loading || failed || page >= (data?.totalPages ?? 1)} onClick={() => { setLoading(true); setPage(page + 1); }}>다음</button></div></div>
        {editor && <MemberEditor initial={editor} onClose={() => setEditor(null)} onSuccess={refresh} onNotify={onNotify} />}
        {modal === 'import' && <ImportModal onClose={() => setModal(null)} onSuccess={refresh} onNotify={onNotify} />}
        {modal === 'fees' && <FeeMappingModal onClose={() => setModal(null)} onNotify={onNotify} />}
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
    return <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4 dark:bg-slate-950/60"><DraggableModal ref={ref} role="dialog" aria-modal="true" aria-labelledby="society-modal-title" tabIndex={-1} onKeyDown={(event) => {
        if (event.key === 'Escape') { event.stopPropagation(); if (!busy) onClose(); }
        if (event.key !== 'Tab') return;
        const elements = Array.from(ref.current?.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), select:not(:disabled), a[href]') ?? []).filter((el) => el.getClientRects().length && !el.closest('fieldset:disabled'));
        const first = elements[0], last = elements[elements.length - 1];
        if (!first) { event.preventDefault(); return; }
        if (event.shiftKey && (document.activeElement === first || document.activeElement === ref.current)) { event.preventDefault(); last.focus(); }
        else if (!event.shiftKey && (document.activeElement === last || document.activeElement === ref.current)) { event.preventDefault(); first.focus(); }
    }} className="flex max-h-[90vh] w-full max-w-2xl flex-col overflow-hidden rounded-2xl bg-white text-slate-900 shadow-2xl outline-none dark:bg-slate-950 dark:text-slate-50"><div data-modal-drag-handle className={`flex shrink-0 cursor-move select-none touch-none justify-between border-b border-slate-200 px-5 py-4 dark:border-slate-800 ${description ? 'items-start gap-4' : 'items-center'}`}>
            <div>
                <h2 id="society-modal-title" className="flex items-center gap-2 text-base font-bold text-slate-900 dark:text-slate-50">{icon}{title}</h2>
                {description && <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">{description}</p>}
            </div>
            <button type="button" onClick={onClose} disabled={busy} aria-label="닫기" className={description ? 'shrink-0 rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 disabled:opacity-40 dark:text-slate-400 dark:hover:bg-slate-900' : 'rounded-lg p-2 hover:bg-slate-100 disabled:opacity-50 dark:hover:bg-slate-900'}><X className="h-5 w-5" /></button>
        </div><div className="overflow-y-auto p-5">{children}</div></DraggableModal></div>;
}

function MemberEditor({ initial, onClose, onSuccess, onNotify }: { initial: Member; onClose: () => void; onSuccess: () => void; onNotify: Notify }) {
    const [member, setMember] = useState(initial);
    const [busy, setBusy] = useState(false);
    const lock = useRef(false);
    const save = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault(); if (lock.current) return;
        if (!member.licenseNumber.trim() || !member.fullName.trim()) { onNotify('error', '면허번호와 이름을 입력해 주세요.'); return; }
        lock.current = true; setBusy(true);
        try { await request(initial.seq ? `${API}/${initial.seq}` : API, json(initial.seq ? 'PUT' : 'POST', member)); onNotify('success', '학회회원 정보를 저장했습니다.'); onSuccess(); onClose(); }
        catch (error) { onNotify('error', errorText(error)); }
        finally { lock.current = false; setBusy(false); }
    };
    return <Modal title={initial.seq ? '학회회원 수정' : '학회회원 등록'} busy={busy} onClose={onClose}><form onSubmit={(event) => void save(event)} noValidate><fieldset disabled={busy}><div className="grid gap-4 sm:grid-cols-2">
        {([['면허번호', 'licenseNumber', 50], ['이름', 'fullName', 100], ['소속', 'affiliation', 255]] as const).map(([title, key, max]) => <label key={key} className={label}><span>{title}{key !== 'affiliation' && <span className="text-rose-500 dark:text-rose-400"> *</span>}</span><input value={member[key]} maxLength={max} required={key !== 'affiliation'} onChange={(e) => setMember({ ...member, [key]: e.target.value })} className={input} /></label>)}
        <label className={label}><span>회원구분 <span className="text-rose-500 dark:text-rose-400">*</span></span><select value={member.memberType} onChange={(e) => setMember({ ...member, memberType: e.target.value })} className={input}>{TYPES.map((type) => <option key={type}>{type}</option>)}</select></label>
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
    return <Modal title="학회회원 엑셀 일괄등록" description="엑셀 명단을 이용해 기존 회원을 유지하면서 학회회원을 추가합니다." icon={<FileSpreadsheet className="h-5 w-5 shrink-0 text-violet-600 dark:text-violet-400" />} busy={busy} onClose={onClose}><form onSubmit={(event) => void upload(event)} className="space-y-5">
        <p className="text-sm text-slate-600 dark:text-slate-300">첫 번째 시트의 5행부터 입력해 주세요. .xlsx, 최대 5MB, 1,000명까지 등록할 수 있습니다. 기존 면허번호와 파일 내 중복은 건너뛰며, 오류가 있는 행을 제외한 정상 행은 등록합니다.</p>
        <a href={TEMPLATE} download className={secondary}><Download className="h-4 w-4" />양식 다운로드</a>
        <input ref={fileRef} type="file" accept=".xlsx" className="hidden" disabled={busy} onChange={(event) => {
            const selected = event.target.files?.[0]; event.target.value = ''; setResult(null); setFile(null);
            if (!selected) return;
            if (!selected.name.toLowerCase().endsWith('.xlsx') || selected.size > 5 * 1024 * 1024 || selected.size === 0) { onNotify('error', '비어 있지 않은 5MB 이하의 .xlsx 파일을 선택해 주세요.'); return; }
            setFile(selected);
        }} />
        <div className="flex flex-wrap items-center gap-3"><button type="button" className={secondary} disabled={busy} onClick={() => fileRef.current?.click()}><Upload className="h-4 w-4" />파일 선택</button><span className="break-all text-sm">{file?.name ?? '선택한 파일 없음'}</span></div>
        {result && <div aria-live="polite"><p className="mb-3 text-sm font-semibold">총 {result.totalCount}명 · 등록 {result.successCount}명 · 건너뜀 {result.skippedCount}명 · 실패 {result.failureCount}명</p><div className="max-h-60 overflow-auto"><table className="w-full text-left text-xs"><thead className="bg-slate-50 dark:bg-slate-900"><tr><th className="p-3">행</th><th className="p-3">면허번호</th><th className="p-3">결과</th></tr></thead><tbody>{result.results.filter((row) => row.status !== 'SUCCESS').map((row) => <tr key={row.rowNumber} className="border-b border-slate-200 dark:border-slate-800"><td className="p-3">{row.rowNumber}</td><td className="p-3">{row.licenseNumber || '-'}</td><td className="p-3">{row.message}</td></tr>)}</tbody></table></div><p className="mt-3 text-xs text-slate-500 dark:text-slate-400">수정한 파일을 다시 선택하여 등록할 수 있습니다. 등록된 회원의 정보 변경은 목록의 수정 기능을 이용해 주세요.</p></div>}
        <div className="flex justify-end gap-2 border-t border-slate-200 pt-5 dark:border-slate-800"><button type="button" className="rounded-lg border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-40 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900" disabled={busy} onClick={onClose}>닫기</button><button type="submit" disabled={busy || !file || !!result} className="inline-flex items-center gap-2 rounded-lg bg-violet-600 px-4 py-2 text-xs font-semibold text-white hover:bg-violet-700 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-violet-600 dark:text-white dark:hover:bg-violet-700">{busy ? <LoaderCircle className="h-4 w-4 animate-spin" /> : <Upload className="h-4 w-4" />}{busy ? '등록 중...' : '일괄등록'}</button></div>
    </form></Modal>;
}

function FeeMappingModal({ onClose, onNotify }: { onClose: () => void; onNotify: Notify }) {
    const [mappings, setMappings] = useState<Mapping[]>([]);
    const [categories, setCategories] = useState<Category[]>([]);
    const [loading, setLoading] = useState(true);
    const [busy, setBusy] = useState(false);
    const [failed, setFailed] = useState(false);
    const lock = useRef(false);
    const notifyRef = useRef(onNotify);
    useEffect(() => { notifyRef.current = onNotify; }, [onNotify]);
    useEffect(() => {
        const controller = new AbortController();
        Promise.all([request<Mapping[]>(`${API}/fee-mappings`, { signal: controller.signal }), request<Category[]>('/api/admin/registration-fees', { signal: controller.signal })])
            .then(([values, options]) => { if (!controller.signal.aborted) { setMappings(values); setCategories(options.filter((c) => c.isUsed === 'Y')); } })
            .catch((error: unknown) => { if (!controller.signal.aborted) { setFailed(true); notifyRef.current('error', errorText(error)); } })
            .finally(() => { if (!controller.signal.aborted) setLoading(false); });
        return () => controller.abort();
    }, []);
    const save = async (event: FormEvent) => {
        event.preventDefault(); if (lock.current || loading || failed) return;
        lock.current = true; setBusy(true);
        try { await request(`${API}/fee-mappings`, json('PUT', mappings)); onNotify('success', '회원구분별 등록비 연결을 저장했습니다.'); onClose(); }
        catch (error) { onNotify('error', errorText(error)); }
        finally { lock.current = false; setBusy(false); }
    };
    return <Modal title="회원구분별 등록비 연결" busy={busy} onClose={onClose}><form onSubmit={(event) => void save(event)}><p className="mb-5 text-sm text-slate-600 dark:text-slate-300">학술대회 설정의 등록비 구분과 금액을 먼저 설정한 뒤 연결해 주세요. 사전등록 추가 시 명부에서 확인한 회원구분과 등록 기간·통화에 맞는 금액을 적용합니다.</p>{loading ? <p>불러오는 중...</p> : failed ? <p>설정을 불러오지 못했습니다. 창을 닫고 다시 시도해 주세요.</p> : <fieldset disabled={busy} className="space-y-5">{mappings.map((mapping) => <label key={mapping.memberType} className={`block ${label}`}><span>{mapping.memberType}</span><select aria-label={mapping.memberType} className={input} value={mapping.categorySeq ?? ''} onChange={(event) => setMappings(mappings.map((m) => m.memberType === mapping.memberType ? { ...m, categorySeq: event.target.value ? Number(event.target.value) : null } : m))}><option value="">미설정</option>{mapping.categorySeq && !categories.some((c) => c.seq === mapping.categorySeq) && <option value={mapping.categorySeq}>기존 연결 (사용 불가 · 다시 선택)</option>}{categories.map((c) => <option key={c.seq} value={c.seq}>{c.categoryName}</option>)}</select></label>)}</fieldset>}<Footer busy={busy} onClose={onClose} disabled={loading || failed} /></form></Modal>;
}
