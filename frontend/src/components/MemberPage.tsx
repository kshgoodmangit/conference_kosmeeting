import { type FormEvent, useEffect, useRef, useState } from 'react';
import {
    CheckCircle2,
    ChevronLeft,
    ChevronRight,
    Download,
    FilterX,
    Globe2,
    Mail,
    Pencil,
    Phone,
    Search,
    Trash2,
    UserPlus,
    XCircle,
    Users
} from 'lucide-react';
import { MemberRegisterModal, type EditableMember } from './MemberRegisterModal';
import { MemberDetailModal } from './MemberDetailModal';
import type { NotificationType } from './NotificationToast';
import { useConfirm } from './confirmDialogContext';
import { useExcelDownloadDialog } from './excelDownloadDialogContext';
import { downloadExcelFile } from '../excelDownload';

type MemberType = 'international' | 'domestic';

const booleanFilterValue = (value: string) => value === '' ? null : value === 'true';
const booleanFilterLabel = (value: string) => value === '' ? '전체' : value === 'true' ? '있음' : '없음';

interface Member {
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
    createdAt?: string | null;
    updatedAt?: string | null;
}

interface MemberPageResponse {
    items: Member[];
    page: number;
    size: number;
    totalCount: number;
    internationalCount: number;
    domesticCount: number;
    preRegistrationCount: number;
    abstractSubmissionCount: number;
    totalPages: number;
}

const PAGE_SIZE = 10;
const rowActionButtonClass = 'inline-flex items-center justify-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900';
const getMemberName = (member: Pick<Member, 'firstName' | 'lastName'>) => `${member.firstName} ${member.lastName}`.trim();

const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
});

interface MemberPageProps {
    onNotify: (type: NotificationType, message: string) => void;
}

interface SearchRadioOptionProps {
    name: string;
    value: string;
    checked: boolean;
    label: string;
    onChange: (value: string) => void;
}

const SearchRadioOption = ({ name, value, checked, label, onChange }: SearchRadioOptionProps) => (
    <label className="inline-flex cursor-pointer items-center gap-1.5 whitespace-nowrap text-xs font-medium text-slate-600 dark:text-slate-300">
        <input
            type="radio"
            name={name}
            value={value}
            checked={checked}
            onChange={(event) => onChange(event.target.value)}
            className="h-4 w-4 accent-blue-600"
        />
        {label}
    </label>
);

export const MemberPage = ({ onNotify }: MemberPageProps) => {
    const confirm = useConfirm();
    const requestExcelDownload = useExcelDownloadDialog();
    const [members, setMembers] = useState<Member[]>([]);
    const [draftSearchKeyword, setDraftSearchKeyword] = useState('');
    const [searchKeyword, setSearchKeyword] = useState('');
    const [draftMemberType, setDraftMemberType] = useState('');
    const [memberType, setMemberType] = useState('');
    const [draftHasPreRegistration, setDraftHasPreRegistration] = useState('');
    const [hasPreRegistration, setHasPreRegistration] = useState('');
    const [draftHasAbstractSubmission, setDraftHasAbstractSubmission] = useState('');
    const [hasAbstractSubmission, setHasAbstractSubmission] = useState('');
    const [currentPage, setCurrentPage] = useState(1);
    const [totalCount, setTotalCount] = useState(0);
    const [internationalCount, setInternationalCount] = useState(0);
    const [domesticCount, setDomesticCount] = useState(0);
    const [preRegistrationCount, setPreRegistrationCount] = useState(0);
    const [abstractSubmissionCount, setAbstractSubmissionCount] = useState(0);
    const [totalPages, setTotalPages] = useState(1);
    const [isLoading, setIsLoading] = useState(false);
    const [isDownloading, setIsDownloading] = useState(false);
    const [errorMessage, setErrorMessage] = useState('');
    const [isRegisterModalOpen, setIsRegisterModalOpen] = useState(false);
    const [detailMemberSeq, setDetailMemberSeq] = useState<number | null>(null);
    const [selectedMember, setSelectedMember] = useState<EditableMember | null>(null);
    const [reloadKey, setReloadKey] = useState(0);
    const onNotifyRef = useRef(onNotify);

    useEffect(() => {
        onNotifyRef.current = onNotify;
    }, [onNotify]);

    useEffect(() => {
        const abortController = new AbortController();

        const fetchMembers = async () => {
            setIsLoading(true);
            setErrorMessage('');

            try {
                const params = new URLSearchParams({
                    page: String(currentPage),
                    size: String(PAGE_SIZE),
                    keyword: searchKeyword.trim()
                });
                if (memberType) params.set('memberType', memberType);
                if (hasPreRegistration) params.set('hasPreRegistration', hasPreRegistration);
                if (hasAbstractSubmission) params.set('hasAbstractSubmission', hasAbstractSubmission);
                const response = await fetch(`/api/admin/members?${params.toString()}`, {
                    signal: abortController.signal
                });

                if (!response.ok) {
                    const message = await response.text();
                    throw new Error(message || '회원 목록을 불러오지 못했습니다.');
                }

                const data = await response.json() as MemberPageResponse;
                setMembers(data.items);
                setTotalCount(data.totalCount);
                setInternationalCount(data.internationalCount);
                setDomesticCount(data.domesticCount);
                setPreRegistrationCount(data.preRegistrationCount);
                setAbstractSubmissionCount(data.abstractSubmissionCount);
                setTotalPages(data.totalPages);

                if (data.page !== currentPage) {
                    setCurrentPage(data.page);
                }
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') {
                    return;
                }
                const message = error instanceof Error ? error.message : '회원 목록을 불러오지 못했습니다.';
                setErrorMessage(message);
                onNotifyRef.current('error', message);
            } finally {
                setIsLoading(false);
            }
        };

        void fetchMembers();
        return () => abortController.abort();
    }, [currentPage, hasAbstractSubmission, hasPreRegistration, memberType, reloadKey, searchKeyword]);

    const safeCurrentPage = Math.min(currentPage, totalPages);
    const startIndex = (safeCurrentPage - 1) * PAGE_SIZE;
    const firstPageNumber = Math.max(1, Math.min(safeCurrentPage - 2, Math.max(1, totalPages - 4)));
    const pageNumbers = Array.from(
        { length: Math.min(5, totalPages - firstPageNumber + 1) },
        (_, index) => firstPageNumber + index
    );
    const hasSearchCondition = Boolean(searchKeyword || memberType || hasPreRegistration || hasAbstractSubmission);

    const refreshMembers = () => setReloadKey((value) => value + 1);

    const formatDate = (value?: string | null) => {
        if (!value) return '-';
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return '-';
        return dateFormatter.format(date);
    };

    const handleSearch = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        setCurrentPage(1);
        setSearchKeyword(draftSearchKeyword.trim());
        setMemberType(draftMemberType);
        setHasPreRegistration(draftHasPreRegistration);
        setHasAbstractSubmission(draftHasAbstractSubmission);
        setReloadKey((value) => value + 1);
    };

    const handleMemberTypeFilterChange = (value: string) => {
        setDraftMemberType(value);
        setMemberType(value);
        setCurrentPage(1);
    };

    const handlePreRegistrationFilterChange = (value: string) => {
        setDraftHasPreRegistration(value);
        setHasPreRegistration(value);
        setCurrentPage(1);
    };

    const handleAbstractSubmissionFilterChange = (value: string) => {
        setDraftHasAbstractSubmission(value);
        setHasAbstractSubmission(value);
        setCurrentPage(1);
    };

    const resetSearch = () => {
        setDraftSearchKeyword('');
        setSearchKeyword('');
        setDraftMemberType('');
        setMemberType('');
        setDraftHasPreRegistration('');
        setHasPreRegistration('');
        setDraftHasAbstractSubmission('');
        setHasAbstractSubmission('');
        setCurrentPage(1);
        setReloadKey((value) => value + 1);
    };

    const openCreateMember = () => {
        setSelectedMember(null);
        setIsRegisterModalOpen(true);
    };

    const openEditMember = (member: Member) => {
        setSelectedMember({
            seq: member.seq,
            memberType: member.memberType,
            email: member.email,
            firstName: member.firstName,
            lastName: member.lastName,
            institution: member.institution,
            department: member.department ?? '',
            positionTitle: member.positionTitle ?? '',
            country: member.country ?? '',
            mobile: member.mobile,
            newsletter: member.newsletter
        });
        setIsRegisterModalOpen(true);
    };

    const handleDeleteMember = async (member: Member) => {
        if (!await confirm({
            title: '회원 삭제',
            message: `회원 ${getMemberName(member)}을(를) 삭제하시겠습니까?`,
            confirmText: '삭제',
            tone: 'danger'
        })) {
            return;
        }

        try {
            const response = await fetch(`/api/admin/members/${member.seq}`, {
                method: 'DELETE'
            });

            if (!response.ok) {
                const message = await response.text();
                throw new Error(message || '회원 삭제에 실패했습니다.');
            }

            onNotifyRef.current('success', '회원이 삭제되었습니다.');
            refreshMembers();
        } catch (error) {
            const message = error instanceof Error ? error.message : '회원 삭제에 실패했습니다.';
            setErrorMessage(message);
            onNotifyRef.current('error', message);
        }
    };

    const handleExcelDownload = async () => {
        setIsDownloading(true);
        try {
            const completed = await requestExcelDownload({
                menuName: '회원 관리',
                filters: [
                    { label: '검색어', value: searchKeyword.trim() || '전체' },
                    { label: '회원 유형', value: memberType === 'domestic' ? '국내' : memberType === 'international' ? '국외' : '전체' },
                    { label: '사전등록', value: booleanFilterLabel(hasPreRegistration) },
                    { label: '초록 제출', value: booleanFilterLabel(hasAbstractSubmission) }
                ],
                execute: async (reason) => {
                    await downloadExcelFile('/api/admin/members/excel', {
                        reason,
                        keyword: searchKeyword.trim(),
                        memberType: memberType || null,
                        hasPreRegistration: booleanFilterValue(hasPreRegistration),
                        hasAbstractSubmission: booleanFilterValue(hasAbstractSubmission)
                    }, 'members.xlsx');
                },
                onError: (message) => onNotifyRef.current('error', message)
            });
            if (completed) onNotifyRef.current('success', '엑셀 파일을 다운로드했습니다.');
        } finally {
            setIsDownloading(false);
        }
    };

    return (
        <>
            <section className="bg-white dark:bg-slate-950 rounded-xl border border-slate-200 dark:border-slate-800 shadow-sm">
                <div className="p-4 md:p-5 border-b border-slate-200 dark:border-slate-800 flex flex-col lg:flex-row lg:items-center justify-between gap-4">
                    <div>
                        <div className="flex items-center gap-2">
                            <Users className="shrink-0 h-4 w-4 text-slate-400" />
                            <h3 className="font-semibold text-sm md:text-base">회원 관리</h3>
                        </div>
                        <p className="text-xs text-slate-400 mt-1">회원 목록을 조회하고, 회원을 등록하거나 수정/삭제할 수 있습니다.</p>
                    </div>

                    <div className="flex flex-col sm:flex-row sm:items-center gap-2">
                        <button
                            onClick={handleExcelDownload}
                            disabled={isDownloading}
                            className="inline-flex items-center justify-center gap-2 px-3 py-2 rounded-lg text-xs font-semibold border border-blue-200 dark:border-blue-900/60 bg-blue-50 dark:bg-blue-950/30 text-blue-700 dark:text-blue-300 hover:bg-blue-100 dark:hover:bg-blue-950/50 disabled:opacity-50"
                        >
                            <Download className="w-4 h-4" />
                            {isDownloading ? '다운로드 중' : '엑셀 다운로드'}
                        </button>
                        <button
                            onClick={openCreateMember}
                            className="inline-flex items-center justify-center gap-2 px-3 py-2 rounded-lg text-xs font-semibold bg-emerald-600 text-white hover:bg-emerald-700"
                        >
                            <UserPlus className="w-4 h-4" />
                            회원 추가
                        </button>
                    </div>
                </div>

                <div className="grid grid-cols-1 border-b border-slate-200 sm:grid-cols-2 lg:grid-cols-5 dark:border-slate-800">
                    <div className="border-b border-slate-200 p-4 md:p-5 lg:border-b-0 lg:border-r dark:border-slate-800">
                        <p className="text-xs text-slate-400 font-medium">{hasSearchCondition ? '검색 결과' : '전체 회원'}</p>
                        <p className="text-2xl font-bold mt-1">{totalCount.toLocaleString()}명</p>
                    </div>
                    <div className="border-b border-slate-200 p-4 md:p-5 lg:border-b-0 lg:border-r dark:border-slate-800">
                        <p className="text-xs text-slate-400 font-medium">International</p>
                        <p className="text-2xl font-bold mt-1 text-blue-600 dark:text-blue-400">
                            {internationalCount.toLocaleString()}명
                        </p>
                    </div>
                    <div className="border-b border-slate-200 p-4 md:p-5 lg:border-b-0 lg:border-r dark:border-slate-800">
                        <p className="text-xs text-slate-400 font-medium">Domestic</p>
                        <p className="text-2xl font-bold mt-1 text-emerald-600 dark:text-emerald-400">
                            {domesticCount.toLocaleString()}명
                        </p>
                    </div>
                    <div className="border-b border-slate-200 p-4 md:p-5 lg:border-b-0 lg:border-r dark:border-slate-800">
                        <p className="text-xs font-medium text-slate-400">사전등록 회원</p>
                        <p className="mt-1 text-2xl font-bold text-violet-600 dark:text-violet-400">
                            {preRegistrationCount.toLocaleString()}명
                        </p>
                    </div>
                    <div className="p-4 md:p-5">
                        <p className="text-xs font-medium text-slate-400">초록접수 회원</p>
                        <p className="mt-1 text-2xl font-bold text-amber-600 dark:text-amber-400">
                            {abstractSubmissionCount.toLocaleString()}명
                        </p>
                    </div>
                </div>

                <form onSubmit={handleSearch} className="border-b border-slate-200 bg-slate-50/70 p-4 dark:border-slate-800 dark:bg-slate-900/40 md:p-5">
                    <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-4">
                        <label>
                            <span className="mb-1 block text-[11px] font-semibold text-slate-500">검색어</span>
                            <div className="relative">
                                <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                                <input
                                    value={draftSearchKeyword}
                                    onChange={(event) => setDraftSearchKeyword(event.target.value)}
                                    className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 pl-9 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-700 dark:bg-slate-950 dark:text-slate-50"
                                    placeholder="이름, 이메일, 소속, 직책, 모바일 검색"
                                />
                            </div>
                        </label>
                        <fieldset>
                            <legend className="mb-1 block text-[11px] font-semibold text-slate-500">회원 구분</legend>
                            <div className="flex min-h-10 flex-wrap items-center gap-x-3 gap-y-2 rounded-lg border border-slate-200 bg-white px-3 py-2 dark:border-slate-700 dark:bg-slate-950">
                                <SearchRadioOption name="memberType" value="" checked={draftMemberType === ''} label="전체" onChange={handleMemberTypeFilterChange} />
                                <SearchRadioOption name="memberType" value="domestic" checked={draftMemberType === 'domestic'} label="국내" onChange={handleMemberTypeFilterChange} />
                                <SearchRadioOption name="memberType" value="international" checked={draftMemberType === 'international'} label="국외" onChange={handleMemberTypeFilterChange} />
                            </div>
                        </fieldset>
                        <fieldset>
                            <legend className="mb-1 block text-[11px] font-semibold text-slate-500">사전등록</legend>
                            <div className="flex min-h-10 flex-wrap items-center gap-x-3 gap-y-2 rounded-lg border border-slate-200 bg-white px-3 py-2 dark:border-slate-700 dark:bg-slate-950">
                                <SearchRadioOption name="hasPreRegistration" value="" checked={draftHasPreRegistration === ''} label="전체" onChange={handlePreRegistrationFilterChange} />
                                <SearchRadioOption name="hasPreRegistration" value="true" checked={draftHasPreRegistration === 'true'} label="있음" onChange={handlePreRegistrationFilterChange} />
                                <SearchRadioOption name="hasPreRegistration" value="false" checked={draftHasPreRegistration === 'false'} label="없음" onChange={handlePreRegistrationFilterChange} />
                            </div>
                        </fieldset>
                        <fieldset>
                            <legend className="mb-1 block text-[11px] font-semibold text-slate-500">초록접수</legend>
                            <div className="flex min-h-10 flex-wrap items-center gap-x-3 gap-y-2 rounded-lg border border-slate-200 bg-white px-3 py-2 dark:border-slate-700 dark:bg-slate-950">
                                <SearchRadioOption name="hasAbstractSubmission" value="" checked={draftHasAbstractSubmission === ''} label="전체" onChange={handleAbstractSubmissionFilterChange} />
                                <SearchRadioOption name="hasAbstractSubmission" value="true" checked={draftHasAbstractSubmission === 'true'} label="있음" onChange={handleAbstractSubmissionFilterChange} />
                                <SearchRadioOption name="hasAbstractSubmission" value="false" checked={draftHasAbstractSubmission === 'false'} label="없음" onChange={handleAbstractSubmissionFilterChange} />
                            </div>
                        </fieldset>
                    </div>
                    <div className="mt-3 flex flex-col justify-end gap-2 sm:flex-row">
                        <button type="button" onClick={resetSearch} className="inline-flex items-center justify-center gap-2 rounded-lg border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-white dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-900">
                            <FilterX className="h-4 w-4" /> 초기화
                        </button>
                        <button type="submit" className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-5 py-2 text-xs font-semibold text-white hover:bg-blue-700">
                            <Search className="h-4 w-4" /> 조회
                        </button>
                    </div>
                </form>

                {errorMessage && (
                    <div className="m-4 md:m-5 rounded-lg border border-rose-200 dark:border-rose-900/60 bg-rose-50 dark:bg-rose-950/30 px-4 py-3 text-sm text-rose-700 dark:text-rose-300">
                        {errorMessage}
                    </div>
                )}

                <div className="overflow-x-auto">
                    <table className="w-full text-left border-collapse text-xs md:text-sm min-w-[1250px]">
                        <thead className="bg-slate-50 dark:bg-slate-900/50 border-b border-slate-200 dark:border-slate-800">
                        <tr>
                            <th className="p-4">회원번호</th>
                            <th className="p-4">회원명</th>
                            <th className="p-4">구분</th>
                            <th className="p-4">이메일</th>
                            <th className="p-4">소속 / 직책</th>
                            <th className="p-4">국가 / 모바일</th>
                            <th className="p-4">뉴스레터</th>
                            <th className="p-4">가입일</th>
                            <th className="p-4 text-center w-16">기능</th>
                        </tr>
                        </thead>
                        <tbody>
                        {isLoading && members.length === 0 && (
                            <tr>
                                <td colSpan={9} className="p-8 text-center text-slate-400">
                                    회원 목록을 불러오는 중입니다.
                                </td>
                            </tr>
                        )}

                        {!isLoading && members.length === 0 && (
                            <tr>
                                <td colSpan={9} className="p-8 text-center text-slate-400">
                                    {hasSearchCondition ? '검색 조건에 맞는 회원이 없습니다.' : '표시할 회원이 없습니다.'}
                                </td>
                            </tr>
                        )}

                        {members.map((member) => (
                            <tr key={member.seq} className="border-b border-slate-200 dark:border-slate-800 hover:bg-slate-50/50 dark:hover:bg-slate-900/40">
                                <td className="p-4 font-mono text-slate-400">#{member.seq}</td>
                                <td className="p-4">
                                    <button
                                        type="button"
                                        onClick={() => setDetailMemberSeq(member.seq)}
                                        className="font-semibold text-blue-600 hover:underline dark:text-blue-400"
                                    >
                                        {getMemberName(member)}
                                    </button>
                                </td>
                                <td className="p-4">
                                    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold border ${
                                        member.memberType === 'international'
                                            ? 'bg-blue-50 text-blue-700 border-blue-200 dark:bg-blue-950/40 dark:text-blue-400 dark:border-blue-900/60'
                                            : 'bg-emerald-50 text-emerald-700 border-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-400 dark:border-emerald-900/60'
                                    }`}>
                                        {member.memberType === 'international' ? 'International' : 'Domestic'}
                                    </span>
                                </td>
                                <td className="p-4">
                                    <div className="flex items-center gap-2 text-slate-600 dark:text-slate-300">
                                        <Mail className="w-4 h-4 text-slate-400" />
                                        {member.email}
                                    </div>
                                </td>
                                <td className="p-4 text-slate-500 dark:text-slate-400">
                                    <div className="font-medium text-slate-700 dark:text-slate-300">{member.institution}</div>
                                    {(member.department || member.positionTitle) && (
                                        <div className="mt-1 text-xs">
                                            {[member.department, member.positionTitle].filter(Boolean).join(' · ')}
                                        </div>
                                    )}
                                </td>
                                <td className="p-4 text-slate-500 dark:text-slate-400">
                                    <div className="space-y-1.5">
                                    {member.country && (
                                        <div className="flex items-center gap-2">
                                            <Globe2 className="w-4 h-4 text-slate-400" />
                                            {member.country}
                                        </div>
                                    )}
                                        <div className="flex items-center gap-2">
                                            <Phone className="w-4 h-4 text-slate-400" />
                                            {member.mobile || '-'}
                                        </div>
                                    </div>
                                </td>
                                <td className="p-4">
                                    {member.newsletter ? (
                                        <span className="inline-flex items-center gap-1.5 text-emerald-600 dark:text-emerald-400 font-medium">
                                            <CheckCircle2 className="w-4 h-4" />
                                            수신
                                        </span>
                                    ) : (
                                        <span className="inline-flex items-center gap-1.5 text-slate-400 font-medium">
                                            <XCircle className="w-4 h-4" />
                                            미수신
                                        </span>
                                    )}
                                </td>
                                <td className="p-4 text-slate-500 dark:text-slate-400">{formatDate(member.createdAt)}</td>
                                <td className="p-4 text-center">
                                    <div className="flex items-center justify-center gap-2">
                                        <button type="button" onClick={() => openEditMember(member)} className={rowActionButtonClass} aria-label={`${getMemberName(member)} 수정`}><Pencil className="h-4 w-4" /></button>
                                        <button type="button" onClick={() => void handleDeleteMember(member)} className={`${rowActionButtonClass} !text-rose-600 dark:!text-rose-400`} aria-label={`${getMemberName(member)} 삭제`}><Trash2 className="h-4 w-4" /></button>
                                    </div>
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>

                <div className="p-4 md:p-5 border-t border-slate-200 dark:border-slate-800 flex flex-col sm:flex-row items-center justify-between gap-4">
                    <div className="text-xs text-slate-400 font-medium">
                        전체 <span className="text-slate-900 dark:text-slate-50 font-bold">{totalCount.toLocaleString()}</span>명 중{' '}
                        <span className="text-slate-900 dark:text-slate-50 font-bold">
                            {totalCount === 0 ? '0' : `${startIndex + 1}-${Math.min(startIndex + members.length, totalCount)}`}
                        </span>
                        번째 표시
                    </div>

                    <div className="flex items-center gap-1.5">
                        <button
                            disabled={safeCurrentPage === 1}
                            onClick={() => setCurrentPage((page) => Math.max(page - 1, 1))}
                            className="p-2 border border-slate-200 dark:border-slate-800 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-900 disabled:opacity-40"
                        >
                            <ChevronLeft className="w-4 h-4" />
                        </button>

                        {pageNumbers.map((page) => (
                            <button
                                key={page}
                                onClick={() => setCurrentPage(page)}
                                className={`w-8 h-8 rounded-lg text-xs font-bold border transition-colors ${
                                    safeCurrentPage === page
                                        ? 'border-blue-600 bg-blue-600 text-white'
                                        : 'border-slate-200 dark:border-slate-800 hover:bg-slate-50 dark:hover:bg-slate-900'
                                }`}
                            >
                                {page}
                            </button>
                        ))}

                        <button
                            disabled={safeCurrentPage === totalPages}
                            onClick={() => setCurrentPage((page) => Math.min(page + 1, totalPages))}
                            className="p-2 border border-slate-200 dark:border-slate-800 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-900 disabled:opacity-40"
                        >
                            <ChevronRight className="w-4 h-4" />
                        </button>
                    </div>
                </div>
            </section>

            <MemberRegisterModal
                isOpen={isRegisterModalOpen}
                member={selectedMember}
                onClose={() => {
                    setIsRegisterModalOpen(false);
                    setSelectedMember(null);
                }}
                onSuccess={() => {
                    refreshMembers();
                    onNotifyRef.current('success', selectedMember ? '회원이 수정되었습니다.' : '회원이 등록되었습니다.');
                    setSelectedMember(null);
                }}
                onNotify={onNotifyRef.current}
            />
            <MemberDetailModal
                memberSeq={detailMemberSeq}
                onClose={() => setDetailMemberSeq(null)}
                onNotify={onNotifyRef.current}
            />
        </>
    );
};
