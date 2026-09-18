package com.bjworld21.congress.controller;

import com.bjworld21.congress.dto.PaymentCheckoutRequest;
import com.bjworld21.congress.dto.PaymentCheckoutResponse;
import com.bjworld21.congress.dto.PaymentGatewaySummaryResponse;
import com.bjworld21.congress.payment.PaymentGatewayService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/payment-test")
public class PaymentTestController {
    private static final Logger log = LoggerFactory.getLogger(PaymentTestController.class);

    private final PaymentGatewayService paymentGatewayService;

    public PaymentTestController(PaymentGatewayService paymentGatewayService) {
        this.paymentGatewayService = paymentGatewayService;
    }

    @GetMapping("/config")
    public PaymentGatewaySummaryResponse config() {
        return paymentGatewayService.summary();
    }

    @PostMapping("/checkout")
    public PaymentCheckoutResponse checkout(@Valid @RequestBody PaymentCheckoutRequest request) {
        return paymentGatewayService.prepareCheckout(request);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> handleBadRequest(RuntimeException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationError(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " 값을 확인해주세요.")
                .orElse("결제 요청값을 확인해주세요.");
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpectedError(Exception exception) {
        log.error("PG 결제 테스트 준비 중 오류가 발생했습니다.", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "PG 결제 테스트를 준비하지 못했습니다."));
    }
}
