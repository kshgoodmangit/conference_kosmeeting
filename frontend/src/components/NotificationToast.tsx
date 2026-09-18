import { AlertCircle, CheckCircle2, Info, X } from 'lucide-react';
import type { ReactNode } from 'react';
import { createPortal } from 'react-dom';

export type NotificationType = 'success' | 'error' | 'info';

export interface NotificationItem {
    id: number;
    type: NotificationType;
    message: string;
}

interface NotificationToastProps {
    items: NotificationItem[];
    onClose: (id: number) => void;
}

const typeStyles: Record<NotificationType, string> = {
    success: 'border-emerald-200 bg-emerald-50 text-emerald-800 dark:border-emerald-900/60 dark:bg-emerald-950 dark:text-emerald-200',
    error: 'border-rose-200 bg-rose-50 text-rose-800 dark:border-rose-900/60 dark:bg-rose-950 dark:text-rose-200',
    info: 'border-blue-200 bg-blue-50 text-blue-800 dark:border-blue-900/60 dark:bg-blue-950 dark:text-blue-200'
};

const icons: Record<NotificationType, ReactNode> = {
    success: <CheckCircle2 className="w-5 h-5 text-emerald-600 dark:text-emerald-300" />,
    error: <AlertCircle className="w-5 h-5 text-rose-600 dark:text-rose-300" />,
    info: <Info className="w-5 h-5 text-blue-600 dark:text-blue-300" />
};

export const NotificationToast = ({ items, onClose }: NotificationToastProps) => {
    if (items.length === 0) {
        return null;
    }

    return createPortal(
        <div className="pointer-events-none fixed right-4 top-4 z-[300] flex w-[calc(100vw-32px)] max-w-sm flex-col gap-2">
            {items.map((item) => (
                <div
                    key={item.id}
                    role={item.type === 'error' ? 'alert' : 'status'}
                    className={`pointer-events-auto flex items-start gap-3 rounded-lg border px-4 py-3 shadow-lg ${typeStyles[item.type]}`}
                >
                    <div className="mt-0.5">{icons[item.type]}</div>
                    <p className="min-w-0 flex-1 text-sm font-medium leading-5">{item.message}</p>
                    <button
                        type="button"
                        aria-label="알림 닫기"
                        onClick={() => onClose(item.id)}
                        className="rounded p-0.5 opacity-70 hover:opacity-100"
                    >
                        <X className="w-4 h-4" />
                    </button>
                </div>
            ))}
        </div>,
        document.body
    );
};
