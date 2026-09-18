package com.bjworld21.congress.service;

import com.bjworld21.congress.dto.ConferenceSettingsResponse;
import com.bjworld21.congress.dto.ConferenceSettingsSaveAllRequest;
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
        ConferenceSettingsResponse savedSettings = seq == null
                ? conferenceSettingsService.saveSettings(
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
                : conferenceSettingsService.updateSettings(
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

        registrationFeeService.saveAll(savedSettings.getSeq(), request.getCategories(), request.getDeletedCategorySeqs());
        return savedSettings;
    }
}
