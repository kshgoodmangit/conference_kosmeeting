package com.bjworld21.congress.publicsite;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.io.IOException;
import java.util.List;

/** Fixture for unit tests; interceptor integration tests use the actual PublicSiteService. */
public final class PublicSiteTestContext implements Filter {
    public static PublicSiteContext context(long seq, String language) {
        return new PublicSiteContext(seq, "apdrc8", language, List.of("en", "ko"),
                "/apdrc8/" + language, "/api/public/" + seq);
    }
    public static void bind(long seq) {
        var request = new MockHttpServletRequest();
        request.setAttribute(PublicSiteContext.ATTRIBUTE, context(seq, "en"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (request instanceof HttpServletRequest http) {
            var matcher = java.util.regex.Pattern.compile("^/api/public/([0-9]+)/").matcher(http.getRequestURI());
            if (matcher.find()) request.setAttribute(PublicSiteContext.ATTRIBUTE,
                    context(Long.parseLong(matcher.group(1)), "ko".equals(request.getParameter("lang")) ? "ko" : "en"));
        }
        chain.doFilter(request, response);
    }
}
