import { memo, useEffect, useMemo, useRef, useState } from 'react';

interface ShowcaseAnimatedNumberProps {
    value: number;
    decimals?: number;
    delay?: number;
}

export const ShowcaseAnimatedNumber = memo(({ value, decimals = 0, delay = 0 }: ShowcaseAnimatedNumberProps) => {
    const elementRef = useRef<HTMLSpanElement>(null);
    const [displayValue, setDisplayValue] = useState(() =>
        window.matchMedia('(prefers-reduced-motion: reduce)').matches ? value : 0
    );
    const formatter = useMemo(() => new Intl.NumberFormat('ko-KR', {
        minimumFractionDigits: decimals,
        maximumFractionDigits: decimals
    }), [decimals]);
    const finalText = formatter.format(value);

    useEffect(() => {
        const element = elementRef.current;
        if (!element) return;

        const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
        let frameId = 0;
        let started = false;
        const finish = () => {
            window.cancelAnimationFrame(frameId);
            setDisplayValue(value);
        };
        const start = () => {
            if (started) return;
            started = true;
            if (reducedMotion.matches) {
                finish();
                return;
            }
            const startedAt = performance.now() + delay;
            const tick = (now: number) => {
                const progress = Math.min(1, Math.max(0, (now - startedAt) / 1_200));
                setDisplayValue(value * (1 - (1 - progress) ** 3));
                if (progress < 1) frameId = window.requestAnimationFrame(tick);
            };
            frameId = window.requestAnimationFrame(tick);
        };
        const observer = new IntersectionObserver((entries) => {
            if (entries.some((entry) => entry.isIntersecting)) {
                start();
                observer.disconnect();
            }
        }, { threshold: 0.25 });
        const handleMotionChange = () => {
            if (reducedMotion.matches) finish();
        };
        observer.observe(element);
        reducedMotion.addEventListener('change', handleMotionChange);
        return () => {
            observer.disconnect();
            window.cancelAnimationFrame(frameId);
            reducedMotion.removeEventListener('change', handleMotionChange);
        };
    }, [value, delay]);

    return (
        <span ref={elementRef} className="inline-grid tabular-nums">
            {/* 최종 값의 폭을 확보하고, 읽기 도구에는 변하지 않는 값을 제공합니다. */}
            <span className="invisible col-start-1 row-start-1" aria-hidden="true">{finalText}</span>
            <span className="col-start-1 row-start-1 text-right" aria-hidden="true">{formatter.format(displayValue)}</span>
            <span className="sr-only">{finalText}</span>
        </span>
    );
});
