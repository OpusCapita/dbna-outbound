package com.opuscapita.dbna.common.eventing;

import com.opuscapita.dbna.common.container.ContainerMessage;

public interface TicketReporter {

    void reportWithContainerMessage(ContainerMessage cm, Throwable e, String shortDescription);

    void reportWithContainerMessage(ContainerMessage cm, Throwable e, String shortDescription, String additionalDetails);

    void reportWithoutContainerMessage(String customerId, String fileName, Throwable e, String shortDescription);

    void reportWithoutContainerMessage(String customerId, String fileName, Throwable e, String shortDescription, String additionalDetails);
}

