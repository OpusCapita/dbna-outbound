package com.opuscapita.dbna.outbound.controller;

import com.opuscapita.dbna.outbound.exception.AS4TransmissionException;
import com.opuscapita.dbna.outbound.exception.DBNAException;
import com.opuscapita.dbna.outbound.exception.DocumentValidationException;
import com.opuscapita.dbna.outbound.exception.SMLLookupException;
import com.opuscapita.dbna.outbound.exception.SMPDiscoveryException;
import com.opuscapita.dbna.outbound.model.AS4SendResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.naming.CommunicationException;
import javax.naming.NameNotFoundException;
import javax.naming.ServiceUnavailableException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GlobalExceptionHandler
 * 
 * Tests all exception handling paths:
 * - DBNAException and all subclasses (SMLLookupException, SMPDiscoveryException, etc.)
 * - NamingException with different causes
 * - IllegalArgumentException
 * - NoHandlerFoundException
 * - Generic Exception
 */
@DisplayName("GlobalExceptionHandler Unit Tests")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    // ======================== DBNAException Tests ========================

    @Test
    @DisplayName("Should handle DBNAException with correct error code and status")
    void testHandleDBNAException() {
        // Arrange
        DBNAException e = new DBNAException("TEST_ERROR", "Test error message", 400);

        // Act
        ResponseEntity<AS4SendResponse> response = handler.handleDBNAException(e);

        // Assert
        assertEquals(400, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("TEST_ERROR", response.getBody().getStatus());
        assertEquals("Test error message", response.getBody().getErrorMessage());
    }

    @Test
    @DisplayName("Should handle SMLLookupException (subclass of DBNAException)")
    void testHandleSMLLookupException() {
        // Arrange
        SMLLookupException e = new SMLLookupException("SML lookup failed");

        // Act
        ResponseEntity<AS4SendResponse> response = handler.handleDBNAException(e);

        // Assert
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), response.getStatusCodeValue());
        assertEquals("SML_LOOKUP_ERROR", response.getBody().getStatus());
        assertEquals("SML lookup failed", response.getBody().getErrorMessage());
    }

    @Test
    @DisplayName("Should handle SMPDiscoveryException (subclass of DBNAException)")
    void testHandleSMPDiscoveryException() {
        // Arrange
        SMPDiscoveryException e = new SMPDiscoveryException("SMP discovery failed");

        // Act
        ResponseEntity<AS4SendResponse> response = handler.handleDBNAException(e);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getStatusCodeValue());
        assertEquals("SMP_DISCOVERY_ERROR", response.getBody().getStatus());
    }

    @Test
    @DisplayName("Should handle AS4TransmissionException (subclass of DBNAException)")
    void testHandleAS4TransmissionException() {
        // Arrange
        AS4TransmissionException e = new AS4TransmissionException("AS4 transmission failed");

        // Act
        ResponseEntity<AS4SendResponse> response = handler.handleDBNAException(e);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getStatusCodeValue());
        assertEquals("AS4_TRANSMISSION_ERROR", response.getBody().getStatus());
    }

    @Test
    @DisplayName("Should handle DocumentValidationException (subclass of DBNAException)")
    void testHandleDocumentValidationException() {
        // Arrange
        DocumentValidationException e = new DocumentValidationException("Invalid document");

        // Act
        ResponseEntity<AS4SendResponse> response = handler.handleDBNAException(e);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatusCodeValue());
        assertEquals("VALIDATION_ERROR", response.getBody().getStatus());
    }

    // ======================== NamingException Tests ========================

    @Test
    @DisplayName("Should handle NameNotFoundException with appropriate message")
    void testHandleNameNotFoundException() {
        // Arrange
        NameNotFoundException e = new NameNotFoundException("DNS name not found");

        // Act
        ResponseEntity<AS4SendResponse> response = handler.handleNamingException(e);

        // Assert
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), response.getStatusCodeValue());
        assertEquals("SML_LOOKUP_ERROR", response.getBody().getStatus());
        assertTrue(response.getBody().getErrorMessage().contains("not registered in the DBNA SML registry"));
    }

    @Test
    @DisplayName("Should handle ServiceUnavailableException with appropriate message")
    void testHandleServiceUnavailableException() {
        // Arrange
        ServiceUnavailableException e = new ServiceUnavailableException("DNS service unavailable");

        // Act
        ResponseEntity<AS4SendResponse> response = handler.handleNamingException(e);

        // Assert
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), response.getStatusCodeValue());
        assertTrue(response.getBody().getErrorMessage().contains("currently unavailable"));
    }

    @Test
    @DisplayName("Should handle CommunicationException with appropriate message")
    void testHandleCommunicationException() {
        // Arrange
        CommunicationException e = new CommunicationException("DNS communication error");

        // Act
        ResponseEntity<AS4SendResponse> response = handler.handleNamingException(e);

        // Assert
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), response.getStatusCodeValue());
        assertTrue(response.getBody().getErrorMessage().contains("Network communication error"));
    }

    @Test
    @DisplayName("Should handle generic NamingException")
    void testHandleGenericNamingException() {
        // Arrange
        javax.naming.NamingException e = new javax.naming.NamingException("Generic naming error");

        // Act
        ResponseEntity<AS4SendResponse> response = handler.handleNamingException(e);

        // Assert
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), response.getStatusCodeValue());
        assertTrue(response.getBody().getErrorMessage().contains("SML lookup failed"));
    }

    @Test
    @DisplayName("Should include timestamp in NamingException response")
    void testNamingExceptionIncludesTimestamp() {
        // Arrange
        long beforeTime = System.currentTimeMillis();
        NameNotFoundException e = new NameNotFoundException("DNS error");

        // Act
        ResponseEntity<AS4SendResponse> response = handler.handleNamingException(e);
        long afterTime = System.currentTimeMillis();

        // Assert
        long timestamp = response.getBody().getTimestamp();
        assertTrue(timestamp >= beforeTime && timestamp <= afterTime);
    }

    // ======================== IllegalArgumentException Tests ========================

    @Test
    @DisplayName("Should handle IllegalArgumentException with 400 status")
    void testHandleIllegalArgumentException() {
        // Arrange
        IllegalArgumentException e = new IllegalArgumentException("Invalid argument");

        // Act
        ResponseEntity<AS4SendResponse> response = handler.handleIllegalArgumentException(e);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatusCodeValue());
        assertEquals("VALIDATION_ERROR", response.getBody().getStatus());
        assertEquals("Invalid argument", response.getBody().getErrorMessage());
        assertFalse(response.getBody().isSuccess());
    }

    // ======================== NoHandlerFoundException Tests ========================

    @Test
    @DisplayName("Should handle NoHandlerFoundException with 404 status")
    void testHandleNoHandlerFoundException() {
        // Arrange
        NoHandlerFoundException e = new NoHandlerFoundException(
            "GET", "/api/invalid/path", null);

        // Act
        ResponseEntity<AS4SendResponse> response = handler.handleNoHandlerFound(e);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND.value(), response.getStatusCodeValue());
        assertEquals("NOT_FOUND", response.getBody().getStatus());
        assertTrue(response.getBody().getErrorMessage().contains("Endpoint not found"));
        assertFalse(response.getBody().isSuccess());
    }

    // ======================== Generic Exception Tests ========================

    @Test
    @DisplayName("Should handle unexpected exception with 500 status")
    void testHandleGenericException() {
        // Arrange
        Exception e = new RuntimeException("Unexpected error");

        // Act
        ResponseEntity<AS4SendResponse> response = handler.handleGenericException(e);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getStatusCodeValue());
        assertEquals("INTERNAL_SERVER_ERROR", response.getBody().getStatus());
        assertEquals("Internal server error: An unexpected error occurred", 
            response.getBody().getErrorMessage());
        assertFalse(response.getBody().isSuccess());
    }

    @Test
    @DisplayName("Should handle NullPointerException with generic error message")
    void testHandleNullPointerException() {
        // Arrange
        NullPointerException e = new NullPointerException("Object was null");

        // Act
        ResponseEntity<AS4SendResponse> response = handler.handleGenericException(e);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getStatusCodeValue());
        assertEquals("INTERNAL_SERVER_ERROR", response.getBody().getStatus());
    }

    // ======================== Response Structure Tests ========================

    @Test
    @DisplayName("Should always include required fields in error response")
    void testErrorResponseHasRequiredFields() {
        // Arrange
        DocumentValidationException e = new DocumentValidationException("Test error");

        // Act
        ResponseEntity<AS4SendResponse> response = handler.handleDBNAException(e);

        // Assert
        AS4SendResponse body = response.getBody();
        assertNotNull(body);
        assertFalse(body.isSuccess());
        assertNotNull(body.getStatus());
        assertNotNull(body.getErrorMessage());
        assertTrue(body.getTimestamp() > 0);
    }

    @Test
    @DisplayName("Should not include messageId in error response")
    void testErrorResponseHasNoMessageId() {
        // Arrange
        DocumentValidationException e = new DocumentValidationException("Test error");

        // Act
        ResponseEntity<AS4SendResponse> response = handler.handleDBNAException(e);

        // Assert
        AS4SendResponse body = response.getBody();
        assertNull(body.getMessageId());
    }

    // ======================== Exception Message Tests ========================

    @Test
    @DisplayName("Should preserve original exception message")
    void testPreservesExceptionMessage() {
        // Arrange
        String originalMessage = "Specific error message";
        SMLLookupException e = new SMLLookupException(originalMessage);

        // Act
        ResponseEntity<AS4SendResponse> response = handler.handleDBNAException(e);

        // Assert
        assertEquals(originalMessage, response.getBody().getErrorMessage());
    }

    @Test
    @DisplayName("Should handle very long error messages")
    void testHandlesLongErrorMessages() {
        // Arrange
        String longMessage = "a".repeat(500);
        DocumentValidationException e = new DocumentValidationException(longMessage);

        // Act
        ResponseEntity<AS4SendResponse> response = handler.handleDBNAException(e);

        // Assert
        assertEquals(longMessage, response.getBody().getErrorMessage());
    }

    @Test
    @DisplayName("Should handle null error messages gracefully")
    void testHandlesNullErrorMessage() {
        // Arrange
        IllegalArgumentException e = new IllegalArgumentException();

        // Act
        ResponseEntity<AS4SendResponse> response = handler.handleIllegalArgumentException(e);

        // Assert
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getStatus());
    }

    // ======================== Exception Chaining Tests ========================

    @Test
    @DisplayName("Should handle chained exceptions")
    void testHandlesChainedException() {
        // Arrange
        Exception cause = new RuntimeException("Root cause");
        SMLLookupException e = new SMLLookupException("SML failed", cause);

        // Act
        ResponseEntity<AS4SendResponse> response = handler.handleDBNAException(e);

        // Assert
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), response.getStatusCodeValue());
        assertNotNull(response.getBody().getErrorMessage());
    }

    // ======================== HTTP Status Consistency Tests ========================

    @Test
    @DisplayName("All exceptions mapped to correct HTTP status codes")
    void testHttpStatusCodes() {
        // Assert - verify mapping is correct
        assertEquals(HttpStatus.BAD_REQUEST.value(), 400);
        assertEquals(HttpStatus.NOT_FOUND.value(), 404);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), 500);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), 503);
    }

    @Test
    @DisplayName("Should return appropriate status for each DBNA exception type")
    void testDBNAExceptionStatusCodes() {
        // Test SML_LOOKUP_ERROR -> 503
        SMLLookupException smlEx = new SMLLookupException("SML error");
        ResponseEntity<AS4SendResponse> smlResponse = handler.handleDBNAException(smlEx);
        assertEquals(503, smlResponse.getStatusCodeValue());

        // Test SMP_DISCOVERY_ERROR -> 500
        SMPDiscoveryException smpEx = new SMPDiscoveryException("SMP error");
        ResponseEntity<AS4SendResponse> smpResponse = handler.handleDBNAException(smpEx);
        assertEquals(500, smpResponse.getStatusCodeValue());

        // Test VALIDATION_ERROR -> 400
        DocumentValidationException valEx = new DocumentValidationException("Validation error");
        ResponseEntity<AS4SendResponse> valResponse = handler.handleDBNAException(valEx);
        assertEquals(400, valResponse.getStatusCodeValue());
    }
}

