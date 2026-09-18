package com.bjworld21.congress.entity;

import lombok.Data;

@Data
public class ProgramAbstractSnapshot {
    private Long seq;
    private Long programItemSeq;
    private Long abstractSubmissionSeq;
    private String originalTitle;
    private String originalSpeakerText;
    private String originalSpeakersJson;
    private String assignedTitle;
    private String assignedSpeakerText;
    private String assignedSpeakersJson;
}
