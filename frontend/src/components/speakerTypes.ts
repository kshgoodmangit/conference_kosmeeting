export interface Speaker {
    seq: number;
    speakerTypeCode: number;
    speakerTypeName: string;
    displayName: string;
    displayNameKo: string | null;
    affiliation: string;
    department: string | null;
    positionTitle: string | null;
    countryCode: string | null;
    countryName: string | null;
    biography: string | null;
    profileImageOriFilename: string | null;
    profileImageUrl: string | null;
    homepageUrl: string | null;
    contactEmail: string | null;
    featured: boolean;
    enabled: boolean;
    sortOrder: number;
    createdAt: string;
    updatedAt: string;
}

export interface SpeakerType { seq: number; codeName: string }
export interface SpeakerCountry { isoAlpha2: string; countryName: string; countryNameKo?: string | null }
export interface SpeakerPageData { items: Speaker[]; page: number; size: number; totalCount: number; totalPages: number; enabledCount: number; featuredCount: number }

export const speakerInputClass = 'w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm font-normal text-slate-900 placeholder:text-slate-400 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50';
export const speakerButtonClass = 'inline-flex items-center justify-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900';
