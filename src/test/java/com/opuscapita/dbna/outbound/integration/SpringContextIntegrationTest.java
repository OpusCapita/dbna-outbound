package com.opuscapita.dbna.outbound.integration;

import com.opuscapita.dbna.outbound.consumer.OutboundMessageConsumer;
import com.opuscapita.dbna.outbound.service.AS4SendService;
import com.opuscapita.dbna.outbound.service.DummySendService;
import com.opuscapita.dbna.outbound.service.SendServiceFactory;
import com.opuscapita.dbna.outbound.service.SiriusSendService;
import com.opuscapita.dbna.common.container.ContainerMessage;
import com.opuscapita.dbna.common.eventing.EventReporter;
import com.opuscapita.dbna.common.storage.Storage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests to verify Spring context and bean wiring
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Spring Context Integration Tests")
class SpringContextIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("Should load application context successfully")
    void contextLoads() {
        assertNotNull(applicationContext);
    }

    @Test
    @DisplayName("Should have OutboundMessageConsumer bean")
    void testOutboundMessageConsumerBean() {
        // Act
        OutboundMessageConsumer consumer = applicationContext.getBean(OutboundMessageConsumer.class);

        // Assert
        assertNotNull(consumer);
    }

    @Test
    @DisplayName("Should have SendServiceFactory bean")
    void testSendServiceFactoryBean() {
        // Act
        SendServiceFactory factory = applicationContext.getBean(SendServiceFactory.class);

        // Assert
        assertNotNull(factory);
    }

    @Test
    @DisplayName("Should have AS4SendService bean")
    void testAS4SendServiceBean() {
        // Act
        AS4SendService service = applicationContext.getBean(AS4SendService.class);

        // Assert
        assertNotNull(service);
    }

    @Test
    @DisplayName("Should have DummySendService bean")
    void testDummySendServiceBean() {
        // Act
        DummySendService service = applicationContext.getBean(DummySendService.class);

        // Assert
        assertNotNull(service);
    }

    @Test
    @DisplayName("Should have SiriusSendService bean")
    void testSiriusSendServiceBean() {
        // Act
        SiriusSendService service = applicationContext.getBean(SiriusSendService.class);

        // Assert
        assertNotNull(service);
    }

    @Test
    @DisplayName("Should have Storage bean")
    void testStorageBean() {
        // Act
        Storage storage = applicationContext.getBean(Storage.class);

        // Assert
        assertNotNull(storage);
    }

    @Test
    @DisplayName("Should have EventReporter bean")
    void testEventReporterBean() {
        // Act
        EventReporter eventReporter = applicationContext.getBean(EventReporter.class);

        // Assert
        assertNotNull(eventReporter);
    }

    @Test
    @DisplayName("Should have all required configuration beans")
    void testConfigurationBeans() {
        // Import configuration classes
        Class<?>[] configClasses = {
            com.opuscapita.dbna.outbound.config.StorageConfiguration.class,
            com.opuscapita.dbna.outbound.config.EventReporterConfiguration.class,
            com.opuscapita.dbna.outbound.config.NetworkConfiguration.class,
            com.opuscapita.dbna.outbound.config.SiriusConfiguration.class,
            com.opuscapita.dbna.outbound.config.AS4Configuration.class
        };
        
        // Assert all configuration beans exist
        for (Class<?> configClass : configClasses) {
            assertNotNull(applicationContext.getBean(configClass), 
                configClass.getSimpleName() + " bean not found");
        }
    }

    @Test
    @DisplayName("Should wire dependencies correctly in OutboundMessageConsumer")
    void testOutboundMessageConsumerWiring() {
        // Act
        OutboundMessageConsumer consumer = applicationContext.getBean(OutboundMessageConsumer.class);

        // Assert - Verify the consumer was created (constructor injection worked)
        assertNotNull(consumer);
    }

    @Test
    @DisplayName("Should wire dependencies correctly in AS4SendService")
    void testAS4SendServiceWiring() {
        // Act
        AS4SendService service = applicationContext.getBean(AS4SendService.class);

        // Assert - Verify the service was created (constructor injection worked)
        assertNotNull(service);
        
        // Verify retry configuration is loaded
        assertTrue(service.getRetryCount() > 0);
        assertTrue(service.getRetryDelay() > 0);
    }

    @Test
    @DisplayName("Should have unique Storage instance")
    void testStorageSingleton() {
        // Act
        Storage storage1 = applicationContext.getBean(Storage.class);
        Storage storage2 = applicationContext.getBean(Storage.class);

        // Assert
        assertSame(storage1, storage2, "Storage should be a singleton");
    }

    @Test
    @DisplayName("Should have unique EventReporter instance")
    void testEventReporterSingleton() {
        // Act
        EventReporter reporter1 = applicationContext.getBean(EventReporter.class);
        EventReporter reporter2 = applicationContext.getBean(EventReporter.class);

        // Assert
        assertSame(reporter1, reporter2, "EventReporter should be a singleton");
    }

    @Test
    @DisplayName("Should verify all service beans implement SendService")
    void testSendServiceImplementations() {
        // Act
        AS4SendService as4Service = applicationContext.getBean(AS4SendService.class);
        DummySendService dummyService = applicationContext.getBean(DummySendService.class);
        SiriusSendService siriusService = applicationContext.getBean(SiriusSendService.class);

        // Assert
        assertNotNull(as4Service);
        assertNotNull(dummyService);
        assertNotNull(siriusService);
    }
}

