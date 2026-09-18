import { DraggableModal } from './DraggableModal';
import { useEffect, useRef, useState, type DragEvent, type FormEvent, type ReactNode } from 'react';
import {
    CalendarDays,
    ChevronLeft,
    ChevronRight,
    ExternalLink,
    HandCoins,
    Image as ImageIcon,
    Pencil,
    Plus,
    Search,
    Trash2,
    Upload,
    X
} from 'lucide-react';
import type { NotificationType } from './NotificationToast';
import { rowActionButtonClass } from './RowActionMenu';
import { useConfirm } from './confirmDialogContext';

interface SponsorItem {
    seq: number;
    sponsorTypeCode: number;
    sponsorTypeName: string;
    sponsorName: string;
    linkUrl?: string | null;
    logoOriFilename?: string | null;
    logoUrl?: string | null;
    useStartDate?: string | null;
    useEndDate?: string | null;
    enabled: boolean;
    sortOrder: number;
    createdAt?: string | null;
    updatedAt?: string | null;
}

interface SponsorType {
    seq: number;
    codeName: string;
    sortOrder: number;
}

interface SponsorPageResponse {
    items: SponsorItem[];
    page: number;
    size: number;
    totalCount: number;
    enabledCount: number;
    totalPages: number;
}

interface SponsorPageProps {
    onNotify: (type: NotificationType, message: string) => void;
}

const PAGE_SIZE = 10;
const ALLOWED_IMAGE_EXTENSIONS = ['jpg', 'jpeg', 'png', 'webp'];

const dateTimeFormatter = new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
});

export const SponsorPage = ({ onNotify }: SponsorPageProps) => {
    const confirm = useConfirm();
    const [items, setItems] = useState<SponsorItem[]>([]);
    const [sponsorTypes, setSponsorTypes] = useState<SponsorType[]>([]);
    const [searchKeyword, setSearchKeyword] = useState('');
    const [currentPage, setCurrentPage] = useState(1);
    const [totalCount, setTotalCount] = useState(0);
    const [enabledCount, setEnabledCount] = useState(0);
    const [totalPages, setTotalPages] = useState(1);
    const [isLoading, setIsLoading] = useState(false);
    const [errorMessage, setErrorMessage] = useState('');
    const [reloadKey, setReloadKey] = useState(0);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [selectedSponsor, setSelectedSponsor] = useState<SponsorItem | null>(null);
    const onNotifyRef = useRef(onNotify);

    useEffect(() => {
        onNotifyRef.current = onNotify;
    }, [onNotify]);

    useEffect(() => {
        const controller = new AbortController();

        const load = async () => {
            setIsLoading(true);
            setErrorMessage('');

            try {
                const params = new URLSearchParams({
                    page: String(currentPage),
                    size: String(PAGE_SIZE),
                    keyword: searchKeyword.trim()
                });
                const [pageResponse, typeResponse] = await Promise.all([
                    fetch(`/api/admin/sponsors?${params.toString()}`, { signal: controller.signal }),
                    fetch('/api/admin/sponsors/types', { signal: controller.signal })
                ]);

                if (!pageResponse.ok) {
                    throw new Error(await pageResponse.text() || '스폰서 목록을 불러오지 못했습니다.');
                }
                if (!typeResponse.ok) {
                    throw new Error(await typeResponse.text() || '스폰서 구분을 불러오지 못했습니다.');
                }

                const data = await pageResponse.json() as SponsorPageResponse;
                const types = await typeResponse.json() as SponsorType[];
                setItems(data.items);
                setSponsorTypes(types);
                setTotalCount(data.totalCount);
                setEnabledCount(data.enabledCount);
                setTotalPages(data.totalPages);
                if (data.page !== currentPage) {
                    setCurrentPage(data.page);
                }
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') {
                    return;
                }
                const message = error instanceof Error ? error.message : '스폰서 목록을 불러오지 못했습니다.';
                setErrorMessage(message);
                onNotifyRef.current('error', message);
            } finally {
                setIsLoading(false);
            }
        };

        void load();
        return () => controller.abort();
    }, [currentPage, reloadKey, searchKeyword]);

    const safeCurrentPage = Math.min(currentPage, totalPages);
    const startIndex = totalCount === 0 ? 0 : (safeCurrentPage - 1) * PAGE_SIZE + 1;
    const endIndex = Math.min((safeCurrentPage - 1) * PAGE_SIZE + items.length, totalCount);
    const firstPage = Math.max(1, Math.min(safeCurrentPage - 2, Math.max(1, totalPages - 4)));
    const pageNumbers = Array.from(
        { length: Math.min(5, totalPages - firstPage + 1) },
        (_, index) => firstPage + index
    );

    const refresh = () => setReloadKey((value) => value + 1);

    const openCreate = () => {
        if (sponsorTypes.length === 0) {
            onNotifyRef.current('error', '사용 가능한 스폰서 구분이 없습니다. 공통코드 parentSeq 9의 하위 코드를 확인해주세요.');
            return;
        }
        setSelectedSponsor(null);
        setIsModalOpen(true);
    };

    const openEdit = (sponsor: SponsorItem) => {
        setSelectedSponsor(sponsor);
        setIsModalOpen(true);
    };

    const deleteSponsor = async (sponsor: SponsorItem) => {
        const confirmed = await confirm({
            title: '스폰서 삭제',
            message: `스폰서 "${sponsor.sponsorName}"을(를) 삭제하시겠습니까?`,
            confirmText: '삭제',
            tone: 'danger'
        });
        if (!confirmed) {
            return;
        }

        try {
            const response = await fetch(`/api/admin/sponsors/${sponsor.seq}`, { method: 'DELETE' });
            if (!response.ok) {
                throw new Error(await response.text() || '스폰서 삭제에 실패했습니다.');
            }
            onNotifyRef.current('success', '스폰서를 삭제했습니다.');
            refresh();
        } catch (error) {
            const message = error instanceof Error ? error.message : '스폰서 삭제에 실패했습니다.';
            setErrorMessage(message);
            onNotifyRef.current('error', message);
        }
    };

    const closeModal = () => {
        setIsModalOpen(false);
        setSelectedSponsor(null);
    };

    const handleModalSuccess = () => {
        closeModal();
        refresh();
    };

    return (
        <section className="rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-950">
            <div className="flex flex-col justify-between gap-4 border-b border-slate-200 p-4 dark:border-slate-800 lg:flex-row lg:items-center md:p-5">
                <div>
                    <div className="flex items-center gap-2">
                        <HandCoins className="shrink-0 h-4 w-4 text-slate-400" />
                        <h3 className="text-sm font-semibold md:text-base">스폰서 관리</h3>
                    </div>
                    <p className="mt-1 text-xs text-slate-400">스폰서 구분, 로고, 링크와 노출 기간을 관리합니다.</p>
                </div>
                <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
                    <div className="relative">
                        <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                        <input
                            value={searchKeyword}
                            onChange={(event) => {
                                setSearchKeyword(event.target.value);
                                setCurrentPage(1);
                            }}
                            className="w-full rounded-lg border border-slate-200 bg-white py-2 pl-9 pr-3 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50 sm:w-72"
                            placeholder="이름, 구분, 링크 검색"
                        />
                    </div>
                    <button
                        type="button"
                        onClick={openCreate}
                        className="inline-flex items-center justify-center gap-2 rounded-lg bg-emerald-600 px-3 py-2 text-xs font-semibold text-white hover:bg-emerald-700"
                    >
                        <Plus className="h-4 w-4" />
                        스폰서 등록
                    </button>
                </div>
            </div>

            <div className="grid grid-cols-1 border-b border-slate-200 sm:grid-cols-3 dark:border-slate-800">
                <SummaryCard label="전체 스폰서" value={`${totalCount.toLocaleString()}건`} />
                <SummaryCard label="사용 스폰서" value={`${enabledCount.toLocaleString()}건`} accent="text-emerald-600 dark:text-emerald-400" />
                <SummaryCard label="표시 범위" value={totalCount === 0 ? '0' : `${startIndex}-${endIndex}`} accent="text-blue-600 dark:text-blue-400" />
            </div>

            {errorMessage && (
                <div className="m-4 rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 dark:border-rose-900/60 dark:bg-rose-950/30 dark:text-rose-300 md:m-5">
                    {errorMessage}
                </div>
            )}

            <div className="overflow-x-auto">
                <table className="w-full min-w-[1100px] border-collapse text-left text-xs md:text-sm">
                    <thead className="border-b border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900/50">
                        <tr>
                            <th className="p-4">로고</th>
                            <th className="p-4">스폰서</th>
                            <th className="p-4">구분</th>
                            <th className="p-4">링크</th>
                            <th className="p-4">사용여부</th>
                            <th className="p-4">노출기간</th>
                            <th className="p-4 text-center">순서</th>
                            <th className="p-4">등록일</th>
                            <th className="p-4 text-center">기능</th>
                        </tr>
                    </thead>
                    <tbody>
                        {isLoading && items.length === 0 && (
                            <tr><td colSpan={9} className="p-8 text-center text-slate-400">스폰서 목록을 불러오는 중입니다.</td></tr>
                        )}
                        {!isLoading && items.length === 0 && (
                            <tr><td colSpan={9} className="p-8 text-center text-slate-400">등록된 스폰서가 없습니다.</td></tr>
                        )}
                        {items.map((sponsor) => (
                            <tr key={sponsor.seq} className="border-b border-slate-200 hover:bg-slate-50/60 dark:border-slate-800 dark:hover:bg-slate-900/40">
                                <td className="p-4">
                                    <div className="flex h-14 w-24 items-center justify-center overflow-hidden rounded-lg border border-slate-200 bg-white p-2 dark:border-slate-800 dark:bg-slate-900">
                                        {sponsor.logoUrl ? (
                                            <img src={sponsor.logoUrl} alt={sponsor.sponsorName} className="h-full w-full object-contain" />
                                        ) : (
                                            <ImageIcon className="h-5 w-5 text-slate-400" />
                                        )}
                                    </div>
                                </td>
                                <td className="p-4">
                                    <div className="font-semibold text-slate-900 dark:text-slate-50">{sponsor.sponsorName}</div>
                                    <div className="mt-1 max-w-48 truncate text-xs text-slate-400">{sponsor.logoOriFilename || '로고 없음'}</div>
                                </td>
                                <td className="p-4">
                                    <span className="rounded-full bg-violet-50 px-2.5 py-1 text-xs font-semibold text-violet-700 dark:bg-violet-950/40 dark:text-violet-300">
                                        {sponsor.sponsorTypeName}
                                    </span>
                                </td>
                                <td className="p-4">
                                    {sponsor.linkUrl ? (
                                        <a href={sponsor.linkUrl} target="_blank" rel="noreferrer" className="inline-flex max-w-56 items-center gap-1.5 text-blue-600 hover:underline dark:text-blue-400">
                                            <ExternalLink className="h-4 w-4 shrink-0" />
                                            <span className="truncate">{sponsor.linkUrl}</span>
                                        </a>
                                    ) : <span className="text-slate-400">-</span>}
                                </td>
                                <td className="p-4">
                                    <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${
                                        sponsor.enabled
                                            ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300'
                                            : 'bg-slate-100 text-slate-500 dark:bg-slate-800 dark:text-slate-400'
                                    }`}>
                                        {sponsor.enabled ? '사용' : '미사용'}
                                    </span>
                                </td>
                                <td className="p-4 text-slate-600 dark:text-slate-300">
                                    <div className="flex items-center gap-1.5">
                                        <CalendarDays className="h-4 w-4 text-slate-400" />
                                        {formatPeriod(sponsor.useStartDate, sponsor.useEndDate)}
                                    </div>
                                </td>
                                <td className="p-4 text-center font-semibold">{sponsor.sortOrder}</td>
                                <td className="p-4 text-slate-500 dark:text-slate-400">
                                    {sponsor.createdAt ? dateTimeFormatter.format(new Date(sponsor.createdAt)) : '-'}
                                </td>
                                <td className="p-4">
                                    <div className="flex justify-center gap-2">
                                        <button
                                            type="button"
                                            onClick={() => openEdit(sponsor)}
                                            className={rowActionButtonClass}
                                            aria-label={`${sponsor.sponsorName} 수정`}
                                        >
                                            <Pencil className="h-4 w-4" />
                                        </button>
                                        <button
                                            type="button"
                                            onClick={() => void deleteSponsor(sponsor)}
                                            className={`${rowActionButtonClass} !text-rose-600 dark:!text-rose-400`}
                                            aria-label={`${sponsor.sponsorName} 삭제`}
                                        >
                                            <Trash2 className="h-4 w-4" />
                                        </button>
                                    </div>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            <div className="flex flex-col items-center justify-between gap-3 p-4 sm:flex-row md:p-5">
                <p className="text-xs text-slate-400">총 {totalCount.toLocaleString()}건 중 {startIndex}-{endIndex}</p>
                <div className="flex items-center gap-1">
                    <PageButton disabled={safeCurrentPage <= 1} onClick={() => setCurrentPage((page) => Math.max(1, page - 1))} label="이전">
                        <ChevronLeft className="h-4 w-4" />
                    </PageButton>
                    {pageNumbers.map((page) => (
                        <button
                            type="button"
                            key={page}
                            onClick={() => setCurrentPage(page)}
                            className={`h-8 min-w-8 rounded-lg px-2 text-xs font-semibold ${
                                page === safeCurrentPage
                                    ? 'bg-blue-600 text-white'
                                    : 'text-slate-500 hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-900'
                            }`}
                        >
                            {page}
                        </button>
                    ))}
                    <PageButton disabled={safeCurrentPage >= totalPages} onClick={() => setCurrentPage((page) => Math.min(totalPages, page + 1))} label="다음">
                        <ChevronRight className="h-4 w-4" />
                    </PageButton>
                </div>
            </div>

            {isModalOpen && (
                <SponsorEditModal
                    sponsor={selectedSponsor}
                    sponsorTypes={sponsorTypes}
                    onClose={closeModal}
                    onSuccess={handleModalSuccess}
                    onNotify={onNotify}
                />
            )}
        </section>
    );
};

interface SponsorEditModalProps {
    sponsor: SponsorItem | null;
    sponsorTypes: SponsorType[];
    onClose: () => void;
    onSuccess: () => void;
    onNotify: (type: NotificationType, message: string) => void;
}

const SponsorEditModal = ({ sponsor, sponsorTypes, onClose, onSuccess, onNotify }: SponsorEditModalProps) => {
    const [sponsorTypeCode, setSponsorTypeCode] = useState(String(sponsor?.sponsorTypeCode ?? sponsorTypes[0]?.seq ?? ''));
    const [sponsorName, setSponsorName] = useState(sponsor?.sponsorName ?? '');
    const [linkUrl, setLinkUrl] = useState(sponsor?.linkUrl ?? '');
    const [useStartDate, setUseStartDate] = useState(sponsor?.useStartDate ?? '');
    const [useEndDate, setUseEndDate] = useState(sponsor?.useEndDate ?? '');
    const [enabled, setEnabled] = useState(sponsor?.enabled ?? true);
    const [sortOrder, setSortOrder] = useState(String(sponsor?.sortOrder ?? 0));
    const [logoFile, setLogoFile] = useState<File | null>(null);
    const [previewUrl, setPreviewUrl] = useState<string | null>(sponsor?.logoUrl ?? null);
    const [isDraggingLogo, setIsDraggingLogo] = useState(false);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const fileInputRef = useRef<HTMLInputElement>(null);
    const dragDepthRef = useRef(0);

    useEffect(() => {
        if (!logoFile) {
            setPreviewUrl(sponsor?.logoUrl ?? null);
            return;
        }
        const objectUrl = URL.createObjectURL(logoFile);
        setPreviewUrl(objectUrl);
        return () => URL.revokeObjectURL(objectUrl);
    }, [logoFile, sponsor]);

    const selectLogo = (file?: File) => {
        if (!file) {
            return;
        }
        const extension = file.name.split('.').pop()?.toLowerCase() ?? '';
        if (!ALLOWED_IMAGE_EXTENSIONS.includes(extension)) {
            const message = '로고는 jpg, jpeg, png, webp 파일만 업로드할 수 있습니다.';
            onNotify('error', message);
            return;
        }
        if (file.size > 2 * 1024 * 1024) {
            const message = '로고는 2MB 이하만 업로드할 수 있습니다.';
            onNotify('error', message);
            return;
        }
        setLogoFile(file);
    };

    const handleLogoDragEnter = (event: DragEvent<HTMLDivElement>) => {
        event.preventDefault();
        event.stopPropagation();
        dragDepthRef.current += 1;
        setIsDraggingLogo(true);
    };

    const handleLogoDragOver = (event: DragEvent<HTMLDivElement>) => {
        event.preventDefault();
        event.stopPropagation();
        event.dataTransfer.dropEffect = 'copy';
    };

    const handleLogoDragLeave = (event: DragEvent<HTMLDivElement>) => {
        event.preventDefault();
        event.stopPropagation();
        dragDepthRef.current = Math.max(0, dragDepthRef.current - 1);
        if (dragDepthRef.current === 0) {
            setIsDraggingLogo(false);
        }
    };

    const handleLogoDrop = (event: DragEvent<HTMLDivElement>) => {
        event.preventDefault();
        event.stopPropagation();
        dragDepthRef.current = 0;
        setIsDraggingLogo(false);
        selectLogo(event.dataTransfer.files?.[0]);
    };

    const handleSubmit = async (event: FormEvent) => {
        event.preventDefault();
        if (!sponsorTypeCode) {
            onNotify('error', '스폰서 구분을 선택해주세요.');
            return;
        }
        if (!sponsorName.trim()) {
            onNotify('error', '스폰서 이름을 입력해주세요.');
            return;
        }
        if (!sponsor && !logoFile) {
            onNotify('error', '스폰서 로고를 선택해주세요.');
            return;
        }
        if (useStartDate && useEndDate && useEndDate < useStartDate) {
            onNotify('error', '사용 종료일은 시작일보다 빠를 수 없습니다.');
            return;
        }

        setIsSubmitting(true);
        try {
            const formData = new FormData();
            formData.append('sponsorTypeCode', sponsorTypeCode);
            formData.append('sponsorName', sponsorName.trim());
            formData.append('linkUrl', linkUrl.trim());
            formData.append('useStartDate', useStartDate);
            formData.append('useEndDate', useEndDate);
            formData.append('enabled', String(enabled));
            formData.append('sortOrder', sortOrder || '0');
            if (logoFile) {
                formData.append('logoFile', logoFile);
            }

            const response = await fetch(sponsor ? `/api/admin/sponsors/${sponsor.seq}` : '/api/admin/sponsors', {
                method: sponsor ? 'PUT' : 'POST',
                body: formData
            });
            if (!response.ok) {
                throw new Error(await response.text() || '스폰서 저장에 실패했습니다.');
            }
            onNotify('success', sponsor ? '스폰서를 수정했습니다.' : '스폰서를 등록했습니다.');
            onSuccess();
        } catch (error) {
            const message = error instanceof Error ? error.message : '스폰서 저장에 실패했습니다.';
            onNotify('error', message);
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4" role="dialog" aria-modal="true">
            <DraggableModal className="max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-2xl bg-white shadow-2xl dark:bg-slate-950">
                <div data-modal-drag-handle className="cursor-move select-none touch-none flex items-center justify-between border-b border-slate-200 px-5 py-4 dark:border-slate-800">
                    <div>
                        <h3 className="text-base font-bold">{sponsor ? '스폰서 수정' : '스폰서 등록'}</h3>
                        <p className="mt-1 text-xs text-slate-400">홈페이지에 노출할 스폰서 정보를 입력합니다.</p>
                    </div>
                    <button type="button" onClick={onClose} className="rounded-lg p-2 text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-900" aria-label="닫기">
                        <X className="h-5 w-5" />
                    </button>
                </div>

                <form onSubmit={(event) => void handleSubmit(event)} className="space-y-5 p-5">

                    <div className="grid gap-4 sm:grid-cols-2">
                        <label className="space-y-1.5 text-sm font-semibold text-slate-700 dark:text-slate-200">
                            <span>스폰서 구분 <span className="text-rose-500">*</span></span>
                            <select
                                value={sponsorTypeCode}
                                onChange={(event) => setSponsorTypeCode(event.target.value)}
                                required
                                className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm font-normal text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50"
                            >
                                <option value="">선택해주세요</option>
                                {sponsorTypes.map((type) => <option key={type.seq} value={type.seq}>{type.codeName}</option>)}
                            </select>
                        </label>
                        <TextInput label="스폰서 이름" value={sponsorName} onChange={setSponsorName} maxLength={150} required />
                    </div>

                    <TextInput label="링크 URL" type="url" value={linkUrl} onChange={setLinkUrl} placeholder="https://example.com" maxLength={1000} />

                    <div className="space-y-2">
                        <span className="text-sm font-semibold text-slate-700 dark:text-slate-200">
                            스폰서 로고 {!sponsor && <span className="text-rose-500">*</span>}
                        </span>
                        <div
                            onDragEnter={handleLogoDragEnter}
                            onDragOver={handleLogoDragOver}
                            onDragLeave={handleLogoDragLeave}
                            onDrop={handleLogoDrop}
                            className={`flex flex-col gap-4 rounded-xl border-2 border-dashed p-4 transition-colors sm:flex-row sm:items-center ${
                                isDraggingLogo
                                    ? 'border-blue-500 bg-blue-50 dark:border-blue-400 dark:bg-blue-950/30'
                                    : 'border-slate-300 bg-slate-50/50 dark:border-slate-700 dark:bg-slate-900/30'
                            }`}
                        >
                            <div className="flex h-24 w-full items-center justify-center overflow-hidden rounded-lg border border-slate-200 bg-white p-3 dark:border-slate-800 dark:bg-slate-900 sm:w-40">
                                {previewUrl ? (
                                    <img src={previewUrl} alt="스폰서 로고 미리보기" className="h-full w-full object-contain" />
                                ) : (
                                    <ImageIcon className="h-8 w-8 text-slate-300 dark:text-slate-600" />
                                )}
                            </div>
                            <div className="flex-1">
                                <input
                                    ref={fileInputRef}
                                    type="file"
                                    accept=".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp"
                                    className="hidden"
                                    onChange={(event) => selectLogo(event.target.files?.[0])}
                                />
                                <button
                                    type="button"
                                    onClick={() => fileInputRef.current?.click()}
                                    className="inline-flex items-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900"
                                >
                                    <Upload className="h-4 w-4" />
                                    {sponsor ? '로고 교체' : '로고 선택'}
                                </button>
                                <p className={`mt-2 text-xs font-medium ${isDraggingLogo ? 'text-blue-600 dark:text-blue-300' : 'text-slate-400'}`}>
                                    {isDraggingLogo ? '여기에 로고 파일을 놓으세요.' : '파일을 이 영역에 끌어다 놓거나 버튼으로 선택하세요.'}
                                </p>
                                <p className="mt-1 text-xs text-slate-400">PNG, WebP, JPG/JPEG · 최대 2MB</p>
                                <p className="mt-1 truncate text-xs text-slate-500 dark:text-slate-300">
                                    {logoFile?.name || sponsor?.logoOriFilename || '선택한 파일 없음'}
                                </p>
                            </div>
                        </div>
                    </div>

                    <div className="grid gap-4 sm:grid-cols-2">
                        <TextInput label="사용 시작일" type="date" value={useStartDate} onChange={setUseStartDate} />
                        <TextInput label="사용 종료일" type="date" value={useEndDate} onChange={setUseEndDate} />
                    </div>

                    <div className="grid gap-4 sm:grid-cols-2">
                        <TextInput label="노출 순서" type="number" value={sortOrder} onChange={setSortOrder} min={0} required />
                        <label className="flex items-center justify-between rounded-lg border border-slate-200 px-4 py-3 dark:border-slate-800">
                            <div>
                                <div className="text-sm font-semibold text-slate-700 dark:text-slate-200">사용 여부</div>
                                <div className="mt-0.5 text-xs text-slate-400">미사용 시 기간과 관계없이 노출되지 않습니다.</div>
                            </div>
                            <input
                                type="checkbox"
                                checked={enabled}
                                onChange={(event) => setEnabled(event.target.checked)}
                                className="h-5 w-5 rounded border-slate-300 text-blue-600 focus:ring-blue-500"
                            />
                        </label>
                    </div>

                    <div className="flex justify-end gap-2 border-t border-slate-200 pt-5 dark:border-slate-800">
                        <button type="button" onClick={onClose} disabled={isSubmitting} className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900">
                            취소
                        </button>
                        <button type="submit" disabled={isSubmitting} className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-50">
                            {isSubmitting ? '저장 중...' : '저장'}
                        </button>
                    </div>
                </form>
            </DraggableModal>
        </div>
    );
};

interface TextInputProps {
    label: string;
    value: string;
    onChange: (value: string) => void;
    type?: string;
    placeholder?: string;
    maxLength?: number;
    min?: number;
    required?: boolean;
}

const TextInput = ({ label, value, onChange, type = 'text', placeholder, maxLength, min, required }: TextInputProps) => (
    <label className="space-y-1.5 text-sm font-semibold text-slate-700 dark:text-slate-200">
        <span>{label} {required && <span className="text-rose-500">*</span>}</span>
        <input
            type={type}
            value={value}
            onChange={(event) => onChange(event.target.value)}
            placeholder={placeholder}
            maxLength={maxLength}
            min={min}
            required={required}
            className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm font-normal text-slate-900 placeholder:text-slate-400 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50"
        />
    </label>
);

const SummaryCard = ({ label, value, accent = 'text-slate-900 dark:text-slate-50' }: { label: string; value: string; accent?: string }) => (
    <div className="border-b border-slate-200 p-4 last:border-b-0 dark:border-slate-800 sm:border-b-0 sm:border-r sm:last:border-r-0 md:p-5">
        <div className="text-xs font-medium text-slate-400">{label}</div>
        <div className={`mt-1 text-xl font-bold ${accent}`}>{value}</div>
    </div>
);

const PageButton = ({ disabled, onClick, label, children }: { disabled: boolean; onClick: () => void; label: string; children: ReactNode }) => (
    <button
        type="button"
        disabled={disabled}
        onClick={onClick}
        aria-label={label}
        className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-500 hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-30 dark:text-slate-400 dark:hover:bg-slate-900"
    >
        {children}
    </button>
);

const formatPeriod = (start?: string | null, end?: string | null) => {
    if (!start && !end) {
        return '상시';
    }
    return `${start || '제한 없음'} ~ ${end || '제한 없음'}`;
};
