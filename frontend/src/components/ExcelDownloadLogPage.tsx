import { DraggableModal } from './DraggableModal';
import { useEffect, useRef, useState } from 'react';
import {
    ChevronLeft,
    ChevronRight,
    Download,
    Eye,
    FilterX,
    Search,
    X
} from 'lucide-react';
import type { NotificationType } from './NotificationToast';

interface Props {
    onNotify: (type: NotificationType, message: string) => void;
}

interface ExcelDownloadLog {
    seq: number;
    exportType: string;
    menuKey: string;
    menuName: string;
    reason: string;
    filterJson?: string | null;
    adminSeq?: number | null;
    adminEmail: string;
    adminName: string;
    adminRole: string;
    ipAddress?: string | null;
    userAgent?: string | null;
    status: 'PROCESSING' | 'SUCCESS' | 'FAILED';
    rowCount?: number | null;
    fileName?: string | null;
    fileSize?: number | null;
    failureCode?: string | null;
    failureMessage?: string | null;
    requestedAt: string;
    completedAt?: string | null;
}

interface PageResponse {
    items: ExcelDownloadLog[];
    page: number;
    size: number;
    totalCount: number;
    totalPages: number;
}

interface Filters {
    dateFrom: string;
    dateTo: string;
    exportType: string;
    status: string;
    adminKeyword: string;
    reasonKeyword: string;
}

const EMPTY_FILTERS: Filters = {
    dateFrom: '',
    dateTo: '',
    exportType: '',
    status: '',
    adminKeyword: '',
    reasonKeyword: ''
};

const EXPORT_TYPE_OPTIONS = [
    { value: 'ADMIN_ACCOUNTS', label: '관리자 계정 관리' },
    { value: 'MEMBERS', label: '회원 관리' },
    { value: 'ABSTRACTS', label: '초록 관리' },
    { value: 'PRE_REGISTRATIONS', label: '사전등록관리' }
];

const STATUS_LABELS: Record<ExcelDownloadLog['status'], string> = {
    PROCESSING: '처리 중',
    SUCCESS: '성공',
    FAILED: '실패'
};

export const ExcelDownloadLogPage = ({ onNotify }: Props) => {
    const onNotifyRef = useRef(onNotify);
    const [items, setItems] = useState<ExcelDownloadLog[]>([]);
    const [draftFilters, setDraftFilters] = useState<Filters>(EMPTY_FILTERS);
    const [filters, setFilters] = useState<Filters>(EMPTY_FILTERS);
    const [page, setPage] = useState(1);
    const [totalCount, setTotalCount] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [isLoading, setIsLoading] = useState(false);
    const [detail, setDetail] = useState<ExcelDownloadLog | null>(null);
    const [isDetailLoading, setIsDetailLoading] = useState(false);

    useEffect(() => {
        onNotifyRef.current = onNotify;
    }, [onNotify]);

    useEffect(() => {
        const controller = new AbortController();
        const load = async () => {
            setIsLoading(true);
            try {
                const params = new URLSearchParams({ page: String(page), size: '20' });
                Object.entries(filters).forEach(([key, value]) => {
                    if (value) params.set(key, value);
                });
                const response = await fetch(`/api/admin/excel-download-logs?${params.toString()}`, {
                    signal: controller.signal
                });
                if (!response.ok) {
                    throw new Error(await response.text() || '다운로드 이력을 불러오지 못했습니다.');
                }
                const data = await response.json() as PageResponse;
                setItems(data.items);
                setPage(data.page);
                setTotalCount(data.totalCount);
                setTotalPages(data.totalPages);
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') return;
                onNotifyRef.current('error', error instanceof Error ? error.message : '다운로드 이력을 불러오지 못했습니다.');
            } finally {
                setIsLoading(false);
            }
        };
        void load();
        return () => controller.abort();
    }, [filters, page]);

    const applyFilters = () => {
        if (draftFilters.dateFrom && draftFilters.dateTo && draftFilters.dateTo < draftFilters.dateFrom) {
            onNotifyRef.current('error', '조회 종료일은 시작일보다 빠를 수 없습니다.');
            return;
        }
        setPage(1);
        setFilters({
            ...draftFilters,
            adminKeyword: draftFilters.adminKeyword.trim(),
            reasonKeyword: draftFilters.reasonKeyword.trim()
        });
    };

    const applyImmediateFilter = (key: 'dateFrom' | 'dateTo' | 'exportType' | 'status', value: string) => {
        const next = { ...draftFilters, [key]: value };
        setDraftFilters(next);
        if (next.dateFrom && next.dateTo && next.dateTo < next.dateFrom) {
            onNotifyRef.current('error', '조회 종료일은 시작일보다 빠를 수 없습니다.');
            return;
        }
        setPage(1);
        setFilters({ ...next, adminKeyword: filters.adminKeyword, reasonKeyword: filters.reasonKeyword });
    };

    const resetFilters = () => {
        setDraftFilters(EMPTY_FILTERS);
        setFilters(EMPTY_FILTERS);
        setPage(1);
    };

    const openDetail = async (seq: number) => {
        setIsDetailLoading(true);
        try {
            const response = await fetch(`/api/admin/excel-download-logs/${seq}`);
            if (!response.ok) {
                throw new Error(await response.text() || '다운로드 이력 상세를 불러오지 못했습니다.');
            }
            setDetail(await response.json() as ExcelDownloadLog);
        } catch (error) {
            onNotifyRef.current('error', error instanceof Error ? error.message : '다운로드 이력 상세를 불러오지 못했습니다.');
        } finally {
            setIsDetailLoading(false);
        }
    };

    return (
        <section className="space-y-4">
            <div className="rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-950">
                <div className="flex flex-col justify-between gap-4 border-b border-slate-200 p-4 dark:border-slate-800 md:flex-row md:items-center md:p-5">
                    <div>
                        <div className="flex items-center gap-2">
                            <Download className="shrink-0 h-5 w-5 text-blue-500" />
                            <h3 className="text-sm font-semibold md:text-base">다운로드이력</h3>
                        </div>
                        <p className="mt-1 text-xs text-slate-400">관리자 엑셀 다운로드 사유와 처리 결과를 조회합니다.</p>
                    </div>
                </div>

                <div className="grid gap-3 border-b border-slate-200 bg-slate-50/70 p-4 dark:border-slate-800 dark:bg-slate-900/30 md:grid-cols-2 xl:grid-cols-6">
                    <FilterInput type="date" label="시작일" value={draftFilters.dateFrom} onChange={(value) => applyImmediateFilter('dateFrom', value)} />
                    <FilterInput type="date" label="종료일" value={draftFilters.dateTo} onChange={(value) => applyImmediateFilter('dateTo', value)} />
                    <FilterSelect label="메뉴" value={draftFilters.exportType} onChange={(value) => applyImmediateFilter('exportType', value)} options={EXPORT_TYPE_OPTIONS} />
                    <FilterSelect label="상태" value={draftFilters.status} onChange={(value) => applyImmediateFilter('status', value)} options={[{ value: 'SUCCESS', label: '성공' }, { value: 'FAILED', label: '실패' }, { value: 'PROCESSING', label: '처리 중' }]} />
                    <FilterInput label="관리자" value={draftFilters.adminKeyword} placeholder="이름 또는 이메일" onChange={(value) => setDraftFilters((previous) => ({ ...previous, adminKeyword: value }))} />
                    <FilterInput label="사유" value={draftFilters.reasonKeyword} placeholder="다운로드 사유" onChange={(value) => setDraftFilters((previous) => ({ ...previous, reasonKeyword: value }))} />
                    <div className="flex gap-2 md:col-span-2 xl:col-span-6 xl:justify-end">
                        <button type="button" onClick={resetFilters} className="inline-flex items-center gap-2 rounded-lg border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-white dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-900"><FilterX className="h-4 w-4" />초기화</button>
                        <button type="button" onClick={applyFilters} className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-xs font-semibold text-white hover:bg-blue-700"><Search className="h-4 w-4" />조회</button>
                    </div>
                </div>

                <div className="overflow-x-auto">
                    <table className="w-full min-w-[1300px] border-collapse text-left text-xs">
                        <thead className="border-b border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900/50">
                            <tr><th scope="col" className="whitespace-nowrap p-4 text-right">ID</th><th className="p-4">요청일시</th><th className="p-4">메뉴</th><th className="p-4">관리자</th><th className="p-4">다운로드 사유</th><th className="p-4">적용 필터</th><th className="p-4 text-right">행 수</th><th className="p-4">파일</th><th className="p-4">IP</th><th className="p-4">상태</th><th className="p-4 text-right">상세</th></tr>
                        </thead>
                        <tbody>
                            {isLoading && items.length === 0 && <tr><td colSpan={11} className="p-10 text-center text-slate-400">다운로드 이력을 불러오는 중입니다.</td></tr>}
                            {!isLoading && items.length === 0 && <tr><td colSpan={11} className="p-10 text-center text-slate-400">조회된 다운로드 이력이 없습니다.</td></tr>}
                            {items.map((item) => (
                                <tr key={item.seq} className="border-b border-slate-200 hover:bg-slate-50/60 dark:border-slate-800 dark:hover:bg-slate-900/40">
                                    <td className="whitespace-nowrap p-4 text-right font-mono tabular-nums text-slate-700 dark:text-slate-200">{item.seq}</td>
                                    <td className="whitespace-nowrap p-4 text-slate-500 dark:text-slate-400">{formatDateTime(item.requestedAt)}</td>
                                    <td className="p-4 font-semibold text-slate-800 dark:text-slate-100">{item.menuName}</td>
                                    <td className="p-4"><div className="font-medium text-slate-700 dark:text-slate-200">{item.adminName}</div><div className="mt-1 text-[11px] text-slate-400">{item.adminEmail}</div></td>
                                    <td className="max-w-72 p-4"><div className="line-clamp-2" title={item.reason}>{item.reason}</div></td>
                                    <td className="max-w-72 p-4 text-slate-500 dark:text-slate-400"><div className="line-clamp-2" title={filterText(item.filterJson)}>{filterText(item.filterJson)}</div></td>
                                    <td className="p-4 text-right tabular-nums">{item.rowCount?.toLocaleString() ?? '-'}</td>
                                    <td className="max-w-48 p-4"><div className="truncate" title={item.fileName ?? ''}>{item.fileName || '-'}</div><div className="mt-1 text-[11px] text-slate-400">{formatBytes(item.fileSize)}</div></td>
                                    <td className="p-4 font-mono text-[11px] text-slate-500 dark:text-slate-400">{item.ipAddress || '-'}</td>
                                    <td className="p-4"><StatusBadge status={item.status} /></td>
                                    <td className="p-4 text-right"><button type="button" onClick={() => void openDetail(item.seq)} disabled={isDetailLoading} aria-label={`${item.seq}번 다운로드 이력 상세`} className="rounded-lg p-2 text-slate-500 hover:bg-slate-100 hover:text-blue-600 disabled:opacity-50 dark:hover:bg-slate-900"><Eye className="h-4 w-4" /></button></td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>

                <div className="flex flex-col gap-3 border-t border-slate-200 p-4 dark:border-slate-800 sm:flex-row sm:items-center sm:justify-between">
                    <span className="text-xs text-slate-400">총 {totalCount.toLocaleString()}건</span>
                    <div className="flex items-center justify-center gap-2">
                        <button type="button" onClick={() => setPage((value) => Math.max(1, value - 1))} disabled={page <= 1 || isLoading} className="rounded-lg border border-slate-200 p-2 text-slate-500 disabled:opacity-40 dark:border-slate-800"><ChevronLeft className="h-4 w-4" /></button>
                        <span className="min-w-20 text-center text-xs font-semibold">{page} / {Math.max(1, totalPages)}</span>
                        <button type="button" onClick={() => setPage((value) => Math.min(totalPages, value + 1))} disabled={page >= totalPages || isLoading} className="rounded-lg border border-slate-200 p-2 text-slate-500 disabled:opacity-40 dark:border-slate-800"><ChevronRight className="h-4 w-4" /></button>
                    </div>
                </div>
            </div>

            {detail && <DetailModal item={detail} onClose={() => setDetail(null)} />}
        </section>
    );
};

const FilterInput = ({ label, value, onChange, type = 'text', placeholder }: { label: string; value: string; onChange: (value: string) => void; type?: string; placeholder?: string }) => <label className="space-y-1.5"><span className="text-[11px] font-semibold text-slate-500 dark:text-slate-400">{label}</span><input type={type} value={value} placeholder={placeholder} onChange={(event) => onChange(event.target.value)} className={inputClass} /></label>;

const FilterSelect = ({ label, value, onChange, options }: { label: string; value: string; onChange: (value: string) => void; options: { value: string; label: string }[] }) => <label className="space-y-1.5"><span className="text-[11px] font-semibold text-slate-500 dark:text-slate-400">{label}</span><select value={value} onChange={(event) => onChange(event.target.value)} className={inputClass}><option value="">전체</option>{options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select></label>;

const StatusBadge = ({ status }: { status: ExcelDownloadLog['status'] }) => {
    const className = status === 'SUCCESS'
        ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300'
        : status === 'FAILED'
            ? 'bg-rose-50 text-rose-700 dark:bg-rose-950/40 dark:text-rose-300'
            : 'bg-amber-50 text-amber-700 dark:bg-amber-950/40 dark:text-amber-300';
    return <span className={`inline-flex rounded-full px-2.5 py-1 text-[11px] font-semibold ${className}`}>{STATUS_LABELS[status]}</span>;
};

const DetailModal = ({ item, onClose }: { item: ExcelDownloadLog; onClose: () => void }) => (
    <div className="fixed inset-0 z-[190] flex items-center justify-center bg-slate-950/60 p-4 backdrop-blur-sm" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
        <DraggableModal className="max-h-[90vh] w-full max-w-3xl overflow-y-auto rounded-2xl border border-slate-200 bg-white shadow-2xl dark:border-slate-800 dark:bg-slate-950">
            <div data-modal-drag-handle className="cursor-move select-none touch-none sticky top-0 flex items-center justify-between border-b border-slate-200 bg-white px-5 py-4 dark:border-slate-800 dark:bg-slate-950"><div><h3 className="font-semibold">다운로드 이력 #{item.seq}</h3><p className="mt-1 text-xs text-slate-400">{item.menuName} · {formatDateTime(item.requestedAt)}</p></div><button type="button" onClick={onClose} aria-label="닫기" className="rounded-lg p-2 text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-900"><X className="h-5 w-5" /></button></div>
            <div className="grid gap-4 p-5 sm:grid-cols-2">
                <DetailItem label="처리 상태" value={STATUS_LABELS[item.status]} />
                <DetailItem label="완료일시" value={formatDateTime(item.completedAt)} />
                <DetailItem label="관리자" value={`${item.adminName} (${item.adminEmail})`} />
                <DetailItem label="접속 IP" value={item.ipAddress || '-'} mono />
                <DetailItem label="결과 행 수" value={item.rowCount == null ? '-' : `${item.rowCount.toLocaleString()}행`} />
                <DetailItem label="파일" value={item.fileName ? `${item.fileName} (${formatBytes(item.fileSize)})` : '-'} />
                <DetailItem label="다운로드 사유" value={item.reason} wide />
                <DetailItem label="적용 필터" value={prettyFilterJson(item.filterJson)} wide pre />
                {item.status === 'FAILED' && <DetailItem label="실패 정보" value={`${item.failureCode || 'EXPORT_FAILED'} · ${item.failureMessage || '처리 중 오류가 발생했습니다.'}`} wide />}
                <DetailItem label="User-Agent" value={item.userAgent || '-'} wide />
            </div>
        </DraggableModal>
    </div>
);

const DetailItem = ({ label, value, wide = false, mono = false, pre = false }: { label: string; value: string; wide?: boolean; mono?: boolean; pre?: boolean }) => <div className={`rounded-xl border border-slate-200 bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-900/50 ${wide ? 'sm:col-span-2' : ''}`}><div className="text-[11px] font-semibold text-slate-400">{label}</div><div className={`mt-2 break-words text-sm text-slate-700 dark:text-slate-200 ${mono ? 'font-mono' : ''} ${pre ? 'whitespace-pre-wrap font-mono text-xs leading-5' : ''}`}>{value}</div></div>;

const inputClass = 'w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 outline-none focus:border-blue-500 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100';

const filterText = (value?: string | null) => {
    if (!value) return '-';
    try {
        const parsed = JSON.parse(value) as Record<string, unknown>;
        const entries = Object.entries(parsed).filter(([, item]) => item !== null && item !== '');
        return entries.length ? entries.map(([key, item]) => `${key}: ${String(item)}`).join(', ') : '전체';
    } catch {
        return value;
    }
};

const prettyFilterJson = (value?: string | null) => {
    if (!value) return '{}';
    try {
        return JSON.stringify(JSON.parse(value), null, 2);
    } catch {
        return value;
    }
};

const formatDateTime = (value?: string | null) => {
    if (!value) return '-';
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'medium' }).format(date);
};

const formatBytes = (value?: number | null) => {
    if (value == null) return '-';
    if (value < 1024) return `${value} B`;
    if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
    return `${(value / (1024 * 1024)).toFixed(1)} MB`;
};
