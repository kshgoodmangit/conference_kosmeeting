package com.bjworld21.conference.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record AdminAccessRequestInput(
        @NotBlank @Size(max = 200) String affiliation,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 30) @Pattern(regexp = "^[+0-9\\s().-]+$") String contact,
        @NotBlank @Size(max = 1000) String purpose,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) {}
