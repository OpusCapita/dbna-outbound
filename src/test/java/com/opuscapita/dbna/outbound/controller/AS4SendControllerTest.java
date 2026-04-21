package com.opuscapita.dbna.outbound.controller;

import com.opuscapita.dbna.outbound.exception.DocumentValidationException;
import com.opuscapita.dbna.outbound.exception.SMLLookupException;
import com.opuscapita.dbna.outbound.exception.SMPDiscoveryException;
import com.opuscapita.dbna.outbound.exception.AS4TransmissionException;
import com.opuscapita.dbna.outbound.model.AS4SendRequest;
import com.opuscapita.dbna.outbound.model.AS4SendResponse;
import com.opuscapita.dbna.outbound.service.AS4SendService;
import com.opuscapita.dbna.outbound.service.CertificateValidationService;
import com.opuscapita.dbna.outbound.service.SMLLookupService;
import com.opuscapita.dbna.outbound.service.SMPService;
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
 * Tests both basic functionality and DBNA-specific SML/SMP/Certificate validation
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AS4SendController Unit Tests")
class AS4SendControllerTest {

    @Mock
    private AS4SendService as4SendService;
    
    @Mock
    private SMLLookupService smlLookupService;
    
    @Mock
    private SMPService smpService;
    
    @Mock
    private CertificateValidationService certificateValidationService;

    private AS4SendController controller;

    private String testDocumentContent;

    @BeforeEach
    void setUp() throws Exception {
        // Use constructor injection instead of reflection
        controller = new AS4SendController(as4SendService, smlLookupService, smpService, certificateValidationService);

        // Load test document
        testDocumentContent = TestResourceLoader.loadTestInvoice();
    }

    @Test
    @DisplayName("Should successfully send document with all path variables")
    void testSendDocumentSuccess() throws Exception {
        // Arrange
        String senderId = "GLN::1234567890123";
        String receiverId = "GLN::9876543210987";
        String docTypeId = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
        String processId = "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0";
        String smpEndpoint = "https://smp.example.com/";
        String receiverEndpoint = "https://receiver.example.com/as4";

        // Mock SML lookup
        when(smlLookupService.lookupSMPEndpoint("GLN", "9876543210987"))
            .thenReturn(smpEndpoint);
        
        // Mock SMP discovery
        when(smpService.discoverServiceEndpoint(smpEndpoint, receiverId, docTypeId, processId))
            .thenReturn(receiverEndpoint);
        
        // Mock AS4 send
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

        // Act & Assert
        assertThrows(DocumentValidationException.class, () ->
            controller.sendDocument(senderId, receiverId, docTypeId, processId, emptyContent),
            "Should throw DocumentValidationException for empty content");

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

        // Act & Assert
        assertThrows(DocumentValidationException.class, () ->
            controller.sendDocument(senderId, receiverId, docTypeId, processId, null),
            "Should throw DocumentValidationException for null content");

        // Verify service was not called
        verify(as4SendService, never()).sendAS4Message(any());
    }

    @Test
    @DisplayName("Should return internal server error when send fails")
    void testSendDocumentServiceFailure() throws Exception {
        // Arrange
        String senderId = "GLN::1234567890123";
        String receiverId = "GLN::9876543210987";
        String docTypeId = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
        String processId = "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0";
        String smpEndpoint = "https://smp.example.com/";
        String receiverEndpoint = "https://receiver.example.com/as4";

        // Mock SML and SMP
        when(smlLookupService.lookupSMPEndpoint("GLN", "9876543210987"))
            .thenReturn(smpEndpoint);
        when(smpService.discoverServiceEndpoint(smpEndpoint, receiverId, docTypeId, processId))
            .thenReturn(receiverEndpoint);

        AS4SendResponse failureResponse = AS4SendResponse.builder()
            .success(false)
            .status("ERROR")
            .errorMessage("Failed to send AS4 message")
            .timestamp(System.currentTimeMillis())
            .build();

        when(as4SendService.sendAS4Message(any(AS4SendRequest.class)))
            .thenReturn(failureResponse);

        // Act & Assert
        assertThrows(AS4TransmissionException.class, () ->
            controller.sendDocument(senderId, receiverId, docTypeId, processId, testDocumentContent),
            "Should throw AS4TransmissionException when AS4 message transmission fails");
    }

    @Test
    @DisplayName("Should populate all path variables into AS4SendRequest")
    void testPathVariablesPopulation() throws Exception {
        // Arrange
        String senderId = "GLN::1234567890123";
        String receiverId = "GLN::9876543210987";
        String docTypeId = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
        String processId = "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0";
        String smpEndpoint = "https://smp.example.com/";
        String receiverEndpoint = "https://receiver.example.com/as4";

        when(smlLookupService.lookupSMPEndpoint("GLN", "9876543210987"))
            .thenReturn(smpEndpoint);
        when(smpService.discoverServiceEndpoint(smpEndpoint, receiverId, docTypeId, processId))
            .thenReturn(receiverEndpoint);

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
        assertEquals(senderId, capturedRequest.getSenderId());
        assertEquals(receiverId, capturedRequest.getReceiverId());
        assertEquals(docTypeId, capturedRequest.getDocumentType());
        assertEquals(processId, capturedRequest.getProcessId());
    }

    @Test
    @DisplayName("Should handle special characters in path variables")
    void testSpecialCharactersInPathVariables() throws Exception {
        // Arrange
        String senderId = "GLN::1234567890123";
        String receiverId = "0192::9876543210987";  // Different scheme
        String docTypeId = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
        String processId = "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0";
        String smpEndpoint = "https://smp.example.com/";
        String receiverEndpoint = "https://receiver.example.com/as4";

        when(smlLookupService.lookupSMPEndpoint("0192", "9876543210987"))
            .thenReturn(smpEndpoint);
        when(smpService.discoverServiceEndpoint(smpEndpoint, receiverId, docTypeId, processId))
            .thenReturn(receiverEndpoint);

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
    }

    @Test
    @DisplayName("Should validate receiver ID format (scheme::identifier)")
    void testInvalidReceiverIdFormat() throws Exception {
        // Arrange - Invalid format: missing :: delimiter
        String senderId = "GLN::1234567890123";
        String receiverId = "GLN-9876543210987";  // Invalid format
        String docTypeId = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
        String processId = "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0";

        // Act & Assert
        assertThrows(DocumentValidationException.class, () ->
            controller.sendDocument(senderId, receiverId, docTypeId, processId, testDocumentContent),
            "Should throw DocumentValidationException for invalid receiver ID format");

        // SML/SMP should not be called with invalid receiver ID
        verify(smlLookupService, never()).lookupSMPEndpoint(any(), any());
    }

    @Test
    @DisplayName("Should query SML when receiver ID is valid")
    void testSMLLookupIntegration() throws Exception {
        // Arrange
        String senderId = "GLN::1234567890123";
        String receiverId = "GLN::9876543210987";
        String docTypeId = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
        String processId = "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0";
        String smpEndpoint = "https://smp.example.com/";
        String receiverEndpoint = "https://receiver.example.com/as4";

        when(smlLookupService.lookupSMPEndpoint("GLN", "9876543210987"))
            .thenReturn(smpEndpoint);
        
        when(smpService.discoverServiceEndpoint(smpEndpoint, receiverId, docTypeId, processId))
            .thenReturn(receiverEndpoint);
        
        AS4SendResponse successResponse = AS4SendResponse.builder()
            .success(true)
            .messageId("DBNA-MSG-001")
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
        assertTrue(response.getBody().isSuccess());

        // Verify SML lookup was called
        verify(smlLookupService, times(1)).lookupSMPEndpoint("GLN", "9876543210987");
        
        // Verify SMP discovery was called
        verify(smpService, times(1)).discoverServiceEndpoint(smpEndpoint, receiverId, docTypeId, processId);
    }

    @Test
    @DisplayName("Should return 404 when SML lookup fails")
    void testSMLLookupFailure() throws Exception {
        // Arrange
        String senderId = "GLN::1234567890123";
        String receiverId = "GLN::9999999999999";  // Receiver not in SML
        String docTypeId = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
        String processId = "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0";

        when(smlLookupService.lookupSMPEndpoint("GLN", "9999999999999"))
            .thenReturn(null);

        // Act & Assert
        assertThrows(SMLLookupException.class, () ->
            controller.sendDocument(senderId, receiverId, docTypeId, processId, testDocumentContent),
            "Should throw SMLLookupException when SML lookup returns null");
        
        // SMP should not be called if SML lookup fails
        verify(smpService, never()).discoverServiceEndpoint(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should handle SML lookup exception")
    void testSMLLookupException() throws Exception {
        // Arrange
        String senderId = "GLN::1234567890123";
        String receiverId = "GLN::9876543210987";
        String docTypeId = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
        String processId = "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0";

        when(smlLookupService.lookupSMPEndpoint("GLN", "9876543210987"))
            .thenThrow(new SMLLookupException("DNS query failed"));

        // Act & Assert
        assertThrows(SMLLookupException.class, () ->
            controller.sendDocument(senderId, receiverId, docTypeId, processId, testDocumentContent),
            "Should throw SMLLookupException when SML lookup fails");
    }

    @Test
    @DisplayName("Should query SMP after successful SML lookup")
    void testSMPDiscoveryIntegration() throws Exception {
        // Arrange
        String senderId = "GLN::1234567890123";
        String receiverId = "GLN::9876543210987";
        String docTypeId = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
        String processId = "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0";
        String smpEndpoint = "https://smp.example.com/";
        String receiverEndpoint = "https://receiver.example.com/as4";

        when(smlLookupService.lookupSMPEndpoint("GLN", "9876543210987"))
            .thenReturn(smpEndpoint);
        
        when(smpService.discoverServiceEndpoint(smpEndpoint, receiverId, docTypeId, processId))
            .thenReturn(receiverEndpoint);
        
        AS4SendResponse successResponse = AS4SendResponse.builder()
            .success(true)
            .messageId("DBNA-MSG-002")
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

        // Verify SMP discovery was called with correct parameters
        ArgumentCaptor<String> endpointCaptor = ArgumentCaptor.forClass(String.class);
        verify(smpService).discoverServiceEndpoint(
            endpointCaptor.capture(), 
            eq(receiverId), 
            eq(docTypeId), 
            eq(processId)
        );
        assertEquals(smpEndpoint, endpointCaptor.getValue());
    }

    @Test
    @DisplayName("Should return 404 when SMP endpoint not found")
    void testSMPDiscoveryFailure() throws Exception {
        // Arrange
        String senderId = "GLN::1234567890123";
        String receiverId = "GLN::9876543210987";
        String docTypeId = "urn:invalid:document:type";  // Unsupported document type
        String processId = "urn:invalid:process";
        String smpEndpoint = "https://smp.example.com/";

        when(smlLookupService.lookupSMPEndpoint("GLN", "9876543210987"))
            .thenReturn(smpEndpoint);
        
        when(smpService.discoverServiceEndpoint(smpEndpoint, receiverId, docTypeId, processId))
            .thenReturn(null);

        // Act & Assert
        assertThrows(SMPDiscoveryException.class, () ->
            controller.sendDocument(senderId, receiverId, docTypeId, processId, testDocumentContent),
            "Should throw SMPDiscoveryException when service endpoint is not found");
        
        // AS4 service should not be called if SMP discovery fails
        verify(as4SendService, never()).sendAS4Message(any());
    }

    @Test
    @DisplayName("Should propagate SMP endpoint URL to AS4SendRequest")
    void testSMPEndpointPropagation() throws Exception {
        // Arrange
        String senderId = "GLN::1234567890123";
        String receiverId = "GLN::9876543210987";
        String docTypeId = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
        String processId = "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0";
        String smpEndpoint = "https://smp.example.com/";
        String receiverEndpoint = "https://receiver.example.com/as4";

        when(smlLookupService.lookupSMPEndpoint("GLN", "9876543210987"))
            .thenReturn(smpEndpoint);
        
        when(smpService.discoverServiceEndpoint(smpEndpoint, receiverId, docTypeId, processId))
            .thenReturn(receiverEndpoint);
        
        AS4SendResponse successResponse = AS4SendResponse.builder()
            .success(true)
            .messageId("DBNA-MSG-003")
            .status("SENT")
            .timestamp(System.currentTimeMillis())
            .build();

        when(as4SendService.sendAS4Message(any(AS4SendRequest.class)))
            .thenReturn(successResponse);

        // Act
        controller.sendDocument(senderId, receiverId, docTypeId, processId, testDocumentContent);

        // Assert - Verify the endpoint URL was propagated to AS4SendRequest
        ArgumentCaptor<AS4SendRequest> requestCaptor = ArgumentCaptor.forClass(AS4SendRequest.class);
        verify(as4SendService).sendAS4Message(requestCaptor.capture());

        AS4SendRequest capturedRequest = requestCaptor.getValue();
        assertEquals(receiverEndpoint, capturedRequest.getReceiverEndpointUrl());
    }

    @Test
    @DisplayName("Should set DBNA PMode parameters in AS4SendRequest")
    void testDBNAPModeParametersConfiguration() throws Exception {
        // Arrange
        String senderId = "GLN::1234567890123";
        String receiverId = "GLN::9876543210987";
        String docTypeId = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
        String processId = "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0";
        String smpEndpoint = "https://smp.example.com/";
        String receiverEndpoint = "https://receiver.example.com/as4";

        when(smlLookupService.lookupSMPEndpoint(any(), any())).thenReturn(smpEndpoint);
        when(smpService.discoverServiceEndpoint(any(), any(), any(), any())).thenReturn(receiverEndpoint);
        
        AS4SendResponse successResponse = AS4SendResponse.builder()
            .success(true)
            .messageId("DBNA-MSG-004")
            .status("SENT")
            .timestamp(System.currentTimeMillis())
            .build();

        when(as4SendService.sendAS4Message(any(AS4SendRequest.class)))
            .thenReturn(successResponse);

        // Act
        controller.sendDocument(senderId, receiverId, docTypeId, processId, testDocumentContent);

        // Assert - Verify DBNA PMode parameters
        ArgumentCaptor<AS4SendRequest> requestCaptor = ArgumentCaptor.forClass(AS4SendRequest.class);
        verify(as4SendService).sendAS4Message(requestCaptor.capture());

        AS4SendRequest capturedRequest = requestCaptor.getValue();
        // DBNA Profile for AS4 v1.0 requirements
        assertTrue(capturedRequest.isSignMessage(), "Message signing must be enabled");
        assertTrue(capturedRequest.isEncryptMessage(), "Message encryption must be enabled");
        assertEquals("https://dbnalliance.org/agreements/access_point.html", capturedRequest.getAgreementRef());
    }
}




