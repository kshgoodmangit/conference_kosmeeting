package com.bjworld21.conference.controller;

import com.bjworld21.conference.dto.AdminClientIpResponse;
import com.bjworld21.conference.security.ClientIpResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAccessInfoControllerTest {

    @Mock
    private ClientIpResolver clientIpResolver;

    @InjectMocks
    private AdminAccessInfoController controller;

    @Test
    void returnsResolvedClientIpWithoutCaching() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(clientIpResolver.resolve(request)).thenReturn("203.0.113.25");

        ResponseEntity<AdminClientIpResponse> response = controller.getClientIp(request);

        assertThat(response.getBody()).isEqualTo(new AdminClientIpResponse("203.0.113.25"));
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    }
}
