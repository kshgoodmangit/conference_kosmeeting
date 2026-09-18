package com.bjworld21.congress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewerReviewFilterMetaResponse {
    private List<AbstractPresentationTypeResponse> presentationTypes;
    private List<AbstractCategoryResponse> categories;
}
