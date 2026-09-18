import { useRef, useState } from 'react';
import {
    CalendarClock,
    CheckCircle2,
    Download,
    FileSpreadsheet,
    LoaderCircle,
    Upload,
    X
} from 'lucide-react';
import type { NotificationType } from './NotificationToast';
import { useModalDrag } from '../hooks/useModalDrag';

type ImportStatus = 'success' | 'skipped' | 'failure';

interface BulkImportRowResult {
    rowNumber: number;
    submissionNo: string;
    reviewerId: string;
    status: ImportStatus;
    message: string;
}

interface BulkImportResponse {
    totalCount: number;
    successCount: number;
    skippedCount: number;
    failureCount: number;
    results: BulkImportRowResult[];
}

interface Props {
    isOpen: boolean;
    onClose: () => void;
    onSuccess: () => void;
    onNotify: (type: NotificationType, message: string) => void;
}

const MAX_FILE_BYTES = 5 * 1024 * 1024;

export const ReviewerAssignmentBulkImportModal = ({
    isOpen,
    onClose,
    onSuccess,
    onNotify
}: Props) => {
    const { modalRef, headerRef } = useModalDrag(isOpen);
    const fileInputRef = useRef<HTMLInputElement>(null);
    const [file, setFile] = useState<File | null>(null);
    const [dueAt, setDueAt] = useState('');
    const [isDragging, setIsDragging] = useState(false);
    const [isUploading, setIsUploading] = useState(false);
    const [result, setResult] = useState<BulkImportResponse | null>(null);

    const close = () => {
        if (isUploading) return;
        setFile(null);
        setDueAt('');
        setIsDragging(false);
        setResult(null);
        onClose();
    };

    if (!isOpen) return null;

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
            onNotify('error', '심사자 일괄배정 파일은 5MB를 초과할 수 없습니다.');
            return;
        }
        setFile(selectedFile);
    };

    const upload = async () => {
        if (!file || isUploading) return;
        setIsUploading(true);
        setResult(null);
        try {
            const formData = new FormData();
            formData.append('file', file);
            if (dueAt) formData.append('dueAt', `${dueAt}:00`);
            const response = await fetch('/api/admin/abstracts/review-assignments/import', {
                method: 'POST',
                body: formData
            });
            if (!response.ok) {
                throw new Error(await response.text() || '심사자 일괄배정에 실패했습니다.');
            }
            const data = await response.json() as BulkImportResponse;
            setResult(data);
            if (data.successCount > 0) {
                onSuccess();
                onNotify('success', `심사자 배정 ${data.successCount}건을 등록했습니다.`);
            }
        } catch (error) {
            const message = error instanceof Error ? error.message : '심사자 일괄배정에 실패했습니다.';
            onNotify('error', message);
        } finally {
            setIsUploading(false);
        }
    };

    const detailRows = result?.results.filter((row) => row.status !== 'success') ?? [];

    return (
        <div className="fixed inset-0 z-[110] flex items-center justify-center p-4">
            <button type="button" aria-label="심사자 일괄배정 닫기" onClick={close} disabled={isUploading} className="absolute inset-0 bg-slate-900/60" />
            <div
                ref={modalRef}
                role="dialog"
                aria-modal="true"
                aria-labelledby="reviewer-bulk-import-title"
                className="absolute left-1/2 top-1/2 z-10 flex max-h-[90vh] w-[calc(100%-2rem)] max-w-4xl flex-col overflow-hidden rounded-xl border border-slate-200 bg-white shadow-2xl dark:border-slate-800 dark:bg-slate-950"
                style={{ transform: 'translate(-50%, -50%)' }}
            >
                <div
                    ref={headerRef}
                    className="flex shrink-0 cursor-move select-none touch-none items-start justify-between gap-4 border-b border-slate-200 px-5 py-4 dark:border-slate-800"
                >
                    <div>
                        <h3 id="reviewer-bulk-import-title" className="flex items-center gap-2 text-base font-bold text-slate-900 dark:text-slate-50">
                            <FileSpreadsheet className="h-5 w-5 shrink-0 text-violet-600 dark:text-violet-400" />심사자 일괄배정
                        </h3>
                        <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">초록번호와 심사자 로그인 아이디를 이용해 기존 배정을 유지하면서 추가합니다.</p>
                    </div>
                    <button type="button" onClick={close} disabled={isUploading} aria-label="심사자 일괄배정 닫기" className="shrink-0 rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 disabled:opacity-40 dark:text-slate-400 dark:hover:bg-slate-900"><X className="h-5 w-5" /></button>
                </div>

                <div className="overflow-y-auto p-5">
                    <div className="mb-4 flex flex-col gap-3 rounded-lg border border-violet-200 bg-violet-50 px-4 py-3 text-xs text-violet-800 sm:flex-row sm:items-center sm:justify-between dark:border-violet-900/60 dark:bg-violet-950/30 dark:text-violet-300">
                        <span>한 초록에 여러 심사자를 배정하려면 초록번호를 반복 입력하세요. 최대 1,000건까지 처리합니다.</span>
                        <a
                            href="/templates/abstract_reviewer_assignment_template.xlsx"
                            download="abstract_reviewer_assignment_template.xlsx"
                            className="inline-flex shrink-0 items-center gap-1.5 font-semibold text-violet-700 underline underline-offset-2 dark:text-violet-300"
                        >
                            <Download className="h-4 w-4" />
                            일괄배정 양식 다운로드
                        </a>
                    </div>

                    <label className="mb-4 block">
                        <span className="mb-1.5 block text-xs font-semibold text-slate-600 dark:text-slate-300">공통 심사 마감일시 <span className="font-normal text-slate-400">(선택)</span></span>
                        <div className="relative max-w-sm">
                            <CalendarClock className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                            <input type="datetime-local" value={dueAt} onChange={(event) => setDueAt(event.target.value)} disabled={isUploading} className="w-full rounded-lg border border-slate-200 bg-white py-2 pl-9 pr-3 text-sm text-slate-900 focus:border-violet-500 focus:outline-none disabled:opacity-50 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-50" />
                        </div>
                    </label>

                    <input ref={fileInputRef} type="file" accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" className="hidden" onChange={(event) => { selectFile(event.currentTarget.files?.item(0) ?? undefined); event.currentTarget.value = ''; }} />
                    <div
                        onDragEnter={(event) => { event.preventDefault(); setIsDragging(true); }}
                        onDragOver={(event) => event.preventDefault()}
                        onDragLeave={() => setIsDragging(false)}
                        onDrop={(event) => { event.preventDefault(); setIsDragging(false); selectFile(event.dataTransfer.files.item(0) ?? undefined); }}
                        className={`rounded-xl border-2 border-dashed px-5 py-8 text-center transition-colors ${isDragging ? 'border-violet-500 bg-violet-50 dark:bg-violet-950/30' : 'border-slate-300 bg-slate-50 dark:border-slate-700 dark:bg-slate-900/60'}`}
                    >
                        <Upload className={`mx-auto h-9 w-9 ${isDragging ? 'text-violet-600' : 'text-slate-400'}`} />
                        <p className="mt-3 text-sm font-semibold text-slate-700 dark:text-slate-200">엑셀 파일을 이곳에 끌어다 놓으세요</p>
                        <p className="mt-1 text-xs text-slate-400">.xlsx 형식, 최대 5MB</p>
                        <button type="button" onClick={() => fileInputRef.current?.click()} disabled={isUploading} className="mt-4 rounded-lg border border-slate-200 bg-white px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-100 disabled:opacity-40 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-200 dark:hover:bg-slate-800">파일 선택</button>
                    </div>

                    {file && (
                        <div className="mt-3 flex items-center justify-between gap-3 rounded-lg border border-slate-200 px-4 py-3 dark:border-slate-800">
                            <div className="min-w-0"><p className="truncate text-sm font-semibold text-slate-800 dark:text-slate-100">{file.name}</p><p className="mt-0.5 text-xs text-slate-400">{(file.size / 1024).toFixed(1)} KB</p></div>
                            <button type="button" onClick={() => selectFile()} disabled={isUploading} className="shrink-0 text-xs font-semibold text-rose-600 hover:underline disabled:opacity-40 dark:text-rose-400">선택 해제</button>
                        </div>
                    )}

                    {result && (
                        <div className="mt-5 space-y-4">
                            <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
                                {[
                                    ['전체', result.totalCount, 'border-slate-200 bg-slate-50 text-slate-900 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50'],
                                    ['배정', result.successCount, 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-900/60 dark:bg-emerald-950/30 dark:text-emerald-300'],
                                    ['건너뜀', result.skippedCount, 'border-amber-200 bg-amber-50 text-amber-700 dark:border-amber-900/60 dark:bg-amber-950/30 dark:text-amber-300'],
                                    ['실패', result.failureCount, 'border-rose-200 bg-rose-50 text-rose-700 dark:border-rose-900/60 dark:bg-rose-950/30 dark:text-rose-300']
                                ].map(([label, count, className]) => (
                                    <div key={String(label)} className={`rounded-lg border p-3 text-center ${className}`}><p className="text-xs opacity-75">{label}</p><p className="mt-1 text-xl font-bold">{count}</p></div>
                                ))}
                            </div>
                            {detailRows.length === 0 ? (
                                <div className="flex items-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700 dark:border-emerald-900/60 dark:bg-emerald-950/30 dark:text-emerald-300"><CheckCircle2 className="h-5 w-5" />모든 심사자 배정이 등록되었습니다.</div>
                            ) : (
                                <div>
                                    <h4 className="mb-2 text-sm font-bold text-slate-800 dark:text-slate-100">건너뜀 및 실패 내역</h4>
                                    <div className="max-h-64 overflow-auto rounded-lg border border-slate-200 dark:border-slate-800">
                                        <table className="w-full min-w-[720px] text-left text-xs">
                                            <thead className="sticky top-0 bg-slate-100 dark:bg-slate-900"><tr><th className="w-20 px-3 py-2">엑셀 행</th><th className="px-3 py-2">초록번호</th><th className="px-3 py-2">심사자 아이디</th><th className="w-20 px-3 py-2">결과</th><th className="px-3 py-2">처리 내용</th></tr></thead>
                                            <tbody>{detailRows.map((row) => <tr key={`${row.rowNumber}-${row.submissionNo}-${row.reviewerId}`} className="border-t border-slate-200 dark:border-slate-800"><td className="px-3 py-2 font-mono text-slate-500">{row.rowNumber}행</td><td className="px-3 py-2 font-semibold text-slate-700 dark:text-slate-200">{row.submissionNo || '-'}</td><td className="px-3 py-2 text-slate-600 dark:text-slate-300">{row.reviewerId || '-'}</td><td className={`px-3 py-2 font-semibold ${row.status === 'failure' ? 'text-rose-600 dark:text-rose-400' : 'text-amber-600 dark:text-amber-400'}`}>{row.status === 'failure' ? '실패' : '건너뜀'}</td><td className="px-3 py-2 text-slate-600 dark:text-slate-300">{row.message}</td></tr>)}</tbody>
                                        </table>
                                    </div>
                                </div>
                            )}
                        </div>
                    )}
                </div>

                <div className="flex shrink-0 justify-end gap-2 border-t border-slate-200 px-5 py-4 dark:border-slate-800">
                    <button type="button" onClick={close} disabled={isUploading} className="rounded-lg border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-40 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900">닫기</button>
                    <button type="button" onClick={() => void upload()} disabled={!file || isUploading} className="inline-flex items-center gap-2 rounded-lg bg-violet-600 px-4 py-2 text-xs font-semibold text-white hover:bg-violet-700 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-violet-600 dark:text-white dark:hover:bg-violet-700">{isUploading ? <LoaderCircle className="h-4 w-4 animate-spin" /> : <Upload className="h-4 w-4" />}{isUploading ? '배정 중' : '일괄배정'}</button>
                </div>
            </div>
        </div>
    );
};
