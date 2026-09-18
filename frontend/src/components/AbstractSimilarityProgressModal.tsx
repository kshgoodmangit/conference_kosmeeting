import { useEffect, useMemo, useRef, useState, type ComponentType, type RefObject } from 'react';
import {
    Activity,
    AlertTriangle,
    BrainCircuit,
    CheckCircle2,
    Circle,
    Cpu,
    Database,
    LoaderCircle,
    Play,
    Radio,
    RotateCcw,
    Save,
    ScanSearch,
    SlidersHorizontal,
    Sparkles,
    X
} from 'lucide-react';
import { DraggableModal } from './DraggableModal';

export type AbstractSimilarityJobStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED';
export type AbstractSimilarityJobPhase = 'PREPARING' | 'EMBEDDING' | 'CALCULATING' | 'SAVING' | 'COMPLETED' | 'FAILED';

export interface AbstractSimilarityWeights {
    title: number;
    objective: number;
    methods: number;
    results: number;
    conclusions: number;
}

export interface AbstractSimilarityJob {
    seq: number;
    status: AbstractSimilarityJobStatus;
    phase: AbstractSimilarityJobPhase;
    progressPercent: number;
    message: string;
    processedCount: number;
    totalCount: number;
    abstractCount: number;
    updatedAbstractCount: number;
    generatedEmbeddingCount: number;
    similarityResultCount: number;
    titleWeight: number;
    objectiveWeight: number;
    methodsWeight: number;
    resultsWeight: number;
    conclusionsWeight: number;
    startedAt?: string | null;
    completedAt?: string | null;
    createdAt: string;
    updatedAt: string;
}

interface Props {
    isOpen: boolean;
    job: AbstractSimilarityJob | null;
    connected: boolean;
    defaultWeights: AbstractSimilarityWeights;
    isStarting: boolean;
    onStart: (weights: AbstractSimilarityWeights) => Promise<void>;
    onClose: () => void;
}

interface PhaseItem {
    key: AbstractSimilarityJobPhase;
    label: string;
    description: string;
    icon: ComponentType<{ className?: string }>;
}

const PHASES: PhaseItem[] = [
    { key: 'PREPARING', label: '데이터 확인', description: '분석 대상 조회', icon: Database },
    { key: 'EMBEDDING', label: '분석 데이터 생성', description: 'AI 데이터 변환', icon: Cpu },
    { key: 'CALCULATING', label: '유사도 계산', description: '전체 초록 비교', icon: ScanSearch },
    { key: 'SAVING', label: '결과 저장', description: '상위 결과 반영', icon: Save },
    { key: 'COMPLETED', label: '분석 완료', description: '결과 사용 가능', icon: CheckCircle2 }
];

const ACTIVE_STATUSES = new Set<AbstractSimilarityJobStatus>(['QUEUED', 'RUNNING']);

type WeightKey = keyof AbstractSimilarityWeights;

const WEIGHT_ITEMS: Array<{ key: WeightKey; label: string; description: string; color: string }> = [
    { key: 'title', label: '제목', description: '연구 주제와 핵심 표현', color: 'bg-blue-500' },
    { key: 'objective', label: 'Objective', description: '연구 목적과 문제 정의', color: 'bg-cyan-500' },
    { key: 'methods', label: 'Methods', description: '연구 방법과 설계', color: 'bg-violet-500' },
    { key: 'results', label: 'Results', description: '주요 결과와 수치', color: 'bg-fuchsia-500' },
    { key: 'conclusions', label: 'Conclusions', description: '결론과 연구 의미', color: 'bg-emerald-500' }
];

const weightsFromJob = (job: AbstractSimilarityJob, fallback: AbstractSimilarityWeights): AbstractSimilarityWeights => ({
    title: job.titleWeight ?? fallback.title,
    objective: job.objectiveWeight ?? fallback.objective,
    methods: job.methodsWeight ?? fallback.methods,
    results: job.resultsWeight ?? fallback.results,
    conclusions: job.conclusionsWeight ?? fallback.conclusions
});

export const AbstractSimilarityProgressModal = ({
    isOpen,
    job,
    connected,
    defaultWeights,
    isStarting,
    onStart,
    onClose
}: Props) => {
    const modalRef = useRef<HTMLDivElement>(null);
    const [now, setNow] = useState(0);
    const [isEditingWeights, setIsEditingWeights] = useState(false);
    const [weights, setWeights] = useState<AbstractSimilarityWeights>(defaultWeights);
    const isActive = Boolean(job && ACTIVE_STATUSES.has(job.status));

    useEffect(() => {
        if (!isOpen) return;
        const previousOverflow = document.body.style.overflow;
        document.body.style.overflow = 'hidden';
        modalRef.current?.focus();
        const handleKeyDown = (event: KeyboardEvent) => {
            if (event.key === 'Escape' && !isStarting) onClose();
        };
        window.addEventListener('keydown', handleKeyDown);
        return () => {
            document.body.style.overflow = previousOverflow;
            window.removeEventListener('keydown', handleKeyDown);
        };
    }, [isOpen, isStarting, onClose]);

    useEffect(() => {
        if (!isOpen || !isActive) return;
        const timer = window.setInterval(() => setNow(Date.now()), 1_000);
        return () => window.clearInterval(timer);
    }, [isActive, isOpen]);

    if (!isOpen) return null;

    if (!job || isEditingWeights) {
        return (
            <WeightSettingsModal
                modalRef={modalRef}
                weights={weights}
                defaultWeights={defaultWeights}
                isStarting={isStarting}
                replacingExistingResults={Boolean(job)}
                onChange={setWeights}
                onStart={onStart}
                onClose={onClose}
            />
        );
    }

    const progress = Math.max(0, Math.min(job.progressPercent ?? 0, 100));
    const currentPhaseIndex = job.status === 'FAILED'
        ? Math.max(0, PHASES.findIndex((phase) => phase.key === job.phase))
        : PHASES.findIndex((phase) => phase.key === job.phase);
    const startedAt = Date.parse(job.startedAt ?? job.createdAt);
    const endedAt = job.completedAt ? Date.parse(job.completedAt) : now || Date.parse(job.updatedAt);
    const elapsedSeconds = Number.isNaN(startedAt) || Number.isNaN(endedAt)
        ? 0
        : Math.max(0, Math.floor((endedAt - startedAt) / 1_000));
    const progressUnit = job.phase === 'EMBEDDING'
        ? '벡터 항목'
        : job.phase === 'CALCULATING'
            ? '초록'
            : job.phase === 'SAVING'
                ? '분석 결과'
                : '처리 항목';

    return (
        <div className="fixed inset-0 z-[120] flex items-center justify-center p-4">
            <button type="button" aria-label="AI 유사도 분석 진행상황 닫기" onClick={onClose} className="absolute inset-0 bg-slate-950/70 backdrop-blur-sm" />
            <DraggableModal
                ref={modalRef}
                role="dialog"
                aria-modal="true"
                aria-labelledby="abstract-similarity-progress-title"
                tabIndex={-1}
                className="relative z-10 flex max-h-[92vh] w-full max-w-2xl flex-col overflow-hidden rounded-2xl border border-white/10 bg-white shadow-[0_30px_90px_-20px_rgba(15,23,42,0.75)] outline-none dark:bg-slate-950"
            >
                <header data-modal-drag-handle className="relative shrink-0 cursor-move select-none touch-none overflow-hidden bg-gradient-to-br from-slate-950 via-blue-950 to-indigo-950 px-5 py-5 text-white">
                    <div className="pointer-events-none absolute -right-12 -top-16 h-44 w-44 rounded-full bg-blue-500/20 blur-3xl" />
                    <div className="pointer-events-none absolute -bottom-16 left-24 h-36 w-36 rounded-full bg-violet-500/20 blur-3xl" />
                    <div className="relative flex items-start justify-between gap-4">
                        <div>
                            <div className="flex items-center gap-3">
                                <span className="flex h-10 w-10 items-center justify-center rounded-xl border border-white/15 bg-white/10 shadow-inner">
                                    <BrainCircuit className="h-5 w-5 text-blue-200" />
                                </span>
                                <div>
                                    <div className="flex flex-wrap items-center gap-2">
                                        <h2 id="abstract-similarity-progress-title" className="text-base font-bold">AI 유사도 분석 진행상황</h2>
                                        <span className={`inline-flex items-center gap-1.5 rounded-full border px-2 py-0.5 text-[10px] font-semibold ${job.status === 'COMPLETED' ? 'border-emerald-400/30 bg-emerald-400/15 text-emerald-200' : job.status === 'FAILED' ? 'border-rose-400/30 bg-rose-400/15 text-rose-200' : 'border-blue-300/30 bg-blue-300/15 text-blue-100'}`}>
                                            {isActive && <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-blue-300" />}
                                            {job.status === 'COMPLETED' ? '완료' : job.status === 'FAILED' ? '실패' : job.status === 'QUEUED' ? '대기 중' : '분석 중'}
                                        </span>
                                    </div>
                                    <p className="mt-1 text-xs text-blue-100/70">작업 #{job.seq} · 임시저장 초록은 분석에서 제외됩니다.</p>
                                </div>
                            </div>
                        </div>
                        <button type="button" onClick={onClose} aria-label="AI 유사도 분석 진행상황 닫기" className="shrink-0 rounded-lg p-1.5 text-blue-100/70 hover:bg-white/10 hover:text-white"><X className="h-5 w-5" /></button>
                    </div>
                </header>

                <div className="overflow-y-auto bg-slate-50/70 p-5 dark:bg-slate-950">
                    <section className="grid gap-5 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:grid-cols-[150px_1fr] sm:items-center dark:border-slate-800 dark:bg-slate-900/60">
                        <div className="relative mx-auto flex h-32 w-32 items-center justify-center">
                            <svg className="h-32 w-32 -rotate-90" viewBox="0 0 120 120" aria-hidden="true">
                                <defs>
                                    <linearGradient id="similarity-progress-gradient" x1="0" y1="0" x2="1" y2="1">
                                        <stop offset="0%" stopColor="#3b82f6" />
                                        <stop offset="55%" stopColor="#6366f1" />
                                        <stop offset="100%" stopColor="#a855f7" />
                                    </linearGradient>
                                </defs>
                                <circle cx="60" cy="60" r="51" fill="none" stroke="currentColor" strokeWidth="8" className="text-slate-100 dark:text-slate-800" />
                                <circle cx="60" cy="60" r="51" fill="none" stroke="url(#similarity-progress-gradient)" strokeWidth="8" strokeLinecap="round" pathLength="100" strokeDasharray="100" strokeDashoffset={100 - progress} className="transition-all duration-700 ease-out" />
                            </svg>
                            <div className="absolute text-center">
                                <p className="text-3xl font-black tracking-tight text-slate-900 dark:text-white">{progress}<span className="text-base text-slate-400">%</span></p>
                                <p className="mt-0.5 text-[10px] font-semibold uppercase tracking-[0.18em] text-slate-400">Progress</p>
                            </div>
                        </div>

                        <div className="min-w-0">
                            <div className="flex flex-wrap items-center justify-between gap-2">
                                <div className="flex items-center gap-2 text-sm font-bold text-slate-900 dark:text-slate-50">
                                    {isActive ? <Activity className="h-4 w-4 animate-pulse text-blue-500" /> : job.status === 'COMPLETED' ? <CheckCircle2 className="h-4 w-4 text-emerald-500" /> : <AlertTriangle className="h-4 w-4 text-rose-500" />}
                                    {job.status === 'COMPLETED' ? '분석이 완료되었습니다' : job.status === 'FAILED' ? '분석을 완료하지 못했습니다' : 'AI 분석 엔진이 작업 중입니다'}
                                </div>
                                {isActive && (
                                    <span className={`inline-flex items-center gap-1.5 text-[11px] font-medium ${connected ? 'text-emerald-600 dark:text-emerald-400' : 'text-amber-600 dark:text-amber-400'}`}>
                                        <Radio className={`h-3.5 w-3.5 ${connected ? 'animate-pulse' : ''}`} />
                                        {connected ? '실시간 연결됨' : '연결 재시도 중'}
                                    </span>
                                )}
                            </div>
                            <p className={`mt-2 text-sm leading-6 ${job.status === 'FAILED' ? 'text-rose-600 dark:text-rose-300' : 'text-slate-600 dark:text-slate-300'}`}>{job.message}</p>
                            <div className="mt-4 h-2.5 overflow-hidden rounded-full bg-slate-100 shadow-inner dark:bg-slate-800" role="progressbar" aria-label="AI 유사도 분석 진행률" aria-valuemin={0} aria-valuemax={100} aria-valuenow={progress}>
                                <div className={`h-full rounded-full transition-all duration-700 ease-out ${job.status === 'FAILED' ? 'bg-rose-500' : 'bg-gradient-to-r from-blue-500 via-indigo-500 to-violet-500'}`} style={{ width: `${progress}%` }} />
                            </div>
                            <div className="mt-2 flex items-center justify-between text-[11px] text-slate-400">
                                <span>{job.totalCount > 0 ? `${progressUnit} ${job.processedCount.toLocaleString()} / ${job.totalCount.toLocaleString()}` : '처리 대상을 확인하는 중입니다.'}</span>
                                <span>경과 {formatDuration(elapsedSeconds)}</span>
                            </div>
                        </div>
                    </section>

                    <section className="mt-4 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-slate-900/60">
                        <div className="grid grid-cols-2 gap-3 sm:grid-cols-5">
                            {PHASES.map((phase, index) => {
                                const Icon = phase.icon;
                                const complete = job.status === 'COMPLETED' || index < currentPhaseIndex;
                                const current = job.status !== 'COMPLETED' && index === currentPhaseIndex;
                                return (
                                    <div key={phase.key} className={`relative rounded-xl border px-3 py-3 transition-colors ${complete ? 'border-emerald-200 bg-emerald-50/70 dark:border-emerald-900/60 dark:bg-emerald-950/20' : current ? job.status === 'FAILED' ? 'border-rose-200 bg-rose-50 dark:border-rose-900/60 dark:bg-rose-950/20' : 'border-blue-200 bg-blue-50 dark:border-blue-900/60 dark:bg-blue-950/20' : 'border-slate-200 bg-slate-50/60 dark:border-slate-800 dark:bg-slate-950/40'}`}>
                                        <span className={`flex h-8 w-8 items-center justify-center rounded-lg ${complete ? 'bg-emerald-500 text-white' : current ? job.status === 'FAILED' ? 'bg-rose-500 text-white' : 'bg-blue-600 text-white shadow-lg shadow-blue-500/20' : 'bg-slate-200 text-slate-400 dark:bg-slate-800'}`}>
                                            {complete ? <CheckCircle2 className="h-4 w-4" /> : current ? <Icon className={`h-4 w-4 ${isActive ? 'animate-pulse' : ''}`} /> : <Circle className="h-3.5 w-3.5" />}
                                        </span>
                                        <p className={`mt-2 text-xs font-bold ${complete ? 'text-emerald-700 dark:text-emerald-300' : current ? job.status === 'FAILED' ? 'text-rose-700 dark:text-rose-300' : 'text-blue-700 dark:text-blue-300' : 'text-slate-500 dark:text-slate-400'}`}>{phase.label}</p>
                                        <p className="mt-0.5 text-[10px] leading-4 text-slate-400">{phase.description}</p>
                                    </div>
                                );
                            })}
                        </div>
                    </section>

                    <section className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-3">
                        <MetricCard icon={Database} label="분석 대상 초록" value={`${job.abstractCount.toLocaleString()}건`} tone="blue" />
                        <MetricCard icon={Cpu} label="새로 만든 분석 데이터" value={job.status === 'COMPLETED' ? `${job.generatedEmbeddingCount.toLocaleString()}개` : '집계 중'} tone="violet" />
                        <MetricCard icon={Sparkles} label="저장된 유사도 결과" value={job.status === 'COMPLETED' ? `${job.similarityResultCount.toLocaleString()}건` : '집계 중'} tone="emerald" />
                    </section>

                    <section className="mt-4 rounded-xl border border-slate-200 bg-white px-4 py-3 dark:border-slate-800 dark:bg-slate-900/60">
                        <div className="flex items-center gap-2">
                            <SlidersHorizontal className="h-4 w-4 text-violet-500" />
                            <p className="text-xs font-bold text-slate-700 dark:text-slate-200">이번 분석에 적용한 가중치</p>
                        </div>
                        <div className="mt-2 flex flex-wrap gap-2">
                            {WEIGHT_ITEMS.map((item) => (
                                <span key={item.key} className="rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-[11px] text-slate-600 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-300">
                                    {item.label} <strong>{weightsFromJob(job, defaultWeights)[item.key]}%</strong>
                                </span>
                            ))}
                        </div>
                    </section>

                    {job.status === 'COMPLETED' && (
                        <div className="mt-4 flex items-start gap-3 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-emerald-800 dark:border-emerald-900/60 dark:bg-emerald-950/30 dark:text-emerald-200">
                            <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0" />
                            <div><p className="text-sm font-bold">최신 분석 결과를 사용할 수 있습니다.</p><p className="mt-1 text-xs opacity-80">초록 {job.updatedAbstractCount.toLocaleString()}건의 분석 데이터를 갱신하고 상위 유사도 결과를 저장했습니다.</p></div>
                        </div>
                    )}

                    {isActive && (
                        <p className="mt-4 text-center text-[11px] text-slate-400">창을 닫아도 서버에서 분석은 계속됩니다. 다시 `진행상황 보기`를 누르면 현재 상태를 확인할 수 있습니다.</p>
                    )}
                </div>

                <footer className="flex shrink-0 items-center justify-between gap-2 border-t border-slate-200 bg-white px-5 py-4 dark:border-slate-800 dark:bg-slate-950">
                    <div>
                        {!isActive && (
                            <button
                                type="button"
                                onClick={() => {
                                    setWeights(weightsFromJob(job, defaultWeights));
                                    setIsEditingWeights(true);
                                }}
                                className="inline-flex items-center gap-2 rounded-lg border border-violet-200 bg-violet-50 px-3 py-2 text-xs font-semibold text-violet-700 hover:bg-violet-100 dark:border-violet-900/60 dark:bg-violet-950/30 dark:text-violet-300 dark:hover:bg-violet-950/50"
                            >
                                <SlidersHorizontal className="h-4 w-4" />
                                가중치 조정 후 재분석
                            </button>
                        )}
                    </div>
                    <button type="button" onClick={onClose} className={`rounded-lg px-4 py-2 text-xs font-semibold ${isActive ? 'border border-slate-200 text-slate-700 hover:bg-slate-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900' : 'bg-blue-600 text-white hover:bg-blue-700 dark:bg-blue-600 dark:text-white dark:hover:bg-blue-700'}`}>{isActive ? '백그라운드에서 계속' : '닫기'}</button>
                </footer>
            </DraggableModal>
        </div>
    );
};

const WeightSettingsModal = ({
    modalRef,
    weights,
    defaultWeights,
    isStarting,
    replacingExistingResults,
    onChange,
    onStart,
    onClose
}: {
    modalRef: RefObject<HTMLDivElement | null>;
    weights: AbstractSimilarityWeights;
    defaultWeights: AbstractSimilarityWeights;
    isStarting: boolean;
    replacingExistingResults: boolean;
    onChange: (weights: AbstractSimilarityWeights) => void;
    onStart: (weights: AbstractSimilarityWeights) => Promise<void>;
    onClose: () => void;
}) => {
    const total = useMemo(() => Object.values(weights).reduce((sum, value) => sum + value, 0), [weights]);
    const valid = total === 100;

    const updateWeight = (key: WeightKey, rawValue: string) => {
        const parsed = Number.parseInt(rawValue, 10);
        const value = Number.isNaN(parsed) ? 0 : Math.max(0, Math.min(parsed, 100));
        onChange({ ...weights, [key]: value });
    };

    return (
        <div className="fixed inset-0 z-[120] flex items-center justify-center p-4">
            <button type="button" aria-label="AI 유사도 분석 설정 닫기" onClick={isStarting ? undefined : onClose} className="absolute inset-0 bg-slate-950/70 backdrop-blur-sm" />
            <DraggableModal
                ref={modalRef}
                role="dialog"
                aria-modal="true"
                aria-labelledby="abstract-similarity-settings-title"
                tabIndex={-1}
                className="relative z-10 flex max-h-[92vh] w-full max-w-3xl flex-col overflow-hidden rounded-2xl border border-white/10 bg-white shadow-[0_30px_90px_-20px_rgba(15,23,42,0.75)] outline-none dark:bg-slate-950"
            >
                <header data-modal-drag-handle className="relative shrink-0 cursor-move select-none touch-none overflow-hidden bg-gradient-to-br from-slate-950 via-blue-950 to-indigo-950 px-5 py-5 text-white">
                    <div className="pointer-events-none absolute -right-12 -top-16 h-44 w-44 rounded-full bg-blue-500/20 blur-3xl" />
                    <div className="pointer-events-none absolute -bottom-16 left-24 h-36 w-36 rounded-full bg-violet-500/20 blur-3xl" />
                    <div className="relative flex items-start justify-between gap-4">
                        <div className="flex items-center gap-3">
                            <span className="flex h-10 w-10 items-center justify-center rounded-xl border border-white/15 bg-white/10 shadow-inner"><SlidersHorizontal className="h-5 w-5 text-blue-200" /></span>
                            <div>
                                <h2 id="abstract-similarity-settings-title" className="text-base font-bold">AI 유사도 분석 설정</h2>
                                <p className="mt-1 text-xs text-blue-100/70">항목별 중요도를 조정한 뒤 분석을 실행합니다.</p>
                            </div>
                        </div>
                        <button type="button" disabled={isStarting} onClick={onClose} aria-label="AI 유사도 분석 설정 닫기" className="shrink-0 rounded-lg p-1.5 text-blue-100/70 hover:bg-white/10 hover:text-white disabled:opacity-40"><X className="h-5 w-5" /></button>
                    </div>
                </header>

                <div className="overflow-y-auto bg-slate-50/70 p-5 dark:bg-slate-950">
                    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900/60">
                        <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-start">
                            <div>
                                <div className="flex items-center gap-2"><Sparkles className="h-4 w-4 text-violet-500" /><h3 className="text-sm font-bold text-slate-900 dark:text-white">종합 유사도 가중치</h3></div>
                                <p className="mt-1 text-xs leading-5 text-slate-500 dark:text-slate-400">값이 클수록 해당 항목이 최종 유사도 순위에 더 크게 반영됩니다.</p>
                            </div>
                            <div className={`rounded-xl border px-4 py-2 text-center ${valid ? 'border-emerald-200 bg-emerald-50 dark:border-emerald-900/60 dark:bg-emerald-950/30' : 'border-rose-200 bg-rose-50 dark:border-rose-900/60 dark:bg-rose-950/30'}`}>
                                <p className="text-[10px] font-semibold uppercase tracking-wider text-slate-400">합계</p>
                                <p className={`text-lg font-black ${valid ? 'text-emerald-600 dark:text-emerald-300' : 'text-rose-600 dark:text-rose-300'}`}>{total}%</p>
                            </div>
                        </div>

                        <div className="mt-5 space-y-3">
                            {WEIGHT_ITEMS.map((item) => (
                                <div key={item.key} className="grid gap-3 rounded-xl border border-slate-200 bg-slate-50/70 px-4 py-3 sm:grid-cols-[150px_1fr_78px] sm:items-center dark:border-slate-800 dark:bg-slate-950/50">
                                    <div><p className="text-xs font-bold text-slate-800 dark:text-slate-100">{item.label}</p><p className="mt-0.5 text-[10px] text-slate-400">{item.description}</p></div>
                                    <div>
                                        <input type="range" min={0} max={100} step={1} value={weights[item.key]} onChange={(event) => updateWeight(item.key, event.target.value)} aria-label={`${item.label} 가중치`} className="h-2 w-full cursor-pointer appearance-none rounded-full bg-slate-200 accent-blue-600 dark:bg-slate-700" />
                                        <div className="mt-1 h-1 overflow-hidden rounded-full bg-slate-200 dark:bg-slate-800"><div className={`h-full rounded-full ${item.color}`} style={{ width: `${weights[item.key]}%` }} /></div>
                                    </div>
                                    <div className="relative">
                                        <input type="number" min={0} max={100} value={weights[item.key]} onChange={(event) => updateWeight(item.key, event.target.value)} className="w-full rounded-lg border border-slate-200 bg-white py-2 pl-3 pr-7 text-right text-sm font-bold text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-700 dark:bg-slate-900 dark:text-white" />
                                        <span className="pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 text-xs text-slate-400">%</span>
                                    </div>
                                </div>
                            ))}
                        </div>

                        <div className="mt-4 rounded-xl bg-slate-950 px-4 py-3 text-xs text-slate-300 dark:bg-black/30">
                            <p className="font-semibold text-white">종합 점수 계산식</p>
                            <p className="mt-1 break-words font-mono leading-5">제목 {weights.title}% + Objective {weights.objective}% + Methods {weights.methods}% + Results {weights.results}% + Conclusions {weights.conclusions}%</p>
                        </div>

                        {!valid && <div className="mt-4 flex items-start gap-2 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-xs text-rose-700 dark:border-rose-900/60 dark:bg-rose-950/30 dark:text-rose-300"><AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />가중치 합계를 100%로 맞춰 주세요. 현재 합계는 {total}%입니다.</div>}
                    </section>

                    <div className={`mt-4 flex items-start gap-3 rounded-xl border px-4 py-3 ${replacingExistingResults ? 'border-amber-200 bg-amber-50 text-amber-800 dark:border-amber-900/60 dark:bg-amber-950/30 dark:text-amber-200' : 'border-blue-200 bg-blue-50 text-blue-800 dark:border-blue-900/60 dark:bg-blue-950/30 dark:text-blue-200'}`}>
                        {replacingExistingResults ? <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0" /> : <BrainCircuit className="mt-0.5 h-5 w-5 shrink-0" />}
                        <div><p className="text-sm font-bold">{replacingExistingResults ? '재분석하면 현재 결과가 교체됩니다.' : '전체 초록을 대상으로 분석합니다.'}</p><p className="mt-1 text-xs leading-5 opacity-80">임시저장을 제외한 초록의 분석 데이터를 준비하고, 설정한 가중치로 유사도를 계산하여 상위 결과를 저장합니다. 초록 수에 따라 시간이 걸릴 수 있습니다.</p></div>
                    </div>
                </div>

                <footer className="flex shrink-0 flex-wrap items-center justify-between gap-2 border-t border-slate-200 bg-white px-5 py-4 dark:border-slate-800 dark:bg-slate-950">
                    <button type="button" disabled={isStarting} onClick={() => onChange(defaultWeights)} className="inline-flex items-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900"><RotateCcw className="h-3.5 w-3.5" />기본값 복원</button>
                    <div className="flex items-center gap-2">
                        <button type="button" disabled={isStarting} onClick={onClose} className="rounded-lg border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900">취소</button>
                        <button type="button" disabled={!valid || isStarting} onClick={() => void onStart(weights)} className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-xs font-semibold text-white shadow-lg shadow-blue-500/20 hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50">{isStarting ? <LoaderCircle className="h-4 w-4 animate-spin" /> : <Play className="h-4 w-4" />}{isStarting ? '작업 시작 중' : replacingExistingResults ? '이 설정으로 재분석' : '이 설정으로 분석 실행'}</button>
                    </div>
                </footer>
            </DraggableModal>
        </div>
    );
};

const MetricCard = ({ icon: Icon, label, value, tone }: {
    icon: ComponentType<{ className?: string }>;
    label: string;
    value: string;
    tone: 'blue' | 'violet' | 'emerald';
}) => {
    const toneClass = tone === 'blue'
        ? 'bg-blue-50 text-blue-600 dark:bg-blue-950/40 dark:text-blue-300'
        : tone === 'violet'
            ? 'bg-violet-50 text-violet-600 dark:bg-violet-950/40 dark:text-violet-300'
            : 'bg-emerald-50 text-emerald-600 dark:bg-emerald-950/40 dark:text-emerald-300';
    return (
        <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-slate-900/60">
            <div className="flex items-center gap-3">
                <span className={`flex h-9 w-9 items-center justify-center rounded-lg ${toneClass}`}><Icon className="h-4 w-4" /></span>
                <div className="min-w-0"><p className="text-[11px] font-medium text-slate-400">{label}</p><p className="mt-0.5 truncate text-sm font-black text-slate-900 dark:text-white">{value}</p></div>
            </div>
        </div>
    );
};

const formatDuration = (seconds: number) => {
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;
    return minutes > 0 ? `${minutes}분 ${remainingSeconds}초` : `${remainingSeconds}초`;
};
