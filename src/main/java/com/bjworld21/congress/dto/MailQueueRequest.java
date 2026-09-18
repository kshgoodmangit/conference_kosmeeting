package com.bjworld21.congress.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MailQueueRequest {
    @NotBlank(message = "중복 요청 방지 키는 필수입니다.")
    private String idempotencyKey;
}
