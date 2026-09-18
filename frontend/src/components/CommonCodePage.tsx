/* eslint-disable react-hooks/set-state-in-effect */
import { useEffect, useMemo, useRef, useState, type DragEvent as ReactDragEvent } from 'react';
import {
    Braces,
    CheckCircle2,
    ChevronDown,
    ChevronRight,
    CircleOff,
    GripVertical,
    LockKeyhole,
    Plus,
    RefreshCw,
    Save,
    Settings2,
    Trash2,
    X
} from 'lucide-react';
import type { NotificationType } from './NotificationToast';
import { useConfirm } from './confirmDialogContext';

interface CommonCodeNode {
    seq: number;
    groupCode: string;
    parentSeq: number;
    codeName: string;
    sortOrder: number;
    isUsed: 'Y' | 'N';
    codeEtc1?: string | null;
    codeEtc2?: string | null;
    codeEtc3?: string | null;
    isEditable: 'Y' | 'N';
    isEtc: 'Y' | 'N';
    createdAt?: string | null;
    updatedAt?: string | null;
    children?: CommonCodeNode[];
}

interface CommonCodePageProps {
    onNotify: (type: NotificationType, message: string) => void;
}

type DropPosition = 'before' | 'inside' | 'after';

interface DragPreview {
    draggedSeq: number;
    targetSeq: number;
    position: DropPosition;
}

const inputClassName = 'w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-500 dark:border-slate-800 dark:bg-slate-950 dark:text-slate-50 dark:disabled:bg-slate-900 dark:disabled:text-slate-500';

export const CommonCodePage = ({ onNotify }: CommonCodePageProps) => {
    const confirm = useConfirm();
    const [codes, setCodes] = useState<CommonCodeNode[]>([]);
    const [selectedSeq, setSelectedSeq] = useState<number | null>(null);
    const [expandedSeqs, setExpandedSeqs] = useState<Record<number, boolean>>({});
    const [isCreateMode, setIsCreateMode] = useState(false);
    const [parentSeq, setParentSeq] = useState('0');
    const [groupCode, setGroupCode] = useState('');
    const [codeName, setCodeName] = useState('');
    const [sortOrder, setSortOrder] = useState('0');
    const [isUsed, setIsUsed] = useState(true);
    const [isEditable, setIsEditable] = useState(true);
    const [isEtc, setIsEtc] = useState(false);
    const [codeEtc1, setCodeEtc1] = useState('');
    const [codeEtc2, setCodeEtc2] = useState('');
    const [codeEtc3, setCodeEtc3] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [isSaving, setIsSaving] = useState(false);
    const [isDeleting, setIsDeleting] = useState(false);
    const [isOrderSaving, setIsOrderSaving] = useState(false);
    const [hasOrderChanges, setHasOrderChanges] = useState(false);
    const [draggedSeq, setDraggedSeq] = useState<number | null>(null);
    const [dragPreview, setDragPreview] = useState<DragPreview | null>(null);
    const [errorMessage, setErrorMessage] = useState('');
    const [reloadKey, setReloadKey] = useState(0);
    const onNotifyRef = useRef(onNotify);

    useEffect(() => {
        onNotifyRef.current = onNotify;
    }, [onNotify]);

    useEffect(() => {
        const abortController = new AbortController();

        const fetchCodes = async () => {
            setIsLoading(true);
            setErrorMessage('');

            try {
                const response = await fetch('/api/admin/common-codes/tree', {
                    signal: abortController.signal
                });
                if (!response.ok) {
                    throw new Error(await response.text() || '공통코드 목록을 불러오지 못했습니다.');
                }

                const data = normalizeTree(await response.json() as CommonCodeNode[]);
                setCodes(data);
                setHasOrderChanges(false);
                setDraggedSeq(null);
                setDragPreview(null);
                setExpandedSeqs((previous) => Object.keys(previous).length > 0 ? previous : collectExpandedSeqs(data));
                setSelectedSeq((current) => {
                    if (current == null) {
                        return data[0]?.seq ?? null;
                    }
                    return findBySeq(data, current)?.seq ?? data[0]?.seq ?? null;
                });
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') {
                    return;
                }
                const message = error instanceof Error ? error.message : '공통코드 목록을 불러오지 못했습니다.';
                setErrorMessage(message);
                onNotifyRef.current('error', message);
            } finally {
                setIsLoading(false);
            }
        };

        void fetchCodes();
        return () => abortController.abort();
    }, [reloadKey]);

    const selectedCode = useMemo(
        () => selectedSeq != null ? findBySeq(codes, selectedSeq) : null,
        [codes, selectedSeq]
    );
    const flatCodes = useMemo(() => flattenCodes(codes), [codes]);
    const parentOptions = useMemo(
        () => flatCodes.filter((item) => item.parentSeq === 0),
        [flatCodes]
    );
    const selectedPath = useMemo(
        () => selectedCode ? findCodePath(codes, selectedCode.seq)?.map((item) => item.codeName).join(' / ') ?? selectedCode.codeName : '',
        [codes, selectedCode]
    );
    const isLocked = !isCreateMode && selectedCode?.isEditable === 'N';
    const canChangeParent = !isCreateMode && Boolean(selectedCode && selectedCode.parentSeq !== 0);
    const selectedParentGroupCode = parentSeq !== '0'
        ? findBySeq(codes, Number(parentSeq))?.groupCode ?? ''
        : '';
    const displayGroupCode = parentSeq === '0'
        ? (isCreateMode ? groupCode : selectedCode?.groupCode ?? '')
        : selectedParentGroupCode;

    useEffect(() => {
        if (isCreateMode) {
            return;
        }

        if (!selectedCode) {
            setParentSeq('0');
            setGroupCode('');
            setCodeName('');
            setSortOrder('0');
            setIsUsed(true);
            setIsEditable(true);
            setIsEtc(false);
            setCodeEtc1('');
            setCodeEtc2('');
            setCodeEtc3('');
            return;
        }

        setParentSeq(String(selectedCode.parentSeq ?? 0));
        setGroupCode(selectedCode.groupCode);
        setCodeName(selectedCode.codeName);
        setSortOrder(String(selectedCode.sortOrder ?? 0));
        setIsUsed(selectedCode.isUsed === 'Y');
        setIsEditable(selectedCode.isEditable === 'Y');
        setIsEtc(selectedCode.isEtc === 'Y');
        setCodeEtc1(selectedCode.codeEtc1 ?? '');
        setCodeEtc2(selectedCode.codeEtc2 ?? '');
        setCodeEtc3(selectedCode.codeEtc3 ?? '');
    }, [isCreateMode, selectedCode]);

    const handleStartCreate = () => {
        const defaultParent = selectedCode
            ? (selectedCode.parentSeq === 0 ? selectedCode : findBySeq(codes, selectedCode.parentSeq))
            : null;
        setIsCreateMode(true);
        setParentSeq(defaultParent ? String(defaultParent.seq) : '0');
        setGroupCode(defaultParent?.groupCode ?? '');
        setCodeName('');
        setSortOrder('0');
        setIsUsed(true);
        setIsEditable(true);
        setIsEtc(false);
        setCodeEtc1('');
        setCodeEtc2('');
        setCodeEtc3('');
        setErrorMessage('');
        if (defaultParent) {
            setExpandedSeqs((previous) => ({ ...previous, [defaultParent.seq]: true }));
        }
    };

    const handleSelect = (node: CommonCodeNode) => {
        setIsCreateMode(false);
        setSelectedSeq(node.seq);
        setErrorMessage('');
    };

    const handleSave = async (event: React.FormEvent) => {
        event.preventDefault();
        if ((!isCreateMode && !selectedCode) || isLocked) {
            return;
        }

        setIsSaving(true);
        setErrorMessage('');

        try {
            const body = new URLSearchParams();
            body.set('codeName', codeName.trim());
            body.set('sortOrder', sortOrder.trim() || '0');
            body.set('isUsed', isUsed ? 'Y' : 'N');
            body.set('isEditable', isEditable ? 'Y' : 'N');
            body.set('isEtc', isEtc ? 'Y' : 'N');
            if (codeEtc1.trim()) body.set('codeEtc1', codeEtc1.trim());
            if (codeEtc2.trim()) body.set('codeEtc2', codeEtc2.trim());
            if (codeEtc3.trim()) body.set('codeEtc3', codeEtc3.trim());
            if (isCreateMode) {
                body.set('parentSeq', parentSeq || '0');
                if (displayGroupCode.trim()) body.set('groupCode', displayGroupCode.trim());
            } else if (selectedCode && selectedCode.parentSeq !== 0) {
                body.set('parentSeq', parentSeq);
            }

            const response = await fetch(
                isCreateMode ? '/api/admin/common-codes' : `/api/admin/common-codes/${selectedCode?.seq}`,
                {
                    method: isCreateMode ? 'POST' : 'PUT',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    body
                }
            );
            if (!response.ok) {
                throw new Error(await response.text() || '공통코드 저장에 실패했습니다.');
            }

            const saved = await response.json() as CommonCodeNode;
            const wasCreateMode = isCreateMode;
            setSelectedSeq(saved.seq);
            setIsCreateMode(false);
            setReloadKey((value) => value + 1);
            onNotify('success', wasCreateMode ? '공통코드가 등록되었습니다.' : '공통코드가 저장되었습니다.');
        } catch (error) {
            const message = error instanceof Error ? error.message : '공통코드 저장에 실패했습니다.';
            setErrorMessage(message);
            onNotify('error', message);
        } finally {
            setIsSaving(false);
        }
    };

    const handleDelete = async () => {
        if (!selectedCode || isCreateMode || isLocked) {
            return;
        }
        if (!await confirm({
            title: '공통코드 삭제',
            message: `'${selectedCode.codeName}' 공통코드를 삭제하시겠습니까?`,
            confirmText: '삭제',
            tone: 'danger'
        })) {
            return;
        }

        setIsDeleting(true);
        setErrorMessage('');
        try {
            const response = await fetch(`/api/admin/common-codes/${selectedCode.seq}`, { method: 'DELETE' });
            if (!response.ok) {
                throw new Error(await response.text() || '공통코드 삭제에 실패했습니다.');
            }
            setSelectedSeq(selectedCode.parentSeq > 0 ? selectedCode.parentSeq : null);
            setReloadKey((value) => value + 1);
            onNotify('success', '공통코드가 삭제되었습니다.');
        } catch (error) {
            const message = error instanceof Error ? error.message : '공통코드 삭제에 실패했습니다.';
            setErrorMessage(message);
            onNotify('error', message);
        } finally {
            setIsDeleting(false);
        }
    };

    const handleOrderSave = async () => {
        setIsOrderSaving(true);
        setErrorMessage('');
        try {
            const response = await fetch('/api/admin/common-codes/reorder', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ items: buildReorderPayload(codes) })
            });
            if (!response.ok) {
                throw new Error(await response.text() || '공통코드 정렬 저장에 실패했습니다.');
            }

            const data = normalizeTree(await response.json() as CommonCodeNode[]);
            setCodes(data);
            setHasOrderChanges(false);
            setDraggedSeq(null);
            setDragPreview(null);
            onNotify('success', '공통코드 정렬이 저장되었습니다.');
        } catch (error) {
            const message = error instanceof Error ? error.message : '공통코드 정렬 저장에 실패했습니다.';
            setErrorMessage(message);
            onNotify('error', message);
        } finally {
            setIsOrderSaving(false);
        }
    };

    const handleDragStart = (node: CommonCodeNode, event: ReactDragEvent<HTMLDivElement>) => {
        if (node.isEditable !== 'Y') {
            return;
        }
        event.dataTransfer.effectAllowed = 'move';
        event.dataTransfer.setData('text/plain', String(node.seq));
        setDraggedSeq(node.seq);
        setDragPreview(null);
    };

    const handleDragOver = (node: CommonCodeNode, event: ReactDragEvent<HTMLDivElement>) => {
        if (draggedSeq == null) {
            return;
        }
        event.stopPropagation();
        const position = resolveDropPosition(event);
        if (!isValidDrop(codes, draggedSeq, node.seq, position)) {
            setDragPreview(null);
            return;
        }

        event.preventDefault();
        setDragPreview((previous) => (
            previous?.draggedSeq === draggedSeq
                && previous.targetSeq === node.seq
                && previous.position === position
                ? previous
                : { draggedSeq, targetSeq: node.seq, position }
        ));
    };

    const handleDrop = (node: CommonCodeNode, event: ReactDragEvent<HTMLDivElement>) => {
        event.preventDefault();
        event.stopPropagation();
        if (draggedSeq == null) {
            return;
        }

        const position = dragPreview?.targetSeq === node.seq
            ? dragPreview.position
            : resolveDropPosition(event);
        const nextCodes = moveCommonCodeNode(codes, draggedSeq, node.seq, position);
        setDraggedSeq(null);
        setDragPreview(null);

        if (!nextCodes) {
            onNotify('error', '이 위치로는 공통코드를 이동할 수 없습니다.');
            return;
        }

        setCodes(nextCodes);
        setHasOrderChanges(true);
        setExpandedSeqs((previous) => ({ ...previous, [node.seq]: true }));
    };

    const handleDragEnd = () => {
        setDraggedSeq(null);
        setDragPreview(null);
    };

    return (
        <section className="rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-950">
            <div className="flex flex-col justify-between gap-3 border-b border-slate-200 p-4 dark:border-slate-800 sm:flex-row sm:items-start md:p-5">
                <div>
                    <div className="flex items-center gap-2">
                        <Braces className="shrink-0 h-4 w-4 text-slate-400" />
                        <h3 className="text-sm font-semibold md:text-base">공통코드 관리</h3>
                    </div>
                    <p className="mt-1 text-xs text-slate-400">시스템에서 사용하는 공통코드를 최대 2뎁스 트리 구조로 관리합니다.</p>
                    <p className="mt-1 text-xs text-amber-600 dark:text-amber-300">드래그로 순서와 상위 코드를 변경한 뒤 정렬 저장을 눌러 반영하세요.</p>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                    {hasOrderChanges && (
                        <button
                            type="button"
                            onClick={() => setReloadKey((value) => value + 1)}
                            disabled={isOrderSaving}
                            className="inline-flex items-center justify-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900"
                        >
                            <RefreshCw className="h-4 w-4" />
                            정렬 되돌리기
                        </button>
                    )}
                    <button
                        type="button"
                        onClick={handleOrderSave}
                        disabled={!hasOrderChanges || isOrderSaving}
                        className="inline-flex items-center justify-center gap-2 rounded-lg bg-emerald-600 px-3 py-2 text-xs font-semibold text-white hover:bg-emerald-700 disabled:opacity-50"
                    >
                        <Save className="h-4 w-4" />
                        {isOrderSaving ? '정렬 저장 중' : '정렬 저장'}
                    </button>
                    <button
                        type="button"
                        onClick={handleStartCreate}
                        className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-3 py-2 text-xs font-semibold text-white hover:bg-blue-700"
                    >
                        <Plus className="h-4 w-4" />
                        코드 추가
                    </button>
                </div>
            </div>

            {errorMessage && (
                <div className="m-4 rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 dark:border-rose-900/60 dark:bg-rose-950/30 dark:text-rose-300 md:m-5">
                    {errorMessage}
                </div>
            )}

            <div className="grid grid-cols-1 lg:grid-cols-[360px_1fr]">
                <div className="border-b border-slate-200 p-4 dark:border-slate-800 lg:border-b-0 lg:border-r md:p-5">
                    <div className="overflow-hidden rounded-lg border border-slate-200 dark:border-slate-800">
                        <div className="flex items-center gap-2 border-b border-slate-200 bg-slate-50 px-4 py-3 dark:border-slate-800 dark:bg-slate-900/50">
                            <Settings2 className="h-4 w-4 text-slate-400" />
                            <span className="text-sm font-semibold">공통코드 트리</span>
                            {hasOrderChanges && <span className="ml-auto text-[11px] font-semibold text-amber-600 dark:text-amber-300">정렬 변경 있음</span>}
                            <span className={`${hasOrderChanges ? '' : 'ml-auto'} text-[11px] text-slate-400`}>{flatCodes.length}개</span>
                        </div>
                        <div className="min-h-[420px] p-2">
                            {isLoading && codes.length === 0 && <p className="p-4 text-sm text-slate-400">공통코드를 불러오는 중입니다.</p>}
                            {!isLoading && codes.length === 0 && <p className="p-4 text-sm text-slate-400">등록된 공통코드가 없습니다.</p>}
                            {codes.map((node) => (
                                <CodeTreeNode
                                    key={node.seq}
                                    node={node}
                                    depth={0}
                                    selectedSeq={isCreateMode ? null : selectedSeq}
                                    expandedSeqs={expandedSeqs}
                                    onToggle={(target) => setExpandedSeqs((previous) => ({
                                        ...previous,
                                        [target.seq]: !(previous[target.seq] ?? false)
                                    }))}
                                    onSelect={handleSelect}
                                    draggedSeq={draggedSeq}
                                    dragPreview={dragPreview}
                                    onDragStart={handleDragStart}
                                    onDragOver={handleDragOver}
                                    onDrop={handleDrop}
                                    onDragEnd={handleDragEnd}
                                />
                            ))}
                        </div>
                    </div>
                </div>

                <form onSubmit={handleSave} className="space-y-5 p-4 md:p-5">
                    {!isCreateMode && !selectedCode ? (
                        <div className="flex min-h-[420px] items-center justify-center text-sm text-slate-400">
                            관리할 공통코드를 선택하거나 새 코드를 추가하세요.
                        </div>
                    ) : (
                        <>
                            <div className="flex flex-col justify-between gap-3 border-b border-slate-200 pb-4 dark:border-slate-800 sm:flex-row sm:items-center">
                                <div>
                                    <p className="text-xs font-medium text-slate-400">{isCreateMode ? '신규 공통코드' : '선택 공통코드'}</p>
                                    <h4 className="mt-1 text-base font-bold">{isCreateMode ? '공통코드 추가' : selectedPath}</h4>
                                </div>
                                <div className="flex flex-wrap gap-2">
                                    <StatusBadge active={isUsed} />
                                    {isLocked && (
                                        <span className="inline-flex items-center gap-1.5 rounded-full border border-amber-200 bg-amber-50 px-2.5 py-1 text-xs font-semibold text-amber-700 dark:border-amber-900/60 dark:bg-amber-950/40 dark:text-amber-300">
                                            <LockKeyhole className="h-3.5 w-3.5" />
                                            수정 제한
                                        </span>
                                    )}
                                    {isCreateMode && (
                                        <button
                                            type="button"
                                            onClick={() => setIsCreateMode(false)}
                                            className="inline-flex items-center gap-1.5 rounded-full border border-slate-200 px-2.5 py-1 text-xs font-semibold text-slate-500 hover:bg-slate-50 dark:border-slate-800 dark:hover:bg-slate-900"
                                        >
                                            <X className="h-3.5 w-3.5" />
                                            취소
                                        </button>
                                    )}
                                </div>
                            </div>

                            {isLocked && (
                                <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-700 dark:border-amber-900/60 dark:bg-amber-950/30 dark:text-amber-300">
                                    이 코드는 시스템 보호 대상으로 설정되어 수정하거나 삭제할 수 없습니다.
                                </div>
                            )}

                            <fieldset disabled={isLocked || isSaving || isDeleting} className="space-y-5 disabled:opacity-75">
                                <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
                                    <div>
                                        <label className="mb-1.5 block text-xs font-semibold uppercase text-slate-500">상위 코드</label>
                                        {isCreateMode || canChangeParent ? (
                                            <div className="relative">
                                                <select
                                                    value={parentSeq}
                                                    onChange={(event) => setParentSeq(event.target.value)}
                                                    className={`${inputClassName} appearance-none pr-10`}
                                                >
                                                    {isCreateMode && <option value="0">최상위 코드</option>}
                                                    {parentOptions.map((item) => (
                                                        <option key={item.seq} value={item.seq}>{getIndentedLabel(codes, item)}</option>
                                                    ))}
                                                </select>
                                                <ChevronDown className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                                            </div>
                                        ) : (
                                            <input value={getParentLabel(codes, selectedCode?.parentSeq ?? 0)} disabled className={inputClassName} />
                                        )}
                                    </div>
                                    <div>
                                        <label className="mb-1.5 block text-xs font-semibold uppercase text-slate-500">그룹 코드</label>
                                        <input
                                            value={displayGroupCode}
                                            onChange={(event) => setGroupCode(event.target.value)}
                                            disabled={!isCreateMode || parentSeq !== '0'}
                                            required={isCreateMode && parentSeq === '0'}
                                            maxLength={100}
                                            placeholder="예: abstract_category"
                                            className={inputClassName}
                                        />
                                        <p className="mt-1 text-[11px] text-slate-400">최상위 등록 시 입력하며 하위 코드는 상위 그룹을 자동 사용합니다.</p>
                                    </div>
                                    <div>
                                        <label className="mb-1.5 block text-xs font-semibold uppercase text-slate-500">정렬 순서</label>
                                        <input type="number" value={sortOrder} onChange={(event) => setSortOrder(event.target.value)} className={inputClassName} />
                                    </div>
                                </div>

                                <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                                    <div>
                                        <label className="mb-1.5 block text-xs font-semibold uppercase text-slate-500">코드명</label>
                                        <input value={codeName} onChange={(event) => setCodeName(event.target.value)} maxLength={255} placeholder="예: 결제 상태" required className={inputClassName} />
                                    </div>
                                    <div>
                                        <label className="mb-1.5 block text-xs font-semibold uppercase text-slate-500">SEQ</label>
                                        <input value={isCreateMode ? '등록 시 자동 발급' : String(selectedCode?.seq ?? '')} disabled className={inputClassName} />
                                    </div>
                                </div>

                                <div className="space-y-4 rounded-lg border border-slate-200 bg-slate-50/70 p-4 dark:border-slate-800 dark:bg-slate-900/40">
                                    <div>
                                        <h5 className="text-sm font-semibold">추가 값</h5>
                                        <p className="mt-1 text-xs text-slate-400">업무에서 필요한 보조 값을 최대 3개까지 저장할 수 있습니다.</p>
                                    </div>
                                    <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
                                        <div>
                                            <label className="mb-1.5 block text-xs font-semibold uppercase text-slate-500">기타 값 1</label>
                                            <input value={codeEtc1} onChange={(event) => setCodeEtc1(event.target.value)} maxLength={100} className={inputClassName} />
                                        </div>
                                        <div>
                                            <label className="mb-1.5 block text-xs font-semibold uppercase text-slate-500">기타 값 2</label>
                                            <input value={codeEtc2} onChange={(event) => setCodeEtc2(event.target.value)} maxLength={100} className={inputClassName} />
                                        </div>
                                        <div>
                                            <label className="mb-1.5 block text-xs font-semibold uppercase text-slate-500">기타 값 3</label>
                                            <input value={codeEtc3} onChange={(event) => setCodeEtc3(event.target.value)} maxLength={100} className={inputClassName} />
                                        </div>
                                    </div>
                                </div>

                                <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
                                    <ToggleField checked={isUsed} onChange={setIsUsed} label="사용 여부" description="서비스에서 선택 가능한 코드" />
                                    <ToggleField checked={isEtc} onChange={setIsEtc} label="기타 코드 여부" description="직접 입력용 기타 항목" />
                                    <ToggleField checked={isEditable} onChange={setIsEditable} label="수정 가능 여부" description="해제 후 저장하면 코드 보호" tone="amber" />
                                </div>

                                <div className="flex flex-col justify-between gap-3 border-t border-slate-200 pt-4 dark:border-slate-800 sm:flex-row sm:items-center">
                                    {!isCreateMode && selectedCode && (
                                        <button
                                            type="button"
                                            onClick={handleDelete}
                                            className="inline-flex items-center justify-center gap-2 rounded-lg border border-rose-200 px-4 py-2 text-xs font-semibold text-rose-600 hover:bg-rose-50 disabled:opacity-50 dark:border-rose-900/70 dark:text-rose-300 dark:hover:bg-rose-950/30"
                                        >
                                            <Trash2 className="h-4 w-4" />
                                            삭제
                                        </button>
                                    )}
                                    <button
                                        type="submit"
                                        className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-xs font-semibold text-white hover:bg-blue-700 disabled:opacity-50 sm:ml-auto"
                                    >
                                        <Save className="h-4 w-4" />
                                        {isSaving ? '저장 중' : isCreateMode ? '등록' : '저장'}
                                    </button>
                                </div>
                            </fieldset>
                        </>
                    )}
                </form>
            </div>
        </section>
    );
};

interface CodeTreeNodeProps {
    node: CommonCodeNode;
    depth: number;
    selectedSeq: number | null;
    expandedSeqs: Record<number, boolean>;
    draggedSeq: number | null;
    dragPreview: DragPreview | null;
    onToggle: (node: CommonCodeNode) => void;
    onSelect: (node: CommonCodeNode) => void;
    onDragStart: (node: CommonCodeNode, event: ReactDragEvent<HTMLDivElement>) => void;
    onDragOver: (node: CommonCodeNode, event: ReactDragEvent<HTMLDivElement>) => void;
    onDrop: (node: CommonCodeNode, event: ReactDragEvent<HTMLDivElement>) => void;
    onDragEnd: () => void;
}

const CodeTreeNode = ({
    node,
    depth,
    selectedSeq,
    expandedSeqs,
    draggedSeq,
    dragPreview,
    onToggle,
    onSelect,
    onDragStart,
    onDragOver,
    onDrop,
    onDragEnd
}: CodeTreeNodeProps) => {
    const hasChildren = Boolean(node.children?.length);
    const expanded = expandedSeqs[node.seq] ?? false;
    const selected = node.seq === selectedSeq;
    const isDragging = draggedSeq === node.seq;
    const isDropTarget = dragPreview?.targetSeq === node.seq;

    return (
        <div>
            <div
                draggable={node.isEditable === 'Y'}
                onDragStart={(event) => onDragStart(node, event)}
                onDragOver={(event) => onDragOver(node, event)}
                onDrop={(event) => onDrop(node, event)}
                onDragEnd={onDragEnd}
                className={`relative rounded-md ${
                    isDropTarget && dragPreview?.position === 'before'
                        ? 'before:absolute before:left-2 before:right-2 before:top-0 before:border-t-2 before:border-blue-500 before:content-[\'\']'
                        : ''
                } ${
                    isDropTarget && dragPreview?.position === 'after'
                        ? 'after:absolute after:bottom-0 after:left-2 after:right-2 after:border-b-2 after:border-blue-500 after:content-[\'\']'
                        : ''
                }`}
            >
                <div
                    className={`flex items-center gap-1 rounded-md py-1.5 pr-2 text-sm transition-colors ${
                        isDropTarget && dragPreview?.position === 'inside'
                            ? 'bg-blue-50/70 ring-1 ring-blue-500 dark:bg-blue-950/30'
                            : ''
                    } ${
                        selected
                            ? 'bg-blue-50 text-blue-700 dark:bg-blue-950/50 dark:text-blue-300'
                            : 'text-slate-700 hover:bg-slate-50 dark:text-slate-300 dark:hover:bg-slate-900'
                    } ${isDragging ? 'opacity-50' : ''}`}
                    style={{ paddingLeft: `${depth * 18 + 6}px` }}
                >
                    <GripVertical className={`h-4 w-4 shrink-0 ${node.isEditable === 'Y' ? 'cursor-grab text-slate-300 dark:text-slate-600' : 'text-slate-200 dark:text-slate-800'}`} />
                    <button
                        type="button"
                        onClick={() => onToggle(node)}
                        disabled={!hasChildren}
                        aria-label={expanded ? '하위 코드 접기' : '하위 코드 펼치기'}
                        className="flex h-6 w-6 shrink-0 items-center justify-center rounded text-slate-400 hover:bg-white disabled:opacity-30 dark:hover:bg-slate-800"
                    >
                        {expanded ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
                    </button>
                    <button type="button" onClick={() => onSelect(node)} className="flex min-w-0 flex-1 items-center gap-2 text-left">
                        <Braces className={`h-4 w-4 shrink-0 ${node.isUsed === 'Y' ? 'text-blue-500' : 'text-slate-400'}`} />
                        <span className="min-w-0 flex-1 truncate font-medium">{node.codeName}</span>
                        <span className="max-w-24 truncate text-[10px] text-slate-400">#{node.seq}</span>
                        {node.isEditable === 'N' && <LockKeyhole className="h-3.5 w-3.5 shrink-0 text-amber-500" />}
                        {node.isUsed === 'N' && <CircleOff className="h-3.5 w-3.5 shrink-0 text-slate-400" />}
                    </button>
                </div>
            </div>
            {hasChildren && expanded && node.children?.map((child) => (
                <CodeTreeNode
                    key={child.seq}
                    node={child}
                    depth={depth + 1}
                    selectedSeq={selectedSeq}
                    expandedSeqs={expandedSeqs}
                    draggedSeq={draggedSeq}
                    dragPreview={dragPreview}
                    onToggle={onToggle}
                    onSelect={onSelect}
                    onDragStart={onDragStart}
                    onDragOver={onDragOver}
                    onDrop={onDrop}
                    onDragEnd={onDragEnd}
                />
            ))}
        </div>
    );
};

const StatusBadge = ({ active }: { active: boolean }) => (
    <span className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-semibold ${
        active
            ? 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-900/60 dark:bg-emerald-950/40 dark:text-emerald-300'
            : 'border-slate-200 bg-slate-100 text-slate-500 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-400'
    }`}>
        <CheckCircle2 className="h-3.5 w-3.5" />
        {active ? '사용' : '미사용'}
    </span>
);

interface ToggleFieldProps {
    checked: boolean;
    onChange: (checked: boolean) => void;
    label: string;
    description: string;
    tone?: 'default' | 'amber';
}

const ToggleField = ({ checked, onChange, label, description, tone = 'default' }: ToggleFieldProps) => (
    <label className={`flex cursor-pointer items-start gap-3 rounded-lg border px-4 py-3 ${
        tone === 'amber'
            ? 'border-amber-200 bg-amber-50/50 dark:border-amber-900/60 dark:bg-amber-950/20'
            : 'border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-950'
    }`}>
        <input type="checkbox" checked={checked} onChange={(event) => onChange(event.target.checked)} className="mt-0.5 h-4 w-4 rounded" />
        <span>
            <span className="block text-sm font-medium text-slate-700 dark:text-slate-300">{label}</span>
            <span className="mt-0.5 block text-xs text-slate-400">{description}</span>
        </span>
    </label>
);

const resolveDropPosition = (event: ReactDragEvent<HTMLDivElement>): DropPosition => {
    const rect = event.currentTarget.getBoundingClientRect();
    const pointerY = event.clientY - rect.top;
    if (pointerY < rect.height / 3) return 'before';
    if (pointerY > rect.height * 2 / 3) return 'after';
    return 'inside';
};

const isValidDrop = (
    nodes: CommonCodeNode[],
    draggedSeq: number,
    targetSeq: number,
    position: DropPosition
): boolean => {
    const dragged = findBySeq(nodes, draggedSeq);
    const target = findBySeq(nodes, targetSeq);
    if (!dragged || !target || dragged.isEditable !== 'Y' || containsSeq(dragged, targetSeq)) {
        return false;
    }

    const draggedIsRoot = dragged.parentSeq === 0;
    const targetIsRoot = target.parentSeq === 0;
    if (draggedIsRoot) {
        return targetIsRoot && position !== 'inside';
    }
    return position === 'inside' ? targetIsRoot : !targetIsRoot;
};

const cloneCodes = (nodes: CommonCodeNode[]): CommonCodeNode[] => (
    nodes.map((node) => ({ ...node, children: cloneCodes(node.children ?? []) }))
);

const removeCodeNode = (nodes: CommonCodeNode[], seq: number): CommonCodeNode | null => {
    for (let index = 0; index < nodes.length; index += 1) {
        if (nodes[index].seq === seq) {
            return nodes.splice(index, 1)[0];
        }
        const removed = removeCodeNode(nodes[index].children ?? [], seq);
        if (removed) return removed;
    }
    return null;
};

const insertCodeNode = (
    nodes: CommonCodeNode[],
    movingNode: CommonCodeNode,
    targetSeq: number,
    position: DropPosition
): boolean => {
    for (let index = 0; index < nodes.length; index += 1) {
        const target = nodes[index];
        if (target.seq === targetSeq) {
            if (position === 'inside') {
                target.children = target.children ?? [];
                target.children.push(movingNode);
            } else {
                nodes.splice(position === 'before' ? index : index + 1, 0, movingNode);
            }
            return true;
        }
        if (insertCodeNode(target.children ?? [], movingNode, targetSeq, position)) return true;
    }
    return false;
};

const normalizeStructure = (
    nodes: CommonCodeNode[],
    parentSeq = 0,
    inheritedGroupCode = ''
): CommonCodeNode[] => (
    nodes.map((node, index) => {
        const groupCode = parentSeq === 0 ? node.groupCode : inheritedGroupCode;
        return {
            ...node,
            parentSeq,
            groupCode,
            sortOrder: index * 10,
            children: normalizeStructure(node.children ?? [], node.seq, groupCode)
        };
    })
);

const moveCommonCodeNode = (
    sourceNodes: CommonCodeNode[],
    draggedSeq: number,
    targetSeq: number,
    position: DropPosition
): CommonCodeNode[] | null => {
    if (!isValidDrop(sourceNodes, draggedSeq, targetSeq, position)) {
        return null;
    }

    const nextNodes = cloneCodes(sourceNodes);
    const movingNode = removeCodeNode(nextNodes, draggedSeq);
    if (!movingNode || !insertCodeNode(nextNodes, movingNode, targetSeq, position)) {
        return null;
    }
    return normalizeStructure(nextNodes);
};

const buildReorderPayload = (nodes: CommonCodeNode[]) => (
    flattenCodes(nodes).map((node) => ({
        seq: node.seq,
        parentSeq: node.parentSeq,
        sortOrder: node.sortOrder
    }))
);

const normalizeTree = (nodes: CommonCodeNode[]): CommonCodeNode[] => (
    [...nodes]
        .sort(compareCodes)
        .map((node) => ({
            ...node,
            children: normalizeTree(node.children ?? [])
        }))
);

const compareCodes = (left: CommonCodeNode, right: CommonCodeNode) => (
    left.sortOrder - right.sortOrder || left.seq - right.seq
);

const flattenCodes = (nodes: CommonCodeNode[]): CommonCodeNode[] => (
    nodes.flatMap((node) => [node, ...flattenCodes(node.children ?? [])])
);

const findBySeq = (nodes: CommonCodeNode[], seq: number): CommonCodeNode | null => {
    for (const node of nodes) {
        if (node.seq === seq) return node;
        const child = findBySeq(node.children ?? [], seq);
        if (child) return child;
    }
    return null;
};

const containsSeq = (node: CommonCodeNode, seq: number): boolean => (
    node.seq === seq || (node.children ?? []).some((child) => containsSeq(child, seq))
);

const findCodePath = (nodes: CommonCodeNode[], seq: number, path: CommonCodeNode[] = []): CommonCodeNode[] | null => {
    for (const node of nodes) {
        const nextPath = [...path, node];
        if (node.seq === seq) return nextPath;
        const childPath = findCodePath(node.children ?? [], seq, nextPath);
        if (childPath) return childPath;
    }
    return null;
};

const collectExpandedSeqs = (nodes: CommonCodeNode[]): Record<number, boolean> => {
    const result: Record<number, boolean> = {};
    const visit = (items: CommonCodeNode[]) => {
        items.forEach((item) => {
            if (item.children?.length) {
                result[item.seq] = true;
                visit(item.children);
            }
        });
    };
    visit(nodes);
    return result;
};

const getParentLabel = (nodes: CommonCodeNode[], parentSeq: number) => {
    if (parentSeq === 0) return '최상위 코드';
    const parent = findBySeq(nodes, parentSeq);
    return parent ? `${parent.codeName} (#${parent.seq})` : '상위 코드 없음';
};

const getIndentedLabel = (nodes: CommonCodeNode[], node: CommonCodeNode) => {
    const depth = Math.max((findCodePath(nodes, node.seq)?.length ?? 1) - 1, 0);
    return `${'　'.repeat(depth)}${node.codeName} (#${node.seq})`;
};
