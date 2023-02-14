// implement the AMQP consumer endpoint using azure.core.amqp

package io.cloudevents.experimental.endpoints.amqp;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.apache.qpid.proton.message.Message;

import io.cloudevents.amqp.ProtonAmqpMessageFactory;
import io.cloudevents.experimental.endpoints.ConsumerEndpoint;
import io.cloudevents.experimental.endpoints.IEndpointCredential;

public class AmqpConsumerEndpoint extends ConsumerEndpoint {

    private static final Logger logger = LogManager.getLogger(AmqpConsumerEndpoint.class);
    private AmqpProtonClient _client;
    String node;

    public AmqpConsumerEndpoint(IEndpointCredential credential, Map<String, String> options,
            List<URI> endpoints) {
        
        if (endpoints == null || endpoints.size() == 0) {
            throw new IllegalArgumentException("endpoints");
        }
        var endpoint = endpoints.get(0);
        if (options != null && options.containsKey("node")) {
            node = options.get("node");
        } else {
            node = endpoint.getPath();
        }
        if (credential == null) {
            throw new IllegalArgumentException("credential");
        }
        this._client = new AmqpProtonClient(endpoint, credential);
    }

    static String ERROR_LOG_TEMPLATE = "Error in AMQPConsumerEndpoint: %s";
    static String VERBOSE_LOG_TEMPLATE = "AMQPConsumerEndpoint: %s";

    public interface DispatchMessageAsync {
        void onMessage(Message amqpMessage, MessageContext context);
    }

    private final Set<DispatchMessageAsync> subscribers = new HashSet<>();
    private AmqpProtonSubscriber _subscriber;

    public void subscribe(DispatchMessageAsync subscriber) {
        subscribers.add(subscriber);
    }

    public void unsubscribe(DispatchMessageAsync subscriber) {
        subscribers.remove(subscriber);
    }

    @Override
    public CompletableFuture<Void> startAsync() {
        CompletableFuture<Void> future = new CompletableFuture<>();

        try {
            this._subscriber = _client.createSubscriber(node, (amqpMessage, context) -> {

                for (DispatchMessageAsync d : subscribers) {
                    d.onMessage(amqpMessage, context);
                }
                if (!context.getIsSettled()) {
                    boolean hasCEProperty = false;
                    var props = amqpMessage.getApplicationProperties();
                    if (props != null) {
                        for (var prop : props.getValue().keySet()) {
                            if (prop.toString().startsWith("cloudEvents")) {
                                hasCEProperty = true;
                                break;
                            }
                        }
                    }
                    if (amqpMessage.getContentType() != null
                        && (amqpMessage.getContentType().startsWith("application/cloudevents") || hasCEProperty )) {
                        // let's try to parse the message as a CloudEvent
                        try {
                            var reader = ProtonAmqpMessageFactory.createReader(amqpMessage);
                            this.deliver(reader.toEvent());
                            context.accept();
                        } catch (Exception e) {
                            logger.warn(String.format(ERROR_LOG_TEMPLATE, e.getMessage()));
                            context.reject();
                        }
                    } else {
                        context.accept();
                    }
                }
            });
            future.complete(null);
        } catch (Exception e) {
            logger.warn(String.format(ERROR_LOG_TEMPLATE, e.getMessage()));
            future.completeExceptionally(e);
        }
        return future;
    }

    @Override
    public CompletableFuture<Void> stopAsync() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            this._subscriber.close();
            future.complete(null);
        } catch (Exception e) {
            logger.warn(String.format(ERROR_LOG_TEMPLATE, e.getMessage()));
            future.completeExceptionally(e);
        }
        return future;
    }

    @Override
    protected <T> void deliver(T message) {
        super.deliver(message);
    }

    public static void register() {
        ConsumerEndpoint.addConsumerEndpointFactoryHook((credential, protocol, options, endpoints) -> {
            if (protocol.equals("amqp")) {
                return new AmqpConsumerEndpoint(credential, options, endpoints);
            }
            return null;
        });
    }

    @Override
    public void close() {
        try {
            this.stopAsync().get();
        } catch (Exception e) {
            logger.warn(String.format(ERROR_LOG_TEMPLATE, e.getMessage()));
        }
    }

}
