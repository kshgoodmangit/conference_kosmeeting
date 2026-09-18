import { useEffect, useRef } from 'react';

/** Use offset positioning for existing flex-centered modal layouts. */
export function useModalDrag<T extends HTMLElement = HTMLDivElement>(
    isOpen: boolean,
    positioning: 'centered' | 'offset' = 'centered'
) {
    const modalRef = useRef<T>(null);
    const headerRef = useRef<HTMLDivElement>(null);
    const positionRef = useRef({ x: 0, y: 0 });

    useEffect(() => {
        const modal = modalRef.current;
        const header = headerRef.current ?? modal?.querySelector<HTMLElement>('[data-modal-drag-handle]');
        if (!isOpen || !modal || !header) return;

        let frame: number | null = null;
        let pointerId: number | null = null;
        let lastPointer = { x: 0, y: 0 };
        let bounds = { minX: 0, maxX: 0, minY: 0, maxY: 0 };
        const previousTransform = modal.style.transform;
        const previousWillChange = modal.style.willChange;
        positionRef.current = { x: 0, y: 0 };

        const paint = () => {
            frame = null;
            const { x, y } = positionRef.current;
            modal.style.transform = positioning === 'centered'
                ? `translate(calc(-50% + ${x}px), calc(-50% + ${y}px))`
                : `translate(${x}px, ${y}px)`;
        };
        const flush = () => {
            if (frame !== null) cancelAnimationFrame(frame);
            paint();
        };
        const measureBounds = () => {
            const rect = modal.getBoundingClientRect();
            const baseX = rect.left - positionRef.current.x;
            const baseY = rect.top - positionRef.current.y;
            const width = document.documentElement.clientWidth;
            const height = document.documentElement.clientHeight;
            const marginX = Math.min(8, Math.max(0, (width - rect.width) / 2));
            const marginY = Math.min(8, Math.max(0, (height - rect.height) / 2));
            bounds = {
                minX: marginX - baseX,
                maxX: Math.max(marginX, width - rect.width - marginX) - baseX,
                minY: marginY - baseY,
                maxY: Math.max(marginY, height - rect.height - marginY) - baseY
            };
        };
        const clamp = (x: number, y: number) => {
            positionRef.current = {
                x: Math.min(bounds.maxX, Math.max(bounds.minX, x)),
                y: Math.min(bounds.maxY, Math.max(bounds.minY, y))
            };
        };
        const move = (event: PointerEvent) => {
            if (event.pointerId !== pointerId) return;
            // Update the latest coordinates synchronously, independently of React renders.
            clamp(
                positionRef.current.x + event.clientX - lastPointer.x,
                positionRef.current.y + event.clientY - lastPointer.y
            );
            lastPointer = { x: event.clientX, y: event.clientY };
            if (frame === null) frame = requestAnimationFrame(paint);
        };
        const stop = () => {
            const capturedPointer = pointerId;
            pointerId = null;
            document.removeEventListener('pointermove', move);
            document.removeEventListener('pointerup', finish);
            document.removeEventListener('pointercancel', cancel);
            flush();
            modal.style.willChange = previousWillChange;
            if (capturedPointer !== null && header.hasPointerCapture(capturedPointer)) {
                header.releasePointerCapture(capturedPointer);
            }
        };
        const finish = (event: PointerEvent) => {
            if (event.pointerId !== pointerId) return;
            move(event);
            stop();
        };
        const cancel = (event: PointerEvent) => {
            if (event.pointerId === pointerId) stop();
        };
        const start = (event: PointerEvent) => {
            if (event.button !== 0 || !event.isPrimary || pointerId !== null) return;
            if (event.target instanceof Element && event.target.closest('button, input, select, textarea, a')) return;
            event.preventDefault();
            flush();
            measureBounds();
            pointerId = event.pointerId;
            lastPointer = { x: event.clientX, y: event.clientY };
            modal.style.willChange = 'transform';
            header.setPointerCapture(pointerId);
            document.addEventListener('pointermove', move);
            document.addEventListener('pointerup', finish);
            document.addEventListener('pointercancel', cancel);
        };
        const resize = () => {
            stop();
            measureBounds();
            clamp(positionRef.current.x, positionRef.current.y);
            paint();
        };
        const visibilityChange = () => {
            if (document.hidden) stop();
        };

        // Re-clamp after async content (e.g. import results) changes the modal size.
        const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(resize);
        observer?.observe(modal);
        paint();
        header.addEventListener('pointerdown', start);
        header.addEventListener('lostpointercapture', cancel);
        window.addEventListener('blur', stop);
        window.addEventListener('resize', resize);
        document.addEventListener('visibilitychange', visibilityChange);
        return () => {
            observer?.disconnect();
            stop();
            header.removeEventListener('pointerdown', start);
            header.removeEventListener('lostpointercapture', cancel);
            window.removeEventListener('blur', stop);
            window.removeEventListener('resize', resize);
            document.removeEventListener('visibilitychange', visibilityChange);
            modal.style.transform = previousTransform;
        };
    }, [isOpen, positioning]);

    return { modalRef, headerRef };
}
