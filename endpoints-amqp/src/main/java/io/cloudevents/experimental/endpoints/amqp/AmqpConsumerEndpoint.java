package io.cloudevents.experimental.endpoints.amqp;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import com.azure.core.amqp.models.AmqpAnnotatedMessage;

import io.cloudevents.experimental.endpoints.ConsumerEndpoint;
import io.cloudevents.experimental.endpoints.IEndpointCredential;

public class AmqpConsumerEndpoint extends ConsumerEndpoint {
    
    private IEndpointCredential _credential;
    private List<URI> _endpoints;
    private String _node;

    
    public AmqpConsumerEndpoint(Logger logger, IEndpointCredential credential, Map<String, String> options, List<URI> endpoints)  
    {
        super(logger);
        _credential = credential;
        _endpoints = endpoints;
        if (options.containsKey("node")) {
            _node = options.get("node");
        }
    }

    static String ERROR_LOG_TEMPLATE = "Error in AMQPConsumerEndpoint: {0}";
    static String VERBOSE_LOG_TEMPLATE = "AMQPConsumerEndpoint: {0}";

    
    public interface DispatchMessageAsync {
        void onMessage(AmqpAnnotatedMessage amqpMessage, Logger logger);
    }
    
    private final Set<DispatchMessageAsync> subscribers = new HashSet<>();

    public void subscribe(DispatchMessageAsync subscriber) {
        subscribers.add(subscriber);
    }

    public void unsubscribe(DispatchMessageAsync subscriber) {
        subscribers.remove(subscriber);
    }


    @Override
    public CompletableFuture<Void> startAsync() {
        
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Void> stopAsync() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    protected <T> void deliver(T message) {
        super.deliver(message);
    }

    public static void register() {
        ConsumerEndpoint.addConsumerEndpointFactoryHook((logger, credential, protocol, options, endpoints) -> {
            if (protocol.equals("amqp")) {
                return new AmqpConsumerEndpoint(logger, credential, options, endpoints);
            }
            return null;
        });
    }
}
    