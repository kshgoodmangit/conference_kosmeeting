package com.bjworld21.congress.config;

import com.bjworld21.congress.security.ClientIpResolver;
import com.bjworld21.congress.security.IpCidrRange;
import com.bjworld21.congress.service.AdminIpAccessCache;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

@Component
public class AdminIpAccessInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(AdminIpAccessInterceptor.class);

    private final AdminIpAccessProperties properties;
    private final AdminIpAccessCache cache;
    private final ClientIpResolver clientIpResolver;

    public AdminIpAccessInterceptor(
            AdminIpAccessProperties properties,
            AdminIpAccessCache cache,
            ClientIpResolver clientIpResolver
    ) {
        this.properties = properties;
        this.cache = cache;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!properties.isEnabled() || "OPTIONS".equalsIgnoreCase(request.getMethod()) || isExempt(handler)) {
            return true;
        }

        String clientIp;
        try {
            clientIp = clientIpResolver.resolve(request);
            InetAddress address = IpCidrRange.parseLiteralAddress(clientIp);
            if (cache.isAllowed(address)) {
                return true;
            }
        } catch (IllegalArgumentException exception) {
            clientIp = request.getRemoteAddr();
            log.warn("Failed to resolve client IP for protected request: method={}, uri={}",
                    request.getMethod(), request.getRequestURI(), exception);
        }

        log.warn("Blocked request from disallowed IP: ip={}, method={}, uri={}",
                clientIp, request.getMethod(), request.getRequestURI());
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write("허용되지 않은 네트워크입니다.");
        return false;
    }

    private boolean isExempt(Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return false;
        }
        return AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), IpAccessExempt.class)
                || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), IpAccessExempt.class);
    }
}
