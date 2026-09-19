package com.bjworld21.conference.config;

import com.bjworld21.conference.security.ClientIpResolver;
import com.bjworld21.conference.service.AdminIpAccessCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminIpAccessInterceptorTest {
    private AdminIpAccessProperties properties;
    private AdminIpAccessCache cache;
    private AdminIpAccessInterceptor interceptor;

    @BeforeEach
    void setUp() {
        properties = new AdminIpAccessProperties();
        cache = mock(AdminIpAccessCache.class);
        interceptor = new AdminIpAccessInterceptor(properties, cache, new ClientIpResolver(properties));
    }

    @Test
    void bypassesAllChecksWhenFeatureIsDisabled() throws Exception {
        MockHttpServletRequest request = request("203.0.113.10");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), handler("protectedEndpoint"))).isTrue();
        verify(cache, never()).isAllowed(any(InetAddress.class));
    }

    @Test
    void permitsAllowedIpAndBlocksDisallowedIp() throws Exception {
        properties.setEnabled(true);
        when(cache.isAllowed(any(InetAddress.class))).thenReturn(true, false);

        assertThat(interceptor.preHandle(
                request("203.0.113.10"), new MockHttpServletResponse(), handler("protectedEndpoint")))
                .isTrue();

        MockHttpServletResponse deniedResponse = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request("203.0.113.11"), deniedResponse, handler("protectedEndpoint")))
                .isFalse();
        assertThat(deniedResponse.getStatus()).isEqualTo(403);
        assertThat(deniedResponse.getContentAsString()).isEqualTo("허용되지 않은 네트워크입니다.");
    }

    @Test
    void bypassesAnnotatedHandlerEvenWhenFeatureIsEnabled() throws Exception {
        properties.setEnabled(true);

        assertThat(interceptor.preHandle(
                request("203.0.113.11"), new MockHttpServletResponse(), handler("exemptEndpoint")))
                .isTrue();
        verify(cache, never()).isAllowed(any(InetAddress.class));
    }

    private MockHttpServletRequest request(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        request.setMethod("GET");
        request.setRequestURI("/api/test");
        return request;
    }

    private HandlerMethod handler(String methodName) throws NoSuchMethodException {
        TestController controller = new TestController();
        return new HandlerMethod(controller, TestController.class.getDeclaredMethod(methodName));
    }

    private static class TestController {
        public void protectedEndpoint() {
        }

        @IpAccessExempt
        public void exemptEndpoint() {
        }
    }
}
