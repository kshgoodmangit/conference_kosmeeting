package com.bjworld21.conference.dto;

import com.bjworld21.conference.entity.Speaker;
import java.time.LocalDateTime;

public record SpeakerResponse(
        Long seq, Long speakerTypeCode, String speakerTypeName,
        String displayName, String displayNameKo, String affiliation,
        String department, String positionTitle, String countryCode, String countryName, String countryNameEn,
        String biography, String profileImageOriFilename, String profileImageUrl,
        String homepageUrl, String contactEmail, Boolean featured, Boolean enabled,
        Integer sortOrder, LocalDateTime createdAt, LocalDateTime updatedAt
) {
    public static SpeakerResponse from(Speaker speaker) {
        return new SpeakerResponse(
                speaker.getSeq(), speaker.getSpeakerTypeCode(), speaker.getSpeakerTypeName(),
                speaker.getDisplayName(), speaker.getDisplayNameKo(), speaker.getAffiliation(),
                speaker.getDepartment(), speaker.getPositionTitle(), speaker.getCountryCode(), speaker.getCountryName(), speaker.getCountryNameEn(),
                speaker.getBiography(), speaker.getProfileImageOriFilename(),
                speaker.getProfileImageSaveFilename() == null ? null : "/api/admin/speakers/" + speaker.getSeq() + "/image",
                speaker.getHomepageUrl(), speaker.getContactEmail(), speaker.getFeatured(), speaker.getEnabled(),
                speaker.getSortOrder(), speaker.getCreatedAt(), speaker.getUpdatedAt());
    }
}
