import { useMemo, useRef, useState } from 'react';
import { ArrowDown, ArrowUp } from 'lucide-react';
import { buildHtmlDiff, diffEntries, type DiffCell, type DiffRow } from '../menuHtmlDiff';
import { htmlSyntaxLines, mergeSyntaxWithDiff, type SyntaxKind, type SyntaxPart } from '../htmlSyntax';

interface Props {
    previous: string;
    current: string;
    revision: number;
    currentRevision: number;
}

const rowColors = {
    equal: 'bg-white dark:bg-slate-950',
    added: 'bg-emerald-50 dark:bg-emerald-950/50',
    removed: 'bg-rose-50 dark:bg-rose-950/50',
    modified: 'bg-blue-50 dark:bg-blue-950/50'
};
const labels = { equal: '변경 없음', added: '추가', removed: '삭제', modified: '수정' };
const symbols = { equal: '', added: '+', removed: '−', modified: '~' };
const buttonStyle = 'inline-flex items-center gap-1 rounded-lg border border-slate-200 px-2 py-1.5 text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-40 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-900';
const syntaxColors: Record<SyntaxKind, string> = {
    text: 'text-slate-800 dark:text-slate-200',
    tag: 'text-amber-800 dark:text-amber-300',
    attribute: 'text-sky-800 dark:text-sky-300',
    value: 'text-green-800 dark:text-green-300',
    comment: 'text-slate-500 italic dark:text-slate-400',
    entity: 'text-violet-800 dark:text-violet-300'
};

function SourceCell({ cell, kind, side, tokens }: { cell?: DiffCell; kind: DiffRow['kind']; side: 'left' | 'right'; tokens: SyntaxPart[] }) {
    if (!cell) return <td aria-label="대응하는 줄 없음" className="border-r border-slate-200 bg-slate-100/80 p-0 dark:border-slate-800 dark:bg-slate-900/80" />;
    return <td className={`border-r border-slate-200 p-0 align-top dark:border-slate-800 ${rowColors[kind]}`}>
        <div className="flex min-h-6 font-mono text-xs leading-6">
            <span aria-label={`${cell.line}행`} className="w-12 shrink-0 select-none border-r border-slate-200/70 px-2 text-right text-slate-400 dark:border-slate-800 dark:text-slate-500">{cell.line}</span>
            <span title={labels[kind]} className="w-6 shrink-0 select-none text-center font-bold text-slate-500 dark:text-slate-400">{symbols[kind]}</span>
            <code className="min-w-0 flex-1 whitespace-pre-wrap break-all pr-3 text-slate-800 [tab-size:4] dark:text-slate-200">
                {mergeSyntaxWithDiff(tokens, cell.parts).map((part, index) => <span key={index} className={`${syntaxColors[part.kind]} ${part.changed && kind === 'modified'
                    ? side === 'left' ? 'rounded-sm bg-rose-200 dark:bg-rose-900' : 'rounded-sm bg-emerald-200 dark:bg-emerald-900'
                    : ''}`}>{part.text}</span>)}
                {cell.text === '' && <span aria-label="빈 줄">{'\u00a0'}</span>}
            </code>
        </div>
    </td>;
}

export function HtmlSourceComparison({ previous, current, revision, currentRevision }: Props) {
    const diff = useMemo(() => buildHtmlDiff(previous, current), [previous, current]);
    const leftSyntax = useMemo(() => diff.limited ? [] : htmlSyntaxLines(previous), [previous, diff.limited]);
    const rightSyntax = useMemo(() => diff.limited ? [] : htmlSyntaxLines(current), [current, diff.limited]);
    const [compact, setCompact] = useState(true);
    const [expanded, setExpanded] = useState<Set<number>>(() => new Set());
    const [activeBlock, setActiveBlock] = useState(-1);
    const scrollRef = useRef<HTMLDivElement>(null);
    const entries = useMemo(() => diffEntries(diff.rows, compact), [diff, compact]);

    const navigate = (direction: number) => {
        if (!diff.blocks) return;
        const next = activeBlock < 0 ? direction > 0 ? 0 : diff.blocks - 1
            : (activeBlock + direction + diff.blocks) % diff.blocks;
        setActiveBlock(next);
        const container = scrollRef.current;
        const row = container?.querySelector<HTMLElement>(`[data-diff-block="${next}"]`);
        if (container && row) {
            container.scrollTop += row.getBoundingClientRect().top - container.getBoundingClientRect().top - 60;
        }
    };

    const renderRow = (row: DiffRow, index: number) => <tr key={index} data-diff-block={row.block} data-diff-kind={row.kind} aria-label={labels[row.kind]}>
        <SourceCell cell={row.left} kind={row.kind} side="left" tokens={leftSyntax[(row.left?.line ?? 0) - 1] ?? []} />
        <td aria-hidden="true" className={`w-5 p-0 text-center font-mono text-xs ${row.block !== undefined && row.block === activeBlock
            ? 'bg-blue-500 text-white dark:bg-blue-500 dark:text-white'
            : 'bg-slate-100 text-slate-400 dark:bg-slate-900 dark:text-slate-500'}`}>{symbols[row.kind]}</td>
        <SourceCell cell={row.right} kind={row.kind} side="right" tokens={rightSyntax[(row.right?.line ?? 0) - 1] ?? []} />
    </tr>;

    if (diff.limited) return <section aria-label="HTML 변경 비교" className="min-h-[280px] flex-1">
        <p role="status" className="rounded-lg border border-slate-200 p-5 text-sm dark:border-slate-800">HTML 크기 또는 변경량이 커서 상세 비교를 생략했습니다. HTML 소스 탭에서 원문을 확인해 주세요.</p>
    </section>;

    return <section aria-label="HTML 변경 비교" className="flex min-h-[280px] flex-1 flex-col gap-3">
        <div className="flex shrink-0 flex-wrap items-center justify-between gap-3 text-xs">
            <div className="flex flex-wrap items-center gap-2" aria-label="변경 요약">
                <span className="rounded bg-emerald-100 px-2 py-1 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-200">+ 추가 {diff.added}줄</span>
                <span className="rounded bg-rose-100 px-2 py-1 text-rose-800 dark:bg-rose-950 dark:text-rose-200">− 삭제 {diff.removed}줄</span>
                <span className="rounded bg-blue-100 px-2 py-1 text-blue-800 dark:bg-blue-950 dark:text-blue-200">~ 수정 {diff.modified}줄</span>
            </div>
            <div className="flex flex-wrap items-center gap-2">
                <label className="mr-2 flex items-center gap-1.5 text-slate-600 dark:text-slate-300"><input type="checkbox" checked={compact} onChange={event => { setCompact(event.target.checked); setExpanded(new Set()); }} />변경 없는 구간 접기</label>
                <span aria-live="polite" className="text-slate-500 dark:text-slate-400">{activeBlock < 0 ? `변경 ${diff.blocks}곳` : `${activeBlock + 1} / ${diff.blocks}`}</span>
                <button type="button" className={buttonStyle} disabled={!diff.blocks} onClick={() => navigate(-1)}><ArrowUp className="h-3.5 w-3.5" />이전 변경</button>
                <button type="button" className={buttonStyle} disabled={!diff.blocks} onClick={() => navigate(1)}><ArrowDown className="h-3.5 w-3.5" />다음 변경</button>
            </div>
        </div>
        <p className="shrink-0 text-xs text-slate-500 dark:text-slate-400">선택 이력 → 현재 저장본 기준입니다. 빈 칸은 대응하는 줄이 없는 부분이며, 수정된 글자는 진하게 표시합니다. 원문 기준 줄 번호이며 줄바꿈 형식(CRLF/LF)만 통일해 비교합니다.</p>
        <div ref={scrollRef} tabIndex={0} role="region" aria-label="좌우 HTML 비교 내용" className="min-h-0 flex-1 overflow-auto rounded-lg border border-slate-200 bg-white outline-offset-2 dark:border-slate-800 dark:bg-slate-950">
                <table className="w-full min-w-[640px] table-fixed border-separate border-spacing-0 text-left">
                    <colgroup><col /><col className="w-5" /><col /></colgroup>
                    <thead className="sticky top-0 z-10 bg-slate-100 text-xs dark:bg-slate-900"><tr>
                        <th scope="col" className="border-b border-slate-200 px-3 py-3 dark:border-slate-700">선택 이력 v{revision}</th>
                        <th aria-label="변경 구분" className="border-b border-slate-200 dark:border-slate-700" />
                        <th scope="col" className="border-b border-slate-200 px-3 py-3 dark:border-slate-700">현재 저장본 v{currentRevision}</th>
                    </tr></thead>
                    <tbody>{entries.map(entry => entry.kind === 'row' ? renderRow(entry.row, entry.index)
                        : expanded.has(entry.start) ? diff.rows.slice(entry.start, entry.start + entry.count).map((row, offset) => renderRow(row, entry.start + offset))
                            : <tr key={`fold-${entry.start}`}><td colSpan={3} className="border-y border-slate-200 bg-slate-50 p-0 text-center dark:border-slate-800 dark:bg-slate-900">
                                <button type="button" className="w-full px-3 py-2 text-xs text-slate-500 hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-800" onClick={() => setExpanded(value => new Set(value).add(entry.start))}>··· 변경 없는 {entry.count}줄 펼치기 ···</button>
                            </td></tr>)}</tbody>
                </table>
                {diff.blocks === 0 && <p role="status" className="p-4 text-center text-sm text-slate-500 dark:text-slate-400">{diff.rows.length ? 'HTML 내용이 동일합니다.' : '양쪽 모두 빈 HTML입니다.'}</p>}
        </div>
    </section>;
}
