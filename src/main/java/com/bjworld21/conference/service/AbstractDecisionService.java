package com.bjworld21.conference.service;

import com.bjworld21.conference.config.PersonalDataProperties;
import com.bjworld21.conference.dto.AbstractDecisionRequest;
import com.bjworld21.conference.dto.AbstractSubmissionResponse;
import com.bjworld21.conference.repository.AbstractReviewAssignmentRepository;
import com.bjworld21.conference.repository.AbstractSubmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class AbstractDecisionService {
    public static final int REQUIRED_REVIEWER_COUNT = 2;
    private static final Set<String> DECISIONS = Set.of("approved", "rejected");

    private final AbstractSubmissionRepository abstractSubmissionRepository;
    private final AbstractReviewAssignmentRepository assignmentRepository;
    private final PersonalDataProperties personalDataProperties;

    public AbstractDecisionService(
            AbstractSubmissionRepository abstractSubmissionRepository,
            AbstractReviewAssignmentRepository assignmentRepository,
            PersonalDataProperties personalDataProperties
    ) {
        this.abstractSubmissionRepository = abstractSubmissionRepository;
        this.assignmentRepository = assignmentRepository;
        this.personalDataProperties = personalDataProperties;
    }

    @Transactional
    public AbstractSubmissionResponse decide(
            Long conferenceSeq,
            Long abstractSeq,
            Long adminSeq,
            AbstractDecisionRequest request
    ) {
        if (abstractSeq == null || adminSeq == null) {
            throw new IllegalArgumentException("초록 또는 관리자 정보가 올바르지 않습니다.");
        }
        if (request == null || !DECISIONS.contains(request.getDecision())) {
            throw new IllegalArgumentException("최종 결정은 승인 또는 반려만 선택할 수 있습니다.");
        }

        AbstractSubmissionResponse submission = abstractSubmissionRepository.findBySeq(
                conferenceSeq, abstractSeq, personalDataProperties.requireDbEncString()
        );
        if (submission == null) {
            throw new IllegalArgumentException("초록을 찾을 수 없습니다.");
        }
        if (!Set.of("submitted", "under_review").contains(submission.getStatus())) {
            throw new IllegalArgumentException("제출완료 또는 심사중 상태의 초록만 최종 결정할 수 있습니다.");
        }

        long assignmentCount = assignmentRepository.countEffectiveAssignments(conferenceSeq, abstractSeq);
        long completedReviewCount = assignmentRepository.countCompletedSubmittedReviews(conferenceSeq, abstractSeq);
        boolean reviewCompleted = assignmentCount >= REQUIRED_REVIEWER_COUNT
                && completedReviewCount == assignmentCount;
        boolean forcedDecision = !reviewCompleted;

        String reason = normalizeReason(request.getReason());
        if (forcedDecision && reason == null) {
            throw new IllegalArgumentException("심사가 완료되지 않은 초록을 강제 결정하려면 사유를 입력하세요.");
        }
        if (reason != null && reason.length() > 1000) {
            throw new IllegalArgumentException("결정 사유는 1000자 이하로 입력하세요.");
        }

        Long acceptedPresentationTypeCode = null;
        if ("approved".equals(request.getDecision())) {
            acceptedPresentationTypeCode = request.getAcceptedPresentationTypeCode();
            if (acceptedPresentationTypeCode == null
                    || abstractSubmissionRepository.countEnabledPresentationTypeByCode(acceptedPresentationTypeCode) == 0) {
                throw new IllegalArgumentException("승인할 발표형식을 선택하세요.");
            }
        }

        int updated = abstractSubmissionRepository.updateDecision(
                conferenceSeq,
                abstractSeq,
                request.getDecision(),
                acceptedPresentationTypeCode,
                adminSeq,
                reason,
                forcedDecision
        );
        if (updated != 1) {
            throw new IllegalArgumentException("초록 상태가 변경되어 최종 결정을 저장하지 못했습니다.");
        }
        return abstractSubmissionRepository.findBySeq(
                conferenceSeq, abstractSeq, personalDataProperties.requireDbEncString()
        );
    }

    private String normalizeReason(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
