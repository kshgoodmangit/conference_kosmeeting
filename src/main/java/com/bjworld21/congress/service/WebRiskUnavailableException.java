package com.bjworld21.congress.service;

public class WebRiskUnavailableException extends RuntimeException {
    public WebRiskUnavailableException(String message) {
        super(message);
    }

    public WebRiskUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
