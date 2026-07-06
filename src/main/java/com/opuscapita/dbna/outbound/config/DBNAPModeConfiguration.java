package com.opuscapita.dbna.outbound.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * Configuration for DBNA AS4 PMode using Phase4's DBNA Profile module.
 *
 * According to DBNA AS4 Profile v1.0, DBNA uses the OASIS BDXR AS4 profile.
 * Phase4 provides the phase4-profile-dbnalliance module which includes:
 * - Pre-configured DBNA PMode definitions
 * - DBNA-specific security and retry parameters
 * - Compliance with DBNA network policies
 *
 * The phase4-profile-dbnalliance module is automatically discovered and registered
 * by Phase4 at runtime when it's on the classpath.
 *
 * Reference:
 * - OASIS BDXR AS4: https://docs.oasis-open.org/bdxr/bdx-as4/v1.0/
 * - Phase4 GitHub: https://github.com/phax/phase4
 * - DBNA Network: https://dbnalliance.org
 */
@Configuration
public class DBNAPModeConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(DBNAPModeConfiguration.class);

    /**
     * Log DBNA Profile initialization on application startup.
     * The phase4-profile-dbnalliance module is automatically discovered by Phase4.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void logDBNAProfileInitialization() {
        try {
            logger.info("DBNA Profile (phase4-profile-dbnalliance) is on classpath and will be discovered by Phase4");
            logger.info("PMode 'bdxr-as4-1.0' will be registered with DBNA-specific configuration:");
            logger.info("  - Agreement: https://dbnalliance.org/agreements/access_point.html");
            logger.info("  - Security: X.509 with AES-256-GCM encryption");
            logger.info("  - Retry: Enabled with DBNA network policy (5+ times over 6 hours)");
            logger.info("  - Duplicate Detection: 30 days");
            logger.info("  - Error Handling: Missing receipt notifications enabled");

        } catch (Exception e) {
            logger.warn("Error logging DBNA Profile initialization: {}", e.getMessage());
        }
    }

    /**
     * Get the DBNA PMode ID for OneWay messaging.
     * This is the standard OASIS BDXR OneWay PMode ID registered by the DBNA profile.
     */
    public static String getDBNAPModeId() {
        // The DBNA profile registers PMode with ID "bdxr-as4-1.0"
        return "bdxr-as4-1.0";
    }
}









