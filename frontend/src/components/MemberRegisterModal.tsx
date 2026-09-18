import React, { useEffect, useState } from 'react';
import { Eye, EyeOff, Move, X } from 'lucide-react';
import type { NotificationType } from './NotificationToast';
import { CountrySelect, type CountrySelectOption as CountryOption } from './CountrySelect';
import { useModalDrag } from '../hooks/useModalDrag';

type MemberType = 'international' | 'domestic';

export interface EditableMember {
    seq: number;
    memberType: MemberType;
    email: string;
    firstName: string;
    lastName: string;
    institution: string;
    department?: string | null;
    positionTitle?: string | null;
    country?: string | null;
    mobile: string;
    newsletter: boolean;
}

interface ModalProps {
    isOpen: boolean;
    member?: EditableMember | null;
    onClose: () => void;
    onSuccess: () => void;
    onNotify?: (type: NotificationType, message: string) => void;
}

const splitMobile = (value: string) => {
    const trimmed = value.trim();
    const match = /^(\+\d{1,4})[\s-]*(.*)$/.exec(trimmed);

    return match
        ? { countryCode: match[1], phoneNumber: match[2] }
        : { countryCode: '', phoneNumber: trimmed };
};

export const MemberRegisterModal: React.FC<ModalProps> = ({ isOpen, member, onClose, onSuccess, onNotify }) => {
    const isEditMode = Boolean(member);
    const [memberType, setMemberType] = useState<MemberType>('international');
    const { modalRef, headerRef } = useModalDrag(isOpen);

    const [countries, setCountries] = useState<CountryOption[]>([]);
    const [isCountriesLoading, setIsCountriesLoading] = useState(false);
    const [countryLoadError, setCountryLoadError] = useState('');
    const [country, setCountry] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [firstName, setFirstName] = useState('');
    const [lastName, setLastName] = useState('');
    const [institution, setInstitution] = useState('');
    const [department, setDepartment] = useState('');
    const [positionTitle, setPositionTitle] = useState('');
    const [newsletter, setNewsletter] = useState(false);
    const [showPassword, setShowPassword] = useState(false);
    const [showConfirmPassword, setShowConfirmPassword] = useState(false);
    const [mobileCountryCode, setMobileCountryCode] = useState('');
    const [mobilePhoneNumber, setMobilePhoneNumber] = useState('');
    const [errors, setErrors] = useState<Record<string, string>>({});

    const resetForm = () => {
        setCountry('');
        setEmail('');
        setPassword('');
        setConfirmPassword('');
        setFirstName('');
        setLastName('');
        setInstitution('');
        setDepartment('');
        setPositionTitle('');
        setNewsletter(false);
        setMobileCountryCode('');
        setMobilePhoneNumber('');
        setShowPassword(false);
        setShowConfirmPassword(false);
        setErrors({});
    };

    useEffect(() => {
        if (isOpen) {
            document.body.style.overflow = 'hidden';

            if (member) {
                setMemberType(member.memberType);
                setCountry(member.country ?? '');
                setEmail(member.email);
                setPassword('');
                setConfirmPassword('');
                setFirstName(member.firstName);
                setLastName(member.lastName);
                setInstitution(member.institution);
                setDepartment(member.department ?? '');
                setPositionTitle(member.positionTitle ?? '');
                setNewsletter(Boolean(member.newsletter));
                const parsedMobile = splitMobile(member.mobile);
                setMobileCountryCode(parsedMobile.countryCode);
                setMobilePhoneNumber(parsedMobile.phoneNumber);
                setShowPassword(false);
                setShowConfirmPassword(false);
                setErrors({});
            } else {
                resetForm();
                setMemberType('international');
            }
        } else {
            document.body.style.overflow = '';
            resetForm();
            setMemberType('international');
        }

        return () => {
            document.body.style.overflow = '';
        };
    }, [isOpen, member]);

    useEffect(() => {
        if (!isOpen) {
            return;
        }

        const controller = new AbortController();

        const loadCountries = async () => {
            setIsCountriesLoading(true);
            setCountryLoadError('');

            try {
                const response = await fetch('/api/countries/used', { signal: controller.signal });
                if (!response.ok) {
                    throw new Error(await response.text() || 'Country list could not be loaded');
                }

                const data = await response.json() as CountryOption[];
                setCountries(data);

                if (member && !splitMobile(member.mobile).countryCode) {
                    const selectedCountry = data.find((item) => item.countryName === member.country);
                    const defaultCountry = member.memberType === 'domestic'
                        ? data.find((item) => item.isoAlpha2 === 'KR')
                        : selectedCountry;
                    setMobileCountryCode(defaultCountry?.dialCode ?? '');
                }
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') {
                    return;
                }
                setCountries([]);
                setCountryLoadError(error instanceof Error ? error.message : 'Country list could not be loaded');
            } finally {
                setIsCountriesLoading(false);
            }
        };

        void loadCountries();
        return () => controller.abort();
    }, [isOpen, member]);

    const handleMemberTypeChange = (nextMemberType: MemberType) => {
        setMemberType(nextMemberType);
        setErrors({});

        if (nextMemberType === 'domestic') {
            setMobileCountryCode(countries.find((item) => item.isoAlpha2 === 'KR')?.dialCode ?? '+82');
            return;
        }

        setMobileCountryCode(countries.find((item) => item.countryName === country)?.dialCode ?? '');
    };

    const handleCountryChange = (countryName: string) => {
        setCountry(countryName);
        setMobileCountryCode(countries.find((item) => item.countryName === countryName)?.dialCode ?? '');
    };

    const validateForm = () => {
        const newErrors: Record<string, string> = {};

        if (!email.trim()) {
            newErrors.email = 'Email is required';
        } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
            newErrors.email = 'Invalid email format';
        }

        if (!isEditMode && !password.trim()) {
            newErrors.password = 'Password is required';
        } else if (password.trim() && (password.trim().length < 8 || password.trim().length > 16)) {
            newErrors.password = 'Password must be 8-16 characters';
        }

        if ((!isEditMode && password !== confirmPassword) || (isEditMode && password.trim() && password !== confirmPassword)) {
            newErrors.confirmPassword = 'Passwords do not match';
        }

        if (!firstName.trim()) {
            newErrors.firstName = 'First name is required';
        }

        if (!lastName.trim()) {
            newErrors.lastName = 'Last name is required';
        }

        if (!institution.trim()) {
            newErrors.institution = 'Institution is required';
        }

        if (!mobileCountryCode.trim()) {
            newErrors.mobileCountryCode = 'Country code is required';
        } else if (!/^\+\d{1,4}$/.test(mobileCountryCode.trim())) {
            newErrors.mobileCountryCode = 'Use a valid country code such as +82';
        }

        if (!mobilePhoneNumber.trim()) {
            newErrors.mobilePhoneNumber = 'Mobile number is required';
        } else if (!/^[0-9\-\s\(\)]*$/.test(mobilePhoneNumber)) {
            newErrors.mobilePhoneNumber = 'Invalid mobile number';
        }

        if (memberType === 'international') {
            if (!country) {
                newErrors.country = 'Country is required';
            }
        }

        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();

        if (!validateForm()) {
            onNotify?.('error', '필수 입력사항과 입력 형식을 확인해주세요.');
            return;
        }

        const formData = new FormData();
        formData.append('memberType', memberType);
        formData.append('email', email.trim());
        if (!isEditMode || password.trim()) {
            formData.append('password', password.trim());
        }
        formData.append('firstName', firstName.trim());
        formData.append('lastName', lastName.trim());
        formData.append('institution', institution.trim());
        formData.append('department', department.trim());
        formData.append('positionTitle', positionTitle.trim());
        formData.append('mobile', `${mobileCountryCode.trim()} ${mobilePhoneNumber.trim()}`);
        formData.append('newsletter', newsletter.toString());

        if (memberType === 'international') {
            formData.append('country', country);
        }

        try {
            const response = await fetch(isEditMode ? `/api/admin/members/${member?.seq}` : '/api/members/register', {
                method: isEditMode ? 'PUT' : 'POST',
                body: formData
            });

            if (response.ok) {
                onSuccess();
                onClose();
                return;
            }

            const errText = await response.text();
            onNotify?.('error', `${isEditMode ? '회원 수정' : '회원가입'} 실패: ${errText}`);
        } catch (error) {
            console.error('API Error:', error);
            onNotify?.('error', '서버 통신 중 오류가 발생했습니다.');
        }
    };

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
            <div onClick={onClose} className="absolute inset-0 bg-slate-900/60" />

            <div
                ref={modalRef}
                className="absolute theme-card border rounded-xl shadow-2xl w-full max-w-2xl z-10 overflow-hidden bg-white dark:bg-slate-950 border-slate-200 dark:border-slate-800 max-h-[90vh] overflow-y-auto"
                style={{
                    top: '50%',
                    left: '50%',
                    transform: 'translate(-50%, -50%)'
                }}
            >
                <div
                    ref={headerRef}
                    className="p-4 md:p-5 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between cursor-move select-none touch-none bg-slate-50/50 dark:bg-slate-900/50 sticky top-0 z-10"
                >
                    <div className="flex items-center gap-2">
                        <Move className="w-4 h-4 text-slate-400" />
                        <h3 className="font-bold text-sm md:text-base text-slate-900 dark:text-slate-50">
                            {isEditMode ? '회원 수정' : '회원가입'}
                        </h3>
                    </div>
                    <button onClick={onClose} onMouseDown={(event) => event.stopPropagation()} className="p-1.5 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-400">
                        <X className="w-4 h-4" />
                    </button>
                </div>

                <div className="p-4 md:p-5 border-b border-slate-200 dark:border-slate-800" onMouseDown={(event) => event.stopPropagation()}>
                    <div className="flex gap-4">
                        <label className="flex items-center gap-2 cursor-pointer">
                            <input
                                type="radio"
                                name="memberType"
                                value="international"
                                checked={memberType === 'international'}
                                onChange={() => handleMemberTypeChange('international')}
                                className="w-4 h-4"
                            />
                            <span className="text-sm font-medium text-slate-700 dark:text-slate-300">International Member</span>
                        </label>
                        <label className="flex items-center gap-2 cursor-pointer">
                            <input
                                type="radio"
                                name="memberType"
                                value="domestic"
                                checked={memberType === 'domestic'}
                                onChange={() => handleMemberTypeChange('domestic')}
                                className="w-4 h-4"
                            />
                            <span className="text-sm font-medium text-slate-700 dark:text-slate-300">Domestic Member</span>
                        </label>
                    </div>
                </div>

                <form onSubmit={handleSubmit} className="p-4 md:p-5 space-y-4" onMouseDown={(event) => event.stopPropagation()} autoComplete="off">
                    <input type="text" style={{ display: 'none' }} />
                    <input type="password" style={{ display: 'none' }} />

                    {memberType === 'international' && (
                        <div>
                            <label htmlFor="member-country" className="block text-xs font-semibold text-slate-500 uppercase mb-1.5">Country *</label>
                            <CountrySelect
                                inputId="member-country"
                                ariaLabel="Country"
                                countries={countries}
                                value={country}
                                onChange={handleCountryChange}
                                loading={isCountriesLoading}
                                placeholder="Select a country"
                                locale="en"
                                showDialCode
                                required
                                invalid={Boolean(errors.country)}
                                describedBy={errors.country ? 'member-country-error' : undefined}
                            />
                            {errors.country && <p id="member-country-error" className="text-xs text-red-500 mt-1">{errors.country}</p>}
                            {countryLoadError && <p className="text-xs text-red-500 mt-1">{countryLoadError}</p>}
                        </div>
                    )}

                    <div>
                        <label className="block text-xs font-semibold text-slate-500 uppercase mb-1.5">Email *</label>
                        <input
                            type="email"
                            required
                            value={email}
                            onChange={(event) => setEmail(event.target.value)}
                            autoComplete="none"
                            className="w-full px-3 py-2 text-sm rounded-lg border bg-transparent text-slate-900 dark:text-slate-50 border-slate-200 dark:border-slate-800 focus:outline-none focus:border-blue-500"
                            placeholder="your.email@example.com"
                        />
                        {errors.email && <p className="text-xs text-red-500 mt-1">{errors.email}</p>}
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div>
                            <label className="block text-xs font-semibold text-slate-500 uppercase mb-1.5">First Name *</label>
                            <input
                                type="text"
                                required
                                maxLength={100}
                                value={firstName}
                                onChange={(event) => setFirstName(event.target.value)}
                                className="w-full px-3 py-2 text-sm rounded-lg border bg-transparent text-slate-900 dark:text-slate-50 border-slate-200 dark:border-slate-800 focus:outline-none focus:border-blue-500"
                                placeholder="John"
                            />
                            {errors.firstName && <p className="text-xs text-red-500 mt-1">{errors.firstName}</p>}
                        </div>
                        <div>
                            <label className="block text-xs font-semibold text-slate-500 uppercase mb-1.5">Last Name *</label>
                            <input
                                type="text"
                                required
                                maxLength={100}
                                value={lastName}
                                onChange={(event) => setLastName(event.target.value)}
                                className="w-full px-3 py-2 text-sm rounded-lg border bg-transparent text-slate-900 dark:text-slate-50 border-slate-200 dark:border-slate-800 focus:outline-none focus:border-blue-500"
                                placeholder="Doe"
                            />
                            {errors.lastName && <p className="text-xs text-red-500 mt-1">{errors.lastName}</p>}
                        </div>
                    </div>

                    <div>
                        <label className="block text-xs font-semibold text-slate-500 uppercase mb-1.5">Institution *</label>
                        <input
                            type="text"
                            required
                            maxLength={255}
                            value={institution}
                            onChange={(event) => setInstitution(event.target.value)}
                            className="w-full px-3 py-2 text-sm rounded-lg border bg-transparent text-slate-900 dark:text-slate-50 border-slate-200 dark:border-slate-800 focus:outline-none focus:border-blue-500"
                            placeholder="University or organization"
                        />
                        {errors.institution && <p className="text-xs text-red-500 mt-1">{errors.institution}</p>}
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div>
                            <label className="block text-xs font-semibold text-slate-500 uppercase mb-1.5">Department</label>
                            <input
                                type="text"
                                maxLength={255}
                                value={department}
                                onChange={(event) => setDepartment(event.target.value)}
                                className="w-full px-3 py-2 text-sm rounded-lg border bg-transparent text-slate-900 dark:text-slate-50 border-slate-200 dark:border-slate-800 focus:outline-none focus:border-blue-500"
                                placeholder="Department"
                            />
                        </div>
                        <div>
                            <label className="block text-xs font-semibold text-slate-500 uppercase mb-1.5">Position</label>
                            <input
                                type="text"
                                maxLength={255}
                                value={positionTitle}
                                onChange={(event) => setPositionTitle(event.target.value)}
                                className="w-full px-3 py-2 text-sm rounded-lg border bg-transparent text-slate-900 dark:text-slate-50 border-slate-200 dark:border-slate-800 focus:outline-none focus:border-blue-500"
                                placeholder="Professor, Researcher, etc."
                            />
                        </div>
                    </div>

                    <div>
                        <label className="block text-xs font-semibold text-slate-500 uppercase mb-1.5">
                            Password {isEditMode ? '(optional)' : '*'}
                        </label>
                        <div className="relative">
                            <input
                                type={showPassword ? 'text' : 'password'}
                                required={!isEditMode}
                                value={password}
                                onChange={(event) => setPassword(event.target.value)}
                                autoComplete="new-password"
                                minLength={8}
                                maxLength={16}
                                className="w-full px-3 py-2 text-sm rounded-lg border bg-transparent text-slate-900 dark:text-slate-50 border-slate-200 dark:border-slate-800 focus:outline-none focus:border-blue-500"
                                placeholder="8-16 characters"
                            />
                            <button
                                type="button"
                                onClick={() => setShowPassword(!showPassword)}
                                className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-300"
                            >
                                {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                            </button>
                        </div>
                        {errors.password && <p className="text-xs text-red-500 mt-1">{errors.password}</p>}
                    </div>

                    <div>
                        <label className="block text-xs font-semibold text-slate-500 uppercase mb-1.5">
                            Confirm Password {isEditMode ? '(optional)' : '*'}
                        </label>
                        <div className="relative">
                            <input
                                type={showConfirmPassword ? 'text' : 'password'}
                                required={!isEditMode}
                                value={confirmPassword}
                                onChange={(event) => setConfirmPassword(event.target.value)}
                                autoComplete="new-password"
                                minLength={8}
                                maxLength={16}
                                className="w-full px-3 py-2 text-sm rounded-lg border bg-transparent text-slate-900 dark:text-slate-50 border-slate-200 dark:border-slate-800 focus:outline-none focus:border-blue-500"
                                placeholder="Confirm your password"
                            />
                            <button
                                type="button"
                                onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                                className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-300"
                            >
                                {showConfirmPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                            </button>
                        </div>
                        {errors.confirmPassword && <p className="text-xs text-red-500 mt-1">{errors.confirmPassword}</p>}
                    </div>

                    <div>
                        <label className="block text-xs font-semibold text-slate-500 uppercase mb-1.5">Mobile Number *</label>
                        <div className="grid grid-cols-[minmax(110px,0.35fr)_minmax(0,1fr)] gap-2">
                            <div>
                                <span className="mb-1 block text-[11px] font-medium text-slate-400">Country Code</span>
                                <input
                                    type="tel"
                                    required
                                    maxLength={5}
                                    value={mobileCountryCode}
                                    onChange={(event) => setMobileCountryCode(event.target.value)}
                                    className="w-full px-3 py-2 text-sm rounded-lg border bg-transparent text-slate-900 dark:text-slate-50 border-slate-200 dark:border-slate-800 focus:outline-none focus:border-blue-500"
                                    placeholder="+82"
                                    aria-label="Mobile country code"
                                />
                                {errors.mobileCountryCode && <p className="text-xs text-red-500 mt-1">{errors.mobileCountryCode}</p>}
                            </div>
                            <div>
                                <span className="mb-1 block text-[11px] font-medium text-slate-400">Mobile Number</span>
                                <input
                                    type="tel"
                                    required
                                    maxLength={24}
                                    value={mobilePhoneNumber}
                                    onChange={(event) => setMobilePhoneNumber(event.target.value)}
                                    className="w-full px-3 py-2 text-sm rounded-lg border bg-transparent text-slate-900 dark:text-slate-50 border-slate-200 dark:border-slate-800 focus:outline-none focus:border-blue-500"
                                    placeholder={mobileCountryCode === '+82' ? '10-1234-5678' : 'Mobile number'}
                                    aria-label="Mobile phone number"
                                />
                                {errors.mobilePhoneNumber && <p className="text-xs text-red-500 mt-1">{errors.mobilePhoneNumber}</p>}
                            </div>
                        </div>
                    </div>

                    <div className="flex items-center gap-2">
                        <input
                            type="checkbox"
                            id="newsletter"
                            checked={newsletter}
                            onChange={(event) => setNewsletter(event.target.checked)}
                            className="w-4 h-4 rounded"
                        />
                        <label htmlFor="newsletter" className="text-sm text-slate-700 dark:text-slate-300 cursor-pointer">
                            뉴스레터 수신
                        </label>
                    </div>

                    <div className="flex justify-end gap-2 pt-2 border-t border-slate-200 dark:border-slate-800">
                        <button
                            type="button"
                            onClick={onClose}
                            className="px-4 py-2 rounded-lg text-xs font-semibold border border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-900"
                        >
                            취소
                        </button>
                        <button
                            type="submit"
                            className="px-4 py-2 rounded-lg text-xs font-semibold bg-blue-600 text-white hover:bg-blue-700 transition-all"
                        >
                            {isEditMode ? '저장' : '가입하기'}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
};
