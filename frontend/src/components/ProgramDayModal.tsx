import { useState, type FormEvent, type ReactNode } from 'react';
import { CalendarDays, Save } from 'lucide-react';
import { ProgramModalShell } from './ProgramModalShell';
import type { NotificationType } from './NotificationToast';
import type { ProgramDay } from './programTypes';

export interface ProgramDayPayload {
    eventDate: string;
    dayNumber: number;
    dayTitle: string | null;
    theme: string | null;
    sortOrder: number;
    enabled: boolean;
}

interface ProgramDayModalProps {
    day: ProgramDay | null;
    nextDayNumber: number;
    defaultDate?: string;
    onClose: () => void;
    onNotify: (type: NotificationType, message: string) => void;
    onSave: (payload: ProgramDayPayload) => Promise<void>;
}

export const ProgramDayModal = ({ day, nextDayNumber, defaultDate, onClose, onNotify, onSave }: ProgramDayModalProps) => {
    const [eventDate, setEventDate] = useState(day?.eventDate ?? defaultDate ?? '');
    const [dayNumber, setDayNumber] = useState(String(day?.dayNumber ?? nextDayNumber));
    const [dayTitle, setDayTitle] = useState(day?.dayTitle ?? '');
    const [theme, setTheme] = useState(day?.theme ?? '');
    const [sortOrder, setSortOrder] = useState(String(day?.sortOrder ?? nextDayNumber * 10));
    const [enabled, setEnabled] = useState(day?.enabled ?? true);
    const [isSaving, setIsSaving] = useState(false);

    const submit = async (event: FormEvent) => {
        event.preventDefault();
        if (!eventDate) {
            onNotify('error', '프로그램 진행 일자를 선택해주세요.');
            return;
        }

        setIsSaving(true);
        try {
            await onSave({
                eventDate,
                dayNumber: Number(dayNumber),
                dayTitle: dayTitle.trim() || null,
                theme: theme.trim() || null,
                sortOrder: Number(sortOrder),
                enabled
            });
        } catch (error) {
            onNotify('error', error instanceof Error ? error.message : '프로그램 일자를 저장하지 못했습니다.');
        } finally {
            setIsSaving(false);
        }
    };

    return (
        <ProgramModalShell
            title={day ? '프로그램 일자 수정' : '프로그램 일자 추가'}
            description="행사 설정에 등록한 전체 행사 기간 안에서 일자를 구성합니다."
            onClose={onClose}
        >
            <form onSubmit={submit} className="space-y-5 p-5">

                <div className="grid gap-4 sm:grid-cols-2">
                    <Field label="행사 일차" required>
                        <input type="number" min="1" required value={dayNumber} onChange={(event) => setDayNumber(event.target.value)} className={inputClass} />
                    </Field>
                    <Field label="진행 일자" required>
                        <div className="relative">
                            <CalendarDays className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                            <input type="date" required value={eventDate} onChange={(event) => setEventDate(event.target.value)} className={`${inputClass} pl-9`} />
                        </div>
                    </Field>
                </div>

                <Field label="일자별 제목" description="DAY 탭에 표시할 주제나 짧은 제목입니다.">
                    <input value={dayTitle} onChange={(event) => setDayTitle(event.target.value)} maxLength={255} placeholder="예: Development" className={inputClass} />
                </Field>

                <Field label="일자별 주제">
                    <input value={theme} onChange={(event) => setTheme(event.target.value)} maxLength={255} placeholder="예: DEVELOPMENT" className={inputClass} />
                </Field>

                <div className="grid gap-4 sm:grid-cols-2">
                    <Field label="표시 순서">
                        <input type="number" min="0" value={sortOrder} onChange={(event) => setSortOrder(event.target.value)} className={inputClass} />
                    </Field>
                    <Field label="사용 여부">
                        <label className="flex h-[42px] items-center gap-3 rounded-lg border border-slate-200 px-3 dark:border-slate-700">
                            <input type="checkbox" checked={enabled} onChange={(event) => setEnabled(event.target.checked)} className="h-4 w-4 rounded border-slate-300 text-blue-600" />
                            <span className="text-sm text-slate-700 dark:text-slate-200">사용자 화면에 사용할 일자</span>
                        </label>
                    </Field>
                </div>

                <div className="flex justify-end gap-2 border-t border-slate-200 pt-4 dark:border-slate-800">
                    <button type="button" onClick={onClose} className={secondaryButtonClass}>취소</button>
                    <button type="submit" disabled={isSaving} className={primaryButtonClass}>
                        <Save className="h-4 w-4" />
                        {isSaving ? '저장 중...' : '저장'}
                    </button>
                </div>
            </form>
        </ProgramModalShell>
    );
};

const Field = ({ label, description, required, children }: { label: string; description?: string; required?: boolean; children: ReactNode }) => (
    <label className="block">
        <span className="mb-1.5 block text-xs font-semibold text-slate-700 dark:text-slate-300">
            {label}{required && <span className="ml-1 text-rose-500">*</span>}
        </span>
        {children}
        {description && <span className="mt-1 block text-[11px] text-slate-400">{description}</span>}
    </label>
);

const inputClass = 'w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm text-slate-900 outline-none focus:border-blue-500 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-50';
const secondaryButtonClass = 'rounded-lg border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-600 hover:bg-slate-50 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-900';
const primaryButtonClass = 'inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-50';
