import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { CalendarClock, ChevronLeft, ChevronRight, FileText, FilterX, GripVertical, Search } from 'lucide-react';
import type { NotificationType } from './NotificationToast';
import { useConfirm } from './confirmDialogContext';
import { AbstractDetailModal } from './AbstractDetailModal';
import { AbstractProgramTree } from './AbstractProgramTree.tsx';
import type { AbstractSubmissionDetail } from './abstractTypes';
import type { ProgramItem } from './programTypes';
import { normalizeTime } from './programTypes';
import { isAssignableSlot, nextAvailableSlot, orderSlots, type AbstractProgramData } from './abstractProgramTypes';

interface Props {
    onNotify: (type: NotificationType, message: string) => void;
    onOpenProgram: () => void;
}
interface Filters { keyword: string; presentationTypeCode: string; categoryCode: string; assignmentStatus: string }
interface Query extends Filters { page: number }
const defaultFilters: Filters = { keyword: '', presentationTypeCode: '', categoryCode: '', assignmentStatus: 'free' };
const inputClass = 'w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-700 dark:bg-slate-950 dark:text-slate-50';
const labelClass = 'mb-1 block text-[11px] font-semibold text-slate-500 dark:text-slate-400';
const buttonClass = 'inline-flex items-center justify-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50 dark:border-slate-800 dark:bg-slate-950 dark:text-slate-300 dark:hover:bg-slate-900';
const mutedClass = 'text-xs text-slate-500 dark:text-slate-400';

export const AbstractProgramPage = ({ onNotify, onOpenProgram }: Props) => {
    const confirm = useConfirm();
    const notifyRef = useRef(onNotify);
    const [data, setData] = useState<AbstractProgramData | null>(null);
    const [draft, setDraft] = useState<Filters>(defaultFilters);
    const [query, setQuery] = useState<Query>({ ...defaultFilters, page: 1 });
    const [selectedSlotSeq, setSelectedSlotSeq] = useState<number | null>(null);
    const [selectedAbstractSeq, setSelectedAbstractSeq] = useState<number | null>(null);
    const [loading, setLoading] = useState(true);
    const [failed, setFailed] = useState(false);
    const [saving, setSaving] = useState(false);
    const [detail, setDetail] = useState<AbstractSubmissionDetail | null>(null);
    const [detailLoading, setDetailLoading] = useState(false);
    const [draggedAbstractSeq, setDraggedAbstractSeq] = useState<number | null>(null);
    const [showTreeFilters, setShowTreeFilters] = useState(false);
    const draggedAbstractRef = useRef<number | null>(null);
    const busyRef = useRef(false);
    const initialRef = useRef(true);
    const readController = useRef<AbortController | null>(null);
    const detailController = useRef<AbortController | null>(null);
    const alive = useRef(true);

    useEffect(() => { notifyRef.current = onNotify; }, [onNotify]);
    useEffect(() => {
        alive.current = true;
        return () => { alive.current = false; readController.current?.abort(); detailController.current?.abort(); };
    }, []);

    const load = useCallback(async (search: Query, afterAssignment?: ProgramItem) => {
        readController.current?.abort();
        const controller = new AbortController();
        readController.current = controller;
        setLoading(true);
        setFailed(false);
        try {
            const params = new URLSearchParams({ keyword: search.keyword, assignmentStatus: search.assignmentStatus, page: String(search.page), size: '6' });
            if (search.presentationTypeCode) params.set('presentationTypeCode', search.presentationTypeCode);
            if (search.categoryCode) params.set('categoryCode', search.categoryCode);
            const response = await fetch(`/api/admin/abstract-program?${params}`, { signal: controller.signal });
            if (!response.ok) throw new Error(await response.text() || '초록 편성 정보를 불러오지 못했습니다.');
            const loaded = await response.json() as AbstractProgramData;
            if (controller.signal.aborted || !alive.current) return;
            setData(loaded);
            if (afterAssignment) {
                setSelectedSlotSeq(nextAvailableSlot(loaded.program, afterAssignment)?.seq ?? null);
            } else if (initialRef.current) {
                const firstAvailable = loaded.program.days
                    .flatMap(day => orderSlots(loaded.program.items.filter(item => item.programDaySeq === day.seq)))
                    .find(item => item.abstractSubmissionSeq == null && isAssignableSlot(item, loaded.program));
                setSelectedSlotSeq(firstAvailable?.seq ?? null);
            } else {
                setSelectedSlotSeq(previous => loaded.program.items.some(item => item.seq === previous) ? previous : null);
            }
            initialRef.current = false;
            setSelectedAbstractSeq(previous => loaded.candidates.some(item => item.seq === previous && item.assignmentCount === 0) ? previous : null);
        } catch (error) {
            if (controller.signal.aborted || !alive.current) return;
            setFailed(true);
            notifyRef.current('error', error instanceof Error ? error.message : '초록 편성 정보를 불러오지 못했습니다.');
        } finally {
            if (!controller.signal.aborted && alive.current) setLoading(false);
        }
    }, []);

    // A query change starts a cancellable server fetch and retains the displayed rows.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    useEffect(() => { void load(query); return () => readController.current?.abort(); }, [query, load]);

    const selectedAbstract = data?.candidates.find(item => item.seq === selectedAbstractSeq);
    const disabled = saving || loading;
    const roomName = (item: ProgramItem) => item.scopeType === 'ALL_ROOMS' ? '전체 룸 공통'
        : data?.program.rooms.find(room => room.seq === item.roomSeq)?.roomName ?? '룸 정보 없음';
    const location = (item: ProgramItem) => {
        const day = data?.program.days.find(day => day.seq === item.programDaySeq);
        return `${day?.eventDate ?? ''} · ${roomName(item)} · ${normalizeTime(item.startTime)}–${normalizeTime(item.endTime)}`;
    };

    const search = (event: FormEvent) => {
        event.preventDefault();
        if (busyRef.current) return;
        setSelectedAbstractSeq(null);
        setQuery({ ...draft, keyword: draft.keyword.trim(), page: 1 });
    };

    const applySelectFilter = (key: Exclude<keyof Filters, 'keyword'>, value: string) => {
        const next = { ...draft, [key]: value };
        setDraft(next);
        setSelectedAbstractSeq(null);
        setQuery({ ...next, keyword: query.keyword, page: 1 });
    };

    const assign = async (target: ProgramItem, abstractSeq: number) => {
        const candidate = data?.candidates.find(item => item.seq === abstractSeq);
        if (disabled || failed || busyRef.current || !data || !target || !candidate
            || target.abstractSubmissionSeq != null || !isAssignableSlot(target, data.program)
            || candidate.assignmentCount !== 0 || candidate.presenterCount < 1) return;
        busyRef.current = true;
        setSaving(true);
        setSelectedSlotSeq(target.seq);
        setSelectedAbstractSeq(candidate.seq);
        try {
            const response = await fetch(`/api/admin/abstract-program/items/${target.seq}/assignment`, {
                method: 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ abstractSubmissionSeq: candidate.seq })
            });
            if (!response.ok) throw new Error(await response.text() || '초록을 배정하지 못했습니다.');
            if (!alive.current) return;
            setSelectedAbstractSeq(null);
            setSelectedSlotSeq(null);
            notifyRef.current('success', `${candidate.submissionNo || `#${candidate.seq}`} 초록을 배정했습니다.`);
            await load(query, target);
        } catch (error) {
            if (alive.current) notifyRef.current('error', error instanceof Error ? error.message : '초록을 배정하지 못했습니다.');
        } finally {
            busyRef.current = false;
            if (alive.current) setSaving(false);
        }
    };

    const release = async (item: ProgramItem) => {
        if (!data || disabled || busyRef.current || item.abstractSubmissionSeq == null) return;
        const link = data.assignments.find(link => link.programItemSeq === item.seq);
        const restoreOriginal = link?.canRestore ?? false;
        busyRef.current = true;
        setSaving(true);
        try {
            const approved = await confirm({ title: '초록 배정 해제', confirmText: '배정 해제', tone: 'danger',
                message: `${location(item)}\n${link?.submissionNo || `초록 #${item.abstractSubmissionSeq}`}\n\n${restoreOriginal
                    ? '초록 연결을 해제하고 배정 전 제목과 발표자 정보로 복원합니다.'
                    : '배정 전 복원 정보가 없거나 제목·발표자가 변경되었습니다. 현재 제목과 발표자는 유지하고 초록 연결만 해제합니다.'}\n발표 시간은 유지됩니다.` });
            if (!approved || !alive.current) return;
            const response = await fetch(`/api/admin/abstract-program/items/${item.seq}/assignment`, {
                method: 'DELETE', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ abstractSubmissionSeq: item.abstractSubmissionSeq, restoreOriginal })
            });
            if (!response.ok) throw new Error(await response.text() || '초록 배정을 해제하지 못했습니다.');
            if (!alive.current) return;
            setSelectedSlotSeq(item.seq);
            notifyRef.current('success', '초록 배정을 해제했습니다.');
            await load(query);
        } catch (error) {
            if (alive.current) notifyRef.current('error', error instanceof Error ? error.message : '초록 배정을 해제하지 못했습니다.');
        } finally {
            busyRef.current = false;
            if (alive.current) setSaving(false);
        }
    };

    const showDetail = async (seq: number) => {
        detailController.current?.abort();
        const controller = new AbortController();
        detailController.current = controller;
        setDetailLoading(true);
        try {
            const response = await fetch(`/api/admin/abstracts/${seq}`, { signal: controller.signal });
            if (!response.ok) throw new Error(await response.text() || '초록 상세를 불러오지 못했습니다.');
            const result = await response.json() as AbstractSubmissionDetail;
            if (!controller.signal.aborted && alive.current) setDetail(result);
        } catch (error) {
            if (!controller.signal.aborted && alive.current) notifyRef.current('error', error instanceof Error ? error.message : '초록 상세를 불러오지 못했습니다.');
        } finally {
            if (!controller.signal.aborted && alive.current) setDetailLoading(false);
        }
    };

    const candidatePanel = data && (
        <section className="flex min-w-0 flex-col lg:h-[calc(100vh-15rem)] lg:min-h-[34rem]" aria-label="채택 초록 선택">
            <h2 className="flex h-16 shrink-0 items-center border-b border-slate-200 px-4 text-sm font-semibold dark:border-slate-800 md:px-5">1. 채택 초록</h2>
            <form onSubmit={search} className="shrink-0 border-b border-slate-200 bg-slate-50/70 p-4 dark:border-slate-800 dark:bg-slate-900/40 md:p-5">
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                    <label className="sm:col-span-2"><span className={labelClass}>검색어</span><div className="relative"><Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-400 dark:text-slate-400" /><input className={`${inputClass} pl-9`} maxLength={200} value={draft.keyword} disabled={saving} onChange={e => setDraft({ ...draft, keyword: e.target.value })} placeholder="초록번호, 제목, 발표자, 소속" /></div></label>
                    {showTreeFilters && <><label><span className={labelClass}>최종 승인 발표형식</span><select className={inputClass} disabled={saving} value={draft.presentationTypeCode} onChange={e => applySelectFilter('presentationTypeCode', e.target.value)}><option value="">전체</option>{data.presentationTypes.map(type => <option key={type.code} value={type.code}>{type.name}</option>)}</select></label>
                    <label><span className={labelClass}>카테고리</span><select className={inputClass} disabled={saving} value={draft.categoryCode} onChange={e => applySelectFilter('categoryCode', e.target.value)}><option value="">전체</option>{data.categories.map(category => <option key={category.code} value={category.code}>{category.name}</option>)}</select></label>
                    <label className="sm:col-span-2"><span className={labelClass}>배정 상태</span><select className={inputClass} disabled={saving} value={draft.assignmentStatus} onChange={e => applySelectFilter('assignmentStatus', e.target.value)}><option value="free">미배정</option><option value="all">전체</option><option value="assigned">배정 완료</option></select></label></>}
                </div>
                <div className="mt-3 flex flex-col justify-end gap-2 sm:flex-row"><button type="button" className={`${buttonClass} sm:mr-auto`} aria-expanded={showTreeFilters} onClick={() => setShowTreeFilters(value => !value)}>상세 조건 {showTreeFilters ? '접기' : '펼치기'}</button><button type="button" disabled={disabled} className={`${buttonClass} px-4`} onClick={() => { setDraft(defaultFilters); setSelectedAbstractSeq(null); setQuery({ ...defaultFilters, page: 1 }); }}><FilterX className="h-4 w-4" />초기화</button><button type="submit" disabled={disabled} className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-5 py-2 text-xs font-semibold text-white hover:bg-blue-700 disabled:opacity-50 dark:bg-blue-600 dark:text-white dark:hover:bg-blue-700"><Search className="h-4 w-4" />조회</button></div>
                {!showTreeFilters && <p className={`mt-2 ${mutedClass}`}>{[query.assignmentStatus === 'free' ? '미배정 초록' : query.assignmentStatus === 'assigned' ? '배정 완료 초록' : '전체 채택 초록', data.presentationTypes.find(type => String(type.code) === query.presentationTypeCode)?.name, data.categories.find(category => String(category.code) === query.categoryCode)?.name].filter(Boolean).join(' · ')}</p>}
            </form>
            <div className={`flex justify-between border-b border-slate-200 px-4 py-3 dark:border-slate-800 md:px-5 ${mutedClass}`}><span>{failed ? '조회 실패' : loading ? '조회 중...' : `검색 결과 ${data.totalCount.toLocaleString()}편`}</span><span>{data.page} / {data.totalPages} 페이지</span></div>
            <div aria-busy={loading} className="min-h-0 flex-1 overflow-y-auto">{!data.candidates.length && !loading && !failed && <p className={`p-8 text-center ${mutedClass}`}>검색 조건에 맞는 채택 초록이 없습니다.</p>}
                {data.candidates.map(candidate => <div key={candidate.seq}
                    draggable={!disabled && !failed && candidate.assignmentCount === 0 && candidate.presenterCount > 0}
                    onDragStart={event => {
                        if (disabled || failed || busyRef.current || candidate.assignmentCount > 0 || candidate.presenterCount < 1) { event.preventDefault(); return; }
                        event.dataTransfer.effectAllowed = 'move';
                        event.dataTransfer.setData('application/x-conference-abstract', String(candidate.seq));
                        draggedAbstractRef.current = candidate.seq;
                        setDraggedAbstractSeq(candidate.seq);
                        setSelectedAbstractSeq(candidate.seq);
                    }}
                    onDragEnd={() => { draggedAbstractRef.current = null; setDraggedAbstractSeq(null); }}
                    className={`flex items-start gap-3 border-b border-slate-200 p-4 dark:border-slate-800 md:p-5 ${candidate.seq === selectedAbstractSeq ? 'bg-blue-50 dark:bg-blue-950/40' : 'bg-white dark:bg-slate-950'} ${!disabled && !failed && candidate.assignmentCount === 0 && candidate.presenterCount > 0 ? 'cursor-grab active:cursor-grabbing' : ''} ${draggedAbstractSeq === candidate.seq ? 'opacity-60' : ''}`}>
                    <GripVertical aria-hidden="true" className="mt-1 h-4 w-4 shrink-0 text-slate-400 dark:text-slate-500" />
                    <label className="flex min-w-0 flex-1 items-start gap-3"><input type="radio" name="program-abstract" className="mt-1 h-4 w-4 shrink-0 accent-blue-600" checked={candidate.seq === selectedAbstractSeq} disabled={disabled || candidate.assignmentCount > 0 || candidate.presenterCount === 0} onChange={() => setSelectedAbstractSeq(candidate.seq)} aria-label={`${candidate.submissionNo || candidate.seq} ${candidate.title} 선택`} />
                        <span className="min-w-0"><span className={mutedClass}>{candidate.submissionNo || `#${candidate.seq}`} · {candidate.acceptedPresentationTypeName || '승인 발표형식 미지정'}</span><span className="mt-1 block break-words text-sm font-semibold text-slate-900 dark:text-slate-50">{candidate.title}</span><span className={`mt-1 block break-words ${mutedClass}`}>{candidate.presenterName || '발표자 확인 필요'} · {candidate.affiliation || '소속 확인 필요'}</span><span className={`mt-1 block ${mutedClass}`}>{candidate.categoryName}</span>
                            {candidate.assignmentCount > 0 && <span className="mt-2 block text-xs text-emerald-700 dark:text-emerald-300">배정 완료{data.program.items.filter(item => item.abstractSubmissionSeq === candidate.seq).map(item => <span key={item.seq} className="mt-1 block">{location(item)}{!item.enabled ? ' (미사용)' : ''}</span>)}</span>}</span>
                    </label><button type="button" disabled={detailLoading || saving} onClick={() => void showDetail(candidate.seq)} className={buttonClass} aria-label={`${candidate.submissionNo || candidate.seq} 초록 상세`}><FileText className="h-4 w-4" /></button>
                </div>)}
            </div>
            <div className="flex justify-end gap-2 p-4 md:p-5"><button type="button" className={buttonClass} disabled={disabled || failed || data.page <= 1} onClick={() => { setSelectedAbstractSeq(null); setQuery({ ...query, page: data.page - 1 }); }}><ChevronLeft className="h-4 w-4" />이전</button><button type="button" className={buttonClass} disabled={disabled || failed || data.page >= data.totalPages} onClick={() => { setSelectedAbstractSeq(null); setQuery({ ...query, page: data.page + 1 }); }}>다음<ChevronRight className="h-4 w-4" /></button></div>
        </section>
    );

    return <section className="rounded-xl border border-slate-200 bg-white text-slate-900 shadow-sm dark:border-slate-800 dark:bg-slate-950 dark:text-slate-50">
        <header className="flex flex-col justify-between gap-4 border-b border-slate-200 p-4 dark:border-slate-800 sm:flex-row sm:items-center md:p-5">
            <div>
                <div className="flex items-center gap-2">
                    <CalendarClock className="shrink-0 h-4 w-4 text-blue-600 dark:text-blue-400" />
                    <h1 className="text-sm font-semibold text-slate-900 dark:text-slate-50 md:text-base">초록 편성</h1>
                </div>
                <p className="mt-1 text-xs text-slate-400 dark:text-slate-400">왼쪽 초록을 오른쪽 트리의 빈 발표 시간에 놓으면 바로 배정됩니다.</p>
            </div>
            <div className="flex flex-wrap gap-2"><button type="button" disabled={saving} onClick={onOpenProgram} className={buttonClass}>프로그램 편성표</button></div>
        </header>
        {failed && <div className="border-b border-slate-200 p-4 text-sm text-slate-600 dark:border-slate-800 dark:text-slate-300" role="status">최신 정보를 불러오지 못했습니다. 페이지를 다시 열어 확인해주세요.</div>}
        {!data && loading && <div className="p-10 text-center text-sm text-slate-500 dark:text-slate-400" role="status">초록 편성 정보를 불러오는 중입니다.</div>}
        {data && <div className="grid lg:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]">
            {candidatePanel}
            <AbstractProgramTree data={data} disabled={disabled || failed} saving={saving}
                selectedAbstract={selectedAbstract} selectedSlotSeq={selectedSlotSeq} draggedAbstractSeq={draggedAbstractSeq}
                getDraggedAbstractSeq={() => draggedAbstractRef.current}
                onSelectSlot={setSelectedSlotSeq} onRelease={item => void release(item)}
                onDropAbstract={(item, seq) => {
                    if (draggedAbstractRef.current !== seq) return;
                    draggedAbstractRef.current = null;
                    setDraggedAbstractSeq(null);
                    void assign(item, seq);
                }} />
        </div>}
        <AbstractDetailModal isOpen={detail !== null} abstractSubmission={detail} onClose={() => setDetail(null)} />
    </section>;
};
