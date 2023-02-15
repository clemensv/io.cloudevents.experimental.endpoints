package io.cloudevents.experimental.endpoints.amqp;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.qpid.proton.message.Message;

import io.cloudevents.CloudEvent;
import io.cloudevents.amqp.ProtonAmqpMessageFactory;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.message.Encoding;
import io.cloudevents.experimental.endpoints.IEndpointCredential;
import io.cloudevents.experimental.endpoints.ProducerEndpoint;

/**
 * This class is a producer endpoint implementation that uses AMQP 1.0 as
 * the underlying protocol. The class uses the Apache Qpid Proton-J library
 * and the AMQP extensions of the CloudEvents SDK for Java to implement the
 * AMQP 1.0 protocol support.
 * 
 * The class does not have a public constructor. Instead, instances are
 * created by calling the static
 * {@link io.cloudevents.experimental.endpoints.ProducerEndpoint#create(IEndpointCredential, Map, List)}
 * factory method on the base class.
 * 
 * The AMQP protocol support must be registered with the factory by calling the
 * static {@link #register()} method once during application startup.
 * 
 * The "options" parameter to the factory method is optionally used to specify
 * the AMQP "node" (queue, topic, subscription, etc.) to which the consumer
 * endpoint will attach. The node name can also be specified as a path in the
 * endpoint URI. If the node name is specified in both the options and the URI,
 * the value in the options takes precedence.
 * 
 * In addition to the CloudEvents message model, the AMQP producer endpoint
 * implementation also supports sending "raw" AMQP messages to the consumer
 * endpoint. The {@link #sendAsync(Message)} method can be used to send a raw
 * AMQP message to the consumer endpoint. The message does not need to be a
 * CloudEvent message. 
 * 
 * Example for CloudEvents:
 * 
 * <pre>
 * {@code
 * // Create a producer endpoint that connects to the AMQP broker at
 * // "amqp://localhost:5672" and attaches to the "myqueue" node.
 * 
 * var credential = new PlainEndpointCredential("username", "password");
 * var endpoint = ProducerEndpoint.create(
 *                      credential, "amqp", 
 *                      Map.of("node", "myqueue"), 
 *                      List.of(URI.create("amqp://localhost:5672")));
 * 
 * // Send a CloudEvent message to the consumer endpoint.
 * var event = CloudEventBuilder.v1()
 *                  .withId(UUID.randomUUID().toString())
 *                  .withSource(URI.create("/myapp"))
 *                  .withType("myevent")
 *                  .withData("text/plain", "Hello, world!".getBytes())
 *                  .build();
 * 
 * endpoint.sendAsync(event, Encoding.BINARY, EventFormatProvider.getInstance().resolveFormat("json")).join();
 * }
 * </pre>
 * 
 * Example for AMQP:
 * 
 * <pre>
 * {@code
 * // Create a producer endpoint that connects to the AMQP broker at
 * // "amqp://localhost:5672" and attaches to the "myqueue" node.
 * 
 * var credential = new PlainEndpointCredential("username", "password");
 * var endpoint = (AmqpProducerEndpoint)ProducerEndpoint.create(
 *                      credential, "amqp", 
 *                      Map.of("node", "myqueue"), 
 *                      List.of(URI.create("amqp://localhost:5672")));
 *
 * // Send a raw AMQP message to the consumer endpoint.
 * var message = Proton.message();
 * message.setAddress("myqueue");
 * message.setBody(new AmqpValue("Hello, world!"));
 * endpoint.sendAsync(message).join();
 * }
 * </pre>
 */
public class AmqpProducerEndpoint extends ProducerEndpoint {

    private AmqpProtonClient _client;
    String node;
    private AmqpProtonSender _sender;

    /**
     * Private constructor. Instances are created by calling the factory method
     * @param credential The endpoint credential
     * @param options The endpoint options
     * @param endpoints The endpoint URIs
     */
    AmqpProducerEndpoint(IEndpointCredential credential, Map<String, String> options, List<URI> endpoints) {
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

    /**
     * Registers the AMQP protocol support with the producer endpoint factory.
     */
    public static void register() {

        addProducerEndpointFactoryHook((credential, protocol, options, endpoints) -> {
            if (protocol.equals("amqp")) {
                return new AmqpProducerEndpoint(credential, options, endpoints);
            }
            return null;
        });
    }

    /**
     * Sends a CloudEvent message to the consumer endpoint.
     * @param cloudEvent The CloudEvent message to send
     * @param contentMode The content mode of the message
     * @param formatter The event formatter to use
     * @return A CompletableFuture that completes when the message has been sent
     */
    @Override
    public CompletableFuture<Void> sendAsync(CloudEvent cloudEvent, Encoding contentMode, EventFormat formatter) {
        var writer = ProtonAmqpMessageFactory.createWriter();

        Message amqpMessage = null;
        if (contentMode == Encoding.BINARY) {
            amqpMessage = writer.writeBinary(cloudEvent);
        } else if (contentMode == Encoding.STRUCTURED) {
            amqpMessage = writer.writeStructured(cloudEvent, formatter);
        }
        return sendAsync(amqpMessage);
    }

    /**
     * Sends a raw AMQP message to the consumer endpoint.
     * @param amqpMessage The AMQP message to send
     * @return A CompletableFuture that completes when the message has been sent
     */
    public CompletableFuture<Void> sendAsync(Message amqpMessage) {
        try {
            if (_sender == null) {
                _sender = _client.createSender(node);
            }
            return _sender.sendAsync(amqpMessage);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Closes the producer endpoint.
     */
    @Override
    public void close() throws Exception {
        if (_sender != null) {
            _sender.close();
            _sender = null;
        }
        if (_client != null) {
            _client.close();
            _client = null;
        }

    }
}
