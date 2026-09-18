package com.bjworld21.congress.service;

import com.bjworld21.congress.config.UploadProperties;
import com.bjworld21.congress.dto.AbstractSubmissionResponse;
import com.bjworld21.congress.entity.AbstractSubmissionAttachment;
import com.bjworld21.congress.entity.ConferenceSettings;
import com.bjworld21.congress.repository.AbstractSubmissionRepository;
import com.bjworld21.congress.repository.ConferenceSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbstractPresentationAttachmentServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Mock
    private AbstractSubmissionRepository repository;
    @Mock
    private ConferenceSettingsRepository conferenceSettingsRepository;

    private AbstractPresentationAttachmentService service;

    @BeforeEach
    void setUp() {
        UploadProperties properties = new UploadProperties();
        properties.setBaseDirectory(temporaryDirectory.toString());
        service = new AbstractPresentationAttachmentService(
                repository, conferenceSettingsRepository, new UploadStorage(properties), PersonalDataTestSupport.properties(),
                Clock.system(ZoneId.of("Asia/Seoul"))
        );
    }

    @Test
    void storesApprovedAbstractPresentationFileInMonthlyAbstractDirectory() throws Exception {
        when(repository.findBySeq(1L, 1L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(AbstractSubmissionResponse.builder()
                .seq(1L)
                .status("approved")
                .build());
        when(repository.countAttachments(1L)).thenReturn(0L);
        doAnswer(invocation -> {
            AbstractSubmissionAttachment attachment = invocation.getArgument(0);
            attachment.setSeq(10L);
            return null;
        }).when(repository).insertAttachment(any(AbstractSubmissionAttachment.class));

        var response = service.add(1L, 1L, new MockMultipartFile(
                "file",
                "presentation.pptx",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                new byte[]{1, 2, 3}
        ));

        ArgumentCaptor<AbstractSubmissionAttachment> captor = ArgumentCaptor.forClass(AbstractSubmissionAttachment.class);
        verify(repository).insertAttachment(captor.capture());
        AbstractSubmissionAttachment saved = captor.getValue();
        assertThat(response.getSeq()).isEqualTo(10L);
        assertThat(saved.getSaveFilename()).matches("\\d{6}/[0-9a-f-]+\\.pptx");
        assertThat(Files.readAllBytes(temporaryDirectory.resolve("abstracts").resolve(saved.getSaveFilename())))
                .containsExactly(1, 2, 3);
    }

    @Test
    void rejectsPresentationFileBeforeApproval() {
        when(repository.findBySeq(1L, 1L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(AbstractSubmissionResponse.builder()
                .seq(1L)
                .status("under_review")
                .build());

        assertThatThrownBy(() -> service.add(1L, 1L, new MockMultipartFile(
                "file", "presentation.pdf", "application/pdf", new byte[]{1}
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("승인된 초록");
    }

    @Test
    void rejectsUnsupportedFileExtension() {
        when(repository.findBySeq(1L, 1L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(AbstractSubmissionResponse.builder()
                .seq(1L)
                .status("approved")
                .build());

        assertThatThrownBy(() -> service.add(1L, 1L, new MockMultipartFile(
                "file", "presentation.exe", "application/octet-stream", new byte[]{1}
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PDF, PPT, PPTX");
    }

    @Test
    void letsTheOwnerUploadDuringThePresentationMaterialPeriod() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        when(repository.findBySeq(1L, 1L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(AbstractSubmissionResponse.builder()
                .seq(1L)
                .memberSeq(7L)
                .submissionSource("member")
                .status("approved")
                .build());
        when(conferenceSettingsRepository.findBySeq(1L)).thenReturn(ConferenceSettings.builder()
                .seq(1L)
                .presentationMaterialStartDate(today.minusDays(1))
                .presentationMaterialEndDate(today.plusDays(1))
                .build());

        service.addByMember(1L, 1L, 7L, new MockMultipartFile(
                "file", "presentation.pdf", "application/pdf", new byte[]{1}
        ));

        verify(repository).insertAttachment(any(AbstractSubmissionAttachment.class));
    }

    @Test
    void rejectsAnotherMembersPresentationMaterialUpload() {
        when(repository.findBySeq(1L, 1L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(AbstractSubmissionResponse.builder()
                .seq(1L)
                .memberSeq(8L)
                .submissionSource("member")
                .status("approved")
                .build());

        assertThatThrownBy(() -> service.addByMember(1L, 1L, 7L, new MockMultipartFile(
                "file", "presentation.pdf", "application/pdf", new byte[]{1}
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("본인의 승인된 초록");
    }

    @Test
    void rejectsMemberUploadOutsideThePresentationMaterialPeriod() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        when(repository.findBySeq(1L, 1L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(AbstractSubmissionResponse.builder()
                .seq(1L)
                .memberSeq(7L)
                .submissionSource("member")
                .status("approved")
                .build());
        when(conferenceSettingsRepository.findBySeq(1L)).thenReturn(ConferenceSettings.builder()
                .seq(1L)
                .presentationMaterialStartDate(today.minusDays(2))
                .presentationMaterialEndDate(today.minusDays(1))
                .build());

        assertThatThrownBy(() -> service.addByMember(1L, 1L, 7L, new MockMultipartFile(
                "file", "presentation.pdf", "application/pdf", new byte[]{1}
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("등록기간이 아닙니다");
    }

    @Test
    void letsTheOwnerDeleteOutsideThePresentationMaterialPeriod() {
        when(repository.findBySeq(1L, 1L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(AbstractSubmissionResponse.builder()
                .seq(1L)
                .memberSeq(7L)
                .submissionSource("member")
                .status("approved")
                .build());
        when(repository.findAttachment(1L, 10L)).thenReturn(AbstractSubmissionAttachment.builder()
                .seq(10L)
                .abstractSeq(1L)
                .saveFilename("202609/missing.pdf")
                .build());
        when(repository.deleteAttachment(1L, 10L)).thenReturn(1);

        service.deleteByMember(1L, 1L, 10L, 7L);

        verify(repository).deleteAttachment(1L, 10L);
        verifyNoInteractions(conferenceSettingsRepository);
    }
}
