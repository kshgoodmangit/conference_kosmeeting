package com.bjworld21.conference.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MailContactRequest {
    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Size(max = 255)
    private String email;

    @Size(max = 255)
    private String fullName;

    @Size(max = 255)
    private String affiliation;

    @Size(max = 100)
    private String country;

    @Size(max = 50)
    private String phoneNumber;

    @Size(max = 1000)
    private String memo;
}
