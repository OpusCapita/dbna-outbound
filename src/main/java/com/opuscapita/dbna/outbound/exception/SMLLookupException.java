package com.opuscapita.dbna.outbound.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception for SML (Service Metadata Locator) lookup failures
 */
public class SMLLookupException extends DBNAException {
    
    public SMLLookupException(String message) {
        super("SML_LOOKUP_ERROR", message, HttpStatus.SERVICE_UNAVAILABLE.value());
    }
    
    public SMLLookupException(String message, Throwable cause) {
        super("SML_LOOKUP_ERROR", message, HttpStatus.SERVICE_UNAVAILABLE.value(), cause);
    }
}

