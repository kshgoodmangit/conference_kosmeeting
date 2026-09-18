export function DashboardLoadState({ failed, retry }: { failed: boolean; retry: () => void }) {
    return <section aria-live="polite" aria-busy={!failed} className="rounded-xl border border-slate-200 bg-white p-5 text-sm text-slate-500 shadow-sm dark:border-slate-800 dark:bg-slate-950 dark:text-slate-400">
        <p>{failed ? '통계를 표시할 수 없습니다.' : '대시보드 통계를 집계하고 있습니다...'}</p>
        {failed && <button type="button" onClick={retry} className="mt-3 rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold hover:bg-slate-50 dark:border-slate-800 dark:hover:bg-slate-900">다시 시도</button>}
    </section>;
}
