import { useCallback, useEffect, useId, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Minimize2, Users } from 'lucide-react';
import type { ShowcaseDashboardPalette } from './showcaseDashboardPalettes';
import landGeoJson from './showcase-land.geojson?raw';
import countryBoundariesGeoJson from './showcase-country-boundaries.geojson?raw';

// Natural Earth 1:110m land, public domain. Kept locally for offline presentation.
// https://www.naturalearthdata.com/about/terms-of-use/
type Position = [number, number];
interface LandGeometry {
    type: 'Polygon' | 'MultiPolygon';
    coordinates: Position[][] | Position[][][];
}
const LAND = JSON.parse(landGeoJson) as { features: { geometry: LandGeometry }[] };
const SIZE = 600;
const CENTER = SIZE / 2;
const RADIUS = 246;
const MAP_WIDTH = 2048;
const MAP_HEIGHT = 1024;
const RAD = Math.PI / 180;
type BoundaryGeometry = { type: 'LineString'; coordinates: Position[] }
    | { type: 'MultiLineString'; coordinates: Position[][] };
const BOUNDARIES = JSON.parse(countryBoundariesGeoJson) as { geometries: BoundaryGeometry[] };
// Subdivide before projection so the horizon never joins distant visible vertices.
const BOUNDARY_LINES = BOUNDARIES.geometries.flatMap(geometry =>
    geometry.type === 'LineString' ? [geometry.coordinates] : geometry.coordinates
).map(line => line.flatMap((point, index): Position[] => {
    if (index === 0) return [point];
    const previous = line[index - 1];
    const longitudeDelta = ((point[0] - previous[0] + 540) % 360) - 180;
    const latitudeDelta = point[1] - previous[1];
    const steps = Math.max(1, Math.ceil(Math.max(Math.abs(longitudeDelta), Math.abs(latitudeDelta))));
    return Array.from({ length: steps }, (_, step): Position => [
        previous[0] + longitudeDelta * (step + 1) / steps,
        previous[1] + latitudeDelta * (step + 1) / steps
    ]);
}));

// Great-circle interpolation follows the sphere, including across the date line.
const createConnection = (from: Position, to: Position): Position[] => {
    const vector = ([lon, lat]: Position) => [Math.cos(lat * RAD) * Math.cos(lon * RAD),
        Math.cos(lat * RAD) * Math.sin(lon * RAD), Math.sin(lat * RAD)];
    const a = vector(from);
    const b = vector(to);
    const angle = Math.acos(Math.max(-1, Math.min(1, a.reduce((sum, value, i) => sum + value * b[i], 0))));
    if (angle < 0.00001 || Math.PI - angle < 0.00001) return [];
    return Array.from({ length: 121 }, (_, index): Position => {
        const t = index / 120;
        const start = Math.sin((1 - t) * angle) / Math.sin(angle);
        const end = Math.sin(t * angle) / Math.sin(angle);
        const [x, y, z] = a.map((value, i) => value * start + b[i] * end);
        return [Math.atan2(y, x) / RAD, Math.atan2(z, Math.hypot(x, y)) / RAD];
    });
};
interface GlobeCountry {
    code: string;
    name: string;
    value: number;
    longitude: number;
    latitude: number;
}
interface CountryTooltip {
    country: GlobeCountry;
    x: number;
    y: number;
    alignLeft: boolean;
}

let landMask: Uint8ClampedArray | undefined;
function getLandMask() {
    if (landMask) return landMask;
    const map = document.createElement('canvas');
    map.width = MAP_WIDTH;
    map.height = MAP_HEIGHT;
    const context = map.getContext('2d');
    if (!context) return undefined;
    context.fillStyle = '#fff';
    for (const { geometry } of LAND.features) {
        const polygons = geometry.type === 'Polygon'
            ? [geometry.coordinates as Position[][]]
            : geometry.coordinates as Position[][][];
        for (const polygon of polygons) {
            context.beginPath();
            for (const ring of polygon) {
                ring.forEach(([longitude, latitude], index) => {
                    const x = (longitude + 180) / 360 * MAP_WIDTH;
                    const y = (90 - latitude) / 180 * MAP_HEIGHT;
                    if (index === 0) context.moveTo(x, y);
                    else context.lineTo(x, y);
                });
                context.closePath();
            }
            context.fill('evenodd');
        }
    }
    landMask = context.getImageData(0, 0, MAP_WIDTH, MAP_HEIGHT).data;
    return landMask;
}

const rgb = (hex: string) => [1, 3, 5].map(start => parseInt(hex.slice(start, start + 2), 16));

export const ShowcaseGlobe = ({ compact, palette, countries }: { compact: boolean; palette: ShowcaseDashboardPalette; countries: readonly GlobeCountry[] }) => {
    const anchorRef = useRef<HTMLDivElement>(null);
    const sceneRef = useRef<HTMLDivElement>(null);
    const canvasRef = useRef<HTMLCanvasElement>(null);
    const detailCanvasRef = useRef<HTMLCanvasElement>(null);
    const zoomRef = useRef(0);
    const orientationRef = useRef({ longitude: 112, latitude: 18 });
    const [expansion, setExpansion] = useState(0);
    const [dragging, setDragging] = useState(false);
    const [tooltip, setTooltip] = useState<CountryTooltip | null>(null);
    const hoveredCountryRef = useRef<string | null>(null);
    const tooltipId = useId();
    const rankingTitleId = useId();
    const clearTooltip = useCallback(() => {
        hoveredCountryRef.current = null;
        setTooltip(null);
    }, []);
    const [anchor, setAnchor] = useState({ left: 0, top: 0, size: 176, width: 0, height: 0, visible: false });

    useLayoutEffect(() => {
        const element = anchorRef.current;
        if (!element) return;
        const measure = () => {
            const rect = element.getBoundingClientRect();
            setAnchor({
                left: rect.left, top: rect.top, size: rect.width,
                width: window.innerWidth, height: window.innerHeight,
                visible: rect.bottom > 0 && rect.top < window.innerHeight && rect.right > 0 && rect.left < window.innerWidth
            });
        };
        measure();
        const observer = new ResizeObserver(measure);
        observer.observe(element);
        window.addEventListener('resize', measure);
        window.addEventListener('scroll', measure, true);
        return () => {
            observer.disconnect();
            window.removeEventListener('resize', measure);
            window.removeEventListener('scroll', measure, true);
        };
    }, [compact]);

    const returnToCard = () => {
        clearTooltip();
        zoomRef.current = 0;
        setExpansion(0);
        canvasRef.current?.focus({ preventScroll: true });
    };

    useEffect(() => {
        const canvas = canvasRef.current;
        const scene = sceneRef.current;
        const surfaceContext = canvas?.getContext('2d');
        const detailCanvas = detailCanvasRef.current;
        const context = detailCanvas?.getContext('2d');
        const mask = getLandMask();
        if (!canvas || !scene || !surfaceContext || !detailCanvas || !context || !mask) return;

        const destination = countries.find(country => country.code === 'KR');
        const connections = destination ? countries.filter(country => country.code !== 'KR').map(country =>
            createConnection([country.longitude, country.latitude], [destination.longitude, destination.latitude])
        ).filter(points => points.length > 0) : [];
        let displaySize = canvas.clientWidth || SIZE;

        const { primary, primaryLight, globeEnd, canvas: oceanBackground } = palette.chart;
        const landColor = rgb(primaryLight);
        const landAccent = rgb(primary);
        const oceanColor = rgb(globeEnd);
        const background = rgb(oceanBackground);
        const frame = context.createImageData(SIZE, SIZE);
        const pixels: { offset: number; longitude: number; row: number; light: number; edge: number }[] = [];
        let cosTilt = Math.cos(orientationRef.current.latitude * RAD);
        let sinTilt = Math.sin(orientationRef.current.latitude * RAD);
        const radius = RADIUS;

        // Vertical dragging changes the viewing latitude. Horizontal rotation reuses the projection.
        const updateProjection = () => {
            cosTilt = Math.cos(orientationRef.current.latitude * RAD);
            sinTilt = Math.sin(orientationRef.current.latitude * RAD);
            pixels.length = 0;
            frame.data.fill(0);
            for (let y = 0; y < SIZE; y++) {
                for (let x = 0; x < SIZE; x++) {
                    const nx = (x + 0.5 - CENTER) / radius;
                    const ny = -(y + 0.5 - CENTER) / radius;
                    const radiusSquared = nx * nx + ny * ny;
                    if (radiusSquared > 1) continue;
                    const nz = Math.sqrt(1 - radiusSquared);
                    const latitude = Math.asin(ny * cosTilt + nz * sinTilt);
                    const longitude = Math.atan2(nx, nz * cosTilt - ny * sinTilt);
                    const light = 0.34 + 0.66 * Math.max(0, -nx * 0.45 + ny * 0.35 + nz * 0.82);
                    pixels.push({
                        offset: (y * SIZE + x) * 4,
                        longitude: (longitude / (2 * Math.PI) + 0.5) * MAP_WIDTH,
                        row: Math.min(MAP_HEIGHT - 1, Math.floor((0.5 - latitude / Math.PI) * MAP_HEIGHT)),
                        light,
                        edge: Math.min(1, (1 - Math.sqrt(radiusSquared)) * radius)
                    });
                }
            }
        };
        updateProjection();

        const project = (longitude: number, latitude: number, rotation: number) => {
            const lon = longitude * RAD - rotation;
            const lat = latitude * RAD;
            const x = Math.cos(lat) * Math.sin(lon);
            const y = Math.sin(lat) * cosTilt - Math.cos(lat) * Math.cos(lon) * sinTilt;
            const z = Math.sin(lat) * sinTilt + Math.cos(lat) * Math.cos(lon) * cosTilt;
            return { x: CENTER + radius * x, y: CENTER - radius * y, visible: z > 0.015 };
        };

        const draw = (elapsed: number) => {
            const rotation = orientationRef.current.longitude * RAD;
            const shift = rotation / (2 * Math.PI) * MAP_WIDTH;
            for (const pixel of pixels) {
                const column = ((Math.floor(pixel.longitude + shift) % MAP_WIDTH) + MAP_WIDTH) % MAP_WIDTH;
                const land = mask[(pixel.row * MAP_WIDTH + column) * 4 + 3] / 255;
                for (let channel = 0; channel < 3; channel++) {
                    const sea = (oceanColor[channel] * 0.48 + background[channel] * 0.52) * pixel.light;
                    const terrain = (landColor[channel] * 0.72 + landAccent[channel] * 0.28) * pixel.light;
                    frame.data[pixel.offset + channel] = sea * (1 - land) + terrain * land;
                }
                frame.data[pixel.offset + 3] = pixel.edge * 255;
            }
            surfaceContext.clearRect(0, 0, SIZE, SIZE);
            surfaceContext.putImageData(frame, 0, 0);
            context.clearRect(0, 0, SIZE, SIZE);
            const detail = Math.max(0, Math.min(1, (displaySize - 220) / 340));
            const screenPixel = SIZE / displaySize;

            context.strokeStyle = primaryLight;
            context.lineWidth = 0.7;
            context.globalAlpha = 0.14;
            const gridLine = (points: Position[]) => {
                context.beginPath();
                let connected = false;
                for (const [lon, lat] of points) {
                    const point = project(lon, lat, rotation);
                    if (point.visible) {
                        if (connected) context.lineTo(point.x, point.y);
                        else context.moveTo(point.x, point.y);
                    }
                    connected = point.visible;
                }
                context.stroke();
            };
            for (let latitude = -60; latitude <= 60; latitude += 30) {
                gridLine(Array.from({ length: 181 }, (_, i) => [i * 2 - 180, latitude]));
            }
            for (let longitude = -180; longitude < 180; longitude += 30) {
                gridLine(Array.from({ length: 91 }, (_, i) => [longitude, i * 2 - 90]));
            }

            context.save();
            context.lineJoin = 'round';
            context.lineCap = 'round';
            // Dark borders contrast with the luminous land across all dashboard palettes.
            context.strokeStyle = oceanBackground;
            context.globalAlpha = 0.24 + detail * 0.42;
            context.lineWidth = (0.45 + detail * 0.35) * screenPixel;
            BOUNDARY_LINES.forEach(gridLine);

            if (zoomRef.current > 0 && detail > 0) {
                connections.forEach((points, index) => {
                    context.strokeStyle = primaryLight;
                    context.lineWidth = 1.1 * screenPixel;
                    context.globalAlpha = detail * 0.65;
                    context.setLineDash([5 * screenPixel, 7 * screenPixel]);
                    context.lineDashOffset = -(elapsed / 90 + index * 4) * screenPixel;
                    gridLine(points);
                    context.setLineDash([]);
                    if (!reducedMotion.matches) {
                        const progress = (elapsed / (3600 + index * 650) + index * 0.23) % 1;
                        const [lon, lat] = points[Math.floor(progress * (points.length - 1))];
                        const signal = project(lon, lat, rotation);
                        if (signal.visible) {
                            context.globalAlpha = detail;
                            context.fillStyle = '#fff';
                            context.shadowColor = primary;
                            context.shadowBlur = 8;
                            context.beginPath();
                            context.arc(signal.x, signal.y, 2.2 * screenPixel, 0, Math.PI * 2);
                            context.fill();
                            context.shadowBlur = 0;
                        }
                    }
                });
            }
            context.restore();

            context.globalAlpha = 1;
            const atmosphere = context.createRadialGradient(CENTER, CENTER, radius - 3, CENTER, CENTER, radius + 13);
            atmosphere.addColorStop(0, 'transparent');
            atmosphere.addColorStop(0.28, `${primary}65`);
            atmosphere.addColorStop(1, 'transparent');
            context.fillStyle = atmosphere;
            context.beginPath();
            context.arc(CENTER, CENTER, radius + 13, 0, Math.PI * 2);
            context.fill();
            context.strokeStyle = `${primaryLight}80`;
            context.lineWidth = 1.2;
            context.beginPath();
            context.arc(CENTER, CENTER, radius, 0, Math.PI * 2);
            context.stroke();

            countries.forEach((country, index) => {
                const point = project(country.longitude, country.latitude, rotation);
                if (!point.visible) return;
                const pulse = (elapsed / 2400 + index * 0.2) % 1;
                context.strokeStyle = primaryLight;
                context.globalAlpha = (1 - pulse) * 0.65;
                context.lineWidth = 1.5;
                context.beginPath();
                context.arc(point.x, point.y, 4 + pulse * 11, 0, Math.PI * 2);
                context.stroke();
                context.globalAlpha = 1;
                if (hoveredCountryRef.current === country.code) {
                    context.strokeStyle = '#fff';
                    context.beginPath();
                    context.arc(point.x, point.y, 7, 0, Math.PI * 2);
                    context.stroke();
                }
                context.fillStyle = '#fff';
                context.shadowColor = primary;
                context.shadowBlur = 12;
                context.beginPath();
                context.arc(point.x, point.y, 3, 0, Math.PI * 2);
                context.fill();
                context.shadowBlur = 0;
            });
        };

        const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
        let animationId = 0;
        let visible = false;
        let elapsed = 0;
        let lastFrame = 0;
        let interactionFrameId = 0;
        let drag: { pointerId: number; x: number; y: number } | undefined;
        let projectionDirty = false;
        const setZoom = (value: number) => {
            const next = Math.min(1, Math.max(0, value));
            if (next === zoomRef.current) return;
            clearTooltip();
            zoomRef.current = next;
            setExpansion(next);
            requestInteractionDraw();
        };
        const handleWheel = (event: WheelEvent) => {
            // Scrolling the country list must not resize the globe.
            if (event.target !== canvas) return;
            if (event.ctrlKey || event.deltaY === 0) return;
            event.preventDefault();
            event.stopPropagation();
            canvas.focus({ preventScroll: true });
            const unit = event.deltaMode === WheelEvent.DOM_DELTA_LINE ? 16
                : event.deltaMode === WheelEvent.DOM_DELTA_PAGE ? canvas.clientHeight : 1;
            const delta = Math.max(-160, Math.min(160, event.deltaY * unit));
            setZoom(zoomRef.current - delta * 0.0015);
        };
        const resetZoom = () => {
            setZoom(0);
            canvas.focus({ preventScroll: true });
        };
        const maximizeGlobe = (event: MouseEvent) => {
            event.preventDefault();
            setZoom(1);
            canvas.focus({ preventScroll: true });
        };
        const handleKeyDown = (event: KeyboardEvent) => {
            if (event.ctrlKey || event.metaKey || event.altKey) return;
            if (event.key === 'Escape' && zoomRef.current > 0) {
                event.preventDefault();
                event.stopPropagation();
                resetZoom();
                return;
            }
            if (event.target !== canvas) return;
            if (!['+', '=', '-', '0', 'ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(event.key)) return;
            event.preventDefault();
            if (event.key === '0') resetZoom();
            else if (event.key.startsWith('Arrow')) {
                clearTooltip();
                orientationRef.current.longitude += event.key === 'ArrowLeft' ? 8 : event.key === 'ArrowRight' ? -8 : 0;
                orientationRef.current.latitude = Math.max(-80, Math.min(80,
                    orientationRef.current.latitude + (event.key === 'ArrowUp' ? -8 : event.key === 'ArrowDown' ? 8 : 0)));
                projectionDirty = true;
                requestInteractionDraw();
            } else setZoom(zoomRef.current + (event.key === '-' ? -0.12 : 0.12));
        };
        const requestInteractionDraw = () => {
            window.cancelAnimationFrame(interactionFrameId);
            interactionFrameId = window.requestAnimationFrame(() => {
                if (projectionDirty) {
                    updateProjection();
                    projectionDirty = false;
                }
                draw(elapsed);
            });
        };
        const handlePointerDown = (event: PointerEvent) => {
            if (event.button !== 0 || drag) return;
            clearTooltip();
            event.preventDefault();
            canvas.focus({ preventScroll: true });
            canvas.setPointerCapture(event.pointerId);
            drag = { pointerId: event.pointerId, x: event.clientX, y: event.clientY };
            setDragging(true);
        };
        const handlePointerMove = (event: PointerEvent) => {
            if (!drag) {
                if (zoomRef.current === 0) return;
                const rect = canvas.getBoundingClientRect();
                const scale = rect.width / SIZE;
                const rotation = orientationRef.current.longitude * RAD;
                let nearest: CountryTooltip | null = null;
                let nearestDistance = Math.max(12, 9 * scale);
                for (const country of countries) {
                    const point = project(country.longitude, country.latitude, rotation);
                    if (!point.visible) continue;
                    const markerX = rect.left + point.x * scale;
                    const markerY = rect.top + point.y * scale;
                    const distance = Math.hypot(event.clientX - markerX, event.clientY - markerY);
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearest = { country, x: point.x, y: point.y, alignLeft: markerX + 196 > window.innerWidth };
                    }
                }
                hoveredCountryRef.current = nearest?.country.code ?? null;
                setTooltip(previous => previous?.country.code === nearest?.country.code
                    && previous?.x === nearest?.x && previous?.y === nearest?.y
                    && previous?.alignLeft === nearest?.alignLeft ? previous : nearest);
                requestInteractionDraw();
                return;
            }
            if (event.pointerId !== drag.pointerId) return;
            const degreesPerPixel = 180 / canvas.getBoundingClientRect().width;
            orientationRef.current.longitude -= (event.clientX - drag.x) * degreesPerPixel;
            const latitude = Math.max(-80, Math.min(80,
                orientationRef.current.latitude + (event.clientY - drag.y) * degreesPerPixel));
            projectionDirty ||= latitude !== orientationRef.current.latitude;
            orientationRef.current.latitude = latitude;
            drag.x = event.clientX;
            drag.y = event.clientY;
            requestInteractionDraw();
        };
        const endDrag = (event: PointerEvent) => {
            if (!drag || event.pointerId !== drag.pointerId) return;
            drag = undefined;
            if (canvas.hasPointerCapture(event.pointerId)) canvas.releasePointerCapture(event.pointerId);
            setDragging(false);
        };
        const animate = (time: number) => {
            if (time - lastFrame >= 1000 / 24) {
                const delta = lastFrame ? time - lastFrame : 0;
                elapsed += delta;
                // Hold the globe still while inspecting it in the center or dragging it.
                if (!drag && zoomRef.current === 0) orientationRef.current.longitude += delta * 0.003;
                lastFrame = time;
                if (projectionDirty) {
                    updateProjection();
                    projectionDirty = false;
                }
                draw(elapsed);
            }
            animationId = window.requestAnimationFrame(animate);
        };
        const updateAnimation = () => {
            window.cancelAnimationFrame(animationId);
            lastFrame = 0;
            if (visible && !document.hidden && !reducedMotion.matches) {
                animationId = window.requestAnimationFrame(animate);
            } else {
                draw(elapsed);
            }
        };
        const observer = new IntersectionObserver(([entry]) => {
            visible = entry.isIntersecting;
            updateAnimation();
        });
        // Keep vector details crisp when enlarged without multiplying the terrain pixel loop.
        const detailObserver = new ResizeObserver(([entry]) => {
            displaySize = Math.max(1, entry.contentRect.width);
            const resolution = Math.min(2160, Math.ceil(displaySize * Math.min(window.devicePixelRatio || 1, 2)));
            if (detailCanvas.width !== resolution) {
                detailCanvas.width = resolution;
                detailCanvas.height = resolution;
                context.setTransform(resolution / SIZE, 0, 0, resolution / SIZE, 0, 0);
            }
            requestInteractionDraw();
        });
        detailObserver.observe(canvas);
        draw(0);
        observer.observe(canvas);
        scene.addEventListener('wheel', handleWheel, { passive: false });
        canvas.addEventListener('dblclick', maximizeGlobe);
        scene.addEventListener('keydown', handleKeyDown);
        canvas.addEventListener('pointerdown', handlePointerDown);
        canvas.addEventListener('pointermove', handlePointerMove);
        canvas.addEventListener('pointerleave', clearTooltip);
        canvas.addEventListener('blur', clearTooltip);
        canvas.addEventListener('pointerup', endDrag);
        canvas.addEventListener('pointercancel', endDrag);
        canvas.addEventListener('lostpointercapture', endDrag);
        reducedMotion.addEventListener('change', updateAnimation);
        document.addEventListener('visibilitychange', updateAnimation);
        return () => {
            window.cancelAnimationFrame(animationId);
            window.cancelAnimationFrame(interactionFrameId);
            observer.disconnect();
            detailObserver.disconnect();
            scene.removeEventListener('wheel', handleWheel);
            canvas.removeEventListener('dblclick', maximizeGlobe);
            scene.removeEventListener('keydown', handleKeyDown);
            canvas.removeEventListener('pointerdown', handlePointerDown);
            canvas.removeEventListener('pointermove', handlePointerMove);
            canvas.removeEventListener('pointerleave', clearTooltip);
            canvas.removeEventListener('blur', clearTooltip);
            canvas.removeEventListener('pointerup', endDrag);
            canvas.removeEventListener('pointercancel', endDrag);
            canvas.removeEventListener('lostpointercapture', endDrag);
            reducedMotion.removeEventListener('change', updateAnimation);
            document.removeEventListener('visibilitychange', updateAnimation);
        };
    }, [palette, countries, clearTooltip]);

    const sideBySide = anchor.width >= 900;
    const rankingWidth = sideBySide ? Math.min(380, anchor.width * 0.29) : Math.max(0, anchor.width - 32);
    const enlargedSize = Math.max(1, sideBySide
        ? Math.min(anchor.width - rankingWidth - 72, anchor.height - 100, 1080)
        : Math.min(anchor.width - 32, anchor.height * 0.42));
    const groupLeft = sideBySide ? (anchor.width - enlargedSize - rankingWidth - 24) / 2 : (anchor.width - enlargedSize) / 2;
    const enlargedTop = sideBySide ? (anchor.height - enlargedSize) / 2 - 12 : 24;
    const size = anchor.size + (enlargedSize - anchor.size) * expansion;
    const left = anchor.left + (groupLeft - anchor.left) * expansion;
    const top = anchor.top + (enlargedTop - anchor.top) * expansion;
    const rankingHeight = sideBySide ? Math.min(740, anchor.height - 48) : Math.max(100, anchor.height - enlargedSize - 92);
    const topCountries = [...countries].sort((a, b) => b.value - a.value || a.code.localeCompare(b.code)).slice(0, 20);
    const participantTotal = countries.reduce((sum, country) => sum + country.value, 0);
    const description = `더블클릭하면 참여국가 목록과 함께 최대화합니다. 휠로 크기 조절 (${Math.round(size / anchor.size * 100)}%). 드래그 또는 방향키로 회전. 0·Esc로 원래 위치로 돌아갑니다.`;

    return <>
        <div ref={anchorRef} className={`relative mx-auto aspect-square w-full ${compact ? 'max-h-full max-w-44' : 'max-w-48'}`} />
        {createPortal(
            <div ref={sceneRef} className="pointer-events-none fixed inset-0 z-[120]" style={{ visibility: anchor.visible || expansion > 0 ? 'visible' : 'hidden' }}>
                <button
                    type="button"
                    tabIndex={-1}
                    aria-hidden="true"
                    onClick={returnToCard}
                    className={`absolute inset-0 bg-slate-950/80 transition-opacity duration-500 motion-reduce:transition-none dark:bg-slate-950/80 ${expansion > 0 ? 'pointer-events-auto' : 'pointer-events-none'}`}
                    style={{ opacity: expansion * 0.85 }}
                />
                <div
                    className="absolute transition-[left,top,width,height] duration-500 ease-out motion-reduce:transition-none"
                    style={{ left, top, width: size, height: size }}
                >
                    <div className={`pointer-events-none absolute inset-[10%] rounded-full ${palette.globeHaloClass} blur-xl`} />
                    <canvas
                        ref={canvasRef}
                        width={SIZE}
                        height={SIZE}
                        className={`pointer-events-auto relative h-full w-full touch-none select-none rounded-full outline-none focus-visible:ring-2 focus-visible:ring-cyan-300 dark:focus-visible:ring-cyan-300 ${dragging ? 'cursor-grabbing' : 'cursor-grab'}`}
                        tabIndex={0}
                        role="img"
                        aria-label="국가 경계와 참가국 위치가 표시된 지구본. 확대하면 한국으로 향하는 연결선이 표시됩니다."
                        aria-description={description}
                        aria-describedby={tooltip ? tooltipId : undefined}
                        title={tooltip ? undefined : description}
                    />
                    <canvas ref={detailCanvasRef} width={SIZE} height={SIZE} aria-hidden="true"
                        className="pointer-events-none absolute inset-0 h-full w-full" />
                    {tooltip && expansion > 0 && !dragging && (
                        <div
                            id={tooltipId}
                            role="tooltip"
                            className={`pointer-events-none absolute z-10 w-44 -translate-y-1/2 rounded-xl border border-slate-600 bg-slate-950/95 px-4 py-3 text-white shadow-xl backdrop-blur-md dark:border-slate-600 dark:bg-slate-950/95 dark:text-white ${tooltip.alignLeft ? '-translate-x-full -ml-4' : 'ml-4'}`}
                            style={{ left: `${tooltip.x / SIZE * 100}%`, top: `${tooltip.y / SIZE * 100}%` }}
                        >
                            <div className="flex items-center justify-between gap-3">
                                <p className="text-sm font-semibold">{tooltip.country.name}</p>
                                <span className="text-[10px] font-semibold text-slate-400 dark:text-slate-400">{tooltip.country.code}</span>
                            </div>
                            <p className="mt-2 text-[11px] text-slate-400 dark:text-slate-400">참가 인원</p>
                            <p className={`mt-0.5 text-xl font-bold tabular-nums ${palette.primaryTextClass}`}>{tooltip.country.value.toLocaleString('ko-KR')}<span className="ml-1 text-xs font-medium">명</span></p>
                        </div>
                    )}
                    <div className={`absolute bottom-0 left-1/2 -translate-x-1/2 whitespace-nowrap rounded-full border bg-slate-950/65 px-3 py-1 text-[9px] font-bold tracking-wider backdrop-blur-md dark:bg-slate-950/65 ${palette.globeBadgeClass}`}>{countries.length} COUNTRIES CONNECTED</div>
                    {expansion > 0 && <>
                        <button
                            type="button"
                            onClick={returnToCard}
                            aria-label="지구본 원래 위치로 복원"
                            className="pointer-events-auto absolute right-0 top-0 rounded-full border border-white/20 bg-slate-900/80 p-2 text-white hover:bg-slate-800 dark:border-white/20 dark:bg-slate-900/80 dark:text-white dark:hover:bg-slate-800"
                        ><Minimize2 size={16} /></button>
                        <p className="absolute -bottom-9 left-1/2 w-full -translate-x-1/2 text-center text-[11px] text-slate-300 dark:text-slate-300">드래그로 회전 · 국가 표시로 인원 확인 · Esc로 돌아가기</p>
                    </>}
                </div>
                {expansion > 0 && (
                    <section
                        aria-labelledby={rankingTitleId}
                        className="pointer-events-auto absolute flex min-h-0 flex-col overflow-hidden rounded-2xl border border-slate-700/80 bg-slate-950/95 text-slate-100 shadow-2xl backdrop-blur-xl dark:border-slate-700/80 dark:bg-slate-950/95 dark:text-slate-100"
                        style={{
                            left: sideBySide ? groupLeft + enlargedSize + 24 : 16,
                            top: sideBySide ? (anchor.height - rankingHeight) / 2 : enlargedTop + enlargedSize + 52,
                            width: rankingWidth, height: rankingHeight,
                            opacity: Math.min(1, expansion * 4)
                        }}
                    >
                        <div className="shrink-0 border-b border-slate-800 px-4 py-3 dark:border-slate-800">
                            <div className="flex items-center justify-between gap-2">
                                <h2 id={rankingTitleId} className="flex items-center gap-2 text-sm font-semibold md:text-base"><Users className={`h-4 w-4 ${palette.primaryTextClass}`} />참여국가 TOP {topCountries.length}</h2>
                                <span className={`rounded-full border px-2 py-1 text-[10px] font-semibold ${palette.globeBadgeClass}`}>{countries.length}개국</span>
                            </div>
                            <p className="mt-2 text-xs text-slate-400 dark:text-slate-400">전체 {participantTotal.toLocaleString('ko-KR')}명 · 참가 인원순</p>
                        </div>
                        <ol aria-label="참가 인원 상위 국가" tabIndex={0} className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-4 py-2 outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-cyan-300 dark:focus-visible:ring-cyan-300">
                            {topCountries.map((country, index) => (
                                <li key={country.code} className="flex min-h-7 items-center gap-2 border-b border-slate-800/60 py-1.5 text-xs last:border-0 dark:border-slate-800/60">
                                    <span className={`w-5 shrink-0 text-center text-[11px] font-bold tabular-nums ${index < 3 ? palette.primaryTextClass : 'text-slate-500 dark:text-slate-500'}`}>{index + 1}</span>
                                    <span className="w-6 shrink-0 text-[10px] text-slate-500 dark:text-slate-500">{country.code}</span>
                                    <span className="min-w-0 flex-1 break-words font-medium">{country.name}</span>
                                    <span className="shrink-0 font-semibold tabular-nums text-white dark:text-white">{country.value.toLocaleString('ko-KR')}<span className="ml-1 text-[10px] font-normal text-slate-400 dark:text-slate-400">명</span></span>
                                </li>
                            ))}
                        </ol>
                        <p className="shrink-0 border-t border-slate-800 px-4 py-2 text-[10px] text-slate-500 dark:border-slate-800 dark:text-slate-500">지구본에는 전체 참여국가를 표시합니다.</p>
                    </section>
                )}
            </div>,
            document.body
        )}
    </>;
};
