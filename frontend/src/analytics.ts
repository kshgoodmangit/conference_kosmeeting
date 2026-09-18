import { useEffect, useRef, useState } from 'react';
import type { NotificationType } from './components/NotificationToast';

export interface AnalyticsProps {
    conferenceSeq: number | null;
    onNotify: (type: NotificationType, message: string) => void;
}
export interface Totals {
    visitors: number; sessions: number; pageViews: number; totalDurationSeconds: number; bouncedSessions: number;
}
export interface Dimension {
    dimensionKey: string; visitors: number; sessions: number; pageViews: number; pageTitle?: string; exits?: number;
}
export interface AnalyticsSummary {
    startDate: string; endDate: string; generatedAt: string; timezone: string;
    totals: Totals; previous: Totals;
    trend: {date: string; visitors: number; pageViews: number}[];
    countries: Dimension[]; sources: Dimension[];
    realtime: {visitors: number; change: number; asOf: string; pages: {path: string; title: string; visitors: number}[]};
}
export interface AnalyticsDashboard extends Omit<AnalyticsSummary, 'previous' | 'startDate' | 'endDate' | 'sources'> {
    startDate: string | null; endDate: string | null;
    mapCountries: Dimension[];
    previous: Omit<Totals, 'bouncedSessions'> | null;
    previousConference: {conferenceSeq: number; eventName: string | null} | null;
    sources: {dimensionKey: string; sessions: number; pageViews: number}[];
}
export interface AnalyticsOverview extends AnalyticsSummary {
    devices: Dimension[]; browsers: Dimension[]; operatingSystems: Dimension[];
    mapCountries?: Dimension[];
    hourly: {dayOfWeek: number; hour: number; pageViews: number}[];
    pages: Dimension[];
}
export const ratio = (value: number, total: number) => total > 0 ? value / total : 0;
export const percent = (value: number, total: number) => (ratio(value, total) * 100).toFixed(1);
export const changeLabel = (value: number, previous: number) => previous > 0
    ? `이전 기간 대비 ${value >= previous ? '+' : ''}${((value - previous) / previous * 100).toFixed(1)}%`
    : value > 0 ? '이전 기간 데이터 없음' : '이전 기간 대비 0%';
const countryNames = new Intl.DisplayNames(['ko'], {type: 'region'});
export const countryLabel = (code: string) => /^[A-Z]{2}$/.test(code) ? countryNames.of(code) || code : '알 수 없음';
export const dimensionLabels: Record<string,string> = {
    mobile: '모바일', desktop: '데스크톱', tablet: '태블릿', search: '검색 엔진', direct: '직접 방문',
    referral: '외부 링크', social: '소셜 미디어', UNKNOWN: '알 수 없음', Other: '기타'
};
type AnalyticsResult<T> = {data: T | null; error: boolean; refresh: () => void};
export function useAnalytics(props: AnalyticsProps, startDate: string | undefined, endDate: string | undefined, view: 'dashboard'): AnalyticsResult<AnalyticsDashboard>;
export function useAnalytics(props: AnalyticsProps, startDate?: string, endDate?: string): AnalyticsResult<AnalyticsOverview>;
export function useAnalytics({conferenceSeq, onNotify}: AnalyticsProps, startDate?: string, endDate?: string, view: 'details' | 'dashboard' = 'details'): AnalyticsResult<AnalyticsOverview | AnalyticsDashboard> {
    const [refreshVersion, setRefreshVersion] = useState(0);
    const requestKey = `${conferenceSeq}:${startDate || ''}:${endDate || ''}:${view}`;
    const [result, setResult] = useState<{key: string; data: AnalyticsOverview | AnalyticsDashboard | null; error: boolean}>({key: '', data: null, error: false});
    const notify = useRef(onNotify);
    useEffect(() => { notify.current = onNotify; }, [onNotify]);
    useEffect(() => {
        const controller = new AbortController();
        let timeout: ReturnType<typeof setTimeout>;
        let notified = false;
        const load = async () => {
            if (conferenceSeq === null) return;
            try {
                const params = new URLSearchParams();
                if (startDate) params.set('startDate', startDate);
                if (endDate) params.set('endDate', endDate);
                const endpoint = view === 'dashboard' ? '/api/admin/analytics/dashboard' : '/api/admin/analytics';
                const response = await fetch(`${endpoint}?${params}`, {signal: controller.signal, headers: {'X-Conference-Seq': String(conferenceSeq)}});
                if (!response.ok) throw new Error(await response.text() || '접속 통계 조회에 실패했습니다.');
                const next = await response.json() as AnalyticsOverview | AnalyticsDashboard;
                if (!controller.signal.aborted) { setResult({key: requestKey, data: next, error: false}); notified = false; }
            } catch (failure) {
                if (!controller.signal.aborted) {
                    setResult({key: requestKey, data: null, error: true});
                    if (!notified) notify.current('error', failure instanceof Error ? failure.message : '접속 통계 조회에 실패했습니다.');
                    notified = true;
                }
            } finally { if (!controller.signal.aborted) timeout = setTimeout(() => void load(), 60000); }
        };
        void load();
        return () => { controller.abort(); clearTimeout(timeout); };
    }, [conferenceSeq, startDate, endDate, view, requestKey, refreshVersion]);
    const current = result.key === requestKey ? {data: result.data, error: result.error} : {data: null, error: false};
    return {...current, refresh: () => setRefreshVersion(version => version + 1)};
}
