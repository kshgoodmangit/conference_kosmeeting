package com.bjworld21.conference.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class ReviewerSessionInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("adminSeq") == null) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "로그인이 필요합니다.");
            return false;
        }
        if (!"reviewer".equals(session.getAttribute("adminRole"))) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "심사자 권한이 필요합니다.");
            return false;
        }
        if (!(session.getAttribute("reviewerConferenceSeq") instanceof Number)) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "심사자 학회 정보가 없습니다. 다시 로그인해 주세요.");
            return false;
        }

        return true;
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write(message);
    }
}
