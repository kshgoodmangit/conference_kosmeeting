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
public class MailAddressBook {
    private Long seq;
    private String addressBookName;
    private String description;
    private Long createdBy;
    private Integer contactCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
