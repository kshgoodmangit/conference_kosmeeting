import { DraggableModal } from './DraggableModal';
import { useEffect, useState } from 'react';
import {
    ChevronLeft,
    ChevronRight,
    Check,
    Copy,
    Download,
    Mail,
    Pencil,
    RotateCcw,
    Search,
    ShieldCheck,
    FileSpreadsheet,
    UserPlus
} from 'lucide-react';
import { AdminAccountCreateModal, type EditableAdminAccount } from './AdminAccountCreateModal';
import { AdminBulkImportModal } from './AdminBulkImportModal';
import type { NotificationType } from './NotificationToast';
import { useConfirm } from './confirmDialogContext';
import { useExcelDownloadDialog } from './excelDownloadDialogContext';
import { downloadExcelFile } from '../excelDownload';

interface AdminAccount {
    seq: number;
    email: string;
    adminName: string;
    affiliation?: string | null;
    department?: string | null;
    positionTitle?: string | null;
    phoneNumber?: string | null;
    contactEmail?: string | null;
    role: 'admin' | 'reviewer' | 'maintenance';
    status: 'active' | 'inactive';
    reviewerProfile?: EditableAdminAccount['reviewerProfile'];
    lastLoginAt?: string | null;
    createdAt?: string | null;
    updatedAt?: string | null;
}

interface AdminPageResponse {
    items: AdminAccount[];
    page: number;
    size: number;
    totalCount: number;
    adminCount: number;
    reviewerCount: number;
    maintenanceCount: number;
    totalPages: number;
}

const PAGE_SIZE = 10;
const rowActionButtonClass = 'inline-flex items-center justify-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900';

const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
});

interface AdminPageProps {
    onNotify: (type: NotificationType, message: string) => void;
}

export const AdminPage = ({ onNotify }: AdminPageProps) => {
    const confirm = useConfirm();
    const requestExcelDownload = useExcelDownloadDialog();
    const [admins, setAdmins] = useState<AdminAccount[]>([]);
    const [searchKeyword, setSearchKeyword] = useState('');
    const [currentPage, setCurrentPage] = useState(1);
    const [totalCount, setTotalCount] = useState(0);
    const [adminCount, setAdminCount] = useState(0);
    const [reviewerCount, setReviewerCount] = useState(0);
    const [maintenanceCount, setMaintenanceCount] = useState(0);
    const [totalPages, setTotalPages] = useState(1);
    const [isLoading, setIsLoading] = useState(false);
    const [isDownloading, setIsDownloading] = useState(false);
    const [errorMessage, setErrorMessage] = useState('');
    const [successMessage, setSuccessMessage] = useState('');
    const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
    const [isBulkImportModalOpen, setIsBulkImportModalOpen] = useState(false);
    const [selectedAdmin, setSelectedAdmin] = useState<EditableAdminAccount | null>(null);
    const [reloadKey, setReloadKey] = useState(0);
    const [resetPasswordResult, setResetPasswordResult] = useState<{ email: string; password: string } | null>(null);
    const [isPasswordCopied, setIsPasswordCopied] = useState(false);

    useEffect(() => {
        const abortController = new AbortController();

        const fetchAdmins = async () => {
            setIsLoading(true);
            setErrorMessage('');
            setSuccessMessage('');

            try {
                const params = new URLSearchParams({
                    page: String(currentPage),
                    size: String(PAGE_SIZE),
                    keyword: searchKeyword.trim()
                });
                const response = await fetch(`/api/admin/accounts/page?${params.toString()}`, {
                    signal: abortController.signal
                });

                if (!response.ok) {
                    const message = await response.text();
                    throw new Error(message || '관리자 계정 목록을 불러오지 못했습니다.');
                }

                const data = await response.json() as AdminPageResponse;
                setAdmins(data.items);
                setTotalCount(data.totalCount);
                setAdminCount(data.adminCount);
                setReviewerCount(data.reviewerCount);
                setMaintenanceCount(data.maintenanceCount);
                setTotalPages(data.totalPages);

                if (data.page !== currentPage) {
                    setCurrentPage(data.page);
                }
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') {
                    return;
                }
                const message = error instanceof Error ? error.message : '관리자 계정 목록을 불러오지 못했습니다.';
                setErrorMessage(message);
                onNotify('error', message);
            } finally {
                setIsLoading(false);
            }
        };

        void fetchAdmins();

        return () => abortController.abort();
    }, [currentPage, reloadKey, searchKeyword]);

    const safeCurrentPage = Math.min(currentPage, totalPages);
    const startIndex = (safeCurrentPage - 1) * PAGE_SIZE;
    const firstPageNumber = Math.max(1, Math.min(safeCurrentPage - 2, Math.max(1, totalPages - 4)));
    const pageNumbers = Array.from(
        { length: Math.min(5, totalPages - firstPageNumber + 1) },
        (_, index) => firstPageNumber + index
    );

    const refreshAdmins = () => setReloadKey((value) => value + 1);

    const formatDate = (value?: string | null) => {
        if (!value) {
            return '-';
        }

        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return '-';
        }

        return dateFormatter.format(date);
    };

    const handleSearchChange = (value: string) => {
        setSearchKeyword(value);
        setCurrentPage(1);
    };

    const openCreateModal = () => {
        setSelectedAdmin(null);
        setIsCreateModalOpen(true);
    };

    const openEditModal = (admin: AdminAccount) => {
        setSelectedAdmin({
            seq: admin.seq,
            email: admin.email,
            adminName: admin.adminName,
            affiliation: admin.affiliation,
            department: admin.department,
            positionTitle: admin.positionTitle,
            phoneNumber: admin.phoneNumber,
            contactEmail: admin.contactEmail,
            role: admin.role,
            status: admin.status,
            reviewerProfile: admin.reviewerProfile
        });
        setIsCreateModalOpen(true);
    };

    const handleExcelDownload = async () => {
        setIsDownloading(true);
        try {
            const completed = await requestExcelDownload({
                menuName: '관리자 계정 관리',
                filters: [{ label: '검색어', value: searchKeyword.trim() || '전체' }],
                execute: async (reason) => {
                    await downloadExcelFile('/api/admin/accounts/excel', {
                        reason,
                        keyword: searchKeyword.trim()
                    }, 'admin-accounts.xlsx');
                },
                onError: (message) => onNotify('error', message)
            });
            if (completed) onNotify('success', '엑셀 파일을 다운로드했습니다.');
        } finally {
            setIsDownloading(false);
        }
    };

    const handleResetPassword = async (admin: AdminAccount) => {
        if (!await confirm({
            title: '비밀번호 초기화',
            message: `${admin.email} 계정의 비밀번호를 초기화하시겠습니까?\n초기화하면 기존 비밀번호로 로그인할 수 없습니다.`,
            confirmText: '초기화'
        })) {
            return;
        }

        setErrorMessage('');
        setSuccessMessage('');
        try {
            const response = await fetch(`/api/admin/accounts/${admin.seq}/reset-password`, {
                method: 'POST'
            });

            if (!response.ok) {
                const message = await response.text();
                throw new Error(message || '비밀번호 초기화에 실패했습니다.');
            }

            const newPassword = await response.text();
            setResetPasswordResult({ email: admin.email, password: newPassword });
            setIsPasswordCopied(false);
            refreshAdmins();
        } catch (error) {
            const message = error instanceof Error ? error.message : '비밀번호 초기화에 실패했습니다.';
            setErrorMessage(message);
            onNotify('error', message);
        }
    };

    const handleCopyResetPassword = async () => {
        if (!resetPasswordResult) {
            return;
        }

        try {
            await navigator.clipboard.writeText(resetPasswordResult.password);
            setIsPasswordCopied(true);
        } catch {
            onNotify('error', '비밀번호를 복사하지 못했습니다. 입력란에서 직접 복사해주세요.');
        }
    };

    return (
        <>
            <section className="bg-white dark:bg-slate-950 rounded-xl border border-slate-200 dark:border-slate-800 shadow-sm">
                <div className="p-4 md:p-5 border-b border-slate-200 dark:border-slate-800 flex flex-col xl:flex-row xl:items-center justify-between gap-4">
                    <div>
                        <div className="flex items-center gap-2">
                            <ShieldCheck className="shrink-0 h-4 w-4 text-slate-400" />
                            <h3 className="font-semibold text-sm md:text-base">관리자 계정 관리</h3>
                        </div>
                        <p className="text-xs text-slate-400 mt-1">관리자 계정 목록, 권한, 상태 정보를 관리합니다.</p>
                    </div>

                    <div className="flex flex-col sm:flex-row sm:items-center gap-2">
                        <div className="relative">
                            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                            <input
                                value={searchKeyword}
                                onChange={(event) => handleSearchChange(event.target.value)}
                                className="w-full sm:w-72 pl-9 pr-3 py-2 rounded-lg border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 text-sm text-slate-900 dark:text-slate-50 focus:outline-none focus:border-blue-500"
                                placeholder="아이디, 이름, 권한, 상태 검색"
                            />
                        </div>
                        <button
                            onClick={handleExcelDownload}
                            disabled={isDownloading}
                            className="inline-flex items-center justify-center gap-2 px-3 py-2 rounded-lg text-xs font-semibold border border-blue-200 dark:border-blue-900/60 bg-blue-50 dark:bg-blue-950/30 text-blue-700 dark:text-blue-300 hover:bg-blue-100 dark:hover:bg-blue-950/50 disabled:opacity-50"
                        >
                            <Download className="w-4 h-4" />
                            {isDownloading ? '다운로드 중' : '엑셀다운로드'}
                        </button>
                        <button
                            type="button"
                            onClick={() => setIsBulkImportModalOpen(true)}
                            className="inline-flex items-center justify-center gap-2 rounded-lg border border-violet-200 bg-violet-50 px-3 py-2 text-xs font-semibold text-violet-700 hover:bg-violet-100 disabled:opacity-50 dark:border-violet-900/60 dark:bg-violet-950/30 dark:text-violet-300 dark:hover:bg-violet-950/50"
                        >
                            <FileSpreadsheet className="h-4 w-4" />
                            일괄등록
                        </button>
                        <button
                            onClick={openCreateModal}
                            className="inline-flex items-center justify-center gap-2 px-3 py-2 rounded-lg text-xs font-semibold bg-emerald-600 text-white hover:bg-emerald-700"
                        >
                            <UserPlus className="w-4 h-4" />
                            계정 추가
                        </button>
                    </div>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 border-b border-slate-200 dark:border-slate-800">
                    <div className="p-4 md:p-5 border-b xl:border-b-0 xl:border-r border-slate-200 dark:border-slate-800">
                        <p className="text-xs text-slate-400 font-medium">전체 관리자</p>
                        <p className="text-2xl font-bold mt-1">{totalCount.toLocaleString()}명</p>
                    </div>
                    <div className="p-4 md:p-5 border-b sm:border-b-0 sm:border-r border-slate-200 dark:border-slate-800">
                        <p className="text-xs text-slate-400 font-medium">Admin</p>
                        <p className="text-2xl font-bold mt-1 text-blue-600 dark:text-blue-400">{adminCount.toLocaleString()}명</p>
                    </div>
                    <div className="p-4 md:p-5 border-b sm:border-b-0 sm:border-r border-slate-200 dark:border-slate-800">
                        <p className="text-xs text-slate-400 font-medium">Reviewer</p>
                        <p className="text-2xl font-bold mt-1 text-violet-600 dark:text-violet-400">{reviewerCount.toLocaleString()}명</p>
                    </div>
                    <div className="p-4 md:p-5">
                        <p className="text-xs text-slate-400 font-medium">Maintenance</p>
                        <p className="text-2xl font-bold mt-1 text-emerald-600 dark:text-emerald-400">{maintenanceCount.toLocaleString()}명</p>
                    </div>
                </div>

                {errorMessage && (
                    <div className="m-4 md:m-5 rounded-lg border border-rose-200 dark:border-rose-900/60 bg-rose-50 dark:bg-rose-950/30 px-4 py-3 text-sm text-rose-700 dark:text-rose-300">
                        {errorMessage}
                    </div>
                )}
                {successMessage && (
                    <div className="m-4 md:m-5 rounded-lg border border-emerald-200 dark:border-emerald-900/60 bg-emerald-50 dark:bg-emerald-950/30 px-4 py-3 text-sm text-emerald-700 dark:text-emerald-300">
                        {successMessage}
                    </div>
                )}

                <div className="overflow-x-auto">
                    <table className="w-full text-left border-collapse text-xs md:text-sm min-w-[1000px]">
                        <thead className="bg-slate-50 dark:bg-slate-900/50 border-b border-slate-200 dark:border-slate-800">
                        <tr>
                            <th className="p-4">관리자번호</th>
                            <th className="p-4">관리자명</th>
                            <th className="p-4">관리자 아이디</th>
                            <th className="p-4">권한</th>
                            <th className="p-4">상태</th>
                            <th className="p-4">마지막 로그인</th>
                            <th className="p-4">생성일</th>
                            <th className="p-4 text-center w-16">기능</th>
                        </tr>
                        </thead>
                        <tbody>
                        {isLoading && admins.length === 0 && (
                            <tr>
                                <td colSpan={8} className="p-8 text-center text-slate-400">관리자 계정 목록을 불러오는 중입니다.</td>
                            </tr>
                        )}

                        {!isLoading && admins.length === 0 && (
                            <tr>
                                <td colSpan={8} className="p-8 text-center text-slate-400">표시할 관리자 계정이 없습니다.</td>
                            </tr>
                        )}

                        {admins.map((admin) => (
                            <tr key={admin.seq} className="border-b border-slate-200 dark:border-slate-800 hover:bg-slate-50/50 dark:hover:bg-slate-900/40">
                                <td className="p-4 font-mono text-slate-400">#{admin.seq}</td>
                                <td className="p-4">
                                    <div className="flex items-center gap-2 font-semibold text-slate-900 dark:text-slate-50">
                                        <ShieldCheck className="w-4 h-4 text-slate-400" />
                                        {admin.adminName}
                                    </div>
                                    {admin.reviewerProfile && (
                                        <div className="mt-1 text-xs font-normal text-slate-400">
                                            {[admin.reviewerProfile.affiliation, admin.reviewerProfile.department].filter(Boolean).join(' · ')}
                                        </div>
                                    )}
                                </td>
                                <td className="p-4">
                                    <div className="flex items-center gap-2 text-slate-600 dark:text-slate-300">
                                        <Mail className="w-4 h-4 text-slate-400" />
                                        {admin.email}
                                    </div>
                                </td>
                                <td className="p-4">
                                    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold border ${
                                        admin.role === 'admin'
                                            ? 'bg-blue-50 text-blue-700 border-blue-200 dark:bg-blue-950/40 dark:text-blue-400 dark:border-blue-900/60'
                                            : admin.role === 'reviewer'
                                                ? 'bg-violet-50 text-violet-700 border-violet-200 dark:bg-violet-950/40 dark:text-violet-400 dark:border-violet-900/60'
                                                : 'bg-emerald-50 text-emerald-700 border-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-400 dark:border-emerald-900/60'
                                    }`}>
                                        {admin.role}
                                    </span>
                                </td>
                                <td className="p-4">
                                    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold border ${
                                        admin.status === 'active'
                                            ? 'bg-slate-100 text-slate-700 border-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:border-slate-700'
                                            : 'bg-rose-50 text-rose-700 border-rose-200 dark:bg-rose-950/40 dark:text-rose-400 dark:border-rose-900/60'
                                    }`}>
                                        {admin.status}
                                    </span>
                                </td>
                                <td className="p-4 text-slate-500 dark:text-slate-400">{formatDate(admin.lastLoginAt)}</td>
                                <td className="p-4 text-slate-500 dark:text-slate-400">{formatDate(admin.createdAt)}</td>
                                <td className="p-4 text-center">
                                    <div className="flex items-center justify-center gap-2">
                                        <button type="button" onClick={() => openEditModal(admin)} className={rowActionButtonClass} aria-label={`${admin.adminName} 수정`}><Pencil className="h-4 w-4" /></button>
                                        <button type="button" onClick={() => void handleResetPassword(admin)} className={rowActionButtonClass} aria-label={`${admin.adminName} 비밀번호 초기화`}><RotateCcw className="h-4 w-4" /></button>
                                    </div>
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>

                <div className="p-4 md:p-5 border-t border-slate-200 dark:border-slate-800 flex flex-col sm:flex-row items-center justify-between gap-4">
                    <div className="text-xs text-slate-400 font-medium">
                        전체 <span className="text-slate-900 dark:text-slate-50 font-bold">{totalCount.toLocaleString()}</span>건 중{' '}
                        <span className="text-slate-900 dark:text-slate-50 font-bold">
                            {totalCount === 0 ? '0' : `${startIndex + 1}-${Math.min(startIndex + admins.length, totalCount)}`}
                        </span>
                        행 노출
                    </div>

                    <div className="flex items-center gap-1.5">
                        <button
                            disabled={safeCurrentPage === 1}
                            onClick={() => setCurrentPage((page) => Math.max(page - 1, 1))}
                            className="p-2 border border-slate-200 dark:border-slate-800 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-900 disabled:opacity-40"
                        >
                            <ChevronLeft className="w-4 h-4" />
                        </button>

                        {pageNumbers.map((page) => (
                            <button
                                key={page}
                                onClick={() => setCurrentPage(page)}
                                className={`w-8 h-8 rounded-lg text-xs font-bold border transition-colors ${
                                    safeCurrentPage === page
                                        ? 'border-blue-600 bg-blue-600 text-white'
                                        : 'border-slate-200 dark:border-slate-800 hover:bg-slate-50 dark:hover:bg-slate-900'
                                }`}
                            >
                                {page}
                            </button>
                        ))}

                        <button
                            disabled={safeCurrentPage === totalPages}
                            onClick={() => setCurrentPage((page) => Math.min(page + 1, totalPages))}
                            className="p-2 border border-slate-200 dark:border-slate-800 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-900 disabled:opacity-40"
                        >
                            <ChevronRight className="w-4 h-4" />
                        </button>
                    </div>
                </div>
            </section>

            <AdminAccountCreateModal
                isOpen={isCreateModalOpen}
                admin={selectedAdmin}
                onClose={() => setIsCreateModalOpen(false)}
                onSuccess={refreshAdmins}
                onNotify={onNotify}
            />

            <AdminBulkImportModal
                isOpen={isBulkImportModalOpen}
                onClose={() => setIsBulkImportModalOpen(false)}
                onSuccess={refreshAdmins}
                onNotify={onNotify}
            />

            {resetPasswordResult && (
                <div className="fixed inset-0 z-[110] flex items-center justify-center p-4">
                    <button type="button" aria-label="초기화 결과 닫기" onClick={() => setResetPasswordResult(null)} className="absolute inset-0 bg-slate-900/60" />
                    <DraggableModal role="dialog" aria-modal="true" aria-labelledby="reset-password-result-title" className="relative z-10 w-full max-w-md rounded-xl border border-slate-200 bg-white p-5 shadow-2xl dark:border-slate-800 dark:bg-slate-950">
                        <h3 id="reset-password-result-title" data-modal-drag-handle className="cursor-move select-none touch-none text-base font-bold text-slate-900 dark:text-slate-50">비밀번호 초기화 완료</h3>
                        <p className="mt-2 text-sm text-slate-600 dark:text-slate-300">{resetPasswordResult.email} 계정의 새 비밀번호입니다.</p>
                        <div className="mt-4 flex gap-2">
                            <input value={resetPasswordResult.password} readOnly onFocus={(event) => event.currentTarget.select()} className="min-w-0 flex-1 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2.5 font-mono text-base font-bold tracking-wider text-slate-900 outline-none focus:border-blue-500 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50" />
                            <button type="button" onClick={() => void handleCopyResetPassword()} className="inline-flex shrink-0 items-center gap-1.5 rounded-lg bg-blue-600 px-3 py-2 text-xs font-semibold text-white hover:bg-blue-700">
                                {isPasswordCopied ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
                                {isPasswordCopied ? '복사됨' : '복사'}
                            </button>
                        </div>
                        <p className="mt-2 text-xs text-slate-400">보안을 위해 창을 닫기 전에 비밀번호를 복사해 전달해주세요.</p>
                        <div className="mt-5 flex justify-end">
                            <button type="button" onClick={() => setResetPasswordResult(null)} className="rounded-lg border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900">닫기</button>
                        </div>
                    </DraggableModal>
                </div>
            )}
        </>
    );
};
