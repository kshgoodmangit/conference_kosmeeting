package com.bjworld21.congress.service;

import com.bjworld21.congress.dto.AdminBulkImportResponse;
import com.bjworld21.congress.dto.AdminCreateRequest;
import org.apache.poi.ss.usermodel.DataValidation;
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
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AdminBulkImportServiceTest {
    private AdminService adminService;
    private AdminBulkImportService service;

    @BeforeEach
    void setUp() {
        adminService = mock(AdminService.class);
        service = new AdminBulkImportService(adminService);
        doAnswer(invocation -> {
            AdminCreateRequest request = invocation.getArgument(1);
            if ("existing".equals(request.getEmail())) {
                throw new IllegalArgumentException("이미 등록된 아이디입니다.");
            }
            return null;
        }).when(adminService).create(eq(1L), any(AdminCreateRequest.class));
    }

    @Test
    void continuesAfterFailuresAndBuildsPasswordsFromAdminIds() throws Exception {
        MockMultipartFile file = workbookFile(new String[][]{
                {"existing", "기존 관리자", "admin", "테스트 학회", "운영팀", "팀장", "02-1111-1111", "existing@example.com", "", ""},
                {"bulkadmin1", "일괄 관리자", "admin", "테스트 학회", "사무국", "담당자", "02-2222-2222", "admin@example.com", "", ""},
                {"bulkreviewer1", "일괄 심사자", "reviewer", "테스트 병원", "내과", "심사위원", "02-1234-5678", "reviewer@example.com", "Y", "101,102"},
                {"bulkadmin1", "중복 관리자", "admin", "테스트 학회", "사무국", "담당자", "02-2222-2222", "admin@example.com", "", ""},
                {"wrong-role", "잘못된 권한", "manager", "테스트 학회", "운영팀", "담당자", "02-3333-3333", "wrong@example.com", "", ""}
        });

        AdminBulkImportResponse response = service.importAccounts(1L, file);

        assertThat(response.totalCount()).isEqualTo(5);
        assertThat(response.successCount()).isEqualTo(2);
        assertThat(response.failureCount()).isEqualTo(3);
        assertThat(response.results()).filteredOn(result -> !result.success())
                .extracting(AdminBulkImportResponse.RowResult::message)
                .containsExactly(
                        "이미 등록된 아이디입니다.",
                        "엑셀 파일 안에서 아이디가 중복되었습니다.",
                        "권한은 admin, reviewer 또는 maintenance만 입력할 수 있습니다."
                );

        ArgumentCaptor<AdminCreateRequest> captor = ArgumentCaptor.forClass(AdminCreateRequest.class);
        verify(adminService, times(3)).create(eq(1L), captor.capture());
        List<AdminCreateRequest> requests = captor.getAllValues();
        assertThat(requests).extracting(AdminCreateRequest::getPassword)
                .containsExactly("existing12#$", "bulkadmin112#$", "bulkreviewer112#$");
        assertThat(requests.get(1).getAffiliation()).isEqualTo("테스트 학회");
        assertThat(requests.get(2).getAffiliation()).isEqualTo("테스트 병원");
        assertThat(requests.get(2).getReviewerProfile().getExpertiseCodes()).containsExactly("101", "102");
    }

    @Test
    void templateOffersMaintenanceRoleAndDocumentsIt() throws Exception {
        Path templatePath = Path.of(
                "frontend", "public", "templates", "admin_account_bulk_import_template.xlsx"
        );
        assertThat(Files.exists(templatePath)).isTrue();

        try (Workbook workbook = WorkbookFactory.create(Files.newInputStream(templatePath))) {
            Sheet inputSheet = workbook.getSheet("관리자 일괄등록");
            Sheet guideSheet = workbook.getSheet("작성안내");

            assertThat(inputSheet).isNotNull();
            assertThat(guideSheet).isNotNull();

            DataValidation roleValidation = inputSheet.getDataValidations().stream()
                    .filter(validation -> Arrays.stream(validation.getRegions().getCellRangeAddresses())
                            .anyMatch(range -> range.isInRange(4, 2)))
                    .findFirst()
                    .orElseThrow();
            assertThat(Arrays.asList(roleValidation.getValidationConstraint().getExplicitListValues()))
                    .containsExactly("admin", "reviewer", "maintenance");

            assertThat(guideSheet.getRow(4).getCell(1).getStringCellValue()).contains("maintenance");
            assertThat(guideSheet.getRow(15).getCell(0).getStringCellValue()).isEqualTo("bulkmaintenance1");
            assertThat(guideSheet.getRow(15).getCell(2).getStringCellValue()).isEqualTo("maintenance");
        }
    }

    private MockMultipartFile workbookFile(String[][] values) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("관리자 일괄등록");
            Row header = sheet.createRow(3);
            String[] headers = {
                    "아이디*", "관리자명*", "권한*", "소속기관*", "부서·학과·진료과*",
                    "직위*", "연락처*", "이메일*", "심사 배정 사용 여부", "심사 전문분야 코드"
            };
            for (int index = 0; index < headers.length; index++) {
                header.createCell(index).setCellValue(headers[index]);
            }
            for (int rowIndex = 0; rowIndex < values.length; rowIndex++) {
                Row row = sheet.createRow(rowIndex + 4);
                for (int columnIndex = 0; columnIndex < values[rowIndex].length; columnIndex++) {
                    row.createCell(columnIndex).setCellValue(values[rowIndex][columnIndex]);
                }
            }
            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "admin-accounts.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray()
            );
        }
    }
}
