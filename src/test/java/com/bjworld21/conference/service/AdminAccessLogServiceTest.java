package com.bjworld21.conference.service;

import com.bjworld21.conference.config.PersonalDataProperties;
import com.bjworld21.conference.dto.AdminLoginResponse;
import com.bjworld21.conference.entity.AdminAccessLog;
import com.bjworld21.conference.repository.AdminAccessLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class AdminAccessLogServiceTest {

    @Mock
    private AdminAccessLogRepository adminAccessLogRepository;

    @Mock
    private PersonalDataProperties personalDataProperties;

    @InjectMocks
    private AdminAccessLogService adminAccessLogService;

    @BeforeEach
    void setUp() {
        lenient().when(personalDataProperties.requireDbEncString())
                .thenReturn(PersonalDataTestSupport.DB_ENC_STRING);
    }

    @Test
    void recordsSuccessfulLoginWithAccountSnapshotAndRequestMetadata() {
        AdminLoginResponse loginResponse = AdminLoginResponse.builder()
                .seq(12L)
                .email("reviewer@example.com")
                .adminName("Reviewer")
                .role("reviewer")
                .build();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("2001:db8::1");
        request.addHeader("User-Agent", "Test Browser");

        adminAccessLogService.recordSuccessfulLogin(loginResponse, request);

        ArgumentCaptor<AdminAccessLog> captor = ArgumentCaptor.forClass(AdminAccessLog.class);
        verify(adminAccessLogRepository).insert(
                captor.capture(), eq(PersonalDataTestSupport.DB_ENC_STRING)
        );
        AdminAccessLog accessLog = captor.getValue();
        assertThat(accessLog.getAdminSeq()).isEqualTo(12L);
        assertThat(accessLog.getAdminEmail()).isEqualTo("reviewer@example.com");
        assertThat(accessLog.getAdminName()).isEqualTo("Reviewer");
        assertThat(accessLog.getAdminRole()).isEqualTo("reviewer");
        assertThat(accessLog.getIpAddress()).isEqualTo("2001:db8::1");
        assertThat(accessLog.getUserAgent()).isEqualTo("Test Browser");
    }

    @Test
    void truncatesRequestMetadataToDatabaseColumnLengths() {
        AdminLoginResponse loginResponse = AdminLoginResponse.builder()
                .seq(1L)
                .email("admin")
                .adminName("Administrator")
                .role("admin")
                .build();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("1".repeat(46));
        request.addHeader("User-Agent", "A".repeat(501));

        adminAccessLogService.recordSuccessfulLogin(loginResponse, request);

        ArgumentCaptor<AdminAccessLog> captor = ArgumentCaptor.forClass(AdminAccessLog.class);
        verify(adminAccessLogRepository).insert(
                captor.capture(), eq(PersonalDataTestSupport.DB_ENC_STRING)
        );
        assertThat(captor.getValue().getIpAddress()).hasSize(45);
        assertThat(captor.getValue().getUserAgent()).hasSize(500);
    }
}
