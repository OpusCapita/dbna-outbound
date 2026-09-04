package com.opuscapita.dbna.outbound.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Exception for SMP (Service Metadata Publishing) discovery failures
 */
public class SMPDiscoveryException extends DBNAException {
    
    private final Integer httpStatusCode;
    /**
     * -- GETTER --
     *  Gets the SMP endpoint that was queried
     *
     * @return The SMP endpoint, or null if not applicable
     */
    @Getter
    private final String smpEndpoint;

    public SMPDiscoveryException(String message) {
        super("SMP_DISCOVERY_ERROR", message, HttpStatus.INTERNAL_SERVER_ERROR.value());
        this.httpStatusCode = null;
        this.smpEndpoint = null;
    }
    
    public SMPDiscoveryException(String message, Throwable cause) {
        super("SMP_DISCOVERY_ERROR", message, HttpStatus.INTERNAL_SERVER_ERROR.value(), cause);
        this.httpStatusCode = null;
        this.smpEndpoint = null;
    }

    /**
     * Creates an SMPDiscoveryException for an HTTP error response from SMP
     *
     * @param message The error message
     * @param httpStatusCode The HTTP status code returned by SMP
     * @param smpEndpoint The SMP endpoint that was queried
     */
    public SMPDiscoveryException(String message, int httpStatusCode, String smpEndpoint) {
        super("SMP_DISCOVERY_ERROR", message, httpStatusCode);
        this.httpStatusCode = httpStatusCode;
        this.smpEndpoint = smpEndpoint;
    }

    /**
     * Creates an SMPDiscoveryException for an HTTP error response from SMP with cause
     *
     * @param message The error message
     * @param httpStatusCode The HTTP status code returned by SMP
     * @param smpEndpoint The SMP endpoint that was queried
     * @param cause The underlying cause
     */
    public SMPDiscoveryException(String message, int httpStatusCode, String smpEndpoint, Throwable cause) {
        super("SMP_DISCOVERY_ERROR", message, httpStatusCode, cause);
        this.httpStatusCode = httpStatusCode;
        this.smpEndpoint = smpEndpoint;
    }

    /**
     * Gets the HTTP status code if this was an HTTP error response from SMP
     *
     * @return The HTTP status code returned by SMP, or null if not an HTTP error
     */
    public Integer getSmpHttpStatusCode() {
        return httpStatusCode;
    }

    /**
     * Checks if this exception represents a valid HTTP error response from SMP
     *
     * @return true if this was caused by an HTTP error response, false otherwise
     */
    public boolean isHttpError() {
        return httpStatusCode != null;
    }
}

