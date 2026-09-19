package com.bjworld21.conference.config;

import com.bjworld21.conference.publicsite.PublicSiteContext;
import com.bjworld21.conference.publicsite.PublicSiteService;
import com.bjworld21.conference.publicsite.PublicApiMessages;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Map;

@Configuration
public class PublicApiConfig implements WebMvcConfigurer, HandlerInterceptor {
    private final PublicSiteService sites;

    public PublicApiConfig(PublicSiteService sites) { this.sites = sites; }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/public/**").order(-100);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws java.io.IOException {
        try {
            Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
            if (!(variables instanceof Map<?, ?> paths) || paths.get("conferenceSeq") == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
            long conferenceSeq;
            try { conferenceSeq = Long.parseLong(paths.get("conferenceSeq").toString()); }
            catch (NumberFormatException exception) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
            PublicSiteContext context = sites.resolveApi(conferenceSeq, request.getParameter("lang"));
            request.setAttribute(PublicSiteContext.ATTRIBUTE, context);
            response.setHeader("Content-Language", context.language());
            response.setHeader("Cache-Control", "no-store");
            return true;
        } catch (ResponseStatusException exception) {
            int status = exception.getStatusCode().value();
            String code = PublicApiMessages.code(null, status);
            String language = "ko".equals(request.getParameter("lang")) ? "ko" : "en";
            response.setStatus(status);
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json");
            response.setHeader("Cache-Control", "no-store");
            new com.fasterxml.jackson.databind.ObjectMapper().writeValue(response.getWriter(),
                    Map.of("code", code, "message", PublicApiMessages.message(code, language)));
            return false;
        }
    }
}
