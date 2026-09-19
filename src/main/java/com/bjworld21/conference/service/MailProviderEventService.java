package com.bjworld21.conference.service;

import com.bjworld21.conference.config.PersonalDataProperties;
import com.bjworld21.conference.dto.NormalizedMailEvent;
import com.bjworld21.conference.entity.MailProviderEvent;
import com.bjworld21.conference.repository.MailEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Service
public class MailProviderEventService {
    private static final Set<String> EVENT_TYPES = Set.of(
            "ACCEPTED", "DELIVERED", "DEFERRED", "SOFT_BOUNCE", "HARD_BOUNCE",
            "COMPLAINT", "OPENED", "CLICKED", "UNSUBSCRIBED", "DROPPED", "REJECTED", "UNKNOWN"
    );

    private final MailEventRepository repository;
    private final MailSuppressionService suppressionService;
    private final PersonalDataProperties personalDataProperties;

    public MailProviderEventService(MailEventRepository repository, MailSuppressionService suppressionService,
                                    PersonalDataProperties personalDataProperties) {
        this.repository = repository;
        this.suppressionService = suppressionService;
        this.personalDataProperties = personalDataProperties;
    }

    @Transactional
    public boolean recordVerifiedEvent(NormalizedMailEvent event) {
        validate(event);
        MailProviderEvent inbox = MailProviderEvent.builder()
                .provider(event.getProvider())
                .providerEventId(event.getProviderEventId())
                .providerMessageId(event.getProviderMessageId())
                .eventType(event.getEventType())
                .recipientEmail(event.getRecipientEmail())
                .occurredAt(event.getOccurredAt())
                .signatureVerified(true)
                .payloadJson(event.getPayloadJson())
                .build();
        if (repository.insertProviderEvent(inbox, dbEncString()) == 0) {
            return false;
        }

        Long resultSeq = event.getProviderMessageId() == null ? null
                : repository.findSendResult(event.getProvider(), event.getProviderMessageId());
        if (resultSeq == null) {
            repository.updateProviderEventStatus(inbox.getSeq(), "UNMATCHED", "연결할 발송 결과가 없습니다.");
            return true;
        }
        if (repository.insertRecipientEvent(inbox.getSeq(), resultSeq, event) == 0) {
            repository.updateProviderEventStatus(inbox.getSeq(), "DUPLICATE", null);
            return false;
        }

        applyAggregate(resultSeq, inbox.getSeq(), event);
        repository.updateProviderEventStatus(inbox.getSeq(), "PROCESSED", null);
        return true;
    }

    private void applyAggregate(Long resultSeq, Long providerEventSeq, NormalizedMailEvent event) {
        LocalDateTime occurredAt = event.getOccurredAt();
        switch (event.getEventType()) {
            case "ACCEPTED" -> repository.markAccepted(resultSeq, event.getProviderMessageId(), occurredAt);
            case "DELIVERED" -> repository.markDelivered(resultSeq, occurredAt);
            case "DEFERRED" -> repository.markDeferred(
                    resultSeq, occurredAt, event.getFailureCode(), event.getFailureReason());
            case "SOFT_BOUNCE" -> repository.markSoftBounce(
                    resultSeq, occurredAt, event.getFailureCode(), event.getFailureReason());
            case "HARD_BOUNCE" -> {
                repository.markHardBounce(resultSeq, occurredAt, event.getFailureCode(), event.getFailureReason());
                suppressEvent(event, "HARD_BOUNCE", providerEventSeq);
            }
            case "COMPLAINT" -> {
                repository.markComplaint(resultSeq, occurredAt);
                suppressEvent(event, "COMPLAINT", providerEventSeq);
            }
            case "OPENED" -> repository.markOpened(resultSeq, occurredAt);
            case "CLICKED" -> repository.markClicked(resultSeq, occurredAt);
            case "UNSUBSCRIBED" -> {
                repository.markUnsubscribed(resultSeq, occurredAt);
                suppressEvent(event, "UNSUBSCRIBE", providerEventSeq);
            }
            default -> {
                // 원본 이벤트는 보관하되 아직 정의되지 않은 집계는 변경하지 않는다.
            }
        }
    }

    private void suppressEvent(NormalizedMailEvent event, String type, Long providerEventSeq) {
        if (event.getRecipientEmail() == null || event.getRecipientEmail().isBlank()) {
            return;
        }
        String normalizedEmail = MailAddressBookService.normalizeEmail(event.getRecipientEmail());
        suppressionService.suppress(event.getRecipientEmail(), normalizedEmail, type, "PROVIDER_WEBHOOK",
                null, event.getProvider(), event.getFailureReason(), event.getOccurredAt());
        if ("UNSUBSCRIBE".equals(type)) {
            repository.insertUnsubscribeEvent(null, event.getRecipientEmail(), normalizedEmail,
                    "PROVIDER_WEBHOOK", event.getProvider(), providerEventSeq, null, event.getOccurredAt(),
                    dbEncString());
        }
    }

    private void validate(NormalizedMailEvent event) {
        if (event == null || event.getProvider() == null || event.getProvider().isBlank()
                || event.getProviderEventId() == null || event.getProviderEventId().isBlank()
                || event.getEventType() == null || !EVENT_TYPES.contains(event.getEventType())
                || event.getOccurredAt() == null) {
            throw new IllegalArgumentException("정규화된 메일 이벤트의 필수 값이 올바르지 않습니다.");
        }
    }
    private String dbEncString() {
        return personalDataProperties.requireDbEncString();
    }
}
