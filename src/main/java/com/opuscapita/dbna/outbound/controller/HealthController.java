package com.opuscapita.dbna.outbound.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for health check endpoints
 * 
 * Provides diagnostic endpoints for monitoring and health verification
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {
    
    private static final Logger logger = LoggerFactory.getLogger(HealthController.class);
    
    /**
     * Health check endpoint
     * 
     * @return Status message indicating service is running
     */
    @GetMapping("/check")
    public ResponseEntity<String> check() {
        logger.debug("Health check requested");
        return ResponseEntity.ok("AS4 Outbound Service is running");
    }
}

