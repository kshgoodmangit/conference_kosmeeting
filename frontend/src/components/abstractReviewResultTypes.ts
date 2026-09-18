export type ReviewRecommendation = 'accept' | 'reject';
export type ReviewAssignmentStatus = 'assigned' | 'accepted' | 'in_review' | 'completed';

export interface AbstractReviewResultScore {
    reviewSeq: number;
    evaluationItemSeq: number;
    itemName: string;
    sortOrder: number;
    score: number;
    itemComment?: string | null;
}

export interface AbstractReviewResultReviewer {
    assignmentSeq: number;
    reviewerSeq: number;
    reviewerName: string;
    affiliation?: string | null;
    department?: string | null;
    assignmentStatus: ReviewAssignmentStatus;
    dueAt?: string | null;
    reviewSeq?: number | null;
    reviewStatus?: 'draft' | 'submitted' | null;
    recommendation?: ReviewRecommendation | null;
    overallComment?: string | null;
    confidentialComment?: string | null;
    submittedAt?: string | null;
    averageScore?: number | null;
    scores: AbstractReviewResultScore[];
}

export interface AbstractReviewEvaluationSummary {
    evaluationItemSeq: number;
    itemName: string;
    sortOrder: number;
    reviewerCount: number;
    averageScore: number;
}

export interface AbstractReviewResultData {
    abstractSeq: number;
    submissionNo?: string | null;
    title: string;
    assignedCount: number;
    completedCount: number;
    averageScore?: number | null;
    recommendationCounts: Record<ReviewRecommendation, number>;
    evaluationSummaries: AbstractReviewEvaluationSummary[];
    reviews: AbstractReviewResultReviewer[];
}
