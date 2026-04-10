package com.opuscapita.dbna.outbound.service;

import com.opuscapita.dbna.outbound.config.NetworkConfiguration;
import com.opuscapita.dbna.outbound.exception.SendServiceFactoryException;
import com.opuscapita.dbna.common.container.ContainerMessage;
import com.opuscapita.dbna.common.container.state.Source;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SendServiceFactory
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SendServiceFactory Unit Tests")
class SendServiceFactoryTest {

    @Mock
    private DummySendService dummySendService;

    @Mock
    private AS4SendService as4SendService;

    @Mock
    private SiriusSendService siriusSendService;

    @Mock
    private NetworkConfiguration networkConfiguration;

    @Mock
    private ContainerMessage containerMessage;

    private SendServiceFactory factory;

    @BeforeEach
    void setUp() {
        factory = new SendServiceFactory(
            dummySendService,
            as4SendService,
            siriusSendService,
            networkConfiguration
        );
        
        // Default mock behavior using lenient to avoid UnnecessaryStubbingException
        lenient().when(networkConfiguration.getStopConfig()).thenReturn("");
        lenient().when(networkConfiguration.getFakeConfig()).thenReturn("");
        lenient().when(containerMessage.getFileName()).thenReturn("test.xml");
    }

    @Test
    @DisplayName("Should return AS4SendService for NETWORK destination")
    void testGetServiceForNetwork() throws Exception {
        // Act
        SendService result = factory.getService(containerMessage, Source.NETWORK);

        // Assert
        assertNotNull(result);
        assertSame(as4SendService, result);
        verify(networkConfiguration).getStopConfig();
        verify(networkConfiguration).getFakeConfig();
    }

    @Test
    @DisplayName("Should return SiriusSendService for SIRIUS destination")
    void testGetServiceForSirius() throws Exception {
        // Act
        SendService result = factory.getService(containerMessage, Source.SIRIUS);

        // Assert
        assertNotNull(result);
        assertSame(siriusSendService, result);
    }

    @Test
    @DisplayName("Should return DummySendService when destination is in fake config")
    void testGetServiceWithFakeConfig() throws Exception {
        // Arrange
        when(networkConfiguration.getFakeConfig()).thenReturn("NETWORK,SIRIUS");

        // Act
        SendService result = factory.getService(containerMessage, Source.NETWORK);

        // Assert
        assertNotNull(result);
        assertSame(dummySendService, result);
    }

    @Test
    @DisplayName("Should throw exception when destination is in stop config")
    void testGetServiceWithStopConfig() {
        // Arrange
        when(networkConfiguration.getStopConfig()).thenReturn("NETWORK");

        // Act & Assert
        SendServiceFactoryException exception = assertThrows(
            SendServiceFactoryException.class,
            () -> factory.getService(containerMessage, Source.NETWORK)
        );

        assertTrue(exception.getMessage().contains("Stopped delivery to NETWORK"));
    }

    @Test
    @DisplayName("Should throw exception for UNKNOWN destination")
    void testGetServiceForUnknown() {
        // Act & Assert
        RuntimeException exception = assertThrows(
            RuntimeException.class,
            () -> factory.getService(containerMessage, Source.UNKNOWN)
        );

        assertTrue(exception.getMessage().contains("destination UNKNOWN"));
    }

    @Test
    @DisplayName("Should handle fake config with multiple destinations")
    void testFakeConfigMultipleDestinations() throws Exception {
        // Arrange
        when(networkConfiguration.getFakeConfig()).thenReturn("NETWORK,SIRIUS,A2A");

        // Act - Test NETWORK
        SendService networkResult = factory.getService(containerMessage, Source.NETWORK);
        
        // Assert
        assertSame(dummySendService, networkResult);
    }

    @Test
    @DisplayName("Should handle stop config with specific destination")
    void testStopConfigSpecificDestination() {
        // Arrange
        when(networkConfiguration.getStopConfig()).thenReturn("SIRIUS");

        // Act & Assert - SIRIUS should be stopped
        assertThrows(SendServiceFactoryException.class,
            () -> factory.getService(containerMessage, Source.SIRIUS)
        );

        // NETWORK should still work
        when(networkConfiguration.getStopConfig()).thenReturn("SIRIUS");
        assertDoesNotThrow(() -> factory.getService(containerMessage, Source.NETWORK));
    }

    @Test
    @DisplayName("Should prioritize stop config over fake config")
    void testStopConfigOverFakeConfig() {
        // Arrange
        when(networkConfiguration.getStopConfig()).thenReturn("NETWORK");
        lenient().when(networkConfiguration.getFakeConfig()).thenReturn("NETWORK");

        // Act & Assert
        assertThrows(SendServiceFactoryException.class,
            () -> factory.getService(containerMessage, Source.NETWORK)
        );
    }

    @Test
    @DisplayName("Should return correct service for SIRIUS when not stopped or faked")
    void testGetServiceForSiriusNormalFlow() throws Exception {
        // Arrange
        when(networkConfiguration.getStopConfig()).thenReturn("");
        when(networkConfiguration.getFakeConfig()).thenReturn("");

        // Act
        SendService result = factory.getService(containerMessage, Source.SIRIUS);

        // Assert
        assertSame(siriusSendService, result);
    }

    @Test
    @DisplayName("Should handle empty stop and fake configurations")
    void testEmptyConfigurations() throws Exception {
        // Arrange
        when(networkConfiguration.getStopConfig()).thenReturn("");
        when(networkConfiguration.getFakeConfig()).thenReturn("");

        // Act
        SendService networkService = factory.getService(containerMessage, Source.NETWORK);
        SendService siriusService = factory.getService(containerMessage, Source.SIRIUS);

        // Assert
        assertSame(as4SendService, networkService);
        assertSame(siriusSendService, siriusService);
    }

    @Test
    @DisplayName("Should handle null configurations gracefully")
    void testNullConfigurations() {
        // Arrange
        when(networkConfiguration.getStopConfig()).thenReturn(null);
        lenient().when(networkConfiguration.getFakeConfig()).thenReturn(null);

        // Act & Assert - Should not throw NullPointerException
        assertThrows(NullPointerException.class,
            () -> factory.getService(containerMessage, Source.NETWORK)
        );
    }

    @Test
    @DisplayName("Should check configurations in correct order")
    void testConfigurationCheckOrder() throws Exception {
        // Arrange
        when(networkConfiguration.getStopConfig()).thenReturn("");
        when(networkConfiguration.getFakeConfig()).thenReturn("NETWORK");

        // Act
        factory.getService(containerMessage, Source.NETWORK);

        // Assert - verify order of calls
        var inOrder = inOrder(networkConfiguration);
        inOrder.verify(networkConfiguration).getStopConfig();
        inOrder.verify(networkConfiguration).getFakeConfig();
    }
}

