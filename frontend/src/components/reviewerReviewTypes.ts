import type { AbstractSubmissionDetail } from './abstractTypes';

export type AssignmentStatus = 'assigned' | 'accepted' | 'in_review' | 'completed';
export type ReviewStatus = 'draft' | 'submitted';
export type ReviewRecommendation = 'accept' | 'reject';

export interface ReviewerReviewListItem {
    assignmentSeq: number;
    abstractSeq: number;
    submissionNo: string;
    title: string;
    categoryName: string;
    presentationTypeName: string;
    assignmentStatus: AssignmentStatus;
    dueAt?: string | null;
    assignedAt: string;
    reviewSeq?: number | null;
    reviewStatus?: ReviewStatus | null;
    recommendation?: ReviewRecommendation | null;
    reviewUpdatedAt?: string | null;
}

export interface ReviewerReviewEvaluationItem {
    evaluationItemSeq: number;
    itemName: string;
    description?: string | null;
    sortOrder: number;
    score1Guide?: string | null;
    score2Guide?: string | null;
    score3Guide?: string | null;
    score4Guide?: string | null;
    score5Guide?: string | null;
    score6Guide?: string | null;
    score?: number | null;
    itemComment?: string | null;
}

export interface ReviewerReviewDetail {
    assignmentSeq: number;
    assignmentStatus: AssignmentStatus;
    dueAt?: string | null;
    showAuthorInformation: boolean;
    abstractSubmission: AbstractSubmissionDetail;
    reviewSeq?: number | null;
    reviewStatus: ReviewStatus;
    recommendation?: ReviewRecommendation | null;
    overallComment?: string | null;
    confidentialComment?: string | null;
    submittedAt?: string | null;
    evaluationItems: ReviewerReviewEvaluationItem[];
}
