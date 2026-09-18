package com.bjworld21.congress.config;

import com.bjworld21.congress.repository.MemberRepository;
import com.bjworld21.congress.security.MemberCredentialFingerprint;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemberCredentialSessionConfigTest {
    private final MemberRepository members = mock(MemberRepository.class);
    private final MemberCredentialSessionConfig interceptor = new MemberCredentialSessionConfig(members);

    @Test
    void passwordChangeClearsMemberLoginButPreservesAdminAndCsrfSession() {
        var request = signedIn();
        request.getSession().setAttribute("adminSeq", 99L);
        when(members.findCredential(7L, 11L)).thenReturn("changed-bcrypt");
        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        assertThat(request.getSession().getAttribute("memberSeq")).isNull();
        assertThat(request.getSession().getAttribute("memberConferenceSeq")).isNull();
        assertThat(request.getSession().getAttribute(MemberCredentialFingerprint.SESSION_ATTRIBUTE)).isNull();
        assertThat(request.getSession().getAttribute("adminSeq")).isEqualTo(99L);
    }

    @Test
    void unchangedPasswordKeepsMemberSignedIn() {
        var request = signedIn();
        when(members.findCredential(7L, 11L)).thenReturn("existing-bcrypt");
        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
        assertThat(request.getSession().getAttribute("memberSeq")).isEqualTo(11L);
    }

    @Test
    void preDeploymentSessionsMustSignInAgain() {
        var request = signedIn();
        request.getSession().removeAttribute(MemberCredentialFingerprint.SESSION_ATTRIBUTE);
        when(members.findCredential(7L, 11L)).thenReturn("existing-bcrypt");
        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
        assertThat(request.getSession().getAttribute("memberSeq")).isNull();
    }

    @Test
    void anonymousRequestsDoNotQueryOrCreateSessions() {
        var request = new MockHttpServletRequest();
        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
        assertThat(request.getSession(false)).isNull();
        verifyNoInteractions(members);
    }

    private MockHttpServletRequest signedIn() {
        var request = new MockHttpServletRequest("GET", "/mypage");
        request.getSession().setAttribute("memberSeq", 11L);
        request.getSession().setAttribute("memberConferenceSeq", 7L);
        request.getSession().setAttribute(MemberCredentialFingerprint.SESSION_ATTRIBUTE,
                MemberCredentialFingerprint.hash("existing-bcrypt"));
        return request;
    }
}
