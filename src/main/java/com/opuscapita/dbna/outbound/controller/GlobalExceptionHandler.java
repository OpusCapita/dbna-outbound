package com.opuscapita.dbna.outbound.controller;

import com.opuscapita.dbna.outbound.exception.DBNAException;
import com.opuscapita.dbna.outbound.model.AS4SendResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import javax.naming.NamingException;

/**
 * Global exception handler for REST API
 * 
 * Handles:
 * - DBNAException and subclasses
 * - NamingException (DNS-related errors)
 * - IllegalArgumentException (validation errors)
 * - Generic exceptions (unexpected errors)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * Handle DBNA-specific exceptions
     */
    @ExceptionHandler(DBNAException.class)
    public ResponseEntity<AS4SendResponse> handleDBNAException(DBNAException e) {
        logger.error("DBNA Exception [{}]: {}", e.getErrorCode(), e.getMessage());
        
        AS4SendResponse errorResponse = AS4SendResponse.builder()
            .success(false)
            .status(e.getErrorCode())
            .errorMessage(e.getMessage())
            .timestamp(System.currentTimeMillis())
            .build();
        
        return ResponseEntity.status(e.getHttpStatusCode()).body(errorResponse);
    }
    
    /**
     * Handle NamingException (DNS lookup failures)
     */
    @ExceptionHandler(NamingException.class)
    public ResponseEntity<AS4SendResponse> handleNamingException(NamingException e) {
        logger.error("DNS/Naming Exception: {}", e.getMessage(), e);
        
        String userMessage = buildNamingExceptionMessage(e);
        
        AS4SendResponse errorResponse = AS4SendResponse.builder()
            .success(false)
            .status("SML_LOOKUP_ERROR")
            .errorMessage(userMessage)
            .timestamp(System.currentTimeMillis())
            .build();
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
    }
    
    /**
     * Handle IllegalArgumentException (validation errors)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<AS4SendResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        logger.warn("Validation Error: {}", e.getMessage());
        
        AS4SendResponse errorResponse = AS4SendResponse.builder()
            .success(false)
            .status("VALIDATION_ERROR")
            .errorMessage(e.getMessage())
            .timestamp(System.currentTimeMillis())
            .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
    
    /**
     * Handle 404 Not Found errors
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<AS4SendResponse> handleNoHandlerFound(NoHandlerFoundException e) {
        logger.warn("Endpoint not found: {} {}", e.getHttpMethod(), e.getRequestURL());
        
        AS4SendResponse errorResponse = AS4SendResponse.builder()
            .success(false)
            .status("NOT_FOUND")
            .errorMessage("Endpoint not found: " + e.getRequestURL())
            .timestamp(System.currentTimeMillis())
            .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * Handle static resource not found (e.g., favicon.ico, CSS, JS files)
     * Returns 404 NOT_FOUND instead of 500 INTERNAL_SERVER_ERROR
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<AS4SendResponse> handleNoResourceFound(NoResourceFoundException e) {
        logger.debug("Static resource not found: {}", e.getMessage());

        AS4SendResponse errorResponse = AS4SendResponse.builder()
            .success(false)
            .status("NOT_FOUND")
            .errorMessage("Static resource not found")
            .timestamp(System.currentTimeMillis())
            .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }
    
    /**
     * Handle all other unexpected exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<AS4SendResponse> handleGenericException(Exception e) {
        logger.error("Unexpected exception", e);
        
        AS4SendResponse errorResponse = AS4SendResponse.builder()
            .success(false)
            .status("INTERNAL_SERVER_ERROR")
            .errorMessage("Internal server error: An unexpected error occurred")
            .timestamp(System.currentTimeMillis())
            .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
    
    /**
     * Build a user-friendly message for NamingException
     */
    private String buildNamingExceptionMessage(NamingException e) {
        String cause = e.getCause() != null ? e.getCause().getClass().getSimpleName() : e.getClass().getSimpleName();
        
        if (cause.contains("NameNotFoundException") || e.getMessage().contains("not found in SML registry")) {
            return "Receiver is not registered in the DBNA SML registry. Please verify the receiver identifier is correct and registered.";
        } else if (cause.contains("ServiceUnavailableException")) {
            return "DNS service is currently unavailable. Please try again later.";
        } else if (cause.contains("CommunicationException")) {
            return "Network communication error while querying SML. Please check your network connection and DNS settings.";
        } else {
            return "SML lookup failed: " + e.getMessage();
        }
    }
}

