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

public abstract class ProducerEndpoint implements AutoCloseable {

	public static interface ProducerEndpointFactoryHandler {
		ProducerEndpoint invoke(IEndpointCredential credential, String protocol, Map<String, String> options,
				List<URI> endpoints);
	}

	private static List<ProducerEndpointFactoryHandler> _producerEndpointFactoryHooks = new ArrayList<>();

	public static void addProducerEndpointFactoryHook(ProducerEndpointFactoryHandler hook) {
		if (hook == null) {
			throw new IllegalArgumentException("Hook cannot be null.");
		}
		_producerEndpointFactoryHooks.add(hook);
	}

	public static void removeProducerEndpointFactoryHook(ProducerEndpointFactoryHandler hook) {
		if (hook == null) {
			throw new IllegalArgumentException("Hook cannot be null.");
		}
		_producerEndpointFactoryHooks.remove(hook);
	}

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

	public abstract CompletableFuture<Void> sendAsync(CloudEvent cloudEvent, Encoding contentMode,
			EventFormat formatter);

}




