package io.cloudevents.experimental.endpoints.amqp;

public final class AmqpProtocol {
    public static final String NAME = "amqp";

    public static void register() {
        AmqpConsumerEndpoint.register();
        AmqpProducerEndpoint.register();
    }

    
}
