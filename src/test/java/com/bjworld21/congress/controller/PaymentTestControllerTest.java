package com.bjworld21.congress.controller;

import com.bjworld21.congress.dto.PaymentGatewaySummaryResponse;
import com.bjworld21.congress.payment.PaymentGatewayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentTestControllerTest {
    private PaymentGatewayService paymentGatewayService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        paymentGatewayService = mock(PaymentGatewayService.class);
        PaymentTestController controller = new PaymentTestController(paymentGatewayService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void returnsConfiguredGatewaySummary() throws Exception {
        when(paymentGatewayService.summary()).thenReturn(new PaymentGatewaySummaryResponse(
                false,
                "paygate",
                "PayGate",
                "PayGate 설정은 로드되었습니다.",
                List.of()
        ));

        mockMvc.perform(get("/api/admin/payment-test/config").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("paygate"));
    }
}
