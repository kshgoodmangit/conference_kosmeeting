import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import ts from 'typescript';

// Run the page's actual tree helpers without mounting its editor or fetching menus.
const source = readFileSync(new URL('../src/components/MenuPage.tsx', import.meta.url), 'utf8');
const compiled = ts.transpileModule(`${source}\nexport { moveMenuNode, buildReorderPayload };`, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX }
}).outputText;
const exports = {};
runInNewContext(compiled, { exports, require: () => ({}) });
const { moveMenuNode, buildReorderPayload } = exports;
const plain = value => JSON.parse(JSON.stringify(value));

function tree(scope = 'admin', offset = 0) {
    const node = (seq, menuKey, parentKey, children = []) => ({
        seq: offset + seq, menuScope: scope, menuKey, parentKey,
        menuName: menuKey, enabled: true, sortOrder: seq * 10, children
    });
    return [node(1, 'root', null, [
        node(2, 'group', 'root', [node(3, 'section', 'group', [
            node(4, 'first', 'section'), node(5, 'second', 'section')
        ])]),
        node(6, 'other', 'root'), node(7, 'last', 'root')
    ])];
}

for (const scope of ['admin', 'user']) {
    test(`${scope}: reorder third-level siblings before and after without changing the input`, () => {
        const input = tree(scope);
        const original = structuredClone(input);
        const moved = moveMenuNode(input, `${scope}:second`, `${scope}:first`, 'before');
        assert.ok(moved, 'a valid third-level sibling move must be accepted');
        assert.deepEqual(plain(moved[0].children[0].children[0].children.map(n => n.menuKey)), ['second', 'first']);
        const restored = moveMenuNode(moved, `${scope}:second`, `${scope}:first`, 'after');
        assert.deepEqual(plain(restored[0].children[0].children[0].children.map(n => n.menuKey)), ['first', 'second']);
        assert.deepEqual(input, original);
    });
}

test('an existing third-level branch in either scope does not block top-level sorting', () => {
    const input = [...tree(), ...tree('user', 100)];
    const moved = moveMenuNode(input, 'admin:last', 'admin:other', 'before');
    assert.ok(moved);
    assert.deepEqual(plain(moved[0].children.map(n => n.menuKey)), ['group', 'last', 'other']);
    assert.deepEqual(plain(moved[1].children.map(n => n.menuKey)), ['group', 'other', 'last']);
});

test('moving inside a second-level menu accepts depth three and updates the save payload', () => {
    const moved = moveMenuNode(tree(), 'admin:other', 'admin:section', 'inside');
    assert.ok(moved);
    const items = plain(buildReorderPayload(moved));
    assert.deepEqual(items.find(n => n.seq === 6), { seq: 6, parentKey: 'section', sortOrder: 20 });
    assert.equal(items.some(n => n.seq === 1), false);
    assert.equal(new Set(items.map(n => n.seq)).size, 6);
});

test('moving a subtree to the root preserves its children and changes its parent', () => {
    const moved = moveMenuNode(tree(), 'admin:section', 'admin:root', 'inside');
    assert.ok(moved);
    const section = moved[0].children.at(-1);
    assert.equal(section.parentKey, 'root');
    assert.deepEqual(plain(section.children.map(n => n.menuKey)), ['first', 'second']);
});

test('fourth-level placement and moves that push descendants beyond depth three remain blocked', () => {
    assert.equal(moveMenuNode(tree(), 'admin:other', 'admin:first', 'inside'), null);
    assert.equal(moveMenuNode(tree(), 'admin:group', 'admin:other', 'inside'), null);
});

test('self, descendant, cross-scope and missing-node drops remain blocked', () => {
    const input = [...tree(), ...tree('user', 100)];
    for (const [dragged, target] of [
        ['admin:first', 'admin:first'], ['admin:group', 'admin:section'],
        ['admin:first', 'user:section'], ['admin:missing', 'admin:section']
    ]) {
        assert.equal(moveMenuNode(input, dragged, target, 'inside'), null);
    }
});
