package com.opuscapita.dbna.outbound.model;

/**
 * Business response implementation for transmission responses
 * This is a stub class to allow compilation; actual implementation
 * may be provided at runtime by external application.
 */
public class BusinessResponse implements TransmissionResponse {
    
    @Override
    public Object getTransmissionIdentifier() {
        return "BUSINESS_TRANSMISSION_" + System.currentTimeMillis();
    }
    
    @Override
    public java.util.List<?> getReceipts() {
        return java.util.Collections.emptyList();
    }
}

