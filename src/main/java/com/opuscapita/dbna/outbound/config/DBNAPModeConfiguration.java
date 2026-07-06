package com.opuscapita.dbna.outbound.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * Configuration for Phase4 AS4 messaging support.
 *
 * DBNA (Digital Business Networks Alliance) uses AS4 messaging for document exchange.
 * This configuration class serves as a marker for Phase4 AS4 initialization.
 * The actual AS4 message configuration happens in AS4SendService at send time.
 */
@Configuration
public class DBNAPModeConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(DBNAPModeConfiguration.class);

    private static final String DBNA_PMODE_ID = "DBNA-AS4";

    /**
     * Initialize Phase4 support on application startup.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializePhase4() {
        try {
            logger.info("Phase4 AS4 messaging support ready for DBNA document transmission");
        } catch (Exception e) {
            logger.warn("Phase4 initialization check: {}", e.getMessage());
        }
    }

    /**
     * Get the DBNA PMode ID for use by AS4Sender builder.
     * Note: This PMode ID is used by the builder but may not be registered in MetaAS4Manager
     * if profile modules are not available. The builder will use the ID reference anyway.
     */
    public static String getDBNAPModeId() {
        return DBNA_PMODE_ID;
    }
}









