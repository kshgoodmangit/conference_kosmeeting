package com.bjworld21.conference.security;

import com.bjworld21.conference.config.AdminIpAccessProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class RequestSiteUrlResolverTest {
    private RequestSiteUrlResolver resolver(String... trustedProxies) {
        var properties = new AdminIpAccessProperties();
        properties.setTrustedProxies(List.of(trustedProxies));
        return new RequestSiteUrlResolver(new ClientIpResolver(properties));
    }

    private MockHttpServletRequest request(String host) {
        var request = new MockHttpServletRequest();
        request.setScheme("http"); request.setRemoteAddr("203.0.113.10");
        request.addHeader("Host", host);
        return request;
    }

    @Test void worksWithoutEnvironmentSettingsAndKeepsDevPort() {
        assertThat(resolver().resolve(request("localhost:5173"))).isEqualTo("http://localhost:5173");
        assertThat(resolver().resolve(request("CONFERENCE.example:80"))).isEqualTo("http://conference.example");
        assertThat(resolver().resolve(request("[::1]:5173"))).isEqualTo("http://[::1]:5173");
    }

    @Test void ignoresUntrustedForwardingAndBrowserSuppliedOrigin() {
        var request = request("conference.example");
        request.addHeader("Origin", "https://evil.example");
        request.addHeader("Referer", "https://evil.example/admin");
        request.addHeader("Forwarded", "host=evil.example;proto=https");
        request.addHeader("X-Forwarded-Host", "evil.example");
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("X-Forwarded-Port", "9443");
        assertThat(resolver().resolve(request)).isEqualTo("http://conference.example");
    }

    @Test void readsExternalOriginOnlyFromTrustedProxy() {
        var request = request("backend:8080"); request.setRemoteAddr("10.0.0.2");
        request.addHeader("X-Forwarded-Host", "Conference.example");
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("X-Forwarded-Port", "443");
        assertThat(resolver("10.0.0.2").resolve(request)).isEqualTo("https://conference.example");
    }

    @Test void rejectsUnsafeOrAmbiguousAuthorities() {
        for (String host : List.of("user@evil.example", "evil.example/path", "evil.example?x=1", "evil.example#x",
                "a.example,b.example", "evil.example:0", "evil.example:65536", "evil.example:", "evil.example\r\nX-Test: x")) {
            assertThatThrownBy(() -> resolver().resolve(request(host))).as(host).isInstanceOf(IllegalArgumentException.class);
        }
        var request = request("conference.example"); request.addHeader("Host", "evil.example");
        assertThatThrownBy(() -> resolver().resolve(request)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsBadForwardingFromTrustedProxyInsteadOfBuildingUnsafeLink() {
        var request = request("backend:8080"); request.setRemoteAddr("10.0.0.2");
        request.addHeader("X-Forwarded-Host", "public.example, evil.example");
        assertThatThrownBy(() -> resolver("10.0.0.2").resolve(request)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void usesServletAddressWhenNoHostHeaderIsAvailable() {
        var request = new MockHttpServletRequest(); request.setScheme("https");
        request.setServerName("conference.example"); request.setServerPort(443);
        assertThat(resolver().resolve(request)).isEqualTo("https://conference.example");
    }
}
