package com.bjworld21.congress.service;

import com.bjworld21.congress.config.PersonalDataProperties;
import com.bjworld21.congress.dto.AbstractReviewAssignmentBulkImportResponse;
import com.bjworld21.congress.dto.AbstractSubmissionResponse;
import com.bjworld21.congress.entity.AbstractReviewAssignment;
import com.bjworld21.congress.repository.AbstractReviewAssignmentRepository;
import com.bjworld21.congress.repository.AbstractSubmissionRepository;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AbstractReviewAssignmentBulkImportService {
    private static final long MAX_FILE_BYTES = 5L * 1024L * 1024L;
    private static final int MAX_DATA_ROWS = 1_000;
    private static final int MAX_REVIEWERS_PER_ABSTRACT = 20;
    private static final Set<String> ASSIGNABLE_ABSTRACT_STATUSES = Set.of("submitted", "under_review");
    private static final Set<String> ACTIVE_ASSIGNMENT_STATUSES = Set.of("assigned", "accepted", "in_review");
    private static final Set<String> REACTIVATABLE_ASSIGNMENT_STATUSES = Set.of("cancelled", "declined");

    private final AbstractReviewAssignmentRepository assignmentRepository;
    private final AbstractSubmissionRepository abstractSubmissionRepository;
    private final PersonalDataProperties personalDataProperties;

    public AbstractReviewAssignmentBulkImportService(
            AbstractReviewAssignmentRepository assignmentRepository,
            AbstractSubmissionRepository abstractSubmissionRepository,
            PersonalDataProperties personalDataProperties
    ) {
        this.assignmentRepository = assignmentRepository;
        this.abstractSubmissionRepository = abstractSubmissionRepository;
        this.personalDataProperties = personalDataProperties;
    }

    @Transactional
    public AbstractReviewAssignmentBulkImportResponse importAssignments(
            Long conferenceSeq,
            MultipartFile file,
            LocalDateTime dueAt,
            Long assignedByAdminSeq
    ) {
        validateFile(file);
        if (assignedByAdminSeq == null) {
            throw new IllegalArgumentException("관리자 로그인 정보가 올바르지 않습니다.");
        }

        List<ImportRow> rows = parse(file);
        List<AbstractReviewAssignmentBulkImportResponse.RowResult> results = new ArrayList<>();
        Set<String> workbookAssignments = new HashSet<>();
        int successCount = 0;
        int skippedCount = 0;

        for (ImportRow row : rows) {
            String duplicateKey = row.submissionNo().toLowerCase(Locale.ROOT)
                    + "\u0000" + row.reviewerId().toLowerCase(Locale.ROOT);
            if (!workbookAssignments.add(duplicateKey)) {
                results.add(result(row, "failure", "엑셀 파일 안에서 동일한 초록과 심사자 조합이 중복되었습니다."));
                continue;
            }

            try {
                String status = assign(conferenceSeq, row, dueAt, assignedByAdminSeq);
                if ("success".equals(status)) {
                    successCount++;
                    results.add(result(row, status, "심사자가 배정되었습니다."));
                } else {
                    skippedCount++;
                    results.add(result(row, status, "이미 배정되었거나 심사가 완료되어 변경하지 않았습니다."));
                }
            } catch (IllegalArgumentException exception) {
                results.add(result(row, "failure", exception.getMessage()));
            } catch (RuntimeException exception) {
                results.add(result(row, "failure", "심사자 배정 저장 중 오류가 발생했습니다."));
            }
        }

        int failureCount = rows.size() - successCount - skippedCount;
        return new AbstractReviewAssignmentBulkImportResponse(
                rows.size(), successCount, skippedCount, failureCount, List.copyOf(results)
        );
    }

    private String assign(Long conferenceSeq, ImportRow row, LocalDateTime dueAt, Long assignedByAdminSeq) {
        if (row.submissionNo().isBlank()) {
            throw new IllegalArgumentException("초록번호는 필수입니다.");
        }
        if (row.reviewerId().isBlank()) {
            throw new IllegalArgumentException("심사자 아이디는 필수입니다.");
        }
        if (row.submissionNo().length() > 30) {
            throw new IllegalArgumentException("초록번호는 30자 이하로 입력해 주세요.");
        }
        if (row.reviewerId().length() > 255) {
            throw new IllegalArgumentException("심사자 아이디는 255자 이하로 입력해 주세요.");
        }

        AbstractSubmissionResponse abstractSubmission = abstractSubmissionRepository.findBySubmissionNo(
                conferenceSeq, row.submissionNo()
        );
        if (abstractSubmission == null) {
            throw new IllegalArgumentException("초록번호에 해당하는 초록을 찾을 수 없습니다.");
        }
        if (!ASSIGNABLE_ABSTRACT_STATUSES.contains(abstractSubmission.getStatus())) {
            throw new IllegalArgumentException("제출완료 또는 심사중 상태의 초록만 배정할 수 있습니다.");
        }

        Long reviewerSeq = assignmentRepository.findEligibleReviewerSeqById(
                conferenceSeq, row.reviewerId(), personalDataProperties.requireDbEncString()
        );
        if (reviewerSeq == null) {
            throw new IllegalArgumentException("사용 가능한 심사자 아이디를 찾을 수 없습니다.");
        }

        AbstractReviewAssignment existing = assignmentRepository.findByAbstractAndReviewer(
                conferenceSeq, abstractSubmission.getSeq(), reviewerSeq
        );
        if (existing != null && (ACTIVE_ASSIGNMENT_STATUSES.contains(existing.getStatus())
                || "completed".equals(existing.getStatus()))) {
            return "skipped";
        }
        if (assignmentRepository.countEffectiveAssignments(conferenceSeq, abstractSubmission.getSeq())
                >= MAX_REVIEWERS_PER_ABSTRACT) {
            throw new IllegalArgumentException("한 초록에는 심사자를 최대 20명까지 배정할 수 있습니다.");
        }

        if (existing == null) {
            assignmentRepository.insert(AbstractReviewAssignment.builder()
                    .conferenceSeq(conferenceSeq)
                    .abstractSeq(abstractSubmission.getSeq())
                    .reviewerSeq(reviewerSeq)
                    .assignedByAdminSeq(assignedByAdminSeq)
                    .dueAt(dueAt)
                    .build());
        } else if (REACTIVATABLE_ASSIGNMENT_STATUSES.contains(existing.getStatus())) {
            existing.setAssignedByAdminSeq(assignedByAdminSeq);
            existing.setDueAt(dueAt);
            assignmentRepository.reactivate(existing);
        } else {
            throw new IllegalArgumentException("현재 상태에서는 해당 심사자를 다시 배정할 수 없습니다.");
        }

        abstractSubmissionRepository.updateReviewStatus(
                conferenceSeq, abstractSubmission.getSeq(), "under_review"
        );
        return "success";
    }

    private List<ImportRow> parse(MultipartFile file) {
        DataFormatter formatter = new DataFormatter(Locale.KOREA);
        List<ImportRow> rows = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("엑셀 파일에 시트가 없습니다.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            HeaderColumns columns = findHeader(sheet, formatter);
            if (columns == null) {
                throw new IllegalArgumentException("첫 번째 시트에서 초록번호, 심사자 아이디 헤더를 찾을 수 없습니다.");
            }

            for (int rowIndex = columns.rowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                ImportRow imported = new ImportRow(
                        rowIndex + 1,
                        cellValue(row, columns.submissionNoColumn(), formatter),
                        cellValue(row, columns.reviewerIdColumn(), formatter)
                );
                if (imported.isBlank()) {
                    continue;
                }
                rows.add(imported);
                if (rows.size() > MAX_DATA_ROWS) {
                    throw new IllegalArgumentException("한 번에 최대 1,000개 배정까지 등록할 수 있습니다.");
                }
            }
        } catch (EncryptedDocumentException exception) {
            throw new IllegalArgumentException("암호화된 엑셀 파일은 등록할 수 없습니다.");
        } catch (IOException exception) {
            throw new IllegalArgumentException("손상되었거나 지원하지 않는 엑셀 파일입니다.");
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("손상되었거나 지원하지 않는 엑셀 파일입니다.");
        }

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("등록할 심사자 배정 정보가 없습니다.");
        }
        return List.copyOf(rows);
    }

    private HeaderColumns findHeader(Sheet sheet, DataFormatter formatter) {
        int lastCandidate = Math.min(sheet.getLastRowNum(), 9);
        for (int rowIndex = 0; rowIndex <= lastCandidate; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            Integer submissionNo = null;
            Integer reviewerId = null;
            for (Cell cell : row) {
                String header = normalizeHeader(formatter.formatCellValue(cell));
                if ("초록번호".equals(header) || "접수번호".equals(header) || "submissionno".equals(header)) {
                    submissionNo = cell.getColumnIndex();
                }
                if ("심사자아이디".equals(header) || "reviewerid".equals(header)) {
                    reviewerId = cell.getColumnIndex();
                }
            }
            if (submissionNo != null && reviewerId != null) {
                return new HeaderColumns(rowIndex, submissionNo, reviewerId);
            }
        }
        return null;
    }

    private String normalizeHeader(String value) {
        return value.trim().replace("*", "").replace(" ", "").toLowerCase(Locale.ROOT);
    }

    private String cellValue(Row row, int columnIndex, DataFormatter formatter) {
        if (row == null) {
            return "";
        }
        Cell cell = row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("등록할 엑셀 파일을 선택해 주세요.");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("심사자 일괄배정 파일은 5MB를 초과할 수 없습니다.");
        }
        String filename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        if (filename.contains("..") || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException(".xlsx 형식의 엑셀 파일만 등록할 수 있습니다.");
        }
    }

    private AbstractReviewAssignmentBulkImportResponse.RowResult result(
            ImportRow row,
            String status,
            String message
    ) {
        return new AbstractReviewAssignmentBulkImportResponse.RowResult(
                row.rowNumber(), row.submissionNo(), row.reviewerId(), status, message
        );
    }

    private record HeaderColumns(int rowIndex, int submissionNoColumn, int reviewerIdColumn) {
    }

    private record ImportRow(int rowNumber, String submissionNo, String reviewerId) {
        private boolean isBlank() {
            return submissionNo.isBlank() && reviewerId.isBlank();
        }
    }
}
