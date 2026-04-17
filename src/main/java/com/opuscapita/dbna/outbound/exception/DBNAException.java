package com.opuscapita.dbna.outbound.exception;

/**
 * Base exception for DBNA outbound service
 * All service exceptions should extend this class
 */
public class DBNAException extends RuntimeException {
    
    private final String errorCode;
    private final int httpStatusCode;
    
    /**
     * Create a DBNA exception
     * 
     * @param errorCode Unique error code for API responses
     * @param message Human-readable error message
     * @param httpStatusCode HTTP status code to return
     */
    public DBNAException(String errorCode, String message, int httpStatusCode) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatusCode = httpStatusCode;
    }
    
    /**
     * Create a DBNA exception with cause
     * 
     * @param errorCode Unique error code for API responses
     * @param message Human-readable error message
     * @param httpStatusCode HTTP status code to return
     * @param cause Root cause exception
     */
    public DBNAException(String errorCode, String message, int httpStatusCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatusCode = httpStatusCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public int getHttpStatusCode() {
        return httpStatusCode;
    }
}


