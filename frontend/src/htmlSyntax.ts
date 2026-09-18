import { refractor } from 'refractor/core';
import markup from 'refractor/markup';
import type { DiffPart } from './menuHtmlDiff';

// Register HTML only. No global DOM highlighting, editor styles or HTML execution.
refractor.register(markup);

export type SyntaxKind = 'text' | 'tag' | 'attribute' | 'value' | 'comment' | 'entity';
export interface SyntaxPart { text: string; kind: SyntaxKind }
export interface HighlightedDiffPart extends SyntaxPart { changed: boolean }
type SyntaxNode = ReturnType<typeof refractor.highlight>['children'][number];

export function htmlSyntaxLines(source: string): SyntaxPart[][] {
    const normalized = source.replace(/\r\n?/g, '\n');
    const plain = () => normalized.split('\n').map(text => [{ text, kind: 'text' as const }]);
    // Syntax coloring is optional; large content still retains the original diff.
    if (normalized.length > 200_000) return plain();
    const lines: SyntaxPart[][] = [[]];
    let count = 0;
    const append = (text: string, kind: SyntaxKind) => {
        text.split('\n').forEach((value, index) => {
            if (index > 0) lines.push([]);
            if (!value) return;
            const line = lines[lines.length - 1];
            const last = line[line.length - 1];
            if (last?.kind === kind) last.text += value;
            else { line.push({ text: value, kind }); count++; }
        });
    };
    const visit = (node: SyntaxNode, inherited: SyntaxKind = 'text') => {
        if (node.type === 'text') { append(node.value, inherited); return; }
        if (node.type !== 'element') return;
        const classes = node.properties.className;
        const has = (value: string) => Array.isArray(classes) && classes.includes(value);
        const kind: SyntaxKind = has('comment') ? 'comment'
            : has('attr-name') ? 'attribute' : has('attr-value') ? 'value'
                : has('entity') ? 'entity' : has('tag') || has('doctype') || has('prolog') ? 'tag' : inherited;
        node.children.forEach(child => visit(child, kind));
    };
    try {
        refractor.highlight(normalized, 'markup').children.forEach(node => visit(node));
        return count > 20_000 ? plain() : lines;
    } catch {
        // Incomplete HTML must remain readable even if a tokenizer fails.
        return plain();
    }
}

// Split on both token and diff boundaries so a changed attribute keeps its color
// AND its addition/deletion background, without changing a single source byte.
export function mergeSyntaxWithDiff(tokens: SyntaxPart[], parts: DiffPart[]): HighlightedDiffPart[] {
    const result: HighlightedDiffPart[] = [];
    let tokenIndex = 0;
    let tokenOffset = 0;
    for (const part of parts) {
        let offset = 0;
        while (offset < part.text.length) {
            while (tokenIndex < tokens.length && tokenOffset >= tokens[tokenIndex].text.length) {
                tokenIndex++;
                tokenOffset = 0;
            }
            const token = tokens[tokenIndex];
            const length = Math.min(part.text.length - offset, token ? token.text.length - tokenOffset : part.text.length);
            result.push({ text: part.text.slice(offset, offset + length), kind: token?.kind ?? 'text', changed: part.changed });
            offset += length;
            tokenOffset += length;
        }
    }
    return result;
}
