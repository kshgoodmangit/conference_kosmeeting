import type { AbstractCategory, AbstractPresentationType } from './abstractTypes';
import type { ProgramItem, ProgramManagementData } from './programTypes';

export interface AbstractProgramCandidate {
    seq: number;
    submissionNo: string | null;
    title: string;
    acceptedPresentationTypeCode: number | null;
    acceptedPresentationTypeName: string | null;
    categoryCode: number;
    categoryName: string | null;
    presenterName: string | null;
    affiliation: string | null;
    presenterCount: number;
    assignmentCount: number;
}

export interface AbstractProgramAssignment {
    programItemSeq: number;
    abstractSubmissionSeq: number;
    submissionNo: string | null;
    status: string | null;
    canRestore: boolean;
}

export interface AbstractProgramData {
    program: ProgramManagementData;
    assignments: AbstractProgramAssignment[];
    presentationTypes: AbstractPresentationType[];
    categories: AbstractCategory[];
    candidates: AbstractProgramCandidate[];
    page: number;
    size: number;
    totalCount: number;
    totalPages: number;
}

export const roomKey = (item: ProgramItem) => `${item.scopeType}:${item.roomSeq ?? 0}`;
export const sessionKey = (item: ProgramItem) => String(item.parentSeq ?? 'none');
export const orderSlots = (items: ProgramItem[]) => [...items].sort((a, b) =>
    a.startTime.localeCompare(b.startTime) || a.sortOrder - b.sortOrder || a.seq - b.seq);

export function isAssignableSlot(item: ProgramItem, program: ProgramManagementData): boolean {
    if (item.itemType !== 'ABSTRACT_PRESENTATION' || !item.enabled
        || !program.days.some(day => day.seq === item.programDaySeq && day.enabled)) return false;
    if (item.scopeType === 'ROOM' && (!program.rooms.some(room => room.seq === item.roomSeq && room.enabled)
        || !program.dayRooms.some(room => room.programDaySeq === item.programDaySeq && room.roomSeq === item.roomSeq && room.enabled))) return false;
    if (item.parentSeq != null) {
        const parent = program.items.find(candidate => candidate.seq === item.parentSeq);
        if (!parent || !parent.enabled || parent.itemType !== 'SESSION' || parent.programDaySeq !== item.programDaySeq
            || parent.roomSeq !== item.roomSeq || parent.scopeType !== item.scopeType
            || item.startTime < parent.startTime || item.endTime > parent.endTime) return false;
    }
    return true;
}

export function nextAvailableSlot(program: ProgramManagementData, current: ProgramItem): ProgramItem | undefined {
    const group = orderSlots(program.items.filter(item => item.itemType === 'ABSTRACT_PRESENTATION'
        && item.programDaySeq === current.programDaySeq && roomKey(item) === roomKey(current)
        && sessionKey(item) === sessionKey(current)));
    const index = group.findIndex(item => item.seq === current.seq);
    return index < 0 ? undefined : group.slice(index + 1).find(item => item.abstractSubmissionSeq == null && isAssignableSlot(item, program));
}
