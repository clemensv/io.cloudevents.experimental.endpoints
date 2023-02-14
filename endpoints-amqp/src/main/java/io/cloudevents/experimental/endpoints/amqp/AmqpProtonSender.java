package io.cloudevents.experimental.endpoints.amqp;

import java.util.concurrent.CompletableFuture;
import org.apache.qpid.proton.message.Message;

public interface AmqpProtonSender extends AutoCloseable {
    CompletableFuture<Void> sendAsync(Message message);
}