
package io.cloudevents.experimental.endpoints.amqp;


/**
 * This interface is used to register a receiver on an AMQP endpoint.
 * @see AmqpProtonClient#createSubscriber(String, MessageHandler)
 */
public interface AmqpProtonSubscriber extends AutoCloseable {
    /** 
     * Get the message handler.
     */
    public MessageHandler getHandler();
}
