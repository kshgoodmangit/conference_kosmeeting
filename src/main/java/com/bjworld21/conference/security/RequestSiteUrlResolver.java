package com.bjworld21.conference.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

/** Resolves the request origin. The edge proxy must reject unknown Host names. */
@Component
public class RequestSiteUrlResolver {
    private final ClientIpResolver clientIpResolver;

    public RequestSiteUrlResolver(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    public String resolve(HttpServletRequest request) {
        String scheme = request.getScheme();
        String authority = singleHeader(request, "Host");
        if (authority == null) {
            String host = request.getServerName();
            if (host.contains(":") && !host.startsWith("[")) host = "[" + host + "]";
            authority = host + ":" + request.getServerPort();
        }
        // Never trust arbitrary Origin/Referer or forwarding headers from direct clients.
        if (clientIpResolver.isTrustedProxy(request)) {
            String forwardedHost = singleHeader(request, "X-Forwarded-Host");
            String forwardedProto = singleHeader(request, "X-Forwarded-Proto");
            String forwardedPort = singleHeader(request, "X-Forwarded-Port");
            if (forwardedHost != null) authority = forwardedHost;
            if (forwardedProto != null) scheme = forwardedProto;
            if (forwardedPort != null) {
                URI origin = URI.create(normalize(scheme + "://" + authority));
                if (!forwardedPort.matches("[0-9]{1,5}")) throw invalidAddress();
                authority = origin.getHost() + ":" + forwardedPort;
            }
        }
        return normalize(scheme + "://" + authority);
    }

    public static String normalize(String value) {
        try {
            if (value == null || value.length() > 255 || value.chars().anyMatch(c -> c <= 32 || c >= 127)) {
                throw invalidAddress();
            }
            URI uri = URI.create(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                    || uri.getRawAuthority().endsWith(":")
                    || uri.getPort() == 0 || uri.getPort() > 65535) throw invalidAddress();
            int port = uri.getPort();
            if (("https".equals(scheme) && port == 443) || ("http".equals(scheme) && port == 80)) port = -1;
            return new URI(scheme, null, uri.getHost().toLowerCase(Locale.ROOT), port, null, null, null).toASCIIString();
        } catch (Exception e) {
            throw invalidAddress();
        }
    }

    private String singleHeader(HttpServletRequest request, String name) {
        var values = request.getHeaders(name);
        if (values == null || !values.hasMoreElements()) return null;
        String value = values.nextElement();
        if (values.hasMoreElements() || value == null || value.isBlank() || value.contains(",")) throw invalidAddress();
        return value;
    }

    private static IllegalArgumentException invalidAddress() {
        return new IllegalArgumentException("접속 사이트 주소를 확인할 수 없습니다. 접속 주소와 프록시 설정을 확인해 주세요.");
    }
}
