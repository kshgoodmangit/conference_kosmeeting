package com.bjworld21.congress.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Speaker {
    private Long seq;
    private Long conferenceSeq;
    private Long speakerTypeCode;
    private String speakerTypeName;
    private String displayName;
    private String displayNameKo;
    private String affiliation;
    private String department;
    private String positionTitle;
    private String countryCode;
    private String countryName;
    private String countryNameEn;
    private String biography;
    private String profileImageOriFilename;
    private String profileImageSaveFilename;
    private String homepageUrl;
    private String contactEmail;
    private Boolean featured;
    private Boolean enabled;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
