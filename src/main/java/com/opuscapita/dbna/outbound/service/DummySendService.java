package com.opuscapita.dbna.outbound.service;

import com.opuscapita.dbna.outbound.model.DummyResponse;
import com.opuscapita.dbna.common.container.ContainerMessage;
import com.opuscapita.dbna.outbound.model.TransmissionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DummySendService implements SendService {

    private static final Logger logger = LoggerFactory.getLogger(DummySendService.class);

    @Override
    public TransmissionResponse send(ContainerMessage cm) throws Exception {
        // Thread.sleep(2000); // pretend like not fake
        DummyResponse.throwExceptionIfExpectedInFilename(cm);

        logger.info("FakeSender emulated sending, returning some transmission response");
        return new DummyResponse();
    }

}
