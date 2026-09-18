import { DraggableModal } from './DraggableModal';
import { useEffect, useRef, useState } from 'react';
import {
    CalendarDays,
    Network,
    Pencil,
    Plus,
    RefreshCw,
    ShieldCheck,
    Trash2,
    X
} from 'lucide-react';
import type { NotificationType } from './NotificationToast';
import { rowActionButtonClass } from './RowActionMenu';
import { useConfirm } from './confirmDialogContext';

interface AdminIpAllowlistItem {
    seq: number;
    ruleName: string;
    ipCidr: string;
    description?: string | null;
    useStartDate?: string | null;
    useEndDate?: string | null;
    enabled: boolean;
    activeNow: boolean;
    createdByAdminName?: string | null;
    updatedByAdminName?: string | null;
    createdAt?: string | null;
    updatedAt?: string | null;
}

interface AdminIpAccessStatus {
    enabled: boolean;
    cachedRuleCount: number;
    currentIp: string;
    reloadedAt?: string | null;
}

interface RuleForm {
    seq?: number;
    ruleName: string;
    ipCidr: string;
    description: string;
    useStartDate: string;
    useEndDate: string;
    enabled: boolean;
}

interface AdminIpAllowlistPageProps {
    onNotify: (type: NotificationType, message: string) => void;
}

const emptyForm = (): RuleForm => ({
    ruleName: '',
    ipCidr: '',
    description: '',
    useStartDate: '',
    useEndDate: '',
    enabled: true
});

const dateTimeFormatter = new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
});

export const AdminIpAllowlistPage = ({ onNotify }: AdminIpAllowlistPageProps) => {
    const confirm = useConfirm();
    const onNotifyRef = useRef(onNotify);
    const [items, setItems] = useState<AdminIpAllowlistItem[]>([]);
    const [status, setStatus] = useState<AdminIpAccessStatus | null>(null);
    const [isLoading, setIsLoading] = useState(false);
    const [isReloading, setIsReloading] = useState(false);
    const [isSaving, setIsSaving] = useState(false);
    const [deletingSeq, setDeletingSeq] = useState<number | null>(null);
    const [errorMessage, setErrorMessage] = useState('');
    const [form, setForm] = useState<RuleForm | null>(null);
    const [reloadKey, setReloadKey] = useState(0);

    useEffect(() => {
        onNotifyRef.current = onNotify;
    }, [onNotify]);

    useEffect(() => {
        const controller = new AbortController();

        const load = async () => {
            setIsLoading(true);
            setErrorMessage('');
            try {
                const [listResponse, statusResponse] = await Promise.all([
                    fetch('/api/admin/ip-allowlist', { signal: controller.signal }),
                    fetch('/api/admin/ip-allowlist/status', { signal: controller.signal })
                ]);
                if (!listResponse.ok) {
                    throw new Error(await listResponse.text() || '접근 허용 IP 목록을 불러오지 못했습니다.');
                }
                if (!statusResponse.ok) {
                    throw new Error(await statusResponse.text() || 'IP 접근제어 상태를 불러오지 못했습니다.');
                }
                setItems(await listResponse.json() as AdminIpAllowlistItem[]);
                setStatus(await statusResponse.json() as AdminIpAccessStatus);
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') {
                    return;
                }
                const message = error instanceof Error ? error.message : '접근 허용 IP 정보를 불러오지 못했습니다.';
                setErrorMessage(message);
                onNotifyRef.current('error', message);
            } finally {
                setIsLoading(false);
            }
        };

        void load();
        return () => controller.abort();
    }, [reloadKey]);

    const openCreate = (useCurrentIp = false) => {
        setForm({
            ...emptyForm(),
            ipCidr: useCurrentIp ? (status?.currentIp ?? '') : ''
        });
    };

    const openEdit = (item: AdminIpAllowlistItem) => {
        setForm({
            seq: item.seq,
            ruleName: item.ruleName,
            ipCidr: item.ipCidr,
            description: item.description ?? '',
            useStartDate: item.useStartDate ?? '',
            useEndDate: item.useEndDate ?? '',
            enabled: item.enabled
        });
    };

    const handleSave = async () => {
        if (!form || isSaving) {
            return;
        }
        if (!form.ruleName.trim() || !form.ipCidr.trim()) {
            onNotifyRef.current('error', '규칙명과 IP 또는 CIDR을 입력해 주세요.');
            return;
        }
        if (form.useStartDate && form.useEndDate && form.useEndDate < form.useStartDate) {
            onNotifyRef.current('error', '사용 종료일은 시작일보다 빠를 수 없습니다.');
            return;
        }

        setIsSaving(true);
        try {
            const response = await fetch(
                form.seq ? `/api/admin/ip-allowlist/${form.seq}` : '/api/admin/ip-allowlist',
                {
                    method: form.seq ? 'PUT' : 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        ruleName: form.ruleName.trim(),
                        ipCidr: form.ipCidr.trim(),
                        description: form.description.trim() || null,
                        useStartDate: form.useStartDate || null,
                        useEndDate: form.useEndDate || null,
                        enabled: form.enabled
                    })
                }
            );
            if (!response.ok) {
                throw new Error(await response.text() || '접근 허용 IP 규칙을 저장하지 못했습니다.');
            }
            onNotifyRef.current('success', form.seq ? '접근 허용 IP 규칙을 수정했습니다.' : '접근 허용 IP 규칙을 등록했습니다.');
            setForm(null);
            setReloadKey((value) => value + 1);
        } catch (error) {
            onNotifyRef.current('error', error instanceof Error ? error.message : '접근 허용 IP 규칙을 저장하지 못했습니다.');
        } finally {
            setIsSaving(false);
        }
    };

    const handleDelete = async (item: AdminIpAllowlistItem) => {
        if (!await confirm({
            title: '접근 허용 IP 삭제',
            message: `'${item.ruleName}' 규칙(${item.ipCidr})을 삭제하시겠습니까?`,
            confirmText: '삭제',
            tone: 'danger'
        })) {
            return;
        }

        setDeletingSeq(item.seq);
        try {
            const response = await fetch(`/api/admin/ip-allowlist/${item.seq}`, { method: 'DELETE' });
            if (!response.ok) {
                throw new Error(await response.text() || '접근 허용 IP 규칙을 삭제하지 못했습니다.');
            }
            onNotifyRef.current('success', '접근 허용 IP 규칙을 삭제했습니다.');
            setReloadKey((value) => value + 1);
        } catch (error) {
            onNotifyRef.current('error', error instanceof Error ? error.message : '접근 허용 IP 규칙을 삭제하지 못했습니다.');
        } finally {
            setDeletingSeq(null);
        }
    };

    const handleCacheReload = async () => {
        setIsReloading(true);
        try {
            const response = await fetch('/api/admin/ip-allowlist/reload', { method: 'POST' });
            if (!response.ok) {
                throw new Error(await response.text() || '접근 허용 IP 캐시를 다시 불러오지 못했습니다.');
            }
            setStatus(await response.json() as AdminIpAccessStatus);
            setReloadKey((value) => value + 1);
            onNotifyRef.current('success', 'DB의 접근 허용 IP를 메모리 캐시에 다시 불러왔습니다.');
        } catch (error) {
            onNotifyRef.current('error', error instanceof Error ? error.message : '접근 허용 IP 캐시를 다시 불러오지 못했습니다.');
        } finally {
            setIsReloading(false);
        }
    };

    return (
        <section className="space-y-4">
            <div className="rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-950">
                <div className="flex flex-col justify-between gap-4 border-b border-slate-200 p-4 dark:border-slate-800 md:flex-row md:items-center md:p-5">
                    <div>
                        <div className="flex items-center gap-2">
                            <Network className="shrink-0 h-5 w-5 text-blue-500" />
                            <h3 className="text-sm font-semibold md:text-base">접근허용 IP 관리</h3>
                        </div>
                        <p className="mt-1 text-xs text-slate-400">관리자 화면과 API에 접근할 수 있는 IP 및 CIDR 범위를 관리합니다.</p>
                    </div>
                    <div className="flex flex-wrap gap-2">
                        <button type="button" onClick={handleCacheReload} disabled={isReloading} className="inline-flex items-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900">
                            <RefreshCw className={`h-4 w-4 ${isReloading ? 'animate-spin' : ''}`} />
                            DB에서 다시 로딩
                        </button>
                        <button type="button" onClick={() => openCreate(true)} className="inline-flex items-center gap-2 rounded-lg border border-blue-200 bg-blue-50 px-3 py-2 text-xs font-semibold text-blue-700 hover:bg-blue-100 dark:border-blue-900 dark:bg-blue-950/40 dark:text-blue-300">
                            <ShieldCheck className="h-4 w-4" />
                            현재 IP 추가
                        </button>
                        <button type="button" onClick={() => openCreate()} className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-3 py-2 text-xs font-semibold text-white hover:bg-blue-700">
                            <Plus className="h-4 w-4" />
                            IP 규칙 추가
                        </button>
                    </div>
                </div>

                <div className="grid grid-cols-1 border-b border-slate-200 sm:grid-cols-4 dark:border-slate-800">
                    <StatusCard label="접근제어 설정" value={status?.enabled ? '사용 중' : '사용 안 함'} accent={status?.enabled ? 'text-emerald-600 dark:text-emerald-400' : 'text-amber-600 dark:text-amber-400'} />
                    <StatusCard label="현재 접속 IP" value={status?.currentIp ?? '-'} mono />
                    <StatusCard label="활성 캐시 규칙" value={`${status?.cachedRuleCount ?? 0}개`} />
                    <StatusCard label="마지막 캐시 로딩" value={formatDateTime(status?.reloadedAt)} />
                </div>

                {errorMessage && <div className="m-4 rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 dark:border-rose-900/60 dark:bg-rose-950/30 dark:text-rose-300 md:m-5">{errorMessage}</div>}

                <div className="overflow-x-auto">
                    <table className="w-full min-w-[1100px] border-collapse text-left text-xs md:text-sm">
                        <thead className="border-b border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900/50">
                            <tr><th className="p-4">규칙명</th><th className="p-4">IP / CIDR</th><th className="p-4">사용기간</th><th className="p-4">상태</th><th className="p-4">설명</th><th className="p-4">최종 수정</th><th className="p-4 text-right">기능</th></tr>
                        </thead>
                        <tbody>
                            {isLoading && items.length === 0 && <tr><td colSpan={7} className="p-10 text-center text-slate-400">접근 허용 IP 목록을 불러오는 중입니다.</td></tr>}
                            {!isLoading && items.length === 0 && <tr><td colSpan={7} className="p-10 text-center text-slate-400">등록된 접근 허용 IP 규칙이 없습니다.</td></tr>}
                            {items.map((item) => (
                                <tr key={item.seq} className="border-b border-slate-200 hover:bg-slate-50/60 dark:border-slate-800 dark:hover:bg-slate-900/40">
                                    <td className="p-4 font-semibold text-slate-900 dark:text-slate-50">{item.ruleName}</td>
                                    <td className="p-4"><code className="rounded bg-slate-100 px-2 py-1 font-mono text-blue-700 dark:bg-slate-900 dark:text-blue-300">{item.ipCidr}</code></td>
                                    <td className="p-4 text-slate-600 dark:text-slate-300"><span className="inline-flex items-center gap-1.5"><CalendarDays className="h-3.5 w-3.5 text-slate-400" />{formatPeriod(item)}</span></td>
                                    <td className="p-4"><span className={`inline-flex rounded-full px-2.5 py-1 text-[11px] font-semibold ${item.activeNow ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300' : 'bg-slate-100 text-slate-500 dark:bg-slate-900 dark:text-slate-400'}`}>{item.activeNow ? '현재 허용' : item.enabled ? '기간 외' : '미사용'}</span></td>
                                    <td className="max-w-64 truncate p-4 text-slate-500 dark:text-slate-400" title={item.description ?? ''}>{item.description || '-'}</td>
                                    <td className="p-4 text-slate-500 dark:text-slate-400"><div>{item.updatedByAdminName || item.createdByAdminName || '-'}</div><div className="mt-1 text-[11px]">{formatDateTime(item.updatedAt || item.createdAt)}</div></td>
                                    <td className="p-4"><div className="flex justify-end gap-2"><button type="button" onClick={() => openEdit(item)} className={rowActionButtonClass} aria-label={`${item.ruleName} 수정`}><Pencil className="h-4 w-4" /></button><button type="button" onClick={() => handleDelete(item)} disabled={deletingSeq === item.seq} className={`${rowActionButtonClass} !text-rose-600 dark:!text-rose-400`} aria-label={`${item.ruleName} 삭제`}><Trash2 className="h-4 w-4" /></button></div></td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            </div>

            {form && <RuleModal form={form} isSaving={isSaving} onChange={setForm} onClose={() => setForm(null)} onSave={handleSave} />}
        </section>
    );
};

const RuleModal = ({ form, isSaving, onChange, onClose, onSave }: { form: RuleForm; isSaving: boolean; onChange: (form: RuleForm) => void; onClose: () => void; onSave: () => void }) => (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4 backdrop-blur-sm">
        <DraggableModal className="w-full max-w-xl overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl dark:border-slate-800 dark:bg-slate-950">
            <div data-modal-drag-handle className="cursor-move select-none touch-none flex items-center justify-between border-b border-slate-200 px-5 py-4 dark:border-slate-800"><div><h3 className="font-semibold">{form.seq ? '접근 허용 IP 수정' : '접근 허용 IP 추가'}</h3><p className="mt-1 text-xs text-slate-400">단일 IP 또는 CIDR 범위를 입력할 수 있습니다.</p></div><button type="button" onClick={onClose} className="rounded-lg p-2 text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-900"><X className="h-5 w-5" /></button></div>
            <div className="grid gap-4 p-5 sm:grid-cols-2">
                <label className="space-y-1.5 sm:col-span-2"><span className="text-xs font-semibold text-slate-600 dark:text-slate-300">규칙명 *</span><input value={form.ruleName} maxLength={100} onChange={(event) => onChange({ ...form, ruleName: event.target.value })} className={inputClass} placeholder="예: 본사 관리자 네트워크" /></label>
                <label className="space-y-1.5 sm:col-span-2"><span className="text-xs font-semibold text-slate-600 dark:text-slate-300">IP 또는 CIDR *</span><input value={form.ipCidr} maxLength={50} onChange={(event) => onChange({ ...form, ipCidr: event.target.value })} className={`${inputClass} font-mono`} placeholder="203.0.113.10 또는 203.0.113.0/24" /><span className="block text-[11px] text-slate-400">단일 IPv4는 /32, IPv6는 /128로 자동 정규화됩니다.</span></label>
                <label className="space-y-1.5"><span className="text-xs font-semibold text-slate-600 dark:text-slate-300">사용 시작일</span><input type="date" value={form.useStartDate} onChange={(event) => onChange({ ...form, useStartDate: event.target.value })} className={inputClass} /></label>
                <label className="space-y-1.5"><span className="text-xs font-semibold text-slate-600 dark:text-slate-300">사용 종료일</span><input type="date" value={form.useEndDate} onChange={(event) => onChange({ ...form, useEndDate: event.target.value })} className={inputClass} /></label>
                <label className="space-y-1.5 sm:col-span-2"><span className="text-xs font-semibold text-slate-600 dark:text-slate-300">설명</span><textarea value={form.description} maxLength={500} rows={3} onChange={(event) => onChange({ ...form, description: event.target.value })} className={`${inputClass} resize-y`} placeholder="허용 목적이나 담당 부서를 입력하세요." /></label>
                <label className="flex items-center gap-2 text-sm font-semibold text-slate-700 dark:text-slate-300 sm:col-span-2"><input type="checkbox" checked={form.enabled} onChange={(event) => onChange({ ...form, enabled: event.target.checked })} className="h-4 w-4 rounded border-slate-300 text-blue-600" />이 규칙 사용</label>
            </div>
            <div className="flex justify-end gap-2 border-t border-slate-200 px-5 py-4 dark:border-slate-800"><button type="button" onClick={onClose} className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-600 hover:bg-slate-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900">취소</button><button type="button" onClick={onSave} disabled={isSaving} className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-50">{isSaving ? '저장 중...' : '저장'}</button></div>
        </DraggableModal>
    </div>
);

const StatusCard = ({ label, value, accent = 'text-slate-900 dark:text-slate-50', mono = false }: { label: string; value: string; accent?: string; mono?: boolean }) => <div className="border-b border-slate-200 p-4 last:border-b-0 sm:border-b-0 sm:border-r sm:last:border-r-0 dark:border-slate-800"><div className="text-[11px] font-semibold uppercase tracking-wide text-slate-400">{label}</div><div className={`mt-1.5 truncate text-sm font-bold ${accent} ${mono ? 'font-mono' : ''}`} title={value}>{value}</div></div>;

const inputClass = 'w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 outline-none focus:border-blue-500 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50';

const formatDateTime = (value?: string | null) => {
    if (!value) return '-';
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? '-' : dateTimeFormatter.format(date);
};

const formatPeriod = (item: AdminIpAllowlistItem) => `${item.useStartDate || '제한 없음'} ~ ${item.useEndDate || '제한 없음'}`;
