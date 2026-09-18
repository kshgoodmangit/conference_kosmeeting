export const RECIPIENT_GROUPS = [
    { value: 'ALL_MEMBERS', label: '회원 전체', description: '등록된 회원 전체' },
    { value: 'ALL_REGISTRANTS', label: '사전등록자 전체', description: '취소 제외 · 결제 여부 무관' },
    { value: 'ALL_SUBMITTERS', label: '초록 접수자 전체', description: '접수 회원 기준 · 임시저장 제외' },
    { value: 'ALL_ACCEPTED', label: '초록 채택자 전체', description: '채택 초록을 보유한 접수 회원' }
] as const;
export type RecipientGroup = typeof RECIPIENT_GROUPS[number]['value'];
