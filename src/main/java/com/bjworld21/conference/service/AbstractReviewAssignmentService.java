package com.bjworld21.conference.service;

import com.bjworld21.conference.config.PersonalDataProperties;
import com.bjworld21.conference.dto.AbstractReviewAssignmentRequest;
import com.bjworld21.conference.dto.AbstractReviewAssignmentResponse;
import com.bjworld21.conference.dto.AbstractSubmissionResponse;
import com.bjworld21.conference.dto.ReviewerAssignmentCandidateResponse;
import com.bjworld21.conference.entity.AbstractReviewAssignment;
import com.bjworld21.conference.repository.AbstractReviewAssignmentRepository;
import com.bjworld21.conference.repository.AbstractSubmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class AbstractReviewAssignmentService {
    private static final Set<String> ASSIGNABLE_ABSTRACT_STATUSES = Set.of("submitted", "under_review");
    private static final Set<String> REACTIVATABLE_ASSIGNMENT_STATUSES = Set.of("cancelled", "declined");

    private final AbstractReviewAssignmentRepository assignmentRepository;
    private final AbstractSubmissionRepository abstractSubmissionRepository;
    private final PersonalDataProperties personalDataProperties;

    public AbstractReviewAssignmentService(
            AbstractReviewAssignmentRepository assignmentRepository,
            AbstractSubmissionRepository abstractSubmissionRepository,
            PersonalDataProperties personalDataProperties
    ) {
        this.assignmentRepository = assignmentRepository;
        this.abstractSubmissionRepository = abstractSubmissionRepository;
        this.personalDataProperties = personalDataProperties;
    }

    public AbstractReviewAssignmentResponse getAssignments(Long conferenceSeq, Long abstractSeq) {
        AbstractSubmissionResponse abstractSubmission = requireAssignableAbstract(conferenceSeq, abstractSeq);
        List<ReviewerAssignmentCandidateResponse> reviewers = assignmentRepository.findCandidates(
                conferenceSeq, abstractSeq, personalDataProperties.requireDbEncString()
        );
        LocalDateTime dueAt = reviewers.stream()
                .filter(candidate -> isEffectiveStatus(candidate.getAssignmentStatus()))
                .map(ReviewerAssignmentCandidateResponse::getDueAt)
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);

        return AbstractReviewAssignmentResponse.builder()
                .abstractSeq(abstractSubmission.getSeq())
                .submissionNo(abstractSubmission.getSubmissionNo())
                .title(abstractSubmission.getTitle())
                .abstractStatus(abstractSubmission.getStatus())
                .categoryCode(abstractSubmission.getCategoryCode())
                .categoryName(abstractSubmission.getCategoryName())
                .dueAt(dueAt)
                .reviewers(reviewers)
                .build();
    }

    @Transactional
    public AbstractReviewAssignmentResponse saveAssignments(
            Long conferenceSeq,
            Long abstractSeq,
            AbstractReviewAssignmentRequest request,
            Long assignedByAdminSeq
    ) {
        requireAssignableAbstract(conferenceSeq, abstractSeq);
        if (request == null) {
            throw new IllegalArgumentException("심사자 할당 정보를 입력하세요.");
        }

        LinkedHashSet<Long> selectedReviewerSeqs = new LinkedHashSet<>();
        if (request.getReviewerSeqs() != null) {
            for (Long reviewerSeq : request.getReviewerSeqs()) {
                if (reviewerSeq == null) {
                    throw new IllegalArgumentException("심사자 정보가 올바르지 않습니다.");
                }
                selectedReviewerSeqs.add(reviewerSeq);
            }
        }
        if (selectedReviewerSeqs.size() > 20) {
            throw new IllegalArgumentException("한 초록에는 심사자를 최대 20명까지 할당할 수 있습니다.");
        }

        for (Long reviewerSeq : selectedReviewerSeqs) {
            AbstractReviewAssignment existing = assignmentRepository.findByAbstractAndReviewer(
                    conferenceSeq, abstractSeq, reviewerSeq
            );
            if (existing != null && "completed".equals(existing.getStatus())) {
                continue;
            }
            boolean existingActive = existing != null && isEffectiveStatus(existing.getStatus());
            if (!existingActive && assignmentRepository.countEligibleReviewer(conferenceSeq, reviewerSeq) == 0) {
                throw new IllegalArgumentException("할당할 수 없는 심사자가 포함되어 있습니다.");
            }

            if (existing == null) {
                assignmentRepository.insert(AbstractReviewAssignment.builder()
                        .conferenceSeq(conferenceSeq)
                        .abstractSeq(abstractSeq)
                        .reviewerSeq(reviewerSeq)
                        .assignedByAdminSeq(assignedByAdminSeq)
                        .dueAt(request.getDueAt())
                        .build());
            } else if (REACTIVATABLE_ASSIGNMENT_STATUSES.contains(existing.getStatus())) {
                existing.setAssignedByAdminSeq(assignedByAdminSeq);
                existing.setDueAt(request.getDueAt());
                assignmentRepository.reactivate(existing);
            } else {
                assignmentRepository.updateDueAt(conferenceSeq, existing.getSeq(), request.getDueAt());
            }
        }

        for (Long reviewerSeq : assignmentRepository.findActiveReviewerSeqs(conferenceSeq, abstractSeq)) {
            if (!selectedReviewerSeqs.contains(reviewerSeq)) {
                assignmentRepository.cancel(conferenceSeq, abstractSeq, reviewerSeq);
            }
        }

        String nextStatus = assignmentRepository.countEffectiveAssignments(conferenceSeq, abstractSeq) > 0
                ? "under_review"
                : "submitted";
        abstractSubmissionRepository.updateReviewStatus(conferenceSeq, abstractSeq, nextStatus);
        return getAssignments(conferenceSeq, abstractSeq);
    }

    private AbstractSubmissionResponse requireAssignableAbstract(Long conferenceSeq, Long abstractSeq) {
        if (abstractSeq == null) {
            throw new IllegalArgumentException("초록 정보가 올바르지 않습니다.");
        }
        AbstractSubmissionResponse abstractSubmission = abstractSubmissionRepository.findBySeq(
                conferenceSeq, abstractSeq, personalDataProperties.requireDbEncString()
        );
        if (abstractSubmission == null) {
            throw new IllegalArgumentException("초록을 찾을 수 없습니다.");
        }
        if (!ASSIGNABLE_ABSTRACT_STATUSES.contains(abstractSubmission.getStatus())) {
            throw new IllegalArgumentException("제출완료 또는 심사중 상태의 초록만 심사자를 할당할 수 있습니다.");
        }
        return abstractSubmission;
    }

    private boolean isEffectiveStatus(String status) {
        return "assigned".equals(status)
                || "accepted".equals(status)
                || "in_review".equals(status)
                || "completed".equals(status);
    }
}
