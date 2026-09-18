import { downloadExcelFile } from '../excelDownload';

interface AbstractExportFilters {
    searchKeyword: string;
    presentationTypeCode: string;
    acceptedPresentationTypeCode: string;
    categoryCode: string;
    status: string;
}

export const downloadAbstractsToXlsx = async (filters: AbstractExportFilters, reason: string) => {
    await downloadExcelFile('/api/admin/abstracts/export', {
        reason,
        keyword: filters.searchKeyword.trim(),
        presentationTypeCode: filters.presentationTypeCode ? Number(filters.presentationTypeCode) : null,
        acceptedPresentationTypeCode: filters.acceptedPresentationTypeCode ? Number(filters.acceptedPresentationTypeCode) : null,
        categoryCode: filters.categoryCode ? Number(filters.categoryCode) : null,
        status: filters.status || null
    }, 'abstracts.xlsx');
};
