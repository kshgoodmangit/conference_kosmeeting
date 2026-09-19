package com.bjworld21.conference.service;

import com.bjworld21.conference.dto.MemberRegisterRequest;
import com.bjworld21.conference.entity.Member;
import com.bjworld21.conference.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MemberPasswordLengthTest {
    private final MemberRepository repository = mock(MemberRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final MemberService service = new MemberService();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "memberRepository", repository);
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
        ReflectionTestUtils.setField(service, "personalDataProperties", PersonalDataTestSupport.properties());
        when(encoder.encode(anyString())).thenReturn("encoded-password");
        when(repository.findBySeq(eq(1L), eq(2L), anyString())).thenReturn(Member.builder().seq(2L).build());
    }

    @ParameterizedTest
    @ValueSource(ints = {8, 16})
    void registrationAndUpdatesAcceptBothLengthBoundaries(int length) {
        String password = "a".repeat(length);
        service.register(1L, request(password));
        service.update(1L, 2L, request(password));
        verify(encoder, times(2)).encode(password);
        verify(repository).insert(eq(1L), any(Member.class), anyString());
        verify(repository).updatePassword(eq(1L), any(Member.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {7, 17})
    void registrationAndUpdatesRejectPasswordsOutsideTheRange(int length) {
        var request = request("a".repeat(length));
        assertThatThrownBy(() -> service.register(1L, request))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Password must be 8-16 characters");
        assertThatThrownBy(() -> service.update(1L, 2L, request))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Password must be 8-16 characters");
        verifyNoInteractions(encoder);
        verify(repository, never()).insert(anyLong(), any(), anyString());
        verify(repository, never()).update(anyLong(), any(), anyString());
        verify(repository, never()).updatePassword(anyLong(), any());
    }

    @Test
    void leavingPasswordEmptyOnProfileUpdateKeepsExistingPassword() {
        service.update(1L, 2L, request(""));
        verifyNoInteractions(encoder);
        verify(repository, never()).updatePassword(anyLong(), any());
    }

    private MemberRegisterRequest request(String password) {
        return MemberRegisterRequest.builder().memberType("domestic").email("member@example.com")
                .password(password).firstName("Jane").lastName("Doe")
                .institution("University").mobile("+82 1012345678").build();
    }
}
