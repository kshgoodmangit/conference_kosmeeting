import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { ListChecks, Pencil, Plus, Save, Trash2 } from 'lucide-react';
import type { NotificationType } from './NotificationToast';
import { useConfirm } from './confirmDialogContext';
import { rowActionButtonClass } from './RowActionMenu';

interface Option {
    seq?: number; conferenceSeq?: number; optionName: string; description: string;
    krwPrice: number | null; usdPrice: number | null;
    saleStartsAt: string | null; saleEndsAt: string | null; changeEndsAt: string | null;
    capacity: number | null; maxPerPerson: number; enabled: boolean; sortOrder: number; versionNo: number;
}
interface Page { items: Option[]; page: number; totalPages: number }
interface Props { conferenceSeq: number; onNotify: (type: NotificationType, message: string) => void; onBusyChange: (busy: boolean) => void }
type Form = Omit<Option, 'krwPrice' | 'usdPrice' | 'capacity'> & { krwPrice: string; usdPrice: string; capacity: string };
const empty = (): Form => ({ optionName: '', description: '', krwPrice: '', usdPrice: '', capacity: '', maxPerPerson: 1,
    saleStartsAt: null, saleEndsAt: null, changeEndsAt: null, enabled: true, sortOrder: 0, versionNo: 0 });
const secondary = 'inline-flex items-center justify-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-900';
const blue = 'inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-50 dark:bg-blue-600 dark:text-white dark:hover:bg-blue-700';
const editorInput = 'w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm font-normal text-slate-900 placeholder:text-slate-400 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50';
const editorCancel = 'inline-flex items-center justify-center rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900';

const OptionField = ({ label, required, fullWidth, children }: { label: string; required?: boolean; fullWidth?: boolean; children: ReactNode }) => (
    <label className={`block min-w-0 text-sm font-semibold text-slate-700 dark:text-slate-200 ${fullWidth ? 'sm:col-span-2' : ''}`}>
        <span className="mb-1.5 block">{label}{required && <span className="ml-1 text-rose-500 dark:text-rose-400">*</span>}</span>
        {children}
    </label>
);

export const RegistrationOptionPanel = ({ conferenceSeq, onNotify, onBusyChange }: Props) => {
    const confirm = useConfirm();
    const notify = useRef(onNotify);
    const [data, setData] = useState<Page | null>(null);
    const [page, setPage] = useState(1);
    const [refresh, setRefresh] = useState(0);
    const [loading, setLoading] = useState(true);
    const [failed, setFailed] = useState(false);
    const [saving, setSaving] = useState(false);
    const [deletingSeq, setDeletingSeq] = useState<number | null>(null);
    const [form, setForm] = useState<Form | null>(null);
    const [editingName, setEditingName] = useState('');
    const editor = useRef<HTMLFormElement>(null);
    const editorKey = form === null ? null : form.seq ?? 'new';
    const api = `/api/admin/conference-settings/${conferenceSeq}/registration-options`;
    useEffect(() => { notify.current = onNotify; }, [onNotify]);
    const busy = saving || deletingSeq !== null;
    useEffect(() => {
        onBusyChange(busy);
        return () => onBusyChange(false);
    }, [busy, onBusyChange]);
    useEffect(() => {
        const controller = new AbortController();
        void (async () => {
            await Promise.resolve();
            if (controller.signal.aborted) return;
            setLoading(true); setFailed(false);
            try {
                const params = new URLSearchParams({ page: String(page) });
                const response = await fetch(`${api}?${params}`, { signal: controller.signal });
                if (!response.ok) throw new Error(await response.text() || '옵션을 조회하지 못했습니다.');
                const result = await response.json() as Page;
                if (!controller.signal.aborted) {
                    setData(result);
                    setPage(result.page);
                }
            } catch (error) {
                if (controller.signal.aborted) return;
                setFailed(true);
                notify.current('error', error instanceof Error ? error.message : '옵션을 조회하지 못했습니다.');
            } finally { if (!controller.signal.aborted) setLoading(false); }
        })();
        return () => controller.abort();
    }, [api, page, refresh]);
    useEffect(() => { if (editorKey !== null) editor.current?.querySelector('input')?.focus(); }, [editorKey]);

    const edit = (item?: Option) => {
        setEditingName(item?.optionName ?? '');
        setForm(item ? { ...item, krwPrice: item.krwPrice == null ? '' : String(item.krwPrice),
            usdPrice: item.usdPrice == null ? '' : String(item.usdPrice), capacity: item.capacity == null ? '' : String(item.capacity) } : empty());
    };
    const save = async (event: FormEvent) => {
        event.preventDefault();
        if (!form || saving) return;
        if (!form.optionName.trim() || (form.krwPrice === '' && form.usdPrice === '')) {
            notify.current('error', '옵션명과 한 통화 이상의 가격을 입력해 주세요. 무료는 0을 입력하세요.'); return;
        }
        if (form.seq && !form.enabled && !await confirm({ title: '옵션 사용 중지', message: '이 옵션을 사용 안 함으로 저장하시겠습니까?', confirmText: '사용 중지', tone: 'danger' })) return;
        setSaving(true);
        try {
            const response = await fetch(form.seq ? `${api}/${form.seq}` : api, {
                method: form.seq ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ ...form, krwPrice: form.krwPrice === '' ? null : form.krwPrice,
                    usdPrice: form.usdPrice === '' ? null : form.usdPrice, capacity: form.capacity === '' ? null : Number(form.capacity) })
            });
            if (!response.ok) throw new Error(await response.text() || '옵션을 저장하지 못했습니다.');
            setForm(null); setRefresh(value => value + 1);
            notify.current('success', '등록 옵션을 저장했습니다.');
        } catch (error) { notify.current('error', error instanceof Error ? error.message : '옵션을 저장하지 못했습니다.'); }
        finally { setSaving(false); }
    };
    const remove = async (item: Option) => {
        if (item.seq == null || busy) return;
        setDeletingSeq(item.seq);
        try {
            const confirmed = await confirm({
                title: '등록 옵션 삭제',
                message: `'${item.optionName}' 옵션을 삭제하시겠습니까? 삭제한 옵션은 복구할 수 없습니다.`,
                confirmText: '삭제',
                tone: 'danger'
            });
            if (!confirmed) return;
            const response = await fetch(`${api}/${item.seq}`, { method: 'DELETE' });
            if (!response.ok) throw new Error(await response.text() || '옵션을 삭제하지 못했습니다.');
            setRefresh(value => value + 1);
            notify.current('success', '등록 옵션을 삭제했습니다.');
        } catch (error) {
            notify.current('error', error instanceof Error ? error.message : '옵션을 삭제하지 못했습니다.');
        } finally {
            setDeletingSeq(null);
        }
    };
    return <section className="p-5 text-slate-900 dark:text-slate-50">
            <header className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
                <div>
                    <div className="flex items-center gap-2">
                        <ListChecks className="h-5 w-5 shrink-0 text-blue-500 dark:text-blue-400" />
                        <h4 className="text-sm font-bold text-slate-800 dark:text-slate-100">등록 옵션 설정</h4>
                    </div>
                    <p className="mt-1 text-xs text-slate-400 dark:text-slate-400">현재 학회의 옵션과 신청 조건을 관리합니다. 옵션은 개별 저장됩니다.</p>
                </div>
                <button type="button" disabled={busy || form !== null} onClick={() => edit()} className="inline-flex items-center justify-center gap-2 rounded-lg bg-emerald-600 px-3 py-2 text-xs font-semibold text-white hover:bg-emerald-700 disabled:opacity-50 dark:bg-emerald-600 dark:text-white dark:hover:bg-emerald-700"><Plus className="h-4 w-4" />옵션 추가</button>
            </header>
            <div className="mt-5 overflow-x-auto"><table className="w-full text-left text-xs md:text-sm"><thead className="bg-slate-50 dark:bg-slate-900"><tr>{['옵션명', 'KRW / USD', '정원 / 1인 최대', '신청기간 (한국 시간)', '사용 / 순서', '기능'].map(label => <th key={label} className="p-4">{label}</th>)}</tr></thead><tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                {failed || !data?.items.length ? <tr><td colSpan={6} className="p-8 text-center">{loading ? '불러오는 중...' : failed ? '조회하지 못했습니다. 조회 버튼으로 다시 시도해 주세요.' : '등록된 옵션이 없습니다.'}</td></tr> : data.items.map(item => <tr key={item.seq} className={form?.seq === item.seq ? 'bg-blue-50 dark:bg-blue-950/40' : 'hover:bg-slate-50 dark:hover:bg-slate-900/60'}>
                    <td className="min-w-40 p-4"><div className="flex flex-wrap items-center gap-2"><p className="font-semibold">{item.optionName}</p>{form?.seq === item.seq && <span className="inline-flex items-center gap-1 rounded-full bg-blue-100 px-2 py-0.5 text-xs font-semibold text-blue-700 dark:bg-blue-900/60 dark:text-blue-300"><Pencil className="h-3 w-3" aria-hidden="true" />수정 중</span>}</div><p className="mt-1 max-w-64 whitespace-pre-wrap break-words text-xs text-slate-500 dark:text-slate-400">{item.description}</p></td>
                    <td className="whitespace-nowrap p-4">{item.krwPrice == null ? '미제공' : `${item.krwPrice.toLocaleString()}원`} / {item.usdPrice == null ? '미제공' : `$${item.usdPrice.toFixed(2)}`}</td>
                    <td className="whitespace-nowrap p-4">{item.capacity == null ? '무제한' : item.capacity} / {item.maxPerPerson}</td>
                    <td className="min-w-44 p-4">{item.saleStartsAt?.replace('T', ' ').slice(0, 16) || '제한 없음'}<br />~ {item.saleEndsAt?.replace('T', ' ').slice(0, 16) || '제한 없음'}</td>
                    <td className="whitespace-nowrap p-4">{item.enabled ? '사용' : '사용 안 함'} / {item.sortOrder}</td>
                    <td className="p-4"><div className="flex gap-2"><button type="button" disabled={form !== null || busy} className={rowActionButtonClass} aria-label={`${item.optionName} 수정`} onClick={() => edit(item)}><Pencil className="h-4 w-4" /></button><button type="button" disabled={form !== null || busy} className={`${rowActionButtonClass} !text-rose-600 dark:!text-rose-400`} aria-label={`${item.optionName} 삭제`} onClick={() => void remove(item)}><Trash2 className="h-4 w-4" /></button></div></td>
                </tr>)}
            </tbody></table></div>
            <div className="flex items-center justify-end gap-3 border-t border-slate-200 p-4 text-xs dark:border-slate-800"><button type="button" className={secondary} disabled={loading || failed || !data || data.page <= 1} onClick={() => setPage((data?.page ?? 1) - 1)}>이전</button><span>{data?.page ?? 1} / {data?.totalPages ?? 1}</span><button type="button" className={secondary} disabled={loading || failed || !data || data.page >= data.totalPages} onClick={() => setPage((data?.page ?? 1) + 1)}>다음</button></div>
        {form && <form ref={editor} onSubmit={event => void save(event)} className="mt-5 rounded-xl border border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-950 md:p-5">
            <h4 className="mb-4 break-words text-sm font-semibold text-slate-900 dark:text-slate-50">{form.seq ? `옵션 수정 · ${editingName}` : '옵션 추가'}</h4>
            <fieldset disabled={saving} className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <OptionField label="옵션명" required><input className={editorInput} required maxLength={100} value={form.optionName} onChange={e => setForm({ ...form, optionName: e.target.value })} /></OptionField>
                <OptionField label="사용 여부"><select className={editorInput} value={form.enabled ? 'Y' : 'N'} onChange={e => setForm({ ...form, enabled: e.target.value === 'Y' })}><option value="Y">사용</option><option value="N">사용 안 함</option></select></OptionField>
                <OptionField label="설명" fullWidth><textarea className={editorInput} maxLength={1000} rows={2} value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} /></OptionField>
                {(['krwPrice', 'usdPrice'] as const).map(key => <OptionField key={key} label={key === 'krwPrice' ? 'KRW 가격' : 'USD 가격'}><input className={editorInput} type="number" min="0" max={key === 'krwPrice' ? '9999999999' : '9999999999.99'} step={key === 'krwPrice' ? '1' : '0.01'} value={form[key]} onChange={e => setForm({ ...form, [key]: e.target.value })} /><span className="mt-1.5 block text-xs font-normal text-slate-400 dark:text-slate-400">공란: 해당 통화 미제공 · 0: 무료</span></OptionField>)}
                {([['saleStartsAt', '신청 시작'], ['saleEndsAt', '신청 마감'], ['changeEndsAt', '변경·취소 마감']] as const).map(([key, label]) => <OptionField key={key} label={`${label} (한국 시간)`}><input className={editorInput} type="datetime-local" value={form[key]?.slice(0, 16) ?? ''} onChange={e => setForm({ ...form, [key]: e.target.value || null })} /></OptionField>)}
                <OptionField label="정원"><input className={editorInput} type="number" min="0" max="2147483647" step="1" placeholder="공란이면 무제한" value={form.capacity} onChange={e => setForm({ ...form, capacity: e.target.value })} /></OptionField>
                <OptionField label="1인당 최대 수량" required><input className={editorInput} type="number" required min="1" max="2147483647" step="1" value={form.maxPerPerson} onChange={e => setForm({ ...form, maxPerPerson: Number(e.target.value) })} /></OptionField>
                <OptionField label="표시 순서" required><input className={editorInput} type="number" required min="0" max="2147483647" step="1" value={form.sortOrder} onChange={e => setForm({ ...form, sortOrder: Number(e.target.value) })} /></OptionField>
            </fieldset>
            <div className="mt-5 flex justify-end gap-2 border-t border-slate-200 pt-5 dark:border-slate-800"><button type="button" disabled={saving} className={editorCancel} onClick={() => setForm(null)}>취소</button><button type="submit" disabled={saving} className={blue}><Save className="h-4 w-4" />{saving ? '저장 중...' : '저장'}</button></div>
        </form>}
    </section>;
};
