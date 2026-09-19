package com.bjworld21.conference.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MailDirectRecipientRequest {
    @Pattern(regexp = "DIRECT|INTERNAL_MEMBER|INTERNAL_ADMIN|ADDRESS_BOOK_CONTACT", message = "수신자 출처가 올바르지 않습니다.")
    private String sourceType = "DIRECT";

    @NotBlank(message = "직접 수신자 이메일은 필수입니다.")
    @Email(message = "직접 수신자 이메일 형식이 올바르지 않습니다.")
    @Size(max = 255)
    private String email;

    @Size(max = 255)
    private String fullName;

    @Size(max = 255)
    private String affiliation;
}
