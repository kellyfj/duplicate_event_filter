package com.example.duplicateeventfilter.controller;

import com.example.duplicateeventfilter.api.EventsApi;
import com.example.duplicateeventfilter.model.EventRequest;
import com.example.duplicateeventfilter.model.EventResponse;
import com.example.duplicateeventfilter.service.DeduplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EventsController implements EventsApi {

    private final DeduplicationService deduplicationService;

    @Override
    public ResponseEntity<EventResponse> submitEvent(EventRequest eventRequest) {
        boolean duplicate = deduplicationService.isDuplicate(eventRequest);
        EventResponse response = new EventResponse();
        response.setStatus("accepted");
        response.setMessage(duplicate ? "Duplicate event suppressed" : "Event received");
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
