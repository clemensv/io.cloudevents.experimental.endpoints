package io.cloudevents.experimental.endpoints.amqp;

/**
 * This interface is given to the message handler when a message is received. It
 * is used to accept or reject the message. 
 * 
 * In the context of the AmqpConsumerEndpoint, you can register a message
 * handler that inspects the message and does not call any of these methods.
 * This will cause the message to bubble up to the CloudEvents consumer. If the
 * message is a CloudEvent, the consumer will be called and the message will be
 * auto-accepted. If the message is not a CloudEvent, the message will be
 * rejected.
 * 
 * Outside of the context of the AmqpConsumerEndpoint, messages will be 
 * auto-rejected if the message handler does not call any of these methods.
 * 
 * The message handler can call one of these methods once. Any subsequent calls
 * will be ignored.
 * 
 * @see MessageHandler
 * @see AmqpProtonSubscriber
 * @see AmqpConsumerEndpoint
 */
public interface MessageContext {
    /**
     * Accepts the message. With a queue broker, this will mean that
     * the message will be removed from the queue. 
     */
    void accept();

    /**
     * Rejects the message. With a queue broker, this will mean that
     * the message will no longer be available for delivery and may 
     * be moved to a dead letter queue.
     */
    void reject();
    
    /**
     * Releases the message. With a queue broker, this will mean that
     * the message will be available for delivery again.
     */
    void release();

    /**
     * Modifies the message. With a queue broker, this will mean that
     * the message will be available for delivery again, but with 
     * modifications.
     */
    void modify(boolean deliveryFailed, boolean undeliverableHere);

    /**
     * Gets whether the message has been settled. You must not call
     * any of the accept, reject, release, or modify methods after
     * the message has been settled.
     * @return true if the message has been settled by calling one of the
     * accept, reject, release, or modify methods.
     */
    boolean getIsSettled();
}