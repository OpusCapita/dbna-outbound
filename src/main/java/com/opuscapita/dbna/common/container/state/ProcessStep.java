package com.opuscapita.dbna.common.container.state;

/**
 * Processing stage of a container message.
 */
public enum ProcessStep {
    INBOUND,
    PROCESSOR,
    VALIDATOR,
    OUTBOUND,
    NETWORK,
    REPROCESSOR,

    WEB,
    REST,
    TEST,
    UNKNOWN;
}
