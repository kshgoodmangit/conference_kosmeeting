import { Check, Eye, Layout } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import type { NotificationType } from './NotificationToast';
import {
    PopupLayoutPreviewModal,
    type PopupLayoutKey,
    type PopupPreviewLayout
} from './PopupLayoutPreviewModal';
import { useConfirm } from './confirmDialogContext';

interface PopupLayoutOption extends PopupPreviewLayout {
    description: string;
    placement: string;
    imageMode: string;
    imageSize: string;
}

interface PopupLayoutPageProps {
    onNotify: (type: NotificationType, message: string) => void;
}

interface PopupLayoutSettingsResponse {
    popupLayoutNo: number;
    message: string | null;
}

const POPUP_LAYOUT_OPTIONS: PopupLayoutOption[] = [
    {
        key: 'popup1',
        number: '01',
        name: '좌측 상단 기본형',
        description: '큰 배너형 팝업을 화면 왼쪽 위에 표시합니다.',
        placement: '좌측 상단',
        imageMode: '1장씩 자동 롤링',
        imageSize: '고정 불필요 · 원본 비율'
    },
    {
        key: 'popup11',
        number: '02',
        name: '중앙 기본형',
        description: '큰 배너형 팝업 하나를 화면 정중앙에 표시합니다.',
        placement: '화면 중앙',
        imageMode: '1장씩 자동 롤링',
        imageSize: '고정 불필요 · 원본 비율'
    },
    {
        key: 'popup12',
        number: '03',
        name: '우측 상단 기본형',
        description: '큰 배너형 팝업을 화면 오른쪽 위에 표시합니다.',
        placement: '우측 상단',
        imageMode: '1장씩 자동 롤링',
        imageSize: '고정 불필요 · 원본 비율'
    },
    {
        key: 'popup8',
        number: '04',
        name: '중앙 카드 덱',
        description: '화면 중앙에 여러 팝업을 카드처럼 포개어 표시합니다.',
        placement: '화면 중앙',
        imageMode: '1장씩 수동 전환',
        imageSize: '동일 크기 필수 · 4:3 권장'
    },
    {
        key: 'popup15',
        number: '05',
        name: '중앙 3장 나란히형',
        description: '화면을 어둡게 덮고 팝업 3장을 겹치지 않게 나란히 표시합니다.',
        placement: '화면 중앙',
        imageMode: '3장씩 자동 롤링',
        imageSize: '동일 크기 필수 · 4:3 권장'
    },
    {
        key: 'popup4',
        number: '06',
        name: '중앙 3장 오버레이',
        description: '화면을 어둡게 덮고 중앙에 팝업 3장을 함께 표시합니다.',
        placement: '화면 중앙',
        imageMode: '3장씩 자동 롤링',
        imageSize: '동일 크기 필수 · 4:3 권장'
    },
    {
        key: 'popup7',
        number: '07',
        name: '중앙 커버플로',
        description: '화면 중앙에 입체적으로 겹친 팝업 3장을 표시합니다.',
        placement: '화면 중앙',
        imageMode: '3장씩 자동 롤링',
        imageSize: '동일 크기 필수 · 4:3 권장'
    },
    {
        key: 'popup6',
        number: '08',
        name: '우측 사이드 드로어',
        description: '화면 오른쪽에서 가로·세로 팝업 이미지가 롤링되는 패널을 펼칩니다.',
        placement: '화면 우측',
        imageMode: '1장씩 자동 롤링',
        imageSize: '고정 불필요 · 비율 혼합 가능'
    }
];

const ScreenBackground = () => (
    <>
        <div className="absolute inset-x-0 top-0 h-[13%] border-b border-slate-200 bg-slate-50" />
        <div className="absolute left-[6%] top-[4.5%] h-[3.5%] w-[17%] rounded-full bg-slate-300" />
        <div className="absolute right-[6%] top-[4.5%] h-[3.5%] w-[28%] rounded-full bg-slate-200" />
        <div className="absolute left-[6%] right-[6%] top-[20%] h-[10%] rounded-md bg-slate-100" />
        <div className="absolute bottom-[10%] left-[6%] top-[36%] w-[26%] rounded-md bg-slate-100" />
        <div className="absolute bottom-[10%] left-[37%] right-[6%] top-[36%] rounded-md bg-slate-100" />
    </>
);

const PopupLayoutPreview = ({ layoutKey }: { layoutKey: PopupLayoutKey }) => (
    <div className="relative aspect-[16/10] overflow-hidden rounded-lg border border-slate-200 bg-white shadow-inner">
        <ScreenBackground />

        {layoutKey === 'popup1' && (
            <div className="absolute left-[5%] top-[8%] h-[58%] w-[55%] rounded-md border-2 border-slate-500 bg-slate-300/95 shadow-md">
                <div className="absolute inset-x-0 top-0 h-[8%] rounded-t bg-slate-400" />
                <div className="absolute bottom-[8%] left-[8%] right-[8%] h-[4%] rounded-full bg-slate-400" />
            </div>
        )}

        {layoutKey === 'popup15' && (
            <div className="absolute inset-0 bg-slate-900/65">
                <div className="absolute left-[3%] top-[27%] h-[36%] w-[30%] rounded-md border border-white/35 bg-slate-400 shadow-md" />
                <div className="absolute left-1/2 top-[27%] h-[36%] w-[30%] -translate-x-1/2 rounded-md border border-white/35 bg-slate-300 shadow-md" />
                <div className="absolute right-[3%] top-[27%] h-[36%] w-[30%] rounded-md border border-white/35 bg-slate-400 shadow-md" />
                <div className="absolute bottom-[13%] left-1/2 flex h-[8%] w-[28%] -translate-x-1/2 items-center justify-center gap-[6%] rounded-full bg-white/15">
                    <span className="h-[32%] w-[13%] rounded-full bg-white/80" />
                    <span className="h-[32%] w-[13%] rounded-full bg-white/55" />
                    <span className="h-[32%] w-[22%] rounded-full bg-white/55" />
                </div>
            </div>
        )}

        {layoutKey === 'popup4' && (
            <div className="absolute inset-0 bg-slate-900/65">
                <div className="absolute left-[8%] top-[30%] h-[44%] w-[40%] -rotate-2 rounded-md border border-white/25 bg-slate-500 opacity-65 shadow-md" />
                <div className="absolute right-[8%] top-[30%] h-[44%] w-[40%] rotate-2 rounded-md border border-white/25 bg-slate-500 opacity-65 shadow-md" />
                <div className="absolute left-1/2 top-[21%] z-10 h-[58%] w-[48%] -translate-x-1/2 rounded-md border border-white/45 bg-slate-300 shadow-lg" />
                <div className="absolute bottom-[8%] left-1/2 flex h-[8%] w-[28%] -translate-x-1/2 items-center justify-center gap-[6%] rounded-full bg-white/15">
                    <span className="h-[32%] w-[13%] rounded-full bg-white/80" />
                    <span className="h-[32%] w-[13%] rounded-full bg-white/55" />
                    <span className="h-[32%] w-[22%] rounded-full bg-white/55" />
                </div>
            </div>
        )}

        {layoutKey === 'popup6' && (
            <div className="absolute inset-0 bg-slate-900/35">
                <div className="absolute inset-y-0 right-0 w-[38%] border-l-2 border-slate-500 bg-slate-200 shadow-lg">
                    <div className="absolute inset-x-0 top-0 h-[14%] border-b border-slate-400 bg-slate-300" />
                    <div className="absolute inset-x-0 bottom-[16%] top-[14%] bg-slate-400">
                        <div className="absolute bottom-[7%] left-1/2 flex -translate-x-1/2 gap-1 rounded-full bg-slate-700/65 px-2 py-1">
                            <span className="h-1.5 w-4 rounded-full bg-white" />
                            <span className="h-1.5 w-1.5 rounded-full bg-white/50" />
                            <span className="h-1.5 w-1.5 rounded-full bg-white/50" />
                            <span className="h-1.5 w-1.5 rounded-full bg-white/50" />
                        </div>
                    </div>
                    <div className="absolute inset-x-0 bottom-0 h-[16%] border-t border-slate-400 bg-slate-300" />
                </div>
            </div>
        )}

        {layoutKey === 'popup7' && (
            <div className="absolute inset-0 bg-slate-900/65 [perspective:500px]">
                <div className="absolute left-[5%] top-[29%] h-[48%] w-[43%] origin-right -rotate-y-[38deg] rounded-md border-2 border-slate-300 bg-slate-400/80 shadow-md" />
                <div className="absolute right-[5%] top-[29%] h-[48%] w-[43%] origin-left rotate-y-[38deg] rounded-md border-2 border-slate-300 bg-slate-400/80 shadow-md" />
                <div className="absolute left-1/2 top-[20%] z-10 h-[58%] w-[48%] -translate-x-1/2 rounded-md border-2 border-white bg-slate-300 shadow-lg">
                    <div className="absolute inset-x-[10%] bottom-[9%] h-[4%] rounded-full bg-slate-400" />
                </div>
                <div className="absolute bottom-[9%] left-1/2 h-[4%] w-[30%] -translate-x-1/2 rounded-full bg-white/55" />
            </div>
        )}

        {layoutKey === 'popup8' && (
            <>
                <div className="absolute left-1/2 top-[20%] h-[61%] w-[52%] -translate-x-1/2 translate-y-[15%] scale-[0.91] rounded-md border border-slate-400 bg-slate-300/40 shadow-sm" />
                <div className="absolute left-1/2 top-[20%] h-[61%] w-[52%] -translate-x-1/2 translate-y-[10%] scale-[0.94] rounded-md border border-slate-400 bg-slate-300/55 shadow-sm" />
                <div className="absolute left-1/2 top-[20%] h-[61%] w-[52%] -translate-x-1/2 translate-y-[5%] scale-[0.97] rounded-md border border-slate-400 bg-slate-300/75 shadow-md" />
                <div className="absolute left-1/2 top-[20%] z-10 h-[61%] w-[52%] -translate-x-1/2 overflow-hidden rounded-md border border-slate-500 bg-slate-300 shadow-lg">
                    <div className="absolute inset-x-0 bottom-0 flex h-[18%] items-center justify-between border-t border-slate-400 bg-slate-200 px-[6%]">
                        <span className="h-[20%] w-[32%] rounded-full bg-slate-400" />
                        <span className="h-[38%] w-[22%] rounded bg-slate-500" />
                    </div>
                </div>
            </>
        )}

        {layoutKey === 'popup11' && (
            <div className="absolute left-1/2 top-1/2 h-[58%] w-[55%] -translate-x-1/2 -translate-y-1/2 rounded-md border-2 border-slate-500 bg-slate-300/95 shadow-md">
                <div className="absolute inset-x-0 top-0 h-[8%] rounded-t bg-slate-400" />
                <div className="absolute bottom-[8%] left-[8%] right-[8%] h-[4%] rounded-full bg-slate-400" />
            </div>
        )}

        {layoutKey === 'popup12' && (
            <div className="absolute right-[5%] top-[8%] h-[58%] w-[55%] rounded-md border-2 border-slate-500 bg-slate-300/95 shadow-md">
                <div className="absolute inset-x-0 top-0 h-[8%] rounded-t bg-slate-400" />
                <div className="absolute bottom-[8%] left-[8%] right-[8%] h-[4%] rounded-full bg-slate-400" />
            </div>
        )}

    </div>
);

export const PopupLayoutPage = ({ onNotify }: PopupLayoutPageProps) => {
    const confirm = useConfirm();
    const [selectedLayout, setSelectedLayout] = useState<PopupLayoutKey | null>(null);
    const [previewLayout, setPreviewLayout] = useState<PopupLayoutOption | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [isSaving, setIsSaving] = useState(false);
    const previewTriggerRef = useRef<HTMLButtonElement>(null);
    const closePreview = useCallback(() => setPreviewLayout(null), []);

    useEffect(() => {
        const controller = new AbortController();

        const loadPopupLayout = async () => {
            setIsLoading(true);
            try {
                const response = await fetch('/api/admin/popup-layout-settings', {
                    signal: controller.signal
                });
                if (!response.ok) {
                    throw new Error((await response.text()) || '팝업 레이아웃을 불러오지 못했습니다.');
                }

                const data = await response.json() as PopupLayoutSettingsResponse;
                const savedLayout = POPUP_LAYOUT_OPTIONS.find(
                    (option) => Number(option.number) === data.popupLayoutNo
                );
                if (!savedLayout) {
                    throw new Error('저장된 팝업 레이아웃 번호가 올바르지 않습니다.');
                }
                setSelectedLayout(savedLayout.key);
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') {
                    return;
                }
                onNotify('error', error instanceof Error ? error.message : '팝업 레이아웃을 불러오지 못했습니다.');
            } finally {
                if (!controller.signal.aborted) {
                    setIsLoading(false);
                }
            }
        };

        void loadPopupLayout();
        return () => controller.abort();
    }, [onNotify]);

    const handleLayoutChange = async (option: PopupLayoutOption) => {
        if (isLoading || isSaving || selectedLayout === option.key) {
            return;
        }

        const confirmed = await confirm({
            title: '팝업 레이아웃 변경',
            message: `${option.name}(LAYOUT ${option.number})으로 변경하시겠습니까?`,
            confirmText: '변경'
        });
        if (!confirmed) {
            return;
        }

        setIsSaving(true);
        try {
            const response = await fetch('/api/admin/popup-layout-settings', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ popupLayoutNo: Number(option.number) })
            });
            if (!response.ok) {
                throw new Error((await response.text()) || '팝업 레이아웃을 변경하지 못했습니다.');
            }

            const data = await response.json() as PopupLayoutSettingsResponse;
            setSelectedLayout(option.key);
            onNotify('success', data.message || '팝업 레이아웃을 변경했습니다.');
        } catch (error) {
            onNotify('error', error instanceof Error ? error.message : '팝업 레이아웃을 변경하지 못했습니다.');
        } finally {
            setIsSaving(false);
        }
    };

    return (
        <>
        <section className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-950">
            <header className="border-b border-slate-200 p-4 dark:border-slate-800 md:p-5">
                <div className="flex items-center gap-2 text-slate-900 dark:text-slate-50">
                    <Layout className="h-4 w-4 shrink-0 text-blue-600 dark:text-blue-400" aria-hidden="true" />
                    <h1 className="text-sm font-semibold md:text-base">팝업 레이아웃 설정</h1>
                </div>
                <p className="mt-1 text-xs text-slate-400 dark:text-slate-400">
                    사용자 메인 화면에서 팝업이 표시될 위치와 형태를 선택합니다.
                </p>
            </header>

            <div className="p-4 md:p-5">
                <fieldset aria-busy={isLoading || isSaving}>
                    <legend className="text-sm font-semibold text-slate-700 dark:text-slate-200">
                        레이아웃 선택
                    </legend>
                    <p className="mt-1 text-xs text-slate-400 dark:text-slate-400">
                        {isLoading
                            ? '저장된 레이아웃을 불러오는 중입니다.'
                            : isSaving
                                ? '선택한 레이아웃을 저장하는 중입니다.'
                                : '카드를 눌러 레이아웃을 선택하고, LAYOUT 버튼으로 실제 적용 모습을 확인하세요.'}
                    </p>

                    <div className="mt-4 grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
                        {POPUP_LAYOUT_OPTIONS.map((option) => {
                            const isSelected = selectedLayout === option.key;

                            return (
                                <label
                                    key={option.key}
                                    className={`group relative flex flex-col rounded-xl border p-3 transition focus-within:ring-2 focus-within:ring-blue-500 focus-within:ring-offset-2 dark:focus-within:ring-offset-slate-950 ${
                                        isLoading || isSaving ? 'cursor-wait opacity-70' : 'cursor-pointer'
                                    } ${
                                        isSelected
                                            ? 'border-blue-500 bg-blue-50/60 ring-1 ring-blue-500 dark:border-blue-400 dark:bg-blue-950/25 dark:ring-blue-400'
                                            : 'border-slate-200 bg-white hover:border-slate-300 hover:bg-slate-50 dark:border-slate-800 dark:bg-slate-950 dark:hover:border-slate-700 dark:hover:bg-slate-900/60'
                                    }`}
                                >
                                    <input
                                        type="radio"
                                        name="popupLayout"
                                        value={option.key}
                                        checked={isSelected}
                                        onChange={() => void handleLayoutChange(option)}
                                        disabled={isLoading || isSaving}
                                        className="sr-only"
                                    />

                                    <PopupLayoutPreview layoutKey={option.key} />

                                    <div className="flex flex-1 items-start gap-3 px-1 pb-1 pt-4">
                                        <span
                                            className={`mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full border ${
                                                isSelected
                                                    ? 'border-blue-600 bg-blue-600 text-white dark:border-blue-500 dark:bg-blue-500 dark:text-white'
                                                    : 'border-slate-300 bg-white text-transparent dark:border-slate-600 dark:bg-slate-900'
                                            }`}
                                            aria-hidden="true"
                                        >
                                            <Check className="h-3 w-3" strokeWidth={3} />
                                        </span>

                                        <div className="min-w-0 flex-1">
                                            <div className="flex flex-wrap items-center justify-between gap-2">
                                                <h2 className="text-sm font-semibold text-slate-900 dark:text-slate-50">
                                                    {option.name}
                                                </h2>
                                                <button
                                                    type="button"
                                                    onClick={(event) => {
                                                        event.preventDefault();
                                                        event.stopPropagation();
                                                        previewTriggerRef.current = event.currentTarget;
                                                        setPreviewLayout(option);
                                                    }}
                                                    className="inline-flex items-center gap-1 rounded-md bg-slate-100 px-2 py-1 text-[10px] font-bold tracking-wide text-slate-500 hover:bg-blue-100 hover:text-blue-700 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-blue-950/60 dark:hover:text-blue-300"
                                                    aria-label={`${option.name} 실제 팝업 미리보기`}
                                                >
                                                    <Eye className="h-3 w-3" aria-hidden="true" />
                                                    LAYOUT {option.number}
                                                </button>
                                            </div>
                                            <p className="mt-1.5 text-xs leading-5 text-slate-500 dark:text-slate-400">
                                                {option.description}
                                            </p>
                                            <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px] font-semibold">
                                                <span className="text-blue-600 dark:text-blue-400">
                                                    표시 위치 · {option.placement}
                                                </span>
                                                <span className="text-violet-600 dark:text-violet-400">
                                                    이미지 전환 · {option.imageMode}
                                                </span>
                                                <span className="text-emerald-600 dark:text-emerald-400">
                                                    이미지 규격 · {option.imageSize}
                                                </span>
                                            </div>
                                        </div>
                                    </div>
                                </label>
                            );
                        })}
                    </div>
                </fieldset>
            </div>
        </section>
        <PopupLayoutPreviewModal
            layout={previewLayout}
            onClose={closePreview}
            returnFocusRef={previewTriggerRef}
        />
        </>
    );
};
