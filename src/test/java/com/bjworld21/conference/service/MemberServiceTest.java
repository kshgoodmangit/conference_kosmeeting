package com.bjworld21.conference.service;

import com.bjworld21.conference.dto.MemberRegisterRequest;
import com.bjworld21.conference.entity.Member;
import com.bjworld21.conference.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemberServiceTest {

    @Test
    void profileUpdatePersistsAffiliationAndClearsBlankOptionalFields() {
        MemberRepository repository = mock(MemberRepository.class);
        MemberService service = new MemberService();
        ReflectionTestUtils.setField(service, "memberRepository", repository);
        ReflectionTestUtils.setField(service, "personalDataProperties", PersonalDataTestSupport.properties());
        when(repository.findBySeq(1L, 11L, PersonalDataTestSupport.DB_ENC_STRING))
                .thenReturn(Member.builder().seq(11L).password("existing-hash").build());
        MemberRegisterRequest request = MemberRegisterRequest.builder()
                .memberType("international").country("Japan").email("member@example.com")
                .firstName(" Jane ").lastName(" Doe ").institution(" New University ")
                .department(" Biology ").positionTitle(" Researcher ").mobile("+81 90-1234-5678").build();
        service.update(1L, 11L, request);
        ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
        verify(repository).update(eq(1L), saved.capture(), eq(PersonalDataTestSupport.DB_ENC_STRING));
        assertThat(saved.getValue().getInstitution()).isEqualTo("New University");
        assertThat(saved.getValue().getDepartment()).isEqualTo("Biology");
        assertThat(saved.getValue().getPositionTitle()).isEqualTo("Researcher");
        assertThat(saved.getValue().getPassword()).isEqualTo("existing-hash");
        request.setDepartment(" ");
        request.setPositionTitle("");
        service.update(1L, 11L, request);
        assertThat(saved.getValue().getDepartment()).isNull();
        assertThat(saved.getValue().getPositionTitle()).isNull();
        request.setInstitution(" ");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.update(1L, 11L, request))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Institution is required");
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).updatePassword(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void registrationNormalizesEmailBeforeDuplicateCheckAndEncryptedInsert() {
        MemberRepository repository = mock(MemberRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode("Password123!")).thenReturn("encoded-password");

        MemberService service = new MemberService();
        ReflectionTestUtils.setField(service, "memberRepository", repository);
        ReflectionTestUtils.setField(service, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(service, "personalDataProperties", PersonalDataTestSupport.properties());

        MemberRegisterRequest request = MemberRegisterRequest.builder()
                .memberType("domestic")
                .email("  MEMBER@Example.COM ")
                .password("Password123!")
                .firstName("길동")
                .lastName("홍")
                .institution("테스트 기관")
                .mobile("010-1234-5678")
                .newsletter(false)
                .build();

        service.register(1L, request);

        verify(repository).findByEmail(1L, "member@example.com", PersonalDataTestSupport.DB_ENC_STRING);
        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        verify(repository).insert(eq(1L), memberCaptor.capture(), eq(PersonalDataTestSupport.DB_ENC_STRING));
        assertThat(memberCaptor.getValue().getEmail()).isEqualTo("member@example.com");
        assertThat(memberCaptor.getValue().getPassword()).isEqualTo("encoded-password");
    }
}
