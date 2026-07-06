package com.opuscapita.dbna.outbound.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * Configuration for DBNA AS4 messaging with OASIS BDXR profile.
 *
 * According to DBNA AS4 Profile v1.0, DBNA uses the OASIS BDXR AS4 profile:
 * https://docs.oasis-open.org/bdxr/bdx-as4/v1.0/cs01/bdx-as4-v1.0-cs01.html
 *
 * This configuration provides the BDXR OneWay PMode ID "bdxr-as4-1.0" which is passed
 * to the AS4Sender builder. Phase4 will look up this PMode in its configured profile.
 *
 * For full BDXR compliance without the phase4-profile-bdxr module, a PMode XML file
 * needs to be created and registered with Phase4's configuration.
 */
@Configuration
public class DBNAPModeConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(DBNAPModeConfiguration.class);

    private static final String BDXR_PMODE_ID = "bdxr-as4-1.0";

    /**
     * Log DBNA AS4 messaging initialization on application startup.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void logDBNAInitialization() {
        logger.info("DBNA AS4 messaging configured with BDXR PMode ID: {}", BDXR_PMODE_ID);
        logger.info("Reference: OASIS BDXR AS4 v1.0 - https://docs.oasis-open.org/bdxr/bdx-as4/v1.0/");
    }

    /**
     * Get the BDXR PMode ID to be used by AS4Sender builder.
     * This is the standard OASIS BDXR OneWay PMode ID.
     */
    public static String getDBNAPModeId() {
        return BDXR_PMODE_ID;
    }
}









