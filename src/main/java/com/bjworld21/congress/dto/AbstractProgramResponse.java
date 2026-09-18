package com.bjworld21.congress.dto;

import java.util.List;

public record AbstractProgramResponse(
        ProgramManagementResponse program,
        List<Assignment> assignments,
        List<AbstractPresentationTypeResponse> presentationTypes,
        List<AbstractCategoryResponse> categories,
        List<AbstractProgramCandidate> candidates,
        int page, int size, long totalCount, int totalPages
) {
    public record Assignment(Long programItemSeq, Long abstractSubmissionSeq,
                             String submissionNo, String status, boolean canRestore) {}
    public record AssignRequest(Long abstractSubmissionSeq) {}
    public record ReleaseRequest(Long abstractSubmissionSeq, boolean restoreOriginal) {}
}
