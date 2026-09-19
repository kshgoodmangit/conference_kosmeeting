package com.bjworld21.conference.service;

import com.bjworld21.conference.dto.ConferenceSettingsResponse;
import com.bjworld21.conference.entity.ConferenceSettings;
import com.bjworld21.conference.repository.ConferenceSettingsRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class ConferenceSettingsService {
    private final ConferenceSettingsRepository conferenceSettingsRepository;

    public ConferenceSettingsService(ConferenceSettingsRepository conferenceSettingsRepository) {
        this.conferenceSettingsRepository = conferenceSettingsRepository;
    }

    @org.springframework.transaction.annotation.Transactional
    public ConferenceSettingsResponse saveConfigured(Long seq, com.bjworld21.conference.dto.ConferenceSettingsSaveAllRequest request) {
        ConferenceSettingsResponse savedSettings = seq == null
                ? saveSettings(
                        request.getEventName(),
                        request.getEventStartDate(),
                        request.getEventEndDate(),
                        request.getEarlyBirdStartDate(),
                        request.getEarlyBirdEndDate(),
                        request.getRegularStartDate(),
                        request.getRegularEndDate(),
                        request.getRegistrationCurrency(),
                        request.getAbstractStartDate(),
                        request.getAbstractEndDate(),
                        request.getPresentationMaterialStartDate(),
                        request.getPresentationMaterialEndDate(),
                        request.getVenueAddress()
                )
                : updateSettings(
                        seq,
                        request.getEventName(),
                        request.getEventStartDate(),
                        request.getEventEndDate(),
                        request.getEarlyBirdStartDate(),
                        request.getEarlyBirdEndDate(),
                        request.getRegularStartDate(),
                        request.getRegularEndDate(),
                        request.getRegistrationCurrency(),
                        request.getAbstractStartDate(),
                        request.getAbstractEndDate(),
                        request.getPresentationMaterialStartDate(),
                        request.getPresentationMaterialEndDate(),
                        request.getVenueAddress()
                );

        savedSettings = savePublicSite(savedSettings.getSeq(), request.getSitePath(),
                request.getDefaultLanguage(), request.getSupportedLanguages());
        return savedSettings;
    }

    public ConferenceSettingsResponse getSettingsBySitePath(String sitePath) {
        ConferenceSettings settings = conferenceSettingsRepository.findBySitePath(sitePath);
        if (settings == null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "학회를 찾을 수 없습니다.");
        }
        return toResponse(settings, null);
    }

    @org.springframework.transaction.annotation.Transactional
    public ConferenceSettingsResponse savePublicSite(Long seq, String sitePath, String defaultLanguage, List<String> languages) {
        ConferenceSettings settings = conferenceSettingsRepository.findBySeq(seq);
        if (settings == null) throw new IllegalArgumentException("학회를 찾을 수 없습니다.");
        String path = sitePath == null ? "" : sitePath.trim().toLowerCase(java.util.Locale.ROOT);
        if (!path.matches("[a-z0-9]+(?:[-_][a-z0-9]+)*") || path.length() > 100
                || java.util.Set.of("admin", "api", "public", "assets", "images", "vendor", "commoncode", "error", "actuator", "webjars", "login", "ko", "en").contains(path)) {
            throw new IllegalArgumentException("학회 경로는 시스템 예약어를 제외한 영문 소문자, 숫자, 하이픈(-), 밑줄(_)로 입력해 주세요.");
        }
        ConferenceSettings duplicate = conferenceSettingsRepository.findBySitePath(path);
        if (duplicate != null && !duplicate.getSeq().equals(seq)) throw new IllegalArgumentException("이미 사용 중인 학회 경로입니다.");
        if (languages == null || languages.isEmpty()) throw new IllegalArgumentException("지원 언어를 한 개 이상 선택해 주세요.");
        List<String> normalized = languages.stream().map(ConferenceSettingsService::normalizeLanguage).toList();
        if (normalized.stream().distinct().count() != normalized.size()) throw new IllegalArgumentException("지원 언어가 중복되었습니다.");
        String fallback = normalizeLanguage(defaultLanguage);
        if (!normalized.contains(fallback)) throw new IllegalArgumentException("기본 언어는 지원 언어 중에서 선택해 주세요.");
        for (String language : normalized) {
            if (conferenceSettingsRepository.countLanguageRouteConflicts(seq, language) > 0)
                throw new IllegalArgumentException("지원 언어 코드와 같은 메뉴 경로가 있습니다: /" + language);
        }
        settings.setSitePath(path);
        settings.setDefaultLanguage(fallback);
        conferenceSettingsRepository.updatePublicSite(settings);
        conferenceSettingsRepository.deleteLanguages(seq);
        for (int i=0; i<normalized.size(); i++) conferenceSettingsRepository.insertLanguage(seq, normalized.get(i), i);
        return toResponse(settings, "학회 설정을 저장했습니다.");
    }

    public static String normalizeLanguage(String language) {
        if (language == null || !language.trim().matches("[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*") || language.length() > 35)
            throw new IllegalArgumentException("언어 코드를 확인해 주세요. (예: ko, en, ja, zh-Hans)");
        return java.util.Locale.forLanguageTag(language.trim()).toLanguageTag();
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
                .sitePath("conference-" + java.util.UUID.randomUUID())
                .defaultLanguage("en")
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
                .sitePath(settings.getSitePath())
                .defaultLanguage(settings.getDefaultLanguage())
                .supportedLanguages(conferenceSettingsRepository.findLanguages(settings.getSeq()))
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
