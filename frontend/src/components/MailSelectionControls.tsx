import { Mail } from 'lucide-react';

export const MailSelectionButton = ({ count, disabled, onClick }: {
    count: number;
    disabled: boolean;
    onClick: () => void;
}) => (
    <button type="button" onClick={onClick} disabled={!count || disabled}
        title={count ? '선택한 수신자에게 보낼 메일 작성' : '목록에서 메일 수신자를 먼저 선택해 주세요.'}
        className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-3 py-2 text-xs font-semibold text-white enabled:hover:bg-blue-700 disabled:opacity-50 dark:bg-blue-600 dark:text-white dark:enabled:hover:bg-blue-700">
        <Mail className="h-4 w-4" />메일 발송{count > 0 && ` (${count}명)`}
    </button>
);

export const MailSelectionSummary = ({ count, onClear }: { count: number; onClear: () => void }) => (
    <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-200 px-4 py-3 text-xs dark:border-slate-800 md:px-5">
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
            <span aria-live="polite" className="font-semibold text-blue-600 dark:text-blue-400">메일 수신자 {count}명 선택</span>
            <span className="text-slate-500 dark:text-slate-400">전체 선택은 현재 페이지에 적용됩니다. 페이지 이동 시 유지되며 조회·필터 변경 시 해제됩니다.</span>
        </div>
        <button type="button" onClick={onClear} disabled={!count}
            className="rounded-lg border border-slate-200 px-3 py-2 font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900">선택 해제</button>
    </div>
);

export const MailSelectionCheckbox = ({ checked, indeterminate = false, disabled, label, onChange }: {
    checked: boolean;
    indeterminate?: boolean;
    disabled: boolean;
    label: string;
    onChange: (checked: boolean) => void;
}) => (
    <input type="checkbox" checked={checked} disabled={disabled} aria-label={label}
        ref={(element) => { if (element) element.indeterminate = indeterminate; }}
        onChange={(event) => onChange(event.target.checked)}
        className="h-4 w-4 rounded border-slate-300 accent-blue-600 dark:border-slate-700 dark:accent-blue-500" />
);
