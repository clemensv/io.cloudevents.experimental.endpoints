package io.cloudevents.experimental.endpoints.amqp;

import org.apache.qpid.proton.message.Message;

@FunctionalInterface
public interface MessageHandler {
    void handle(Message amqpMessage, MessageContext context);
}