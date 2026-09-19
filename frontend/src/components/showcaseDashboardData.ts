import { useEffect, useRef, useState } from 'react';
import type { ConferenceSettings } from './ConferenceSettingsModal';
import type { NotificationType } from './NotificationToast';

export interface ShowcaseDashboardData {
    conferenceSeq: number;
    event: ConferenceSettings;
    asOf: string;
    generatedAt: string;
    trendStartDate: string;
    trendEndDate: string;
    memberCount: number;
    todayMemberCount: number;
    todayRegistrationCount: number;
    todayAbstractCount: number;
    registration: { paidCount: number; unpaidCount: number };
    amounts: { currency: string; paid: number; todayPaid: number }[];
    fields: { categorySeq: number; name: string; unassigned: number; reviewing: number; completed: number; overdue: number }[];
    trends: { date: string; registered: number; submitted: number }[];
    countries: { code: string; name: string; value: number }[];
    partners: string[];
    sponsorship: { depositedAmount: number; taxInvoicePendingCount: number };
}

export function useShowcaseDashboard(conferenceSeq: number | null, onNotify: (type: NotificationType, message: string) => void) {
    const [state, setState] = useState<{ data: ShowcaseDashboardData | null; failedFor?: number }>({ data: null });
    const [attempt, setAttempt] = useState(0);
    const notify = useRef(onNotify);
    useEffect(() => { notify.current = onNotify; }, [onNotify]);
    useEffect(() => {
        if (conferenceSeq === null) return;
        const controller = new AbortController();
        const load = async () => {
            try {
                const response = await fetch('/api/admin/dashboard/board', {
                    signal: controller.signal, headers: { 'X-Conference-Seq': String(conferenceSeq) }
                });
                if (!response.ok) throw new Error('행사 현황판을 불러오지 못했습니다. 로그인 상태와 서버 연결을 확인해 주세요.');
                const data = await response.json() as ShowcaseDashboardData;
                if (data.conferenceSeq !== conferenceSeq) throw new Error('선택한 학회와 조회 결과가 다릅니다. 다시 조회해 주세요.');
                if (!controller.signal.aborted) setState({ data });
            } catch (error) {
                if (controller.signal.aborted) return;
                setState({ data: null, failedFor: conferenceSeq });
                notify.current('error', error instanceof Error ? error.message : '행사 현황판 조회에 실패했습니다.');
            }
        };
        void load();
        return () => controller.abort();
    }, [conferenceSeq, attempt]);
    return {
        data: state.data?.conferenceSeq === conferenceSeq ? state.data : null,
        failed: state.failedFor !== undefined && state.failedFor === conferenceSeq,
        retry: () => { setState({ data: null }); setAttempt(value => value + 1); }
    };
}

export function milestoneLabel(date: string | null | undefined, asOf: string) {
    if (!date) return '미설정';
    const days = Math.round((Date.parse(date) - Date.parse(asOf)) / 86_400_000);
    return days > 0 ? `D-${days}` : days === 0 ? 'D-Day' : '마감';
}
