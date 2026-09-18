package com.bjworld21.congress.service;

import com.bjworld21.congress.entity.AdminAccount;
import com.bjworld21.congress.entity.ExcelDownloadLog;
import com.bjworld21.congress.repository.AdminAccountRepository;
import com.bjworld21.congress.util.XlsxWorkbookWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.List;
import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExcelDownloadAuditServiceTest {
    @Mock
    private ExcelDownloadAuditLogWriter writer;
    @Mock
    private AdminAccountRepository adminAccountRepository;
    @Mock
    private HttpServletRequest servletRequest;

    private ExcelDownloadAuditService service;

    @BeforeEach
    void setUp() {
        service = new ExcelDownloadAuditService(
                writer, adminAccountRepository, new ObjectMapper(), PersonalDataTestSupport.properties()
        );
    }

    @Test
    void rejectsShortReasonBeforeCreatingAuditLog() {
        assertThatThrownBy(() -> service.execute(
                ExcelExportType.MEMBERS,
                1L,
                "업무",
                Map.of(),
                servletRequest,
                () -> new ExcelExportResult(new byte[]{1}, 1, "members.xlsx")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("다운로드 사유를 5자 이상 입력해 주세요.");

        verifyNoInteractions(writer, adminAccountRepository);
    }

    @Test
    void recordsSuccessWithNormalizedReasonAndFileMetadata() throws Exception {
        when(adminAccountRepository.findBySeq(7L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(activeAdmin());
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(servletRequest.getHeader("User-Agent")).thenReturn("test-agent");
        when(writer.start(org.mockito.ArgumentMatchers.any())).thenReturn(31L);
        ExcelExportResult exportResult = validExport();

        AuditedExcelExportResult result = service.execute(
                ExcelExportType.MEMBERS,
                7L,
                "  월간 보고 자료 작성  ",
                Map.of("keyword", "kim"),
                servletRequest,
                () -> exportResult
        );

        ArgumentCaptor<ExcelDownloadLog> captor = ArgumentCaptor.forClass(ExcelDownloadLog.class);
        verify(writer).start(captor.capture());
        assertThat(captor.getValue().getReason()).isEqualTo("월간 보고 자료 작성");
        assertThat(captor.getValue().getFilterJson()).isEqualTo("{\"keyword\":\"kim\"}");
        assertThat(captor.getValue().getAdminEmail()).isEqualTo("admin@example.com");
        verify(writer).succeed(31L, result.exportResult());
        verify(writer, never()).fail(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        assertThat(result.logSeq()).isEqualTo(31L);
        assertThat(result.exportResult().filename()).isEqualTo(exportResult.filename());
        assertThat(result.exportResult().rowCount()).isEqualTo(exportResult.rowCount());
        assertThat(result.exportResult().content()).isNotEqualTo(exportResult.content());
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result.exportResult().content()))) {
            assertThat(workbook.getProperties().getCustomProperties()
                    .getProperty(ExcelDownloadTrace.LOG_ID_PROPERTY).getLpwstr()).isEqualTo("31");
            assertThat(workbook.getSheet(ExcelDownloadTrace.SHEET_NAME).getRow(1).getCell(1).getStringCellValue())
                    .isEqualTo("31");
        }
    }

    @Test
    void allowsMaintenanceAccountToCreateAuditLog() {
        AdminAccount maintenance = activeAdmin();
        maintenance.setRole("maintenance");
        when(adminAccountRepository.findBySeq(7L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(maintenance);
        when(writer.start(org.mockito.ArgumentMatchers.any())).thenReturn(33L);

        service.execute(
                ExcelExportType.MEMBERS,
                7L,
                "유지보수 자료 확인",
                Map.of(),
                servletRequest,
                this::validExport
        );

        verify(writer).succeed(org.mockito.ArgumentMatchers.eq(33L), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void recordsFailureWhenGeneratorThrows() {
        when(adminAccountRepository.findBySeq(7L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(activeAdmin());
        when(writer.start(org.mockito.ArgumentMatchers.any())).thenReturn(32L);

        assertThatThrownBy(() -> service.execute(
                ExcelExportType.ABSTRACTS,
                7L,
                "심사 현황 확인",
                Map.of(),
                servletRequest,
                () -> { throw new IllegalStateException("workbook failure"); }
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("workbook failure");

        verify(writer).fail(32L, "EXPORT_FAILED", "엑셀 파일 생성 중 오류가 발생했습니다.");
    }

    @Test
    void recordsFailureAndDoesNotReturnUnmarkedFileWhenTraceCannotBeInserted() {
        when(adminAccountRepository.findBySeq(7L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(activeAdmin());
        when(writer.start(org.mockito.ArgumentMatchers.any())).thenReturn(34L);

        assertThatThrownBy(() -> service.execute(
                ExcelExportType.MEMBERS, 7L, "회원 자료 확인", Map.of(), servletRequest,
                () -> new ExcelExportResult(new byte[]{1, 2, 3}, 1, "members.xlsx")
        )).isInstanceOf(RuntimeException.class);

        verify(writer).fail(34L, "EXPORT_FAILED", "엑셀 파일 생성 중 오류가 발생했습니다.");
        verify(writer, never()).succeed(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }

    private ExcelExportResult validExport() {
        return new ExcelExportResult(XlsxWorkbookWriter.createWorkbook(
                "회원", List.of("이름"), List.of(List.of("테스트 회원"))
        ), 1, "members.xlsx");
    }

    private AdminAccount activeAdmin() {
        return AdminAccount.builder()
                .seq(7L)
                .email("admin@example.com")
                .adminName("관리자")
                .role("admin")
                .status("active")
                .build();
    }
}
