package com.opuscapita.dbna.outbound.model;

import lombok.Getter;

/**
 * Enumeration of possible AS4 send operation status values.
 * These statuses are returned in the AS4SendResponse to indicate the outcome of the transmission.
 */
@Getter
public enum AS4SendStatus {
    /**
     * AS4 message validation failed (UBL format, required fields, etc.)
     */
    VALIDATION_FAILED("VALIDATION_FAILED"),

    /**
     * AS4 message was sent successfully, but the receipt from the receiving endpoint
     * had a malformed structure (e.g., empty or incomplete Receipt element).
     * This indicates the remote endpoint may have a non-compliant AS4 implementation.
     */
    SENT_RECEIPT_MALFORMED("SENT_RECEIPT_MALFORMED"),

    /**
     * AS4 message was sent successfully, but the receipt from the receiving endpoint
     * failed schema validation.
     * This indicates the remote endpoint may have sent an invalid or non-compliant receipt.
     */
    SENT_RECEIPT_INVALID("SENT_RECEIPT_INVALID"),

    /**
     * AS4 message transmission failed due to transport/network errors.
     * This may indicate response parsing issues, SSL/TLS problems, or non-SOAP responses.
     */
    TRANSMISSION_ERROR("TRANSMISSION_ERROR"),

    /**
     * AS4 message send failed with a general or unspecified error.
     */
    FAILED("FAILED"),

    /**
     * AS4 message was sent successfully and receipt was validated.
     */
    SENT("SENT"),

    /**
     * AS4 message preparation or sending resulted in an unexpected error.
     */
    ERROR("ERROR");
    
    private final String value;

    AS4SendStatus(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}

