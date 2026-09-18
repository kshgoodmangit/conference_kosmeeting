import { useEffect, useRef, useState } from 'react';
import type { NotificationType } from './NotificationToast';

export interface DashboardProps {
    onNavigate: (nav: string) => void;
    onNotify: (type: NotificationType, message: string) => void;
}
interface BaseDashboard {
    asOf: string;
    generatedAt: string;
    eventName: string;
    deadlines: { label: string; date: string | null }[];
}
export interface RegistrationDashboard extends BaseDashboard {
    categories: { categorySeq: number; categoryName: string; paid: number; unpaid: number; longUnpaid: number; cancelled: number; refunded: number }[];
    amounts: { currency: string; paid: number; todayPaid: number; unpaid: number }[];
    daily: { date: string; registered: number; paid: number }[];
    periods: { label: string; count: number }[];
}
export interface AbstractDashboard extends BaseDashboard {
    fields: { categorySeq: number; name: string; unassigned: number; reviewing: number; completed: number; overdue: number }[];
    decisions: { key: string; label: string; value: number }[];
    pendingDecisionCount: number;
    presenters: { presentationTypeSeq: number; label: string; registered: number; unregistered: number }[];
    daily: { date: string; submitted: number }[];
}

export function useOperationalDashboard<T>(path: string, onNotify: DashboardProps['onNotify']) {
    const [state, setState] = useState<{ data: T | null; failed: boolean }>({ data: null, failed: false });
    const [attempt, setAttempt] = useState(0);
    const notifyRef = useRef(onNotify);
    useEffect(() => { notifyRef.current = onNotify; }, [onNotify]);
    useEffect(() => {
        const controller = new AbortController();
        const load = async () => {
            try {
                const response = await fetch(path, { signal: controller.signal });
                if (!response.ok) throw new Error('대시보드 통계를 불러오지 못했습니다. 로그인 상태와 서버 연결을 확인해 주세요.');
                const data = await response.json() as T;
                if (!controller.signal.aborted) setState({ data, failed: false });
            } catch (error) {
                if (controller.signal.aborted) return;
                setState({ data: null, failed: true });
                notifyRef.current('error', error instanceof Error ? error.message : '대시보드 조회에 실패했습니다.');
            }
        };
        void load();
        return () => controller.abort();
    }, [path, attempt]);
    const retry = () => { setState({ data: null, failed: false }); setAttempt(value => value + 1); };
    return { ...state, retry };
}

export function deadlineLabel(date: string | null, asOf: string) {
    if (!date) return '미설정';
    const days = Math.round((Date.parse(date) - Date.parse(asOf)) / 86_400_000);
    return days > 0 ? `D-${days}` : days === 0 ? 'D-Day' : '마감';
}
