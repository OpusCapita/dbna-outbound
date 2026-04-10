package com.opuscapita.dbna.outbound.error;

import com.opuscapita.dbna.outbound.service.SendService;
import com.opuscapita.dbna.common.container.ContainerMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Error handler for outbound message processing failures in DBNA network
 */
@Component
public class OutboundErrorHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(OutboundErrorHandler.class);
    
    /**
     * Handle errors that occur during message transmission
     * 
     * @param cm The container message
     * @param sendService The send service that was used (may be null)
     * @param exception The exception that occurred
     */
    public void handle(ContainerMessage cm, SendService sendService, Exception exception) {
        logger.error("Error processing outbound message: {}", cm.getFileName(), exception);
        
        // Add error details to message history
        if (cm.getHistory() != null) {
            cm.getHistory().addError("Transmission failed: " + exception.getMessage());
        }
        
        // Log send service details if available
        if (sendService != null) {
            logger.error("Failed send service type: {}", sendService.getClass().getSimpleName());
        }
        
        // Determine error type and log accordingly
        String errorMessage = exception.getMessage();
        if (errorMessage != null) {
            if (errorMessage.contains("certificate") || errorMessage.contains("SSL") || errorMessage.contains("TLS")) {
                logger.error("Certificate/SSL related error detected for message: {}", cm.getFileName());
            } else if (errorMessage.contains("timeout") || errorMessage.contains("Timeout")) {
                logger.error("Timeout error detected for message: {}", cm.getFileName());
            } else if (errorMessage.contains("connection") || errorMessage.contains("Connection")) {
                logger.error("Connection error detected for message: {}", cm.getFileName());
            }
        }
        
        // Additional error handling logic can be added here
        // For example: retry logic, dead letter queue, notifications, alerting, etc.
    }
}

