package com.bjworld21.conference.service;

import com.bjworld21.conference.entity.AdminAccount;
import com.bjworld21.conference.entity.MaintenanceRequest;
import com.bjworld21.conference.repository.AdminAccountRepository;
import com.bjworld21.conference.repository.MaintenanceRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MaintenanceRequestServiceTest {
    private MaintenanceRequestRepository repository;
    private AdminAccountRepository adminAccountRepository;
    private CmsHtmlSanitizer htmlSanitizer;
    private MaintenanceRequestService service;

    @BeforeEach
    void setUp() {
        repository = mock(MaintenanceRequestRepository.class);
        adminAccountRepository = mock(AdminAccountRepository.class);
        htmlSanitizer = mock(CmsHtmlSanitizer.class);
        service = new MaintenanceRequestService(
                repository,
                adminAccountRepository,
                PersonalDataTestSupport.properties(),
                htmlSanitizer,
                mock(UploadStorage.class),
                mock(ApplicationEventPublisher.class)
        );
    }

    @Test
    void maintenanceAccountCanUpdateRequestLikeAdministrator() {
        AdminAccount maintenance = AdminAccount.builder()
                .seq(7L)
                .role("maintenance")
                .status("active")
                .build();
        MaintenanceRequest existing = request(11L, "기존 제목", "<p>기존 내용</p>");
        MaintenanceRequest updated = request(11L, "수정 제목", "<p>수정 내용</p>");

        when(adminAccountRepository.findBySeq(7L, PersonalDataTestSupport.DB_ENC_STRING))
                .thenReturn(maintenance);
        when(repository.findBySeq(3L, 11L, PersonalDataTestSupport.DB_ENC_STRING))
                .thenReturn(existing, updated);
        when(repository.findAttachments(11L)).thenReturn(List.of());
        when(htmlSanitizer.sanitize("<p>수정 내용</p>")).thenReturn("<p>수정 내용</p>");
        when(repository.updateRequest(3L, 11L, "수정 제목", "<p>수정 내용</p>"))
                .thenReturn(1);

        var response = service.update(
                3L, 7L, 11L, " 수정 제목 ", "<p>수정 내용</p>", List.of()
        );

        assertThat(response.getTitle()).isEqualTo("수정 제목");
        assertThat(response.getContent()).isEqualTo("<p>수정 내용</p>");
        verify(repository).updateRequest(3L, 11L, "수정 제목", "<p>수정 내용</p>");
    }

    @Test
    void reviewerCannotUpdateMaintenanceRequest() {
        AdminAccount reviewer = AdminAccount.builder()
                .seq(8L)
                .role("reviewer")
                .status("active")
                .build();
        when(adminAccountRepository.findBySeq(8L, PersonalDataTestSupport.DB_ENC_STRING))
                .thenReturn(reviewer);

        assertThatThrownBy(() -> service.update(
                3L, 8L, 11L, "제목", "<p>내용</p>", List.of()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("해당 작업을 수행할 권한이 없습니다.");
    }

    private MaintenanceRequest request(Long seq, String title, String content) {
        MaintenanceRequest request = new MaintenanceRequest();
        request.setSeq(seq);
        request.setConferenceSeq(3L);
        request.setTitle(title);
        request.setContent(content);
        request.setStatus("REQUESTED");
        request.setRequestedByAdminSeq(7L);
        request.setRequestedByName("담당자");
        request.setAttachmentCount(0);
        return request;
    }
}
