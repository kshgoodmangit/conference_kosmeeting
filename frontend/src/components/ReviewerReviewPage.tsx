import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { CalendarClock, CheckCircle2, ClipboardCheck, FilePenLine, FilterX, Search } from 'lucide-react';
import type { NotificationType } from './NotificationToast';
import { ReviewerReviewModal } from './ReviewerReviewModal';
import type { ReviewerReviewListItem } from './reviewerReviewTypes';
import type { AbstractCategory, AbstractPresentationType } from './abstractTypes';

interface Props {
    onNotify: (type: NotificationType, message: string) => void;
}

interface ReviewerReviewFilterMeta {
    presentationTypes: AbstractPresentationType[];
    categories: AbstractCategory[];
}

const ASSIGNMENT_STATUS_LABEL: Record<ReviewerReviewListItem['assignmentStatus'], string> = {
    assigned: '배정됨',
    accepted: '수락',
    in_review: '작성중',
    completed: '제출완료'
};

const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
});

const formatDate = (value?: string | null) => {
    if (!value) return '-';
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? '-' : dateFormatter.format(date);
};

export const ReviewerReviewPage = ({ onNotify }: Props) => {
    const onNotifyRef = useRef(onNotify);
    const [items, setItems] = useState<ReviewerReviewListItem[]>([]);
    const [draftSearchKeyword, setDraftSearchKeyword] = useState('');
    const [searchKeyword, setSearchKeyword] = useState('');
    const [draftPresentationTypeCode, setDraftPresentationTypeCode] = useState('');
    const [presentationTypeCode, setPresentationTypeCode] = useState('');
    const [draftCategoryCode, setDraftCategoryCode] = useState('');
    const [categoryCode, setCategoryCode] = useState('');
    const [draftStatus, setDraftStatus] = useState('');
    const [status, setStatus] = useState('');
    const [presentationTypes, setPresentationTypes] = useState<AbstractPresentationType[]>([]);
    const [categories, setCategories] = useState<AbstractCategory[]>([]);
    const [loading, setLoading] = useState(false);
    const [errorMessage, setErrorMessage] = useState('');
    const [reloadKey, setReloadKey] = useState(0);
    const [selectedAssignmentSeq, setSelectedAssignmentSeq] = useState<number | null>(null);
    const [currentTimestamp] = useState(() => Date.now());

    useEffect(() => {
        onNotifyRef.current = onNotify;
    }, [onNotify]);

    useEffect(() => {
        const controller = new AbortController();
        const loadMeta = async () => {
            try {
                const response = await fetch('/api/reviewer/reviews/meta', { signal: controller.signal });
                if (!response.ok) {
                    throw new Error(await response.text() || '검색 조건을 불러오지 못했습니다.');
                }
                const data = await response.json() as ReviewerReviewFilterMeta;
                setPresentationTypes(data.presentationTypes);
                setCategories(data.categories);
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') return;
                onNotifyRef.current('error', error instanceof Error ? error.message : '검색 조건을 불러오지 못했습니다.');
            }
        };
        void loadMeta();
        return () => controller.abort();
    }, []);

    useEffect(() => {
        const controller = new AbortController();
        const load = async () => {
            setLoading(true);
            setErrorMessage('');
            try {
                const params = new URLSearchParams({ keyword: searchKeyword });
                if (presentationTypeCode) params.set('presentationTypeCode', presentationTypeCode);
                if (categoryCode) params.set('categoryCode', categoryCode);
                if (status) params.set('status', status);
                const response = await fetch(`/api/reviewer/reviews?${params.toString()}`, { signal: controller.signal });
                if (!response.ok) {
                    throw new Error(await response.text() || '배정된 초록을 불러오지 못했습니다.');
                }
                setItems(await response.json() as ReviewerReviewListItem[]);
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') return;
                const message = error instanceof Error ? error.message : '배정된 초록을 불러오지 못했습니다.';
                setErrorMessage(message);
                onNotifyRef.current('error', message);
            } finally {
                setLoading(false);
            }
        };
        void load();
        return () => controller.abort();
    }, [categoryCode, presentationTypeCode, reloadKey, searchKeyword, status]);

    const refresh = useCallback(() => setReloadKey((value) => value + 1), []);
    const handleSearch = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        setSearchKeyword(draftSearchKeyword.trim());
        setPresentationTypeCode(draftPresentationTypeCode);
        setCategoryCode(draftCategoryCode);
        setStatus(draftStatus);
        setReloadKey((value) => value + 1);
    };
    const applySelectSearch = (
        nextPresentationTypeCode: string,
        nextCategoryCode: string,
        nextStatus: string
    ) => {
        setSearchKeyword(draftSearchKeyword.trim());
        setPresentationTypeCode(nextPresentationTypeCode);
        setCategoryCode(nextCategoryCode);
        setStatus(nextStatus);
    };
    const resetSearch = () => {
        setDraftSearchKeyword('');
        setSearchKeyword('');
        setDraftPresentationTypeCode('');
        setPresentationTypeCode('');
        setDraftCategoryCode('');
        setCategoryCode('');
        setDraftStatus('');
        setStatus('');
        setReloadKey((value) => value + 1);
    };
    const hasSearchCondition = Boolean(searchKeyword || presentationTypeCode || categoryCode || status);
    const completedCount = items.filter((item) => item.assignmentStatus === 'completed').length;
    const inProgressCount = items.filter((item) => item.assignmentStatus === 'in_review').length;
    const pendingCount = items.length - completedCount - inProgressCount;

    return (
        <section className="rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-950">
            <div className="flex flex-col justify-between gap-4 border-b border-slate-200 p-4 dark:border-slate-800 md:flex-row md:items-center md:p-5">
                <div>
                    <div className="flex items-center gap-2">
                        <ClipboardCheck className="shrink-0 h-5 w-5 text-violet-500" />
                        <h3 className="text-base font-semibold">내 초록 심사</h3>
                    </div>
                    <p className="mt-1 text-xs text-slate-400">본인에게 배정된 초록의 평가점수와 심사의견을 등록합니다.</p>
                </div>
            </div>

            <div className="grid grid-cols-2 divide-x divide-slate-200 border-b border-slate-200 dark:divide-slate-800 dark:border-slate-800 md:grid-cols-4">
                <Summary label="전체 배정" value={items.length} color="text-blue-600 dark:text-blue-400" />
                <Summary label="대기" value={pendingCount} color="text-slate-600 dark:text-slate-300" />
                <Summary label="작성중" value={inProgressCount} color="text-amber-600 dark:text-amber-400" />
                <Summary label="제출완료" value={completedCount} color="text-emerald-600 dark:text-emerald-400" />
            </div>

            <form onSubmit={handleSearch} className="border-b border-slate-200 bg-slate-50/70 p-4 dark:border-slate-800 dark:bg-slate-900/40 md:p-5">
                <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-4">
                    <label>
                        <span className="mb-1 block text-[11px] font-semibold text-slate-500">검색어</span>
                        <div className="relative">
                            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                            <input
                                value={draftSearchKeyword}
                                onChange={(event) => setDraftSearchKeyword(event.target.value)}
                                className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 pl-9 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-700 dark:bg-slate-950 dark:text-slate-50"
                                placeholder="접수번호, 제목 검색"
                            />
                        </div>
                    </label>
                    <label>
                        <span className="mb-1 block text-[11px] font-semibold text-slate-500">발표형식</span>
                        <select
                            value={draftPresentationTypeCode}
                            onChange={(event) => {
                                const value = event.target.value;
                                setDraftPresentationTypeCode(value);
                                applySelectSearch(value, draftCategoryCode, draftStatus);
                            }}
                            className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-700 dark:bg-slate-950 dark:text-slate-50"
                        >
                            <option value="">전체 발표형식</option>
                            {presentationTypes.map((item) => <option key={item.code} value={item.code}>{item.name}</option>)}
                        </select>
                    </label>
                    <label>
                        <span className="mb-1 block text-[11px] font-semibold text-slate-500">분류</span>
                        <select
                            value={draftCategoryCode}
                            onChange={(event) => {
                                const value = event.target.value;
                                setDraftCategoryCode(value);
                                applySelectSearch(draftPresentationTypeCode, value, draftStatus);
                            }}
                            className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-700 dark:bg-slate-950 dark:text-slate-50"
                        >
                            <option value="">전체 분류</option>
                            {categories.map((item) => <option key={item.code} value={item.code}>{item.name}</option>)}
                        </select>
                    </label>
                    <label>
                        <span className="mb-1 block text-[11px] font-semibold text-slate-500">상태</span>
                        <select
                            value={draftStatus}
                            onChange={(event) => {
                                const value = event.target.value;
                                setDraftStatus(value);
                                applySelectSearch(draftPresentationTypeCode, draftCategoryCode, value);
                            }}
                            className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-700 dark:bg-slate-950 dark:text-slate-50"
                        >
                            <option value="">전체 상태</option>
                            {Object.entries(ASSIGNMENT_STATUS_LABEL).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                        </select>
                    </label>
                </div>
                <div className="mt-3 flex flex-col justify-end gap-2 sm:flex-row">
                    <button type="button" onClick={resetSearch} className="inline-flex items-center justify-center gap-2 rounded-lg border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-white dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-900">
                        <FilterX className="h-4 w-4" /> 초기화
                    </button>
                    <button type="submit" className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-5 py-2 text-xs font-semibold text-white hover:bg-blue-700">
                        <Search className="h-4 w-4" /> 조회
                    </button>
                </div>
            </form>

            {errorMessage && <div className="m-4 rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 dark:border-rose-900/60 dark:bg-rose-950/30 dark:text-rose-300 md:m-5">{errorMessage}</div>}

            <div className="overflow-x-auto">
                <table className="min-w-[900px] w-full text-left text-sm">
                    <thead className="border-b border-slate-200 bg-slate-50 text-xs dark:border-slate-800 dark:bg-slate-900/50">
                        <tr><th className="p-4">접수번호</th><th className="p-4">초록</th><th className="p-4">분류 / 발표형식</th><th className="p-4">심사 마감</th><th className="p-4 text-center">상태</th><th className="p-4 text-center">심사</th></tr>
                    </thead>
                    <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                        {loading && items.length === 0 && <tr><td colSpan={6} className="p-10 text-center text-slate-400">배정된 초록을 불러오는 중입니다.</td></tr>}
                        {!loading && items.length === 0 && <tr><td colSpan={6} className="p-10 text-center text-slate-400">{hasSearchCondition ? '검색 조건에 맞는 배정 초록이 없습니다.' : '현재 배정된 초록이 없습니다.'}</td></tr>}
                        {items.map((item) => {
                            const completed = item.assignmentStatus === 'completed';
                            const overdue = !completed && item.dueAt != null && new Date(item.dueAt).getTime() < currentTimestamp;
                            return (
                                <tr key={item.assignmentSeq} className="hover:bg-slate-50/60 dark:hover:bg-slate-900/40">
                                    <td className="p-4 font-mono text-xs text-slate-500">{item.submissionNo}</td>
                                    <td className="p-4"><p className="max-w-md font-semibold text-slate-900 dark:text-slate-50">{item.title}</p></td>
                                    <td className="p-4"><p className="text-slate-600 dark:text-slate-300">{item.categoryName}</p><p className="mt-1 text-xs text-slate-400">{item.presentationTypeName}</p></td>
                                    <td className="p-4"><span className={`inline-flex items-center gap-1.5 ${overdue ? 'font-semibold text-rose-600 dark:text-rose-400' : 'text-slate-500 dark:text-slate-400'}`}><CalendarClock className="h-4 w-4" />{formatDate(item.dueAt)}</span>{overdue && <p className="mt-1 text-xs text-rose-500">마감일이 지났습니다.</p>}</td>
                                    <td className="p-4 text-center"><span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${completed ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300' : item.assignmentStatus === 'in_review' ? 'bg-amber-50 text-amber-700 dark:bg-amber-950/40 dark:text-amber-300' : 'bg-blue-50 text-blue-700 dark:bg-blue-950/40 dark:text-blue-300'}`}>{ASSIGNMENT_STATUS_LABEL[item.assignmentStatus]}</span></td>
                                    <td className="p-4 text-center"><button type="button" onClick={() => setSelectedAssignmentSeq(item.assignmentSeq)} className={`inline-flex items-center gap-1.5 rounded-lg px-3 py-2 text-xs font-semibold text-white ${completed ? 'bg-slate-600 hover:bg-slate-700' : 'bg-violet-600 hover:bg-violet-700'}`}>{completed ? <CheckCircle2 className="h-4 w-4" /> : <FilePenLine className="h-4 w-4" />}{completed ? '결과 보기' : item.assignmentStatus === 'in_review' ? '이어서 작성' : '심사 시작'}</button></td>
                                </tr>
                            );
                        })}
                    </tbody>
                </table>
            </div>

            <ReviewerReviewModal
                assignmentSeq={selectedAssignmentSeq}
                onClose={() => setSelectedAssignmentSeq(null)}
                onSaved={(submitted) => {
                    refresh();
                    if (submitted) setSelectedAssignmentSeq(null);
                }}
                onNotify={(type, message) => onNotifyRef.current(type, message)}
            />
        </section>
    );
};

const Summary = ({ label, value, color }: { label: string; value: number; color: string }) => (
    <div className="p-4 md:p-5"><p className="text-xs font-medium text-slate-400">{label}</p><p className={`mt-1 text-2xl font-bold ${color}`}>{value.toLocaleString()}</p></div>
);
