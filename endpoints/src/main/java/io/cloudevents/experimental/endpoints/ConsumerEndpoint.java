
package io.cloudevents.experimental.endpoints;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;


import io.cloudevents.CloudEvent;

/**
 * This class is both a base class and a factory for transport specific "consumer
 * endpoint" implementations as defined in the CloudEvents registry specification.
 * A consumer endpoint implementation typically attaches to a network or messaging 
 * infrastructure and receives events from producer endpoints.
 * 
 * The factory method {@link #create(IEndpointCredential, String, Map, List)} is
 * used to create a consumer endpoint instance. 
 * 
 * Consumer endpoints host a receive loop that receives events from producer
 * endpoints and delivers them to subscribers. The receive loop is typically
 * implemented as a separate thread. The receive loop is started by calling the
 * {@link #startAsync()} method and stopped by calling the {@link #stopAsync()}
 * method.
 * 
 * The {@link #subscribe(DispatchCloudEventAsync)} and
 * {@link #unsubscribe(DispatchCloudEventAsync)} methods are used to register
 * and unregister subscribers. The receive loop will deliver events to all
 * registered subscribers.
 * 
 * Concrete consumer endpoint implementations can be registered with the factory
 * at runtime by calling the
 * {@link #addConsumerEndpointFactoryHook(ConsumerEndpointFactoryHandler)}. The
 * factory will then use the registered hooks to create an instance of the
 * appropriate consumer endpoint implementation.
 * 
 * The common message model for all consumer endpoint implementations is a
 * CloudEvent. The consumer endpoint implementation is responsible for converting
 * the transport specific message into a CloudEvent instance and then delivering
 * the CloudEvent to the registered subscribers.
 * 
 * Any consumer endpoint implementation may have protocol-specific options and
 * protocol-specific delivery subscriber hooks that allow delivery "raw"
 * messages to subscribers.
 */
public abstract class ConsumerEndpoint implements AutoCloseable {

	/**
	 * Constructor.
	 */
	public ConsumerEndpoint() {
	}
    
	/**
	 * This is the callback interface used for delivering CloudEvents to subscribers.
	 */
	public interface DispatchCloudEventAsync {
		/**
		 * Deliver a CloudEvent to the subscriber.
		 * @param event The CloudEvent to deliver.	
		 */ 
        void onEvent(CloudEvent event);
    }
    
	private final Set<DispatchCloudEventAsync> subscribers = new HashSet<>();

	/**
	 * Subscribe to receive CloudEvents from this consumer endpoint.
	 * @param subscriber the subscriber
	 */
	public void subscribe(DispatchCloudEventAsync subscriber) {
		subscribers.add(subscriber);
	}

	/**
	 * Unsubscribe from receiving CloudEvents from this consumer endpoint.
	 * @param subscriber the subscriber
	 */
	public void unsubscribe(DispatchCloudEventAsync subscriber) {
		subscribers.remove(subscriber);
	}

	/**
	 * Start the consumer endpoint.
	 * @return A future that will be completed when the consumer endpoint has
	 */
	public abstract CompletableFuture<Void> startAsync();

	/**
	 * Stop the consumer endpoint.
	 * @return A future that will be completed when the consumer endpoint has
	 */
	public abstract CompletableFuture<Void> stopAsync();

	/**
	 * Deliver a message to the subscribers.
	 * @param message The message to deliver.
	 * @param <T> The type of the message.
	 */
	protected <T> void deliver(T message) {
		if (message instanceof CloudEvent) {
			for (DispatchCloudEventAsync subscriber : subscribers) {
				subscriber.onEvent((CloudEvent) message);
			}
		}
	}

	/**
	 * Factory method for creating a consumer endpoint instance.
	 * @param credential a credential for authenticating with the endpoint. The
	 * {@link io.cloudevents.experimental.endpoints.IEndpointCredential}
	 * interface is a marker interface that is implemented by credential
	 * classes. The
	 * {@link io.cloudevents.experimental.endpoints.PlainEndpointCredential}
	 * class implements a simple client-id and client-secret credential that
	 * should be supported by all consumer endpoint implementations.
	 * @param protocol The protocol name and version, e.g. "HTTP/1.1". The
	 * version number is optional and may be omitted unless the provider of the
	 * consumer endpoint implementation needs to be instructed to use a specific
	 * version of the protocol.
	 * @param options The protocol-specific options. The options are defined by
	 * the provider of the consumer endpoint implementation.
	 * @param endpoints The list of endpoint URIs. The endpoint URI requirements
	 * are defined by the provider of the consumer endpoint implementation.
	 * Usually, the list of endpoints will contain a single endpoint. 
	 * @return A consumer endpoint instance.
	 */
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

	/**
	 * Register a consumer endpoint factory hook for a specific protocol.
	 * @param hook The hook.
	 */
	public static void addConsumerEndpointFactoryHook(ConsumerEndpointFactoryHandler hook) {
		_consumerEndpointFactoryHooks.add(hook);
	}

	/**
	 * Unregister a consumer endpoint factory hook for a specific protocol.
	 * @param hook The hook.
	 */
	public static void removeConsumerEndpointFactoryHook(ConsumerEndpointFactoryHandler hook) {
		_consumerEndpointFactoryHooks.remove(hook);
	}

	/**
	 * This is the callback interface used for creating a consumer endpoint instance.
	 */
	public interface ConsumerEndpointFactoryHandler {
		/**
		 * Create a consumer endpoint instance.
		 * @param credential a credential for authenticating with the endpoint. The
		 * @param protocol The protocol name and version, e.g. "HTTP". The
		 * @param options The protocol-specific options. The options are defined by
		 * @param endpoints The list of endpoint URIs. The endpoint URI requirements
		 * @return A consumer endpoint instance or null if the hook does not support
		 */
		ConsumerEndpoint invoke(IEndpointCredential credential, String protocol, Map<String, String> options,
				List<URI> endpoints);
	}

}