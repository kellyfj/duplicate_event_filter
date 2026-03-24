package com.example.duplicateeventfilter.service;

import com.example.duplicateeventfilter.model.EventRequest;

/**
 * Provides atomic check-and-record deduplication using a backing store with TTL support.
 *
 * <p>A deduplication key is derived from the event's {@code sourceId}, {@code eventType},
 * {@code entityId}, and 15-minute timestamp bucket. The key is SHA-256 hashed before storage.
 */
public interface DeduplicationService {

    /**
     * Atomically checks whether the event has been seen within the deduplication window
     * and, if not, records it.
     *
     * <p>Uses a single atomic SET-if-not-exists + TTL operation so there is no race condition
     * between the check and the record steps.
     *
     * @param event the incoming event — must have non-null sourceId, eventType, entityId, timestamp
     * @return {@code true} if the event is a <em>duplicate</em> (already seen within the window);
     *         {@code false} if the event is new and has been recorded
     * @throws com.example.duplicateeventfilter.exception.DeduplicationStoreUnavailableException
     *         if the backing store (Redis) cannot be reached
     */
    boolean isDuplicate(EventRequest event);
}
