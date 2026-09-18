import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import { createRequire } from 'node:module';
import ts from 'typescript';

// Match the existing test harness; also runs with the project's bundled Node 20.
const source = readFileSync(new URL('../src/menuHtmlDiff.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
const exports = {};
runInNewContext(compiled, { exports, require: createRequire(import.meta.url) });
const plain = value => JSON.parse(JSON.stringify(value));
const buildHtmlDiff = (...args) => plain(exports.buildHtmlDiff(...args));
const diffEntries = (...args) => plain(exports.diffEntries(...args));

const kinds = diff => diff.rows.map(row => row.kind);
const texts = (diff, side) => diff.rows.flatMap(row => row[side] ? [row[side].text] : []);

test('insertion aligns unchanged lines instead of marking all subsequent lines changed', () => {
    const diff = buildHtmlDiff('A\nB\nC', 'A\nnew\nB\nC');
    assert.deepEqual(kinds(diff), ['equal', 'added', 'equal', 'equal']);
    assert.equal(diff.rows[1].left, undefined);
    assert.equal(diff.rows[2].left.line, 2);
    assert.equal(diff.rows[2].right.line, 3);
    assert.equal(diff.added, 1);
});

test('deletion leaves an empty counterpart on the right', () => {
    const diff = buildHtmlDiff('A\nold\nB', 'A\nB');
    assert.deepEqual(kinds(diff), ['equal', 'removed', 'equal']);
    assert.equal(diff.rows[1].right, undefined);
    assert.equal(diff.removed, 1);
});

test('replacement highlights only changed characters including Korean and attributes', () => {
    const diff = buildHtmlDiff('<p class="old">학회 안내</p>', '<p class="new">학회 일정</p>');
    const row = diff.rows[0];
    assert.equal(row.kind, 'modified');
    assert.equal(diff.modified, 1);
    assert.equal(row.left.parts.map(part => part.text).join(''), row.left.text);
    assert.equal(row.right.parts.map(part => part.text).join(''), row.right.text);
    assert.ok(row.right.parts.some(part => !part.changed && part.text.includes('학회')));
    assert.ok(row.right.parts.some(part => part.changed && part.text.includes('일정')));
});

test('unequal replacement blocks pair available lines and retain extra insertions', () => {
    const diff = buildHtmlDiff('start\nold\nend', 'start\nnew\nextra\nend');
    assert.deepEqual(kinds(diff), ['equal', 'modified', 'added', 'equal']);
    assert.equal(diff.blocks, 1);
    assert.equal(diff.rows[1].block, diff.rows[2].block);
});

test('separate changes have separate navigation block identifiers', () => {
    const diff = buildHtmlDiff('A\nB\nC\nD', 'A\nX\nC\nY');
    assert.equal(diff.blocks, 2);
    assert.equal(diff.rows[1].block, 0);
    assert.equal(diff.rows[3].block, 1);
});

test('blank input and empty lines are distinguished', () => {
    assert.equal(buildHtmlDiff('', '').rows.length, 0);
    assert.deepEqual(kinds(buildHtmlDiff('', '<p>new</p>')), ['added']);
    assert.deepEqual(kinds(buildHtmlDiff('<p>old</p>', '')), ['removed']);
    assert.deepEqual(kinds(buildHtmlDiff('A', 'A\n')), ['equal', 'added']);
    assert.deepEqual(kinds(buildHtmlDiff('A\n\nB', 'A\nB')), ['equal', 'removed', 'equal']);
});

test('CRLF and LF compare equally, meaningful spaces and case remain differences', () => {
    assert.equal(buildHtmlDiff('A\r\nB', 'A\nB').blocks, 0);
    assert.equal(buildHtmlDiff('<pre>a b</pre>', '<pre>a  b</pre>').modified, 1);
    assert.equal(buildHtmlDiff('Title', 'title').modified, 1);
});

test('repeated HTML lines and moved blocks preserve both complete originals', () => {
    const before = '<div>\n<p>A</p>\n</div>\n<div>\n<p>B</p>\n</div>';
    const after = '<div>\n<p>B</p>\n</div>\n<div>\n<p>A</p>\n</div>';
    const diff = buildHtmlDiff(before, after);
    assert.equal(texts(diff, 'left').join('\n'), before);
    assert.equal(texts(diff, 'right').join('\n'), after);
});

test('folding preserves context and never hides changes', () => {
    const before = Array.from({ length: 30 }, (_, i) => `line ${i}`).join('\n');
    const diff = buildHtmlDiff(before, `${before}\nnew`);
    const entries = diffEntries(diff.rows, true);
    assert.equal(entries.find(entry => entry.kind === 'fold').count, 24);
    assert.equal(entries.at(-1).row.kind, 'added');
    assert.equal(diffEntries(diff.rows, false).length, 31);
});

test('size and line limits explicitly decline comparison', () => {
    assert.equal(buildHtmlDiff('x'.repeat(1_000_001), '').limited, true);
    assert.equal(buildHtmlDiff('\n'.repeat(12_001), '').limited, true);
});

test('large replacement safely falls back from character highlighting without changing source', () => {
    const diff = buildHtmlDiff('a'.repeat(9_000), 'b'.repeat(9_000));
    assert.equal(diff.limited, false);
    assert.equal(diff.modified, 1);
    assert.equal(diff.rows[0].left.parts[0].text.length, 9_000);
});

test('markup remains inert source strings, including scripts', () => {
    const html = '<script>alert(1)</script><img src=x onerror=alert(2)>';
    assert.equal(buildHtmlDiff('', html).rows[0].right.text, html);
});

test('deterministic random edits reconstruct both inputs with sequential line numbers', () => {
    let seed = 17;
    const random = () => (seed = (seed * 16807) % 2147483647) % 8;
    for (let trial = 0; trial < 100; trial++) {
        const before = Array.from({ length: 20 }, () => String(random()));
        const after = before.filter(() => random() > 1).flatMap(value => random() === 0 ? [value, 'new'] : [value]);
        const diff = buildHtmlDiff(before.join('\n'), after.join('\n'));
        assert.deepEqual(texts(diff, 'left'), before);
        assert.deepEqual(texts(diff, 'right'), after);
        for (const side of ['left', 'right']) {
            const cells = diff.rows.flatMap(row => row[side] ? [row[side]] : []);
            assert.deepEqual(cells.map(cell => cell.line), cells.map((_, i) => i + 1));
        }
    }
});
