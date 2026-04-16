package com.opuscapita.dbna.outbound.controller;

import com.opuscapita.dbna.outbound.model.AS4SendRequest;
import com.opuscapita.dbna.outbound.model.AS4SendResponse;
import com.opuscapita.dbna.outbound.service.AS4SendService;
import com.opuscapita.dbna.outbound.test.TestResourceLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AS4SendController
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AS4SendController Unit Tests")
class AS4SendControllerTest {

    @Mock
    private AS4SendService as4SendService;

    private AS4SendController controller;

    private String testDocumentContent;

    @BeforeEach
    void setUp() throws Exception {
        controller = new AS4SendController();
        // Use reflection to inject the mock service
        java.lang.reflect.Field field = AS4SendController.class.getDeclaredField("as4SendService");
        field.setAccessible(true);
        field.set(controller, as4SendService);
// ...existing code...
        // Load test document
        testDocumentContent = TestResourceLoader.loadTestInvoice();
    }

    @Test
    @DisplayName("Should successfully send document with all path variables")
    void testSendDocumentSuccess() throws Exception {
        // Arrange
        String senderId = "sender-001";
        String receiverId = "receiver-001";
        String docTypeId = "invoice";
        String processId = "urn:dbna:process:invoice:1.0";
        // Endpoint: POST /as4/sendas4/{senderId}/{receiverId}/{docTypeId}/{processId}

        AS4SendResponse successResponse = AS4SendResponse.builder()
            .success(true)
            .messageId("TEST-MSG-123")
            .status("SENT")
            .timestamp(System.currentTimeMillis())
            .build();

        when(as4SendService.sendAS4Message(any(AS4SendRequest.class)))
            .thenReturn(successResponse);

        // Act
        ResponseEntity<AS4SendResponse> response = controller.sendDocument(
            senderId, receiverId, docTypeId, processId, testDocumentContent);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("TEST-MSG-123", response.getBody().getMessageId());
        assertEquals("SENT", response.getBody().getStatus());

        // Verify the service was called with correct request
        ArgumentCaptor<AS4SendRequest> requestCaptor = ArgumentCaptor.forClass(AS4SendRequest.class);
        verify(as4SendService, times(1)).sendAS4Message(requestCaptor.capture());

        AS4SendRequest capturedRequest = requestCaptor.getValue();
        assertEquals(senderId, capturedRequest.getSenderId());
        assertEquals(receiverId, capturedRequest.getReceiverId());
        assertEquals(docTypeId, capturedRequest.getDocumentType());
        assertEquals(processId, capturedRequest.getProcessId());
        assertEquals(testDocumentContent, capturedRequest.getUblDocumentContent());
    }

    @Test
    @DisplayName("Should return bad request when document content is empty")
    void testSendDocumentWithEmptyContent() throws Exception {
        // Arrange
        String senderId = "sender-001";
        String receiverId = "receiver-001";
        String docTypeId = "invoice";
        String processId = "urn:dbna:process:invoice:1.0";
        String emptyContent = "";

        // Act
        ResponseEntity<AS4SendResponse> response = controller.sendDocument(
            senderId, receiverId, docTypeId, processId, emptyContent);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("VALIDATION_ERROR", response.getBody().getStatus());
        assertEquals("Document content is required", response.getBody().getErrorMessage());

        // Verify service was not called
        verify(as4SendService, never()).sendAS4Message(any());
    }

    @Test
    @DisplayName("Should return bad request when document content is null")
    void testSendDocumentWithNullContent() throws Exception {
        // Arrange
        String senderId = "sender-001";
        String receiverId = "receiver-001";
        String docTypeId = "invoice";
        String processId = "urn:dbna:process:invoice:1.0";

        // Act
        ResponseEntity<AS4SendResponse> response = controller.sendDocument(
            senderId, receiverId, docTypeId, processId, null);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("VALIDATION_ERROR", response.getBody().getStatus());
        assertEquals("Document content is required", response.getBody().getErrorMessage());

        // Verify service was not called
        verify(as4SendService, never()).sendAS4Message(any());
    }

    @Test
    @DisplayName("Should return internal server error when send fails")
    void testSendDocumentServiceFailure() throws Exception {
        // Arrange
        String senderId = "sender-001";
        String receiverId = "receiver-001";
        String docTypeId = "invoice";
        String processId = "urn:dbna:process:invoice:1.0";

        AS4SendResponse failureResponse = AS4SendResponse.builder()
            .success(false)
            .status("ERROR")
            .errorMessage("Failed to send AS4 message")
            .timestamp(System.currentTimeMillis())
            .build();

        when(as4SendService.sendAS4Message(any(AS4SendRequest.class)))
            .thenReturn(failureResponse);

        // Act
        ResponseEntity<AS4SendResponse> response = controller.sendDocument(
            senderId, receiverId, docTypeId, processId, testDocumentContent);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("ERROR", response.getBody().getStatus());
        assertEquals("Failed to send AS4 message", response.getBody().getErrorMessage());
    }

    @Test
    @DisplayName("Should populate all path variables into AS4SendRequest")
    void testPathVariablesPopulation() throws Exception {
        // Arrange
        String senderId = "test-sender-id";
        String receiverId = "test-receiver-id";
        String docTypeId = "invoice";
        String processId = "test-process-id";

        AS4SendResponse successResponse = AS4SendResponse.builder()
            .success(true)
            .messageId("MSG-001")
            .status("SENT")
            .timestamp(System.currentTimeMillis())
            .build();

        when(as4SendService.sendAS4Message(any(AS4SendRequest.class)))
            .thenReturn(successResponse);

        // Act
        controller.sendDocument(senderId, receiverId, docTypeId, processId, testDocumentContent);

        // Assert
        ArgumentCaptor<AS4SendRequest> requestCaptor = ArgumentCaptor.forClass(AS4SendRequest.class);
        verify(as4SendService).sendAS4Message(requestCaptor.capture());

        AS4SendRequest capturedRequest = requestCaptor.getValue();
        assertEquals("test-sender-id", capturedRequest.getSenderId());
        assertEquals("test-receiver-id", capturedRequest.getReceiverId());
        assertEquals("invoice", capturedRequest.getDocumentType());
        assertEquals("test-process-id", capturedRequest.getProcessId());
        assertEquals(testDocumentContent, capturedRequest.getUblDocumentContent());
    }

    @Test
    @DisplayName("Should handle special characters in path variables")
    void testSpecialCharactersInPathVariables() throws Exception {
        // Arrange
        String senderId = "sender:special-123";
        String receiverId = "receiver:special-456";
        String docTypeId = "invoice-ubl";
        String processId = "urn:dbna:process:invoice:1.0";

        AS4SendResponse successResponse = AS4SendResponse.builder()
            .success(true)
            .messageId("MSG-002")
            .status("SENT")
            .timestamp(System.currentTimeMillis())
            .build();

        when(as4SendService.sendAS4Message(any(AS4SendRequest.class)))
            .thenReturn(successResponse);

        // Act
        ResponseEntity<AS4SendResponse> response = controller.sendDocument(
            senderId, receiverId, docTypeId, processId, testDocumentContent);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());

        ArgumentCaptor<AS4SendRequest> requestCaptor = ArgumentCaptor.forClass(AS4SendRequest.class);
        verify(as4SendService).sendAS4Message(requestCaptor.capture());

        AS4SendRequest capturedRequest = requestCaptor.getValue();
        assertEquals("sender:special-123", capturedRequest.getSenderId());
        assertEquals("receiver:special-456", capturedRequest.getReceiverId());
    }

    @Test
    @DisplayName("Health check endpoint should return OK")
    void testHealthCheckEndpoint() {
        // Act
        ResponseEntity<String> response = controller.health();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("AS4 Outbound Service is running", response.getBody());
    }
}




