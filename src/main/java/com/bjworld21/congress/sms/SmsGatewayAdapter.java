package com.bjworld21.congress.sms;

public interface SmsGatewayAdapter {
    String providerKey();
    // Implement provider-specific byte limits, supported destinations and sender registration checks.
    void validate(Dispatch request);
    // Invoked only by a future explicit dispatch worker, never by preparation.
    Receipt send(Dispatch request);
    record Dispatch(Long recipientSeq, String idempotencyKey, String senderNumber, String recipientNumber,
                    String title, String message, String messageType) {}
    // Provider acceptance is distinct from delivery. Future verified callbacks update delivery status.
    record Receipt(String providerMessageId) {}
}
