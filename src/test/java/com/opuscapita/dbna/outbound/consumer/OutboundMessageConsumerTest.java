package com.opuscapita.dbna.outbound.consumer;

import com.opuscapita.dbna.outbound.error.OutboundErrorHandler;
import com.opuscapita.dbna.outbound.model.TransmissionResponse;
import com.opuscapita.dbna.outbound.service.SendService;
import com.opuscapita.dbna.outbound.service.SendServiceFactory;
import com.opuscapita.dbna.common.container.ContainerMessage;
import com.opuscapita.dbna.common.container.ContainerMessageHistory;
import com.opuscapita.dbna.common.container.metadata.ContainerMessageMetadata;
import com.opuscapita.dbna.common.container.state.ProcessStep;
import com.opuscapita.dbna.common.container.state.Route;
import com.opuscapita.dbna.common.container.state.Source;
import com.opuscapita.dbna.common.eventing.EventReporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for OutboundMessageConsumer
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OutboundMessageConsumer Unit Tests")
class OutboundMessageConsumerTest {

    @Mock
    private SendServiceFactory sendServiceFactory;

    @Mock
    private EventReporter eventReporter;

    @Mock
    private OutboundErrorHandler errorHandler;

    @Mock
    private SendService sendService;

    @Mock
    private ContainerMessage containerMessage;

    @Mock
    private TransmissionResponse transmissionResponse;

    @Mock
    private Route route;

    @Mock
    private Source destination;

    @Mock
    private ContainerMessageHistory messageHistory;

    @Mock
    private ContainerMessageMetadata metadata;

    @InjectMocks
    private OutboundMessageConsumer consumer;

    @BeforeEach
    void setUp() {
        // Setup common mock behavior with lenient() for stubs that may not be used in all tests
        lenient().when(containerMessage.getFileName()).thenReturn("test-invoice.xml");
        lenient().when(containerMessage.toKibana()).thenReturn("test-message-kibana");
        lenient().when(containerMessage.getRoute()).thenReturn(route);
        lenient().when(route.getDestination()).thenReturn(destination);
        lenient().when(containerMessage.getHistory()).thenReturn(messageHistory);
        lenient().when(containerMessage.getMetadata()).thenReturn(metadata);
    }

    @Test
    @DisplayName("Should successfully consume and send message")
    void testConsumeMessageSuccess() throws Exception {
        // Arrange
        when(sendServiceFactory.getService(any(), any())).thenReturn(sendService);
        when(sendService.send(containerMessage)).thenReturn(transmissionResponse);
        when(transmissionResponse.getTransmissionIdentifier()).thenReturn("TX-12345");
        when(transmissionResponse.getReceipts()).thenAnswer(invocation -> Collections.singletonList("Receipt-1"));

        // Act
        consumer.consume(containerMessage);

        // Assert
        verify(containerMessage).setStep(ProcessStep.OUTBOUND);
        verify(containerMessage).setStep(ProcessStep.NETWORK);
        verify(sendService).send(containerMessage);
        verify(eventReporter).reportStatus(containerMessage);
        verify(errorHandler, never()).handle(any(), any(), any());
    }

    @Test
    @DisplayName("Should throw exception when filename is blank")
    void testConsumeWithBlankFileName() throws Exception {
        // Arrange
        when(containerMessage.getFileName()).thenReturn("");

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> consumer.consume(containerMessage)
        );

        assertTrue(exception.getMessage().contains("File name is empty"));
        verify(containerMessage).setStep(ProcessStep.OUTBOUND);
        verify(sendServiceFactory, never()).getService(any(), any());
    }

    @Test
    @DisplayName("Should throw exception when filename is null")
    void testConsumeWithNullFileName() throws Exception {
        // Arrange
        when(containerMessage.getFileName()).thenReturn(null);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> consumer.consume(containerMessage)
        );

        assertTrue(exception.getMessage().contains("File name is empty"));
        verify(sendServiceFactory, never()).getService(any(), any());
    }

    @Test
    @DisplayName("Should handle send service exception and call error handler")
    void testConsumeWithSendServiceException() throws Exception {
        // Arrange
        Exception sendException = new RuntimeException("AS4 send failed");
        when(sendServiceFactory.getService(any(), any())).thenReturn(sendService);
        when(sendService.send(containerMessage)).thenThrow(sendException);

        // Act
        consumer.consume(containerMessage);

        // Assert
        verify(sendService).send(containerMessage);
        verify(errorHandler).handle(eq(containerMessage), eq(sendService), eq(sendException));
        verify(eventReporter).reportStatus(containerMessage);
        verify(containerMessage, never()).setStep(ProcessStep.NETWORK);
    }

    @Test
    @DisplayName("Should handle factory exception and call error handler")
    void testConsumeWithFactoryException() throws Exception {
        // Arrange
        Exception factoryException = new RuntimeException("Cannot create send service");
        when(sendServiceFactory.getService(any(), any())).thenThrow(factoryException);

        // Act
        consumer.consume(containerMessage);

        // Assert
        verify(sendServiceFactory).getService(any(), any());
        verify(errorHandler).handle(eq(containerMessage), isNull(), eq(factoryException));
        verify(eventReporter).reportStatus(containerMessage);
    }

    @Test
    @DisplayName("Should report status even when error occurs")
    void testEventReporterAlwaysCalled() throws Exception {
        // Arrange
        when(sendServiceFactory.getService(any(), any())).thenReturn(sendService);
        when(sendService.send(containerMessage)).thenThrow(new RuntimeException("Error"));

        // Act
        consumer.consume(containerMessage);

        // Assert
        verify(eventReporter).reportStatus(containerMessage);
    }

    @Test
    @DisplayName("Should handle multiple receipts in transmission response")
    void testMultipleReceipts() throws Exception {
        // Arrange
        when(sendServiceFactory.getService(any(), any())).thenReturn(sendService);
        when(sendService.send(containerMessage)).thenReturn(transmissionResponse);
        when(transmissionResponse.getTransmissionIdentifier()).thenReturn("TX-MULTI");
        when(transmissionResponse.getReceipts()).thenAnswer(invocation -> 
            java.util.Arrays.asList("Receipt-1", "Receipt-2", "Receipt-3")
        );

        // Act
        consumer.consume(containerMessage);

        // Assert
        verify(transmissionResponse).getReceipts();
        verify(containerMessage).setStep(ProcessStep.NETWORK);
    }
}







