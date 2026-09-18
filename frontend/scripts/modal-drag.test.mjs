import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import ts from 'typescript';

const source = readFileSync(new URL('../src/hooks/useModalDrag.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;

// Exercise the hook's real event handlers with deterministic layout and animation frames.
function setup(positioning = 'centered') {
    class ElementStub extends EventTarget {
        style = { transform: '', willChange: '' };
        captured = null;
        closest() { return null; }
        setPointerCapture(id) { this.captured = id; }
        hasPointerCapture(id) { return this.captured === id; }
        releasePointerCapture() { this.captured = null; }
    }
    const document = new EventTarget();
    document.documentElement = { clientWidth: 1000, clientHeight: 800 };
    const window = new EventTarget();
    const modal = new ElementStub();
    const header = new ElementStub();
    const position = () => {
        const numbers = [...modal.style.transform.matchAll(/\+ (-?[\d.]+)px/g)].map(match => Number(match[1]));
        if (numbers.length === 2) return numbers;
        const offset = modal.style.transform.match(/^translate\((-?[\d.]+)px, (-?[\d.]+)px\)$/);
        return offset ? [Number(offset[1]), Number(offset[2])] : [0, 0];
    };
    modal.getBoundingClientRect = () => {
        const [x, y] = position();
        return { left: (document.documentElement.clientWidth - 400) / 2 + x,
            top: (document.documentElement.clientHeight - 300) / 2 + y, width: 400, height: 300 };
    };
    const refs = [];
    let effect;
    let frameId = 0;
    const frames = new Map();
    let resizeCallback;
    let observing = false;
    const exports = {};
    runInNewContext(compiled, {
        exports, document, window, Element: ElementStub,
        ResizeObserver: class {
            constructor(callback) { resizeCallback = callback; }
            observe() { observing = true; }
            disconnect() { observing = false; }
        },
        requestAnimationFrame: callback => { frames.set(++frameId, callback); return frameId; },
        cancelAnimationFrame: id => frames.delete(id),
        require: name => {
            assert.equal(name, 'react');
            return { useRef: value => { const ref = { current: value }; refs.push(ref); return ref; },
                useEffect: callback => { effect = callback; } };
        }
    });
    exports.useModalDrag(true, positioning);
    refs[0].current = modal;
    refs[1].current = header;
    let cleanup = effect();
    const pointer = (target, type, x, y, extra = {}) => {
        const event = new Event(type, { cancelable: true });
        Object.assign(event, { clientX: x, clientY: y, button: 0, isPrimary: true, pointerId: 1, ...extra });
        target.dispatchEvent(event);
    };
    return { document, window, modal, header, position, frames, pointer,
        resizeContent: () => { if (observing) resizeCallback(); },
        close: () => cleanup(), reopen: () => { cleanup = effect(); },
        paint: () => { const callbacks = [...frames.values()]; frames.clear(); callbacks.forEach(fn => fn()); } };
}

test('repeated drags retain the latest position even when released before the animation frame', () => {
    const h = setup();
    for (let i = 1; i <= 4; i++) {
        h.pointer(h.header, 'pointerdown', 100, 100);
        h.pointer(h.document, 'pointermove', 125, 110);
        h.pointer(h.document, 'pointermove', 150, 120);
        assert.equal(h.frames.size, 1);
        h.pointer(h.document, 'pointerup', 150, 120);
        assert.deepEqual(h.position(), [50 * i, 20 * i]);
        assert.equal(h.frames.size, 0);
    }
    h.close();
});

test('offset mode preserves flex centering and reacts to content resize', () => {
    const h = setup('offset');
    assert.equal(h.modal.style.transform, 'translate(0px, 0px)');
    h.pointer(h.header, 'pointerdown', 100, 100);
    h.pointer(h.document, 'pointerup', 150, 120);
    assert.equal(h.modal.style.transform, 'translate(50px, 20px)');
    h.pointer(h.header, 'pointerdown', 150, 120);
    h.pointer(h.document, 'pointerup', 175, 130);
    assert.equal(h.modal.style.transform, 'translate(75px, 30px)');
    h.modal.getBoundingClientRect = () => ({ left: 375, top: -30, width: 400, height: 790 });
    h.resizeContent();
    assert.equal(h.modal.style.transform, 'translate(75px, 65px)');
    h.close();
    h.resizeContent();
    assert.equal(h.modal.style.transform, '');
});

test('viewport edges clamp movement and the next drag can immediately move away from the edge', () => {
    const h = setup();
    h.pointer(h.header, 'pointerdown', 0, 0);
    h.pointer(h.document, 'pointerup', 2000, 2000);
    assert.deepEqual(h.position(), [292, 242]);
    h.pointer(h.header, 'pointerdown', 500, 500);
    h.pointer(h.document, 'pointerup', 480, 470);
    assert.deepEqual(h.position(), [272, 212]);
    h.document.documentElement = { clientWidth: 500, clientHeight: 400 };
    h.window.dispatchEvent(new Event('resize'));
    assert.deepEqual(h.position(), [42, 42]);
    h.close();
});

test('blur, pointer cancellation and lost capture end movement; closing cancels pending work', () => {
    for (const end of ['blur', 'pointercancel', 'lostpointercapture', 'close']) {
        const h = setup();
        h.pointer(h.header, 'pointerdown', 100, 100);
        h.pointer(h.document, 'pointermove', 120, 130);
        if (end === 'blur') h.window.dispatchEvent(new Event('blur'));
        else if (end === 'close') h.close();
        else h.pointer(end === 'lostpointercapture' ? h.header : h.document, end, 120, 130);
        const stopped = h.position();
        h.pointer(h.document, 'pointermove', 500, 500);
        h.paint();
        assert.deepEqual(h.position(), stopped);
        assert.equal(h.frames.size, 0);
        assert.equal(h.header.captured, null);
        if (end !== 'close') h.close();
        h.reopen();
        assert.deepEqual(h.position(), [0, 0]);
        h.close();
    }
});

test('secondary pointers, right clicks and header buttons do not start a drag', () => {
    const h = setup();
    for (const extra of [{ button: 2 }, { isPrimary: false }]) {
        h.pointer(h.header, 'pointerdown', 0, 0, extra);
        h.pointer(h.document, 'pointerup', 100, 100);
        assert.deepEqual(h.position(), [0, 0]);
    }
    h.header.closest = () => h.header;
    h.pointer(h.header, 'pointerdown', 0, 0);
    h.pointer(h.document, 'pointerup', 100, 100);
    assert.deepEqual(h.position(), [0, 0]);
    h.close();
});
