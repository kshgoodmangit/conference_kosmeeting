package com.bjworld21.congress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberRegisterResponse {
    private Long seq;
    private String email;
    private String firstName;
    private String lastName;
    private String memberType;
    private String message;
}

