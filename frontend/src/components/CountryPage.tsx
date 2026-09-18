import { useEffect, useRef, useState } from 'react';
import {
    ChevronLeft,
    ChevronRight,
    Globe2,
    Search
} from 'lucide-react';
import type { NotificationType } from './NotificationToast';

type UsageValue = 'Y' | 'N';

interface CountryItem {
    seq: number;
    isoAlpha2: string;
    isoAlpha3: string;
    isoNumeric?: string | null;
    countryName: string;
    countryNameKo?: string | null;
    dialCode?: string | null;
    isUsed: UsageValue;
}

interface CountryPageResponse {
    items: CountryItem[];
    page: number;
    size: number;
    totalCount: number;
    usedCount: number;
    unusedCount: number;
    totalPages: number;
}

interface CountryPageProps {
    onNotify: (type: NotificationType, message: string) => void;
}

const PAGE_SIZE = 10;

export const CountryPage = ({ onNotify }: CountryPageProps) => {
    const [countries, setCountries] = useState<CountryItem[]>([]);
    const [searchKeyword, setSearchKeyword] = useState('');
    const [currentPage, setCurrentPage] = useState(1);
    const [totalCount, setTotalCount] = useState(0);
    const [usedCount, setUsedCount] = useState(0);
    const [unusedCount, setUnusedCount] = useState(0);
    const [totalPages, setTotalPages] = useState(1);
    const [isLoading, setIsLoading] = useState(false);
    const [updatingSeq, setUpdatingSeq] = useState<number | null>(null);
    const [errorMessage, setErrorMessage] = useState('');
    const onNotifyRef = useRef(onNotify);

    useEffect(() => {
        onNotifyRef.current = onNotify;
    }, [onNotify]);

    useEffect(() => {
        const controller = new AbortController();

        const loadCountries = async () => {
            setIsLoading(true);
            setErrorMessage('');

            try {
                const params = new URLSearchParams({
                    page: String(currentPage),
                    size: String(PAGE_SIZE),
                    keyword: searchKeyword.trim()
                });
                const response = await fetch(`/api/admin/countries?${params.toString()}`, {
                    signal: controller.signal
                });

                if (!response.ok) {
                    throw new Error(await response.text() || '국가 목록을 불러오지 못했습니다.');
                }

                const data = await response.json() as CountryPageResponse;
                setCountries(data.items);
                setTotalCount(data.totalCount);
                setUsedCount(data.usedCount);
                setUnusedCount(data.unusedCount);
                setTotalPages(data.totalPages);

                if (data.page !== currentPage) {
                    setCurrentPage(data.page);
                }
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') {
                    return;
                }
                const message = error instanceof Error ? error.message : '국가 목록을 불러오지 못했습니다.';
                setErrorMessage(message);
                onNotifyRef.current('error', message);
            } finally {
                setIsLoading(false);
            }
        };

        void loadCountries();
        return () => controller.abort();
    }, [currentPage, searchKeyword]);

    const safeCurrentPage = Math.min(currentPage, totalPages);
    const startIndex = totalCount === 0 ? 0 : (safeCurrentPage - 1) * PAGE_SIZE + 1;
    const endIndex = Math.min((safeCurrentPage - 1) * PAGE_SIZE + countries.length, totalCount);
    const firstPage = Math.max(1, Math.min(safeCurrentPage - 2, Math.max(1, totalPages - 4)));
    const pageNumbers = Array.from(
        { length: Math.min(5, totalPages - firstPage + 1) },
        (_, index) => firstPage + index
    );

    const updateIsUsed = async (country: CountryItem, nextValue: UsageValue) => {
        if (country.isUsed === nextValue || updatingSeq !== null) {
            return;
        }

        setUpdatingSeq(country.seq);
        setErrorMessage('');

        try {
            const formData = new FormData();
            formData.append('isUsed', nextValue);
            const response = await fetch(`/api/admin/countries/${country.seq}/is-used`, {
                method: 'PUT',
                body: formData
            });

            if (!response.ok) {
                throw new Error(await response.text() || '국가 사용 여부를 변경하지 못했습니다.');
            }

            const updatedCountry = await response.json() as CountryItem;
            setCountries((items) => items.map((item) => (
                item.seq === updatedCountry.seq ? updatedCountry : item
            )));

            if (country.isUsed === 'Y') {
                setUsedCount((count) => Math.max(0, count - 1));
                setUnusedCount((count) => count + 1);
            } else {
                setUsedCount((count) => count + 1);
                setUnusedCount((count) => Math.max(0, count - 1));
            }

            onNotifyRef.current(
                'success',
                `${country.countryNameKo || country.countryName} 국가를 ${nextValue === 'Y' ? '사용' : '미사용'}으로 변경했습니다.`
            );
        } catch (error) {
            const message = error instanceof Error ? error.message : '국가 사용 여부를 변경하지 못했습니다.';
            setErrorMessage(message);
            onNotifyRef.current('error', message);
        } finally {
            setUpdatingSeq(null);
        }
    };

    return (
        <section className="rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-950">
            <div className="flex flex-col justify-between gap-4 border-b border-slate-200 p-4 dark:border-slate-800 lg:flex-row lg:items-center md:p-5">
                <div>
                    <div className="flex items-center gap-2">
                        <Globe2 className="shrink-0 h-4 w-4 text-slate-400" />
                        <h3 className="text-sm font-semibold md:text-base">국가 관리</h3>
                    </div>
                    <p className="mt-1 text-xs text-slate-400">국가 코드와 국제전화 번호를 확인하고 사용 여부만 변경할 수 있습니다.</p>
                </div>

                <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
                    <div className="relative">
                        <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                        <input
                            value={searchKeyword}
                            onChange={(event) => {
                                setSearchKeyword(event.target.value);
                                setCurrentPage(1);
                            }}
                            className="w-full rounded-lg border border-slate-200 bg-white py-2 pl-9 pr-3 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50 sm:w-80"
                            placeholder="국가명, ISO 코드, 국가번호 검색"
                        />
                    </div>
                </div>
            </div>

            <div className="grid grid-cols-1 border-b border-slate-200 sm:grid-cols-3 dark:border-slate-800">
                <SummaryCard label="전체 국가" value={`${totalCount.toLocaleString()}개`} />
                <SummaryCard label="사용 국가" value={`${usedCount.toLocaleString()}개`} accent="text-emerald-600 dark:text-emerald-400" />
                <SummaryCard label="미사용 국가" value={`${unusedCount.toLocaleString()}개`} accent="text-slate-500 dark:text-slate-300" />
            </div>

            {errorMessage && (
                <div className="m-4 rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 dark:border-rose-900/60 dark:bg-rose-950/30 dark:text-rose-300 md:m-5">
                    {errorMessage}
                </div>
            )}

            <div className="overflow-x-auto">
                <table className="w-full min-w-[980px] border-collapse text-left text-xs md:text-sm">
                    <thead className="border-b border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900/50">
                        <tr>
                            <th className="p-4">번호</th>
                            <th className="p-4">ISO 코드</th>
                            <th className="p-4">국가명</th>
                            <th className="p-4">한글 국가명</th>
                            <th className="p-4">숫자 코드</th>
                            <th className="p-4">국가번호</th>
                            <th className="p-4">사용 여부</th>
                        </tr>
                    </thead>
                    <tbody>
                        {isLoading && countries.length === 0 && (
                            <tr>
                                <td colSpan={7} className="p-8 text-center text-slate-400">국가 목록을 불러오는 중입니다.</td>
                            </tr>
                        )}

                        {!isLoading && countries.length === 0 && (
                            <tr>
                                <td colSpan={7} className="p-8 text-center text-slate-400">검색된 국가가 없습니다.</td>
                            </tr>
                        )}

                        {countries.map((country) => (
                            <tr key={country.seq} className="border-b border-slate-200 hover:bg-slate-50/60 dark:border-slate-800 dark:hover:bg-slate-900/40">
                                <td className="p-4 font-mono text-slate-400">#{country.seq}</td>
                                <td className="p-4">
                                    <div className="flex items-center gap-2">
                                        <span className="inline-flex min-w-9 justify-center rounded-md bg-blue-50 px-2 py-1 font-mono font-bold text-blue-700 dark:bg-blue-950/40 dark:text-blue-300">
                                            {country.isoAlpha2}
                                        </span>
                                        <span className="font-mono text-slate-500 dark:text-slate-400">{country.isoAlpha3}</span>
                                    </div>
                                </td>
                                <td className="p-4 font-semibold text-slate-900 dark:text-slate-50">{country.countryName}</td>
                                <td className="p-4 text-slate-600 dark:text-slate-300">{country.countryNameKo || '-'}</td>
                                <td className="p-4 font-mono text-slate-500 dark:text-slate-400">{country.isoNumeric || '-'}</td>
                                <td className="p-4 font-mono font-semibold text-slate-700 dark:text-slate-300">{country.dialCode || '-'}</td>
                                <td className="p-4">
                                    <fieldset
                                        aria-label={`${country.countryName} 사용 여부`}
                                        disabled={updatingSeq !== null}
                                        className="flex items-center gap-3 disabled:cursor-wait disabled:opacity-60"
                                    >
                                        <label className="flex cursor-pointer items-center gap-1.5 font-semibold text-emerald-700 dark:text-emerald-300">
                                            <input
                                                type="radio"
                                                name={`country-is-used-${country.seq}`}
                                                value="Y"
                                                checked={country.isUsed === 'Y'}
                                                onChange={() => void updateIsUsed(country, 'Y')}
                                                className="h-4 w-4 border-slate-300 text-emerald-600 focus:ring-emerald-500 dark:border-slate-700 dark:bg-slate-900"
                                            />
                                            사용
                                        </label>
                                        <label className="flex cursor-pointer items-center gap-1.5 font-semibold text-slate-600 dark:text-slate-300">
                                            <input
                                                type="radio"
                                                name={`country-is-used-${country.seq}`}
                                                value="N"
                                                checked={country.isUsed === 'N'}
                                                onChange={() => void updateIsUsed(country, 'N')}
                                                className="h-4 w-4 border-slate-300 text-slate-600 focus:ring-slate-500 dark:border-slate-700 dark:bg-slate-900"
                                            />
                                            미사용
                                        </label>
                                    </fieldset>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            <div className="flex flex-col items-center justify-between gap-4 border-t border-slate-200 p-4 dark:border-slate-800 md:flex-row md:p-5">
                <div className="text-xs font-medium text-slate-400">
                    전체 <span className="font-bold text-slate-900 dark:text-slate-50">{totalCount.toLocaleString()}</span>개 중
                    <span className="font-bold text-slate-900 dark:text-slate-50"> {totalCount === 0 ? '0' : `${startIndex}-${endIndex}`} </span>
                    표시 중입니다.
                </div>

                <div className="flex items-center gap-1.5">
                    <button
                        type="button"
                        disabled={safeCurrentPage === 1}
                        onClick={() => setCurrentPage((page) => Math.max(page - 1, 1))}
                        className="rounded-lg border border-slate-200 p-2 hover:bg-slate-50 disabled:opacity-40 dark:border-slate-800 dark:hover:bg-slate-900"
                    >
                        <ChevronLeft className="h-4 w-4" />
                    </button>

                    {pageNumbers.map((page) => (
                        <button
                            key={page}
                            type="button"
                            onClick={() => setCurrentPage(page)}
                            className={`h-8 w-8 rounded-lg border text-xs font-bold transition-colors ${
                                safeCurrentPage === page
                                    ? 'border-blue-600 bg-blue-600 text-white'
                                    : 'border-slate-200 hover:bg-slate-50 dark:border-slate-800 dark:hover:bg-slate-900'
                            }`}
                        >
                            {page}
                        </button>
                    ))}

                    <button
                        type="button"
                        disabled={safeCurrentPage === totalPages}
                        onClick={() => setCurrentPage((page) => Math.min(page + 1, totalPages))}
                        className="rounded-lg border border-slate-200 p-2 hover:bg-slate-50 disabled:opacity-40 dark:border-slate-800 dark:hover:bg-slate-900"
                    >
                        <ChevronRight className="h-4 w-4" />
                    </button>
                </div>
            </div>
        </section>
    );
};

interface SummaryCardProps {
    label: string;
    value: string;
    accent?: string;
}

const SummaryCard = ({ label, value, accent = 'text-slate-900 dark:text-slate-50' }: SummaryCardProps) => (
    <div className="border-b border-slate-200 p-4 last:border-b-0 dark:border-slate-800 sm:border-b-0 sm:border-r sm:last:border-r-0 md:p-5">
        <p className="text-xs font-medium text-slate-400">{label}</p>
        <p className={`mt-1 text-2xl font-bold ${accent}`}>{value}</p>
    </div>
);
