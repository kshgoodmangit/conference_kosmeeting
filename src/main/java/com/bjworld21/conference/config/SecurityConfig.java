package com.bjworld21.conference.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

import java.nio.charset.StandardCharsets;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        HttpSessionCsrfTokenRepository csrfTokenRepository = new HttpSessionCsrfTokenRepository();
        csrfTokenRepository.setHeaderName("X-CSRF-TOKEN");

        CsrfTokenRequestAttributeHandler csrfTokenRequestHandler = new CsrfTokenRequestAttributeHandler();
        // Prepare the session before Thymeleaf starts streaming the response body.
        csrfTokenRequestHandler.setCsrfRequestAttributeName(null);
        AccessDeniedHandler csrfAccessDeniedHandler = (request, response, exception) -> {
            response.setStatus(403);
            response.setHeader("X-CSRF-ERROR", "true");
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            if (request.getRequestURI().startsWith("/api/public/")) {
                response.setContentType("application/json;charset=UTF-8");
                String language = request.getParameter("lang");
                String message = com.bjworld21.conference.publicsite.PublicApiMessages.message(
                        "CSRF_INVALID", language == null || language.isBlank() ? "en" : language);
                new com.fasterxml.jackson.databind.ObjectMapper().writeValue(response.getWriter(),
                        java.util.Map.of("code", "CSRF_INVALID", "message", message));
            } else {
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().write("CSRF 토큰이 없거나 유효하지 않습니다.");
            }
        };

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/admin",
                                "/admin/",
                                "/admin/**",
                                "/assets/**",
                                "/favicon.svg",
                                "/icons.svg"
                        ).permitAll()
                        .requestMatchers("/api/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/**").permitAll()
                        .anyRequest().authenticated()
                )
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfTokenRequestHandler)
                        .ignoringRequestMatchers("/api/webhooks/mail/**")
                        .withObjectPostProcessor(new ObjectPostProcessor<CsrfFilter>() {
                            @Override
                            public <O extends CsrfFilter> O postProcess(O csrfFilter) {
                                csrfFilter.setAccessDeniedHandler(csrfAccessDeniedHandler);
                                return csrfFilter;
                            }
                        }));
        return http.build();
    }
}

