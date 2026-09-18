export const MAIL_SOURCE_MENUS = {
    'pre-registrations': '사전등록관리', abstracts: '초록 접수 내역',
    'oral-accepted-abstracts': '구두발표 대상', 'poster-accepted-abstracts': '포스터발표 대상',
    speakers: '초청연자 관리', sponsorship: '후원 신청 관리'
} as const;
export type MailSourceMenu = keyof typeof MAIL_SOURCE_MENUS;
export const MAIL_HISTORY_STATUS: Record<string, string> = {
    SAVED: '저장됨 · 미발송', EXCLUDED: '제외', QUEUED: '발송 대기', SCHEDULED: '예약',
    SENDING: '전송 중', COMPLETED: '처리 완료', FAILED: '실패', ACCEPTED: '공급자 접수', DELIVERED: '전달 완료'
};
export interface MailHistoryItem {
    seq: number;
    campaignSeq: number;
    sourceMenu: MailSourceMenu;
    subject: string;
    htmlContent?: string;
    adminName: string;
    createdBy: number;
    status: string;
    selectedCount: number;
    duplicateCount: number;
    invalidCount: number;
    suppressionCount: number;
    recipientCount: number;
    excludedCount: number;
    createdAt: string;
}
export interface MailHistoryDetail {
    history: MailHistoryItem;
    recipients: { seq: number; email: string; fullName: string; affiliation: string; status: string; exclusionReason?: string; acceptedAt?: string; deliveredAt?: string; failureReason?: string }[];
    origins: { recipientSeq: number; sourceType: string; sourceSeq: number; sourceLabel: string }[];
    attachments: { seq: number; originalFilename: string; fileSize: number }[];
}

export interface MailEmailSummary {
    totalCount: number;
    historyCount: number;
    savedCount: number;
    excludedCount: number;
}
export interface MailEmailItem {
    recipientSeq: number;
    email: string;
    fullName: string;
    historyCount: number;
    savedCount: number;
    excludedCount: number;
    lastCreatedAt: string;
}
export interface MailEmailHistoryItem {
    seq: number;
    recipientSeq: number;
    sourceMenu: MailSourceMenu;
    subject: string;
    adminName: string;
    email: string;
    fullName: string;
    status: string;
    exclusionReason?: string;
    createdAt: string;
    acceptedAt?: string;
    deliveredAt?: string;
    failureReason?: string;
}
