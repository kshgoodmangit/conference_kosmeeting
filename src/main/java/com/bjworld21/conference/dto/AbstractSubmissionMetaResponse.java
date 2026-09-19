package com.bjworld21.conference.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AbstractSubmissionMetaResponse {
    private List<AbstractPresentationTypeResponse> presentationTypes;
    private List<AbstractCategoryResponse> categories;
    private List<AbstractAiOptionResponse> aiTools;
    private List<AbstractAiOptionResponse> aiScopes;
}
