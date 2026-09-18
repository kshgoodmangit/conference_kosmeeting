import { useEffect, useRef, useState } from 'react';
import {
    Activity,
    Building2,
    BarChart3,
    CheckCircle2,
    ChevronLeft,
    ChevronRight,
    Download,
    Database,
    EllipsisVertical,
    FileSpreadsheet,
    FileText,
    FilterX,
    Pencil,
    Plus,
    LoaderCircle,
    Search,
    Trash2,
    UserPlus,
    Users
} from 'lucide-react';
import { useCallback } from 'react';
import type { FormEvent, MouseEvent } from 'react';
import { AbstractDetailModal } from './AbstractDetailModal';
import { AbstractRegisterModal } from './AbstractRegisterModal';
import { ReviewerAssignmentModal } from './ReviewerAssignmentModal';
import { ReviewerAssignmentBulkImportModal } from './ReviewerAssignmentBulkImportModal';
import { AbstractReviewResultModal } from './AbstractReviewResultModal';
import { AbstractDecisionModal } from './AbstractDecisionModal';
import {
    AbstractSimilarityProgressModal,
    type AbstractSimilarityJob,
    type AbstractSimilarityWeights
} from './AbstractSimilarityProgressModal';
import { downloadAbstractsToXlsx } from './abstractExport';
import type {
    AbstractCategory,
    AbstractPresentationType,
    AbstractSubmissionDetail,
    AbstractSubmissionListItem,
    AbstractSubmissionMetaResponse,
    AbstractSubmissionPageResponse
} from './abstractTypes';
import type { NotificationType } from './NotificationToast';
import { useConfirm } from './confirmDialogContext';
import { useExcelDownloadDialog } from './excelDownloadDialogContext';
import { getStoredAdminConferenceSeq } from '../adminSession';
import { MailComposeModal } from './MailComposeModal';
import { MailSelectionButton, MailSelectionCheckbox, MailSelectionSummary } from './MailSelectionControls';
import { useMailSelection } from './useMailSelection';

interface Props {
    view?: AbstractPageView;
    onNotify: (type: NotificationType, message: string) => void;
}

export type AbstractPageView = 'submissions' | 'oral-accepted' | 'poster-accepted';

interface AbstractPageViewConfig {
    title: string;
    description: string;
    exportMenuName: string;
    summaryLabel: string;
    fixedStatus?: AbstractSubmissionListItem['status'];
    presentationTypeAliases?: string[];
    showManagementActions: boolean;
}

const VIEW_CONFIG: Record<AbstractPageView, AbstractPageViewConfig> = {
    submissions: {
        title: '초록 접수 내역',
        description: '접수된 초록을 조회하고 등록, 수정, 심사자 배정을 관리합니다.',
        exportMenuName: '초록 접수 내역',
        summaryLabel: '검색 결과',
        showManagementActions: true
    },
    'oral-accepted': {
        title: '구두발표 대상',
        description: '승인된 초록 중 구두발표 대상으로 확정된 내역입니다.',
        exportMenuName: '구두발표 대상',
        summaryLabel: '구두발표 대상',
        fixedStatus: 'approved',
        presentationTypeAliases: ['구두 발표', '구두발표', 'oral', 'oral presentation'],
        showManagementActions: false
    },
    'poster-accepted': {
        title: '포스터발표 대상',
        description: '승인된 초록 중 포스터발표 대상으로 확정된 내역입니다.',
        exportMenuName: '포스터발표 대상',
        summaryLabel: '포스터발표 대상',
        fixedStatus: 'approved',
        presentationTypeAliases: ['포스터 발표', '포스터발표', 'poster', 'poster presentation'],
        showManagementActions: false
    }
};

const PAGE_SIZE = 10;
const ACTION_MENU_WIDTH = 160;
const DEFAULT_SIMILARITY_WEIGHTS: AbstractSimilarityWeights = {
    title: 10,
    objective: 20,
    methods: 20,
    results: 30,
    conclusions: 20
};
const STATUS_LABEL: Record<AbstractSubmissionListItem['status'], string> = {
    draft: '임시저장',
    submitted: '제출완료',
    under_review: '심사중',
    approved: '승인',
    rejected: '반려'
};

const STATUS_CLASS: Record<AbstractSubmissionListItem['status'], string> = {
    draft: 'bg-slate-100 text-slate-700 border-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:border-slate-700',
    submitted: 'bg-blue-50 text-blue-700 border-blue-200 dark:bg-blue-950/40 dark:text-blue-300 dark:border-blue-900/60',
    under_review: 'bg-amber-50 text-amber-700 border-amber-200 dark:bg-amber-950/40 dark:text-amber-300 dark:border-amber-900/60',
    approved: 'bg-emerald-50 text-emerald-700 border-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-300 dark:border-emerald-900/60',
    rejected: 'bg-rose-50 text-rose-700 border-rose-200 dark:bg-rose-950/40 dark:text-rose-300 dark:border-rose-900/60'
};

const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
});

const normalizePresentationTypeName = (value: string) => (
    value.trim().toLocaleLowerCase().replace(/[\s_-]+/g, '')
);

export const AbstractPage = ({ view = 'submissions', onNotify }: Props) => (
    <AbstractPageContent key={view} view={view} onNotify={onNotify} />
);

const AbstractPageContent = ({ view = 'submissions', onNotify }: Props) => {
    const viewConfig = VIEW_CONFIG[view];
    const fixedStatus = viewConfig.fixedStatus ?? '';
    const confirm = useConfirm();
    const requestExcelDownload = useExcelDownloadDialog();
    const [items, setItems] = useState<AbstractSubmissionListItem[]>([]);
    const [draftSearchKeyword, setDraftSearchKeyword] = useState('');
    const [searchKeyword, setSearchKeyword] = useState('');
    const [draftPresentationTypeCode, setDraftPresentationTypeCode] = useState('');
    const [presentationTypeCode, setPresentationTypeCode] = useState('');
    const [draftCategoryCode, setDraftCategoryCode] = useState('');
    const [categoryCode, setCategoryCode] = useState('');
    const [draftStatus, setDraftStatus] = useState(fixedStatus);
    const [status, setStatus] = useState(fixedStatus);
    const [presentationTypes, setPresentationTypes] = useState<AbstractPresentationType[]>([]);
    const [categories, setCategories] = useState<AbstractCategory[]>([]);
    const [isPresentationPresetReady, setIsPresentationPresetReady] = useState(view === 'submissions');
    const [currentPage, setCurrentPage] = useState(1);
    const [totalCount, setTotalCount] = useState(0);
    const [summary, setSummary] = useState<AbstractSubmissionPageResponse['summary']>();
    const [totalPages, setTotalPages] = useState(1);
    const [isLoading, setIsLoading] = useState(false);
    const [isExporting, setIsExporting] = useState(false);
    const [isStartingSimilarityAnalysis, setIsStartingSimilarityAnalysis] = useState(false);
    const [similarityJob, setSimilarityJob] = useState<AbstractSimilarityJob | null>(null);
    const [defaultSimilarityWeights, setDefaultSimilarityWeights] = useState<AbstractSimilarityWeights>(DEFAULT_SIMILARITY_WEIGHTS);
    const [isSimilarityProgressOpen, setIsSimilarityProgressOpen] = useState(false);
    const [isSimilarityStreamConnected, setIsSimilarityStreamConnected] = useState(false);
    const [errorMessage, setErrorMessage] = useState('');
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [selectedAbstract, setSelectedAbstract] = useState<AbstractSubmissionDetail | null>(null);
    const [isDetailModalOpen, setIsDetailModalOpen] = useState(false);
    const [selectedDetailAbstract, setSelectedDetailAbstract] = useState<AbstractSubmissionDetail | null>(null);
    const [abstractSimilarityEnabled, setAbstractSimilarityEnabled] = useState(false);
    const [assignmentAbstract, setAssignmentAbstract] = useState<AbstractSubmissionListItem | null>(null);
    const [isBulkAssignmentOpen, setIsBulkAssignmentOpen] = useState(false);
    const [reviewResultAbstract, setReviewResultAbstract] = useState<AbstractSubmissionListItem | null>(null);
    const [decisionAbstract, setDecisionAbstract] = useState<AbstractSubmissionListItem | null>(null);
    const [activeDropdownId, setActiveDropdownId] = useState<number | null>(null);
    const [dropdownPosition, setDropdownPosition] = useState({ top: 0, left: 0 });
    const [reloadKey, setReloadKey] = useState(0);
    const mailDisabled = isLoading || !!errorMessage || !isPresentationPresetReady;
    const mail = useMailSelection(items, mailDisabled, (item) => ({
        id: `abstract-${item.seq}`,
        sourceSeq: item.seq,
        name: item.memberFullName?.trim() || '이름 미등록',
        email: item.memberEmail
    }));
    const { syncSelected } = mail;
    const onNotifyRef = useRef(onNotify);
    const notifiedSimilarityJobsRef = useRef(new Set<string>());
    const similarityJobSeq = similarityJob?.seq;
    const isSimilarityJobActive = similarityJob?.status === 'QUEUED' || similarityJob?.status === 'RUNNING';
    const isSimilarityJobBusy = isStartingSimilarityAnalysis || isSimilarityJobActive;

    useEffect(() => {
        onNotifyRef.current = onNotify;
    }, [onNotify]);

    useEffect(() => {
        const controller = new AbortController();
        const loadCapabilities = async () => {
            try {
                const response = await fetch('/api/admin/conference-settings/capabilities', {
                    signal: controller.signal
                });
                if (!response.ok) {
                    throw new Error(await response.text() || '기능 설정을 불러오지 못했습니다.');
                }
                const capabilities = await response.json() as { abstractSimilarityEnabled?: boolean };
                if (!controller.signal.aborted) {
                    setAbstractSimilarityEnabled(capabilities.abstractSimilarityEnabled === true);
                }
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') return;
                setAbstractSimilarityEnabled(false);
                onNotifyRef.current('error', error instanceof Error ? error.message : '기능 설정을 불러오지 못했습니다.');
            }
        };
        void loadCapabilities();
        return () => controller.abort();
    }, []);

    useEffect(() => {
        if (!abstractSimilarityEnabled || view !== 'submissions') return;
        const controller = new AbortController();
        const loadDefaultWeights = async () => {
            try {
                const response = await fetch('/api/admin/abstracts/similarity-jobs/default-weights', {
                    signal: controller.signal
                });
                if (!response.ok) {
                    throw new Error(await response.text() || '기본 유사도 가중치를 불러오지 못했습니다.');
                }
                const weights = await response.json() as AbstractSimilarityWeights;
                if (!controller.signal.aborted) setDefaultSimilarityWeights(weights);
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') return;
                onNotifyRef.current('error', error instanceof Error ? error.message : '기본 유사도 가중치를 불러오지 못했습니다.');
            }
        };
        const loadActiveSimilarityJob = async () => {
            try {
                const response = await fetch('/api/admin/abstracts/similarity-jobs/active', {
                    signal: controller.signal
                });
                if (response.status === 204) return;
                if (!response.ok) {
                    throw new Error(await response.text() || '진행 중인 유사도 분석 작업을 확인하지 못했습니다.');
                }
                const activeJob = await response.json() as AbstractSimilarityJob;
                if (!controller.signal.aborted) {
                    setSimilarityJob(activeJob);
                    setIsSimilarityProgressOpen(true);
                }
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') return;
                onNotifyRef.current('error', error instanceof Error ? error.message : '진행 중인 유사도 분석 작업을 확인하지 못했습니다.');
            }
        };
        void loadDefaultWeights();
        void loadActiveSimilarityJob();
        return () => controller.abort();
    }, [abstractSimilarityEnabled, view]);

    useEffect(() => {
        if (!similarityJobSeq || !isSimilarityJobActive) {
            return;
        }

        const conferenceSeq = getStoredAdminConferenceSeq();
        if (!Number.isInteger(conferenceSeq) || Number(conferenceSeq) <= 0) {
            onNotifyRef.current('error', 'AI 유사도 분석 진행상황을 확인할 학회 정보가 없습니다.');
            return;
        }

        let disposed = false;
        let terminal = false;
        let pollingEnabled = false;
        let pollingTimer: number | null = null;
        const pollingController = new AbortController();
        const jobEndpoint = `/api/admin/abstracts/similarity-jobs/${similarityJobSeq}`;
        const eventUrl = `${jobEndpoint}/events?conferenceSeq=${encodeURIComponent(String(conferenceSeq))}`;
        const eventSource = new EventSource(eventUrl);

        const stopPolling = () => {
            pollingEnabled = false;
            if (pollingTimer !== null) {
                window.clearTimeout(pollingTimer);
                pollingTimer = null;
            }
        };

        const applyJobUpdate = (nextJob: AbstractSimilarityJob) => {
            if (disposed) return;
            setSimilarityJob(nextJob);
            if (nextJob.status !== 'COMPLETED' && nextJob.status !== 'FAILED') return;

            terminal = true;
            stopPolling();
            setIsSimilarityStreamConnected(false);
            eventSource.close();
            const notificationKey = `${nextJob.seq}:${nextJob.status}`;
            if (notifiedSimilarityJobsRef.current.has(notificationKey)) return;

            notifiedSimilarityJobsRef.current.add(notificationKey);
            if (nextJob.status === 'COMPLETED') {
                onNotifyRef.current(
                    'success',
                    `초록 ${nextJob.abstractCount.toLocaleString()}건의 유사도 분석을 완료했습니다. 결과 ${nextJob.similarityResultCount.toLocaleString()}건을 저장했습니다.`
                );
            } else {
                onNotifyRef.current('error', nextJob.message || '유사도 분석에 실패했습니다.');
            }
        };

        const pollJob = async () => {
            if (disposed || terminal || !pollingEnabled) return;
            try {
                const response = await fetch(jobEndpoint, { signal: pollingController.signal });
                if (!response.ok) {
                    throw new Error(await response.text() || 'AI 유사도 분석 진행상황을 확인하지 못했습니다.');
                }
                setIsSimilarityStreamConnected(true);
                applyJobUpdate(await response.json() as AbstractSimilarityJob);
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') return;
                setIsSimilarityStreamConnected(false);
            } finally {
                if (!disposed && !terminal && pollingEnabled) {
                    pollingTimer = window.setTimeout(() => void pollJob(), 2_000);
                }
            }
        };

        const startPolling = () => {
            if (disposed || terminal || pollingEnabled) return;
            pollingEnabled = true;
            void pollJob();
        };

        const handleProgress = (event: MessageEvent<string>) => {
            try {
                applyJobUpdate(JSON.parse(event.data) as AbstractSimilarityJob);
            } catch {
                onNotifyRef.current('error', 'AI 유사도 분석 진행상황을 해석하지 못했습니다.');
            }
        };
        eventSource.addEventListener('progress', handleProgress as EventListener);
        eventSource.onopen = () => {
            stopPolling();
            setIsSimilarityStreamConnected(true);
        };
        eventSource.onerror = () => {
            eventSource.close();
            setIsSimilarityStreamConnected(false);
            startPolling();
        };

        const connectionWatchdog = window.setTimeout(() => {
            if (eventSource.readyState !== EventSource.OPEN) startPolling();
        }, 3_000);

        return () => {
            disposed = true;
            terminal = true;
            window.clearTimeout(connectionWatchdog);
            stopPolling();
            pollingController.abort();
            eventSource.removeEventListener('progress', handleProgress as EventListener);
            eventSource.close();
            setIsSimilarityStreamConnected(false);
        };
    }, [isSimilarityJobActive, similarityJobSeq]);

    const notify = useCallback((type: NotificationType, message: string) => {
        onNotifyRef.current(type, message);
    }, []);

    useEffect(() => {
        const controller = new AbortController();
        const loadMeta = async () => {
            try {
                const response = await fetch('/api/admin/abstracts/meta', { signal: controller.signal });
                if (!response.ok) {
                    throw new Error(await response.text() || '검색 조건을 불러오지 못했습니다.');
                }
                const data = await response.json() as AbstractSubmissionMetaResponse;
                setPresentationTypes(data.presentationTypes);
                setCategories(data.categories);

                if (viewConfig.presentationTypeAliases) {
                    const normalizedAliases = viewConfig.presentationTypeAliases.map(normalizePresentationTypeName);
                    const matchedType = data.presentationTypes.find((item) => (
                        normalizedAliases.includes(normalizePresentationTypeName(item.name))
                    ));
                    if (!matchedType) {
                        throw new Error(`${viewConfig.title}에 사용할 발표형식 공통코드를 찾지 못했습니다.`);
                    }

                    const matchedCode = String(matchedType.code);
                    setDraftPresentationTypeCode(matchedCode);
                    setPresentationTypeCode(matchedCode);
                }
                setIsPresentationPresetReady(true);
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') return;
                setIsPresentationPresetReady(false);
                onNotifyRef.current('error', error instanceof Error ? error.message : '검색 조건을 불러오지 못했습니다.');
            }
        };
        void loadMeta();
        return () => controller.abort();
    }, [viewConfig.presentationTypeAliases, viewConfig.title]);

    useEffect(() => {
        const controller = new AbortController();

        const load = async () => {
            if (!isPresentationPresetReady) {
                setIsLoading(false);
                return;
            }

            setIsLoading(true);
            setErrorMessage('');
            try {
                const params = new URLSearchParams({
                    page: String(currentPage),
                    size: String(PAGE_SIZE),
                    keyword: searchKeyword.trim()
                });
                if (presentationTypeCode) {
                    params.set(viewConfig.presentationTypeAliases ? 'acceptedPresentationTypeCode' : 'presentationTypeCode', presentationTypeCode);
                }
                if (categoryCode) params.set('categoryCode', categoryCode);
                if (status) params.set('status', status);
                const response = await fetch(`/api/admin/abstracts?${params.toString()}`, { signal: controller.signal });
                if (!response.ok) {
                    throw new Error(await response.text() || '초록 목록을 불러오지 못했습니다.');
                }
                const data = await response.json() as AbstractSubmissionPageResponse;
                if (controller.signal.aborted) return;
                setItems(data.items);
                syncSelected(data.items);
                setSummary(data.summary);
                setTotalCount(data.totalCount);
                setTotalPages(data.totalPages);
                if (data.page !== currentPage) {
                    setCurrentPage(data.page);
                }
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') {
                    return;
                }
                const message = error instanceof Error ? error.message : '초록 목록을 불러오지 못했습니다.';
                setErrorMessage(message);
                onNotifyRef.current('error', message);
            } finally {
                if (!controller.signal.aborted) setIsLoading(false);
            }
        };

        void load();
        return () => controller.abort();
    }, [categoryCode, currentPage, isPresentationPresetReady, presentationTypeCode, reloadKey, searchKeyword, status, viewConfig.presentationTypeAliases, syncSelected]);

    const safeCurrentPage = Math.min(currentPage, totalPages);
    const summaryCount = (value: number | undefined) => (
        isLoading ? '집계 중...' : errorMessage || value == null ? '-' : `${value.toLocaleString()}건`
    );
    const startIndex = totalCount === 0 ? 0 : (safeCurrentPage - 1) * PAGE_SIZE + 1;
    const endIndex = Math.min((safeCurrentPage - 1) * PAGE_SIZE + items.length, totalCount);
    const firstPage = Math.max(1, Math.min(safeCurrentPage - 2, Math.max(1, totalPages - 4)));
    const pageNumbers = Array.from({ length: Math.min(5, totalPages - firstPage + 1) }, (_, index) => firstPage + index);

    const refresh = () => setReloadKey((value) => value + 1);

    const handleSearch = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        mail.clear();
        setCurrentPage(1);
        setSearchKeyword(draftSearchKeyword.trim());
        setPresentationTypeCode(viewConfig.presentationTypeAliases ? presentationTypeCode : draftPresentationTypeCode);
        setCategoryCode(draftCategoryCode);
        setStatus(viewConfig.fixedStatus ?? draftStatus);
        setReloadKey((value) => value + 1);
    };

    const applySelectSearch = (
        nextPresentationTypeCode: string,
        nextCategoryCode: string,
        nextStatus: string
    ) => {
        mail.clear();
        setCurrentPage(1);
        setSearchKeyword(draftSearchKeyword.trim());
        setPresentationTypeCode(viewConfig.presentationTypeAliases ? presentationTypeCode : nextPresentationTypeCode);
        setCategoryCode(nextCategoryCode);
        setStatus(viewConfig.fixedStatus ?? nextStatus);
    };

    const resetSearch = () => {
        mail.clear();
        setDraftSearchKeyword('');
        setSearchKeyword('');
        if (!viewConfig.presentationTypeAliases) {
            setDraftPresentationTypeCode('');
            setPresentationTypeCode('');
        }
        setDraftCategoryCode('');
        setCategoryCode('');
        setDraftStatus(fixedStatus);
        setStatus(fixedStatus);
        setCurrentPage(1);
        setReloadKey((value) => value + 1);
    };

    const exportExcel = async () => {
        setIsExporting(true);
        try {
            const completed = await requestExcelDownload({
                menuName: viewConfig.exportMenuName,
                filters: [
                    { label: '검색어', value: searchKeyword || '전체' },
                    { label: '발표 형식', value: presentationTypes.find((item) => String(item.code) === presentationTypeCode)?.name ?? '전체' },
                    { label: '분류', value: categories.find((item) => String(item.code) === categoryCode)?.name ?? '전체' },
                    { label: '상태', value: status ? (STATUS_LABEL[status as AbstractSubmissionListItem['status']] ?? status) : '전체' }
                ],
                execute: (reason) => downloadAbstractsToXlsx(
                    {
                        searchKeyword,
                        presentationTypeCode: viewConfig.presentationTypeAliases ? '' : presentationTypeCode,
                        acceptedPresentationTypeCode: viewConfig.presentationTypeAliases ? presentationTypeCode : '',
                        categoryCode,
                        status
                    },
                    reason
                ),
                onError: (message) => onNotifyRef.current('error', message)
            });
            if (completed) onNotifyRef.current('success', '엑셀 파일을 다운로드했습니다.');
        } finally {
            setIsExporting(false);
        }
    };

    const openSimilarityAnalysis = () => {
        if (!isSimilarityJobActive) setSimilarityJob(null);
        setIsSimilarityProgressOpen(true);
    };

    const startSimilarityAnalysis = async (weights: AbstractSimilarityWeights) => {
        if (isStartingSimilarityAnalysis) return;

        setIsStartingSimilarityAnalysis(true);
        try {
            const response = await fetch('/api/admin/abstracts/similarity-jobs', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(weights)
            });
            if (!response.ok) {
                throw new Error(await response.text() || '유사도 분석 작업을 시작하지 못했습니다.');
            }
            const job = await response.json() as AbstractSimilarityJob;
            setSimilarityJob(job);
            setIsSimilarityProgressOpen(true);
        } catch (error) {
            onNotifyRef.current('error', error instanceof Error ? error.message : '유사도 분석 작업을 시작하지 못했습니다.');
        } finally {
            setIsStartingSimilarityAnalysis(false);
        }
    };

    const formatDate = (value?: string | null) => {
        if (!value) return '-';
        const date = new Date(value);
        return Number.isNaN(date.getTime()) ? '-' : dateFormatter.format(date);
    };

    const openCreate = () => {
        setSelectedAbstract(null);
        setIsModalOpen(true);
    };

    const openEdit = async (seq: number) => {
        try {
            const response = await fetch(`/api/admin/abstracts/${seq}`);
            if (!response.ok) {
                throw new Error(await response.text() || '초록 정보를 불러오지 못했습니다.');
            }
            setSelectedAbstract(await response.json() as AbstractSubmissionDetail);
            setIsModalOpen(true);
        } catch (error) {
            const message = error instanceof Error ? error.message : '초록 정보를 불러오지 못했습니다.';
            setErrorMessage(message);
            onNotifyRef.current('error', message);
        }
    };

    const openDetail = async (seq: number) => {
        try {
            const response = await fetch(`/api/admin/abstracts/${seq}`);
            if (!response.ok) {
                throw new Error(await response.text() || '초록 정보를 불러오지 못했습니다.');
            }
            setSelectedDetailAbstract(await response.json() as AbstractSubmissionDetail);
            setIsDetailModalOpen(true);
        } catch (error) {
            const message = error instanceof Error ? error.message : '초록 정보를 불러오지 못했습니다.';
            setErrorMessage(message);
            onNotifyRef.current('error', message);
        }
    };

    const handleDetailEdit = (seq: number) => {
        setIsDetailModalOpen(false);
        setSelectedDetailAbstract(null);
        void openEdit(seq);
    };

    const handleDetailDecision = (abstractSubmission: AbstractSubmissionDetail) => {
        setIsDetailModalOpen(false);
        setSelectedDetailAbstract(null);
        setDecisionAbstract(abstractSubmission);
    };

    const deleteAbstract = async (item: AbstractSubmissionListItem) => {
        if (!await confirm({
            title: '초록 삭제',
            message: `초록 "${item.title}"을(를) 삭제하시겠습니까?`,
            confirmText: '삭제',
            tone: 'danger'
        })) {
            return;
        }

        try {
            const response = await fetch(`/api/admin/abstracts/${item.seq}`, { method: 'DELETE' });
            if (!response.ok) {
                throw new Error(await response.text() || '초록 삭제에 실패했습니다.');
            }
            onNotifyRef.current('success', '초록을 삭제했습니다.');
            mail.remove(item.seq);
            refresh();
        } catch (error) {
            const message = error instanceof Error ? error.message : '초록 삭제에 실패했습니다.';
            setErrorMessage(message);
            onNotifyRef.current('error', message);
        }
    };

    const handleModalSuccess = (saved: AbstractSubmissionDetail) => {
        syncSelected([saved]);
        setIsModalOpen(false);
        setSelectedAbstract(null);
        refresh();
    };

    const hasSearchCondition = Boolean(searchKeyword || presentationTypeCode || categoryCode || status);

    const handleAssignmentSuccess = () => {
        setAssignmentAbstract(null);
        refresh();
    };

    const toggleActionMenu = (abstractSeq: number, event: MouseEvent<HTMLButtonElement>) => {
        event.stopPropagation();
        const rect = event.currentTarget.getBoundingClientRect();
        setDropdownPosition({
            top: rect.bottom + 4,
            left: Math.max(8, rect.right - ACTION_MENU_WIDTH)
        });
        setActiveDropdownId((value) => value === abstractSeq ? null : abstractSeq);
    };

    return (
        <section onClick={() => setActiveDropdownId(null)} className="rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-950">
            <div className="flex flex-col justify-between gap-4 border-b border-slate-200 p-4 dark:border-slate-800 lg:flex-row lg:items-center md:p-5">
                <div>
                    <div className="flex items-center gap-2">
                        <FileText className="shrink-0 h-4 w-4 text-slate-400" />
                        <h3 className="text-sm font-semibold md:text-base">{viewConfig.title}</h3>
                    </div>
                    <p className="mt-1 text-xs text-slate-400">{viewConfig.description}</p>
                </div>
                <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
                    <MailSelectionButton count={mail.count} disabled={mailDisabled} onClick={mail.open} />
                    {viewConfig.showManagementActions && abstractSimilarityEnabled && (
                        <button
                            type="button"
                            onClick={openSimilarityAnalysis}
                            disabled={isLoading || isExporting || isStartingSimilarityAnalysis}
                            className="inline-flex items-center justify-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900"
                        >
                            {isStartingSimilarityAnalysis
                                ? <LoaderCircle className="h-4 w-4 animate-spin" />
                                : isSimilarityJobActive
                                    ? <Activity className="h-4 w-4 animate-pulse" />
                                    : <Database className="h-4 w-4" />}
                            {isStartingSimilarityAnalysis ? '작업 시작 중' : isSimilarityJobActive ? '진행상황 보기' : 'AI 유사도 분석'}
                        </button>
                    )}
                    {viewConfig.showManagementActions && (
                        <button
                            type="button"
                            onClick={() => setIsBulkAssignmentOpen(true)}
                            disabled={isLoading || isExporting || isSimilarityJobBusy}
                            className="inline-flex items-center justify-center gap-2 rounded-lg border border-violet-200 bg-violet-50 px-3 py-2 text-xs font-semibold text-violet-700 hover:bg-violet-100 disabled:opacity-50 dark:border-violet-900/60 dark:bg-violet-950/30 dark:text-violet-300 dark:hover:bg-violet-950/50"
                        >
                            <FileSpreadsheet className="h-4 w-4" />
                            심사자 일괄배정
                        </button>
                    )}
                    <button
                        onClick={() => void exportExcel()}
                        disabled={isLoading || isExporting || isSimilarityJobBusy}
                        className="inline-flex items-center justify-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-xs font-semibold text-emerald-700 hover:bg-emerald-100 disabled:opacity-50 dark:border-emerald-900/60 dark:bg-emerald-950/30 dark:text-emerald-300 dark:hover:bg-emerald-950/50"
                    >
                        <Download className="h-4 w-4" />
                        {isExporting ? '엑셀 생성 중' : '엑셀 다운로드'}
                    </button>
                    {viewConfig.showManagementActions && (
                        <button
                            type="button"
                            onClick={openCreate}
                            disabled={isSimilarityJobBusy}
                            className="inline-flex items-center justify-center gap-2 rounded-lg bg-emerald-600 px-3 py-2 text-xs font-semibold text-white hover:bg-emerald-700"
                        >
                            <Plus className="h-4 w-4" />
                            초록 등록
                        </button>
                    )}
                </div>
            </div>

            {view === 'submissions' ? (
                <div className="grid grid-cols-1 divide-y divide-slate-200 border-b border-slate-200 dark:divide-slate-800 dark:border-slate-800 sm:grid-cols-2 sm:divide-y-0 xl:grid-cols-5">
                    {[
                        { label: '검색 결과', count: totalCount, hint: '현재 검색 조건에 해당하는 전체 초록 (임시저장 포함)', color: 'text-slate-900 dark:text-slate-50' },
                        { label: '심사자 미배정', count: summary?.unassignedCount, hint: '접수 후 활성 심사자가 한 명도 배정되지 않은 초록', color: 'text-amber-600 dark:text-amber-400' },
                        { label: '심사 미완료', count: summary?.pendingReviewCount, hint: '심사자 배정 후 아직 제출되지 않은 평가가 있는 초록', color: 'text-blue-600 dark:text-blue-400' },
                        { label: '최종 결정 대기', count: summary?.pendingDecisionCount, hint: '배정된 모든 심사자의 평가 제출이 완료되어 승인 또는 반려 결정이 필요한 초록', color: 'text-emerald-600 dark:text-emerald-400' },
                        { label: '채택 초록수', count: summary?.acceptedCount, hint: '현재 검색 조건에 해당하는 최종 승인된 초록 (강제 승인 포함)', color: 'text-emerald-600 dark:text-emerald-400' }
                    ].map((metric, index) => (
                        <div key={metric.label} title={metric.hint} className={`border-slate-200 p-4 dark:border-slate-800 md:p-5 ${index % 2 === 1 ? 'sm:border-l' : ''} ${index >= 2 ? 'sm:border-t xl:border-t-0' : ''} ${index > 0 ? 'xl:border-l' : ''}`}>
                            <p className="text-xs font-medium text-slate-500 dark:text-slate-400">{metric.label}</p>
                            <p className={`mt-1 text-xl font-bold ${metric.color}`}>{summaryCount(metric.count)}</p>
                        </div>
                    ))}
                </div>
            ) : (
            <div className="grid grid-cols-1 border-b border-slate-200 sm:grid-cols-3 dark:border-slate-800">
                <div className="p-4 md:p-5">
                    <p className="text-xs font-medium text-slate-400">{viewConfig.summaryLabel}</p>
                    <p className="mt-1 text-2xl font-bold">{totalCount.toLocaleString()}건</p>
                </div>
                <div className="p-4 md:p-5">
                    <p className="text-xs font-medium text-slate-400">현재 페이지</p>
                    <p className="mt-1 text-2xl font-bold text-blue-600 dark:text-blue-400">{safeCurrentPage} / {totalPages}</p>
                </div>
                <div className="p-4 md:p-5">
                    <p className="text-xs font-medium text-slate-400">표시 범위</p>
                    <p className="mt-1 text-2xl font-bold text-emerald-600 dark:text-emerald-400">{totalCount === 0 ? '0' : `${startIndex}-${endIndex}`}</p>
                </div>
            </div>

            )}

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
                                placeholder="접수번호, 제목, 회원명, 이메일 검색"
                            />
                        </div>
                    </label>
                    <label>
                        <span className="mb-1 block text-[11px] font-semibold text-slate-500">발표형식</span>
                        <select
                            value={draftPresentationTypeCode}
                            disabled={Boolean(viewConfig.presentationTypeAliases)}
                            onChange={(event) => {
                                const value = event.target.value;
                                setDraftPresentationTypeCode(value);
                                applySelectSearch(value, draftCategoryCode, draftStatus);
                            }}
                            className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-500 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-50 dark:disabled:bg-slate-900 dark:disabled:text-slate-400"
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
                            disabled={Boolean(viewConfig.fixedStatus)}
                            onChange={(event) => {
                                const value = event.target.value;
                                setDraftStatus(value);
                                applySelectSearch(draftPresentationTypeCode, draftCategoryCode, value);
                            }}
                            className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-500 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-50 dark:disabled:bg-slate-900 dark:disabled:text-slate-400"
                        >
                            <option value="">전체 상태</option>
                            {Object.entries(STATUS_LABEL).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
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

            {errorMessage && (
                <div className="m-4 rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 dark:border-rose-900/60 dark:bg-rose-950/30 dark:text-rose-300 md:m-5">
                    {errorMessage}
                </div>
            )}

            <MailSelectionSummary count={mail.count} onClear={mail.clear} />
            <div className="overflow-x-auto">
                <table className="min-w-[1200px] w-full border-collapse text-left text-xs md:text-sm">
                    <colgroup>
                        <col className="w-12" />
                        <col style={{ width: '14%' }} />
                        <col style={{ width: '10%' }} />
                        <col style={{ width: '' }} />
                        <col style={{ width: '10%' }} />
                        <col style={{ width: '10%' }} />
                        <col style={{ width: '8%' }} />
                        <col style={{ width: '8%' }} />
                        <col style={{ width: '80px' }} />
                    </colgroup>
                    <thead className="border-b border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900/50">
                        <tr>
                            <th className="w-12 p-4 text-center">
                                <MailSelectionCheckbox checked={mail.allOnPageSelected} indeterminate={mail.someOnPageSelected}
                                    disabled={mailDisabled || !items.length} label="현재 페이지 메일 수신자 전체 선택"
                                    onChange={(checked) => mail.toggle(items, checked)} />
                            </th>
                            <th className="p-4">접수번호</th>
                            <th className="p-4">회원</th>
                            <th className="p-4">제목</th>
                            <th className="p-4">형식 / 분류</th>
                            <th className="p-4">저자 / 기관</th>
                            <th className="p-4">{viewConfig.showManagementActions ? '상태' : '평가'}</th>
                            <th className="p-4">등록일</th>
                            <th className="w-16 p-4 text-center">기능</th>
                        </tr>
                    </thead>
                    <tbody>
                        {isLoading && items.length === 0 && (
                            <tr><td colSpan={9} className="p-8 text-center text-slate-400">초록 목록을 불러오는 중입니다.</td></tr>
                        )}
                        {!isLoading && items.length === 0 && (
                            <tr><td colSpan={9} className="p-8 text-center text-slate-400">{errorMessage ? '목록을 표시할 수 없습니다. 조회 버튼으로 다시 시도해주세요.' : hasSearchCondition ? '검색 조건에 맞는 초록이 없습니다.' : '등록된 초록이 없습니다.'}</td></tr>
                        )}
                        {items.map((item) => (
                            <tr key={item.seq} className={`border-b border-slate-200 dark:border-slate-800 ${mail.selected[item.seq] ? 'bg-blue-50/70 hover:bg-blue-50 dark:bg-blue-950/30 dark:hover:bg-blue-950/50' : 'hover:bg-slate-50/60 dark:hover:bg-slate-900/40'}`}>
                                <td className="p-4 text-center">
                                    <MailSelectionCheckbox checked={!!mail.selected[item.seq]} disabled={mailDisabled}
                                        label={`${item.memberFullName || '이름 미등록'} (${item.submissionNo || item.seq}) 메일 수신자 선택`}
                                        onChange={(checked) => mail.toggle([item], checked)} />
                                </td>
                                <td className="p-4">
                                    <button
                                        type="button"
                                        onClick={() => void openDetail(item.seq)}
                                        className="font-mono font-semibold text-blue-600 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2 dark:text-blue-400 dark:focus-visible:ring-offset-slate-950"
                                    >
                                        {item.submissionNo || `#${item.seq}`}
                                    </button>
                                </td>
                                <td className="p-4">
                                    <div className="flex flex-col gap-1">
                                        <div className="flex items-center gap-2 font-semibold text-slate-900 dark:text-slate-50"><Users className="h-4 w-4 text-slate-400" />{item.memberFullName || '-'}</div>
                                        <div className="text-xs text-slate-400">{item.memberEmail || '-'}</div>
                                        <div className={`text-[11px] font-semibold ${item.submissionSource === 'member' ? 'text-cyan-600 dark:text-cyan-400' : 'text-slate-400'}`}>
                                            {item.submissionSource === 'member' ? '회원 접수 · 관리자 내용 수정 불가' : '관리자 등록'}
                                        </div>
                                    </div>
                                </td>
                                <td className="p-4">
                                    <div className="font-medium text-slate-900 line-clamp-2 dark:text-slate-50">{item.title}</div>
                                    <div className="mt-1 flex flex-wrap gap-1.5 text-[11px] text-slate-400">
                                        {item.aiUsage && <span className="rounded-full bg-violet-50 px-2 py-0.5 text-violet-700 dark:bg-violet-950/40 dark:text-violet-300">AI 사용</span>}
                                        {item.aiDataAnalysisUsed && <span className="rounded-full bg-rose-50 px-2 py-0.5 font-semibold text-rose-700 dark:bg-rose-950/40 dark:text-rose-300">편집 검토 필요</span>}
                                        {(item.titleSimilarityMatchCount ?? 0) > 0 && item.titleSimilarityMaxScore != null && (
                                            <span className="rounded-full bg-amber-50 px-2 py-0.5 font-semibold text-amber-700 dark:bg-amber-950/40 dark:text-amber-300">
                                                유사 제목 {item.titleSimilarityMaxScore.toFixed(1)}%
                                            </span>
                                        )}
                                    </div>
                                </td>
                                <td className="p-4">
                                    <div className="flex flex-col gap-1">
                                        <span className="inline-flex items-center gap-1.5 text-slate-600 dark:text-slate-300"><FileText className="h-4 w-4 text-slate-400" />{viewConfig.presentationTypeAliases ? (item.acceptedPresentationTypeName || item.acceptedPresentationTypeCode || '-') : (item.presentationTypeName || item.presentationTypeCode)}</span>
                                        {viewConfig.presentationTypeAliases && item.presentationTypeName && <span className="text-[11px] text-slate-400">접수 희망: {item.presentationTypeName}</span>}
                                        <span className="text-xs text-slate-400">{item.categoryName || item.categoryCode}</span>
                                    </div>
                                </td>
                                <td className="p-4">
                                    <div className="flex flex-col gap-1">
                                        <span className="inline-flex items-center gap-1.5 text-slate-600 dark:text-slate-300"><Building2 className="h-4 w-4 text-slate-400" />{item.mainAuthorName || '-'}</span>
                                        <span className="text-xs text-slate-400">저자 {item.authorCount ?? 0}명 / 기관 {item.institutionCount ?? 0}개</span>
                                        {viewConfig.showManagementActions && (
                                            <>
                                                <span className="text-xs font-medium text-violet-500">심사 완료 {item.completedReviewCount ?? 0} / {item.reviewerAssignmentCount ?? 0}명</span>
                                                {item.averageReviewScore != null && <span className="text-xs font-semibold text-blue-500">평균 {item.averageReviewScore.toFixed(2)} / 6</span>}
                                            </>
                                        )}
                                    </div>
                                </td>
                                <td className="p-4">
                                    {viewConfig.showManagementActions ? (
                                        <div className="flex flex-col items-start gap-1">
                                            <span className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold ${STATUS_CLASS[item.status]}`}>{STATUS_LABEL[item.status]}</span>
                                            {item.forcedDecision && <span className="text-[11px] font-semibold text-amber-600 dark:text-amber-400">강제 결정</span>}
                                        </div>
                                    ) : (item.completedReviewCount ?? 0) > 0 ? (
                                        <button
                                            type="button"
                                            onClick={() => setReviewResultAbstract(item)}
                                            className="rounded-lg px-2 py-1 text-left transition-colors hover:bg-blue-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 dark:hover:bg-blue-950/30"
                                            title="심사 결과 보기"
                                        >
                                            <span className="block whitespace-nowrap text-sm font-bold text-blue-600 dark:text-blue-400">
                                                {item.averageReviewScore != null ? `${item.averageReviewScore.toFixed(2)} / 6` : '집계 전'}
                                            </span>
                                            <span className="mt-0.5 block whitespace-nowrap text-[11px] text-slate-400">
                                                완료 {item.completedReviewCount ?? 0} / {item.reviewerAssignmentCount ?? 0}명
                                            </span>
                                            {item.forcedDecision && <span className="mt-0.5 block text-[11px] font-semibold text-amber-600 dark:text-amber-400">강제 승인</span>}
                                        </button>
                                    ) : (
                                        <span className="flex flex-col gap-1 text-xs font-medium text-slate-400">
                                            <span>평가 없음</span>
                                            {item.forcedDecision && <span className="font-semibold text-amber-600 dark:text-amber-400">강제 승인</span>}
                                        </span>
                                    )}
                                </td>
                                <td className="p-4 text-slate-500 dark:text-slate-400">{formatDate(item.createdAt)}</td>
                                <td className="relative p-4 text-center">
                                    <button
                                        type="button"
                                        onClick={(event) => toggleActionMenu(item.seq, event)}
                                        className="rounded-md p-1.5 text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800"
                                        aria-label={`${item.submissionNo || item.seq} 기능 메뉴`}
                                    >
                                        <EllipsisVertical className="h-4 w-4" />
                                    </button>
                                    {activeDropdownId === item.seq && (
                                        <div
                                            className="fixed z-[100] w-40 rounded-lg border border-slate-200 bg-white py-1 text-left shadow-lg dark:border-slate-800 dark:bg-slate-950"
                                            style={{ top: dropdownPosition.top, left: dropdownPosition.left }}
                                            onClick={(event) => event.stopPropagation()}
                                        >
                                            {viewConfig.showManagementActions && (
                                                <button
                                                    type="button"
                                                    onClick={() => { setActiveDropdownId(null); setAssignmentAbstract(item); }}
                                                    disabled={item.status !== 'submitted' && item.status !== 'under_review'}
                                                    title={item.status === 'submitted' || item.status === 'under_review' ? '심사자 할당' : '제출완료 또는 심사중 초록만 할당할 수 있습니다.'}
                                                    className="flex w-full items-center gap-2 px-4 py-2 text-sm hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40 dark:hover:bg-slate-900"
                                                >
                                                    <UserPlus className="h-4 w-4 text-violet-500" />심사자 할당
                                                </button>
                                            )}
                                            <button
                                                type="button"
                                                onClick={() => { setActiveDropdownId(null); setReviewResultAbstract(item); }}
                                                disabled={(item.completedReviewCount ?? 0) === 0}
                                                title={(item.completedReviewCount ?? 0) > 0 ? '심사 결과 확인' : '등록된 심사 결과가 없습니다.'}
                                                className="flex w-full items-center gap-2 px-4 py-2 text-sm hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40 dark:hover:bg-slate-900"
                                            >
                                                <BarChart3 className="h-4 w-4 text-blue-500" />심사 결과
                                            </button>
                                            {viewConfig.showManagementActions && (
                                                <button
                                                    type="button"
                                                    onClick={() => { setActiveDropdownId(null); setDecisionAbstract(item); }}
                                                    disabled={item.status !== 'submitted' && item.status !== 'under_review'}
                                                    title={item.status === 'submitted' || item.status === 'under_review' ? '최종 승인 또는 반려 결정' : '이미 최종 결정된 초록입니다.'}
                                                    className="flex w-full items-center gap-2 px-4 py-2 text-sm hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40 dark:hover:bg-slate-900"
                                                >
                                                    <CheckCircle2 className="h-4 w-4 text-emerald-500" />최종 결정
                                                </button>
                                            )}
                                            {item.submissionSource === 'admin' && (
                                                <button
                                                    type="button"
                                                    onClick={() => { setActiveDropdownId(null); void openEdit(item.seq); }}
                                                    className="flex w-full items-center gap-2 px-4 py-2 text-sm hover:bg-slate-50 dark:hover:bg-slate-900"
                                                >
                                                    <Pencil className="h-4 w-4 text-blue-500" />수정
                                                </button>
                                            )}
                                            {viewConfig.showManagementActions && item.submissionSource === 'admin' && (
                                                <button
                                                    type="button"
                                                    onClick={() => { setActiveDropdownId(null); void deleteAbstract(item); }}
                                                    className="flex w-full items-center gap-2 px-4 py-2 text-sm text-rose-600 hover:bg-slate-50 dark:hover:bg-slate-900"
                                                >
                                                    <Trash2 className="h-4 w-4" />삭제
                                                </button>
                                            )}
                                        </div>
                                    )}
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            <div className="flex flex-col items-center justify-between gap-4 border-t border-slate-200 p-4 dark:border-slate-800 md:flex-row md:p-5">
                <div className="text-xs font-medium text-slate-400">
                    전체 <span className="font-bold text-slate-900 dark:text-slate-50">{totalCount.toLocaleString()}</span>건 중
                    <span className="font-bold text-slate-900 dark:text-slate-50"> {totalCount === 0 ? '0' : `${startIndex}-${endIndex}`} </span>
                    표시 중입니다.
                </div>
                <div className="flex items-center gap-1.5">
                    <button disabled={safeCurrentPage === 1} onClick={() => setCurrentPage((page) => Math.max(page - 1, 1))} className="rounded-lg border border-slate-200 p-2 hover:bg-slate-50 disabled:opacity-40 dark:border-slate-800 dark:hover:bg-slate-900"><ChevronLeft className="h-4 w-4" /></button>
                    {pageNumbers.map((page) => (
                        <button key={page} onClick={() => setCurrentPage(page)} className={`h-8 w-8 rounded-lg border text-xs font-bold transition-colors ${safeCurrentPage === page ? 'border-blue-600 bg-blue-600 text-white' : 'border-slate-200 hover:bg-slate-50 dark:border-slate-800 dark:hover:bg-slate-900'}`}>{page}</button>
                    ))}
                    <button disabled={safeCurrentPage === totalPages} onClick={() => setCurrentPage((page) => Math.min(page + 1, totalPages))} className="rounded-lg border border-slate-200 p-2 hover:bg-slate-50 disabled:opacity-40 dark:border-slate-800 dark:hover:bg-slate-900"><ChevronRight className="h-4 w-4" /></button>
                </div>
            </div>

            <MailComposeModal sourceMenu={view === 'submissions' ? 'abstracts' : view === 'oral-accepted' ? 'oral-accepted-abstracts' : 'poster-accepted-abstracts'} isOpen={mail.recipients !== null} recipients={mail.recipients ?? []} onClose={mail.close} onNotify={notify} />
            <AbstractRegisterModal
                key={isModalOpen ? selectedAbstract?.seq ?? 'create' : 'closed'}
                isOpen={isModalOpen}
                abstractSubmission={selectedAbstract}
                onClose={() => { setIsModalOpen(false); setSelectedAbstract(null); }}
                onSuccess={handleModalSuccess}
                onNotify={notify}
            />
            <ReviewerAssignmentModal
                isOpen={assignmentAbstract !== null}
                abstractSubmission={assignmentAbstract}
                onClose={() => setAssignmentAbstract(null)}
                onSuccess={handleAssignmentSuccess}
                onNotify={notify}
            />
            <ReviewerAssignmentBulkImportModal
                isOpen={isBulkAssignmentOpen}
                onClose={() => setIsBulkAssignmentOpen(false)}
                onSuccess={refresh}
                onNotify={notify}
            />
            <AbstractReviewResultModal
                abstractSubmission={reviewResultAbstract}
                onClose={() => setReviewResultAbstract(null)}
                onNotify={notify}
            />
            <AbstractDecisionModal
                key={decisionAbstract?.seq ?? 'closed'}
                abstractSubmission={decisionAbstract}
                presentationTypes={presentationTypes}
                onClose={() => setDecisionAbstract(null)}
                onSuccess={() => { setDecisionAbstract(null); refresh(); }}
                onNotify={notify}
            />
            <AbstractDetailModal
                key={selectedDetailAbstract?.seq ?? 'closed'}
                isOpen={isDetailModalOpen}
                abstractSubmission={selectedDetailAbstract}
                similarityEnabled={abstractSimilarityEnabled}
                similarityAnalysisActive={isSimilarityJobActive}
                onClose={() => { setIsDetailModalOpen(false); setSelectedDetailAbstract(null); }}
                onEdit={handleDetailEdit}
                onDecision={viewConfig.showManagementActions ? handleDetailDecision : undefined}
                onNotify={notify}
            />
            <AbstractSimilarityProgressModal
                key={similarityJob?.seq ?? `similarity-settings-${isSimilarityProgressOpen}`}
                isOpen={isSimilarityProgressOpen}
                job={similarityJob}
                connected={isSimilarityStreamConnected}
                defaultWeights={defaultSimilarityWeights}
                isStarting={isStartingSimilarityAnalysis}
                onStart={startSimilarityAnalysis}
                onClose={() => setIsSimilarityProgressOpen(false)}
            />
        </section>
    );
};
