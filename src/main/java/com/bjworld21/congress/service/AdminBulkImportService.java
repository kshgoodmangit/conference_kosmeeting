package com.bjworld21.congress.service;

import com.bjworld21.congress.dto.AdminBulkImportResponse;
import com.bjworld21.congress.dto.AdminCreateRequest;
import com.bjworld21.congress.dto.ReviewerProfileRequest;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AdminBulkImportService {
    private static final long MAX_FILE_BYTES = 5L * 1024L * 1024L;
    private static final int MAX_DATA_ROWS = 1_000;

    private final AdminService adminService;

    public AdminBulkImportService(AdminService adminService) {
        this.adminService = adminService;
    }

    /**
     * 일괄 처리 자체에는 트랜잭션을 적용하지 않는다. AdminService#create 호출마다
     * 별도 트랜잭션이 시작되므로 한 행의 실패가 다른 성공 행을 롤백하지 않는다.
     */
    public AdminBulkImportResponse importAccounts(Long conferenceSeq, MultipartFile file) {
        validateFile(file);
        List<ImportRow> rows = parse(file);
        List<AdminBulkImportResponse.RowResult> results = new ArrayList<>();
        Set<String> fileAdminIds = new HashSet<>();
        int successCount = 0;

        for (ImportRow row : rows) {
            String normalizedId = row.adminId().toLowerCase(Locale.ROOT);
            if (!normalizedId.isBlank() && !fileAdminIds.add(normalizedId)) {
                results.add(failure(row, "엑셀 파일 안에서 아이디가 중복되었습니다."));
                continue;
            }

            try {
                AdminCreateRequest request = toCreateRequest(row);
                adminService.create(conferenceSeq, request);
                successCount++;
                results.add(new AdminBulkImportResponse.RowResult(
                        row.rowNumber(), row.adminId(), true, "등록되었습니다."
                ));
            } catch (IllegalArgumentException exception) {
                results.add(failure(row, exception.getMessage()));
            } catch (RuntimeException exception) {
                results.add(failure(row, "계정 저장 중 오류가 발생했습니다."));
            }
        }

        return new AdminBulkImportResponse(
                rows.size(), successCount, rows.size() - successCount, List.copyOf(results)
        );
    }

    private AdminCreateRequest toCreateRequest(ImportRow row) {
        validateLength(row.adminId(), 255, "아이디");
        if (row.adminId().isBlank()) {
            throw new IllegalArgumentException("아이디는 필수입니다.");
        }
        validateLength(row.adminName(), 255, "관리자명");
        if (row.adminName().isBlank()) {
            throw new IllegalArgumentException("관리자명은 필수입니다.");
        }

        String role = row.role().toLowerCase(Locale.ROOT);
        if (role.isBlank()) {
            throw new IllegalArgumentException("권한은 필수입니다.");
        }
        if (!"admin".equals(role) && !"reviewer".equals(role) && !"maintenance".equals(role)) {
            throw new IllegalArgumentException("권한은 admin, reviewer 또는 maintenance만 입력할 수 있습니다.");
        }

        ReviewerProfileRequest reviewerProfile = null;
        if ("reviewer".equals(role)) {
            reviewerProfile = ReviewerProfileRequest.builder()
                    .isUsed(row.isUsed().isBlank() ? "Y" : row.isUsed())
                    .expertiseCodes(parseExpertiseCodes(row.expertiseCodes()))
                    .build();
        }

        return AdminCreateRequest.builder()
                .email(row.adminId())
                .password(row.adminId() + "12#$")
                .adminName(row.adminName())
                .affiliation(emptyToNull(row.affiliation()))
                .department(emptyToNull(row.department()))
                .positionTitle(emptyToNull(row.positionTitle()))
                .phoneNumber(emptyToNull(row.phoneNumber()))
                .contactEmail(emptyToNull(row.contactEmail()))
                .role(role)
                .reviewerProfile(reviewerProfile)
                .build();
    }

    private List<String> parseExpertiseCodes(String value) {
        if (value.isBlank()) {
            return List.of();
        }

        Set<String> codes = new LinkedHashSet<>();
        for (String token : value.split(",")) {
            String code = token.trim();
            if (code.isEmpty() || !code.matches("[1-9][0-9]*")) {
                throw new IllegalArgumentException("심사 전문분야 코드는 숫자로 입력하고 여러 개는 쉼표로 구분해 주세요.");
            }
            codes.add(code);
        }
        return List.copyOf(codes);
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
                throw new IllegalArgumentException("첫 번째 시트에서 아이디, 관리자명, 권한 헤더를 찾을 수 없습니다.");
            }

            for (int rowIndex = columns.rowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                ImportRow imported = new ImportRow(
                        rowIndex + 1,
                        cellValue(row, columns.adminIdColumn(), formatter),
                        cellValue(row, columns.adminNameColumn(), formatter),
                        cellValue(row, columns.roleColumn(), formatter),
                        cellValue(row, columns.affiliationColumn(), formatter),
                        cellValue(row, columns.departmentColumn(), formatter),
                        cellValue(row, columns.positionTitleColumn(), formatter),
                        cellValue(row, columns.phoneNumberColumn(), formatter),
                        cellValue(row, columns.contactEmailColumn(), formatter),
                        cellValue(row, columns.isUsedColumn(), formatter),
                        cellValue(row, columns.expertiseCodesColumn(), formatter)
                );
                if (imported.isBlank()) {
                    continue;
                }
                rows.add(imported);
                if (rows.size() > MAX_DATA_ROWS) {
                    throw new IllegalArgumentException("한 번에 최대 1,000개 계정까지 등록할 수 있습니다.");
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
            throw new IllegalArgumentException("등록할 관리자 계정이 없습니다.");
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

            Integer adminId = null;
            Integer adminName = null;
            Integer role = null;
            Integer affiliation = null;
            Integer department = null;
            Integer positionTitle = null;
            Integer phoneNumber = null;
            Integer contactEmail = null;
            Integer isUsed = null;
            Integer expertiseCodes = null;

            for (Cell cell : row) {
                String header = normalizeHeader(formatter.formatCellValue(cell));
                int column = cell.getColumnIndex();
                if ("아이디".equals(header) || "관리자아이디".equals(header) || "adminid".equals(header)) adminId = column;
                if ("관리자명".equals(header) || "이름".equals(header) || "adminname".equals(header)) adminName = column;
                if ("권한".equals(header) || "role".equals(header)) role = column;
                if ("소속기관".equals(header) || "소속".equals(header) || "affiliation".equals(header)) affiliation = column;
                if ("부서·학과·진료과".equals(header) || "부서학과진료과".equals(header) || "department".equals(header)) department = column;
                if ("직위".equals(header) || "positiontitle".equals(header)) positionTitle = column;
                if ("연락처".equals(header) || "phonenumber".equals(header)) phoneNumber = column;
                if ("이메일".equals(header) || "심사연락용이메일".equals(header) || "contactemail".equals(header)) contactEmail = column;
                if ("심사배정사용여부".equals(header) || "isused".equals(header)) isUsed = column;
                if ("심사전문분야코드".equals(header) || "expertisecodes".equals(header)) expertiseCodes = column;
            }

            if (adminId != null && adminName != null && role != null) {
                return new HeaderColumns(
                        rowIndex, adminId, adminName, role, affiliation, department,
                        positionTitle, phoneNumber, contactEmail, isUsed, expertiseCodes
                );
            }
        }
        return null;
    }

    private String normalizeHeader(String value) {
        return value.trim()
                .replace("*", "")
                .replace(" ", "")
                .toLowerCase(Locale.ROOT);
    }

    private String cellValue(Row row, Integer columnIndex, DataFormatter formatter) {
        if (row == null || columnIndex == null) {
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
            throw new IllegalArgumentException("관리자 일괄등록 파일은 5MB를 초과할 수 없습니다.");
        }
        String filename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        if (filename.contains("..") || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException(".xlsx 형식의 엑셀 파일만 등록할 수 있습니다.");
        }
    }

    private void validateLength(String value, int maxLength, String fieldName) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "은(는) " + maxLength + "자 이하로 입력해 주세요.");
        }
    }

    private String emptyToNull(String value) {
        return value.isBlank() ? null : value;
    }

    private AdminBulkImportResponse.RowResult failure(ImportRow row, String message) {
        return new AdminBulkImportResponse.RowResult(row.rowNumber(), row.adminId(), false, message);
    }

    private record HeaderColumns(
            int rowIndex,
            int adminIdColumn,
            int adminNameColumn,
            int roleColumn,
            Integer affiliationColumn,
            Integer departmentColumn,
            Integer positionTitleColumn,
            Integer phoneNumberColumn,
            Integer contactEmailColumn,
            Integer isUsedColumn,
            Integer expertiseCodesColumn
    ) {
    }

    private record ImportRow(
            int rowNumber,
            String adminId,
            String adminName,
            String role,
            String affiliation,
            String department,
            String positionTitle,
            String phoneNumber,
            String contactEmail,
            String isUsed,
            String expertiseCodes
    ) {
        private boolean isBlank() {
            return adminId.isBlank() && adminName.isBlank() && role.isBlank()
                    && affiliation.isBlank() && department.isBlank() && positionTitle.isBlank()
                    && phoneNumber.isBlank() && contactEmail.isBlank() && isUsed.isBlank()
                    && expertiseCodes.isBlank();
        }
    }
}
