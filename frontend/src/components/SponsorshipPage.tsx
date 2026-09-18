import { DraggableModal } from './DraggableModal';
import { useEffect, useRef, useState, type DragEvent, type FormEvent, type ReactNode } from 'react';
import {
    Building2,
    ChevronLeft,
    ChevronRight,
    FileText,
    HandHeart,
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
import { MailComposeModal } from './MailComposeModal';
import { MailSelectionButton, MailSelectionCheckbox, MailSelectionSummary } from './MailSelectionControls';
import { useMailSelection } from './useMailSelection';

interface SponsorshipApplication {
    seq: number;
    companyKrName: string;
    companyEnName?: string | null;
    ceoName: string;
    businessNumber: string;
    zonecode: string;
    address: string;
    addressDetail: string;
    sponsorshipType: string;
    sponsorshipAmount: number;
    businessLicenseOriFilename?: string | null;
    businessLicenseUrl?: string | null;
    contactPersonName: string;
    contactPersonPosition?: string | null;
    contactPersonDepartment?: string | null;
    contactPersonPhone: string;
    contactPersonMobile: string;
    contactPersonEmail: string;
    faxNumber?: string | null;
    isDeposited: boolean;
    depositDate?: string | null;
    expectedDepositDate?: string | null;
    taxInvoiceRecipient?: string | null;
    taxInvoiceEmail?: string | null;
    taxInvoiceIssueDate?: string | null;
    taxInvoiceType?: string | null;
    remarks?: string | null;
    createdAt?: string | null;
}

interface SponsorshipPageResponse {
    items: SponsorshipApplication[];
    page: number;
    totalCount: number;
    depositedCount: number;
    pendingCount: number;
    totalAmount: number;
    totalPages: number;
}

interface Props {
    onNotify: (type: NotificationType, message: string) => void;
}

const PAGE_SIZE = 10;
const EMPTY_FORM = {
    companyKrName: '',
    companyEnName: '',
    ceoName: '',
    businessNumber: '',
    zonecode: '',
    address: '',
    addressDetail: '',
    sponsorshipType: '일반후원',
    sponsorshipAmount: '0',
    contactPersonName: '',
    contactPersonPosition: '',
    contactPersonDepartment: '',
    contactPersonPhone: '',
    contactPersonMobile: '',
    contactPersonEmail: '',
    faxNumber: '',
    isDeposited: false,
    depositDate: '',
    expectedDepositDate: '',
    taxInvoiceRecipient: '',
    taxInvoiceEmail: '',
    taxInvoiceIssueDate: '',
    taxInvoiceType: '',
    remarks: ''
};

const dateTimeFormatter = new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
});

const currencyFormatter = new Intl.NumberFormat('ko-KR');

export const SponsorshipPage = ({ onNotify }: Props) => {
    const confirm = useConfirm();
    const [items, setItems] = useState<SponsorshipApplication[]>([]);
    const [searchKeyword, setSearchKeyword] = useState('');
    const [currentPage, setCurrentPage] = useState(1);
    const [totalCount, setTotalCount] = useState(0);
    const [depositedCount, setDepositedCount] = useState(0);
    const [pendingCount, setPendingCount] = useState(0);
    const [totalAmount, setTotalAmount] = useState(0);
    const [totalPages, setTotalPages] = useState(1);
    const [isLoading, setIsLoading] = useState(false);
    const [errorMessage, setErrorMessage] = useState('');
    const [reloadKey, setReloadKey] = useState(0);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [selectedApplication, setSelectedApplication] = useState<SponsorshipApplication | null>(null);
    const mailDisabled = isLoading || !!errorMessage;
    const mail = useMailSelection(items, mailDisabled, (application) => ({
        id: `sponsorship-${application.seq}`,
        sourceSeq: application.seq,
        name: application.contactPersonName,
        email: application.contactPersonEmail,
        affiliation: application.companyKrName
    }));
    const { syncSelected } = mail;
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
                const response = await fetch(`/api/admin/sponsorship-applications?${params.toString()}`, {
                    signal: controller.signal
                });
                if (!response.ok) {
                    throw new Error(await response.text() || '후원 신청 목록을 불러오지 못했습니다.');
                }

                const data = await response.json() as SponsorshipPageResponse;
                if (controller.signal.aborted) return;
                setItems(data.items);
                syncSelected(data.items);
                setTotalCount(data.totalCount);
                setDepositedCount(data.depositedCount);
                setPendingCount(data.pendingCount);
                setTotalAmount(data.totalAmount);
                setTotalPages(data.totalPages);
                if (data.page !== currentPage) {
                    setCurrentPage(data.page);
                }
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') {
                    return;
                }
                const message = error instanceof Error ? error.message : '후원 신청 목록을 불러오지 못했습니다.';
                setErrorMessage(message);
                onNotifyRef.current('error', message);
            } finally {
                if (!controller.signal.aborted) setIsLoading(false);
            }
        };

        void load();
        return () => controller.abort();
    }, [currentPage, reloadKey, searchKeyword, syncSelected]);

    const safeCurrentPage = Math.min(currentPage, totalPages);
    const firstPage = Math.max(1, Math.min(safeCurrentPage - 2, Math.max(1, totalPages - 4)));
    const pageNumbers = Array.from({ length: Math.min(5, totalPages - firstPage + 1) }, (_, index) => firstPage + index);

    const refresh = () => setReloadKey((value) => value + 1);

    const deleteApplication = async (application: SponsorshipApplication) => {
        if (!await confirm({
            title: '후원 신청 삭제',
            message: `후원 신청 "${application.companyKrName}"을 삭제하시겠습니까?`,
            confirmText: '삭제',
            tone: 'danger'
        })) {
            return;
        }

        try {
            const response = await fetch(`/api/admin/sponsorship-applications/${application.seq}`, { method: 'DELETE' });
            if (!response.ok) {
                throw new Error(await response.text() || '후원 신청 삭제에 실패했습니다.');
            }
            onNotifyRef.current('success', '후원 신청이 삭제되었습니다.');
            mail.remove(application.seq);
            refresh();
        } catch (error) {
            const message = error instanceof Error ? error.message : '후원 신청 삭제에 실패했습니다.';
            setErrorMessage(message);
            onNotifyRef.current('error', message);
        }
    };

    return (
        <section className="rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-950">
            <div className="flex flex-col justify-between gap-4 border-b border-slate-200 p-4 dark:border-slate-800 lg:flex-row lg:items-center md:p-5">
                <div>
                    <div className="flex items-center gap-2">
                        <HandHeart className="shrink-0 h-4 w-4 text-slate-400" />
                        <h3 className="text-sm font-semibold md:text-base">후원 신청 관리</h3>
                    </div>
                    <p className="mt-1 text-xs text-slate-400">기업 후원 신청 정보, 담당자, 입금 여부와 세금계산서 정보를 관리합니다.</p>
                </div>
                <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
                    <MailSelectionButton count={mail.count} disabled={mailDisabled} onClick={mail.open} />
                    <div className="relative">
                        <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                        <input
                            value={searchKeyword}
                            onChange={(event) => {
                                mail.clear();
                                setSearchKeyword(event.target.value);
                                setCurrentPage(1);
                            }}
                            className="w-full rounded-lg border border-slate-200 bg-white py-2 pl-9 pr-3 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50 sm:w-72"
                            placeholder="회사명, 사업자번호, 담당자 검색"
                        />
                    </div>
                    <button onClick={() => { setSelectedApplication(null); setIsModalOpen(true); }} className="inline-flex items-center justify-center gap-2 rounded-lg bg-emerald-600 px-3 py-2 text-xs font-semibold text-white hover:bg-emerald-700">
                        <Plus className="h-4 w-4" />
                        후원 신청 등록
                    </button>
                </div>
            </div>

            <div className="grid grid-cols-1 border-b border-slate-200 dark:border-slate-800 sm:grid-cols-4">
                <SummaryCard label="전체 신청" value={`${totalCount.toLocaleString()}건`} />
                <SummaryCard label="입금 완료" value={`${depositedCount.toLocaleString()}건`} accent="text-emerald-600 dark:text-emerald-400" />
                <SummaryCard label="입금 대기" value={`${pendingCount.toLocaleString()}건`} accent="text-amber-600 dark:text-amber-400" />
                <SummaryCard label="후원 총액" value={`${currencyFormatter.format(totalAmount)}원`} accent="text-blue-600 dark:text-blue-400" />
            </div>

            {errorMessage && (
                <div className="m-4 rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 dark:border-rose-900/60 dark:bg-rose-950/30 dark:text-rose-300 md:m-5">
                    {errorMessage}
                </div>
            )}

            <MailSelectionSummary count={mail.count} onClear={mail.clear} />
            <div className="overflow-x-auto">
                <table className="min-w-[1200px] w-full border-collapse text-left text-xs md:text-sm">
                    <thead className="border-b border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900/50">
                        <tr>
                            <th className="w-12 p-4 text-center">
                                <MailSelectionCheckbox checked={mail.allOnPageSelected} indeterminate={mail.someOnPageSelected}
                                    disabled={mailDisabled || !items.length} label="현재 페이지 메일 수신자 전체 선택"
                                    onChange={(checked) => mail.toggle(items, checked)} />
                            </th>
                            <th className="p-4">회사</th>
                            <th className="p-4">후원 구분</th>
                            <th className="p-4">금액</th>
                            <th className="p-4">담당자</th>
                            <th className="p-4">입금</th>
                            <th className="p-4">사업자등록증</th>
                            <th className="p-4">등록일</th>
                            <th className="p-4 text-center">기능</th>
                        </tr>
                    </thead>
                    <tbody>
                        {isLoading && items.length === 0 && <tr><td colSpan={9} className="p-8 text-center text-slate-400">후원 신청 목록을 불러오는 중입니다.</td></tr>}
                        {!isLoading && items.length === 0 && <tr><td colSpan={9} className="p-8 text-center text-slate-400">{errorMessage ? '목록을 표시할 수 없습니다. 검색 조건을 변경해 다시 시도해주세요.' : '등록된 후원 신청이 없습니다.'}</td></tr>}
                        {items.map((application) => (
                            <tr key={application.seq} className={`border-b border-slate-200 dark:border-slate-800 ${mail.selected[application.seq] ? 'bg-blue-50/70 hover:bg-blue-50 dark:bg-blue-950/30 dark:hover:bg-blue-950/50' : 'hover:bg-slate-50/60 dark:hover:bg-slate-900/40'}`}>
                                <td className="p-4 text-center">
                                    <MailSelectionCheckbox checked={!!mail.selected[application.seq]} disabled={mailDisabled}
                                        label={`${application.contactPersonName} (${application.companyKrName}, ${application.seq}) 메일 수신자 선택`}
                                        onChange={(checked) => mail.toggle([application], checked)} />
                                </td>
                                <td className="p-4">
                                    <div className="flex items-start gap-3">
                                        <div className="mt-0.5 flex h-9 w-9 items-center justify-center rounded-lg bg-blue-50 text-blue-600 dark:bg-blue-950/40 dark:text-blue-300">
                                            <Building2 className="h-5 w-5" />
                                        </div>
                                        <div>
                                            <div className="font-semibold text-slate-900 dark:text-slate-50">{application.companyKrName}</div>
                                            <div className="mt-1 text-xs text-slate-400">{application.businessNumber}</div>
                                        </div>
                                    </div>
                                </td>
                                <td className="p-4">{application.sponsorshipType}</td>
                                <td className="p-4 font-semibold">{currencyFormatter.format(application.sponsorshipAmount ?? 0)}원</td>
                                <td className="p-4">
                                    <div className="font-medium">{application.contactPersonName}</div>
                                    <div className="mt-1 text-xs text-slate-400">{application.contactPersonEmail}</div>
                                </td>
                                <td className="p-4">
                                    <span className={`inline-flex rounded-full border px-2.5 py-0.5 text-xs font-semibold ${application.isDeposited ? 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-900/60 dark:bg-emerald-950/40 dark:text-emerald-300' : 'border-amber-200 bg-amber-50 text-amber-700 dark:border-amber-900/60 dark:bg-amber-950/40 dark:text-amber-300'}`}>
                                        {application.isDeposited ? '입금완료' : '입금대기'}
                                    </span>
                                    <div className="mt-1 text-xs text-slate-400">{application.isDeposited ? application.depositDate || '-' : application.expectedDepositDate || '-'}</div>
                                </td>
                                <td className="p-4">
                                    {application.businessLicenseUrl ? (
                                        <a href={application.businessLicenseUrl} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1.5 text-blue-600 hover:underline dark:text-blue-400">
                                            <FileText className="h-4 w-4" />
                                            {application.businessLicenseOriFilename || '파일 보기'}
                                        </a>
                                    ) : <span className="text-slate-400">-</span>}
                                </td>
                                <td className="p-4 text-slate-500 dark:text-slate-400">{formatDateTime(application.createdAt)}</td>
                                <td className="p-4">
                                    <div className="flex items-center justify-center gap-1.5">
                                        <button type="button" onClick={() => { setSelectedApplication(application); setIsModalOpen(true); }} className={rowActionButtonClass} aria-label={`${application.companyKrName} 수정`}>
                                            <Pencil className="h-4 w-4" />
                                        </button>
                                        <button type="button" onClick={() => void deleteApplication(application)} className={`${rowActionButtonClass} !text-rose-600 dark:!text-rose-400`} aria-label={`${application.companyKrName} 삭제`}>
                                            <Trash2 className="h-4 w-4" />
                                        </button>
                                    </div>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            <div className="flex flex-col items-center justify-between gap-4 border-t border-slate-200 p-4 dark:border-slate-800 md:flex-row md:p-5">
                <div className="text-xs font-medium text-slate-400">전체 <span className="font-bold text-slate-900 dark:text-slate-50">{totalCount.toLocaleString()}</span>건</div>
                <div className="flex items-center gap-1.5">
                    <button disabled={safeCurrentPage === 1} onClick={() => setCurrentPage((page) => Math.max(page - 1, 1))} className="rounded-lg border border-slate-200 p-2 hover:bg-slate-50 disabled:opacity-40 dark:border-slate-800 dark:hover:bg-slate-900"><ChevronLeft className="h-4 w-4" /></button>
                    {pageNumbers.map((page) => <button key={page} onClick={() => setCurrentPage(page)} className={`h-8 w-8 rounded-lg border text-xs font-bold ${safeCurrentPage === page ? 'border-blue-600 bg-blue-600 text-white' : 'border-slate-200 hover:bg-slate-50 dark:border-slate-800 dark:hover:bg-slate-900'}`}>{page}</button>)}
                    <button disabled={safeCurrentPage === totalPages} onClick={() => setCurrentPage((page) => Math.min(page + 1, totalPages))} className="rounded-lg border border-slate-200 p-2 hover:bg-slate-50 disabled:opacity-40 dark:border-slate-800 dark:hover:bg-slate-900"><ChevronRight className="h-4 w-4" /></button>
                </div>
            </div>

            <MailComposeModal sourceMenu="sponsorship" isOpen={mail.recipients !== null} recipients={mail.recipients ?? []} onClose={mail.close} onNotify={onNotify} />
            {isModalOpen && <SponsorshipEditModal
                application={selectedApplication}
                onClose={() => { setIsModalOpen(false); setSelectedApplication(null); }}
                onSuccess={(saved) => { syncSelected([saved]); setIsModalOpen(false); setSelectedApplication(null); refresh(); }}
                onNotify={onNotify}
            />}
        </section>
    );
};

interface ModalProps {
    application: SponsorshipApplication | null;
    onClose: () => void;
    onSuccess: (application: SponsorshipApplication) => void;
    onNotify: (type: NotificationType, message: string) => void;
}

const SponsorshipEditModal = ({ application, onClose, onSuccess, onNotify }: ModalProps) => {
    const [form, setForm] = useState(() => application ? {
            companyKrName: application.companyKrName,
            companyEnName: application.companyEnName ?? '',
            ceoName: application.ceoName,
            businessNumber: application.businessNumber,
            zonecode: application.zonecode,
            address: application.address,
            addressDetail: application.addressDetail,
            sponsorshipType: application.sponsorshipType,
            sponsorshipAmount: String(application.sponsorshipAmount ?? 0),
            contactPersonName: application.contactPersonName,
            contactPersonPosition: application.contactPersonPosition ?? '',
            contactPersonDepartment: application.contactPersonDepartment ?? '',
            contactPersonPhone: application.contactPersonPhone,
            contactPersonMobile: application.contactPersonMobile,
            contactPersonEmail: application.contactPersonEmail,
            faxNumber: application.faxNumber ?? '',
            isDeposited: application.isDeposited,
            depositDate: application.depositDate ?? '',
            expectedDepositDate: application.expectedDepositDate ?? '',
            taxInvoiceRecipient: application.taxInvoiceRecipient ?? '',
            taxInvoiceEmail: application.taxInvoiceEmail ?? '',
            taxInvoiceIssueDate: application.taxInvoiceIssueDate ?? '',
            taxInvoiceType: application.taxInvoiceType ?? '',
            remarks: application.remarks ?? ''
        } : EMPTY_FORM);
    const [businessLicenseFile, setBusinessLicenseFile] = useState<File | null>(null);
    const [isDraggingLicense, setIsDraggingLicense] = useState(false);
    const [isSaving, setIsSaving] = useState(false);
    const fileInputRef = useRef<HTMLInputElement>(null);
    const dragDepthRef = useRef(0);

    const updateField = (key: keyof typeof EMPTY_FORM, value: string | boolean) => {
        setForm((current) => ({ ...current, [key]: value }));
    };

    const selectBusinessLicense = (file?: File) => {
        if (!file || isSaving) return;
        const extension = file.name.split('.').pop()?.toLowerCase() ?? '';
        if (!['pdf', 'jpg', 'jpeg', 'png'].includes(extension)) {
            onNotify('error', '사업자등록증은 PDF, JPG/JPEG, PNG 파일만 첨부할 수 있습니다.');
            return;
        }
        if (file.size > 10 * 1024 * 1024) {
            onNotify('error', '사업자등록증은 10MB 이하만 첨부할 수 있습니다.');
            return;
        }
        setBusinessLicenseFile(file);
    };

    const handleLicenseDragEnter = (event: DragEvent<HTMLDivElement>) => {
        event.preventDefault();
        event.stopPropagation();
        if (isSaving || !event.dataTransfer.types.includes('Files')) return;
        dragDepthRef.current += 1;
        setIsDraggingLicense(true);
    };

    const handleLicenseDragOver = (event: DragEvent<HTMLDivElement>) => {
        event.preventDefault();
        event.stopPropagation();
        event.dataTransfer.dropEffect = isSaving ? 'none' : 'copy';
    };

    const handleLicenseDragLeave = (event: DragEvent<HTMLDivElement>) => {
        event.preventDefault();
        event.stopPropagation();
        dragDepthRef.current = Math.max(0, dragDepthRef.current - 1);
        if (dragDepthRef.current === 0) setIsDraggingLicense(false);
    };

    const handleLicenseDrop = (event: DragEvent<HTMLDivElement>) => {
        event.preventDefault();
        event.stopPropagation();
        dragDepthRef.current = 0;
        setIsDraggingLicense(false);
        if (isSaving) return;
        if (event.dataTransfer.files.length > 1) {
            onNotify('error', '사업자등록증은 파일 한 개만 첨부해 주세요.');
            return;
        }
        selectBusinessLicense(event.dataTransfer.files[0]);
    };

    const handleSubmit = async (event: FormEvent) => {
        event.preventDefault();
        setIsSaving(true);

        try {
            const formData = new FormData();
            Object.entries(form).forEach(([key, value]) => {
                if (typeof value === 'boolean') {
                    formData.append(key, String(value));
                } else {
                    formData.append(key, value.trim());
                }
            });
            if (businessLicenseFile) {
                formData.append('businessLicenseFile', businessLicenseFile);
            }

            const response = await fetch(application ? `/api/admin/sponsorship-applications/${application.seq}` : '/api/admin/sponsorship-applications', {
                method: application ? 'PUT' : 'POST',
                body: formData
            });

            if (!response.ok) {
                throw new Error(await response.text() || '후원 신청 저장에 실패했습니다.');
            }

            const saved = await response.json() as SponsorshipApplication;
            onNotify('success', application ? '후원 신청이 수정되었습니다.' : '후원 신청이 등록되었습니다.');
            onSuccess(saved);
        } catch (error) {
            const message = error instanceof Error ? error.message : '후원 신청 저장에 실패했습니다.';
            onNotify('error', message);
        } finally {
            setIsSaving(false);
        }
    };

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/50 p-4">
            <DraggableModal className="max-h-[92vh] w-full max-w-5xl overflow-y-auto rounded-xl border border-slate-200 bg-white shadow-xl dark:border-slate-800 dark:bg-slate-950">
                <div data-modal-drag-handle className="cursor-move select-none touch-none flex items-center justify-between border-b border-slate-200 p-4 dark:border-slate-800">
                    <div>
                        <h3 className="text-base font-bold">{application ? '후원 신청 수정' : '후원 신청 등록'}</h3>
                        <p className="mt-1 text-xs text-slate-400">사업자등록증은 pdf, jpg, jpeg, png 파일을 업로드할 수 있습니다.</p>
                    </div>
                    <button onClick={onClose} className="rounded-lg p-2 text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-900"><X className="h-5 w-5" /></button>
                </div>

                <form onSubmit={handleSubmit} className="space-y-5 p-4 md:p-5">

                    <FormSection title="기업 정보">
                        <TextInput label="회사명(국문)" value={form.companyKrName} onChange={(value) => updateField('companyKrName', value)} required />
                        <TextInput label="회사명(영문)" value={form.companyEnName} onChange={(value) => updateField('companyEnName', value)} />
                        <TextInput label="대표자명" value={form.ceoName} onChange={(value) => updateField('ceoName', value)} required />
                        <TextInput label="사업자등록번호" value={form.businessNumber} onChange={(value) => updateField('businessNumber', value)} required />
                        <TextInput label="우편번호" value={form.zonecode} onChange={(value) => updateField('zonecode', value)} required />
                        <TextInput label="주소" value={form.address} onChange={(value) => updateField('address', value)} required />
                        <TextInput label="상세 주소" value={form.addressDetail} onChange={(value) => updateField('addressDetail', value)} required />
                    </FormSection>

                    <FormSection title="후원 정보">
                        <TextInput label="후원 구분" value={form.sponsorshipType} onChange={(value) => updateField('sponsorshipType', value)} required />
                        <TextInput label="후원 금액" type="number" value={form.sponsorshipAmount} onChange={(value) => updateField('sponsorshipAmount', value)} required />
                        <label className="flex items-center gap-3 rounded-lg border border-slate-200 px-3 py-2 dark:border-slate-800">
                            <input type="checkbox" checked={form.isDeposited} onChange={(event) => updateField('isDeposited', event.target.checked)} className="h-4 w-4 rounded" />
                            <span className="text-sm font-medium">입금 완료</span>
                        </label>
                        <TextInput label="입금일" type="date" value={form.depositDate} onChange={(value) => updateField('depositDate', value)} />
                        <TextInput label="입금 예정일" type="date" value={form.expectedDepositDate} onChange={(value) => updateField('expectedDepositDate', value)} />
                    </FormSection>

                    <FormSection title="담당자 정보">
                        <TextInput label="담당자 이름" value={form.contactPersonName} onChange={(value) => updateField('contactPersonName', value)} required />
                        <TextInput label="직급" value={form.contactPersonPosition} onChange={(value) => updateField('contactPersonPosition', value)} />
                        <TextInput label="부서" value={form.contactPersonDepartment} onChange={(value) => updateField('contactPersonDepartment', value)} />
                        <TextInput label="전화" value={form.contactPersonPhone} onChange={(value) => updateField('contactPersonPhone', value)} required />
                        <TextInput label="휴대전화" value={form.contactPersonMobile} onChange={(value) => updateField('contactPersonMobile', value)} required />
                        <TextInput label="이메일" type="email" value={form.contactPersonEmail} onChange={(value) => updateField('contactPersonEmail', value)} required />
                        <TextInput label="팩스" value={form.faxNumber} onChange={(value) => updateField('faxNumber', value)} />
                    </FormSection>

                    <FormSection title="세금계산서 및 파일">
                        <TextInput label="수령자" value={form.taxInvoiceRecipient} onChange={(value) => updateField('taxInvoiceRecipient', value)} />
                        <TextInput label="이메일" type="email" value={form.taxInvoiceEmail} onChange={(value) => updateField('taxInvoiceEmail', value)} />
                        <TextInput label="발행일" type="date" value={form.taxInvoiceIssueDate} onChange={(value) => updateField('taxInvoiceIssueDate', value)} />
                        <TextInput label="종류" value={form.taxInvoiceType} onChange={(value) => updateField('taxInvoiceType', value)} />
                        <div className="md:col-span-2">
                            <label htmlFor="sponsorship-business-license" className="mb-1.5 block text-sm font-semibold text-slate-700 dark:text-slate-200">사업자등록증</label>
                            <div
                                onDragEnter={handleLicenseDragEnter}
                                onDragOver={handleLicenseDragOver}
                                onDragLeave={handleLicenseDragLeave}
                                onDrop={handleLicenseDrop}
                                className={`rounded-xl border-2 border-dashed p-4 transition-colors ${
                                    isDraggingLicense
                                        ? 'border-blue-500 bg-blue-50 dark:border-blue-400 dark:bg-blue-950/30'
                                        : 'border-slate-300 bg-slate-50/50 dark:border-slate-700 dark:bg-slate-900/30'
                                }`}
                            >
                                <input
                                    ref={fileInputRef}
                                    id="sponsorship-business-license"
                                    type="file"
                                    accept=".pdf,.jpg,.jpeg,.png"
                                    disabled={isSaving}
                                    onChange={(event) => {
                                        selectBusinessLicense(event.target.files?.[0]);
                                        event.target.value = '';
                                    }}
                                    className="hidden"
                                />
                                <button
                                    type="button"
                                    disabled={isSaving}
                                    onClick={() => fileInputRef.current?.click()}
                                    className="inline-flex items-center justify-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900"
                                >
                                    <Upload className="h-4 w-4" />
                                    {businessLicenseFile || application?.businessLicenseOriFilename ? '파일 교체' : '파일 선택'}
                                </button>
                                <p className={`mt-2 text-xs font-medium ${isDraggingLicense ? 'text-blue-600 dark:text-blue-300' : 'text-slate-400 dark:text-slate-400'}`}>
                                    {isDraggingLicense ? '여기에 사업자등록증 파일을 놓으세요.' : '파일을 이 영역에 끌어다 놓거나 버튼으로 선택하세요.'}
                                </p>
                                <p className="mt-1 text-xs text-slate-400 dark:text-slate-400">PDF, JPG/JPEG, PNG · 최대 10MB · 파일 1개</p>
                                <p aria-live="polite" className="mt-1 break-all text-xs text-slate-500 dark:text-slate-300">{businessLicenseFile?.name || application?.businessLicenseOriFilename || '선택된 파일이 없습니다.'}</p>
                            </div>
                        </div>
                        <div className="md:col-span-2">
                            <label className="mb-1.5 block text-xs font-semibold uppercase text-slate-500">비고</label>
                            <textarea value={form.remarks} onChange={(event) => updateField('remarks', event.target.value)} className="min-h-24 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50" />
                        </div>
                    </FormSection>

                    <div className="flex justify-end gap-2 border-t border-slate-200 pt-4 dark:border-slate-800">
                        <button type="button" onClick={onClose} className="rounded-lg border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900">취소</button>
                        <button type="submit" disabled={isSaving} className="rounded-lg bg-blue-600 px-4 py-2 text-xs font-semibold text-white hover:bg-blue-700 disabled:opacity-50">{isSaving ? '저장 중' : '저장'}</button>
                    </div>
                </form>
            </DraggableModal>
        </div>
    );
};

const SummaryCard = ({ label, value, accent = '' }: { label: string; value: string; accent?: string }) => (
    <div className="p-4 md:p-5">
        <p className="text-xs font-medium text-slate-400">{label}</p>
        <p className={`mt-1 text-2xl font-bold ${accent}`}>{value}</p>
    </div>
);

const FormSection = ({ title, children }: { title: string; children: ReactNode }) => (
    <section>
        <div className="mb-3 flex items-center gap-2 text-sm font-bold">
            <HandHeart className="h-4 w-4 text-blue-500" />
            {title}
        </div>
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">{children}</div>
    </section>
);

const TextInput = ({ label, value, onChange, type = 'text', required = false }: {
    label: string;
    value: string;
    onChange: (value: string) => void;
    type?: string;
    required?: boolean;
}) => (
    <div>
        <label className="mb-1.5 block text-xs font-semibold uppercase text-slate-500">{label}</label>
        <input
            type={type}
            value={value}
            onChange={(event) => onChange(event.target.value)}
            required={required}
            className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50"
        />
    </div>
);

const formatDateTime = (value?: string | null) => {
    if (!value) return '-';
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? '-' : dateTimeFormatter.format(date);
};
