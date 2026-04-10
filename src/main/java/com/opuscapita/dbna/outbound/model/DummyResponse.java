package com.opuscapita.dbna.outbound.model;

import com.opuscapita.dbna.common.container.ContainerMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Dummy transmission response for testing purposes
 * Returns simulated/mock data without actually sending messages
 */
public class DummyResponse implements TransmissionResponse {
    
    private static final Logger logger = LoggerFactory.getLogger(DummyResponse.class);
    
    private final String transmissionId;
    private final long timestamp;
    
    public DummyResponse() {
        this.timestamp = System.currentTimeMillis();
        this.transmissionId = "DUMMY-" + timestamp;
        logger.debug("Created dummy transmission response with ID: {}", transmissionId);
    }
    
    @Override
    public Object getTransmissionIdentifier() {
        return transmissionId;
    }
    
    @Override
    public List<?> getReceipts() {
        return Collections.emptyList();
    }
    
    /**
     * Utility method to throw an exception if the filename contains specific keywords
     * Used for testing error handling scenarios
     * 
     * @param cm The container message to check
     * @throws IOException if filename contains "error" or "exception" (case-insensitive)
     */
    public static void throwExceptionIfExpectedInFilename(ContainerMessage cm) throws IOException {
        if (cm == null) {
            return;
        }
        
        String filename = cm.getFileName();
        if (filename == null) {
            return;
        }
        
        String lowerFilename = filename.toLowerCase();
        
        if (lowerFilename.contains("error")) {
            logger.warn("Throwing simulated error for file: {}", filename);
            throw new IOException("Simulated error: filename contains 'error' - " + filename);
        }
        
        if (lowerFilename.contains("exception")) {
            logger.warn("Throwing simulated exception for file: {}", filename);
            throw new IOException("Simulated exception: filename contains 'exception' - " + filename);
        }
        
        if (lowerFilename.contains("fail")) {
            logger.warn("Throwing simulated failure for file: {}", filename);
            throw new IOException("Simulated failure: filename contains 'fail' - " + filename);
        }
    }
    
    @Override
    public String toString() {
        return "DummyResponse{" +
                "transmissionId='" + transmissionId + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}

