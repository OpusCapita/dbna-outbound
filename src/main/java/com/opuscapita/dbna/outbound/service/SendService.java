package com.opuscapita.dbna.outbound.service;

import com.opuscapita.dbna.common.container.ContainerMessage;
import com.opuscapita.dbna.outbound.model.TransmissionResponse;

@FunctionalInterface
public interface SendService {

    TransmissionResponse send(ContainerMessage cm) throws Exception;

    default int getRetryCount() {
        return 0;
    }

    default int getRetryDelay() {
        return 100000;
    }

}
