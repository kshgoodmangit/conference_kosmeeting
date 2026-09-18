import { useEffect, useRef, useState, type ReactNode } from 'react';
import { EllipsisVertical } from 'lucide-react';
import { createPortal } from 'react-dom';

export interface RowAction {
    label: string;
    icon: ReactNode;
    onClick: () => void;
    disabled?: boolean;
    title?: string;
    tone?: 'default' | 'danger';
}

interface Props {
    actions: RowAction[];
    itemLabel: string;
}

export const rowActionButtonClass = 'inline-flex items-center justify-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900';

export const RowActionMenu = ({ actions, itemLabel }: Props) => {
    const [isOpen, setIsOpen] = useState(false);
    const [position, setPosition] = useState({ top: 0, left: 0 });
    const triggerRef = useRef<HTMLButtonElement>(null);

    useEffect(() => {
        if (!isOpen) return;
        const close = () => setIsOpen(false);
        window.addEventListener('click', close);
        window.addEventListener('resize', close);
        window.addEventListener('scroll', close, true);
        return () => {
            window.removeEventListener('click', close);
            window.removeEventListener('resize', close);
            window.removeEventListener('scroll', close, true);
        };
    }, [isOpen]);

    if (actions.length <= 2) {
        return (
            <div className="flex items-center justify-center gap-2">
                {actions.map((action) => (
                    <button key={action.label} type="button" disabled={action.disabled} title={action.title} aria-label={`${itemLabel} ${action.label}`} onClick={action.onClick} className={`${rowActionButtonClass} ${action.tone === 'danger' ? '!text-rose-600 dark:!text-rose-400' : ''}`}>
                        {action.icon}
                    </button>
                ))}
            </div>
        );
    }

    const toggle = (event: React.MouseEvent<HTMLButtonElement>) => {
        event.stopPropagation();
        const rect = event.currentTarget.getBoundingClientRect();
        setPosition({ top: rect.bottom + 4, left: Math.max(8, rect.right - 160) });
        setIsOpen((value) => !value);
    };

    return (
        <>
            <button ref={triggerRef} type="button" onClick={toggle} className="rounded-md p-1.5 text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800" aria-label={`${itemLabel} 기능 메뉴`} aria-haspopup="menu" aria-expanded={isOpen}>
                <EllipsisVertical className="h-4 w-4" />
            </button>
            {isOpen && createPortal(
                <div role="menu" className="fixed z-[100] w-40 rounded-lg border border-slate-200 bg-white py-1 text-left shadow-lg dark:border-slate-800 dark:bg-slate-950" style={position} onClick={(event) => event.stopPropagation()}>
                    {actions.map((action) => (
                        <button key={action.label} type="button" role="menuitem" disabled={action.disabled} title={action.title} onClick={() => { setIsOpen(false); action.onClick(); }} className={`flex w-full items-center gap-2 px-4 py-2 text-sm hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40 dark:hover:bg-slate-900 ${action.tone === 'danger' ? 'text-rose-600 dark:text-rose-400' : ''}`}>
                            {action.icon}{action.label}
                        </button>
                    ))}
                </div>,
                document.body
            )}
        </>
    );
};
