package com.bjworld21.congress.service;

import com.bjworld21.congress.config.PersonalDataProperties;
import com.bjworld21.congress.dto.MailAddressBookImportResponse;
import com.bjworld21.congress.entity.MailContact;
import com.bjworld21.congress.repository.MailAddressBookRepository;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class MailAddressBookImportService {
    private static final long MAX_FILE_BYTES = 5L * 1024L * 1024L;
    private static final int MAX_DATA_ROWS = 10_000;
    private static final int DB_BATCH_SIZE = 1_000;
    private static final int MAX_REPORTED_ERRORS = 100;
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?(?:\\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+$",
            Pattern.CASE_INSENSITIVE
    );

    private final MailAddressBookService addressBookService;
    private final MailAddressBookRepository repository;
    private final PersonalDataProperties personalDataProperties;

    public MailAddressBookImportService(
            MailAddressBookService addressBookService,
            MailAddressBookRepository repository,
            PersonalDataProperties personalDataProperties
    ) {
        this.addressBookService = addressBookService;
        this.repository = repository;
        this.personalDataProperties = personalDataProperties;
    }

    @Transactional
    public MailAddressBookImportResponse importContacts(Long addressBookSeq, MultipartFile file) {
        addressBookService.requireAddressBook(addressBookSeq);
        validateFile(file);
        ParseResult parseResult = parse(file);
        if (!parseResult.errors().isEmpty()) {
            return response(parseResult, 0, 0, 0);
        }

        List<MailContact> contacts = parseResult.contacts().values().stream()
                .map(importedContact -> MailContact.builder()
                    .addressBookSeq(addressBookSeq)
                    .email(importedContact.email())
                    .normalizedEmail(importedContact.normalizedEmail())
                    .fullName(importedContact.fullName())
                    .affiliation(importedContact.affiliation())
                    .phoneNumber(importedContact.phoneNumber())
                    .build())
                .toList();

        int createdCount = 0;
        int updatedCount = 0;
        for (int start = 0; start < contacts.size(); start += DB_BATCH_SIZE) {
            List<MailContact> batch = contacts.subList(start, Math.min(start + DB_BATCH_SIZE, contacts.size()));
            List<String> normalizedEmails = batch.stream().map(MailContact::getNormalizedEmail).toList();
            int existingCount = repository.findContactRefsByNormalizedEmails(
                    addressBookSeq, normalizedEmails, dbEncString()
            ).size();
            repository.upsertImportedContacts(batch, dbEncString());
            createdCount += batch.size() - existingCount;
            updatedCount += existingCount;
        }
        return response(parseResult, contacts.size(), createdCount, updatedCount);
    }

    private ParseResult parse(MultipartFile file) {
        DataFormatter formatter = new DataFormatter(Locale.KOREA);
        Map<String, ImportedContact> contacts = new LinkedHashMap<>();
        List<MailAddressBookImportResponse.RowError> errors = new ArrayList<>();
        int totalRows = 0;
        int duplicateCount = 0;

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) {
                return new ParseResult(0, 0, contacts, List.of(error(0, "엑셀에 시트가 없습니다.")));
            }
            Sheet sheet = workbook.getSheetAt(0);
            HeaderColumns columns = findHeader(sheet, formatter);
            if (columns == null) {
                return new ParseResult(0, 0, contacts, List.of(error(0, "첫 번째 시트에서 이름, 소속, 이메일 헤더를 찾을 수 없습니다.")));
            }
            if (sheet.getLastRowNum() - columns.rowIndex() > MAX_DATA_ROWS) {
                return new ParseResult(0, 0, contacts, List.of(error(0, "한 번에 최대 10,000행까지 등록할 수 있습니다.")));
            }

            for (int rowIndex = columns.rowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                String fullName = cellValue(row, columns.fullNameColumn(), formatter);
                String affiliation = cellValue(row, columns.affiliationColumn(), formatter);
                String email = cellValue(row, columns.emailColumn(), formatter);
                String phoneNumber = cellValue(row, columns.phoneNumberColumn(), formatter);
                if (fullName.isBlank() && affiliation.isBlank() && email.isBlank() && phoneNumber.isBlank()) {
                    continue;
                }

                totalRows++;
                validateRow(errors, rowIndex + 1, fullName, affiliation, email, phoneNumber);
                if (fullName.isBlank() || email.isBlank() || !EMAIL_PATTERN.matcher(email).matches()
                        || fullName.length() > 255 || affiliation.length() > 255 || email.length() > 255 || phoneNumber.length() > 50) {
                    continue;
                }

                String normalizedEmail = MailAddressBookService.normalizeEmail(email);
                ImportedContact existing = contacts.putIfAbsent(normalizedEmail,
                        new ImportedContact(fullName, blankToNull(affiliation), email, normalizedEmail, blankToNull(phoneNumber)));
                if (existing != null) {
                    duplicateCount++;
                }
            }
        } catch (EncryptedDocumentException exception) {
            return new ParseResult(0, 0, contacts, List.of(error(0, "암호화된 엑셀 파일은 등록할 수 없습니다.")));
        } catch (IOException | RuntimeException exception) {
            return new ParseResult(0, 0, contacts, List.of(error(0, "손상되었거나 지원하지 않는 엑셀 파일입니다.")));
        }

        if (totalRows == 0 && errors.isEmpty()) {
            errors.add(error(0, "등록할 연락처가 없습니다."));
        }
        return new ParseResult(totalRows, duplicateCount, contacts, List.copyOf(errors));
    }

    private HeaderColumns findHeader(Sheet sheet, DataFormatter formatter) {
        int lastHeaderCandidate = Math.min(sheet.getLastRowNum(), 9);
        for (int rowIndex = 0; rowIndex <= lastHeaderCandidate; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            Integer fullNameColumn = null;
            Integer affiliationColumn = null;
            Integer emailColumn = null;
            int phoneNumberColumn = -1;
            for (Cell cell : row) {
                String header = formatter.formatCellValue(cell).trim().replace(" ", "").replace("*", "").toLowerCase(Locale.ROOT);
                if ("이름".equals(header) || "name".equals(header)) fullNameColumn = cell.getColumnIndex();
                if ("연락처".equals(header) || "전화번호".equals(header) || "phonenumber".equals(header)) phoneNumberColumn = cell.getColumnIndex();
                if ("소속".equals(header) || "affiliation".equals(header)) affiliationColumn = cell.getColumnIndex();
                if ("이메일".equals(header) || "email".equals(header) || "이메일주소".equals(header)) emailColumn = cell.getColumnIndex();
            }
            if (fullNameColumn != null && affiliationColumn != null && emailColumn != null) {
                return new HeaderColumns(rowIndex, fullNameColumn, affiliationColumn, emailColumn, phoneNumberColumn);
            }
        }
        return null;
    }

    private void validateRow(
            List<MailAddressBookImportResponse.RowError> errors,
            int rowNumber,
            String fullName,
            String affiliation,
            String email,
            String phoneNumber
    ) {
        if (phoneNumber.length() > 50) addError(errors, rowNumber, "연락처는 50자 이하로 입력해 주세요.");
        if (fullName.isBlank()) addError(errors, rowNumber, "이름은 필수입니다.");
        else if (fullName.length() > 255) addError(errors, rowNumber, "이름은 255자 이하로 입력해 주세요.");
        if (affiliation.length() > 255) addError(errors, rowNumber, "소속은 255자 이하로 입력해 주세요.");
        if (email.isBlank()) addError(errors, rowNumber, "이메일은 필수입니다.");
        else if (email.length() > 255 || !EMAIL_PATTERN.matcher(email).matches()) {
            addError(errors, rowNumber, "이메일 형식이 올바르지 않습니다.");
        }
    }

    private String cellValue(Row row, int columnIndex, DataFormatter formatter) {
        if (row == null || columnIndex < 0) return "";
        Cell cell = row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("등록할 엑셀 파일을 선택해 주세요.");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("주소록 엑셀 파일은 5MB를 초과할 수 없습니다.");
        }
        String filename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        if (filename.contains("..") || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException(".xlsx 형식의 엑셀 파일만 등록할 수 있습니다.");
        }
    }

    private void addError(List<MailAddressBookImportResponse.RowError> errors, int rowNumber, String message) {
        if (errors.size() < MAX_REPORTED_ERRORS) {
            errors.add(error(rowNumber, message));
        }
    }

    private MailAddressBookImportResponse.RowError error(int rowNumber, String message) {
        return new MailAddressBookImportResponse.RowError(rowNumber, message);
    }

    private MailAddressBookImportResponse response(ParseResult parsed, int imported, int created, int updated) {
        return new MailAddressBookImportResponse(
                parsed.totalRows(), imported, created, updated, parsed.duplicateCount(),
                parsed.errors().size(), parsed.errors()
        );
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record HeaderColumns(int rowIndex, int fullNameColumn, int affiliationColumn, int emailColumn, int phoneNumberColumn) {
    }

    private record ImportedContact(String fullName, String affiliation, String email, String normalizedEmail, String phoneNumber) {
    }

    private record ParseResult(
            int totalRows,
            int duplicateCount,
            Map<String, ImportedContact> contacts,
            List<MailAddressBookImportResponse.RowError> errors
    ) {
    }
    private String dbEncString() {
        return personalDataProperties.requireDbEncString();
    }
}
