import { useId, useState } from 'react';
import { Check, Eye, Palette } from 'lucide-react';
import { conferenceColorPalettes, type ConferenceColorPalette } from './conferenceColorPalettes';
import type { ConferenceLayoutOption } from './conferenceLayoutOptions';
import { ConferenceLayoutDiagram } from './ConferenceLayoutSelector';

export const ConferencePaletteSwatches = ({ palette }: { palette: ConferenceColorPalette }) => (
    <span className="inline-flex overflow-hidden rounded border border-slate-200 dark:border-slate-600" aria-hidden="true">
        {[palette.primary, palette.accent, palette.surface].map((color, index) => <span key={index} className={`h-4 w-6 ${color}`} />)}
    </span>
);

interface Props {
    value: ConferenceColorPalette;
    layout: ConferenceLayoutOption;
    eventName: string;
    onChange: (palette: ConferenceColorPalette) => void;
    onPreview: () => void;
}

const categories = ['전체', '봄 행사', '가을 행사', '공공기관', '의료학회'] as const;

export const ConferencePaletteSelector = ({ value, layout, eventName, onChange, onPreview }: Props) => {
    const id = useId();
    const [category, setCategory] = useState<typeof categories[number]>('전체');
    const palettes = conferenceColorPalettes.filter(palette => category === '전체' || palette.category === category);

    return <section className="mt-6 border-t border-slate-200 pt-6 dark:border-slate-800" aria-labelledby={`${id}-title`}>
        <h4 id={`${id}-title`} className="flex items-center gap-2 text-sm font-semibold"><Palette className="h-4 w-4 text-blue-600 dark:text-blue-400" aria-hidden="true" />2. 컬러 팔레트</h4>
        <p className="mt-2 text-xs leading-5 text-slate-500 dark:text-slate-400">행사의 계절이나 분위기에 맞는 색상 조합을 골라보세요. 모든 팔레트는 세 가지 레이아웃에 공통으로 사용할 수 있습니다.</p>
        <div role="group" aria-label="팔레트 추천 분류" className="my-4 flex flex-wrap gap-2">
            {categories.map(item => <button key={item} type="button" aria-pressed={category === item} onClick={() => setCategory(item)}
                className={`rounded-lg border px-3 py-1.5 text-xs font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-500 ${category === item
                    ? 'border-blue-600 bg-blue-50 text-blue-700 dark:border-blue-500 dark:bg-blue-950/50 dark:text-blue-300'
                    : 'border-slate-200 bg-white text-slate-500 hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-400 dark:hover:bg-slate-900'}`}>{item}</button>)}
        </div>
        <div className="grid items-start gap-5 lg:grid-cols-[minmax(0,1fr)_280px]">
            <fieldset>
                <legend className="sr-only">컬러 팔레트 선택</legend>
                <div className="grid grid-cols-2 gap-3 lg:grid-cols-1 xl:grid-cols-2">
                    {palettes.map(palette => {
                        const selected = value.key === palette.key;
                        return <label key={palette.key} className={`relative cursor-pointer rounded-lg border p-3 focus-within:ring-2 focus-within:ring-blue-500 focus-within:ring-offset-2 dark:focus-within:ring-offset-slate-950 ${selected
                            ? 'border-blue-500 bg-blue-50/40 ring-1 ring-blue-500 dark:border-blue-400 dark:bg-blue-950/25 dark:ring-blue-400'
                            : 'border-slate-200 bg-white hover:border-slate-400 dark:border-slate-800 dark:bg-slate-950 dark:hover:border-slate-600'}`}>
                            <input type="radio" name="conferenceCopyPalette" value={palette.key} checked={selected} onChange={() => onChange(palette)} aria-labelledby={`${id}-${palette.key}`} className="sr-only" />
                            <span className="flex items-center justify-between gap-2"><ConferencePaletteSwatches palette={palette} /><span className={`flex h-4 w-4 items-center justify-center rounded-full border ${selected ? 'border-blue-600 bg-blue-600 text-white dark:border-blue-500 dark:bg-blue-500 dark:text-white' : 'border-slate-300 dark:border-slate-600'}`}>{selected && <Check className="h-3 w-3" aria-hidden="true" />}</span></span>
                            <span id={`${id}-${palette.key}`} className="mt-3 block text-xs font-semibold">{palette.name}</span>
                            <span className="mt-1 block text-[11px] text-slate-500 dark:text-slate-400">{palette.category}{palette.key === 'navy' ? ' · 기본' : ''}</span>
                        </label>;
                    })}
                </div>
                <p className="mt-3 text-[11px] leading-5 text-slate-500 dark:text-slate-400">분류는 선택을 돕기 위한 추천입니다. 행사 종류와 관계없이 원하는 조합을 선택하세요.</p>
            </fieldset>
            <aside className="min-w-0 lg:sticky lg:top-5" aria-label="선택한 컬러 팔레트 미리보기">
                <div className="mb-3" aria-live="polite"><p className="text-[11px] font-semibold text-slate-500 dark:text-slate-400">선택한 조합</p><p className="mt-1 text-sm font-semibold">{value.name}</p><p className="mt-1 text-xs leading-5 text-slate-500 dark:text-slate-400">{value.description}</p></div>
                <ConferenceLayoutDiagram layout={layout} palette={value} eventName={eventName} compact />
                <p className="mt-2 text-[11px] text-slate-500 dark:text-slate-400">LAYOUT {layout.number} · {layout.name}</p>
                <dl className="mt-4 space-y-2 text-[11px]">
                    {[
                        ['메인', '메뉴 · 등록 버튼', value.primary],
                        ['강조', '바로가기 · 포인트', value.accent],
                        ['배경', '주요 콘텐츠 영역', value.surface]
                    ].map(([name, usage, color], index) => <div key={name} className="flex items-center gap-2">
                        <span aria-hidden="true" className={`h-5 w-5 shrink-0 rounded border border-slate-200 dark:border-slate-600 ${color}`} />
                        <dt className="font-semibold">{name}</dt><dd className="min-w-0 flex-1 text-slate-500 dark:text-slate-400">{usage}</dd><dd className="font-mono text-slate-500 dark:text-slate-400">{value.colors[index]}</dd>
                    </div>)}
                </dl>
                <button type="button" onClick={onPreview} className="mt-4 inline-flex w-full items-center justify-center gap-1.5 rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-900"><Eye className="h-3.5 w-3.5" aria-hidden="true" />이 조합 크게 보기</button>
                <p className="mt-2 text-[11px] leading-5 text-slate-400 dark:text-slate-500">레이아웃을 변경해도 선택한 팔레트는 유지됩니다.</p>
            </aside>
        </div>
    </section>;
};
