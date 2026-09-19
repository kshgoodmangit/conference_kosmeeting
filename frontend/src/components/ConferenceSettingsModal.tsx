import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import {
    ArrowDown,
    ArrowUp,
    CalendarDays,
    CircleDollarSign,
    Info,
    MapPin,
    Plus,
    Save,
    Trash2
} from 'lucide-react';
import type { NotificationType } from './NotificationToast';
import { ProgramModalShell } from './ProgramModalShell';
import { useConfirm } from './confirmDialogContext';
import { RegistrationOptionPanel } from './RegistrationOptionPanel';

export interface ConferenceSettings {
    seq?: number | null;
    sitePath?: string | null;
    published?: boolean;
    defaultLanguage?: string | null;
    supportedLanguages?: string[];
    eventName?: string | null;
    eventStartDate?: string | null;
    eventEndDate?: string | null;
    earlyBirdStartDate?: string | null;
    earlyBirdEndDate?: string | null;
    regularStartDate?: string | null;
    regularEndDate?: string | null;
    registrationCurrency?: string | null;
    abstractStartDate?: string | null;
    abstractEndDate?: string | null;
    presentationMaterialStartDate?: string | null;
    presentationMaterialEndDate?: string | null;
    venueAddress?: string | null;
    createdAt?: string | null;
    updatedAt?: string | null;
}

interface RegistrationFeeCategoryResponse {
    seq: number;
    categoryCode: string;
    categoryName: string;
    description?: string | null;
    sortOrder: number;
    isUsed: 'Y' | 'N';
    earlyBirdUsdFee?: number | null;
    earlyBirdKrwFee?: number | null;
    regularUsdFee?: number | null;
    regularKrwFee?: number | null;
}

interface RegistrationFeeRow {
    localId: string;
    seq?: number;
    categoryCode: string;
    categoryName: string;
    description: string;
    sortOrder: number;
    isUsed: 'Y' | 'N';
    earlyBirdUsdFee: string;
    earlyBirdKrwFee: string;
    regularUsdFee: string;
    regularKrwFee: string;
}

interface ConferenceSettingsModalProps {
    settings: ConferenceSettings | null;
    onClose: () => void;
    onSaved: (settings: ConferenceSettings, message: string) => void;
    onNotify: (type: NotificationType, message: string) => void;
}

const inputClassName = 'w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-400 dark:border-slate-800 dark:bg-slate-950 dark:text-slate-50 dark:disabled:bg-slate-900 dark:disabled:text-slate-500';

const toFeeRow = (item: RegistrationFeeCategoryResponse): RegistrationFeeRow => ({
    localId: `saved-${item.seq}`,
    seq: item.seq,
    categoryCode: item.categoryCode,
    categoryName: item.categoryName,
    description: item.description ?? '',
    sortOrder: item.sortOrder,
    isUsed: item.isUsed,
    earlyBirdUsdFee: item.earlyBirdUsdFee == null ? '' : String(item.earlyBirdUsdFee),
    earlyBirdKrwFee: item.earlyBirdKrwFee == null ? '' : String(item.earlyBirdKrwFee),
    regularUsdFee: item.regularUsdFee == null ? '' : String(item.regularUsdFee),
    regularKrwFee: item.regularKrwFee == null ? '' : String(item.regularKrwFee)
});

const normalizeCodeInput = (value: string) => value
    .toUpperCase()
    .replace(/[^A-Z0-9_]/g, '_')
    .replace(/_+/g, '_');

export const ConferenceSettingsModal = ({ settings, onClose, onSaved, onNotify }: ConferenceSettingsModalProps) => {
    const confirm = useConfirm();
    const onNotifyRef = useRef(onNotify);
    const [sitePath, setSitePath] = useState(settings?.sitePath ?? '');
    const [published, setPublished] = useState(settings?.published === true);
    const [supportedLanguages, setSupportedLanguages] = useState(settings?.supportedLanguages?.length ? settings.supportedLanguages : ['ko', 'en']);
    const [defaultLanguage, setDefaultLanguage] = useState(settings?.defaultLanguage ?? 'en');
    const [newLanguage, setNewLanguage] = useState('');
    const [eventName, setEventName] = useState(settings?.eventName ?? '');
    const [eventStartDate, setEventStartDate] = useState(settings?.eventStartDate ?? '');
    const [eventEndDate, setEventEndDate] = useState(settings?.eventEndDate ?? '');
    const [earlyBirdStartDate, setEarlyBirdStartDate] = useState(settings?.earlyBirdStartDate ?? '');
    const [earlyBirdEndDate, setEarlyBirdEndDate] = useState(settings?.earlyBirdEndDate ?? '');
    const [regularStartDate, setRegularStartDate] = useState(settings?.regularStartDate ?? '');
    const [regularEndDate, setRegularEndDate] = useState(settings?.regularEndDate ?? '');
    const registrationCurrency = settings?.registrationCurrency?.trim() || 'USD';
    const [abstractStartDate, setAbstractStartDate] = useState(settings?.abstractStartDate ?? '');
    const [abstractEndDate, setAbstractEndDate] = useState(settings?.abstractEndDate ?? '');
    const [presentationMaterialStartDate, setPresentationMaterialStartDate] = useState(settings?.presentationMaterialStartDate ?? '');
    const [presentationMaterialEndDate, setPresentationMaterialEndDate] = useState(settings?.presentationMaterialEndDate ?? '');
    const [venueAddress, setVenueAddress] = useState(settings?.venueAddress ?? '');
    const [feeRows, setFeeRows] = useState<RegistrationFeeRow[]>([]);
    const [deletedFeeRowSeqs, setDeletedFeeRowSeqs] = useState<number[]>([]);
    const [persistedSettingsSeq, setPersistedSettingsSeq] = useState<number | null>(settings?.seq ?? null);
    const [isLoadingFees, setIsLoadingFees] = useState(true);
    const [isSaving, setIsSaving] = useState(false);
    const [activeTab, setActiveTab] = useState<'settings' | 'fees' | 'options'>('settings');
    const [optionsSaving, setOptionsSaving] = useState(false);

    useEffect(() => {
        onNotifyRef.current = onNotify;
    }, [onNotify]);

    useEffect(() => {
        const abortController = new AbortController();

        const fetchFees = async () => {
            if (persistedSettingsSeq == null) {
                setFeeRows([]);
                setIsLoadingFees(false);
                return;
            }
            try {
                const response = await fetch(
                    `/api/admin/conference-settings/${persistedSettingsSeq}/registration-fees`,
                    { signal: abortController.signal }
                );
                if (!response.ok) throw new Error(await response.text() || '등록비 정보를 불러오지 못했습니다.');
                setFeeRows((await response.json() as RegistrationFeeCategoryResponse[]).map(toFeeRow));
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') return;
                onNotifyRef.current('error', error instanceof Error ? error.message : '등록비 정보를 불러오지 못했습니다.');
            } finally {
                setIsLoadingFees(false);
            }
        };

        void fetchFees();
        return () => abortController.abort();
    }, [persistedSettingsSeq]);

    const updateFeeRow = (localId: string, changes: Partial<RegistrationFeeRow>) => {
        setFeeRows((rows) => rows.map((row) => row.localId === localId ? { ...row, ...changes } : row));
    };

    const addFeeRow = () => {
        const rowNumber = feeRows.length + 1;
        setFeeRows((rows) => [...rows, {
            localId: `new-${Date.now()}`,
            categoryCode: `CATEGORY_${rowNumber}`,
            categoryName: '',
            description: '',
            sortOrder: rowNumber * 10,
            isUsed: 'Y',
            earlyBirdUsdFee: '',
            earlyBirdKrwFee: '',
            regularUsdFee: '',
            regularKrwFee: ''
        }]);
    };

    const removeFeeRow = async (row: RegistrationFeeRow) => {
        if (row.seq && !await confirm({
            title: '등록 구분 삭제',
            message: `'${row.categoryName}' 등록 구분을 삭제하시겠습니까? 학회 저장 시 함께 반영됩니다.`,
            confirmText: '삭제',
            tone: 'danger'
        })) return;

        if (row.seq) setDeletedFeeRowSeqs((seqs) => [...seqs, row.seq as number]);
        setFeeRows((rows) => rows.filter((item) => item.localId !== row.localId));
    };

    const moveFeeRow = (index: number, direction: -1 | 1) => {
        const targetIndex = index + direction;
        if (targetIndex < 0 || targetIndex >= feeRows.length) return;
        setFeeRows((rows) => {
            const nextRows = [...rows];
            [nextRows[index], nextRows[targetIndex]] = [nextRows[targetIndex], nextRows[index]];
            return nextRows.map((row, rowIndex) => ({ ...row, sortOrder: (rowIndex + 1) * 10 }));
        });
    };

    const handleSubmit = async (event: FormEvent) => {
        event.preventDefault();
        if (!eventName.trim()) {
            setActiveTab('settings');
            onNotify('error', '학회명을 입력해 주세요.');
            return;
        }
        if (!sitePath.trim() || !supportedLanguages.length || !supportedLanguages.includes(defaultLanguage)) {
            setActiveTab('settings');
            onNotify('error', '학회 경로, 지원 언어와 기본 언어를 확인해 주세요.');
            return;
        }
        if (feeRows.length === 0) {
            setActiveTab('fees');
            onNotify('error', '등록 구분을 한 개 이상 입력해 주세요.');
            return;
        }
        const invalidFeeRow = feeRows.find((row) => !row.categoryCode.trim() || !row.categoryName.trim()
            || (row.isUsed === 'Y' && [row.earlyBirdUsdFee, row.earlyBirdKrwFee, row.regularUsdFee, row.regularKrwFee].some((fee) => fee === '')));
        if (invalidFeeRow) {
            setActiveTab('fees');
            onNotify('error', '등록 구분명과 사용 중인 등록비의 USD·KRW 금액을 모두 입력해 주세요.');
            return;
        }

        setIsSaving(true);

        try {
            const isUpdating = persistedSettingsSeq != null;
            const response = await fetch(
                isUpdating
                    ? `/api/admin/conference-settings/${persistedSettingsSeq}/save-all`
                    : '/api/admin/conference-settings/save-all',
                {
                    method: isUpdating ? 'PUT' : 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        sitePath: sitePath.trim(), defaultLanguage, supportedLanguages, published,
                        eventName: eventName.trim(),
                        eventStartDate: eventStartDate || null,
                        eventEndDate: eventEndDate || null,
                        earlyBirdStartDate: earlyBirdStartDate || null,
                        earlyBirdEndDate: earlyBirdEndDate || null,
                        regularStartDate: regularStartDate || null,
                        regularEndDate: regularEndDate || null,
                        registrationCurrency,
                        abstractStartDate: abstractStartDate || null,
                        abstractEndDate: abstractEndDate || null,
                        presentationMaterialStartDate: presentationMaterialStartDate || null,
                        presentationMaterialEndDate: presentationMaterialEndDate || null,
                        venueAddress: venueAddress.trim() || null,
                        categories: feeRows.map((row, index) => ({
                            seq: row.seq,
                            categoryCode: row.categoryCode.trim(),
                            categoryName: row.categoryName.trim(),
                            description: row.description.trim() || null,
                            sortOrder: (index + 1) * 10,
                            isUsed: row.isUsed,
                            earlyBirdUsdFee: row.earlyBirdUsdFee === '' ? null : Number(row.earlyBirdUsdFee),
                            earlyBirdKrwFee: row.earlyBirdKrwFee === '' ? null : Number(row.earlyBirdKrwFee),
                            regularUsdFee: row.regularUsdFee === '' ? null : Number(row.regularUsdFee),
                            regularKrwFee: row.regularKrwFee === '' ? null : Number(row.regularKrwFee)
                        })),
                        deletedCategorySeqs: deletedFeeRowSeqs
                    })
                }
            );
            if (!response.ok) {
                throw new Error(await response.text() || `학회 ${isUpdating ? '수정' : '추가'}에 실패했습니다.`);
            }

            const savedSettings = await response.json() as ConferenceSettings & { message?: string };
            if (savedSettings.seq != null) setPersistedSettingsSeq(savedSettings.seq);
            onSaved(savedSettings, `${savedSettings.message || `학회가 ${isUpdating ? '수정' : '추가'}되었습니다.`} 등록비가 함께 저장되었습니다.`);
        } catch (error) {
            const message = error instanceof Error ? error.message : '학회 설정을 저장하지 못했습니다.';
            onNotify('error', message);
        } finally {
            setIsSaving(false);
        }
    };

    const isEdit = persistedSettingsSeq != null;
    const activeRows = feeRows.filter((row) => row.isUsed === 'Y');
    const configuredRows = activeRows.filter((row) => [
        row.earlyBirdUsdFee,
        row.earlyBirdKrwFee,
        row.regularUsdFee,
        row.regularKrwFee
    ].every(Boolean));

    return (
        <ProgramModalShell
            title={isEdit ? '학회 수정' : '학회 추가'}
            description="행사 일정, 등록비와 학회별 등록 옵션을 설정합니다."
            onClose={() => { if (!isSaving && !optionsSaving) onClose(); }}
            widthClass="max-w-7xl"
        >
            <div className="flex flex-wrap gap-2 border-b border-slate-200 px-5 py-3 dark:border-slate-800" role="tablist" aria-label="학회 설정 구분">
                {([['settings', '기본 설정'], ['fees', '등록 구분별 등록비'], ['options', '등록 옵션 설정']] as const).map(([tab, label]) => <button key={tab} type="button" role="tab" aria-selected={activeTab === tab} disabled={isSaving || optionsSaving} onClick={() => setActiveTab(tab)} className={`rounded-lg px-4 py-2 text-sm font-semibold ${activeTab === tab ? 'bg-blue-600 text-white dark:bg-blue-600 dark:text-white' : 'text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-900'}`}>{label}</button>)}
            </div>
            {activeTab === 'options' && (persistedSettingsSeq == null
                ? <p className="p-5 text-sm text-slate-600 dark:text-slate-300">기본 설정과 등록 구분별 등록비를 먼저 저장한 뒤 학회를 다시 열어 옵션을 추가해 주세요.</p>
                : <RegistrationOptionPanel conferenceSeq={persistedSettingsSeq} onNotify={onNotify} onBusyChange={setOptionsSaving} />)}
            <form hidden={activeTab === 'options'} onSubmit={handleSubmit} className="space-y-7 p-5">
                {activeTab === 'settings' && <section className="space-y-5">
                    <SectionTitle
                        icon={<CalendarDays className="h-5 w-5 text-blue-500" />}
                        title="행사 기본 설정"
                        description="학회명과 행사·등록·초록 제출 기간을 입력합니다."
                    />

                    <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
                        <Field label="학회명" required>
                            <input value={eventName} onChange={(event) => setEventName(event.target.value)} className={inputClassName} placeholder="ICMS 2026" maxLength={255} required autoFocus />
                        </Field>
                        <Field label="행사장소 주소">
                            <div className="relative">
                                <MapPin className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                                <input value={venueAddress} onChange={(event) => setVenueAddress(event.target.value)} className={`${inputClassName} pl-9`} placeholder="행사장 주소" maxLength={500} />
                            </div>
                        </Field>
                    </div>

                    <div className="grid gap-4 sm:grid-cols-2">
                        <Field label="학회 경로" required>
                            <input value={sitePath} onChange={event => setSitePath(event.target.value.toLowerCase())} className={inputClassName} placeholder="apdrc8" maxLength={100} pattern="[a-z0-9]+((-|_)[a-z0-9]+)*" required />
                            <p className="mt-1 text-xs font-normal text-slate-500 dark:text-slate-400">여러 학회 운영 시 사용자 주소의 첫 경로로 사용합니다. 영문 소문자, 숫자, 하이픈(-), 밑줄(_)을 사용할 수 있습니다.</p>
                        </Field>
                        <Field label="기본 언어" required>
                            <select value={defaultLanguage} onChange={event => setDefaultLanguage(event.target.value)} className={inputClassName}>
                                {supportedLanguages.map(code => <option key={code} value={code}>{languageLabel(code)}</option>)}
                            </select>
                        </Field>
                        <div className="space-y-3 sm:col-span-2">
                            <span className="text-sm font-semibold text-slate-700 dark:text-slate-200">지원 언어</span>
                            <div className="flex flex-wrap gap-3">{Array.from(new Set(['ko', 'en', ...supportedLanguages])).map(code => <label key={code} className="flex items-center gap-2 rounded-lg border border-slate-200 p-3 text-sm text-slate-700 dark:border-slate-800 dark:text-slate-200">
                                <input type="checkbox" checked={supportedLanguages.includes(code)} className="h-5 w-5" onChange={event => {
                                    const next = event.target.checked ? [...supportedLanguages, code] : supportedLanguages.filter(value => value !== code);
                                    if (!next.length) { onNotify('error', '지원 언어는 한 개 이상 필요합니다.'); return; }
                                    setSupportedLanguages(next);
                                    if (!next.includes(defaultLanguage)) setDefaultLanguage(next[0]);
                                }} />{languageLabel(code)}
                            </label>)}</div>
                            <div className="flex gap-2">
                                <input aria-label="추가할 언어 코드" value={newLanguage} onChange={event => setNewLanguage(event.target.value)} placeholder="추가 언어 코드 (예: ja, zh-Hans)" maxLength={35} className={inputClassName} />
                                <button type="button" className="shrink-0 rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900" onClick={() => {
                                    try {
                                        if (!/^[a-z]{2,3}(-[a-z0-9]{2,8})*$/i.test(newLanguage.trim())) throw new Error();
                                        const code = Intl.getCanonicalLocales(newLanguage.trim())[0];
                                        setSupportedLanguages(previous => Array.from(new Set([...previous, code])));
                                        setNewLanguage('');
                                    } catch { onNotify('error', '올바른 언어 코드를 입력해 주세요.'); }
                                }}>언어 추가</button>
                            </div>
                            <p className="text-xs text-slate-500 dark:text-slate-400">언어가 하나면 언어 경로를 생략하고, 두 개 이상이면 /ko/, /en/처럼 구분합니다. 추가 언어의 메뉴 본문은 별도로 작성해 주세요.</p>
                        </div>
                    </div>

                    <label className="flex items-start justify-between gap-4 rounded-lg border border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-950">
                        <span>
                            <span className="block text-sm font-semibold text-slate-700 dark:text-slate-200">홈페이지 공개</span>
                            <span className="mt-1 block text-xs text-slate-500 dark:text-slate-400">공개하면 방문자가 학회 홈페이지를 이용할 수 있습니다. 비공개 상태에서는 관리자만 설정을 편집할 수 있습니다.</span>
                        </span>
                        <input type="checkbox" checked={published} onChange={event => setPublished(event.target.checked)} disabled={isSaving || optionsSaving}
                            className="h-5 w-5 shrink-0 rounded border-slate-300 accent-blue-600 dark:border-slate-700 dark:accent-blue-500" />
                    </label>

                    <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-5">
                        <DateRangeGroup title="행사 기간" description="학회 개최 기간" startValue={eventStartDate} endValue={eventEndDate} onStartChange={setEventStartDate} onEndChange={setEventEndDate} />
                        <DateRangeGroup title="Early Bird" description="조기 등록 기간" startValue={earlyBirdStartDate} endValue={earlyBirdEndDate} onStartChange={setEarlyBirdStartDate} onEndChange={setEarlyBirdEndDate} accent="emerald" />
                        <DateRangeGroup title="Regular" description="일반 등록 기간" startValue={regularStartDate} endValue={regularEndDate} onStartChange={setRegularStartDate} onEndChange={setRegularEndDate} accent="blue" />
                        <DateRangeGroup title="초록 제출 기간" description="초록 접수 기간" startValue={abstractStartDate} endValue={abstractEndDate} onStartChange={setAbstractStartDate} onEndChange={setAbstractEndDate} />
                        <DateRangeGroup title="발표자료 등록기간" description="초록 채택자의 발표자료 등록 기간" startValue={presentationMaterialStartDate} endValue={presentationMaterialEndDate} onStartChange={setPresentationMaterialStartDate} onEndChange={setPresentationMaterialEndDate} accent="blue" />
                    </div>

                </section>}

                {activeTab === 'fees' && <section className="space-y-4">
                    <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
                        <SectionTitle
                            icon={<CircleDollarSign className="h-5 w-5 text-emerald-500" />}
                            title="등록 구분별 등록비"
                            description="기간별로 달러와 한화 금액을 각각 관리합니다."
                        />
                        <button type="button" onClick={addFeeRow} disabled={isLoadingFees || isSaving} className="inline-flex items-center justify-center gap-2 rounded-lg bg-emerald-600 px-3 py-2 text-xs font-semibold text-white hover:bg-emerald-700 disabled:opacity-50">
                            <Plus className="h-4 w-4" /> 등록 구분 추가
                        </button>
                    </div>

                    <div className="flex flex-col gap-2 rounded-lg bg-slate-50 px-3 py-2.5 text-xs text-slate-500 dark:bg-slate-900/60 dark:text-slate-400 sm:flex-row sm:items-center sm:justify-between">
                        <div className="flex items-start gap-2"><Info className="mt-0.5 h-4 w-4 shrink-0 text-blue-500" /><p>사용 중인 등록 구분은 Early Bird와 Regular의 USD·KRW 금액을 모두 입력해야 합니다.</p></div>
                        <div className="shrink-0 font-semibold">활성 {activeRows.length}개 · 입력 완료 {configuredRows.length}개</div>
                    </div>

                    {isLoadingFees && <div className="py-10 text-center text-sm text-slate-400">등록비 정보를 불러오는 중입니다.</div>}

                    {!isLoadingFees && feeRows.length === 0 && (
                        <div className="py-10 text-center">
                            <CircleDollarSign className="mx-auto h-8 w-8 text-slate-300 dark:text-slate-700" />
                            <p className="mt-2 text-sm font-semibold text-slate-600 dark:text-slate-300">등록 구분이 없습니다.</p>
                            <p className="mt-1 text-xs text-slate-400">등록 구분을 한 개 이상 추가해주세요.</p>
                        </div>
                    )}

                    <div className="space-y-3">
                        {feeRows.map((row, index) => (
                            <FeeCategoryCard
                                key={row.localId}
                                row={row}
                                index={index}
                                rowCount={feeRows.length}
                                onChange={(changes) => updateFeeRow(row.localId, changes)}
                                onMove={moveFeeRow}
                                onRemove={() => void removeFeeRow(row)}
                            />
                        ))}
                    </div>
                </section>}

                <div className="flex justify-end gap-2 border-t border-slate-200 pt-4 dark:border-slate-800">
                    <button type="button" onClick={onClose} disabled={isSaving} className="rounded-lg border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-900">취소</button>
                    <button type="submit" disabled={isSaving || isLoadingFees || feeRows.length === 0} className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-50">
                        <Save className="h-4 w-4" />{isSaving ? '저장 중...' : isEdit ? '수정 저장' : '학회 추가'}
                    </button>
                </div>
            </form>
        </ProgramModalShell>
    );
};

const SectionTitle = ({ icon, title, description }: { icon: ReactNode; title: string; description: string }) => (
    <div>
        <div className="flex items-center gap-2">
            <div>{icon}</div>
            <h4 className="text-sm font-bold text-slate-800 dark:text-slate-100">{title}</h4>
        </div>
        <p className="mt-1 text-xs text-slate-400 dark:text-slate-400">{description}</p>
    </div>
);

const Field = ({ label, required, children }: { label: string; required?: boolean; children: ReactNode }) => (
    <label className="block">
        <span className="mb-1.5 block text-xs font-semibold uppercase text-slate-500">{label}{required && <span className="ml-1 text-rose-500">*</span>}</span>
        {children}
    </label>
);

interface FeeCategoryCardProps {
    row: RegistrationFeeRow;
    index: number;
    rowCount: number;
    onChange: (changes: Partial<RegistrationFeeRow>) => void;
    onMove: (index: number, direction: -1 | 1) => void;
    onRemove: () => void;
}

const FeeCategoryCard = ({ row, index, rowCount, onChange, onMove, onRemove }: FeeCategoryCardProps) => (
    <article className={`rounded-xl border p-4 transition-colors ${row.isUsed === 'Y' ? 'border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-950' : 'border-slate-200 bg-slate-50/70 opacity-75 dark:border-slate-800 dark:bg-slate-900/40'}`}>
        <div className="flex flex-col gap-3 border-b border-slate-100 pb-3 dark:border-slate-800 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex items-center gap-2">
                <span className="inline-flex h-7 min-w-7 items-center justify-center rounded-full bg-slate-100 px-2 text-xs font-bold text-slate-500 dark:bg-slate-800 dark:text-slate-300">{index + 1}</span>
                <div><p className="text-sm font-bold text-slate-800 dark:text-slate-100">{row.categoryName || '새 등록 구분'}</p><p className="mt-0.5 font-mono text-[11px] text-slate-400">{row.categoryCode || 'CATEGORY_CODE'}</p></div>
            </div>
            <div className="flex items-center gap-1">
                <button type="button" onClick={() => onMove(index, -1)} disabled={index === 0} className="rounded-md p-2 text-slate-400 hover:bg-slate-100 disabled:opacity-25 dark:hover:bg-slate-800" aria-label="위로 이동"><ArrowUp className="h-4 w-4" /></button>
                <button type="button" onClick={() => onMove(index, 1)} disabled={index === rowCount - 1} className="rounded-md p-2 text-slate-400 hover:bg-slate-100 disabled:opacity-25 dark:hover:bg-slate-800" aria-label="아래로 이동"><ArrowDown className="h-4 w-4" /></button>
                <button type="button" role="switch" aria-checked={row.isUsed === 'Y'} aria-label="사용 여부" onClick={() => onChange({ isUsed: row.isUsed === 'Y' ? 'N' : 'Y' })} className={`relative mx-2 inline-flex h-6 w-11 items-center rounded-full transition-colors ${row.isUsed === 'Y' ? 'bg-emerald-500' : 'bg-slate-300 dark:bg-slate-700'}`}><span className={`inline-block h-4 w-4 rounded-full bg-white shadow transition-transform ${row.isUsed === 'Y' ? 'translate-x-6' : 'translate-x-1'}`} /></button>
                <button type="button" onClick={onRemove} className="rounded-lg p-2 text-slate-400 hover:bg-rose-50 hover:text-rose-600 dark:hover:bg-rose-950/40" aria-label="등록 구분 삭제"><Trash2 className="h-4 w-4" /></button>
            </div>
        </div>

        <div className="mt-4 grid gap-4 xl:grid-cols-[minmax(260px,0.8fr)_minmax(0,1fr)_minmax(0,1fr)]">
            <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-1">
                <Field label="관리 코드" required><input value={row.categoryCode} onChange={(event) => onChange({ categoryCode: normalizeCodeInput(event.target.value) })} disabled={Boolean(row.seq)} className={`${inputClassName} font-mono text-xs`} placeholder="CATEGORY_CODE" maxLength={50} required /></Field>
                <Field label="등록 구분명" required><input value={row.categoryName} onChange={(event) => onChange({ categoryName: event.target.value })} className={inputClassName} placeholder="예: Student" maxLength={100} required /></Field>
            </div>

            <PeriodFeePanel title="Early Bird" tone="emerald">
                <FeeInput currency="USD" symbol="$" value={row.earlyBirdUsdFee} disabled={row.isUsed === 'N'} onChange={(value) => onChange({ earlyBirdUsdFee: value })} label={`${row.categoryName || '등록 구분'} Early Bird USD 등록비`} />
                <FeeInput currency="KRW" symbol="₩" value={row.earlyBirdKrwFee} disabled={row.isUsed === 'N'} onChange={(value) => onChange({ earlyBirdKrwFee: value })} label={`${row.categoryName || '등록 구분'} Early Bird KRW 등록비`} />
            </PeriodFeePanel>

            <PeriodFeePanel title="Regular" tone="blue">
                <FeeInput currency="USD" symbol="$" value={row.regularUsdFee} disabled={row.isUsed === 'N'} onChange={(value) => onChange({ regularUsdFee: value })} label={`${row.categoryName || '등록 구분'} Regular USD 등록비`} />
                <FeeInput currency="KRW" symbol="₩" value={row.regularKrwFee} disabled={row.isUsed === 'N'} onChange={(value) => onChange({ regularKrwFee: value })} label={`${row.categoryName || '등록 구분'} Regular KRW 등록비`} />
            </PeriodFeePanel>
        </div>
    </article>
);

const PeriodFeePanel = ({ title, tone, children }: { title: string; tone: 'emerald' | 'blue'; children: ReactNode }) => {
    const classes = tone === 'emerald'
        ? 'border-emerald-200 bg-emerald-50/60 dark:border-emerald-900/60 dark:bg-emerald-950/20'
        : 'border-blue-200 bg-blue-50/60 dark:border-blue-900/60 dark:bg-blue-950/20';
    const titleClass = tone === 'emerald' ? 'text-emerald-700 dark:text-emerald-300' : 'text-blue-700 dark:text-blue-300';
    return <div className={`rounded-xl border p-3 ${classes}`}><p className={`mb-3 text-xs font-bold uppercase tracking-wide ${titleClass}`}>{title}</p><div className="grid gap-3 sm:grid-cols-2">{children}</div></div>;
};

interface DateRangeGroupProps {
    title: string;
    description?: string;
    startValue: string;
    endValue: string;
    onStartChange: (value: string) => void;
    onEndChange: (value: string) => void;
    accent?: 'blue' | 'emerald' | 'slate';
}

const DateRangeGroup = ({ title, description, startValue, endValue, onStartChange, onEndChange, accent = 'slate' }: DateRangeGroupProps) => {
    const accentClassName = accent === 'emerald' ? 'text-emerald-500' : accent === 'blue' ? 'text-blue-500' : 'text-slate-500';
    return (
        <div className="rounded-xl border border-slate-200 bg-slate-50/70 p-4 dark:border-slate-800 dark:bg-slate-900/40">
            <div className="mb-3 flex items-start gap-2"><CalendarDays className={`mt-0.5 h-4 w-4 ${accentClassName}`} /><div><h4 className="text-sm font-semibold">{title}</h4>{description && <p className="mt-0.5 text-[11px] text-slate-400">{description}</p>}</div></div>
            <div className="space-y-3">
                <div><label className="mb-1.5 block text-xs font-semibold uppercase text-slate-500">시작일</label><input type="date" value={startValue} onChange={(event) => onStartChange(event.target.value)} className={inputClassName} /></div>
                <div><label className="mb-1.5 block text-xs font-semibold uppercase text-slate-500">종료일</label><input type="date" value={endValue} onChange={(event) => onEndChange(event.target.value)} className={inputClassName} /></div>
            </div>
        </div>
    );
};

interface FeeInputProps {
    currency: 'USD' | 'KRW';
    symbol: string;
    value: string;
    disabled: boolean;
    label: string;
    onChange: (value: string) => void;
}

const FeeInput = ({ currency, symbol, value, disabled, label, onChange }: FeeInputProps) => (
    <label className="block">
        <span className="mb-1.5 block text-[11px] font-bold text-slate-500 dark:text-slate-400">{currency}</span>
        <div className="relative">
            <span className="absolute left-3 top-1/2 -translate-y-1/2 text-sm font-semibold text-slate-400">{symbol}</span>
            <input type="number" min="0" step={currency === 'USD' ? '0.01' : '1'} value={value} disabled={disabled} onChange={(event) => onChange(event.target.value)} className={`${inputClassName} pl-8 text-right font-semibold tabular-nums`} aria-label={label} placeholder="0" required={!disabled} />
        </div>
    </label>
);

function languageLabel(code: string) { return code === 'ko' ? '국문 (ko)' : code === 'en' ? '영문 (en)' : code; }
