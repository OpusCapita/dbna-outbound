package com.opuscapita.dbna.outbound.service;

import com.helger.phase4.crypto.IAS4CryptoFactory;
import com.opuscapita.dbna.outbound.config.AS4Configuration;
import com.opuscapita.dbna.outbound.model.AS4SendRequest;
import com.opuscapita.dbna.outbound.model.AS4SendResponse;
import com.opuscapita.dbna.outbound.model.AS4TransmissionResponse;
import com.opuscapita.dbna.outbound.model.TransmissionResponse;
import com.opuscapita.dbna.outbound.test.TestResourceLoader;
import com.opuscapita.dbna.common.container.ContainerMessage;
import com.opuscapita.dbna.common.container.metadata.ContainerMessageMetadata;
import com.opuscapita.dbna.common.storage.Storage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for AS4SendService
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AS4SendService Unit Tests")
class AS4SendServiceTest {

    @Mock
    private Storage storage;

    @Mock
    private UBLDocumentService ublDocumentService;

    @Mock
    private IAS4CryptoFactory as4CryptoFactory;

    @Mock
    private AS4Configuration as4Configuration;

    @Mock
    private ContainerMessage containerMessage;

    @Mock
    private ContainerMessageMetadata metadata;

    private AS4SendService as4SendService;

    private String testUblContent;

    @BeforeEach
    void setUp() throws Exception {
        // Create a real instance first
        AS4SendService realService = new AS4SendService(storage, ublDocumentService, as4CryptoFactory, as4Configuration);
        
        // Create a spy so we can mock sendAS4Message while keeping other methods real
        as4SendService = spy(realService);

        // Load actual UBL test file
        testUblContent = TestResourceLoader.loadTestInvoice();
        
        // Mock UBL validation to return true by default (can be overridden in individual tests)
        lenient().when(ublDocumentService.validateUBLDocument(anyString())).thenReturn(true);
        
        // Mock sendAS4Message to return a successful response by default
        AS4SendResponse successResponse = AS4SendResponse.builder()
            .success(true)
            .messageId("TEST-MSG-" + System.currentTimeMillis())
            .status("SENT")
            .timestamp(System.currentTimeMillis())
            .build();
        lenient().doReturn(successResponse).when(as4SendService).sendAS4Message(any(AS4SendRequest.class));
        
        // Set @Value properties using ReflectionTestUtils
        ReflectionTestUtils.setField(as4SendService, "fromPartyId", "test-sender");
        ReflectionTestUtils.setField(as4SendService, "fromPartyRole", "http://test/initiator");
        ReflectionTestUtils.setField(as4SendService, "toPartyRole", "http://test/responder");
        ReflectionTestUtils.setField(as4SendService, "serviceType", "");
        ReflectionTestUtils.setField(as4SendService, "defaultService", "http://test/service");
        ReflectionTestUtils.setField(as4SendService, "defaultAction", "http://test/action");
        ReflectionTestUtils.setField(as4SendService, "defaultReceiverEndpointUrl", "http://localhost:8080/as4");
        ReflectionTestUtils.setField(as4SendService, "maxRetryAttempts", 3);
        ReflectionTestUtils.setField(as4SendService, "retryDelayMs", 1000L);
    }

    @Test
    @DisplayName("Should successfully send message from ContainerMessage")
    void testSendFromContainerMessage() throws Exception {
        // Arrange
        String fileName = "test-invoice.xml";
        InputStream inputStream = new ByteArrayInputStream(testUblContent.getBytes(StandardCharsets.UTF_8));
        
        when(containerMessage.getFileName()).thenReturn(fileName);
        when(containerMessage.getMetadata()).thenReturn(metadata);
        when(storage.get(fileName)).thenReturn(inputStream);
        
        when(metadata.getSenderId()).thenReturn("SENDER123");
        when(metadata.getRecipientId()).thenReturn("RECEIVER456");
        when(metadata.getMessageId()).thenReturn("MSG-789");
        when(metadata.getDocumentTypeIdentifier()).thenReturn("urn:oasis:names:specification:ubl:schema:xsd:Invoice-2");
        when(metadata.getProfileTypeIdentifier()).thenReturn("urn:fdc:peppol.eu:2017:poacc:billing:01:1.0");

        // Act
        TransmissionResponse response = as4SendService.send(containerMessage);

        // Assert
        assertNotNull(response);
        assertInstanceOf(AS4TransmissionResponse.class, response);
        verify(storage).get(fileName);
        verify(containerMessage, atLeastOnce()).getMetadata();
    }

    @Test
    @DisplayName("Should throw exception when file not found in storage")
    void testSendWithFileNotFound() throws Exception {
        // Arrange
        String fileName = "missing-file.xml";
        when(containerMessage.getFileName()).thenReturn(fileName);
        when(storage.get(fileName)).thenReturn(null);

        // Act & Assert
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            as4SendService.send(containerMessage);
        });

        assertTrue(exception.getMessage().contains("File not found in storage"));
        verify(storage).get(fileName);
    }

    @Test
    @DisplayName("Should validate AS4SendRequest with missing endpoint URL")
    void testSendDocumentWithMissingEndpoint() {
        // Arrange
        AS4SendRequest request = AS4SendRequest.builder()
            .ublDocumentContent(testUblContent)
            .senderId("SENDER123")
            .receiverId("RECEIVER456")
            // Missing receiverEndpointUrl
            .build();

        // Use real method for validation tests
        doCallRealMethod().when(as4SendService).sendAS4Message(any(AS4SendRequest.class));

        // Act
        AS4SendResponse response = as4SendService.sendAS4Message(request);

        // Assert
        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertTrue(response.getErrorMessage().contains("Receiver endpoint URL is required"));
    }

    @Test
    @DisplayName("Should validate AS4SendRequest with missing sender ID")
    void testSendDocumentWithMissingSenderId() {
        // Arrange
        AS4SendRequest request = AS4SendRequest.builder()
            .ublDocumentContent(testUblContent)
            .receiverEndpointUrl("http://receiver.com/as4")
            .receiverId("RECEIVER456")
            // Missing senderId
            .build();

        // Use real method for validation tests
        doCallRealMethod().when(as4SendService).sendAS4Message(any(AS4SendRequest.class));

        // Act
        AS4SendResponse response = as4SendService.sendAS4Message(request);

        // Assert
        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertTrue(response.getErrorMessage().contains("Sender ID is required"));
    }

    @Test
    @DisplayName("Should validate AS4SendRequest with missing receiver ID")
    void testSendDocumentWithMissingReceiverId() {
        // Arrange
        AS4SendRequest request = AS4SendRequest.builder()
            .ublDocumentContent(testUblContent)
            .receiverEndpointUrl("http://receiver.com/as4")
            .senderId("SENDER123")
            // Missing receiverId
            .build();

        // Use real method for validation tests
        doCallRealMethod().when(as4SendService).sendAS4Message(any(AS4SendRequest.class));

        // Act
        AS4SendResponse response = as4SendService.sendAS4Message(request);

        // Assert
        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertTrue(response.getErrorMessage().contains("Receiver ID is required"));
    }

    @Test
    @DisplayName("Should validate AS4SendRequest with missing UBL content")
    void testSendDocumentWithMissingUblContent() {
        // Arrange
        AS4SendRequest request = AS4SendRequest.builder()
            .receiverEndpointUrl("http://receiver.com/as4")
            .senderId("SENDER123")
            .receiverId("RECEIVER456")
            // Missing ublDocumentContent
            .build();

        // Use real method for validation tests
        doCallRealMethod().when(as4SendService).sendAS4Message(any(AS4SendRequest.class));

        // Act
        AS4SendResponse response = as4SendService.sendAS4Message(request);

        // Assert
        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertTrue(response.getErrorMessage().contains("UBL document content is required"));
    }

    @Test
    @DisplayName("Should return retry count from configuration")
    void testGetRetryCount() {
        // Act
        int retryCount = as4SendService.getRetryCount();

        // Assert
        assertEquals(3, retryCount);
    }

    @Test
    @DisplayName("Should return retry delay from configuration")
    void testGetRetryDelay() {
        // Act
        int retryDelay = as4SendService.getRetryDelay();

        // Assert
        assertEquals(1000, retryDelay);
    }

    @Test
    @DisplayName("Should extract metadata from container message correctly")
    void testMetadataExtraction() throws Exception {
        // Arrange
        String fileName = "invoice.xml";
        InputStream inputStream = new ByteArrayInputStream(testUblContent.getBytes(StandardCharsets.UTF_8));

        when(containerMessage.getFileName()).thenReturn(fileName);
        when(containerMessage.getMetadata()).thenReturn(metadata);
        when(storage.get(fileName)).thenReturn(inputStream);
        
        when(metadata.getSenderId()).thenReturn("SE123");
        when(metadata.getRecipientId()).thenReturn("RE456");
        when(metadata.getMessageId()).thenReturn("MSG-001");
        when(metadata.getDocumentTypeIdentifier()).thenReturn("Invoice");
        when(metadata.getProfileTypeIdentifier()).thenReturn("Billing");

        // Act
        as4SendService.send(containerMessage);

        // Assert
        verify(metadata).getSenderId();
        verify(metadata).getRecipientId();
        verify(metadata).getMessageId();
        verify(metadata).getDocumentTypeIdentifier();
        verify(metadata).getProfileTypeIdentifier();
    }

    @Test
    @DisplayName("Should handle exception during UBL content reading")
    void testSendWithStorageException() throws Exception {
        // Arrange
        when(containerMessage.getFileName()).thenReturn("test.xml");
        when(storage.get(anyString())).thenThrow(new RuntimeException("Storage error"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            as4SendService.send(containerMessage);
        });
    }

    @Test
    @DisplayName("Should use default receiver endpoint URL from configuration")
    void testDefaultReceiverEndpointUrl() throws Exception {
        // Arrange
        String fileName = "test.xml";
        InputStream inputStream = new ByteArrayInputStream(testUblContent.getBytes(StandardCharsets.UTF_8));

        when(containerMessage.getFileName()).thenReturn(fileName);
        when(containerMessage.getMetadata()).thenReturn(metadata);
        when(storage.get(fileName)).thenReturn(inputStream);
        
        when(metadata.getSenderId()).thenReturn("SENDER");
        when(metadata.getRecipientId()).thenReturn("RECEIVER");
        when(metadata.getMessageId()).thenReturn("MSG");
        when(metadata.getDocumentTypeIdentifier()).thenReturn("Invoice");
        when(metadata.getProfileTypeIdentifier()).thenReturn("Billing");

        // Act
        as4SendService.send(containerMessage);

        // Assert - verify storage was accessed
        verify(storage).get(fileName);
    }

    @Test
    @DisplayName("Should handle DBNA compliant UBL invoice from resources")
    void testDbnaCompliantInvoice() throws Exception {
        // Arrange
        String dbnaInvoice = TestResourceLoader.loadDbnaInvoice();
        String fileName = "DBNA000005UBLInvoice.xml";
        InputStream inputStream = new ByteArrayInputStream(dbnaInvoice.getBytes(StandardCharsets.UTF_8));
        
        when(containerMessage.getFileName()).thenReturn(fileName);
        when(containerMessage.getMetadata()).thenReturn(metadata);
        when(storage.get(fileName)).thenReturn(inputStream);
        
        when(metadata.getSenderId()).thenReturn("DBNA-SENDER");
        when(metadata.getRecipientId()).thenReturn("DBNA-RECEIVER");
        when(metadata.getMessageId()).thenReturn("DBNA-MSG-001");
        when(metadata.getDocumentTypeIdentifier()).thenReturn("urn:oasis:names:specification:ubl:schema:xsd:Invoice-2");
        when(metadata.getProfileTypeIdentifier()).thenReturn("bdx:noprocess");

        // Act
        TransmissionResponse response = as4SendService.send(containerMessage);

        // Assert
        assertNotNull(response);
        verify(storage).get(fileName);
    }

    @Test
    @DisplayName("Should handle UBL Order document from resources")
    void testUblOrderDocument() throws Exception {
        // Arrange
        String orderContent = TestResourceLoader.loadTestOrder();
        String fileName = "test-order.xml";
        InputStream inputStream = new ByteArrayInputStream(orderContent.getBytes(StandardCharsets.UTF_8));
        
        when(containerMessage.getFileName()).thenReturn(fileName);
        when(containerMessage.getMetadata()).thenReturn(metadata);
        when(storage.get(fileName)).thenReturn(inputStream);
        
        when(metadata.getSenderId()).thenReturn("BUYER");
        when(metadata.getRecipientId()).thenReturn("SELLER");
        when(metadata.getMessageId()).thenReturn("ORDER-MSG-001");
        when(metadata.getDocumentTypeIdentifier()).thenReturn("urn:oasis:names:specification:ubl:schema:xsd:Order-2");
        when(metadata.getProfileTypeIdentifier()).thenReturn("urn:fdc:peppol.eu:poacc:bis:ordering:3");

        // Act
        TransmissionResponse response = as4SendService.send(containerMessage);

        // Assert
        assertNotNull(response);
        verify(storage).get(fileName);
    }

    @Test
    @DisplayName("Should handle UBL DespatchAdvice document from resources")
    void testUblDespatchAdviceDocument() throws Exception {
        // Arrange
        String despatchContent = TestResourceLoader.loadTestDespatchAdvice();
        String fileName = "test-despatch-advice.xml";
        InputStream inputStream = new ByteArrayInputStream(despatchContent.getBytes(StandardCharsets.UTF_8));
        
        when(containerMessage.getFileName()).thenReturn(fileName);
        when(containerMessage.getMetadata()).thenReturn(metadata);
        when(storage.get(fileName)).thenReturn(inputStream);
        
        when(metadata.getSenderId()).thenReturn("SHIPPER");
        when(metadata.getRecipientId()).thenReturn("CONSIGNEE");
        when(metadata.getMessageId()).thenReturn("DESPATCH-MSG-001");
        when(metadata.getDocumentTypeIdentifier()).thenReturn("urn:oasis:names:specification:ubl:schema:xsd:DespatchAdvice-2");
        when(metadata.getProfileTypeIdentifier()).thenReturn("urn:fdc:peppol.eu:poacc:bis:despatch_advice:3");

        // Act
        TransmissionResponse response = as4SendService.send(containerMessage);

        // Assert
        assertNotNull(response);
        verify(storage).get(fileName);
    }

    @Test
    @DisplayName("Should detect invalid UBL document from resources")
    void testInvalidUblDocument() throws Exception {
        // Arrange
        String invalidContent = TestResourceLoader.loadInvalidDocument();
        String fileName = "invalid-document.xml";
        InputStream inputStream = new ByteArrayInputStream(invalidContent.getBytes(StandardCharsets.UTF_8));
        
        when(containerMessage.getFileName()).thenReturn(fileName);
        when(containerMessage.getMetadata()).thenReturn(metadata);
        when(storage.get(fileName)).thenReturn(inputStream);
        
        when(metadata.getSenderId()).thenReturn("SENDER");
        when(metadata.getRecipientId()).thenReturn("RECEIVER");
        when(metadata.getMessageId()).thenReturn("INVALID-MSG");
        when(metadata.getDocumentTypeIdentifier()).thenReturn("Invalid");
        when(metadata.getProfileTypeIdentifier()).thenReturn("Invalid");
        
        // For this test, use the real sendAS4Message method so validation actually happens
        doCallRealMethod().when(as4SendService).sendAS4Message(any(AS4SendRequest.class));
        when(ublDocumentService.validateUBLDocument(invalidContent)).thenReturn(false);

        // Act
        Exception exception = assertThrows(Exception.class, () -> {
            as4SendService.send(containerMessage);
        });

        // Assert
        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("Invalid UBL 2.3 document format"));
        verify(storage).get(fileName);
    }
}


