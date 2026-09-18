import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { Download, FileText, LoaderCircle, Plus, Save, Trash2, Upload, X } from 'lucide-react';
import { useRef } from 'react';
import type { ChangeEvent, DragEvent } from 'react';
import type {
    AbstractSubmissionAttachment,
    AbstractSubmissionDetail,
    AbstractSubmissionMetaResponse,
    EditableAbstractSubmission
} from './abstractTypes';
import type { NotificationType } from './NotificationToast';
import { CountrySelect, type CountrySelectOption as CountryOption } from './CountrySelect';
import { useModalDrag } from '../hooks/useModalDrag';
import { useConfirm } from './confirmDialogContext';

interface Props {
    isOpen: boolean;
    abstractSubmission?: AbstractSubmissionDetail | EditableAbstractSubmission | null;
    onClose: () => void;
    onSuccess: (abstractSubmission: AbstractSubmissionDetail) => void;
    onNotify: (type: NotificationType, message: string) => void;
}

type Status = AbstractSubmissionDetail['status'];

interface InstitutionForm {
    institutionNo: string;
    country: string;
    institutionName: string;
    department: string;
}

interface AuthorForm {
    authorOrder: string;
    authorName: string;
    institutionNo: string;
    isPresentingAuthor: boolean;
    isCorrespondingAuthor: boolean;
    email: string;
    country: string;
}

interface FormState {
    memberId: string;
    presentationTypeCode: string;
    categoryCode: string;
    title: string;
    objectiveText: string;
    methodsText: string;
    resultsText: string;
    conclusionsText: string;
    aiUsage: boolean;
    aiToolCodes: string[];
    otherAiToolName: string;
    otherAiProviderName: string;
    aiVersionInfo: string;
    aiScopeCodes: string[];
    otherAiScopeText: string;
    aiDataAnalysisUsed: boolean;
    plagiarismPolicyConfirmed: boolean;
    status: Status;
    institutions: InstitutionForm[];
    authors: AuthorForm[];
}

const STATUS_OPTIONS: { value: Status; label: string }[] = [
    { value: 'draft', label: '임시저장' },
    { value: 'submitted', label: '제출완료' }
];

const STATUS_LABEL: Record<Status, string> = {
    draft: '임시저장',
    submitted: '제출완료',
    under_review: '심사중',
    approved: '승인',
    rejected: '반려'
};

const MAX_PRESENTATION_FILE_SIZE = 100 * 1024 * 1024;
const ALLOWED_PRESENTATION_EXTENSIONS = new Set(['pdf', 'ppt', 'pptx']);
type EnglishTextField = 'title' | 'objectiveText' | 'methodsText' | 'resultsText' | 'conclusionsText';

const isAllowedEnglishCharacter = (character: string) => {
    const codePoint = character.codePointAt(0) ?? 0;
    return character === '\n' || character === '\r' || character === '\t' || (codePoint >= 0x20 && codePoint <= 0x7e);
};

const isEnglishText = (value: string) => Array.from(value).every(isAllowedEnglishCharacter);

const filterNewEnglishText = (previousValue: string, nextValue: string) => {
    let prefixLength = 0;
    const sharedLength = Math.min(previousValue.length, nextValue.length);
    while (prefixLength < sharedLength && previousValue[prefixLength] === nextValue[prefixLength]) {
        prefixLength += 1;
    }

    let suffixLength = 0;
    while (
        suffixLength < previousValue.length - prefixLength
        && suffixLength < nextValue.length - prefixLength
        && previousValue[previousValue.length - 1 - suffixLength] === nextValue[nextValue.length - 1 - suffixLength]
    ) {
        suffixLength += 1;
    }

    const insertedEnd = nextValue.length - suffixLength;
    const insertedText = nextValue.slice(prefixLength, insertedEnd);
    if (!isEnglishText(insertedText)) {
        return previousValue;
    }
    return nextValue.slice(0, prefixLength)
        + insertedText
        + nextValue.slice(insertedEnd);
};

const formatFileSize = (value: number) => {
    if (value < 1024) return `${value.toLocaleString()} B`;
    if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
    return `${(value / (1024 * 1024)).toFixed(1)} MB`;
};

const emptyInstitution = (institutionNo = '1'): InstitutionForm => ({
    institutionNo,
    country: '',
    institutionName: '',
    department: ''
});

const emptyAuthor = (institutionNo = '1', authorOrder = '1'): AuthorForm => ({
    authorOrder,
    authorName: '',
    institutionNo,
    isPresentingAuthor: true,
    isCorrespondingAuthor: false,
    email: '',
    country: ''
});

const toPositiveInteger = (value: string): number | null => {
    const parsed = Number.parseInt(value, 10);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
};

const getInstitutionOptions = (institutions: InstitutionForm[]): string[] => {
    const values = institutions
        .map((institution) => toPositiveInteger(institution.institutionNo))
        .filter((value): value is number => value !== null);
    return Array.from(new Set(values)).sort((left, right) => left - right).map(String);
};

const getNextInstitutionNo = (institutions: InstitutionForm[]): string => {
    const next = institutions
        .map((institution) => toPositiveInteger(institution.institutionNo))
        .filter((value): value is number => value !== null)
        .reduce((max, value) => Math.max(max, value), 0) + 1;
    return String(next);
};

const getNextAuthorOrder = (authors: AuthorForm[]): string => {
    const next = authors
        .map((author) => toPositiveInteger(author.authorOrder))
        .filter((value): value is number => value !== null)
        .reduce((max, value) => Math.max(max, value), 0) + 1;
    return String(next);
};

const normalizeAuthors = (authors: AuthorForm[], institutions: InstitutionForm[]): AuthorForm[] => {
    const options = getInstitutionOptions(institutions);
    const fallback = options[0] ?? '1';
    return authors.map((author) => (options.includes(author.institutionNo) ? author : { ...author, institutionNo: fallback }));
};

const createState = (value?: AbstractSubmissionDetail | EditableAbstractSubmission | null): FormState => {
    const institutions = value?.institutions?.length
        ? value.institutions.map((institution, index) => ({
            institutionNo: String(institution.institutionNo ?? index + 1),
            country: institution.country ?? '',
            institutionName: institution.institutionName ?? '',
            department: institution.department ?? ''
        }))
        : [emptyInstitution()];

    const fallbackInstitutionNo = getInstitutionOptions(institutions)[0] ?? '1';
    const authors = value?.authors?.length
        ? value.authors.map((author, index) => ({
            authorOrder: String(author.authorOrder ?? index + 1),
            authorName: author.authorName ?? '',
            institutionNo: String(author.institutionNo ?? fallbackInstitutionNo),
            isPresentingAuthor: Boolean(author.isPresentingAuthor ?? index === 0),
            isCorrespondingAuthor: Boolean(author.isCorrespondingAuthor),
            email: author.email ?? '',
            country: author.country ?? ''
        }))
        : [emptyAuthor(fallbackInstitutionNo)];

    const otherAiTool = value?.aiTools?.find((tool) => tool.isEtc === 'Y' || tool.otherToolName || tool.otherProviderName);
    const otherAiScope = value?.aiScopes?.find((scope) => scope.isEtc === 'Y' || scope.otherScopeText);

    return {
        memberId: value?.memberEmail ?? '',
        presentationTypeCode: value?.presentationTypeCode ? String(value.presentationTypeCode) : '',
        categoryCode: value?.categoryCode ? String(value.categoryCode) : '',
        title: value?.title ?? '',
        objectiveText: value?.objectiveText ?? '',
        methodsText: value?.methodsText ?? '',
        resultsText: value?.resultsText ?? '',
        conclusionsText: value?.conclusionsText ?? '',
        aiUsage: Boolean(value?.aiUsage),
        aiToolCodes: value?.aiTools?.map((tool) => String(tool.aiToolCode)) ?? [],
        otherAiToolName: otherAiTool?.otherToolName ?? '',
        otherAiProviderName: otherAiTool?.otherProviderName ?? '',
        aiVersionInfo: value?.aiVersionInfo ?? '',
        aiScopeCodes: value?.aiScopes?.map((scope) => String(scope.aiScopeCode)) ?? [],
        otherAiScopeText: otherAiScope?.otherScopeText ?? '',
        aiDataAnalysisUsed: Boolean(value?.aiDataAnalysisUsed),
        plagiarismPolicyConfirmed: Boolean(value?.plagiarismPolicyConfirmed),
        status: value?.status ?? 'draft',
        institutions,
        authors: normalizeAuthors(authors, institutions)
    };
};

export const AbstractRegisterModal = ({ isOpen, abstractSubmission, onClose, onSuccess, onNotify }: Props) => {
    const confirm = useConfirm();
    const { modalRef, headerRef } = useModalDrag(isOpen);
    const presentationFileInputRef = useRef<HTMLInputElement>(null);
    const composingEnglishFieldRef = useRef<EnglishTextField | null>(null);
    const englishCompositionStartValueRef = useRef('');
    const [meta, setMeta] = useState<AbstractSubmissionMetaResponse>({
        presentationTypes: [],
        categories: [],
        aiTools: [],
        aiScopes: []
    });
    const [countries, setCountries] = useState<CountryOption[]>([]);
    const [form, setForm] = useState<FormState>(() => createState(abstractSubmission));
    const [loadingMeta, setLoadingMeta] = useState(false);
    const [loadingCountries, setLoadingCountries] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [presentationAttachments, setPresentationAttachments] = useState<AbstractSubmissionAttachment[]>(() => (
        abstractSubmission && 'attachments' in abstractSubmission
            ? abstractSubmission.attachments ?? []
            : []
    ));
    const [presentationFile, setPresentationFile] = useState<File | null>(null);
    const [isPresentationFileDragging, setIsPresentationFileDragging] = useState(false);
    const [uploadingPresentationFile, setUploadingPresentationFile] = useState(false);
    const [deletingAttachmentSeq, setDeletingAttachmentSeq] = useState<number | null>(null);

    const isEditMode = Boolean(abstractSubmission?.seq);
    const isApprovedEditMode = isEditMode && abstractSubmission?.status === 'approved';
    const isBusy = submitting || uploadingPresentationFile || deletingAttachmentSeq !== null;
    const institutionOptions = useMemo(() => getInstitutionOptions(form.institutions), [form.institutions]);
    const fallbackInstitutionNo = institutionOptions[0] ?? '1';

    useEffect(() => {
        if (!isOpen) {
            return;
        }
        const controller = new AbortController();
        const loadMeta = async () => {
            setLoadingMeta(true);
            try {
                const response = await fetch('/api/admin/abstracts/meta', { signal: controller.signal });
                if (!response.ok) {
                    throw new Error(await response.text() || 'Failed to load meta data.');
                }
                setMeta(await response.json() as AbstractSubmissionMetaResponse);
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') {
                    return;
                }
                const message = error instanceof Error ? error.message : 'Failed to load meta data.';
                onNotify('error', message);
            } finally {
                setLoadingMeta(false);
            }
        };
        void loadMeta();
        return () => controller.abort();
    }, [isOpen, onNotify]);

    useEffect(() => {
        if (!isOpen) {
            return;
        }
        const controller = new AbortController();
        const loadCountries = async () => {
            setLoadingCountries(true);
            try {
                const response = await fetch('/api/countries/used', { signal: controller.signal });
                if (!response.ok) {
                    throw new Error(await response.text() || '국가 정보를 불러오지 못했습니다.');
                }
                setCountries(await response.json() as CountryOption[]);
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') {
                    return;
                }
                const message = error instanceof Error ? error.message : '국가 정보를 불러오지 못했습니다.';
                setCountries([]);
                onNotify('error', message);
            } finally {
                setLoadingCountries(false);
            }
        };
        void loadCountries();
        return () => controller.abort();
    }, [isOpen, onNotify]);

    const setField = <K extends keyof FormState>(field: K, value: FormState[K]) => {
        setForm((previous) => ({ ...previous, [field]: value }));
    };

    const setEnglishTextField = (field: EnglishTextField, value: string) => {
        if (composingEnglishFieldRef.current === field) {
            // Let the browser finish IME composition without resetting its editing range.
            setField(field, value);
            return;
        }
        setForm((previous) => ({
            ...previous,
            [field]: filterNewEnglishText(previous[field], value)
        }));
    };

    const startEnglishTextComposition = (field: EnglishTextField) => {
        composingEnglishFieldRef.current = field;
        englishCompositionStartValueRef.current = form[field];
    };

    const endEnglishTextComposition = (field: EnglishTextField, input: HTMLInputElement | HTMLTextAreaElement) => {
        if (composingEnglishFieldRef.current !== field) return;
        const valueBeforeComposition = englishCompositionStartValueRef.current;
        const nextValue = filterNewEnglishText(valueBeforeComposition, input.value);
        composingEnglishFieldRef.current = null;
        input.value = nextValue;
        setForm((previous) => ({ ...previous, [field]: nextValue }));
    };

    const setAiUsage = (aiUsage: boolean) => {
        setForm((previous) => ({
            ...previous,
            aiUsage,
            ...(aiUsage ? {} : {
                aiToolCodes: [],
                otherAiToolName: '',
                otherAiProviderName: '',
                aiVersionInfo: '',
                aiScopeCodes: [],
                otherAiScopeText: '',
                aiDataAnalysisUsed: false
            })
        }));
    };

    const toggleAiOption = (field: 'aiToolCodes' | 'aiScopeCodes', code: string, checked: boolean) => {
        setForm((previous) => ({
            ...previous,
            [field]: checked
                ? Array.from(new Set([...previous[field], code]))
                : previous[field].filter((value) => value !== code)
        }));
    };

    const updateInstitution = (index: number, patch: Partial<InstitutionForm>) => {
        setForm((previous) => {
            const institutions = previous.institutions.map((institution, itemIndex) => (
                itemIndex === index ? { ...institution, ...patch } : institution
            ));
            return { ...previous, institutions, authors: normalizeAuthors(previous.authors, institutions) };
        });
    };

    const updateAuthor = (index: number, patch: Partial<AuthorForm>) => {
        setForm((previous) => ({
            ...previous,
            authors: previous.authors.map((author, itemIndex) => (
                itemIndex === index ? { ...author, ...patch } : author
            ))
        }));
    };

    const addInstitution = () => {
        setForm((previous) => {
            const institutions = [...previous.institutions, emptyInstitution(getNextInstitutionNo(previous.institutions))];
            return { ...previous, institutions, authors: normalizeAuthors(previous.authors, institutions) };
        });
    };

    const removeInstitution = (index: number) => {
        setForm((previous) => {
            if (previous.institutions.length === 1) {
                return previous;
            }
            const institutions = previous.institutions.filter((_, itemIndex) => itemIndex !== index);
            const options = getInstitutionOptions(institutions);
            const fallback = options[0] ?? '1';
            return {
                ...previous,
                institutions,
                authors: previous.authors.map((author) => ({
                    ...author,
                    institutionNo: options.includes(author.institutionNo) ? author.institutionNo : fallback
                }))
            };
        });
    };

    const addAuthor = () => {
        setForm((previous) => ({
            ...previous,
            authors: [...previous.authors, emptyAuthor(fallbackInstitutionNo, getNextAuthorOrder(previous.authors))]
        }));
    };

    const removeAuthor = (index: number) => {
        setForm((previous) => {
            if (previous.authors.length === 1) {
                return previous;
            }
            return {
                ...previous,
                authors: previous.authors
                    .filter((_, itemIndex) => itemIndex !== index)
                    .map((author, itemIndex) => ({ ...author, authorOrder: String(itemIndex + 1) }))
            };
        });
    };

    const validateForm = (state: FormState): string | null => {
        if (!state.memberId.trim()) return '회원 아이디를 입력하세요.';
        if (!state.presentationTypeCode) return '발표형식을 선택하세요.';
        if (!state.categoryCode) return '분류를 선택하세요.';
        if (!state.title.trim()) return '초록 제목을 입력하세요.';
        if (state.title.trim().length > 500) return '초록 제목은 500자 이하로 입력하세요.';
        if (!isEnglishText(state.title)) return '초록 제목은 영문으로만 입력하세요.';
        if (state.objectiveText.length > 1000) return 'Objective는 1000자 이하로 입력하세요.';
        if (state.methodsText.length > 1000) return 'Methods는 1000자 이하로 입력하세요.';
        if (state.resultsText.length > 1000) return 'Results는 1000자 이하로 입력하세요.';
        if (state.conclusionsText.length > 1000) return 'Conclusions는 1000자 이하로 입력하세요.';
        if (!isEnglishText(state.objectiveText)) return 'Objective는 영문으로만 입력하세요.';
        if (!isEnglishText(state.methodsText)) return 'Methods는 영문으로만 입력하세요.';
        if (!isEnglishText(state.resultsText)) return 'Results는 영문으로만 입력하세요.';
        if (!isEnglishText(state.conclusionsText)) return 'Conclusions는 영문으로만 입력하세요.';
        if (!state.plagiarismPolicyConfirmed) return 'AI 정확성, 표절 및 정책 준수 확인이 필요합니다.';
        if (state.aiVersionInfo.length > 1000) return 'AI 버전 정보는 1000자 이하로 입력하세요.';
        if (state.otherAiToolName.length > 255) return '기타 AI 도구명은 255자 이하로 입력하세요.';
        if (state.otherAiProviderName.length > 255) return '기타 AI 개발사/제공사명은 255자 이하로 입력하세요.';
        if (state.otherAiScopeText.length > 500) return '기타 AI 활용 범위는 500자 이하로 입력하세요.';
        if (state.aiUsage) {
            if (state.aiToolCodes.length === 0) return '사용한 AI 도구를 1개 이상 선택하세요.';
            if (!state.aiVersionInfo.trim()) return 'AI 버전 정보를 입력하세요.';
            if (state.aiScopeCodes.length === 0) return 'AI 활용 범위를 1개 이상 선택하세요.';

            const otherTool = meta.aiTools.find((option) => option.isEtc === 'Y');
            if (otherTool && state.aiToolCodes.includes(String(otherTool.code))) {
                if (!state.otherAiToolName.trim()) return '기타 AI 도구명을 입력하세요.';
                if (!state.otherAiProviderName.trim()) return '기타 AI 개발사/제공사명을 입력하세요.';
            }

            const otherScope = meta.aiScopes.find((option) => option.isEtc === 'Y');
            if (otherScope && state.aiScopeCodes.includes(String(otherScope.code)) && !state.otherAiScopeText.trim()) {
                return '기타 AI 활용 범위를 입력하세요.';
            }
            if (state.aiDataAnalysisUsed && !state.methodsText.trim()) {
                return 'AI를 데이터 분석 또는 결과 해석에 사용한 경우 Methods를 입력하세요.';
            }
        }
        if (state.institutions.length === 0) return '기관을 1개 이상 등록하세요.';
        if (state.authors.length === 0) return '저자를 1명 이상 등록하세요.';

        const institutionNumbers = state.institutions
            .map((institution) => toPositiveInteger(institution.institutionNo))
            .filter((value): value is number => value !== null);
        if (new Set(institutionNumbers).size !== institutionNumbers.length) return '기관 번호는 중복될 수 없습니다.';

        for (const institution of state.institutions) {
            if (!toPositiveInteger(institution.institutionNo)) return '기관 번호를 올바르게 입력하세요.';
            if (!institution.country.trim()) return '기관 국가를 입력하세요.';
            if (!institution.institutionName.trim()) return '기관명을 입력하세요.';
            if (institution.department.length > 1000) return '기관 부서는 1000자 이하로 입력하세요.';
        }

        let hasPresentingAuthor = false;
        for (const author of state.authors) {
            const authorOrder = toPositiveInteger(author.authorOrder);
            const institutionNo = toPositiveInteger(author.institutionNo);
            if (!authorOrder) return '저자 순서를 올바르게 입력하세요.';
            if (!author.authorName.trim()) return '저자명을 입력하세요.';
            if (!institutionNo) return '저자의 소속 기관을 선택하세요.';
            if (!institutionNumbers.includes(institutionNo)) return '저자의 소속 기관이 등록된 기관과 일치하지 않습니다.';
            if (author.email.length > 1000) return '이메일은 1000자 이하로 입력하세요.';
            if (author.country.length > 1000) return '저자 국가는 1000자 이하로 입력하세요.';
            if (author.isPresentingAuthor) hasPresentingAuthor = true;
        }

        if (!hasPresentingAuthor) return '발표저자를 1명 이상 지정하세요.';
        return null;
    };

    const selectPresentationFile = (file: File | null) => {
        if (!file) {
            setPresentationFile(null);
            return false;
        }
        const extension = file.name.split('.').pop()?.toLowerCase() ?? '';
        if (!ALLOWED_PRESENTATION_EXTENSIONS.has(extension)) {
            onNotify('error', '발표 자료는 PDF, PPT, PPTX 파일만 등록할 수 있습니다.');
            setPresentationFile(null);
            return false;
        }
        if (file.size > MAX_PRESENTATION_FILE_SIZE) {
            onNotify('error', '발표 자료는 파일당 100MB 이하만 등록할 수 있습니다.');
            setPresentationFile(null);
            return false;
        }
        setPresentationFile(file);
        return true;
    };

    const handlePresentationFileChange = (event: ChangeEvent<HTMLInputElement>) => {
        if (!selectPresentationFile(event.target.files?.[0] ?? null)) {
            event.target.value = '';
        }
    };

    const handlePresentationFileDragOver = (event: DragEvent<HTMLDivElement>) => {
        event.preventDefault();
        event.stopPropagation();
        if (!uploadingPresentationFile && presentationAttachments.length < 5) {
            event.dataTransfer.dropEffect = 'copy';
            setIsPresentationFileDragging(true);
        }
    };

    const handlePresentationFileDragLeave = (event: DragEvent<HTMLDivElement>) => {
        event.preventDefault();
        event.stopPropagation();
        setIsPresentationFileDragging(false);
    };

    const handlePresentationFileDrop = (event: DragEvent<HTMLDivElement>) => {
        event.preventDefault();
        event.stopPropagation();
        setIsPresentationFileDragging(false);
        if (uploadingPresentationFile || presentationAttachments.length >= 5) return;

        const files = Array.from(event.dataTransfer.files);
        if (files.length === 0) return;
        if (files.length > 1) {
            onNotify('info', '발표 자료는 한 번에 한 파일씩 첨부할 수 있습니다. 첫 번째 파일을 선택했습니다.');
        }
        if (selectPresentationFile(files[0]) && presentationFileInputRef.current) {
            presentationFileInputRef.current.value = '';
        }
    };

    const uploadPresentationFile = async () => {
        const abstractSeq = abstractSubmission?.seq;
        if (!abstractSeq || !presentationFile) {
            onNotify('error', '업로드할 발표 자료를 선택하세요.');
            return;
        }
        if (presentationAttachments.length >= 5) {
            onNotify('error', '발표 자료는 최대 5개까지 등록할 수 있습니다.');
            return;
        }

        const formData = new FormData();
        formData.append('file', presentationFile);
        setUploadingPresentationFile(true);
        try {
            const response = await fetch(`/api/admin/abstracts/${abstractSeq}/attachments`, {
                method: 'POST',
                body: formData
            });
            if (!response.ok) throw new Error(await response.text() || '발표 자료를 업로드하지 못했습니다.');
            const attachment = await response.json() as AbstractSubmissionAttachment;
            setPresentationAttachments((current) => [attachment, ...current]);
            setPresentationFile(null);
            if (presentationFileInputRef.current) presentationFileInputRef.current.value = '';
            onNotify('success', '발표 자료를 업로드했습니다.');
        } catch (error) {
            onNotify('error', error instanceof Error ? error.message : '발표 자료를 업로드하지 못했습니다.');
        } finally {
            setUploadingPresentationFile(false);
        }
    };

    const deletePresentationAttachment = async (attachment: AbstractSubmissionAttachment) => {
        const abstractSeq = abstractSubmission?.seq;
        if (!abstractSeq || deletingAttachmentSeq !== null) return;
        const confirmed = await confirm({
            title: '발표 자료 삭제',
            message: `“${attachment.originalFilename}” 파일을 삭제하시겠습니까?`,
            confirmText: '삭제',
            tone: 'danger'
        });
        if (!confirmed) return;

        setDeletingAttachmentSeq(attachment.seq);
        try {
            const response = await fetch(`/api/admin/abstracts/${abstractSeq}/attachments/${attachment.seq}`, {
                method: 'DELETE'
            });
            if (!response.ok) throw new Error(await response.text() || '발표 자료를 삭제하지 못했습니다.');
            setPresentationAttachments((current) => current.filter((item) => item.seq !== attachment.seq));
            onNotify('success', '발표 자료를 삭제했습니다.');
        } catch (error) {
            onNotify('error', error instanceof Error ? error.message : '발표 자료를 삭제하지 못했습니다.');
        } finally {
            setDeletingAttachmentSeq(null);
        }
    };

    const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();

        const validationError = validateForm(form);
        if (validationError) {
            onNotify('error', validationError);
            return;
        }

        const payload = {
            seq: isEditMode ? abstractSubmission?.seq ?? null : null,
            memberId: form.memberId.trim(),
            presentationTypeCode: Number.parseInt(form.presentationTypeCode, 10),
            categoryCode: Number.parseInt(form.categoryCode, 10),
            title: form.title.trim(),
            objectiveText: form.objectiveText.trim() || null,
            methodsText: form.methodsText.trim() || null,
            resultsText: form.resultsText.trim() || null,
            conclusionsText: form.conclusionsText.trim() || null,
            aiUsage: form.aiUsage,
            aiVersionInfo: form.aiUsage ? form.aiVersionInfo.trim() || null : null,
            aiDataAnalysisUsed: form.aiUsage && form.aiDataAnalysisUsed,
            plagiarismPolicyConfirmed: form.plagiarismPolicyConfirmed,
            aiTools: form.aiUsage ? form.aiToolCodes.map((code) => {
                const option = meta.aiTools.find((item) => String(item.code) === code);
                return {
                    aiToolCode: Number.parseInt(code, 10),
                    otherToolName: option?.isEtc === 'Y' ? form.otherAiToolName.trim() : null,
                    otherProviderName: option?.isEtc === 'Y' ? form.otherAiProviderName.trim() : null
                };
            }) : [],
            aiScopes: form.aiUsage ? form.aiScopeCodes.map((code) => {
                const option = meta.aiScopes.find((item) => String(item.code) === code);
                return {
                    aiScopeCode: Number.parseInt(code, 10),
                    otherScopeText: option?.isEtc === 'Y' ? form.otherAiScopeText.trim() : null
                };
            }) : [],
            status: form.status,
            institutions: form.institutions.map((institution) => ({
                institutionNo: Number.parseInt(institution.institutionNo, 10),
                country: institution.country.trim(),
                institutionName: institution.institutionName.trim(),
                department: institution.department.trim() || null
            })),
            authors: form.authors.map((author, index) => ({
                authorOrder: Number.parseInt(author.authorOrder, 10) || index + 1,
                authorName: author.authorName.trim(),
                institutionNo: Number.parseInt(author.institutionNo, 10),
                isPresentingAuthor: author.isPresentingAuthor,
                isCorrespondingAuthor: author.isCorrespondingAuthor,
                email: author.email.trim() || null,
                country: author.country.trim() || null
            }))
        };

        setSubmitting(true);

        try {
            const response = await fetch(isEditMode && abstractSubmission?.seq ? `/api/admin/abstracts/${abstractSubmission.seq}` : '/api/admin/abstracts', {
                method: isEditMode && abstractSubmission?.seq ? 'PUT' : 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            if (!response.ok) {
                throw new Error(await response.text() || (isEditMode ? '초록 수정에 실패했습니다.' : '초록 등록에 실패했습니다.'));
            }

            const saved = await response.json() as AbstractSubmissionDetail;
            onNotify('success', isEditMode ? '초록을 수정했습니다.' : '초록을 등록했습니다.');
            onSuccess(saved);
        } catch (error) {
            const message = error instanceof Error ? error.message : (isEditMode ? '초록 수정에 실패했습니다.' : '초록 등록에 실패했습니다.');
            onNotify('error', message);
        } finally {
            setSubmitting(false);
        }
    };

    if (!isOpen) {
        return null;
    }

    return (
        <div className="fixed inset-0 z-[70] flex items-center justify-center bg-slate-950/60 px-4 py-6 backdrop-blur-sm">
            <div
                ref={modalRef}
                className="absolute left-1/2 top-1/2 flex max-h-[calc(100vh-3rem)] w-[calc(100%-2rem)] max-w-6xl flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl dark:border-slate-800 dark:bg-slate-950"
                style={{ transform: 'translate(-50%, -50%)' }}
            >
                <div
                    ref={headerRef}
                    className="flex shrink-0 cursor-move select-none touch-none items-center justify-between border-b border-slate-200 px-5 py-4 dark:border-slate-800"
                >
                    <div>
                        <h2 className="text-lg font-bold">{isEditMode ? '초록 수정' : '초록 등록'}</h2>
                        <p className="mt-1 text-xs text-slate-400">회원, 기관, 저자, 초록 본문을 함께 저장합니다.</p>
                    </div>
                    <button type="button" onClick={onClose} disabled={isBusy} className="rounded-lg p-2 text-slate-400 hover:bg-slate-100 disabled:opacity-40 dark:hover:bg-slate-900" aria-label="닫기">
                        <X className="h-5 w-5" />
                    </button>
                </div>

                {(loadingMeta || loadingCountries) && (
                    <div className="border-b border-slate-200 px-5 py-3 dark:border-slate-800">
                        {loadingMeta && <p className="text-xs text-slate-400">기본 정보를 불러오는 중입니다.</p>}
                        {loadingCountries && <p className="text-xs text-slate-400">국가 정보를 불러오는 중입니다.</p>}
                    </div>
                )}

                <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto p-5">
                    <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
                        <label className="space-y-1.5">
                            <span className="text-xs font-semibold uppercase text-slate-500">회원 아이디</span>
                            <input type="email" value={form.memberId} onChange={(event) => setField('memberId', event.target.value)} className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50" placeholder="회원 이메일" autoComplete="off" required />
                        </label>
                        <label className="space-y-1.5">
                            <span className="text-xs font-semibold uppercase text-slate-500">발표형식</span>
                            <select value={form.presentationTypeCode} onChange={(event) => setField('presentationTypeCode', event.target.value)} className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50" required>
                                <option value="">선택하세요</option>
                                {meta.presentationTypes.map((item) => <option key={item.code} value={item.code}>{item.name}</option>)}
                            </select>
                        </label>
                        <label className="space-y-1.5">
                            <span className="text-xs font-semibold uppercase text-slate-500">분류</span>
                            <select value={form.categoryCode} onChange={(event) => setField('categoryCode', event.target.value)} className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50" required>
                                <option value="">선택하세요</option>
                                {meta.categories.map((item) => <option key={item.code} value={item.code}>{item.name}</option>)}
                            </select>
                        </label>
                        <label className="space-y-1.5 md:col-span-2 xl:col-span-1">
                            <span className="text-xs font-semibold uppercase text-slate-500">상태</span>
                            {isEditMode ? (
                                <input value={STATUS_LABEL[form.status]} readOnly className="w-full cursor-not-allowed rounded-lg border border-slate-200 bg-slate-100 px-3 py-2 text-sm text-slate-500 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-400" />
                            ) : (
                                <select value={form.status} onChange={(event) => setField('status', event.target.value as Status)} className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50">
                                    {STATUS_OPTIONS.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
                                </select>
                            )}
                        </label>
                    </div>
                    <label className="mt-4 block space-y-1.5">
                        <span className="text-xs font-semibold uppercase text-slate-500">초록 제목</span>
                        <input type="text" value={form.title}
                            onChange={(event) => setEnglishTextField('title', event.target.value)}
                            onCompositionStart={() => startEnglishTextComposition('title')}
                            onCompositionEnd={(event) => endEnglishTextComposition('title', event.currentTarget)}
                            onKeyDown={(event) => {
                                if (event.key === 'Enter' && (event.nativeEvent.isComposing || composingEnglishFieldRef.current === 'title')) {
                                    event.preventDefault();
                                }
                            }}
                            className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50" placeholder="Enter the abstract title in English" maxLength={500} required />
                        <span className="block text-[11px] font-normal text-slate-400">영문, 숫자 및 일반 문장부호만 입력할 수 있습니다.</span>
                    </label>
                    <div className="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-2">
                        <label className="space-y-1.5">
                            <span className="text-xs font-semibold uppercase text-slate-500">Objective</span>
                            <textarea value={form.objectiveText}
                                onChange={(event) => setEnglishTextField('objectiveText', event.target.value)}
                                onCompositionStart={() => startEnglishTextComposition('objectiveText')}
                                onCompositionEnd={(event) => endEnglishTextComposition('objectiveText', event.currentTarget)}
                                className="min-h-28 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50" maxLength={1000} />
                        </label>
                        <label className="space-y-1.5">
                            <span className="text-xs font-semibold uppercase text-slate-500">Methods</span>
                            <textarea value={form.methodsText}
                                onChange={(event) => setEnglishTextField('methodsText', event.target.value)}
                                onCompositionStart={() => startEnglishTextComposition('methodsText')}
                                onCompositionEnd={(event) => endEnglishTextComposition('methodsText', event.currentTarget)}
                                className="min-h-28 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50" maxLength={1000} />
                        </label>
                        <label className="space-y-1.5">
                            <span className="text-xs font-semibold uppercase text-slate-500">Results</span>
                            <textarea value={form.resultsText}
                                onChange={(event) => setEnglishTextField('resultsText', event.target.value)}
                                onCompositionStart={() => startEnglishTextComposition('resultsText')}
                                onCompositionEnd={(event) => endEnglishTextComposition('resultsText', event.currentTarget)}
                                className="min-h-28 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50" maxLength={1000} />
                        </label>
                        <label className="space-y-1.5">
                            <span className="text-xs font-semibold uppercase text-slate-500">Conclusions</span>
                            <textarea value={form.conclusionsText}
                                onChange={(event) => setEnglishTextField('conclusionsText', event.target.value)}
                                onCompositionStart={() => startEnglishTextComposition('conclusionsText')}
                                onCompositionEnd={(event) => endEnglishTextComposition('conclusionsText', event.currentTarget)}
                                className="min-h-28 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50" maxLength={1000} />
                        </label>
                    </div>
                    <p className="mt-2 text-[11px] text-slate-400">Objective, Methods, Results, Conclusions는 영문, 숫자 및 일반 문장부호만 입력할 수 있습니다.</p>

                    {isApprovedEditMode && (
                        <section className="mt-4 rounded-xl border border-slate-200 p-4 dark:border-slate-800">
                            <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-start">
                                <div>
                                    <div className="flex items-center gap-2">
                                        <FileText className="h-4 w-4 text-blue-500" />
                                        <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-50">발표 자료</h3>
                                    </div>
                                    <p className="mt-1 text-xs text-slate-400">승인된 초록의 발표 자료를 등록합니다. PDF, PPT, PPTX · 파일당 최대 100MB · 최대 5개</p>
                                </div>
                                <span className="text-xs font-medium text-slate-400">{presentationAttachments.length} / 5개</span>
                            </div>

                            <input
                                ref={presentationFileInputRef}
                                type="file"
                                accept=".pdf,.ppt,.pptx,application/pdf,application/vnd.ms-powerpoint,application/vnd.openxmlformats-officedocument.presentationml.presentation"
                                onChange={handlePresentationFileChange}
                                disabled={uploadingPresentationFile || presentationAttachments.length >= 5}
                                className="hidden"
                            />
                            <div className="mt-4 flex flex-col gap-2 sm:flex-row">
                                <div
                                    role="button"
                                    tabIndex={uploadingPresentationFile || presentationAttachments.length >= 5 ? -1 : 0}
                                    aria-label="발표 자료 파일 선택 또는 드래그 앤 드롭"
                                    aria-disabled={uploadingPresentationFile || presentationAttachments.length >= 5}
                                    onClick={() => {
                                        if (!uploadingPresentationFile && presentationAttachments.length < 5) presentationFileInputRef.current?.click();
                                    }}
                                    onKeyDown={(event) => {
                                        if ((event.key === 'Enter' || event.key === ' ') && !uploadingPresentationFile && presentationAttachments.length < 5) {
                                            event.preventDefault();
                                            presentationFileInputRef.current?.click();
                                        }
                                    }}
                                    onDragOver={handlePresentationFileDragOver}
                                    onDragLeave={handlePresentationFileDragLeave}
                                    onDrop={handlePresentationFileDrop}
                                    className={`flex min-h-24 min-w-0 flex-1 items-center justify-center rounded-xl border-2 border-dashed px-4 py-4 text-center transition-colors ${
                                        uploadingPresentationFile || presentationAttachments.length >= 5
                                            ? 'border-slate-200 bg-slate-50 opacity-50 dark:border-slate-800 dark:bg-slate-900/40'
                                            : isPresentationFileDragging
                                                ? 'border-blue-500 bg-blue-50 dark:border-blue-400 dark:bg-blue-950/30'
                                                : 'border-slate-300 bg-slate-50 hover:border-blue-400 hover:bg-blue-50/60 focus:border-blue-500 focus:outline-none dark:border-slate-700 dark:bg-slate-900/40 dark:hover:border-blue-500 dark:hover:bg-blue-950/20'
                                    }`}
                                >
                                    <div className="pointer-events-none min-w-0">
                                        <Upload className={`mx-auto h-5 w-5 ${isPresentationFileDragging ? 'text-blue-600 dark:text-blue-400' : 'text-slate-400'}`} />
                                        <p className="mt-2 truncate text-xs font-semibold text-slate-700 dark:text-slate-200">
                                            {presentationFile ? presentationFile.name : isPresentationFileDragging ? '여기에 파일을 놓으세요.' : '파일을 끌어 놓거나 클릭해서 선택하세요.'}
                                        </p>
                                        <p className="mt-1 text-[11px] text-slate-400">
                                            {presentationFile ? formatFileSize(presentationFile.size) : 'PDF, PPT, PPTX · 최대 100MB'}
                                        </p>
                                    </div>
                                </div>
                                <button
                                    type="button"
                                    onClick={() => void uploadPresentationFile()}
                                    disabled={!presentationFile || uploadingPresentationFile || presentationAttachments.length >= 5}
                                    className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-xs font-semibold text-white hover:bg-blue-700 disabled:opacity-50 dark:bg-blue-600 dark:text-white dark:hover:bg-blue-700"
                                >
                                    {uploadingPresentationFile ? <LoaderCircle className="h-4 w-4 animate-spin" /> : <Upload className="h-4 w-4" />}
                                    {uploadingPresentationFile ? '업로드 중' : '업로드'}
                                </button>
                            </div>

                            <div className="mt-4 space-y-2">
                                {presentationAttachments.length === 0 ? (
                                    <p className="rounded-lg border border-dashed border-slate-200 px-3 py-4 text-center text-xs text-slate-400 dark:border-slate-800">등록된 발표 자료가 없습니다.</p>
                                ) : presentationAttachments.map((attachment) => (
                                    <div key={attachment.seq} className="flex flex-col justify-between gap-3 rounded-lg border border-slate-200 bg-slate-50 p-3 dark:border-slate-800 dark:bg-slate-900/40 sm:flex-row sm:items-center">
                                        <div className="min-w-0">
                                            <p className="truncate text-sm font-semibold text-slate-900 dark:text-slate-50">{attachment.originalFilename}</p>
                                            <p className="mt-1 text-xs text-slate-400">{attachment.fileExtension.toUpperCase()} · {formatFileSize(attachment.fileSize)}</p>
                                        </div>
                                        <div className="flex shrink-0 items-center gap-2">
                                            <a
                                                href={`/api/admin/abstracts/${abstractSubmission?.seq}/attachments/${attachment.seq}`}
                                                target={attachment.fileExtension.toLowerCase() === 'pdf' ? '_blank' : undefined}
                                                rel={attachment.fileExtension.toLowerCase() === 'pdf' ? 'noreferrer' : undefined}
                                                className="inline-flex items-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-white dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-950"
                                            >
                                                <Download className="h-4 w-4" />{attachment.fileExtension.toLowerCase() === 'pdf' ? '보기' : '다운로드'}
                                            </a>
                                            <button
                                                type="button"
                                                onClick={() => void deletePresentationAttachment(attachment)}
                                                disabled={deletingAttachmentSeq !== null}
                                                aria-label={`${attachment.originalFilename} 삭제`}
                                                className="rounded-lg border border-rose-200 p-2 text-rose-600 hover:bg-rose-50 disabled:opacity-40 dark:border-rose-900/60 dark:text-rose-400 dark:hover:bg-rose-950/30"
                                            >
                                                {deletingAttachmentSeq === attachment.seq ? <LoaderCircle className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}
                                            </button>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </section>
                    )}

                    <div className="mt-4 overflow-hidden rounded-xl border border-slate-200 dark:border-slate-800">
                        <div className="border-b border-slate-200 bg-slate-50 px-4 py-3 dark:border-slate-800 dark:bg-slate-900/50">
                            <h3 className="text-sm font-semibold">AI 사용 정보</h3>
                        </div>

                        <div className="grid grid-cols-1 border-b border-slate-200 dark:border-slate-800 md:grid-cols-[190px_1fr]">
                            <div className="bg-slate-50 px-4 py-3 text-sm font-semibold dark:bg-slate-900/40">AI 사용 <span className="text-rose-500">*</span></div>
                            <div className="flex items-center gap-6 px-4 py-3">
                                <label className="flex items-center gap-2 text-sm"><input type="radio" name="aiUsage" checked={form.aiUsage} onChange={() => setAiUsage(true)} className="h-4 w-4 border-slate-300 text-blue-600 focus:ring-blue-500" />예</label>
                                <label className="flex items-center gap-2 text-sm"><input type="radio" name="aiUsage" checked={!form.aiUsage} onChange={() => setAiUsage(false)} className="h-4 w-4 border-slate-300 text-blue-600 focus:ring-blue-500" />아니오</label>
                            </div>
                        </div>

                        <div className={`grid grid-cols-1 border-b border-slate-200 dark:border-slate-800 md:grid-cols-[190px_1fr] ${!form.aiUsage ? 'opacity-50' : ''}`}>
                            <div className="bg-slate-50 px-4 py-3 text-sm font-semibold dark:bg-slate-900/40">사용한 AI 도구 <span className="text-rose-500">*</span></div>
                            <div className="space-y-2 px-4 py-3">
                                {meta.aiTools.length === 0 && <p className="text-sm text-amber-600 dark:text-amber-400">등록된 AI 도구 공통코드가 없습니다.</p>}
                                {meta.aiTools.map((option) => {
                                    const code = String(option.code);
                                    const checked = form.aiToolCodes.includes(code);
                                    return (
                                        <div key={option.code} className="flex flex-wrap items-center gap-3">
                                            <label className="flex min-w-44 items-center gap-2 text-sm text-slate-700 dark:text-slate-300">
                                                <input type="checkbox" checked={checked} disabled={!form.aiUsage} onChange={(event) => toggleAiOption('aiToolCodes', code, event.target.checked)} className="h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500" />
                                                {option.name}
                                            </label>
                                            {option.isEtc === 'Y' && checked && (
                                                <>
                                                    <input type="text" value={form.otherAiToolName} onChange={(event) => setField('otherAiToolName', event.target.value)} disabled={!form.aiUsage} maxLength={255} placeholder="도구명" className="min-w-44 flex-1 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900" />
                                                    <input type="text" value={form.otherAiProviderName} onChange={(event) => setField('otherAiProviderName', event.target.value)} disabled={!form.aiUsage} maxLength={255} placeholder="개발사 / 제공사" className="min-w-44 flex-1 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900" />
                                                </>
                                            )}
                                        </div>
                                    );
                                })}
                            </div>
                        </div>

                        <div className={`grid grid-cols-1 border-b border-slate-200 dark:border-slate-800 md:grid-cols-[190px_1fr] ${!form.aiUsage ? 'opacity-50' : ''}`}>
                            <div className="bg-slate-50 px-4 py-3 text-sm font-semibold dark:bg-slate-900/40">버전 정보 <span className="text-rose-500">*</span></div>
                            <div className="px-4 py-3">
                                <input type="text" value={form.aiVersionInfo} onChange={(event) => setField('aiVersionInfo', event.target.value)} disabled={!form.aiUsage} maxLength={1000} placeholder="예: ChatGPT-5.2, Gemini 3" className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900" />
                            </div>
                        </div>

                        <div className={`grid grid-cols-1 border-b border-slate-200 dark:border-slate-800 md:grid-cols-[190px_1fr] ${!form.aiUsage ? 'opacity-50' : ''}`}>
                            <div className="bg-slate-50 px-4 py-3 text-sm font-semibold dark:bg-slate-900/40">AI 활용 범위 <span className="text-rose-500">*</span></div>
                            <div className="space-y-2 px-4 py-3">
                                {meta.aiScopes.length === 0 && <p className="text-sm text-amber-600 dark:text-amber-400">등록된 AI 활용 범위 공통코드가 없습니다.</p>}
                                {meta.aiScopes.map((option) => {
                                    const code = String(option.code);
                                    const checked = form.aiScopeCodes.includes(code);
                                    return (
                                        <div key={option.code} className="flex flex-wrap items-center gap-3">
                                            <label className="flex min-w-64 items-center gap-2 text-sm text-slate-700 dark:text-slate-300">
                                                <input type="checkbox" checked={checked} disabled={!form.aiUsage} onChange={(event) => toggleAiOption('aiScopeCodes', code, event.target.checked)} className="h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500" />
                                                {option.name}
                                            </label>
                                            {option.isEtc === 'Y' && checked && <input type="text" value={form.otherAiScopeText} onChange={(event) => setField('otherAiScopeText', event.target.value)} disabled={!form.aiUsage} maxLength={500} placeholder="기타 활용 범위" className="min-w-64 flex-1 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900" />}
                                        </div>
                                    );
                                })}
                            </div>
                        </div>

                        <div className={`grid grid-cols-1 border-b border-slate-200 dark:border-slate-800 md:grid-cols-[190px_1fr] ${!form.aiUsage ? 'opacity-50' : ''}`}>
                            <div className="bg-slate-50 px-4 py-3 text-sm font-semibold dark:bg-slate-900/40">특별 규정: 데이터 분석</div>
                            <div className="space-y-2 px-4 py-3">
                                <label className="flex items-start gap-2 text-sm text-slate-700 dark:text-slate-300">
                                    <input type="checkbox" checked={form.aiDataAnalysisUsed} disabled={!form.aiUsage} onChange={(event) => setField('aiDataAnalysisUsed', event.target.checked)} className="mt-0.5 h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500" />
                                    AI를 데이터 분석 또는 결과 해석에 사용했습니다.
                                </label>
                                <p className="text-xs font-medium leading-5 text-rose-600 dark:text-rose-400">AI가 연구 설계, 데이터 분석 또는 결과 해석에 관여한 경우 편집 검토 대상으로 표시되며, Methods에 사용 방법을 구체적으로 작성해야 합니다.</p>
                            </div>
                        </div>

                        <div className="grid grid-cols-1 md:grid-cols-[190px_1fr]">
                            <div className="bg-slate-50 px-4 py-3 text-sm font-semibold dark:bg-slate-900/40">정확성·표절·정책 준수 <span className="text-rose-500">*</span></div>
                            <div className="px-4 py-3">
                                <label className="flex items-start gap-2 text-sm leading-6 text-slate-700 dark:text-slate-300">
                                    <input type="checkbox" checked={form.plagiarismPolicyConfirmed} onChange={(event) => setField('plagiarismPolicyConfirmed', event.target.checked)} className="mt-1 h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500" />
                                    AI 지원 콘텐츠의 정확성을 확인했으며 표절, 허위 또는 오해를 유발하는 정보가 없음을 확인합니다. 관련 정책 미준수 시 초록이 반려되거나 철회될 수 있음을 이해합니다.
                                </label>
                            </div>
                        </div>
                    </div>

                    <div className="mt-4 rounded-xl border border-slate-200 p-4 dark:border-slate-800">
                        <div className="flex items-center justify-between gap-3">
                            <div>
                                <h3 className="text-sm font-semibold">기관 정보</h3>
                                <p className="mt-1 text-xs text-slate-400">등록한 기관명은 저자 소속 선택에 사용됩니다.</p>
                            </div>
                            <button type="button" onClick={addInstitution} className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-3 py-2 text-xs font-semibold text-white hover:bg-blue-700">
                                <Plus className="h-4 w-4" />기관 추가
                            </button>
                        </div>
                        <div className="mt-4 space-y-4">
                            {form.institutions.map((institution, index) => (
                                <div key={`${institution.institutionNo}-${index}`} className="rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-900/40">
                                    <div className="mb-3 flex items-center justify-between gap-3">
                                        <h4 className="text-sm font-semibold">{institution.institutionName.trim() || '기관 정보'}</h4>
                                        <button type="button" onClick={() => removeInstitution(index)} disabled={form.institutions.length === 1} className="inline-flex items-center gap-1 rounded-lg px-2 py-1 text-xs font-semibold text-rose-600 hover:bg-rose-50 disabled:cursor-not-allowed disabled:opacity-40 dark:hover:bg-rose-950/30">
                                            <Trash2 className="h-4 w-4" />삭제
                                        </button>
                                    </div>
                                    <div className="grid grid-cols-1 gap-3 md:grid-cols-4">
                                        <label className="space-y-1.5"><span className="text-xs font-semibold uppercase text-slate-500">국가</span>
                                            <CountrySelect countries={countries} value={institution.country}
                                                onChange={(value) => updateInstitution(index, { country: value })}
                                                ariaLabel={`소속기관 ${index + 1} 국가`} loading={loadingCountries} required />
                                        </label>
                                        <label className="space-y-1.5 md:col-span-3"><span className="text-xs font-semibold uppercase text-slate-500">기관명</span><input type="text" value={institution.institutionName} onChange={(event) => updateInstitution(index, { institutionName: event.target.value })} className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-950 dark:text-slate-50" required /></label>
                                        <label className="space-y-1.5 md:col-span-4"><span className="text-xs font-semibold uppercase text-slate-500">부서 / 학과</span><input type="text" value={institution.department} onChange={(event) => updateInstitution(index, { department: event.target.value })} className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-950 dark:text-slate-50" maxLength={1000} /></label>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>

                    <div className="mt-4 rounded-xl border border-slate-200 p-4 dark:border-slate-800">
                        <div className="flex items-center justify-between gap-3">
                            <div>
                                <h3 className="text-sm font-semibold">저자 정보</h3>
                                <p className="mt-1 text-xs text-slate-400">발표저자 1명 이상이 필요합니다.</p>
                            </div>
                            <button type="button" onClick={addAuthor} className="inline-flex items-center gap-2 rounded-lg bg-emerald-600 px-3 py-2 text-xs font-semibold text-white hover:bg-emerald-700">
                                <Plus className="h-4 w-4" />저자 추가
                            </button>
                        </div>
                        <div className="mt-4 space-y-4">
                            {form.authors.map((author, index) => (
                                <div key={`${author.authorOrder}-${index}`} className="rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-900/40">
                                    <div className="mb-3 flex items-center justify-between gap-3">
                                        <h4 className="text-sm font-semibold">저자 #{index + 1}</h4>
                                        <button type="button" onClick={() => removeAuthor(index)} disabled={form.authors.length === 1} className="inline-flex items-center gap-1 rounded-lg px-2 py-1 text-xs font-semibold text-rose-600 hover:bg-rose-50 disabled:cursor-not-allowed disabled:opacity-40 dark:hover:bg-rose-950/30">
                                            <Trash2 className="h-4 w-4" />삭제
                                        </button>
                                    </div>
                                    <div className="grid grid-cols-1 gap-3 md:grid-cols-4">
                                        <label className="space-y-1.5"><span className="text-xs font-semibold uppercase text-slate-500">저자 순서</span><input type="number" value={author.authorOrder} onChange={(event) => updateAuthor(index, { authorOrder: event.target.value })} className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-950 dark:text-slate-50" min={1} required /></label>
                                        <label className="space-y-1.5 md:col-span-2"><span className="text-xs font-semibold uppercase text-slate-500">저자명</span><input type="text" value={author.authorName} onChange={(event) => updateAuthor(index, { authorName: event.target.value })} className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-950 dark:text-slate-50" required /></label>
                                        <label className="space-y-1.5"><span className="text-xs font-semibold uppercase text-slate-500">소속 기관</span><select value={author.institutionNo} onChange={(event) => updateAuthor(index, { institutionNo: event.target.value })} className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-950 dark:text-slate-50">{institutionOptions.map((option) => { const institution = form.institutions.find((item) => item.institutionNo === option); return <option key={option} value={option}>{institution?.institutionName.trim() || '기관명 입력 필요'}{institution?.department.trim() ? ` · ${institution.department.trim()}` : ''}</option>; })}</select></label>
                                        <label className="space-y-1.5"><span className="text-xs font-semibold uppercase text-slate-500">이메일</span><input type="email" value={author.email} onChange={(event) => updateAuthor(index, { email: event.target.value })} className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-950 dark:text-slate-50" /></label>
                                        <label className="space-y-1.5"><span className="text-xs font-semibold uppercase text-slate-500">국가</span>
                                            <CountrySelect countries={countries} value={author.country}
                                                onChange={(value) => updateAuthor(index, { country: value })}
                                                ariaLabel={`저자 ${index + 1} 국가`} loading={loadingCountries} placeholder="선택 안 함" />
                                        </label>
                                    </div>
                                    <div className="mt-3 flex flex-wrap gap-4">
                                        <label className="flex items-center gap-2 text-sm text-slate-700 dark:text-slate-300"><input type="checkbox" checked={author.isPresentingAuthor} onChange={(event) => updateAuthor(index, { isPresentingAuthor: event.target.checked })} className="h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500" />발표저자</label>
                                        <label className="flex items-center gap-2 text-sm text-slate-700 dark:text-slate-300"><input type="checkbox" checked={author.isCorrespondingAuthor} onChange={(event) => updateAuthor(index, { isCorrespondingAuthor: event.target.checked })} className="h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500" />교신저자</label>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>

                    <div className="mt-5 flex flex-col-reverse gap-3 border-t border-slate-200 pt-4 dark:border-slate-800 md:flex-row md:items-center md:justify-between">
                        <div className="text-xs text-slate-400">저장 후 목록이 자동으로 갱신됩니다.</div>
                        <div className="flex items-center justify-end gap-2">
                            <button type="button" onClick={onClose} disabled={isBusy} className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900">취소</button>
                            <button type="submit" disabled={isBusy} className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-50">
                                <Save className="h-4 w-4" />{submitting ? '저장 중' : isEditMode ? '수정 저장' : '등록 저장'}
                            </button>
                        </div>
                    </div>
                </form>
            </div>
        </div>
    );
};
