package com.bjworld21.congress.config;

import com.bjworld21.congress.repository.MemberRepository;
import com.bjworld21.congress.controller.PublicMemberSession;
import com.bjworld21.congress.security.MemberCredentialFingerprint;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MemberCredentialSessionConfig implements WebMvcConfigurer, HandlerInterceptor {
    private final MemberRepository members;

    public MemberCredentialSessionConfig(MemberRepository members) {
        this.members = members;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/**")
                .excludePathPatterns("/public/**", "/assets/**", "/favicon.ico", "/error");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        var session = request.getSession(false);
        if (session == null) return true;
        for (PublicMemberSession member : PublicMemberSession.all(session)) {
            String password = members.findCredential(member.conferenceSeq(), member.memberSeq());
            String fingerprint = PublicMemberSession.fingerprint(session, member.conferenceSeq());
            if (password == null || !MemberCredentialFingerprint.hash(password).equals(fingerprint)) {
                PublicMemberSession.signOut(session, member.conferenceSeq());
            }
        }
        return true;
    }
}
