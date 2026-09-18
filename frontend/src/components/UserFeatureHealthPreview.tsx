import { Activity, ChevronDown, ClipboardList, CreditCard, FileText, LogIn, UserPlus } from 'lucide-react';

const muted = 'text-slate-500 dark:text-slate-400';
const panel = 'min-w-0 rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-950';
const tones = {
    normal: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300',
    warning: 'bg-amber-50 text-amber-800 dark:bg-amber-950/40 dark:text-amber-300',
    urgent: 'bg-rose-50 text-rose-700 dark:bg-rose-950/40 dark:text-rose-300',
};
type Tone = keyof typeof tones;

const Badge = ({ tone, children }: { tone: Tone; children: React.ReactNode }) => (
    <span className={`inline-flex shrink-0 rounded-md px-2 py-1 text-[11px] font-semibold ${tones[tone]}`}>{children}</span>
);

const incidents: {
    id: string;
    tone: Tone;
    severity: string;
    title: string;
    feature: string;
    count: number;
    occurred: string;
    impact: string;
    owner: string;
    action: string;
    progress: string;
    technical: string;
}[] = [
    {
        id: 'INC-0913-03',
        tone: 'urgent',
        severity: '우선 대응',
        title: '초록 첨부파일 저장 실패',
        feature: '초록제출',
        count: 3,
        occurred: '13:42–14:12',
        impact: '해당 요청의 최종 제출이 완료되지 않았을 수 있습니다.',
        owner: '개발·서버 담당자',
        action: '저장소 연결과 쓰기 권한을 확인하고 해당 초록의 파일 및 제출 상태를 대조하세요.',
        progress: '14:18 담당자 확인 시작 · 복구 확인 전',
        technical: 'UPLOAD_WRITE_FAILED · 대표 추적번호 demo-abstract-1412',
    },
    {
        id: 'INC-0913-02',
        tone: 'warning',
        severity: '조사 중',
        title: '결제 승인 결과 반영 지연',
        feature: '결제',
        count: 8,
        occurred: '13:50–14:10',
        impact: '결제한 참가자가 일시적으로 미납으로 표시될 수 있으며 불일치 2건을 확인해야 합니다.',
        owner: '등록·결제 담당자',
        action: 'PG 승인 결과와 등록 내역을 대조하고 중복 결제가 발생하지 않도록 안내하세요.',
        progress: '14:20 승인 내역 대조 중 · 결과 반영 여부 확인 필요',
        technical: 'PAYMENT_RESPONSE_TIMEOUT · 대표 추적번호 demo-payment-1410',
    },
    {
        id: 'INC-0913-01',
        tone: 'warning',
        severity: '조사 중',
        title: '사전등록 신청 저장 실패',
        feature: '사전등록',
        count: 4,
        occurred: '13:55–14:06',
        impact: '신청 완료 화면으로 이동하지 못했을 수 있으며 결제 단계 진입 전 오류입니다.',
        owner: '개발·등록 담당자',
        action: '오류 요청의 저장 여부와 중복 신청 여부를 확인한 뒤 필요한 대상에게 다시 안내하세요.',
        progress: '14:15 조사 시작 · 같은 시간대 정상 신청도 확인됨',
        technical: 'REGISTRATION_SAVE_FAILED · 대표 추적번호 demo-registration-1406',
    },
];

const features: {
    key: string;
    name: string;
    icon: typeof Activity;
    status: string;
    tone: Tone;
    success: number;
    rejected: number;
    systemErrors: number;
    lastSuccess: string;
    step: string;
    result: string;
}[] = [
    { key: 'login', name: '로그인', icon: LogIn, status: '정상', tone: 'normal', success: 310, rejected: 12, systemErrors: 2, lastSuccess: '14:29', step: '인증 및 세션 생성', result: '13:40 이후 동일 오류 없음' },
    { key: 'signup', name: '회원가입', icon: UserPlus, status: '정상', tone: 'normal', success: 42, rejected: 4, systemErrors: 1, lastSuccess: '14:27', step: '회원 정보 저장', result: '재점검 통과 · 정상 저장 확인' },
    { key: 'registration', name: '사전등록', icon: ClipboardList, status: '주의', tone: 'warning', success: 86, rejected: 2, systemErrors: 4, lastSuccess: '14:29', step: '신청 정보 저장 · 결제 별도', result: '일부 저장 오류 조사 중' },
    { key: 'payment', name: '결제', icon: CreditCard, status: '주의', tone: 'warning', success: 68, rejected: 5, systemErrors: 8, lastSuccess: '14:28', step: '승인 요청 및 결과 반영', result: '승인 결과 불일치 2건 확인 필요' },
    { key: 'abstract', name: '초록제출', icon: FileText, status: '장애', tone: 'urgent', success: 117, rejected: 6, systemErrors: 3, lastSuccess: '14:29', step: '첨부파일 저장 및 최종 제출', result: '파일 저장 실패 3건 우선 확인' },
];

export const UserFeatureHealthPreview = () => (
    <section className={panel} aria-label="활성 장애 및 사용자 핵심 기능 상태">
        <div className="flex flex-col justify-between gap-3 border-b border-slate-200 p-4 dark:border-slate-800 sm:flex-row sm:items-center md:p-5">
            <div>
                <h2 className="flex items-center gap-2 text-sm font-semibold"><Activity className="h-4 w-4" />현재 활성 장애</h2>
                <p className={`mt-1 text-xs leading-5 ${muted}`}>사용자 영향이 있는 사건만 최근 발생순으로 표시하며 같은 원인의 오류는 하나로 묶습니다.</p>
            </div>
            <Badge tone="urgent">3건 대응 중</Badge>
        </div>
        <div className="divide-y divide-slate-100 dark:divide-slate-800">
            {incidents.map(incident => <details key={incident.id} className="group p-4 md:p-5">
                <summary className="flex cursor-pointer list-none flex-wrap items-start gap-3 rounded-lg focus-visible:outline-2 focus-visible:outline-blue-500 [&::-webkit-details-marker]:hidden">
                    <Badge tone={incident.tone}>{incident.severity}</Badge>
                    <div className="min-w-0 flex-1"><h3 className="text-sm font-semibold">{incident.title}</h3><p className={`mt-1 text-xs leading-5 ${muted}`}>{incident.feature} · {incident.occurred} · {incident.id}</p><p className={`mt-1 text-xs leading-5 ${muted}`}>{incident.impact}</p></div>
                    <div className="flex items-center gap-3"><span className="text-sm font-semibold tabular-nums">{incident.count}건</span><ChevronDown className={`h-4 w-4 shrink-0 transition-transform group-open:rotate-180 ${muted}`} /></div>
                </summary>
                <dl className="mt-4 grid gap-4 rounded-lg bg-slate-50 p-4 text-xs leading-5 dark:bg-slate-900 sm:grid-cols-2">
                    <div><dt className="font-semibold">담당자</dt><dd className={`mt-1 ${muted}`}>{incident.owner}</dd></div>
                    <div><dt className="font-semibold">처리 경과</dt><dd className={`mt-1 ${muted}`}>{incident.progress}</dd></div>
                    <div className="sm:col-span-2"><dt className="font-semibold">권장 조치</dt><dd className={`mt-1 ${muted}`}>{incident.action}</dd></div>
                    <details className="group/technical sm:col-span-2"><summary className="flex cursor-pointer list-none items-center gap-2 font-semibold text-blue-700 dark:text-blue-300 [&::-webkit-details-marker]:hidden">기술 상세 보기<ChevronDown className="h-3.5 w-3.5 transition-transform group-open/technical:rotate-180" /></summary><p className={`mt-2 break-words ${muted}`}>{incident.technical}</p></details>
                </dl>
            </details>)}
        </div>

        <div className="border-y border-slate-200 p-4 dark:border-slate-800 md:p-5"><h2 className="text-sm font-semibold">사용자 핵심 기능 상태</h2><p className={`mt-1 text-xs leading-5 ${muted}`}>2026.09.13 13:30–14:30 KST · 실제 사용 흐름의 성공 여부를 기능별로 비교합니다.</p></div>
        <div className="overflow-x-auto">
            <table className="w-full min-w-[860px] text-left text-xs md:text-sm">
                <caption className="sr-only">로그인, 회원가입, 사전등록, 결제, 초록제출 기능별 상태와 최근 처리 결과</caption>
                <thead className="bg-slate-50 dark:bg-slate-900/60"><tr><th scope="col" className="p-4">기능</th><th scope="col" className="p-4">현재 상태</th><th scope="col" className="p-4">최근 1시간 처리</th><th scope="col" className="p-4 text-right">시스템 오류</th><th scope="col" className="p-4">최근 성공</th><th scope="col" className="p-4">운영 판단</th></tr></thead>
                <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                    {features.map(feature => {
                        const Icon = feature.icon;
                        return <tr key={feature.key} className="hover:bg-slate-50 dark:hover:bg-slate-900/40">
                            <th scope="row" className="p-4"><span className="flex items-center gap-2 font-semibold"><Icon className={`h-4 w-4 ${muted}`} />{feature.name}</span><p className={`mt-1 text-xs font-normal ${muted}`}>{feature.step}</p></th>
                            <td className="p-4"><Badge tone={feature.tone}>{feature.status}</Badge></td>
                            <td className="p-4 tabular-nums"><span className="font-semibold">{feature.success}건 성공</span><p className={`mt-1 text-xs ${muted}`}>입력·조건 미충족 {feature.rejected}건</p></td>
                            <td className={`p-4 text-right font-semibold tabular-nums ${feature.systemErrors > 2 ? 'text-rose-600 dark:text-rose-400' : ''}`}>{feature.systemErrors}건</td>
                            <td className="p-4 tabular-nums">{feature.lastSuccess}</td>
                            <td className={`p-4 ${muted}`}>{feature.result}</td>
                        </tr>;
                    })}
                </tbody>
            </table>
        </div>
        <p className={`border-t border-slate-200 px-4 py-3 text-xs leading-5 dark:border-slate-800 md:px-5 ${muted}`}>비밀번호 불일치·중복 가입·필수값 누락·결제 거절은 시스템 오류와 구분합니다. 정상 상태는 최근 재점검이 성공했다는 의미이며 과거 실패 요청의 자동 복구를 뜻하지 않습니다.</p>
    </section>
);
