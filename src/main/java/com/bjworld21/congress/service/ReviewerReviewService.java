package com.bjworld21.congress.service;

import com.bjworld21.congress.config.AbstractReviewProperties;
import com.bjworld21.congress.config.PersonalDataProperties;
import com.bjworld21.congress.dto.AbstractSubmissionResponse;
import com.bjworld21.congress.dto.ReviewerReviewDetailResponse;
import com.bjworld21.congress.dto.ReviewerReviewEvaluationItemResponse;
import com.bjworld21.congress.dto.ReviewerReviewListItemResponse;
import com.bjworld21.congress.dto.ReviewerReviewFilterMetaResponse;
import com.bjworld21.congress.dto.ReviewerReviewSaveRequest;
import com.bjworld21.congress.dto.ReviewerReviewScoreRequest;
import com.bjworld21.congress.entity.AbstractReview;
import com.bjworld21.congress.entity.AbstractReviewAssignment;
import com.bjworld21.congress.entity.AbstractReviewScore;
import com.bjworld21.congress.entity.ReviewerProfile;
import com.bjworld21.congress.repository.AbstractSubmissionRepository;
import com.bjworld21.congress.repository.ReviewerRepository;
import com.bjworld21.congress.repository.ReviewerReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ReviewerReviewService {
    private static final Set<String> RECOMMENDATIONS = Set.of("accept", "reject");
    private static final Set<String> ASSIGNMENT_STATUSES = Set.of(
            "assigned", "accepted", "in_review", "completed"
    );

    private final ReviewerRepository reviewerRepository;
    private final ReviewerReviewRepository reviewerReviewRepository;
    private final AbstractSubmissionRepository abstractSubmissionRepository;
    private final AbstractReviewProperties abstractReviewProperties;
    private final PersonalDataProperties personalDataProperties;

    public ReviewerReviewService(
            ReviewerRepository reviewerRepository,
            ReviewerReviewRepository reviewerReviewRepository,
            AbstractSubmissionRepository abstractSubmissionRepository,
            AbstractReviewProperties abstractReviewProperties,
            PersonalDataProperties personalDataProperties
    ) {
        this.reviewerRepository = reviewerRepository;
        this.reviewerReviewRepository = reviewerReviewRepository;
        this.abstractSubmissionRepository = abstractSubmissionRepository;
        this.abstractReviewProperties = abstractReviewProperties;
        this.personalDataProperties = personalDataProperties;
    }

    public List<ReviewerReviewListItemResponse> findMyAssignments(
            Long conferenceSeq,
            Long adminSeq,
            String keyword
    ) {
        return findMyAssignments(conferenceSeq, adminSeq, keyword, null, null, "");
    }

    public List<ReviewerReviewListItemResponse> findMyAssignments(
            Long conferenceSeq,
            Long adminSeq,
            String keyword,
            Long presentationTypeCode,
            Long categoryCode,
            String status
    ) {
        ReviewerProfile reviewer = requireReviewer(conferenceSeq, adminSeq);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String normalizedStatus = status == null ? "" : status.trim();
        if (!normalizedStatus.isEmpty() && !ASSIGNMENT_STATUSES.contains(normalizedStatus)) {
            throw new IllegalArgumentException("심사 상태가 올바르지 않습니다.");
        }
        return reviewerReviewRepository.findAssignments(
                conferenceSeq, reviewer.getSeq(), normalizedKeyword,
                presentationTypeCode, categoryCode, normalizedStatus
        );
    }

    public ReviewerReviewFilterMetaResponse getFilterMeta() {
        return ReviewerReviewFilterMetaResponse.builder()
                .presentationTypes(abstractSubmissionRepository.findEnabledPresentationTypes())
                .categories(abstractSubmissionRepository.findEnabledCategories())
                .build();
    }

    public ReviewerReviewDetailResponse getDetail(Long conferenceSeq, Long adminSeq, Long assignmentSeq) {
        ReviewerProfile reviewer = requireReviewer(conferenceSeq, adminSeq);
        AbstractReviewAssignment assignment = requireAssignment(conferenceSeq, reviewer.getSeq(), assignmentSeq);
        AbstractReview review = reviewerReviewRepository.findReviewByAssignment(conferenceSeq, assignmentSeq);

        AbstractSubmissionResponse abstractSubmission = abstractSubmissionRepository.findBySeq(
                conferenceSeq, assignment.getAbstractSeq(), personalDataProperties.requireDbEncString()
        );
        if (abstractSubmission == null) {
            throw new IllegalArgumentException("배정된 초록을 찾을 수 없습니다.");
        }
        boolean showAuthorInformation = abstractReviewProperties.isShowAuthorInformation();
        if (showAuthorInformation) {
            abstractSubmission.setInstitutions(
                    abstractSubmissionRepository.findInstitutionsByAbstractSeq(assignment.getAbstractSeq())
            );
            abstractSubmission.setAuthors(
                    abstractSubmissionRepository.findAuthorsByAbstractSeq(
                            assignment.getAbstractSeq(), personalDataProperties.requireDbEncString()
                    )
            );
        } else {
            hideAuthorInformation(abstractSubmission);
        }

        Long reviewSeq = review == null ? null : review.getSeq();
        return ReviewerReviewDetailResponse.builder()
                .assignmentSeq(assignment.getSeq())
                .assignmentStatus(assignment.getStatus())
                .dueAt(assignment.getDueAt())
                .showAuthorInformation(showAuthorInformation)
                .abstractSubmission(abstractSubmission)
                .reviewSeq(reviewSeq)
                .reviewStatus(review == null ? "draft" : review.getStatus())
                .recommendation(review == null ? null : review.getRecommendation())
                .overallComment(review == null ? null : review.getOverallComment())
                .confidentialComment(review == null ? null : review.getConfidentialComment())
                .submittedAt(review == null ? null : review.getSubmittedAt())
                .evaluationItems(reviewerReviewRepository.findEvaluationItems(conferenceSeq, reviewSeq))
                .build();
    }

    private void hideAuthorInformation(AbstractSubmissionResponse abstractSubmission) {
        abstractSubmission.setMemberSeq(null);
        abstractSubmission.setMemberEmail(null);
        abstractSubmission.setMemberFullName(null);
        abstractSubmission.setMainAuthorName(null);
        abstractSubmission.setInstitutions(List.of());
        abstractSubmission.setAuthors(List.of());
    }

    @Transactional
    public ReviewerReviewDetailResponse saveDraft(
            Long conferenceSeq,
            Long adminSeq,
            Long assignmentSeq,
            ReviewerReviewSaveRequest request
    ) {
        return save(conferenceSeq, adminSeq, assignmentSeq, request, false);
    }

    @Transactional
    public ReviewerReviewDetailResponse submit(
            Long conferenceSeq,
            Long adminSeq,
            Long assignmentSeq,
            ReviewerReviewSaveRequest request
    ) {
        return save(conferenceSeq, adminSeq, assignmentSeq, request, true);
    }

    private ReviewerReviewDetailResponse save(
            Long conferenceSeq,
            Long adminSeq,
            Long assignmentSeq,
            ReviewerReviewSaveRequest request,
            boolean submit
    ) {
        ReviewerProfile reviewer = requireReviewer(conferenceSeq, adminSeq);
        AbstractReviewAssignment assignment = requireAssignment(conferenceSeq, reviewer.getSeq(), assignmentSeq);
        if ("completed".equals(assignment.getStatus())) {
            throw new IllegalArgumentException("이미 제출 완료된 심사입니다.");
        }
        if (request == null) {
            throw new IllegalArgumentException("심사 내용을 입력하세요.");
        }

        AbstractReview existing = reviewerReviewRepository.findReviewByAssignment(conferenceSeq, assignmentSeq);
        if (existing != null && "submitted".equals(existing.getStatus())) {
            throw new IllegalArgumentException("이미 제출 완료된 심사입니다.");
        }

        String recommendation = normalizeRecommendation(request.getRecommendation(), submit);
        String overallComment = normalizeText(
                request.getOverallComment(), 10000, "종합의견은 10,000자 이하로 입력하세요."
        );
        String confidentialComment = normalizeText(
                request.getConfidentialComment(), 10000, "관리자 전용 의견은 10,000자 이하로 입력하세요."
        );
        if (submit && overallComment == null) {
            throw new IllegalArgumentException("종합의견을 입력하세요.");
        }

        Long currentReviewSeq = existing == null ? null : existing.getSeq();
        List<ReviewerReviewEvaluationItemResponse> evaluationItems =
                reviewerReviewRepository.findEvaluationItems(conferenceSeq, currentReviewSeq);
        if (submit && evaluationItems.isEmpty()) {
            throw new IllegalArgumentException("등록된 심사 평가항목이 없습니다.");
        }

        Map<Long, ReviewerReviewScoreRequest> scores = normalizeScores(
                request.getScores(), evaluationItems
        );
        if (submit) {
            for (ReviewerReviewEvaluationItemResponse item : evaluationItems) {
                ReviewerReviewScoreRequest score = scores.get(item.getEvaluationItemSeq());
                if (score == null || score.getScore() == null) {
                    throw new IllegalArgumentException("모든 평가항목의 점수를 입력하세요.");
                }
            }
        }

        AbstractReview review = existing == null
                ? AbstractReview.builder().assignmentSeq(assignmentSeq).build()
                : existing;
        review.setRecommendation(recommendation);
        review.setOverallComment(overallComment);
        review.setConfidentialComment(confidentialComment);

        if (existing == null) {
            reviewerReviewRepository.insertReview(review);
        } else if (reviewerReviewRepository.updateDraft(review) == 0) {
            throw new IllegalArgumentException("제출 완료된 심사는 수정할 수 없습니다.");
        }

        reviewerReviewRepository.deleteScores(review.getSeq());
        for (ReviewerReviewScoreRequest scoreRequest : scores.values()) {
            if (scoreRequest.getScore() == null) {
                continue;
            }
            reviewerReviewRepository.insertScore(AbstractReviewScore.builder()
                    .reviewSeq(review.getSeq())
                    .evaluationItemSeq(scoreRequest.getEvaluationItemSeq())
                    .score(scoreRequest.getScore())
                    .itemComment(scoreRequest.getItemComment())
                    .build());
        }

        if (submit) {
            if (reviewerReviewRepository.submitReview(review) == 0
                    || reviewerReviewRepository.markCompleted(
                            conferenceSeq, assignmentSeq, reviewer.getSeq()
                    ) == 0) {
                throw new IllegalArgumentException("심사 제출 상태를 변경하지 못했습니다.");
            }
        } else if (reviewerReviewRepository.markInReview(
                conferenceSeq, assignmentSeq, reviewer.getSeq()
        ) == 0) {
            throw new IllegalArgumentException("심사 진행 상태를 변경하지 못했습니다.");
        }

        return getDetail(conferenceSeq, adminSeq, assignmentSeq);
    }

    private Map<Long, ReviewerReviewScoreRequest> normalizeScores(
            List<ReviewerReviewScoreRequest> requestedScores,
            List<ReviewerReviewEvaluationItemResponse> evaluationItems
    ) {
        Set<Long> allowedItemSeqs = evaluationItems.stream()
                .map(ReviewerReviewEvaluationItemResponse::getEvaluationItemSeq)
                .collect(java.util.stream.Collectors.toSet());
        Map<Long, ReviewerReviewScoreRequest> normalized = new LinkedHashMap<>();

        for (ReviewerReviewScoreRequest score : requestedScores == null ? List.<ReviewerReviewScoreRequest>of() : requestedScores) {
            if (score == null || score.getEvaluationItemSeq() == null
                    || !allowedItemSeqs.contains(score.getEvaluationItemSeq())) {
                throw new IllegalArgumentException("평가항목 정보가 올바르지 않습니다.");
            }
            if (normalized.containsKey(score.getEvaluationItemSeq())) {
                throw new IllegalArgumentException("중복된 평가항목이 포함되어 있습니다.");
            }
            if (score.getScore() != null && (score.getScore() < 1 || score.getScore() > 6)) {
                throw new IllegalArgumentException("평가점수는 1점부터 6점까지 입력할 수 있습니다.");
            }
            score.setItemComment(normalizeText(
                    score.getItemComment(), 1000, "평가항목별 의견은 1,000자 이하로 입력하세요."
            ));
            normalized.put(score.getEvaluationItemSeq(), score);
        }
        return normalized;
    }

    private ReviewerProfile requireReviewer(Long conferenceSeq, Long adminSeq) {
        ReviewerProfile reviewer = adminSeq == null ? null : reviewerRepository.findByAdminSeq(
                conferenceSeq, adminSeq, personalDataProperties.requireDbEncString()
        );
        if (reviewer == null) {
            throw new IllegalArgumentException("심사자 정보를 찾을 수 없습니다.");
        }
        return reviewer;
    }

    private AbstractReviewAssignment requireAssignment(Long conferenceSeq, Long reviewerSeq, Long assignmentSeq) {
        AbstractReviewAssignment assignment = assignmentSeq == null
                ? null
                : reviewerReviewRepository.findAssignmentForReviewer(
                        conferenceSeq, assignmentSeq, reviewerSeq
                );
        if (assignment == null) {
            throw new IllegalArgumentException("본인에게 배정된 심사를 찾을 수 없습니다.");
        }
        return assignment;
    }

    private String normalizeRecommendation(String value, boolean required) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            if (required) {
                throw new IllegalArgumentException("최종 추천을 선택하세요.");
            }
            return null;
        }
        if (!RECOMMENDATIONS.contains(normalized)) {
            throw new IllegalArgumentException("최종 추천 값이 올바르지 않습니다.");
        }
        return normalized;
    }

    private String normalizeText(String value, int maxLength, String message) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
