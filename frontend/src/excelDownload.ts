export interface ExcelDownloadResponse {
    logId: string | null;
    filename: string;
}

export const downloadExcelFile = async (
    endpoint: string,
    payload: Record<string, unknown>,
    fallbackFilename: string
): Promise<ExcelDownloadResponse> => {
    const response = await fetch(endpoint, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            Accept: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
        },
        body: JSON.stringify(payload)
    });

    if (!response.ok) {
        throw new Error(await response.text() || '엑셀 파일 다운로드에 실패했습니다.');
    }

    const blob = await response.blob();
    const filename = filenameFromDisposition(response.headers.get('content-disposition')) || fallbackFilename;
    const url = URL.createObjectURL(blob);
    try {
        const link = document.createElement('a');
        link.href = url;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        link.remove();
    } finally {
        URL.revokeObjectURL(url);
    }

    return {
        logId: response.headers.get('X-Excel-Download-Log-Id'),
        filename
    };
};

const filenameFromDisposition = (value: string | null) => {
    if (!value) return null;
    const utf8Match = value.match(/filename\*=UTF-8''([^;]+)/i);
    if (utf8Match?.[1]) {
        try {
            return decodeURIComponent(utf8Match[1].replace(/^"|"$/g, ''));
        } catch {
            return utf8Match[1].replace(/^"|"$/g, '');
        }
    }
    const plainMatch = value.match(/filename="?([^";]+)"?/i);
    return plainMatch?.[1] ?? null;
};
