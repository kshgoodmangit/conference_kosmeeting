package com.bjworld21.conference.service;

import com.bjworld21.conference.dto.FreeRecipientData.*;
import org.apache.poi.ss.usermodel.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@Service
public class FreeRecipientBulkImportService {
    private final FreeRecipientService service;
    private static final List<String> HEADERS = List.of("소속", "이름", "직책", "연락처", "이메일", "대상구분", "사용여부", "관리자메모");
    private record ParsedRow(int number, Recipient member, String error) {}

    public FreeRecipientBulkImportService(FreeRecipientService service) { this.service = service; }

    // 파싱을 먼저 끝내고, 저장은 별도 서비스의 행별 트랜잭션으로 처리한다.
    public ImportResult importRecipients(Long conferenceSeq, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("엑셀 파일을 선택해 주세요.");
        if (file.getSize() > 5L * 1024 * 1024) throw new IllegalArgumentException("파일은 5MB 이하여야 합니다.");
        if (file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx"))
            throw new IllegalArgumentException(".xlsx 파일만 등록할 수 있습니다.");
        List<ParsedRow> rows = parse(file);
        List<RowResult> results = new ArrayList<>();
        Set<String> licenses = new HashSet<>();
        int success = 0, skipped = 0, failure = 0;
        for (ParsedRow row : rows) {
            String status, message;
            try {
                if (row.error() != null) throw new IllegalArgumentException(row.error());
                service.validate(row.member());
                if (!licenses.add(row.member().getFullName() + "\u0000" + row.member().getNormalizedPhone())) {
                    status = "SKIPPED"; message = "파일 안에서 이름과 연락처가 중복되어 건너뛰었습니다."; skipped++;
                } else {
                    service.save(conferenceSeq, null, row.member());
                    status = "SUCCESS"; message = "등록되었습니다."; success++;
                }
            } catch (DuplicateKeyException exception) {
                status = "SKIPPED"; message = "이미 등록된 이름과 연락처로 기존 정보를 유지합니다."; skipped++;
            } catch (IllegalArgumentException exception) {
                status = "FAILED"; message = exception.getMessage(); failure++;
            } catch (RuntimeException exception) {
                status = "FAILED"; message = "저장 중 오류가 발생했습니다. 해당 행을 다시 시도해 주세요."; failure++;
            }
            results.add(new RowResult(row.number(), row.member().getFullName(), status, message));
        }
        return new ImportResult(rows.size(), success, skipped, failure, List.copyOf(results));
    }

    private List<ParsedRow> parse(MultipartFile file) {
        List<ParsedRow> rows = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (!(workbook instanceof org.apache.poi.xssf.usermodel.XSSFWorkbook))
                throw new IllegalArgumentException("실제 .xlsx 형식의 파일만 등록할 수 있습니다.");
            if (workbook.getNumberOfSheets() == 0) throw new IllegalArgumentException("첫 번째 입력 시트가 필요합니다.");
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.KOREA);
            Map<String, Integer> columns = null;
            int headerRow = -1;
            for (int i = 0; i <= Math.min(9, sheet.getLastRowNum()); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                Map<String, Integer> candidate = new HashMap<>();
                for (Cell cell : row) {
                    String header = formatter.formatCellValue(cell).replace("*", "").replace(" ", "").strip();
                    if (HEADERS.contains(header) && candidate.put(header, cell.getColumnIndex()) != null)
                        throw new IllegalArgumentException("헤더가 중복되었습니다: " + header);
                }
                if (candidate.keySet().containsAll(HEADERS)) { columns = candidate; headerRow = i; break; }
            }
            if (columns == null) throw new IllegalArgumentException("첫 10행 안에 소속, 이름, 직책, 연락처, 이메일, 대상구분, 사용여부, 관리자메모 헤더가 필요합니다.");
            // 생성된 빈 서식 행은 건너뛰되, 실제 데이터 행은 최대 1,000개로 제한한다.
            for (Row row : sheet) {
                if (row.getRowNum() <= headerRow) continue;
                List<String> values = new ArrayList<>();
                String error = null;
                for (String header : HEADERS) {
                    Cell cell = row.getCell(columns.get(header));
                    if (cell != null && (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR))
                        error = "수식 또는 오류 셀은 허용하지 않습니다. 값으로 입력해 주세요.";
                    values.add(cell == null ? "" : formatter.formatCellValue(cell).strip());
                }
                if (values.stream().allMatch(String::isEmpty) && error == null) continue;
                Recipient member = new Recipient();
                member.setAffiliation(values.get(0)); member.setFullName(values.get(1));
                member.setPosition(values.get(2)); member.setPhoneNumber(values.get(3));
                member.setEmail(values.get(4)); member.setRecipientType(values.get(5));
                member.setIsUsed(values.get(6)); member.setAdminMemo(values.get(7));
                rows.add(new ParsedRow(row.getRowNum() + 1, member, error));
                if (rows.size() > 1000) throw new IllegalArgumentException("한 번에 최대 1,000명까지 등록할 수 있습니다.");
            }
        } catch (IllegalArgumentException exception) { throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("암호화되었거나 손상된 엑셀 파일은 등록할 수 없습니다.");
        }
        if (rows.isEmpty()) throw new IllegalArgumentException("등록할 무료 대상자 데이터가 없습니다.");
        return rows;
    }
}
