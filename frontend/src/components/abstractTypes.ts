export interface AbstractPresentationType {
    code: number;
    name: string;
    sortOrder?: number;
    enabled?: boolean;
}

export interface AbstractCategory {
    code: number;
    name: string;
    sortOrder?: number;
    enabled?: boolean;
}

export interface AbstractAiOption {
    code: number;
    name: string;
    sortOrder?: number;
    isEtc: 'Y' | 'N';
}

export interface AbstractSubmissionAiTool {
    seq?: number | null;
    abstractSeq?: number | null;
    aiToolCode: number;
    aiToolName?: string | null;
    isEtc?: 'Y' | 'N' | null;
    otherToolName?: string | null;
    otherProviderName?: string | null;
}

export interface AbstractSubmissionAiScope {
    seq?: number | null;
    abstractSeq?: number | null;
    aiScopeCode: number;
    aiScopeName?: string | null;
    isEtc?: 'Y' | 'N' | null;
    otherScopeText?: string | null;
}

export interface AbstractSubmissionAttachment {
    seq: number;
    abstractSeq: number;
    originalFilename: string;
    contentType: string;
    fileExtension: string;
    fileSize: number;
    createdAt?: string | null;
    updatedAt?: string | null;
}

export interface AbstractSubmissionInstitution {
    seq?: number | null;
    abstractSeq?: number | null;
    institutionNo: number;
    country: string;
    institutionName: string;
    department?: string | null;
}

export interface AbstractSubmissionAuthor {
    seq?: number | null;
    abstractSeq?: number | null;
    authorOrder: number;
    authorName: string;
    institutionNo: number;
    isPresentingAuthor: boolean;
    isCorrespondingAuthor: boolean;
    email?: string | null;
    country?: string | null;
    officeCountryCode?: string | null;
    officePhoneNumber?: string | null;
    mobileCountryCode?: string | null;
    mobilePhoneNumber?: string | null;
}

export interface AbstractSubmissionDetail {
    seq: number;
    memberSeq: number;
    submissionSource: 'member' | 'admin';
    createdByAdminSeq?: number | null;
    createdByAdminName?: string | null;
    memberEmail?: string | null;
    memberFullName?: string | null;
    submissionNo?: string | null;
    presentationTypeCode: number;
    presentationTypeName?: string | null;
    acceptedPresentationTypeCode?: number | null;
    acceptedPresentationTypeName?: string | null;
    categoryCode: number;
    categoryName?: string | null;
    title: string;
    objectiveText?: string | null;
    methodsText?: string | null;
    resultsText?: string | null;
    conclusionsText?: string | null;
    aiUsage: boolean;
    aiVersionInfo?: string | null;
    aiDataAnalysisUsed: boolean;
    plagiarismPolicyConfirmed: boolean;
    wordCount?: number | null;
    status: 'draft' | 'submitted' | 'under_review' | 'approved' | 'rejected';
    decisionByAdminSeq?: number | null;
    decisionByAdminName?: string | null;
    decisionAt?: string | null;
    decisionReason?: string | null;
    forcedDecision?: boolean | null;
    authorCount?: number | null;
    institutionCount?: number | null;
    reviewerAssignmentCount?: number | null;
    completedReviewCount?: number | null;
    averageReviewScore?: number | null;
    titleSimilarityMaxScore?: number | null;
    titleSimilarityMatchCount?: number | null;
    titleSimilarityAlgorithmVersion?: string | null;
    titleSimilarityCheckedAt?: string | null;
    mainAuthorName?: string | null;
    submittedAt?: string | null;
    reviewedAt?: string | null;
    createdAt?: string | null;
    updatedAt?: string | null;
    institutions: AbstractSubmissionInstitution[];
    authors: AbstractSubmissionAuthor[];
    aiTools: AbstractSubmissionAiTool[];
    aiScopes: AbstractSubmissionAiScope[];
    attachments?: AbstractSubmissionAttachment[];
}

export interface AbstractSubmissionListItem {
    seq: number;
    memberSeq: number;
    submissionSource: 'member' | 'admin';
    createdByAdminSeq?: number | null;
    createdByAdminName?: string | null;
    memberEmail?: string | null;
    memberFullName?: string | null;
    submissionNo?: string | null;
    presentationTypeCode: number;
    presentationTypeName?: string | null;
    acceptedPresentationTypeCode?: number | null;
    acceptedPresentationTypeName?: string | null;
    categoryCode: number;
    categoryName?: string | null;
    title: string;
    aiUsage: boolean;
    aiDataAnalysisUsed: boolean;
    status: 'draft' | 'submitted' | 'under_review' | 'approved' | 'rejected';
    decisionByAdminSeq?: number | null;
    decisionByAdminName?: string | null;
    decisionAt?: string | null;
    decisionReason?: string | null;
    forcedDecision?: boolean | null;
    authorCount?: number | null;
    institutionCount?: number | null;
    reviewerAssignmentCount?: number | null;
    completedReviewCount?: number | null;
    averageReviewScore?: number | null;
    titleSimilarityMaxScore?: number | null;
    titleSimilarityMatchCount?: number | null;
    titleSimilarityAlgorithmVersion?: string | null;
    titleSimilarityCheckedAt?: string | null;
    mainAuthorName?: string | null;
    submittedAt?: string | null;
    reviewedAt?: string | null;
    createdAt?: string | null;
    updatedAt?: string | null;
}

export interface AbstractSubmissionPageResponse {
    summary?: {
        unassignedCount: number;
        pendingReviewCount: number;
        pendingDecisionCount: number;
        acceptedCount: number;
    };
    items: AbstractSubmissionListItem[];
    page: number;
    size: number;
    totalCount: number;
    totalPages: number;
}

export interface AbstractSubmissionMetaResponse {
    presentationTypes: AbstractPresentationType[];
    categories: AbstractCategory[];
    aiTools: AbstractAiOption[];
    aiScopes: AbstractAiOption[];
}

export interface AbstractSimilarityMatch {
    abstractSeq: number;
    submissionNo?: string | null;
    title: string;
    overallSimilarity: number;
    titleSimilarity?: number | null;
    objectiveSimilarity?: number | null;
    methodsSimilarity?: number | null;
    resultsSimilarity?: number | null;
    conclusionsSimilarity?: number | null;
    highestSection: 'title' | 'objective' | 'methods' | 'results' | 'conclusions';
    highestSimilarity: number;
}

export interface AbstractSimilarityResponse {
    abstractSeq: number;
    model: string;
    dimension: number;
    comparedCount: number;
    analyzedAt?: string | null;
    stale: boolean;
    matches: AbstractSimilarityMatch[];
}

export interface AbstractTitleSimilarityMatch {
    abstractSeq: number;
    submissionNo?: string | null;
    title: string;
    similarityScore: number;
    levenshteinSimilarity: number;
    trigramSimilarity: number;
    jaccardSimilarity: number;
    exactMatch: boolean;
}

export interface AbstractTitleSimilarityResponse {
    abstractSeq: number;
    warningThreshold: number;
    maxSimilarity?: number | null;
    matchCount: number;
    algorithmVersion?: string | null;
    checkedAt?: string | null;
    matches: AbstractTitleSimilarityMatch[];
}

export interface AbstractSimilarityContent {
    abstractSeq: number;
    submissionNo?: string | null;
    title: string;
    objectiveText?: string | null;
    methodsText?: string | null;
    resultsText?: string | null;
    conclusionsText?: string | null;
}

export interface AbstractSimilarityComparisonResponse {
    source: AbstractSimilarityContent;
    target: AbstractSimilarityContent;
    similarity: AbstractSimilarityMatch;
    analyzedAt?: string | null;
    stale: boolean;
}

export interface ReviewerAssignmentCandidate {
    reviewerSeq: number;
    reviewerName: string;
    affiliation: string;
    department: string;
    positionTitle?: string | null;
    contactEmail?: string | null;
    expertiseNames?: string | null;
    expertiseMatched: boolean;
    activeAssignmentCount: number;
    assignmentSeq?: number | null;
    assignmentStatus?: 'assigned' | 'accepted' | 'in_review' | 'completed' | 'declined' | 'cancelled' | null;
    dueAt?: string | null;
}

export interface AbstractReviewAssignmentData {
    abstractSeq: number;
    submissionNo?: string | null;
    title: string;
    abstractStatus: AbstractSubmissionListItem['status'];
    categoryCode: number;
    categoryName?: string | null;
    dueAt?: string | null;
    reviewers: ReviewerAssignmentCandidate[];
}

export interface EditableAbstractSubmission {
    seq?: number | null;
    memberEmail?: string | null;
    presentationTypeCode: number;
    categoryCode: number;
    title: string;
    objectiveText: string;
    methodsText: string;
    resultsText: string;
    conclusionsText: string;
    aiUsage: boolean;
    aiVersionInfo: string;
    aiDataAnalysisUsed: boolean;
    plagiarismPolicyConfirmed: boolean;
    status: 'draft' | 'submitted' | 'under_review' | 'approved' | 'rejected';
    institutions: AbstractSubmissionInstitution[];
    authors: AbstractSubmissionAuthor[];
    aiTools: AbstractSubmissionAiTool[];
    aiScopes: AbstractSubmissionAiScope[];
}
