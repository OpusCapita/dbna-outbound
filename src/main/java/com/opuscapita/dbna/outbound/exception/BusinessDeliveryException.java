package com.opuscapita.dbna.outbound.exception;

/**
 * Exception thrown when business message delivery fails
 */
public class BusinessDeliveryException extends Exception {
    
    public BusinessDeliveryException(String message) {
        super(message);
    }
    
    public BusinessDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}

