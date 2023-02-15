package io.cloudevents.experimental.endpoints.amqp;

import org.apache.qpid.proton.message.Message;

/**
 * This interface is used to register a receiver on an AMQP endpoint.
 * @see AmqpProtonClient#createSubscriber(String, MessageHandler)
 */
@FunctionalInterface
public interface MessageHandler {
    /**
     * This method is called when a message is received.
     * @param amqpMessage The message received.
     * @param context The message context.
     * @see MessageContext
     * @see Message
     */
    void handle(Message amqpMessage, MessageContext context);
}