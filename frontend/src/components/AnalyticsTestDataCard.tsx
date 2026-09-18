import { useEffect, useRef, useState } from 'react';
import { Globe2, LoaderCircle } from 'lucide-react';
import { useConfirm } from './confirmDialogContext';
import type { NotificationType } from './NotificationToast';
import { getStoredAdminConferenceSeq } from '../adminSession';

interface Job {
    status: 'IDLE'|'RUNNING'|'COMPLETED'|'FAILED';
    startDate?: string;endDate?:string;completedDays?:number;totalDays?:number;
    phase?:'RESETTING'|'GENERATING';conferenceSeq?:number;
    createdEvents?:number;deletedEvents?:number;deletedFacts?:number;pageViews?:number;message?:string;
    expectedTotals?: {visitors:number;sessions:number;pageViews:number;totalDurationSeconds:number;bouncedSessions:number};
}
export const AnalyticsTestDataCard = ({onNotify,disabled,onBusy,dashboardEnabled}:{
    dashboardEnabled?:boolean;
    onNotify:(type:NotificationType,message:string)=>void;disabled:boolean;onBusy:(busy:boolean)=>void
}) => {
    const confirm=useConfirm();
    const [job,setJob]=useState<Job>({status:'IDLE'});
    const [starting,setStarting]=useState(false);
    const [version,setVersion]=useState(0);
    const callbacks=useRef({onNotify,onBusy});
    useEffect(()=>{callbacks.current={onNotify,onBusy};},[onNotify,onBusy]);
    useEffect(()=>{
        let timer:ReturnType<typeof setTimeout>;
        let previous='IDLE';
        const controller=new AbortController();
        const poll=async()=>{
            try {
                const response=await fetch('/api/admin/testdata/analytics',{signal:controller.signal});
                if(!response.ok)throw new Error(await response.text() || '생성 상태 조회에 실패했습니다.');
                const next=await response.json() as Job;
                if(controller.signal.aborted)return;
                setJob(next);callbacks.current.onBusy(next.status==='RUNNING');
                if(previous==='RUNNING' && next.status==='COMPLETED')callbacks.current.onNotify('success','60일 접속 데이터 생성 및 일별 집계를 완료했습니다.');
                if(next.status==='FAILED')callbacks.current.onNotify('error',next.message || '생성에 실패했습니다.');
                previous=next.status;
                if(next.status==='RUNNING')timer=setTimeout(()=>void poll(),2000);
            } catch(error) {
                if(!controller.signal.aborted) {callbacks.current.onNotify('error',error instanceof Error?error.message:'상태 확인에 실패했습니다.');timer=setTimeout(()=>void poll(),5000);}
            }
        };
        void poll();
        return()=>{controller.abort();clearTimeout(timer);};
    },[version]);
    const start=async()=>{
        if(disabled || starting || job.status==='RUNNING')return;
        const conferenceSeq=getStoredAdminConferenceSeq();
        if(!conferenceSeq){onNotify('error','관리할 행사를 선택해 주세요.');return;}
        const approved=await confirm({title:'기존 접속 데이터 삭제 후 재생성',message:`선택한 행사 #${conferenceSeq}의 기존 접속 데이터 전체(실제 접속·테스트 이벤트·일별 집계)를 삭제합니다. 삭제한 데이터는 복구할 수 없습니다. 오늘 포함 최근 60일 동안 하루 1,000~2,000 페이지뷰, 총 6만~12만 페이지뷰를 새로 생성합니다. 체류시간 이벤트를 포함한 원본 이벤트 수는 이보다 많습니다. 다른 행사의 데이터는 유지합니다.`,confirmText:'전체 삭제 후 60일 데이터 생성',tone:'danger'});
        if(!approved)return;
        if(getStoredAdminConferenceSeq()!==conferenceSeq){onNotify('error','선택 행사가 변경되었습니다. 삭제 대상을 다시 확인해 주세요.');return;}
        setStarting(true);onBusy(true);
        try {
            const response=await fetch(`/api/admin/testdata/analytics?confirmedConferenceSeq=${conferenceSeq}`,{method:'POST'});
            if(!response.ok)throw new Error(await response.text() || '생성을 시작하지 못했습니다.');
            setJob(await response.json() as Job);setVersion(v=>v+1);
        }catch(error){onNotify('error',error instanceof Error?error.message:'생성에 실패했습니다.');onBusy(false);}
        finally{setStarting(false);}
    };
    const number=(value=0)=>value.toLocaleString('ko-KR');
    return <section className="rounded-xl border border-cyan-200 bg-white p-5 shadow-sm dark:border-cyan-900 dark:bg-slate-950">
        <h2 className="flex items-center gap-2 text-sm font-semibold text-slate-900 dark:text-slate-50"><Globe2 className="h-5 w-5 text-cyan-600 dark:text-cyan-400" />사용자 접속 분석 테스트 데이터</h2>
        <p className="mt-2 text-xs leading-6 text-slate-500 dark:text-slate-400">오늘 포함 최근 60일 · 하루 1,000~2,000 페이지뷰(총 6만~12만) · 기기·브라우저·OS·국가·유입경로·시간대·인기 페이지·체류시간 포함</p>
        <p className="mt-1 text-xs leading-6 text-rose-600 dark:text-rose-400">선택 행사의 기존 접속 데이터와 집계를 모두 삭제한 뒤 생성합니다. 실제 접속 기록도 삭제되며 다른 행사의 데이터는 유지됩니다.</p>
        <button type="button" onClick={()=>void start()} disabled={disabled || starting || job.status==='RUNNING'} className="mt-4 inline-flex items-center gap-2 rounded-lg bg-emerald-600 px-3 py-2 text-xs font-semibold text-white hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-emerald-600 dark:hover:bg-emerald-700">{(starting || job.status==='RUNNING') && <LoaderCircle className="h-4 w-4 animate-spin" />}{job.status==='RUNNING' ? '접속 데이터 생성 중' : '60일 접속 데이터 생성'}</button>
        {job.status!=='IDLE' && <div className="mt-4 space-y-2 text-xs text-slate-600 dark:text-slate-300" role="status">
            <p>{job.startDate} ~ {job.endDate} · {job.status==='COMPLETED'?'완료':job.status==='FAILED'?'실패':job.phase==='RESETTING'?'기존 데이터 삭제 중':'생성 중'} · {job.completedDays || 0}/60일</p>
            <progress aria-label="접속 데이터 생성 진행률" className="h-2 w-full accent-cyan-600" max={60} value={job.completedDays || 0} />
            <p>삭제한 기존 이벤트 {number(job.deletedEvents)}건 · 삭제한 집계 상세 {number(job.deletedFacts)}건</p>
            <p>페이지뷰 {number(job.pageViews)}건 · 생성 이벤트 {number(job.createdEvents)}건</p>
            {job.expectedTotals && <p>전체 60일 생성 기준: 순 방문자 {number(job.expectedTotals.visitors)}명 / 세션 {number(job.expectedTotals.sessions)}회 / 페이지뷰 {number(job.expectedTotals.pageViews)}회</p>}
            {job.status==='COMPLETED' && <a href={dashboardEnabled ? '/admin/dashboard/user-analytics' : '/admin/dashboard/user-analytics-details'} className="inline-block text-blue-600 underline dark:text-blue-400">{dashboardEnabled ? '사용자 접속 현황판에서 확인' : '사용자 접속 통계에서 확인'}</a>}
        </div>}
    </section>;
};
