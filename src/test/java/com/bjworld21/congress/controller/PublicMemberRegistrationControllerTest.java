package com.bjworld21.congress.controller;

import com.bjworld21.congress.dto.MemberDetailResponse;
import com.bjworld21.congress.dto.MemberListResponse;
import com.bjworld21.congress.dto.MemberRegisterRequest;
import com.bjworld21.congress.dto.MemberRegisterResponse;
import com.bjworld21.congress.service.ConferenceSettingsService;
import com.bjworld21.congress.service.MemberService;
import com.bjworld21.congress.service.MemberEmailVerificationService;
import com.bjworld21.congress.config.PersonalDataProperties;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicMemberRegistrationControllerTest {
    private MemberService memberService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        memberService = mock(MemberService.class);
        ConferenceSettingsService conferenceSettingsService = mock(ConferenceSettingsService.class);
        when(conferenceSettingsService.getLatestConferenceSeq()).thenReturn(7L);
        when(memberService.register(eq(7L), any(MemberRegisterRequest.class)))
                .thenReturn(MemberRegisterResponse.builder().seq(1L).build());
        when(memberService.findDetail(7L, 11L)).thenReturn(MemberDetailResponse.builder()
                .member(MemberListResponse.builder()
                        .seq(11L)
                        .memberType("international")
                        .email("member@example.com")
                        .institution("Example University")
                        .department("Research")
                        .positionTitle("Professor")
                        .build())
                .build());
        when(memberService.update(eq(7L), eq(11L), any(MemberRegisterRequest.class)))
                .thenReturn(MemberRegisterResponse.builder().seq(11L).build());

        mockMvc = MockMvcBuilders.standaloneSetup(
                new PublicMemberRegistrationController(memberService, conferenceSettingsService,
                        new MemberEmailVerificationService(null, new PersonalDataProperties(), null,
                                conferenceSettingsService, null, mock(PlatformTransactionManager.class), Clock.systemUTC(), null))
        ).addFilters(new com.bjworld21.congress.publicsite.PublicSiteTestContext()).build();
    }

    @Test
    void registersInternationalMemberThroughPublicEndpoint() throws Exception {
        mockMvc.perform(post("/api/public/7/members/register")
                        .sessionAttr(MemberEmailVerificationService.VERIFIED_KEY + 7,
                                new MemberEmailVerificationService.VerifiedEmail(7L, "member@example.com", Instant.now().plusSeconds(1800)))
                        .param("memberType", "international")
                        .param("country", "South Korea")
                        .param("email", " MEMBER@example.com ")
                        .param("firstName", " Jane ")
                        .param("lastName", " Doe ")
                        .param("institution", " Example University ")
                        .param("department", "Research")
                        .param("positionTitle", "Professor")
                        .param("password", " Password123! ")
                        .param("passwordConfirm", " Password123! ")
                        .param("mobileCountryCode", "+82")
                        .param("mobilePhoneNumber", "10-1234-5678")
                        .param("newsletter", "true")
                        .param("privacyConsent", "true"))
                .andExpect(status().isOk());

        ArgumentCaptor<MemberRegisterRequest> captor = ArgumentCaptor.forClass(MemberRegisterRequest.class);
        verify(memberService).register(eq(7L), captor.capture());
        assertThat(captor.getValue().getMemberType()).isEqualTo("international");
        assertThat(captor.getValue().getEmail()).isEqualTo("MEMBER@example.com");
        assertThat(captor.getValue().getMobile()).isEqualTo("+82 10-1234-5678");
    }

    @Test
    void rejectsMissingPrivacyConsentBeforeRegistration() throws Exception {
        mockMvc.perform(post("/api/public/7/members/register")
                        .param("memberType", "international")
                        .param("country", "United States")
                        .param("email", "member@example.com")
                        .param("firstName", "Jane")
                        .param("lastName", "Doe")
                        .param("institution", "Example University")
                        .param("password", "Password123!")
                        .param("passwordConfirm", "Password123!")
                        .param("mobileCountryCode", "+1")
                        .param("mobilePhoneNumber", "202-555-0100"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Privacy consent is required"));

        verify(memberService, never()).register(eq(7L), any(MemberRegisterRequest.class));
    }

    @Test
    void registersDomesticMemberWithoutCountry() throws Exception {
        mockMvc.perform(post("/api/public/7/members/register")
                        .sessionAttr(MemberEmailVerificationService.VERIFIED_KEY + 7,
                                new MemberEmailVerificationService.VerifiedEmail(7L, "member@example.com", Instant.now().plusSeconds(1800)))
                        .param("memberType", "domestic")
                        .param("email", "member@example.com")
                        .param("firstName", "Gildong")
                        .param("lastName", "Hong")
                        .param("institution", "Example University")
                        .param("password", "Password123!")
                        .param("passwordConfirm", "Password123!")
                        .param("mobileCountryCode", "+82")
                        .param("mobilePhoneNumber", "10-1234-5678")
                        .param("privacyConsent", "true"))
                .andExpect(status().isOk());

        ArgumentCaptor<MemberRegisterRequest> captor = ArgumentCaptor.forClass(MemberRegisterRequest.class);
        verify(memberService).register(eq(7L), captor.capture());
        assertThat(captor.getValue().getMemberType()).isEqualTo("domestic");
        assertThat(captor.getValue().getCountry()).isNull();
    }

    @Test
    void rejectsDirectSignupWithoutEmailVerification() throws Exception {
        mockMvc.perform(post("/api/public/7/members/register")
                        .param("memberType", "domestic").param("email", "member@example.com")
                        .param("firstName", "Gildong").param("lastName", "Hong")
                        .param("institution", "Example University").param("privacyConsent", "true")
                        .param("password", "Password123!").param("passwordConfirm", "Password123!")
                        .param("mobileCountryCode", "+82").param("mobilePhoneNumber", "10-1234-5678"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Please verify your email before signing up."));
        verify(memberService, never()).register(eq(7L), any(MemberRegisterRequest.class));
    }

    @Test
    void rejectsInvalidMobilePartsBeforeRegistration() throws Exception {
        mockMvc.perform(post("/api/public/7/members/register")
                        .param("memberType", "domestic")
                        .param("email", "member@example.com")
                        .param("firstName", "Gildong")
                        .param("lastName", "Hong")
                        .param("institution", "Example University")
                        .param("password", "Password123!")
                        .param("passwordConfirm", "Password123!")
                        .param("mobileCountryCode", "82")
                        .param("mobilePhoneNumber", "010.ABCD")
                        .param("privacyConsent", "true"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid mobile country code"));

        verify(memberService, never()).register(eq(7L), any(MemberRegisterRequest.class));
    }

    @Test
    void updatesOnlyTheLoggedInMembersPublicProfile() throws Exception {
        mockMvc.perform(post("/api/public/7/members/profile")
                        .sessionAttr("publicMember.7", new PublicMemberSession(11L, 7L))
                        .param("country", "Japan")
                        .param("firstName", " Jane ")
                        .param("lastName", " Doe ")
                        .param("institution", " New University ")
                        .param("department", "Biology")
                        .param("positionTitle", "Researcher")
                        .param("email", "other@example.com")
                        .param("memberType", "domestic")
                        .param("mobileCountryCode", "+81")
                        .param("mobilePhoneNumber", "90-1234-5678")
                        .param("newsletter", "true"))
                .andExpect(status().isOk());

        ArgumentCaptor<MemberRegisterRequest> captor = ArgumentCaptor.forClass(MemberRegisterRequest.class);
        verify(memberService).update(eq(7L), eq(11L), captor.capture());
        assertThat(captor.getValue().getMemberType()).isEqualTo("international");
        assertThat(captor.getValue().getEmail()).isEqualTo("member@example.com");
        assertThat(captor.getValue().getInstitution()).isEqualTo("New University");
        assertThat(captor.getValue().getDepartment()).isEqualTo("Biology");
        assertThat(captor.getValue().getPositionTitle()).isEqualTo("Researcher");
        assertThat(captor.getValue().getFirstName()).isEqualTo("Jane");
        assertThat(captor.getValue().getMobile()).isEqualTo("+81 90-1234-5678");
        assertThat(captor.getValue().getPassword()).isNull();
    }

    @Test
    void profileCannotBypassCurrentPasswordVerification() throws Exception {
        mockMvc.perform(post("/api/public/7/members/profile")
                        .sessionAttr("publicMember.7", new PublicMemberSession(11L, 7L))
                        .param("firstName", "Jane").param("lastName", "Doe")
                        .param("institution", "Example University")
                        .param("mobileCountryCode", "+81").param("mobilePhoneNumber", "90-1234-5678")
                        .param("password", "NewPassword123!").param("passwordConfirm", "NewPassword123!"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Please use Change Password to update your password."));
        verify(memberService, never()).update(any(), any(), any());
    }

    @Test
    void rejectsProfileUpdateWithoutMemberSession() throws Exception {
        mockMvc.perform(post("/api/public/7/members/profile")
                        .param("firstName", "Jane")
                        .param("lastName", "Doe")
                        .param("institution", "Example University")
                        .param("mobileCountryCode", "+81")
                        .param("mobilePhoneNumber", "90-1234-5678"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Login is required"));

        verify(memberService, never()).update(eq(7L), any(), any(MemberRegisterRequest.class));
    }

    @Test
    void rendersInternationalRegistrationTemplate() throws Exception {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        var messages = new org.springframework.context.support.ResourceBundleMessageSource();
        messages.setBasename("public-ui");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        engine.setTemplateEngineMessageSource(messages);
        var site = new com.bjworld21.congress.publicsite.PublicSiteContext(7L, "apdrc8", "en", java.util.List.of("en", "ko"), "/apdrc8/en", "/api/public/7");

        Context context = new Context(java.util.Locale.ENGLISH);
        context.setVariable("siteContext", site);
        assertThat(engine.process("public/member/join-international", context))
                .contains("/api/public/7/members/register", "<select id=\"international-country\"", "mobileCountryCode", "mobilePhoneNumber", "privacyConsent")
                .doesNotContain("<datalist");
        assertThat(engine.process("public/member/join-domestic", context))
                .contains("/api/public/7/members/register", "value=\"domestic\"", "value=\"+82\"")
                .doesNotContain("domestic-country");

        Context profileContext = new Context(java.util.Locale.ENGLISH);
        profileContext.setVariable("siteContext", site);
        profileContext.setVariable("mypageMember", MemberListResponse.builder()
                .memberType("international")
                .country("Japan")
                .email("member@example.com")
                .firstName("Jane")
                .lastName("Doe")
                .institution("Example University")
                .department("Biology")
                .positionTitle("Researcher")
                .mobile("+81 90-1234-5678")
                .newsletter(true)
                .build());
        String profileHtml = engine.process("public/member/mypage-profile", profileContext);
        assertThat(profileHtml)
                .contains("/api/public/7/members/profile", "data-selected-country=\"Japan\"",
                        "<span id=\"profile-email\">member@example.com</span>", "value=\"+81\"", "value=\"90-1234-5678\"",
                        "name=\"institution\"", "value=\"Example University\"", "name=\"department\"", "value=\"Biology\"",
                        "name=\"positionTitle\"", "value=\"Researcher\"", "First Name", "Last Name")
                .doesNotContain("name=\"email\"", "name=\"password\"", "type=\"email\"");
        String homeHtml = engine.process("public/member/mypage", profileContext);
        assertThat(homeHtml).contains("First Name", "Last Name", "Institution", "Department", "Position", "Mobile Number",
                "Example University", "Biology", "Researcher", "+81 90-1234-5678", "Subscribed");

        profileContext.setVariable("mypageMember", MemberListResponse.builder()
                .memberType("domestic")
                .email("member@example.com")
                .firstName("Gildong")
                .lastName("Hong")
                .mobile("+82 10-1234-5678")
                .build());
        assertThat(engine.process("public/member/mypage-profile", profileContext))
                .contains("Republic of Korea", "value=\"+82\"", "value=\"10-1234-5678\"")
                .doesNotContain("<select id=\"profile-country\"");

        profileContext.setVariable("mypageMember", MemberListResponse.builder().memberType("domestic")
                .email("member@example.com").firstName("Jane").lastName("Doe").mobile("010-1234-5678").build());
        assertThat(engine.process("public/member/mypage-profile", profileContext))
                .contains("value=\"010-1234-5678\"", "value=\"+82\"");
        profileContext.setVariable("mypageMember", MemberListResponse.builder().memberType("international")
                .email("member@example.com").firstName("Jane").lastName("Doe").build());
        assertThat(engine.process("public/member/mypage-profile", profileContext)).contains("profile-mobile-phone-number");
        assertThat(engine.process("public/member/mypage", profileContext)).contains("<dd>-</dd>");

    }

    @Test
    void profileAllowsClearingOptionalAffiliationFields() throws Exception {
        mockMvc.perform(post("/api/public/7/members/profile")
                        .sessionAttr("publicMember.7", new PublicMemberSession(11L, 7L))
                        .param("country", "Japan").param("firstName", "Jane").param("lastName", "Doe")
                        .param("institution", "New University").param("department", "").param("positionTitle", "")
                        .param("mobileCountryCode", "+81").param("mobilePhoneNumber", "90-1234-5678"))
                .andExpect(status().isOk());
        ArgumentCaptor<MemberRegisterRequest> request = ArgumentCaptor.forClass(MemberRegisterRequest.class);
        verify(memberService).update(eq(7L), eq(11L), request.capture());
        assertThat(request.getValue().getDepartment()).isEmpty();
        assertThat(request.getValue().getPositionTitle()).isEmpty();
    }
}
