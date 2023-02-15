package io.cloudevents.experimental.endpoints.amqp;

public final class AmqpProtocol {
    public static final String NAME = "amqp";

    /**
     * Registers the AMQP protocol.
     */
    public static void register() {
        AmqpConsumerEndpoint.register();
        AmqpProducerEndpoint.register();
    }    
}
