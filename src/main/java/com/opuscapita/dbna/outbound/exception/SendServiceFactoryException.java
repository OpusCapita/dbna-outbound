package com.opuscapita.dbna.outbound.exception;

import java.io.IOException;

public class SendServiceFactoryException extends IOException {

    public SendServiceFactoryException() {
        super();
    }

    public SendServiceFactoryException(String message) {
        super(message);
    }

    public SendServiceFactoryException(String message, Throwable cause) {
        super(message, cause);
    }

    public SendServiceFactoryException(Throwable cause) {
        super(cause);
    }
}
