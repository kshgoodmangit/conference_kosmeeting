package com.bjworld21.conference.service;

import com.bjworld21.conference.dto.ProgramBulkImportResponse;
import com.bjworld21.conference.dto.ProgramItemPersonRequest;
import com.bjworld21.conference.dto.ProgramItemRequest;
import com.bjworld21.conference.dto.ProgramManagementResponse;
import com.bjworld21.conference.entity.Country;
import com.bjworld21.conference.entity.ProgramDay;
import com.bjworld21.conference.entity.ProgramItem;
import com.bjworld21.conference.entity.ProgramRoom;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class ProgramBulkImportService {
    private static final long MAX_FILE_BYTES = 5L * 1024L * 1024L;
    private static final int MAX_DATA_ROWS = 500;
    private static final DateTimeFormatter DATE_FORMATTER = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, 4)
            .appendLiteral('-')
            .appendValue(ChronoField.MONTH_OF_YEAR, 1, 2, java.time.format.SignStyle.NOT_NEGATIVE)
            .appendLiteral('-')
            .appendValue(ChronoField.DAY_OF_MONTH, 1, 2, java.time.format.SignStyle.NOT_NEGATIVE)
            .toFormatter(Locale.ROOT);
    private static final DateTimeFormatter TIME_FORMATTER = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.HOUR_OF_DAY, 1, 2, java.time.format.SignStyle.NOT_NEGATIVE)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .optionalStart()
            .appendLiteral(':')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .optionalEnd()
            .toFormatter(Locale.ROOT);

    private final ProgramService programService;

    public ProgramBulkImportService(ProgramService programService) {
        this.programService = programService;
    }

    /** 각 행은 ProgramService#createItem의 개별 트랜잭션으로 처리하여 부분 성공을 허용한다. */
    public ProgramBulkImportResponse importItems(Long conferenceSeq, MultipartFile file) {
        validateFile(file);
        List<ImportRow> rows = parse(file);
        ImportContext context = new ImportContext(programService.getManagementData(conferenceSeq));
        List<ProgramBulkImportResponse.RowResult> results = new ArrayList<>();

        rows.stream()
                .sorted(Comparator.comparing((ImportRow row) -> !isSessionValue(row.itemType()))
                        .thenComparingInt(ImportRow::rowNumber))
                .forEach(row -> results.add(importRow(conferenceSeq, row, context)));

        results.sort(Comparator.comparingInt(ProgramBulkImportResponse.RowResult::rowNumber));
        int successCount = (int) results.stream().filter(result -> "SUCCESS".equals(result.status())).count();
        int skippedCount = (int) results.stream().filter(result -> "SKIPPED".equals(result.status())).count();
        int failureCount = results.size() - successCount - skippedCount;
        return new ProgramBulkImportResponse(
                results.size(), successCount, skippedCount, failureCount, List.copyOf(results)
        );
    }

    private ProgramBulkImportResponse.RowResult importRow(Long conferenceSeq, ImportRow row, ImportContext context) {
        try {
            ResolvedImport resolved = resolve(row, context);
            if (context.duplicateKeys.contains(resolved.duplicateKey())) {
                return result(row, "SKIPPED", "같은 날짜·룸·시작시간·제목의 프로그램이 이미 등록되어 있습니다.");
            }

            ProgramItem saved = programService.createItem(conferenceSeq, resolved.request());
            context.duplicateKeys.add(resolved.duplicateKey());
            context.items.add(saved);
            return result(row, "SUCCESS", "등록되었습니다.");
        } catch (IllegalArgumentException exception) {
            return result(row, "FAILED", exception.getMessage());
        } catch (RuntimeException exception) {
            return result(row, "FAILED", "프로그램 저장 중 오류가 발생했습니다.");
        }
    }

    private ResolvedImport resolve(ImportRow row, ImportContext context) {
        LocalDate eventDate = parseDate(row.eventDate());
        ProgramDay day = context.days.stream()
                .filter(candidate -> eventDate.equals(candidate.getEventDate()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("등록된 행사 일자를 찾을 수 없습니다."));
        String scopeType = parseScopeType(row.scopeType());
        ProgramRoom room = resolveRoom(row.roomCode(), scopeType, context.rooms);
        String itemType = parseItemType(row.itemType());
        LocalTime startTime = parseTime(row.startTime(), "시작 시간");
        LocalTime endTime = parseTime(row.endTime(), "종료 시간");
        String title = required(row.title(), "프로그램명 또는 발표 제목은 필수입니다.");
        Long parentSeq = resolveParent(row, day, room, scopeType, itemType, context.items);

        ProgramItemRequest request = ProgramItemRequest.builder()
                .programDaySeq(day.getSeq())
                .roomSeq(room == null ? null : room.getSeq())
                .parentSeq(parentSeq)
                .scopeType(scopeType)
                .itemType(itemType)
                .startTime(startTime)
                .endTime(endTime)
                .title(title)
                .subtitle(emptyToNull(row.subtitle()))
                .notes(emptyToNull(row.notes()))
                .rowStyle(parseRowStyle(row.rowStyle()))
                .sortOrder(parseSortOrder(row.sortOrder()))
                .enabled(parseEnabled(row.enabled()))
                .people(parsePeople(row, context.countries))
                .build();

        String duplicateKey = String.join("|",
                String.valueOf(day.getSeq()), scopeType,
                room == null ? "" : String.valueOf(room.getSeq()),
                startTime.toString(), normalizeKey(title)
        );
        return new ResolvedImport(request, duplicateKey);
    }

    private ProgramRoom resolveRoom(String roomCode, String scopeType, List<ProgramRoom> rooms) {
        if ("ALL_ROOMS".equals(scopeType)) {
            return null;
        }
        String code = required(roomCode, "룸별 프로그램은 룸 코드를 입력해야 합니다.");
        return rooms.stream()
                .filter(room -> code.equalsIgnoreCase(room.getRoomCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("룸 코드 '" + code + "'에 해당하는 룸을 찾을 수 없습니다."));
    }

    private Long resolveParent(
            ImportRow row,
            ProgramDay day,
            ProgramRoom room,
            String scopeType,
            String itemType,
            List<ProgramItem> items
    ) {
        String parentTitle = row.parentSessionTitle().trim();
        String parentStartValue = row.parentSessionStartTime().trim();
        if (parentTitle.isEmpty()) {
            if (!parentStartValue.isEmpty()) {
                throw new IllegalArgumentException("상위 세션 시작시간을 입력하려면 상위 세션명을 함께 입력해야 합니다.");
            }
            return null;
        }
        if ("SESSION".equals(itemType)) {
            throw new IllegalArgumentException("세션에는 상위 세션을 지정할 수 없습니다.");
        }

        LocalTime parentStart = parentStartValue.isEmpty() ? null : parseTime(parentStartValue, "상위 세션 시작시간");
        List<ProgramItem> candidates = items.stream()
                .filter(item -> "SESSION".equals(item.getItemType()) && item.getParentSeq() == null)
                .filter(item -> Objects.equals(day.getSeq(), item.getProgramDaySeq()))
                .filter(item -> scopeType.equals(item.getScopeType()))
                .filter(item -> Objects.equals(room == null ? null : room.getSeq(), item.getRoomSeq()))
                .filter(item -> parentTitle.equalsIgnoreCase(item.getTitle()))
                .filter(item -> parentStart == null || parentStart.equals(item.getStartTime()))
                .toList();
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("같은 일자와 룸에서 상위 세션을 찾을 수 없습니다.");
        }
        if (candidates.size() > 1) {
            throw new IllegalArgumentException("같은 이름의 상위 세션이 여러 개입니다. 상위 세션 시작시간을 입력해 주세요.");
        }
        return candidates.get(0).getSeq();
    }

    private List<ProgramItemPersonRequest> parsePeople(ImportRow row, List<Country> countries) {
        List<ProgramItemPersonRequest> people = new ArrayList<>();
        appendPeople(people, "ORGANIZER", row.organizers(), countries);
        appendPeople(people, "SPEAKER", row.speakers(), countries);
        appendPeople(people, "CHAIR", row.chairs(), countries);
        return List.copyOf(people);
    }

    private void appendPeople(
            List<ProgramItemPersonRequest> target,
            String roleType,
            String value,
            List<Country> countries
    ) {
        if (value.isBlank()) return;
        int sortOrder = 0;
        for (String rawLine : value.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\s*\\|\\s*", -1);
            if (parts.length > 3) {
                throw new IllegalArgumentException("담당자는 '이름 | 소속 | 국가코드' 형식으로 입력해 주세요.");
            }
            String personName = required(parts[0], "담당자 이름은 필수입니다.");
            String affiliation = parts.length > 1 ? emptyToNull(parts[1]) : null;
            Long countrySeq = parts.length > 2 ? resolveCountry(parts[2], countries) : null;
            target.add(ProgramItemPersonRequest.builder()
                    .roleType(roleType)
                    .countrySeq(countrySeq)
                    .affiliation(affiliation)
                    .personName(personName)
                    .sortOrder(sortOrder)
                    .enabled(true)
                    .build());
            sortOrder += 10;
        }
    }

    private Long resolveCountry(String value, List<Country> countries) {
        String key = value.trim();
        if (key.isEmpty()) return null;
        return countries.stream()
                .filter(country -> equalsIgnoreCase(key, country.getIsoAlpha2())
                        || equalsIgnoreCase(key, country.getIsoAlpha3())
                        || equalsIgnoreCase(key, country.getCountryName())
                        || equalsIgnoreCase(key, country.getCountryNameKo()))
                .map(Country::getSeq)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("국가 '" + key + "'를 찾을 수 없습니다."));
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return right != null && left.equalsIgnoreCase(right.trim());
    }

    private String parseScopeType(String value) {
        return switch (normalizeKey(required(value, "적용 범위는 필수입니다."))) {
            case "room", "룸별", "룸별프로그램" -> "ROOM";
            case "allrooms", "전체룸공통", "공통" -> "ALL_ROOMS";
            default -> throw new IllegalArgumentException("적용 범위는 '룸별' 또는 '전체 룸 공통'으로 입력해 주세요.");
        };
    }

    private String parseItemType(String value) {
        return switch (normalizeKey(required(value, "프로그램 유형은 필수입니다."))) {
            case "session", "세션" -> "SESSION";
            case "presentation", "발표" -> "PRESENTATION";
            case "abstractpresentation", "초록발표" -> "ABSTRACT_PRESENTATION";
            case "plenary", "기조초청강연" -> "PLENARY";
            case "ceremony", "공식행사" -> "CEREMONY";
            case "break", "휴식" -> "BREAK";
            case "meal", "식사" -> "MEAL";
            case "registration", "등록" -> "REGISTRATION";
            case "social", "소셜프로그램" -> "SOCIAL";
            case "other", "기타" -> "OTHER";
            default -> throw new IllegalArgumentException("지원하지 않는 프로그램 유형입니다.");
        };
    }

    private boolean isSessionValue(String value) {
        String normalized = normalizeKey(value);
        return "session".equals(normalized) || "세션".equals(normalized);
    }

    private String parseRowStyle(String value) {
        if (value.isBlank()) return "DEFAULT";
        return switch (normalizeKey(value)) {
            case "default", "기본" -> "DEFAULT";
            case "section", "세션강조" -> "SECTION";
            case "highlight", "주요일정강조" -> "HIGHLIGHT";
            case "muted", "보조일정" -> "MUTED";
            default -> throw new IllegalArgumentException("지원하지 않는 행 스타일입니다.");
        };
    }

    private Boolean parseEnabled(String value) {
        if (value.isBlank()) return true;
        return switch (normalizeKey(value)) {
            case "y", "yes", "true", "1", "사용" -> true;
            case "n", "no", "false", "0", "미사용" -> false;
            default -> throw new IllegalArgumentException("사용 여부는 Y 또는 N으로 입력해 주세요.");
        };
    }

    private Integer parseSortOrder(String value) {
        if (value.isBlank()) return 0;
        try {
            int parsed = new BigDecimal(value.replace(",", "")).intValueExact();
            if (parsed < 0) throw new NumberFormatException();
            return parsed;
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException("표시 순서는 0 이상의 정수로 입력해 주세요.");
        }
    }

    private LocalDate parseDate(String value) {
        String normalized = required(value, "행사 일자는 필수입니다.")
                .replace(" ", "")
                .replace('.', '-')
                .replace('/', '-');
        while (normalized.endsWith("-")) normalized = normalized.substring(0, normalized.length() - 1);
        try {
            return LocalDate.parse(normalized, DATE_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("행사 일자는 yyyy-MM-dd 형식으로 입력해 주세요.");
        }
    }

    private LocalTime parseTime(String value, String label) {
        try {
            return LocalTime.parse(required(value, label + "은(는) 필수입니다."), TIME_FORMATTER).withSecond(0);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(label + "은(는) HH:mm 형식으로 입력해 주세요.");
        }
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
                throw new IllegalArgumentException("첫 번째 시트에서 필수 프로그램 헤더를 찾을 수 없습니다.");
            }
            for (int rowIndex = columns.rowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                ImportRow imported = new ImportRow(
                        rowIndex + 1,
                        cellValue(row, columns.eventDate(), formatter),
                        cellValue(row, columns.scopeType(), formatter),
                        cellValue(row, columns.roomCode(), formatter),
                        cellValue(row, columns.itemType(), formatter),
                        cellValue(row, columns.startTime(), formatter),
                        cellValue(row, columns.endTime(), formatter),
                        cellValue(row, columns.title(), formatter),
                        cellValue(row, columns.subtitle(), formatter),
                        cellValue(row, columns.parentSessionTitle(), formatter),
                        cellValue(row, columns.parentSessionStartTime(), formatter),
                        cellValue(row, columns.organizers(), formatter),
                        cellValue(row, columns.speakers(), formatter),
                        cellValue(row, columns.chairs(), formatter),
                        cellValue(row, columns.notes(), formatter),
                        cellValue(row, columns.rowStyle(), formatter),
                        cellValue(row, columns.sortOrder(), formatter),
                        cellValue(row, columns.enabled(), formatter)
                );
                if (imported.isBlank()) continue;
                rows.add(imported);
                if (rows.size() > MAX_DATA_ROWS) {
                    throw new IllegalArgumentException("한 번에 최대 500개 프로그램까지 등록할 수 있습니다.");
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
        if (rows.isEmpty()) throw new IllegalArgumentException("등록할 프로그램이 없습니다.");
        return List.copyOf(rows);
    }

    private HeaderColumns findHeader(Sheet sheet, DataFormatter formatter) {
        for (int rowIndex = 0; rowIndex <= Math.min(sheet.getLastRowNum(), 9); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            Integer eventDate = null;
            Integer scopeType = null;
            Integer roomCode = null;
            Integer itemType = null;
            Integer startTime = null;
            Integer endTime = null;
            Integer title = null;
            Integer subtitle = null;
            Integer parentSessionTitle = null;
            Integer parentSessionStartTime = null;
            Integer organizers = null;
            Integer speakers = null;
            Integer chairs = null;
            Integer notes = null;
            Integer rowStyle = null;
            Integer sortOrder = null;
            Integer enabled = null;
            for (Cell cell : row) {
                String header = normalizeHeader(formatter.formatCellValue(cell));
                int column = cell.getColumnIndex();
                if (Set.of("행사일자", "일자", "eventdate").contains(header)) eventDate = column;
                if (Set.of("적용범위", "범위", "scopetype").contains(header)) scopeType = column;
                if (Set.of("룸코드", "roomcode").contains(header)) roomCode = column;
                if (Set.of("프로그램유형", "유형", "itemtype").contains(header)) itemType = column;
                if (Set.of("시작시간", "starttime").contains(header)) startTime = column;
                if (Set.of("종료시간", "endtime").contains(header)) endTime = column;
                if (Set.of("프로그램명또는발표제목", "프로그램명", "발표제목", "title").contains(header)) title = column;
                if (Set.of("부제목", "subtitle").contains(header)) subtitle = column;
                if (Set.of("상위세션명", "parentsessiontitle").contains(header)) parentSessionTitle = column;
                if (Set.of("상위세션시작시간", "parentsessionstarttime").contains(header)) parentSessionStartTime = column;
                if (Set.of("organizer", "organizers", "기획자").contains(header)) organizers = column;
                if (Set.of("발표자", "speaker", "speakers").contains(header)) speakers = column;
                if (Set.of("좌장또는진행자", "좌장진행자", "chair", "chairs").contains(header)) chairs = column;
                if (Set.of("비고", "notes").contains(header)) notes = column;
                if (Set.of("행스타일", "rowstyle").contains(header)) rowStyle = column;
                if (Set.of("표시순서", "sortorder").contains(header)) sortOrder = column;
                if (Set.of("사용여부", "enabled").contains(header)) enabled = column;
            }
            if (eventDate != null && scopeType != null && itemType != null
                    && startTime != null && endTime != null && title != null) {
                return new HeaderColumns(rowIndex, eventDate, scopeType, roomCode, itemType, startTime, endTime,
                        title, subtitle, parentSessionTitle, parentSessionStartTime, organizers, speakers, chairs,
                        notes, rowStyle, sortOrder, enabled);
            }
        }
        return null;
    }

    private String cellValue(Row row, Integer columnIndex, DataFormatter formatter) {
        if (row == null || columnIndex == null) return "";
        Cell cell = row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private String normalizeHeader(String value) {
        return value.trim().replace("*", "").replace(" ", "").replace("/", "").replace("·", "")
                .toLowerCase(Locale.ROOT);
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().replace("_", "").replace("-", "").replace("·", "")
                .replace(" ", "").toLowerCase(Locale.ROOT);
    }

    private String required(String value, String message) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(message);
        return normalized;
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ProgramBulkImportResponse.RowResult result(ImportRow row, String status, String message) {
        return new ProgramBulkImportResponse.RowResult(row.rowNumber(), row.title(), status, message);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("등록할 엑셀 파일을 선택해 주세요.");
        if (file.getSize() > MAX_FILE_BYTES) throw new IllegalArgumentException("프로그램 일괄등록 파일은 5MB를 초과할 수 없습니다.");
        String filename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        if (filename.contains("..") || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException(".xlsx 형식의 엑셀 파일만 등록할 수 있습니다.");
        }
    }

    private record ResolvedImport(ProgramItemRequest request, String duplicateKey) {
    }

    private static final class ImportContext {
        private final List<ProgramDay> days;
        private final List<ProgramRoom> rooms;
        private final List<Country> countries;
        private final List<ProgramItem> items;
        private final Set<String> duplicateKeys = new HashSet<>();

        private ImportContext(ProgramManagementResponse data) {
            days = data.getDays() == null ? List.of() : data.getDays();
            rooms = data.getRooms() == null ? List.of() : data.getRooms();
            countries = data.getCountries() == null ? List.of() : data.getCountries();
            items = new ArrayList<>(data.getItems() == null ? List.of() : data.getItems());
            for (ProgramItem item : items) {
                duplicateKeys.add(String.join("|",
                        String.valueOf(item.getProgramDaySeq()), item.getScopeType(),
                        item.getRoomSeq() == null ? "" : String.valueOf(item.getRoomSeq()),
                        item.getStartTime().toString(), normalizeStatic(item.getTitle())
                ));
            }
        }

        private static String normalizeStatic(String value) {
            return value == null ? "" : value.trim().replace("_", "").replace("-", "").replace("·", "")
                    .replace(" ", "").toLowerCase(Locale.ROOT);
        }
    }

    private record HeaderColumns(
            int rowIndex,
            int eventDate,
            int scopeType,
            Integer roomCode,
            int itemType,
            int startTime,
            int endTime,
            int title,
            Integer subtitle,
            Integer parentSessionTitle,
            Integer parentSessionStartTime,
            Integer organizers,
            Integer speakers,
            Integer chairs,
            Integer notes,
            Integer rowStyle,
            Integer sortOrder,
            Integer enabled
    ) {
    }

    private record ImportRow(
            int rowNumber,
            String eventDate,
            String scopeType,
            String roomCode,
            String itemType,
            String startTime,
            String endTime,
            String title,
            String subtitle,
            String parentSessionTitle,
            String parentSessionStartTime,
            String organizers,
            String speakers,
            String chairs,
            String notes,
            String rowStyle,
            String sortOrder,
            String enabled
    ) {
        private boolean isBlank() {
            return eventDate.isBlank() && scopeType.isBlank() && roomCode.isBlank() && itemType.isBlank()
                    && startTime.isBlank() && endTime.isBlank() && title.isBlank() && subtitle.isBlank()
                    && parentSessionTitle.isBlank() && parentSessionStartTime.isBlank()
                    && organizers.isBlank() && speakers.isBlank() && chairs.isBlank() && notes.isBlank()
                    && rowStyle.isBlank() && sortOrder.isBlank() && enabled.isBlank();
        }
    }
}
