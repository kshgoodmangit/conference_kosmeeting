import { DraggableModal } from './DraggableModal';
import { useRef, useState } from 'react';
import type { NotificationType } from './NotificationToast';
import {
    AlertTriangle,
    CheckCircle2,
    Download,
    FileSpreadsheet,
    LoaderCircle,
    Upload,
    X
} from 'lucide-react';

interface BulkImportRowResult {
    rowNumber: number;
    adminId: string;
    success: boolean;
    message: string;
}

interface BulkImportResponse {
    totalCount: number;
    successCount: number;
    failureCount: number;
    results: BulkImportRowResult[];
}

interface AdminBulkImportModalProps {
    isOpen: boolean;
    onClose: () => void;
    onSuccess: () => void;
    onNotify: (type: NotificationType, message: string) => void;
}

const MAX_FILE_BYTES = 5 * 1024 * 1024;

export const AdminBulkImportModal = (props: AdminBulkImportModalProps) => (
    props.isOpen ? <AdminBulkImportContent {...props} /> : null
);

const AdminBulkImportContent = ({ onClose, onSuccess, onNotify }: AdminBulkImportModalProps) => {
    const fileInputRef = useRef<HTMLInputElement>(null);
    const [file, setFile] = useState<File | null>(null);
    const [isDragging, setIsDragging] = useState(false);
    const [isUploading, setIsUploading] = useState(false);
    const [result, setResult] = useState<BulkImportResponse | null>(null);

    const selectFile = (selectedFile?: File) => {
        setResult(null);

        if (!selectedFile) {
            setFile(null);
            return;
        }
        if (!selectedFile.name.toLowerCase().endsWith('.xlsx')) {
            setFile(null);
            onNotify('error', '.xlsx 형식의 엑셀 파일만 등록할 수 있습니다.');
            return;
        }
        if (selectedFile.size > MAX_FILE_BYTES) {
            setFile(null);
            onNotify('error', '관리자 일괄등록 파일은 5MB를 초과할 수 없습니다.');
            return;
        }
        setFile(selectedFile);
    };

    const handleDrop = (event: React.DragEvent<HTMLDivElement>) => {
        event.preventDefault();
        setIsDragging(false);
        selectFile(event.dataTransfer.files.item(0) ?? undefined);
    };

    const handleUpload = async () => {
        if (!file || isUploading) {
            return;
        }

        setIsUploading(true);
        setResult(null);
        try {
            const formData = new FormData();
            formData.append('file', file);
            const response = await fetch('/api/admin/accounts/import', {
                method: 'POST',
                body: formData
            });
            if (!response.ok) {
                const message = await response.text();
                throw new Error(message || '관리자 계정 일괄등록에 실패했습니다.');
            }

            const data = await response.json() as BulkImportResponse;
            setResult(data);
            if (data.successCount > 0) {
                onSuccess();
            }
        } catch (error) {
            onNotify('error', error instanceof Error ? error.message : '관리자 계정 일괄등록에 실패했습니다.');
        } finally {
            setIsUploading(false);
        }
    };

    const failureRows = result?.results.filter((row) => !row.success) ?? [];

    return (
        <div className="fixed inset-0 z-[110] flex items-center justify-center p-4">
            <button
                type="button"
                aria-label="관리자 일괄등록 닫기"
                onClick={isUploading ? undefined : onClose}
                className="absolute inset-0 bg-slate-900/60"
            />
            <DraggableModal
                role="dialog"
                aria-modal="true"
                aria-labelledby="admin-bulk-import-title"
                className="relative z-10 flex max-h-[90vh] w-full max-w-3xl flex-col overflow-hidden rounded-xl border border-slate-200 bg-white shadow-2xl dark:border-slate-800 dark:bg-slate-950"
            >
                <div data-modal-drag-handle className="cursor-move select-none touch-none flex shrink-0 items-start justify-between gap-4 border-b border-slate-200 px-5 py-4 dark:border-slate-800">
                    <div>
                        <h3 id="admin-bulk-import-title" className="flex items-center gap-2 text-base font-bold text-slate-900 dark:text-slate-50">
                            <FileSpreadsheet className="h-5 w-5 shrink-0 text-violet-600 dark:text-violet-400" />
                            관리자 계정 일괄등록
                        </h3>
                        <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                            비밀번호는 각 아이디 뒤에 12#$를 붙여 자동 생성합니다.
                        </p>
                    </div>
                    <button
                        type="button"
                        onClick={onClose}
                        disabled={isUploading}
                        aria-label="관리자 일괄등록 닫기"
                        className="shrink-0 rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 disabled:opacity-40 dark:text-slate-400 dark:hover:bg-slate-900"
                    >
                        <X className="h-5 w-5" />
                    </button>
                </div>

                <div className="overflow-y-auto p-5">
                    <div className="mb-4 flex flex-col gap-2 rounded-lg border border-blue-200 bg-blue-50 px-4 py-3 text-xs text-blue-800 sm:flex-row sm:items-center sm:justify-between dark:border-blue-900/60 dark:bg-blue-950/30 dark:text-blue-300">
                        <span>양식의 첫 번째 시트에 계정 정보를 입력해 주세요. 최대 1,000개까지 등록할 수 있습니다.</span>
                        <a
                            href="/templates/admin_account_bulk_import_template.xlsx"
                            download="admin_account_bulk_import_template.xlsx"
                            className="inline-flex shrink-0 items-center gap-1.5 font-semibold text-blue-700 underline underline-offset-2 dark:text-blue-300"
                        >
                            <Download className="h-4 w-4" />
                            일괄등록 양식 다운로드
                        </a>
                    </div>

                    <input
                        ref={fileInputRef}
                        type="file"
                        accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        className="hidden"
                        onChange={(event) => {
                            selectFile(event.currentTarget.files?.item(0) ?? undefined);
                            event.currentTarget.value = '';
                        }}
                    />
                    <div
                        onDragEnter={(event) => {
                            event.preventDefault();
                            setIsDragging(true);
                        }}
                        onDragOver={(event) => event.preventDefault()}
                        onDragLeave={() => setIsDragging(false)}
                        onDrop={handleDrop}
                        className={`rounded-xl border-2 border-dashed px-5 py-8 text-center transition-colors ${
                            isDragging
                                ? 'border-emerald-500 bg-emerald-50 dark:bg-emerald-950/30'
                                : 'border-slate-300 bg-slate-50 dark:border-slate-700 dark:bg-slate-900/60'
                        }`}
                    >
                        <Upload className={`mx-auto h-9 w-9 ${isDragging ? 'text-emerald-600' : 'text-slate-400'}`} />
                        <p className="mt-3 text-sm font-semibold text-slate-700 dark:text-slate-200">
                            엑셀 파일을 이곳에 끌어다 놓으세요
                        </p>
                        <p className="mt-1 text-xs text-slate-400">또는 파일 선택 버튼을 이용하세요. (.xlsx, 최대 5MB)</p>
                        <button
                            type="button"
                            onClick={() => fileInputRef.current?.click()}
                            disabled={isUploading}
                            className="mt-4 rounded-lg border border-slate-200 bg-white px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-100 disabled:opacity-40 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-200 dark:hover:bg-slate-800"
                        >
                            파일 선택
                        </button>
                    </div>

                    {file && (
                        <div className="mt-3 flex items-center justify-between gap-3 rounded-lg border border-slate-200 px-4 py-3 dark:border-slate-800">
                            <div className="min-w-0">
                                <p className="truncate text-sm font-semibold text-slate-800 dark:text-slate-100">{file.name}</p>
                                <p className="mt-0.5 text-xs text-slate-400">{(file.size / 1024).toFixed(1)} KB</p>
                            </div>
                            <button
                                type="button"
                                onClick={() => selectFile()}
                                disabled={isUploading}
                                className="shrink-0 text-xs font-semibold text-rose-600 hover:underline disabled:opacity-40 dark:text-rose-400"
                            >
                                선택 해제
                            </button>
                        </div>
                    )}

                    {result && (
                        <div className="mt-5 space-y-4">
                            <div className="grid grid-cols-3 gap-2">
                                <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-center dark:border-slate-800 dark:bg-slate-900">
                                    <p className="text-xs text-slate-400">전체</p>
                                    <p className="mt-1 text-xl font-bold text-slate-900 dark:text-slate-50">{result.totalCount}</p>
                                </div>
                                <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-center dark:border-emerald-900/60 dark:bg-emerald-950/30">
                                    <p className="text-xs text-emerald-600 dark:text-emerald-400">성공</p>
                                    <p className="mt-1 text-xl font-bold text-emerald-700 dark:text-emerald-300">{result.successCount}</p>
                                </div>
                                <div className="rounded-lg border border-rose-200 bg-rose-50 p-3 text-center dark:border-rose-900/60 dark:bg-rose-950/30">
                                    <p className="text-xs text-rose-600 dark:text-rose-400">실패</p>
                                    <p className="mt-1 text-xl font-bold text-rose-700 dark:text-rose-300">{result.failureCount}</p>
                                </div>
                            </div>

                            {failureRows.length === 0 ? (
                                <div className="flex items-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700 dark:border-emerald-900/60 dark:bg-emerald-950/30 dark:text-emerald-300">
                                    <CheckCircle2 className="h-5 w-5" />
                                    모든 관리자 계정이 등록되었습니다.
                                </div>
                            ) : (
                                <div>
                                    <h4 className="mb-2 flex items-center gap-2 text-sm font-bold text-slate-800 dark:text-slate-100">
                                        <AlertTriangle className="h-4 w-4 text-rose-500" />
                                        실패 내역
                                    </h4>
                                    <div className="max-h-64 overflow-auto rounded-lg border border-slate-200 dark:border-slate-800">
                                        <table className="w-full min-w-[560px] text-left text-xs">
                                            <thead className="sticky top-0 bg-slate-100 dark:bg-slate-900">
                                            <tr>
                                                <th className="w-20 px-3 py-2">엑셀 행</th>
                                                <th className="w-44 px-3 py-2">아이디</th>
                                                <th className="px-3 py-2">실패 사유</th>
                                            </tr>
                                            </thead>
                                            <tbody>
                                            {failureRows.map((row) => (
                                                <tr key={`${row.rowNumber}-${row.adminId}`} className="border-t border-slate-200 dark:border-slate-800">
                                                    <td className="px-3 py-2 font-mono text-slate-500">{row.rowNumber}행</td>
                                                    <td className="px-3 py-2 font-semibold text-slate-700 dark:text-slate-200">{row.adminId || '-'}</td>
                                                    <td className="px-3 py-2 text-rose-600 dark:text-rose-400">{row.message}</td>
                                                </tr>
                                            ))}
                                            </tbody>
                                        </table>
                                    </div>
                                </div>
                            )}
                        </div>
                    )}
                </div>

                <div className="flex shrink-0 justify-end gap-2 border-t border-slate-200 px-5 py-4 dark:border-slate-800">
                    <button
                        type="button"
                        onClick={onClose}
                        disabled={isUploading}
                        className="rounded-lg border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-40 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900"
                    >
                        닫기
                    </button>
                    <button
                        type="button"
                        onClick={() => void handleUpload()}
                        disabled={!file || isUploading}
                        className="inline-flex items-center gap-2 rounded-lg bg-violet-600 px-4 py-2 text-xs font-semibold text-white hover:bg-violet-700 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-violet-600 dark:text-white dark:hover:bg-violet-700"
                    >
                        {isUploading ? <LoaderCircle className="h-4 w-4 animate-spin" /> : <Upload className="h-4 w-4" />}
                        {isUploading ? '등록 중' : '일괄등록'}
                    </button>
                </div>
            </DraggableModal>
        </div>
    );
};
