package io.cloudevents.experimental.endpoints.amqp;

import io.cloudevents.experimental.endpoints.amqp.AmqpConsumerEndpoint;
import io.cloudevents.experimental.endpoints.amqp.AmqpProducerEndpoint;

public final class AmqpProtocol {
    public static final String NAME = "amqp";

    static {
        AmqpConsumerEndpoint.register();
        AmqpProducerEndpoint.register();
    }
}
