import { useMemo, useState, type FormEvent, type ReactNode } from 'react';
import { ChevronDown, ChevronUp, Clock3, Mic2, Plus, Save, Trash2, UserRoundCog, Users } from 'lucide-react';
import { ProgramModalShell } from './ProgramModalShell';
import type { NotificationType } from './NotificationToast';
import { CountrySelect } from './CountrySelect';
import {
    PROGRAM_ITEM_LABELS,
    PROGRAM_ROW_STYLE_LABELS,
    normalizeTime,
    type ProgramDay,
    type ProgramDayRoom,
    type ProgramCountry,
    type ProgramItem,
    type ProgramItemPerson,
    type ProgramItemType,
    type ProgramPersonRole,
    type ProgramRoom,
    type ProgramRowStyle,
    type ProgramScopeType
} from './programTypes';

export interface ProgramItemPayload {
    programDaySeq: number;
    roomSeq: number | null;
    parentSeq: number | null;
    abstractSubmissionSeq: number | null;
    scopeType: ProgramScopeType;
    itemType: ProgramItemType;
    startTime: string;
    endTime: string;
    title: string;
    subtitle: string | null;
    notes: string | null;
    rowStyle: ProgramRowStyle;
    sortOrder: number;
    enabled: boolean;
    people: ProgramItemPersonPayload[];
}

interface ProgramItemPersonPayload {
    roleType: ProgramPersonRole;
    countrySeq: number | null;
    affiliation: string | null;
    personName: string;
    sortOrder: number;
    enabled: boolean;
}

interface PersonDraft {
    key: number;
    roleType: ProgramPersonRole;
    countrySeq: string;
    affiliation: string;
    personName: string;
}

interface ProgramItemModalProps {
    day: ProgramDay;
    item: ProgramItem | null;
    rooms: ProgramRoom[];
    dayRooms: ProgramDayRoom[];
    items: ProgramItem[];
    people: ProgramItemPerson[];
    countries: ProgramCountry[];
    preferredRoomSeq?: number | null;
    onClose: () => void;
    onNotify: (type: NotificationType, message: string) => void;
    onSave: (payload: ProgramItemPayload) => Promise<void>;
}

export const ProgramItemModal = ({ day, item, rooms, dayRooms, items, people, countries, preferredRoomSeq, onClose, onNotify, onSave }: ProgramItemModalProps) => {
    const assignedRooms = useMemo(() => dayRooms
        .filter((assignment) => assignment.enabled)
        .sort((left, right) => left.sortOrder - right.sortOrder)
        .map((assignment) => ({
            assignment,
            room: rooms.find((room) => room.seq === assignment.roomSeq)
        }))
        .filter((entry): entry is { assignment: ProgramDayRoom; room: ProgramRoom } => Boolean(entry.room)), [dayRooms, rooms]);

    const initialScope = item?.scopeType ?? (preferredRoomSeq ? 'ROOM' : assignedRooms.length > 0 ? 'ROOM' : 'ALL_ROOMS');
    const initialRoomSeq = item?.roomSeq ?? preferredRoomSeq ?? assignedRooms[0]?.room.seq ?? null;
    const [scopeType, setScopeType] = useState<ProgramScopeType>(initialScope);
    const [roomSeq, setRoomSeq] = useState(initialRoomSeq ? String(initialRoomSeq) : '');
    const [parentSeq, setParentSeq] = useState(item?.parentSeq ? String(item.parentSeq) : '');
    const [abstractSubmissionSeq, setAbstractSubmissionSeq] = useState(item?.abstractSubmissionSeq ? String(item.abstractSubmissionSeq) : '');
    const [itemType, setItemType] = useState<ProgramItemType>(item?.itemType ?? 'OTHER');
    const [startTime, setStartTime] = useState(item ? normalizeTime(item.startTime) : '09:00');
    const [endTime, setEndTime] = useState(item ? normalizeTime(item.endTime) : '09:30');
    const [title, setTitle] = useState(item?.title ?? '');
    const [subtitle, setSubtitle] = useState(item?.subtitle ?? '');
    const [personKeySeed, setPersonKeySeed] = useState(() => Math.max(1000, ...people.map((person) => person.seq)));
    const [personDrafts, setPersonDrafts] = useState<PersonDraft[]>(() => createInitialPersonDrafts(item, people));
    const [notes, setNotes] = useState(item?.notes ?? '');
    const [rowStyle, setRowStyle] = useState<ProgramRowStyle>(item?.rowStyle ?? 'DEFAULT');
    const [sortOrder, setSortOrder] = useState(String(item?.sortOrder ?? 0));
    const [enabled, setEnabled] = useState(item?.enabled ?? true);
    const [isSaving, setIsSaving] = useState(false);

    const parentSessions = useMemo(() => items.filter((candidate) => (
        candidate.itemType === 'SESSION'
        && candidate.parentSeq == null
        && candidate.seq !== item?.seq
        && candidate.scopeType === scopeType
        && (scopeType === 'ALL_ROOMS' || candidate.roomSeq === Number(roomSeq))
    )), [item?.seq, items, roomSeq, scopeType]);

    const validParentSeq = parentSessions.some((session) => session.seq === Number(parentSeq)) ? parentSeq : '';

    const addPerson = (roleType: ProgramPersonRole) => {
        const nextKey = personKeySeed + 1;
        setPersonKeySeed(nextKey);
        setPersonDrafts((previous) => [...previous, {
            key: nextKey,
            roleType,
            countrySeq: '',
            affiliation: '',
            personName: ''
        }]);
    };

    const updatePerson = (key: number, updates: Partial<PersonDraft>) => {
        setPersonDrafts((previous) => previous.map((person) => person.key === key ? { ...person, ...updates } : person));
    };

    const removePerson = (key: number) => {
        setPersonDrafts((previous) => previous.filter((person) => person.key !== key));
    };

    const movePerson = (key: number, direction: -1 | 1) => {
        setPersonDrafts((previous) => {
            const currentIndex = previous.findIndex((person) => person.key === key);
            if (currentIndex < 0) return previous;
            const roleIndexes = previous
                .map((person, index) => person.roleType === previous[currentIndex].roleType ? index : -1)
                .filter((index) => index >= 0);
            const roleIndex = roleIndexes.indexOf(currentIndex);
            const targetIndex = roleIndexes[roleIndex + direction];
            if (targetIndex === undefined) return previous;
            const next = [...previous];
            [next[currentIndex], next[targetIndex]] = [next[targetIndex], next[currentIndex]];
            return next;
        });
    };

    const submit = async (event: FormEvent) => {
        event.preventDefault();
        setIsSaving(true);
        try {
            await onSave({
                programDaySeq: day.seq,
                roomSeq: scopeType === 'ROOM' ? Number(roomSeq) : null,
                parentSeq: validParentSeq ? Number(validParentSeq) : null,
                abstractSubmissionSeq: abstractSubmissionSeq ? Number(abstractSubmissionSeq) : null,
                scopeType,
                itemType,
                startTime,
                endTime,
                title: title.trim(),
                subtitle: subtitle.trim() || null,
                notes: notes.trim() || null,
                rowStyle,
                sortOrder: Number(sortOrder),
                enabled,
                people: personDrafts.map((person) => ({
                    roleType: person.roleType,
                    countrySeq: person.countrySeq ? Number(person.countrySeq) : null,
                    affiliation: person.affiliation.trim() || null,
                    personName: person.personName.trim(),
                    sortOrder: personDrafts.filter((candidate) => candidate.roleType === person.roleType).findIndex((candidate) => candidate.key === person.key) * 10,
                    enabled: true
                }))
            });
        } catch (error) {
            onNotify('error', error instanceof Error ? error.message : '프로그램 항목을 저장하지 못했습니다.');
        } finally {
            setIsSaving(false);
        }
    };

    return (
        <ProgramModalShell
            title={item ? '프로그램 항목 수정' : `DAY ${day.dayNumber} 프로그램 추가`}
            description="세션, 발표, 휴식 등 사용자 프로그램 표에 표시할 내용을 입력합니다."
            onClose={onClose}
            widthClass="max-w-6xl"
        >
            <form onSubmit={submit} className="space-y-5 p-5">

                <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                    <Field label="적용 범위" required>
                        <select value={scopeType} onChange={(event) => setScopeType(event.target.value as ProgramScopeType)} className={inputClass}>
                            <option value="ROOM">룸별 프로그램</option>
                            <option value="ALL_ROOMS">전체 룸 공통</option>
                        </select>
                    </Field>
                    <Field label="룸" required={scopeType === 'ROOM'}>
                        <select value={roomSeq} onChange={(event) => setRoomSeq(event.target.value)} disabled={scopeType === 'ALL_ROOMS'} required={scopeType === 'ROOM'} className={`${inputClass} disabled:opacity-50`}>
                            <option value="">룸 선택</option>
                            {assignedRooms.map(({ room, assignment }) => <option key={room.seq} value={room.seq}>{assignment.tabName || room.roomName}</option>)}
                        </select>
                    </Field>
                    <Field label="프로그램 유형" required>
                        <select value={itemType} onChange={(event) => setItemType(event.target.value as ProgramItemType)} className={inputClass}>
                            {Object.entries(PROGRAM_ITEM_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                        </select>
                    </Field>
                </div>

                <Field label="상위 세션">
                    <select value={validParentSeq} onChange={(event) => setParentSeq(event.target.value)} className={inputClass}>
                        <option value="">상위 세션 없음</option>
                        {parentSessions.map((session) => <option key={session.seq} value={session.seq}>{normalizeTime(session.startTime)} {session.title}</option>)}
                    </select>
                </Field>

                <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                    <Field label="시작 시간" required>
                        <div className="relative"><Clock3 className={clockIconClass} /><input type="time" required value={startTime} onChange={(event) => setStartTime(event.target.value)} className={`${inputClass} pl-9`} /></div>
                    </Field>
                    <Field label="종료 시간" required>
                        <div className="relative"><Clock3 className={clockIconClass} /><input type="time" required value={endTime} onChange={(event) => setEndTime(event.target.value)} className={`${inputClass} pl-9`} /></div>
                    </Field>
                    <Field label="행 스타일">
                        <select value={rowStyle} onChange={(event) => setRowStyle(event.target.value as ProgramRowStyle)} className={inputClass}>
                            {Object.entries(PROGRAM_ROW_STYLE_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                        </select>
                    </Field>
                    <Field label="동일 시간 표시 순서">
                        <input type="number" min="0" value={sortOrder} onChange={(event) => setSortOrder(event.target.value)} className={inputClass} />
                    </Field>
                </div>

                <Field label="프로그램명 또는 발표 제목" required>
                    <textarea
                        required
                        maxLength={500}
                        rows={3}
                        value={title}
                        onChange={(event) => setTitle(event.target.value)}
                        placeholder="Session 4 — Disease Models"
                        className={`${inputClass} resize-y`}
                    />
                </Field>
                <Field label="부제목">
                    <input maxLength={500} value={subtitle} onChange={(event) => setSubtitle(event.target.value)} className={inputClass} />
                </Field>

                <div className="rounded-xl border border-slate-200 bg-slate-50/60 p-4 dark:border-slate-800 dark:bg-slate-900/30">
                    <div className="mb-4">
                        <h4 className="text-sm font-bold text-slate-900 dark:text-slate-50">프로그램 담당자</h4>
                        <p className="mt-1 text-xs text-slate-400">역할별로 여러 명을 추가할 수 있습니다. 입력 순서대로 사용자 화면에 표시됩니다.</p>
                    </div>
                    <div className="space-y-4">
                        <RolePeopleEditor
                            roleType="ORGANIZER"
                            title="Organizer"
                            description="세션이나 프로그램을 기획한 담당자"
                            icon={<UserRoundCog className="h-4 w-4" />}
                            people={personDrafts}
                            countries={countries}
                            onAdd={addPerson}
                            onUpdate={updatePerson}
                            onRemove={removePerson}
                            onMove={movePerson}
                        />
                        <RolePeopleEditor
                            roleType="SPEAKER"
                            title="발표자"
                            description="강연자, 발표자 또는 패널 참여자"
                            icon={<Mic2 className="h-4 w-4" />}
                            people={personDrafts}
                            countries={countries}
                            onAdd={addPerson}
                            onUpdate={updatePerson}
                            onRemove={removePerson}
                            onMove={movePerson}
                        />
                        <RolePeopleEditor
                            roleType="CHAIR"
                            title="좌장 또는 진행자"
                            description="세션 좌장, 사회자 또는 모더레이터"
                            icon={<Users className="h-4 w-4" />}
                            people={personDrafts}
                            countries={countries}
                            onAdd={addPerson}
                            onUpdate={updatePerson}
                            onRemove={removePerson}
                            onMove={movePerson}
                        />
                    </div>
                </div>

                <div className="grid gap-4 sm:grid-cols-[1fr_220px]">
                    <Field label="비고">
                        <textarea maxLength={1000} value={notes} onChange={(event) => setNotes(event.target.value)} rows={3} placeholder="Foyer, 준비사항 등" className={inputClass} />
                    </Field>
                    <div className="space-y-4">
                        <Field label="연결 초록 제출 ID">
                            <input type="number" min="1" value={abstractSubmissionSeq} onChange={(event) => setAbstractSubmissionSeq(event.target.value)} placeholder="선택 입력" className={inputClass} />
                        </Field>
                        <label className="flex items-center gap-3 rounded-lg border border-slate-200 px-3 py-2.5 dark:border-slate-700">
                            <input type="checkbox" checked={enabled} onChange={(event) => setEnabled(event.target.checked)} className="h-4 w-4 rounded text-blue-600" />
                            <span className="text-sm text-slate-700 dark:text-slate-200">사용하는 항목</span>
                        </label>
                    </div>
                </div>

                <div className="flex justify-end gap-2 border-t border-slate-200 pt-4 dark:border-slate-800">
                    <button type="button" onClick={onClose} className="rounded-lg border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-600 hover:bg-slate-50 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-900">취소</button>
                    <button type="submit" disabled={isSaving || (scopeType === 'ROOM' && !roomSeq)} className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-50">
                        <Save className="h-4 w-4" /> {isSaving ? '저장 중...' : '프로그램 저장'}
                    </button>
                </div>
            </form>
        </ProgramModalShell>
    );
};

const Field = ({ label, required, children }: { label: string; required?: boolean; children: ReactNode }) => (
    <label className="block min-w-0">
        <span className="mb-1.5 block text-xs font-semibold text-slate-700 dark:text-slate-300">{label}{required && <span className="ml-1 text-rose-500">*</span>}</span>
        {children}
    </label>
);

const RolePeopleEditor = ({
    roleType,
    title,
    description,
    icon,
    people,
    countries,
    onAdd,
    onUpdate,
    onRemove,
    onMove
}: {
    roleType: ProgramPersonRole;
    title: string;
    description: string;
    icon: ReactNode;
    people: PersonDraft[];
    countries: ProgramCountry[];
    onAdd: (roleType: ProgramPersonRole) => void;
    onUpdate: (key: number, updates: Partial<PersonDraft>) => void;
    onRemove: (key: number) => void;
    onMove: (key: number, direction: -1 | 1) => void;
}) => {
    const rolePeople = people.filter((person) => person.roleType === roleType);
    return (
        <section className="overflow-hidden rounded-xl border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-950">
            <div className="flex flex-col gap-3 border-b border-slate-200 px-4 py-3 dark:border-slate-800 sm:flex-row sm:items-center sm:justify-between">
                <div className="flex items-start gap-2.5">
                    <span className="mt-0.5 flex h-7 w-7 items-center justify-center rounded-lg bg-blue-50 text-blue-600 dark:bg-blue-950/50 dark:text-blue-300">{icon}</span>
                    <div>
                        <div className="flex items-center gap-2">
                            <h5 className="text-sm font-bold">{title}</h5>
                            <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-bold text-slate-500 dark:bg-slate-800 dark:text-slate-400">{rolePeople.length}명</span>
                        </div>
                        <p className="mt-0.5 text-[11px] text-slate-400">{description}</p>
                    </div>
                </div>
                <button type="button" onClick={() => onAdd(roleType)} className="inline-flex items-center justify-center gap-1.5 rounded-lg border border-blue-200 px-3 py-1.5 text-xs font-semibold text-blue-600 hover:bg-blue-50 dark:border-blue-900 dark:text-blue-300 dark:hover:bg-blue-950/30">
                    <Plus className="h-3.5 w-3.5" /> {title} 추가
                </button>
            </div>
            {rolePeople.length === 0 ? (
                <button type="button" onClick={() => onAdd(roleType)} className="block w-full px-4 py-5 text-center text-xs text-slate-400 hover:bg-slate-50 dark:hover:bg-slate-900/50">
                    등록된 {title}가 없습니다. 클릭하여 추가하세요.
                </button>
            ) : (
                <div className="divide-y divide-slate-100 dark:divide-slate-800">
                    {rolePeople.map((person, index) => (
                        <div key={person.key} className="grid gap-3 p-3 lg:grid-cols-[190px_1fr_1fr_auto] lg:items-end">
                            <label>
                                <span className={personLabelClass}>국가</span>
                                <CountrySelect countries={countries} value={person.countrySeq} valueKey="seq"
                                    onChange={(value) => onUpdate(person.key, { countrySeq: value })}
                                    ariaLabel={`${title} ${index + 1} 국가`} placeholder="국가 미지정" />
                            </label>
                            <label>
                                <span className={personLabelClass}>소속 기관 또는 단체</span>
                                <input maxLength={255} value={person.affiliation} onChange={(event) => onUpdate(person.key, { affiliation: event.target.value })} placeholder="Nagoya University" className={inputClass} />
                            </label>
                            <label>
                                <span className={personLabelClass}>이름 *</span>
                                <input required maxLength={255} value={person.personName} onChange={(event) => onUpdate(person.key, { personName: event.target.value })} placeholder="Shizue Ohsawa" className={inputClass} />
                            </label>
                            <div className="flex justify-end gap-1">
                                <button type="button" onClick={() => onMove(person.key, -1)} disabled={index === 0} className={personIconButtonClass} title="위로 이동"><ChevronUp className="h-4 w-4" /></button>
                                <button type="button" onClick={() => onMove(person.key, 1)} disabled={index === rolePeople.length - 1} className={personIconButtonClass} title="아래로 이동"><ChevronDown className="h-4 w-4" /></button>
                                <button type="button" onClick={() => onRemove(person.key)} className={`${personIconButtonClass} hover:text-rose-600`} title="삭제"><Trash2 className="h-4 w-4" /></button>
                            </div>
                        </div>
                    ))}
                </div>
            )}
        </section>
    );
};

const createInitialPersonDrafts = (item: ProgramItem | null, people: ProgramItemPerson[]): PersonDraft[] => {
    if (!item) return [];
    const savedPeople = people
        .filter((person) => person.programItemSeq === item.seq)
        .sort((left, right) => left.sortOrder - right.sortOrder)
        .map((person, index) => ({
            key: person.seq || index + 1,
            roleType: person.roleType,
            countrySeq: person.countrySeq ? String(person.countrySeq) : '',
            affiliation: person.affiliation ?? '',
            personName: person.personName
        }));
    if (savedPeople.length > 0) return savedPeople;

    const legacyValues: Array<[ProgramPersonRole, string | null | undefined]> = [
        ['ORGANIZER', item.organizerText],
        ['SPEAKER', item.speakerText],
        ['CHAIR', item.chairText]
    ];
    return legacyValues.flatMap(([roleType, value], roleIndex) => value
        ?.split(/\r?\n/)
        .map((entry) => entry.trim())
        .filter(Boolean)
        .map((entry, entryIndex) => ({
            key: roleIndex * 100 + entryIndex + 1,
            roleType,
            countrySeq: '',
            ...parseLegacyPersonText(entry)
        })) ?? []);
};

const parseLegacyPersonText = (value: string): Pick<PersonDraft, 'affiliation' | 'personName'> => {
    const match = value.match(/^(.*?)\s*\(([^()]*)\)\s*$/);
    if (!match) return { affiliation: '', personName: value };

    const left = match[1].trim();
    const right = match[2].trim();
    const institutionPattern = /\b(?:committee|university|institute|hospital|college|academy|society|association|center|centre|laboratory|lab|department|foundation|organization|organizing|school|corp|inc|ltd)\b/i;
    const leftLooksLikeInstitution = institutionPattern.test(left);
    const rightLooksLikeInstitution = institutionPattern.test(right);

    if (leftLooksLikeInstitution && !rightLooksLikeInstitution) {
        return { affiliation: left, personName: right };
    }
    if (rightLooksLikeInstitution && !leftLooksLikeInstitution) {
        return { affiliation: right, personName: left };
    }
    return { affiliation: '', personName: value };
};

const inputClass = 'w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm text-slate-900 outline-none focus:border-blue-500 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-50';
const clockIconClass = 'pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400';
const personLabelClass = 'mb-1 block text-[11px] font-semibold text-slate-500 dark:text-slate-400';
const personIconButtonClass = 'rounded-lg border border-slate-200 p-2 text-slate-400 hover:bg-slate-50 hover:text-blue-600 disabled:cursor-not-allowed disabled:opacity-30 dark:border-slate-700 dark:hover:bg-slate-900';
