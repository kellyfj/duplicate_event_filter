package com.example.duplicateeventfilter.service;

import com.example.duplicateeventfilter.exception.DeduplicationStoreUnavailableException;
import com.example.duplicateeventfilter.model.EventRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisDeduplicationServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private RedisDeduplicationService service;

    private EventRequest event;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "ttlSeconds", 900L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        event = new EventRequest(
            "019612ab-1c3e-7000-8000-3e5f6a7b8c9d",
            "ORDER_CREATED",
            "order-99",
            OffsetDateTime.parse("2026-03-24T10:11:00Z")
        );
    }

    @Test
    void isDuplicate_newEvent_returnsFalse() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
            .thenReturn(Boolean.TRUE);

        boolean result = service.isDuplicate(event);

        assertThat(result).isFalse();
    }

    @Test
    void isDuplicate_duplicateEvent_returnsTrue() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
            .thenReturn(Boolean.FALSE);

        boolean result = service.isDuplicate(event);

        assertThat(result).isTrue();
    }

    @Test
    void isDuplicate_nullReturnFromRedis_treatedAsDuplicate() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
            .thenReturn(null);

        boolean result = service.isDuplicate(event);

        assertThat(result).isTrue();
    }

    @Test
    void isDuplicate_sameEventSame15MinBucket_producesSameKey() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
            .thenReturn(Boolean.TRUE);

        EventRequest sameWindowEvent = new EventRequest(
            "019612ab-1c3e-7000-8000-3e5f6a7b8c9d",
            "ORDER_CREATED",
            "order-99",
            OffsetDateTime.parse("2026-03-24T10:14:59Z")
        );

        service.isDuplicate(event);
        service.isDuplicate(sameWindowEvent);

        // Both calls must use the identical Redis key (same 15-min bucket)
        verify(valueOperations, org.mockito.Mockito.times(2))
            .setIfAbsent(
                org.mockito.ArgumentMatchers.matches("^dedup:[0-9a-f]{64}$"),
                eq("1"),
                eq(900L),
                eq(TimeUnit.SECONDS)
            );
    }

    @Test
    void isDuplicate_eventsInDifferentBuckets_produceDifferentKeys() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
            .thenReturn(Boolean.TRUE);

        EventRequest nextWindowEvent = new EventRequest(
            "019612ab-1c3e-7000-8000-3e5f6a7b8c9d",
            "ORDER_CREATED",
            "order-99",
            OffsetDateTime.parse("2026-03-24T10:15:00Z")
        );

        service.isDuplicate(event);
        service.isDuplicate(nextWindowEvent);

        // Capture the two keys used
        org.mockito.ArgumentCaptor<String> keyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(valueOperations, org.mockito.Mockito.times(2))
            .setIfAbsent(keyCaptor.capture(), anyString(), anyLong(), any(TimeUnit.class));

        assertThat(keyCaptor.getAllValues().get(0))
            .isNotEqualTo(keyCaptor.getAllValues().get(1));
    }

    @Test
    void isDuplicate_redisUnavailable_throwsDeduplicationStoreUnavailableException() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
            .thenThrow(new RedisConnectionFailureException("Connection refused"));

        assertThatThrownBy(() -> service.isDuplicate(event))
            .isInstanceOf(DeduplicationStoreUnavailableException.class)
            .hasMessageContaining("unavailable");
    }

    @Test
    void isDuplicate_usesCorrectTtl() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
            .thenReturn(Boolean.TRUE);

        service.isDuplicate(event);

        verify(valueOperations).setIfAbsent(anyString(), anyString(), eq(900L), eq(TimeUnit.SECONDS));
    }

    @Test
    void isDuplicate_keyIsPrefixedWithDedup() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
            .thenReturn(Boolean.TRUE);

        service.isDuplicate(event);

        verify(valueOperations).setIfAbsent(
            org.mockito.ArgumentMatchers.startsWith("dedup:"),
            anyString(),
            anyLong(),
            any(TimeUnit.class)
        );
    }
}
