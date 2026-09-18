import { RECIPIENT_GROUPS, type RecipientGroup } from '../recipientGroups';

interface Props {
    selected: RecipientGroup[];
    counts?: Record<RecipientGroup, number>;
    loading: boolean;
    failed?: boolean;
    onToggle: (group: RecipientGroup) => void;
}
export const RecipientGroupSelector = ({ selected, counts, loading, failed, onToggle }: Props) => (
    <fieldset className="space-y-3">
        <legend className="text-sm font-semibold text-slate-700 dark:text-slate-200">전체 대상 선택 <span className="ml-1 text-xs font-normal text-slate-500 dark:text-slate-400">복수 선택 가능</span></legend>
        <div className="grid gap-3 sm:grid-cols-2">
            {RECIPIENT_GROUPS.map((group) => (
                <label key={group.value} className="flex items-start gap-3 rounded-lg border border-slate-200 bg-white p-3 text-slate-700 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-200">
                    <input type="checkbox" checked={selected.includes(group.value)} onChange={() => onToggle(group.value)} className="mt-0.5 h-5 w-5 shrink-0" />
                    <span className="min-w-0 flex-1">
                        <span className="flex flex-wrap justify-between gap-2 text-sm font-semibold"><span>{group.label}</span><span>{counts ? `${counts[group.value].toLocaleString()}명` : loading ? '집계 중...' : '-'}</span></span>
                        <span className="mt-1 block text-xs text-slate-500 dark:text-slate-400">{group.description}</span>
                    </span>
                </label>
            ))}
        </div>
        <p aria-live="polite" className="text-xs text-slate-500 dark:text-slate-400">{failed ? '인원을 조회하지 못했습니다. 모달을 다시 열어 주세요.' : loading ? '현재 수신 대상 인원을 집계하고 있습니다.' : '그룹별 인원은 모달을 연 시점 기준입니다.'}</p>
    </fieldset>
);
