package com.bjworld21.congress.service;

public class EmbeddingServiceException extends RuntimeException {
    public EmbeddingServiceException(String message) {
        super(message);
    }

    public EmbeddingServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
