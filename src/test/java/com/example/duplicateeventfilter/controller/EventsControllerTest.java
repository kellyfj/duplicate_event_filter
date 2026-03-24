package com.example.duplicateeventfilter.controller;

import com.example.duplicateeventfilter.exception.DeduplicationStoreUnavailableException;
import com.example.duplicateeventfilter.model.EventRequest;
import com.example.duplicateeventfilter.model.EventResponse;
import com.example.duplicateeventfilter.service.DeduplicationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventsControllerTest {

    @Mock
    private DeduplicationService deduplicationService;

    @InjectMocks
    private EventsController controller;

    private EventRequest validEvent() {
        return new EventRequest(
            "019612ab-1c3e-7000-8000-3e5f6a7b8c9d",
            "ORDER_CREATED",
            "order-99",
            OffsetDateTime.parse("2026-03-24T10:11:00Z")
        );
    }

    @Test
    void submitEvent_newEvent_returns202WithReceivedMessage() {
        when(deduplicationService.isDuplicate(org.mockito.ArgumentMatchers.any())).thenReturn(false);

        ResponseEntity<EventResponse> response = controller.submitEvent(validEvent());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("accepted");
        assertThat(response.getBody().getMessage()).isEqualTo("Event received");
    }

    @Test
    void submitEvent_duplicateEvent_returns202WithSuppressedMessage() {
        when(deduplicationService.isDuplicate(org.mockito.ArgumentMatchers.any())).thenReturn(true);

        ResponseEntity<EventResponse> response = controller.submitEvent(validEvent());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("accepted");
        assertThat(response.getBody().getMessage()).isEqualTo("Duplicate event suppressed");
    }

    @Test
    void submitEvent_delegatesToDeduplicationService() {
        EventRequest event = validEvent();
        when(deduplicationService.isDuplicate(event)).thenReturn(false);

        controller.submitEvent(event);

        verify(deduplicationService).isDuplicate(event);
    }

    @Test
    void submitEvent_redisUnavailable_propagatesException() {
        when(deduplicationService.isDuplicate(org.mockito.ArgumentMatchers.any()))
            .thenThrow(new DeduplicationStoreUnavailableException("Redis down", new RuntimeException()));

        assertThatThrownBy(() -> controller.submitEvent(validEvent()))
            .isInstanceOf(DeduplicationStoreUnavailableException.class);
    }
}
