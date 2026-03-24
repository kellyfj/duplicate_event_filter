package com.example.duplicateeventfilter.service;

import com.example.duplicateeventfilter.exception.DeduplicationStoreUnavailableException;
import com.example.duplicateeventfilter.model.EventRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisDeduplicationService implements DeduplicationService {

    private static final long BUCKET_SIZE_SECONDS = 900L;
    private static final String KEY_PREFIX = "dedup:";
    private static final String SENTINEL_VALUE = "1";

    private final StringRedisTemplate redisTemplate;

    @Value("${app.deduplication.ttl-seconds:900}")
    private long ttlSeconds;

    @Override
    public boolean isDuplicate(EventRequest event) {
        String rawKey = buildRawKey(event);
        String hashedKey = KEY_PREFIX + sha256(rawKey);

        try {
            Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(hashedKey, SENTINEL_VALUE, ttlSeconds, TimeUnit.SECONDS);

            boolean duplicate = !Boolean.TRUE.equals(isNew);
            if (duplicate) {
                log.info("action=dedup_check outcome=duplicate dedupKey={}", hashedKey);
            } else {
                log.info("action=dedup_check outcome=accepted dedupKey={}", hashedKey);
            }
            return duplicate;
        } catch (RedisConnectionFailureException ex) {
            log.error("action=dedup_check outcome=store_unavailable error={}", ex.getMessage());
            throw new DeduplicationStoreUnavailableException(
                "Deduplication store is currently unavailable", ex);
        }
    }

    private String buildRawKey(EventRequest event) {
        long bucketEpoch = computeBucket(event.getTimestamp());
        return event.getSourceId()
            + ":" + event.getEventType()
            + ":" + event.getEntityId()
            + ":" + bucketEpoch;
    }

    private long computeBucket(OffsetDateTime timestamp) {
        long epochSeconds = timestamp.toEpochSecond();
        return (epochSeconds / BUCKET_SIZE_SECONDS) * BUCKET_SIZE_SECONDS;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }
}
