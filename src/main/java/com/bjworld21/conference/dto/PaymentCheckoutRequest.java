package com.bjworld21.conference.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentCheckoutRequest {
    @NotBlank
    private String memberType;

    @NotBlank
    private String route;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amount;

    @NotBlank
    @Size(max = 100)
    private String productName;

    @Size(max = 40)
    private String orderNumber;

    @Size(max = 100)
    private String buyerName;

    @Email
    @Size(max = 100)
    private String buyerEmail;

    @Size(max = 20)
    private String buyerPhone;
}
