export interface ProgramDay {
    seq: number;
    eventDate: string;
    dayNumber: number;
    dayTitle?: string | null;
    theme?: string | null;
    sortOrder: number;
    enabled: boolean;
    createdAt?: string | null;
    updatedAt?: string | null;
}

export interface ProgramRoom {
    seq: number;
    roomCode: string;
    roomName: string;
    location?: string | null;
    sortOrder: number;
    enabled: boolean;
    createdAt?: string | null;
    updatedAt?: string | null;
}

export interface ProgramDayRoom {
    seq: number;
    programDaySeq: number;
    roomSeq: number;
    tabName?: string | null;
    sortOrder: number;
    enabled: boolean;
    createdAt?: string | null;
    updatedAt?: string | null;
}

export type ProgramScopeType = 'ROOM' | 'ALL_ROOMS';
export type ProgramItemType = 'SESSION' | 'ABSTRACT_PRESENTATION' | 'PRESENTATION' | 'PLENARY' | 'CEREMONY' | 'BREAK' | 'MEAL' | 'REGISTRATION' | 'SOCIAL' | 'OTHER';
export type ProgramRowStyle = 'DEFAULT' | 'SECTION' | 'HIGHLIGHT' | 'MUTED';
export type ProgramPersonRole = 'ORGANIZER' | 'SPEAKER' | 'CHAIR';

export interface ProgramItem {
    seq: number;
    programDaySeq: number;
    roomSeq?: number | null;
    parentSeq?: number | null;
    abstractSubmissionSeq?: number | null;
    scopeType: ProgramScopeType;
    itemType: ProgramItemType;
    startTime: string;
    endTime: string;
    title: string;
    subtitle?: string | null;
    organizerText?: string | null;
    speakerText?: string | null;
    chairText?: string | null;
    notes?: string | null;
    rowStyle: ProgramRowStyle;
    sortOrder: number;
    enabled: boolean;
    createdAt?: string | null;
    updatedAt?: string | null;
}

export interface ProgramItemPerson {
    seq: number;
    programItemSeq: number;
    roleType: ProgramPersonRole;
    countrySeq?: number | null;
    affiliation?: string | null;
    personName: string;
    sortOrder: number;
    enabled: boolean;
}

export interface ProgramCountry {
    seq: number;
    isoAlpha2: string;
    isoAlpha3: string;
    countryName: string;
    countryNameKo?: string | null;
}

export interface ProgramManagementData {
    days: ProgramDay[];
    rooms: ProgramRoom[];
    dayRooms: ProgramDayRoom[];
    items: ProgramItem[];
    people: ProgramItemPerson[];
    countries: ProgramCountry[];
    matchedItemSeqs?: number[];
    matchedCount?: number;
    matchedEnabledCount?: number;
    matchedDisabledCount?: number;
}

export const PROGRAM_ITEM_LABELS: Record<ProgramItemType, string> = {
    SESSION: '세션',
    PRESENTATION: '발표',
    ABSTRACT_PRESENTATION: '초록 발표',
    PLENARY: '기조·초청강연',
    CEREMONY: '공식행사',
    BREAK: '휴식',
    MEAL: '식사',
    REGISTRATION: '등록',
    SOCIAL: '소셜 프로그램',
    OTHER: '기타'
};

export const PROGRAM_ROW_STYLE_LABELS: Record<ProgramRowStyle, string> = {
    DEFAULT: '기본',
    SECTION: '세션 강조',
    HIGHLIGHT: '주요 일정 강조',
    MUTED: '보조 일정'
};

export const normalizeTime = (value: string) => value.slice(0, 5);
