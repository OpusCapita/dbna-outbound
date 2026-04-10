package com.opuscapita.dbna.outbound.consumer;

import com.opuscapita.dbna.outbound.error.OutboundErrorHandler;
import com.opuscapita.dbna.outbound.service.SendService;
import com.opuscapita.dbna.outbound.service.SendServiceFactory;
import com.opuscapita.dbna.common.container.ContainerMessage;
import com.opuscapita.dbna.common.container.state.ProcessStep;
import com.opuscapita.dbna.common.container.state.Source;
import com.opuscapita.dbna.common.eventing.EventReporter;
import com.opuscapita.dbna.common.queue.consume.ContainerMessageConsumer;
import com.opuscapita.dbna.outbound.model.TransmissionResponse;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class OutboundMessageConsumer implements ContainerMessageConsumer {

    private static final Logger logger = LoggerFactory.getLogger(OutboundMessageConsumer.class);

    private final SendServiceFactory sendServiceFactory;
    private final EventReporter eventReporter;
    private final OutboundErrorHandler errorHandler;

    public OutboundMessageConsumer(SendServiceFactory sendServiceFactory, EventReporter eventReporter, OutboundErrorHandler errorHandler) {
        this.sendServiceFactory = sendServiceFactory;
        this.eventReporter = eventReporter;
        this.errorHandler = errorHandler;
    }

    @Override
    public void consume(@NotNull ContainerMessage cm) throws Exception {
        cm.setStep(ProcessStep.OUTBOUND);
        Source destination = cm.getRoute().getDestination();
        cm.getHistory().addInfo("Received and started transmission");
        logger.info("Outbound received the message: {} destination: {}", cm.toKibana(), destination);

        if (StringUtils.isBlank(cm.getFileName())) {
            throw new IllegalArgumentException("File name is empty in received message: " + cm.toKibana());
        }

        SendService service = null;
        try {
            service = sendServiceFactory.getService(cm, destination);
            cm.getHistory().addInfo("About to send file using: " + service.getClass().getSimpleName());

            TransmissionResponse response = service.send(cm);

            cm.setStep(ProcessStep.NETWORK); // a virtual step, shows that it is delivered out
            cm.getHistory().addInfo("Successfully delivered to " + destination);
            logger.info("The message {} successfully delivered to {} with transmission ID = {}", cm.toKibana(), destination, response.getTransmissionIdentifier());
            logger.debug("MDN Receipt(s) for {} is = {}", cm.getFileName(), response.getReceipts().stream().map(Object::toString).collect(Collectors.joining(", ")));

        } catch (Exception exception) {
            cm.getHistory().addInfo("Message delivery failed");
            errorHandler.handle(cm, service, exception);
        }

        eventReporter.reportStatus(cm);
    }

}
