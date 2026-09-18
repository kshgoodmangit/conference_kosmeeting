import { DraggableModal } from './DraggableModal';
import { useEffect, useMemo, useRef, useState, type FormEvent, type MouseEvent as ReactMouseEvent, type ReactNode } from 'react';
import {
    Ban,
    CalendarDays,
    ChevronLeft,
    ChevronRight,
    Download,
    EllipsisVertical,
    FilterX,
    Mail,
    Pencil,
    Plus,
    Save,
    Search,
    Trash2,
    UserCheck,
    X
} from 'lucide-react';
import type { NotificationType } from './NotificationToast';
import { useConfirm } from './confirmDialogContext';
import { useExcelDownloadDialog } from './excelDownloadDialogContext';
import { downloadExcelFile } from '../excelDownload';
import { getStoredAdminConferenceSeq } from '../adminSession';
import { MailComposeModal, type MailRecipient } from './MailComposeModal';

interface PreRegistration {
    seq: number;
    registrationNumber: string;
    memberSeq: number;
    conferenceSeq?: number | null;
    eventName?: string | null;
    memberType: string;
    email: string;
    firstName: string;
    lastName: string;
    institution: string;
    department?: string | null;
    positionTitle?: string | null;
    country?: string | null;
    mobile: string;
    categorySeq: number;
    categoryCode: string;
    categoryName: string;
    periodType: 'EARLY_BIRD' | 'REGULAR';
    currency: string;
    feeAmount: number;
    optionAmount?: number | null;
    totalAmount?: number | null;
    options?: PreRegistrationOptionItem[];
    applicationStatus: 'SUBMITTED' | 'CANCELLED';
    paymentStatus: 'UNPAID' | 'PAID' | 'REFUNDED' | 'FAILED';
    paymentMethod?: string | null;
    paymentTransactionId?: string | null;
    paidAmount?: number | null;
    paidAt?: string | null;
    privacyAgreed: boolean;
    privacyAgreedAt?: string | null;
    termsAgreed: boolean;
    termsAgreedAt?: string | null;
    cancelledAt?: string | null;
    adminMemo?: string | null;
    createdAt: string;
    updatedAt: string;
}

interface PreRegistrationPageResponse {
    items: PreRegistration[];
    page: number;
    size: number;
    totalPages: number;
    totalCount: number;
    submittedCount: number;
    cancelledCount: number;
    paidCount: number;
    unpaidCount: number;
    totalPaidKrwAmount: number;
    totalPaidUsdAmount: number;
}

interface CategoryOption {
    categorySeq: number;
    categoryCode: string;
    categoryName: string;
}

interface ConferenceOption {
    seq: number;
    eventName: string;
    registrationCurrency: string;
}

interface RegistrationCatalogOption {
    optionSeq: number;
    optionName: string;
    description: string;
    currency: string;
    unitPrice: number;
    maxPerPerson: number;
    remainingCapacity?: number | null;
    enabled: boolean;
}

interface RegistrationFilterOption {
    optionSeq: number;
    optionName: string;
    eventName?: string | null;
}

interface PreRegistrationOptionItem {
    seq: number;
    optionSeq: number;
    optionName: string;
    optionDescription: string;
    currency: string;
    unitPrice: number;
    quantity: number;
    amount: number;
}

interface PreRegistrationUpdatePayload {
    memberSeq?: number;
    conferenceSeq?: number;
    options?: { optionSeq: number; quantity: number }[];
    societyLicenseNumber?: string;
    societyMemberName?: string;
    categorySeq: number;
    periodType: 'EARLY_BIRD' | 'REGULAR';
    currency: string;
    feeAmount: number;
    applicationStatus: 'SUBMITTED' | 'CANCELLED';
    paymentStatus?: PreRegistration['paymentStatus'];
    adminMemo: string | null;
}

interface Props {
    onNotify: (type: NotificationType, message: string) => void;
}

interface Filters {
    keyword: string;
    categorySeq: string;
    optionSeq: string;
    periodType: string;
    applicationStatus: string;
    paymentStatus: string;
    dateFrom: string;
    dateTo: string;
}

const PAGE_SIZE = 20;
const ACTION_MENU_WIDTH = 160;
const EMPTY_FILTERS: Filters = {
    keyword: '',
    categorySeq: '',
    optionSeq: '',
    periodType: '',
    applicationStatus: '',
    paymentStatus: '',
    dateFrom: '',
    dateTo: ''
};

const dateTimeFormatter = new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
});

export const PreRegistrationManagementPage = ({ onNotify }: Props) => {
    const confirm = useConfirm();
    const requestExcelDownload = useExcelDownloadDialog();
    const [items, setItems] = useState<PreRegistration[]>([]);
    const [categories, setCategories] = useState<CategoryOption[]>([]);
    const [filterOptions, setFilterOptions] = useState<RegistrationFilterOption[]>([]);
    const [conferences, setConferences] = useState<ConferenceOption[]>([]);
    const [draftFilters, setDraftFilters] = useState<Filters>(EMPTY_FILTERS);
    const [filters, setFilters] = useState<Filters>(EMPTY_FILTERS);
    const [currentPage, setCurrentPage] = useState(1);
    const [totalPages, setTotalPages] = useState(1);
    const [totalCount, setTotalCount] = useState(0);
    const [submittedCount, setSubmittedCount] = useState(0);
    const [cancelledCount, setCancelledCount] = useState(0);
    const [paidCount, setPaidCount] = useState(0);
    const [unpaidCount, setUnpaidCount] = useState(0);
    const [paidAmounts, setPaidAmounts] = useState({ KRW: 0, USD: 0 });
    const [registrationCurrency, setRegistrationCurrency] = useState('USD');
    const [isLoading, setIsLoading] = useState(false);
    const [isDownloading, setIsDownloading] = useState(false);
    const [errorMessage, setErrorMessage] = useState('');
    const [reloadKey, setReloadKey] = useState(0);
    const [selectedSeq, setSelectedSeq] = useState<number | null>(null);
    const [selectedItem, setSelectedItem] = useState<PreRegistration | null>(null);
    const [isDetailLoading, setIsDetailLoading] = useState(false);
    const [activeDropdownId, setActiveDropdownId] = useState<number | null>(null);
    const [dropdownPosition, setDropdownPosition] = useState({ top: 0, left: 0 });
    const [cancellingSeq, setCancellingSeq] = useState<number | null>(null);
    const [deletingSeq, setDeletingSeq] = useState<number | null>(null);
    const [editingItem, setEditingItem] = useState<PreRegistration | null>(null);
    const [isFormOpen, setIsFormOpen] = useState(false);
    const [isSaving, setIsSaving] = useState(false);
    const [mailSelection, setMailSelection] = useState<Record<number, PreRegistration>>({});
    const [mailRecipients, setMailRecipients] = useState<MailRecipient[] | null>(null);
    const selectPageRef = useRef<HTMLInputElement>(null);
    const savingRef = useRef(false);
    const onNotifyRef = useRef(onNotify);

    useEffect(() => {
        onNotifyRef.current = onNotify;
    }, [onNotify]);

    useEffect(() => {
        const controller = new AbortController();

        const loadCategories = async () => {
            try {
                const [categoryResponse, optionResponse, settingsResponse] = await Promise.all([
                    fetch('/api/admin/pre-registrations/categories', { signal: controller.signal }),
                    fetch('/api/admin/pre-registrations/filter-options', { signal: controller.signal }),
                    fetch('/api/admin/conference-settings', { signal: controller.signal })
                ]);
                if (!categoryResponse.ok) {
                    throw new Error(await categoryResponse.text() || '등록 구분을 불러오지 못했습니다.');
                }
                setCategories(await categoryResponse.json() as CategoryOption[]);
                if (!optionResponse.ok) throw new Error(await optionResponse.text() || '옵션 검색 조건을 불러오지 못했습니다.');
                setFilterOptions(await optionResponse.json() as RegistrationFilterOption[]);
                if (!settingsResponse.ok) throw new Error(await settingsResponse.text() || '학회 설정을 불러오지 못했습니다.');
                const settings = await settingsResponse.json() as ConferenceOption[];
                setConferences(settings);
                const selectedConferenceSeq = getStoredAdminConferenceSeq();
                const selectedSettings = settings.find((conference) => conference.seq === selectedConferenceSeq);
                setRegistrationCurrency(selectedSettings?.registrationCurrency?.trim() || 'USD');
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') return;
                const message = error instanceof Error ? error.message : '등록 구분을 불러오지 못했습니다.';
                onNotifyRef.current('error', message);
            }
        };

        void loadCategories();
        return () => controller.abort();
    }, []);

    useEffect(() => {
        const controller = new AbortController();

        const load = async () => {
            setIsLoading(true);
            setErrorMessage('');
            try {
                const params = buildQuery(filters);
                params.set('page', String(currentPage));
                params.set('size', String(PAGE_SIZE));
                const response = await fetch(`/api/admin/pre-registrations?${params.toString()}`, {
                    signal: controller.signal
                });
                if (!response.ok) {
                    throw new Error(await response.text() || '사전등록 내역을 불러오지 못했습니다.');
                }

                const data = await response.json() as PreRegistrationPageResponse;
                if (controller.signal.aborted) return;
                setItems(data.items);
                setMailSelection((current) => {
                    const next = { ...current };
                    data.items.forEach((item) => { if (next[item.seq]) next[item.seq] = item; });
                    return next;
                });
                setCurrentPage(data.page);
                setTotalPages(data.totalPages);
                setTotalCount(data.totalCount);
                setSubmittedCount(data.submittedCount);
                setCancelledCount(data.cancelledCount);
                setPaidCount(data.paidCount);
                setUnpaidCount(data.unpaidCount);
                setPaidAmounts({ KRW: data.totalPaidKrwAmount, USD: data.totalPaidUsdAmount });
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') return;
                const message = error instanceof Error ? error.message : '사전등록 내역을 불러오지 못했습니다.';
                setErrorMessage(message);
                onNotifyRef.current('error', message);
            } finally {
                if (!controller.signal.aborted) setIsLoading(false);
            }
        };

        void load();
        return () => controller.abort();
    }, [currentPage, filters, reloadKey]);

    useEffect(() => {
        if (selectedSeq === null) {
            return;
        }

        const controller = new AbortController();
        const loadDetail = async () => {
            setIsDetailLoading(true);
            try {
                const response = await fetch(`/api/admin/pre-registrations/${selectedSeq}`, {
                    signal: controller.signal
                });
                if (!response.ok) {
                    throw new Error(await response.text() || '사전등록 상세정보를 불러오지 못했습니다.');
                }
                setSelectedItem(await response.json() as PreRegistration);
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') return;
                const message = error instanceof Error ? error.message : '사전등록 상세정보를 불러오지 못했습니다.';
                onNotifyRef.current('error', message);
                setSelectedSeq(null);
            } finally {
                setIsDetailLoading(false);
            }
        };

        void loadDetail();
        return () => controller.abort();
    }, [selectedSeq]);

    const pageNumbers = useMemo(() => {
        const firstPage = Math.max(1, Math.min(currentPage - 2, Math.max(1, totalPages - 4)));
        return Array.from({ length: Math.min(5, totalPages - firstPage + 1) }, (_, index) => firstPage + index);
    }, [currentPage, totalPages]);

    const selectedMailCount = Object.keys(mailSelection).length;
    const selectedOnPageCount = items.filter((item) => mailSelection[item.seq]).length;
    const allOnPageSelected = items.length > 0 && selectedOnPageCount === items.length;

    useEffect(() => {
        if (selectPageRef.current) {
            selectPageRef.current.indeterminate = selectedOnPageCount > 0 && !allOnPageSelected;
        }
    }, [selectedOnPageCount, allOnPageSelected]);

    const toggleMailSelection = (rows: PreRegistration[], checked: boolean) => {
        if (isLoading || errorMessage) return;
        setMailSelection((current) => {
            const next = { ...current };
            rows.forEach((item) => {
                if (checked) next[item.seq] = item;
                else delete next[item.seq];
            });
            return next;
        });
    };

    const openMailComposer = () => {
        if (!selectedMailCount || isLoading || errorMessage) return;
        setMailRecipients(Object.values(mailSelection).map((item) => ({
            id: `pre-registration-${item.seq}`,
            sourceSeq: item.seq,
            name: fullName(item),
            email: item.email,
            affiliation: item.institution
        })));
    };

    const handleSearch = (event: FormEvent) => {
        event.preventDefault();
        if (draftFilters.dateFrom && draftFilters.dateTo && draftFilters.dateTo < draftFilters.dateFrom) {
            onNotifyRef.current('error', '조회 종료일은 시작일보다 빠를 수 없습니다.');
            return;
        }
        setCurrentPage(1);
        setMailSelection({});
        setFilters({ ...draftFilters, keyword: draftFilters.keyword.trim() });
    };

    const applyImmediateFilter = (key: Exclude<keyof Filters, 'keyword'>, value: string) => {
        const nextFilters = { ...draftFilters, [key]: value };
        setDraftFilters(nextFilters);
        if (nextFilters.dateFrom && nextFilters.dateTo && nextFilters.dateTo < nextFilters.dateFrom) {
            onNotifyRef.current('error', '조회 종료일은 시작일보다 빠를 수 없습니다.');
            return;
        }
        setCurrentPage(1);
        setMailSelection({});
        setFilters({ ...nextFilters, keyword: filters.keyword });
    };

    const resetFilters = () => {
        setMailSelection({});
        setDraftFilters(EMPTY_FILTERS);
        setFilters(EMPTY_FILTERS);
        setCurrentPage(1);
    };

    const downloadExcel = async () => {
        setIsDownloading(true);
        try {
            const completed = await requestExcelDownload({
                menuName: '사전등록관리',
                filters: [
                    { label: '검색어', value: filters.keyword || '전체' },
                    { label: '등록 구분', value: categories.find((item) => String(item.categorySeq) === filters.categorySeq)?.categoryName ?? '전체' },
                    { label: '옵션', value: filterOptions.find((item) => String(item.optionSeq) === filters.optionSeq)?.optionName ?? '전체' },
                    { label: '등록 기간', value: filters.periodType || '전체' },
                    { label: '신청 상태', value: filters.applicationStatus || '전체' },
                    { label: '결제 상태', value: filters.paymentStatus || '전체' },
                    { label: '신청일', value: filters.dateFrom || filters.dateTo ? `${filters.dateFrom || '처음'} ~ ${filters.dateTo || '현재'}` : '전체' }
                ],
                execute: async (reason) => {
                    await downloadExcelFile('/api/admin/pre-registrations/excel', {
                        reason,
                        keyword: filters.keyword || null,
                        categorySeq: filters.categorySeq ? Number(filters.categorySeq) : null,
                        optionSeq: filters.optionSeq ? Number(filters.optionSeq) : null,
                        periodType: filters.periodType || null,
                        applicationStatus: filters.applicationStatus || null,
                        paymentStatus: filters.paymentStatus || null,
                        dateFrom: filters.dateFrom || null,
                        dateTo: filters.dateTo || null
                    }, 'pre-registrations.xlsx');
                },
                onError: (message) => onNotifyRef.current('error', message)
            });
            if (completed) onNotifyRef.current('success', '엑셀 파일을 다운로드했습니다.');
        } finally {
            setIsDownloading(false);
        }
    };

    const toggleActionMenu = (registrationSeq: number, event: ReactMouseEvent<HTMLButtonElement>) => {
        event.stopPropagation();
        const rect = event.currentTarget.getBoundingClientRect();
        setDropdownPosition({
            top: rect.bottom + 4,
            left: Math.max(8, rect.right - ACTION_MENU_WIDTH)
        });
        setActiveDropdownId((value) => value === registrationSeq ? null : registrationSeq);
    };

    const cancelPayment = async (item: PreRegistration) => {
        setActiveDropdownId(null);
        if (item.paymentStatus !== 'PAID') return;

        if (!await confirm({
            title: '결제 취소',
            message: `등록번호 ${item.registrationNumber}의 결제를 취소 처리하시겠습니까? 결제 상태가 환불로 변경됩니다.`,
            confirmText: '결제 취소',
            tone: 'danger'
        })) {
            return;
        }

        setCancellingSeq(item.seq);
        setErrorMessage('');
        try {
            const response = await fetch(`/api/admin/pre-registrations/${item.seq}/cancel-payment`, {
                method: 'POST'
            });
            if (!response.ok) {
                throw new Error(await response.text() || '결제 취소 처리에 실패했습니다.');
            }
            onNotifyRef.current('success', '결제가 취소 처리되었습니다.');
            setReloadKey((value) => value + 1);
        } catch (error) {
            const message = error instanceof Error ? error.message : '결제 취소 처리에 실패했습니다.';
            setErrorMessage(message);
            onNotifyRef.current('error', message);
        } finally {
            setCancellingSeq(null);
        }
    };

    const openEdit = (item: PreRegistration) => {
        (document.activeElement as HTMLElement | null)?.closest('td')?.querySelector<HTMLButtonElement>('button')?.focus();
        setActiveDropdownId(null);
        setEditingItem(item);
        setIsFormOpen(true);
    };

    const saveRegistration = async (seq: number | null, payload: PreRegistrationUpdatePayload) => {
        if (savingRef.current) return;
        savingRef.current = true;
        setIsSaving(true);
        setErrorMessage('');
        try {
            const response = await fetch(seq === null ? '/api/admin/pre-registrations' : `/api/admin/pre-registrations/${seq}`, {
                method: seq === null ? 'POST' : 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            if (!response.ok) {
                throw new Error(await response.text() || '사전등록 저장에 실패했습니다.');
            }
            const saved = await response.json() as PreRegistration;
            setMailSelection((current) => current[saved.seq] ? { ...current, [saved.seq]: saved } : current);
            onNotifyRef.current('success', seq === null ? `사전등록 내역이 등록되었습니다. (${saved.registrationNumber})` : '사전등록 내역이 수정되었습니다.');
            setIsFormOpen(false);
            setEditingItem(null);
            setSelectedSeq(null);
            if (seq === null) setCurrentPage(1);
            setReloadKey((value) => value + 1);
        } catch (error) {
            const message = error instanceof Error ? error.message : '사전등록 저장에 실패했습니다.';
            onNotifyRef.current('error', message);
        } finally {
            savingRef.current = false;
            setIsSaving(false);
        }
    };

    const deleteRegistration = async (item: PreRegistration) => {
        setActiveDropdownId(null);
        if (item.paymentStatus === 'PAID') return;

        if (!await confirm({
            title: '사전등록 삭제',
            message: `등록번호 ${item.registrationNumber}의 사전등록 내역을 삭제하시겠습니까? 삭제한 내역은 복구할 수 없습니다.`,
            confirmText: '삭제',
            tone: 'danger'
        })) {
            return;
        }

        setDeletingSeq(item.seq);
        setErrorMessage('');
        try {
            const response = await fetch(`/api/admin/pre-registrations/${item.seq}`, {
                method: 'DELETE'
            });
            if (!response.ok) {
                throw new Error(await response.text() || '사전등록 삭제에 실패했습니다.');
            }
            onNotifyRef.current('success', '사전등록 내역이 삭제되었습니다.');
            setMailSelection((current) => {
                const next = { ...current };
                delete next[item.seq];
                return next;
            });
            setSelectedSeq(null);
            setReloadKey((value) => value + 1);
        } catch (error) {
            const message = error instanceof Error ? error.message : '사전등록 삭제에 실패했습니다.';
            setErrorMessage(message);
            onNotifyRef.current('error', message);
        } finally {
            setDeletingSeq(null);
        }
    };

    return (
        <section onClick={() => setActiveDropdownId(null)} className="rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-950">
            <div className="flex flex-col justify-between gap-4 border-b border-slate-200 p-4 dark:border-slate-800 lg:flex-row lg:items-center md:p-5">
                <div>
                    <div className="flex items-center gap-2">
                        <UserCheck className="shrink-0 h-5 w-5 text-blue-500" />
                        <h3 className="text-sm font-semibold md:text-base">사전등록관리</h3>
                    </div>
                    <p className="mt-1 text-xs text-slate-400">로그인 회원의 사전등록 및 결제 현황을 조회합니다.</p>
                </div>
                <div className="flex flex-col gap-2 sm:flex-row">
                    <button type="button" onClick={openMailComposer} disabled={!selectedMailCount || isLoading || !!errorMessage}
                        title={selectedMailCount ? '선택한 수신자에게 보낼 메일 작성' : '목록에서 메일 수신자를 먼저 선택해 주세요.'}
                        className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-3 py-2 text-xs font-semibold text-white enabled:hover:bg-blue-700 disabled:opacity-50 dark:bg-blue-600 dark:text-white dark:enabled:hover:bg-blue-700">
                        <Mail className="h-4 w-4" />메일 발송{selectedMailCount > 0 && ` (${selectedMailCount}명)`}
                    </button>
                    <button type="button" onClick={() => { setEditingItem(null); setIsFormOpen(true); }}
                        className="inline-flex items-center justify-center gap-2 rounded-lg bg-emerald-600 px-3 py-2 text-xs font-semibold text-white hover:bg-emerald-700 dark:bg-emerald-600 dark:text-white dark:hover:bg-emerald-700">
                        <Plus className="h-4 w-4" /> 사전등록 추가
                    </button>
                    <button
                        type="button"
                        onClick={() => void downloadExcel()}
                        disabled={isDownloading || isLoading}
                        className="inline-flex items-center justify-center gap-2 rounded-lg bg-emerald-600 px-3 py-2 text-xs font-semibold text-white hover:bg-emerald-700 disabled:opacity-50"
                    >
                        <Download className="h-4 w-4" /> {isDownloading ? '다운로드 중' : '엑셀 다운로드'}
                    </button>
                </div>
            </div>

            <div className="grid grid-cols-2 border-b border-slate-200 dark:border-slate-800 lg:grid-cols-6">
                <SummaryCard label="전체 신청" value={`${totalCount.toLocaleString()}건`} />
                <SummaryCard label="신청 완료" value={`${submittedCount.toLocaleString()}건`} accent="text-blue-600 dark:text-blue-400" />
                <SummaryCard label="결제 완료" value={`${paidCount.toLocaleString()}건`} accent="text-emerald-600 dark:text-emerald-400" />
                <SummaryCard label="결제 대기" value={`${unpaidCount.toLocaleString()}건`} accent="text-amber-600 dark:text-amber-400" />
                <SummaryCard label="취소" value={`${cancelledCount.toLocaleString()}건`} accent="text-rose-600 dark:text-rose-400" />
                <div className="border-b border-slate-200 p-4 dark:border-slate-800">
                    <div className="text-xs font-semibold text-slate-400 dark:text-slate-400">결제 합계</div>
                    <div className="mt-1 space-y-1 text-violet-600 dark:text-violet-400">
                        {(['KRW', 'USD'] as const).map((currency) => (
                            <div key={currency} className="flex flex-wrap items-baseline justify-between gap-x-2">
                                <span className="text-[10px] font-semibold text-slate-400 dark:text-slate-400">{currency}</span>
                                <span className="text-base font-bold tabular-nums">{isLoading ? '집계 중...' : errorMessage ? '-' : formatCurrency(paidAmounts[currency], currency)}</span>
                            </div>
                        ))}
                    </div>
                </div>
            </div>

            <form onSubmit={handleSearch} className="border-b border-slate-200 bg-slate-50/70 p-4 dark:border-slate-800 dark:bg-slate-900/40 md:p-5">
                <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-4">
                    <label>
                        <span className="mb-1 block text-[11px] font-semibold text-slate-500">검색어</span>
                        <div className="relative">
                            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                            <input
                                value={draftFilters.keyword}
                                onChange={(event) => setDraftFilters((previous) => ({ ...previous, keyword: event.target.value }))}
                                className={inputClassName('pl-9')}
                                placeholder="등록번호, 이름, 이메일, 소속 검색"
                            />
                        </div>
                    </label>
                    <SelectFilter label="옵션" value={draftFilters.optionSeq} onChange={(value) => applyImmediateFilter('optionSeq', value)}>
                        <option value="">전체 옵션</option>
                        {filterOptions.map((option) => <option key={option.optionSeq} value={option.optionSeq}>{option.optionName}{option.eventName ? ` · ${option.eventName}` : ''}</option>)}
                    </SelectFilter>
                    <SelectFilter label="등록 구분" value={draftFilters.categorySeq} onChange={(value) => applyImmediateFilter('categorySeq', value)}>
                        <option value="">전체 등록 구분</option>
                        {categories.map((category) => <option key={category.categorySeq} value={category.categorySeq}>{category.categoryName}</option>)}
                    </SelectFilter>
                    <SelectFilter label="등록 기간" value={draftFilters.periodType} onChange={(value) => applyImmediateFilter('periodType', value)}>
                        <option value="">전체 등록 기간</option>
                        <option value="EARLY_BIRD">Early Bird</option>
                        <option value="REGULAR">Regular</option>
                    </SelectFilter>
                    <SelectFilter label="신청 상태" value={draftFilters.applicationStatus} onChange={(value) => applyImmediateFilter('applicationStatus', value)}>
                        <option value="">전체 신청 상태</option>
                        <option value="SUBMITTED">신청 완료</option>
                        <option value="CANCELLED">취소</option>
                    </SelectFilter>
                    <SelectFilter label="결제 상태" value={draftFilters.paymentStatus} onChange={(value) => applyImmediateFilter('paymentStatus', value)}>
                        <option value="">전체 결제 상태</option>
                        <option value="UNPAID">결제 대기</option>
                        <option value="PAID">결제 완료</option>
                        <option value="REFUNDED">환불</option>
                        <option value="FAILED">결제 실패</option>
                    </SelectFilter>
                    <DateFilter label="신청 시작일" value={draftFilters.dateFrom} onChange={(value) => applyImmediateFilter('dateFrom', value)} />
                    <DateFilter label="신청 종료일" value={draftFilters.dateTo} onChange={(value) => applyImmediateFilter('dateTo', value)} />
                </div>
                <div className="mt-3 flex flex-col justify-end gap-2 sm:flex-row">
                    <button type="button" onClick={resetFilters} className="inline-flex items-center justify-center gap-2 rounded-lg border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-white dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-900">
                        <FilterX className="h-4 w-4" /> 초기화
                    </button>
                    <button type="submit" className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-5 py-2 text-xs font-semibold text-white hover:bg-blue-700">
                        <Search className="h-4 w-4" /> 조회
                    </button>
                </div>
            </form>

            {errorMessage && <div className="m-4 rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 dark:border-rose-900/60 dark:bg-rose-950/30 dark:text-rose-300 md:m-5">{errorMessage}</div>}

            <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-200 px-4 py-3 text-xs dark:border-slate-800 md:px-5">
                <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                    <span aria-live="polite" className="font-semibold text-blue-600 dark:text-blue-400">메일 수신자 {selectedMailCount}명 선택</span>
                    <span className="text-slate-500 dark:text-slate-400">전체 선택은 현재 페이지에 적용됩니다. 페이지 이동 시 유지되며 조회·필터 변경 시 해제됩니다.</span>
                </div>
                <button type="button" onClick={() => setMailSelection({})} disabled={!selectedMailCount}
                    className="rounded-lg border border-slate-200 px-3 py-2 font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900">선택 해제</button>
            </div>

            <div className="overflow-x-auto">
                <table className="w-full min-w-[1220px] border-collapse text-left text-xs md:text-sm">
                    <thead className="border-b border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900/50">
                        <tr>
                            <th className="w-12 p-4 text-center">
                                <input ref={selectPageRef} type="checkbox" checked={allOnPageSelected} disabled={isLoading || !!errorMessage || !items.length}
                                    onChange={(event) => toggleMailSelection(items, event.target.checked)} aria-label="현재 페이지 메일 수신자 전체 선택"
                                    className="h-4 w-4 rounded border-slate-300 accent-blue-600 dark:border-slate-700 dark:accent-blue-500" />
                            </th>
                            <th className="p-4">등록번호</th><th className="p-4">신청자</th><th className="p-4">소속</th>
                            <th className="p-4">등록 구분</th><th className="p-4">신청금액</th><th className="p-4">신청 상태</th>
                            <th className="p-4">결제 상태</th><th className="p-4">신청일</th><th className="w-16 p-4 text-center">기능</th>
                        </tr>
                    </thead>
                    <tbody>
                        {isLoading && <tr><td colSpan={10} className="p-10 text-center text-slate-400 dark:text-slate-400">사전등록 내역을 불러오는 중입니다.</td></tr>}
                        {!isLoading && !errorMessage && items.length === 0 && <tr><td colSpan={10} className="p-10 text-center text-slate-400 dark:text-slate-400">조회된 사전등록 내역이 없습니다.</td></tr>}
                        {!isLoading && errorMessage && items.length === 0 && <tr><td colSpan={10} className="p-10 text-center text-slate-400 dark:text-slate-400">목록을 불러오지 못했습니다. 조회 버튼으로 다시 시도해 주세요.</td></tr>}
                        {!isLoading && items.map((item) => (
                            <tr key={item.seq} className={`border-b border-slate-200 dark:border-slate-800 ${mailSelection[item.seq] ? 'bg-blue-50/70 hover:bg-blue-50 dark:bg-blue-950/30 dark:hover:bg-blue-950/50' : 'hover:bg-slate-50/60 dark:hover:bg-slate-900/40'}`}>
                                <td className="p-4 text-center">
                                    <input type="checkbox" checked={!!mailSelection[item.seq]} disabled={isLoading || !!errorMessage}
                                        onChange={(event) => toggleMailSelection([item], event.target.checked)} aria-label={`${fullName(item)} (${item.registrationNumber}) 메일 수신자 선택`}
                                        className="h-4 w-4 rounded border-slate-300 accent-blue-600 dark:border-slate-700 dark:accent-blue-500" />
                                </td>
                                <td className="p-4">
                                    <button type="button" onClick={() => setSelectedSeq(item.seq)} className="font-mono text-xs font-semibold text-blue-600 hover:underline dark:text-blue-400">
                                        {item.registrationNumber}
                                    </button>
                                </td>
                                <td className="p-4"><div className="font-semibold">{fullName(item)}</div><div className="mt-1 text-xs text-slate-400">{item.email}</div></td>
                                <td className="p-4"><div>{item.institution}</div><div className="mt-1 text-xs text-slate-400">{item.department || '-'}</div></td>
                                <td className="p-4"><div className="font-medium">{item.categoryName}</div><div className="mt-1 text-xs text-slate-400">{periodLabel(item.periodType)}</div></td>
                                <td className="p-4 font-semibold">{formatCurrency(item.totalAmount ?? item.feeAmount, item.currency)}</td>
                                <td className="p-4"><StatusBadge type="application" value={item.applicationStatus} /></td>
                                <td className="p-4"><StatusBadge type="payment" value={item.paymentStatus} /></td>
                                <td className="p-4 text-slate-500 dark:text-slate-400">{formatDateTime(item.createdAt)}</td>
                                <td className="relative p-4 text-center">
                                    <button type="button" onClick={(event) => toggleActionMenu(item.seq, event)} className="rounded-md p-1.5 text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800" aria-label={`${item.registrationNumber} 기능 메뉴`}>
                                        <EllipsisVertical className="h-4 w-4" />
                                    </button>
                                    {activeDropdownId === item.seq && (
                                        <div className="fixed z-[100] w-40 rounded-lg border border-slate-200 bg-white py-1 text-left shadow-lg dark:border-slate-800 dark:bg-slate-950" style={{ top: dropdownPosition.top, left: dropdownPosition.left }} onClick={(event) => event.stopPropagation()}>
                                            <button
                                                type="button"
                                                onClick={() => openEdit(item)}
                                                className="flex w-full items-center gap-2 px-4 py-2 text-sm hover:bg-slate-50 dark:hover:bg-slate-900"
                                            >
                                                <Pencil className="h-4 w-4 text-blue-500" />
                                                수정
                                            </button>
                                            <button
                                                type="button"
                                                onClick={() => void deleteRegistration(item)}
                                                disabled={item.paymentStatus === 'PAID' || deletingSeq === item.seq}
                                                title={item.paymentStatus === 'PAID' ? '결제 완료 건은 결제 취소 후 삭제할 수 있습니다.' : undefined}
                                                className="flex w-full items-center gap-2 px-4 py-2 text-sm text-rose-600 hover:bg-slate-50 disabled:cursor-not-allowed disabled:text-slate-300 dark:hover:bg-slate-900 dark:disabled:text-slate-700"
                                            >
                                                <Trash2 className="h-4 w-4" />
                                                {deletingSeq === item.seq ? '삭제 중' : '삭제'}
                                            </button>
                                            <button
                                                type="button"
                                                onClick={() => void cancelPayment(item)}
                                                disabled={item.paymentStatus !== 'PAID' || cancellingSeq === item.seq}
                                                className="flex w-full items-center gap-2 px-4 py-2 text-sm text-rose-600 hover:bg-slate-50 disabled:cursor-not-allowed disabled:text-slate-300 dark:hover:bg-slate-900 dark:disabled:text-slate-700"
                                            >
                                                <Ban className="h-4 w-4" />
                                                {cancellingSeq === item.seq ? '처리 중' : '결제 취소'}
                                            </button>
                                        </div>
                                    )}
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            <div className="flex flex-col items-center justify-between gap-4 border-t border-slate-200 p-4 dark:border-slate-800 md:flex-row md:p-5">
                <div className="text-xs font-medium text-slate-400">전체 <span className="font-bold text-slate-900 dark:text-slate-50">{totalCount.toLocaleString()}</span>건</div>
                <div className="flex items-center gap-1.5">
                    <PageButton disabled={currentPage === 1} onClick={() => setCurrentPage((page) => Math.max(1, page - 1))}><ChevronLeft className="h-4 w-4" /></PageButton>
                    {pageNumbers.map((page) => <button key={page} type="button" onClick={() => setCurrentPage(page)} className={`h-8 w-8 rounded-lg border text-xs font-bold ${currentPage === page ? 'border-blue-600 bg-blue-600 text-white' : 'border-slate-200 hover:bg-slate-50 dark:border-slate-800 dark:hover:bg-slate-900'}`}>{page}</button>)}
                    <PageButton disabled={currentPage === totalPages} onClick={() => setCurrentPage((page) => Math.min(totalPages, page + 1))}><ChevronRight className="h-4 w-4" /></PageButton>
                </div>
            </div>

            {selectedSeq !== null && <PreRegistrationDetailModal item={selectedItem?.seq === selectedSeq ? selectedItem : null} isLoading={isDetailLoading} onClose={() => setSelectedSeq(null)} />}
            <MailComposeModal sourceMenu="pre-registrations" isOpen={mailRecipients !== null} recipients={mailRecipients ?? []} onClose={() => setMailRecipients(null)} onNotify={onNotify} />
            {isFormOpen && (
                <PreRegistrationFormModal
                    item={editingItem}
                    categories={categories}
                    conferences={conferences}
                    defaultCurrency={registrationCurrency}
                    isSaving={isSaving}
                    onClose={() => { if (!savingRef.current) { setIsFormOpen(false); setEditingItem(null); } }}
                    onSave={saveRegistration}
                    onNotify={onNotify}
                />
            )}
        </section>
    );
};

interface MemberOption {
    seq: number;
    memberType: string;
    firstName: string;
    lastName: string;
    email: string;
    institution: string;
}

interface PreRegistrationFormModalProps {
    item: PreRegistration | null;
    categories: CategoryOption[];
    conferences: ConferenceOption[];
    defaultCurrency: string;
    isSaving: boolean;
    onClose: () => void;
    onSave: (seq: number | null, payload: PreRegistrationUpdatePayload) => Promise<void>;
    onNotify: (type: NotificationType, message: string) => void;
}

const modalInputClass = 'w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm font-normal text-slate-900 placeholder:text-slate-400 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50';
const modalLabelClass = 'space-y-1.5 text-sm font-semibold text-slate-700 dark:text-slate-200';

const PreRegistrationFormModal = ({ item, categories, conferences, defaultCurrency, isSaving, onClose, onSave, onNotify }: PreRegistrationFormModalProps) => {
    const conferenceSeq = String(item?.conferenceSeq ?? getStoredAdminConferenceSeq() ?? '');
    const selectedConferenceName = conferences.find((conference) => String(conference.seq) === conferenceSeq)?.eventName;
    const [categorySeq, setCategorySeq] = useState(String(item?.categorySeq ?? ''));
    const [periodType, setPeriodType] = useState<PreRegistrationUpdatePayload['periodType']>(item?.periodType ?? 'REGULAR');
    const [rates, setRates] = useState<{ seq: number; earlyBirdUsdFee: number | null; earlyBirdKrwFee: number | null; regularUsdFee: number | null; regularKrwFee: number | null }[]>([]);
    const [availableOptions, setAvailableOptions] = useState<RegistrationCatalogOption[]>([]);
    const [optionQuantities, setOptionQuantities] = useState<Record<number, number>>(() => Object.fromEntries(
        (item?.options ?? []).map((option) => [option.optionSeq, option.quantity])
    ));
    const [existingOptionQuantities, setExistingOptionQuantities] = useState<Record<number, number>>(() => Object.fromEntries(
        item?.applicationStatus === 'SUBMITTED'
            ? (item.options ?? []).map((option) => [option.optionSeq, option.quantity])
            : []
    ));
    const [isOptionLoading, setIsOptionLoading] = useState(false);
    const [isExistingOptionsLoading, setIsExistingOptionsLoading] = useState(Boolean(item));
    const [applicationStatus, setApplicationStatus] = useState<PreRegistrationUpdatePayload['applicationStatus']>(item?.applicationStatus ?? 'SUBMITTED');
    const [paymentStatus, setPaymentStatus] = useState<PreRegistration['paymentStatus']>(item?.paymentStatus ?? 'UNPAID');
    const [adminMemo, setAdminMemo] = useState(item?.adminMemo ?? '');
    const [memberKeyword, setMemberKeyword] = useState('');
    const [members, setMembers] = useState<MemberOption[]>([]);
    const [member, setMember] = useState<MemberOption | null>(null);
    const [isMemberLoading, setIsMemberLoading] = useState(false);
    const [memberSearchFailed, setMemberSearchFailed] = useState(false);
    const [useSociety, setUseSociety] = useState(false);
    const [societyLicense, setSocietyLicense] = useState('');
    const [societyName, setSocietyName] = useState('');
    const [societyQuote, setSocietyQuote] = useState<{ key: string; memberType: string } | null>(null);
    const [checkingSociety, setCheckingSociety] = useState(false);
    const societyLock = useRef(false);
    const memberType = item?.memberType ?? member?.memberType;
    const currency = memberType === 'domestic' ? 'KRW' : memberType === 'international' ? 'USD' : defaultCurrency;
    const rate = rates.find((entry) => String(entry.seq) === categorySeq);
    const amount = periodType === 'EARLY_BIRD'
        ? (currency === 'KRW' ? rate?.earlyBirdKrwFee : rate?.earlyBirdUsdFee)
        : (currency === 'KRW' ? rate?.regularKrwFee : rate?.regularUsdFee);
    const feeAmount = amount == null ? '' : String(amount);
    const societyKey = JSON.stringify([member?.seq, societyLicense.trim(), societyName.trim(), periodType, currency]);
    const optionAmount = useMemo(() => availableOptions.reduce(
        (total, option) => total + option.unitPrice * (optionQuantities[option.optionSeq] ?? 0), 0),
    [availableOptions, optionQuantities]);
    const totalAmount = (Number(feeAmount) || 0) + optionAmount;
    const dialogRef = useRef<HTMLDivElement>(null);
    const submitRef = useRef(false);
    const notifyRef = useRef(onNotify);

    useEffect(() => { notifyRef.current = onNotify; }, [onNotify]);
    useEffect(() => {
        const controller = new AbortController();
        void (async () => {
            try {
                const response = await fetch('/api/admin/registration-fees', { signal: controller.signal });
                if (!response.ok) throw new Error('설정된 등록비를 불러오지 못했습니다.');
                setRates(await response.json());
            } catch (error) {
                if (!controller.signal.aborted) notifyRef.current('error', error instanceof Error ? error.message : '등록비 조회 실패');
            }
        })();
        return () => controller.abort();
    }, []);
    useEffect(() => {
        if (!conferenceSeq) return;
        const controller = new AbortController();
        const loadOptions = async () => {
            setIsOptionLoading(true);
            try {
                const params = new URLSearchParams({ conferenceSeq, currency });
                if (item) params.set('preRegistrationSeq', String(item.seq));
                const response = await fetch(`/api/admin/pre-registrations/available-options?${params}`, { signal: controller.signal });
                if (!response.ok) throw new Error(await response.text() || '행사 옵션을 불러오지 못했습니다.');
                setAvailableOptions(await response.json() as RegistrationCatalogOption[]);
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') return;
                setAvailableOptions([]);
                notifyRef.current('error', error instanceof Error ? error.message : '행사 옵션을 불러오지 못했습니다.');
            } finally {
                if (!controller.signal.aborted) setIsOptionLoading(false);
            }
        };
        void loadOptions();
        return () => controller.abort();
    }, [conferenceSeq, item, currency]);
    useEffect(() => {
        if (!item) return;
        const controller = new AbortController();
        const loadSelectedOptions = async () => {
            setIsExistingOptionsLoading(true);
            try {
                const response = await fetch(`/api/admin/pre-registrations/${item.seq}`, { signal: controller.signal });
                if (!response.ok) throw new Error(await response.text() || '선택한 행사 옵션을 불러오지 못했습니다.');
                const detail = await response.json() as PreRegistration;
                if (!controller.signal.aborted) {
                    const quantities = Object.fromEntries((detail.options ?? []).map((option) => [option.optionSeq, option.quantity]));
                    setOptionQuantities(quantities);
                    setExistingOptionQuantities(detail.applicationStatus === 'SUBMITTED' ? quantities : {});
                }
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') return;
                notifyRef.current('error', error instanceof Error ? error.message : '선택한 행사 옵션을 불러오지 못했습니다.');
            } finally {
                if (!controller.signal.aborted) setIsExistingOptionsLoading(false);
            }
        };
        void loadSelectedOptions();
        return () => controller.abort();
    }, [item]);
    useEffect(() => {
        const previousFocus = document.activeElement as HTMLElement | null;
        const previousOverflow = document.body.style.overflow;
        document.body.style.overflow = 'hidden';
        dialogRef.current?.focus();
        return () => {
            document.body.style.overflow = previousOverflow;
            previousFocus?.focus();
        };
    }, []);

    useEffect(() => {
        if (item || member || !memberKeyword.trim()) return;
        const controller = new AbortController();
        const timer = window.setTimeout(async () => {
            try {
                const params = new URLSearchParams({ keyword: memberKeyword.trim(), page: '1', size: '20' });
                const response = await fetch(`/api/admin/members?${params}`, { signal: controller.signal });
                if (!response.ok) throw new Error(await response.text() || '회원을 검색하지 못했습니다.');
                const data = await response.json() as { items: MemberOption[] };
                if (!controller.signal.aborted) setMembers(data.items);
            } catch (error) {
                if (controller.signal.aborted) return;
                setMemberSearchFailed(true);
                notifyRef.current('error', error instanceof Error ? error.message : '회원을 검색하지 못했습니다.');
            } finally {
                if (!controller.signal.aborted) setIsMemberLoading(false);
            }
        }, 300);
        return () => { window.clearTimeout(timer); controller.abort(); };
    }, [item, member, memberKeyword]);

    const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (submitRef.current || isSaving || societyLock.current || isOptionLoading || isExistingOptionsLoading) return;
        if (!item && useSociety && societyQuote?.key !== societyKey) {
            onNotify('error', '현재 신청 회원과 등록 조건으로 학회회원 확인을 진행해 주세요.');
            return;
        }
        if (!item && !member) {
            onNotify('error', '신청 회원을 검색하여 선택해 주세요.');
            return;
        }
        if (!event.currentTarget.checkValidity()) {
            const invalid = event.currentTarget.querySelector<HTMLInputElement>(':invalid');
            onNotify('error', invalid?.validationMessage || '필수 입력사항을 확인해 주세요.');
            invalid?.focus();
            return;
        }
        const parsedCategorySeq = Number(categorySeq);
        const parsedConferenceSeq = Number(conferenceSeq);
        const parsedFeeAmount = Number(feeAmount);
        if (!item && (!Number.isSafeInteger(parsedConferenceSeq) || parsedConferenceSeq <= 0)) {
            onNotify('error', '학회를 선택해 주세요.');
            return;
        }
        if (!Number.isSafeInteger(parsedCategorySeq) || parsedCategorySeq <= 0) {
            onNotify('error', '등록 구분을 선택해 주세요.');
            return;
        }
        if (!feeAmount.trim() || !Number.isFinite(parsedFeeAmount) || parsedFeeAmount < 0 || parsedFeeAmount > 9999999999.99) {
            onNotify('error', '신청 금액은 0~9,999,999,999.99 범위로 입력해 주세요.');
            return;
        }
        if (!/^[A-Za-z]{3}$/.test(currency.trim())) {
            onNotify('error', '통화 코드는 영문 3자리로 입력해 주세요.');
            return;
        }
        if (adminMemo.trim().length > 1000) {
            onNotify('error', '관리자 메모는 1,000자 이내로 입력해 주세요.');
            return;
        }
        submitRef.current = true;
        try {
            await onSave(item?.seq ?? null, {
                ...(!item && member ? { memberSeq: member.seq } : {}),
                ...(!item ? { conferenceSeq: parsedConferenceSeq } : {}),
                options: availableOptions
                    .map((option) => ({ optionSeq: option.optionSeq, quantity: optionQuantities[option.optionSeq] ?? 0 }))
                    .filter((selection) => selection.quantity > 0),
                ...(!item && useSociety ? { societyLicenseNumber: societyLicense.trim(), societyMemberName: societyName.trim() } : {}),
                categorySeq: parsedCategorySeq, periodType,
                currency: currency.trim().toUpperCase(), feeAmount: parsedFeeAmount,
                applicationStatus: item ? applicationStatus : 'SUBMITTED',
                ...(item ? { paymentStatus } : {}),
                adminMemo: adminMemo.trim() || null
            });
        } finally {
            submitRef.current = false;
        }
    };

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4 dark:bg-slate-950/60">
            <DraggableModal ref={dialogRef} role="dialog" aria-modal="true" aria-labelledby="pre-registration-form-title" tabIndex={-1}
                onKeyDown={(event) => {
                    if (event.key === 'Escape') {
                        event.stopPropagation();
                        if (!submitRef.current && !isSaving && !societyLock.current) onClose();
                    }
                    if (event.key !== 'Tab') return;
                    const elements = Array.from(dialogRef.current?.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled)') ?? [])
                        .filter((element) => element.getClientRects().length > 0 && !element.closest('fieldset:disabled'));
                    const first = elements[0];
                    const last = elements[elements.length - 1];
                    if (!first) { event.preventDefault(); return; }
                    if (event.shiftKey && (document.activeElement === first || document.activeElement === dialogRef.current)) {
                        event.preventDefault(); last.focus();
                    } else if (!event.shiftKey && (document.activeElement === last || document.activeElement === dialogRef.current)) {
                        event.preventDefault(); first.focus();
                    }
                }}
                className="flex max-h-[90vh] w-full max-w-4xl flex-col overflow-hidden rounded-2xl bg-white text-slate-900 shadow-2xl outline-none dark:bg-slate-950 dark:text-slate-50">
                <div data-modal-drag-handle className="cursor-move select-none touch-none flex shrink-0 items-center justify-between border-b border-slate-200 px-5 py-4 dark:border-slate-800">
                    <div>
                        <h2 id="pre-registration-form-title" className="text-base font-bold">{item ? '사전등록 수정' : '사전등록 추가'}</h2>
                        <p className="mt-1 text-xs text-slate-400 dark:text-slate-400">{item ? item.registrationNumber : '기존 회원을 선택하여 사전등록 내역을 등록합니다.'}</p>
                    </div>
                    <button type="button" onClick={onClose} disabled={isSaving || checkingSociety} className="rounded-lg p-2 text-slate-400 hover:bg-slate-100 disabled:opacity-50 dark:text-slate-400 dark:hover:bg-slate-900" aria-label="닫기"><X className="h-5 w-5" /></button>
                </div>
                <form onSubmit={(event) => void handleSubmit(event)} noValidate className="overflow-y-auto p-5">
                    <fieldset disabled={isSaving || checkingSociety || isOptionLoading || isExistingOptionsLoading} className="space-y-5">
                        {item ? (
                            <label className={`block ${modalLabelClass}`}><span>신청자</span>
                                <input value={`${fullName(item)} (${item.email})`} readOnly className={modalInputClass} />
                            </label>
                        ) : (
                            <div className="space-y-2">
                                <label className={`block ${modalLabelClass}`}><span>신청 회원 <span className="text-rose-500 dark:text-rose-400">*</span></span>
                                    <input value={memberKeyword} disabled={member !== null} placeholder="회원 이름 또는 이메일 검색"
                                        onKeyDown={(event) => { if (event.key === 'Enter') event.preventDefault(); }}
                                        onChange={(event) => {
                                            setMemberKeyword(event.target.value); setMembers([]); setMemberSearchFailed(false);
                                            setIsMemberLoading(Boolean(event.target.value.trim()));
                                        }} className={modalInputClass} />
                                </label>
                                {member ? (
                                    <div className="flex items-center justify-between gap-3 rounded-lg border border-blue-200 bg-blue-50 p-3 dark:border-blue-900 dark:bg-blue-950/40">
                                        <div className="min-w-0 break-words text-sm"><p className="font-semibold">{member.firstName} {member.lastName}</p><p>{member.email}</p><p className="text-xs text-slate-500 dark:text-slate-400">{member.institution}</p></div>
                                        <button type="button" onClick={() => { setMember(null); setMemberKeyword(''); setMembers([]); setIsMemberLoading(false); }}
                                            className="shrink-0 rounded-lg border border-slate-200 px-3 py-2 text-xs dark:border-slate-700">변경</button>
                                    </div>
                                ) : memberKeyword.trim() && (
                                    <div className="max-h-48 overflow-y-auto rounded-lg border border-slate-200 dark:border-slate-800" aria-live="polite">
                                        {isMemberLoading ? <p className="p-3 text-sm text-slate-500 dark:text-slate-400">회원 검색 중...</p>
                                            : memberSearchFailed ? <p className="p-3 text-sm text-slate-500 dark:text-slate-400">검색하지 못했습니다. 검색어를 다시 입력해 주세요.</p>
                                            : members.length === 0 ? <p className="p-3 text-sm text-slate-500 dark:text-slate-400">검색된 회원이 없습니다.</p>
                                            : members.map((option) => (
                                                <button key={option.seq} type="button" onClick={() => setMember(option)}
                                                    className="block w-full border-b border-slate-200 px-3 py-2 text-left text-sm last:border-0 hover:bg-slate-50 dark:border-slate-800 dark:hover:bg-slate-900">
                                                    <span className="block break-words font-semibold">{option.firstName} {option.lastName} · {option.email}</span>
                                                    <span className="block break-words text-xs text-slate-500 dark:text-slate-400">{option.institution}</span>
                                                </button>
                                            ))}
                                    </div>
                                )}
                                {!member && <p className="text-xs text-slate-400 dark:text-slate-400">최대 20명을 표시합니다. 원하는 회원이 없으면 검색어를 구체적으로 입력해 주세요.</p>}
                            </div>
                        )}
                        {item ? (
                            <label className={`block ${modalLabelClass}`}><span>학회</span>
                                <input value={item.eventName || '기존 미매핑 자료'} readOnly className={modalInputClass} />
                            </label>
                        ) : (
                            <label className={`block ${modalLabelClass}`}><span>학회 <span className="text-rose-500 dark:text-rose-400">*</span></span>
                                <input
                                    value={selectedConferenceName || (conferenceSeq ? `학회 #${conferenceSeq}` : '선택된 학회 없음')}
                                    readOnly
                                    className={modalInputClass}
                                />
                                <span className="block text-xs font-normal text-slate-500 dark:text-slate-400">관리자 화면 상단에서 선택한 학회로 저장됩니다.</span>
                            </label>
                        )}
                        {!item && <div className="space-y-4 rounded-lg border border-slate-200 p-4 dark:border-slate-800">
                            <label className="flex items-center gap-2 text-sm font-semibold"><input type="checkbox" className="h-5 w-5" checked={useSociety} onChange={(event) => { setUseSociety(event.target.checked); setSocietyQuote(null); setCategorySeq(''); }} />학회회원 등급 등록비 적용</label>
                            {useSociety && <>
                                <p className="text-xs text-slate-500 dark:text-slate-400">선택한 신청자의 면허번호와 명부에 등록된 이름을 확인해 주세요. 등록 기간·통화를 바꾸면 다시 확인해야 합니다.</p>
                                <div className="grid gap-4 sm:grid-cols-2"><label className={modalLabelClass}><span>면허번호</span><input maxLength={50} value={societyLicense} onChange={(event) => setSocietyLicense(event.target.value)} className={modalInputClass} /></label><label className={modalLabelClass}><span>학회회원 이름</span><input maxLength={100} value={societyName} onChange={(event) => setSocietyName(event.target.value)} className={modalInputClass} /></label></div>
                                <button type="button" disabled={!member || checkingSociety} className="rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold hover:bg-slate-50 disabled:opacity-50 dark:border-slate-800 dark:hover:bg-slate-900" onClick={async () => {
                                    if (societyLock.current) return;
                                    societyLock.current = true; setCheckingSociety(true); setSocietyQuote(null);
                                    try {
                                        const response = await fetch('/api/admin/society-members/fee-quote', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ licenseNumber: societyLicense.trim(), fullName: societyName.trim(), periodType, currency }) });
                                        if (!response.ok) throw new Error(await response.text() || '학회회원 확인에 실패했습니다.');
                                        const quote = await response.json() as { categorySeq: number; amount: number; memberType: string };
                                        setCategorySeq(String(quote.categorySeq)); setSocietyQuote({ key: societyKey, memberType: quote.memberType });
                                        onNotify('success', `${quote.memberType} 등록비를 적용했습니다.`);
                                    } catch (error) { onNotify('error', error instanceof Error ? error.message : '학회회원 확인에 실패했습니다.'); }
                                    finally { societyLock.current = false; setCheckingSociety(false); }
                                }}>{checkingSociety ? '확인 중...' : '학회회원 확인 및 등록비 적용'}</button>
                                {societyQuote?.key === societyKey && <p className="text-xs text-emerald-600 dark:text-emerald-400">{societyQuote.memberType} 확인 완료</p>}
                            </>}
                        </div>}
                        <div className="grid gap-4 sm:grid-cols-2">
                            <label className={modalLabelClass}><span>등록 구분 <span className="text-rose-500 dark:text-rose-400">*</span></span>
                                <select value={categorySeq} disabled={!item && useSociety} onChange={(event) => setCategorySeq(event.target.value)} required className={modalInputClass}>
                                    <option value="">등록 구분 선택</option>
                                    {item && !categories.some((category) => category.categorySeq === item.categorySeq) && <option value={item.categorySeq}>{item.categoryName} (기존 구분)</option>}
                                    {categories.map((category) => <option key={category.categorySeq} value={category.categorySeq}>{category.categoryName}</option>)}
                                </select>
                            </label>
                            <label className={modalLabelClass}><span>등록 기간 <span className="text-rose-500 dark:text-rose-400">*</span></span>
                                <select value={periodType} onChange={(event) => setPeriodType(event.target.value as PreRegistrationUpdatePayload['periodType'])} className={modalInputClass}>
                                    <option value="EARLY_BIRD">Early Bird</option><option value="REGULAR">Regular</option>
                                </select>
                            </label>
                            <label className={modalLabelClass}><span>신청 금액 <span className="text-rose-500 dark:text-rose-400">*</span></span>
                                <input type="number" readOnly min="0" step={currency === 'KRW' ? '1' : '0.01'} value={feeAmount} required className={modalInputClass} />
                                <span className="block text-xs font-normal text-slate-500 dark:text-slate-400">회원 구분·등록 구분·기간에 설정된 등록비가 자동 적용됩니다.</span>
                            </label>
                            <label className={modalLabelClass}><span>통화 코드 <span className="text-rose-500 dark:text-rose-400">*</span></span>
                                <input value={currency} readOnly required className={modalInputClass} />
                                <span className="block text-xs font-normal text-slate-500 dark:text-slate-400">국내 회원: KRW / 해외 회원: USD</span>
                            </label>
                            {item && <label className={modalLabelClass}><span>신청 상태</span>
                                <select value={applicationStatus} onChange={(event) => setApplicationStatus(event.target.value as PreRegistrationUpdatePayload['applicationStatus'])} className={modalInputClass}>
                                    <option value="SUBMITTED">신청 완료</option><option value="CANCELLED">취소</option>
                                </select>
                            </label>}
                            {item && <label className={modalLabelClass}><span>결제 상태</span>
                                <select value={paymentStatus} onChange={(event) => setPaymentStatus(event.target.value as PreRegistration['paymentStatus'])} className={modalInputClass}>
                                    <option value="UNPAID">결제 대기</option>
                                    <option value="PAID">결제 완료</option>
                                    <option value="REFUNDED">환불</option>
                                    <option value="FAILED">결제 실패</option>
                                </select>
                                <span className="block text-xs font-normal text-slate-500 dark:text-slate-400">관리 내역의 상태를 변경합니다. 실제 카드 승인·환불은 별도로 처리해 주세요.</span>
                                {paymentStatus === 'PAID' && item.paymentStatus !== 'PAID' && <span className="block text-xs font-normal text-slate-500 dark:text-slate-400">결제 기록이 없으면 총 신청금액과 저장 시각을 결제 금액·결제일로 기록합니다.</span>}
                            </label>}
                            <label className={`sm:col-span-2 ${modalLabelClass}`}><span>관리자 메모</span>
                                <textarea value={adminMemo} onChange={(event) => setAdminMemo(event.target.value)} maxLength={1000} rows={4} className={`${modalInputClass} resize-y`} />
                                <span className="block text-right text-xs font-normal text-slate-400 dark:text-slate-400">{adminMemo.length}/1,000</span>
                            </label>
                        </div>
                        {(!item || item.conferenceSeq) && (
                            <section className="space-y-3 rounded-lg border border-slate-200 p-4 dark:border-slate-800">
                                <div>
                                    <h3 className="text-sm font-bold">행사 옵션</h3>
                                    <p className="mt-1 text-xs font-normal text-slate-500 dark:text-slate-400">관리자 등록에서는 판매 기간과 사용 여부에 관계없이 현재 통화 가격이 설정된 옵션을 표시합니다.</p>
                                </div>
                                {!conferenceSeq ? <p className="text-sm text-slate-500 dark:text-slate-400">학회를 먼저 선택해 주세요.</p>
                                    : isOptionLoading ? <p className="text-sm text-slate-500 dark:text-slate-400">신청 가능한 옵션을 불러오는 중입니다.</p>
                                    : availableOptions.length === 0 ? <p className="rounded-lg bg-slate-50 px-3 py-4 text-center text-sm text-slate-500 dark:bg-slate-900/60 dark:text-slate-400">현재 신청 가능한 행사 옵션이 없습니다.</p>
                                    : availableOptions.map((option) => {
                                        const existingQuantity = existingOptionQuantities[option.optionSeq] ?? 0;
                                        const selectableCapacity = option.remainingCapacity == null
                                            ? option.maxPerPerson
                                            : option.remainingCapacity + existingQuantity;
                                        const limit = Math.max(0, Math.min(option.maxPerPerson, selectableCapacity));
                                        const quantity = optionQuantities[option.optionSeq] ?? 0;
                                        return <div key={option.optionSeq} className="grid gap-3 rounded-lg border border-slate-200 p-3 dark:border-slate-800 sm:grid-cols-[1fr_8rem_9rem] sm:items-center">
                                            <div className="min-w-0">
                                                <p className="flex flex-wrap items-center gap-2 break-words text-sm font-semibold">{option.optionName}{!option.enabled && <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-semibold text-slate-500 dark:bg-slate-800 dark:text-slate-400">미사용</span>}</p>
                                                {option.description && <p className="mt-1 break-words text-xs text-slate-500 dark:text-slate-400">{option.description}</p>}
                                                <p className="mt-1 text-xs font-semibold text-blue-600 dark:text-blue-400">{formatCurrency(option.unitPrice, option.currency)} / 개{option.remainingCapacity == null ? '' : ` · 잔여 ${option.remainingCapacity.toLocaleString()}개`}</p>
                                            </div>
                                            <label className="text-xs font-semibold text-slate-500 dark:text-slate-400">수량
                                                <select value={quantity} className={`${modalInputClass} mt-1`} onChange={(event) => setOptionQuantities((current) => ({ ...current, [option.optionSeq]: Number(event.target.value) }))}>
                                                    {Array.from({ length: limit + 1 }, (_, index) => <option key={index} value={index}>{index === 0 ? '선택 안 함' : `${index}개`}</option>)}
                                                </select>
                                            </label>
                                            <div className="text-right"><p className="text-xs text-slate-500 dark:text-slate-400">옵션 금액</p><p className="mt-1 text-sm font-bold">{formatCurrency(option.unitPrice * quantity, option.currency)}</p></div>
                                        </div>;
                                    })}
                                <div className="grid gap-2 rounded-lg bg-slate-50 p-3 text-sm dark:bg-slate-900/60 sm:grid-cols-3">
                                    <AmountSummary label="기본 등록비" amount={Number(feeAmount) || 0} currency={currency} />
                                    <AmountSummary label="옵션 금액" amount={optionAmount} currency={currency} />
                                    <AmountSummary label="총 신청금액" amount={totalAmount} currency={currency} emphasized />
                                </div>
                            </section>
                        )}
                        {!item && <p className="text-xs text-slate-500 dark:text-slate-400">등록번호는 자동 발급됩니다. 신청 완료·결제 대기로 저장되며, 회원의 개인정보 및 등록 규정 동의 이력은 생성하지 않습니다.</p>}
                    </fieldset>
                    <div className="mt-5 flex justify-end gap-2 border-t border-slate-200 pt-5 dark:border-slate-800">
                        <button type="button" onClick={onClose} disabled={isSaving || checkingSociety} className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900">취소</button>
                        <button type="submit" disabled={isSaving || checkingSociety || isOptionLoading || isExistingOptionsLoading} className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-50 dark:bg-blue-600 dark:text-white dark:hover:bg-blue-700"><Save className="h-4 w-4" />{isSaving ? '저장 중...' : '저장'}</button>
                    </div>
                </form>
            </DraggableModal>
        </div>
    );
};

const PreRegistrationDetailModal = ({ item, isLoading, onClose }: { item: PreRegistration | null; isLoading: boolean; onClose: () => void }) => (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/55 p-4 backdrop-blur-sm" role="dialog" aria-modal="true" aria-label="사전등록 상세">
        <DraggableModal className="max-h-[90vh] w-full max-w-4xl overflow-y-auto rounded-xl border border-slate-200 bg-white shadow-2xl dark:border-slate-700 dark:bg-slate-950">
            <div data-modal-drag-handle className="cursor-move select-none touch-none sticky top-0 z-10 flex items-center justify-between border-b border-slate-200 bg-white px-5 py-4 dark:border-slate-800 dark:bg-slate-950">
                <div><h3 className="font-bold">사전등록 상세</h3><p className="mt-1 font-mono text-xs text-blue-600 dark:text-blue-400">{item?.registrationNumber ?? '불러오는 중'}</p></div>
                <button type="button" onClick={onClose} className="rounded-lg p-2 text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800" aria-label="닫기"><X className="h-5 w-5" /></button>
            </div>
            {isLoading || !item ? <div className="p-12 text-center text-sm text-slate-400">상세정보를 불러오는 중입니다.</div> : (
                <div className="space-y-5 p-5">
                    <DetailSection title="회원 정보">
                        <DetailItem label="이름" value={fullName(item)} /><DetailItem label="이메일(아이디)" value={item.email} />
                        <DetailItem label="회원 유형" value={memberTypeLabel(item.memberType)} /><DetailItem label="휴대전화" value={item.mobile} />
                        <DetailItem label="소속" value={item.institution} /><DetailItem label="부서" value={item.department} />
                        <DetailItem label="직위" value={item.positionTitle} /><DetailItem label="국가" value={item.country} />
                    </DetailSection>
                    <DetailSection title="등록 정보">
                        <DetailItem label="학회" value={item.eventName || '기존 미매핑 자료'} />
                        <DetailItem label="등록 구분" value={`${item.categoryName} (${item.categoryCode})`} />
                        <DetailItem label="등록 기간" value={periodLabel(item.periodType)} />
                        <DetailItem label="기본 등록비" value={formatCurrency(item.feeAmount, item.currency)} />
                        <DetailItem label="옵션 금액" value={formatCurrency(item.optionAmount ?? 0, item.currency)} />
                        <DetailItem label="총 신청금액" value={formatCurrency(item.totalAmount ?? item.feeAmount, item.currency)} />
                        <DetailItem label="신청 상태" value={applicationStatusLabel(item.applicationStatus)} />
                        <DetailItem label="신청일시" value={formatDateTime(item.createdAt)} />
                        <DetailItem label="취소일시" value={formatDateTime(item.cancelledAt)} />
                    </DetailSection>
                    <section className="rounded-lg border border-slate-200 dark:border-slate-800">
                        <h4 className="border-b border-slate-200 bg-slate-50 px-4 py-2.5 text-xs font-bold dark:border-slate-800 dark:bg-slate-900/50">신청 행사 옵션</h4>
                        {(item.options?.length ?? 0) === 0 ? <p className="px-4 py-5 text-center text-sm text-slate-500 dark:text-slate-400">선택한 행사 옵션이 없습니다.</p> : (
                            <div className="overflow-x-auto">
                                <table className="w-full min-w-[640px] text-left text-sm">
                                    <thead className="border-b border-slate-200 text-xs text-slate-500 dark:border-slate-800 dark:text-slate-400"><tr><th className="px-4 py-2.5">옵션</th><th className="px-4 py-2.5 text-right">단가</th><th className="px-4 py-2.5 text-right">수량</th><th className="px-4 py-2.5 text-right">금액</th></tr></thead>
                                    <tbody>{item.options?.map((option) => <tr key={option.seq} className="border-b border-slate-100 last:border-0 dark:border-slate-900"><td className="px-4 py-3"><p className="font-semibold">{option.optionName}</p>{option.optionDescription && <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">{option.optionDescription}</p>}</td><td className="px-4 py-3 text-right">{formatCurrency(option.unitPrice, option.currency)}</td><td className="px-4 py-3 text-right">{option.quantity.toLocaleString()}개</td><td className="px-4 py-3 text-right font-semibold">{formatCurrency(option.amount, option.currency)}</td></tr>)}</tbody>
                                </table>
                            </div>
                        )}
                    </section>
                    <DetailSection title="결제 정보">
                        <DetailItem label="결제 상태" value={paymentStatusLabel(item.paymentStatus)} />
                        <DetailItem label="결제 수단" value={item.paymentMethod} />
                        <DetailItem label="결제 금액" value={item.paidAmount == null ? '-' : formatCurrency(item.paidAmount, item.currency)} />
                        <DetailItem label="결제 금액 검증" value={paymentAmountConsistency(item)} />
                        <DetailItem label="결제일시" value={formatDateTime(item.paidAt)} />
                        <DetailItem label="거래번호" value={item.paymentTransactionId} wide />
                    </DetailSection>
                    <DetailSection title="동의 및 관리 정보">
                        <DetailItem label="개인정보 동의" value={agreementLabel(item.privacyAgreed, item.privacyAgreedAt)} />
                        <DetailItem label="등록 규정 동의" value={agreementLabel(item.termsAgreed, item.termsAgreedAt)} />
                        <DetailItem label="관리자 메모" value={item.adminMemo} wide />
                    </DetailSection>
                </div>
            )}
        </DraggableModal>
    </div>
);

const SummaryCard = ({ label, value, accent = '' }: { label: string; value: string; accent?: string }) => <div className="border-b border-r border-slate-200 p-4 last:border-r-0 dark:border-slate-800"><div className="text-xs font-semibold text-slate-400">{label}</div><div className={`mt-1 text-lg font-bold ${accent}`}>{value}</div></div>;
const SelectFilter = ({ label, value, onChange, children }: { label: string; value: string; onChange: (value: string) => void; children: ReactNode }) => <label><span className="mb-1 block text-[11px] font-semibold text-slate-500">{label}</span><select value={value} onChange={(event) => onChange(event.target.value)} className={inputClassName()}>{children}</select></label>;
const DateFilter = ({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) => <label><span className="mb-1 block text-[11px] font-semibold text-slate-500">{label}</span><div className="relative"><CalendarDays className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" /><input type="date" value={value} onChange={(event) => onChange(event.target.value)} className={inputClassName('pl-9')} /></div></label>;
const PageButton = ({ disabled, onClick, children }: { disabled: boolean; onClick: () => void; children: ReactNode }) => <button type="button" disabled={disabled} onClick={onClick} className="rounded-lg border border-slate-200 p-2 hover:bg-slate-50 disabled:opacity-40 dark:border-slate-800 dark:hover:bg-slate-900">{children}</button>;
const DetailSection = ({ title, children }: { title: string; children: ReactNode }) => <section className="rounded-lg border border-slate-200 dark:border-slate-800"><h4 className="border-b border-slate-200 bg-slate-50 px-4 py-2.5 text-xs font-bold dark:border-slate-800 dark:bg-slate-900/50">{title}</h4><div className="grid grid-cols-1 gap-px bg-slate-200 dark:bg-slate-800 sm:grid-cols-2">{children}</div></section>;
const DetailItem = ({ label, value, wide = false }: { label: string; value?: string | null; wide?: boolean }) => <div className={`bg-white px-4 py-3 dark:bg-slate-950 ${wide ? 'sm:col-span-2' : ''}`}><div className="text-[11px] font-semibold text-slate-400">{label}</div><div className="mt-1 break-words text-sm font-medium">{value || '-'}</div></div>;
const AmountSummary = ({ label, amount, currency, emphasized = false }: { label: string; amount: number; currency: string; emphasized?: boolean }) => <div className={emphasized ? 'text-blue-600 dark:text-blue-400 sm:text-right' : 'sm:text-right'}><p className="text-xs font-medium text-slate-500 dark:text-slate-400">{label}</p><p className="mt-1 font-bold">{formatCurrency(amount, currency)}</p></div>;

const StatusBadge = ({ type, value }: { type: 'application' | 'payment'; value: string }) => {
    const labels = type === 'application' ? applicationStatuses : paymentStatuses;
    const status = labels[value] ?? { label: value, className: 'border-slate-200 bg-slate-50 text-slate-600 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300' };
    return <span className={`inline-flex rounded-full border px-2.5 py-0.5 text-xs font-semibold ${status.className}`}>{status.label}</span>;
};

const applicationStatuses: Record<string, { label: string; className: string }> = {
    SUBMITTED: { label: '신청 완료', className: 'border-blue-200 bg-blue-50 text-blue-700 dark:border-blue-900/60 dark:bg-blue-950/40 dark:text-blue-300' },
    CANCELLED: { label: '취소', className: 'border-rose-200 bg-rose-50 text-rose-700 dark:border-rose-900/60 dark:bg-rose-950/40 dark:text-rose-300' }
};
const paymentStatuses: Record<string, { label: string; className: string }> = {
    UNPAID: { label: '결제 대기', className: 'border-amber-200 bg-amber-50 text-amber-700 dark:border-amber-900/60 dark:bg-amber-950/40 dark:text-amber-300' },
    PAID: { label: '결제 완료', className: 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-900/60 dark:bg-emerald-950/40 dark:text-emerald-300' },
    REFUNDED: { label: '환불', className: 'border-violet-200 bg-violet-50 text-violet-700 dark:border-violet-900/60 dark:bg-violet-950/40 dark:text-violet-300' },
    FAILED: { label: '결제 실패', className: 'border-rose-200 bg-rose-50 text-rose-700 dark:border-rose-900/60 dark:bg-rose-950/40 dark:text-rose-300' }
};

const buildQuery = (filters: Filters) => {
    const params = new URLSearchParams();
    Object.entries(filters).forEach(([key, value]) => { if (value) params.set(key, value); });
    return params;
};
const inputClassName = (extra = '') => `w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-700 dark:bg-slate-950 dark:text-slate-50 ${extra}`;
const fullName = (item: PreRegistration) => `${item.firstName ?? ''} ${item.lastName ?? ''}`.trim();
const formatDateTime = (value?: string | null) => value ? dateTimeFormatter.format(new Date(value)) : '-';
const formatCurrency = (amount: number, currency: string) => { try { return new Intl.NumberFormat('ko-KR', { style: 'currency', currency: currency || 'KRW', currencyDisplay: 'narrowSymbol' }).format(amount ?? 0); } catch { return `${(amount ?? 0).toLocaleString()} ${currency ?? ''}`.trim(); } };
const periodLabel = (value: string) => value === 'EARLY_BIRD' ? 'Early Bird' : value === 'REGULAR' ? 'Regular' : value;
const memberTypeLabel = (value: string) => value === 'international' ? 'International' : value === 'domestic' ? 'Domestic' : value;
const applicationStatusLabel = (value: string) => applicationStatuses[value]?.label ?? value;
const paymentStatusLabel = (value: string) => paymentStatuses[value]?.label ?? value;
const paymentAmountConsistency = (item: PreRegistration) => {
    if (item.paidAmount == null) return '결제 금액 없음';
    return Math.abs(item.paidAmount - (item.totalAmount ?? item.feeAmount)) < 0.001 ? '총 신청금액과 일치' : '총 신청금액과 불일치';
};
const agreementLabel = (agreed: boolean, agreedAt?: string | null) => agreed ? `동의 (${formatDateTime(agreedAt)})` : '미동의';
