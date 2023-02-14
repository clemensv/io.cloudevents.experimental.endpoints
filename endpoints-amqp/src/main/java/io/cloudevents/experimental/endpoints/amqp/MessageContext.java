package io.cloudevents.experimental.endpoints.amqp;

public interface MessageContext {
    void accept();

    void reject();
    
    void release();

    void modify(boolean deliveryFailed, boolean undeliverableHere);

    boolean getIsSettled();
}