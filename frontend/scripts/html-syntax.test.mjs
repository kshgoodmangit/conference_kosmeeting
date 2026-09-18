import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import * as core from 'refractor/core';
import markup from 'refractor/markup';
import ts from 'typescript';

const source = readFileSync(new URL('../src/htmlSyntax.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
const exports = {};
runInNewContext(compiled, { exports, require: name => name === 'refractor/core' ? core : { __esModule: true, default: markup } });
const plain = value => JSON.parse(JSON.stringify(value));
const lines = value => plain(exports.htmlSyntaxLines(value));
const merge = (tokens, parts) => plain(exports.mergeSyntaxWithDiff(tokens, parts));
const restore = tokens => tokens.map(line => line.map(part => part.text).join('')).join('\n');

test('tags, attributes, values and body text have independent colors', () => {
    const source = '<div class="faq-answer" th:utext="value">APDRC 2027</div>';
    const tokens = lines(source);
    assert.equal(restore(tokens), source);
    assert.ok(tokens[0].some(part => part.kind === 'tag' && part.text.includes('div')));
    assert.ok(tokens[0].some(part => part.kind === 'attribute' && part.text === 'class'));
    assert.ok(tokens[0].some(part => part.kind === 'value' && part.text.includes('faq-answer')));
    assert.ok(tokens[0].some(part => part.kind === 'attribute' && part.text.includes('th:utext')));
    assert.ok(tokens[0].some(part => part.kind === 'text' && part.text === 'APDRC 2027'));
});

test('multiline comments and attribute values preserve their syntax context', () => {
    const source = '<!-- first\n<div>not a tag</div>\nlast -->\n<div title="first\nsecond">text</div>';
    const tokens = lines(source);
    assert.equal(restore(tokens), source);
    assert.equal(tokens[1][0].kind, 'comment');
    assert.equal(tokens[4][0].kind, 'value');
});

test('quoted delimiters, entities and unsafe HTML remain exact inert strings', () => {
    const source = '<img title="a > b" onerror="alert(1)">&lt;script&gt; &amp; &#10;';
    const tokens = lines(source);
    assert.equal(restore(tokens), source);
    assert.ok(tokens[0].some(part => part.kind === 'value' && part.text.includes('a > b')));
    assert.ok(tokens[0].some(part => part.kind === 'entity' && part.text.includes('&lt;')));
});

test('combining diff and syntax boundaries preserves every character and changed range', () => {
    const source = '<p class="new">안내</p>';
    const offset = source.indexOf('new');
    const parts = [{ text: source.slice(0, offset), changed: false }, { text: 'new', changed: true }, { text: source.slice(offset + 3), changed: false }];
    const merged = merge(lines(source)[0], parts);
    assert.equal(merged.map(part => part.text).join(''), source);
    assert.equal(merged.filter(part => part.changed).map(part => part.text).join(''), 'new');
    assert.equal(merged.find(part => part.changed).kind, 'value');
});

test('a changed range spanning several syntax tokens is split without losing the highlight', () => {
    const source = '<p class="new">안내</p>';
    const merged = merge(lines(source)[0], [{ text: source, changed: true }]);
    assert.equal(merged.map(part => part.text).join(''), source);
    assert.ok(merged.every(part => part.changed));
    assert.ok(new Set(merged.map(part => part.kind)).size >= 4);
});

test('empty and incomplete markup, unicode and line endings preserve displayed source', () => {
    for (const source of ['', '<div class="', '<p>🙂 한글</p>\n\n', '<div\r\n class=test>\rtext']) {
        assert.equal(restore(lines(source)), source.replace(/\r\n?/g, '\n'));
    }
    assert.deepEqual(merge([], [{ text: 'fallback', changed: true }]), [{ text: 'fallback', kind: 'text', changed: true }]);
});

test('large inputs skip optional colors while preserving all content', () => {
    const source = '<p>text</p>'.repeat(20_001);
    const tokens = lines(source);
    assert.equal(restore(tokens), source);
    assert.equal(tokens[0].length, 1);
    assert.equal(tokens[0][0].kind, 'text');
});
