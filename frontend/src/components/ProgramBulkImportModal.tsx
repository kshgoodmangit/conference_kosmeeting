import { useEffect, useRef, useState, type FormEvent } from 'react';
import {
    AlertTriangle,
    CheckCircle2,
    Download,
    FileSpreadsheet,
    LoaderCircle,
    Upload,
    X
} from 'lucide-react';
import { DraggableModalForm } from './DraggableModal';
import type { NotificationType } from './NotificationToast';

interface ProgramBulkImportRowResult {
    rowNumber: number;
    title: string;
    status: 'SUCCESS' | 'SKIPPED' | 'FAILED';
    message: string;
}

interface ProgramBulkImportResponse {
    totalCount: number;
    successCount: number;
    skippedCount: number;
    failureCount: number;
    results: ProgramBulkImportRowResult[];
}

interface ProgramBulkImportModalProps {
    onClose: () => void;
    onSuccess: () => void;
    onNotify: (type: NotificationType, message: string) => void;
}

const MAX_FILE_BYTES = 5 * 1024 * 1024;

export const ProgramBulkImportModal = ({ onClose, onSuccess, onNotify }: ProgramBulkImportModalProps) => {
    const modalRef = useRef<HTMLFormElement>(null);
    const fileInputRef = useRef<HTMLInputElement>(null);
    const [file, setFile] = useState<File | null>(null);
    const [isDragging, setIsDragging] = useState(false);
    const [isUploading, setIsUploading] = useState(false);
    const [result, setResult] = useState<ProgramBulkImportResponse | null>(null);

    useEffect(() => {
        const previousOverflow = document.body.style.overflow;
        document.body.style.overflow = 'hidden';
        modalRef.current?.focus();
        const handleKeyDown = (event: KeyboardEvent) => {
            if (event.key === 'Escape' && !isUploading) onClose();
        };
        window.addEventListener('keydown', handleKeyDown);
        return () => {
            document.body.style.overflow = previousOverflow;
            window.removeEventListener('keydown', handleKeyDown);
        };
    }, [isUploading, onClose]);

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
            onNotify('error', '프로그램 일괄등록 파일은 5MB를 초과할 수 없습니다.');
            return;
        }
        setFile(selectedFile);
    };

    const submit = async (event: FormEvent) => {
        event.preventDefault();
        if (!file || isUploading || result) return;
        setIsUploading(true);
        try {
            const formData = new FormData();
            formData.append('file', file);
            const response = await fetch('/api/admin/program/items/bulk-import', {
                method: 'POST',
                body: formData
            });
            if (!response.ok) {
                throw new Error(await response.text() || '프로그램 일괄등록에 실패했습니다.');
            }
            const imported = await response.json() as ProgramBulkImportResponse;
            setResult(imported);
            if (imported.successCount > 0) {
                onSuccess();
                onNotify('success', `프로그램 ${imported.successCount}건을 등록했습니다.`);
            } else if (imported.skippedCount > 0 && imported.failureCount === 0) {
                onNotify('info', '등록할 새 프로그램이 없습니다. 모든 행을 건너뛰었습니다.');
            }
        } catch (error) {
            onNotify('error', error instanceof Error ? error.message : '프로그램 일괄등록에 실패했습니다.');
        } finally {
            setIsUploading(false);
        }
    };

    const close = () => {
        if (!isUploading) onClose();
    };

    return (
        <div className="fixed inset-0 z-[110] flex items-center justify-center p-4">
            <button type="button" aria-label="프로그램 일괄등록 닫기" onClick={close} className="absolute inset-0 bg-slate-900/60" />
            <DraggableModalForm
                ref={modalRef}
                onSubmit={(event) => void submit(event)}
                role="dialog"
                aria-modal="true"
                aria-labelledby="program-bulk-import-title"
                tabIndex={-1}
                className="relative z-10 flex max-h-[90vh] w-full max-w-4xl flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl outline-none dark:border-slate-800 dark:bg-slate-950"
            >
                <div data-modal-drag-handle className="flex shrink-0 cursor-move select-none touch-none items-start justify-between gap-4 border-b border-slate-200 px-5 py-4 dark:border-slate-800">
                    <div>
                        <h3 id="program-bulk-import-title" className="flex items-center gap-2 text-base font-bold text-slate-900 dark:text-slate-50">
                            <FileSpreadsheet className="h-5 w-5 shrink-0 text-violet-600 dark:text-violet-400" />
                            프로그램 엑셀 일괄등록
                        </h3>
                        <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                            등록된 일자와 룸을 기준으로 프로그램을 추가하며 기존 프로그램은 유지합니다.
                        </p>
                    </div>
                    <button type="button" onClick={close} disabled={isUploading} aria-label="프로그램 일괄등록 닫기" className="shrink-0 rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 disabled:opacity-40 dark:hover:bg-slate-900">
                        <X className="h-5 w-5" />
                    </button>
                </div>

                <div className="overflow-y-auto p-5">
                    <div className="mb-4 flex flex-col gap-2 rounded-lg border border-blue-200 bg-blue-50 px-4 py-3 text-xs text-blue-800 sm:flex-row sm:items-center sm:justify-between dark:border-blue-900/60 dark:bg-blue-950/30 dark:text-blue-300">
                        <span>첫 번째 시트에 최대 500개 프로그램을 입력할 수 있습니다. 세션은 하위 발표보다 먼저 처리됩니다.</span>
                        <a href="/templates/program_bulk_import_template.xlsx" download="program_bulk_import_template.xlsx" className="inline-flex shrink-0 items-center gap-1.5 font-semibold text-blue-700 underline underline-offset-2 dark:text-blue-300">
                            <Download className="h-4 w-4" />
                            일괄등록 양식 다운로드
                        </a>
                    </div>

                    <input
                        ref={fileInputRef}
                        type="file"
                        accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        className="hidden"
                        disabled={isUploading}
                        onChange={(event) => {
                            selectFile(event.currentTarget.files?.item(0) ?? undefined);
                            event.currentTarget.value = '';
                        }}
                    />
                    <div
                        onDragEnter={(event) => { event.preventDefault(); if (!isUploading) setIsDragging(true); }}
                        onDragOver={(event) => event.preventDefault()}
                        onDragLeave={() => setIsDragging(false)}
                        onDrop={(event) => {
                            event.preventDefault();
                            setIsDragging(false);
                            if (!isUploading) selectFile(event.dataTransfer.files.item(0) ?? undefined);
                        }}
                        className={`rounded-xl border-2 border-dashed px-5 py-8 text-center transition-colors ${isDragging ? 'border-violet-500 bg-violet-50 dark:bg-violet-950/30' : 'border-slate-300 bg-slate-50 dark:border-slate-700 dark:bg-slate-900/60'}`}
                    >
                        <Upload className={`mx-auto h-9 w-9 ${isDragging ? 'text-violet-600' : 'text-slate-400'}`} />
                        <p className="mt-3 text-sm font-semibold text-slate-700 dark:text-slate-200">엑셀 파일을 이곳에 끌어다 놓으세요</p>
                        <p className="mt-1 text-xs text-slate-400">.xlsx 파일만 가능하며 최대 크기는 5MB입니다.</p>
                        <button type="button" onClick={() => fileInputRef.current?.click()} disabled={isUploading} className="mt-4 rounded-lg border border-slate-200 bg-white px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-100 disabled:opacity-40 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-200 dark:hover:bg-slate-800">
                            {file ? '다른 파일 선택' : '파일 선택'}
                        </button>
                    </div>

                    {file && (
                        <div className="mt-3 flex items-center justify-between gap-3 rounded-lg border border-slate-200 px-4 py-3 dark:border-slate-800">
                            <div className="min-w-0">
                                <p className="truncate text-sm font-semibold text-slate-800 dark:text-slate-100">{file.name}</p>
                                <p className="mt-0.5 text-xs text-slate-400">{(file.size / 1024).toFixed(1)} KB</p>
                            </div>
                            <button type="button" onClick={() => selectFile()} disabled={isUploading} className="shrink-0 text-xs font-semibold text-rose-600 hover:underline disabled:opacity-40 dark:text-rose-400">선택 해제</button>
                        </div>
                    )}

                    {result && <ImportResult result={result} />}
                </div>

                <div className="flex shrink-0 justify-end gap-2 border-t border-slate-200 px-5 py-4 dark:border-slate-800">
                    <button type="button" onClick={close} disabled={isUploading} className="rounded-lg border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-40 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900">닫기</button>
                    <button type="submit" disabled={!file || isUploading || Boolean(result)} className="inline-flex items-center gap-2 rounded-lg bg-violet-600 px-4 py-2 text-xs font-semibold text-white hover:bg-violet-700 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-violet-600 dark:text-white dark:hover:bg-violet-700">
                        {isUploading ? <LoaderCircle className="h-4 w-4 animate-spin" /> : <Upload className="h-4 w-4" />}
                        {isUploading ? '등록 중' : '일괄등록'}
                    </button>
                </div>
            </DraggableModalForm>
        </div>
    );
};

const ImportResult = ({ result }: { result: ProgramBulkImportResponse }) => (
    <div className="mt-5 space-y-4">
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
            <ResultCount label="전체" value={result.totalCount} className="text-slate-900 dark:text-slate-50" />
            <ResultCount label="성공" value={result.successCount} className="text-emerald-700 dark:text-emerald-300" />
            <ResultCount label="건너뜀" value={result.skippedCount} className="text-amber-700 dark:text-amber-300" />
            <ResultCount label="실패" value={result.failureCount} className="text-rose-700 dark:text-rose-300" />
        </div>
        {result.failureCount === 0 && result.skippedCount === 0 && (
            <div className="flex items-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700 dark:border-emerald-900/60 dark:bg-emerald-950/30 dark:text-emerald-300">
                <CheckCircle2 className="h-5 w-5" />모든 프로그램이 등록되었습니다.
            </div>
        )}
        <div>
            <h4 className="mb-2 flex items-center gap-2 text-sm font-bold text-slate-800 dark:text-slate-100">
                <AlertTriangle className="h-4 w-4 text-violet-500" />행별 처리 결과
            </h4>
            <div className="max-h-72 overflow-auto rounded-lg border border-slate-200 dark:border-slate-800">
                <table className="w-full min-w-[680px] text-left text-xs">
                    <thead className="sticky top-0 bg-slate-100 dark:bg-slate-900">
                        <tr><th className="w-20 px-3 py-2">엑셀 행</th><th className="px-3 py-2">프로그램명</th><th className="w-24 px-3 py-2">결과</th><th className="px-3 py-2">처리 내용</th></tr>
                    </thead>
                    <tbody>
                        {result.results.map((row) => (
                            <tr key={row.rowNumber} className="border-t border-slate-200 dark:border-slate-800">
                                <td className="px-3 py-2 font-mono text-slate-500">{row.rowNumber}행</td>
                                <td className="max-w-64 break-words px-3 py-2 font-semibold text-slate-700 dark:text-slate-200">{row.title || '-'}</td>
                                <td className="px-3 py-2"><ResultBadge status={row.status} /></td>
                                <td className="px-3 py-2 text-slate-600 dark:text-slate-300">{row.message}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        </div>
    </div>
);

const ResultCount = ({ label, value, className }: { label: string; value: number; className: string }) => (
    <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-center dark:border-slate-800 dark:bg-slate-900">
        <p className="text-xs text-slate-400">{label}</p>
        <p className={`mt-1 text-xl font-bold ${className}`}>{value}</p>
    </div>
);

const ResultBadge = ({ status }: { status: ProgramBulkImportRowResult['status'] }) => {
    const styles = status === 'SUCCESS'
        ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300'
        : status === 'SKIPPED'
            ? 'bg-amber-50 text-amber-700 dark:bg-amber-950/40 dark:text-amber-300'
            : 'bg-rose-50 text-rose-700 dark:bg-rose-950/40 dark:text-rose-300';
    return <span className={`inline-flex rounded-full px-2 py-1 font-semibold ${styles}`}>{status === 'SUCCESS' ? '성공' : status === 'SKIPPED' ? '건너뜀' : '실패'}</span>;
};
