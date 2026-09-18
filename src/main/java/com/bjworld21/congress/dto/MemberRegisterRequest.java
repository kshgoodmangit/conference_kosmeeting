package com.bjworld21.congress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberRegisterRequest {
    private String memberType; // 'international' or 'domestic'
    private String email;
    private String password;
    private String firstName;
    private String lastName;
    private String institution;
    private String department;
    private String positionTitle;
    private String country; // for international
    private String mobile;
    private Boolean newsletter;
}

