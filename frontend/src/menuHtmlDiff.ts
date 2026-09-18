import { diffArrays, diffChars } from 'diff';

export interface DiffPart { text: string; changed: boolean }
export interface DiffCell { line: number; text: string; parts: DiffPart[] }
export interface DiffRow {
    kind: 'equal' | 'added' | 'removed' | 'modified';
    left?: DiffCell;
    right?: DiffCell;
    block?: number;
}
export interface HtmlDiff {
    rows: DiffRow[];
    blocks: number;
    added: number;
    removed: number;
    modified: number;
    limited: boolean;
}

// Compare source, never parse/serialize HTML: whitespace and attributes are data.
// Only CRLF/CR line endings are normalized for cross-platform source comparison.
export function buildHtmlDiff(previous: string, current: string): HtmlDiff {
    const result: HtmlDiff = { rows: [], blocks: 0, added: 0, removed: 0, modified: 0, limited: false };
    if (previous.length + current.length > 1_000_000) return { ...result, limited: true };
    const lines = (value: string) => value === '' ? [] : value.replace(/\r\n?/g, '\n').split('\n');
    const before = lines(previous);
    const after = lines(current);
    if (before.length + after.length > 12_000) return { ...result, limited: true };
    const changes = diffArrays(before, after, { timeout: 200, maxEditLength: 2_000 });
    if (!changes) return { ...result, limited: true };

    let leftLine = 1;
    let rightLine = 1;
    let inlineBudget = 100_000;
    const cell = (text: string, line: number, changed: boolean): DiffCell => ({
        line, text, parts: [{ text, changed }]
    });
    for (let index = 0; index < changes.length;) {
        const change = changes[index];
        if (!change.added && !change.removed) {
            for (const text of change.value) {
                result.rows.push({ kind: 'equal', left: cell(text, leftLine++, false), right: cell(text, rightLine++, false) });
            }
            index++;
            continue;
        }
        const removed: string[] = [];
        const added: string[] = [];
        while (index < changes.length && (changes[index].added || changes[index].removed)) {
            const part = changes[index++];
            (part.removed ? removed : added).push(...part.value);
        }
        const block = result.blocks++;
        for (let offset = 0; offset < Math.max(removed.length, added.length); offset++) {
            const left = offset < removed.length ? cell(removed[offset], leftLine++, true) : undefined;
            const right = offset < added.length ? cell(added[offset], rightLine++, true) : undefined;
            const kind = left && right ? 'modified' : left ? 'removed' : 'added';
            result[kind]++;
            if (left && right && left.text.length + right.text.length <= Math.min(8_000, inlineBudget)) {
                inlineBudget -= left.text.length + right.text.length;
                const parts = diffChars(left.text, right.text, { timeout: 5, maxEditLength: 500 });
                if (parts) {
                    left.parts = parts.filter(part => !part.added).map(part => ({ text: part.value, changed: part.removed }));
                    right.parts = parts.filter(part => !part.removed).map(part => ({ text: part.value, changed: part.added }));
                }
            }
            result.rows.push({ kind, left, right, block });
        }
    }
    return result;
}

export type DiffEntry = { kind: 'row'; row: DiffRow; index: number }
    | { kind: 'fold'; start: number; count: number };

export function diffEntries(rows: DiffRow[], compact: boolean): DiffEntry[] {
    if (!compact) return rows.map((row, index) => ({ kind: 'row', row, index }));
    const entries: DiffEntry[] = [];
    for (let start = 0; start < rows.length;) {
        let end = start;
        while (end < rows.length && rows[end].kind === 'equal') end++;
        if (compact && end - start > 8) {
            for (let index = start; index < start + 3; index++) entries.push({ kind: 'row', row: rows[index], index });
            entries.push({ kind: 'fold', start: start + 3, count: end - start - 6 });
            for (let index = end - 3; index < end; index++) entries.push({ kind: 'row', row: rows[index], index });
            start = end;
        } else {
            entries.push({ kind: 'row', row: rows[start], index: start });
            start++;
        }
    }
    return entries;
}
