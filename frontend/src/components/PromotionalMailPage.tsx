import { DraggableModal } from './DraggableModal';
import { useEffect, useRef, useState } from 'react';
import {
    BookUser,
    Building2,
    CheckCircle2,
    ChevronLeft,
    ChevronRight,
    Download,
    FilePlus2,
    FileSpreadsheet,
    LoaderCircle,
    Mail,
    Paperclip,
    Pencil,
    Plus,
    RefreshCw,
    Search,
    Send,
    ShieldCheck,
    Trash2,
    UserPlus,
    Users,
    X
} from 'lucide-react';
import type { NotificationType } from './NotificationToast';
import { useConfirm } from './confirmDialogContext';
import { MailRichTextEditor } from './MailRichTextEditor';
import { RecipientGroupSelector } from './RecipientGroupSelector';
import { RECIPIENT_GROUPS, type RecipientGroup } from '../recipientGroups';
import { RowActionMenu } from './RowActionMenu';

interface PromotionalMailPageProps {
    onNotify: (type: NotificationType, message: string) => void;
}

interface CampaignSummary {
    seq: number;
    subject: string;
    senderName: string;
    senderEmail: string;
    mailType: 'ADVERTISEMENT' | 'INFORMATION';
    status: string;
    scheduledAt?: string | null;
    sourceCount: number;
    attachmentCount: number;
    createdAt: string;
    updatedAt: string;
}

interface CampaignPageResponse {
    items: CampaignSummary[];
    page: number;
    size: number;
    totalCount: number;
    totalPages: number;
}

interface CampaignDetailResponse {
    campaign: CampaignSummary & {
        replyToEmail?: string | null;
        htmlContent: string;
        textContent?: string | null;
        trackOpens: boolean;
        trackClicks: boolean;
        versionNo: number;
    };
    recipientGroups: RecipientGroup[];
    addressBookSeqs: number[];
    directRecipients: DirectRecipient[];
    attachments: Attachment[];
}

interface RecipientPreview {
    groupCounts: Record<RecipientGroup, number>;
    includedCount: number;
    duplicateCount: number;
    suppressionCount: number;
    invalidCount: number;
}
const groupRecipientKey = (group: RecipientGroup) => `group:${group}`;

interface DirectRecipient {
    sourceType?: 'DIRECT' | 'INTERNAL_MEMBER' | 'INTERNAL_ADMIN' | 'ADDRESS_BOOK_CONTACT';
    email: string;
    fullName?: string | null;
    affiliation?: string | null;
}

interface InternalRecipientSearchResult {
    recipientType: 'MEMBER' | 'ADMIN' | 'ADDRESS_BOOK';
    referenceSeq: number;
    email: string;
    fullName?: string | null;
    affiliation?: string | null;
    detail: string;
}

interface Attachment {
    seq: number;
    originalFilename: string;
    fileSize: number;
}

interface MailAddressBook {
    seq: number;
    addressBookName: string;
    description?: string | null;
    contactCount: number;
}

interface MailContact {
    seq: number;
    email: string;
    fullName?: string | null;
    affiliation?: string | null;
    country?: string | null;
    phoneNumber?: string | null;
}

interface QueueResponse {
    jobSeq: number;
    includedCount: number;
    excludedCount: number;
    duplicateCount: number;
    suppressionCount: number;
    invalidCount: number;
}

interface AddressBookImportResponse {
    totalRows: number;
    importedCount: number;
    createdCount: number;
    updatedCount: number;
    duplicateCount: number;
    errorCount: number;
    errors: Array<{ rowNumber: number; message: string }>;
}

interface CampaignFormState {
    seq?: number;
    subject: string;
    senderName: string;
    senderEmail: string;
    replyToEmail: string;
    htmlContent: string;
    textContent: string;
    mailType: 'ADVERTISEMENT' | 'INFORMATION';
    trackOpens: boolean;
    trackClicks: boolean;
    scheduledAt: string;
    versionNo?: number;
    recipientGroups: RecipientGroup[];
    addressBookSeqs: number[];
    directRecipients: DirectRecipient[];
}

const EMPTY_CAMPAIGN: CampaignFormState = {
    subject: '',
    senderName: 'APDRC8',
    senderEmail: '',
    replyToEmail: '',
    htmlContent: '',
    textContent: '',
    mailType: 'ADVERTISEMENT',
    trackOpens: true,
    trackClicks: true,
    scheduledAt: '',
    recipientGroups: [],
    addressBookSeqs: [],
    directRecipients: []
};

const PAGE_SIZE = 10;
const addressBookRecipientKey = (seq: number) => `book:${seq}`;
const directRecipientSourceKey = (email: string) => `direct:${email.trim().toLowerCase()}`;

export const PromotionalMailPage = ({ onNotify }: PromotionalMailPageProps) => {
    const confirm = useConfirm();
    const [activeTab, setActiveTab] = useState<'campaigns' | 'addressBooks'>('campaigns');
    const [campaigns, setCampaigns] = useState<CampaignSummary[]>([]);
    const [campaignKeyword, setCampaignKeyword] = useState('');
    const [campaignPage, setCampaignPage] = useState(1);
    const [campaignTotal, setCampaignTotal] = useState(0);
    const [campaignTotalPages, setCampaignTotalPages] = useState(1);
    const [campaignReload, setCampaignReload] = useState(0);
    const [addressBooks, setAddressBooks] = useState<MailAddressBook[]>([]);
    const [addressBookReload, setAddressBookReload] = useState(0);
    const [selectedAddressBook, setSelectedAddressBook] = useState<MailAddressBook | null>(null);
    const [contacts, setContacts] = useState<MailContact[]>([]);
    const [isImportingContacts, setIsImportingContacts] = useState(false);
    const [contactImportResult, setContactImportResult] = useState<AddressBookImportResponse | null>(null);
    const [isLoading, setIsLoading] = useState(false);
    const [isCampaignModalOpen, setIsCampaignModalOpen] = useState(false);
    const [campaignForm, setCampaignForm] = useState<CampaignFormState>(EMPTY_CAMPAIGN);
    const [campaignFiles, setCampaignFiles] = useState<File[]>([]);
    const [existingAttachments, setExistingAttachments] = useState<Attachment[]>([]);
    const [addressBookToAdd, setAddressBookToAdd] = useState('');
    const [directRecipientForm, setDirectRecipientForm] = useState({ fullName: '', email: '' });
    const [selectedRecipientKeys, setSelectedRecipientKeys] = useState<string[]>([]);
    const [isSaving, setIsSaving] = useState(false);
    const [isAddressBookFormOpen, setIsAddressBookFormOpen] = useState(false);
    const [deletingAddressBookSeq, setDeletingAddressBookSeq] = useState<number | null>(null);
    const [addressBookForm, setAddressBookForm] = useState({ addressBookName: '', description: '' });
    const [contactForm, setContactForm] = useState({ email: '', fullName: '', affiliation: '', country: '', phoneNumber: '' });
    const notifyRef = useRef(onNotify);

    useEffect(() => {
        notifyRef.current = onNotify;
    }, [onNotify]);

    useEffect(() => {
        const controller = new AbortController();
        const load = async () => {
            setIsLoading(true);
            try {
                const params = new URLSearchParams({
                    page: String(campaignPage),
                    size: String(PAGE_SIZE),
                    keyword: campaignKeyword.trim()
                });
                const response = await fetch(`/api/admin/mail/campaigns?${params}`, { signal: controller.signal });
                if (!response.ok) throw new Error(await response.text() || '캠페인 목록을 불러오지 못했습니다.');
                const data = await response.json() as CampaignPageResponse;
                setCampaigns(data.items);
                setCampaignTotal(data.totalCount);
                setCampaignTotalPages(data.totalPages);
                if (data.page !== campaignPage) setCampaignPage(data.page);
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') return;
                notifyRef.current('error', error instanceof Error ? error.message : '캠페인 목록을 불러오지 못했습니다.');
            } finally {
                setIsLoading(false);
            }
        };
        void load();
        return () => controller.abort();
    }, [campaignKeyword, campaignPage, campaignReload]);

    useEffect(() => {
        const controller = new AbortController();
        const load = async () => {
            try {
                const response = await fetch('/api/admin/mail/address-books', { signal: controller.signal });
                if (!response.ok) throw new Error(await response.text() || '주소록을 불러오지 못했습니다.');
                const data = await response.json() as MailAddressBook[];
                setAddressBooks(data);
                setSelectedAddressBook((current) => current
                    ? data.find((item) => item.seq === current.seq) ?? data[0] ?? null
                    : data[0] ?? null);
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') return;
                notifyRef.current('error', error instanceof Error ? error.message : '주소록을 불러오지 못했습니다.');
            }
        };
        void load();
        return () => controller.abort();
    }, [addressBookReload]);

    useEffect(() => {
        if (!selectedAddressBook) {
            return;
        }
        const controller = new AbortController();
        const load = async () => {
            try {
                const response = await fetch(`/api/admin/mail/address-books/${selectedAddressBook.seq}/contacts`, { signal: controller.signal });
                if (!response.ok) throw new Error(await response.text() || '연락처를 불러오지 못했습니다.');
                setContacts(await response.json() as MailContact[]);
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') return;
                notifyRef.current('error', error instanceof Error ? error.message : '연락처를 불러오지 못했습니다.');
            }
        };
        void load();
        return () => controller.abort();
    }, [selectedAddressBook, addressBookReload]);

    const openCreateCampaign = () => {
        setCampaignForm({ ...EMPTY_CAMPAIGN, recipientGroups: [], addressBookSeqs: [], directRecipients: [] });
        setCampaignFiles([]);
        setExistingAttachments([]);
        setAddressBookToAdd('');
        setDirectRecipientForm({ fullName: '', email: '' });
        setSelectedRecipientKeys([]);
        setIsCampaignModalOpen(true);
    };

    const openEditCampaign = async (seq: number) => {
        try {
            const response = await fetch(`/api/admin/mail/campaigns/${seq}`);
            if (!response.ok) throw new Error(await response.text() || '캠페인을 불러오지 못했습니다.');
            const detail = await response.json() as CampaignDetailResponse;
            const campaign = detail.campaign;
            setCampaignForm({
                seq: campaign.seq,
                subject: campaign.subject,
                senderName: campaign.senderName,
                senderEmail: campaign.senderEmail,
                replyToEmail: campaign.replyToEmail ?? '',
                htmlContent: campaign.htmlContent,
                textContent: campaign.textContent ?? '',
                mailType: campaign.mailType,
                trackOpens: campaign.trackOpens,
                trackClicks: campaign.trackClicks,
                scheduledAt: campaign.scheduledAt ? campaign.scheduledAt.slice(0, 16) : '',
                versionNo: campaign.versionNo,
                recipientGroups: detail.recipientGroups ?? [],
                addressBookSeqs: detail.addressBookSeqs,
                directRecipients: detail.directRecipients
            });
            setCampaignFiles([]);
            setExistingAttachments(detail.attachments);
            setAddressBookToAdd('');
            setDirectRecipientForm({ fullName: '', email: '' });
            setSelectedRecipientKeys([]);
            setIsCampaignModalOpen(true);
        } catch (error) {
            notifyRef.current('error', error instanceof Error ? error.message : '캠페인을 불러오지 못했습니다.');
        }
    };

    const recipientSourceKeys = [
        ...campaignForm.recipientGroups.map(groupRecipientKey),
        ...campaignForm.addressBookSeqs.map(addressBookRecipientKey),
        ...campaignForm.directRecipients.map((recipient) => directRecipientSourceKey(recipient.email))
    ];
    const allRecipientSourcesSelected = recipientSourceKeys.length > 0
        && recipientSourceKeys.every((key) => selectedRecipientKeys.includes(key));

    const addAddressBookSource = () => {
        const seq = Number(addressBookToAdd);
        const addressBook = addressBooks.find((book) => book.seq === seq);
        if (!addressBook) {
            notifyRef.current('info', '추가할 주소록을 선택해 주세요.');
            return;
        }
        if (campaignForm.addressBookSeqs.includes(seq)) {
            notifyRef.current('info', '이미 추가된 주소록입니다.');
            return;
        }
        setCampaignForm((current) => ({
            ...current,
            addressBookSeqs: [...current.addressBookSeqs, seq]
        }));
        setAddressBookToAdd('');
    };

    const addDirectRecipient = () => {
        const fullName = directRecipientForm.fullName.trim();
        const email = directRecipientForm.email.trim();
        if (!fullName) {
            notifyRef.current('info', '개별 수신자 이름을 입력해 주세요.');
            return;
        }
        if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
            notifyRef.current('info', '올바른 이메일 주소를 입력해 주세요.');
            return;
        }
        if (campaignForm.directRecipients.some((recipient) => recipient.email.trim().toLowerCase() === email.toLowerCase())) {
            notifyRef.current('info', '이미 추가된 개별 수신자입니다.');
            return;
        }
        setCampaignForm((current) => ({
            ...current,
            directRecipients: [...current.directRecipients, {
                sourceType: 'DIRECT',
                fullName,
                email
            }]
        }));
        setDirectRecipientForm({ fullName: '', email: '' });
    };

    const addInternalRecipients = (recipients: InternalRecipientSearchResult[]) => {
        const existingEmails = new Set(campaignForm.directRecipients.map((recipient) => recipient.email.trim().toLowerCase()));
        const additions = recipients.filter((recipient) => {
            const normalizedEmail = recipient.email.trim().toLowerCase();
            if (existingEmails.has(normalizedEmail)) return false;
            existingEmails.add(normalizedEmail);
            return true;
        });
        if (additions.length === 0) {
            notifyRef.current('info', '선택한 수신자는 이미 추가되어 있습니다.');
            return;
        }
        setCampaignForm((current) => ({
            ...current,
            directRecipients: [...current.directRecipients, ...additions.map((recipient) => ({
                sourceType: recipient.recipientType === 'MEMBER'
                    ? 'INTERNAL_MEMBER' as const
                    : recipient.recipientType === 'ADMIN'
                        ? 'INTERNAL_ADMIN' as const
                        : 'ADDRESS_BOOK_CONTACT' as const,
                email: recipient.email,
                fullName: recipient.fullName,
                affiliation: recipient.affiliation
            }))]
        }));
        notifyRef.current('success', `선택한 수신자 ${additions.length.toLocaleString()}명을 추가했습니다.`);
    };

    const toggleRecipientSource = (key: string) => {
        setSelectedRecipientKeys((current) => current.includes(key)
            ? current.filter((item) => item !== key)
            : [...current, key]);
    };

    const removeRecipientSources = (keys: string[]) => {
        if (keys.length === 0) return;
        const keySet = new Set(keys);
        setCampaignForm((current) => ({
            ...current,
            recipientGroups: current.recipientGroups.filter((group) => !keySet.has(groupRecipientKey(group))),
            addressBookSeqs: current.addressBookSeqs.filter((seq) => !keySet.has(addressBookRecipientKey(seq))),
            directRecipients: current.directRecipients.filter((recipient) => !keySet.has(directRecipientSourceKey(recipient.email)))
        }));
        setSelectedRecipientKeys((current) => current.filter((key) => !keySet.has(key)));
    };

    const toggleAllRecipientSources = () => {
        setSelectedRecipientKeys(allRecipientSourcesSelected ? [] : recipientSourceKeys);
    };

    const saveCampaign = async () => {
        const payload = {
            ...campaignForm,
            htmlContent: campaignForm.htmlContent,
            scheduledAt: campaignForm.scheduledAt || null
        };
        setIsSaving(true);
        try {
            const response = await fetch(campaignForm.seq
                ? `/api/admin/mail/campaigns/${campaignForm.seq}`
                : '/api/admin/mail/campaigns', {
                method: campaignForm.seq ? 'PUT' : 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            if (!response.ok) throw new Error(await response.text() || '메일 저장에 실패했습니다.');
            const detail = await response.json() as CampaignDetailResponse;
            for (const file of campaignFiles) {
                const formData = new FormData();
                formData.append('file', file);
                const uploadResponse = await fetch(`/api/admin/mail/campaigns/${detail.campaign.seq}/attachments`, {
                    method: 'POST', body: formData
                });
                if (!uploadResponse.ok) throw new Error(await uploadResponse.text() || `${file.name} 업로드에 실패했습니다.`);
            }
            notifyRef.current('success', '메일을 저장했습니다.');
            setIsCampaignModalOpen(false);
            setCampaignReload((value) => value + 1);
        } catch (error) {
            notifyRef.current('error', error instanceof Error ? error.message : '메일 저장에 실패했습니다.');
        } finally {
            setIsSaving(false);
        }
    };

    const prepareCampaign = async (campaign: CampaignSummary) => {
        if (!await confirm({
            title: '발송 준비',
            message: `"${campaign.subject}" 메일의 수신자 스냅샷을 생성하시겠습니까?\n실제 메일은 아직 발송되지 않습니다.`,
            confirmText: '준비'
        })) return;
        try {
            const response = await fetch(`/api/admin/mail/campaigns/${campaign.seq}/prepare`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ idempotencyKey: crypto.randomUUID() })
            });
            if (!response.ok) throw new Error(await response.text() || '발송 준비에 실패했습니다.');
            const result = await response.json() as QueueResponse;
            notifyRef.current('success', `작업 #${result.jobSeq}: 포함 ${result.includedCount}명, 수신 거부 ${result.suppressionCount}명, 중복 ${result.duplicateCount}건, 이메일 오류 ${result.invalidCount}건`);
            setCampaignReload((value) => value + 1);
        } catch (error) {
            notifyRef.current('error', error instanceof Error ? error.message : '발송 준비에 실패했습니다.');
        }
    };

    const deleteCampaign = async (campaign: CampaignSummary) => {
        if (!await confirm({ title: '메일 삭제', message: `"${campaign.subject}" 메일을 삭제하시겠습니까?`, confirmText: '삭제', tone: 'danger' })) return;
        try {
            const response = await fetch(`/api/admin/mail/campaigns/${campaign.seq}`, { method: 'DELETE' });
            if (!response.ok) throw new Error(await response.text() || '메일 삭제에 실패했습니다.');
            notifyRef.current('success', '캠페인을 삭제했습니다.');
            setCampaignReload((value) => value + 1);
        } catch (error) {
            notifyRef.current('error', error instanceof Error ? error.message : '메일 삭제에 실패했습니다.');
        }
    };

    const deleteExistingAttachment = async (attachment: Attachment) => {
        if (!campaignForm.seq || !await confirm({ title: '첨부파일 삭제', message: `${attachment.originalFilename} 첨부파일을 삭제하시겠습니까?`, confirmText: '삭제', tone: 'danger' })) return;
        try {
            const response = await fetch(`/api/admin/mail/campaigns/${campaignForm.seq}/attachments/${attachment.seq}`, { method: 'DELETE' });
            if (!response.ok) throw new Error(await response.text() || '첨부파일 삭제에 실패했습니다.');
            setExistingAttachments((items) => items.filter((item) => item.seq !== attachment.seq));
        } catch (error) {
            notifyRef.current('error', error instanceof Error ? error.message : '첨부파일 삭제에 실패했습니다.');
        }
    };

    const saveAddressBook = async () => {
        try {
            const response = await fetch('/api/admin/mail/address-books', {
                method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(addressBookForm)
            });
            if (!response.ok) throw new Error(await response.text() || '주소록 저장에 실패했습니다.');
            notifyRef.current('success', '주소록을 만들었습니다.');
            setAddressBookForm({ addressBookName: '', description: '' });
            setIsAddressBookFormOpen(false);
            setAddressBookReload((value) => value + 1);
        } catch (error) {
            notifyRef.current('error', error instanceof Error ? error.message : '주소록 저장에 실패했습니다.');
        }
    };

    const saveContact = async () => {
        if (!selectedAddressBook) return;
        try {
            const response = await fetch(`/api/admin/mail/address-books/${selectedAddressBook.seq}/contacts`, {
                method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(contactForm)
            });
            if (!response.ok) throw new Error(await response.text() || '연락처 저장에 실패했습니다.');
            notifyRef.current('success', '연락처를 등록했습니다.');
            setContactForm({ email: '', fullName: '', affiliation: '', country: '', phoneNumber: '' });
            setAddressBookReload((value) => value + 1);
        } catch (error) {
            notifyRef.current('error', error instanceof Error ? error.message : '연락처 저장에 실패했습니다.');
        }
    };

    const deleteAddressBook = async (addressBook: MailAddressBook) => {
        if (!await confirm({
            title: '주소록 삭제',
            message: `"${addressBook.addressBookName}" 주소록과 포함된 연락처 ${addressBook.contactCount.toLocaleString()}명을 삭제하시겠습니까?\n다른 주소록의 연락처에는 영향을 주지 않습니다.`,
            confirmText: '삭제',
            tone: 'danger'
        })) return;
        setDeletingAddressBookSeq(addressBook.seq);
        try {
            const response = await fetch(`/api/admin/mail/address-books/${addressBook.seq}`, { method: 'DELETE' });
            if (!response.ok) throw new Error(await response.text() || '주소록 삭제에 실패했습니다.');
            if (selectedAddressBook?.seq === addressBook.seq) {
                setSelectedAddressBook(null);
                setContacts([]);
                setContactImportResult(null);
            }
            setCampaignForm((current) => ({
                ...current,
                addressBookSeqs: current.addressBookSeqs.filter((seq) => seq !== addressBook.seq)
            }));
            notifyRef.current('success', '주소록을 삭제했습니다.');
            setAddressBookReload((value) => value + 1);
        } catch (error) {
            notifyRef.current('error', error instanceof Error ? error.message : '주소록 삭제에 실패했습니다.');
        } finally {
            setDeletingAddressBookSeq(null);
        }
    };

    const importContacts = async (file: File) => {
        if (!selectedAddressBook) return;
        if (!file.name.toLowerCase().endsWith('.xlsx')) {
            notifyRef.current('error', '.xlsx 형식의 엑셀 파일을 선택해 주세요.');
            return;
        }
        setIsImportingContacts(true);
        setContactImportResult(null);
        try {
            const formData = new FormData();
            formData.append('file', file);
            const response = await fetch(`/api/admin/mail/address-books/${selectedAddressBook.seq}/contacts/import`, {
                method: 'POST',
                body: formData
            });
            const responseText = await response.text();
            let result: AddressBookImportResponse | null = null;
            try {
                result = JSON.parse(responseText) as AddressBookImportResponse;
            } catch {
                // 공통 예외 응답은 평문일 수 있다.
            }
            if (!response.ok) {
                if (result) setContactImportResult(result);
                const firstError = result?.errors[0];
                throw new Error(firstError
                    ? `${firstError.rowNumber > 0 ? `${firstError.rowNumber}행: ` : ''}${firstError.message}`
                    : responseText || '주소록 일괄등록에 실패했습니다.');
            }
            if (!result) throw new Error('주소록 일괄등록 결과를 확인할 수 없습니다.');
            setContactImportResult(result);
            notifyRef.current('success', `엑셀 ${result.importedCount.toLocaleString()}건 처리 · 신규 등록 ${result.createdCount.toLocaleString()}명`);
            setAddressBookReload((value) => value + 1);
        } catch (error) {
            notifyRef.current('error', error instanceof Error ? error.message : '주소록 일괄등록에 실패했습니다.');
        } finally {
            setIsImportingContacts(false);
        }
    };

    const deleteContact = async (contact: MailContact) => {
        if (!selectedAddressBook || !await confirm({ title: '연락처 삭제', message: `${contact.email} 연락처를 삭제하시겠습니까?`, confirmText: '삭제', tone: 'danger' })) return;
        try {
            const response = await fetch(`/api/admin/mail/address-books/${selectedAddressBook.seq}/contacts/${contact.seq}`, { method: 'DELETE' });
            if (!response.ok) throw new Error(await response.text() || '연락처 삭제에 실패했습니다.');
            setAddressBookReload((value) => value + 1);
        } catch (error) {
            notifyRef.current('error', error instanceof Error ? error.message : '연락처 삭제에 실패했습니다.');
        }
    };

    return (
        <div className="space-y-5">
            <section className="rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-950">
                <div className="flex flex-col justify-between gap-4 border-b border-slate-200 p-5 dark:border-slate-800 md:flex-row md:items-center">
                    <div>
                        <div className="flex items-center gap-2">
                            <Mail className="h-5 w-5 text-blue-600 dark:text-blue-400" />
                            <h2 className="text-lg font-bold">메일발송</h2>
                        </div>
                        <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">메일과 수신자를 저장하고 공급자 연동 전 발송 작업을 준비합니다.</p>
                    </div>
                    <div className="flex rounded-lg bg-slate-100 p-1 dark:bg-slate-900">
                        <TabButton active={activeTab === 'campaigns'} onClick={() => setActiveTab('campaigns')} icon={<Mail className="h-4 w-4" />} label="메일" />
                        <TabButton active={activeTab === 'addressBooks'} onClick={() => setActiveTab('addressBooks')} icon={<BookUser className="h-4 w-4" />} label="주소록" />
                    </div>
                </div>

                {activeTab === 'campaigns' ? (
                    <div>
                        <div className="flex flex-col gap-3 border-b border-slate-200 p-4 dark:border-slate-800 sm:flex-row sm:items-center sm:justify-between">
                            <div className="relative w-full sm:max-w-sm">
                                <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                                <input value={campaignKeyword} onChange={(event) => { setCampaignKeyword(event.target.value); setCampaignPage(1); }} className={inputClass('pl-9')} placeholder="메일 제목 검색" />
                            </div>
                            <div className="flex gap-2">
                                <button onClick={openCreateCampaign} className={primaryButtonClass}><Plus className="h-4 w-4" />메일 작성</button>
                            </div>
                        </div>
                        <div className="overflow-x-auto">
                            <table className="min-w-full text-left text-sm">
                                <thead className="bg-slate-50 dark:bg-slate-900">
                                    <tr><th className="px-4 py-3">메일 제목</th><th className="px-4 py-3">유형/상태</th><th className="px-4 py-3">수신 소스</th><th className="px-4 py-3">예약</th><th className="px-4 py-3 text-right">기능</th></tr>
                                </thead>
                                <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                                    {campaigns.map((campaign) => (
                                        <tr key={campaign.seq} className="hover:bg-slate-50 dark:hover:bg-slate-900/60">
                                            <td className="px-4 py-3"><p className="font-semibold">{campaign.subject}</p></td>
                                            <td className="px-4 py-3"><span className={campaign.mailType === 'ADVERTISEMENT' ? amberBadge : blueBadge}>{campaign.mailType === 'ADVERTISEMENT' ? '광고성' : '안내성'}</span><span className="ml-1 rounded-full bg-slate-100 px-2 py-1 text-[11px] dark:bg-slate-800">{campaign.status}</span></td>
                                            <td className="px-4 py-3 text-slate-600 dark:text-slate-300">{campaign.sourceCount}개 · 첨부 {campaign.attachmentCount}개</td>
                                            <td className="px-4 py-3 text-xs text-slate-500">{formatDate(campaign.scheduledAt)}</td>
                                            <td className="px-4 py-3 text-right">{campaign.status === 'DRAFT' && <RowActionMenu itemLabel={campaign.subject} actions={[
                                                { label: '수정', icon: <Pencil className="h-4 w-4" />, onClick: () => void openEditCampaign(campaign.seq) },
                                                { label: '발송 준비', icon: <Send className="h-4 w-4" />, onClick: () => void prepareCampaign(campaign) },
                                                { label: '삭제', icon: <Trash2 className="h-4 w-4" />, onClick: () => void deleteCampaign(campaign), tone: 'danger' }
                                            ]} />}</td>
                                        </tr>
                                    ))}
                                    {!isLoading && campaigns.length === 0 && <tr><td colSpan={5} className="px-4 py-14 text-center text-slate-400">저장된 메일이 없습니다.</td></tr>}
                                </tbody>
                            </table>
                        </div>
                        <div className="flex items-center justify-between border-t border-slate-200 p-4 text-xs text-slate-500 dark:border-slate-800">
                            <span>총 {campaignTotal.toLocaleString()}건</span>
                            <div className="flex items-center gap-2"><button disabled={campaignPage <= 1} onClick={() => setCampaignPage((value) => value - 1)} className={pageButtonClass}><ChevronLeft className="h-4 w-4" /></button><span>{campaignPage} / {campaignTotalPages}</span><button disabled={campaignPage >= campaignTotalPages} onClick={() => setCampaignPage((value) => value + 1)} className={pageButtonClass}><ChevronRight className="h-4 w-4" /></button></div>
                        </div>
                    </div>
                ) : (
                    <div className="grid min-h-[520px] lg:grid-cols-[320px_1fr]">
                        <aside className="border-b border-slate-200 p-4 dark:border-slate-800 lg:border-b-0 lg:border-r">
                            <div className="mb-3 flex items-center justify-between"><h3 className="font-semibold">주소록</h3><button onClick={() => setIsAddressBookFormOpen((value) => !value)} className={primaryButtonClass}><Plus className="h-4 w-4" />추가</button></div>
                            {isAddressBookFormOpen && <div className="mb-4 space-y-2 rounded-lg border border-blue-200 bg-blue-50 p-3 dark:border-blue-900 dark:bg-blue-950/30">
                                <input value={addressBookForm.addressBookName} onChange={(event) => setAddressBookForm({ ...addressBookForm, addressBookName: event.target.value })} className={inputClass()} placeholder="주소록명" />
                                <textarea value={addressBookForm.description} onChange={(event) => setAddressBookForm({ ...addressBookForm, description: event.target.value })} className={inputClass()} placeholder="설명" />
                                <button onClick={() => void saveAddressBook()} className={`${primaryButtonClass} w-full justify-center`}>저장</button>
                            </div>}
                            <div className="space-y-2">{addressBooks.map((book) => <div key={book.seq} className={`flex overflow-hidden rounded-lg border transition ${selectedAddressBook?.seq === book.seq ? 'border-blue-500 bg-blue-50 dark:bg-blue-950/30' : 'border-slate-200 hover:bg-slate-50 dark:border-slate-800 dark:hover:bg-slate-900'}`}><button onClick={() => { setSelectedAddressBook(book); setContactImportResult(null); }} className="min-w-0 flex-1 p-3 text-left"><div className="flex min-w-0 items-center gap-3"><span className="min-w-0 flex-1 truncate font-semibold">{book.addressBookName}</span><span className={`${blueBadge} shrink-0 whitespace-nowrap tabular-nums`}>{book.contactCount.toLocaleString()}명</span></div><p className="mt-1 truncate text-xs text-slate-500">{book.description || '설명 없음'}</p></button><button type="button" title="주소록 삭제" disabled={deletingAddressBookSeq !== null} onClick={() => void deleteAddressBook(book)} className="shrink-0 border-l border-slate-200 px-3 text-slate-400 hover:bg-rose-50 hover:text-rose-500 disabled:opacity-40 dark:border-slate-800 dark:hover:bg-rose-950/40">{deletingAddressBookSeq === book.seq ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}</button></div>)}</div>
                        </aside>
                        <div className="p-4 md:p-5">
                            {selectedAddressBook ? <>
                                <div className="mb-5 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                                    <div><h3 className="font-semibold">{selectedAddressBook.addressBookName} 연락처</h3><p className="text-xs text-slate-500">주소록에 등록된 연락처는 모두 발송 가능한 수신자로 처리됩니다.</p></div>
                                    <div className="flex flex-wrap gap-2">
                                        <a href="/templates/mail_address_book_import_template.xlsx" download className={secondaryButtonClass}><Download className="h-4 w-4" />엑셀 양식</a>
                                        <label className={`inline-flex items-center justify-center gap-2 rounded-lg border border-violet-200 bg-violet-50 px-3 py-2 text-xs font-semibold text-violet-700 hover:bg-violet-100 disabled:opacity-50 dark:border-violet-900/60 dark:bg-violet-950/30 dark:text-violet-300 dark:hover:bg-violet-950/50 cursor-pointer focus-within:ring-2 focus-within:ring-violet-500 dark:focus-within:ring-violet-400 ${isImportingContacts ? 'pointer-events-none opacity-50' : ''}`}>
                                            {isImportingContacts ? <LoaderCircle className="h-4 w-4 animate-spin" /> : <FileSpreadsheet className="h-4 w-4" />}엑셀 일괄등록
                                            <input
                                                type="file"
                                                accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                                className="sr-only"
                                                disabled={isImportingContacts}
                                                onChange={(event) => {
                                                    const file = event.target.files?.[0];
                                                    if (file) void importContacts(file);
                                                    event.target.value = '';
                                                }}
                                            />
                                        </label>
                                    </div>
                                </div>
                                <div className="mb-5 grid items-end gap-4 rounded-xl bg-slate-50 p-4 dark:bg-slate-900 sm:grid-cols-2 xl:grid-cols-4">
                                    <Field label="이메일 *"><input maxLength={255} value={contactForm.email} onChange={(event) => setContactForm({ ...contactForm, email: event.target.value })} className={inputClass()} placeholder="이메일 *" /></Field>
                                    <Field label="이름"><input maxLength={255} value={contactForm.fullName} onChange={(event) => setContactForm({ ...contactForm, fullName: event.target.value })} className={inputClass()} placeholder="이름" /></Field>
                                    <Field label="소속"><input maxLength={255} value={contactForm.affiliation} onChange={(event) => setContactForm({ ...contactForm, affiliation: event.target.value })} className={inputClass()} placeholder="소속" /></Field>
                                    <Field label="연락처"><input type="tel" maxLength={50} value={contactForm.phoneNumber} onChange={(event) => setContactForm({ ...contactForm, phoneNumber: event.target.value })} className={inputClass()} placeholder="010-1234-5678" /></Field>
                                    <button onClick={() => void saveContact()} className={`${primaryButtonClass} justify-center`}><Plus className="h-4 w-4" />등록</button>
                                </div>
                                {contactImportResult && <div className={`mb-5 rounded-xl border p-4 text-xs ${contactImportResult.errorCount > 0 ? 'border-rose-200 bg-rose-50 text-rose-700 dark:border-rose-900 dark:bg-rose-950/30 dark:text-rose-300' : 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-900 dark:bg-emerald-950/30 dark:text-emerald-300'}`}>
                                    <p className="font-semibold">{contactImportResult.errorCount > 0 ? `검증 오류 ${contactImportResult.errorCount.toLocaleString()}건 — DB에는 반영하지 않았습니다.` : `총 ${contactImportResult.totalRows.toLocaleString()}행을 처리했습니다.`}</p>
                                    {contactImportResult.errorCount === 0 && <p className="mt-1">신규 등록 {contactImportResult.createdCount.toLocaleString()}명 · 기존 갱신 {contactImportResult.updatedCount.toLocaleString()}명 · 파일 내 중복 {contactImportResult.duplicateCount.toLocaleString()}명</p>}
                                    {contactImportResult.errors.length > 0 && <ul className="mt-2 max-h-28 list-disc space-y-1 overflow-y-auto pl-5">{contactImportResult.errors.map((error, index) => <li key={`${error.rowNumber}-${index}`}>{error.rowNumber > 0 ? `${error.rowNumber}행: ` : ''}{error.message}</li>)}</ul>}
                                </div>}
                                <div className="overflow-x-auto rounded-lg border border-slate-200 dark:border-slate-800"><table className="min-w-full text-left text-sm"><thead className="bg-slate-50 dark:bg-slate-900"><tr><th className="px-3 py-2">이름</th><th className="px-3 py-2">이메일</th><th className="px-3 py-2">소속</th><th className="px-3 py-2">연락처</th><th className="px-3 py-2" /></tr></thead><tbody className="divide-y divide-slate-200 dark:divide-slate-800">{contacts.map((contact) => <tr key={contact.seq}><td className="px-3 py-2">{contact.fullName || '-'}</td><td className="px-3 py-2">{contact.email}</td><td className="px-3 py-2">{contact.affiliation || '-'}</td><td className="whitespace-nowrap px-3 py-2">{contact.phoneNumber || '-'}</td><td className="px-3 py-2 text-right"><IconButton title="연락처 삭제" onClick={() => void deleteContact(contact)}><Trash2 className="h-4 w-4 text-rose-500" /></IconButton></td></tr>)}{contacts.length === 0 && <tr><td colSpan={5} className="py-12 text-center text-slate-400">등록된 연락처가 없습니다.</td></tr>}</tbody></table></div>
                            </> : <div className="flex h-full items-center justify-center text-slate-400"><Users className="mr-2 h-5 w-5" />주소록을 먼저 만들어 주세요.</div>}
                        </div>
                    </div>
                )}
            </section>

            {isCampaignModalOpen && <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-3"><DraggableModal className="max-h-[96vh] w-full max-w-6xl overflow-y-auto rounded-xl bg-white shadow-2xl dark:bg-slate-950">
                <div data-modal-drag-handle className="cursor-move select-none touch-none sticky top-0 z-10 flex items-center justify-between border-b border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-950"><div><h3 className="font-bold">{campaignForm.seq ? '메일 수정' : '메일 작성'}</h3><p className="text-xs text-slate-500">현재 단계에서는 DB 저장과 발송 작업 준비까지만 수행합니다.</p></div><button onClick={() => setIsCampaignModalOpen(false)} className={pageButtonClass}><X className="h-5 w-5" /></button></div>
                <div className="space-y-5 p-4 md:p-6">
                    <div className="grid gap-3 sm:grid-cols-3"><Field label="발신자명"><input value={campaignForm.senderName} onChange={(event) => setCampaignForm({ ...campaignForm, senderName: event.target.value })} className={inputClass()} /></Field><Field label="발신 이메일"><input type="email" value={campaignForm.senderEmail} onChange={(event) => setCampaignForm({ ...campaignForm, senderEmail: event.target.value })} className={inputClass()} /></Field><Field label="회신 이메일"><input type="email" value={campaignForm.replyToEmail} onChange={(event) => setCampaignForm({ ...campaignForm, replyToEmail: event.target.value })} className={inputClass()} /></Field></div>

                    <Field label="메일 제목"><input value={campaignForm.subject} onChange={(event) => setCampaignForm({ ...campaignForm, subject: event.target.value })} className={inputClass()} maxLength={500} /></Field>

                    <div className="space-y-4">
                        <div className="grid gap-3 sm:grid-cols-2"><Field label="메일 유형"><select value={campaignForm.mailType} onChange={(event) => setCampaignForm({ ...campaignForm, mailType: event.target.value as 'ADVERTISEMENT' | 'INFORMATION' })} className={inputClass()}><option value="ADVERTISEMENT">광고성</option><option value="INFORMATION">안내성</option></select></Field><Field label="예약 일시"><input type="datetime-local" value={campaignForm.scheduledAt} onChange={(event) => setCampaignForm({ ...campaignForm, scheduledAt: event.target.value })} className={inputClass()} /></Field></div>
                        <div className="flex gap-4 text-xs"><label className="flex items-center gap-2"><input type="checkbox" checked={campaignForm.trackOpens} onChange={(event) => setCampaignForm({ ...campaignForm, trackOpens: event.target.checked })} />읽음 추적</label><label className="flex items-center gap-2"><input type="checkbox" checked={campaignForm.trackClicks} onChange={(event) => setCampaignForm({ ...campaignForm, trackClicks: event.target.checked })} />클릭 추적</label></div>
                    </div>

                    <div role="group" aria-labelledby="mail-html-content-label">
                        <span id="mail-html-content-label" className="mb-1 block text-sm font-semibold text-slate-700 dark:text-slate-200">HTML 내용</span>
                        <MailRichTextEditor initialContent={campaignForm.htmlContent} onChange={(htmlContent) => setCampaignForm((current) => ({ ...current, htmlContent }))} />
                        <p className="mt-1 text-[11px] text-slate-500 dark:text-slate-400">저장 시 위험한 HTML과 임의 인라인 CSS는 서버에서 제거됩니다. 광고성 메일은 {'{{unsubscribeUrl}}'} 변수가 필요합니다.</p>
                    </div>

                    <div className="rounded-xl border border-slate-200 p-4 dark:border-slate-800"><h4 className="mb-2 flex items-center gap-2 font-semibold"><Paperclip className="h-4 w-4" />첨부파일</h4>{existingAttachments.map((attachment) => <div key={attachment.seq} className="mb-1 flex items-center justify-between gap-2 text-xs"><span className="truncate">{attachment.originalFilename} ({formatBytes(attachment.fileSize)})</span><button type="button" title="첨부파일 삭제" onClick={() => void deleteExistingAttachment(attachment)} className="rounded p-1 text-rose-500 hover:bg-rose-50 dark:hover:bg-rose-950"><X className="h-3.5 w-3.5" /></button></div>)}<label className="mt-2 flex cursor-pointer items-center justify-center gap-2 rounded-lg border border-dashed border-slate-300 p-3 text-xs text-slate-500 hover:border-blue-500 dark:border-slate-700"><FilePlus2 className="h-4 w-4" />파일 선택<input type="file" multiple className="hidden" onChange={(event) => { const files = Array.from(event.target.files ?? []); const total = existingAttachments.reduce((sum, file) => sum + file.fileSize, 0) + files.reduce((sum, file) => sum + file.size, 0); if (total > 10 * 1024 * 1024) notifyRef.current('error', '첨부파일 합계가 10MB를 초과합니다.'); else if (existingAttachments.length + files.length > 5) notifyRef.current('error', '첨부파일은 최대 5개까지 등록할 수 있습니다.'); else setCampaignFiles(files); }} /></label>{campaignFiles.map((file) => <p key={`${file.name}-${file.size}`} className="mt-1 truncate text-xs text-slate-500">{file.name} ({formatBytes(file.size)})</p>)}</div>

                    <CampaignRecipientListBox
                        addressBooks={addressBooks}
                        addressBookSeqs={campaignForm.addressBookSeqs}
                        directRecipients={campaignForm.directRecipients}
                        addressBookToAdd={addressBookToAdd}
                        directRecipientForm={directRecipientForm}
                        selectedKeys={selectedRecipientKeys}
                        recipientGroups={campaignForm.recipientGroups}
                        onGroupChange={(group) => {
                            setCampaignForm((current) => ({
                                ...current,
                                recipientGroups: current.recipientGroups.includes(group)
                                    ? current.recipientGroups.filter((value) => value !== group)
                                    : [...current.recipientGroups, group]
                            }));
                            setSelectedRecipientKeys((current) => current.filter((key) => key !== groupRecipientKey(group)));
                        }}
                        onNotify={onNotify}
                        allSelected={allRecipientSourcesSelected}
                        onAddressBookToAddChange={setAddressBookToAdd}
                        onDirectRecipientFormChange={setDirectRecipientForm}
                        onAddAddressBook={addAddressBookSource}
                        onAddDirectRecipient={addDirectRecipient}
                        onAddInternalRecipients={addInternalRecipients}
                        onToggle={toggleRecipientSource}
                        onToggleAll={toggleAllRecipientSources}
                        onRemove={removeRecipientSources}
                    />
                </div>
                <div className="sticky bottom-0 flex justify-end gap-2 border-t border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-950"><button onClick={() => setIsCampaignModalOpen(false)} className={secondaryButtonClass}>취소</button><button disabled={isSaving} onClick={() => void saveCampaign()} className={primaryButtonClass}>{isSaving ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}저장</button></div>
            </DraggableModal></div>}
        </div>
    );
};

interface CampaignRecipientListBoxProps {
    addressBooks: MailAddressBook[];
    recipientGroups: RecipientGroup[];
    addressBookSeqs: number[];
    directRecipients: DirectRecipient[];
    addressBookToAdd: string;
    directRecipientForm: { fullName: string; email: string };
    selectedKeys: string[];
    onGroupChange: (group: RecipientGroup) => void;
    onNotify: (type: NotificationType, message: string) => void;
    allSelected: boolean;
    onAddressBookToAddChange: (value: string) => void;
    onDirectRecipientFormChange: (value: { fullName: string; email: string }) => void;
    onAddAddressBook: () => void;
    onAddDirectRecipient: () => void;
    onAddInternalRecipients: (recipients: InternalRecipientSearchResult[]) => void;
    onToggle: (key: string) => void;
    onToggleAll: () => void;
    onRemove: (keys: string[]) => void;
}

const CampaignRecipientListBox = ({
    addressBooks,
    addressBookSeqs,
    directRecipients,
    addressBookToAdd,
    directRecipientForm,
    selectedKeys,
    recipientGroups,
    onGroupChange,
    onNotify,
    allSelected,
    onAddressBookToAddChange,
    onDirectRecipientFormChange,
    onAddAddressBook,
    onAddDirectRecipient,
    onAddInternalRecipients,
    onToggle,
    onToggleAll,
    onRemove
}: CampaignRecipientListBoxProps) => {
    const selectedAddressBooks = addressBookSeqs.map((seq) => ({
        seq,
        book: addressBooks.find((addressBook) => addressBook.seq === seq)
    }));
    const sourceCount = recipientGroups.length + addressBookSeqs.length + directRecipients.length;
    const [previewResult, setPreviewResult] = useState<{ data?: RecipientPreview; failed?: boolean } | null>(null);
    const notifyRef = useRef(onNotify);
    useEffect(() => { notifyRef.current = onNotify; }, [onNotify]);
    const preview = previewResult?.data;
    const isPreviewLoading = previewResult === null;
    const selectedRecipientCount = preview
        ? recipientGroups.reduce((total, group) => total + preview.groupCounts[group], 0)
            + selectedAddressBooks.reduce((total, { book }) => total + (book?.contactCount ?? 0), 0)
            + directRecipients.length
        : null;
    useEffect(() => {
        const controller = new AbortController();
        const timeout = window.setTimeout(() => {
            const load = async () => {
                try {
                    const response = await fetch('/api/admin/mail/campaigns/recipient-preview', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ recipientGroups: [], addressBookSeqs: [], directRecipients: [] }),
                        signal: controller.signal
                    });
                    if (!response.ok) throw new Error(await response.text() || '수신 대상 인원을 조회하지 못했습니다.');
                    const data = await response.json() as RecipientPreview;
                    if (!controller.signal.aborted) setPreviewResult({ data });
                } catch (error) {
                    if (controller.signal.aborted) return;
                    setPreviewResult({ failed: true });
                    notifyRef.current('error', error instanceof Error ? error.message : '수신 대상 인원을 조회하지 못했습니다.');
                }
            };
            void load();
        }, 0);
        return () => { window.clearTimeout(timeout); controller.abort(); };
    }, []);
    const groupCountLabel = (group: RecipientGroup) => preview
        ? `${preview.groupCounts[group].toLocaleString()}명`
        : isPreviewLoading ? '집계 중...' : '-';

    return (
        <section className="overflow-hidden rounded-xl border border-slate-200 dark:border-slate-800">
            <div className="flex flex-col gap-3 border-b border-slate-200 bg-slate-50 px-4 py-3 dark:border-slate-800 dark:bg-slate-900 sm:flex-row sm:items-center sm:justify-between">
                <div>
                    <h4 className="flex items-center gap-2 font-semibold"><Users className="h-4 w-4 text-blue-600 dark:text-blue-400" />수신자</h4>
                    <p className="mt-0.5 text-[11px] text-slate-500">전체 대상 그룹, 주소록, 내부회원 또는 개별 수신자를 함께 추가할 수 있습니다.</p>
                </div>
                <div className="flex items-center gap-2 text-xs">
                    <span className={blueBadge}>소스 {sourceCount.toLocaleString()}개</span>
                    <span className={greenBadge}>선택 합계 {selectedRecipientCount !== null ? `${selectedRecipientCount.toLocaleString()}명` : isPreviewLoading ? '집계 중...' : '-'}</span>
                </div>
            </div>

            <div className="border-b border-slate-200 p-4 dark:border-slate-800">
                <RecipientGroupSelector selected={recipientGroups} counts={preview?.groupCounts} loading={isPreviewLoading} failed={previewResult?.failed} onToggle={onGroupChange} />
            </div>

            <div className="grid gap-3 border-b border-slate-200 p-4 dark:border-slate-800 lg:grid-cols-2">
                <div>
                    <span className="mb-1 block text-xs font-semibold text-slate-600 dark:text-slate-300">주소록 추가</span>
                    <div className="flex gap-2">
                        <select value={addressBookToAdd} onChange={(event) => onAddressBookToAddChange(event.target.value)} className={inputClass()}>
                            <option value="">주소록을 선택해 주세요</option>
                            {addressBooks.map((book) => (
                                <option key={book.seq} value={book.seq} disabled={addressBookSeqs.includes(book.seq)}>
                                    {book.addressBookName} · {book.contactCount.toLocaleString()}명{addressBookSeqs.includes(book.seq) ? ' (추가됨)' : ''}
                                </option>
                            ))}
                        </select>
                        <button type="button" onClick={onAddAddressBook} className={`${secondaryButtonClass} shrink-0`}><BookUser className="h-4 w-4" />추가</button>
                    </div>
                </div>

                <div>
                    <span className="mb-1 block text-xs font-semibold text-slate-600 dark:text-slate-300">개별 수신자 추가</span>
                    <div className="grid gap-2 sm:grid-cols-[0.7fr_1fr_auto]">
                        <input
                            value={directRecipientForm.fullName}
                            onChange={(event) => onDirectRecipientFormChange({ ...directRecipientForm, fullName: event.target.value })}
                            className={inputClass()}
                            placeholder="이름"
                            maxLength={255}
                        />
                        <input
                            type="email"
                            value={directRecipientForm.email}
                            onChange={(event) => onDirectRecipientFormChange({ ...directRecipientForm, email: event.target.value })}
                            onKeyDown={(event) => { if (event.key === 'Enter') { event.preventDefault(); onAddDirectRecipient(); } }}
                            className={inputClass()}
                            placeholder="이메일 주소"
                            maxLength={255}
                        />
                        <button type="button" onClick={onAddDirectRecipient} className={`${primaryButtonClass} justify-center`}><UserPlus className="h-4 w-4" />추가</button>
                    </div>
                </div>

                <InternalRecipientSearchPanel
                    directRecipients={directRecipients}
                    onAdd={onAddInternalRecipients}
                />
            </div>

            <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-200 bg-white px-3 py-2 dark:border-slate-800 dark:bg-slate-950">
                <span className="text-xs text-slate-500">선택 {selectedKeys.length.toLocaleString()}개</span>
                <div className="flex gap-2">
                    <button type="button" disabled={sourceCount === 0} onClick={onToggleAll} className={secondaryButtonClass}>
                        <CheckCircle2 className="h-3.5 w-3.5" />{allSelected ? '전체 해제' : '전체 선택'}
                    </button>
                    <button type="button" disabled={selectedKeys.length === 0} onClick={() => onRemove(selectedKeys)} className="inline-flex items-center gap-1.5 rounded-lg border border-rose-200 px-3 py-2 text-xs font-semibold text-rose-600 hover:bg-rose-50 disabled:opacity-40 dark:border-rose-900 dark:text-rose-300 dark:hover:bg-rose-950/40">
                        <Trash2 className="h-3.5 w-3.5" />선택 삭제
                    </button>
                </div>
            </div>

            <div role="listbox" aria-label="캠페인 수신자 목록" aria-multiselectable="true" className="max-h-60 min-h-36 overflow-y-auto bg-white dark:bg-slate-950">
                {RECIPIENT_GROUPS.filter((group) => recipientGroups.includes(group.value)).map((group) => {
                    const key = groupRecipientKey(group.value);
                    const selected = selectedKeys.includes(key);
                    return (
                        <div key={key} role="option" aria-selected={selected} className="flex items-center gap-3 border-b border-slate-100 px-4 py-3 text-slate-700 last:border-b-0 dark:border-slate-900 dark:text-slate-200">
                            <input type="checkbox" checked={selected} onChange={() => onToggle(key)} aria-label={`${group.label} 선택`} />
                            <Users className="h-4 w-4 shrink-0 text-blue-500 dark:text-blue-400" />
                            <div className="min-w-0 flex-1"><p className="text-sm font-semibold">{group.label}</p><p className="text-xs text-slate-500 dark:text-slate-400">전체 대상 그룹</p></div>
                            <span className="text-sm font-semibold">{groupCountLabel(group.value)}</span>
                            <button type="button" aria-label={`${group.label} 제거`} onClick={() => onRemove([key])} className="rounded-lg p-1.5 text-slate-400 hover:bg-rose-50 hover:text-rose-500 dark:text-slate-400 dark:hover:bg-rose-950/40 dark:hover:text-rose-400"><X className="h-4 w-4" /></button>
                        </div>
                    );
                })}
                {selectedAddressBooks.map(({ seq, book }) => {
                    const key = addressBookRecipientKey(seq);
                    const selected = selectedKeys.includes(key);
                    return (
                        <div key={key} role="option" aria-selected={selected} className={`flex items-center gap-3 border-b border-slate-100 px-4 py-3 last:border-b-0 dark:border-slate-900 ${selected ? 'bg-blue-50 dark:bg-blue-950/30' : 'hover:bg-slate-50 dark:hover:bg-slate-900/60'}`}>
                            <input type="checkbox" checked={selected} onChange={() => onToggle(key)} aria-label={`${book?.addressBookName ?? `주소록 ${seq}`} 선택`} />
                            <BookUser className="h-4 w-4 shrink-0 text-blue-500" />
                            <div className="min-w-0 flex-1">
                                <p className="truncate text-sm font-semibold">{book?.addressBookName ?? `삭제된 주소록 #${seq}`}</p>
                                <p className="text-[11px] text-slate-500">주소록</p>
                            </div>
                            <span className="shrink-0 text-sm font-semibold text-slate-700 dark:text-slate-200">{(book?.contactCount ?? 0).toLocaleString()}명</span>
                            <button type="button" title="주소록 제거" onClick={() => onRemove([key])} className="rounded p-1.5 text-slate-400 hover:bg-rose-50 hover:text-rose-500 dark:hover:bg-rose-950/40"><X className="h-4 w-4" /></button>
                        </div>
                    );
                })}

                {directRecipients.map((recipient) => {
                    const key = directRecipientSourceKey(recipient.email);
                    const selected = selectedKeys.includes(key);
                    const isMember = recipient.sourceType === 'INTERNAL_MEMBER';
                    const isAdmin = recipient.sourceType === 'INTERNAL_ADMIN';
                    const isAddressBookContact = recipient.sourceType === 'ADDRESS_BOOK_CONTACT';
                    const sourceLabel = isMember ? '회원' : isAdmin ? '관리자' : isAddressBookContact ? '주소록 개별' : '개별 수신자';
                    return (
                        <div key={key} role="option" aria-selected={selected} className={`flex items-center gap-3 border-b border-slate-100 px-4 py-3 last:border-b-0 dark:border-slate-900 ${selected ? 'bg-blue-50 dark:bg-blue-950/30' : 'hover:bg-slate-50 dark:hover:bg-slate-900/60'}`}>
                            <input type="checkbox" checked={selected} onChange={() => onToggle(key)} aria-label={`${recipient.fullName || recipient.email} 선택`} />
                            {isAdmin
                                ? <ShieldCheck className="h-4 w-4 shrink-0 text-violet-500" />
                                : isAddressBookContact
                                    ? <BookUser className="h-4 w-4 shrink-0 text-teal-500" />
                                : isMember
                                    ? <Building2 className="h-4 w-4 shrink-0 text-sky-500" />
                                    : <UserPlus className="h-4 w-4 shrink-0 text-emerald-500" />}
                            <div className="min-w-0 flex-1 sm:grid sm:grid-cols-[0.6fr_1fr] sm:items-center sm:gap-3">
                                <div className="min-w-0">
                                    <p className="truncate text-sm font-semibold">{recipient.fullName || '이름 없음'}</p>
                                    <p className="text-[11px] text-slate-500">{sourceLabel}{recipient.affiliation ? ` · ${recipient.affiliation}` : ''}</p>
                                </div>
                                <p className="truncate text-xs text-slate-500">{recipient.email}</p>
                            </div>
                            <button type="button" title="개별 수신자 제거" onClick={() => onRemove([key])} className="rounded p-1.5 text-slate-400 hover:bg-rose-50 hover:text-rose-500 dark:hover:bg-rose-950/40"><X className="h-4 w-4" /></button>
                        </div>
                    );
                })}

                {sourceCount === 0 && (
                    <div className="flex min-h-36 flex-col items-center justify-center px-4 text-center text-slate-400">
                        <Users className="mb-2 h-6 w-6" />
                        <p className="text-sm">추가된 수신자가 없습니다.</p>
                        <p className="mt-1 text-[11px]">전체 대상 그룹이나 주소록을 선택하고, 필요하면 개별 수신자를 추가해 주세요.</p>
                    </div>
                )}
            </div>

            <div className="border-t border-slate-200 bg-slate-50 px-4 py-2 text-[11px] text-slate-500 dark:border-slate-800 dark:bg-slate-900">
                그룹별 인원은 이메일 중복·수신 거부·이메일 오류를 제외한 값입니다. 선택 합계는 그룹·주소록·개별 수신자 인원을 더한 값으로 대상 간 중복이 포함될 수 있습니다. 최종 인원과 명단은 발송 준비 시 중복과 제외 대상을 확인하여 확정하며, 예약 메일도 확정된 명단을 사용합니다.
            </div>
        </section>
    );
};

const InternalRecipientSearchPanel = ({
    directRecipients,
    onAdd
}: {
    directRecipients: DirectRecipient[];
    onAdd: (recipients: InternalRecipientSearchResult[]) => void;
}) => {
    const [keyword, setKeyword] = useState('');
    const [category, setCategory] = useState<'ALL' | 'MEMBER' | 'ADMIN' | 'ADDRESS_BOOK'>('ALL');
    const [results, setResults] = useState<InternalRecipientSearchResult[]>([]);
    const [selectedKeys, setSelectedKeys] = useState<string[]>([]);
    const [isSearching, setIsSearching] = useState(false);
    const [hasSearched, setHasSearched] = useState(false);
    const [searchError, setSearchError] = useState('');

    const searchRecipients = async () => {
        const normalizedKeyword = keyword.trim();
        if (normalizedKeyword.length < 2) return;
        setIsSearching(true);
        setHasSearched(true);
        setSearchError('');
        try {
            const params = new URLSearchParams({ keyword: normalizedKeyword, category, limit: '30' });
            const response = await fetch(`/api/admin/mail/internal-recipients?${params}`);
            if (!response.ok) throw new Error(await response.text() || '내부회원 검색에 실패했습니다.');
            setResults(await response.json() as InternalRecipientSearchResult[]);
            setSelectedKeys([]);
        } catch (error) {
            setResults([]);
            setSelectedKeys([]);
            setSearchError(error instanceof Error ? error.message : '내부회원 검색에 실패했습니다.');
        } finally {
            setIsSearching(false);
        }
    };

    const resultKey = (recipient: InternalRecipientSearchResult) => `${recipient.recipientType}:${recipient.referenceSeq}`;
    const addedEmails = new Set(directRecipients.map((recipient) => recipient.email.trim().toLowerCase()));
    const selectedRecipients = results.filter((recipient) => selectedKeys.includes(resultKey(recipient)));

    return (
        <div className="rounded-xl border border-blue-100 bg-blue-50/60 p-3 dark:border-blue-900/60 dark:bg-blue-950/20 lg:col-span-2">
            <div className="mb-2 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                <div>
                    <span className="flex items-center gap-2 text-xs font-semibold text-slate-700 dark:text-slate-200"><Users className="h-4 w-4 text-blue-500" />회원·주소록 검색</span>
                    <p className="mt-0.5 text-[11px] text-slate-500">회원, 활성 관리자, 주소록 연락처에서 이름·소속·이메일로 검색해 개별 추가합니다.</p>
                </div>
                <div className="inline-flex w-fit rounded-lg border border-slate-200 bg-white p-0.5 dark:border-slate-700 dark:bg-slate-950">
                    {(['ALL', 'MEMBER', 'ADMIN', 'ADDRESS_BOOK'] as const).map((value) => (
                        <button key={value} type="button" onClick={() => setCategory(value)} className={`rounded-md px-2.5 py-1.5 text-[11px] font-semibold ${category === value ? 'bg-blue-600 text-white' : 'text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-800'}`}>
                            {value === 'ALL' ? '전체' : value === 'MEMBER' ? '회원' : value === 'ADMIN' ? '관리자' : '주소록'}
                        </button>
                    ))}
                </div>
            </div>
            <div className="flex gap-2">
                <div className="relative flex-1">
                    <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                    <input value={keyword} onChange={(event) => setKeyword(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter') { event.preventDefault(); void searchRecipients(); } }} className={inputClass('pl-9')} placeholder="이름, 이메일, 소속을 2자 이상 입력" maxLength={100} />
                </div>
                <button type="button" disabled={keyword.trim().length < 2 || isSearching} onClick={() => void searchRecipients()} className={`${secondaryButtonClass} shrink-0`}>
                    {isSearching ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Search className="h-4 w-4" />}검색
                </button>
            </div>

            {searchError && <p className="mt-2 rounded-lg bg-rose-50 px-3 py-2 text-xs text-rose-600 dark:bg-rose-950/30 dark:text-rose-300">{searchError}</p>}

            {hasSearched && (
                <div className="mt-3 overflow-hidden rounded-lg border border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-950">
                    <div className="flex items-center justify-between border-b border-slate-200 px-3 py-2 text-[11px] text-slate-500 dark:border-slate-800">
                        <span>검색 결과 {results.length.toLocaleString()}명{results.length === 30 ? ' · 최대 30명 표시' : ''}</span>
                        <div className="flex items-center gap-2">
                            <button type="button" disabled={selectedRecipients.length === 0} onClick={() => { onAdd(selectedRecipients); setSelectedKeys([]); }} className={primaryButtonClass}>
                                <UserPlus className="h-3.5 w-3.5" />선택 {selectedRecipients.length.toLocaleString()}명 추가
                            </button>
                            <button type="button" onClick={() => { setHasSearched(false); setResults([]); setSelectedKeys([]); setSearchError(''); }} className={secondaryButtonClass}>
                                <X className="h-3.5 w-3.5" />닫기
                            </button>
                        </div>
                    </div>
                    {results.length > 0 && (
                        <div className="hidden grid-cols-[20px_20px_minmax(110px,0.7fr)_minmax(120px,0.8fr)_minmax(180px,1fr)_auto] items-center gap-3 border-b border-slate-200 bg-slate-50 px-3 py-2 text-[11px] font-semibold text-slate-500 dark:border-slate-800 dark:bg-slate-900 sm:grid">
                            <span />
                            <span />
                            <span>이름</span>
                            <span>소속</span>
                            <span>이메일</span>
                            <span>구분</span>
                        </div>
                    )}
                    <div className="max-h-52 overflow-y-auto">
                        {results.map((recipient) => {
                            const key = resultKey(recipient);
                            const isAdded = addedEmails.has(recipient.email.trim().toLowerCase());
                            const isSelected = selectedKeys.includes(key);
                            return (
                                <label key={key} className={`grid cursor-pointer grid-cols-[20px_20px_minmax(0,1fr)] items-center gap-3 border-b border-slate-100 px-3 py-2.5 last:border-b-0 dark:border-slate-900 sm:grid-cols-[20px_20px_minmax(110px,0.7fr)_minmax(120px,0.8fr)_minmax(180px,1fr)_auto] ${isAdded ? 'cursor-default bg-slate-50 opacity-60 dark:bg-slate-900/50' : isSelected ? 'bg-blue-50 dark:bg-blue-950/30' : 'hover:bg-slate-50 dark:hover:bg-slate-900/60'}`}>
                                    <input type="checkbox" disabled={isAdded} checked={isSelected || isAdded} onChange={() => setSelectedKeys((current) => current.includes(key) ? current.filter((item) => item !== key) : [...current, key])} />
                                    {recipient.recipientType === 'ADMIN'
                                        ? <ShieldCheck className="h-4 w-4 shrink-0 text-violet-500" />
                                        : recipient.recipientType === 'ADDRESS_BOOK'
                                            ? <BookUser className="h-4 w-4 shrink-0 text-teal-500" />
                                            : <Building2 className="h-4 w-4 shrink-0 text-sky-500" />}
                                    <div className="min-w-0 sm:contents">
                                        <p className="truncate text-sm font-semibold">{recipient.fullName || '이름 없음'}</p>
                                        <p className="truncate text-xs text-slate-500"><span className="mr-1 text-[10px] text-slate-400 sm:hidden">소속</span>{recipient.affiliation || '-'}</p>
                                        <p className="truncate text-xs text-slate-500"><span className="mr-1 text-[10px] text-slate-400 sm:hidden">이메일</span>{recipient.email}</p>
                                        <span className={`mt-1 w-fit max-w-32 shrink-0 truncate rounded-full px-2 py-1 text-[10px] font-semibold sm:mt-0 ${recipient.recipientType === 'ADMIN' ? 'bg-violet-100 text-violet-700 dark:bg-violet-950 dark:text-violet-300' : recipient.recipientType === 'ADDRESS_BOOK' ? 'bg-teal-100 text-teal-700 dark:bg-teal-950 dark:text-teal-300' : 'bg-sky-100 text-sky-700 dark:bg-sky-950 dark:text-sky-300'}`}>{isAdded ? '추가됨' : recipient.detail}</span>
                                    </div>
                                </label>
                            );
                        })}
                        {!isSearching && results.length === 0 && <div className="px-4 py-8 text-center text-xs text-slate-400">일치하는 회원, 관리자 또는 주소록 연락처가 없습니다.</div>}
                    </div>
                </div>
            )}
        </div>
    );
};

const TabButton = ({ active, onClick, icon, label }: { active: boolean; onClick: () => void; icon: React.ReactNode; label: string }) => <button onClick={onClick} className={`flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium ${active ? 'bg-white text-blue-600 shadow-sm dark:bg-slate-800 dark:text-blue-400' : 'text-slate-500'}`}>{icon}{label}</button>;
const Field = ({ label, children }: { label: string; children: React.ReactNode }) => <label className="block"><span className="mb-1 block text-xs font-semibold text-slate-600 dark:text-slate-300">{label}</span>{children}</label>;
const IconButton = ({ title, onClick, children }: { title: string; onClick: () => void; children: React.ReactNode }) => <button title={title} onClick={onClick} className="rounded-md p-2 text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-800">{children}</button>;
const inputClass = (extra = '') => `w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 outline-none focus:border-blue-500 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-50 ${extra}`;
const primaryButtonClass = 'inline-flex items-center gap-2 rounded-lg bg-blue-600 px-3 py-2 text-xs font-semibold text-white hover:bg-blue-700 disabled:opacity-50';
const secondaryButtonClass = 'inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-300 dark:hover:bg-slate-900';
const pageButtonClass = 'rounded-lg border border-slate-200 p-2 disabled:opacity-30 dark:border-slate-700';
const blueBadge = 'rounded-full bg-blue-100 px-2 py-1 text-[11px] font-medium text-blue-700 dark:bg-blue-950 dark:text-blue-300';
const amberBadge = 'rounded-full bg-amber-100 px-2 py-1 text-[11px] font-medium text-amber-700 dark:bg-amber-950 dark:text-amber-300';
const greenBadge = 'rounded-full bg-emerald-100 px-2 py-1 text-[11px] font-medium text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300';

const formatDate = (value?: string | null) => value ? new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '-';
const formatBytes = (bytes: number) => bytes < 1024 * 1024 ? `${Math.ceil(bytes / 1024)}KB` : `${(bytes / 1024 / 1024).toFixed(1)}MB`;
