package com.bjworld21.congress.service;

import com.bjworld21.congress.dto.AbstractReviewAssignmentBulkImportResponse;
import com.bjworld21.congress.dto.AbstractSubmissionResponse;
import com.bjworld21.congress.entity.AbstractReviewAssignment;
import com.bjworld21.congress.repository.AbstractReviewAssignmentRepository;
import com.bjworld21.congress.repository.AbstractSubmissionRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbstractReviewAssignmentBulkImportServiceTest {
    private AbstractReviewAssignmentRepository assignmentRepository;
    private AbstractSubmissionRepository abstractSubmissionRepository;
    private AbstractReviewAssignmentBulkImportService service;

    @BeforeEach
    void setUp() {
        assignmentRepository = mock(AbstractReviewAssignmentRepository.class);
        abstractSubmissionRepository = mock(AbstractSubmissionRepository.class);
        service = new AbstractReviewAssignmentBulkImportService(
                assignmentRepository, abstractSubmissionRepository, PersonalDataTestSupport.properties()
        );
    }

    @Test
    void addsAssignmentsWithoutCancellingExistingAssignmentsAndReportsEachRow() throws Exception {
        AbstractSubmissionResponse submitted = AbstractSubmissionResponse.builder()
                .seq(1L)
                .submissionNo("APDRC8-Abstract-0001")
                .status("submitted")
                .build();
        AbstractSubmissionResponse approved = AbstractSubmissionResponse.builder()
                .seq(2L)
                .submissionNo("APDRC8-Abstract-0002")
                .status("approved")
                .build();
        when(abstractSubmissionRepository.findBySubmissionNo(1L, "APDRC8-Abstract-0001")).thenReturn(submitted);
        when(abstractSubmissionRepository.findBySubmissionNo(1L, "APDRC8-Abstract-0002")).thenReturn(approved);
        when(assignmentRepository.findEligibleReviewerSeqById(
                1L, "reviewer1", PersonalDataTestSupport.DB_ENC_STRING
        )).thenReturn(10L);
        when(assignmentRepository.findEligibleReviewerSeqById(
                1L, "reviewer2", PersonalDataTestSupport.DB_ENC_STRING
        )).thenReturn(20L);
        when(assignmentRepository.findEligibleReviewerSeqById(
                1L, "missing-reviewer", PersonalDataTestSupport.DB_ENC_STRING
        )).thenReturn(null);
        when(assignmentRepository.findByAbstractAndReviewer(1L, 1L, 20L)).thenReturn(
                AbstractReviewAssignment.builder().seq(200L).abstractSeq(1L).reviewerSeq(20L).status("completed").build()
        );
        when(assignmentRepository.countEffectiveAssignments(1L, 1L)).thenReturn(1L);

        MockMultipartFile file = workbookFile(new String[][]{
                {"APDRC8-Abstract-0001", "reviewer1"},
                {"APDRC8-Abstract-0001", "reviewer2"},
                {"APDRC8-Abstract-0002", "reviewer1"},
                {"APDRC8-Abstract-0001", "reviewer1"},
                {"APDRC8-Abstract-0001", "missing-reviewer"}
        });
        LocalDateTime dueAt = LocalDateTime.of(2026, 9, 30, 18, 0);

        AbstractReviewAssignmentBulkImportResponse response = service.importAssignments(1L, file, dueAt, 99L);

        assertThat(response.totalCount()).isEqualTo(5);
        assertThat(response.successCount()).isEqualTo(1);
        assertThat(response.skippedCount()).isEqualTo(1);
        assertThat(response.failureCount()).isEqualTo(3);
        assertThat(response.results()).extracting(AbstractReviewAssignmentBulkImportResponse.RowResult::status)
                .containsExactly("success", "skipped", "failure", "failure", "failure");

        ArgumentCaptor<AbstractReviewAssignment> assignmentCaptor = ArgumentCaptor.forClass(AbstractReviewAssignment.class);
        verify(assignmentRepository).insert(assignmentCaptor.capture());
        assertThat(assignmentCaptor.getValue().getAbstractSeq()).isEqualTo(1L);
        assertThat(assignmentCaptor.getValue().getReviewerSeq()).isEqualTo(10L);
        assertThat(assignmentCaptor.getValue().getAssignedByAdminSeq()).isEqualTo(99L);
        assertThat(assignmentCaptor.getValue().getDueAt()).isEqualTo(dueAt);
        verify(abstractSubmissionRepository).updateReviewStatus(1L, 1L, "under_review");
        verify(assignmentRepository, never()).cancel(any(), any(), any());
    }

    @Test
    void staticTemplateHasExpectedUploadHeadersAndLoginIdExamples() throws Exception {
        Path templatePath = Path.of(
                "frontend", "public", "templates", "abstract_reviewer_assignment_template.xlsx"
        );
        assertThat(Files.exists(templatePath)).isTrue();

        try (Workbook workbook = WorkbookFactory.create(Files.newInputStream(templatePath))) {
            Sheet input = workbook.getSheetAt(0);
            Sheet guide = workbook.getSheetAt(1);

            assertThat(input.getSheetName()).isEqualTo("심사자 일괄배정");
            assertThat(input.getRow(3).getCell(0).getStringCellValue()).isEqualTo("초록번호*");
            assertThat(input.getRow(3).getCell(1).getStringCellValue()).isEqualTo("심사자 아이디*");

            assertThat(guide.getSheetName()).isEqualTo("작성안내");
            assertThat(guide.getRow(14).getCell(1).getStringCellValue()).isEqualTo("reviewer1");
            assertThat(guide.getRow(14).getCell(1).getStringCellValue()).doesNotContain("@");
        }
    }

    private MockMultipartFile workbookFile(String[][] values) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("심사자 일괄배정");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("초록번호*");
            header.createCell(1).setCellValue("심사자 아이디*");
            for (int rowIndex = 0; rowIndex < values.length; rowIndex++) {
                Row row = sheet.createRow(rowIndex + 1);
                row.createCell(0).setCellValue(values[rowIndex][0]);
                row.createCell(1).setCellValue(values[rowIndex][1]);
            }
            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "reviewer-assignments.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray()
            );
        }
    }
}
