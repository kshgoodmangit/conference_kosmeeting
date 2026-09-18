import type { ProgramItem, ProgramManagementData } from './programTypes';
import { orderSlots, roomKey, sessionKey } from './abstractProgramTypes.ts';

export interface AbstractProgramTreeNode {
    key: string;
    label: string;
    children?: AbstractProgramTreeNode[];
    slots?: ProgramItem[];
}

export function buildAbstractProgramTree(program: ProgramManagementData): AbstractProgramTreeNode[] {
    const slots = orderSlots(program.items.filter(item => item.itemType === 'ABSTRACT_PRESENTATION'));
    return [...program.days].sort((a, b) => a.eventDate.localeCompare(b.eventDate) || a.sortOrder - b.sortOrder || a.seq - b.seq)
        .filter(day => slots.some(item => item.programDaySeq === day.seq)).map(day => {
            const daySlots = slots.filter(item => item.programDaySeq === day.seq);
            const dayKey = `day:${day.seq}`;
            return { key: dayKey, label: `DAY ${day.dayNumber} · ${day.eventDate}${day.enabled ? '' : ' (미사용)'}`,
                children: [...new Set(daySlots.map(roomKey))].map(room => {
                    const roomSlots = daySlots.filter(item => roomKey(item) === room);
                    const entry = roomSlots[0];
                    const roomInfo = program.rooms.find(candidate => candidate.seq === entry.roomSeq);
                    const key = `${dayKey}/${room}`;
                    return { key, label: entry.scopeType === 'ALL_ROOMS' ? '전체 룸 공통'
                        : `${roomInfo?.roomName ?? '룸 정보 없음'}${roomInfo?.enabled ? '' : ' (미사용)'}`,
                        children: [...new Set(roomSlots.map(sessionKey))].map(session => {
                            const parent = program.items.find(item => String(item.seq) === session);
                            return { key: `${key}/session:${session}`,
                                label: session === 'none' ? '세션 미지정' : `${parent?.title ?? '세션 정보 없음'}${parent?.enabled ? '' : ' (미사용)'}`,
                                slots: roomSlots.filter(item => sessionKey(item) === session) };
                        }) };
                }) };
        });
}
