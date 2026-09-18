package com.bjworld21.congress.service;

import com.bjworld21.congress.dto.AbstractSubmissionAiScope;
import com.bjworld21.congress.dto.AbstractSubmissionAiTool;
import com.bjworld21.congress.dto.AbstractSubmissionAuthor;
import com.bjworld21.congress.dto.AbstractSubmissionInstitution;
import com.bjworld21.congress.dto.AbstractSubmissionRequest;
import com.bjworld21.congress.dto.AbstractSubmissionResponse;
import com.bjworld21.congress.entity.ConferenceSettings;
import com.bjworld21.congress.entity.Member;
import com.bjworld21.congress.repository.AbstractSubmissionRepository;
import com.bjworld21.congress.repository.ConferenceSettingsRepository;
import com.bjworld21.congress.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class AbstractSubmissionServiceTest {
    @Mock
    private AbstractSubmissionRepository repository;
    @Mock
    private ConferenceSettingsRepository conferenceSettingsRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private UploadStorage uploadStorage;
    @Mock
    private AbstractTitleSimilarityService abstractTitleSimilarityService;

    private AbstractSubmissionService service;

    @BeforeEach
    void setUp() {
        service = new AbstractSubmissionService(
                repository, conferenceSettingsRepository, memberRepository, uploadStorage,
                PersonalDataTestSupport.properties(),
                Clock.fixed(Instant.parse("2026-09-14T00:00:00Z"), ZoneId.of("Asia/Seoul")),
                abstractTitleSimilarityService
        );
        lenient().when(conferenceSettingsRepository.findBySeq(1L)).thenReturn(ConferenceSettings.builder()
                .seq(1L)
                .abstractStartDate(LocalDate.of(2026, 9, 1))
                .abstractEndDate(LocalDate.of(2026, 9, 30))
                .build());
        lenient().when(memberRepository.findByEmail(1L, "member@example.com", PersonalDataTestSupport.DB_ENC_STRING))
                .thenReturn(Member.builder().seq(1L).email("member@example.com").build());
        lenient().when(repository.countEnabledPresentationTypeByCode(10L)).thenReturn(1L);
        lenient().when(repository.countEnabledCategoryByCode(20L)).thenReturn(1L);
    }

    @Test
    void requiresPolicyConfirmation() {
        AbstractSubmissionRequest request = baseRequest();
        request.setAiUsage(false);
        request.setPlagiarismPolicyConfirmed(false);

        assertThatThrownBy(() -> service.create(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("policy compliance confirmation");
    }

    @Test
    void requiresToolAndScopeWhenAiWasUsed() {
        AbstractSubmissionRequest request = baseRequest();
        request.setAiUsage(true);
        request.setAiVersionInfo("ChatGPT-5.2");
        request.setPlagiarismPolicyConfirmed(true);
        request.setAiScopes(List.of(AbstractSubmissionAiScope.builder().aiScopeCode(200L).build()));

        assertThatThrownBy(() -> service.create(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one AI tool");
    }

    @Test
    void requiresOtherToolNameAndProviderForEtcCode() {
        AbstractSubmissionRequest request = validAiRequest();
        request.setAiTools(List.of(AbstractSubmissionAiTool.builder()
                .aiToolCode(100L)
                .otherToolName("Custom AI")
                .build()));

        when(repository.countEnabledAiOption("ABSTRACT_AI_TOOLS", 100L)).thenReturn(1L);
        when(repository.countEnabledEtcAiOption("ABSTRACT_AI_TOOLS", 100L)).thenReturn(1L);

        assertThatThrownBy(() -> service.create(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tool name and provider");
    }

    @Test
    void requiresMethodsWhenAiDataAnalysisWasUsed() {
        AbstractSubmissionRequest request = validAiRequest();
        request.setMethodsText(null);
        request.setAiDataAnalysisUsed(true);

        when(repository.countEnabledAiOption("ABSTRACT_AI_TOOLS", 100L)).thenReturn(1L);
        when(repository.countEnabledAiOption("ABSTRACT_AI_SCOPES", 200L)).thenReturn(1L);

        assertThatThrownBy(() -> service.create(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Methods are required");
    }

    @Test
    void rejectsNonEnglishAbstractTitle() {
        AbstractSubmissionRequest request = baseRequest();
        request.setTitle("한글 초록 제목");

        assertThatThrownBy(() -> service.create(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Title must contain English characters only.");
    }

    @Test
    void rejectsNonEnglishStructuredAbstractText() {
        AbstractSubmissionRequest request = baseRequest();
        request.setObjectiveText("연구 목적");

        assertThatThrownBy(() -> service.create(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Objective must contain English characters only.");
    }

    @Test
    void acceptsEnglishTextWithNumbersAndPunctuation() {
        AbstractSubmissionRequest request = baseRequest();
        request.setTitle("Patient Safety Study: Phase 2 (2026)");
        request.setObjectiveText("To assess safety, efficacy, and risk (95% CI).\nSecondary outcomes were included.");
        request.setAiUsage(false);
        request.setPlagiarismPolicyConfirmed(true);

        doAnswer(invocation -> {
            AbstractSubmissionRequest savedRequest = invocation.getArgument(0);
            savedRequest.setSeq(43L);
            return null;
        }).when(repository).insert(any(AbstractSubmissionRequest.class));
        when(repository.findBySeq(1L, 43L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(AbstractSubmissionResponse.builder().seq(43L).build());

        service.create(1L, request);

        verify(repository).updateSubmissionNo(1L, 43L, "APDRC8-Abstract-0043");
    }

    @Test
    void createsSubmissionNumberWithApdrc8AbstractPrefix() {
        AbstractSubmissionRequest request = baseRequest();
        request.setAiUsage(false);
        request.setPlagiarismPolicyConfirmed(true);

        doAnswer(invocation -> {
            AbstractSubmissionRequest savedRequest = invocation.getArgument(0);
            savedRequest.setSeq(42L);
            return null;
        }).when(repository).insert(any(AbstractSubmissionRequest.class));
        when(repository.findBySeq(1L, 42L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(AbstractSubmissionResponse.builder()
                .seq(42L)
                .submissionNo("APDRC8-Abstract-0042")
                .build());

        service.create(1L, request);

        verify(repository).updateSubmissionNo(1L, 42L, "APDRC8-Abstract-0042");
        assertThat(request.getSubmissionSource()).isEqualTo("admin");
    }

    @Test
    void adminCannotEditMemberSubmittedContent() {
        AbstractSubmissionRequest request = baseRequest();
        when(repository.findBySeq(1L, 7L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(AbstractSubmissionResponse.builder()
                .seq(7L)
                .submissionSource("member")
                .build());

        assertThatThrownBy(() -> service.update(1L, 7L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("관리자가 수정할 수 없습니다");
    }

    @Test
    void memberCannotEditAfterReviewHasStarted() {
        AbstractSubmissionRequest request = baseRequest();
        when(repository.findBySeq(1L, 7L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(AbstractSubmissionResponse.builder()
                .seq(7L)
                .memberSeq(1L)
                .submissionSource("member")
                .status("under_review")
                .build());

        assertThatThrownBy(() -> service.updateByMember(1L, 7L, 1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("심사가 시작된");
    }

    @Test
    void memberCanSubmitAnExistingDraft() {
        AbstractSubmissionRequest request = baseRequest();
        request.setStatus("submitted");
        request.setAiUsage(false);
        request.setPlagiarismPolicyConfirmed(true);
        when(repository.findBySeq(1L, 7L, PersonalDataTestSupport.DB_ENC_STRING))
                .thenReturn(AbstractSubmissionResponse.builder()
                        .seq(7L)
                        .memberSeq(1L)
                        .memberEmail("member@example.com")
                        .submissionSource("member")
                        .submissionNo("APDRC8-Abstract-0007")
                        .status("draft")
                        .build())
                .thenReturn(AbstractSubmissionResponse.builder().seq(7L).build());

        service.updateByMember(1L, 7L, 1L, request);

        assertThat(request.getStatus()).isEqualTo("submitted");
        assertThat(request.getSubmittedAt()).isNotNull();
        verify(repository).update(request);
        verify(abstractTitleSimilarityService).refreshAfterSave(1L, 7L);
    }

    @Test
    void memberCannotCreateOutsideAbstractSubmissionPeriod() {
        when(memberRepository.findBySeq(1L, 1L, PersonalDataTestSupport.DB_ENC_STRING))
                .thenReturn(Member.builder().seq(1L).email("member@example.com").build());
        when(conferenceSettingsRepository.findBySeq(1L)).thenReturn(ConferenceSettings.builder()
                .abstractStartDate(LocalDate.of(2026, 9, 15))
                .abstractEndDate(LocalDate.of(2026, 9, 30))
                .build());

        assertThatThrownBy(() -> service.createByMember(1L, baseRequest(), 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Abstract submission is currently closed.");
    }

    @Test
    void memberCannotUpdateOutsideAbstractSubmissionPeriod() {
        when(repository.findBySeq(1L, 7L, PersonalDataTestSupport.DB_ENC_STRING))
                .thenReturn(AbstractSubmissionResponse.builder()
                        .seq(7L).memberSeq(1L).submissionSource("member").status("submitted").build());
        when(conferenceSettingsRepository.findBySeq(1L)).thenReturn(ConferenceSettings.builder()
                .abstractStartDate(LocalDate.of(2026, 9, 1))
                .abstractEndDate(LocalDate.of(2026, 9, 13))
                .build());

        assertThatThrownBy(() -> service.updateByMember(1L, 7L, 1L, baseRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Abstract submission is currently closed.");
    }

    @Test
    void memberCanDeleteOnlyOwnDraft() {
        when(repository.findMemberStatusForUpdate(1L, 7L, 1L)).thenReturn("draft");

        service.deleteDraftByMember(1L, 7L, 1L);

        verify(repository).deleteInstitutions(7L);
        verify(repository).deleteAuthors(7L);
        verify(repository).deleteAiTools(7L);
        verify(repository).deleteAiScopes(7L);
        verify(repository).delete(1L, 7L);
    }

    @Test
    void memberCannotDeleteSubmittedAbstract() {
        when(repository.findMemberStatusForUpdate(1L, 7L, 1L)).thenReturn("submitted");

        assertThatThrownBy(() -> service.deleteDraftByMember(1L, 7L, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only draft abstracts can be deleted.");

        verify(repository, never()).delete(1L, 7L);
    }

    @Test
    void memberCannotReadAnotherMembersAbstract() {
        when(repository.findBySeq(1L, 7L, PersonalDataTestSupport.DB_ENC_STRING))
                .thenReturn(AbstractSubmissionResponse.builder()
                        .seq(7L)
                        .memberSeq(2L)
                        .submissionSource("member")
                        .build());

        assertThatThrownBy(() -> service.getByMember(1L, 7L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("조회할 수 있는");
    }

    private AbstractSubmissionRequest validAiRequest() {
        AbstractSubmissionRequest request = baseRequest();
        request.setAiUsage(true);
        request.setAiVersionInfo("ChatGPT-5.2");
        request.setAiDataAnalysisUsed(false);
        request.setPlagiarismPolicyConfirmed(true);
        request.setAiTools(List.of(AbstractSubmissionAiTool.builder().aiToolCode(100L).build()));
        request.setAiScopes(List.of(AbstractSubmissionAiScope.builder().aiScopeCode(200L).build()));
        return request;
    }

    private AbstractSubmissionRequest baseRequest() {
        return AbstractSubmissionRequest.builder()
                .memberId("member@example.com")
                .presentationTypeCode(10L)
                .categoryCode(20L)
                .title("AI disclosure test")
                .methodsText("Methods")
                .status("draft")
                .institutions(List.of(AbstractSubmissionInstitution.builder()
                        .institutionNo(1)
                        .country("Korea")
                        .institutionName("Test Institution")
                        .build()))
                .authors(List.of(AbstractSubmissionAuthor.builder()
                        .authorOrder(1)
                        .authorName("Author")
                        .institutionNo(1)
                        .isPresentingAuthor(true)
                        .build()))
                .build();
    }
}
