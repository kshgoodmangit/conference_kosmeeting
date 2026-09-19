package com.bjworld21.conference.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Member {
    private Long seq;
    private Long conferenceSeq;
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

