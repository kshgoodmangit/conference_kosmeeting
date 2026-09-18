// 화면 검토용 데이터입니다. 실제 회원·결제 내역이 아닙니다.
// 등록구분별 건수는 취소·환불을 포함하며, 결제율은 취소·환불 제외 기준입니다.
// 미입금에는 결제 시도에 실패한 뒤 아직 결제를 완료하지 않은 등록자가 포함됩니다.
// longUnpaid는 미입금 중 신청 후 7일 이상 경과한 인원이며 별도 상태로 합산하지 않습니다.
export const PRE_REGISTRATION_DASHBOARD_PREVIEW = {
    asOf: '2026-09-08',
    eventName: 'ICMS 2026',
    deadlines: [
        { label: '얼리버드 마감', date: '2026-09-30' },
        { label: '사전등록 마감', date: '2026-10-31' },
        { label: '행사 개최', date: '2026-11-12' }
    ],
    categories: [
        { categorySeq: 1, categoryName: 'PI / Ph.D.', paid: 800, unpaid: 68, longUnpaid: 20, cancelled: 16, refunded: 6 },
        { categorySeq: 2, categoryName: 'Student', paid: 386, unpaid: 56, longUnpaid: 14, cancelled: 12, refunded: 6 }
    ],
    amounts: [
        { currency: 'KRW' as const, paid: 186_300_000, todayPaid: 3_150_000, unpaid: 14_700_000 },
        { currency: 'USD' as const, paid: 38_400, todayPaid: 2_400, unpaid: 4_800 }
    ],
    daily: [
        { date: '2026-09-02', registered: 43, paid: 32 },
        { date: '2026-09-03', registered: 56, paid: 41 },
        { date: '2026-09-04', registered: 41, paid: 35 },
        { date: '2026-09-05', registered: 64, paid: 50 },
        { date: '2026-09-06', registered: 52, paid: 39 },
        { date: '2026-09-07', registered: 61, paid: 48 },
        { date: '2026-09-08', registered: 28, paid: 21 }
    ],
    periods: [
        { label: '얼리버드', count: 860 },
        { label: '일반등록', count: 490 }
    ]
};
