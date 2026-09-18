package com.bjworld21.congress.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class SpeakerRequest {
    @NotNull(message = "연자 구분을 선택해주세요.")
    private Long speakerTypeCode;
    @NotBlank(message = "연자명을 입력해주세요.")
    @Size(max = 200, message = "연자명은 200자 이하로 입력해주세요.")
    private String displayName;
    @Size(max = 200, message = "한글명은 200자 이하로 입력해주세요.")
    private String displayNameKo;
    @NotBlank(message = "소속 기관을 입력해주세요.")
    @Size(max = 255, message = "소속 기관은 255자 이하로 입력해주세요.")
    private String affiliation;
    @Size(max = 255, message = "부서·학과는 255자 이하로 입력해주세요.")
    private String department;
    @Size(max = 255, message = "직책·직위는 255자 이하로 입력해주세요.")
    private String positionTitle;
    @Pattern(regexp = "(?i)([a-z]{2})?", message = "국가 코드가 올바르지 않습니다.")
    private String countryCode;
    @Size(max = 50000, message = "약력은 50,000자 이하로 입력해주세요.")
    private String biography;
    @Size(max = 1000, message = "홈페이지 URL은 1000자 이하로 입력해주세요.")
    private String homepageUrl;
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Size(max = 255, message = "이메일은 255자 이하로 입력해주세요.")
    private String contactEmail;
    @NotNull(message = "주요 연자 여부를 선택해주세요.")
    private Boolean featured = false;
    @NotNull(message = "공개 여부를 선택해주세요.")
    private Boolean enabled = true;
    @NotNull(message = "노출 순서를 입력해주세요.")
    @Min(value = 0, message = "노출 순서는 0 이상이어야 합니다.")
    private Integer sortOrder = 0;
    private boolean removeProfileImage;
}
