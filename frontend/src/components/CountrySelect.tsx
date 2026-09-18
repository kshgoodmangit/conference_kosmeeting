import { useMemo } from 'react';
import { SearchableSelect, type SearchableSelectOption } from './SearchableSelect';

export interface CountrySelectOption {
    seq?: number;
    countryName: string;
    countryNameKo?: string | null;
    isoAlpha2?: string | null;
    isoAlpha3?: string | null;
    dialCode?: string | null;
}

interface CountrySelectProps {
    countries: CountrySelectOption[];
    value: string;
    onChange: (value: string) => void;
    valueKey?: 'countryName' | 'isoAlpha2' | 'seq';
    ariaLabel?: string;
    inputId?: string;
    placeholder?: string;
    disabled?: boolean;
    loading?: boolean;
    required?: boolean;
    invalid?: boolean;
    describedBy?: string;
    showDialCode?: boolean;
    fallbackLabel?: string | null;
    locale?: 'ko' | 'en';
}

export const CountrySelect = ({
    countries, value, onChange, valueKey = 'countryName', showDialCode = false, fallbackLabel,
    ariaLabel = '국가', placeholder = '국가 선택', locale = 'ko', ...inputProps
}: CountrySelectProps) => {
    const options = useMemo(() => {
        const result: SearchableSelectOption[] = countries
            .filter((country) => country[valueKey] != null && country[valueKey] !== '')
            .map((country) => ({
                value: String(country[valueKey]),
                label: [
                    country.countryName,
                    country.countryNameKo ? `(${country.countryNameKo})` : '',
                    country.isoAlpha2 ? `[${country.isoAlpha2}]` : '',
                    showDialCode ? country.dialCode : ''
                ].filter(Boolean).join(' '),
                searchText: [country.countryName, country.countryNameKo, country.isoAlpha2, country.isoAlpha3,
                    showDialCode ? country.dialCode : ''].filter(Boolean).join(' ')
            }));
        // Preserve saved values even when the country has since been disabled or removed from the options.
        if (value && !result.some((option) => option.value === value)) {
            result.unshift({
                value,
                label: `${fallbackLabel || (valueKey === 'seq' ? `ID ${value}` : value)} (${locale === 'en' ? 'Saved value' : '기존 값'})`
            });
        }
        return result;
    }, [countries, value, valueKey, showDialCode, fallbackLabel, locale]);

    return <SearchableSelect {...inputProps} options={options} value={value} onChange={onChange}
        ariaLabel={ariaLabel} placeholder={placeholder} locale={locale} />;
};
