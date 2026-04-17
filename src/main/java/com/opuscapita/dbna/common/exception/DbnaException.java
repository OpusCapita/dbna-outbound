package com.opuscapita.dbna.common.exception;

/**
 * Base exception class for DBNA common library.
 */
public class DbnaException extends RuntimeException {

    /**
     * Constructs a new DbnaException with the specified message.
     *
     * @param message the detail message
     */
    public DbnaException(String message) {
        super(message);
    }

    /**
     * Constructs a new DbnaException with the specified message and cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public DbnaException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new DbnaException with the specified cause.
     *
     * @param cause the cause
     */
    public DbnaException(Throwable cause) {
        super(cause);
    }
}

