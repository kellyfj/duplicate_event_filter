package com.example.duplicateeventfilter.exception;

/**
 * Thrown when the deduplication backing store (Redis) is unreachable.
 * Controllers should map this to HTTP 503 Service Unavailable.
 */
public class DeduplicationStoreUnavailableException extends RuntimeException {

    public DeduplicationStoreUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
