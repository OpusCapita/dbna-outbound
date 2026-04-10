package com.opuscapita.dbna.outbound.model;

/**
 * TransmissionResponse stub interface
 * This is a minimal stub to allow compilation; actual implementation 
 * will be provided at runtime by the external Peppol/Oxalis application.
 */
public interface TransmissionResponse {
    
    /**
     * Get the transmission identifier
     * @return transmission identifier
     */
    Object getTransmissionIdentifier();
    
    /**
     * Get the receipts for this transmission
     * @return receipts
     */
    java.util.List<?> getReceipts();
}

