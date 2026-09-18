import { useEffect, useRef, useState, type FormEvent } from 'react';
import { ChevronLeft, ChevronRight, FilterX, Mic, Pencil, Plus, Search, Trash2 } from 'lucide-react';
import type { NotificationType } from './NotificationToast';
import { useConfirm } from './confirmDialogContext';
import { SpeakerEditModal } from './SpeakerEditModal';
import { speakerButtonClass, type Speaker, type SpeakerCountry, type SpeakerPageData, type SpeakerType } from './speakerTypes';
import { MailComposeModal } from './MailComposeModal';
import { MailSelectionButton, MailSelectionCheckbox, MailSelectionSummary } from './MailSelectionControls';
import { useMailSelection } from './useMailSelection';

interface SpeakerPageProps { onNotify: (type: NotificationType, message: string) => void }

const searchInputClass = 'w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-700 dark:bg-slate-950 dark:text-slate-50';

export const SpeakerPage = ({ onNotify }: SpeakerPageProps) => {
    const confirm = useConfirm();
    const notifyRef = useRef(onNotify);
    const [data, setData] = useState<SpeakerPageData>({ items: [], page: 1, size: 10, totalCount: 0, totalPages: 1, enabledCount: 0, featuredCount: 0 });
    const [types, setTypes] = useState<SpeakerType[]>([]);
    const [countries, setCountries] = useState<SpeakerCountry[]>([]);
    const [keyword, setKeyword] = useState('');
    const [searchTypeCode, setSearchTypeCode] = useState('');
    const [searchEnabled, setSearchEnabled] = useState('');
    const [filters, setFilters] = useState({ keyword: '', typeCode: '', enabled: '', page: 1 });
    const [reload, setReload] = useState(0);
    const [loading, setLoading] = useState(true);
    const [failed, setFailed] = useState(false);
    const [busy, setBusy] = useState(false);
    const busyRef = useRef(false);
    const [modal, setModal] = useState<{ speaker: Speaker | null } | null>(null);
    const mailDisabled = loading || failed || busy;
    const mail = useMailSelection(data.items, mailDisabled, (speaker) => ({
        id: `speaker-${speaker.seq}`,
        sourceSeq: speaker.seq,
        name: speaker.displayName,
        email: speaker.contactEmail,
        affiliation: speaker.affiliation
    }));
    const { syncSelected } = mail;

    useEffect(() => { notifyRef.current = onNotify; }, [onNotify]);
    useEffect(() => {
        const controller = new AbortController();
        const load = async () => {
            setLoading(true);
            setFailed(false);
            try {
                const params = new URLSearchParams({ page: String(filters.page), size: '10', keyword: filters.keyword });
                if (filters.typeCode) params.set('typeCode', filters.typeCode);
                if (filters.enabled) params.set('enabled', filters.enabled);
                const responses = await Promise.all([
                    fetch(`/api/admin/speakers?${params}`, { signal: controller.signal }),
                    fetch('/api/admin/speakers/types', { signal: controller.signal }),
                    fetch('/api/countries/used', { signal: controller.signal })
                ]);
                for (const response of responses) if (!response.ok) throw new Error(await response.text() || '연자 정보를 불러오지 못했습니다.');
                const [pageData, typeData, countryData] = await Promise.all(responses.map((response) => response.json()));
                if (controller.signal.aborted) return;
                setData(pageData as SpeakerPageData);
                syncSelected((pageData as SpeakerPageData).items);
                setTypes(typeData as SpeakerType[]);
                setCountries(countryData as SpeakerCountry[]);
            } catch (error) {
                if (!controller.signal.aborted) {
                    setFailed(true);
                    notifyRef.current('error', error instanceof Error ? error.message : '연자 정보를 불러오지 못했습니다.');
                }
            } finally {
                if (!controller.signal.aborted) setLoading(false);
            }
        };
        void load();
        return () => controller.abort();
    }, [filters, reload, syncSelected]);

    const edit = async (speaker: Speaker) => {
        if (busyRef.current) return;
        busyRef.current = true; setBusy(true);
        try {
            const response = await fetch(`/api/admin/speakers/${speaker.seq}`);
            if (!response.ok) throw new Error(await response.text() || '연자 정보를 불러오지 못했습니다.');
            setModal({ speaker: await response.json() as Speaker });
        } catch (error) {
            onNotify('error', error instanceof Error ? error.message : '연자 조회에 실패했습니다.');
        } finally { busyRef.current = false; setBusy(false); }
    };

    const remove = async (speaker: Speaker) => {
        if (busyRef.current) return;
        busyRef.current = true; setBusy(true);
        try {
            if (!await confirm({ title: '초청연자 삭제', message: `${speaker.displayName} 연자와 프로필 이미지를 삭제하시겠습니까?`, tone: 'danger', confirmText: '삭제' })) return;
            const response = await fetch(`/api/admin/speakers/${speaker.seq}`, { method: 'DELETE' });
            if (!response.ok) throw new Error(await response.text() || '연자 삭제에 실패했습니다.');
            onNotify('success', '연자를 삭제했습니다.');
            mail.remove(speaker.seq);
            setReload((value) => value + 1);
        } catch (error) {
            onNotify('error', error instanceof Error ? error.message : '연자 삭제에 실패했습니다.');
        } finally { busyRef.current = false; setBusy(false); }
    };

    const search = (event: FormEvent) => {
        event.preventDefault();
        mail.clear();
        setFilters({ keyword: keyword.trim(), typeCode: searchTypeCode, enabled: searchEnabled, page: 1 });
    };
    const applySelectFilter = (typeCode: string, enabled: string) => {
        mail.clear();
        setFilters({ keyword: filters.keyword, typeCode, enabled, page: 1 });
    };
    const resetSearch = () => {
        mail.clear();
        setKeyword('');
        setSearchTypeCode('');
        setSearchEnabled('');
        setFilters({ keyword: '', typeCode: '', enabled: '', page: 1 });
    };
    const summary = [
        { label: '검색 결과', value: data.totalCount, accent: 'text-slate-900 dark:text-slate-50' },
        { label: '공개 연자', value: data.enabledCount, accent: 'text-emerald-600 dark:text-emerald-400' },
        { label: '주요 연자', value: data.featuredCount, accent: 'text-blue-600 dark:text-blue-400' }
    ];

    return (
        <section className="overflow-hidden rounded-xl border border-slate-200 bg-white text-slate-900 shadow-sm dark:border-slate-800 dark:bg-slate-950 dark:text-slate-50">
            <div className="flex flex-col justify-between gap-4 border-b border-slate-200 p-4 dark:border-slate-800 sm:flex-row sm:items-center md:p-5">
                <div>
                    <div className="flex items-center gap-2">
                        <Mic className="shrink-0 h-4 w-4 text-slate-400 dark:text-slate-400" />
                        <h1 className="text-sm font-semibold md:text-base">초청연자 관리</h1>
                    </div>
                    <p className="mt-1 text-xs text-slate-400 dark:text-slate-400">연자 프로필, 공개 여부와 노출 순서를 관리합니다.</p>
                </div>
                <div className="flex flex-col gap-2 sm:flex-row">
                    <MailSelectionButton count={mail.count} disabled={mailDisabled} onClick={mail.open} />
                    <button type="button" className="inline-flex items-center justify-center gap-2 rounded-lg bg-emerald-600 px-3 py-2 text-xs font-semibold text-white hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-emerald-600 dark:text-white dark:hover:bg-emerald-700" disabled={loading || busy || failed} onClick={() => setModal({ speaker: null })}><Plus className="h-4 w-4" />연자 등록</button>
                </div>
            </div>
            <dl aria-label="연자 요약 통계" aria-busy={loading} className="grid grid-cols-1 border-b border-slate-200 dark:border-slate-800 sm:grid-cols-3">
                {summary.map((item) => (
                    <div key={item.label} className="border-b border-slate-200 p-4 last:border-b-0 dark:border-slate-800 sm:border-b-0 sm:border-r sm:last:border-r-0 md:p-5">
                        <dt className="text-xs font-medium text-slate-400 dark:text-slate-400">{item.label}</dt>
                        <dd className={`mt-1 text-xl font-bold ${item.accent}`}>{loading ? '집계 중...' : failed ? '-' : `${item.value.toLocaleString()}명`}</dd>
                    </div>
                ))}
            </dl>
            <form onSubmit={search} aria-label="연자 검색 조건" className="border-b border-slate-200 bg-slate-50/70 p-4 dark:border-slate-800 dark:bg-slate-900/40 md:p-5">
                <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-4">
                    <label className="md:col-span-2">
                        <span className="mb-1 block text-[11px] font-semibold text-slate-500 dark:text-slate-400">검색어</span>
                        <div className="relative">
                            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400 dark:text-slate-400" />
                            <input placeholder="연자명, 소속, 이메일 검색" maxLength={200} value={keyword} onChange={(e) => setKeyword(e.target.value)} className={`${searchInputClass} pl-9`} />
                        </div>
                    </label>
                    <label>
                        <span className="mb-1 block text-[11px] font-semibold text-slate-500 dark:text-slate-400">연자 구분</span>
                        <select value={searchTypeCode} onChange={(e) => { setSearchTypeCode(e.target.value); applySelectFilter(e.target.value, searchEnabled); }} className={searchInputClass}>
                            <option value="">전체 구분</option>
                            {types.map((type) => <option key={type.seq} value={type.seq}>{type.codeName}</option>)}
                        </select>
                    </label>
                    <label>
                        <span className="mb-1 block text-[11px] font-semibold text-slate-500 dark:text-slate-400">공개 여부</span>
                        <select value={searchEnabled} onChange={(e) => { setSearchEnabled(e.target.value); applySelectFilter(searchTypeCode, e.target.value); }} className={searchInputClass}>
                            <option value="">전체 공개 상태</option>
                            <option value="true">공개</option>
                            <option value="false">비공개</option>
                        </select>
                    </label>
                </div>
                <div className="mt-3 flex flex-col justify-end gap-2 sm:flex-row">
                    <button type="button" onClick={resetSearch} className="inline-flex items-center justify-center gap-2 rounded-lg border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-white dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-900">
                        <FilterX className="h-4 w-4" /> 초기화
                    </button>
                    <button type="submit" disabled={loading} className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-5 py-2 text-xs font-semibold text-white hover:bg-blue-700 disabled:opacity-50 dark:bg-blue-600 dark:text-white dark:hover:bg-blue-700">
                        <Search className="h-4 w-4" /> 조회
                    </button>
                </div>
            </form>
            <MailSelectionSummary count={mail.count} onClear={mail.clear} />
            <div className="overflow-x-auto" aria-busy={loading}>
                <table className="w-full min-w-[900px] text-left text-sm">
                    <thead className="bg-slate-50 dark:bg-slate-900"><tr>
                        <th className="w-12 p-4 text-center">
                            <MailSelectionCheckbox checked={mail.allOnPageSelected} indeterminate={mail.someOnPageSelected}
                                disabled={mailDisabled || !data.items.length} label="현재 페이지 메일 수신자 전체 선택"
                                onChange={(checked) => mail.toggle(data.items, checked)} />
                        </th>
                        {['프로필', '구분 / 이름', '소속 / 국가', '연락 이메일', '공개 / 주요', '순서', '기능'].map((label) => <th key={label} className="px-4 py-3">{label}</th>)}
                    </tr></thead>
                    <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                        {loading && data.items.length === 0 && <tr><td colSpan={8} className="p-10 text-center text-slate-500 dark:text-slate-400">불러오는 중...</td></tr>}
                        {!loading && failed && data.items.length === 0 && <tr><td colSpan={8} className="p-10 text-center text-slate-500 dark:text-slate-400">목록을 표시할 수 없습니다. 조회 버튼으로 다시 시도해주세요.</td></tr>}
                        {!loading && !failed && data.items.length === 0 && <tr><td colSpan={8} className="p-10 text-center text-slate-500 dark:text-slate-400">등록된 연자가 없습니다.</td></tr>}
                        {data.items.map((speaker) => (
                            <tr key={speaker.seq} className={mail.selected[speaker.seq] ? 'bg-blue-50/70 hover:bg-blue-50 dark:bg-blue-950/30 dark:hover:bg-blue-950/50' : 'hover:bg-slate-50/60 dark:hover:bg-slate-900/40'}>
                                <td className="p-4 text-center">
                                    <MailSelectionCheckbox checked={!!mail.selected[speaker.seq]} disabled={mailDisabled}
                                        label={`${speaker.displayName} (${speaker.seq}) 메일 수신자 선택`}
                                        onChange={(checked) => mail.toggle([speaker], checked)} />
                                </td>
                                <td className="px-4 py-3">{speaker.profileImageUrl ? <img src={`${speaker.profileImageUrl}?v=${encodeURIComponent(speaker.updatedAt)}`} alt={`${speaker.displayName} 프로필`} className="h-14 w-14 rounded-lg object-cover" /> : <span className="text-slate-400 dark:text-slate-500">없음</span>}</td>
                                <td className="px-4 py-3"><div className="text-xs text-slate-500 dark:text-slate-400">{speaker.speakerTypeName}</div><button type="button" disabled={busy} onClick={() => void edit(speaker)} className="mt-1 text-left font-semibold text-blue-700 hover:underline dark:text-blue-300">{speaker.displayName}</button>{speaker.displayNameKo && <div className="text-xs">{speaker.displayNameKo}</div>}</td>
                                <td className="max-w-64 break-words px-4 py-3">{speaker.affiliation}<div className="mt-1 text-xs text-slate-500 dark:text-slate-400">{speaker.countryName || speaker.countryCode || '-'}</div></td>
                                <td className="max-w-60 break-all px-4 py-3">{speaker.contactEmail || '-'}</td>
                                <td className="px-4 py-3">{speaker.enabled ? '공개' : '비공개'}{speaker.featured && <div className="text-xs font-semibold text-amber-700 dark:text-amber-300">주요 연자</div>}</td>
                                <td className="px-4 py-3">{speaker.sortOrder}</td>
                                <td className="px-4 py-3"><div className="flex gap-2"><button type="button" disabled={busy} className={speakerButtonClass} onClick={() => void edit(speaker)} aria-label={`${speaker.displayName} 수정`}><Pencil className="h-4 w-4" /></button><button type="button" disabled={busy} className={`${speakerButtonClass} !text-rose-600 dark:!text-rose-400`} onClick={() => void remove(speaker)} aria-label={`${speaker.displayName} 삭제`}><Trash2 className="h-4 w-4" /></button></div></td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
            <div className="flex items-center justify-between gap-3 border-t border-slate-200 p-5 text-sm dark:border-slate-800">
                <span>{failed ? '-' : `총 ${data.totalCount.toLocaleString()}명`}</span>
                <div className="flex items-center gap-3"><button type="button" disabled={loading || failed || data.page <= 1} onClick={() => setFilters({ ...filters, page: data.page - 1 })} className={speakerButtonClass} aria-label="이전 페이지"><ChevronLeft className="h-4 w-4" /></button><span>{data.page} / {data.totalPages}</span><button type="button" disabled={loading || failed || data.page >= data.totalPages} onClick={() => setFilters({ ...filters, page: data.page + 1 })} className={speakerButtonClass} aria-label="다음 페이지"><ChevronRight className="h-4 w-4" /></button></div>
            </div>
            <MailComposeModal sourceMenu="speakers" isOpen={mail.recipients !== null} recipients={mail.recipients ?? []} onClose={mail.close} onNotify={onNotify} />
            {modal && <SpeakerEditModal speaker={modal.speaker} types={types} countries={countries} onNotify={onNotify} onClose={() => setModal(null)} onSuccess={(saved) => { syncSelected([saved]); setModal(null); setReload((value) => value + 1); }} />}
        </section>
    );
};
