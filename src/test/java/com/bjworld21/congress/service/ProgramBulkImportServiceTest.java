package com.bjworld21.congress.service;

import com.bjworld21.congress.dto.ProgramBulkImportResponse;
import com.bjworld21.congress.dto.ProgramItemRequest;
import com.bjworld21.congress.dto.ProgramManagementResponse;
import com.bjworld21.congress.entity.Country;
import com.bjworld21.congress.entity.ProgramDay;
import com.bjworld21.congress.entity.ProgramDayRoom;
import com.bjworld21.congress.entity.ProgramItem;
import com.bjworld21.congress.entity.ProgramRoom;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProgramBulkImportServiceTest {
    private ProgramService programService;
    private ProgramBulkImportService service;

    @BeforeEach
    void setUp() {
        programService = mock(ProgramService.class);
        service = new ProgramBulkImportService(programService);
        when(programService.getManagementData(1L)).thenReturn(managementData());

        AtomicLong sequence = new AtomicLong(100L);
        when(programService.createItem(eq(1L), any(ProgramItemRequest.class))).thenAnswer(invocation -> {
            ProgramItemRequest request = invocation.getArgument(1);
            return ProgramItem.builder()
                    .seq(sequence.getAndIncrement())
                    .programDaySeq(request.getProgramDaySeq())
                    .roomSeq(request.getRoomSeq())
                    .parentSeq(request.getParentSeq())
                    .scopeType(request.getScopeType())
                    .itemType(request.getItemType())
                    .startTime(request.getStartTime())
                    .endTime(request.getEndTime())
                    .title(request.getTitle())
                    .rowStyle(request.getRowStyle())
                    .sortOrder(request.getSortOrder())
                    .enabled(request.getEnabled())
                    .build();
        });
    }

    @Test
    void importsSessionsBeforeChildrenAndReportsPartialResults() throws Exception {
        MockMultipartFile file = workbookFile(new String[][]{
                {"2027-05-09", "룸별", "MAIN_HALL", "발표", "14:30", "14:50", "Patterning the Early Drosophila Embryo", "", "Session 1", "14:30", "", "Shizue Ohsawa | Nagoya University | JP\n정종경 | Seoul Organizing Committee | KR", "", "", "기본", "10", "Y"},
                {"2027-05-09", "룸별", "MAIN_HALL", "세션", "14:30", "17:00", "Session 1", "Developmental Mechanisms", "", "", "심지원 | Seoul Natl. Univ | KR", "", "", "", "세션 강조", "0", "Y"},
                {"2027-05-09", "룸별", "MAIN_HALL", "등록", "10:00", "13:00", "Registration Desk Open", "", "", "", "", "", "", "", "기본", "0", "Y"},
                {"2027-05-09", "룸별", "UNKNOWN", "발표", "15:00", "15:20", "Unknown room item", "", "", "", "", "", "", "", "기본", "0", "Y"}
        });

        ProgramBulkImportResponse response = service.importItems(1L, file);

        assertThat(response.totalCount()).isEqualTo(4);
        assertThat(response.successCount()).isEqualTo(2);
        assertThat(response.skippedCount()).isEqualTo(1);
        assertThat(response.failureCount()).isEqualTo(1);
        assertThat(response.results()).extracting(ProgramBulkImportResponse.RowResult::status)
                .containsExactly("SUCCESS", "SUCCESS", "SKIPPED", "FAILED");
        assertThat(response.results().get(3).message()).contains("UNKNOWN");

        ArgumentCaptor<ProgramItemRequest> captor = ArgumentCaptor.forClass(ProgramItemRequest.class);
        verify(programService, times(2)).createItem(eq(1L), captor.capture());
        List<ProgramItemRequest> requests = captor.getAllValues();
        assertThat(requests.get(0).getItemType()).isEqualTo("SESSION");
        assertThat(requests.get(1).getItemType()).isEqualTo("PRESENTATION");
        assertThat(requests.get(1).getParentSeq()).isEqualTo(100L);
        assertThat(requests.get(1).getPeople()).hasSize(2);
        assertThat(requests.get(1).getPeople()).extracting(person -> person.getCountrySeq())
                .containsExactly(2L, 1L);
    }

    @Test
    void staticTemplateKeepsExpectedSheetsHeadersAndGuideExamples() throws Exception {
        Path templatePath = Path.of("frontend/public/templates/program_bulk_import_template.xlsx");
        try (Workbook workbook = WorkbookFactory.create(Files.newInputStream(templatePath))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
            assertThat(workbook.getSheetAt(0).getSheetName()).isEqualTo("프로그램 일괄등록");
            assertThat(workbook.getSheetAt(1).getSheetName()).isEqualTo("작성안내");
            Row header = workbook.getSheetAt(0).getRow(3);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("행사 일자*");
            assertThat(header.getCell(6).getStringCellValue()).isEqualTo("프로그램명 또는 발표 제목*");
            assertThat(header.getCell(16).getStringCellValue()).isEqualTo("사용 여부");
            assertThat(workbook.getSheetAt(0).getRow(4).getCell(0).getStringCellValue()).isBlank();
            assertThat(workbook.getSheetAt(1).getRow(21).getCell(2).getStringCellValue()).isEqualTo("MAIN_HALL");
        }
    }

    private ProgramManagementResponse managementData() {
        ProgramDay day = ProgramDay.builder().seq(1L).eventDate(LocalDate.of(2027, 5, 9)).dayNumber(1).enabled(true).build();
        ProgramRoom room = ProgramRoom.builder().seq(10L).roomCode("MAIN_HALL").roomName("Main Hall").enabled(true).build();
        ProgramItem existing = ProgramItem.builder()
                .seq(50L).programDaySeq(1L).roomSeq(10L).scopeType("ROOM").itemType("REGISTRATION")
                .startTime(LocalTime.of(10, 0)).endTime(LocalTime.of(13, 0)).title("Registration Desk Open")
                .rowStyle("DEFAULT").sortOrder(0).enabled(true).build();
        return ProgramManagementResponse.builder()
                .days(List.of(day))
                .rooms(List.of(room))
                .dayRooms(List.of(ProgramDayRoom.builder().seq(1L).programDaySeq(1L).roomSeq(10L).enabled(true).build()))
                .items(List.of(existing))
                .people(List.of())
                .countries(List.of(
                        Country.builder().seq(1L).isoAlpha2("KR").isoAlpha3("KOR").countryName("South Korea").countryNameKo("대한민국").build(),
                        Country.builder().seq(2L).isoAlpha2("JP").isoAlpha3("JPN").countryName("Japan").countryNameKo("일본").build()
                ))
                .build();
    }

    private MockMultipartFile workbookFile(String[][] values) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("프로그램 일괄등록");
            Row header = sheet.createRow(3);
            String[] headers = {
                    "행사 일자*", "적용 범위*", "룸 코드", "프로그램 유형*", "시작 시간*", "종료 시간*",
                    "프로그램명 또는 발표 제목*", "부제목", "상위 세션명", "상위 세션 시작시간",
                    "Organizer", "발표자", "좌장 또는 진행자", "비고", "행 스타일", "표시 순서", "사용 여부"
            };
            for (int index = 0; index < headers.length; index++) header.createCell(index).setCellValue(headers[index]);
            for (int rowIndex = 0; rowIndex < values.length; rowIndex++) {
                Row row = sheet.createRow(rowIndex + 4);
                for (int columnIndex = 0; columnIndex < values[rowIndex].length; columnIndex++) {
                    row.createCell(columnIndex).setCellValue(values[rowIndex][columnIndex]);
                }
            }
            workbook.write(output);
            return new MockMultipartFile("file", "programs.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }
}
