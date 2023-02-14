
package io.cloudevents.experimental.endpoints;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;


import io.cloudevents.CloudEvent;

public abstract class ConsumerEndpoint implements AutoCloseable {

    public ConsumerEndpoint() {
    }
    
    public interface DispatchCloudEventAsync {
        void onEvent(CloudEvent event);
    }
    
    private final Set<DispatchCloudEventAsync> subscribers = new HashSet<>();

    public void subscribe(DispatchCloudEventAsync subscriber) {
        subscribers.add(subscriber);
    }

    public void unsubscribe(DispatchCloudEventAsync subscriber) {
        subscribers.remove(subscriber);
    }

	public abstract CompletableFuture<Void> startAsync();

	public abstract CompletableFuture<Void> stopAsync();

	protected <T> void deliver(T message) {
		if (message instanceof CloudEvent) {
			for (DispatchCloudEventAsync subscriber : subscribers) {
                subscriber.onEvent((CloudEvent)message);
            }
		}
	}

	public static ConsumerEndpoint create(IEndpointCredential credential, String protocol, Map<String, String> options,
			List<URI> endpoints) {
		for (ConsumerEndpointFactoryHandler hook : _consumerEndpointFactoryHooks) {
			ConsumerEndpoint ep = hook.invoke(credential, protocol, options, endpoints);
			if (ep != null) {
				return ep;
			}
		}

		switch (protocol) {
		default:
			throw new UnsupportedOperationException("Protocol '" + protocol + "' is not supported.");
		}
	}

	private static List<ConsumerEndpointFactoryHandler> _consumerEndpointFactoryHooks = new ArrayList<>();

	public static void addConsumerEndpointFactoryHook(ConsumerEndpointFactoryHandler hook) {
		_consumerEndpointFactoryHooks.add(hook);
	}

	public static void removeConsumerEndpointFactoryHook(ConsumerEndpointFactoryHandler hook) {
		_consumerEndpointFactoryHooks.remove(hook);
	}

	public interface ConsumerEndpointFactoryHandler {
		ConsumerEndpoint invoke(IEndpointCredential credential, String protocol, Map<String, String> options,
				List<URI> endpoints);
	}

}