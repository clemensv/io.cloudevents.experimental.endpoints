
package io.cloudevents.experimental.endpoints.amqp;

public interface AmqpProtonSubscriber extends AutoCloseable {
    public MessageHandler getHandler();
}
