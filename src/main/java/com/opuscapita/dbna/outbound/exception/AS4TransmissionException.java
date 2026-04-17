package com.opuscapita.dbna.outbound.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception for AS4 message transmission failures
 */
public class AS4TransmissionException extends DBNAException {
    
    public AS4TransmissionException(String message) {
        super("AS4_TRANSMISSION_ERROR", message, HttpStatus.INTERNAL_SERVER_ERROR.value());
    }
    
    public AS4TransmissionException(String message, Throwable cause) {
        super("AS4_TRANSMISSION_ERROR", message, HttpStatus.INTERNAL_SERVER_ERROR.value(), cause);
    }
}

