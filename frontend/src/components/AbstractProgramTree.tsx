import { useId, useMemo, useState, type DragEvent, type ReactNode } from 'react';
import { ChevronRight, ListTree, Unlink } from 'lucide-react';
import type { ProgramItem } from './programTypes';
import { normalizeTime } from './programTypes';
import { isAssignableSlot, type AbstractProgramCandidate, type AbstractProgramData } from './abstractProgramTypes';
import { buildAbstractProgramTree, type AbstractProgramTreeNode } from './abstractProgramTreeModel';

interface Props {
    data: AbstractProgramData;
    disabled: boolean;
    saving: boolean;
    selectedAbstract?: AbstractProgramCandidate;
    selectedSlotSeq: number | null;
    draggedAbstractSeq: number | null;
    getDraggedAbstractSeq: () => number | null;
    onSelectSlot: (seq: number) => void;
    onRelease: (item: ProgramItem) => void;
    onDropAbstract: (item: ProgramItem, seq: number) => void;
}

const buttonClass = 'inline-flex shrink-0 items-center justify-center gap-1 rounded-lg border border-slate-200 px-2 py-1.5 text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-900';

export const AbstractProgramTree = ({ data, disabled, saving, selectedAbstract, selectedSlotSeq, draggedAbstractSeq,
    getDraggedAbstractSeq, onSelectSlot, onRelease, onDropAbstract }: Props) => {
    const tree = useMemo(() => buildAbstractProgramTree(data.program), [data.program]);
    const [collapsed, setCollapsed] = useState<Set<string>>(new Set());
    const [overSlot, setOverSlot] = useState<number | null>(null);
    const prefix = useId();
    const candidate = data.candidates.find(item => item.seq === draggedAbstractSeq);
    const canUseCandidate = (value?: AbstractProgramCandidate) => !!value && value.assignmentCount === 0 && value.presenterCount > 0;
    const dragging = !disabled && canUseCandidate(candidate);
    const liveCandidate = () => data.candidates.find(item => item.seq === getDraggedAbstractSeq());
    const toggle = (key: string) => setCollapsed(previous => {
        const next = new Set(previous);
        if (next.has(key)) next.delete(key); else next.add(key);
        return next;
    });
    const groupKeys = (nodes: AbstractProgramTreeNode[]): string[] => nodes.flatMap(node => [node.key, ...groupKeys(node.children ?? [])]);

    const dragOver = (event: DragEvent, item: ProgramItem) => {
        if (disabled || !canUseCandidate(liveCandidate()) || item.abstractSubmissionSeq != null || !isAssignableSlot(item, data.program)) return;
        event.preventDefault();
        event.dataTransfer.dropEffect = 'move';
        setOverSlot(item.seq);
    };

    const slotRow = (item: ProgramItem) => {
        const eligible = isAssignableSlot(item, data.program);
        const assigned = item.abstractSubmissionSeq != null;
        const link = data.assignments.find(entry => entry.programItemSeq === item.seq);
        const warning = assigned && (!eligible || link?.status !== 'approved'
            || data.assignments.filter(entry => entry.abstractSubmissionSeq === item.abstractSubmissionSeq).length > 1);
        const canDrop = dragging && eligible && !assigned;
        const hovered = canDrop && overSlot === item.seq;
        const selected = selectedSlotSeq === item.seq;
        return <li key={item.seq} className="relative pl-3 sm:pl-4">
            <span aria-hidden="true" className="absolute left-0 top-6 w-3 border-t border-slate-200 dark:border-slate-700 sm:w-4" />
            <div data-program-slot={item.seq} onDragOver={event => dragOver(event, item)}
                onDragLeave={event => { if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setOverSlot(null); }}
                onDrop={event => {
                    event.preventDefault();
                    event.stopPropagation();
                    setOverSlot(null);
                    const dropped = liveCandidate();
                    if (disabled || !dropped || !canUseCandidate(dropped) || assigned || !eligible
                        || event.dataTransfer.getData('application/x-conference-abstract') !== String(dropped.seq)) return;
                    onDropAbstract(item, dropped.seq);
                }}
                className={`my-2 rounded-lg border p-3 transition-colors ${hovered ? 'border-blue-500 bg-blue-100 ring-2 ring-blue-300 dark:border-blue-400 dark:bg-blue-900/60 dark:ring-blue-600'
                    : canDrop ? 'border-dashed border-blue-400 bg-blue-50 dark:border-blue-500 dark:bg-blue-950/40'
                    : selected ? 'border-blue-400 bg-blue-50 dark:border-blue-600 dark:bg-blue-950/40'
                    : 'border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-950'}`}>
                <div className="flex flex-wrap items-start justify-between gap-2">
                    <button type="button" disabled={disabled || !eligible || assigned} onClick={() => onSelectSlot(item.seq)} aria-pressed={selected}
                        className="min-w-0 flex-1 text-left disabled:opacity-70" aria-label={`${normalizeTime(item.startTime)} ${item.title} 발표 시간 선택`}>
                        <span className="font-mono text-xs font-semibold">{normalizeTime(item.startTime)}–{normalizeTime(item.endTime)}</span>
                        <span className="mt-1 block break-words text-sm font-semibold">{item.title}</span>
                    </button>
                    <span className={`shrink-0 rounded px-2 py-1 text-[11px] font-semibold ${warning ? 'bg-rose-50 text-rose-700 dark:bg-rose-950/40 dark:text-rose-300'
                        : assigned ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300'
                        : selected ? 'bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300'
                        : 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300'}`}>
                        {warning ? '확인 필요' : assigned ? '배정 완료' : !eligible ? '배정 불가' : selected ? '선택됨' : '미배정'}</span>
                </div>
                {assigned ? <div className="mt-2 flex items-start justify-between gap-2"><p className="min-w-0 whitespace-pre-line break-words text-xs text-slate-500 dark:text-slate-400">{link?.submissionNo || `초록 #${item.abstractSubmissionSeq}`} · {item.speakerText || '발표자 정보 없음'}</p>
                    <button type="button" disabled={disabled} onClick={() => onRelease(item)} className={buttonClass} aria-label={`${item.title} 초록 배정 해제`}><Unlink className="h-3.5 w-3.5" />해제</button></div>
                    : <p className={`mt-2 text-xs ${canDrop ? 'text-blue-700 dark:text-blue-300' : 'text-slate-500 dark:text-slate-400'}`}>
                        {!eligible ? '사용 여부와 상위 세션을 확인해주세요.' : hovered ? '여기에 놓으면 바로 배정됩니다.' : '초록을 이 시간에 끌어다 놓으세요.'}</p>}
            </div>
        </li>;
    };

    const branch = (node: AbstractProgramTreeNode, depth: number): ReactNode => {
        const open = !collapsed.has(node.key);
        const id = `${prefix}-${node.key}`;
        return <li key={node.key}>
            <button type="button" onClick={() => toggle(node.key)} aria-expanded={open} aria-controls={id}
                className={`flex w-full items-start gap-2 rounded-lg px-2 py-2.5 text-left text-sm font-semibold hover:bg-slate-100 dark:hover:bg-slate-800 ${depth === 0 ? 'bg-slate-50 dark:bg-slate-900' : ''}`}>
                <ChevronRight className={`mt-0.5 h-4 w-4 shrink-0 text-slate-400 transition-transform dark:text-slate-500 ${open ? 'rotate-90' : ''}`} />
                <span className="min-w-0 break-words">{node.label}</span>
            </button>
            <ul id={id} hidden={!open} className="ml-3 border-l border-slate-200 pl-1 dark:border-slate-700 sm:ml-4 sm:pl-2">
                {node.children?.map(child => branch(child, depth + 1))}{node.slots?.map(slotRow)}
            </ul>
        </li>;
    };

    return <section aria-label="초록 발표 편성 트리" className="flex min-w-0 flex-col border-t border-slate-200 dark:border-slate-800 lg:h-[calc(100vh-15rem)] lg:min-h-[34rem] lg:border-l lg:border-t-0">
        <div className="flex h-16 shrink-0 items-center justify-between gap-2 border-b border-slate-200 px-4 dark:border-slate-800 md:px-5">
            <h2 className="flex min-w-0 items-center gap-2 text-sm font-semibold"><ListTree className="h-4 w-4 shrink-0 text-blue-600 dark:text-blue-400" />2. 초록 발표 편성</h2>
            <div className="flex shrink-0 gap-2"><button type="button" className={buttonClass} onClick={() => setCollapsed(new Set())}>모두 펼치기</button>
                <button type="button" className={buttonClass} onClick={() => setCollapsed(new Set(groupKeys(tree)))}>모두 접기</button></div>
        </div>
        <div className="shrink-0 border-b border-slate-200 bg-slate-50/70 px-4 py-3 dark:border-slate-800 dark:bg-slate-900/40" aria-live="polite">
            <p className="break-words text-xs text-slate-600 dark:text-slate-300">{saving ? '배정 정보를 저장하고 있습니다.' : selectedAbstract
                ? `선택 초록: ${selectedAbstract.submissionNo || `#${selectedAbstract.seq}`} · ${selectedAbstract.title}`
                : '왼쪽 초록을 빈 발표 시간에 끌어다 놓으면 바로 배정됩니다.'}</p>
        </div>
        <div className="min-h-40 flex-1 overflow-y-auto p-3 md:p-4" aria-busy={saving}
            onDragOver={event => {
                if (disabled || !canUseCandidate(liveCandidate())) return;
                const bounds = event.currentTarget.getBoundingClientRect();
                if (event.clientY < bounds.top + 48) event.currentTarget.scrollBy(0, -14);
                else if (event.clientY > bounds.bottom - 48) event.currentTarget.scrollBy(0, 14);
            }} onDrop={event => event.preventDefault()}>
            {tree.length ? <ul className="space-y-2">{tree.map(node => branch(node, 0))}</ul>
                : <p className="p-6 text-center text-sm text-slate-500 dark:text-slate-400">초록 발표로 편성된 시간이 없습니다. 프로그램 편성표에서 먼저 등록해주세요.</p>}
        </div>
        <p className="shrink-0 border-t border-slate-200 px-4 py-3 text-xs text-slate-500 dark:border-slate-800 dark:text-slate-400">개별 빈 시간에만 배정할 수 있습니다. 날짜·룸·시간은 유지됩니다.</p>
    </section>;
};
