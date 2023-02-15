// (c) Cloud Native Computing Foundation. See LICENSE for details
package io.cloudevents.experimental.endpoints;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.message.Encoding;

/**
 * This class is both a base class and a factory for transport specific
 * "producer
 * endpoint" implementations as defined in the CloudEvents registry
 * specification.
 * A producer endpoint implementation typically attaches to a network or
 * messaging
 * infrastructure and sends events to consumer endpoints.
 * 
 * The factory method {@link #create(IEndpointCredential, String, Map, List)} is
 * used to create a producer endpoint instance.
 * 
 * The {@link #sendAsync(CloudEvent, EventFormat, Encoding)} method is used to
 * send a CloudEvent to a consumer endpoint using a specific event format and
 * encoding. The send operation is asynchronous and returns a future that will
 * be
 * completed when the send operation is complete.
 * 
 * Concrete producer endpoint implementations can be registered with the factory
 * at runtime by calling the
 * {@link #addProducerEndpointFactoryHook(ProducerEndpointFactoryHandler)}. The
 * factory will then use the registered hooks to create an instance of the
 * appropriate producer endpoint implementation.
 * 
 * The common message model for all producer endpoint implementations is a
 * CloudEvent. The producer endpoint implementation is responsible for
 * converting
 * the CloudEvent into a transport specific message and then sending the message
 * to the consumer endpoint.
 * 
 * Any producer endpoint implementation may have protocol-specific options and
 * protocol-specific delivery subscriber hooks that allow delivery "raw"
 * messages to subscribers.
 * 
 */
public abstract class ProducerEndpoint implements AutoCloseable {

	/**
	 * Constructor.
	 */
	public ProducerEndpoint() {
	}

	/**
	 * Create a producer endpoint instance with the specified credential,
	 * protocol, options, and endpoints. 
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
	 * @return The producer endpoint instance.
	 */
	public static ProducerEndpoint create(
			IEndpointCredential credential,
			String protocol,
			Map<String, String> options,
			List<URI> endpoints) {
		if (protocol == null) {
			throw new IllegalArgumentException("Protocol cannot be null.");
		}
		if (endpoints == null || endpoints.size() == 0) {
			throw new IllegalArgumentException("Endpoints cannot be null or empty.");
		}
		if (credential == null) {
			throw new IllegalArgumentException("Credential cannot be null.");
		}
		if (options == null) {
			throw new IllegalArgumentException("Options cannot be null.");
		}

		for (ProducerEndpointFactoryHandler hook : _producerEndpointFactoryHooks) {
			ProducerEndpoint ep = hook.invoke(credential, protocol, options, endpoints);
			if (ep != null) {
				return ep;
			}
		}

		switch (protocol) {
			default:
				throw new UnsupportedOperationException("Protocol '" + protocol + "' is not supported.");
		}
	}

	/**
	 * Send a CloudEvent to a consumer endpoint using a specific event format
	 * and encoding.
	 * @param cloudEvent The CloudEvent to send.
	 * @param formatter The event format to use.
	 * @param contentMode The encoding to use.
	 * @return A future that will be completed when the send operation is
	 * complete.
	 */
	public abstract CompletableFuture<Void> sendAsync(CloudEvent cloudEvent, Encoding contentMode,
			EventFormat formatter);

	/**
	 * The callback interface for producer endpoint factory hooks.
	 */
	public static interface ProducerEndpointFactoryHandler {
		ProducerEndpoint invoke(IEndpointCredential credential, String protocol, Map<String, String> options,
				List<URI> endpoints);
	}

	private static List<ProducerEndpointFactoryHandler> _producerEndpointFactoryHooks = new ArrayList<>();

	/**
	 * Add a producer endpoint factory hook. The hook will be invoked when a
	 * producer endpoint instance is created. The hook can return a producer
	 * endpoint instance if it can handle the specified protocol and options.
	 * @param hook The hook.
	 */
	public static void addProducerEndpointFactoryHook(ProducerEndpointFactoryHandler hook) {
		if (hook == null) {
			throw new IllegalArgumentException("Hook cannot be null.");
		}
		_producerEndpointFactoryHooks.add(hook);
	}

	/**
	 * Remove a producer endpoint factory hook.
	 * @param hook The hook.
	 */
	public static void removeProducerEndpointFactoryHook(ProducerEndpointFactoryHandler hook) {
		if (hook == null) {
			throw new IllegalArgumentException("Hook cannot be null.");
		}
		_producerEndpointFactoryHooks.remove(hook);
	}

}
