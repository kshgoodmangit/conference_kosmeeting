import {useEffect, useRef, useState} from 'react';
import {CalendarDays, Copy, Pencil, Plus} from 'lucide-react';
import type {NotificationType} from './NotificationToast';
import {rowActionButtonClass} from './RowActionMenu';
import {ConferenceSettingsModal, type ConferenceSettings} from './ConferenceSettingsModal';
import {ConferenceDetailModal} from './ConferenceDetailModal';
import {ConferenceCopyModal} from './ConferenceCopyModal';

interface ConferencePageProps {
    onNotify: (type: NotificationType, message: string) => void;
    onSaved?: () => void;
}

const formatDateRange = (startDate?: string | null, endDate?: string | null) => {
    if (!startDate && !endDate) return '-';
    return `${startDate || '미정'} ~ ${endDate || '미정'}`;
};

export const ConferencePage = ({onNotify, onSaved}: ConferencePageProps) => {
    const onNotifyRef = useRef(onNotify);
    const [settingsList, setSettingsList] = useState<ConferenceSettings[]>([]);
    const [detailSettings, setDetailSettings] = useState<ConferenceSettings | null>(null);
    const [editingSettings, setEditingSettings] = useState<ConferenceSettings | null>(null);
    const [isSettingsModalOpen, setIsSettingsModalOpen] = useState(false);
    const [isLoading, setIsLoading] = useState(false);
    const [reloadKey, setReloadKey] = useState(0);
    const [canCreateConference, setCanCreateConference] = useState(false);
    const [canCopyConference, setCanCopyConference] = useState(false);
    const [copyingSettings, setCopyingSettings] = useState<ConferenceSettings | null>(null);

    useEffect(() => {
        onNotifyRef.current = onNotify;
    }, [onNotify]);

    useEffect(() => {
        const controller = new AbortController();
        void (async () => {
            setCanCreateConference(false);
            setCanCopyConference(false);
            try {
                const response = await fetch('/api/admin/conference-settings/capabilities', {signal: controller.signal});
                if (!response.ok) throw new Error('학회 추가 권한을 확인하지 못했습니다.');
                const capabilities = await response.json() as {conferenceCreationEnabled?: boolean; conferenceCopyEnabled?: boolean};
                if (!controller.signal.aborted) {
                    setCanCreateConference(capabilities.conferenceCreationEnabled === true);
                    setCanCopyConference(capabilities.conferenceCopyEnabled === true && capabilities.conferenceCreationEnabled === true);
                }
            } catch (error) {
                if (controller.signal.aborted) return;
                onNotifyRef.current('error', error instanceof Error ? error.message : '학회 추가 권한을 확인하지 못했습니다.');
            }
        })();
        return () => controller.abort();
    }, [reloadKey]);

    useEffect(() => {
        const abortController = new AbortController();

        const fetchSettings = async () => {
            setIsLoading(true);
            try {
                const response = await fetch('/api/admin/conference-settings', {signal: abortController.signal});
                if (!response.ok) throw new Error(await response.text() || '학회 목록을 불러오지 못했습니다.');
                setSettingsList(await response.json() as ConferenceSettings[]);
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') return;
                const message = error instanceof Error ? error.message : '학회 목록을 불러오지 못했습니다.';
                onNotifyRef.current('error', message);
            } finally {
                setIsLoading(false);
            }
        };

        void fetchSettings();
        return () => abortController.abort();
    }, [reloadKey]);

    const openAddModal = () => {
        if (!canCreateConference) return;
        setEditingSettings(null);
        setIsSettingsModalOpen(true);
    };

    const openEditModal = (settings: ConferenceSettings) => {
        setEditingSettings(settings);
        setIsSettingsModalOpen(true);
    };

    const closeModal = () => {
        setIsSettingsModalOpen(false);
        setEditingSettings(null);
    };

    const handleSettingsSaved = (_settings: ConferenceSettings, message: string) => {
        closeModal();
        onNotifyRef.current('success', message);
        onSaved?.();
        setReloadKey((value) => value + 1);
    };

    return (
        <div className="space-y-6">
            <section
                className="rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-950">
                <div
                    className="flex flex-col justify-between gap-3 border-b border-slate-200 p-4 dark:border-slate-800 sm:flex-row sm:items-center md:p-5">
                    <div>
                        <div className="flex items-center gap-2">
                            <CalendarDays className="shrink-0 h-4 w-4 text-slate-400"/>
                            <h3 className="text-sm font-semibold md:text-base">학회 목록</h3>
                        </div>
                        <p className="mt-1 text-xs text-slate-400">학회별 컨퍼런스 행사 일정과 등록비를 관리합니다.</p>
                    </div>
                    {canCreateConference && <div className="flex flex-col gap-2 sm:flex-row">
                        <button type="button" onClick={openAddModal} disabled={isLoading}
                                className="inline-flex items-center justify-center gap-2 rounded-lg bg-emerald-600 px-3 py-2 text-xs font-semibold text-white hover:bg-emerald-700 disabled:opacity-50 dark:bg-emerald-600 dark:text-white dark:hover:bg-emerald-700">
                            <Plus className="h-4 w-4"/> 학회 추가
                        </button>
                    </div>}
                </div>

                <div className="overflow-x-auto">
                    <table className="w-full min-w-[1320px] text-left text-sm">
                        <thead
                            className="border-b border-slate-200 bg-slate-50 text-xs uppercase dark:border-slate-800 dark:bg-slate-900/70">
                        <tr>
                            <th className="w-16 px-4 py-3 text-center">번호</th>
                            <th className="min-w-52 px-4 py-3">학회명</th>
                            <th className="w-56 px-4 py-3">행사 기간</th>
                            <th className="w-56 px-4 py-3">등록 기간</th>
                            <th className="w-56 px-4 py-3">초록 제출 기간</th>
                            <th className="w-56 px-4 py-3">발표자료 등록기간</th>
                            <th className="min-w-56 px-4 py-3">행사장소</th>
                            <th className="min-w-40 px-4 py-3 text-center">기능</th>
                        </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-100 dark:divide-slate-900">
                        {settingsList.map((settings, index) => (
                            <tr key={settings.seq ?? index} className="hover:bg-slate-50/70 dark:hover:bg-slate-900/40">
                                <td className="px-4 py-4 text-center text-xs tabular-nums text-slate-400">{settingsList.length - index}</td>
                                <td className="px-4 py-4">
                                    <button
                                        type="button"
                                        onClick={() => setDetailSettings(settings)}
                                        className="text-left font-semibold text-blue-600 hover:underline focus:outline-none focus-visible:rounded focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2 dark:text-blue-400 dark:focus-visible:ring-offset-slate-950"
                                    >
                                        {settings.eventName || '-'}
                                    </button>
                                </td>
                                <td className="px-4 py-4 text-xs tabular-nums text-slate-600 dark:text-slate-300">{formatDateRange(settings.eventStartDate, settings.eventEndDate)}</td>
                                <td className="px-4 py-4 text-xs tabular-nums text-slate-600 dark:text-slate-300">
                                    <div><span
                                        className="mr-1 font-semibold text-emerald-600 dark:text-emerald-400">E</span>{formatDateRange(settings.earlyBirdStartDate, settings.earlyBirdEndDate)}
                                    </div>
                                    <div className="mt-1"><span
                                        className="mr-1 font-semibold text-blue-600 dark:text-blue-400">R</span>{formatDateRange(settings.regularStartDate, settings.regularEndDate)}
                                    </div>
                                </td>
                                <td className="px-4 py-4 text-xs tabular-nums text-slate-600 dark:text-slate-300">{formatDateRange(settings.abstractStartDate, settings.abstractEndDate)}</td>
                                <td className="px-4 py-4 text-xs tabular-nums text-slate-600 dark:text-slate-300">{formatDateRange(settings.presentationMaterialStartDate, settings.presentationMaterialEndDate)}</td>
                                <td className="max-w-72 px-4 py-4 text-xs text-slate-600 dark:text-slate-300"><span
                                    className="line-clamp-2">{settings.venueAddress || '-'}</span></td>
                                <td className="px-4 py-4 text-center">
                                    <div className="flex items-center justify-center gap-2">
                                        <button type="button" onClick={() => openEditModal(settings)}
                                                className={rowActionButtonClass} aria-label={`${settings.eventName || '학회'} 수정`}>
                                            <Pencil className="h-4 w-4"/>
                                        </button>
                                        {canCopyConference && <button type="button"
                                            onClick={() => { if (canCopyConference) setCopyingSettings(settings); }}
                                            className={`${rowActionButtonClass} shrink-0 gap-1.5 whitespace-nowrap`}
                                            aria-label={`${settings.eventName || '학회'} 복사`}>
                                            <Copy className="h-4 w-4" aria-hidden="true"/> 복사
                                        </button>}
                                    </div>
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>

                {!isLoading && settingsList.length === 0 && (
                    <div className="px-4 py-14 text-center">
                        <CalendarDays className="mx-auto h-10 w-10 text-slate-300 dark:text-slate-700"/>
                        <p className="mt-3 text-sm font-semibold text-slate-600 dark:text-slate-300">등록된 학회가 없습니다.</p>
                        {canCreateConference && <><p className="mt-1 text-xs text-slate-400 dark:text-slate-400">학회 추가 버튼을 눌러 첫 컨퍼런스 행사를 설정해주세요.</p>
                        <button type="button" onClick={openAddModal}
                                className="mt-4 inline-flex items-center justify-center gap-2 rounded-lg bg-emerald-600 px-3 py-2 text-xs font-semibold text-white hover:bg-emerald-700 dark:bg-emerald-600 dark:text-white dark:hover:bg-emerald-700">
                            <Plus className="h-4 w-4"/> 학회 추가
                        </button></>}
                    </div>
                )}
            </section>

            {copyingSettings && canCopyConference && (
                <ConferenceCopyModal
                    source={copyingSettings}
                    onClose={() => setCopyingSettings(null)}
                    onNotify={onNotify}
                />
            )}

            {detailSettings && (
                <ConferenceDetailModal
                    settings={detailSettings}
                    onClose={() => setDetailSettings(null)}
                    onEdit={() => {
                        const settings = detailSettings;
                        setDetailSettings(null);
                        openEditModal(settings);
                    }}
                    onNotify={onNotify}
                />
            )}

            {isSettingsModalOpen && (editingSettings !== null || canCreateConference) && (
                <ConferenceSettingsModal
                    settings={editingSettings}
                    onClose={closeModal}
                    onSaved={handleSettingsSaved}
                    onNotify={onNotify}
                />
            )}
        </div>
    );
};
