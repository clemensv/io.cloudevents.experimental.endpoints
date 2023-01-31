// (c) Cloud Native Computing Foundation. See LICENSE for details
package io.cloudevents.experimental.endpoints;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import java.util.logging.Logger;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.message.Encoding;

public abstract class ProducerEndpoint implements AutoCloseable {

	public static interface ProducerEndpointFactoryHandler {
		ProducerEndpoint invoke(Logger logger, IEndpointCredential credential, String protocol, Map<String, String> options,
				List<URI> endpoints);
	}

	private static List<ProducerEndpointFactoryHandler> _producerEndpointFactoryHooks = new ArrayList<>();

	public static void addProducerEndpointFactoryHook(ProducerEndpointFactoryHandler hook) {
		_producerEndpointFactoryHooks.add(hook);
	}

	public static void removeProducerEndpointFactoryHook(ProducerEndpointFactoryHandler hook) {
		_producerEndpointFactoryHooks.remove(hook);
	}

	public static ProducerEndpoint create(Logger logger, IEndpointCredential credential, String protocol, Map<String, String> options,
			List<URI> endpoints) {
		for (ProducerEndpointFactoryHandler hook : _producerEndpointFactoryHooks) {
			ProducerEndpoint ep = hook.invoke(logger, credential, protocol, options, endpoints);
			if (ep != null) {
				return ep;
			}
		}

		switch (protocol) {
		default:
			throw new UnsupportedOperationException("Protocol '" + protocol + "' is not supported.");
		}
	}

	public abstract void sendAsync(CloudEvent cloudEvent, Encoding contentMode, EventFormat formatter) throws Exception;

	@Override
	public void close() throws Exception {
		// TODO Auto-generated method stub

	}

}