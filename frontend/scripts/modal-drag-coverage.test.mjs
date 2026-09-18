import test from 'node:test';
import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import ts from 'typescript';

test('every modal overlay has a draggable surface and an explicit header handle', () => {
    const directory = new URL('../src/components/', import.meta.url);
    // Fullscreen dashboard and decorative globe are not dialog windows.
    const excluded = new Set(['AdminShowcaseDashboardPage.tsx', 'ShowcaseGlobe.tsx']);
    const missing = [];
    let count = 0;
    for (const file of readdirSync(directory).filter(name => name.endsWith('.tsx') && !excluded.has(name))) {
        const source = readFileSync(new URL(file, directory), 'utf8');
        const tree = ts.createSourceFile(file, source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
        const attribute = (node, name) => node.openingElement.attributes.properties.find(a => ts.isJsxAttribute(a) && a.name.getText() === name);
        const classes = node => attribute(node, 'className')?.initializer?.getText() ?? '';
        const children = node => node.children.filter(ts.isJsxElement);
        const hasHandle = node => {
            if (attribute(node, 'data-modal-drag-handle') || attribute(node, 'ref')?.getText().includes('headerRef')) return true;
            return children(node).some(hasHandle);
        };
        function visit(node) {
            if (ts.isJsxElement(node) && classes(node).includes('fixed inset-0')) {
                count++;
                const surface = children(node).find(child => /DraggableModal/.test(child.openingElement.tagName.getText()) || attribute(child, 'ref')?.getText().includes('modalRef'));
                if (!surface || !hasHandle(surface)) missing.push(`${file}:${tree.getLineAndCharacterOfPosition(node.getStart()).line + 1}`);
            }
            ts.forEachChild(node, visit);
        }
        visit(tree);
    }
    assert.ok(count >= 31, `Unexpectedly few overlays discovered: ${count}`);
    assert.deepEqual(missing, []);
});
