package com.bjworld21.conference.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@lombok.EqualsAndHashCode(callSuper = true)
public class MailCampaignRequest extends MailRecipientSelectionRequest {
    @NotBlank(message = "메일 제목은 필수입니다.")
    @Size(max = 500)
    private String subject;

    @NotBlank(message = "발신자명은 필수입니다.")
    @Size(max = 255)
    private String senderName;

    @NotBlank(message = "발신 이메일은 필수입니다.")
    @Email(message = "발신 이메일 형식이 올바르지 않습니다.")
    private String senderEmail;

    @Email(message = "회신 이메일 형식이 올바르지 않습니다.")
    private String replyToEmail;

    @NotBlank(message = "HTML 본문은 필수입니다.")
    private String htmlContent;

    private String textContent;

    @Pattern(regexp = "ADVERTISEMENT|INFORMATION", message = "메일 유형이 올바르지 않습니다.")
    private String mailType = "ADVERTISEMENT";

    private Boolean trackOpens = true;
    private Boolean trackClicks = true;
    private LocalDateTime scheduledAt;
    private Integer versionNo;
}
