import { useState, type FormEvent } from 'react';
import { MapPin, Pencil, Plus, Save, Trash2 } from 'lucide-react';
import { ProgramModalShell } from './ProgramModalShell';
import type { NotificationType } from './NotificationToast';
import type { ProgramRoom } from './programTypes';

export interface ProgramRoomPayload {
    roomCode: string;
    roomName: string;
    location: string | null;
    sortOrder: number;
    enabled: boolean;
}

interface ProgramRoomManagerModalProps {
    rooms: ProgramRoom[];
    onClose: () => void;
    onNotify: (type: NotificationType, message: string) => void;
    onSave: (roomSeq: number | null, payload: ProgramRoomPayload) => Promise<void>;
    onDelete: (room: ProgramRoom) => Promise<void>;
}

export const ProgramRoomManagerModal = ({ rooms, onClose, onNotify, onSave, onDelete }: ProgramRoomManagerModalProps) => {
    const [selectedRoom, setSelectedRoom] = useState<ProgramRoom | null>(null);
    const [formKey, setFormKey] = useState(0);

    const selectRoom = (room: ProgramRoom | null) => {
        setSelectedRoom(room);
        setFormKey((value) => value + 1);
    };

    return (
        <ProgramModalShell
            title="프로그램 룸 관리"
            description="행사에서 사용할 룸을 등록한 뒤 일자별로 배정합니다."
            onClose={onClose}
            widthClass="max-w-5xl"
        >
            <div className="grid min-h-[520px] lg:grid-cols-[1.1fr_0.9fr]">
                <div className="border-b border-slate-200 p-5 dark:border-slate-800 lg:border-b-0 lg:border-r">
                    <div className="mb-3 flex items-center justify-between">
                        <h4 className="text-sm font-bold">등록된 룸</h4>
                        <button type="button" onClick={() => selectRoom(null)} className={primarySmallButtonClass}>
                            <Plus className="h-4 w-4" /> 신규 룸
                        </button>
                    </div>
                    <div className="space-y-2">
                        {rooms.length === 0 && (
                            <div className="rounded-xl border border-dashed border-slate-300 p-8 text-center text-sm text-slate-400 dark:border-slate-700">등록된 룸이 없습니다.</div>
                        )}
                        {rooms.map((room) => (
                            <div key={room.seq} className={`flex items-center gap-3 rounded-xl border p-3 ${selectedRoom?.seq === room.seq ? 'border-blue-400 bg-blue-50/70 dark:border-blue-700 dark:bg-blue-950/20' : 'border-slate-200 dark:border-slate-800'}`}>
                                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-slate-100 text-slate-500 dark:bg-slate-900 dark:text-slate-400">
                                    <MapPin className="h-4 w-4" />
                                </div>
                                <div className="min-w-0 flex-1">
                                    <div className="flex items-center gap-2">
                                        <span className="truncate text-sm font-semibold">{room.roomName}</span>
                                        <span className="rounded bg-slate-100 px-1.5 py-0.5 text-[10px] font-bold text-slate-500 dark:bg-slate-800 dark:text-slate-400">{room.roomCode}</span>
                                        {!room.enabled && <span className="text-[10px] font-semibold text-slate-400">미사용</span>}
                                    </div>
                                    <p className="mt-1 truncate text-xs text-slate-400">{room.location || '위치 설명 없음'} · 순서 {room.sortOrder}</p>
                                </div>
                                <button type="button" onClick={() => selectRoom(room)} className={iconButtonClass} title="수정"><Pencil className="h-4 w-4" /></button>
                                <button type="button" onClick={() => void onDelete(room)} className={`${iconButtonClass} hover:text-rose-600`} title="삭제"><Trash2 className="h-4 w-4" /></button>
                            </div>
                        ))}
                    </div>
                </div>

                <RoomForm
                    key={formKey}
                    onNotify={onNotify}
                    room={selectedRoom}
                    nextSortOrder={(rooms.length + 1) * 10}
                    onSave={async (payload) => {
                        await onSave(selectedRoom?.seq ?? null, payload);
                        selectRoom(null);
                    }}
                />
            </div>
        </ProgramModalShell>
    );
};

const RoomForm = ({ room, nextSortOrder, onNotify, onSave }: { room: ProgramRoom | null; nextSortOrder: number; onNotify: (type: NotificationType, message: string) => void; onSave: (payload: ProgramRoomPayload) => Promise<void> }) => {
    const [roomCode, setRoomCode] = useState(room?.roomCode ?? '');
    const [roomName, setRoomName] = useState(room?.roomName ?? '');
    const [location, setLocation] = useState(room?.location ?? '');
    const [sortOrder, setSortOrder] = useState(String(room?.sortOrder ?? nextSortOrder));
    const [enabled, setEnabled] = useState(room?.enabled ?? true);
    const [isSaving, setIsSaving] = useState(false);

    const submit = async (event: FormEvent) => {
        event.preventDefault();
        setIsSaving(true);
        try {
            await onSave({
                roomCode: roomCode.trim(),
                roomName: roomName.trim(),
                location: location.trim() || null,
                sortOrder: Number(sortOrder),
                enabled
            });
            if (!room) {
                setRoomCode('');
                setRoomName('');
                setLocation('');
                setSortOrder(String(nextSortOrder + 10));
            }
        } catch (error) {
            onNotify('error', error instanceof Error ? error.message : '룸을 저장하지 못했습니다.');
        } finally {
            setIsSaving(false);
        }
    };

    return (
        <form onSubmit={submit} className="space-y-4 p-5">
            <div>
                <h4 className="text-sm font-bold">{room ? '룸 수정' : '신규 룸 등록'}</h4>
                <p className="mt-1 text-xs text-slate-400">룸 코드는 영문, 숫자, 하이픈과 밑줄을 사용할 수 있습니다.</p>
            </div>
            <label className="block">
                <span className={labelClass}>룸 코드 *</span>
                <input required maxLength={50} value={roomCode} onChange={(event) => setRoomCode(event.target.value)} placeholder="MAIN_HALL" className={inputClass} />
            </label>
            <label className="block">
                <span className={labelClass}>룸 이름 *</span>
                <input required maxLength={255} value={roomName} onChange={(event) => setRoomName(event.target.value)} placeholder="Main Hall" className={inputClass} />
            </label>
            <label className="block">
                <span className={labelClass}>위치 또는 설명</span>
                <input maxLength={500} value={location} onChange={(event) => setLocation(event.target.value)} placeholder="컨벤션센터 2층" className={inputClass} />
            </label>
            <label className="block">
                <span className={labelClass}>표시 순서</span>
                <input type="number" min="0" value={sortOrder} onChange={(event) => setSortOrder(event.target.value)} className={inputClass} />
            </label>
            <label className="flex items-center gap-3 rounded-lg border border-slate-200 px-3 py-2.5 dark:border-slate-700">
                <input type="checkbox" checked={enabled} onChange={(event) => setEnabled(event.target.checked)} className="h-4 w-4 rounded text-blue-600" />
                <span className="text-sm text-slate-700 dark:text-slate-200">사용하는 룸</span>
            </label>
            <button type="submit" disabled={isSaving} className="inline-flex w-full items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-50">
                <Save className="h-4 w-4" /> {isSaving ? '저장 중...' : room ? '룸 수정' : '룸 등록'}
            </button>
        </form>
    );
};

const inputClass = 'w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm text-slate-900 outline-none focus:border-blue-500 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-50';
const labelClass = 'mb-1.5 block text-xs font-semibold text-slate-700 dark:text-slate-300';
const iconButtonClass = 'rounded-lg p-2 text-slate-400 hover:bg-white hover:text-blue-600 dark:hover:bg-slate-800';
const primarySmallButtonClass = 'inline-flex items-center gap-1.5 rounded-lg bg-blue-600 px-3 py-2 text-xs font-semibold text-white hover:bg-blue-700';
