package com.opuscapita.dbna.outbound.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HealthController
 * Tests health check endpoints
 */
@DisplayName("HealthController Unit Tests")
class HealthControllerTest {

    private HealthController controller;

    @BeforeEach
    void setUp() {
        controller = new HealthController();
    }

    @Test
    @DisplayName("Should return OK status and running message for health check")
    void testHealthCheckSuccess() {
        // Act
        ResponseEntity<String> response = controller.check();

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("AS4 Outbound Service is running", response.getBody());
    }

    @Test
    @DisplayName("Should return 200 OK status code")
    void testHealthCheckStatusCode() {
        // Act
        ResponseEntity<String> response = controller.check();

        // Assert
        assertEquals(200, response.getStatusCodeValue());
    }

    @Test
    @DisplayName("Should return non-null response body")
    void testHealthCheckResponseNotNull() {
        // Act
        ResponseEntity<String> response = controller.check();

        // Assert
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isEmpty());
    }
}

