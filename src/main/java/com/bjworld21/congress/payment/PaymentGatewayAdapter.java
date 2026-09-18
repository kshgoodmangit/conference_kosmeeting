package com.bjworld21.congress.payment;

import com.bjworld21.congress.dto.PaymentCheckoutRequest;
import com.bjworld21.congress.dto.PaymentCheckoutResponse;
import com.bjworld21.congress.dto.PaymentGatewaySummaryResponse;

public interface PaymentGatewayAdapter {
    String providerCode();

    PaymentGatewaySummaryResponse summary();

    PaymentCheckoutResponse prepareCheckout(PaymentCheckoutRequest request);
}
