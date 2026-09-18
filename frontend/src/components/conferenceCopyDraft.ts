import type { ConferenceSettings } from './ConferenceSettingsModal';

export const copyDateRanges = [
    { key: 'event', label: '행사 기간', start: 'eventStartDate', end: 'eventEndDate' },
    { key: 'earlyBird', label: 'Early Bird 등록', start: 'earlyBirdStartDate', end: 'earlyBirdEndDate' },
    { key: 'regular', label: 'Regular 등록', start: 'regularStartDate', end: 'regularEndDate' },
    { key: 'abstract', label: '초록 제출', start: 'abstractStartDate', end: 'abstractEndDate' },
    { key: 'presentation', label: '발표자료 등록', start: 'presentationMaterialStartDate', end: 'presentationMaterialEndDate' }
] as const;

export type CopyDateField = typeof copyDateRanges[number]['start' | 'end'];
export type CopyDraft = Record<CopyDateField, string> & { eventName: string; venueAddress: string; sitePath: string; defaultLanguage: string; supportedLanguages: string[] };
export type CopyScheduleMode = 'relative' | 'manual';

const dayMilliseconds = 86_400_000;

const dateTime = (value?: string | null): number | null => {
    if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return null;
    const timestamp = Date.parse(`${value}T00:00:00Z`);
    return Number.isFinite(timestamp) && new Date(timestamp).toISOString().slice(0, 10) === value
        ? timestamp : null;
};

export const dateOffset = (from?: string | null, to?: string | null): number | null => {
    const start = dateTime(from);
    const end = dateTime(to);
    return start == null || end == null ? null : (end - start) / dayMilliseconds;
};

const shiftDate = (value: string | null | undefined, days: number): string => {
    const timestamp = dateTime(value);
    if (timestamp == null) return '';
    const shifted = new Date(timestamp + days * dayMilliseconds).toISOString();
    return /^\d{4}-/.test(shifted) ? shifted.slice(0, 10) : '';
};

export const createCopyDraft = (source: ConferenceSettings): CopyDraft => ({
    sitePath: '', defaultLanguage: source.defaultLanguage ?? 'en', supportedLanguages: [...(source.supportedLanguages ?? ['ko', 'en'])],
    eventName: source.eventName ? `${source.eventName.slice(0, 250)} (복사)` : '',
    venueAddress: source.venueAddress ?? '',
    eventStartDate: '', eventEndDate: '',
    earlyBirdStartDate: '', earlyBirdEndDate: '',
    regularStartDate: '', regularEndDate: '',
    abstractStartDate: '', abstractEndDate: '',
    presentationMaterialStartDate: '', presentationMaterialEndDate: ''
});

export const shiftCopyDates = (source: ConferenceSettings, newStartDate: string): Record<CopyDateField, string> => {
    const days = dateOffset(source.eventStartDate, newStartDate);
    const dates = Object.fromEntries(copyDateRanges.flatMap(range => [
        [range.start, days == null ? '' : shiftDate(source[range.start], days)],
        [range.end, days == null ? '' : shiftDate(source[range.end], days)]
    ])) as Record<CopyDateField, string>;
    return { ...dates, eventStartDate: newStartDate };
};

export const validateCopyDraft = (draft: CopyDraft): { field: keyof CopyDraft; message: string } | null => {
    if (!draft.eventName.trim()) return { field: 'eventName', message: '새 학회명을 입력해 주세요.' };
    if (draft.eventName.trim().length > 255) return { field: 'eventName', message: '학회명은 255자 이내로 입력해 주세요.' };
    if (!draft.eventStartDate) return { field: 'eventStartDate', message: '새 행사 시작일을 입력해 주세요.' };
    if (!draft.eventEndDate) return { field: 'eventEndDate', message: '새 행사 종료일을 입력해 주세요.' };
    for (const range of copyDateRanges) {
        for (const field of [range.start, range.end]) {
            if (draft[field] && (dateTime(draft[field]) == null || draft[field] < '1000-01-01')) {
                return { field, message: `${range.label}의 날짜를 확인해 주세요.` };
            }
        }
        if (draft[range.start] && draft[range.end] && draft[range.start] > draft[range.end]) {
            return { field: range.end, message: `${range.label} 종료일은 시작일보다 빠를 수 없습니다.` };
        }
    }
    if (draft.earlyBirdEndDate && draft.regularStartDate && draft.regularStartDate <= draft.earlyBirdEndDate) {
        return { field: 'regularStartDate', message: 'Regular 등록 시작일은 Early Bird 등록 종료일보다 늦어야 합니다.' };
    }
    return null;
};

export const formatCopyDate = (value?: string | null) => value ? value.replaceAll('-', '.') : '미정';
export const formatCopyRange = (start?: string | null, end?: string | null) =>
    !start && !end ? '미정' : `${formatCopyDate(start)} – ${formatCopyDate(end)}`;
