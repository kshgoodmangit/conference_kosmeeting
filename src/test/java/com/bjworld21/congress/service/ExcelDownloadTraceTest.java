package com.bjworld21.congress.service;

import com.bjworld21.congress.util.XlsxWorkbookWriter;
import com.bjworld21.congress.repository.AdminAccountRepository;
import com.bjworld21.congress.repository.MemberRepository;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ExcelDownloadTraceTest {
    @ParameterizedTest
    @EnumSource(ExcelExportType.class)
    void embedsAuditReferenceInBothLocationsWithoutChangingData(ExcelExportType type) throws Exception {
        ExcelExportResult source = source();
        ExcelExportResult result = ExcelDownloadTrace.attach(source, Long.MAX_VALUE, type);

        assertThat(result.filename()).isEqualTo(source.filename());
        assertThat(result.rowCount()).isEqualTo(1);
        try (XSSFWorkbook before = open(source.content()); XSSFWorkbook after = open(result.content())) {
            assertTrace(after, Long.toString(Long.MAX_VALUE), type);
            var sheet = after.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("회원");
            assertThat(after.getActiveSheetIndex()).isEqualTo(before.getActiveSheetIndex());
            assertThat(after.getSheetVisibility(0)).isEqualTo(SheetVisibility.VISIBLE);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("홍길동 & 테스트");
            assertThat(sheet.getRow(1).getCell(1).getNumericCellValue()).isEqualTo(12);
            assertThat(sheet.getColumnWidth(0)).isEqualTo(before.getSheetAt(0).getColumnWidth(0));
            assertThat(sheet.getRow(0).getCell(0).getCellStyle().getFillForegroundColorColor().getARGBHex())
                    .isEqualTo(before.getSheetAt(0).getRow(0).getCell(0).getCellStyle().getFillForegroundColorColor().getARGBHex());
            assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();
            assertThat(sheet.getCTWorksheet().getAutoFilter().getRef()).isEqualTo("A1:B2");
            assertThat(after.getProperties().getCustomProperties().getUnderlyingProperties().sizeOfPropertyArray()).isEqualTo(3);
        }
    }

    @Test
    void acceptsExistingAdminAndMemberWorkbookGenerators() throws Exception {
        AdminService admins = new AdminService();
        ReflectionTestUtils.setField(admins, "adminAccountRepository", mock(AdminAccountRepository.class));
        ReflectionTestUtils.setField(admins, "personalDataProperties", PersonalDataTestSupport.properties());
        MemberService members = new MemberService();
        ReflectionTestUtils.setField(members, "memberRepository", mock(MemberRepository.class));
        ReflectionTestUtils.setField(members, "personalDataProperties", PersonalDataTestSupport.properties());

        ExcelExportResult adminExport = ExcelDownloadTrace.attach(
                admins.createAdminsXlsx(1L, null), 41L, ExcelExportType.ADMIN_ACCOUNTS);
        ExcelExportResult memberExport = ExcelDownloadTrace.attach(
                members.createMembersXlsx(1L, null, null, null, null), 42L, ExcelExportType.MEMBERS);
        try (XSSFWorkbook adminWorkbook = open(adminExport.content());
             XSSFWorkbook memberWorkbook = open(memberExport.content())) {
            assertTrace(adminWorkbook, "41", ExcelExportType.ADMIN_ACCOUNTS);
            assertTrace(memberWorkbook, "42", ExcelExportType.MEMBERS);
        }
    }

    @Test
    void retainsBothReferencesAfterEditingCellsAndResaving() throws Exception {
        byte[] traced = ExcelDownloadTrace.attach(source(), 31L, ExcelExportType.MEMBERS).content();
        byte[] saved;
        try (XSSFWorkbook workbook = open(traced); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            // Clear the existing inline string before replacing it through POI.
            var editedCell = workbook.getSheetAt(0).getRow(1).getCell(0);
            editedCell.setBlank();
            editedCell.setCellValue("수정한 이름");
            workbook.getSheetAt(0).getRow(1).createCell(2).setCellFormula("B2*2");
            workbook.write(output);
            saved = output.toByteArray();
        }
        try (XSSFWorkbook reopened = open(saved)) {
            assertTrace(reopened, "31", ExcelExportType.MEMBERS);
            assertThat(reopened.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()).isEqualTo("수정한 이름");
            assertThat(reopened.getSheetAt(0).getRow(1).getCell(2).getCellFormula()).isEqualTo("B2*2");
        }
    }

    @Test
    void propertySurvivesEvenIfHiddenSheetIsRemoved() throws Exception {
        byte[] traced = ExcelDownloadTrace.attach(source(), 31L, ExcelExportType.MEMBERS).content();
        try (XSSFWorkbook workbook = open(traced); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.removeSheetAt(workbook.getSheetIndex(ExcelDownloadTrace.SHEET_NAME));
            workbook.write(output);
            try (XSSFWorkbook reopened = open(output.toByteArray())) {
                assertThat(reopened.getProperties().getCustomProperties()
                        .getProperty(ExcelDownloadTrace.LOG_ID_PROPERTY).getLpwstr()).isEqualTo("31");
            }
        }
    }

    @Test
    void refusesToOverwriteExistingTrace() {
        ExcelExportResult traced = ExcelDownloadTrace.attach(source(), 31L, ExcelExportType.MEMBERS);
        assertThatThrownBy(() -> ExcelDownloadTrace.attach(traced, 32L, ExcelExportType.MEMBERS))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("이미 존재");
    }

    @Test
    void supportsEmptyExports() throws Exception {
        ExcelExportResult source = new ExcelExportResult(
                XlsxWorkbookWriter.createWorkbook("회원", List.of("이름"), List.of()), 0, "members.xlsx");
        ExcelExportResult result = ExcelDownloadTrace.attach(source, 31L, ExcelExportType.MEMBERS);
        assertThat(result.rowCount()).isZero();
        try (XSSFWorkbook workbook = open(result.content())) {
            assertTrace(workbook, "31", ExcelExportType.MEMBERS);
            assertThat(workbook.getSheetAt(0).getPhysicalNumberOfRows()).isEqualTo(1);
        }
    }

    private void assertTrace(XSSFWorkbook workbook, String logId, ExcelExportType type) {
        assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
        var trace = workbook.getSheet(ExcelDownloadTrace.SHEET_NAME);
        assertThat(trace).isNotNull();
        assertThat(workbook.getSheetVisibility(workbook.getSheetIndex(trace))).isEqualTo(SheetVisibility.VERY_HIDDEN);
        assertThat(trace.getRow(0).getCell(1).getStringCellValue()).isEqualTo("1");
        assertThat(trace.getRow(1).getCell(1).getStringCellValue()).isEqualTo(logId);
        assertThat(trace.getRow(2).getCell(1).getStringCellValue()).isEqualTo(type.name());
        assertThat(trace.getPhysicalNumberOfRows()).isEqualTo(3);
        var properties = workbook.getProperties().getCustomProperties();
        assertThat(properties.getProperty(ExcelDownloadTrace.LOG_ID_PROPERTY).getLpwstr()).isEqualTo(logId);
        assertThat(properties.getProperty(ExcelDownloadTrace.TYPE_PROPERTY).getLpwstr()).isEqualTo(type.name());
        assertThat(properties.getProperty(ExcelDownloadTrace.VERSION_PROPERTY).getLpwstr()).isEqualTo("1");
    }

    private ExcelExportResult source() {
        return new ExcelExportResult(XlsxWorkbookWriter.createWorkbook(
                "회원", List.of("이름", "건수"), List.of(List.of("홍길동 & 테스트", 12))
        ), 1, "members.xlsx");
    }

    private XSSFWorkbook open(byte[] bytes) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }
}
