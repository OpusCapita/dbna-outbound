package com.opuscapita.dbna.common.eventing;

import com.opuscapita.dbna.common.container.ContainerMessage;

public interface EventReporter {

    void reportStatus(ContainerMessage cm);
}

