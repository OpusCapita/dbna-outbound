package com.opuscapita.dbna.outbound.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception for SMP (Service Metadata Publishing) discovery failures
 */
public class SMPDiscoveryException extends DBNAException {
    
    public SMPDiscoveryException(String message) {
        super("SMP_DISCOVERY_ERROR", message, HttpStatus.INTERNAL_SERVER_ERROR.value());
    }
    
    public SMPDiscoveryException(String message, Throwable cause) {
        super("SMP_DISCOVERY_ERROR", message, HttpStatus.INTERNAL_SERVER_ERROR.value(), cause);
    }
}

