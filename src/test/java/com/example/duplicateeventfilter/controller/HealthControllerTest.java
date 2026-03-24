package com.example.duplicateeventfilter.controller;

import com.example.duplicateeventfilter.model.ComponentHealth;
import com.example.duplicateeventfilter.model.HealthResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthControllerTest {

    @Mock
    private RedisConnectionFactory redisConnectionFactory;

    @InjectMocks
    private HealthController controller;

    @Test
    void getHealth_redisUp_returns200() {
        RedisConnection mockConnection = mock(RedisConnection.class);
        when(redisConnectionFactory.getConnection()).thenReturn(mockConnection);
        when(mockConnection.ping()).thenReturn("PONG");

        ResponseEntity<HealthResponse> response = controller.getHealth();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(HealthResponse.StatusEnum.UP);
        assertThat(response.getBody().getComponents()).containsKey("redis");
        assertThat(response.getBody().getComponents().get("redis").getStatus())
            .isEqualTo(ComponentHealth.StatusEnum.UP);
    }

    @Test
    void getHealth_redisDown_returns503() {
        when(redisConnectionFactory.getConnection())
            .thenThrow(new RuntimeException("Connection refused"));

        ResponseEntity<HealthResponse> response = controller.getHealth();

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(HealthResponse.StatusEnum.DOWN);
        assertThat(response.getBody().getComponents()).containsKey("redis");
        assertThat(response.getBody().getComponents().get("redis").getStatus())
            .isEqualTo(ComponentHealth.StatusEnum.DOWN);
    }
}
