package io.cloudevents.experimental.endpoints.mqtt;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import io.cloudevents.experimental.endpoints.ConsumerEndpoint;
import io.cloudevents.experimental.endpoints.IEndpointCredential;

public class MqttConsumerEndpoint extends ConsumerEndpoint {

    private IEndpointCredential _credential;
    private List<URI> _endpoints;
    private String _node;

    public MqttConsumerEndpoint(Logger logger, IEndpointCredential credential, Map<String, String> options,
            List<URI> endpoints) {
        super(logger);
        _credential = credential;
        _endpoints = endpoints;
        if (options.containsKey("node")) {
            _node = options.get("node");
        }
    }

    static String ERROR_LOG_TEMPLATE = "Error in mqttConsumerEndpoint: {0}";
    static String VERBOSE_LOG_TEMPLATE = "mqttConsumerEndpoint: {0}";

    public interface DispatchMqttV3MessageAsync {
        void onMessage(org.eclipse.paho.client.mqttv3.MqttMessage message, Logger logger);
    }

    private final Set<DispatchMqttV3MessageAsync> mqttV3Subscribers = new HashSet<>();

    public void subscribe(DispatchMqttV3MessageAsync subscriber) {
        mqttV3Subscribers.add(subscriber);
    }

    public void unsubscribe(DispatchMqttV3MessageAsync subscriber) {
        mqttV3Subscribers.remove(subscriber);
    }

    public interface DispatchMqttV5MessageAsync {
        void onMessage(org.eclipse.paho.mqttv5.common.packet.MqttWireMessage message, Logger logger);
    }

    private final Set<DispatchMqttV5MessageAsync> mqttV5Subscribers = new HashSet<>();

    public void subscribe(DispatchMqttV5MessageAsync subscriber) {
        mqttV5Subscribers.add(subscriber);
    }

    public void unsubscribe(DispatchMqttV5MessageAsync subscriber) {
        mqttV5Subscribers.remove(subscriber);
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
            if (protocol.equals("mqtt")) {
                return new MqttConsumerEndpoint(logger, credential, options, endpoints);
            }
            return null;
        });
    }
}
