import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import {
    CalendarDays,
    Eye,
    EyeOff,
    FileSpreadsheet,
    FilterX,
    Search,
    Layers3,
    MapPinned,
    Pencil,
    Plus,
    Settings2,
    Trash2,
    Users
} from 'lucide-react';
import type { NotificationType } from './NotificationToast';
import { rowActionButtonClass } from './RowActionMenu';
import { useConfirm } from './confirmDialogContext';
import { ProgramDayModal, type ProgramDayPayload } from './ProgramDayModal';
import { ProgramDayRoomsModal, type ProgramDayRoomPayload } from './ProgramDayRoomsModal';
import { ProgramItemModal, type ProgramItemPayload } from './ProgramItemModal';
import { ProgramBulkImportModal } from './ProgramBulkImportModal';
import { ProgramRoomManagerModal, type ProgramRoomPayload } from './ProgramRoomManagerModal';
import {
    PROGRAM_ITEM_LABELS,
    normalizeTime,
    type ProgramDay,
    type ProgramCountry,
    type ProgramItem,
    type ProgramItemPerson,
    type ProgramManagementData,
    type ProgramPersonRole,
    type ProgramRoom
} from './programTypes';

interface ProgramManagementPageProps {
    onNotify: (type: NotificationType, message: string) => void;
}

type RoomFilter = 'all' | 'common' | number;
const defaultSearch = { keyword: '', itemType: '', enabled: '' };
const searchLabelClass = 'mb-1 block text-[11px] font-semibold text-slate-500 dark:text-slate-400';
const searchInputClass = 'w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-700 dark:bg-slate-950 dark:text-slate-50';

const emptyData: ProgramManagementData = { days: [], rooms: [], dayRooms: [], items: [], people: [], countries: [] };

export const ProgramManagementPage = ({ onNotify }: ProgramManagementPageProps) => {
    const confirm = useConfirm();
    const onNotifyRef = useRef(onNotify);
    const [data, setData] = useState<ProgramManagementData>(emptyData);
    const [selectedDaySeq, setSelectedDaySeq] = useState<number | null>(null);
    const [roomFilter, setRoomFilter] = useState<RoomFilter>('all');
    const [isLoading, setIsLoading] = useState(false);
    const [errorMessage, setErrorMessage] = useState('');
    const [reloadKey, setReloadKey] = useState(0);
    const [dayModal, setDayModal] = useState<{ open: boolean; day: ProgramDay | null }>({ open: false, day: null });
    const [roomManagerOpen, setRoomManagerOpen] = useState(false);
    const [dayRoomsOpen, setDayRoomsOpen] = useState(false);
    const [itemModal, setItemModal] = useState<{ open: boolean; item: ProgramItem | null }>({ open: false, item: null });
    const [bulkImportOpen, setBulkImportOpen] = useState(false);
    const [draftSearch, setDraftSearch] = useState(defaultSearch);
    const [search, setSearch] = useState(defaultSearch);
    const selectFirstResult = useRef(false);

    useEffect(() => {
        onNotifyRef.current = onNotify;
    }, [onNotify]);

    useEffect(() => {
        const controller = new AbortController();
        const load = async () => {
            setIsLoading(true);
            setErrorMessage('');
            try {
                const params = new URLSearchParams();
                Object.entries(search).forEach(([key, value]) => { if (value) params.set(key, value); });
                const response = await fetch(`/api/admin/program?${params}`, { signal: controller.signal });
                if (!response.ok) {
                    throw new Error(await response.text() || '행사 프로그램 정보를 불러오지 못했습니다.');
                }
                const loaded = await response.json() as ProgramManagementData;
                if (controller.signal.aborted) return;
                setData(loaded);
                const firstMatchDay = loaded.days.find((day) => loaded.items.some((item) => item.programDaySeq === day.seq && loaded.matchedItemSeqs?.includes(item.seq)));
                const shouldSelectFirst = selectFirstResult.current;
                selectFirstResult.current = false;
                setSelectedDaySeq((previous) => (
                    shouldSelectFirst ? firstMatchDay?.seq ?? loaded.days[0]?.seq ?? null
                        : previous && loaded.days.some((day) => day.seq === previous)
                        ? previous
                        : loaded.days[0]?.seq ?? null
                ));
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') {
                    return;
                }
                const message = error instanceof Error ? error.message : '행사 프로그램 정보를 불러오지 못했습니다.';
                setErrorMessage(message);
                onNotifyRef.current('error', message);
            } finally {
                if (!controller.signal.aborted) setIsLoading(false);
            }
        };
        void load();
        return () => controller.abort();
    }, [reloadKey, search]);

    const applySearch = (next = draftSearch) => {
        selectFirstResult.current = true;
        setSearch({ ...next, keyword: next.keyword.trim() });
        setRoomFilter('all');
    };
    const summaryValue = (value?: number) => isLoading ? '집계 중...' : errorMessage || value == null ? '-' : `${value}개`;
    const matchedSeqs = useMemo(() => new Set(data.matchedItemSeqs ?? []), [data.matchedItemSeqs]);

    const refresh = () => setReloadKey((value) => value + 1);
    const selectedDay = data.days.find((day) => day.seq === selectedDaySeq) ?? null;
    const selectedDayRooms = useMemo(() => data.dayRooms
        .filter((assignment) => assignment.programDaySeq === selectedDaySeq)
        .sort((left, right) => left.sortOrder - right.sortOrder), [data.dayRooms, selectedDaySeq]);
    const assignedRooms = useMemo(() => selectedDayRooms.map((assignment) => ({
        assignment,
        room: data.rooms.find((room) => room.seq === assignment.roomSeq)
    })).filter((entry): entry is { assignment: typeof selectedDayRooms[number]; room: ProgramRoom } => Boolean(entry.room)), [data.rooms, selectedDayRooms]);
    const dayItems = useMemo(() => data.items.filter((item) => item.programDaySeq === selectedDaySeq), [data.items, selectedDaySeq]);
    const activeRoomFilter: RoomFilter = typeof roomFilter === 'number' && !selectedDayRooms.some((assignment) => assignment.roomSeq === roomFilter)
        ? 'all'
        : roomFilter;
    const filteredItems = useMemo(() => orderProgramItems(dayItems.filter((item) => {
        if (!matchedSeqs.has(item.seq)) return false;
        if (activeRoomFilter === 'all') return true;
        if (activeRoomFilter === 'common') return item.scopeType === 'ALL_ROOMS';
        return item.scopeType === 'ALL_ROOMS' || item.roomSeq === activeRoomFilter;
    })), [activeRoomFilter, dayItems, matchedSeqs]);

    const request = async <T,>(url: string, method: string, body?: unknown): Promise<T> => {
        const response = await fetch(url, {
            method,
            headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
            body: body === undefined ? undefined : JSON.stringify(body)
        });
        if (!response.ok) {
            throw new Error(await response.text() || '요청을 처리하지 못했습니다.');
        }
        if (response.status === 204) {
            return undefined as T;
        }
        const contentType = response.headers.get('content-type') ?? '';
        return contentType.includes('application/json') ? response.json() as Promise<T> : await response.text() as T;
    };

    const saveDay = async (payload: ProgramDayPayload) => {
        const saved = await request<ProgramDay>(
            dayModal.day ? `/api/admin/program/days/${dayModal.day.seq}` : '/api/admin/program/days',
            dayModal.day ? 'PUT' : 'POST',
            payload
        );
        setSelectedDaySeq(saved.seq);
        setDayModal({ open: false, day: null });
        onNotifyRef.current('success', dayModal.day ? '프로그램 일자를 수정했습니다.' : '프로그램 일자를 추가했습니다.');
        refresh();
    };

    const deleteDay = async (day: ProgramDay) => {
        const confirmed = await confirm({
            title: '프로그램 일자 삭제',
            message: `DAY ${day.dayNumber}과 해당 일자의 모든 프로그램을 삭제하시겠습니까?`,
            confirmText: '삭제',
            tone: 'danger'
        });
        if (!confirmed) return;
        try {
            await request<string>(`/api/admin/program/days/${day.seq}`, 'DELETE');
            onNotifyRef.current('success', '프로그램 일자를 삭제했습니다.');
            refresh();
        } catch (error) {
            onNotifyRef.current('error', error instanceof Error ? error.message : '프로그램 일자를 삭제하지 못했습니다.');
        }
    };

    const saveRoom = async (roomSeq: number | null, payload: ProgramRoomPayload) => {
        await request(
            roomSeq ? `/api/admin/program/rooms/${roomSeq}` : '/api/admin/program/rooms',
            roomSeq ? 'PUT' : 'POST',
            payload
        );
        onNotifyRef.current('success', roomSeq ? '프로그램 룸을 수정했습니다.' : '프로그램 룸을 등록했습니다.');
        refresh();
    };

    const deleteRoom = async (room: ProgramRoom) => {
        const confirmed = await confirm({
            title: '프로그램 룸 삭제',
            message: `'${room.roomName}' 룸을 삭제하시겠습니까? 사용 중인 룸은 삭제할 수 없습니다.`,
            confirmText: '삭제',
            tone: 'danger'
        });
        if (!confirmed) return;
        try {
            await request<string>(`/api/admin/program/rooms/${room.seq}`, 'DELETE');
            onNotifyRef.current('success', '프로그램 룸을 삭제했습니다.');
            refresh();
        } catch (error) {
            onNotifyRef.current('error', error instanceof Error ? error.message : '프로그램 룸을 삭제하지 못했습니다.');
        }
    };

    const saveDayRooms = async (rooms: ProgramDayRoomPayload[]) => {
        if (!selectedDay) return;
        await request(`/api/admin/program/days/${selectedDay.seq}/rooms`, 'PUT', { rooms });
        setDayRoomsOpen(false);
        onNotifyRef.current('success', '일자별 룸 배정을 저장했습니다.');
        refresh();
    };

    const saveItem = async (payload: ProgramItemPayload) => {
        await request(
            itemModal.item ? `/api/admin/program/items/${itemModal.item.seq}` : '/api/admin/program/items',
            itemModal.item ? 'PUT' : 'POST',
            payload
        );
        setItemModal({ open: false, item: null });
        onNotifyRef.current('success', itemModal.item ? '프로그램 항목을 수정했습니다.' : '프로그램 항목을 추가했습니다.');
        refresh();
    };

    const deleteItem = async (item: ProgramItem) => {
        const hasChildren = data.items.some((candidate) => candidate.parentSeq === item.seq);
        const confirmed = await confirm({
            title: '프로그램 항목 삭제',
            message: hasChildren
                ? `'${item.title}'과 하위 발표를 모두 삭제하시겠습니까?`
                : `'${item.title}' 항목을 삭제하시겠습니까?`,
            confirmText: '삭제',
            tone: 'danger'
        });
        if (!confirmed) return;
        try {
            await request<string>(`/api/admin/program/items/${item.seq}`, 'DELETE');
            onNotifyRef.current('success', '프로그램 항목을 삭제했습니다.');
            refresh();
        } catch (error) {
            onNotifyRef.current('error', error instanceof Error ? error.message : '프로그램 항목을 삭제하지 못했습니다.');
        }
    };

    return (
        <section className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-950">
            <div className="flex flex-col justify-between gap-4 border-b border-slate-200 p-4 dark:border-slate-800 md:p-5 lg:flex-row lg:items-center">
                <div>
                    <div className="flex items-center gap-2">
                        <CalendarDays className="shrink-0 h-5 w-5 text-blue-500" />
                        <h3 className="text-base font-bold">행사 프로그램 관리</h3>
                    </div>
                    <p className="mt-1 text-xs text-slate-400">일자·룸별 세션, 발표와 공통 일정을 구성합니다.</p>
                </div>
                <div className="flex flex-wrap gap-2">
                    <button type="button" onClick={() => setBulkImportOpen(true)} className={bulkImportButtonClass}>
                        <FileSpreadsheet className="h-4 w-4" /> 엑셀 일괄등록
                    </button>
                    <button type="button" onClick={() => setRoomManagerOpen(true)} className={secondaryButtonClass}>
                        <MapPinned className="h-4 w-4" /> 전체 룸 관리
                    </button>
                    <button type="button" onClick={() => setDayModal({ open: true, day: null })} className={primaryButtonClass}>
                        <Plus className="h-4 w-4" /> 일자 추가
                    </button>
                </div>
            </div>

            <div className="grid grid-cols-1 border-b border-slate-200 sm:grid-cols-3 dark:border-slate-800">
                <Summary label="전체 일자 검색 결과" value={summaryValue(data.matchedCount)} icon={<Layers3 className="h-4 w-4" />} />
                <Summary label="검색 결과 중 사용" value={summaryValue(data.matchedEnabledCount)} icon={<Eye className="h-4 w-4" />} />
                <Summary label="검색 결과 중 미사용" value={summaryValue(data.matchedDisabledCount)} icon={<EyeOff className="h-4 w-4" />} />
            </div>

            <form onSubmit={(event) => { event.preventDefault(); applySearch(); }} className="border-b border-slate-200 bg-slate-50/70 p-4 dark:border-slate-800 dark:bg-slate-900/40 md:p-5">
                <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-4">
                    <label className="md:col-span-2">
                        <span className={searchLabelClass}>검색어</span>
                        <div className="relative">
                            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400 dark:text-slate-400" />
                            <input value={draftSearch.keyword} onChange={(event) => setDraftSearch({ ...draftSearch, keyword: event.target.value })} className={`${searchInputClass} pl-9`} placeholder="프로그램명, 부제목, 담당자, 소속, 비고 검색" />
                        </div>
                    </label>
                    <label><span className={searchLabelClass}>프로그램 유형</span>
                        <select value={draftSearch.itemType} onChange={(event) => {
                            const next = { ...draftSearch, itemType: event.target.value };
                            setDraftSearch(next);
                            applySearch(next);
                        }} className={searchInputClass}>
                            <option value="">전체 유형</option>
                            {Object.entries(PROGRAM_ITEM_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                        </select>
                    </label>
                    <label><span className={searchLabelClass}>사용 여부</span>
                        <select value={draftSearch.enabled} onChange={(event) => {
                            const next = { ...draftSearch, enabled: event.target.value };
                            setDraftSearch(next);
                            applySearch(next);
                        }} className={searchInputClass}>
                            <option value="">전체 상태</option><option value="true">사용</option><option value="false">미사용</option>
                        </select>
                    </label>
                </div>
                <div className="mt-3 flex flex-col justify-end gap-2 sm:flex-row">
                    <button type="button" onClick={() => { setDraftSearch(defaultSearch); applySearch(defaultSearch); }} className="inline-flex items-center justify-center gap-2 rounded-lg border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-white dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-900"><FilterX className="h-4 w-4" /> 초기화</button>
                    <button type="submit" className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-5 py-2 text-xs font-semibold text-white hover:bg-blue-700 dark:bg-blue-600 dark:text-white dark:hover:bg-blue-700"><Search className="h-4 w-4" /> 조회</button>
                </div>
            </form>

            {errorMessage ? <div className="p-8 text-center text-sm text-slate-500 dark:text-slate-400">프로그램 정보를 불러오지 못했습니다. 조회 버튼으로 다시 시도해 주세요.</div> : data.days.length === 0 && !isLoading ? (
                <div className="p-8 text-center md:p-16">
                    <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-blue-50 text-blue-600 dark:bg-blue-950/40 dark:text-blue-400"><CalendarDays className="h-6 w-6" /></div>
                    <h4 className="mt-4 font-bold">등록된 프로그램 일자가 없습니다.</h4>
                    <p className="mt-2 text-sm text-slate-400">룸을 등록하고 첫 번째 행사 일자를 추가해주세요.</p>
                    <button type="button" onClick={() => setDayModal({ open: true, day: null })} className={`${primaryButtonClass} mt-5`}><Plus className="h-4 w-4" /> 첫 일자 추가</button>
                </div>
            ) : (
                <>
                    <div className="overflow-x-auto border-b border-slate-200 bg-slate-50 px-4 pt-3 dark:border-slate-800 dark:bg-slate-900/40 md:px-5">
                        <div className="flex min-w-max gap-2">
                            {data.days.map((day) => (
                                <button
                                    type="button"
                                    key={day.seq}
                                    onClick={() => { setSelectedDaySeq(day.seq); setRoomFilter('all'); }}
                                    className={`min-w-40 rounded-t-xl border border-b-0 px-4 py-3 text-left transition ${selectedDaySeq === day.seq ? 'border-blue-300 bg-white text-blue-700 dark:border-blue-800 dark:bg-slate-950 dark:text-blue-300' : 'border-transparent text-slate-500 hover:bg-white/70 dark:text-slate-400 dark:hover:bg-slate-900'}`}
                                >
                                    <span className="block text-xs font-black">DAY {day.dayNumber} <span className="ml-2 font-normal">{data.items.filter((item) => item.programDaySeq === day.seq && matchedSeqs.has(item.seq)).length}건</span></span>
                                    <span className="mt-0.5 block text-[11px]">{formatDate(day.eventDate)}{day.dayTitle ? ` · ${day.dayTitle}` : ''}</span>
                                </button>
                            ))}
                        </div>
                    </div>

                    {selectedDay && (
                        <div>
                            <div className="flex flex-col gap-4 border-b border-slate-200 p-4 dark:border-slate-800 md:p-5 lg:flex-row lg:items-center lg:justify-between">
                                <div>
                                    <div className="flex flex-wrap items-center gap-2">
                                        <h4 className="text-lg font-black">DAY {selectedDay.dayNumber}</h4>
                                        <span className="text-sm font-semibold text-slate-500 dark:text-slate-400">{formatDate(selectedDay.eventDate)}</span>
                                        <StatusBadge enabled={selectedDay.enabled} />
                                    </div>
                                    <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{selectedDay.dayTitle || '일자 제목 없음'}{selectedDay.theme ? ` · ${selectedDay.theme}` : ''}</p>
                                </div>
                                <div className="flex flex-wrap gap-2">
                                    <button type="button" onClick={() => setDayRoomsOpen(true)} className={secondaryButtonClass}><Settings2 className="h-4 w-4" /> 일자별 룸 배정</button>
                                    <button type="button" onClick={() => setDayModal({ open: true, day: selectedDay })} className={secondaryButtonClass}><Pencil className="h-4 w-4" /> 일자 수정</button>
                                    <button type="button" onClick={() => void deleteDay(selectedDay)} className={dangerButtonClass}><Trash2 className="h-4 w-4" /> 일자 삭제</button>
                                </div>
                            </div>

                            <div className="flex flex-col gap-3 border-b border-slate-200 p-4 dark:border-slate-800 md:flex-row md:items-center md:justify-between md:px-5">
                                <div className="flex flex-wrap gap-2">
                                    <RoomFilterButton active={activeRoomFilter === 'all'} onClick={() => setRoomFilter('all')}>전체</RoomFilterButton>
                                    <RoomFilterButton active={activeRoomFilter === 'common'} onClick={() => setRoomFilter('common')}>전체 룸 공통</RoomFilterButton>
                                    {assignedRooms.map(({ assignment, room }) => (
                                        <RoomFilterButton key={room.seq} active={activeRoomFilter === room.seq} onClick={() => setRoomFilter(room.seq)} disabled={!assignment.enabled}>
                                            {assignment.tabName || room.roomName}
                                        </RoomFilterButton>
                                    ))}
                                </div>
                                <button type="button" onClick={() => setItemModal({ open: true, item: null })} className={primaryButtonClass} disabled={selectedDayRooms.length === 0} title={selectedDayRooms.length === 0 ? '먼저 일자별 룸을 배정해주세요.' : undefined}>
                                    <Plus className="h-4 w-4" /> 프로그램 추가
                                </button>
                            </div>

                            {selectedDayRooms.length === 0 && (
                                <div className="m-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800 dark:border-amber-900/60 dark:bg-amber-950/20 dark:text-amber-300 md:m-5">
                                    이 일자에 배정된 룸이 없습니다. 전체 룸 관리에서 룸을 등록한 뒤 ‘일자별 룸 배정’을 진행해주세요.
                                </div>
                            )}

                            <ProgramTable
                                items={filteredItems}
                                allItems={data.items}
                                rooms={data.rooms}
                                people={data.people}
                                countries={data.countries}
                                onEdit={(item) => setItemModal({ open: true, item })}
                                onDelete={deleteItem}
                                isLoading={isLoading}
                            />
                        </div>
                    )}
                </>
            )}

            {dayModal.open && (
                <ProgramDayModal
                    day={dayModal.day}
                    nextDayNumber={Math.max(0, ...data.days.map((day) => day.dayNumber)) + 1}
                    defaultDate={getNextProgramDate(data.days)}
                    onClose={() => setDayModal({ open: false, day: null })}
                    onNotify={onNotify}
                    onSave={saveDay}
                />
            )}
            {roomManagerOpen && <ProgramRoomManagerModal onNotify={onNotify} rooms={data.rooms} onClose={() => setRoomManagerOpen(false)} onSave={saveRoom} onDelete={deleteRoom} />}
            {dayRoomsOpen && selectedDay && (
                <ProgramDayRoomsModal onNotify={onNotify} day={selectedDay} rooms={data.rooms} assignments={selectedDayRooms} onClose={() => setDayRoomsOpen(false)} onSave={saveDayRooms} />
            )}
            {itemModal.open && selectedDay && (
                <ProgramItemModal
                    day={selectedDay}
                    item={itemModal.item}
                    rooms={data.rooms}
                    dayRooms={selectedDayRooms}
                    items={dayItems}
                    people={data.people}
                    countries={data.countries}
                    preferredRoomSeq={typeof activeRoomFilter === 'number' ? activeRoomFilter : null}
                    onClose={() => setItemModal({ open: false, item: null })}
                    onNotify={onNotify}
                    onSave={saveItem}
                />
            )}
            {bulkImportOpen && (
                <ProgramBulkImportModal
                    onClose={() => setBulkImportOpen(false)}
                    onSuccess={refresh}
                    onNotify={onNotify}
                />
            )}
        </section>
    );
};

const ProgramTable = ({ items, allItems, rooms, people, countries, onEdit, onDelete, isLoading }: {
    items: Array<ProgramItem & { depth: number }>;
    allItems: ProgramItem[];
    rooms: ProgramRoom[];
    people: ProgramItemPerson[];
    countries: ProgramCountry[];
    onEdit: (item: ProgramItem) => void;
    onDelete: (item: ProgramItem) => Promise<void>;
    isLoading: boolean;
}) => (
    <div className="overflow-x-auto">
        <table className="w-full min-w-[1050px] border-collapse text-left text-sm">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs dark:border-slate-800 dark:bg-slate-900/50">
                <tr><th className="p-4">시간</th><th className="p-4">룸</th><th className="p-4">유형</th><th className="p-4">프로그램 / 세션</th><th className="p-4">담당자</th><th className="p-4">비고</th><th className="p-4 text-center">상태</th><th className="p-4 text-center">기능</th></tr>
            </thead>
            <tbody>
                {isLoading && items.length === 0 && <tr><td colSpan={8} className="p-10 text-center text-slate-400">프로그램을 불러오는 중입니다.</td></tr>}
                {!isLoading && items.length === 0 && <tr><td colSpan={8} className="p-10 text-center text-slate-400">선택한 조건에 등록된 프로그램이 없습니다.</td></tr>}
                {items.map((item) => {
                    const room = rooms.find((candidate) => candidate.seq === item.roomSeq);
                    const duration = calculateDuration(item.startTime, item.endTime);
                    const childrenCount = allItems.filter((candidate) => candidate.parentSeq === item.seq).length;
                    return (
                        <tr key={item.seq} className={`border-b border-slate-200 dark:border-slate-800 ${rowStyleClass(item.rowStyle)}`}>
                            <td className="whitespace-nowrap p-4 font-mono text-xs font-semibold">{normalizeTime(item.startTime)}–{normalizeTime(item.endTime)}<span className="mt-1 block font-sans font-normal text-slate-400">{duration}분</span></td>
                            <td className="p-4"><span className="inline-flex rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-600 dark:bg-slate-800 dark:text-slate-300">{item.scopeType === 'ALL_ROOMS' ? '전체 룸 공통' : room?.roomName ?? '-'}</span></td>
                            <td className="p-4"><span className="text-xs font-bold text-blue-600 dark:text-blue-400">{PROGRAM_ITEM_LABELS[item.itemType]}</span></td>
                            <td className="p-4">
                                <div className={item.depth > 0 ? 'pl-6' : ''}>
                                    {item.depth > 0 && <span className="mr-2 text-slate-300 dark:text-slate-600">└</span>}
                                    <span className="font-semibold text-slate-900 dark:text-slate-50">{item.title}</span>
                                    {childrenCount > 0 && <span className="ml-2 rounded bg-blue-50 px-1.5 py-0.5 text-[10px] font-bold text-blue-600 dark:bg-blue-950/40 dark:text-blue-300">발표 {childrenCount}</span>}
                                    {item.subtitle && <span className="mt-1 block text-xs text-slate-500 dark:text-slate-400">{item.subtitle}</span>}
                                    {item.abstractSubmissionSeq && <span className="mt-1 block text-[11px] text-violet-500">연결 초록 #{item.abstractSubmissionSeq}</span>}
                                </div>
                            </td>
                            <td className="p-4 text-xs text-slate-600 dark:text-slate-300">
                                <PersonRoleLines item={item} people={people} countries={countries} />
                            </td>
                            <td className="max-w-56 p-4 text-xs text-slate-500 dark:text-slate-400">{item.notes || '-'}</td>
                            <td className="p-4 text-center"><StatusBadge enabled={item.enabled} /></td>
                            <td className="p-4"><div className="flex justify-center gap-2"><button type="button" onClick={() => onEdit(item)} className={rowActionButtonClass} aria-label={`${item.title} 수정`}><Pencil className="h-4 w-4" /></button><button type="button" onClick={() => void onDelete(item)} className={`${rowActionButtonClass} !text-rose-600 dark:!text-rose-400`} aria-label={`${item.title} 삭제`}><Trash2 className="h-4 w-4" /></button></div></td>
                        </tr>
                    );
                })}
            </tbody>
        </table>
    </div>
);

const Summary = ({ label, value, icon }: { label: string; value: string; icon: ReactNode }) => <div className="border-r border-slate-200 p-4 last:border-r-0 dark:border-slate-800"><div className="flex items-center gap-2 text-xs text-slate-400">{icon}{label}</div><div className="mt-1 text-lg font-black">{value}</div></div>;
const StatusBadge = ({ enabled }: { enabled: boolean }) => <span className={`inline-flex items-center gap-1 rounded-full px-2 py-1 text-[10px] font-bold ${enabled ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300' : 'bg-slate-100 text-slate-500 dark:bg-slate-800 dark:text-slate-400'}`}>{enabled ? <Eye className="h-3 w-3" /> : <EyeOff className="h-3 w-3" />}{enabled ? '사용' : '미사용'}</span>;
const RoomFilterButton = ({ active, disabled, onClick, children }: { active: boolean; disabled?: boolean; onClick: () => void; children: ReactNode }) => <button type="button" disabled={disabled} onClick={onClick} className={`rounded-full border px-3 py-1.5 text-xs font-semibold transition disabled:opacity-40 ${active ? 'border-blue-600 bg-blue-600 text-white' : 'border-slate-200 text-slate-600 hover:border-blue-300 hover:text-blue-600 dark:border-slate-700 dark:text-slate-300'}`}>{children}</button>;
const PersonRoleLines = ({ item, people, countries }: { item: ProgramItem; people: ProgramItemPerson[]; countries: ProgramCountry[] }) => {
    const itemPeople = people.filter((person) => person.programItemSeq === item.seq && person.enabled);
    const roles: Array<{ roleType: ProgramPersonRole; label: string; fallback?: string | null }> = [
        { roleType: 'ORGANIZER', label: 'Organizer', fallback: item.organizerText },
        { roleType: 'SPEAKER', label: '발표', fallback: item.speakerText },
        { roleType: 'CHAIR', label: '좌장', fallback: item.chairText }
    ];
    return (
        <div className="space-y-2">
            {roles.map(({ roleType, label, fallback }) => {
                const rolePeople = itemPeople.filter((person) => person.roleType === roleType).sort((left, right) => left.sortOrder - right.sortOrder);
                if (rolePeople.length === 0 && !fallback) return null;
                return (
                    <div key={roleType} className="flex items-start gap-1.5">
                        {roleType === 'ORGANIZER' && <Users className="mt-0.5 h-3.5 w-3.5 shrink-0 text-slate-400" />}
                        <span className="shrink-0 font-semibold text-slate-400">{label}:</span>
                        <span className="space-y-0.5">
                            {rolePeople.length > 0
                                ? rolePeople.map((person) => <span key={person.seq} className="block">{formatPerson(person, countries)}</span>)
                                : <span className="whitespace-pre-line">{fallback}</span>}
                        </span>
                    </div>
                );
            })}
        </div>
    );
};

const formatPerson = (person: ProgramItemPerson, countries: ProgramCountry[]) => {
    const country = countries.find((candidate) => candidate.seq === person.countrySeq);
    const details = [person.affiliation, country?.countryName].filter(Boolean).join(', ');
    return details ? `${person.personName} (${details})` : person.personName;
};

const orderProgramItems = (items: ProgramItem[]): Array<ProgramItem & { depth: number }> => {
    const compare = (left: ProgramItem, right: ProgramItem) => normalizeTime(left.startTime).localeCompare(normalizeTime(right.startTime)) || left.sortOrder - right.sortOrder || left.seq - right.seq;
    const itemSeqs = new Set(items.map((item) => item.seq));
    const roots = items.filter((item) => item.parentSeq == null || !itemSeqs.has(item.parentSeq)).sort(compare);
    return roots.flatMap((root) => [
        { ...root, depth: 0 },
        ...items.filter((item) => item.parentSeq === root.seq).sort(compare).map((child) => ({ ...child, depth: 1 }))
    ]);
};

const calculateDuration = (start: string, end: string) => {
    const [startHour, startMinute] = normalizeTime(start).split(':').map(Number);
    const [endHour, endMinute] = normalizeTime(end).split(':').map(Number);
    return endHour * 60 + endMinute - startHour * 60 - startMinute;
};

const formatDate = (value: string) => new Intl.DateTimeFormat('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit', weekday: 'short' }).format(new Date(`${value}T00:00:00`));
const getNextProgramDate = (days: ProgramDay[]) => {
    const latestDate = days.map((day) => day.eventDate).sort().at(-1);
    if (!latestDate) return undefined;

    const nextDate = new Date(`${latestDate}T00:00:00`);
    nextDate.setDate(nextDate.getDate() + 1);
    const year = nextDate.getFullYear();
    const month = String(nextDate.getMonth() + 1).padStart(2, '0');
    const day = String(nextDate.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
};
const rowStyleClass = (style: ProgramItem['rowStyle']) => style === 'HIGHLIGHT' ? 'bg-amber-50/80 dark:bg-amber-950/15' : style === 'SECTION' ? 'bg-blue-50/70 dark:bg-blue-950/20' : style === 'MUTED' ? 'bg-slate-50/80 dark:bg-slate-900/40' : 'hover:bg-slate-50/60 dark:hover:bg-slate-900/30';

const primaryButtonClass = 'inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-3 py-2 text-xs font-semibold text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-40';
const bulkImportButtonClass = 'inline-flex items-center justify-center gap-2 rounded-lg border border-violet-200 bg-violet-50 px-3 py-2 text-xs font-semibold text-violet-700 hover:bg-violet-100 disabled:opacity-50 dark:border-violet-900/60 dark:bg-violet-950/30 dark:text-violet-300 dark:hover:bg-violet-950/50';
const secondaryButtonClass = 'inline-flex items-center justify-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-900';
const dangerButtonClass = 'inline-flex items-center justify-center gap-2 rounded-lg border border-rose-200 px-3 py-2 text-xs font-semibold text-rose-600 hover:bg-rose-50 dark:border-rose-900/60 dark:text-rose-400 dark:hover:bg-rose-950/20';
