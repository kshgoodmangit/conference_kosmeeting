package com.bjworld21.conference.service;

import com.bjworld21.conference.dto.MailAddressBookImportResponse;
import com.bjworld21.conference.entity.MailAddressBook;
import com.bjworld21.conference.entity.MailContact;
import com.bjworld21.conference.repository.MailAddressBookRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailAddressBookImportServiceTest {
    private MailAddressBookService addressBookService;
    private MailAddressBookRepository repository;
    private MailAddressBookImportService service;

    @BeforeEach
    void setUp() {
        addressBookService = mock(MailAddressBookService.class);
        repository = mock(MailAddressBookRepository.class);
        service = new MailAddressBookImportService(
                addressBookService, repository, PersonalDataTestSupport.properties()
        );
        when(addressBookService.requireAddressBook(1L)).thenReturn(MailAddressBook.builder().seq(1L).build());
    }

    @Test
    void importsUniqueRowsAndCountsCreatedAndUpdatedContacts() throws Exception {
        MockMultipartFile file = workbookFile(new String[][]{
                {"홍길동", "국제학회", "hong@example.com"},
                {"김연구", "연구소", "kim@example.com"},
                {"중복", "다른 소속", "HONG@example.com"}
        });
        doAnswer(invocation -> {
            for (MailContact contact : invocation.<java.util.List<MailContact>>getArgument(0)) {
                assertThat(contact.getAddressBookSeq()).isEqualTo(1L);
            }
            return null;
        }).when(repository).upsertImportedContacts(anyList(), anyString());
        when(repository.findContactRefsByNormalizedEmails(any(Long.class), anyList(), anyString())).thenReturn(List.of(
                MailContact.builder().seq(10L).normalizedEmail("hong@example.com").build()
        ));

        MailAddressBookImportResponse result = service.importContacts(1L, file);

        assertThat(result.totalRows()).isEqualTo(3);
        assertThat(result.importedCount()).isEqualTo(2);
        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(result.duplicateCount()).isEqualTo(1);
        assertThat(result.errorCount()).isZero();
    }

    @Test
    void splitsDatabaseWritesIntoBatchesOfOneThousand() throws Exception {
        String[][] rows = new String[1_001][3];
        for (int index = 0; index < rows.length; index++) {
            rows[index] = new String[]{"수신자 " + index, "테스트 기관", "user" + index + "@example.com"};
        }
        MockMultipartFile file = workbookFile(rows);
        when(repository.findContactRefsByNormalizedEmails(any(Long.class), anyList(), anyString())).thenReturn(List.of());

        MailAddressBookImportResponse result = service.importContacts(1L, file);

        assertThat(result.importedCount()).isEqualTo(1_001);
        assertThat(result.createdCount()).isEqualTo(1_001);
        assertThat(result.updatedCount()).isZero();
        verify(repository, times(2)).upsertImportedContacts(anyList(), anyString());
        verify(repository, times(2)).findContactRefsByNormalizedEmails(any(Long.class), anyList(), anyString());
    }

    @Test
    void validationErrorsPreventAllDatabaseWrites() throws Exception {
        MockMultipartFile file = workbookFile(new String[][]{
                {"정상", "학회", "valid@example.com"},
                {"오류", "학회", "not-an-email"}
        });

        MailAddressBookImportResponse result = service.importContacts(1L, file);

        assertThat(result.errorCount()).isEqualTo(1);
        assertThat(result.errors().get(0).rowNumber()).isEqualTo(6);
        assertThat(result.importedCount()).isZero();
        verify(repository, never()).upsertImportedContacts(anyList(), anyString());
        verify(repository, never()).findContactRefsByNormalizedEmails(any(Long.class), anyList(), anyString());
    }

    private MockMultipartFile workbookFile(String[][] values) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("주소록 등록");
            Row header = sheet.createRow(3);
            header.createCell(0).setCellValue("이름");
            header.createCell(1).setCellValue("소속");
            header.createCell(2).setCellValue("이메일");
            for (int index = 0; index < values.length; index++) {
                Row row = sheet.createRow(index + 4);
                row.createCell(0).setCellValue(values[index][0]);
                row.createCell(1).setCellValue(values[index][1]);
                row.createCell(2).setCellValue(values[index][2]);
            }
            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "address-book.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray()
            );
        }
    }

    @Test
    void publishedTemplateImportsPhoneNumbersAndKeepsLeadingZero() throws Exception {
        try (Workbook workbook = openTemplate(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(workbook.getSheetAt(1).getSheetName()).isEqualTo("작성안내");
            assertThat(sheet.getRow(3).getCell(3).getStringCellValue()).isEqualTo("연락처");
            Row row = sheet.createRow(4);
            row.createCell(0).setCellValue("홍길동");
            row.createCell(1).setCellValue("학회");
            row.createCell(2).setCellValue("hong@example.com");
            row.createCell(3).setCellValue("01012345678");
            workbook.write(output);
            doAnswer(invocation -> {
                List<MailContact> contacts = invocation.getArgument(0);
                assertThat(contacts).hasSize(1);
                assertThat(contacts.get(0).getPhoneNumber()).isEqualTo("01012345678");
                return null;
            }).when(repository).upsertImportedContacts(anyList(), anyString());
            MailAddressBookImportResponse result = service.importContacts(1L,
                    new MockMultipartFile("file", "contacts.xlsx", null, output.toByteArray()));
            assertThat(result.errorCount()).isZero();
            assertThat(result.importedCount()).isEqualTo(1);
        }
    }

    @Test
    void rejectsOverlongPhoneNumberWithoutWritingAnyContacts() throws Exception {
        try (Workbook workbook = openTemplate(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Row row = workbook.getSheetAt(0).createRow(4);
            row.createCell(0).setCellValue("홍길동");
            row.createCell(2).setCellValue("hong@example.com");
            row.createCell(3).setCellValue("1".repeat(51));
            workbook.write(output);
            MailAddressBookImportResponse result = service.importContacts(1L,
                    new MockMultipartFile("file", "contacts.xlsx", null, output.toByteArray()));
            assertThat(result.errors()).anySatisfy(error -> {
                assertThat(error.rowNumber()).isEqualTo(5);
                assertThat(error.message()).contains("연락처", "50자");
            });
            verify(repository, never()).upsertImportedContacts(anyList(), anyString());
        }
    }

    private Workbook openTemplate() throws Exception {
        return new XSSFWorkbook(Files.newInputStream(Path.of("frontend/public/templates/mail_address_book_import_template.xlsx")));
    }
}
