package com.bjworld21.congress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConferenceSettingsSaveAllRequest {
    private String eventName;
    private LocalDate eventStartDate;
    private LocalDate eventEndDate;
    private LocalDate earlyBirdStartDate;
    private LocalDate earlyBirdEndDate;
    private LocalDate regularStartDate;
    private LocalDate regularEndDate;
    private String registrationCurrency;
    private LocalDate abstractStartDate;
    private LocalDate abstractEndDate;
    private LocalDate presentationMaterialStartDate;
    private LocalDate presentationMaterialEndDate;
    private String venueAddress;
    private List<RegistrationFeeCategoryRequest> categories;
    private List<Long> deletedCategorySeqs;
}
