package com.opuscapita.dbna.outbound.service;

import com.opuscapita.dbna.outbound.config.NetworkConfiguration;
import com.opuscapita.dbna.outbound.exception.SendServiceFactoryException;
import com.opuscapita.dbna.common.container.ContainerMessage;
import com.opuscapita.dbna.common.container.state.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SendServiceFactory {

    private final static Logger logger = LoggerFactory.getLogger(SendServiceFactory.class);

    private final DummySendService dummySendService;
    private final AS4SendService as4SendService;
    private final SiriusSendService siriusSender;
    private final NetworkConfiguration configuration;

    public SendServiceFactory(
            DummySendService dummySendService,
            AS4SendService as4SendService,
            SiriusSendService siriusSender,
            NetworkConfiguration networkConfiguration) {
        this.dummySendService = dummySendService;
        this.as4SendService = as4SendService;
        this.siriusSender = siriusSender;
        this.configuration = networkConfiguration;
    }


    public SendService getService(ContainerMessage cm, Source destination) throws Exception {
        if (configuration.getStopConfig().contains(destination.name())) {
            throw new SendServiceFactoryException("Stopped delivery to " + destination + " as requested");
        }

        if (configuration.getFakeConfig().contains(destination.name())) {
            logger.info("Selected to send via FAKE sender for file: " + cm.getFileName());
            return dummySendService;
        }

        return switch (destination) {
            case NETWORK -> as4SendService;
            case SIRIUS -> siriusSender;
            case UNKNOWN -> {
                logger.warn("Unknown destination for file: " + cm.getFileName());
                throw new RuntimeException("destination UNKNOWN!");
            }
            default -> throw new RuntimeException("This poor lonely document has nowhere to go!");
        };

    }

}
