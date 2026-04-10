package com.opuscapita.dbna.outbound.model;

import java.util.Collections;
import java.util.List;

/**
 * AS4-specific implementation of TransmissionResponse
 * Wraps AS4SendResponse to conform to the TransmissionResponse interface
 */
public class AS4TransmissionResponse implements TransmissionResponse {
    
    private final AS4SendResponse as4Response;
    
    public AS4TransmissionResponse(AS4SendResponse as4Response) {
        this.as4Response = as4Response;
    }
    
    @Override
    public Object getTransmissionIdentifier() {
        return as4Response.getMessageId();
    }
    
    @Override
    public List<?> getReceipts() {
        // AS4 receipts would be added here if available
        // For now, return status information
        if (as4Response.isSuccess()) {
            return Collections.singletonList("AS4 Message sent successfully: " + as4Response.getStatus());
        } else {
            return Collections.singletonList("AS4 Error: " + as4Response.getErrorMessage());
        }
    }
    
    /**
     * Get the underlying AS4SendResponse
     */
    public AS4SendResponse getAs4Response() {
        return as4Response;
    }
}

