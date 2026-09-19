package com.bjworld21.conference.payment;

import com.bjworld21.conference.dto.PaymentCheckoutRequest;
import com.bjworld21.conference.dto.PaymentCheckoutResponse;
import com.bjworld21.conference.dto.PaymentGatewaySummaryResponse;

public interface PaymentGatewayAdapter {
    String providerCode();

    PaymentGatewaySummaryResponse summary();

    PaymentCheckoutResponse prepareCheckout(PaymentCheckoutRequest request);
}
