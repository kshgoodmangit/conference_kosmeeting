import { useState } from 'react';
import { MapPin, Save } from 'lucide-react';
import { ProgramModalShell } from './ProgramModalShell';
import type { NotificationType } from './NotificationToast';
import type { ProgramDay, ProgramDayRoom, ProgramRoom } from './programTypes';

export interface ProgramDayRoomPayload {
    roomSeq: number;
    tabName: string | null;
    sortOrder: number;
    enabled: boolean;
}

interface ProgramDayRoomsModalProps {
    day: ProgramDay;
    rooms: ProgramRoom[];
    assignments: ProgramDayRoom[];
    onClose: () => void;
    onNotify: (type: NotificationType, message: string) => void;
    onSave: (rooms: ProgramDayRoomPayload[]) => Promise<void>;
}

interface AssignmentState {
    selected: boolean;
    tabName: string;
    sortOrder: string;
    enabled: boolean;
}

export const ProgramDayRoomsModal = ({ day, rooms, assignments, onClose, onNotify, onSave }: ProgramDayRoomsModalProps) => {
    const [values, setValues] = useState<Record<number, AssignmentState>>(() => Object.fromEntries(
        rooms.map((room, index) => {
            const assignment = assignments.find((item) => item.roomSeq === room.seq);
            return [room.seq, {
                selected: Boolean(assignment),
                tabName: assignment?.tabName ?? '',
                sortOrder: String(assignment?.sortOrder ?? (index + 1) * 10),
                enabled: assignment?.enabled ?? true
            }];
        })
    ));
    const [isSaving, setIsSaving] = useState(false);

    const update = (roomSeq: number, updates: Partial<AssignmentState>) => {
        setValues((previous) => ({
            ...previous,
            [roomSeq]: { ...previous[roomSeq], ...updates }
        }));
    };

    const save = async () => {
        setIsSaving(true);
        try {
            await onSave(rooms
                .filter((room) => values[room.seq]?.selected)
                .map((room) => ({
                    roomSeq: room.seq,
                    tabName: values[room.seq].tabName.trim() || null,
                    sortOrder: Number(values[room.seq].sortOrder),
                    enabled: values[room.seq].enabled
                })));
        } catch (error) {
            onNotify('error', error instanceof Error ? error.message : '일자별 룸 배정을 저장하지 못했습니다.');
        } finally {
            setIsSaving(false);
        }
    };

    return (
        <ProgramModalShell
            title={`DAY ${day.dayNumber} 룸 배정`}
            description="이 일자에 실제로 운영할 룸과 사용자 화면의 탭 이름을 설정합니다."
            onClose={onClose}
            widthClass="max-w-3xl"
        >
            <div className="space-y-4 p-5">
                {rooms.length === 0 && (
                    <div className="rounded-xl border border-dashed border-slate-300 p-8 text-center text-sm text-slate-400 dark:border-slate-700">먼저 프로그램 룸을 등록해주세요.</div>
                )}
                <div className="space-y-3">
                    {rooms.map((room) => {
                        const value = values[room.seq];
                        return (
                            <div key={room.seq} className={`rounded-xl border p-4 ${value.selected ? 'border-blue-300 bg-blue-50/50 dark:border-blue-800 dark:bg-blue-950/20' : 'border-slate-200 dark:border-slate-800'}`}>
                                <label className="flex cursor-pointer items-center gap-3">
                                    <input type="checkbox" checked={value.selected} onChange={(event) => update(room.seq, { selected: event.target.checked })} className="h-4 w-4 rounded text-blue-600" />
                                    <MapPin className="h-4 w-4 text-slate-400" />
                                    <span className="font-semibold">{room.roomName}</span>
                                    <span className="rounded bg-slate-100 px-1.5 py-0.5 text-[10px] font-bold text-slate-500 dark:bg-slate-800">{room.roomCode}</span>
                                    {!room.enabled && <span className="text-xs text-amber-600 dark:text-amber-400">룸 자체가 미사용 상태입니다.</span>}
                                </label>
                                {value.selected && (
                                    <div className="mt-4 grid gap-3 border-t border-blue-100 pt-4 sm:grid-cols-[1fr_120px_120px] dark:border-blue-900/50">
                                        <label>
                                            <span className={labelClass}>탭 표시명</span>
                                            <input value={value.tabName} onChange={(event) => update(room.seq, { tabName: event.target.value })} placeholder={room.roomName} className={inputClass} />
                                        </label>
                                        <label>
                                            <span className={labelClass}>표시 순서</span>
                                            <input type="number" min="0" value={value.sortOrder} onChange={(event) => update(room.seq, { sortOrder: event.target.value })} className={inputClass} />
                                        </label>
                                        <label>
                                            <span className={labelClass}>사용 여부</span>
                                            <span className="flex h-[42px] items-center gap-2 rounded-lg border border-slate-200 px-3 dark:border-slate-700">
                                                <input type="checkbox" checked={value.enabled} onChange={(event) => update(room.seq, { enabled: event.target.checked })} className="h-4 w-4 rounded text-blue-600" />
                                                <span className="text-xs">사용</span>
                                            </span>
                                        </label>
                                    </div>
                                )}
                            </div>
                        );
                    })}
                </div>
                <div className="flex justify-end gap-2 border-t border-slate-200 pt-4 dark:border-slate-800">
                    <button type="button" onClick={onClose} className="rounded-lg border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-600 hover:bg-slate-50 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-900">취소</button>
                    <button type="button" onClick={() => void save()} disabled={isSaving} className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-50">
                        <Save className="h-4 w-4" /> {isSaving ? '저장 중...' : '룸 배정 저장'}
                    </button>
                </div>
            </div>
        </ProgramModalShell>
    );
};

const inputClass = 'w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm text-slate-900 outline-none focus:border-blue-500 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-50';
const labelClass = 'mb-1.5 block text-xs font-semibold text-slate-700 dark:text-slate-300';
