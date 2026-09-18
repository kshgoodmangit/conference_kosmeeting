package com.bjworld21.congress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberDetailResponse {
    private MemberListResponse member;
    private List<MemberPreRegistrationResponse> preRegistrations;
    private List<MemberAbstractSubmissionResponse> abstractSubmissions;
}
