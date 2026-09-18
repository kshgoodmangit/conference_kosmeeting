package com.bjworld21.congress.service;

import com.bjworld21.congress.dto.ConferenceSettingsResponse;
import com.bjworld21.congress.entity.ConferenceSettings;
import com.bjworld21.congress.repository.ConferenceSettingsRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class ConferenceSettingsService {
    private final ConferenceSettingsRepository conferenceSettingsRepository;

    public ConferenceSettingsService(ConferenceSettingsRepository conferenceSettingsRepository) {
        this.conferenceSettingsRepository = conferenceSettingsRepository;
    }

    public ConferenceSettingsResponse getSettings() {
        ConferenceSettings settings = conferenceSettingsRepository.findLatest();
        return settings == null ? ConferenceSettingsResponse.builder().build() : toResponse(settings, null);
    }

    public List<ConferenceSettingsResponse> getSettingsList() {
        return conferenceSettingsRepository.findAll().stream()
                .map(settings -> toResponse(settings, null))
                .toList();
    }

    public Long getLatestConferenceSeq() {
        ConferenceSettings settings = conferenceSettingsRepository.findLatest();
        if (settings == null) {
            throw new IllegalArgumentException("등록된 학회가 없습니다.");
        }
        return settings.getSeq();
    }

    public ConferenceSettingsResponse getSettings(Long conferenceSeq) {
        ConferenceSettings settings = conferenceSettingsRepository.findBySeq(conferenceSeq);
        return settings == null ? ConferenceSettingsResponse.builder().build() : toResponse(settings, null);
    }

    public ConferenceSettingsResponse saveSettings(
            String eventName,
            LocalDate eventStartDate,
            LocalDate eventEndDate,
            LocalDate earlyBirdStartDate,
            LocalDate earlyBirdEndDate,
            LocalDate regularStartDate,
            LocalDate regularEndDate,
            String registrationCurrency,
            LocalDate abstractStartDate,
            LocalDate abstractEndDate,
            LocalDate presentationMaterialStartDate,
            LocalDate presentationMaterialEndDate,
            String venueAddress
    ) {
        validate(
                eventName,
                eventStartDate,
                eventEndDate,
                earlyBirdStartDate,
                earlyBirdEndDate,
                regularStartDate,
                regularEndDate,
                registrationCurrency,
                abstractStartDate,
                abstractEndDate,
                presentationMaterialStartDate,
                presentationMaterialEndDate
        );

        ConferenceSettings settings = ConferenceSettings.builder()
                .eventName(eventName.trim())
                .eventStartDate(eventStartDate)
                .eventEndDate(eventEndDate)
                .earlyBirdStartDate(earlyBirdStartDate)
                .earlyBirdEndDate(earlyBirdEndDate)
                .regularStartDate(regularStartDate)
                .regularEndDate(regularEndDate)
                .registrationCurrency(registrationCurrency.trim().toUpperCase())
                .abstractStartDate(abstractStartDate)
                .abstractEndDate(abstractEndDate)
                .presentationMaterialStartDate(presentationMaterialStartDate)
                .presentationMaterialEndDate(presentationMaterialEndDate)
                .venueAddress(venueAddress != null ? venueAddress.trim() : null)
                .build();

        conferenceSettingsRepository.insert(settings);

        return toResponse(settings, "학회가 추가되었습니다.");
    }

    public ConferenceSettingsResponse updateSettings(
            Long seq,
            String eventName,
            LocalDate eventStartDate,
            LocalDate eventEndDate,
            LocalDate earlyBirdStartDate,
            LocalDate earlyBirdEndDate,
            LocalDate regularStartDate,
            LocalDate regularEndDate,
            String registrationCurrency,
            LocalDate abstractStartDate,
            LocalDate abstractEndDate,
            LocalDate presentationMaterialStartDate,
            LocalDate presentationMaterialEndDate,
            String venueAddress
    ) {
        validate(
                eventName,
                eventStartDate,
                eventEndDate,
                earlyBirdStartDate,
                earlyBirdEndDate,
                regularStartDate,
                regularEndDate,
                registrationCurrency,
                abstractStartDate,
                abstractEndDate,
                presentationMaterialStartDate,
                presentationMaterialEndDate
        );

        ConferenceSettings existingSettings = conferenceSettingsRepository.findBySeq(seq);
        if (existingSettings == null) {
            throw new IllegalArgumentException("수정할 학회 설정을 찾을 수 없습니다.");
        }

        ConferenceSettings settings = ConferenceSettings.builder()
                .seq(seq)
                .eventName(eventName.trim())
                .eventStartDate(eventStartDate)
                .eventEndDate(eventEndDate)
                .earlyBirdStartDate(earlyBirdStartDate)
                .earlyBirdEndDate(earlyBirdEndDate)
                .regularStartDate(regularStartDate)
                .regularEndDate(regularEndDate)
                .registrationCurrency(registrationCurrency.trim().toUpperCase())
                .abstractStartDate(abstractStartDate)
                .abstractEndDate(abstractEndDate)
                .presentationMaterialStartDate(presentationMaterialStartDate)
                .presentationMaterialEndDate(presentationMaterialEndDate)
                .venueAddress(venueAddress != null ? venueAddress.trim() : null)
                .createdAt(existingSettings.getCreatedAt())
                .updatedAt(existingSettings.getUpdatedAt())
                .build();

        conferenceSettingsRepository.update(settings);
        return toResponse(settings, "학회 설정이 수정되었습니다.");
    }

    private void validate(
            String eventName,
            LocalDate eventStartDate,
            LocalDate eventEndDate,
            LocalDate earlyBirdStartDate,
            LocalDate earlyBirdEndDate,
            LocalDate regularStartDate,
            LocalDate regularEndDate,
            String registrationCurrency,
            LocalDate abstractStartDate,
            LocalDate abstractEndDate,
            LocalDate presentationMaterialStartDate,
            LocalDate presentationMaterialEndDate
    ) {
        if (eventName == null || eventName.trim().isEmpty()) {
            throw new IllegalArgumentException("행사명은 필수입니다.");
        }

        validateRange(eventStartDate, eventEndDate, "행사기간");
        validateRange(earlyBirdStartDate, earlyBirdEndDate, "Early Bird 등록기간");
        validateRange(regularStartDate, regularEndDate, "Regular 등록기간");
        validateRange(abstractStartDate, abstractEndDate, "초록제출기간");
        validateRange(presentationMaterialStartDate, presentationMaterialEndDate, "발표자료 등록기간");

        if (earlyBirdEndDate != null && regularStartDate != null && !regularStartDate.isAfter(earlyBirdEndDate)) {
            throw new IllegalArgumentException("Regular 등록 시작일은 Early Bird 등록 종료일보다 늦어야 합니다.");
        }
        if (registrationCurrency == null || !registrationCurrency.trim().matches("[A-Za-z]{3}")) {
            throw new IllegalArgumentException("등록비 통화 코드는 영문 3자리여야 합니다.");
        }
    }

    private void validateRange(LocalDate startDate, LocalDate endDate, String label) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException(label + " 종료일은 시작일보다 빠를 수 없습니다.");
        }
    }

    private ConferenceSettingsResponse toResponse(ConferenceSettings settings, String message) {
        return ConferenceSettingsResponse.builder()
                .seq(settings.getSeq())
                .eventName(settings.getEventName())
                .eventStartDate(settings.getEventStartDate())
                .eventEndDate(settings.getEventEndDate())
                .earlyBirdStartDate(settings.getEarlyBirdStartDate())
                .earlyBirdEndDate(settings.getEarlyBirdEndDate())
                .regularStartDate(settings.getRegularStartDate())
                .regularEndDate(settings.getRegularEndDate())
                .registrationCurrency(settings.getRegistrationCurrency())
                .abstractStartDate(settings.getAbstractStartDate())
                .abstractEndDate(settings.getAbstractEndDate())
                .presentationMaterialStartDate(settings.getPresentationMaterialStartDate())
                .presentationMaterialEndDate(settings.getPresentationMaterialEndDate())
                .venueAddress(settings.getVenueAddress())
                .createdAt(settings.getCreatedAt())
                .updatedAt(settings.getUpdatedAt())
                .message(message)
                .build();
    }
}
