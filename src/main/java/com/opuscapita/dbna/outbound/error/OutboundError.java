package com.opuscapita.dbna.outbound.error;

import lombok.Getter;

/**
 * List of possible issues while sending the file
 */
@Getter
@SuppressWarnings("unused")
public enum OutboundError {

    /**
     * Error with the document itself, e.g. empty file
     */
    DOCUMENT_ERROR(false),

    /**
     * Data error inside the document, no-retry
     */
    DATA_ERROR(false),

    /**
     * Failed to connect for some reason
     */
    CONNECTION_ERROR(true),

    /**
     * Receiving AP doesn't return 200, possibly retry
     */
    RECEIVING_AP_ERROR(true),

    /**
     * Recipient is not registered in SMP, no-retry
     */
    UNKNOWN_RECIPIENT(true), //making retriable because of issues in ELMA

    /**
     * Doc format for the recipient is not registered in SMP
     */
    UNSUPPORTED_DATA_FORMAT(false),

    /**
     * Security issue - expired, invalid, or unknown certificates
     */
    SECURITY_ERROR(false),

    /**
     * Issue with file validation, used in MLR reporter and shouldn't be used elsewhere
     */
    VALIDATION_ERROR(false),

    /**
     * Any exception occurred while delivering to business platforms
     */
    BP_DELIVERY_ERROR(true),

    /**
     * Used as a configuration to stop delivery to a certain destination
     */
    STOPPED_DELIVERY_ERROR(false),

    /**
     * An unexpected error occurred in our own service, probably from blob
     */
    INTERNAL_SERVICE_ERROR(true),

    /**
     * All other errors
     */
    OTHER_ERROR(false);

    private final boolean isRetryable;

    OutboundError(boolean isRetryable) {
        this.isRetryable = isRetryable;
    }

    public boolean requiresNotification() {
        return OutboundError.DATA_ERROR.equals(this) ||
                OutboundError.DOCUMENT_ERROR.equals(this) ||
                OutboundError.UNKNOWN_RECIPIENT.equals(this) ||
                OutboundError.UNSUPPORTED_DATA_FORMAT.equals(this);
    }

    public boolean requiresTicketCreation() {
        return OutboundError.OTHER_ERROR.equals(this) ||
                OutboundError.SECURITY_ERROR.equals(this) ||
                OutboundError.CONNECTION_ERROR.equals(this) ||
                OutboundError.BP_DELIVERY_ERROR.equals(this) ||
                OutboundError.INTERNAL_SERVICE_ERROR.equals(this) ||
                OutboundError.RECEIVING_AP_ERROR.equals(this);
    }

}
