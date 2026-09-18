import { createContext, useContext } from 'react';

export interface ExcelDownloadFilterSummary {
    label: string;
    value: string;
}

export interface ExcelDownloadDialogOptions {
    menuName: string;
    filters?: ExcelDownloadFilterSummary[];
    execute: (reason: string) => Promise<void>;
    onError: (message: string) => void;
}

export type ExcelDownloadDialogFunction = (options: ExcelDownloadDialogOptions) => Promise<boolean>;

export const ExcelDownloadDialogContext = createContext<ExcelDownloadDialogFunction | null>(null);

export const useExcelDownloadDialog = () => {
    const value = useContext(ExcelDownloadDialogContext);
    if (!value) {
        throw new Error('useExcelDownloadDialog는 ExcelDownloadReasonDialogProvider 안에서 사용해야 합니다.');
    }
    return value;
};
