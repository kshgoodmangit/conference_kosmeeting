package com.bjworld21.conference.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailContact {
    private Long seq;
    private Long addressBookSeq;
    private String email;
    private String normalizedEmail;
    private String fullName;
    private String affiliation;
    private String country;
    private String phoneNumber;
    private String memo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
