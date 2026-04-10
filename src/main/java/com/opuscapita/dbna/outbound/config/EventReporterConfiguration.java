package com.opuscapita.dbna.outbound.config;

import com.opuscapita.dbna.common.container.ContainerMessage;
import com.opuscapita.dbna.common.eventing.EventReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for EventReporter bean
 * Provides a simple implementation that logs container message status
 */
@Configuration
public class EventReporterConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(EventReporterConfiguration.class);

    @Bean
    public EventReporter eventReporter() {
        return (ContainerMessage containerMessage) -> {
            // Log the message status
            logger.info("Event reported for message: {} at step: {}", 
                containerMessage.getFileName(), 
                containerMessage.getStep());
            
            // Additional logging for debugging
            if (logger.isDebugEnabled()) {
                logger.debug("Message details - ID: {}, Step: {}, History: {}", 
                    containerMessage.getFileName(),
                    containerMessage.getStep(),
                    containerMessage.getHistory());
            }
        };
    }
}


