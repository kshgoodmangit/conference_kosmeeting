package com.bjworld21.conference.service;

import com.bjworld21.conference.dto.ConferenceSettingsResponse;
import com.bjworld21.conference.dto.ConferenceSettingsSaveAllRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConferenceSettingsManagementService {
    private final ConferenceSettingsService conferenceSettingsService;
    private final RegistrationFeeService registrationFeeService;

    public ConferenceSettingsManagementService(
            ConferenceSettingsService conferenceSettingsService,
            RegistrationFeeService registrationFeeService
    ) {
        this.conferenceSettingsService = conferenceSettingsService;
        this.registrationFeeService = registrationFeeService;
    }

    @Transactional
    public ConferenceSettingsResponse saveAll(Long seq, ConferenceSettingsSaveAllRequest request) {
        ConferenceSettingsResponse savedSettings = conferenceSettingsService.saveConfigured(seq, request);
        registrationFeeService.saveAll(savedSettings.getSeq(), request.getCategories(), request.getDeletedCategorySeqs());
        return savedSettings;
    }
}
