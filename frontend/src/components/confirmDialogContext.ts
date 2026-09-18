import { createContext, useContext } from 'react';

export type ConfirmTone = 'default' | 'danger';

export interface ConfirmOptions {
    title: string;
    message: string;
    confirmText?: string;
    cancelText?: string;
    tone?: ConfirmTone;
}

export type ConfirmFunction = (options: ConfirmOptions) => Promise<boolean>;

export const ConfirmDialogContext = createContext<ConfirmFunction | null>(null);

export const useConfirm = () => {
    const confirm = useContext(ConfirmDialogContext);
    if (!confirm) {
        throw new Error('useConfirm은 ConfirmDialogProvider 안에서 사용해야 합니다.');
    }
    return confirm;
};
