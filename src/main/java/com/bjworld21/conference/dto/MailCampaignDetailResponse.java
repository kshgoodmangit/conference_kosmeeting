package com.bjworld21.conference.dto;

import com.bjworld21.conference.entity.MailAttachment;
import com.bjworld21.conference.entity.MailCampaign;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailCampaignDetailResponse {
    private MailCampaign campaign;
    private List<String> recipientGroups;
    private List<Long> addressBookSeqs;
    private List<MailDirectRecipientRequest> directRecipients;
    private List<MailAttachment> attachments;
}
