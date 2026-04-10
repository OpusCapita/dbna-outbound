package com.opuscapita.dbna.outbound.integration;

import com.opuscapita.dbna.outbound.config.NetworkConfiguration;
import com.opuscapita.dbna.outbound.consumer.OutboundMessageConsumer;
import com.opuscapita.dbna.outbound.service.SendService;
import com.opuscapita.dbna.outbound.service.SendServiceFactory;
import com.opuscapita.dbna.outbound.test.TestResourceLoader;
import com.opuscapita.dbna.common.container.ContainerMessage;
import com.opuscapita.dbna.common.container.state.Source;
import com.opuscapita.dbna.common.storage.Storage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * End-to-end integration tests
 */
@SpringBootTest(classes = {com.opuscapita.dbna.outbound.DbnaOutboundApplication.class, TestConfiguration.class})
@ActiveProfiles("test")
@DisplayName("End-to-End Integration Tests")
class EndToEndIntegrationTest {

    @Autowired
    private OutboundMessageConsumer outboundMessageConsumer;

    @Autowired
    private SendServiceFactory sendServiceFactory;

    @Autowired
    private Storage storage;

    @Autowired
    private NetworkConfiguration networkConfiguration;

    private String testUblInvoice;

    @BeforeEach
    void setUp() throws Exception {
        // Load actual UBL invoice from test resources
        testUblInvoice = TestResourceLoader.loadTestInvoice();
    }

    @Test
    @DisplayName("Should retrieve correct service for NETWORK destination")
    void testGetNetworkService() throws Exception {
        // Arrange
        ContainerMessage cm = createMockContainerMessage("test-invoice.xml", Source.NETWORK);

        // Act
        SendService service = sendServiceFactory.getService(cm, Source.NETWORK);

        // Assert
        assertNotNull(service);
        assertEquals("AS4SendService", service.getClass().getSimpleName());
    }

    @Test
    @DisplayName("Should retrieve correct service for SIRIUS destination")
    void testGetSiriusService() throws Exception {
        // Arrange
        ContainerMessage cm = createMockContainerMessage("test-invoice.xml", Source.SIRIUS);

        // Act
        SendService service = sendServiceFactory.getService(cm, Source.SIRIUS);

        // Assert
        assertNotNull(service);
        assertEquals("SiriusSendService", service.getClass().getSimpleName());
    }

    @Test
    @DisplayName("Should store and retrieve file from storage")
    void testStorageIntegration() throws Exception {
        // Arrange - use actual UBL invoice from test resources
        String content = testUblInvoice;
        ByteArrayInputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        // Act - Store
        String filename = storage.put(inputStream, "integration-test-", ".xml");

        // Assert - Retrieve
        assertNotNull(filename);
        assertTrue(storage.exists(filename));
        
        Long size = storage.size(filename);
        assertTrue(size > 0);
        assertEquals(content.getBytes(StandardCharsets.UTF_8).length, size);

        // Cleanup
        storage.remove(filename);
        assertFalse(storage.exists(filename));
    }

    @Test
    @DisplayName("Should handle fake sending configuration")
    void testFakeSendingConfiguration() throws Exception {
        // This test verifies the fake sending configuration is working
        // In a real scenario, you would modify the NetworkConfiguration
        // For now, just verify the configuration is accessible
        assertNotNull(networkConfiguration);
        assertNotNull(networkConfiguration.getFakeConfig());
    }

    @Test
    @DisplayName("Should verify retry configuration is loaded")
    void testRetryConfiguration() {
        // Assert
        assertTrue(networkConfiguration.getRetryCount() > 0);
        assertTrue(networkConfiguration.getRetryDelay() > 0);
    }

    @Test
    @DisplayName("Should load and process DBNA compliant invoice")
    void testDbnaCompliantInvoiceIntegration() throws Exception {
        // Arrange - use actual DBNA invoice from resources
        String dbnaInvoice = TestResourceLoader.loadDbnaInvoice();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(dbnaInvoice.getBytes(StandardCharsets.UTF_8));

        // Act - Store the file
        String filename = storage.put(inputStream, "dbna-invoice-", ".xml");

        // Assert
        assertNotNull(filename);
        assertTrue(storage.exists(filename));
        assertTrue(dbnaInvoice.contains("DBNA000005"));
        assertTrue(dbnaInvoice.contains("urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"));

        // Cleanup
        storage.remove(filename);
    }

    @Test
    @DisplayName("Should load and process UBL Order document")
    void testOrderDocumentIntegration() throws Exception {
        // Arrange - use actual Order from resources
        String orderContent = TestResourceLoader.loadTestOrder();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(orderContent.getBytes(StandardCharsets.UTF_8));

        // Act - Store the file
        String filename = storage.put(inputStream, "order-", ".xml");

        // Assert
        assertNotNull(filename);
        assertTrue(storage.exists(filename));

        // Cleanup
        storage.remove(filename);
    }

    @Test
    @DisplayName("Should load and process UBL DespatchAdvice document")
    void testDespatchAdviceIntegration() throws Exception {
        // Arrange - use actual DespatchAdvice from resources
        String despatchContent = TestResourceLoader.loadTestDespatchAdvice();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(despatchContent.getBytes(StandardCharsets.UTF_8));

        // Act - Store the file
        String filename = storage.put(inputStream, "despatch-", ".xml");

        // Assert
        assertNotNull(filename);
        assertTrue(storage.exists(filename));

        // Cleanup
        storage.remove(filename);
    }

    // Helper method to create mock ContainerMessage
    private ContainerMessage createMockContainerMessage(String filename, Source destination) {
        ContainerMessage cm = mock(ContainerMessage.class);
        
        when(cm.getFileName()).thenReturn(filename);
        when(cm.toKibana()).thenReturn("test-kibana-" + filename);

        return cm;
    }
}







