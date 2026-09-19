package com.bjworld21.conference.service;

import java.util.List;

public record MaintenanceRequestCreatedEvent(
        Long requestSeq,
        String title,
        String content,
        String requesterName,
        List<Recipient> recipients
) {
    public record Recipient(Long notificationSeq, String email, String name) {}
}
