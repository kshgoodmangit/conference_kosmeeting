import { useEffect, useRef, useState, type KeyboardEvent } from 'react';
import { History, LoaderCircle, RotateCcw, X } from 'lucide-react';
import { DraggableModal } from './DraggableModal';
import { useConfirm } from './confirmDialogContext';
import type { NotificationType } from './NotificationToast';
import { publicMenuPreviewDocument } from '../publicMenuContent';
import { HtmlSourceComparison } from './HtmlSourceComparison';

interface HistoryItem {
    seq: number;
    revisionNo: number;
    menuHtml: string | null;
    operationType: 'INITIAL' | 'SAVE' | 'RESTORE';
    restoredFromSeq: number | null;
    changeMemo: string | null;
    createdByName: string | null;
    createdAt: string;
}
interface HistoryPage {
    items: HistoryItem[];
    total: number;
    currentRevisionNo: number;
}
interface HistoryDetail {
    history: HistoryItem;
    previewHtml: string;
    currentHtml: string | null;
    currentRevisionNo: number;
}
export interface RestoredMenuHtml {
    seq: number;
    menuHtml: string | null;
    htmlRevisionNo: number;
}
interface Props {
    menuSeq: number;
    menuName: string;
    canRestore: () => boolean;
    onClose: () => void;
    onRestored: (menu: RestoredMenuHtml) => void;
    onNotify: (type: NotificationType, message: string) => void;
}

const buttonStyle = 'inline-flex items-center justify-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-40 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-900';
const operationLabels = { INITIAL: '최초 저장', SAVE: '수정', RESTORE: '복원' };
const normalized = (html: string | null) => (html ?? '').trim();
const dateLabel = (date: string) => date.replace('T', ' ').slice(0, 19);

async function readResponse<T>(response: Response): Promise<T> {
    if (!response.ok) {
        const text = await response.text();
        let message = text;
        try {
            const error = JSON.parse(text) as { detail?: string; message?: string };
            message = error.detail || error.message || `요청에 실패했습니다. (${response.status})`;
        } catch { /* Plain-text API errors are already readable. */ }
        throw new Error(message || `요청에 실패했습니다. (${response.status})`);
    }
    return response.json() as Promise<T>;
}

export function MenuHtmlHistoryModal({ menuSeq, menuName, canRestore, onClose, onRestored, onNotify }: Props) {
    const confirm = useConfirm();
    const dialogRef = useRef<HTMLDivElement>(null);
    const callbacks = useRef({ onClose, onRestored, onNotify, canRestore });
    const busyRef = useRef(false);
    const [page, setPage] = useState(0);
    const [result, setResult] = useState<HistoryPage | null>(null);
    const [selectedSeq, setSelectedSeq] = useState<number | null>(null);
    const [detail, setDetail] = useState<HistoryDetail | null>(null);
    const [loading, setLoading] = useState(true);
    const [detailLoading, setDetailLoading] = useState(false);
    const [failed, setFailed] = useState(false);
    const [retry, setRetry] = useState(0);
    const [restoring, setRestoring] = useState(false);
    const [tab, setTab] = useState<'preview' | 'source' | 'compare'>('preview');
    const [memo, setMemo] = useState('');

    useEffect(() => { callbacks.current = { onClose, onRestored, onNotify, canRestore }; }, [onClose, onRestored, onNotify, canRestore]);
    useEffect(() => {
        const trigger = document.activeElement instanceof HTMLElement ? document.activeElement : null;
        const overflow = document.body.style.overflow;
        document.body.style.overflow = 'hidden';
        dialogRef.current?.focus();
        return () => { document.body.style.overflow = overflow; trigger?.focus(); };
    }, []);

    useEffect(() => {
        const abort = new AbortController();
        void (async () => {
            try {
                const data = await readResponse<HistoryPage>(await fetch(`/api/admin/menu-settings/${menuSeq}/html-histories?page=${page}&size=20`, { signal: abort.signal }));
                if (abort.signal.aborted) return;
                setResult(data);
                setSelectedSeq(data.items[0]?.seq ?? null);
                setDetailLoading(data.items.length > 0);
                setFailed(false);
            } catch (error) {
                if (abort.signal.aborted) return;
                setFailed(true);
                callbacks.current.onNotify('error', error instanceof Error ? error.message : '이력을 불러오지 못했습니다.');
            } finally {
                if (!abort.signal.aborted) setLoading(false);
            }
        })();
        return () => abort.abort();
    }, [menuSeq, page, retry]);

    useEffect(() => {
        if (selectedSeq == null) return;
        const abort = new AbortController();
        void (async () => {
            try {
                const data = await readResponse<HistoryDetail>(await fetch(`/api/admin/menu-settings/${menuSeq}/html-histories/${selectedSeq}`, { signal: abort.signal }));
                if (!abort.signal.aborted) setDetail(data);
            } catch (error) {
                if (!abort.signal.aborted) callbacks.current.onNotify('error', error instanceof Error ? error.message : '이력 내용을 불러오지 못했습니다.');
            } finally {
                if (!abort.signal.aborted) setDetailLoading(false);
            }
        })();
        return () => abort.abort();
    }, [menuSeq, selectedSeq, retry]);

    const close = () => { if (!busyRef.current) callbacks.current.onClose(); };
    const keyDown = (event: KeyboardEvent<HTMLDivElement>) => {
        if (event.key === 'Escape') { event.stopPropagation(); event.preventDefault(); close(); }
        if (event.key !== 'Tab' || !dialogRef.current) return;
        const items = Array.from(dialogRef.current.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), textarea, iframe, [tabindex="0"]'));
        const first = items[0];
        const last = items[items.length - 1];
        if (!first) { event.preventDefault(); return; }
        if (event.shiftKey && (document.activeElement === first || document.activeElement === dialogRef.current)) { event.preventDefault(); last.focus(); }
        else if (!event.shiftKey && (document.activeElement === last || document.activeElement === dialogRef.current)) { event.preventDefault(); first.focus(); }
    };

    const restore = async () => {
        if (!detail || busyRef.current || !callbacks.current.canRestore()) return;
        busyRef.current = true;
        setRestoring(true);
        try {
            const accepted = await confirm({
                title: '메뉴 HTML 복원',
                message: `v${detail.history.revisionNo}의 HTML로 복원하시겠습니까?\n현재 내용은 이력에 유지되고 복원 결과는 새 버전으로 저장됩니다. 메뉴명·URL·노출 설정은 바뀌지 않습니다. 공개 중인 메뉴는 사용자 화면에도 반영됩니다.`,
                confirmText: '이 버전으로 복원',
                tone: 'danger'
            });
            if (!accepted || !callbacks.current.canRestore()) return;
            const restored = await readResponse<RestoredMenuHtml>(await fetch(`/api/admin/menu-settings/${menuSeq}/html-histories/${detail.history.seq}/restore`, {
                method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ changeMemo: memo })
            }));
            callbacks.current.onNotify('success', `메뉴 HTML을 반영했습니다. 현재 v${restored.htmlRevisionNo}입니다.`);
            callbacks.current.onRestored(restored);
        } catch (error) {
            callbacks.current.onNotify('error', error instanceof Error ? error.message : '복원하지 못했습니다.');
        } finally { busyRef.current = false; setRestoring(false); }
    };

    const selectHistory = (seq: number) => {
        if (seq === selectedSeq) return;
        setDetail(null); setDetailLoading(true); setMemo(''); setSelectedSeq(seq);
    };
    const goPage = (next: number) => {
        setLoading(true); setDetail(null); setSelectedSeq(null); setPage(next);
    };
    const sameHtml = detail && normalized(detail.currentHtml) === normalized(detail.history.menuHtml);

    return <div className="fixed inset-0 z-[110] flex items-center justify-center bg-slate-950/60 dark:bg-slate-950/60" onMouseDown={(e) => { if (e.target === e.currentTarget) close(); }}>
        <DraggableModal ref={dialogRef} role="dialog" aria-modal="true" aria-labelledby="menu-history-title" tabIndex={-1} onKeyDown={keyDown} className="flex h-full w-full max-w-none flex-col overflow-hidden bg-white text-slate-900 shadow-2xl outline-none dark:bg-slate-950 dark:text-slate-50">
            <div data-modal-drag-handle className="flex shrink-0 cursor-move select-none touch-none items-start justify-between gap-4 border-b border-slate-200 px-5 py-4 dark:border-slate-800">
                <div><h2 id="menu-history-title" className="flex items-center gap-2 text-base font-bold"><History className="h-4 w-4" />메뉴 HTML 변경 이력</h2><p className="mt-1 text-xs text-slate-500 dark:text-slate-400">{menuName} · HTML만 복원하며 이미지·파일은 복원하지 않습니다.</p></div>
                <button type="button" onClick={close} disabled={restoring} aria-label="이력 닫기" className="rounded-lg p-2 text-slate-500 hover:bg-slate-100 disabled:opacity-40 dark:text-slate-400 dark:hover:bg-slate-800"><X className="h-5 w-5" /></button>
            </div>
            <div className="grid min-h-0 flex-1 overflow-y-auto lg:grid-cols-[280px_minmax(0,1fr)]">
                <aside className="border-b border-slate-200 p-4 dark:border-slate-800 lg:overflow-y-auto lg:border-b-0 lg:border-r">
                    <p className="mb-3 text-xs text-slate-500 dark:text-slate-400">{loading ? '이력 조회 중…' : failed ? '조회 실패' : `총 ${result?.total ?? 0}개 · 현재 v${result?.currentRevisionNo ?? 0}`}</p>
                    {loading ? <p role="status" className="py-6 text-sm">이력을 불러오는 중입니다.</p> : failed ? <button type="button" className={buttonStyle} onClick={() => { setLoading(true); setRetry((v) => v + 1); }}>다시 시도</button> : result?.items.length ? <ul className="space-y-2">{result.items.map((item) => <li key={item.seq}>
                        <button type="button" disabled={restoring} aria-pressed={selectedSeq === item.seq} onClick={() => selectHistory(item.seq)} className={`w-full rounded-lg border p-3 text-left disabled:opacity-50 ${selectedSeq === item.seq ? 'border-blue-500 bg-blue-50 dark:border-blue-500 dark:bg-blue-950/40' : 'border-slate-200 bg-white hover:bg-slate-50 dark:border-slate-800 dark:bg-slate-950 dark:hover:bg-slate-900'}`}>
                            <span className="flex justify-between text-sm font-semibold"><span>v{item.revisionNo} {item.revisionNo === result.currentRevisionNo && '· 현재'}</span><span className="text-xs">{operationLabels[item.operationType]}</span></span>
                            <span className="mt-1 block text-xs text-slate-500 dark:text-slate-400">{dateLabel(item.createdAt)}<br />{item.createdByName || '작업자 정보 없음'}</span>
                            {item.changeMemo && <span className="mt-2 block break-words text-xs">{item.changeMemo}</span>}
                        </button>
                    </li>)}</ul> : <p className="py-6 text-sm">저장된 HTML 이력이 없습니다.</p>}
                    <div className="mt-4 flex items-center justify-between gap-2"><button type="button" className={buttonStyle} disabled={loading || restoring || page === 0} onClick={() => goPage(page - 1)}>이전</button><span className="text-xs">{page + 1} / {Math.max(1, Math.ceil((result?.total ?? 0) / 20))}</span><button type="button" className={buttonStyle} disabled={loading || restoring || (page + 1) * 20 >= (result?.total ?? 0)} onClick={() => goPage(page + 1)}>다음</button></div>
                </aside>
                <div className="flex min-w-0 flex-col gap-4 p-4 lg:overflow-y-auto md:p-5">
                    {detailLoading ? <p role="status">HTML 내용을 불러오는 중입니다.</p> : detail ? <>
                        <div className="flex flex-wrap items-center justify-between gap-3"><h3 className="text-sm font-semibold">v{detail.history.revisionNo} · {operationLabels[detail.history.operationType]}</h3><div className="flex gap-2">{(['preview', 'source', 'compare'] as const).map((value) => <button type="button" key={value} aria-pressed={tab === value} onClick={() => setTab(value)} className={buttonStyle}>{value === 'preview' ? '미리보기' : value === 'source' ? 'HTML 소스' : '현재와 비교'}</button>)}</div></div>
                        {tab === 'preview' ? <><p className="text-xs text-slate-500 dark:text-slate-400">현재 사용자 CSS와 보안 정책으로 표시합니다. 빈 HTML은 빈 화면으로 보입니다.</p><iframe title="이력 HTML 미리보기" sandbox="" srcDoc={publicMenuPreviewDocument(detail.previewHtml)} className="min-h-[280px] w-full flex-1 rounded-lg border border-slate-200 bg-white dark:border-slate-700 dark:bg-white" /></> : tab === 'source' ? <pre tabIndex={0} className="min-h-[280px] flex-1 overflow-auto rounded-lg border border-slate-200 bg-slate-50 p-3 text-xs dark:border-slate-800 dark:bg-slate-900">{detail.history.menuHtml || '(빈 HTML)'}</pre> : <SourceComparison previous={detail.history.menuHtml ?? ''} current={detail.currentHtml ?? ''} revision={detail.history.revisionNo} currentRevision={detail.currentRevisionNo} />}
                        {sameHtml && <p className="text-xs text-slate-500 dark:text-slate-400">현재 저장된 HTML과 동일합니다. 복원 이력을 중복 생성하지 않습니다.</p>}
                        <label className="block space-y-1.5 text-sm font-semibold">복원 메모 <span className="text-xs font-normal text-slate-500 dark:text-slate-400">(선택)</span><input value={memo} maxLength={500} disabled={restoring} onChange={(e) => setMemo(e.target.value)} className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm font-normal text-slate-900 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50" /></label>
                    </> : <p className="text-sm text-slate-500 dark:text-slate-400">{selectedSeq ? '내용을 불러오지 못했습니다.' : '확인할 이력을 선택해 주세요.'}{selectedSeq && <button type="button" className={`${buttonStyle} ml-2`} onClick={() => { setDetailLoading(true); setRetry((v) => v + 1); }}>다시 시도</button>}</p>}
                </div>
            </div>
            <div className="flex shrink-0 justify-end gap-2 border-t border-slate-200 px-5 py-4 dark:border-slate-800"><button type="button" className={buttonStyle} onClick={close} disabled={restoring}>닫기</button><button type="button" onClick={() => void restore()} disabled={restoring || loading || detailLoading || !detail || Boolean(sameHtml)} className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-40 dark:bg-blue-600 dark:text-white dark:hover:bg-blue-700">{restoring ? <LoaderCircle className="h-4 w-4 animate-spin" /> : <RotateCcw className="h-4 w-4" />}{restoring ? '복원 확인·처리 중…' : '이 버전으로 복원'}</button></div>
        </DraggableModal>
    </div>;
}

function SourceComparison({ previous, current, revision, currentRevision }: { previous: string; current: string; revision: number; currentRevision: number }) {
    return <HtmlSourceComparison key={`${revision}:${currentRevision}`} previous={previous} current={current} revision={revision} currentRevision={currentRevision} />;
}
