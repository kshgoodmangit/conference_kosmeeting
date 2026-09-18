import { DraggableModal } from './DraggableModal';
import { X } from 'lucide-react';
import type { ReactNode } from 'react';

interface ProgramModalShellProps {
    title: string;
    description?: string;
    onClose: () => void;
    children: ReactNode;
    widthClass?: string;
}

export const ProgramModalShell = ({
    title,
    description,
    onClose,
    children,
    widthClass = 'max-w-2xl'
}: ProgramModalShellProps) => (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-3 backdrop-blur-sm md:p-6">
        <DraggableModal className={`flex max-h-[92vh] w-full flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl dark:border-slate-700 dark:bg-slate-950 ${widthClass}`}>
            <div data-modal-drag-handle className="cursor-move select-none touch-none flex items-start justify-between gap-4 border-b border-slate-200 px-5 py-4 dark:border-slate-800">
                <div>
                    <h3 className="text-base font-bold text-slate-900 dark:text-slate-50">{title}</h3>
                    {description && <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">{description}</p>}
                </div>
                <button
                    type="button"
                    onClick={onClose}
                    className="rounded-lg p-2 text-slate-400 hover:bg-slate-100 hover:text-slate-700 dark:hover:bg-slate-900 dark:hover:text-slate-200"
                    aria-label="닫기"
                >
                    <X className="h-5 w-5" />
                </button>
            </div>
            <div className="overflow-y-auto">{children}</div>
        </DraggableModal>
    </div>
);
