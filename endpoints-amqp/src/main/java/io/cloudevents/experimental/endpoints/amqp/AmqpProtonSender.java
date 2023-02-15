package io.cloudevents.experimental.endpoints.amqp;

import java.util.concurrent.CompletableFuture;
import org.apache.qpid.proton.message.Message;


/**
 * This interface is used to send messages to an AMQP endpoint.
 * @see AmqpProtonClient#createSender(String)
 */
public interface AmqpProtonSender extends AutoCloseable {
    /**
     * Sends a message to the endpoint.
     * @param message The message to send.
     * @return A future that will be completed when the message has been sent.
     */
    CompletableFuture<Void> sendAsync(Message message);
}