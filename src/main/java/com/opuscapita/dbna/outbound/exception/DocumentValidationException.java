package com.opuscapita.dbna.outbound.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception for document validation failures
 */
public class DocumentValidationException extends DBNAException {
    
    public DocumentValidationException(String message) {
        super("VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST.value());
    }
    
    public DocumentValidationException(String message, Throwable cause) {
        super("VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST.value(), cause);
    }
}

