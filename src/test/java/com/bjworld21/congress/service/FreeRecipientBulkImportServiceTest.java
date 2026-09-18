package com.bjworld21.congress.service;

import com.bjworld21.congress.dto.FreeRecipientData.*;
import com.bjworld21.congress.repository.FreeRecipientRepository;
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

class FreeRecipientBulkImportServiceTest {
    private final FreeRecipientRepository repository = mock(FreeRecipientRepository.class);
    private final FreeRecipientService service = new FreeRecipientService(
            repository, PersonalDataTestSupport.properties()
    );
    private final FreeRecipientBulkImportService importer = new FreeRecipientBulkImportService(service);
    private Workbook template() throws Exception { return WorkbookFactory.create(Files.newInputStream(Path.of("frontend/public/templates/free_recipient_bulk_import_template.xlsx"))); }
    private MockMultipartFile file(Workbook wb) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); wb.write(out);
        return new MockMultipartFile("file", "recipients.xlsx", "application/octet-stream", out.toByteArray());
    }
    private void row(Sheet s, int index, String name, String phone, String email) {
        Row r = s.createRow(index); String[] values = {"테스트소속", name, "연구원", phone, email, "초청", "Y", "허구 테스트"};
        for (int i=0; i<values.length; i++) r.createCell(i).setCellValue(values[i]);
    }
    @Test void templateHasBlankDataHeadersTextPhonesValidationFilterAndGuide() throws Exception {
        try (Workbook wb=template()) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(2);
            Sheet s=wb.getSheetAt(0); assertThat(s.getSheetName()).isEqualTo("무료대상자");
            assertThat(s.getRow(3).getCell(1).getStringCellValue()).isEqualTo("이름 *");
            assertThat(s.getRow(3).getCell(3).getStringCellValue()).isEqualTo("연락처 *");
            assertThat(s.getRow(4).getCell(3).getCellStyle().getDataFormatString()).isEqualTo("@");
            assertThat(s.getPaneInformation().getHorizontalSplitPosition()).isEqualTo((short)4);
            assertThat(s.getDataValidations()).hasSize(2);
            assertThat(((org.apache.poi.xssf.usermodel.XSSFSheet)s).getCTWorksheet().isSetAutoFilter()).isTrue();
            assertThat(wb.getSheet("작성안내").getRow(17).getCell(4).getStringCellValue()).endsWith("@example.com");
            assertThatThrownBy(() -> importer.importRecipients(1L, file(wb))).hasMessageContaining("데이터가 없습니다");
            verifyNoInteractions(repository);
        }
    }
    @Test void partialImportNormalizesDuplicatesAndReportsInvalidAndFormulaRows() throws Exception {
        when(repository.findBySeq(eq(1L), any(), eq(PersonalDataTestSupport.DB_ENC_STRING))).thenReturn(new Recipient());
        doThrow(new DuplicateKeyException("existing")).when(repository).insert(
                eq(1L),
                argThat(r -> "기존샘플".equals(r.getFullName())),
                eq(PersonalDataTestSupport.DB_ENC_STRING)
        );
        try (Workbook wb=template()) {
            Sheet s=wb.getSheetAt(0);
            row(s,4,"샘플 A","010-0000-9001","a@example.com");
            row(s,5," 샘플  A ","+82 10 0000 9001","a@example.com");
            row(s,6,"기존샘플","010-0000-9002","");
            row(s,7,"오류샘플","010-0000-9003","invalid");
            row(s,8,"수식샘플","010-0000-9004",""); s.getRow(8).getCell(2).setCellFormula("1+1");
            row(s,9,"다른샘플","010-0000-9001","a@example.com");
            ImportResult result=importer.importRecipients(1L, file(wb));
            assertThat(result.totalCount()).isEqualTo(6); assertThat(result.successCount()).isEqualTo(2);
            assertThat(result.skippedCount()).isEqualTo(2); assertThat(result.failureCount()).isEqualTo(2);
            assertThat(result.results()).extracting(RowResult::rowNumber).containsExactly(5,6,7,8,9,10);
            verify(repository,times(3)).insert(eq(1L), any(), eq(PersonalDataTestSupport.DB_ENC_STRING));
        }
    }
    @Test void rejectsTooManyRowsBeforeAnyWrite() throws Exception {
        try (Workbook wb=template()) {
            for(int i=4;i<1005;i++) row(wb.getSheetAt(0),i,"샘플"+i,"01000009001","");
            assertThatThrownBy(() -> importer.importRecipients(1L, file(wb))).hasMessageContaining("1,000");
            verifyNoInteractions(repository);
        }
    }
    @Test void rejectsBadHeadersDuplicateHeadersExtensionOversizeAndCorruptFiles() throws Exception {
        try (Workbook wb=new XSSFWorkbook()) {
            row(wb.createSheet(),0,"샘플","01000009001","");
            assertThatThrownBy(() -> importer.importRecipients(1L, file(wb))).hasMessageContaining("헤더");
        }
        try (Workbook wb=template()) {
            wb.getSheetAt(0).getRow(3).createCell(8).setCellValue("이름 *");
            assertThatThrownBy(() -> importer.importRecipients(1L, file(wb))).hasMessageContaining("중복");
        }
        assertThatThrownBy(() -> importer.importRecipients(1L, new MockMultipartFile("file","a.csv","text/csv",new byte[]{1}))).hasMessageContaining(".xlsx");
        assertThatThrownBy(() -> importer.importRecipients(1L, new MockMultipartFile("file","a.xlsx","",new byte[5*1024*1024+1]))).hasMessageContaining("5MB");
        assertThatThrownBy(() -> importer.importRecipients(1L, new MockMultipartFile("file","a.xlsx","",new byte[]{1}))).hasMessageContaining("손상");
        verifyNoInteractions(repository);
    }
}
