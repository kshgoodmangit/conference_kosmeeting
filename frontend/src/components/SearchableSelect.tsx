import { useEffect, useId, useRef, useState } from 'react';
import { ChevronDown, X } from 'lucide-react';
import Select, { components, type ClearIndicatorProps, type DropdownIndicatorProps, type GroupBase } from 'react-select';

export interface SearchableSelectOption {
    value: string;
    label: string;
    searchText?: string;
}

interface SearchableSelectProps {
    options: SearchableSelectOption[];
    value: string;
    onChange: (value: string) => void;
    ariaLabel: string;
    inputId?: string;
    placeholder?: string;
    disabled?: boolean;
    loading?: boolean;
    required?: boolean;
    invalid?: boolean;
    describedBy?: string;
    locale?: 'ko' | 'en';
}

type OptionGroup = GroupBase<SearchableSelectOption>;

const matchesSearch = (option: SearchableSelectOption, input: string) => {
    const terms = input.normalize('NFKC').trim().toLocaleLowerCase().split(/\s+/);
    const text = `${option.label} ${option.searchText ?? ''}`.normalize('NFKC').toLocaleLowerCase();
    return terms.every((term) => text.includes(term));
};

const DropdownIndicator = (props: DropdownIndicatorProps<SearchableSelectOption, false, OptionGroup>) => (
    <components.DropdownIndicator {...props}><ChevronDown aria-hidden="true" className="h-4 w-4" /></components.DropdownIndicator>
);

const ClearIndicator = (props: ClearIndicatorProps<SearchableSelectOption, false, OptionGroup>) => (
    <components.ClearIndicator {...props}><X aria-hidden="true" className="h-4 w-4" /></components.ClearIndicator>
);

export const SearchableSelect = ({
    options, value, onChange, ariaLabel, inputId, placeholder = '선택하세요',
    disabled = false, loading = false, required = false, invalid = false, describedBy, locale = 'ko'
}: SearchableSelectProps) => {
    const instanceId = useId();
    const containerRef = useRef<HTMLDivElement>(null);
    const [menuOpen, setMenuOpen] = useState(false);
    const [query, setQuery] = useState('');
    const english = locale === 'en';
    const isDisabled = disabled || loading;
    const closeMenu = () => { setMenuOpen(false); setQuery(''); };

    useEffect(() => {
        if (!menuOpen) return;
        // Close before a modal drag, resize or outside click can detach the menu from its input.
        const onPointerDown = (event: PointerEvent) => {
            const target = event.target;
            if (!(target instanceof Node)) return;
            const menu = document.getElementById(`react-select-${instanceId}-listbox`)?.parentElement;
            if (!containerRef.current?.contains(target) && !menu?.contains(target)) {
                setMenuOpen(false);
                setQuery('');
            }
        };
        const onResize = () => { setMenuOpen(false); setQuery(''); };
        document.addEventListener('pointerdown', onPointerDown, true);
        window.addEventListener('resize', onResize);
        return () => {
            document.removeEventListener('pointerdown', onPointerDown, true);
            window.removeEventListener('resize', onResize);
        };
    }, [instanceId, menuOpen]);

    return (
        <div ref={containerRef} className="min-w-0 text-sm font-normal">
            <Select<SearchableSelectOption, false, OptionGroup>
                instanceId={instanceId}
                inputId={inputId ?? `${instanceId}-input`}
                aria-label={ariaLabel}
                aria-invalid={invalid || undefined}
                aria-describedby={describedBy}
                options={options}
                value={options.find((option) => option.value === value) ?? null}
                onChange={(option) => onChange(option?.value ?? '')}
                inputValue={query}
                onInputChange={setQuery}
                menuIsOpen={menuOpen && !isDisabled}
                onMenuOpen={() => setMenuOpen(true)}
                onMenuClose={closeMenu}
                onBlur={closeMenu}
                onKeyDown={(event) => {
                    if (event.nativeEvent.isComposing || event.nativeEvent.keyCode === 229) {
                        event.stopPropagation();
                        return;
                    }
                    if (event.key === 'Escape' && menuOpen) event.stopPropagation();
                    if (event.key === 'Enter' && menuOpen && !options.some((option) => matchesSearch(option, query))) {
                        event.preventDefault();
                    }
                }}
                filterOption={({ data }, input) => matchesSearch(data, input)}
                placeholder={loading ? (english ? 'Loading countries...' : '국가를 불러오는 중...') : placeholder}
                noOptionsMessage={() => english ? 'No matching options' : '검색 결과가 없습니다'}
                loadingMessage={() => english ? 'Loading...' : '불러오는 중...'}
                isDisabled={isDisabled}
                isLoading={loading}
                required={required}
                isSearchable
                isClearable
                tabSelectsValue={false}
                escapeClearsValue={false}
                menuPlacement="auto"
                menuPosition="fixed"
                menuPortalTarget={typeof document === 'undefined' ? undefined : document.body}
                menuShouldScrollIntoView={false}
                maxMenuHeight={240}
                closeMenuOnScroll={(event) => {
                    const list = document.getElementById(`react-select-${instanceId}-listbox`);
                    return !(event.target instanceof Node && list?.contains(event.target));
                }}
                unstyled
                components={{ DropdownIndicator, ClearIndicator, IndicatorSeparator: null }}
                classNames={{
                    container: () => 'w-full',
                    control: ({ isFocused }) => `!min-h-0 !rounded-lg !border bg-white text-slate-900 dark:bg-slate-900 dark:text-slate-50 ${
                        invalid ? '!border-rose-500 dark:!border-rose-400'
                            : isFocused ? '!border-blue-500 dark:!border-blue-500'
                                : '!border-slate-200 dark:!border-slate-800'
                    } ${isDisabled ? 'opacity-60' : ''}`,
                    valueContainer: () => 'px-3 py-2.5',
                    input: () => 'text-slate-900 dark:text-slate-50',
                    singleValue: () => 'text-slate-900 dark:text-slate-50',
                    placeholder: () => 'text-slate-400 dark:text-slate-400',
                    indicatorsContainer: () => 'gap-1 pr-2',
                    dropdownIndicator: () => 'p-1 text-slate-400 hover:text-slate-600 dark:text-slate-400 dark:hover:text-slate-200',
                    clearIndicator: () => 'p-1 text-slate-400 hover:text-slate-600 dark:text-slate-400 dark:hover:text-slate-200',
                    loadingIndicator: () => 'text-slate-400 dark:text-slate-400',
                    menuPortal: () => '!z-[60] text-sm font-normal',
                    menu: () => 'my-1 overflow-hidden rounded-lg border border-slate-200 bg-white shadow-lg dark:border-slate-700 dark:bg-slate-900',
                    menuList: () => 'py-1',
                    option: ({ isFocused, isSelected }) => `!cursor-pointer break-words px-3 py-2 ${
                        isSelected ? 'bg-blue-600 text-white dark:bg-blue-600 dark:text-white'
                            : isFocused ? 'bg-blue-50 text-slate-900 dark:bg-slate-800 dark:text-slate-50'
                                : 'bg-white text-slate-900 dark:bg-slate-900 dark:text-slate-50'
                    }`,
                    noOptionsMessage: () => 'px-3 py-3 text-slate-500 dark:text-slate-400',
                    loadingMessage: () => 'px-3 py-3 text-slate-500 dark:text-slate-400'
                }}
            />
        </div>
    );
};
