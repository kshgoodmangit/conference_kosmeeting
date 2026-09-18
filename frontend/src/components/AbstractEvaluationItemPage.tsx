import { DraggableModal } from './DraggableModal';
import { useEffect, useRef, useState } from 'react';
import {
    ClipboardCheck,
    Pencil,
    Plus,
    Trash2,
    X
} from 'lucide-react';
import type { NotificationType } from './NotificationToast';
import { rowActionButtonClass } from './RowActionMenu';
import { useConfirm } from './confirmDialogContext';

interface EvaluationItem {
    seq: number;
    itemName: string;
    description?: string | null;
    sortOrder: number;
    isUsed: 'Y' | 'N';
    score1Guide?: string | null;
    score2Guide?: string | null;
    score3Guide?: string | null;
    score4Guide?: string | null;
    score5Guide?: string | null;
    score6Guide?: string | null;
    createdAt?: string | null;
    updatedAt?: string | null;
}

interface EvaluationItemListResponse {
    scaleMin: number;
    scaleMax: number;
    scaleLabel: string;
    items: EvaluationItem[];
}

interface AbstractEvaluationItemPageProps {
    onNotify: (type: NotificationType, message: string) => void;
}

const emptyPolicy: Omit<EvaluationItemListResponse, 'items'> = {
    scaleMin: 1,
    scaleMax: 6,
    scaleLabel: '각 항목 1~6점',
};

const scoreScaleTitles: Record<number, string> = {
    1: '매우 미흡',
    2: '미흡',
    3: '다소 미흡',
    4: '양호',
    5: '우수',
    6: '매우 우수',
};

const defaultScoreGuides: Record<number, string> = {
    1: '매우 미흡',
    2: '미흡',
    3: '다소 미흡',
    4: '양호',
    5: '우수',
    6: '매우 우수',
};

export const AbstractEvaluationItemPage = ({ onNotify }: AbstractEvaluationItemPageProps) => {
    const confirm = useConfirm();
    const [items, setItems] = useState<EvaluationItem[]>([]);
    const [policy, setPolicy] = useState(emptyPolicy);
    const [isLoading, setIsLoading] = useState(false);
    const [errorMessage, setErrorMessage] = useState('');
    const [reloadKey, setReloadKey] = useState(0);
    const [selectedItem, setSelectedItem] = useState<EvaluationItem | null>(null);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const onNotifyRef = useRef(onNotify);

    useEffect(() => {
        onNotifyRef.current = onNotify;
    }, [onNotify]);

    useEffect(() => {
        const controller = new AbortController();

        const loadItems = async () => {
            setIsLoading(true);
            setErrorMessage('');
            try {
                const response = await fetch('/api/admin/abstract-evaluation-items', { signal: controller.signal });
                if (!response.ok) {
                    throw new Error(await response.text() || '평가항목 목록을 불러오지 못했습니다.');
                }

                const data = await response.json() as EvaluationItemListResponse;
                setItems(data.items);
                setPolicy({
                    scaleMin: data.scaleMin,
                    scaleMax: data.scaleMax,
                    scaleLabel: data.scaleLabel,
                });
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') {
                    return;
                }
                const message = error instanceof Error ? error.message : '평가항목 목록을 불러오지 못했습니다.';
                setErrorMessage(message);
                onNotifyRef.current('error', message);
            } finally {
                setIsLoading(false);
            }
        };

        void loadItems();
        return () => controller.abort();
    }, [reloadKey]);

    const refresh = () => setReloadKey((value) => value + 1);

    const openCreate = () => {
        setSelectedItem(null);
        setIsModalOpen(true);
    };

    const openEdit = (item: EvaluationItem) => {
        setSelectedItem(item);
        setIsModalOpen(true);
    };

    const handleDelete = async (item: EvaluationItem) => {
        const confirmed = await confirm({
            title: '평가항목 삭제',
            message: `'${item.itemName}' 평가항목을 삭제하시겠습니까?\n삭제된 항목은 심사 항목 목록에서 제외됩니다.`,
            confirmText: '삭제',
            tone: 'danger'
        });
        if (!confirmed) {
            return;
        }

        try {
            const response = await fetch(`/api/admin/abstract-evaluation-items/${item.seq}`, { method: 'DELETE' });
            if (!response.ok) {
                throw new Error(await response.text() || '평가항목 삭제에 실패했습니다.');
            }
            onNotifyRef.current('success', '평가항목을 삭제했습니다.');
            refresh();
        } catch (error) {
            const message = error instanceof Error ? error.message : '평가항목 삭제에 실패했습니다.';
            setErrorMessage(message);
            onNotifyRef.current('error', message);
        }
    };

    const closeModal = () => {
        setIsModalOpen(false);
        setSelectedItem(null);
    };

    return (
        <section className="rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-950">
            <div className="flex flex-col justify-between gap-4 border-b border-slate-200 p-4 dark:border-slate-800 lg:flex-row lg:items-center md:p-5">
                <div>
                    <div className="flex items-center gap-2">
                        <ClipboardCheck className="shrink-0 h-4 w-4 text-slate-400 dark:text-slate-500" />
                        <h3 className="text-sm font-semibold md:text-base">초록 평가항목 관리</h3>
                    </div>
                    <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">초록 심사에 사용할 점수 항목과 1~6점별 평가기준을 관리합니다.</p>
                </div>
                <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
                    <button
                        type="button"
                        onClick={openCreate}
                        className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-3 py-2 text-xs font-semibold text-white hover:bg-blue-700 dark:bg-blue-500 dark:hover:bg-blue-600"
                    >
                        <Plus className="h-4 w-4" />
                        평가항목 등록
                    </button>
                </div>
            </div>

            <div className="grid grid-cols-1 border-b border-slate-200 sm:grid-cols-2 dark:border-slate-800">
                <SummaryCard label="평가 척도" value={policy.scaleLabel} />
                <SummaryCard label="사용 항목" value={`${items.filter((item) => item.isUsed === 'Y').length}개`} />
            </div>

            {errorMessage && (
                <div className="m-4 rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 dark:border-rose-900/60 dark:bg-rose-950/30 dark:text-rose-300 md:m-5">
                    {errorMessage}
                </div>
            )}

            <div className="overflow-x-auto">
                <table className="w-full min-w-[980px] border-collapse text-left text-xs md:text-sm">
                    <thead className="border-b border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900/50">
                        <tr>
                            <th className="p-4">순서</th>
                            <th className="p-4">평가항목</th>
                            <th className="p-4">평가기준 및 점수 안내</th>
                            <th className="p-4 text-center">사용 여부</th>
                            <th className="p-4 text-center">기능</th>
                        </tr>
                    </thead>
                    <tbody>
                        {isLoading && items.length === 0 && (
                            <tr><td colSpan={5} className="p-8 text-center text-slate-400 dark:text-slate-500">평가항목을 불러오는 중입니다.</td></tr>
                        )}
                        {!isLoading && items.length === 0 && (
                            <tr><td colSpan={5} className="p-8 text-center text-slate-400 dark:text-slate-500">등록된 평가항목이 없습니다.</td></tr>
                        )}
                        {items.map((item) => (
                            <tr key={item.seq} className="border-b border-slate-200 hover:bg-slate-50/60 dark:border-slate-800 dark:hover:bg-slate-900/40">
                                <td className="p-4 font-medium text-slate-500 dark:text-slate-400">{item.sortOrder}</td>
                                <td className="p-4">
                                    <p className="font-semibold text-slate-900 dark:text-slate-50">{item.itemName}</p>
                                    <p className="mt-1 text-[11px] text-slate-400 dark:text-slate-500">{policy.scaleMin}~{policy.scaleMax}점 척도</p>
                                </td>
                                <td className="max-w-xl p-4">
                                    <p className="text-slate-600 dark:text-slate-300">{item.description || '-'}</p>
                                    <div className="mt-2 flex flex-wrap gap-1.5">
                                        <GuideBadge score={1} guide={item.score1Guide} />
                                        <GuideBadge score={2} guide={item.score2Guide} />
                                        <GuideBadge score={3} guide={item.score3Guide} />
                                        <GuideBadge score={4} guide={item.score4Guide} />
                                        <GuideBadge score={5} guide={item.score5Guide} />
                                        <GuideBadge score={6} guide={item.score6Guide} />
                                    </div>
                                </td>
                                <td className="p-4 text-center">
                                    <span className={`inline-flex rounded-full border px-2.5 py-0.5 text-xs font-semibold ${
                                        item.isUsed === 'Y'
                                            ? 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-900/60 dark:bg-emerald-950/40 dark:text-emerald-300'
                                            : 'border-slate-200 bg-slate-100 text-slate-500 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300'
                                    }`}>
                                        {item.isUsed === 'Y' ? '사용' : '미사용'}
                                    </span>
                                </td>
                                <td className="p-4">
                                    <div className="flex items-center justify-center gap-1.5">
                                        <button type="button" onClick={() => openEdit(item)} className={rowActionButtonClass} aria-label={`${item.itemName} 수정`}>
                                            <Pencil className="h-4 w-4" />
                                        </button>
                                        <button type="button" onClick={() => void handleDelete(item)} className={`${rowActionButtonClass} !text-rose-600 dark:!text-rose-400`} aria-label={`${item.itemName} 삭제`}>
                                            <Trash2 className="h-4 w-4" />
                                        </button>
                                    </div>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            <div className="border-t border-slate-200 px-4 py-3 text-xs text-slate-500 dark:border-slate-800 dark:text-slate-400 md:px-5">
                주제 적합성, 윤리·표절·상업성은 점수항목이 아닌 별도 사전검수 대상으로 관리합니다.
            </div>

            {isModalOpen && (
                <EvaluationItemModal
                    key={selectedItem?.seq ?? 'new'}
                    item={selectedItem}
                    onClose={closeModal}
                    onSuccess={() => {
                        closeModal();
                        refresh();
                    }}
                    onNotify={onNotify}
                />
            )}
        </section>
    );
};

interface SummaryCardProps {
    label: string;
    value: string;
}

const SummaryCard = ({ label, value }: SummaryCardProps) => (
    <div className="border-b border-slate-200 p-4 last:border-b-0 dark:border-slate-800 sm:border-b-0 sm:border-r sm:last:border-r-0 md:p-5">
        <p className="text-xs font-medium text-slate-500 dark:text-slate-400">{label}</p>
        <p className="mt-1 text-xl font-bold text-slate-900 dark:text-slate-50">{value}</p>
    </div>
);

const GuideBadge = ({ score, guide }: { score: number; guide?: string | null }) => (
    <span
        title={guide || `${score}점 ${scoreScaleTitles[score]} 상세 기준 미등록`}
        className={`rounded-md border px-2 py-1 text-[11px] ${
            guide
                ? 'border-blue-200 bg-blue-50 text-blue-700 dark:border-blue-900/60 dark:bg-blue-950/40 dark:text-blue-300'
                : 'border-slate-200 bg-slate-50 text-slate-400 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-500'
        }`}
    >
        {score}점 · {scoreScaleTitles[score]}
        {!guide && ' (기준 없음)'}
    </span>
);

interface EvaluationItemModalProps {
    item: EvaluationItem | null;
    onClose: () => void;
    onSuccess: () => void;
    onNotify: (type: NotificationType, message: string) => void;
}

const EvaluationItemModal = ({ item, onClose, onSuccess, onNotify }: EvaluationItemModalProps) => {
    const [itemName, setItemName] = useState(item?.itemName ?? '');
    const [description, setDescription] = useState(item?.description ?? '');
    const [sortOrder, setSortOrder] = useState(item ? String(item.sortOrder) : '0');
    const [isUsed, setIsUsed] = useState(item?.isUsed !== 'N');
    const [score1Guide, setScore1Guide] = useState(item?.score1Guide ?? defaultScoreGuides[1]);
    const [score2Guide, setScore2Guide] = useState(item?.score2Guide ?? defaultScoreGuides[2]);
    const [score3Guide, setScore3Guide] = useState(item?.score3Guide ?? defaultScoreGuides[3]);
    const [score4Guide, setScore4Guide] = useState(item?.score4Guide ?? defaultScoreGuides[4]);
    const [score5Guide, setScore5Guide] = useState(item?.score5Guide ?? defaultScoreGuides[5]);
    const [score6Guide, setScore6Guide] = useState(item?.score6Guide ?? defaultScoreGuides[6]);
    const [isSaving, setIsSaving] = useState(false);

    useEffect(() => {
        const handleKeyDown = (event: KeyboardEvent) => {
            if (event.key === 'Escape' && !isSaving) {
                onClose();
            }
        };
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [isSaving, onClose]);

    const handleSubmit = async (event: React.FormEvent) => {
        event.preventDefault();
        setIsSaving(true);

        try {
            const response = await fetch(
                item ? `/api/admin/abstract-evaluation-items/${item.seq}` : '/api/admin/abstract-evaluation-items',
                {
                    method: item ? 'PUT' : 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        itemName: itemName.trim(),
                        description: description.trim() || null,
                        sortOrder: Number(sortOrder),
                        isUsed: isUsed ? 'Y' : 'N',
                        score1Guide: score1Guide.trim() || null,
                        score2Guide: score2Guide.trim() || null,
                        score3Guide: score3Guide.trim() || null,
                        score4Guide: score4Guide.trim() || null,
                        score5Guide: score5Guide.trim() || null,
                        score6Guide: score6Guide.trim() || null
                    })
                }
            );
            if (!response.ok) {
                throw new Error(await response.text() || '평가항목 저장에 실패했습니다.');
            }
            onNotify('success', item ? '평가항목을 수정했습니다.' : '평가항목을 등록했습니다.');
            onSuccess();
        } catch (error) {
            const message = error instanceof Error ? error.message : '평가항목 저장에 실패했습니다.';
            onNotify('error', message);
        } finally {
            setIsSaving(false);
        }
    };

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4 backdrop-blur-sm">
            <button type="button" className="absolute inset-0 cursor-default" onClick={onClose} aria-label="평가항목 편집 닫기" />
            <DraggableModal className="relative max-h-[90vh] w-full max-w-3xl overflow-y-auto rounded-xl border border-slate-200 bg-white shadow-2xl dark:border-slate-800 dark:bg-slate-950">
                <div data-modal-drag-handle className="cursor-move select-none touch-none sticky top-0 z-10 flex items-center justify-between border-b border-slate-200 bg-white px-5 py-4 dark:border-slate-800 dark:bg-slate-950">
                    <div>
                        <h3 className="font-semibold text-slate-900 dark:text-slate-50">{item ? '평가항목 수정' : '평가항목 등록'}</h3>
                        <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">모든 항목에는 시스템 공통 1~6점 척도가 적용됩니다.</p>
                    </div>
                    <button type="button" onClick={onClose} disabled={isSaving} className="rounded-lg p-2 text-slate-400 hover:bg-slate-100 disabled:opacity-50 dark:text-slate-500 dark:hover:bg-slate-900">
                        <X className="h-5 w-5" />
                    </button>
                </div>

                <form onSubmit={(event) => void handleSubmit(event)} className="space-y-5 p-5">

                    <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
                        <label className="sm:col-span-2">
                            <span className="mb-1.5 block text-xs font-semibold text-slate-700 dark:text-slate-300">평가항목 이름 <span className="text-rose-500 dark:text-rose-400">*</span></span>
                            <input required maxLength={100} value={itemName} onChange={(event) => setItemName(event.target.value)} className={inputClassName} placeholder="예: 학술적 가치·독창성" />
                        </label>
                        <label>
                            <span className="mb-1.5 block text-xs font-semibold text-slate-700 dark:text-slate-300">정렬순서 <span className="text-rose-500 dark:text-rose-400">*</span></span>
                            <input required min="0" type="number" value={sortOrder} onChange={(event) => setSortOrder(event.target.value)} className={inputClassName} />
                        </label>
                    </div>

                    <label>
                        <span className="mb-1.5 block text-xs font-semibold text-slate-700 dark:text-slate-300">설명(평가기준)</span>
                        <textarea maxLength={1000} rows={3} value={description} onChange={(event) => setDescription(event.target.value)} className={inputClassName} placeholder="심사위원이 이 항목에서 확인할 기준을 입력하세요." />
                    </label>

                    <label className="flex items-center gap-3 rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 dark:border-slate-800 dark:bg-slate-900/50">
                        <input type="checkbox" checked={isUsed} onChange={(event) => setIsUsed(event.target.checked)} className="h-4 w-4 rounded border-slate-300 text-blue-600 dark:border-slate-700 dark:bg-slate-900 dark:text-blue-400" />
                        <span>
                            <span className="block text-sm font-semibold text-slate-700 dark:text-slate-200">사용</span>
                            <span className="text-xs text-slate-500 dark:text-slate-400">실제 심사 항목 목록에 포함</span>
                        </span>
                    </label>

                    <fieldset className="rounded-lg border border-slate-200 p-4 dark:border-slate-800">
                        <legend className="px-2 text-xs font-semibold text-slate-700 dark:text-slate-300">점수별 평가기준 (1~6점)</legend>
                        <div className="mt-2 grid grid-cols-1 gap-4">
                            <GuideInput score={1} value={score1Guide} onChange={setScore1Guide} />
                            <GuideInput score={2} value={score2Guide} onChange={setScore2Guide} />
                            <GuideInput score={3} value={score3Guide} onChange={setScore3Guide} />
                            <GuideInput score={4} value={score4Guide} onChange={setScore4Guide} />
                            <GuideInput score={5} value={score5Guide} onChange={setScore5Guide} />
                            <GuideInput score={6} value={score6Guide} onChange={setScore6Guide} />
                        </div>
                    </fieldset>

                    <div className="flex justify-end gap-2 border-t border-slate-200 pt-4 dark:border-slate-800">
                        <button type="button" onClick={onClose} disabled={isSaving} className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900">취소</button>
                        <button type="submit" disabled={isSaving} className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-50 dark:bg-blue-500 dark:hover:bg-blue-600">{isSaving ? '저장 중...' : '저장'}</button>
                    </div>
                </form>
            </DraggableModal>
        </div>
    );
};

const GuideInput = ({ score, value, onChange }: { score: number; value: string; onChange: (value: string) => void }) => (
    <label className="grid grid-cols-[112px_1fr] items-center gap-3">
        <span className="rounded-md bg-blue-50 px-2 py-2 text-center text-xs font-bold text-blue-700 dark:bg-blue-950/40 dark:text-blue-300">
            {score}점
        </span>
        <input maxLength={500} value={value} onChange={(event) => onChange(event.target.value)} className={inputClassName} placeholder="항목별 상세 평가기준을 입력하세요." />
    </label>
);

const inputClassName = 'w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 placeholder:text-slate-400 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50 dark:placeholder:text-slate-500 dark:focus:border-blue-400';
