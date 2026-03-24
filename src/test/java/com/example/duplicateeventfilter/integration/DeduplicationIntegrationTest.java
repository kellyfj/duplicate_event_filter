package com.example.duplicateeventfilter.integration;

import com.example.duplicateeventfilter.model.EventRequest;
import com.example.duplicateeventfilter.service.DeduplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class DeduplicationIntegrationTest {

    private static final int REDIS_PORT = 6379;

    @Container
    static GenericContainer<?> redis =
        new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(REDIS_PORT);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(REDIS_PORT));
    }

    @Autowired
    private DeduplicationService deduplicationService;

    private EventRequest buildEvent(String entityId, String timestamp) {
        return new EventRequest(
            "019612ab-1c3e-7000-8000-3e5f6a7b8c9d",
            "ORDER_CREATED",
            entityId,
            OffsetDateTime.parse(timestamp)
        );
    }

    @Test
    void firstSubmission_isNotDuplicate() {
        EventRequest event = buildEvent("order-integration-1", "2026-03-24T10:11:00Z");

        assertThat(deduplicationService.isDuplicate(event)).isFalse();
    }

    @Test
    void secondSubmissionSameWindow_isDuplicate() {
        EventRequest event = buildEvent("order-integration-2", "2026-03-24T10:11:00Z");
        EventRequest sameWindowEvent = buildEvent("order-integration-2", "2026-03-24T10:13:00Z");

        deduplicationService.isDuplicate(event);

        assertThat(deduplicationService.isDuplicate(sameWindowEvent)).isTrue();
    }

    @Test
    void submissionInDifferentWindow_isNotDuplicate() {
        EventRequest firstWindow = buildEvent("order-integration-3", "2026-03-24T10:11:00Z");
        EventRequest nextWindow = buildEvent("order-integration-3", "2026-03-24T10:15:00Z");

        deduplicationService.isDuplicate(firstWindow);

        assertThat(deduplicationService.isDuplicate(nextWindow)).isFalse();
    }

    @Test
    void differentEntityId_isNotDuplicate() {
        EventRequest event1 = buildEvent("order-integration-4a", "2026-03-24T10:11:00Z");
        EventRequest event2 = buildEvent("order-integration-4b", "2026-03-24T10:11:00Z");

        deduplicationService.isDuplicate(event1);

        assertThat(deduplicationService.isDuplicate(event2)).isFalse();
    }
}
