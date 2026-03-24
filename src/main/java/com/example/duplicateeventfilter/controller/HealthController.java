package com.example.duplicateeventfilter.controller;

import com.example.duplicateeventfilter.api.HealthApi;
import com.example.duplicateeventfilter.model.ComponentHealth;
import com.example.duplicateeventfilter.model.HealthResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class HealthController implements HealthApi {

    private final RedisConnectionFactory redisConnectionFactory;

    @Override
    public ResponseEntity<HealthResponse> getHealth() {
        HealthResponse response = new HealthResponse();
        ComponentHealth redisHealth = new ComponentHealth();

        try {
            redisConnectionFactory.getConnection().ping();
            redisHealth.setStatus(ComponentHealth.StatusEnum.UP);
            response.setStatus(HealthResponse.StatusEnum.UP);
            response.putComponentsItem("redis", redisHealth);
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            redisHealth.setStatus(ComponentHealth.StatusEnum.DOWN);
            response.setStatus(HealthResponse.StatusEnum.DOWN);
            response.putComponentsItem("redis", redisHealth);
            return ResponseEntity.status(503).body(response);
        }
    }
}
