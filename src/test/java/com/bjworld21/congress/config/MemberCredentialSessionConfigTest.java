package com.bjworld21.congress.config;

import com.bjworld21.congress.repository.MemberRepository;
import com.bjworld21.congress.controller.PublicMemberSession;
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
        assertThat(PublicMemberSession.resolve(request.getSession(), 7)).isNull();
        assertThat(request.getSession().getAttribute("memberConferenceSeq")).isNull();
        assertThat(request.getSession().getAttribute(MemberCredentialFingerprint.SESSION_ATTRIBUTE)).isNull();
        assertThat(request.getSession().getAttribute("adminSeq")).isEqualTo(99L);
    }

    @Test
    void unchangedPasswordKeepsMemberSignedIn() {
        var request = signedIn();
        when(members.findCredential(7L, 11L)).thenReturn("existing-bcrypt");
        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
        assertThat(PublicMemberSession.resolve(request.getSession(), 7).memberSeq()).isEqualTo(11L);
    }

    @Test
    void preDeploymentSessionsMustSignInAgain() {
        var request = signedIn();
        request.getSession().removeAttribute("publicMember.7.credential");
        when(members.findCredential(7L, 11L)).thenReturn("existing-bcrypt");
        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
        assertThat(PublicMemberSession.resolve(request.getSession(), 7)).isNull();
    }

    @Test
    void anonymousRequestsDoNotQueryOrCreateSessions() {
        var request = new MockHttpServletRequest();
        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
        assertThat(request.getSession(false)).isNull();
        verifyNoInteractions(members);
    }

    @Test
    void passwordChangeAtOneConferenceLeavesOtherConferenceLoginIntact() {
        var request = signedIn();
        PublicMemberSession.signIn(request.getSession(), 8, 22, MemberCredentialFingerprint.hash("other"));
        when(members.findCredential(7L, 11L)).thenReturn("changed");
        when(members.findCredential(8L, 22L)).thenReturn("other");
        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
        assertThat(PublicMemberSession.resolve(request.getSession(), 7)).isNull();
        assertThat(PublicMemberSession.resolve(request.getSession(), 8).memberSeq()).isEqualTo(22);
    }

    private MockHttpServletRequest signedIn() {
        var request = new MockHttpServletRequest("GET", "/mypage");
        PublicMemberSession.signIn(request.getSession(), 7, 11, MemberCredentialFingerprint.hash("existing-bcrypt"));
        return request;
    }
}
