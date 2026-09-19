package com.bjworld21.conference.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {
        MailAddressBookController.class,
        MailCampaignController.class,
        MailHistoryController.class,
        SmsCampaignController.class,
        MailContentImageController.class,
        MailWebhookController.class,
        MailUnsubscribeController.class
})
public class MailApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> validation(MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = fieldError == null ? "입력값을 확인해 주세요." : fieldError.getDefaultMessage();
        return ResponseEntity.badRequest().body(message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> conflict(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(exception.getMessage());
    }
}
