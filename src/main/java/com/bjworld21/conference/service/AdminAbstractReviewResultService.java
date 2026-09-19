package com.bjworld21.conference.service;

import com.bjworld21.conference.config.PersonalDataProperties;
import com.bjworld21.conference.dto.AbstractReviewEvaluationSummaryResponse;
import com.bjworld21.conference.dto.AbstractReviewResultResponse;
import com.bjworld21.conference.dto.AbstractReviewReviewerResponse;
import com.bjworld21.conference.dto.AbstractReviewScoreResponse;
import com.bjworld21.conference.dto.AbstractSubmissionResponse;
import com.bjworld21.conference.repository.AbstractSubmissionRepository;
import com.bjworld21.conference.repository.AdminAbstractReviewResultRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminAbstractReviewResultService {
    private final AdminAbstractReviewResultRepository reviewResultRepository;
    private final AbstractSubmissionRepository abstractSubmissionRepository;
    private final PersonalDataProperties personalDataProperties;

    public AdminAbstractReviewResultService(
            AdminAbstractReviewResultRepository reviewResultRepository,
            AbstractSubmissionRepository abstractSubmissionRepository,
            PersonalDataProperties personalDataProperties
    ) {
        this.reviewResultRepository = reviewResultRepository;
        this.abstractSubmissionRepository = abstractSubmissionRepository;
        this.personalDataProperties = personalDataProperties;
    }

    public AbstractReviewResultResponse getReviewResults(Long conferenceSeq, Long abstractSeq) {
        AbstractSubmissionResponse abstractSubmission = abstractSeq == null
                ? null
                : abstractSubmissionRepository.findBySeq(
                        conferenceSeq, abstractSeq, personalDataProperties.requireDbEncString()
                );
        if (abstractSubmission == null) {
            throw new IllegalArgumentException("초록을 찾을 수 없습니다.");
        }

        List<AbstractReviewReviewerResponse> reviews = reviewResultRepository.findReviews(
                conferenceSeq, abstractSeq, personalDataProperties.requireDbEncString()
        );
        List<AbstractReviewScoreResponse> submittedScores = reviewResultRepository.findSubmittedScores(
                conferenceSeq, abstractSeq
        );
        Map<Long, List<AbstractReviewScoreResponse>> scoresByReview = submittedScores.stream()
                .collect(Collectors.groupingBy(
                        AbstractReviewScoreResponse::getReviewSeq,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        LinkedHashMap<String, Integer> recommendationCounts = new LinkedHashMap<>();
        recommendationCounts.put("accept", 0);
        recommendationCounts.put("reject", 0);

        int completedCount = 0;
        for (AbstractReviewReviewerResponse review : reviews) {
            boolean submitted = "submitted".equals(review.getReviewStatus());
            if (!submitted) {
                review.setScores(List.of());
                review.setAverageScore(null);
                continue;
            }

            completedCount += 1;
            List<AbstractReviewScoreResponse> reviewScores = scoresByReview.getOrDefault(review.getReviewSeq(), List.of());
            review.setScores(reviewScores);
            review.setAverageScore(average(reviewScores));
            if (review.getRecommendation() != null && recommendationCounts.containsKey(review.getRecommendation())) {
                recommendationCounts.computeIfPresent(review.getRecommendation(), (key, value) -> value + 1);
            }
        }

        Map<Long, List<AbstractReviewScoreResponse>> scoresByItem = submittedScores.stream()
                .collect(Collectors.groupingBy(
                        AbstractReviewScoreResponse::getEvaluationItemSeq,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        List<AbstractReviewEvaluationSummaryResponse> evaluationSummaries = scoresByItem.values().stream()
                .map(itemScores -> {
                    AbstractReviewScoreResponse first = itemScores.get(0);
                    return AbstractReviewEvaluationSummaryResponse.builder()
                            .evaluationItemSeq(first.getEvaluationItemSeq())
                            .itemName(first.getItemName())
                            .sortOrder(first.getSortOrder())
                            .reviewerCount(itemScores.size())
                            .averageScore(average(itemScores))
                            .build();
                })
                .toList();

        return AbstractReviewResultResponse.builder()
                .abstractSeq(abstractSubmission.getSeq())
                .submissionNo(abstractSubmission.getSubmissionNo())
                .title(abstractSubmission.getTitle())
                .assignedCount(reviews.size())
                .completedCount(completedCount)
                .averageScore(average(submittedScores))
                .recommendationCounts(recommendationCounts)
                .evaluationSummaries(evaluationSummaries)
                .reviews(reviews)
                .build();
    }

    private Double average(List<AbstractReviewScoreResponse> scores) {
        if (scores == null || scores.isEmpty()) {
            return null;
        }
        double value = scores.stream()
                .map(AbstractReviewScoreResponse::getScore)
                .filter(score -> score != null)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(Double.NaN);
        return Double.isNaN(value) ? null : Math.round(value * 100.0) / 100.0;
    }
}
