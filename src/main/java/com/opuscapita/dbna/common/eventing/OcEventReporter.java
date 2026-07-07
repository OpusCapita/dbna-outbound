package com.opuscapita.dbna.common.eventing;

import com.opuscapita.dbna.common.container.ContainerMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Simple event reporter implementation that logs events.
 * This can be extended to send events to external systems as needed.
 */
@Component
public class OcEventReporter implements EventReporter {

    private static final Logger logger = LoggerFactory.getLogger(OcEventReporter.class);

    @Override
    public void reportStatus(ContainerMessage cm) {
        try {
            logger.info("Event reported for message: {} at step: {}", 
                cm.getFileName(), 
                cm.getStep());
        } catch (Exception e) {
            logger.error("Failed to report event status for message: {}", cm.getFileName(), e);
        }
    }
}

