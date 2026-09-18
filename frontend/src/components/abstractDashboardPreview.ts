// 화면 검토용 예시 데이터입니다. 실제 초록·심사·발표자 정보가 아닙니다.
// 제출 완료 초록만 집계하며 임시저장·철회는 제외합니다.
// 미배정 + 심사 중 + 심사 완료 = 전체 접수. 심사 완료는 필수 심사가 모두 완료된 초록입니다.
// 기한 초과는 심사 중의 부분집합이며, 심사위원별 배정 건수가 아닌 고유 초록 수입니다.
export const ABSTRACT_DASHBOARD_PREVIEW = {
    asOf: '2026-09-08',
    eventName: 'ICMS 2026',
    deadlines: [
        { label: '초록 접수 마감', date: '2026-09-26' },
        { label: '초록 심사 마감', date: '2026-10-25' },
        { label: '행사 개최', date: '2026-11-12' }
    ],
    fields: [
        { name: '임상 연구', unassigned: 18, reviewing: 22, completed: 140, overdue: 5 },
        { name: '디지털 헬스케어', unassigned: 12, reviewing: 16, completed: 104, overdue: 3 },
        { name: '기초 연구', unassigned: 10, reviewing: 12, completed: 86, overdue: 2 },
        { name: '기타', unassigned: 8, reviewing: 6, completed: 52, overdue: 2 }
    ],
    // 결과 분포는 심사 완료 초록을 대상으로 하며, 결과 확정 대기도 포함합니다.
    decisions: { oral: 80, poster: 240, rejected: 30, pending: 32 },
    // 채택된 초록의 발표자 기준. 동일 발표자가 여러 초록을 발표하면 초록별로 집계합니다.
    presenters: [
        { label: '구두발표', registered: 72, unregistered: 8, nav: 'oral-accepted-abstracts' },
        { label: '포스터발표', registered: 204, unregistered: 36, nav: 'poster-accepted-abstracts' }
    ],
    daily: [
        { date: '2026-09-02', submitted: 18 },
        { date: '2026-09-03', submitted: 26 },
        { date: '2026-09-04', submitted: 21 },
        { date: '2026-09-05', submitted: 32 },
        { date: '2026-09-06', submitted: 24 },
        { date: '2026-09-07', submitted: 29 },
        { date: '2026-09-08', submitted: 14 }
    ]
};
