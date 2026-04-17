package com.opuscapita.dbna.common.queue.consume;

import com.opuscapita.dbna.common.container.ContainerMessage;

public interface ContainerMessageConsumer {

    void consume(ContainerMessage cm) throws Exception;
}

