package com.opuscapita.dbna.common.container.state;

import com.google.gson.annotations.Since;

import java.io.Serializable;

/**
 * Routing state of a message.
 */
public class Route implements Serializable {

    private static final long serialVersionUID = -9123055794300438134L;

    @Since(1.1)
    private Source destination;
    @Since(1.2)
    private int retryCount = 0;

    public Route() {
    }

    public Route(Source destination) {
        this.destination = destination;
    }

    public Source getDestination() {
        return destination;
    }

    public void setDestination(Source destination) {
        this.destination = destination;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public int incrementAndGetRetryCount() {
        return ++retryCount;
    }

    @Override
    public String toString() {
        return destination + " [" + retryCount + "]";
    }
}
