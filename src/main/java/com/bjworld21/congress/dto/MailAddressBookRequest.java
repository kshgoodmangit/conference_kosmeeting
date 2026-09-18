package com.bjworld21.congress.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MailAddressBookRequest {
    @NotBlank(message = "주소록명은 필수입니다.")
    @Size(max = 255, message = "주소록명은 255자 이하로 입력해야 합니다.")
    private String addressBookName;

    @Size(max = 1000, message = "설명은 1000자 이하로 입력해야 합니다.")
    private String description;
}
