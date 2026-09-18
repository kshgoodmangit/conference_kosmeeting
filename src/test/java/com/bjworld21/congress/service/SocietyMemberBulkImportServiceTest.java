package com.bjworld21.congress.service;

import com.bjworld21.congress.dto.SocietyMemberData.*;
import com.bjworld21.congress.repository.SocietyMemberRepository;
import com.bjworld21.congress.repository.RegistrationFeeRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SocietyMemberBulkImportServiceTest {
    private final SocietyMemberRepository repository = mock(SocietyMemberRepository.class);
    private final SocietyMemberService members = new SocietyMemberService(
            repository, mock(RegistrationFeeRepository.class), PersonalDataTestSupport.properties()
    );
    private final SocietyMemberBulkImportService importer = new SocietyMemberBulkImportService(members);

    private Workbook template() throws Exception {
        return WorkbookFactory.create(Files.newInputStream(Path.of("frontend/public/templates/society_member_bulk_import_template.xlsx")));
    }
    private MockMultipartFile file(Workbook workbook) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); workbook.write(out);
        return new MockMultipartFile("file", "members.xlsx", "application/octet-stream", out.toByteArray());
    }
    private void row(Sheet sheet, int index, String... values) {
        Row row = sheet.createRow(index);
        for (int i = 0; i < values.length; i++) row.createCell(i).setCellValue(values[i]);
    }

    @Test
    void shippedTemplateHasBlankInputTextLicenseDropdownFilterAndGuide() throws Exception {
        try (Workbook workbook = template()) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("학회회원");
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("면허번호 *");
            assertThat(sheet.getRow(3).getCell(3).getStringCellValue()).isEqualTo("회원구분 *");
            assertThat(sheet.getRow(4).getCell(0).getCellStyle().getDataFormatString()).isEqualTo("@");
            assertThat(sheet.getPaneInformation().getHorizontalSplitPosition()).isEqualTo((short) 4);
            assertThat(sheet.getDataValidations()).hasSize(1);
            assertThat(((org.apache.poi.xssf.usermodel.XSSFSheet) sheet).getCTWorksheet().isSetAutoFilter()).isTrue();
            assertThat(workbook.getSheet("작성안내").getRow(17).getCell(0).getStringCellValue()).isEqualTo("001234");
            assertThatThrownBy(() -> importer.importMembers(file(workbook))).hasMessageContaining("데이터가 없습니다");
            verifyNoInteractions(repository);
        }
    }

    @Test
    void importsValidRowsAndReportsDuplicateInvalidAndFormulaRowsWithoutLosingLeadingZero() throws Exception {
        doThrow(new DuplicateKeyException("duplicate")).when(repository).insert(
                argThat(m -> "0002".equals(m.getLicenseNumber())),
                eq(PersonalDataTestSupport.DB_ENC_STRING)
        );
        try (Workbook workbook = template()) {
            Sheet sheet = workbook.getSheetAt(0);
            row(sheet, 4, "0001", " 홍길동 ", "병원", "정회원");
            row(sheet, 5, "0001", "다른 이름", "", "준회원");
            row(sheet, 6, "0002", "김회원", "", "기타");
            row(sheet, 7, "0003", "박회원", "", "특별회원");
            row(sheet, 8, "0004", "", "", "정회원");
            row(sheet, 9, "0005", "수식회원", "", "정회원"); sheet.getRow(9).getCell(0).setCellFormula("1+1");
            row(sheet, 10, "0006", "이회원", "", "준회원");
            ImportResult result = importer.importMembers(file(workbook));
            assertThat(result.totalCount()).isEqualTo(7);
            assertThat(result.successCount()).isEqualTo(2);
            assertThat(result.skippedCount()).isEqualTo(2);
            assertThat(result.failureCount()).isEqualTo(3);
            assertThat(result.results().get(3).rowNumber()).isEqualTo(8);
            verify(repository).insert(
                    argThat(m -> "0001".equals(m.getLicenseNumber()) && "홍길동".equals(m.getFullName())),
                    eq(PersonalDataTestSupport.DB_ENC_STRING)
            );
            verify(repository, times(3)).insert(any(), eq(PersonalDataTestSupport.DB_ENC_STRING));
        }
    }

    @Test
    void rejectsTooManyRowsBeforeSavingAndAllowsOneThousand() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(); row(sheet, 0, "면허번호", "이름", "소속", "회원구분");
            for (int i = 1; i <= 1001; i++) row(sheet, i, "L" + i, "회원", "", "정회원");
            assertThatThrownBy(() -> importer.importMembers(file(workbook))).hasMessageContaining("1,000");
            verifyNoInteractions(repository);
            sheet.removeRow(sheet.getRow(1001));
            assertThat(importer.importMembers(file(workbook)).successCount()).isEqualTo(1000);
        }
    }

    @Test
    void validatesFileHeaderAndSize() throws Exception {
        assertThatThrownBy(() -> importer.importMembers(new MockMultipartFile("file", "a.xls", "", new byte[]{1}))).hasMessageContaining(".xlsx");
        assertThatThrownBy(() -> importer.importMembers(new MockMultipartFile("file", "a.xlsx", "", new byte[5 * 1024 * 1024 + 1]))).hasMessageContaining("5MB");
        assertThatThrownBy(() -> importer.importMembers(new MockMultipartFile("file", "a.xlsx", "", new byte[]{1, 2}))).hasMessageContaining("손상");
        try (Workbook workbook = new org.apache.poi.hssf.usermodel.HSSFWorkbook()) {
            workbook.createSheet();
            assertThatThrownBy(() -> importer.importMembers(file(workbook))).hasMessageContaining("실제 .xlsx");
        }
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(); row(sheet, 10, "면허번호", "이름", "소속", "회원구분");
            assertThatThrownBy(() -> importer.importMembers(file(workbook))).hasMessageContaining("첫 10행");
        }
        verifyNoInteractions(repository);
    }
}
