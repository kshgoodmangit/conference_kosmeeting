package com.bjworld21.conference.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MailRecipientSelectionRequest {
    @Size(max = 4)
    private List<@NotNull @Pattern(regexp = "ALL_MEMBERS|ALL_REGISTRANTS|ALL_SUBMITTERS|ALL_ACCEPTED") String> recipientGroups = new ArrayList<>();
    private List<@NotNull Long> addressBookSeqs = new ArrayList<>();
    @Valid
    private List<@NotNull MailDirectRecipientRequest> directRecipients = new ArrayList<>();
}
