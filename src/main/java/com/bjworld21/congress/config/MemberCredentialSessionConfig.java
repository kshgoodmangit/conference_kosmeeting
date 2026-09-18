package com.bjworld21.congress.config;

import com.bjworld21.congress.repository.MemberRepository;
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
        if (session == null || session.getAttribute("memberSeq") == null) return true;
        Object member = session.getAttribute("memberSeq");
        Object conference = session.getAttribute("memberConferenceSeq");
        Object fingerprint = session.getAttribute(MemberCredentialFingerprint.SESSION_ATTRIBUTE);
        String password = member instanceof Number m && conference instanceof Number c
                ? members.findCredential(c.longValue(), m.longValue()) : null;
        if (password == null || !MemberCredentialFingerprint.hash(password).equals(fingerprint)) {
            // Only clear member authentication; an admin login can share the same HTTP session.
            session.removeAttribute("memberSeq");
            session.removeAttribute("memberConferenceSeq");
            session.removeAttribute(MemberCredentialFingerprint.SESSION_ATTRIBUTE);
        }
        return true;
    }
}
