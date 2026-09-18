import { DraggableModal } from './DraggableModal';
import { useEffect, useRef, useState, type DragEvent, type FormEvent, type KeyboardEvent } from 'react';
import { Image as ImageIcon, Upload, X } from 'lucide-react';
import type { NotificationType } from './NotificationToast';
import { CountrySelect } from './CountrySelect';
import { speakerButtonClass, speakerInputClass, type Speaker, type SpeakerCountry, type SpeakerType } from './speakerTypes';

interface SpeakerEditModalProps {
    speaker: Speaker | null;
    types: SpeakerType[];
    countries: SpeakerCountry[];
    onClose: () => void;
    onSuccess: (speaker: Speaker) => void;
    onNotify: (type: NotificationType, message: string) => void;
}

export const SpeakerEditModal = ({ speaker, types, countries, onClose, onSuccess, onNotify }: SpeakerEditModalProps) => {
    const [form, setForm] = useState({
        speakerTypeCode: String(speaker?.speakerTypeCode ?? types[0]?.seq ?? ''),
        displayName: speaker?.displayName ?? '', displayNameKo: speaker?.displayNameKo ?? '',
        affiliation: speaker?.affiliation ?? '', department: speaker?.department ?? '',
        positionTitle: speaker?.positionTitle ?? '', countryCode: speaker?.countryCode ?? '',
        biography: speaker?.biography ?? '', homepageUrl: speaker?.homepageUrl ?? '',
        contactEmail: speaker?.contactEmail ?? '', sortOrder: String(speaker?.sortOrder ?? 0),
        featured: speaker?.featured ?? false, enabled: speaker?.enabled ?? true
    });
    const [file, setFile] = useState<File | null>(null);
    const [preview, setPreview] = useState<string | null>(null);
    const [removeImage, setRemoveImage] = useState(false);
    const [isDraggingImage, setIsDraggingImage] = useState(false);
    const [saving, setSaving] = useState(false);
    const savingRef = useRef(false);
    const dialogRef = useRef<HTMLDivElement>(null);
    const fileRef = useRef<HTMLInputElement>(null);
    const dragDepthRef = useRef(0);

    useEffect(() => {
        const previousFocus = document.activeElement as HTMLElement | null;
        const previousOverflow = document.body.style.overflow;
        document.body.style.overflow = 'hidden';
        dialogRef.current?.focus();
        return () => {
            document.body.style.overflow = previousOverflow;
            previousFocus?.focus();
        };
    }, []);

    useEffect(() => () => { if (preview) URL.revokeObjectURL(preview); }, [preview]);

    const updateText = (key: keyof typeof form, value: string) => setForm((current) => ({ ...current, [key]: value }));
    const selectFile = (selected?: File) => {
        if (savingRef.current) return;
        if (fileRef.current) fileRef.current.value = '';
        if (!selected) return;
        if (!/\.(jpe?g|png)$/i.test(selected.name) || !selected.size || selected.size > 2 * 1024 * 1024) {
            onNotify('error', '프로필 이미지는 2MB 이하의 JPG 또는 PNG 파일을 선택해주세요.');
            return;
        }
        setFile(selected);
        setRemoveImage(false);
        setPreview(URL.createObjectURL(selected));
    };

    const handleImageDragEnter = (event: DragEvent<HTMLDivElement>) => {
        event.preventDefault();
        event.stopPropagation();
        if (savingRef.current || !event.dataTransfer.types.includes('Files')) return;
        dragDepthRef.current += 1;
        setIsDraggingImage(true);
    };

    const handleImageDragOver = (event: DragEvent<HTMLDivElement>) => {
        event.preventDefault();
        event.stopPropagation();
        event.dataTransfer.dropEffect = savingRef.current ? 'none' : 'copy';
    };

    const handleImageDragLeave = (event: DragEvent<HTMLDivElement>) => {
        event.preventDefault();
        event.stopPropagation();
        dragDepthRef.current = Math.max(0, dragDepthRef.current - 1);
        if (dragDepthRef.current === 0) setIsDraggingImage(false);
    };

    const handleImageDrop = (event: DragEvent<HTMLDivElement>) => {
        event.preventDefault();
        event.stopPropagation();
        dragDepthRef.current = 0;
        setIsDraggingImage(false);
        selectFile(event.dataTransfer.files?.[0]);
    };

    const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
        if (event.key === 'Escape') {
            event.stopPropagation();
            if (!savingRef.current) onClose();
        }
        if (event.key !== 'Tab') return;
        const focusable = Array.from(dialogRef.current?.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled)') ?? [])
            .filter((element) => element.getClientRects().length > 0 && !element.closest('fieldset:disabled'));
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (!first) { event.preventDefault(); return; }
        if (event.shiftKey && (document.activeElement === first || document.activeElement === dialogRef.current)) {
            event.preventDefault(); last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault(); first.focus();
        }
    };

    const submit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (savingRef.current) return;
        if (!form.displayName.trim() || !form.affiliation.trim() || !form.speakerTypeCode) {
            onNotify('error', '연자 구분, 연자명, 소속 기관을 입력해주세요.');
            return;
        }
        if (!event.currentTarget.checkValidity()) {
            const invalid = event.currentTarget.querySelector<HTMLInputElement>(':invalid');
            onNotify('error', invalid?.validationMessage || '입력값을 확인해주세요.');
            invalid?.focus();
            return;
        }
        if (!Number.isInteger(Number(form.sortOrder)) || Number(form.sortOrder) > 2147483647) {
            onNotify('error', '노출 순서는 0~2,147,483,647 범위의 정수로 입력해주세요.');
            return;
        }
        savingRef.current = true;
        dragDepthRef.current = 0;
        setIsDraggingImage(false);
        setSaving(true);
        try {
            const body = new FormData();
            Object.entries(form).forEach(([key, value]) => body.append(key, typeof value === 'string' ? value.trim() : String(value)));
            body.append('removeProfileImage', String(removeImage));
            if (file) body.append('profileImage', file);
            const response = await fetch(speaker ? `/api/admin/speakers/${speaker.seq}` : '/api/admin/speakers', {
                method: speaker ? 'PUT' : 'POST', body
            });
            if (!response.ok) throw new Error(await response.text() || '연자 저장에 실패했습니다.');
            const saved = await response.json() as Speaker;
            onNotify('success', speaker ? '연자 정보를 수정했습니다.' : '연자를 등록했습니다.');
            onSuccess(saved);
        } catch (error) {
            onNotify('error', error instanceof Error ? error.message : '연자 저장에 실패했습니다.');
        } finally {
            savingRef.current = false;
            setSaving(false);
        }
    };

    const imageUrl = removeImage ? null : preview ?? speaker?.profileImageUrl;
    const textFields = [
        ['displayName', '연자명', 200, true, 'text'], ['displayNameKo', '한글명', 200, false, 'text'],
        ['affiliation', '소속 기관', 255, true, 'text'], ['department', '부서·학과', 255, false, 'text'],
        ['positionTitle', '직책·직위', 255, false, 'text'], ['contactEmail', '연락 이메일 (비공개)', 255, false, 'email'],
        ['homepageUrl', '홈페이지 URL', 1000, false, 'url']
    ] as const;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4 dark:bg-slate-950/60">
            <DraggableModal ref={dialogRef} role="dialog" aria-modal="true" aria-labelledby="speaker-modal-title" tabIndex={-1} onKeyDown={onKeyDown}
                className="flex max-h-[90vh] w-full max-w-2xl flex-col overflow-hidden rounded-2xl bg-white text-slate-900 shadow-2xl outline-none dark:bg-slate-950 dark:text-slate-50">
                <div data-modal-drag-handle className="cursor-move select-none touch-none flex shrink-0 items-center justify-between border-b border-slate-200 px-5 py-4 dark:border-slate-800">
                    <div>
                        <h2 id="speaker-modal-title" className="text-base font-bold">{speaker ? '초청연자 수정' : '초청연자 등록'}</h2>
                        <p className="mt-1 text-xs text-slate-400 dark:text-slate-400">연자 프로필과 홈페이지 공개 정보를 입력합니다.</p>
                    </div>
                    <button type="button" onClick={onClose} disabled={saving} className="rounded-lg p-2 text-slate-400 hover:bg-slate-100 disabled:opacity-50 dark:text-slate-400 dark:hover:bg-slate-900" aria-label="닫기"><X className="h-5 w-5" /></button>
                </div>
                <form onSubmit={(event) => void submit(event)} noValidate className="overflow-y-auto p-5">
                    <fieldset disabled={saving} className="space-y-5">
                        {!types.length && <p className="text-sm text-amber-700 dark:text-amber-300">사용 가능한 연자 구분이 없습니다. 메뉴 추가 SQL의 기본 구분 등록 여부를 확인해주세요.</p>}
                        <div className="grid gap-4 sm:grid-cols-2">
                            <label className="space-y-1.5 text-sm font-semibold text-slate-700 dark:text-slate-200"><span>연자 구분 <span className="text-rose-500 dark:text-rose-400">*</span></span>
                                <select value={form.speakerTypeCode} onChange={(e) => updateText('speakerTypeCode', e.target.value)} required className={speakerInputClass}>
                                    <option value="">선택해주세요</option>
                                    {speaker && !types.some((type) => type.seq === speaker.speakerTypeCode) && <option value={speaker.speakerTypeCode}>{speaker.speakerTypeName || '기존 구분'} (사용 중지)</option>}
                                    {types.map((type) => <option key={type.seq} value={type.seq}>{type.codeName}</option>)}
                                </select>
                            </label>
                            <label className="space-y-1.5 text-sm font-semibold text-slate-700 dark:text-slate-200"><span>국가</span>
                                <CountrySelect countries={countries} value={form.countryCode} valueKey="isoAlpha2"
                                    onChange={(value) => updateText('countryCode', value)} placeholder="선택 안 함"
                                    fallbackLabel={speaker?.countryCode === form.countryCode ? speaker.countryName : undefined} disabled={saving} />
                            </label>
                            {textFields.map(([key, label, maxLength, required, type]) => (
                                <label key={key} className="space-y-1.5 text-sm font-semibold text-slate-700 dark:text-slate-200"><span>{label} {required && <span className="text-rose-500 dark:text-rose-400">*</span>}</span>
                                    <input value={form[key]} onChange={(e) => updateText(key, e.target.value)} maxLength={maxLength} required={required} type={type} className={speakerInputClass} />
                                </label>
                            ))}
                            <label className="space-y-1.5 text-sm font-semibold text-slate-700 dark:text-slate-200"><span>노출 순서 <span className="text-rose-500 dark:text-rose-400">*</span></span><input type="number" min={0} max={2147483647} step={1} required value={form.sortOrder} onChange={(e) => updateText('sortOrder', e.target.value)} className={speakerInputClass} /></label>
                        </div>
                        <label className="block space-y-1.5 text-sm font-semibold text-slate-700 dark:text-slate-200"><span>약력</span><textarea rows={6} maxLength={50000} value={form.biography} onChange={(e) => updateText('biography', e.target.value)} className={speakerInputClass} /></label>
                        <div className="space-y-2 text-sm">
                            <span className="font-semibold text-slate-700 dark:text-slate-200">프로필 이미지</span>
                            <div
                                onDragEnter={handleImageDragEnter}
                                onDragOver={handleImageDragOver}
                                onDragLeave={handleImageDragLeave}
                                onDrop={handleImageDrop}
                                className={`flex flex-col gap-4 rounded-xl border-2 border-dashed p-4 transition-colors sm:flex-row sm:items-center ${
                                    isDraggingImage
                                        ? 'border-blue-500 bg-blue-50 dark:border-blue-400 dark:bg-blue-950/30'
                                        : 'border-slate-300 bg-slate-50/50 dark:border-slate-700 dark:bg-slate-900/30'
                                }`}
                            >
                                <div className="flex h-24 w-24 shrink-0 items-center justify-center overflow-hidden rounded-lg border border-slate-200 bg-white p-2 dark:border-slate-800 dark:bg-slate-900">
                                    {imageUrl ? <img src={imageUrl} alt="프로필 미리보기" draggable={false} className="h-full w-full object-contain" /> : <ImageIcon className="h-8 w-8 text-slate-400 dark:text-slate-500" />}
                                </div>
                                <div className="min-w-0 space-y-2">
                                    <input ref={fileRef} type="file" accept=".jpg,.jpeg,.png" onChange={(e) => selectFile(e.target.files?.[0])} aria-label="프로필 이미지 선택" className="hidden" />
                                    <button type="button" onClick={() => fileRef.current?.click()} className={speakerButtonClass}><Upload className="h-4 w-4" />{imageUrl ? '이미지 교체' : '이미지 선택'}</button>
                                    <p className={`mt-2 text-xs font-medium ${isDraggingImage ? 'text-blue-600 dark:text-blue-300' : 'text-slate-400 dark:text-slate-400'}`}>
                                        {isDraggingImage ? '여기에 프로필 이미지를 놓으세요.' : '파일을 이 영역에 끌어다 놓거나 버튼으로 선택하세요.'}
                                    </p>
                                    <p className="text-xs text-slate-500 dark:text-slate-400">JPG, PNG · 최대 2MB · 1,600만 픽셀 이하</p>
                                    <p className="break-all text-xs">{file?.name || (!removeImage && speaker?.profileImageOriFilename) || '선택한 이미지 없음'}</p>
                                    {imageUrl && <button type="button" className={speakerButtonClass} onClick={() => { setFile(null); setPreview(null); setRemoveImage(true); }}>이미지 제거</button>}
                                </div>
                            </div>
                        </div>
                        <div className="grid gap-4 sm:grid-cols-2">
                            <label className="flex items-center justify-between gap-3 rounded-lg border border-slate-200 px-4 py-3 dark:border-slate-800">
                                <span className="text-sm font-semibold text-slate-700 dark:text-slate-200">홈페이지 공개</span>
                                <input type="checkbox" checked={form.enabled} onChange={(e) => setForm({ ...form, enabled: e.target.checked })} className="h-5 w-5 rounded border-slate-300 accent-blue-600 dark:border-slate-700 dark:accent-blue-500" />
                            </label>
                            <label className="flex items-center justify-between gap-3 rounded-lg border border-slate-200 px-4 py-3 dark:border-slate-800">
                                <span className="text-sm font-semibold text-slate-700 dark:text-slate-200">주요 연자</span>
                                <input type="checkbox" checked={form.featured} onChange={(e) => setForm({ ...form, featured: e.target.checked })} className="h-5 w-5 rounded border-slate-300 accent-blue-600 dark:border-slate-700 dark:accent-blue-500" />
                            </label>
                        </div>
                        <p className="text-xs text-slate-500 dark:text-slate-400">공개 여부와 주요 연자 설정을 저장합니다. 연락 이메일은 관리자용 정보입니다.</p>
                    </fieldset>
                    <div className="mt-5 flex justify-end gap-2 border-t border-slate-200 pt-5 dark:border-slate-800">
                        <button type="button" disabled={saving} onClick={onClose} className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900">취소</button>
                        <button type="submit" disabled={saving || (!speaker && !types.length)} className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-50 dark:bg-blue-600 dark:text-white dark:hover:bg-blue-700">{saving ? '저장 중...' : '저장'}</button>
                    </div>
                </form>
            </DraggableModal>
        </div>
    );
};
